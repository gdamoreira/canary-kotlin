package org.opentibia.canary.systems.wheel

/**
 * Mirror of Canary's `IOWheel` deterministic engine data/logic
 * (src/io/io_wheel.cpp, src/io/io_wheel.hpp): revelation stats, slot priority
 * order, vocation detection, and the 36 per-slot vocation bonus functions.
 *
 * The spell-registration and gem sub-systems (io_wheel spell tables,
 * wheel_gems.cpp) and the protocol handlers are not ported here; see
 * docs/development/feature-history.md.
 */
class IOWheel {
    fun getRevelationStatByStage(stageType: WheelStageEnum): Pair<Int, Int> =
        REVELATION_STATS[stageType.value - 1]

    fun getSlotPrioritaryOrder(slot: WheelSlots): Int = when (slot) {
        in FIFTY_SLOTS -> 0
        in SEVENTY_FIVE_SLOTS -> 1
        in HUNDRED_SLOTS -> 2
        in HUNDRED_FIFTY_SLOTS -> 3
        in TWO_HUNDRED_SLOTS -> 4
        else -> -1
    }

    fun buildBonusData(wheel: PlayerWheel): PlayerWheelBonusData {
        val data = PlayerWheelBonusData()
        val vocationCipId = wheel.vocationCipId
        for (slot in WheelSlots.entries) {
            applySlot(data, wheel, slot, wheel.getPointsBySlotType(slot), vocationCipId)
        }
        return data
    }

    private fun isMaxPointAddedToSlot(wheel: PlayerWheel, points: Int, slot: WheelSlots): Boolean =
        points == wheel.getPointsBySlotType(slot) && points == wheel.getMaxPointsPerSlot(slot)

    private fun addSpell(data: PlayerWheelBonusData, wheel: PlayerWheel, slot: WheelSlots, points: Int, spellName: String) {
        if (isMaxPointAddedToSlot(wheel, points, slot)) {
            data.spells.add(spellName)
        }
    }

    private fun addVesselResonance(
        data: PlayerWheelBonusData,
        wheel: PlayerWheel,
        slot: WheelSlots,
        affinity: WheelGemAffinity,
        points: Int,
    ) {
        if (isMaxPointAddedToSlot(wheel, points, slot)) {
            data.unlockedVesselResonances[affinity.value]++
        }
    }

    @Suppress("CyclomaticComplexMethod", "LongMethod")
    private fun applySlot(
        data: PlayerWheelBonusData,
        wheel: PlayerWheel,
        slot: WheelSlots,
        points: Int,
        vocationCipId: Int,
    ) {
        val knight = isKnight(vocationCipId)
        val paladin = isPaladin(vocationCipId)
        val sorcerer = isSorcerer(vocationCipId)
        val druid = isDruid(vocationCipId)
        val monk = isMonk(vocationCipId)
        val max = isMaxPointAddedToSlot(wheel, points, slot)

        when (slot) {
            WheelSlots.SLOT_GREEN_200 -> when {
                knight -> {
                    data.stats.health += 3 * points; data.stats.mana += 1 * points
                    if (max) data.instant.battleInstinct = true
                }
                paladin -> {
                    data.stats.health += 2 * points; data.stats.mana += 3 * points
                    if (max) data.instant.positionalTactics = true
                }
                sorcerer || druid -> {
                    data.stats.health += 1 * points; data.stats.mana += 6 * points
                    if (max) { if (sorcerer) data.instant.runicMastery = true else data.instant.healingLink = true }
                }
                else -> {
                    data.stats.health += 2 * points; data.stats.mana += 2 * points
                    if (max) data.instant.guidingPresence = true
                }
            }

            WheelSlots.SLOT_GREEN_TOP_150 -> {
                data.mitigation += (MITIGATION_INCREASE * points).toFloat()
                if (max) data.leech.manaLeech += MANA_LEECH_INCREASE
            }

            WheelSlots.SLOT_GREEN_TOP_100 -> {
                data.stats.health += healthFor(knight, paladin || monk) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Green, points)
            }

            WheelSlots.SLOT_RED_TOP_100 -> {
                data.stats.mana += manaFor(knight, paladin, sorcerer || druid) * points
                if (max) {
                    when {
                        knight -> data.skills.melee += 1
                        paladin -> data.skills.distance += 1
                        sorcerer || druid -> data.skills.magic += 1
                        else -> data.skills.fist += 1
                    }
                }
            }

            WheelSlots.SLOT_RED_TOP_150 -> {
                data.stats.health += healthFor(knight, paladin || monk) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Red, points)
            }

            WheelSlots.SLOT_RED_200 -> when {
                knight -> {
                    addSpell(data, wheel, slot, points, "Front Sweep")
                    data.stats.health += 3 * points; data.stats.mana += 1 * points
                }
                paladin -> {
                    addSpell(data, wheel, slot, points, "Sharpshooter")
                    data.stats.health += 2 * points; data.stats.mana += 3 * points
                }
                sorcerer || druid -> {
                    if (sorcerer) FOCUS_SPELLS.forEach { addSpell(data, wheel, slot, points, it) }
                    else addSpell(data, wheel, slot, points, "Strong Ice Wave")
                    data.stats.health += 1 * points; data.stats.mana += 6 * points
                }
                else -> {
                    addSpell(data, wheel, slot, points, "Sweeping Takedown")
                    data.stats.health += 2 * points; data.stats.mana += 2 * points
                }
            }

            WheelSlots.SLOT_GREEN_BOTTOM_150 -> {
                data.mitigation += (MITIGATION_INCREASE * points).toFloat()
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Green, points)
            }

            WheelSlots.SLOT_GREEN_MIDDLE_100 -> when {
                knight -> {
                    addSpell(data, wheel, slot, points, "Groundshaker"); data.stats.health += 3 * points
                }
                paladin || monk -> {
                    if (paladin) addSpell(data, wheel, slot, points, "Strong Ethereal Spear")
                    else addSpell(data, wheel, slot, points, "Mass Spirit Mend")
                    data.stats.health += 2 * points
                }
                else -> {
                    if (sorcerer) addSpell(data, wheel, slot, points, "Magic Shield")
                    else addSpell(data, wheel, slot, points, "Mass Healing")
                    data.stats.health += 1 * points
                }
            }

            WheelSlots.SLOT_GREEN_TOP_75 -> {
                data.stats.mana += manaFor(knight, paladin, sorcerer || druid) * points
                if (max) data.leech.lifeLeech += HEALTH_LEECH_INCREASE
            }

            WheelSlots.SLOT_RED_TOP_75 -> {
                data.stats.capacity += capacityFor(knight || monk, paladin) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Red, points)
            }

            WheelSlots.SLOT_RED_MIDDLE_100 -> when {
                knight -> {
                    addSpell(data, wheel, slot, points, "Chivalrous Challenge"); data.stats.mana += 1 * points
                }
                paladin -> {
                    addSpell(data, wheel, slot, points, "Divine Dazzle"); data.stats.mana += 3 * points
                }
                sorcerer || druid -> {
                    if (sorcerer) addSpell(data, wheel, slot, points, "Sap Strength")
                    else addSpell(data, wheel, slot, points, "Nature's Embrace")
                    data.stats.mana += 6 * points
                }
                else -> {
                    addSpell(data, wheel, slot, points, "Mystic Repulse"); data.stats.mana += 2 * points
                }
            }

            WheelSlots.SLOT_RED_BOTTOM_150 -> {
                data.stats.health += healthFor(knight, paladin || monk) * points
                if (max) data.leech.manaLeech += MANA_LEECH_INCREASE
            }

            WheelSlots.SLOT_GREEN_BOTTOM_100 -> when {
                knight -> {
                    addSpell(data, wheel, slot, points, "Intense Wound Cleansing"); data.stats.health += 3 * points
                }
                paladin || monk -> {
                    if (paladin) addSpell(data, wheel, slot, points, "Swift Foot")
                    else addSpell(data, wheel, slot, points, "Chained Penance")
                    data.stats.health += 2 * points
                }
                else -> {
                    if (sorcerer) addSpell(data, wheel, slot, points, "Energy Wave")
                    else addSpell(data, wheel, slot, points, "Terra Wave")
                    data.stats.health += 1 * points
                }
            }

            WheelSlots.SLOT_GREEN_BOTTOM_75 -> {
                data.stats.mana += manaFor(knight, paladin, sorcerer || druid) * points
                if (max) {
                    when {
                        knight -> data.skills.melee += 1
                        paladin -> data.skills.distance += 1
                        sorcerer || druid -> data.skills.magic += 1
                        else -> data.skills.fist += 1
                    }
                }
            }

            WheelSlots.SLOT_GREEN_50 -> {
                data.stats.capacity += capacityFor(knight || monk, paladin) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Green, points)
            }

            WheelSlots.SLOT_RED_50 -> {
                data.mitigation += (MITIGATION_INCREASE * points).toFloat()
                when {
                    knight -> addSpell(data, wheel, slot, points, "Fierce Berserk")
                    paladin -> addSpell(data, wheel, slot, points, "Divine Caldera")
                    sorcerer -> addSpell(data, wheel, slot, points, "Great Fire Wave")
                    druid -> addSpell(data, wheel, slot, points, "Heal Friend")
                    else -> addSpell(data, wheel, slot, points, "Flurry of Blows")
                }
            }

            WheelSlots.SLOT_RED_BOTTOM_75 -> {
                data.stats.capacity += capacityFor(knight || monk, paladin) * points
                if (max) data.leech.lifeLeech += HEALTH_LEECH_INCREASE
            }

            WheelSlots.SLOT_RED_BOTTOM_100 -> {
                data.stats.mana += manaFor(knight, paladin, sorcerer || druid) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Red, points)
            }

            WheelSlots.SLOT_BLUE_TOP_100 -> {
                data.mitigation += (MITIGATION_INCREASE * points).toFloat()
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Blue, points)
            }

            WheelSlots.SLOT_BLUE_TOP_75 -> {
                data.stats.health += healthFor(knight, paladin || monk) * points
                if (max) data.leech.manaLeech += MANA_LEECH_INCREASE
            }

            WheelSlots.SLOT_BLUE_50 -> when {
                knight -> {
                    addSpell(data, wheel, slot, points, "Front Sweep"); data.stats.mana += 1 * points
                }
                paladin -> {
                    addSpell(data, wheel, slot, points, "Sharpshooter"); data.stats.mana += 3 * points
                }
                sorcerer || druid -> {
                    if (sorcerer) FOCUS_SPELLS.forEach { addSpell(data, wheel, slot, points, it) }
                    else addSpell(data, wheel, slot, points, "Strong Ice Wave")
                    data.stats.mana += 6 * points
                }
                else -> {
                    addSpell(data, wheel, slot, points, "Sweeping Takedown"); data.stats.mana += 2 * points
                }
            }

            WheelSlots.SLOT_PURPLE_50 -> {
                data.stats.health += healthFor(knight, paladin || monk) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Purple, points)
            }

            WheelSlots.SLOT_PURPLE_TOP_75 -> {
                data.mitigation += (MITIGATION_INCREASE * points).toFloat()
                if (max) {
                    when {
                        knight -> data.skills.melee += 1
                        paladin -> data.skills.distance += 1
                        sorcerer || druid -> data.skills.magic += 1
                        else -> data.skills.fist += 1
                    }
                }
            }

            WheelSlots.SLOT_PURPLE_TOP_100 -> when {
                knight || monk -> {
                    if (knight) addSpell(data, wheel, slot, points, "Groundshaker")
                    else addSpell(data, wheel, slot, points, "Mass Spirit Mend")
                    data.stats.capacity += 5 * points
                }
                paladin -> {
                    addSpell(data, wheel, slot, points, "Strong Ethereal Spear"); data.stats.capacity += 4 * points
                }
                else -> {
                    if (sorcerer) addSpell(data, wheel, slot, points, "Magic Shield")
                    else addSpell(data, wheel, slot, points, "Mass Healing")
                    data.stats.capacity += 2 * points
                }
            }

            WheelSlots.SLOT_BLUE_TOP_150 -> {
                data.stats.capacity += capacityFor(knight || monk, paladin) * points
                if (max) data.leech.lifeLeech += HEALTH_LEECH_INCREASE
            }

            WheelSlots.SLOT_BLUE_MIDDLE_100 -> {
                data.mitigation += (MITIGATION_INCREASE * points).toFloat()
                when {
                    knight -> addSpell(data, wheel, slot, points, "Chivalrous Challenge")
                    paladin -> addSpell(data, wheel, slot, points, "Divine Dazzle")
                    sorcerer -> addSpell(data, wheel, slot, points, "Sap Strength")
                    druid -> addSpell(data, wheel, slot, points, "Nature's Embrace")
                    else -> addSpell(data, wheel, slot, points, "Mystic Repulse")
                }
            }

            WheelSlots.SLOT_BLUE_BOTTOM_75 -> {
                data.stats.health += healthFor(knight, paladin || monk) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Blue, points)
            }

            WheelSlots.SLOT_PURPLE_BOTTOM_75 -> {
                data.mitigation += (MITIGATION_INCREASE * points).toFloat()
                if (max) data.leech.manaLeech += MANA_LEECH_INCREASE
            }

            WheelSlots.SLOT_PURPLE_MIDDLE_100 -> when {
                knight || monk -> {
                    if (knight) addSpell(data, wheel, slot, points, "Intense Wound Cleansing")
                    else addSpell(data, wheel, slot, points, "Chained Penance")
                    data.stats.capacity += 5 * points
                }
                paladin -> {
                    addSpell(data, wheel, slot, points, "Swift Foot"); data.stats.capacity += 4 * points
                }
                else -> {
                    if (sorcerer) addSpell(data, wheel, slot, points, "Energy Wave")
                    else addSpell(data, wheel, slot, points, "Terra Wave")
                    data.stats.capacity += 2 * points
                }
            }

            WheelSlots.SLOT_PURPLE_TOP_150 -> {
                data.stats.mana += manaFor(knight, paladin, sorcerer || druid) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Purple, points)
            }

            WheelSlots.SLOT_BLUE_200 -> when {
                knight -> {
                    addSpell(data, wheel, slot, points, "Fierce Berserk")
                    data.stats.health += 3 * points; data.stats.mana += 1 * points
                }
                paladin -> {
                    addSpell(data, wheel, slot, points, "Divine Caldera")
                    data.stats.health += 2 * points; data.stats.mana += 3 * points
                }
                sorcerer || druid -> {
                    if (sorcerer) addSpell(data, wheel, slot, points, "Great Fire Wave")
                    else addSpell(data, wheel, slot, points, "Heal Friend")
                    data.stats.health += 1 * points; data.stats.mana += 6 * points
                }
                else -> {
                    addSpell(data, wheel, slot, points, "Flurry of Blows")
                    data.stats.health += 2 * points; data.stats.mana += 2 * points
                }
            }

            WheelSlots.SLOT_BLUE_BOTTOM_150 -> {
                data.stats.capacity += capacityFor(knight || monk, paladin) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Blue, points)
            }

            WheelSlots.SLOT_BLUE_BOTTOM_100 -> {
                data.mitigation += (MITIGATION_INCREASE * points).toFloat()
                if (max) {
                    when {
                        knight -> data.skills.melee += 1
                        paladin -> data.skills.distance += 1
                        sorcerer || druid -> data.skills.magic += 1
                        monk -> data.skills.fist += 1
                    }
                }
            }

            WheelSlots.SLOT_PURPLE_BOTTOM_100 -> {
                data.stats.capacity += capacityFor(knight || monk, paladin) * points
                addVesselResonance(data, wheel, slot, WheelGemAffinity.Purple, points)
            }

            WheelSlots.SLOT_PURPLE_BOTTOM_150 -> {
                data.stats.mana += manaFor(knight, paladin, sorcerer || druid) * points
                if (max) data.leech.lifeLeech += HEALTH_LEECH_INCREASE
            }

            WheelSlots.SLOT_PURPLE_200 -> when {
                knight -> {
                    data.stats.health += 3 * points; data.stats.mana += 1 * points
                    if (max) data.instant.battleHealing = true
                }
                paladin -> {
                    data.stats.health += 2 * points; data.stats.mana += 3 * points
                    if (max) data.instant.ballisticMastery = true
                }
                sorcerer || druid -> {
                    data.stats.health += 1 * points; data.stats.mana += 6 * points
                    if (max) { if (sorcerer) data.instant.focusMastery = true else data.instant.runicMastery = true }
                }
                else -> {
                    data.stats.health += 2 * points; data.stats.mana += 2 * points
                    if (max) data.instant.sanctuary = true
                }
            }
        }
    }

    private fun healthFor(knight: Boolean, paladinOrMonk: Boolean): Int = when {
        knight -> 3
        paladinOrMonk -> 2
        else -> 1
    }

    private fun manaFor(knight: Boolean, paladin: Boolean, sorcererOrDruid: Boolean): Int = when {
        knight -> 1
        paladin -> 3
        sorcererOrDruid -> 6
        else -> 2
    }

    private fun capacityFor(knightOrMonk: Boolean, paladin: Boolean): Int = when {
        knightOrMonk -> 5
        paladin -> 4
        else -> 2
    }

    companion object {
        const val MITIGATION_INCREASE = 0.03
        const val MANA_LEECH_INCREASE = 0.25
        const val HEALTH_LEECH_INCREASE = 0.75

        val FOCUS_SPELLS: List<String> = listOf(
            "Eternal Winter",
            "Hell's Core",
            "Rage of the Skies",
            "Wrath of Nature",
        )

        const val VOCATION_KNIGHT_CIP = 1
        const val VOCATION_PALADIN_CIP = 2
        const val VOCATION_SORCERER_CIP = 3
        const val VOCATION_DRUID_CIP = 4
        const val VOCATION_MONK_CIP = 5

        private val REVELATION_STATS: List<Pair<Int, Int>> = listOf(
            4 to 4,
            9 to 9,
            20 to 20,
        )

        private val FIFTY_SLOTS = setOf(
            WheelSlots.SLOT_BLUE_50, WheelSlots.SLOT_RED_50,
            WheelSlots.SLOT_PURPLE_50, WheelSlots.SLOT_GREEN_50,
        )
        private val SEVENTY_FIVE_SLOTS = setOf(
            WheelSlots.SLOT_GREEN_TOP_75, WheelSlots.SLOT_GREEN_BOTTOM_75,
            WheelSlots.SLOT_RED_TOP_75, WheelSlots.SLOT_RED_BOTTOM_75,
            WheelSlots.SLOT_PURPLE_TOP_75, WheelSlots.SLOT_PURPLE_BOTTOM_75,
            WheelSlots.SLOT_BLUE_TOP_75, WheelSlots.SLOT_BLUE_BOTTOM_75,
        )
        private val HUNDRED_SLOTS = setOf(
            WheelSlots.SLOT_GREEN_BOTTOM_100, WheelSlots.SLOT_GREEN_MIDDLE_100,
            WheelSlots.SLOT_GREEN_TOP_100, WheelSlots.SLOT_RED_BOTTOM_100,
            WheelSlots.SLOT_RED_MIDDLE_100, WheelSlots.SLOT_RED_TOP_100,
            WheelSlots.SLOT_PURPLE_BOTTOM_100, WheelSlots.SLOT_PURPLE_MIDDLE_100,
            WheelSlots.SLOT_PURPLE_TOP_100, WheelSlots.SLOT_BLUE_BOTTOM_100,
            WheelSlots.SLOT_BLUE_MIDDLE_100, WheelSlots.SLOT_BLUE_TOP_100,
        )
        private val HUNDRED_FIFTY_SLOTS = setOf(
            WheelSlots.SLOT_GREEN_TOP_150, WheelSlots.SLOT_GREEN_BOTTOM_150,
            WheelSlots.SLOT_RED_TOP_150, WheelSlots.SLOT_RED_BOTTOM_150,
            WheelSlots.SLOT_PURPLE_TOP_150, WheelSlots.SLOT_PURPLE_BOTTOM_150,
            WheelSlots.SLOT_BLUE_TOP_150, WheelSlots.SLOT_BLUE_BOTTOM_150,
        )
        private val TWO_HUNDRED_SLOTS = setOf(
            WheelSlots.SLOT_GREEN_200, WheelSlots.SLOT_RED_200,
            WheelSlots.SLOT_PURPLE_200, WheelSlots.SLOT_BLUE_200,
        )
    }

    fun isKnight(vocationCipId: Int): Boolean = vocationCipId == VOCATION_KNIGHT_CIP
    fun isPaladin(vocationCipId: Int): Boolean = vocationCipId == VOCATION_PALADIN_CIP
    fun isSorcerer(vocationCipId: Int): Boolean = vocationCipId == VOCATION_SORCERER_CIP
    fun isDruid(vocationCipId: Int): Boolean = vocationCipId == VOCATION_DRUID_CIP
    fun isMonk(vocationCipId: Int): Boolean = vocationCipId == VOCATION_MONK_CIP
}
