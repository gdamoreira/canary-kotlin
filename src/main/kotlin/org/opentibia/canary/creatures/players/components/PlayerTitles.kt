package org.opentibia.canary.creatures.players.components

import org.opentibia.canary.social.guild.GuildRank
import org.opentibia.canary.systems.mount.Mount

/**
 * `enum CyclopediaTitle_t : uint8_t` (`src/enums/player_cyclopedia.hpp:25-38`).
 */
enum class CyclopediaTitle_t(val value: Int) {
    NOTHING(0),
    GOLD(1),
    MOUNTS(2),
    OUTFITS(3),
    LEVEL(4),
    HIGHSCORES(5),
    BESTIARY(6),
    BOSSTIARY(7),
    DAILY_REWARD(8),
    TASK(9),
    MAP(10),
    OTHERS(11),
}

/**
 * `HighscoreCategories_t` (`src/game/game_definitions.hpp:91-107`). The title
 * component only reads the category ids; the `Game::getSkillNameById` mapping
 * lives behind [TitleHighscoreRepository].
 */
enum class HighscoreCategories_t(val value: Int) {
    EXPERIENCE(0),
    FIST_FIGHTING(1),
    CLUB_FIGHTING(2),
    SWORD_FIGHTING(3),
    AXE_FIGHTING(4),
    DISTANCE_FIGHTING(5),
    SHIELDING(6),
    FISHING(7),
    MAGIC_LEVEL(8),
    LOYALTY_POINTS(9),
    ACHIEVEMENTS(10),
    CHARMS(11),
    DROME(12),
    GOSHNAR(13),
    BOSS_POINTS(14),
}

/** `PlayerSex_t` (`src/creatures/creatures_definitions.hpp:448-452`). */
enum class PlayerSex_t(val value: Int) {
    FEMALE(0),
    MALE(1),
}

/**
 * `struct Title` (`player_title.hpp:19-59`).
 *
 * The six C++ constructors collapse into one data class with named arguments:
 * `maleName`/`femaleName`/`description`/`amount`/`permanent`/`skill`/`race`
 * default exactly like the C++ value-initialized `Title()`. `amount` is the
 * C++ `uint32_t` widened to `Long`; `skill` is `uint8_t` and `race` `uint16_t`.
 *
 * PARITY NOTE: C++ `Title::operator==` compares only `m_id`
 * (player_title.hpp:56-58). The port keeps the generated structural equality
 * because unlocked entries retain the full registry record and tests compare
 * whole titles.
 */
data class Title(
    val id: Int = 0,
    val type: CyclopediaTitle_t = CyclopediaTitle_t.NOTHING,
    val maleName: String = "",
    val femaleName: String = "",
    val description: String = "",
    val amount: Long = 0,
    val permanent: Boolean = false,
    val skill: Int = 0,
    val race: Int = 0,
)

/**
 * `Game::getTitleById/getTitleByName/getTitles` (game.cpp:12566-12594) and
 * the `m_titles` registry seeded in `Game::Game` (game.cpp:599-703).
 *
 * PARITY NOTE: C++ stores the registry in a `std::unordered_set<Title>`, so
 * iteration order is unspecified; the port preserves registration order to
 * make [PlayerTitle.checkAndUpdateNewTitles] deterministic.
 */
interface TitleRegistry {
    /** `Game::getTitleById` (game.cpp:12570-12581); `Title()` (id 0) when absent. */
    fun getTitleById(id: Int): Title

    /** `Game::getTitleByName` (game.cpp:12583-12594); matches `maleName` only. */
    fun getTitleByName(name: String): Title

    /** `Game::getTitles` (game.cpp:12566-12568). */
    fun getTitles(): List<Title>
}

/** Registry stand-in for local runs and tests. */
class InMemoryTitleRegistry(titles: List<Title> = emptyList()) : TitleRegistry {

    private val titles = titles.toList()

    override fun getTitleById(id: Int): Title {
        if (id == 0) {
            return Title()
        }
        return titles.firstOrNull { it.id == id } ?: Title()
    }

    override fun getTitleByName(name: String): Title {
        if (name.isEmpty()) {
            return Title()
        }
        return titles.firstOrNull { it.maleName == name } ?: Title()
    }

    override fun getTitles(): List<Title> = titles
}

/**
 * Minimal mirror of the `MonsterType::info` fields `PlayerTitle::checkBestiary`
 * reads through `isCreatureUnlockedOnTaskHunting` (player.cpp:10527-10532).
 */
data class MonsterTypeInfo(
    val raceId: Int,
    val bestiaryToUnlock: Int,
)

/**
 * Seam for the highscore query `PlayerTitle::checkHighscore` runs through
 * `Database::getInstance().storeQuery` (player_title.cpp:205-244).
 *
 * The caller-supplied implementation owns the C++ category switch:
 * `CHARMS` executes the `player_charms` join query (field `player_id`), and
 * every other reachable category executes the `players` skill query (field
 * `id`) with `g_game().getSkillNameById(skill)`.
 */
fun interface TitleHighscoreRepository {
    /** Top ranked player id for [skill], or `null` when the query yields no row. */
    fun getTopPlayerId(skill: Int): Long?
}

/**
 * Injected `Player` view for the title component. Every member transcribes the
 * exact `Player` symbol `PlayerTitle` calls, with the C++ line cited.
 */
interface TitlePlayer {
    /** `Player::getName()` — used by the `checkAndUpdateNewTitles` benchmark log (player_title.cpp:155). */
    fun getName(): String

    /** `Player::getSex()` (player_title.cpp:108). */
    fun getSex(): PlayerSex_t

    /** `Player::getBankBalance()` (player_title.cpp:184). */
    fun getBankBalance(): Long

    /** `Player::hasMount(mount)` (player_title.cpp:190). */
    fun hasMount(mount: Mount): Boolean

    /** `Player::outfits.size()` (player.hpp:1686, player_title.cpp:198). */
    fun getOutfitsCount(): Int

    /** `Player::getLevel()` (player_title.cpp:202). */
    fun getLevel(): Int

    /** `Player::getGUID()` (player_title.cpp:243). */
    fun getGUID(): Long

    /** `Player::getBestiaryKillCount(raceid)` (player_title.cpp:257, 290, 309, 312, 315, 320). */
    fun getBestiaryKillCount(raceid: Int): Int

    /**
     * `Player::isCreatureUnlockedOnTaskHunting(mtype)` (player.cpp:10527-10532).
     * The default body is the exact C++ body: a null monster type is false,
     * otherwise the kill count must reach `bestiaryToUnlock`.
     */
    fun isCreatureUnlockedOnTaskHunting(monsterType: MonsterTypeInfo?): Boolean =
        monsterType != null && getBestiaryKillCount(monsterType.raceId) >= monsterType.bestiaryToUnlock

    /** `Player::getTaskHuntingPoints()` (player_title.cpp:268). */
    fun getTaskHuntingPoints(): Long

    /** `Player::getCharmPoints()` (player_title.cpp:305). */
    fun getCharmPoints(): Int

    /** `Player::getBossPoints()` (player_title.cpp:326). */
    fun getBossPoints(): Int

    /** `Player::canWear(lookType, addons)` (player_title.cpp:284, 287, 294). */
    fun canWear(lookType: Int, addons: Int): Boolean

    /**
     * `Player::getGuildRank()` (player_title.cpp:279). C++ tests
     * `rank && rank->level == 3`; the port's [GuildRank.Leader] is that
     * level-3 rank.
     */
    fun getGuildRank(): GuildRank?
}

/**
 * Mirror of Canary `PlayerTitle`
 * (`src/creatures/players/components/player_title.hpp:68-101`,
 * player_title.cpp:20-329).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Global
 * and player dependencies are constructor-injected seams (AGENTS.md 1.1):
 * [kv] replaces `m_player.kv()`, [registry] replaces `g_game().getTitle*`,
 * [getMounts] replaces `g_game().mounts->getMounts()`,
 * [getMonsterTypeByRaceId] replaces `g_monsters().getMonsterTypeByRaceId` and
 * [highscoreRepository] replaces the `Database::getInstance()` query.
 *
 * PARITY NOTES:
 *  - `Title::amount`/timestamps are `uint32_t` in C++; they are widened to
 *    `Long` (no observable difference for current epoch seconds until 2106).
 *  - [loadUnlockedTitles] appends to the unlocked vector without clearing it,
 *    exactly like C++ (player_title.cpp:160-172), so the trailing call inside
 *    [checkAndUpdateNewTitles] re-adds freshly unlocked titles (Canary parity,
 *    asserted by the transcript tests).
 *  - The `Benchmark` timing log of `checkAndUpdateNewTitles`
 *    (player_title.cpp:116, 155) is not ported.
 */
class PlayerTitle(
    private val player: TitlePlayer,
    /** `m_player.kv()` — the per-player KV root (player_title.cpp:89, 94, 176). */
    private val kv: KV = InMemoryKV(),
    /** `g_game().getTitleById/getTitleByName/getTitles` (player_title.cpp:39, 103, 117, 164). */
    private val registry: TitleRegistry = InMemoryTitleRegistry(),
    /** `g_game().mounts->getMounts()` (player_title.cpp:189). */
    private val getMounts: () -> Collection<Mount> = { emptyList() },
    /** `g_monsters().getMonsterTypeByRaceId(raceId, isBoss)` (player_title.cpp:259). */
    private val getMonsterTypeByRaceId: (raceId: Int, isBoss: Boolean) -> MonsterTypeInfo? = { _, _ -> null },
    /** Highscore query seam (player_title.cpp:206-244). */
    private val highscoreRepository: TitleHighscoreRepository = TitleHighscoreRepository { null },
    /** `OTSYS_TIME() / 1000` (player_title.cpp:55). */
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    /** `g_logger().debug` (player_title.cpp:59, 81, 155, 162). */
    private val logDebug: (message: String) -> Unit = {},
    /** `g_logger().error` (player_title.cpp:166). */
    private val logError: (message: String) -> Unit = {},
) {

    /** `m_titlesUnlocked` — `{Title, time when it was unlocked}` (player_title.hpp:99). */
    private val titlesUnlocked = mutableListOf<Pair<Title, Long>>()

    /** `m_titleUnlockedKV` (player_title.hpp:98). */
    private var titleUnlockedKV: KV? = null

    /** `PlayerTitle::isTitleUnlocked` (player_title.cpp:23-36). */
    fun isTitleUnlocked(id: Int): Boolean {
        if (id == 0) {
            return false
        }

        return titlesUnlocked.any { it.first.id == id }
    }

    /** `PlayerTitle::manage` (player_title.cpp:38-62). */
    fun manage(canAdd: Boolean, id: Int, timestamp: Long = 0): Boolean {
        val title = registry.getTitleById(id)
        if (title.id == 0) {
            return false
        }

        if (!canAdd) {
            if (!title.permanent) {
                remove(title)
            }
            return false
        }

        if (isTitleUnlocked(id)) {
            return false
        }

        val toSaveTimestamp = if (timestamp != 0L) timestamp else now()
        getUnlockedKV().set(title.maleName, toSaveTimestamp)
        titlesUnlocked.add(title to toSaveTimestamp)
        logDebug("[manage] - Added title: ${title.maleName}")

        return true
    }

    /** `PlayerTitle::remove` (player_title.cpp:64-82). */
    fun remove(title: Title) {
        val id = title.id
        if (!isTitleUnlocked(id)) {
            return
        }

        val index = titlesUnlocked.indexOfFirst { it.first.id == id }
        if (index < 0) {
            return
        }

        getUnlockedKV().remove(title.maleName)
        titlesUnlocked.removeAt(index)
        logDebug("[remove] - Removed title: ${title.maleName}")
    }

    /** `PlayerTitle::getUnlockedTitles` (player_title.cpp:84-86). */
    fun getUnlockedTitles(): List<Pair<Title, Long>> = titlesUnlocked

    /**
     * `PlayerTitle::getCurrentTitle` (player_title.cpp:88-91); the stored
     * number is narrowed to `uint8_t` like the C++ `static_cast`.
     */
    fun getCurrentTitle(): Int {
        val title = kv.scoped("titles").get("current-title") ?: return 0
        return (title and 0xFF).toInt()
    }

    /** `PlayerTitle::setCurrentTitle` (player_title.cpp:93-95). */
    fun setCurrentTitle(id: Int) {
        kv.scoped("titles").set("current-title", if (id != 0 && isTitleUnlocked(id)) id.toLong() else 0L)
    }

    /** `PlayerTitle::getCurrentTitleName` (player_title.cpp:97-109). */
    fun getCurrentTitleName(): String {
        val currentTitle = getCurrentTitle()
        if (currentTitle == 0) {
            return ""
        }

        val title = registry.getTitleById(currentTitle)
        if (title.id == 0) {
            return ""
        }

        return getNameBySex(player.getSex(), title.maleName, title.femaleName)
    }

    /** `PlayerTitle::checkAndUpdateNewTitles` (player_title.cpp:115-158). */
    fun checkAndUpdateNewTitles() {
        for (title in registry.getTitles()) {
            when (title.type) {
                CyclopediaTitle_t.NOTHING -> {
                    // No check.
                }
                CyclopediaTitle_t.GOLD -> manage(checkGold(title.amount), title.id)
                CyclopediaTitle_t.MOUNTS -> manage(checkMount(title.amount), title.id)
                CyclopediaTitle_t.OUTFITS -> manage(checkOutfit(title.amount), title.id)
                CyclopediaTitle_t.LEVEL -> manage(checkLevel(title.amount), title.id)
                CyclopediaTitle_t.HIGHSCORES -> manage(checkHighscore(title.skill), title.id)
                CyclopediaTitle_t.BESTIARY,
                CyclopediaTitle_t.BOSSTIARY,
                -> manage(
                    checkBestiary(title.maleName, title.race, title.type == CyclopediaTitle_t.BOSSTIARY, title.amount),
                    title.id,
                )
                CyclopediaTitle_t.DAILY_REWARD -> manage(checkLoginStreak(title.amount), title.id)
                CyclopediaTitle_t.TASK -> manage(checkTask(title.amount), title.id)
                CyclopediaTitle_t.MAP -> {
                    // C++ has `manage(checkMap(...))` commented out (player_title.cpp:146-148).
                }
                CyclopediaTitle_t.OTHERS -> manage(checkOther(title.maleName), title.id)
            }
        }

        loadUnlockedTitles()
    }

    /**
     * `PlayerTitle::loadUnlockedTitles` (player_title.cpp:160-172).
     *
     * PARITY NOTE: like C++ this appends to [titlesUnlocked] without clearing
     * it. C++ reads `getUnlockedKV()->get(titleName)->getNumber()` without a
     * null check; the port treats a key that disappeared between `keys()` and
     * `get()` as 0 (hardening).
     */
    fun loadUnlockedTitles() {
        val unlockedTitles = getUnlockedKV().keys()
        logDebug("[loadUnlockedTitles] - Loading unlocked titles: ${unlockedTitles.size}")
        for (titleName in unlockedTitles) {
            val title = registry.getTitleByName(titleName)
            if (title.id == 0) {
                logError("[loadUnlockedTitles] - Title $titleName not found.")
                continue
            }

            titlesUnlocked.add(title to (getUnlockedKV().get(titleName) ?: 0L))
        }
    }

    /** `PlayerTitle::getUnlockedKV` (player_title.cpp:174-180). */
    fun getUnlockedKV(): KV =
        titleUnlockedKV ?: kv.scoped("titles").scoped("unlocked").also { titleUnlockedKV = it }

    /** `PlayerTitle::checkGold` (player_title.cpp:183-185). */
    fun checkGold(amount: Long): Boolean = player.getBankBalance() >= amount

    /** `PlayerTitle::checkMount` (player_title.cpp:187-195). */
    fun checkMount(amount: Long): Boolean {
        var total = 0
        for (mount in getMounts()) {
            if (player.hasMount(mount)) {
                total++
            }
        }
        return total >= amount
    }

    /** `PlayerTitle::checkOutfit` (player_title.cpp:197-199). */
    fun checkOutfit(amount: Long): Boolean = player.getOutfitsCount() >= amount

    /** `PlayerTitle::checkLevel` (player_title.cpp:201-203). */
    fun checkLevel(amount: Long): Boolean = player.getLevel() >= amount

    /** `PlayerTitle::checkHighscore` (player_title.cpp:205-244). */
    fun checkHighscore(skill: Int): Boolean {
        if (skill == HighscoreCategories_t.DROME.value || skill == HighscoreCategories_t.GOSHNAR.value) {
            return false
        }

        val resultValue = highscoreRepository.getTopPlayerId(skill) ?: return false
        return resultValue == player.getGUID()
    }

    /** `PlayerTitle::checkBestiary` (player_title.cpp:246-260). */
    fun checkBestiary(name: String, race: Int, isBoss: Boolean = false, amount: Long = 0): Boolean {
        if (race == 0) {
            if (name == "Executioner") {
                // todo check if player has unlocked all bestiary (player_title.cpp:249).
            } else if (name == "Boss Executioner") {
                // todo check if player has unlocked all bosses (player_title.cpp:251).
            }
            return false
        }
        if (isBoss && amount > 0) {
            return player.getBestiaryKillCount(race).toLong() >= amount
        }
        return player.isCreatureUnlockedOnTaskHunting(getMonsterTypeByRaceId(race, isBoss))
    }

    /**
     * `PlayerTitle::checkLoginStreak` (player_title.cpp:262-265); the stored
     * number is narrowed to `uint8_t` like the C++ `static_cast`.
     */
    fun checkLoginStreak(amount: Long): Boolean {
        val streak = kv.scoped("daily-reward").get("streak") ?: return false
        return (streak and 0xFF) >= amount
    }

    /** `PlayerTitle::checkTask` (player_title.cpp:267-269). */
    fun checkTask(amount: Long): Boolean = player.getTaskHuntingPoints() >= amount

    /** `PlayerTitle::checkMap` (player_title.cpp:271-274) — cyclopedia TODO in Canary. */
    fun checkMap(amount: Long): Boolean = false

    /** `PlayerTitle::checkOther` (player_title.cpp:276-328). */
    fun checkOther(name: String): Boolean {
        return when (name) {
            "Guild Leader" -> player.getGuildRank() == GuildRank.Leader
            "Proconsul of Iksupan" -> false
            "Admirer of the Crown" -> player.canWear(1457, 3) && player.canWear(1456, 3)
            "Big Spender" -> player.canWear(1211, 3) && player.canWear(1210, 3)
            "Challenger of the Iks" -> player.getBestiaryKillCount(2346) >= 1
            "Royal Bounacean Advisor" -> player.canWear(1437, 3) && player.canWear(1436, 3)
            "Aeternal" -> false
            "Robinson Crusoe" -> false
            "Chompmeister" -> false
            "Bringer of Rain" -> false
            "Beastly" -> player.getCharmPoints() >= 2000
            "Midnight Hunter" -> player.getBestiaryKillCount(698) >= 25
            "Ratinator" -> player.getBestiaryKillCount(56) >= 10000
            "Doomsday Nemesis" -> player.getBestiaryKillCount(1003) >= 1
            "Hero of Bounac" -> false
            "King of Demon" -> player.getBestiaryKillCount(2118) >= 5
            "Planegazer" -> false
            "Time Traveller" -> false
            "Truly Boss" -> player.getBossPoints() >= 15000
            else -> false
        }
    }

    companion object {
        /** `PlayerTitle::getNameBySex` (player_title.cpp:111-113). */
        fun getNameBySex(sex: PlayerSex_t, male: String, female: String): String =
            if (sex == PlayerSex_t.FEMALE && female.isNotEmpty()) female else male
    }
}
