package org.opentibia.canary

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.network.CanaryServer

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "Starting Canary Kotlin Edition..." }
    val server = CanaryServer(port = 7171)
    server.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Shutting down..." }
        server.stop()
    })
}
