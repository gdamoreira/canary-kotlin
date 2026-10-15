package org.opentibia.canary.config

import org.opentibia.canary.database.DatabaseConfig
import org.opentibia.canary.database.DatabaseEngine

/**
 * Command-line overrides resolved from `main`'s arguments. `null` means "not supplied",
 * so environment values and defaults stay in effect. Precedence is CLI > config file >
 * environment > defaults.
 */
data class CliOptions(
    val configPath: String? = null,
    val dataDir: String? = null,
    val dbHost: String? = null,
    val dbPort: Int? = null,
    val dbName: String? = null,
    val dbUser: String? = null,
    val dbPassword: String? = null
)

/**
 * Parses Canary-server CLI arguments. Canary's `main.cpp` only inspects
 * `--generate-lua-api-docs-only` (`hasArgument`); the explicit server/database
 * switches here are the Kotlin bootstrap's CLI contract, resolved with precedence
 * over `CANARY_*` environment variables and defaults.
 */
object ServerCli {

    private val knownFlags = setOf(
        "--config",
        "--data-dir",
        "--db-host",
        "--db-port",
        "--db-name",
        "--db-user",
        "--db-password"
    )

    fun parse(args: Array<String>): CliOptions {
        val values = LinkedHashMap<String, String>()
        var index = 0
        while (index < args.size) {
            val argument = args[index]
            require(argument.startsWith("--")) { "Unexpected argument: $argument" }

            val separator = argument.indexOf('=')
            if (separator > 0) {
                values[argument.substring(0, separator)] = argument.substring(separator + 1)
                index++
            } else {
                require(index + 1 < args.size) { "Missing value for $argument" }
                values[argument] = args[index + 1]
                index += 2
            }
        }

        values.keys.forEach { key -> require(key in knownFlags) { "Unknown argument: $key" } }

        return CliOptions(
            configPath = values["--config"],
            dataDir = values["--data-dir"],
            dbHost = values["--db-host"],
            dbPort = values["--db-port"]?.let { value ->
                value.toIntOrNull() ?: throw IllegalArgumentException("Invalid --db-port value: $value")
            },
            dbName = values["--db-name"],
            dbUser = values["--db-user"],
            dbPassword = values["--db-password"]
        )
    }

    /**
     * Resolves the external database settings (CLI > `CANARY_DB_*` > defaults).
     * Returns `null` when no host is configured, which selects Canary's zero-config
     * H2 in-memory database in PostgreSQL mode.
     */
    fun databaseConfig(
        environment: Map<String, String> = System.getenv(),
        options: CliOptions = CliOptions()
    ): DatabaseConfig? {
        val engine = engineFrom(environment)
        val environmentHost = environment["CANARY_DB_HOST"]?.takeIf { it.isNotBlank() }
        val cliConfigured = options.dbHost != null || options.dbPort != null || options.dbName != null ||
            options.dbUser != null || options.dbPassword != null
        if (environmentHost == null && !cliConfigured) {
            return null
        }

        return DatabaseConfig(
            host = options.dbHost ?: environmentHost ?: "127.0.0.1",
            port = options.dbPort ?: environment["CANARY_DB_PORT"]?.toIntOrNull() ?: engine.defaultPort,
            database = options.dbName ?: environment["CANARY_DB_NAME"] ?: "canary",
            user = options.dbUser ?: environment["CANARY_DB_USER"] ?: "canary",
            password = options.dbPassword ?: environment["CANARY_DB_PASSWORD"] ?: "canary",
            engine = engine
        )
    }

    /**
     * `CANARY_DB_ENGINE` engine selector: `postgres`/`postgresql` ->
     * [DatabaseEngine.POSTGRESQL] (default), `maria`/`mariadb`/`mysql` ->
     * [DatabaseEngine.MARIADB]. An unknown value fails fast naming the variable.
     */
    private fun engineFrom(environment: Map<String, String>): DatabaseEngine =
        when (val value = environment["CANARY_DB_ENGINE"]?.takeIf { it.isNotBlank() }?.lowercase()) {
            null, "postgres", "postgresql" -> DatabaseEngine.POSTGRESQL
            "maria", "mariadb", "mysql" -> DatabaseEngine.MARIADB
            else -> throw IllegalArgumentException("Invalid CANARY_DB_ENGINE value: $value")
        }
}
