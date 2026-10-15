package org.opentibia.canary.network

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import org.opentibia.canary.account.InMemoryAccountRepository
import org.opentibia.canary.config.ConfigKeys
import org.opentibia.canary.config.ConfigManager
import org.opentibia.canary.config.ServerRole
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.map.GameMap
import java.net.Socket
import java.nio.file.Path

class CanaryServerRoleTest {

    private var server: CanaryServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop()
        server = null
    }

    @Test
    fun `login role binds the login and status listener without the game listener`() {
        val s = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            role = ServerRole.LOGIN,
            accountRepository = InMemoryAccountRepository(),
        )
        server = s
        s.start()

        assertThat(s.boundLoginPort).isNotNull
        assertThat(s.boundGamePort).isNull()
        Socket("127.0.0.1", s.boundLoginPort!!).use { socket ->
            assertThat(socket.isConnected).isTrue()
        }
    }

    @Test
    fun `game role binds the game listener without the login listener`() {
        val s = CanaryServer(loginPort = 0, gamePort = 0, role = ServerRole.GAME)
        server = s
        s.start()

        assertThat(s.boundGamePort).isNotNull
        assertThat(s.boundLoginPort).isNull()
        Socket("127.0.0.1", s.boundGamePort!!).use { socket ->
            assertThat(socket.isConnected).isTrue()
        }
    }

    @Test
    fun `all role keeps binding both listeners`() {
        val s = CanaryServer(
            loginPort = 0,
            gamePort = 0,
            role = ServerRole.ALL,
            accountRepository = InMemoryAccountRepository(),
        )
        server = s
        s.start()

        assertThat(s.boundLoginPort).isNotNull
        assertThat(s.boundGamePort).isNotNull
    }

    @Test
    fun `login role skips the world init`(@TempDir dir: Path) {
        val map = GameMap().apply { monsterfile = monsterFile(dir) }
        val s = worldServer(ServerRole.LOGIN, map)
        server = s
        s.start()

        assertThat(map.spawnsMonster.isLoaded()).isFalse()
        assertThat(map.spawnsMonster.isStarted()).isFalse()
    }

    @Test
    fun `game role runs the world init through the spawn startup`(@TempDir dir: Path) {
        val map = GameMap().apply { monsterfile = monsterFile(dir) }
        val s = worldServer(ServerRole.GAME, map)
        server = s
        s.start()

        assertThat(map.spawnsMonster.isLoaded()).isTrue()
        assertThat(map.spawnsMonster.isStarted()).isTrue()
    }

    private fun worldServer(role: ServerRole, map: GameMap): CanaryServer = CanaryServer(
        loginPort = 0,
        gamePort = 0,
        role = role,
        gameMap = map,
        configManager = ConfigManager(keyMap = ConfigKeys.all.associateWith { it.luaIdentifier }),
        monsters = Monsters(),
    )

    private fun monsterFile(dir: Path): String =
        dir.resolve("canary-monster.xml").toFile().apply {
            writeText("<monsters/>")
        }.path
}
