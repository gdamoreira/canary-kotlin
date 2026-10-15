package org.opentibia.canary.bootstrap

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.config.ServerCli
import java.util.concurrent.atomic.AtomicInteger

private val logger = KotlinLogging.logger {}

/**
 * Composition root behind the thin `Main.kt`: parses CLI overrides (including the
 * wave-75 `--role`, resolved with `CANARY_ROLE` by the config boot), wires
 * [ServerBootstrap] with SIGINT/SIGTERM and uncaught-exception handling, then blocks
 * like Canary's `ServiceManager::run` until the first shutdown request.
 */
object ServerLaunch {

    private const val EXIT_OK = 0
    private const val EXIT_FAILURE = 1
    private const val EXIT_USAGE = 2

    fun run(args: Array<String>, environment: Map<String, String> = System.getenv()): Int {
        val options = try {
            ServerCli.parse(args)
        } catch (e: IllegalArgumentException) {
            logger.error { "Invalid command line: ${e.message}" }
            return EXIT_USAGE
        }

        val bootstrap = ServerBootstrap(
            environment = environment,
            options = options,
            databaseConfig = ServerCli.databaseConfig(environment, options)
        )
        val exitCode = AtomicInteger(EXIT_OK)

        Signals(onShutdown = { bootstrap.shutdown() }).install()
        CrashHandler(
            onCrash = {
                exitCode.set(EXIT_FAILURE)
                bootstrap.shutdown()
            }
        ).install()

        return try {
            bootstrap.run()
            bootstrap.awaitShutdown()
            exitCode.get()
        } catch (e: Exception) {
            logger.error(e) { "Failed to initialize server" }
            exitCode.set(EXIT_FAILURE)
            bootstrap.shutdown()
            exitCode.get()
        }
    }
}
