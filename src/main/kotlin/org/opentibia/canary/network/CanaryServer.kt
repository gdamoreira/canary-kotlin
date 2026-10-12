package org.opentibia.canary.network

import io.github.oshai.kotlinlogging.KotlinLogging
import io.netty.bootstrap.ServerBootstrap
import io.netty.channel.ChannelFuture
import io.netty.channel.ChannelInitializer
import io.netty.channel.ChannelOption
import io.netty.channel.EventLoopGroup
import io.netty.channel.nio.NioEventLoopGroup
import io.netty.channel.socket.SocketChannel
import io.netty.channel.socket.nio.NioServerSocketChannel
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.InMemoryAccountRepository
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.protocol.LoginHandler

private val logger = KotlinLogging.logger {}

/**
 * Netty-based TCP Server managing inbound client connections.
 */
class CanaryServer(
    private val port: Int = 7171,
    private val accountRepository: AccountRepository = InMemoryAccountRepository(),
    private val rsaCipher: RsaCipher = RsaCipher()
) {
    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var channelFuture: ChannelFuture? = null

    fun start() {
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        val bootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()
                    pipeline.addLast("frameDecoder", TibiaFrameDecoder())
                    pipeline.addLast("messageEncoder", TibiaMessageEncoder())
                    pipeline.addLast("loginHandler", LoginHandler(rsaCipher, accountRepository))
                }
            })

        val bindFuture = bootstrap.bind(port).sync()
        channelFuture = bindFuture
        logger.info { "Canary Kotlin Server listening on port $port" }
    }

    fun stop() {
        channelFuture?.channel()?.close()?.sync()
        workerGroup?.shutdownGracefully()
        bossGroup?.shutdownGracefully()
        logger.info { "Canary Kotlin Server stopped" }
    }
}
