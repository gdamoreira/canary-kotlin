package org.opentibia.canary

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.InMemoryAccountRepository
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.network.CanaryServer

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "Starting Canary Kotlin Edition..." }
    val serverConfig = ConfigManager.load()

    val dbHost = System.getenv("CANARY_DB_HOST")
    val dataSource = if (!dbHost.isNullOrEmpty()) {
        logger.info { "Connecting to external PostgreSQL at $dbHost..." }
        val dbConfig = DatabaseConfig(
            host = dbHost,
            port = System.getenv("CANARY_DB_PORT")?.toIntOrNull() ?: 5432,
            database = System.getenv("CANARY_DB_NAME") ?: "canary",
            user = System.getenv("CANARY_DB_USER") ?: "canary",
            password = System.getenv("CANARY_DB_PASSWORD") ?: "canary"
        )
        DatabaseConnectionFactory.createDataSource(dbConfig)
    } else {
        logger.info { "No external database configured; initializing In-Memory Database (PostgreSQL mode)..." }
        DatabaseConnectionFactory.createInMemoryDataSource("canary_local")
    }

    // Automatically apply Flyway schema and seed migrations
    DatabaseConnectionFactory.migrate(dataSource)
    val accountRepo: AccountRepository = JdbcAccountRepository(dataSource)

    val server = CanaryServer(
        port = serverConfig.loginPort,
        accountRepository = accountRepo
    )
    server.start()

    Runtime.getRuntime().addShutdownHook(Thread {
        logger.info { "Shutting down..." }
        server.stop()
        dataSource.close()
    })
}
