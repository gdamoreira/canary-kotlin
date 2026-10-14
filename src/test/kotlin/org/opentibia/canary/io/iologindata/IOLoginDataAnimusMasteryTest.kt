package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
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
import org.opentibia.canary.io.IOBestiaryPersistence
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.map.Position

/**
 * Wave 58 A2: the io call sites of the `players.animus_mastery` blob seam
 * (`PlayerAnimusMasteryPersistence`) must run during the login/save flow:
 * `IOLoginDataLoad::loadPlayerAnimusMastery`
 * (iologindata_load_player.cpp:277-289) right after the conditions load and the
 * inline `IOLoginDataSave::savePlayerFirst` block
 * (iologindata_save_player.cpp:293-299).
 *
 * PARITY NOTE: the port schema still lacks the `players.animus_mastery` BYTEA
 * column, so [IOBestiaryPersistence] maps the serialized name sequence to the
 * wave-36 `player_animus_mastery` interim table (V13); the blob codec itself is
 * the Canary `AnimusMastery::serialize`/`unserialize` format.
 *
 * Canary source of truth: `/Users/damoreira/.projects/canary`
 *  - src/io/functions/iologindata_load_player.cpp:277-289
 *  - src/io/functions/iologindata_save_player.cpp:293-299
 *  - src/creatures/players/animus_mastery/animus_mastery.cpp:62-73
 */
class IOLoginDataAnimusMasteryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_iologin_animus_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Animus Hero")
        loginData = newLoginData()
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newLoginData(bestiaryPersistence: IOBestiaryPersistence? = IOBestiaryPersistence(dataSource)): IOLoginData =
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
            bestiaryPersistence = bestiaryPersistence,
        )

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

    private fun newPlayer() = Player(
        id = 101L,
        name = "Animus Hero",
        maxHealth = 100,
        health = 100,
        position = Position(1000, 1000, 7),
    )

    @Test
    fun `savePlayerFirst writes the serialized animus blob`() {
        val player = newPlayer()
        player.features.animusMastery.add("Dragon Lord")
        player.features.animusMastery.add("Demon")

        assertThat(loginData.savePlayerFirst(player)).isTrue()

        val bestiary = IOBestiaryPersistence(dataSource)
        assertThat(bestiary.loadAnimusMastery(101L)).containsExactly("dragon lord", "demon")
        val expected = PropWriteStream().also { player.features.animusMastery.serialize(it) }.getStream()
        assertThat(bestiary.loadAnimusMasteryBlob(101L)).isEqualTo(expected)
    }

    @Test
    fun `loadPlayer restores the persisted masteries into the composed component`() {
        val source = newPlayer()
        source.features.animusMastery.add("Dragon Lord")
        source.features.animusMastery.add("Demon")
        assertThat(loginData.savePlayerFirst(source)).isTrue()

        val record = JdbcPlayerRepository(dataSource).loadRecordById(101L)
        val loaded = newPlayer()
        assertThat(loginData.loadPlayer(loaded, record)).isTrue()

        assertThat(loaded.features.animusMastery.getAnimusMasteries()).containsExactly("dragon lord", "demon")
    }

    @Test
    fun `loadPlayer keeps existing masteries when nothing is stored`() {
        val loaded = newPlayer()
        loaded.features.animusMastery.add("Rat")

        val record = JdbcPlayerRepository(dataSource).loadRecordById(101L)
        assertThat(loginData.loadPlayer(loaded, record)).isTrue()

        assertThat(loaded.features.animusMastery.getAnimusMasteries()).containsExactly("rat")
    }

    @Test
    fun `savePlayer persists the live animus masteries through the guard`() {
        val player = newPlayer()
        player.features.animusMastery.add("Demon")

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(IOBestiaryPersistence(dataSource).loadAnimusMastery(101L)).containsExactly("demon")
    }

    @Test
    fun `the blob path stays inert when the bestiary store is unwired`() {
        val unwired = newLoginData(bestiaryPersistence = null)
        val player = newPlayer()
        player.features.animusMastery.add("Demon")

        assertThat(unwired.savePlayerFirst(player)).isTrue()
        assertThat(IOBestiaryPersistence(dataSource).loadAnimusMastery(101L)).isEmpty()

        val loaded = newPlayer()
        val record = JdbcPlayerRepository(dataSource).loadRecordById(101L)
        assertThat(unwired.loadPlayer(loaded, record)).isTrue()
        assertThat(loaded.features.animusMastery.getAnimusMasteries()).isEmpty()
    }
}
