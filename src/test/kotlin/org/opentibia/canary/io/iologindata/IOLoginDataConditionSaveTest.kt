package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionGeneric
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionSpeed
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcAccountRepository
import org.opentibia.canary.database.JdbcFeatureStateRepository
import org.opentibia.canary.database.JdbcPlayerDepotRepository
import org.opentibia.canary.database.JdbcPlayerItemsRepository
import org.opentibia.canary.database.JdbcPlayerKillsRepository
import org.opentibia.canary.database.JdbcPlayerRepository
import org.opentibia.canary.database.JdbcPlayerRewardsRepository
import org.opentibia.canary.database.JdbcPlayerSpellsRepository
import org.opentibia.canary.database.JdbcPlayerStashRepository
import org.opentibia.canary.database.JdbcPlayerStorageRepository
import org.opentibia.canary.database.JdbcVipRepository
import org.opentibia.canary.io.DbPlayerStorageRepository
import org.opentibia.canary.map.Position
import kotlin.random.Random

/**
 * Wave 59 A3 transcript tests for the `players.conditions` write of
 * `IOLoginDataSave::savePlayerFirst`
 * (src/io/functions/iologindata_save_player.cpp:279-291) as reached through
 * `IOLoginData::savePlayer` (src/io/iologindata.cpp:195-211) and read back by
 * `IOLoginDataLoad::loadPlayerConditions`
 * (src/io/functions/iologindata_load_player.cpp:257-275).
 *
 * The fixture wires the real H2 (Flyway V1-V21) schema and drives both halves
 * of the codec through the production seam:
 *  - [PlayerSubloadRepository.saveConditions] is the port's `players.conditions`
 *    column write;
 *  - [IOLoginDataSubloads.loadPlayerConditions] is the existing decode half.
 *
 * The last test pins the `IOLoginDataSubloads(playerStorage = ...)` composition
 * gap: the default now resolves `Player::storage()` through
 * `Player.features.storage` (player.hpp:1968), so a caller that omits the seam
 * still ingests into the composed component.
 */
class IOLoginDataConditionSaveTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var subloads: IOLoginDataSubloads

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_condition_save_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Condition Hero")

        loginData = newLoginData()
        subloads = IOLoginDataSubloads(
            subloadRepository = DbPlayerSubloadRepository(dataSource),
        )
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `savePlayer writes the persistent conditions and loadPlayerConditions decodes them`() {
        val player = newPlayer()
        val speed = ConditionSpeed(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_HASTE,
            ticks = 10_000,
            buff = false,
            subId = 0,
            initChangeSpeed = 120,
            now = { NOW },
            random = Random(0),
        )
        speed.setFormulaVars(0.5f, 1.0f, 2.0f, 3.0f)
        speed.setParam(ConditionParam.CONDITION_PARAM_OWNER, 42)
        val infight = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_INFIGHT,
            ticks = 5_000,
        )!!
        val damage = ConditionDamage(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_POISON,
            now = { NOW },
            random = Random(0),
        )
        damage.addDamage(3, 2_000, -20)
        val immortal = ConditionGeneric(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_DRUNK,
            ticks = -1,
        )

        assertThat(player.addCondition(speed)).isTrue()
        assertThat(player.addCondition(infight)).isTrue()
        assertThat(player.addCondition(damage)).isTrue()
        assertThat(player.addCondition(immortal)).isTrue()

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(readConditionsColumn(101L)).isNotEmpty()

        val loaded = newPlayer()
        subloads.loadPlayerConditions(loaded)

        assertThat(loaded.storedConditionList.map { it.type }).containsExactly(
            ConditionType.CONDITION_HASTE,
            ConditionType.CONDITION_INFIGHT,
            ConditionType.CONDITION_POISON,
        )

        val loadedSpeed = loaded.storedConditionList[0] as ConditionSpeed
        assertThat(loadedSpeed.ticks).isEqualTo(10_000)
        assertThat(loadedSpeed.speedDelta).isEqualTo(120)
        assertThat(loadedSpeed.mina).isEqualTo(0.5f)
        assertThat(loadedSpeed.minb).isEqualTo(1.0f)
        assertThat(loadedSpeed.maxa).isEqualTo(2.0f)
        assertThat(loadedSpeed.maxb).isEqualTo(3.0f)

        val loadedInfight = loaded.storedConditionList[1]
        assertThat(loadedInfight.id).isEqualTo(ConditionId.CONDITIONID_DEFAULT)
        assertThat(loadedInfight.ticks).isEqualTo(5_000)

        val loadedDamage = loaded.storedConditionList[2] as ConditionDamage
        assertThat(loadedDamage.scheduledDamage.map { it.value }).containsExactly(-20, -20)
        assertThat(loadedDamage.scheduledDamage.map { it.interval }).containsExactly(2_000, 2_000)
        // `Condition::createDamageCondition` (condition.cpp:326-341) drops the
        // header ticks for damage conditions, so `ConditionDamage` starts at 0
        // and every `CONDITIONATTR_INTERVALDATA` adds its interval
        // (condition.cpp:1820-1824): 2 rounds x 2000 ms.
        assertThat(loadedDamage.ticks).isEqualTo(4_000)
    }

    @Test
    fun `savePlayer round trips the loaded conditions before the login replay`() {
        val source = newPlayer()
        assertThat(
            source.addCondition(
                Condition.createCondition(
                    id = ConditionId.CONDITIONID_DEFAULT,
                    type = ConditionType.CONDITION_INFIGHT,
                    ticks = 5_000,
                )!!,
            ),
        ).isTrue()
        assertThat(
            source.addCondition(
                Condition.createCondition(
                    id = ConditionId.CONDITIONID_COMBAT,
                    type = ConditionType.CONDITION_EXHAUST,
                    ticks = 7_000,
                )!!,
            ),
        ).isTrue()
        assertThat(loginData.savePlayer(source)).isTrue()

        val loaded = newPlayer()
        subloads.loadPlayerConditions(loaded)
        assertThat(loaded.storedConditionList.map { it.type }).containsExactly(
            ConditionType.CONDITION_INFIGHT,
            ConditionType.CONDITION_EXHAUST,
        )

        assertThat(loginData.savePlayer(loaded)).isTrue()

        val reloaded = newPlayer()
        subloads.loadPlayerConditions(reloaded)
        assertThat(reloaded.storedConditionList.map { it.type }).containsExactly(
            ConditionType.CONDITION_INFIGHT,
            ConditionType.CONDITION_EXHAUST,
        )
        assertThat(reloaded.storedConditionList.map { it.ticks }).containsExactly(5_000, 7_000)
    }

    @Test
    fun `savePlayer fails when the conditions write fails`() {
        val failing = newLoginData(subloadRepository = FailingConditionRepository)

        assertThat(failing.savePlayer(newPlayer())).isFalse()
    }

    @Test
    fun `loadPlayerStorageMap defaults to the composed player storage`() {
        DbPlayerStorageRepository(dataSource).upsert(101L, mapOf(100 to 7, 200 to -3))
        val defaultSubloads = IOLoginDataSubloads(
            subloadRepository = DbPlayerSubloadRepository(dataSource),
            storageRepository = DbPlayerStorageRepository(dataSource),
        )
        val player = newPlayer()

        defaultSubloads.loadPlayerStorageMap(player)

        assertThat(player.features.storage.get(100)).isEqualTo(7)
        assertThat(player.features.storage.get(200)).isEqualTo(-3)
        assertThat(player.features.storage.getModifiedKeys()).isEmpty()
    }

    private fun newLoginData(subloadRepository: PlayerSubloadRepository? = DbPlayerSubloadRepository(dataSource)): IOLoginData =
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
            features = JdbcFeatureStateRepository(dataSource),
            subloadRepository = subloadRepository,
        )

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Condition Hero",
        maxHealth = 150,
        health = 150,
        position = Position(1000, 1000, 7),
        level = 10,
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

    private fun readConditionsColumn(playerId: Long): ByteArray? = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT conditions FROM players WHERE id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getBytes("conditions") else null
            }
        }
    }

    /** `PlayerSubloadRepository` double whose `saveConditions` fails like a broken write. */
    private object FailingConditionRepository : PlayerSubloadRepository {
        override fun loadConditionSkull(playerId: Long): PlayerConditionSkullRecord? = null

        override fun loadOutfit(playerId: Long): PlayerOutfitRecord? = null

        override fun saveConditions(playerId: Long, blob: ByteArray): Boolean = false
    }

    private companion object {
        const val NOW = 1_700_000_000L
    }
}
