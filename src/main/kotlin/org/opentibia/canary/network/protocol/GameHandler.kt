package org.opentibia.canary.network.protocol

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString

private val logger = KotlinLogging.logger {}

/**
 * Handles Game World connections on port 7172 (`ProtocolGame`).
 */
class GameHandler(
    private val rsaCipher: RsaCipher = RsaCipher(),
    private val accountRepository: AccountRepository,
    private val gameMap: GameMap,
    private val dispatcher: GameDispatcher,
    private val luaEnvironment: LuaEnvironment = LuaEnvironment().apply { initState() }
) : SimpleChannelInboundHandler<ByteBuf>() {

    private var player: Player? = null
    private var xteaCipher: XteaCipher? = null

    companion object {
        const val PACKET_SELF_APPEAR = 0x0A
        const val PACKET_DISCONNECT = 0x14
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (player == null) {
            handleGameLogin(ctx, msg)
        } else {
            handleGamePacket(ctx, msg)
        }
    }

    private fun handleGameLogin(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (msg.readableBytes() < 5) {
            ctx.close()
            return
        }

        val checksum = msg.readUnsignedIntLE()
        val payloadLen = msg.readableBytes()
        val calculatedChecksum = Adler32Checksum.compute(msg, msg.readerIndex(), payloadLen)

        if (checksum != calculatedChecksum) {
            logger.warn { "Adler32 checksum mismatch on game login" }
            ctx.close()
            return
        }

        val os = msg.readUnsignedShortLE()
        val version = msg.readUnsignedShortLE()

        if (msg.readableBytes() > RsaCipher.BLOCK_SIZE) {
            val skip = msg.readableBytes() - RsaCipher.BLOCK_SIZE
            msg.skipBytes(skip)
        }

        if (msg.readableBytes() < RsaCipher.BLOCK_SIZE) {
            ctx.close()
            return
        }

        val rsaBytes = ByteArray(RsaCipher.BLOCK_SIZE)
        msg.readBytes(rsaBytes)

        val decrypted = rsaCipher.decrypt(rsaBytes)
        if (!decrypted || rsaBytes[0] != 0.toByte()) {
            logger.warn { "RSA decryption failed on game handshake" }
            ctx.close()
            return
        }

        val rsaBuf = Unpooled.wrappedBuffer(rsaBytes)
        rsaBuf.readByte() // skip check byte 0x00

        val xteaKey = IntArray(4) { rsaBuf.readIntLE() }
        val cipher = XteaCipher(xteaKey)
        this.xteaCipher = cipher

        val isGamemaster = rsaBuf.readByte() != 0.toByte()
        val sessionKey = rsaBuf.readTibiaString()
        val characterName = rsaBuf.readTibiaString()

        val tokens = sessionKey.split("\n")
        val accountName = tokens.getOrNull(0) ?: ""
        val password = tokens.getOrNull(1) ?: ""

        val account = accountRepository.authenticate(accountName, password)
        val characterMatch = account?.characters?.find { it.name.equals(characterName, ignoreCase = true) }

        val encoder = ctx.pipeline().get(TibiaMessageEncoder::class.java)
        encoder?.xteaCipher = cipher
        encoder?.checksumEnabled = true

        if (characterMatch == null) {
            sendDisconnect(ctx, "Character not found or credentials invalid.")
            return
        }

        // Initialize player entity
        val spawnPos = Position(1000, 1000, 7)
        val activePlayer = Player(
            id = 1L,
            name = characterMatch.name,
            maxHealth = 150,
            health = 150,
            maxMana = 50,
            mana = 50,
            speed = 220,
            position = spawnPos
        )
        this.player = activePlayer

        logger.info { "Player '${activePlayer.name}' logged into game world" }
        sendLoginSuccess(ctx, activePlayer)
    }

    private fun handleGamePacket(ctx: ChannelHandlerContext, msg: ByteBuf) {
        val activePlayer = player ?: return
        val cipher = xteaCipher

        // Verify and decrypt incoming game packet if encrypted
        if (cipher != null && msg.readableBytes() >= 4) {
            val checksum = msg.readUnsignedIntLE()
            val payloadStart = msg.readerIndex()
            val payloadLen = msg.readableBytes()
            val calcChecksum = Adler32Checksum.compute(msg, payloadStart, payloadLen)

            if (checksum == calcChecksum) {
                cipher.decrypt(msg, payloadStart, payloadLen)
            } else {
                msg.readerIndex(payloadStart) // fallback if unchecksummed
            }
        }

        dispatcher.dispatch {
            GamePacketRouter.route(
                activePlayer,
                msg,
                GamePacketContext(map = gameMap, dispatcher = dispatcher, luaEnvironment = luaEnvironment)
            )
        }
    }

    private fun sendLoginSuccess(ctx: ChannelHandlerContext, player: Player) {
        val out = ctx.alloc().buffer()
        // 1. Self Appear (0x0A)
        out.writeByte(PACKET_SELF_APPEAR)
        out.writeIntLE(player.id.toInt())
        out.writeShortLE(50) // Graphics speed
        out.writeByte(0) // Can report bugs

        // 2. Map Description (0x64)
        MapPacketWriter.writeMapDescription(out, gameMap, player.position)

        ctx.writeAndFlush(out)
    }

    private fun sendDisconnect(ctx: ChannelHandlerContext, message: String) {
        val out = ctx.alloc().buffer()
        out.writeByte(PACKET_DISCONNECT)
        out.writeTibiaString(message)
        ctx.writeAndFlush(out).addListener { ctx.close() }
    }
}
