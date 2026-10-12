package org.opentibia.canary.systems

enum class ImbuementType {
    CriticalHit,
    LifeLeech,
    ManaLeech,
    ElementalProtection,
    SkillBoost
}

class ImbuementSlot(
    val type: ImbuementType,
    val tier: Int = 1,
    var durationSeconds: Int = 72000
) {
    val isActive: Boolean get() = durationSeconds > 0

    fun decay(seconds: Int) {
        durationSeconds = kotlin.math.max(0, durationSeconds - seconds)
    }
}

enum class PreyBonusType {
    DamageBoost,
    DamageReduction,
    BonusExperience,
    ImprovedLoot
}

class PreySlot(
    val slotId: Int,
    val monsterName: String,
    val bonusType: PreyBonusType,
    val bonusPercentage: Int,
    var durationMinutes: Int = 120
) {
    val isActive: Boolean get() = durationMinutes > 0
}
