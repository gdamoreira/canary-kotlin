package org.opentibia.canary.creatures.players.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.social.guild.GuildRank
import org.opentibia.canary.systems.mount.Mount

private const val GOLD_ID = 1
private const val MOUNT_ID = 4
private const val LEVEL_ID = 14
private const val BESTIARY_ID = 35
private const val BOSS_ID = 56
private const val DAILY_REWARD_ID = 65
private const val TASK_ID = 70
private const val MAP_ID = 73
private const val GUILD_LEADER_ID = 75
private const val LYCANTHROPE_RACE = 16
private const val BOSS_RACE = 30

private val GOLD_HOARDER = Title(
    id = GOLD_ID,
    type = CyclopediaTitle_t.GOLD,
    maleName = "Gold Hoarder",
    description = "Earned at least 1,000,000 gold.",
    amount = 1_000_000,
)

private val BEASTSTRIDER = Title(
    id = MOUNT_ID,
    type = CyclopediaTitle_t.MOUNTS,
    maleName = "Beaststrider (Grade 1)",
    description = "Unlocked 10 or more Mounts.",
    amount = 10,
    permanent = true,
)

private val TROLLTRASHER = Title(
    id = LEVEL_ID,
    type = CyclopediaTitle_t.LEVEL,
    maleName = "Trolltrasher",
    description = "Reached level 50.",
    amount = 50,
)

private val BLOOD_MOON_HUNTER = Title(
    id = BESTIARY_ID,
    type = CyclopediaTitle_t.BESTIARY,
    maleName = "Blood Moon Hunter",
    femaleName = "Blood Moon Huntress",
    description = "Unlocked All Lycanthrope Bestiary entries.",
    race = LYCANTHROPE_RACE,
)

private val BOSS_DESTROYER = Title(
    id = BOSS_ID,
    type = CyclopediaTitle_t.BOSSTIARY,
    maleName = "Boss Destroyer",
    description = "Unlocked 10 or more Archfoe bosses.",
    amount = 10,
    permanent = true,
    race = BOSS_RACE,
)

private val CREATURE_OF_HABIT = Title(
    id = DAILY_REWARD_ID,
    type = CyclopediaTitle_t.DAILY_REWARD,
    maleName = "Creature of Habit (Grade 1)",
    description = "Reward Streak of at least 7 days of consecutive logins.",
    amount = 7,
    permanent = true,
)

private val ASPIRING_HUNTSMAN = Title(
    id = TASK_ID,
    type = CyclopediaTitle_t.TASK,
    maleName = "Aspiring Huntsman",
    femaleName = "Aspiring Huntswoman",
    description = "Invested 160,000 tasks points.",
    amount = 160_000,
    permanent = true,
)

private val DEDICATED_ENTREPRENEUR = Title(
    id = MAP_ID,
    type = CyclopediaTitle_t.MAP,
    maleName = "Dedicated Entrepreneur",
    description = "Explored 50% of all the map areas.",
    amount = 50,
)

private val GUILD_LEADER = Title(
    id = GUILD_LEADER_ID,
    type = CyclopediaTitle_t.OTHERS,
    maleName = "Guild Leader",
    description = "Leading a Guild.",
    permanent = true,
)

private val ALL_TITLES = listOf(
    GOLD_HOARDER,
    BEASTSTRIDER,
    TROLLTRASHER,
    BLOOD_MOON_HUNTER,
    BOSS_DESTROYER,
    CREATURE_OF_HABIT,
    ASPIRING_HUNTSMAN,
    DEDICATED_ENTREPRENEUR,
    GUILD_LEADER,
)

private fun mount(id: Int): Mount = Mount(
    id = id,
    clientId = id,
    name = "Mount $id",
    speed = 0,
    premium = false,
    type = "tameable",
)

/**
 * Transcript tests for the Canary `PlayerTitle` component.
 *
 * Source of truth: ../.projects/canary
 *  - src/creatures/players/components/player_title.hpp:19-101
 *  - src/creatures/players/components/player_title.cpp:20-329
 *  - src/game/game.cpp:12566-12594 (`Game::getTitleById/getTitleByName/getTitles`)
 *  - src/game/game.cpp:599-703 (title registry data)
 *  - src/creatures/players/player.cpp:10527-10532 (`isCreatureUnlockedOnTaskHunting`)
 */
class PlayerTitlesTest {

    private class FakeTitlePlayer : TitlePlayer {
        var playerName: String = "Knight"
        var playerSex: PlayerSex_t = PlayerSex_t.MALE
        var playerBankBalance: Long = 0
        var playerLevel: Int = 1
        var guid: Long = 100
        var playerTaskHuntingPoints: Long = 0
        var playerCharmPoints: Int = 0
        var playerBossPoints: Int = 0
        var playerGuildRank: GuildRank? = null

        val mounts = mutableSetOf<Int>()
        val bestiaryKillCounts = mutableMapOf<Int, Int>()
        val wearable = mutableSetOf<Pair<Int, Int>>()
        val outfits = mutableListOf<Int>()

        override fun getName(): String = playerName
        override fun getSex(): PlayerSex_t = playerSex
        override fun getBankBalance(): Long = playerBankBalance
        override fun hasMount(mount: Mount): Boolean = mount.id in mounts
        override fun getOutfitsCount(): Int = outfits.size
        override fun getLevel(): Int = playerLevel
        override fun getGUID(): Long = guid
        override fun getBestiaryKillCount(raceid: Int): Int = bestiaryKillCounts[raceid] ?: 0
        override fun getTaskHuntingPoints(): Long = playerTaskHuntingPoints
        override fun getCharmPoints(): Int = playerCharmPoints
        override fun getBossPoints(): Int = playerBossPoints
        override fun canWear(lookType: Int, addons: Int): Boolean = (lookType to addons) in wearable
        override fun getGuildRank(): GuildRank? = playerGuildRank
    }

    private class Fixture(
        titles: List<Title> = ALL_TITLES,
    ) {
        val player = FakeTitlePlayer()
        val kv = InMemoryKV()
        val registry = InMemoryTitleRegistry(titles)
        val mounts = mutableListOf<Mount>()
        val monsters = mutableMapOf<Pair<Int, Boolean>, MonsterTypeInfo>()
        val topPlayerBySkill = mutableMapOf<Int, Long>()
        val highscoreQueries = mutableListOf<Int>()
        val debugLogs = mutableListOf<String>()
        val errorLogs = mutableListOf<String>()
        var nowSeconds = 1_700_000_000L

        val title = PlayerTitle(
            player = player,
            kv = kv,
            registry = registry,
            getMounts = { mounts },
            getMonsterTypeByRaceId = { raceId, isBoss -> monsters[raceId to isBoss] },
            highscoreRepository = TitleHighscoreRepository { skill ->
                highscoreQueries.add(skill)
                topPlayerBySkill[skill]
            },
            now = { nowSeconds },
            logDebug = { debugLogs.add(it) },
            logError = { errorLogs.add(it) },
        )
    }

    @Test
    fun `isTitleUnlocked returns false for id zero`() {
        val f = Fixture()

        assertThat(f.title.isTitleUnlocked(0)).isFalse()
    }

    @Test
    fun `manage refuses an unknown title id`() {
        val f = Fixture()

        assertThat(f.title.manage(true, 200)).isFalse()
        assertThat(f.title.getUnlockedTitles()).isEmpty()
    }

    @Test
    fun `manage unlocks a known title with the current timestamp and persists it`() {
        val f = Fixture()

        assertThat(f.title.manage(true, GOLD_ID)).isTrue()

        assertThat(f.title.isTitleUnlocked(GOLD_ID)).isTrue()
        assertThat(f.title.getUnlockedTitles()).containsExactly(GOLD_HOARDER to 1_700_000_000L)
        assertThat(f.title.getUnlockedKV().get("Gold Hoarder")).isEqualTo(1_700_000_000L)
        assertThat(f.debugLogs).containsExactly("[manage] - Added title: Gold Hoarder")
    }

    @Test
    fun `manage uses the caller timestamp when nonzero`() {
        val f = Fixture()

        assertThat(f.title.manage(true, GOLD_ID, timestamp = 1_234L)).isTrue()

        assertThat(f.title.getUnlockedKV().get("Gold Hoarder")).isEqualTo(1_234L)
    }

    @Test
    fun `manage refuses a title that is already unlocked`() {
        val f = Fixture()
        f.title.manage(true, GOLD_ID)
        f.kv.scoped("titles").scoped("unlocked").set("Gold Hoarder", 999L)

        assertThat(f.title.manage(true, GOLD_ID)).isFalse()

        assertThat(f.title.getUnlockedTitles()).hasSize(1)
        assertThat(f.title.getUnlockedKV().get("Gold Hoarder")).isEqualTo(999L)
    }

    @Test
    fun `manage with canAdd false removes a non-permanent title`() {
        val f = Fixture()
        f.title.manage(true, GOLD_ID)

        assertThat(f.title.manage(false, GOLD_ID)).isFalse()

        assertThat(f.title.isTitleUnlocked(GOLD_ID)).isFalse()
        assertThat(f.title.getUnlockedKV().get("Gold Hoarder")).isNull()
        assertThat(f.debugLogs).contains("[remove] - Removed title: Gold Hoarder")
    }

    @Test
    fun `manage with canAdd false keeps a permanent title`() {
        val f = Fixture()
        f.title.manage(true, MOUNT_ID)

        assertThat(f.title.manage(false, MOUNT_ID)).isFalse()

        assertThat(f.title.isTitleUnlocked(MOUNT_ID)).isTrue()
    }

    @Test
    fun `manage with canAdd false does nothing for a locked title`() {
        val f = Fixture()

        assertThat(f.title.manage(false, GOLD_ID)).isFalse()

        assertThat(f.title.getUnlockedKV().keys()).isEmpty()
    }

    @Test
    fun `remove erases the unlocked entry and its KV key`() {
        val f = Fixture()
        f.title.manage(true, GOLD_ID)

        f.title.remove(GOLD_HOARDER)

        assertThat(f.title.isTitleUnlocked(GOLD_ID)).isFalse()
        assertThat(f.title.getUnlockedKV().get("Gold Hoarder")).isNull()
    }

    @Test
    fun `remove ignores a title that is not unlocked`() {
        val f = Fixture()

        f.title.remove(GOLD_HOARDER)

        assertThat(f.title.getUnlockedTitles()).isEmpty()
    }

    @Test
    fun `getCurrentTitle defaults to zero`() {
        assertThat(Fixture().title.getCurrentTitle()).isEqualTo(0)
    }

    @Test
    fun `setCurrentTitle stores an unlocked id`() {
        val f = Fixture()
        f.title.manage(true, GOLD_ID)

        f.title.setCurrentTitle(GOLD_ID)

        assertThat(f.title.getCurrentTitle()).isEqualTo(GOLD_ID)
        assertThat(f.kv.scoped("titles").get("current-title")).isEqualTo(GOLD_ID.toLong())
    }

    @Test
    fun `setCurrentTitle rejects a locked or zero id and stores zero`() {
        val f = Fixture()
        f.kv.scoped("titles").set("current-title", GOLD_ID.toLong())

        f.title.setCurrentTitle(GOLD_ID)
        assertThat(f.title.getCurrentTitle()).isEqualTo(0)

        f.title.setCurrentTitle(0)
        assertThat(f.title.getCurrentTitle()).isEqualTo(0)
    }

    @Test
    fun `getCurrentTitleName resolves the male name by default`() {
        val f = Fixture()
        f.title.manage(true, GOLD_ID)
        f.title.setCurrentTitle(GOLD_ID)

        assertThat(f.title.getCurrentTitleName()).isEqualTo("Gold Hoarder")
    }

    @Test
    fun `getCurrentTitleName resolves the female name when set`() {
        val f = Fixture()
        f.player.playerSex = PlayerSex_t.FEMALE
        f.title.manage(true, TASK_ID)
        f.title.setCurrentTitle(TASK_ID)

        assertThat(f.title.getCurrentTitleName()).isEqualTo("Aspiring Huntswoman")
    }

    @Test
    fun `getCurrentTitleName falls back to the male name when the female name is empty`() {
        val f = Fixture()
        f.player.playerSex = PlayerSex_t.FEMALE
        f.title.manage(true, GOLD_ID)
        f.title.setCurrentTitle(GOLD_ID)

        assertThat(f.title.getCurrentTitleName()).isEqualTo("Gold Hoarder")
    }

    @Test
    fun `getCurrentTitleName returns empty for zero or unknown current id`() {
        val f = Fixture()

        assertThat(f.title.getCurrentTitleName()).isEmpty()

        f.kv.scoped("titles").set("current-title", 200L)
        assertThat(f.title.getCurrentTitleName()).isEmpty()
    }

    @Test
    fun `getNameBySex picks the female name only for female players and non-empty names`() {
        assertThat(PlayerTitle.getNameBySex(PlayerSex_t.FEMALE, "Huntsman", "Huntress")).isEqualTo("Huntress")
        assertThat(PlayerTitle.getNameBySex(PlayerSex_t.FEMALE, "Gold Hoarder", "")).isEqualTo("Gold Hoarder")
        assertThat(PlayerTitle.getNameBySex(PlayerSex_t.MALE, "Huntsman", "Huntress")).isEqualTo("Huntsman")
    }

    @Test
    fun `loadUnlockedTitles resolves persisted names against the registry`() {
        val f = Fixture()
        f.title.getUnlockedKV().set("Gold Hoarder", 111L)

        f.title.loadUnlockedTitles()

        assertThat(f.title.getUnlockedTitles()).containsExactly(GOLD_HOARDER to 111L)
        assertThat(f.debugLogs).containsExactly("[loadUnlockedTitles] - Loading unlocked titles: 1")
    }

    @Test
    fun `loadUnlockedTitles skips unknown names and logs an error`() {
        val f = Fixture()
        f.title.getUnlockedKV().set("Ghost Title", 111L)

        f.title.loadUnlockedTitles()

        assertThat(f.title.getUnlockedTitles()).isEmpty()
        assertThat(f.errorLogs).containsExactly("[loadUnlockedTitles] - Title Ghost Title not found.")
    }

    @Test
    fun `loadUnlockedTitles appends on every call, matching Canary's load-without-clear`() {
        val f = Fixture()
        f.title.getUnlockedKV().set("Gold Hoarder", 111L)

        f.title.loadUnlockedTitles()
        f.title.loadUnlockedTitles()

        assertThat(f.title.getUnlockedTitles()).containsExactly(
            GOLD_HOARDER to 111L,
            GOLD_HOARDER to 111L,
        )
    }

    @Test
    fun `getUnlockedKV scopes the unlocked entries under titles unlocked`() {
        val f = Fixture()

        f.title.getUnlockedKV().set("Gold Hoarder", 5L)

        assertThat(f.kv.scoped("titles").scoped("unlocked").get("Gold Hoarder")).isEqualTo(5L)
    }

    @Test
    fun `checkGold compares the bank balance`() {
        val f = Fixture()
        f.player.playerBankBalance = 999_999

        assertThat(f.title.checkGold(1_000_000)).isFalse()
        f.player.playerBankBalance = 1_000_000
        assertThat(f.title.checkGold(1_000_000)).isTrue()
    }

    @Test
    fun `checkMount counts only mounts present in the mount registry`() {
        val f = Fixture()
        f.mounts.addAll(listOf(mount(1), mount(2), mount(3)))
        f.player.mounts.addAll(listOf(1, 3))

        assertThat(f.title.checkMount(2)).isTrue()
        assertThat(f.title.checkMount(3)).isFalse()
    }

    @Test
    fun `checkOutfit compares the outfit count`() {
        val f = Fixture()
        f.player.outfits.addAll(listOf(136, 137))

        assertThat(f.title.checkOutfit(2)).isTrue()
        assertThat(f.title.checkOutfit(3)).isFalse()
    }

    @Test
    fun `checkLevel compares the player level`() {
        val f = Fixture()
        f.player.playerLevel = 199

        assertThat(f.title.checkLevel(200)).isFalse()
        f.player.playerLevel = 200
        assertThat(f.title.checkLevel(200)).isTrue()
    }

    @Test
    fun `checkHighscore returns true when the queried top player is the player`() {
        val f = Fixture()
        f.player.guid = 42L
        f.topPlayerBySkill[HighscoreCategories_t.EXPERIENCE.value] = 42L

        assertThat(f.title.checkHighscore(HighscoreCategories_t.EXPERIENCE.value)).isTrue()
        assertThat(f.highscoreQueries).containsExactly(HighscoreCategories_t.EXPERIENCE.value)
    }

    @Test
    fun `checkHighscore returns false when the player is not first or no row exists`() {
        val f = Fixture()
        f.player.guid = 42L

        assertThat(f.title.checkHighscore(HighscoreCategories_t.CHARMS.value)).isFalse()
        f.topPlayerBySkill[HighscoreCategories_t.CHARMS.value] = 7L
        assertThat(f.title.checkHighscore(HighscoreCategories_t.CHARMS.value)).isFalse()
    }

    @Test
    fun `checkHighscore returns false for Tibiadrome and Goshnar without querying`() {
        val f = Fixture()

        assertThat(f.title.checkHighscore(HighscoreCategories_t.DROME.value)).isFalse()
        assertThat(f.title.checkHighscore(HighscoreCategories_t.GOSHNAR.value)).isFalse()
        assertThat(f.highscoreQueries).isEmpty()
    }

    @Test
    fun `checkBestiary returns false for race zero because Executioner is Canary TODO`() {
        val f = Fixture()

        assertThat(f.title.checkBestiary("Executioner", 0)).isFalse()
        assertThat(f.title.checkBestiary("Boss Executioner", 0)).isFalse()
    }

    @Test
    fun `checkBestiary with boss amount compares the bestiary kill count`() {
        val f = Fixture()
        f.player.bestiaryKillCounts[BOSS_RACE] = 9

        assertThat(f.title.checkBestiary("Boss Destroyer", BOSS_RACE, isBoss = true, amount = 10)).isFalse()
        f.player.bestiaryKillCounts[BOSS_RACE] = 10
        assertThat(f.title.checkBestiary("Boss Destroyer", BOSS_RACE, isBoss = true, amount = 10)).isTrue()
    }

    @Test
    fun `checkBestiary without boss amount asks the task hunting unlock state`() {
        val f = Fixture()
        f.monsters[LYCANTHROPE_RACE to false] = MonsterTypeInfo(raceId = LYCANTHROPE_RACE, bestiaryToUnlock = 100)
        f.player.bestiaryKillCounts[LYCANTHROPE_RACE] = 99

        assertThat(f.title.checkBestiary("Blood Moon Hunter", LYCANTHROPE_RACE)).isFalse()
        f.player.bestiaryKillCounts[LYCANTHROPE_RACE] = 100
        assertThat(f.title.checkBestiary("Blood Moon Hunter", LYCANTHROPE_RACE)).isTrue()
        assertThat(f.title.checkBestiary("Blood Moon Hunter", LYCANTHROPE_RACE - 1)).isFalse()
    }

    @Test
    fun `checkLoginStreak reads the daily reward streak and truncates it to uint8`() {
        val f = Fixture()
        f.kv.scoped("daily-reward").set("streak", 7L)

        assertThat(f.title.checkLoginStreak(7)).isTrue()
        assertThat(f.title.checkLoginStreak(8)).isFalse()

        f.kv.scoped("daily-reward").set("streak", 256L)
        assertThat(f.title.checkLoginStreak(1)).isFalse()
    }

    @Test
    fun `checkLoginStreak returns false without a stored streak`() {
        assertThat(Fixture().title.checkLoginStreak(1)).isFalse()
    }

    @Test
    fun `checkTask compares the task hunting points`() {
        val f = Fixture()
        f.player.playerTaskHuntingPoints = 159_999

        assertThat(f.title.checkTask(160_000)).isFalse()
        f.player.playerTaskHuntingPoints = 160_000
        assertThat(f.title.checkTask(160_000)).isTrue()
    }

    @Test
    fun `checkMap always returns false until the cyclopedia map is ported`() {
        val f = Fixture()

        assertThat(f.title.checkMap(1)).isFalse()
        assertThat(f.title.checkMap(100)).isFalse()
    }

    @Test
    fun `checkOther grants Guild Leader to a guild leader`() {
        val f = Fixture()

        assertThat(f.title.checkOther("Guild Leader")).isFalse()
        f.player.playerGuildRank = GuildRank.ViceLeader
        assertThat(f.title.checkOther("Guild Leader")).isFalse()
        f.player.playerGuildRank = GuildRank.Leader
        assertThat(f.title.checkOther("Guild Leader")).isTrue()
    }

    @Test
    fun `checkOther grants the outfit based titles only when both outfits are wearable`() {
        val f = Fixture()

        f.player.wearable.add(1457 to 3)
        assertThat(f.title.checkOther("Admirer of the Crown")).isFalse()
        f.player.wearable.add(1456 to 3)
        assertThat(f.title.checkOther("Admirer of the Crown")).isTrue()

        f.player.wearable.clear()
        f.player.wearable.add(1211 to 3)
        assertThat(f.title.checkOther("Big Spender")).isFalse()
        f.player.wearable.add(1210 to 3)
        assertThat(f.title.checkOther("Big Spender")).isTrue()

        f.player.wearable.clear()
        f.player.wearable.add(1437 to 3)
        assertThat(f.title.checkOther("Royal Bounacean Advisor")).isFalse()
        f.player.wearable.add(1436 to 3)
        assertThat(f.title.checkOther("Royal Bounacean Advisor")).isTrue()
    }

    @Test
    fun `checkOther grants the bestiary kill count titles at their thresholds`() {
        val f = Fixture()

        f.player.bestiaryKillCounts[2346] = 0
        assertThat(f.title.checkOther("Challenger of the Iks")).isFalse()
        f.player.bestiaryKillCounts[2346] = 1
        assertThat(f.title.checkOther("Challenger of the Iks")).isTrue()

        f.player.bestiaryKillCounts[698] = 24
        assertThat(f.title.checkOther("Midnight Hunter")).isFalse()
        f.player.bestiaryKillCounts[698] = 25
        assertThat(f.title.checkOther("Midnight Hunter")).isTrue()

        f.player.bestiaryKillCounts[56] = 9_999
        assertThat(f.title.checkOther("Ratinator")).isFalse()
        f.player.bestiaryKillCounts[56] = 10_000
        assertThat(f.title.checkOther("Ratinator")).isTrue()

        f.player.bestiaryKillCounts[1003] = 0
        assertThat(f.title.checkOther("Doomsday Nemesis")).isFalse()
        f.player.bestiaryKillCounts[1003] = 1
        assertThat(f.title.checkOther("Doomsday Nemesis")).isTrue()

        f.player.bestiaryKillCounts[2118] = 4
        assertThat(f.title.checkOther("King of Demon")).isFalse()
        f.player.bestiaryKillCounts[2118] = 5
        assertThat(f.title.checkOther("King of Demon")).isTrue()
    }

    @Test
    fun `checkOther grants Beastly at 2000 charm points and Truly Boss at 15000 boss points`() {
        val f = Fixture()
        f.player.playerCharmPoints = 1_999
        f.player.playerBossPoints = 14_999

        assertThat(f.title.checkOther("Beastly")).isFalse()
        assertThat(f.title.checkOther("Truly Boss")).isFalse()

        f.player.playerCharmPoints = 2_000
        f.player.playerBossPoints = 15_000
        assertThat(f.title.checkOther("Beastly")).isTrue()
        assertThat(f.title.checkOther("Truly Boss")).isTrue()
    }

    @Test
    fun `checkOther returns false for the Canary TODO branches and unknown names`() {
        val f = Fixture()

        for (name in listOf(
            "Proconsul of Iksupan",
            "Aeternal",
            "Robinson Crusoe",
            "Chompmeister",
            "Bringer of Rain",
            "Hero of Bounac",
            "Planegazer",
            "Time Traveller",
            "Unknown Title",
        )) {
            assertThat(f.title.checkOther(name)).isFalse()
        }
    }

    @Test
    fun `checkAndUpdateNewTitles unlocks qualifying titles and skips MAP`() {
        val f = Fixture()
        f.player.playerBankBalance = 1_000_000
        f.player.playerLevel = 50
        f.player.playerGuildRank = GuildRank.Leader
        f.kv.scoped("daily-reward").set("streak", 7L)
        f.player.playerTaskHuntingPoints = 160_000
        f.monsters[LYCANTHROPE_RACE to false] = MonsterTypeInfo(LYCANTHROPE_RACE, 100)
        f.player.bestiaryKillCounts[LYCANTHROPE_RACE] = 100
        f.player.bestiaryKillCounts[BOSS_RACE] = 10

        f.title.checkAndUpdateNewTitles()

        assertThat(f.title.isTitleUnlocked(GOLD_ID)).isTrue()
        assertThat(f.title.isTitleUnlocked(LEVEL_ID)).isTrue()
        assertThat(f.title.isTitleUnlocked(GUILD_LEADER_ID)).isTrue()
        assertThat(f.title.isTitleUnlocked(DAILY_REWARD_ID)).isTrue()
        assertThat(f.title.isTitleUnlocked(TASK_ID)).isTrue()
        assertThat(f.title.isTitleUnlocked(BESTIARY_ID)).isTrue()
        assertThat(f.title.isTitleUnlocked(BOSS_ID)).isTrue()
        assertThat(f.title.isTitleUnlocked(MAP_ID)).isFalse()
    }

    @Test
    fun `checkAndUpdateNewTitles double-loads freshly unlocked titles, matching Canary`() {
        val f = Fixture(titles = listOf(GOLD_HOARDER))
        f.player.playerBankBalance = 1_000_000

        f.title.checkAndUpdateNewTitles()

        assertThat(f.title.getUnlockedTitles()).containsExactly(
            GOLD_HOARDER to 1_700_000_000L,
            GOLD_HOARDER to 1_700_000_000L,
        )
    }
}
