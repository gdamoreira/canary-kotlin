package org.opentibia.canary.combat

/**
 * Faithful port of Canary `struct CombatDamage`.
 *
 * The C++ anonymous `primary`/`secondary` sub-structs are flattened into
 * `primaryType`/`primaryValue` and `secondaryType`/`secondaryValue` (Kotlin has
 * no anonymous structs). All remaining fields keep their exact names, types and
 * defaults, including `affected = 1`.
 *
 * Source: src/creatures/creatures_definitions.hpp:1725-1758
 */
data class CombatDamage(
    var primaryType: CombatType = CombatType.NONE,
    var primaryValue: Int = 0,
    var secondaryType: CombatType = CombatType.NONE,
    var secondaryValue: Int = 0,
    var origin: CombatOrigin = CombatOrigin.NONE,
    var critical: Boolean = false,
    var affected: Int = 1,
    var extension: Boolean = false,
    var exString: String = "",
    var fatal: Boolean = false,
    var hazardDodge: Boolean = false,
    var criticalDamage: Int = 0,
    var criticalChance: Int = 0,
    var damageMultiplier: Int = 0,
    var damageReductionMultiplier: Int = 0,
    var healingMultiplier: Int = 0,
    var manaLeech: Int = 0,
    var manaLeechChance: Int = 0,
    var lifeLeech: Int = 0,
    var lifeLeechChance: Int = 0,
    var healingLink: Int = 0,
    var instantSpellName: String = "",
    var runeSpellName: String = "",
) {
    /**
     * C++ copy assignment (`CombatDamage::operator=(const CombatDamage&)`,
     * implicit): `Combat::doCombatHealth`/`doCombatMana` and `Combat::CombatFunc`
     * assign the extended copy back over the caller's value
     * (`damage = targetDamage`, combat.cpp:2773; `tmpDamage = extensionsDamage`,
     * combat.cpp:1585). Kotlin data classes only replace references, so the
     * in-place assignment Canary performs is spelled out here.
     */
    fun copyFrom(other: CombatDamage) {
        primaryType = other.primaryType
        primaryValue = other.primaryValue
        secondaryType = other.secondaryType
        secondaryValue = other.secondaryValue
        origin = other.origin
        critical = other.critical
        affected = other.affected
        extension = other.extension
        exString = other.exString
        fatal = other.fatal
        hazardDodge = other.hazardDodge
        criticalDamage = other.criticalDamage
        criticalChance = other.criticalChance
        damageMultiplier = other.damageMultiplier
        damageReductionMultiplier = other.damageReductionMultiplier
        healingMultiplier = other.healingMultiplier
        manaLeech = other.manaLeech
        manaLeechChance = other.manaLeechChance
        lifeLeech = other.lifeLeech
        lifeLeechChance = other.lifeLeechChance
        healingLink = other.healingLink
        instantSpellName = other.instantSpellName
        runeSpellName = other.runeSpellName
    }

    /**
     * Faithful port of `CombatDamage::isEmpty()`.
     *
     * Note: exactly like Canary, `hazardDodge` is intentionally absent from the
     * empty check (src/creatures/creatures_definitions.hpp:1756).
     */
    fun isEmpty(): Boolean = primaryType == CombatType.NONE &&
        primaryValue == 0 &&
        secondaryType == CombatType.NONE &&
        secondaryValue == 0 &&
        origin == CombatOrigin.NONE &&
        !critical &&
        affected == 1 &&
        !extension &&
        exString.isEmpty() &&
        !fatal &&
        criticalDamage == 0 &&
        criticalChance == 0 &&
        damageMultiplier == 0 &&
        damageReductionMultiplier == 0 &&
        healingMultiplier == 0 &&
        manaLeech == 0 &&
        manaLeechChance == 0 &&
        lifeLeech == 0 &&
        lifeLeechChance == 0 &&
        healingLink == 0 &&
        instantSpellName.isEmpty() &&
        runeSpellName.isEmpty()
}
