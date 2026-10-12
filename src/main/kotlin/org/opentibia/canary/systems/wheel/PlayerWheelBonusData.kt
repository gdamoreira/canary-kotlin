package org.opentibia.canary.systems.wheel

/**
 * Mirror of Canary's `PlayerWheelMethodsBonusData`
 * (src/creatures/players/components/wheel/wheel_definitions.hpp).
 */
data class PlayerWheelBonusData(
    val stats: Stats = Stats(),
    val skills: Skills = Skills(),
    val leech: Leech = Leech(),
    val instant: Instant = Instant(),
    val stages: Stages = Stages(),
    val avatar: Avatar = Avatar(),
    val unlockedVesselResonances: IntArray = IntArray(4),
    var momentum: Float = 0f,
    var mitigation: Float = 0f,
    val spells: MutableList<String> = mutableListOf(),
) {
    data class Stats(
        var health: Int = 0,
        var mana: Int = 0,
        var capacity: Int = 0,
        var damage: Int = 0,
        var healing: Int = 0,
    )

    data class Skills(
        var melee: Int = 0,
        var distance: Int = 0,
        var magic: Int = 0,
        var fist: Int = 0,
    )

    data class Leech(
        var manaLeech: Double = 0.0,
        var lifeLeech: Double = 0.0,
    )

    data class Instant(
        var battleInstinct: Boolean = false,
        var battleHealing: Boolean = false,
        var positionalTactics: Boolean = false,
        var ballisticMastery: Boolean = false,
        var healingLink: Boolean = false,
        var runicMastery: Boolean = false,
        var focusMastery: Boolean = false,
        var sanctuary: Boolean = false,
        var guidingPresence: Boolean = false,
    )

    data class Stages(
        var combatMastery: Int = 0,
        var giftOfLife: Int = 0,
        var divineEmpowerment: Int = 0,
        var divineGrenade: Int = 0,
        var blessingOfTheGrove: Int = 0,
        var drainBody: Int = 0,
        var beamMastery: Int = 0,
        var twinBurst: Int = 0,
        var executionersThrow: Int = 0,
        var spiritualOutburst: Int = 0,
        var ascetic: Int = 0,
    )

    data class Avatar(
        var light: Int = 0,
        var nature: Int = 0,
        var steel: Int = 0,
        var storm: Int = 0,
        var balance: Int = 0,
    )

    override fun equals(other: Any?): Boolean = this === other
    override fun hashCode(): Int = System.identityHashCode(this)
}
