package org.opentibia.canary.io.iologindata

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.CyclopediaMonsterTrackerClient
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.players.components.PlayerStorageRow
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
import org.opentibia.canary.database.PlayerSpellsRepository
import org.opentibia.canary.io.IOBestiaryPersistence
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.prey.IOPreyPersistence
import org.opentibia.canary.systems.prey.PlayerPreyState
import org.opentibia.canary.systems.prey.PreyBonus
import org.opentibia.canary.systems.prey.PreyDataState
import org.opentibia.canary.systems.prey.PreyOption
import org.opentibia.canary.systems.prey.PreySlot
import org.opentibia.canary.systems.rewardchest.Reward
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.taskhunting.PlayerTaskHuntingState
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot

/**
 * Transcript tests for `IOLoginData::savePlayerGuard`
 * (src/io/iologindata.cpp:213-267) calling the live walks through
 * `IOLoginData::savePlayer` (:195-211):
 *  - `IOLoginDataSave::savePlayerFirst` (iologindata_save_player.cpp:191-372);
 *  - `IOLoginDataSave::savePlayerStash` (:374-414) over
 *    `Player::getStashItems` (player.cpp:5437) with the decay/ware filters;
 *  - `IOLoginDataSave::savePlayerSpells` / `savePlayerKills` (:416-476);
 *  - `IOLoginDataSave::savePlayerBestiarySystem` (:478-532) over
 *    `Player::getCyclopediaMonsterTrackerSet(false)` (player.cpp:941-943);
 *  - `IOLoginDataSave::savePlayerItem` / `saveItems` inventory walk (:534-564);
 *  - `IOLoginDataSave::savePlayerDepotItems` (:566-597) over
 *    `Player::depotChests` (player.hpp:1673);
 *  - `IOLoginDataSave::saveRewardItems` (:599-632) over
 *    `Player::getRewardList` / `Player::getReward` (player.cpp:2067-2092) with
 *    the seven-day age window;
 *  - `IOLoginDataSave::savePlayerInbox` (:634-659) over
 *    `Player::getInbox()->getItemList`;
 *  - `IOLoginDataSave::savePlayerPreyClass` / `savePlayerTaskHuntingClass`
 *    (:661-775) over the `IOPreyPersistence` slot writes;
 *  - the `DBTransaction::executeWithinTransaction` scope of
 *    `IOLoginData::savePlayer` through [PlayerSaveTransaction].
 */
class IOLoginDataSaveGuardTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var itemTypes: ItemTypeRegistry
    private val depotChests = mutableMapOf<Int, Container>()

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_iologin_guard_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Save Guard Hero")

        itemTypes = ItemTypeRegistry().apply {
            register(ItemType(2376, "backpack", isContainer = true, capacity = 20, isMovable = true))
            register(ItemType(1987, "bag", isContainer = true, capacity = 8, isMovable = true))
            register(ItemType(2400, "magic sword", isMovable = true))
            register(ItemType(2160, "crystal coin", isStackable = true))
        }

        loginData = newLoginData(depotChests = { depotChests })
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newLoginData(
        depotChests: ((Player) -> Map<Int, Container>)? = null,
        transaction: PlayerSaveTransaction? = null,
        spells: PlayerSpellsRepository? = null,
    ): IOLoginData {
        val repositoryDataSource = transaction ?: dataSource
        return IOLoginData(
            players = JdbcPlayerRepository(repositoryDataSource),
            accounts = JdbcAccountRepository(repositoryDataSource),
            vip = JdbcVipRepository(repositoryDataSource),
            storageRepository = JdbcPlayerStorageRepository(repositoryDataSource),
            spells = spells ?: JdbcPlayerSpellsRepository(repositoryDataSource),
            kills = JdbcPlayerKillsRepository(repositoryDataSource),
            stash = JdbcPlayerStashRepository(repositoryDataSource),
            rewards = JdbcPlayerRewardsRepository(repositoryDataSource),
            items = JdbcPlayerItemsRepository(repositoryDataSource),
            depots = JdbcPlayerDepotRepository(repositoryDataSource),
            bestiaryPersistence = IOBestiaryPersistence(repositoryDataSource),
            preyPersistence = IOPreyPersistence(repositoryDataSource),
            itemTypes = itemTypes,
            depotChests = depotChests,
            saveTransaction = transaction,
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

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Save Guard Hero",
        maxHealth = 100,
        health = 100,
        capacity = 100_000.0,
        position = Position(1000, 1000, 7),
    )

    private fun item(id: Int, count: Int = 1): Item = Item.create(id, count, itemTypes)

    @Test
    fun `savePlayer writes the live inventory depot reward and inbox rows`() {
        val player = newPlayer()

        val backpack = item(2376) as Container
        val bag = item(1987) as Container
        bag.addItem(item(2160, count = 50))
        backpack.addItem(bag)
        assertThat(player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)).isEqualTo(ReturnValue.NOERROR)

        player.setLastDepotId(1)
        val depotChest = DepotChest(DepotChest.depotChestItemId(1))
        depotChest.addItem(item(2400))
        depotChests[1] = depotChest

        val rewardId = System.currentTimeMillis() - 1_000L
        val reward = player.getRewardChest().getReward(rewardId, autoCreate = true)!!
        val rewardBag = item(1987) as Container
        rewardBag.addItem(item(2160, count = 7))
        reward.addItem(rewardBag)

        player.inbox.addItem(item(2400))

        assertThat(loginData.savePlayer(player)).isTrue()

        val inventoryRows = loginData.loadPlayerInventoryItems(101L)
        assertThat(inventoryRows.map { it.sid }).containsExactly(103, 102, 101)
        assertThat(inventoryRows.map { it.pid }).containsExactly(102, 101, 3)
        assertThat(inventoryRows.map { it.itemId }).containsExactly(2160, 1987, 2376)
        assertThat(inventoryRows.single { it.sid == 103 }.count).isEqualTo(50)

        val depotRows = loginData.loadPlayerDepotItems(101L)
        assertThat(depotRows).containsExactly(
            PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400),
        )

        val rewardRows = loginData.loadRewardItems(101L)
        assertThat(rewardRows.map { it.sid }).containsExactly(101, 102, 103)
        assertThat(rewardRows.map { it.pid }).containsExactly(0, 101, 102)
        assertThat(rewardRows.map { it.itemId }).containsExactly(Reward.ITEM_REWARD_CONTAINER, 1987, 2160)
        assertThat(rewardRows.single { it.sid == 101 }.attributes[ItemAttribute.DATE]).isEqualTo(rewardId)

        val inboxRows = loginData.loadPlayerInboxItems(101L)
        assertThat(inboxRows).containsExactly(
            PlayerContainerItemState(pid = 0, sid = 101, itemId = 2400),
        )
    }

    @Test
    fun `savePlayer keeps fresh non-empty rewards and drops expired or empty ones`() {
        val player = newPlayer()
        val now = System.currentTimeMillis()
        val chest = player.getRewardChest()

        val fresh = chest.getReward(now - 1_000L, autoCreate = true)!!
        fresh.addItem(item(2400))
        chest.getReward(now - 2_000L, autoCreate = true)
        val expired = chest.getReward(now - 8 * 24 * 60 * 60 * 1_000L, autoCreate = true)!!
        expired.addItem(item(2160))

        assertThat(loginData.savePlayer(player)).isTrue()

        val rewardRows = loginData.loadRewardItems(101L)
        assertThat(rewardRows.map { it.sid }).containsExactly(101, 102)
        assertThat(rewardRows.map { it.itemId }).containsExactly(Reward.ITEM_REWARD_CONTAINER, 2400)
        assertThat(rewardRows.single { it.sid == 101 }.attributes[ItemAttribute.DATE]).isEqualTo(now - 1_000L)
    }

    @Test
    fun `savePlayer skips the depot save when the player never opened a depot`() {
        loginData.savePlayerDepotItems(101L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400)))
        val player = newPlayer()
        val depotChest = DepotChest(DepotChest.depotChestItemId(1))
        depotChest.addItem(item(1987))
        depotChests[1] = depotChest

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(loginData.loadPlayerDepotItems(101L)).containsExactly(
            PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400),
        )
    }

    @Test
    fun `savePlayer leaves depot rows untouched when the depot chest sink is unwired`() {
        loginData.savePlayerDepotItems(101L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400)))
        val unwired = newLoginData()
        val player = newPlayer()
        player.setLastDepotId(1)

        assertThat(unwired.savePlayer(player)).isTrue()

        assertThat(loginData.loadPlayerDepotItems(101L)).containsExactly(
            PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400),
        )
    }

    @Test
    fun `savePlayer clears the persisted inventory depot reward and inbox rows of an empty player`() {
        loginData.savePlayerItem(
            101L,
            listOf(org.opentibia.canary.database.PlayerItemState(pid = 3, sid = 101, itemId = 2376)),
        )
        loginData.savePlayerDepotItems(101L, listOf(PlayerContainerItemState(pid = 1, sid = 101, itemId = 2400)))
        loginData.saveRewardItems(101L, listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 19202)))
        loginData.savePlayerInbox(101L, listOf(PlayerContainerItemState(pid = 0, sid = 101, itemId = 2400)))
        loginData.savePlayerStash(101L, mapOf(2160 to 3))
        val player = newPlayer()
        player.setLastDepotId(1)
        depotChests[1] = DepotChest(DepotChest.depotChestItemId(1))

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(loginData.loadPlayerInventoryItems(101L)).isEmpty()
        assertThat(loginData.loadPlayerDepotItems(101L)).isEmpty()
        assertThat(loginData.loadRewardItems(101L)).isEmpty()
        assertThat(loginData.loadPlayerInboxItems(101L)).isEmpty()
        assertThat(loginData.loadPlayerStashItems(101L)).isEmpty()
    }

    @Test
    fun `savePlayer persists the live stash bestiary prey and task hunting state`() {
        val transaction = PlayerSaveTransaction(dataSource)
        val transactional = newLoginData(depotChests = { depotChests }, transaction = transaction)
        val player = newPlayer()

        player.stash.addItemOnStash(2160, 120)
        player.stash.addItemOnStash(2400, 3)

        val bestiary = player.features.bestiary
        bestiary.addBestiaryKillCount(56, 42)
        bestiary.setCharmPoints(70)
        bestiary.setMaxCharmPoints(140)
        bestiary.setUsedRunesBit(1 shl CharmRune.CHARM_WOUND.value)
        bestiary.setUnlockedRunesBit(1 shl CharmRune.CHARM_WOUND.value)
        bestiary.parseRacebyCharm(CharmRune.CHARM_WOUND, set = true, newRaceid = 56)
        bestiary.setCharmTier(CharmRune.CHARM_WOUND, 2)
        trackBestiaryMonster(player, raceId = 56)

        val now = System.currentTimeMillis()
        val prey = PreySlot(0)
        prey.state = PreyDataState.Active
        prey.selectedRaceId = 40
        prey.bonus = PreyBonus.Damage
        prey.bonusRarity = 3
        prey.bonusPercentage = 19
        prey.bonusTimeLeft = 3_600
        prey.freeRerollTimeStamp = now + 500
        prey.raceIdList.addAll(listOf(1, 258))
        player.features.prey.setPreySlotClass(prey)

        val task = TaskHuntingSlot(0)
        task.state = PreyTaskDataState.Active
        task.selectedRaceId = 40
        task.upgrade = true
        task.rarity = 5
        task.currentKills = 7
        task.disabledUntilTimeStamp = now + 100
        task.freeRerollTimeStamp = now + 200
        task.raceIdList.add(40)
        player.features.taskHunting.setTaskHuntingSlotClass(task)

        assertThat(transactional.savePlayer(player)).isTrue()

        assertThat(transactional.loadPlayerStashItems(101L))
            .containsExactlyInAnyOrderEntriesOf(mapOf(2160 to 120, 2400 to 3))

        val featureState = JdbcFeatureStateRepository(dataSource).load(101L)!!
        assertThat(featureState.charmPoints).isEqualTo(70)
        assertThat(featureState.maxCharmPoints).isEqualTo(140)
        assertThat(featureState.bestiaryKillCounts).containsExactlyEntriesOf(mapOf(56 to 42))
        assertThat(featureState.charmAssignments[CharmRune.CHARM_WOUND.value]!!.raceId).isEqualTo(56)
        assertThat(featureState.charmAssignments[CharmRune.CHARM_WOUND.value]!!.tier).isEqualTo(2)
        assertThat(IOBestiaryPersistence(dataSource).loadBestiaryTracker(101L)).containsExactly(56)

        val loadedPrey = PlayerPreyState()
        loadedPrey.isPremium = true
        IOPreyPersistence(dataSource).loadPlayerPreyClass(loadedPrey, 101L)
        val storedPrey = loadedPrey.getPreySlotById(0)
        assertThat(storedPrey).isNotNull
        assertThat(storedPrey!!.state).isEqualTo(PreyDataState.Active)
        assertThat(storedPrey.selectedRaceId).isEqualTo(40)
        assertThat(storedPrey.option).isEqualTo(PreyOption.None)
        assertThat(storedPrey.bonus).isEqualTo(PreyBonus.Damage)
        assertThat(storedPrey.bonusRarity).isEqualTo(3)
        assertThat(storedPrey.bonusPercentage).isEqualTo(19)
        assertThat(storedPrey.bonusTimeLeft).isEqualTo(3_600)
        assertThat(storedPrey.freeRerollTimeStamp).isEqualTo(now + 500)
        assertThat(storedPrey.raceIdList).containsExactly(1, 258)

        val loadedTask = PlayerTaskHuntingState()
        loadedTask.isPremium = true
        IOPreyPersistence(dataSource).loadPlayerTaskHuntingClass(loadedTask, 101L)
        val storedTask = loadedTask.getTaskHuntingSlotById(0)
        assertThat(storedTask).isNotNull
        assertThat(storedTask!!.state).isEqualTo(PreyTaskDataState.Active)
        assertThat(storedTask.selectedRaceId).isEqualTo(40)
        assertThat(storedTask.upgrade).isTrue()
        assertThat(storedTask.rarity).isEqualTo(5)
        assertThat(storedTask.currentKills).isEqualTo(7)
        assertThat(storedTask.disabledUntilTimeStamp).isEqualTo(now + 100)
        assertThat(storedTask.freeRerollTimeStamp).isEqualTo(now + 200)
        assertThat(storedTask.raceIdList).containsExactly(40)
    }

    @Test
    fun `savePlayer skips decaying and ware items in the live stash write`() {
        itemTypes.register(ItemType(2000, "torch", decayTo = 2001, decayTime = 6_000L))
        itemTypes.register(ItemType(2163, "market ware", wareId = 2164))
        val player = newPlayer()
        player.stash.addItemOnStash(2160, 5)
        player.stash.addItemOnStash(2000, 1)
        player.stash.addItemOnStash(2163, 2)

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(loginData.loadPlayerStashItems(101L)).containsExactlyInAnyOrderEntriesOf(mapOf(2160 to 5))
    }

    @Test
    fun `savePlayer rolls back the guard writes when a later save fails`() {
        val transaction = PlayerSaveTransaction(dataSource)
        val failing = newLoginData(
            depotChests = { depotChests },
            transaction = transaction,
            spells = ThrowingSpellsRepository,
        )
        val player = newPlayer()
        player.drainHealth(40)
        player.stash.addItemOnStash(2160, 9)

        assertThat(failing.savePlayer(player)).isFalse()

        assertThat(JdbcPlayerRepository(dataSource).loadRecordByName("Save Guard Hero")!!.health).isEqualTo(100)
        assertThat(failing.loadPlayerStashItems(101L)).isEmpty()
    }

    @Test
    fun `savePlayer persists the live storage delta through the guard`() {
        val player = newPlayer()
        player.features.storage.add(100, 7)
        player.features.storage.add(200, -3)

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(JdbcPlayerStorageRepository(dataSource).load(101L)).containsExactly(
            PlayerStorageRow(key = 100, value = 7),
            PlayerStorageRow(key = 200, value = -3),
        )
        assertThat(player.features.storage.getModifiedKeys()).isEmpty()
        assertThat(player.features.storage.getRemovedKeys()).isEmpty()
    }

    @Test
    fun `savePlayer persists the animus mastery blob and loadPlayer restores it`() {
        val player = newPlayer()
        player.features.animusMastery.add("Dragon")
        player.features.animusMastery.add("Rat")

        assertThat(loginData.savePlayer(player)).isTrue()

        assertThat(IOBestiaryPersistence(dataSource).loadAnimusMastery(101L))
            .containsExactly("dragon", "rat")

        val loaded = newPlayer()
        assertThat(loginData.loadPlayerByName(loaded, "Save Guard Hero", disableIrrelevantInfo = true)).isTrue()
        assertThat(loaded.features.animusMastery.getAnimusMasteries()).containsExactly("dragon", "rat")
        assertThat(loaded.features.animusMastery.has("Dragon")).isTrue()
    }

    private fun trackBestiaryMonster(player: Player, raceId: Int) {
        player.cyclopediaMonsterTrackerClient = object : CyclopediaMonsterTrackerClient {
            override fun parseSendBosstiary() = Unit

            override fun sendBestiaryEntryChanged(raceId: Int) = Unit

            override fun refreshCyclopediaMonsterTracker(tracker: Set<MonsterType>, isBoss: Boolean) = Unit
        }
        val monster = MonsterType("Demon")
        monster.info.raceid = raceId
        player.addMonsterToCyclopediaTrackerList(monster, isBoss = false)
    }

    /** `PlayerSpellsRepository` double whose `save` fails like a broken `player_spells` write. */
    private object ThrowingSpellsRepository : PlayerSpellsRepository {
        override fun load(playerId: Long): List<String> = emptyList()

        override fun save(playerId: Long, spellNames: List<String>) {
            throw IllegalStateException("simulated player_spells save failure")
        }
    }
}
