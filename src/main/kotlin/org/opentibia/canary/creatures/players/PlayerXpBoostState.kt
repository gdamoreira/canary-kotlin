package org.opentibia.canary.creatures.players

/**
 * Self-contained mirror of the Player experience-boost state
 * (src/creatures/players/player.cpp:3203-3273).
 *
 * The class is intentionally *not* `Player.kt` (per task constraints). Canary
 * keeps `baseXpGain`, `voucherXpBoost`, `grindingXpBoost`, `xpBoostPercent`,
 * `staminaXpBoost` and `xpBoostTime` directly on `Player` (player.hpp:1813,
 * 1820-1824); the state is exposed as an injectable seam so the host player can
 * delegate without a global (AGENTS.md 1.1). `Player::sendStats()`
 * (player.cpp:3245, 3266) is the only out-of-package side effect and is a
 * constructor seam.
 *
 * The methods keep the exact `Player::` names of the C++ symbols they
 * transcribe, so every behavior is auditable against the C++ line references.
 */
class PlayerXpBoostState(
    /** `Player::sendStats()` (player.cpp:3245, 3266) protocol seam. */
    private val sendStats: () -> Unit = {},
) {

    /** `Player::baseXpGain` (player.hpp:1820); default 100. */
    private var baseXpGain = 100

    /** `Player::voucherXpBoost` (player.hpp:1821). */
    private var voucherXpBoost = 0

    /** `Player::grindingXpBoost` (player.hpp:1822). */
    private var grindingXpBoost = 0

    /** `Player::xpBoostPercent` (player.hpp:1823). */
    private var xpBoostPercent = 0

    /** `Player::staminaXpBoost` (player.hpp:1824); default 100. */
    private var staminaXpBoost = 100

    /** `Player::xpBoostTime` (player.hpp:1813). */
    private var xpBoostTime = 0

    /** `Player::getBaseXpGain` (player.cpp:3203-3205). */
    fun getBaseXpGain(): Int = baseXpGain

    /** `Player::setBaseXpGain` (player.cpp:3207-3209). */
    fun setBaseXpGain(value: Int) {
        baseXpGain = minOf(UINT16_MAX, value and UINT16_MAX)
    }

    /** `Player::getVoucherXpBoost` (player.cpp:3211-3213). */
    fun getVoucherXpBoost(): Int = voucherXpBoost

    /** `Player::setVoucherXpBoost` (player.cpp:3215-3217). */
    fun setVoucherXpBoost(value: Int) {
        voucherXpBoost = minOf(UINT16_MAX, value and UINT16_MAX)
    }

    /** `Player::getGrindingXpBoost` (player.cpp:3219-3221). */
    fun getGrindingXpBoost(): Int = grindingXpBoost

    /**
     * `Player::getDisplayGrindingXpBoost` (player.cpp:3223-3225):
     * `std::clamp<uint16_t>(grindingXpBoost * (baseXpGain / 100), 0, max)`.
     * The division is the C++ integer division and the widened product is
     * clamped back into the `uint16_t` range.
     */
    fun getDisplayGrindingXpBoost(): Int =
        minOf(UINT16_MAX, maxOf(0, grindingXpBoost * (baseXpGain / 100)))

    /** `Player::setGrindingXpBoost` (player.cpp:3227-3229). */
    fun setGrindingXpBoost(value: Int) {
        grindingXpBoost = minOf(UINT16_MAX, value and UINT16_MAX)
    }

    /** `Player::getXpBoostPercent` (player.cpp:3231-3233). */
    fun getXpBoostPercent(): Int = xpBoostPercent

    /**
     * `Player::getDisplayXpBoostPercent` (player.cpp:3235-3241): zero while
     * `xpBoostTime` is zero, otherwise the same clamped scaling as
     * [getDisplayGrindingXpBoost].
     */
    fun getDisplayXpBoostPercent(): Int {
        if (xpBoostTime == 0) {
            return 0
        }
        return minOf(UINT16_MAX, maxOf(0, xpBoostPercent * (baseXpGain / 100)))
    }

    /** `Player::setXpBoostPercent` (player.cpp:3243-3246); sends stats. */
    fun setXpBoostPercent(percent: Int) {
        xpBoostPercent = percent and UINT16_MAX
        sendStats()
    }

    /** `Player::getStaminaXpBoost` (player.cpp:3248-3250). */
    fun getStaminaXpBoost(): Int = staminaXpBoost

    /** `Player::setStaminaXpBoost` (player.cpp:3252-3254). */
    fun setStaminaXpBoost(value: Int) {
        staminaXpBoost = minOf(UINT16_MAX, value and UINT16_MAX)
    }

    /**
     * `Player::setXpBoostTime` (player.cpp:3256-3269): boosts longer than
     * twelve hours are capped and `sendStats()` only runs when the
     * "has an active boost" state flips.
     */
    fun setXpBoostTime(timeLeft: Int) {
        val hadXpBoost = xpBoostTime > 0
        val requested = timeLeft and UINT16_MAX

        // only allow time boosts of 12 hours or less
        xpBoostTime = if (requested > MAX_XP_BOOST_TIME) MAX_XP_BOOST_TIME else requested

        if (hadXpBoost != (xpBoostTime > 0)) {
            sendStats()
        }
    }

    /** `Player::getXpBoostTime` (player.cpp:3271-3273). */
    fun getXpBoostTime(): Int = xpBoostTime

    companion object {
        /** `std::numeric_limits<uint16_t>::max()`. */
        private const val UINT16_MAX = 0xFFFF

        /** `12 * 3600` (player.cpp:3259). */
        private const val MAX_XP_BOOST_TIME = 12 * 3600
    }
}
