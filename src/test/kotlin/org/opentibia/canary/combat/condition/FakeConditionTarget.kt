package org.opentibia.canary.combat.condition

import org.opentibia.canary.map.Position

/**
 * Test double for the injected [ConditionTarget]/[ConditionPlayer] seams.
 *
 * It records every mutation the engine performs so the Canary arithmetic can be
 * asserted without a live world. It is intentionally *not* Canary's `Creature`.
 */
class FakeConditionTarget(
    health: Int = 1000,
    maxHealth: Int = 1000,
    mana: Int = 100,
    maxMana: Int = 500,
    var soul: Int = 0,
    baseSpeed: Int = 220,
    var zone: ZoneType = ZoneType.ZONE_NORMAL,
    var isPlayer: Boolean = true,
    var positionValue: Position = Position(100, 100, 7),
) : ConditionTarget, ConditionPlayer {

    var healthValue: Int = health
    var maxHealthValue: Int = maxHealth
    var manaValue: Int = mana
    var maxManaValue: Int = maxMana
    var baseSpeedValue: Int = baseSpeed

    /** Every `changeSpeed` delta in order. */
    val speedChanges: MutableList<Int> = mutableListOf()

    /** Sum of all applied speed deltas. */
    var speedDeltaTotal: Int = 0
        private set

    /** `daily-reward` kv storage. */
    val storage: MutableMap<String, Long> = mutableMapOf()

    var suppress: Boolean = false
    var attackable: Boolean = true

    val buffValues: MutableMap<Buff, Int> = mutableMapOf()
    val absorbValues: MutableMap<CombatType, Int> = mutableMapOf()
    val increaseValues: MutableMap<CombatType, Int> = mutableMapOf()

    var charmModifier: Int = 0
    var useDefenseValue: Boolean = true
    var manaShieldValue: Int = 0
    var maxManaShieldValue: Int = 0

    // Visual state (ConditionInvisible / ConditionOutfit / ConditionLight).
    var visible: Boolean = true
    var isInvisibleValue: Boolean = false
    val visibilityChanges: MutableList<Boolean> = mutableListOf()
    var outfitValue: Outfit = Outfit()
    val outfitChanges: MutableList<Outfit> = mutableListOf()
    var defaultOutfitValue: Outfit = Outfit(lookType = 136)
    var creatureLightInfo: LightInfo = LightInfo(0, 0)
    var lightChanges: Int = 0

    // ConditionPlayer observables.
    var baseMagicLevelValue: Int = 10
    var capacityValue: Double = 400.0
    val baseSkills: MutableMap<Skill, Int> = mutableMapOf()
    val varSkills: MutableMap<Skill, Int> = mutableMapOf()
    val varStats: MutableMap<Stat, Int> = mutableMapOf()
    var statsSent: Int = 0
    var skillsSent: Int = 0
    val monkData: MutableList<Pair<MonkData, Int>> = mutableListOf()
    val spellCooldowns: MutableList<Pair<Int, Int>> = mutableListOf()
    val spellGroupCooldowns: MutableList<Pair<SpellGroup, Int>> = mutableListOf()

    override val player: ConditionPlayer?
        get() = if (isPlayer) this else null

    override fun getHealth(): Int = healthValue

    override fun addHealth(amount: Int): Int {
        healthValue = minOf(maxHealthValue, healthValue + amount)
        return healthValue
    }

    override fun drainHealth(amount: Int): Int {
        healthValue = maxOf(0, healthValue - amount)
        return healthValue
    }

    override fun getMaxHealth(): Int = maxHealthValue

    override fun getMana(): Int = manaValue

    override fun addMana(amount: Int): Int {
        manaValue = minOf(maxManaValue, manaValue + amount)
        return manaValue
    }

    override fun getMaxMana(): Int = maxManaValue

    override fun changeSoul(amount: Int) {
        soul += amount
    }

    override fun getBaseSpeed(): Int = baseSpeedValue

    override fun setSpeed(delta: Int) {
        speedChanges.add(delta)
        speedDeltaTotal += delta
    }

    override fun getZoneType(): ZoneType = zone

    override fun getStorageValue(key: String): Long? = storage[key]

    override fun setStorageValue(key: String, value: Long) {
        storage[key] = value
    }

    override fun isSuppress(type: ConditionType, attackerIsPlayer: Boolean): Boolean = suppress

    override fun isAttackable(): Boolean = attackable

    override fun getPosition(): Position = positionValue

    var immuneFearTime: Long? = null

    override fun setImmuneFear(immuneTime: Long) {
        immuneFearTime = immuneTime
    }

    override fun getBuff(buff: Buff): Int = buffValues[buff] ?: 0

    override fun setBuff(buff: Buff, value: Int) {
        buffValues[buff] = value
    }

    override fun getAbsorbPercent(combat: CombatType): Int = absorbValues[combat] ?: 0

    override fun setAbsorbPercent(combat: CombatType, value: Int) {
        absorbValues[combat] = value
    }

    override fun getIncreasePercent(combat: CombatType): Int = increaseValues[combat] ?: 0

    override fun setIncreasePercent(combat: CombatType, value: Int) {
        increaseValues[combat] = value
    }

    override fun getCharmChanceModifier(): Int = charmModifier

    override fun setCharmChanceModifier(value: Int) {
        charmModifier = value
    }

    override fun setUseDefense(value: Boolean) {
        useDefenseValue = value
    }

    override fun setManaShield(value: Int) {
        manaShieldValue = value
    }

    override fun setMaxManaShield(value: Int) {
        maxManaShieldValue = value
    }

    override fun internalCreatureChangeVisible(visible: Boolean) {
        this.visible = visible
        visibilityChanges.add(visible)
    }

    override fun isInvisible(): Boolean = isInvisibleValue

    override fun internalCreatureChangeOutfit(outfit: Outfit) {
        outfitValue = outfit
        outfitChanges.add(outfit)
    }

    override fun getDefaultOutfit(): Outfit = defaultOutfitValue

    override fun getCreatureLight(): LightInfo = creatureLightInfo

    override fun setCreatureLight(lightInfo: LightInfo) {
        creatureLightInfo = lightInfo
    }

    override fun setNormalCreatureLight() {
        creatureLightInfo = LightInfo(0, 0)
    }

    override fun changeLight() {
        lightChanges++
    }

    override fun sendMonkData(type: MonkData, value: Int) {
        monkData.add(type to value)
    }

    override fun sendSpellCooldown(spellId: Int, time: Int) {
        spellCooldowns.add(spellId to time)
    }

    override fun sendSpellGroupCooldown(groupId: SpellGroup, time: Int) {
        spellGroupCooldowns.add(groupId to time)
    }

    override fun getBaseMagicLevel(): Int = baseMagicLevelValue

    override fun getCapacity(): Double = capacityValue

    override fun getBaseSkill(skill: Skill): Int = baseSkills[skill] ?: 0

    override fun setVarSkill(skill: Skill, value: Int) {
        varSkills[skill] = value
    }

    override fun setVarStats(stat: Stat, value: Int) {
        varStats[stat] = value
    }

    override fun sendStats() {
        statsSent++
    }

    override fun sendSkills() {
        skillsSent++
    }
}
