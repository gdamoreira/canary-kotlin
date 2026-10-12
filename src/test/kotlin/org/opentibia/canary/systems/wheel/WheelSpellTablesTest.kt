package org.opentibia.canary.systems.wheel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class WheelSpellTablesTest {

    @Test
    fun `each vocation has five wheel spells with three grade slots`() {
        assertThat(WheelSpellTables.druid).hasSize(5)
        assertThat(WheelSpellTables.knight).hasSize(5)
        assertThat(WheelSpellTables.paladin).hasSize(5)
        assertThat(WheelSpellTables.sorcerer).hasSize(5)
        assertThat(WheelSpellTables.monk).hasSize(5)
        for (table in listOf(WheelSpellTables.druid, WheelSpellTables.knight, WheelSpellTables.paladin, WheelSpellTables.sorcerer, WheelSpellTables.monk)) {
            table.forEach { assertThat(it.grade).hasSize(3) }
        }
    }

    @Test
    fun `druid spell table matches canary initializeDruidSpells`() {
        val strongIceWave = WheelSpellTables.druid[0]
        assertThat(strongIceWave.name).isEqualTo("Strong Ice Wave")
        assertThat(strongIceWave.grade[1].leech.mana).isEqualTo(3)
        assertThat(strongIceWave.grade[2].increase.damage).isEqualTo(10)

        val massHealing = WheelSpellTables.druid[1]
        assertThat(massHealing.name).isEqualTo("Mass Healing")
        assertThat(massHealing.grade[1].increase.heal).isEqualTo(4)
        assertThat(massHealing.grade[2].increase.area).isTrue()

        val naturesEmbrace = WheelSpellTables.druid[2]
        assertThat(naturesEmbrace.name).isEqualTo("Nature's Embrace")
        assertThat(naturesEmbrace.grade[1].increase.heal).isEqualTo(11)
        assertThat(naturesEmbrace.grade[2].decrease.cooldown).isEqualTo(10)

        val terraWave = WheelSpellTables.druid[3]
        assertThat(terraWave.name).isEqualTo("Terra Wave")
        assertThat(terraWave.grade[1].increase.damage).isEqualTo(7)
        assertThat(terraWave.grade[2].leech.life).isEqualTo(5)

        val healFriend = WheelSpellTables.druid[4]
        assertThat(healFriend.name).isEqualTo("Heal Friend")
        assertThat(healFriend.grade[1].decrease.manaCost).isEqualTo(10)
        assertThat(healFriend.grade[2].increase.heal).isEqualTo(6)
    }

    @Test
    fun `knight spell table matches canary initializeKnightSpells`() {
        val frontSweep = WheelSpellTables.knight[0]
        assertThat(frontSweep.name).isEqualTo("Front Sweep")
        assertThat(frontSweep.grade[1].leech.life).isEqualTo(5)
        assertThat(frontSweep.grade[2].increase.damage).isEqualTo(14)

        val groundshaker = WheelSpellTables.knight[1]
        assertThat(groundshaker.name).isEqualTo("Groundshaker")
        assertThat(groundshaker.grade[1].increase.damage).isEqualTo(13)
        assertThat(groundshaker.grade[2].decrease.cooldown).isEqualTo(2)

        val chivalrousChallenge = WheelSpellTables.knight[2]
        assertThat(chivalrousChallenge.grade[1].decrease.manaCost).isEqualTo(20)
        assertThat(chivalrousChallenge.grade[2].increase.additionalTarget).isEqualTo(1)

        val intenseWoundCleansing = WheelSpellTables.knight[3]
        assertThat(intenseWoundCleansing.name).isEqualTo("Intense Wound Cleansing")
        assertThat(intenseWoundCleansing.grade[1].increase.heal).isEqualTo(125)
        assertThat(intenseWoundCleansing.grade[2].decrease.cooldown).isEqualTo(300)

        val fierceBerserk = WheelSpellTables.knight[4]
        assertThat(fierceBerserk.name).isEqualTo("Fierce Berserk")
        assertThat(fierceBerserk.grade[1].decrease.manaCost).isEqualTo(30)
        assertThat(fierceBerserk.grade[2].increase.damage).isEqualTo(10)
    }

    @Test
    fun `paladin spell table matches canary initializePaladinSpells`() {
        val sharpshooter = WheelSpellTables.paladin[0]
        assertThat(sharpshooter.name).isEqualTo("Sharpshooter")
        assertThat(sharpshooter.grade[1].decrease.secondaryGroupCooldown).isEqualTo(8)
        assertThat(sharpshooter.grade[2].decrease.cooldown).isEqualTo(6)

        val strongEtherealSpear = WheelSpellTables.paladin[1]
        assertThat(strongEtherealSpear.name).isEqualTo("Strong Ethereal Spear")
        assertThat(strongEtherealSpear.grade[1].decrease.cooldown).isEqualTo(2)
        assertThat(strongEtherealSpear.grade[2].increase.damage).isEqualTo(380)

        val divineDazzle = WheelSpellTables.paladin[2]
        assertThat(divineDazzle.name).isEqualTo("Divine Dazzle")
        assertThat(divineDazzle.grade[1].increase.additionalTarget).isEqualTo(1)
        assertThat(divineDazzle.grade[2].increase.duration).isEqualTo(4)
        assertThat(divineDazzle.grade[2].decrease.cooldown).isEqualTo(4)

        val swiftFoot = WheelSpellTables.paladin[3]
        assertThat(swiftFoot.name).isEqualTo("Swift Foot")
        assertThat(swiftFoot.grade[1].decrease.secondaryGroupCooldown).isEqualTo(8)
        assertThat(swiftFoot.grade[2].decrease.cooldown).isEqualTo(6)

        val divineCaldera = WheelSpellTables.paladin[4]
        assertThat(divineCaldera.name).isEqualTo("Divine Caldera")
        assertThat(divineCaldera.grade[1].decrease.manaCost).isEqualTo(20)
        assertThat(divineCaldera.grade[2].increase.damage).isEqualTo(9)
    }

    @Test
    fun `sorcerer spell table matches canary initializeSorcererSpells`() {
        val magicShield = WheelSpellTables.sorcerer[0]
        assertThat(magicShield.name).isEqualTo("Magic Shield")
        assertThat(magicShield.grade[1]).isEqualTo(WheelSpellBonus())
        assertThat(magicShield.grade[2].decrease.cooldown).isEqualTo(6)

        val sapStrength = WheelSpellTables.sorcerer[1]
        assertThat(sapStrength.name).isEqualTo("Sap Strength")
        assertThat(sapStrength.grade[1].increase.area).isTrue()
        assertThat(sapStrength.grade[2].increase.damageReduction).isEqualTo(1)

        val energyWave = WheelSpellTables.sorcerer[2]
        assertThat(energyWave.name).isEqualTo("Energy Wave")
        assertThat(energyWave.grade[1].increase.damage).isEqualTo(5)
        assertThat(energyWave.grade[2].increase.area).isTrue()

        val greatFireWave = WheelSpellTables.sorcerer[3]
        assertThat(greatFireWave.name).isEqualTo("Great Fire Wave")
        assertThat(greatFireWave.grade[1].increase.criticalDamage).isEqualTo(15)
        assertThat(greatFireWave.grade[1].increase.criticalChance).isEqualTo(10)
        assertThat(greatFireWave.grade[2].increase.damage).isEqualTo(5)

        val anyFocus = WheelSpellTables.sorcerer[4]
        assertThat(anyFocus.name).isEqualTo("Any_Focus_Mage_Spell")
        assertThat(anyFocus.grade[1].increase.damage).isEqualTo(5)
        assertThat(anyFocus.grade[2].decrease.cooldown).isEqualTo(4)
        assertThat(anyFocus.grade[2].decrease.secondaryGroupCooldown).isEqualTo(4)
    }

    @Test
    fun `monk spell table matches canary initializeMonkSpells`() {
        val massSpiritMend = WheelSpellTables.monk[0]
        assertThat(massSpiritMend.name).isEqualTo("Mass Spirit Mend")
        assertThat(massSpiritMend.grade[1].increase.heal).isEqualTo(8)
        assertThat(massSpiritMend.grade[2].increase.area).isTrue()

        val mysticRepulse = WheelSpellTables.monk[1]
        assertThat(mysticRepulse.name).isEqualTo("Mystic Repulse")
        assertThat(mysticRepulse.grade[1].decrease.cooldown).isEqualTo(4)
        assertThat(mysticRepulse.grade[2].increase.damage).isEqualTo(40)

        val chainedPenance = WheelSpellTables.monk[2]
        assertThat(chainedPenance.name).isEqualTo("Chained Penance")
        assertThat(chainedPenance.grade[1].increase.additionalTarget).isEqualTo(1)
        assertThat(chainedPenance.grade[2].increase.additionalTarget).isEqualTo(2)

        val flurryOfBlows = WheelSpellTables.monk[3]
        assertThat(flurryOfBlows.name).isEqualTo("Flurry of Blows")
        assertThat(flurryOfBlows.grade[1].leech.life).isEqualTo(5)
        assertThat(flurryOfBlows.grade[2].increase.damage).isEqualTo(12)

        val sweepingTakedown = WheelSpellTables.monk[4]
        assertThat(sweepingTakedown.name).isEqualTo("Sweeping Takedown")
        assertThat(sweepingTakedown.grade[1].leech.mana).isEqualTo(3)
        assertThat(sweepingTakedown.grade[2].increase.criticalDamage).isEqualTo(25)
        assertThat(sweepingTakedown.grade[2].increase.criticalChance).isEqualTo(10)
    }

    @Test
    fun `focus spells match canary m_focusSpells`() {
        assertThat(WheelSpellTables.focusSpells).containsExactly(
            "Eternal Winter",
            "Hell's Core",
            "Rage of the Skies",
            "Wrath of Nature",
        )
    }

    @Test
    fun `forVocation maps cip ids to the correct table`() {
        assertThat(WheelSpellTables.forVocation(IOWheel.VOCATION_KNIGHT_CIP)).isSameAs(WheelSpellTables.knight)
        assertThat(WheelSpellTables.forVocation(IOWheel.VOCATION_PALADIN_CIP)).isSameAs(WheelSpellTables.paladin)
        assertThat(WheelSpellTables.forVocation(IOWheel.VOCATION_SORCERER_CIP)).isSameAs(WheelSpellTables.sorcerer)
        assertThat(WheelSpellTables.forVocation(IOWheel.VOCATION_DRUID_CIP)).isSameAs(WheelSpellTables.druid)
        assertThat(WheelSpellTables.forVocation(IOWheel.VOCATION_MONK_CIP)).isSameAs(WheelSpellTables.monk)
        assertThat(WheelSpellTables.forVocation(99)).isEmpty()
    }
}
