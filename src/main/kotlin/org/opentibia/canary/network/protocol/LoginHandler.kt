package org.opentibia.canary.network.protocol

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.InMemoryAccountRepository
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.crypto.XteaCipher
import org.opentibia.canary.network.message.readTibiaString
import org.opentibia.canary.network.message.writeTibiaString

private val logger = KotlinLogging.logger {}

/**
 * Handles incoming packets on the login port and serves ProtocolLogin requests.
 */
class LoginHandler(
    private val rsaCipher: RsaCipher = RsaCipher(),
    private val accountRepository: AccountRepository = InMemoryAccountRepository()
) : SimpleChannelInboundHandler<ByteBuf>() {

    companion object {
        const val PROTOCOL_LOGIN: Int = 0x01
        const val PROTOCOL_STATUS: Int = 0xFF

        const val RESPONSE_DISCONNECT: Byte = 0x0B
        const val RESPONSE_MOTD: Byte = 0x14
        const val RESPONSE_SESSION_KEY: Byte = 0x28
        const val RESPONSE_CHAR_LIST: Byte = 0x64
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (!msg.isReadable) {
            ctx.close()
            return
        }

        // Check for 4-byte Adler32 checksum at the front of the frame
        if (msg.readableBytes() < 5) {
            ctx.close()
            return
        }

        val checksum = msg.readUnsignedIntLE()
        val payloadLen = msg.readableBytes()
        val calculatedChecksum = Adler32Checksum.compute(msg, msg.readerIndex(), payloadLen)

        if (checksum != calculatedChecksum) {
            logger.warn { "Adler32 checksum mismatch: received=$checksum, expected=$calculatedChecksum" }
            ctx.close()
            return
        }

        val protocolId = msg.readUnsignedByte().toInt()
        when (protocolId) {
            PROTOCOL_LOGIN -> handleLogin(ctx, msg)
            PROTOCOL_STATUS -> {
                logger.info { "Status ping requested" }
                ctx.close()
            }
            else -> {
                logger.warn { "Unknown protocol byte: $protocolId" }
                ctx.close()
            }
        }
    }

    private fun handleLogin(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (msg.readableBytes() < 4) {
            ctx.close()
            return
        }

        val os = msg.readUnsignedShortLE()
        val clientVersion = msg.readUnsignedShortLE()
        logger.info { "Client connecting: OS=$os, Version=$clientVersion" }

        // Skip client metadata / asset checksums if present before RSA block (version-dependent)
        // For standard 10.98+ / modern layouts: 12 bytes signatures + 1 byte preview
        // For earlier 8.60 layouts: 12 bytes signatures
        if (msg.readableBytes() > RsaCipher.BLOCK_SIZE) {
            val skipBytes = msg.readableBytes() - RsaCipher.BLOCK_SIZE
            msg.skipBytes(skipBytes)
        }

        if (msg.readableBytes() < RsaCipher.BLOCK_SIZE) {
            sendErrorAndClose(ctx, null, "Malformed login packet.")
            return
        }

        // Decrypt RSA block
        val rsaBytes = ByteArray(RsaCipher.BLOCK_SIZE)
        msg.readBytes(rsaBytes)

        val decrypted = rsaCipher.decrypt(rsaBytes)
        if (!decrypted || rsaBytes[0] != 0.toByte()) {
            logger.warn { "RSA decryption failed or invalid check byte" }
            ctx.close()
            return
        }

        val rsaBuffer = Unpooled.wrappedBuffer(rsaBytes)
        rsaBuffer.readByte() // skip check byte 0x00

        // Read 16-byte XTEA key (4 x 32-bit unsigned integers Little-Endian)
        val xteaKey = IntArray(4) { rsaBuffer.readIntLE() }
        val xteaCipher = XteaCipher(xteaKey)

        // Read credentials (account name / email and password)
        val accountDescriptor = rsaBuffer.readTibiaString()
        val password = rsaBuffer.readTibiaString()

        // Configure outbound encoder with established XTEA session cipher
        val encoder = ctx.pipeline().get(TibiaMessageEncoder::class.java)
        encoder?.xteaCipher = xteaCipher
        encoder?.checksumEnabled = true

        val account = accountRepository.authenticate(accountDescriptor, password)
        if (account == null) {
            sendErrorAndClose(ctx, xteaCipher, "Account name or password is not correct.")
            return
        }

        sendCharacterList(ctx, account, accountDescriptor, password)
    }

    private fun sendCharacterList(
        ctx: ChannelHandlerContext,
        account: org.opentibia.canary.account.Account,
        accountDescriptor: String,
        password: String
    ) {
        val out = ctx.alloc().buffer()

        // 1. MOTD
        out.writeByte(RESPONSE_MOTD.toInt())
        out.writeTibiaString("1\nWelcome to Canary Kotlin Edition!")

        // 2. Session Key
        out.writeByte(RESPONSE_SESSION_KEY.toInt())
        out.writeTibiaString("$accountDescriptor\n$password")

        // 3. Character List (Opcode 0x64)
        out.writeByte(RESPONSE_CHAR_LIST.toInt())

        // Number of worlds
        out.writeByte(1)
        // World 0 description
        out.writeByte(0) // world id
        out.writeTibiaString("Canary World")
        out.writeTibiaString("127.0.0.1")
        out.writeShortLE(7172) // game port
        out.writeByte(0) // preview / state

        // Characters list
        out.writeByte(account.characters.size)
        for (character in account.characters) {
            out.writeByte(0) // world id
            out.writeTibiaString(character.name)
        }

        // Account status: Premium days remaining
        out.writeByte(account.premiumDays)
        out.writeByte(if (account.premiumDays > 0) 1 else 0) // is premium
        out.writeIntLE((System.currentTimeMillis() / 1000 + account.premiumDays * 86400L).toInt()) // expiration timestamp

        ctx.writeAndFlush(out).addListener {
            ctx.close()
        }
    }

    private fun sendErrorAndClose(ctx: ChannelHandlerContext, cipher: XteaCipher?, message: String) {
        val out = ctx.alloc().buffer()
        out.writeByte(RESPONSE_DISCONNECT.toInt())
        out.writeTibiaString(message)

        val encoder = ctx.pipeline().get(TibiaMessageEncoder::class.java)
        if (cipher != null && encoder != null) {
            encoder.xteaCipher = cipher
        }
        ctx.writeAndFlush(out).addListener {
            ctx.close()
        }
    }
}
