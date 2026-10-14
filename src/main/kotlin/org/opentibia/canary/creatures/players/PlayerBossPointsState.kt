package org.opentibia.canary.creatures.players

/**
 * Mirror of the bosstiary player counter block
 * (src/creatures/players/player.cpp:12673-12727, `player.hpp:1731`):
 *
 * - `Player::setBossPoints` (player.cpp:12673-12675)
 * - `Player::addBossPoints` (player.cpp:12677-12679)
 * - `Player::removeBossPoints` (player.cpp:12681-12683)
 * - `Player::getBossPoints` (player.cpp:12685-12687)
 * - `Player::setSlotBossId` (player.cpp:12695-12704)
 * - `Player::getSlotBossId` (player.cpp:12706-12712)
 * - `Player::addRemoveTime` (player.cpp:12714-12716)
 * - `Player::setRemoveBossTime` (player.cpp:12718-12720)
 * - `Player::getRemoveTimes` (player.cpp:12722-12724)
 *
 * The counters keep the C++ unsigned integer widths: `uint32_t bossPoints`,
 * `uint32_t bossIdSlotOne`/`bossIdSlotTwo` and `uint8_t bossRemoveTimes`. The
 * `client->parseSendBosstiarySlots()` call of [setSlotBossId] is the injected
 * callback (AGENTS.md 1.1).
 */
class PlayerBossPointsState {

    /** `uint32_t bossPoints = 0` (player.hpp:1731). */
    private var bossPoints: Long = 0L

    /** `uint32_t bossIdSlotOne = 0` (player.hpp:1731). */
    private var bossIdSlotOne: Long = 0L

    /** `uint32_t bossIdSlotTwo = 0` (player.hpp:1731). */
    private var bossIdSlotTwo: Long = 0L

    /** `uint8_t bossRemoveTimes = 0` (player.hpp:1731). */
    private var bossRemoveTimes: Int = 0

    /** `Player::setBossPoints` (player.cpp:12673-12675). */
    fun setBossPoints(amount: Long) {
        bossPoints = amount and UINT32_MASK
    }

    /** `Player::addBossPoints` (player.cpp:12677-12679); `uint32_t` wrap. */
    fun addBossPoints(amount: Long) {
        bossPoints = (bossPoints + amount) and UINT32_MASK
    }

    /**
     * `Player::removeBossPoints` (player.cpp:12681-12683):
     * `bossPoints = std::max<uint32_t>(0, bossPoints - amount)`. Because the
     * C++ subtraction is `uint32_t`, an amount above the balance wraps instead
     * of clamping; the transcription keeps that exact behavior.
     */
    fun removeBossPoints(amount: Long) {
        bossPoints = maxOf(0L, (bossPoints - amount) and UINT32_MASK)
    }

    /** `Player::getBossPoints` (player.cpp:12685-12687). */
    fun getBossPoints(): Long = bossPoints

    /** `Player::setSlotBossId` (player.cpp:12695-12704). */
    fun setSlotBossId(slotId: Int, bossId: Long, parseSendBosstiarySlots: () -> Unit = {}) {
        if (slotId == 1) {
            bossIdSlotOne = bossId and UINT32_MASK
        } else {
            bossIdSlotTwo = bossId and UINT32_MASK
        }

        parseSendBosstiarySlots()
    }

    /** `Player::getSlotBossId` (player.cpp:12706-12712). */
    fun getSlotBossId(slotId: Int): Long = if (slotId == 1) bossIdSlotOne else bossIdSlotTwo

    /** `Player::addRemoveTime` (player.cpp:12714-12716); `uint8_t` wrap. */
    fun addRemoveTime() {
        bossRemoveTimes = (bossRemoveTimes + 1) and UINT8_MASK
    }

    /** `Player::setRemoveBossTime` (player.cpp:12718-12720). */
    fun setRemoveBossTime(newRemoveTimes: Int) {
        bossRemoveTimes = newRemoveTimes and UINT8_MASK
    }

    /** `Player::getRemoveTimes` (player.cpp:12722-12724). */
    fun getRemoveTimes(): Int = bossRemoveTimes

    private companion object {
        const val UINT32_MASK = 0xFFFF_FFFFL
        const val UINT8_MASK = 0xFF
    }
}
