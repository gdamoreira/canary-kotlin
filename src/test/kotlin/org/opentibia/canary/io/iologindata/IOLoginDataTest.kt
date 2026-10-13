package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.creatures.players.components.StoragePlayer
import org.opentibia.canary.creatures.players.pvp.Kill
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
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position
import org.opentibia.canary.database.PlayerContainerItemState
import org.opentibia.canary.systems.familiar.FamiliarEntry
import org.opentibia.canary.utils.Tools

/**
 * H2 (Flyway V1-V10) transcript tests for the [IOLoginData] port of
 * `src/io/iologindata.cpp` (24 methods) and its load/save helpers:
 *  - auth + `getAccountType` (iologindata.cpp:24-78);
 *  - `loadPlayerById` / `loadPlayerByName` / `loadPlayer` /
 *    `loadOnlyDataForOnlinePlayer` (:81-193) over the V10 player columns;
 *  - `savePlayer` / `savePlayerGuard` / `saveOnlyDataForOnlinePlayer` (:195-298);
 *  - GUID lookups and `increaseBankBalance` (:300-361);
 *  - VIP statics (:363-499);
 *  - the load/save helpers of `src/io/functions/iologindata_load_player.cpp`
 *    and `iologindata_save_player.cpp` (spells, kills, stash, rewards, storage).
 */
class IOLoginDataTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var playersRepo: JdbcPlayerRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_iologindata_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)

        val accounts = JdbcAccountRepository(dataSource)
        val players = JdbcPlayerRepository(dataSource)
        playersRepo = players
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE accounts SET \"password\" = ? WHERE id = 1").use { stmt ->
                stmt.setString(1, Tools.transformToSHA1("secret"))
                stmt.executeUpdate()
            }
        }
        val otherAccountId = accounts.createAccount("outsider-account", "hash", "outsider@test.com", 0, 1)!!
        insertPlayer(101L, "Login Hero", accountId = 1L)
        insertPlayer(102L, "Outsider", accountId = otherAccountId, vocation = 1, level = 8)

        loginData = IOLoginData(
            players = players,
            accounts = accounts,
            vip = JdbcVipRepository(dataSource),
            storageRepository = JdbcPlayerStorageRepository(dataSource),
            spells = JdbcPlayerSpellsRepository(dataSource),
            kills = JdbcPlayerKillsRepository(dataSource),
            stash = JdbcPlayerStashRepository(dataSource),
            rewards = JdbcPlayerRewardsRepository(dataSource),
            features = JdbcFeatureStateRepository(dataSource),
            items = JdbcPlayerItemsRepository(dataSource),
            depots = JdbcPlayerDepotRepository(dataSource),
        )
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun insertPlayer(id: Long, name: String, accountId: Long, vocation: Int = 4, level: Int = 10) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                """
                INSERT INTO players (
                    id, name, account_id, level, vocation, health, healthmax, mana, manamax,
                    experience, posx, posy, posz, cap, sex, pronoun, town_id
                ) VALUES (?, ?, ?, ?, ?, 100, 100, 50, 50, 1, 1000, 1000, 7, 350, 1, 3, 1)
                """.trimIndent(),
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.setLong(3, accountId)
                stmt.setInt(4, level)
                stmt.setInt(5, vocation)
                stmt.executeUpdate()
            }
        }
    }

    private fun newPlayer(id: Long = 101L, name: String = "Login Hero"): Player = Player(
        id = id,
        name = name,
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    )

    @Test
    fun `gameWorldAuthentication authenticates and resolves the character`() {
        val result = loginData.gameWorldAuthentication(
            accountDescriptor = "1",
            sessionOrPassword = "secret",
            characterName = "Login Hero",
            oldProtocol = true,
            ip = 0,
        )

        assertThat(result).isNotNull
        assertThat(result!!.accountId).isEqualTo(1L)
        assertThat(result.characterName).isEqualTo("Login Hero")
    }

    @Test
    fun `gameWorldAuthentication rejects a wrong password`() {
        val result = loginData.gameWorldAuthentication(
            accountDescriptor = "1",
            sessionOrPassword = "wrong",
            characterName = "Login Hero",
            oldProtocol = true,
            ip = 0,
        )

        assertThat(result).isNull()
    }

    @Test
    fun `gameWorldAuthentication rejects a character owned by another account`() {
        val result = loginData.gameWorldAuthentication(
            accountDescriptor = "1",
            sessionOrPassword = "secret",
            characterName = "Outsider",
            oldProtocol = true,
            ip = 0,
        )

        assertThat(result).isNull()
    }

    @Test
    fun `gameWorldAuthentication rejects an unknown account`() {
        val result = loginData.gameWorldAuthentication(
            accountDescriptor = "missing-account",
            sessionOrPassword = "secret",
            characterName = "Login Hero",
            oldProtocol = true,
            ip = 0,
        )

        assertThat(result).isNull()
    }

    @Test
    fun `getAccountType returns the persisted account type`() {
        assertThat(loginData.getAccountType(1L)).isEqualTo(1)
        assertThat(loginData.getAccountType(999_999L)).isEqualTo(1)
    }

    @Test
    fun `loadPlayerByName applies basic info experience and blessings`() {
        insertPlayerColumn(101L, "blessings1", 2)
        insertPlayerColumn(101L, "blessings8", 3)
        val player = newPlayer()

        val loaded = loginData.loadPlayerByName(player, "Login Hero")

        assertThat(loaded).isTrue()
        assertThat(player.health).isEqualTo(100)
        assertThat(player.maxHealth).isEqualTo(100)
        assertThat(player.mana).isEqualTo(50)
        assertThat(player.maxMana).isEqualTo(50)
        assertThat(player.capacity).isEqualTo(35_000.0)
        assertThat(player.level).isEqualTo(10)
        assertThat(player.vocationId).isEqualTo(4)
        assertThat(player.position).isEqualTo(Position(1000, 1000, 7))
        assertThat(player.getSex()).isEqualTo(org.opentibia.canary.creatures.players.components.PlayerSex_t.MALE)
        assertThat(player.getPronoun()).isEqualTo(org.opentibia.canary.creatures.PlayerPronoun_t.PLAYERPRONOUN_HE)
        assertThat(player.getBlessingCount(1)).isEqualTo(2)
        assertThat(player.getBlessingCount(8)).isEqualTo(3)
        assertThat(player.experience).isEqualTo(Player.getExpForLevel(10))
    }

    @Test
    fun `loadPlayerById loads the matching character`() {
        val player = newPlayer(name = "Login Hero")

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()
        assertThat(loginData.loadPlayerById(player, 999_999L)).isFalse()
    }

    @Test
    fun `loadPlayer rejects a null record like Canary`() {
        assertThat(loginData.loadPlayer(newPlayer(), null)).isFalse()
    }

    @Test
    fun `loadPlayer respects disableIrrelevantInfo for feature state`() {
        seedCharmPoints(101L, 240)
        val player = newPlayer()

        assertThat(loginData.loadPlayerByName(player, "Login Hero", disableIrrelevantInfo = true)).isTrue()
        assertThat(player.features.bestiary.getCharmPoints()).isZero()

        assertThat(loginData.loadPlayerByName(player, "Login Hero", disableIrrelevantInfo = false)).isTrue()
        assertThat(player.features.bestiary.getCharmPoints()).isEqualTo(240)
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

    private fun insertPlayerColumn(playerId: Long, column: String, value: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE players SET $column = ? WHERE id = ?").use { stmt ->
                stmt.setInt(1, value)
                stmt.setLong(2, playerId)
                stmt.executeUpdate()
            }
        }
    }

    @Test
    fun `savePlayer persists stats spells and feature state`() {
        seedCharmPoints(101L, 55)
        val player = newPlayer()
        assertThat(loginData.loadPlayerByName(player, "Login Hero", disableIrrelevantInfo = false)).isTrue()
        player.drainHealth(40)
        player.position = Position(1010, 1020, 7)
        player.spellBook.learnInstantSpell("exura")

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(playersRepo.loadRecordByName("Login Hero")!!.health).isEqualTo(60)
        assertThat(loginData.getNameByGuid(101L)).isEqualTo("Login Hero")
        assertThat(JdbcPlayerSpellsRepository(dataSource).load(101L)).containsExactly("exura")
        assertThat(JdbcFeatureStateRepository(dataSource).load(101L)!!.charmPoints).isEqualTo(55)
    }

    @Test
    fun `savePlayer returns false when the player row does not exist`() {
        val unknown = newPlayer(id = 999_999L, name = "Unknown")

        assertThat(loginData.savePlayer(unknown)).isFalse()
    }

    @Test
    fun `increaseBankBalance delegates to the player row`() {
        loginData.increaseBankBalance(101L, 250L)
        loginData.increaseBankBalance(101L, 50L)

        assertThat(playersRepo.loadRecordByName("Login Hero")!!.balance).isEqualTo(300L)
    }

    @Test
    fun `getGuidByName formatPlayerName and getNameByGuid round trip`() {
        assertThat(loginData.getGuidByName("Login Hero")).isEqualTo(101L)
        assertThat(loginData.formatPlayerName("Login Hero")).isEqualTo("Login Hero")
        assertThat(loginData.getNameByGuid(101L)).isEqualTo("Login Hero")
        assertThat(loginData.getGuidByNameEx("Login Hero")!!.guid).isEqualTo(101L)
    }

    @Test
    fun `loadPlayerStorageMap ingests persisted rows`() {
        JdbcPlayerStorageRepository(dataSource).upsert(101L, mapOf(100 to 7, 200 to -3))
        val storage = newStorage()

        loginData.loadPlayerStorageMap(newPlayer(), storage)

        assertThat(storage.get(100)).isEqualTo(7)
        assertThat(storage.get(200)).isEqualTo(-3)
        assertThat(storage.getModifiedKeys()).isEmpty()
    }

    @Test
    fun `savePlayerStorage flushes upserts and deletions from the delta`() {
        val repository = JdbcPlayerStorageRepository(dataSource)
        val storage = newStorage()
        repository.upsert(101L, mapOf(100 to 7))
        loginData.loadPlayerStorageMap(newPlayer(), storage)
        storage.add(200, 9)
        storage.remove(100)

        assertThat(loginData.savePlayerStorage(newPlayer(), storage)).isTrue()

        assertThat(repository.load(101L)).containsExactly(
            org.opentibia.canary.creatures.players.components.PlayerStorageRow(key = 200, value = 9),
        )
        assertThat(storage.getModifiedKeys()).isEmpty()
        assertThat(storage.getRemovedKeys()).isEmpty()
    }

    @Test
    fun `savePlayerKills and loadPlayerKills round trip with the frag-time window`() {
        val now = 2_000_000L
        assertThat(
            loginData.savePlayerKills(
                101L,
                listOf(
                    Kill(target = 42L, time = now - 100L, unavenged = true),
                    Kill(target = 43L, time = now - 200_000L, unavenged = false),
                ),
            ),
        ).isTrue()

        val withinWindow = loginData.loadPlayerKills(101L, now = now, fragTime = 86_400L)

        assertThat(withinWindow).containsExactly(Kill(target = 42L, time = now - 100L, unavenged = true))
    }

    @Test
    fun `savePlayerStash and loadPlayerStashItems round trip`() {
        assertThat(loginData.savePlayerStash(101L, mapOf(2148 to 100, 2376 to 7))).isTrue()

        assertThat(loginData.loadPlayerStashItems(101L)).containsExactlyInAnyOrderEntriesOf(
            mapOf(2148 to 100, 2376 to 7),
        )
    }

    @Test
    fun `saveRewardItems and loadRewardItems round trip`() {
        val items = listOf(
            PlayerContainerItemState(pid = 0, sid = 101, itemId = 2160, count = 10),
            PlayerContainerItemState(
                pid = 101,
                sid = 102,
                itemId = 2376,
                attributes = mapOf(ItemAttribute.DATE to 1_700_000_000_000L),
            ),
        )

        assertThat(loginData.saveRewardItems(101L, items)).isTrue()

        assertThat(loginData.loadRewardItems(101L)).containsExactlyInAnyOrderElementsOf(items)
    }

    @Test
    fun `vip statics delegate to the vip repository`() {
        loginData.addVipEntry(1L, 102L, "buddy", 1, true)

        val entries = loginData.getVipEntries(1L)
        assertThat(entries).hasSize(1)
        assertThat(entries.single().guid).isEqualTo(102L)
        assertThat(entries.single().name).isEqualTo("Outsider")
        assertThat(entries.single().description).isEqualTo("buddy")

        loginData.removeVipEntry(1L, 102L)
        assertThat(loginData.getVipEntries(1L)).isEmpty()
    }

    @Test
    fun `vip group statics delegate to the vip repository`() {
        loginData.addVipGroupEntry(1, 1L, "Enemies", false)
        loginData.addGuidVipGroupEntry(1, 1L, 102L)

        val groups = loginData.getVipGroupEntries(1L)
        assertThat(groups).hasSize(1)
        assertThat(groups.single().vipGroupGuids).containsExactly(102L)

        loginData.editVipGroupEntry(1, 1L, "Friends", true)
        assertThat(loginData.getVipGroupEntries(1L).single().name).isEqualTo("Friends")

        loginData.removeGuidVipGroupEntry(1L, 102L)
        loginData.removeVipGroupEntry(1, 1L)
        assertThat(loginData.getVipGroupEntries(1L)).isEmpty()
    }

    @Test
    fun `inventory depot and inbox helpers round trip through their repositories`() {
        val items = listOf(org.opentibia.canary.database.PlayerItemState(slot = 3, itemId = 2148, count = 100))
        assertThat(loginData.savePlayerItem(101L, items)).isTrue()
        assertThat(loginData.loadPlayerInventoryItems(101L)).containsExactlyElementsOf(items)

        val depot = listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 1987))
        assertThat(loginData.savePlayerDepotItems(101L, depot)).isTrue()
        assertThat(loginData.loadPlayerDepotItems(101L)).containsExactlyElementsOf(depot)

        val inbox = listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 2160))
        assertThat(loginData.savePlayerInbox(101L, inbox)).isTrue()
        assertThat(loginData.loadPlayerInboxItems(101L)).containsExactlyElementsOf(inbox)
    }

    private fun newStorage(): PlayerStorage {
        val owner = object : StoragePlayer {
            override fun getName(): String = "Login Hero"

            override fun getOutfits(): MutableList<OutfitEntry> = mutableListOf()

            override fun getFamiliars(): MutableList<FamiliarEntry> = mutableListOf()
        }
        return PlayerStorage(owner)
    }
}
