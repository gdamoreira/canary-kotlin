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
import org.opentibia.canary.lua.runtime.ScriptingDispatch
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.codec.RateLimiterHandler
import org.opentibia.canary.network.codec.TibiaFrameDecoder
import org.opentibia.canary.network.codec.TibiaMessageEncoder
import org.opentibia.canary.network.crypto.RsaCipher
import org.opentibia.canary.network.protocol.GameHandler
import org.opentibia.canary.network.protocol.LoginHandler
import org.opentibia.canary.network.protocol.StatusHandler
import java.util.concurrent.atomic.AtomicInteger

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

    private val scriptingDispatch = ScriptingDispatch(luaEnvironment, dispatcher)

    /** `Game::playersOnline` equivalent; see [checkPlayersRecord]. */
    private val playersOnline = AtomicInteger(0)

    /** `Game::playersRecord` equivalent; see [checkPlayersRecord]. */
    private val playersRecord = AtomicInteger(0)

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
                    pipeline.addLast("gameHandler", createGameHandler())
                }
            })

        gameChannelFuture = gameBootstrap.bind(gamePort).sync()
        logger.info { "Canary Game World listener running on port $gamePort" }

        // Canary fires the `startup` global events during `GAME_STATE_INIT`
        // (g_globalEvents().startup(), src/game/game.cpp:988) before the world serves
        // players. Confined to the WorldCommit lane by ScriptingDispatch.
        scriptingDispatch.globalStartup()
    }

    /** Builds the per-connection game handler wired to this server's record tracking. */
    internal fun createGameHandler(): GameHandler = GameHandler(
        rsaCipher,
        accountRepository,
        gameMap,
        dispatcher,
        luaEnvironment,
        onPlayerAppear = { onPlayerAppear() },
        onPlayerDisappear = { onPlayerDisappear() },
    )

    /**
     * Mirrors `Player::onCreatureAppear` -> `Game::checkPlayersRecord`
     * (src/creatures/players/player.cpp:12300, src/game/game.cpp:9625): counts the player
     * online and fires every registered `record` global event when the count beats the
     * stored record. Returns whether a record event was registered and dispatched.
     */
    internal fun onPlayerAppear(): Boolean {
        playersOnline.incrementAndGet()
        return checkPlayersRecord()
    }

    /** Mirrors the online-player decrement of `Game::removeCreature`/player logout. */
    internal fun onPlayerDisappear() {
        playersOnline.decrementAndGet()
    }

    /**
     * `Game::checkPlayersRecord` (src/game/game.cpp:9625-9636): when the online count beats
     * the stored record, advances the record and fires each registered `record` event with
     * `(playersRecord, previousRecord)`. The CAS keeps the counters safe for concurrent
     * logins; the Lua callbacks themselves stay confined to the WorldCommit lane.
     */
    internal fun checkPlayersRecord(): Boolean {
        val current = playersOnline.get()
        var record = playersRecord.get()
        while (current > record) {
            if (playersRecord.compareAndSet(record, current)) {
                return scriptingDispatch.globalRecord(current.toLong(), record.toLong())
            }
            record = playersRecord.get()
        }
        return false
    }

    fun stop() {
        loginChannelFuture?.channel()?.close()?.sync()
        gameChannelFuture?.channel()?.close()?.sync()
        workerGroup?.shutdownGracefully()
        bossGroup?.shutdownGracefully()
        logger.info { "Canary Kotlin Server listeners stopped" }
    }
}
