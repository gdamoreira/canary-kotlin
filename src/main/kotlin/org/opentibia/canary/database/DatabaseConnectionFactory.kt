package org.opentibia.canary.database

import com.zaxxer.hikari.HikariConfig
import com.zaxxer.hikari.HikariDataSource
import io.github.oshai.kotlinlogging.KotlinLogging
import org.flywaydb.core.Flyway
import javax.sql.DataSource

private val logger = KotlinLogging.logger {}

data class DatabaseConfig(
    val host: String = "127.0.0.1",
    val port: Int = 3306,
    val database: String = "canary",
    val user: String = "canary",
    val password: String = "canary",
    val poolSize: Int = 10,
    val jdbcUrlOverride: String? = null
) {
    val jdbcUrl: String
        get() = jdbcUrlOverride ?: "jdbc:mariadb://$host:$port/$database?autoReconnect=true&useUnicode=true&characterEncoding=UTF-8"
}

object DatabaseConnectionFactory {

    fun createDataSource(config: DatabaseConfig): HikariDataSource {
        val hikariConfig = HikariConfig().apply {
            jdbcUrl = config.jdbcUrl
            username = config.user
            password = config.password
            maximumPoolSize = config.poolSize
            connectionTimeout = 5000
            idleTimeout = 600000
            maxLifetime = 1800000
            poolName = "CanaryHikariPool"
        }
        return HikariDataSource(hikariConfig)
    }

    /**
     * Executes database migrations using Flyway.
     */
    fun migrate(dataSource: DataSource, locations: Array<String> = arrayOf("classpath:db/migration")): Int {
        logger.info { "Running database migrations via Flyway..." }
        val flyway = Flyway.configure()
            .dataSource(dataSource)
            .locations(*locations)
            .baselineOnMigrate(true)
            .load()

        val result = flyway.migrate()
        logger.info { "Flyway migrations finished. Applied ${result.migrationsExecuted} migrations." }
        return result.migrationsExecuted
    }
}
