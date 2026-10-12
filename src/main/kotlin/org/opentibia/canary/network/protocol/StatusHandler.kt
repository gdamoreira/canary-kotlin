package org.opentibia.canary.network.protocol

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.buffer.ByteBuf
import io.netty.channel.ChannelHandlerContext
import io.netty.channel.SimpleChannelInboundHandler
import org.opentibia.canary.config.ServerConfig
import org.opentibia.canary.network.crypto.Adler32Checksum
import java.nio.charset.StandardCharsets

private val logger = KotlinLogging.logger {}

/**
 * Handles OpenTibia status queries (Opcode 0xFF) providing server statistics in TSQP XML format.
 */
class StatusHandler(
    private val config: ServerConfig = ServerConfig()
) : SimpleChannelInboundHandler<ByteBuf>() {

    companion object {
        const val PROTOCOL_STATUS = 0xFF
    }

    override fun channelRead0(ctx: ChannelHandlerContext, msg: ByteBuf) {
        if (msg.readableBytes() < 5) {
            ctx.fireChannelRead(msg.retain())
            return
        }

        msg.markReaderIndex()
        val checksum = msg.readUnsignedIntLE()
        val payloadLen = msg.readableBytes()
        val calculatedChecksum = Adler32Checksum.compute(msg, msg.readerIndex(), payloadLen)

        if (checksum != calculatedChecksum) {
            msg.resetReaderIndex()
            ctx.fireChannelRead(msg.retain())
            return
        }

        val protocolId = msg.readUnsignedByte().toInt()
        if (protocolId != PROTOCOL_STATUS) {
            msg.resetReaderIndex()
            ctx.fireChannelRead(msg.retain())
            return
        }

        logger.info { "Handling ProtocolStatus request from ${ctx.channel().remoteAddress()}" }

        val xml = buildXmlStatus()
        val xmlBytes = xml.toByteArray(StandardCharsets.UTF_8)

        val out = ctx.alloc().buffer(xmlBytes.size)
        out.writeBytes(xmlBytes)

        ctx.writeAndFlush(out).addListener {
            ctx.close()
        }
    }

    private fun buildXmlStatus(): String {
        return """
            <tsqp version="1.0">
                <serverinfo uptime="3600" ip="${config.ip}" servername="${config.serverName}" port="${config.loginPort}" location="Brazil" url="https://github.com/opentibiabr/canary" server="${config.serverName}" version="1.0.0" client="10.98"/>
                <owner name="Canary Team" email="opentibiabr@outlook.com"/>
                <players online="0" max="${config.maxPlayers}" peak="0"/>
                <monsters total="0"/>
                <npcs total="0"/>
                <rates exp="1" skill="1" magic="1" loot="1" spawn="1"/>
                <map name="Canary" author="OpenTibiaBR" width="2048" height="2048"/>
                <motd>${config.motd}</motd>
            </tsqp>
        """.trimIndent()
    }
}
