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
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.codec.RateLimiterHandler
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.protocol.GameHandler
import org.opentibia.canary.network.protocol.LoginHandler
import org.opentibia.canary.network.protocol.StatusHandler

private val logger = KotlinLogging.logger {}

/**
 * Netty-based TCP Server managing inbound client connections across Login (7171) and Game (7172) ports.
 */
class CanaryServer(
    private val loginPort: Int = 7171,
    private val gamePort: Int = 7172,
    private val accountRepository: AccountRepository = InMemoryAccountRepository(),
    private val gameMap: GameMap = GameMap(),
    private val dispatcher: GameDispatcher = GameDispatcher(),
    private val rsaCipher: RsaCipher = RsaCipher(),
    private val luaEnvironment: LuaEnvironment = LuaEnvironment().apply { initState() }
) {
    val scriptingEnvironment: LuaEnvironment get() = luaEnvironment

    private var bossGroup: EventLoopGroup? = null
    private var workerGroup: EventLoopGroup? = null
    private var loginChannelFuture: ChannelFuture? = null
    private var gameChannelFuture: ChannelFuture? = null

    fun start() {
        bossGroup = NioEventLoopGroup(1)
        workerGroup = NioEventLoopGroup()

        // 1. Login & Status Server Bootstrap (Port 7171)
        val loginBootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()
                    pipeline.addLast("rateLimiter", RateLimiterHandler(maxPacketsPerSecond = 50))
                    pipeline.addLast("frameDecoder", TibiaFrameDecoder())
                    pipeline.addLast("messageEncoder", TibiaMessageEncoder())
                    pipeline.addLast("statusHandler", StatusHandler())
                    pipeline.addLast("loginHandler", LoginHandler(rsaCipher, accountRepository))
                }
            })

        loginChannelFuture = loginBootstrap.bind(loginPort).sync()
        logger.info { "Canary Login/Status listener running on port $loginPort" }

        // 2. Game World Server Bootstrap (Port 7172)
        val gameBootstrap = ServerBootstrap()
            .group(bossGroup, workerGroup)
            .channel(NioServerSocketChannel::class.java)
            .childOption(ChannelOption.TCP_NODELAY, true)
            .childOption(ChannelOption.SO_KEEPALIVE, true)
            .childHandler(object : ChannelInitializer<SocketChannel>() {
                override fun initChannel(ch: SocketChannel) {
                    val pipeline = ch.pipeline()
                    pipeline.addLast("rateLimiter", RateLimiterHandler(maxPacketsPerSecond = 100))
                    pipeline.addLast("frameDecoder", TibiaFrameDecoder())
                    pipeline.addLast("messageEncoder", TibiaMessageEncoder())
                    pipeline.addLast("gameHandler", GameHandler(rsaCipher, accountRepository, gameMap, dispatcher))
                }
            })

        gameChannelFuture = gameBootstrap.bind(gamePort).sync()
        logger.info { "Canary Game World listener running on port $gamePort" }
    }

    fun stop() {
        loginChannelFuture?.channel()?.close()?.sync()
        gameChannelFuture?.channel()?.close()?.sync()
        workerGroup?.shutdownGracefully()
        bossGroup?.shutdownGracefully()
        logger.info { "Canary Kotlin Server listeners stopped" }
    }
}
