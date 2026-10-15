package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSpellCooldown
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.SpellCooldownClient
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.database.JdbcPlayerDepotRepository
import org.opentibia.canary.database.JdbcPlayerItemsRepository
import org.opentibia.canary.database.JdbcPlayerKillsRepository
import org.opentibia.canary.database.JdbcPlayerRepository
import org.opentibia.canary.database.JdbcPlayerRewardsRepository
import org.opentibia.canary.database.JdbcPlayerSpellsRepository
import org.opentibia.canary.database.JdbcPlayerStashRepository
import org.opentibia.canary.database.JdbcPlayerStorageRepository
import org.opentibia.canary.database.JdbcVipRepository
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.InstantSpell
import org.opentibia.canary.spells.Spells

/**
 * Transcript tests for the login-side `Player::g_spells()` wiring.
 *
 * Canary's `Player::sendSpellCooldowns` (player.cpp:13405-13437) resolves every
 * stored `CONDITION_SPELLCOOLDOWN` sub-id through the process-wide
 * `g_spells().getInstantSpellById`; the port replaces the singleton with the
 * `Player.spells` seam, which the login load must populate from the booted
 * registry (the `IOLoginDataLoad::loadPlayerInstantSpellList` hunk,
 * iologindata_load_player.cpp:527-541, is the load path that first needs it).
 */
class IOLoginDataSpellRegistryTest {

    private lateinit var dataSource: HikariDataSource

    private class RecordingCooldownClient : SpellCooldownClient {
        val spellCooldowns = mutableListOf<Pair<Int, Int>>()
        val groupCooldowns = mutableListOf<Pair<SpellGroup, Int>>()

        override fun sendSpellCooldown(spellId: Int, time: Int) {
            spellCooldowns.add(spellId to time)
        }

        override fun sendSpellGroupCooldown(groupId: SpellGroup, time: Int) {
            groupCooldowns.add(groupId to time)
        }
    }

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_spell_registry_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Spell Registry Hero")
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newLoginData(spellRegistry: (() -> Spells?)? = null): IOLoginData =
        if (spellRegistry == null) {
            IOLoginData(
                players = JdbcPlayerRepository(dataSource),
                accounts = JdbcAccountRepository(dataSource),
                vip = JdbcVipRepository(dataSource),
                storageRepository = JdbcPlayerStorageRepository(dataSource),
                items = JdbcPlayerItemsRepository(dataSource),
                depots = JdbcPlayerDepotRepository(dataSource),
                spells = JdbcPlayerSpellsRepository(dataSource),
                kills = JdbcPlayerKillsRepository(dataSource),
                stash = JdbcPlayerStashRepository(dataSource),
                rewards = JdbcPlayerRewardsRepository(dataSource),
            )
        } else {
            IOLoginData(
                players = JdbcPlayerRepository(dataSource),
                accounts = JdbcAccountRepository(dataSource),
                vip = JdbcVipRepository(dataSource),
                storageRepository = JdbcPlayerStorageRepository(dataSource),
                items = JdbcPlayerItemsRepository(dataSource),
                depots = JdbcPlayerDepotRepository(dataSource),
                spells = JdbcPlayerSpellsRepository(dataSource),
                kills = JdbcPlayerKillsRepository(dataSource),
                stash = JdbcPlayerStashRepository(dataSource),
                rewards = JdbcPlayerRewardsRepository(dataSource),
                spellRegistry = spellRegistry,
            )
        }

    private fun insertPlayer(id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, level, vocation, health, healthmax, mana, manamax,
                    experience, posx, posy, posz, cap, sex, pronoun, town_id
                ) VALUES (?, ?, 1, 10, 4, 100, 100, 50, 50, 1, 1000, 1000, 7, 350, 1, 3, 1)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
    }

    private fun registry(): Spells = Spells().apply {
        registerInstantLuaEvent(
            InstantSpell().apply {
                spellId = 1300
                name = "Exura"
                words = "exura"
                group = SpellGroup.SPELLGROUP_HEALING
            },
        )
    }

    private fun newPlayer() = Player(
        id = 101L,
        name = "Spell Registry Hero",
        maxHealth = 100,
        health = 100,
        position = Position(1, 1, 7),
    ).apply { now = { 1_000_000L } }

    @Test
    fun `loadPlayer attaches the injected g_spells registry and cooldown sends resolve`() {
        val spells = registry()
        val loginData = newLoginData(spellRegistry = { spells })
        val player = newPlayer()

        assertThat(loginData.loadPlayerById(player, 101L, disableIrrelevantInfo = false)).isTrue()

        assertThat(player.spells).isSameAs(spells)

        val client = RecordingCooldownClient()
        player.spellCooldownClient = client
        player.addCondition(
            ConditionSpellCooldown(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_SPELLCOOLDOWN,
                2_000,
                subId = 1300,
                now = { 1_000_000L },
            ),
        )

        player.sendSpellCooldowns()

        assertThat(client.spellCooldowns).containsExactly(1300 to 2_000)
    }

    @Test
    fun `an unresolvable registry seam keeps the attached registry`() {
        val loginData = newLoginData(spellRegistry = { null })
        val player = newPlayer()
        val attached = registry()
        player.spells = attached

        assertThat(loginData.loadPlayerById(player, 101L, disableIrrelevantInfo = false)).isTrue()

        assertThat(player.spells).isSameAs(attached)
    }
}
