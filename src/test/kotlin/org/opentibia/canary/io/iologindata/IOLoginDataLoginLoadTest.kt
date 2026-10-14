package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.players.pvp.Kill
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocations
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
import org.opentibia.canary.database.PlayerContainerItemState
import org.opentibia.canary.database.PlayerItemState
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the full `IOLoginData::loadPlayerById(..., false)`
 * login load (src/io/iologindata.cpp:81-185 over
 * src/io/functions/iologindata_load_player.cpp): the persisted player row,
 * skills, kills, stash, inventory tree, depot chests, inbox and instant spell
 * list materialize into the session `Player`, exactly like
 * `ProtocolGame::login` (protocolgame.cpp:1023).
 */
class IOLoginDataLoginLoadTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var vocations: Vocations
    private var killTime: Long = 0L

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_login_load_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Login Hero")

        vocations = Vocations().apply { loadFromXml(VOCATIONS_XML) }

        val itemTypes = ItemTypeRegistry().apply {
            register(ItemType(2376, "backpack", isContainer = true, capacity = 20, isMovable = true))
            register(ItemType(1987, "bag", isContainer = true, capacity = 8, isMovable = true))
            register(ItemType(2160, "crystal coin", isStackable = true))
            register(ItemType(2148, "gold coin", isStackable = true))
        }

        loginData = IOLoginData(
            players = JdbcPlayerRepository(dataSource),
            accounts = JdbcAccountRepository(dataSource),
            vip = JdbcVipRepository(dataSource),
            storageRepository = JdbcPlayerStorageRepository(dataSource),
            spells = JdbcPlayerSpellsRepository(dataSource),
            kills = JdbcPlayerKillsRepository(dataSource),
            stash = JdbcPlayerStashRepository(dataSource),
            rewards = JdbcPlayerRewardsRepository(dataSource),
            features = JdbcFeatureStateRepository(dataSource),
            items = JdbcPlayerItemsRepository(dataSource),
            depots = JdbcPlayerDepotRepository(dataSource),
            itemTypes = itemTypes,
            vocations = vocations,
        )

        loginData.savePlayerItem(
            101L,
            listOf(
                PlayerItemState(pid = Slots_t.CONST_SLOT_BACKPACK.value, sid = 101, itemId = 2376),
                PlayerItemState(pid = 101, sid = 102, itemId = 2160, count = 50),
            ),
        )
        loginData.savePlayerDepotItems(
            101L,
            listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 1987)),
        )
        loginData.savePlayerInbox(
            101L,
            listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 2160, count = 10)),
        )
        loginData.savePlayerStash(101L, mapOf(2148 to 100))
        killTime = getTimeNow() - 100
        loginData.savePlayerKills(
            101L,
            listOf(Kill(target = 42L, time = killTime, unavenged = true)),
        )
        JdbcPlayerSpellsRepository(dataSource).save(101L, listOf("exura"))
        seedCharmPoints(101L, 240)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun insertPlayer(id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, level, vocation, health, healthmax, mana, manamax,
                    experience, posx, posy, posz, cap, sex, pronoun, town_id,
                    skill_sword, skill_sword_tries, skill_fist, skill_fist_tries
                ) VALUES (?, ?, 1, 10, 4, 100, 100, 50, 50, 1, 1000, 1000, 7, 350, 1, 3, 1, 55, 1234, 10, 999999999)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
    }

    private fun seedCharmPoints(playerId: Long, points: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO player_feature_state (player_id, charm_points) VALUES (?, ?)",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, points)
                stmt.executeUpdate()
            }
        }
    }

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Login Hero",
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    )

    @Test
    fun `loadPlayerById materializes vitals skills inventory depot inbox stash kills spells and features`() {
        val player = newPlayer()

        assertThat(loginData.loadPlayerById(player, 101L, disableIrrelevantInfo = false)).isTrue()

        assertThat(player.health).isEqualTo(100)
        assertThat(player.maxHealth).isEqualTo(100)
        assertThat(player.mana).isEqualTo(50)
        assertThat(player.maxMana).isEqualTo(50)
        assertThat(player.capacity).isEqualTo(35_000.0)
        assertThat(player.position).isEqualTo(Position(1000, 1000, 7))

        assertThat(player.getBaseSkill(skills_t.SKILL_SWORD)).isEqualTo(55)
        assertThat(player.getSkillTries(skills_t.SKILL_SWORD)).isEqualTo(1234L)
        val nextSkillTries = vocations.getVocation(4)!!.getReqSkillTries(skills_t.SKILL_SWORD.value, 56)
        assertThat(player.getSkillPercent(skills_t.SKILL_SWORD))
            .isEqualTo(Player.getPercentLevel(1234L, nextSkillTries))

        // `skill_fist_tries` is far above the next requirement; Canary clamps it to 0.
        assertThat(player.getSkillTries(skills_t.SKILL_FIST)).isZero()

        val backpack = player.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK)
        assertThat(backpack).isInstanceOf(Container::class.java)
        assertThat((backpack as Container).getItemList().single().id).isEqualTo(2160)
        assertThat(backpack.getItemList().single().count).isEqualTo(50)

        assertThat(player.getDepotChest(1, false)!!.getItemList().single().id).isEqualTo(1987)
        assertThat(player.inbox.getItemList().single().id).isEqualTo(2160)
        assertThat(player.stash.getStashItemCount(2148)).isEqualTo(100)
        assertThat(player.pvpPlayer.getUnjustifiedKills()).containsExactly(
            Kill(target = 42L, time = killTime, unavenged = true),
        )
        assertThat(player.learnedInstantSpellList).containsExactly("exura")
        assertThat(player.features.bestiary.getCharmPoints()).isEqualTo(240)
    }

    @Test
    fun `loadPlayerById with disableIrrelevantInfo keeps the advanced feature state unloaded`() {
        val player = newPlayer()

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()

        assertThat(player.features.bestiary.getCharmPoints()).isZero()
        // The login preload still materializes inventory and skills.
        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_BACKPACK)).isNotNull
        assertThat(player.getBaseSkill(skills_t.SKILL_SWORD)).isEqualTo(55)
    }

    private companion object {
        const val VOCATIONS_XML = """
            <vocations>
                <vocation id="4" name="Druid" clientid="4" baseid="4" description="A druid"
                          gaincap="5" gainhp="5" gainmana="30" gainhpticks="6" gainhpamount="1"
                          gainmanaticks="6" gainmanaamount="2" manamultiplier="1.8"
                          attackspeed="2000" basespeed="110" soulmax="200" gainsoulticks="120000"
                          fromvoc="0"/>
            </vocations>
        """
    }
}
