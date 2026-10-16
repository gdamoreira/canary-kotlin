package org.opentibia.canary.config

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.assertThatThrownBy
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseEngine

class ServerCliTest {

    @Test
    fun `parses defaults when no arguments are provided`() {
        val options = ServerCli.parse(emptyArray())

        assertThat(options.configPath).isNull()
        assertThat(options.dataDir).isNull()
        assertThat(options.dbHost).isNull()
        assertThat(options.dbPort).isNull()
        assertThat(options.dbName).isNull()
        assertThat(options.dbUser).isNull()
        assertThat(options.dbPassword).isNull()
    }

    @Test
    fun `parses every supported flag into overrides`() {
        val options = ServerCli.parse(
            arrayOf(
                "--config", "canary.properties",
                "--data-dir", "data-canary",
                "--db-host", "db.example.org",
                "--db-port", "5433",
                "--db-name", "canary_prod",
                "--db-user", "canary_user",
                "--db-password", "secret"
            )
        )

        assertThat(options.configPath).isEqualTo("canary.properties")
        assertThat(options.dataDir).isEqualTo("data-canary")
        assertThat(options.dbHost).isEqualTo("db.example.org")
        assertThat(options.dbPort).isEqualTo(5433)
        assertThat(options.dbName).isEqualTo("canary_prod")
        assertThat(options.dbUser).isEqualTo("canary_user")
        assertThat(options.dbPassword).isEqualTo("secret")
    }

    @Test
    fun `supports the equals form for flags`() {
        val options = ServerCli.parse(arrayOf("--data-dir=data-canary", "--db-port=5434"))

        assertThat(options.dataDir).isEqualTo("data-canary")
        assertThat(options.dbPort).isEqualTo(5434)
    }

    @Test
    fun `rejects unknown flags`() {
        assertThatThrownBy { ServerCli.parse(arrayOf("--unknown")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `rejects a flag without its value`() {
        assertThatThrownBy { ServerCli.parse(arrayOf("--db-host")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `rejects a non-numeric database port`() {
        assertThatThrownBy { ServerCli.parse(arrayOf("--db-port", "not-a-number")) }
            .isInstanceOf(IllegalArgumentException::class.java)
    }

    @Test
    fun `database config is absent when neither CLI nor environment configures a host`() {
        assertThat(ServerCli.databaseConfig(emptyMap(), CliOptions())).isNull()
    }

    @Test
    fun `database config prefers CLI values over the environment`() {
        val environment = mapOf(
            "CANARY_DB_HOST" to "env-host",
            "CANARY_DB_PORT" to "6000",
            "CANARY_DB_NAME" to "env-db",
            "CANARY_DB_USER" to "env-user",
            "CANARY_DB_PASSWORD" to "env-password"
        )
        val options = CliOptions(
            dbHost = "cli-host",
            dbPort = 7000,
            dbName = "cli-db",
            dbUser = "cli-user",
            dbPassword = "cli-password"
        )

        val config = ServerCli.databaseConfig(environment, options)

        assertThat(config).isNotNull
        assertThat(config?.host).isEqualTo("cli-host")
        assertThat(config?.port).isEqualTo(7000)
        assertThat(config?.database).isEqualTo("cli-db")
        assertThat(config?.user).isEqualTo("cli-user")
        assertThat(config?.password).isEqualTo("cli-password")
    }

    @Test
    fun `database config falls back to CANARY environment values`() {
        val environment = mapOf(
            "CANARY_DB_HOST" to "env-host",
            "CANARY_DB_PORT" to "6000",
            "CANARY_DB_NAME" to "env-db",
            "CANARY_DB_USER" to "env-user",
            "CANARY_DB_PASSWORD" to "env-password"
        )

        val config = ServerCli.databaseConfig(environment, CliOptions())

        assertThat(config).isNotNull
        assertThat(config?.host).isEqualTo("env-host")
        assertThat(config?.port).isEqualTo(6000)
        assertThat(config?.database).isEqualTo("env-db")
        assertThat(config?.user).isEqualTo("env-user")
        assertThat(config?.password).isEqualTo("env-password")
    }

    @Test
    fun `a CLI database port without a host defaults to localhost`() {
        val config = ServerCli.databaseConfig(emptyMap(), CliOptions(dbPort = 5433))

        assertThat(config).isNotNull
        assertThat(config?.host).isEqualTo("127.0.0.1")
        assertThat(config?.port).isEqualTo(5433)
    }

    @Test
    fun `database config defaults to the MariaDB engine on port 3306`() {
        val config = ServerCli.databaseConfig(mapOf("CANARY_DB_HOST" to "env-host"), CliOptions())

        assertThat(config?.engine).isEqualTo(DatabaseEngine.MARIADB)
        assertThat(config?.port).isEqualTo(3306)
    }

    @Test
    fun `database config reads the MariaDB engine and its default port`() {
        val environment = mapOf(
            "CANARY_DB_HOST" to "env-host",
            "CANARY_DB_ENGINE" to "mariadb"
        )

        val config = ServerCli.databaseConfig(environment, CliOptions())

        assertThat(config?.engine).isEqualTo(DatabaseEngine.MARIADB)
        assertThat(config?.port).isEqualTo(3306)
    }

    @Test
    fun `database config accepts every engine alias`() {
        listOf("postgres", "postgresql").forEach { alias ->
            val config = ServerCli.databaseConfig(
                mapOf("CANARY_DB_HOST" to "env-host", "CANARY_DB_ENGINE" to alias),
                CliOptions()
            )

            assertThat(config?.engine).describedAs("alias %s", alias).isEqualTo(DatabaseEngine.POSTGRESQL)
        }

        listOf("maria", "mariadb", "mysql").forEach { alias ->
            val config = ServerCli.databaseConfig(
                mapOf("CANARY_DB_HOST" to "env-host", "CANARY_DB_ENGINE" to alias),
                CliOptions()
            )

            assertThat(config?.engine).describedAs("alias %s", alias).isEqualTo(DatabaseEngine.MARIADB)
        }
    }

    @Test
    fun `an explicit database port wins over the engine default`() {
        val environment = mapOf(
            "CANARY_DB_HOST" to "env-host",
            "CANARY_DB_ENGINE" to "maria",
            "CANARY_DB_PORT" to "3307"
        )

        val config = ServerCli.databaseConfig(environment, CliOptions())

        assertThat(config?.engine).isEqualTo(DatabaseEngine.MARIADB)
        assertThat(config?.port).isEqualTo(3307)
    }

    @Test
    fun `an unknown engine value fails fast naming the variable`() {
        val environment = mapOf(
            "CANARY_DB_HOST" to "env-host",
            "CANARY_DB_ENGINE" to "oracle"
        )

        assertThatThrownBy { ServerCli.databaseConfig(environment, CliOptions()) }
            .isInstanceOf(IllegalArgumentException::class.java)
            .hasMessageContaining("CANARY_DB_ENGINE")
    }
}
