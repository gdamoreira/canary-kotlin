package org.opentibia.canary.systems.concoction

/**
 * Mirror of Canary's `Concoction_t` (src/utils/utils_definitions.hpp) and the
 * Player active-concoction state (src/creatures/players/player.cpp
 * updateConcoction / getActiveConcoctions / isConcoctionActive).
 *
 * The concoction effect configuration and registration are authored in Lua
 * (data/libs/systems/concoctions.lua, data/scripts/systems/concoctions.lua) and
 * stay Lua per AGENTS.md 1.4.
 */
enum class ConcoctionType(val itemId: Int) {
    KooldownAid(36723),
    StaminaExtension(36725),
    StrikeEnhancement(36724),
    CharmUpgrade(36726),
    WealthDuplex(36727),
    BestiaryBetterment(36728),
    FireResilience(36729),
    IceResilience(36730),
    EarthResilience(36731),
    EnergyResilience(36732),
    HolyResilience(36733),
    DeathResilience(36734),
    PhysicalResilience(36735),
    FireAmplification(36736),
    IceAmplification(36737),
    EarthAmplification(36738),
    EnergyAmplification(36739),
    HolyAmplification(36740),
    DeathAmplification(36741),
    PhysicalAmplification(36742),
}

class PlayerConcoctions {
    private val activeConcoctions = mutableMapOf<Int, Int>()

    fun updateConcoction(itemId: Int, timeLeft: Int) {
        if (timeLeft == 0) {
            activeConcoctions.remove(itemId)
        } else {
            activeConcoctions[itemId] = timeLeft
        }
    }

    fun getActiveConcoctions(): Map<Int, Int> = activeConcoctions.toMap()

    fun isConcoctionActive(concoction: ConcoctionType): Boolean {
        val itemId = concoction.itemId
        val timeLeft = activeConcoctions[itemId] ?: return false
        return timeLeft > 0
    }
}
