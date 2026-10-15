package org.opentibia.canary.creatures.players.components

import org.opentibia.canary.kv.KV
import org.opentibia.canary.kv.KVMemory
import org.opentibia.canary.kv.ValueWrapper

/**
 * `struct Achievement` (`player_achievement.hpp:15-27`), registered through
 * `Game::registerAchievement` (game.cpp:12481-12491). `grade` and `points` are
 * the C++ `uint8_t` fields, `id` the `uint16_t` id.
 */
data class Achievement(
    val id: Int = 0,
    val name: String = "",
    val description: String = "",
    val secret: Boolean = false,
    val grade: Int = 0,
    val points: Int = 0,
)

/**
 * `Game::getAchievementById/getAchievementByName` (game.cpp:12493-12503). The
 * C++ registry is a `std::map<uint16_t, Achievement>` filled by
 * `Game.registerAchievement` calls from
 * `data/scripts/lib/register_achievements.lua`; per AGENTS.md 1.4 that Lua
 * table stays Lua and is injected here.
 */
interface AchievementRegistry {
    /** `Game::getAchievementById` (game.cpp:12493-12495); `Achievement()` (id 0) when absent. */
    fun getAchievementById(id: Int): Achievement

    /** `Game::getAchievementByName` (game.cpp:12497-12503). */
    fun getAchievementByName(name: String): Achievement

    /** `Game::registerAchievement` (game.cpp:12481-12491). */
    fun registerAchievement(id: Int, name: String, description: String, secret: Boolean, grade: Int, points: Int) {}

    /** `Game::getAchievements` (game.cpp:12527). */
    fun getAchievements(): List<Achievement> = emptyList()

    /** `Game::getSecretAchievements` (game.cpp:12505). */
    fun getSecretAchievements(): List<Achievement> = getAchievements().filter { it.secret }

    /** `Game::getPublicAchievements` (game.cpp:12516). */
    fun getPublicAchievements(): List<Achievement> = getAchievements().filter { !it.secret }
}

/** Registry stand-in for local runs and tests. */
class InMemoryAchievementRegistry(achievements: List<Achievement> = emptyList()) : AchievementRegistry {

    private val achievementsById = linkedMapOf<Int, Achievement>()

    private val achievementsNameToId = linkedMapOf<String, Int>()

    init {
        for (achievement in achievements) {
            achievementsById[achievement.id] = achievement
            achievementsNameToId.putIfAbsent(achievement.name, achievement.id)
        }
    }

    override fun getAchievementById(id: Int): Achievement = achievementsById[id] ?: Achievement()

    override fun getAchievementByName(name: String): Achievement {
        if (name.isEmpty()) {
            return Achievement()
        }
        val id = achievementsNameToId[name] ?: return Achievement()
        return getAchievementById(id)
    }

    override fun registerAchievement(id: Int, name: String, description: String, secret: Boolean, grade: Int, points: Int) {
        achievementsById[id] = Achievement(
            id = id,
            name = name,
            description = description,
            secret = secret,
            grade = grade,
            points = points,
        )
        achievementsNameToId.putIfAbsent(name, id)
    }

    override fun getAchievements(): List<Achievement> = achievementsById.values.toList()
}

/**
 * Injected `Player` view for the achievement component. Every member
 * transcribes the exact `Player` symbol `PlayerAchievement` calls, with the
 * C++ line cited.
 */
interface AchievementPlayer {
    /** `Player::getName()` — logging only (player_achievement.cpp:109). */
    fun getName(): String

    /** `Player::sendTextMessage(MESSAGE_EVENT_ADVANCE, ...)` (player_achievement.cpp:30). */
    fun sendTextMessage(message: String)

    /**
     * `Player::sendCyclopediaCharacterAchievements(secretsUnlocked, achievements)`
     * (player_achievement.cpp:131; player.cpp:8438-8441). The pairs carry the
     * registry achievement plus its unlock timestamp.
     */
    fun sendCyclopediaCharacterAchievements(secretsUnlocked: Int, achievementsUnlocked: List<Pair<Achievement, Long>>)
}

/**
 * Mirror of Canary `PlayerAchievement`
 * (`src/creatures/players/components/player_achievement.hpp:29-48`,
 * player_achievement.cpp:16-140).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Global
 * and player dependencies are constructor-injected seams (AGENTS.md 1.1):
 * [kv] replaces `m_player.kv()`, [registry] replaces the `g_game()`
 * achievement lookups and [player] replaces the message/cyclopedia sends.
 *
 * PARITY NOTES:
 *  - `getPoints` narrows the stored number to `uint16_t` exactly like the C++
 *    `static_cast` (player_achievement.cpp:82).
 *  - [loadUnlockedAchievements] appends to the unlocked vector without
 *    clearing it, exactly like C++ (player_achievement.cpp:99-113).
 *  - C++ reads `getUnlockedKV()->get(achievementName)->getNumber()` without a
 *    null check; the port treats a key that disappeared between `keys()` and
 *    `get()` as 0 (hardening).
 */
class PlayerAchievement(
    private val player: AchievementPlayer,
    /** `m_player.kv()` — the per-player KV root (player_achievement.cpp:81, 87, 92, 136). */
    private val kv: KV = KVMemory(),
    /** `g_game().getAchievementById/getAchievementByName` (player_achievement.cpp:24, 46, 103, 119). */
    private val registry: AchievementRegistry = InMemoryAchievementRegistry(),
    /** `OTSYS_TIME() / 1000` (player_achievement.cpp:34). */
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    /** `g_logger().debug` (player_achievement.cpp:101, 109). */
    private val logDebug: (message: String) -> Unit = {},
    /** `g_logger().error` (player_achievement.cpp:105). */
    private val logError: (message: String) -> Unit = {},
) {

    /** `m_achievementsUnlocked` — `{achievement ID, time when it was unlocked}` (player_achievement.hpp:46). */
    private val achievementsUnlocked = mutableListOf<Pair<Int, Long>>()

    /** `m_unlockedKV` (player_achievement.hpp:45). */
    private var unlockedKV: KV? = null

    /** `PlayerAchievement::add` (player_achievement.cpp:19-39). */
    fun add(id: Int, message: Boolean = true, timestamp: Long = 0): Boolean {
        if (isUnlocked(id)) {
            return false
        }

        val achievement = registry.getAchievementById(id)
        if (achievement.id == 0) {
            return false
        }

        if (message) {
            player.sendTextMessage("Congratulations! You earned the achievement ${achievement.name}.")
        }

        addPoints(achievement.points)
        val toSaveTimestamp = if (timestamp != 0L) timestamp else now()
        getUnlockedKV().set(achievement.name, ValueWrapper(toSaveTimestamp.toInt()))
        achievementsUnlocked.add(achievement.id to toSaveTimestamp)
        return true
    }

    /** `PlayerAchievement::remove` (player_achievement.cpp:41-63). */
    fun remove(id: Int): Boolean {
        if (!isUnlocked(id)) {
            return false
        }

        val achievement = registry.getAchievementById(id)
        if (achievement.id == 0) {
            return false
        }

        val index = achievementsUnlocked.indexOfFirst { it.first == id }
        if (index >= 0) {
            getUnlockedKV().remove(achievement.name)
            achievementsUnlocked.removeAt(index)
            removePoints(achievement.points)
            return true
        }

        return false
    }

    /** `PlayerAchievement::isUnlocked` (player_achievement.cpp:65-78). */
    fun isUnlocked(id: Int): Boolean {
        if (id == 0) {
            return false
        }

        return achievementsUnlocked.any { it.first == id }
    }

    /**
     * `PlayerAchievement::getPoints` (player_achievement.cpp:80-83); the
     * stored number is narrowed to `uint16_t` like the C++ `static_cast`.
     */
    fun getPoints(): Int {
        val points = kv.scoped("achievements").get("points")?.getNumber() ?: return 0
        return points.toInt() and 0xFFFF
    }

    /** `PlayerAchievement::addPoints` (player_achievement.cpp:85-88). */
    fun addPoints(toAddPoints: Int) {
        val oldPoints = getPoints()
        kv.scoped("achievements").set("points", ValueWrapper(oldPoints + toAddPoints))
    }

    /** `PlayerAchievement::removePoints` (player_achievement.cpp:90-93). */
    fun removePoints(points: Int) {
        val oldPoints = getPoints()
        kv.scoped("achievements").set("points", ValueWrapper(oldPoints - minOf(oldPoints, points)))
    }

    /** `PlayerAchievement::getUnlockedAchievements` (player_achievement.cpp:95-97). */
    fun getUnlockedAchievements(): List<Pair<Int, Long>> = achievementsUnlocked

    /**
     * `PlayerAchievement::loadUnlockedAchievements` (player_achievement.cpp:99-113).
     *
     * PARITY NOTE: like C++ this appends to [achievementsUnlocked] without
     * clearing it, so repeated calls duplicate entries.
     */
    fun loadUnlockedAchievements() {
        val unlockedAchievements = getUnlockedKV().keys()
        logDebug("[loadUnlockedAchievements] - Loading unlocked achievements: ${unlockedAchievements.size}")
        for (achievementName in unlockedAchievements) {
            val achievement = registry.getAchievementByName(achievementName)
            if (achievement.id == 0) {
                logError("[loadUnlockedAchievements] - Achievement $achievementName not found.")
                continue
            }

            logDebug(
                "[loadUnlockedAchievements] - Achievement $achievementName found for player ${player.getName()}.",
            )
            achievementsUnlocked.add(
                achievement.id to (getUnlockedKV().get(achievementName)?.getNumber()?.toLong() ?: 0L),
            )
        }
    }

    /** `PlayerAchievement::sendUnlockedSecretAchievements` (player_achievement.cpp:115-132). */
    fun sendUnlockedSecretAchievements() {
        val achievements = mutableListOf<Pair<Achievement, Long>>()
        var unlockedSecret = 0
        for ((achievId, achievCreatedTime) in getUnlockedAchievements()) {
            val achievement = registry.getAchievementById(achievId)
            if (achievement.id == 0) {
                continue
            }

            if (achievement.secret) {
                unlockedSecret++
            }

            achievements.add(achievement to achievCreatedTime)
        }

        player.sendCyclopediaCharacterAchievements(unlockedSecret, achievements)
    }

    /** `PlayerAchievement::getUnlockedKV` (player_achievement.cpp:134-140). */
    fun getUnlockedKV(): KV =
        unlockedKV ?: kv.scoped("achievements").scoped("unlocked").also { unlockedKV = it }
}
