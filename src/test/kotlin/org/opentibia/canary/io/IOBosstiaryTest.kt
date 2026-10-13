package org.opentibia.canary.io

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.monsters.BosstiaryRarity_t
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.database.DatabaseConnectionFactory

/**
 * H2 (Flyway V1-V13) round-trip tests for [IOBosstiary], transcribed from
 * Canary's bosstiary persistence and progression rules:
 *
 *  - `IOBosstiary::loadBoostedBoss` (src/io/io_bosstiary.cpp:22-124) against the
 *    `boosted_boss` table (schema.sql:266-279) and the slot resets on
 *    `player_bosstiary` (schema.sql:755-766)
 *  - `IOBosstiary::addBosstiaryMonster` / `getBosstiaryMap` / boosted accessors
 *    (io_bosstiary.cpp:126-171)
 *  - `IOBosstiary::getBossCurrentLevel` (io_bosstiary.cpp:275-301) over the
 *    `levelInfos` thresholds (io_bosstiary.hpp:44-48)
 *  - `IOBosstiary::calculateLootBonus` / `calculateBossPoints`
 *    (io_bosstiary.cpp:217-239), `calculteRemoveBoss` (io_bosstiary.cpp:303-308)
 *  - `IOLoginDataLoad::loadPlayerBosstiary` /
 *    `IOLoginDataSave::savePlayerBosstiary`
 *    (iologindata_load_player.cpp:916-951, iologindata_save_player.cpp:786-835)
 *  - `players.boss_points` load/save (iologindata_load_player.cpp:175,
 *    iologindata_save_player.cpp:264)
 */
class IOBosstiaryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var monsters: Monsters

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_io_bosstiary_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        monsters = Monsters()
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun newBosstiary(
        randomIndex: (Int) -> Int = { 0 },
        today: Int = 15,
    ): IOBosstiary = IOBosstiary(
        dataSource = dataSource,
        monsters = monsters,
        randomIndex = randomIndex,
        todayProvider = { today },
    )

    private fun registerBoss(name: String, raceId: Int, rarity: BosstiaryRarity_t, lookType: Int = 136): MonsterType {
        val mtype = MonsterType(name)
        mtype.info.raceid = raceId
        mtype.info.bosstiaryRace = rarity
        mtype.info.outfit.lookType = lookType
        mtype.info.outfit.lookHead = 1
        mtype.info.outfit.lookBody = 2
        mtype.info.outfit.lookLegs = 3
        mtype.info.outfit.lookFeet = 4
        mtype.info.outfit.lookAddons = 5
        mtype.info.outfit.lookMount = 6
        monsters.tryAddMonsterType(name, mtype)
        return mtype
    }

    private fun seedBoostedBoss(date: String, name: String, raceId: String) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "UPDATE boosted_boss SET \"date\" = ?, boostname = ?, raceid = ?",
            ).use { stmt ->
                stmt.setString(1, date)
                stmt.setString(2, name)
                stmt.setString(3, raceId)
                stmt.executeUpdate()
            }
        }
    }

    private fun storedBoostedBoss(): Triple<String, String?, String?> = dataSource.connection.use { conn ->
        conn.prepareStatement(
            "SELECT \"date\", boostname, raceid FROM boosted_boss",
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                assertThat(rs.next()).isTrue()
                Triple(
                    rs.getString("date"),
                    rs.getString("boostname"),
                    rs.getString("raceid"),
                )
            }
        }
    }

    private fun storedBoostedOutfit(): List<Int> = dataSource.connection.use { conn ->
        conn.prepareStatement(
            "SELECT looktype, lookhead, lookbody, looklegs, lookfeet, lookaddons, lookmount FROM boosted_boss",
        ).use { stmt ->
            stmt.executeQuery().use { rs ->
                assertThat(rs.next()).isTrue()
                listOf(
                    rs.getInt("looktype"),
                    rs.getInt("lookhead"),
                    rs.getInt("lookbody"),
                    rs.getInt("looklegs"),
                    rs.getInt("lookfeet"),
                    rs.getInt("lookaddons"),
                    rs.getInt("lookmount"),
                )
            }
        }
    }

    private fun seedBosstiaryRow(playerId: Long, slotOne: Int, slotTwo: Int, tracker: ByteArray?) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO player_bosstiary (player_id, boss_id_slot_one, boss_id_slot_two, remove_times, tracker) VALUES (?, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, slotOne)
                stmt.setInt(3, slotTwo)
                stmt.setInt(4, 1)
                stmt.setBytes(5, tracker)
                stmt.executeUpdate()
            }
        }
    }

    private fun storedSlots(playerId: Long): Pair<Int, Int> = dataSource.connection.use { conn ->
        conn.prepareStatement(
            "SELECT boss_id_slot_one, boss_id_slot_two FROM player_bosstiary WHERE player_id = ?",
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                assertThat(rs.next()).isTrue()
                rs.getInt("boss_id_slot_one") to rs.getInt("boss_id_slot_two")
            }
        }
    }

    @Test
    fun `level infos match the canary boss race kill stages`() {
        val bosstiary = newBosstiary()

        assertThat(bosstiary.levelInfos[BosstiaryRarity_t.RARITY_BANE])
            .containsExactly(LevelInfo(25, 5), LevelInfo(100, 15), LevelInfo(300, 30))
        assertThat(bosstiary.levelInfos[BosstiaryRarity_t.RARITY_ARCHFOE])
            .containsExactly(LevelInfo(5, 10), LevelInfo(20, 30), LevelInfo(60, 60))
        assertThat(bosstiary.levelInfos[BosstiaryRarity_t.RARITY_NEMESIS])
            .containsExactly(LevelInfo(1, 10), LevelInfo(3, 30), LevelInfo(5, 60))
    }

    @Test
    fun `addBosstiaryMonster keeps the first registration and getBossRaceKillStages serves thresholds`() {
        val bosstiary = newBosstiary()
        registerBoss("Drume", 50, BosstiaryRarity_t.RARITY_BANE)

        bosstiary.addBosstiaryMonster(50, "Drume")
        bosstiary.addBosstiaryMonster(50, "Not Drume")

        assertThat(bosstiary.getBosstiaryMap()).containsExactlyEntriesOf(mapOf(50 to "Drume"))
        assertThat(bosstiary.getBossRaceKillStages(BosstiaryRarity_t.RARITY_BANE))
            .containsExactly(LevelInfo(25, 5), LevelInfo(100, 15), LevelInfo(300, 30))
        assertThat(bosstiary.getBossRaceKillStages(BosstiaryRarity_t.BOSS_INVALID)).isEmpty()
    }

    @Test
    fun `getBossCurrentLevel counts the reached kill stages`() {
        val bosstiary = newBosstiary()
        registerBoss("Drume", 50, BosstiaryRarity_t.RARITY_BANE)
        registerBoss("Scarlett Etzel", 100, BosstiaryRarity_t.RARITY_ARCHFOE)
        bosstiary.addBosstiaryMonster(50, "Drume")
        bosstiary.addBosstiaryMonster(100, "Scarlett Etzel")

        assertThat(bosstiary.getBossCurrentLevel(0, 999)).isZero()
        assertThat(bosstiary.getBossCurrentLevel(999, 999)).isZero()
        assertThat(bosstiary.getBossCurrentLevel(50, 0)).isZero()
        assertThat(bosstiary.getBossCurrentLevel(50, 24)).isZero()
        assertThat(bosstiary.getBossCurrentLevel(50, 25)).isEqualTo(1)
        assertThat(bosstiary.getBossCurrentLevel(50, 100)).isEqualTo(2)
        assertThat(bosstiary.getBossCurrentLevel(50, 300)).isEqualTo(3)
        assertThat(bosstiary.getBossCurrentLevel(100, 5)).isEqualTo(1)
        assertThat(bosstiary.getBossCurrentLevel(100, 20)).isEqualTo(2)
        assertThat(bosstiary.getBossCurrentLevel(100, 60)).isEqualTo(3)
    }

    @Test
    fun `calculateLootBonus transcribes the canary piecewise formula`() {
        val bosstiary = newBosstiary()

        assertThat(bosstiary.calculateLootBonus(0)).isEqualTo(25)
        assertThat(bosstiary.calculateLootBonus(100)).isEqualTo(35)
        assertThat(bosstiary.calculateLootBonus(250)).isEqualTo(50)
        assertThat(bosstiary.calculateLootBonus(251)).isEqualTo(49)
        assertThat(bosstiary.calculateLootBonus(1249)).isEqualTo(99)
        assertThat(bosstiary.calculateLootBonus(1250)).isEqualTo(100)
        assertThat(bosstiary.calculateLootBonus(1500)).isEqualTo(106)
    }

    @Test
    fun `calculateBossPoints transcribes the canary piecewise formula`() {
        val bosstiary = newBosstiary()

        assertThat(bosstiary.calculateBossPoints(25)).isZero()
        assertThat(bosstiary.calculateBossPoints(26)).isEqualTo(10)
        assertThat(bosstiary.calculateBossPoints(50)).isEqualTo(250)
        assertThat(bosstiary.calculateBossPoints(51)).isEqualTo(270)
        assertThat(bosstiary.calculateBossPoints(100)).isEqualTo(1250)
        assertThat(bosstiary.calculateBossPoints(101)).isEqualTo(1275)
        assertThat(bosstiary.calculateBossPoints(200)).isEqualTo(28500)
    }

    @Test
    fun `calculteRemoveBoss transcribes the canary fee formula`() {
        val bosstiary = newBosstiary()

        assertThat(bosstiary.calculteRemoveBoss(0)).isZero()
        assertThat(bosstiary.calculteRemoveBoss(1)).isZero()
        assertThat(bosstiary.calculteRemoveBoss(2)).isEqualTo(100000)
        assertThat(bosstiary.calculteRemoveBoss(3)).isEqualTo(400000)
    }

    @Test
    fun `getBosstiaryFinished filters by kill stage and orders ids`() {
        val bosstiary = newBosstiary()
        registerBoss("Drume", 50, BosstiaryRarity_t.RARITY_BANE)
        registerBoss("Scarlett Etzel", 100, BosstiaryRarity_t.RARITY_ARCHFOE)
        bosstiary.addBosstiaryMonster(100, "Scarlett Etzel")
        bosstiary.addBosstiaryMonster(50, "Drume")
        val kills = mapOf(50 to 25, 100 to 60)

        assertThat(bosstiary.getBosstiaryFinished({ id -> kills[id] ?: 0 })).containsExactly(50, 100)
        assertThat(bosstiary.getBosstiaryFinished({ id -> kills[id] ?: 0 }, level = 3)).containsExactly(100)
    }

    @Test
    fun `loadPlayerBosstiary returns the canary defaults when the row is missing`() {
        val bosstiary = newBosstiary()

        val state = bosstiary.loadPlayerBosstiary(2)

        assertThat(state).isEqualTo(PlayerBosstiaryState())
        assertThat(state.removeTimes).isEqualTo(1)
    }

    @Test
    fun `savePlayerBosstiary replaces slots removeTimes and the tracker blob`() {
        val bosstiary = newBosstiary()

        bosstiary.savePlayerBosstiary(1, PlayerBosstiaryState(bossIdSlotOne = 12, bossIdSlotTwo = 34, removeTimes = 3, tracker = listOf(100, 200)))
        assertThat(bosstiary.loadPlayerBosstiary(1))
            .isEqualTo(PlayerBosstiaryState(12, 34, 3, listOf(100, 200)))

        bosstiary.savePlayerBosstiary(1, PlayerBosstiaryState(bossIdSlotOne = 7, tracker = listOf(300)))
        assertThat(bosstiary.loadPlayerBosstiary(1))
            .isEqualTo(PlayerBosstiaryState(7, 0, 1, listOf(300)))
    }

    @Test
    fun `boss points round-trip through the players row`() {
        val bosstiary = newBosstiary()

        assertThat(bosstiary.loadBossPoints(2)).isZero()
        bosstiary.saveBossPoints(2, 15000)
        assertThat(bosstiary.loadBossPoints(2)).isEqualTo(15000)
        bosstiary.saveBossPoints(2, 0)
        assertThat(bosstiary.loadBossPoints(2)).isZero()
    }

    @Test
    fun `loadBoostedBoss keeps the boosted boss of the same day`() {
        registerBoss("Drume", 50, BosstiaryRarity_t.RARITY_BANE)
        registerBoss("Scarlett Etzel", 100, BosstiaryRarity_t.RARITY_ARCHFOE, lookType = 1234)
        seedBoostedBoss(date = "15", name = "Drume", raceId = "50")
        val bosstiary = newBosstiary()
        bosstiary.addBosstiaryMonster(50, "Drume")
        bosstiary.addBosstiaryMonster(100, "Scarlett Etzel")

        bosstiary.loadBoostedBoss()

        assertThat(bosstiary.getBoostedBossName()).isEqualTo("Drume")
        assertThat(bosstiary.getBoostedBossId()).isEqualTo(50)
        assertThat(storedBoostedBoss().first).isEqualTo("15")
        assertThat(storedBoostedOutfit()).isEqualTo(listOf(136, 0, 0, 0, 0, 0, 0))
    }

    @Test
    fun `loadBoostedBoss picks an archfoe and resets the selected player slots`() {
        registerBoss("Drume", 50, BosstiaryRarity_t.RARITY_BANE, lookType = 111)
        registerBoss("Scarlett Etzel", 100, BosstiaryRarity_t.RARITY_ARCHFOE, lookType = 1234)
        seedBoostedBoss(date = "5", name = "Drume", raceId = "50")
        seedBosstiaryRow(1, slotOne = 100, slotTwo = 50, tracker = null)
        seedBosstiaryRow(2, slotOne = 55, slotTwo = 100, tracker = null)
        val bosstiary = newBosstiary(randomIndex = { 0 })
        bosstiary.addBosstiaryMonster(50, "Drume")
        bosstiary.addBosstiaryMonster(100, "Scarlett Etzel")

        bosstiary.loadBoostedBoss()

        assertThat(bosstiary.getBoostedBossName()).isEqualTo("Scarlett Etzel")
        assertThat(bosstiary.getBoostedBossId()).isEqualTo(100)
        val (date, name, raceId) = storedBoostedBoss()
        assertThat(date).isEqualTo("15")
        assertThat(name).isEqualTo("Scarlett Etzel")
        assertThat(raceId).isEqualTo("100")
        assertThat(storedBoostedOutfit()).isEqualTo(listOf(1234, 1, 2, 3, 4, 5, 6))
        assertThat(storedSlots(1)).isEqualTo(0 to 50)
        assertThat(storedSlots(2)).isEqualTo(55 to 0)
    }

    @Test
    fun `loadBoostedBoss keeps the row when no archfoe is registered`() {
        registerBoss("Drume", 50, BosstiaryRarity_t.RARITY_BANE)
        seedBoostedBoss(date = "5", name = "Drume", raceId = "50")
        val bosstiary = newBosstiary()
        bosstiary.addBosstiaryMonster(50, "Drume")
        bosstiary.addBosstiaryMonster(100, "Scarlett Etzel")

        bosstiary.loadBoostedBoss()

        assertThat(bosstiary.getBoostedBossName()).isEmpty()
        assertThat(bosstiary.getBoostedBossId()).isZero()
        assertThat(storedBoostedBoss().first).isEqualTo("5")
    }
}
