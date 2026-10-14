package org.opentibia.canary.creatures.players.animus_mastery

import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.utils.Tools

/**
 * Mirror of Canary `AnimusMastery`
 * (`src/creatures/players/animus_mastery/animus_mastery.hpp:16-45`,
 * animus_mastery.cpp:17-74): the Soul War animus-mastery monster set, its
 * experience multiplier and the `players.animus_mastery` blob codec.
 *
 * Canary's `Player &m_player` constructor member (animus_mastery.cpp:18) is
 * stored but never read, so the port does not carry it. `AnimusMastery::init()`
 * (animus_mastery.cpp:20-25) is folded into the constructor: the four
 * `g_configManager()` reads become constructor parameters per AGENTS.md 1.1
 * and run the same `std::clamp<uint16_t>` on [monstersAmountToMultiply].
 *
 * PARITY NOTE: Canary's `std::clamp<uint16_t>` converts the config number to
 * `uint16_t` *before* clamping (animus_mastery.cpp:24), so values above 65535
 * wrap modulo 65536; the port masks with `and 0xFFFF` first to reproduce that.
 * A zero `divisor` cannot happen through config (it clamps to 1); direct
 * construction with a wrapped zero is clamped the same way.
 */
class AnimusMastery(
    /**
     * `maxMonsterXpMultiplier`, header default 4.0
     * (`ANIMUS_MASTERY_MAX_MONSTER_XP_MULTIPLIER`, configmanager.cpp:222).
     */
    private val maxMonsterXpMultiplier: Float = 4.0f,
    /**
     * `monsterXpMultiplier`, header default 2.0
     * (`ANIMUS_MASTERY_MONSTER_XP_MULTIPLIER`, configmanager.cpp:223).
     */
    private val monsterXpMultiplier: Float = 2.0f,
    /**
     * `monstersXpMultiplier`, header default 0.1
     * (`ANIMUS_MASTERY_MONSTERS_XP_MULTIPLIER`, configmanager.cpp:224).
     */
    private val monstersXpMultiplier: Float = 0.1f,
    /**
     * `monstersAmountToMultiply`, header default 10
     * (`ANIMUS_MASTERY_MONSTERS_TO_INCREASE_XP_MULTIPLIER`, configmanager.cpp:390).
     */
    monstersAmountToMultiply: Int = 10,
) {
    /** `monstersAmountToMultiply` (animus_mastery.hpp:42) after the C++ `std::clamp<uint16_t>`. */
    private val monstersAmountToMultiply: Int = (monstersAmountToMultiply and 0xFFFF).coerceIn(1, 0xFFFF)

    /** `animusMasteries` (animus_mastery.hpp:44). */
    private val animusMasteries = mutableListOf<String>()

    /** `AnimusMastery::add` (animus_mastery.cpp:27-32). */
    fun add(addMonsterType: String) {
        if (!has(addMonsterType)) {
            animusMasteries.add(Tools.asLowerCaseString(addMonsterType))
        }
    }

    /** `AnimusMastery::remove` (animus_mastery.cpp:34-39): `std::erase_if` drops every match. */
    fun remove(removeMonsterType: String) {
        val lowerMonsterName = Tools.asLowerCaseString(removeMonsterType)
        animusMasteries.removeAll { Tools.asLowerCaseString(it) == lowerMonsterName }
    }

    /** `AnimusMastery::has` (animus_mastery.cpp:41-46). */
    fun has(searchMonsterType: String): Boolean =
        animusMasteries.contains(Tools.asLowerCaseString(searchMonsterType))

    /** `AnimusMastery::getExperienceMultiplier` (animus_mastery.cpp:48-52). */
    fun getExperienceMultiplier(): Float {
        val monsterAmountMultiplier = (animusMasteries.size / monstersAmountToMultiply) and 0xFFFF
        return minOf(
            maxMonsterXpMultiplier,
            1 + (monsterXpMultiplier + (monsterAmountMultiplier * monstersXpMultiplier)) / 100,
        )
    }

    /** `AnimusMastery::getPoints` (animus_mastery.cpp:54-56): `size()` narrowed to `uint16_t`. */
    fun getPoints(): Int = animusMasteries.size and 0xFFFF

    /** `AnimusMastery::getAnimusMasteries` (animus_mastery.cpp:58-60): a live view, like the const reference. */
    fun getAnimusMasteries(): List<String> = animusMasteries

    /** `AnimusMastery::serialize` (animus_mastery.cpp:62-66). */
    fun serialize(propWriteStream: PropWriteStream) {
        for (monsterName in animusMasteries) {
            propWriteStream.writeString(monsterName)
        }
    }

    /** `AnimusMastery::unserialize` (animus_mastery.cpp:68-73): appends without clearing. */
    fun unserialize(propStream: PropStream): Boolean {
        while (true) {
            val monsterName = propStream.readString() ?: break
            animusMasteries.add(monsterName)
        }
        return true
    }
}
