package org.opentibia.canary.combat.condition

/**
 * Player-level condition suppression state, transcribing
 * `Player::isSuppress`/`addConditionSuppressions`/`removeConditionSuppressions`
 * and the `lastConditionTime` bookkeeping (`src/creatures/players/player.cpp:356-392,
 * 1143-1160`, `player.hpp:687-688, 831, 1724, 1784`).
 *
 * The `MIN_DELAY_BETWEEN_CONDITIONS` config value (`configmanager.cpp:321`) is
 * injected through [minDelayBetweenConditions] and the icon refresh
 * (`Player::sendIcons`) through [sendIcons], per AGENTS.md 1.1.
 */
class ConditionSuppression(
    private val minDelayBetweenConditions: Int = 0,
    private val now: () -> Long = { System.currentTimeMillis() },
    private val sendIcons: () -> Unit = {},
) {
    /**
     * `Player::m_conditionSuppressionCount`
     * (`player.hpp:1784`), indexed by `static_cast<size_t>(conditionType)`.
     */
    private val conditionSuppressionCount = IntArray(ConditionType.CONDITION_COUNT.value)

    /** `Player::lastConditionTime` (`player.hpp:1724`). */
    private val lastConditionTime = HashMap<Int, Long>()

    /**
     * `Player::isSuppress` (`player.cpp:356-364`). A suppressible condition is
     * blocked while inside the re-application window; any condition is blocked
     * while its suppression count is positive. `attackerPlayer` is unused in
     * Canary's `Player` override.
     */
    fun isSuppress(type: ConditionType, attackerPlayer: Boolean): Boolean {
        if (type.isSuppressible && checkLastConditionTimeWithin(type, minDelayBetweenConditions)) {
            return true
        }
        return suppressionCount(type) > 0
    }

    /** `Player::addConditionSuppressions` (`player.cpp:366-376`). */
    fun addConditionSuppressions(addConditions: Array<ConditionType>) {
        for (conditionType in addConditions) {
            val conditionId = conditionType.value
            if (conditionId >= ConditionType.CONDITION_COUNT.value) {
                continue
            }
            if (conditionSuppressionCount[conditionId] < UINT8_MAX) {
                conditionSuppressionCount[conditionId]++
            }
        }
    }

    /** `Player::removeConditionSuppressions` (`player.cpp:378-394`). */
    fun removeConditionSuppressions(toRemoveConditions: List<ConditionType>) {
        for (conditionType in toRemoveConditions) {
            if (conditionType == ConditionType.CONDITION_NONE) {
                continue
            }
            val conditionId = conditionType.value
            if (conditionId >= ConditionType.CONDITION_COUNT.value) {
                continue
            }
            if (conditionSuppressionCount[conditionId] > 0) {
                conditionSuppressionCount[conditionId]--
                if (conditionSuppressionCount[conditionId] == 0) {
                    sendIcons()
                }
            }
        }
    }

    /** `Player::getLastConditionTime` (`player.cpp:1143-1149`). */
    fun getLastConditionTime(type: ConditionType): Long = lastConditionTime[type.value] ?: 0

    /** `Player::updateLastConditionTime` (`player.cpp:1150-1152`). */
    fun updateLastConditionTime(type: ConditionType) {
        lastConditionTime[type.value] = now()
    }

    /** `Player::checkLastConditionTimeWithin` (`player.cpp:1154-1160`). */
    fun checkLastConditionTimeWithin(type: ConditionType, interval: Int): Boolean {
        val last = lastConditionTime[type.value] ?: return false
        return last > 0 && (now() - last) < interval
    }

    private fun suppressionCount(type: ConditionType): Int =
        conditionSuppressionCount.getOrElse(type.value) { 0 }

    private companion object {
        /** `UINT8_MAX` (`player.cpp:372`). */
        const val UINT8_MAX = 255
    }
}
