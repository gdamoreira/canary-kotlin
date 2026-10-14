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
import org.opentibia.canary.io.IOBestiaryPersistence
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.PlayerBestiaryState
import org.opentibia.canary.systems.prey.IOPreyPersistence
import org.opentibia.canary.systems.prey.PreyDataState
import org.opentibia.canary.systems.prey.PreySlot
import org.opentibia.canary.systems.taskhunting.PreyTaskDataState
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot
import org.opentibia.canary.utils.Tools

/**
 * Transcript tests for the wave-56 `IOLoginData::loadPlayer` sub-loads part 2
 * (src/io/iologindata.cpp:95-185 over
 * src/io/functions/iologindata_load_player.cpp):
 *
 *  - `loadPlayerBestiaryCharms` (iologindata_load_player.cpp:472-525)
 *  - `loadPlayerStoreInbox` (iologindata_load_player.cpp:634-643)
 *  - `loadRewardItems` plus `bindRewardBag`/`insertItemsIntoRewardBag`
 *    (iologindata_load_player.cpp:645-661, 953-990)
 *  - `loadPlayerPreyClass` (iologindata_load_player.cpp:808-853)
 *  - `loadPlayerTaskHuntingClass` (iologindata_load_player.cpp:855-903)
 */
class IOLoginDataSubloads2Test {

    private lateinit var dataSource: HikariDataSource
    private lateinit var loginData: IOLoginData
    private lateinit var bestiaryPersistence: IOBestiaryPersistence
    private lateinit var preyPersistence: IOPreyPersistence
    private lateinit var itemTypes: ItemTypeRegistry
    private val monstersByRace = mutableMapOf<Int, MonsterType>()
    private val fixedNow = 1_700_000_000_000L

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_subloads2_${System.nanoTime()}")
        DatabaseConnectionFactory.migrate(dataSource)
        insertPlayer(101L, "Subload Hero")

        itemTypes = ItemTypeRegistry().apply {
            register(ItemType(2376, "backpack", isContainer = true, capacity = 20, isMovable = true))
            register(ItemType(2160, "crystal coin", isStackable = true))
            register(ItemType(19202, "reward container", isContainer = true, capacity = 32))
            register(ItemType(23396, "store inbox", isContainer = true, capacity = 20))
        }

        bestiaryPersistence = IOBestiaryPersistence(dataSource)
        preyPersistence = IOPreyPersistence(dataSource, now = { fixedNow })

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
            bestiaryPersistence = bestiaryPersistence,
            preyPersistence = preyPersistence,
            monsterTypeByRaceId = { raceId, _ -> monstersByRace[raceId] },
        )
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun insertPlayer(id: Long, name: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO players (id, name, account_id) VALUES (?, ?, 1)",
            ).use { stmt ->
                stmt.setLong(1, id)
                stmt.setString(2, name)
                stmt.executeUpdate()
            }
        }
    }

    private fun newPlayer(): Player = Player(
        id = 101L,
        name = "Subload Hero",
        maxHealth = 1,
        health = 1,
        position = Position(1, 1, 7),
    )

    private class RecordingTrackerClient : CyclopediaMonsterTrackerClient {
        val refreshes = mutableListOf<Pair<Set<MonsterType>, Boolean>>()

        override fun parseSendBosstiary() = Unit

        override fun sendBestiaryEntryChanged(raceId: Int) = Unit

        override fun refreshCyclopediaMonsterTracker(tracker: Set<MonsterType>, isBoss: Boolean) {
            refreshes.add(tracker to isBoss)
        }
    }

    @Test
    fun `loadPlayerById loads the bestiary charms and applies the tracker list`() {
        val state = PlayerBestiaryState()
        state.setCharmPoints(240)
        state.setMinorCharmEchoes(7)
        state.setMaxCharmPoints(400)
        state.setMaxMinorCharmEchoes(20)
        state.hasCharmExpansion = true
        state.parseRacebyCharm(CharmRune.CHARM_WOUND, set = true, newRaceid = 42)
        state.setCharmTier(CharmRune.CHARM_WOUND, 3)
        bestiaryPersistence.savePlayerBestiarySystem(101L, state, tracker = listOf(42))

        val monster = MonsterType("Rat").apply { info.raceid = 42 }
        monstersByRace[42] = monster

        val client = RecordingTrackerClient()
        val player = newPlayer()
        player.cyclopediaMonsterTrackerClient = client

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()

        assertThat(player.features.bestiary.getCharmPoints()).isEqualTo(240)
        assertThat(player.features.bestiary.getMinorCharmEchoes()).isEqualTo(7)
        assertThat(player.features.bestiary.getMaxCharmPoints()).isEqualTo(400)
        assertThat(player.features.bestiary.getMaxMinorCharmEchoes()).isEqualTo(20)
        assertThat(player.features.bestiary.hasCharmExpansion).isTrue()
        assertThat(player.features.bestiary.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(42)
        assertThat(player.features.bestiary.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(3)

        assertThat(player.getCyclopediaMonsterTrackerSet(false)).containsExactly(monster)
        assertThat(client.refreshes).hasSize(1)
    }

    @Test
    fun `loadPlayerById drops tracker race ids the monster resolver does not know`() {
        bestiaryPersistence.saveBestiaryTracker(101L, listOf(42, 99))

        val monster = MonsterType("Rat").apply { info.raceid = 42 }
        monstersByRace[42] = monster

        val player = newPlayer()
        player.cyclopediaMonsterTrackerClient = RecordingTrackerClient()

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()

        assertThat(player.getCyclopediaMonsterTrackerSet(false)).containsExactly(monster)
    }

    @Test
    fun `loadPlayerById creates the store inbox item when the slot is empty`() {
        val player = newPlayer()

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()

        val storeInbox = player.getInventoryItem(Slots_t.CONST_SLOT_STORE_INBOX)
        assertThat(storeInbox).isNotNull
        assertThat(storeInbox!!.id).isEqualTo(Container.ITEM_STORE_INBOX)
    }

    @Test
    fun `loadPlayerById keeps an already materialized store inbox`() {
        val existing = Item.create(2160, count = 1, registry = itemTypes)
        val player = newPlayer()
        player.internalAddThing(Slots_t.CONST_SLOT_STORE_INBOX.value, existing)

        assertThat(loginData.loadPlayerById(player, 101L)).isTrue()

        assertThat(player.getInventoryItem(Slots_t.CONST_SLOT_STORE_INBOX)).isSameAs(existing)
    }

    @Test
    fun `reward chest items round-trip through loadPlayerById in slot order`() {
        val rewardId = Tools.getTimeMsNow() - 1_000
        val source = newPlayer()
        val reward = source.getRewardChest().getReward(rewardId, autoCreate = true)!!
        reward.addItem(Item.create(2160, count = 50, registry = itemTypes))
        reward.addItem(Item.create(2376, registry = itemTypes))
        assertThat(loginData.saveRewardItems(source)).isTrue()

        val loaded = newPlayer()
        assertThat(loginData.loadPlayerById(loaded, 101L)).isTrue()

        val loadedChest = loaded.getRewardChest()
        assertThat(loadedChest.getRewardList()).containsExactly(rewardId)
        val loadedReward = loadedChest.getReward(rewardId, autoCreate = false)
        assertThat(loadedReward).isNotNull
        assertThat(loadedReward!!.items.map { it.id }).containsExactly(2160, 2376)
        assertThat(loadedReward.items.first().getSubType()).isEqualTo(50)
    }

    @Test
    fun `nested reward containers round-trip through loadPlayerById`() {
        val rewardId = Tools.getTimeMsNow() - 1_000
        val source = newPlayer()
        val reward = source.getRewardChest().getReward(rewardId, autoCreate = true)!!
        val backpack = Item.create(2376, registry = itemTypes) as Container
        backpack.internalAddThing(Item.create(2160, count = 25, registry = itemTypes))
        reward.addItem(backpack)
        assertThat(loginData.saveRewardItems(source)).isTrue()

        val loaded = newPlayer()
        assertThat(loginData.loadPlayerById(loaded, 101L)).isTrue()

        val loadedReward = loaded.getRewardChest().getReward(rewardId, autoCreate = false)
        assertThat(loadedReward).isNotNull
        val loadedBackpack = loadedReward!!.items.single()
        assertThat(loadedBackpack.id).isEqualTo(2376)
        assertThat((loadedBackpack as Container).getItemList().single().getSubType()).isEqualTo(25)
    }

    @Test
    fun `prey slots round-trip through loadPlayerById`() {
        val source = newPlayer()
        val slot = PreySlot(0, now = { fixedNow })
        slot.state = PreyDataState.Active
        slot.selectedRaceId = 40
        slot.bonusTimeLeft = 3600
        slot.raceIdList.addAll(listOf(1, 258))
        source.features.prey.setPreySlotClass(slot)
        assertThat(loginData.savePlayerPreyClass(source)).isTrue()

        val loaded = newPlayer()
        assertThat(loginData.loadPlayerById(loaded, 101L)).isTrue()

        val stored = loaded.features.prey.getPreySlotById(0)
        assertThat(stored).isNotNull
        assertThat(stored!!.state).isEqualTo(PreyDataState.Active)
        assertThat(stored.selectedRaceId).isEqualTo(40)
        assertThat(stored.bonusTimeLeft).isEqualTo(3600)
        assertThat(stored.raceIdList).containsExactly(1, 258)
    }

    @Test
    fun `loadPlayerById unlocks the stored locked prey slot two for a premium player`() {
        val source = newPlayer()
        source.features.prey.setPreySlotClass(PreySlot(1, now = { fixedNow }).apply { state = PreyDataState.Locked })
        assertThat(loginData.savePlayerPreyClass(source)).isTrue()

        val loaded = newPlayer()
        loaded.premium = true
        assertThat(loginData.loadPlayerById(loaded, 101L)).isTrue()

        assertThat(loaded.features.prey.getPreySlotById(1)?.state).isEqualTo(PreyDataState.Selection)
    }

    @Test
    fun `task hunting slots round-trip through loadPlayerById`() {
        val source = newPlayer()
        val slot = TaskHuntingSlot(0, now = { fixedNow })
        slot.state = PreyTaskDataState.Active
        slot.selectedRaceId = 40
        slot.upgrade = true
        slot.rarity = 4
        slot.currentKills = 12
        slot.raceIdList.addAll(listOf(1, 258))
        source.features.taskHunting.setTaskHuntingSlotClass(slot)
        assertThat(loginData.savePlayerTaskHuntingClass(source)).isTrue()

        val loaded = newPlayer()
        assertThat(loginData.loadPlayerById(loaded, 101L)).isTrue()

        val stored = loaded.features.taskHunting.getTaskHuntingSlotById(0)
        assertThat(stored).isNotNull
        assertThat(stored!!.state).isEqualTo(PreyTaskDataState.Active)
        assertThat(stored.selectedRaceId).isEqualTo(40)
        assertThat(stored.upgrade).isTrue()
        assertThat(stored.rarity).isEqualTo(4)
        assertThat(stored.currentKills).isEqualTo(12)
        assertThat(stored.raceIdList).containsExactly(1, 258)
    }

    @Test
    fun `loadPlayerById unlocks the stored locked task hunting slot two for a premium player`() {
        val source = newPlayer()
        source.features.taskHunting.setTaskHuntingSlotClass(
            TaskHuntingSlot(1, now = { fixedNow }).apply { state = PreyTaskDataState.Locked },
        )
        assertThat(loginData.savePlayerTaskHuntingClass(source)).isTrue()

        val loaded = newPlayer()
        loaded.premium = true
        assertThat(loginData.loadPlayerById(loaded, 101L)).isTrue()

        assertThat(loaded.features.taskHunting.getTaskHuntingSlotById(1)?.state)
            .isEqualTo(PreyTaskDataState.Selection)
    }
}
