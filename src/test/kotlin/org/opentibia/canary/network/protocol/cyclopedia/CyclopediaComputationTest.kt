package org.opentibia.canary.network.protocol.cyclopedia

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.within
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.SLOTP_ARMOR
import org.opentibia.canary.items.SLOTP_FEET
import org.opentibia.canary.items.SLOTP_HEAD
import org.opentibia.canary.items.SLOTP_LEFT
import org.opentibia.canary.items.SLOTP_LEGS
import org.opentibia.canary.systems.imbuement.Imbuement
import org.opentibia.canary.systems.imbuement.ImbuementInfo
import org.opentibia.canary.systems.proficiency.WeaponProficiencyCriticalBonus
import org.opentibia.canary.systems.wheel.WheelAvatarSkill
import org.opentibia.canary.systems.wheel.WheelMajor
import org.opentibia.canary.systems.wheel.WheelStat

/**
 * Test fake for [CyclopediaWheelStats] backed by explicit maps.
 */
internal class FakeCyclopediaWheel(
    private val stats: Map<WheelStat, Int> = emptyMap(),
    private val resistances: Map<CombatType, Int> = emptyMap(),
    private val conditionals: Map<Pair<String, WheelMajor>, Int> = emptyMap(),
    private val avatarSkills: Map<WheelAvatarSkill, Int> = emptyMap(),
    private val momentum: Double = 0.0,
) : CyclopediaWheelStats {
    override fun getStat(stat: WheelStat): Int = stats[stat] ?: 0

    override fun getResistance(combatType: CombatType): Int = resistances[combatType] ?: 0

    override fun getMajorStatConditional(instant: String, major: WheelMajor): Int =
        conditionals[instant to major] ?: 0

    override fun checkAvatarSkill(skill: WheelAvatarSkill): Int = avatarSkills[skill] ?: 0

    override fun getBonusMomentum(): Double = momentum
}

/**
 * Test fake for [CyclopediaWeaponProficiencyStats] backed by explicit maps.
 */
internal class FakeCyclopediaProficiency(
    private val skillBonuses: Map<Skill, Int> = emptyMap(),
    private val generalCritical: WeaponProficiencyCriticalBonus = WeaponProficiencyCriticalBonus(),
) : CyclopediaWeaponProficiencyStats {
    override fun getSkillBonus(skill: Skill): Int = skillBonuses[skill] ?: 0

    override fun getGeneralCritical(): WeaponProficiencyCriticalBonus = generalCritical
}

/**
 * Test fake for [CyclopediaComputationPlayer]; every member returns the injected
 * state without touching a live world.
 */
internal class FakeCyclopediaPlayer(
    override val oldProtocol: Boolean = false,
    private val inventory: Map<Slots_t, Item> = emptyMap(),
    private val abilitiesEnabled: Set<Slots_t> = emptySet(),
    private val skills: Map<Skill, Int> = emptyMap(),
    private val absorbPercent: Map<CombatType, Int> = emptyMap(),
    private val baseCritical: BaseCritical = BaseCritical(),
    private val wheel: CyclopediaWheelStats = FakeCyclopediaWheel(),
    private val proficiency: CyclopediaWeaponProficiencyStats = FakeCyclopediaProficiency(),
) : CyclopediaComputationPlayer {
    override fun getWeapon(ignoreAmmo: Boolean): Item? = inventory[Slots_t.CONST_SLOT_LEFT]

    override fun getInventoryItem(slot: Slots_t): Item? = inventory[slot]

    override fun isItemAbilityEnabled(slot: Slots_t): Boolean = slot in abilitiesEnabled

    override fun getSkillLevel(skill: Skill): Int = skills[skill] ?: 0

    override fun getAbsorbPercent(combatType: CombatType): Int = absorbPercent[combatType] ?: 0

    override fun getBaseCritical(): BaseCritical = baseCritical

    override fun wheel(): CyclopediaWheelStats = wheel

    override fun weaponProficiency(): CyclopediaWeaponProficiencyStats = proficiency
}

class CyclopediaComputationTest {

    private fun tierItem(id: Int, slotPosition: Int, tier: Int): Item {
        val type = ItemType(
            id = id,
            name = "item-$id",
            slotPosition = slotPosition,
            weaponType = if (slotPosition == SLOTP_LEFT) WeaponType_t.WEAPON_SWORD else WeaponType_t.WEAPON_NONE,
            upgradeClassification = 1,
        )
        return Item(id = id, type = type).also { it.setTier(tier) }
    }

    private fun imbuement(
        id: Int,
        combatType: CombatType,
        elementDamage: Int,
        absorb: Pair<CombatType, Int>? = null,
    ): Imbuement = Imbuement(id, 1).apply {
        this.combatType = combatType
        this.elementDamage = elementDamage
        absorb?.let { absorbPercent[it.first.value] = it.second }
    }

    @Test
    fun `get cipbia element maps every combat type`() {
        assertThat(CipbiaElement.fromCombatType(CombatType.PHYSICALDAMAGE)).isEqualTo(0)
        assertThat(CipbiaElement.fromCombatType(CombatType.FIREDAMAGE)).isEqualTo(1)
        assertThat(CipbiaElement.fromCombatType(CombatType.EARTHDAMAGE)).isEqualTo(2)
        assertThat(CipbiaElement.fromCombatType(CombatType.ENERGYDAMAGE)).isEqualTo(3)
        assertThat(CipbiaElement.fromCombatType(CombatType.ICEDAMAGE)).isEqualTo(4)
        assertThat(CipbiaElement.fromCombatType(CombatType.HOLYDAMAGE)).isEqualTo(5)
        assertThat(CipbiaElement.fromCombatType(CombatType.DEATHDAMAGE)).isEqualTo(6)
        assertThat(CipbiaElement.fromCombatType(CombatType.HEALING)).isEqualTo(7)
        assertThat(CipbiaElement.fromCombatType(CombatType.DROWNDAMAGE)).isEqualTo(8)
        assertThat(CipbiaElement.fromCombatType(CombatType.LIFEDRAIN)).isEqualTo(9)
        assertThat(CipbiaElement.fromCombatType(CombatType.MANADRAIN)).isEqualTo(10)
        assertThat(CipbiaElement.fromCombatType(CombatType.AGONYDAMAGE)).isEqualTo(11)
        assertThat(CipbiaElement.fromCombatType(CombatType.NEUTRALDAMAGE)).isEqualTo(11)
        assertThat(CipbiaElement.fromCombatType(CombatType.UNDEFINEDDAMAGE)).isEqualTo(12)
        assertThat(CipbiaElement.fromCombatType(CombatType.NONE)).isEqualTo(12)
        assertThat(CipbiaElement.fromCombatType(CombatType.COUNT)).isEqualTo(12)
    }

    @Test
    fun `get cipbia element reverse maps every client element`() {
        assertThat(CipbiaElement.toCombatType(0)).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(CipbiaElement.toCombatType(1)).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(CipbiaElement.toCombatType(2)).isEqualTo(CombatType.EARTHDAMAGE)
        assertThat(CipbiaElement.toCombatType(3)).isEqualTo(CombatType.ENERGYDAMAGE)
        assertThat(CipbiaElement.toCombatType(4)).isEqualTo(CombatType.ICEDAMAGE)
        assertThat(CipbiaElement.toCombatType(5)).isEqualTo(CombatType.HOLYDAMAGE)
        assertThat(CipbiaElement.toCombatType(6)).isEqualTo(CombatType.DEATHDAMAGE)
        assertThat(CipbiaElement.toCombatType(7)).isEqualTo(CombatType.HEALING)
        assertThat(CipbiaElement.toCombatType(8)).isEqualTo(CombatType.DROWNDAMAGE)
        assertThat(CipbiaElement.toCombatType(9)).isEqualTo(CombatType.LIFEDRAIN)
        assertThat(CipbiaElement.toCombatType(10)).isEqualTo(CombatType.MANADRAIN)
        assertThat(CipbiaElement.toCombatType(11)).isEqualTo(CombatType.AGONYDAMAGE)
        assertThat(CipbiaElement.toCombatType(12)).isEqualTo(CombatType.NONE)
        assertThat(CipbiaElement.toCombatType(18)).isEqualTo(CombatType.HEALING)
    }

    @Test
    fun `get cipbia skill maps skills both ways`() {
        assertThat(CipbiaSkill.fromSkill(Skill.SKILL_FIST)).isEqualTo(11)
        assertThat(CipbiaSkill.fromSkill(Skill.SKILL_CLUB)).isEqualTo(9)
        assertThat(CipbiaSkill.fromSkill(Skill.SKILL_SWORD)).isEqualTo(8)
        assertThat(CipbiaSkill.fromSkill(Skill.SKILL_AXE)).isEqualTo(10)
        assertThat(CipbiaSkill.fromSkill(Skill.SKILL_DISTANCE)).isEqualTo(7)
        assertThat(CipbiaSkill.fromSkill(Skill.SKILL_SHIELD)).isEqualTo(6)
        assertThat(CipbiaSkill.fromSkill(Skill.SKILL_FISHING)).isEqualTo(13)
        assertThat(CipbiaSkill.fromSkill(Skill.SKILL_MAGLEVEL)).isEqualTo(1)
        assertThat(CipbiaSkill.fromSkill(Skill.SKILL_CRITICAL_HIT_CHANCE)).isEqualTo(0)

        assertThat(CipbiaSkill.toSkill(11)).isEqualTo(Skill.SKILL_FIST)
        assertThat(CipbiaSkill.toSkill(9)).isEqualTo(Skill.SKILL_CLUB)
        assertThat(CipbiaSkill.toSkill(8)).isEqualTo(Skill.SKILL_SWORD)
        assertThat(CipbiaSkill.toSkill(10)).isEqualTo(Skill.SKILL_AXE)
        assertThat(CipbiaSkill.toSkill(7)).isEqualTo(Skill.SKILL_DISTANCE)
        assertThat(CipbiaSkill.toSkill(6)).isEqualTo(Skill.SKILL_SHIELD)
        assertThat(CipbiaSkill.toSkill(13)).isEqualTo(Skill.SKILL_FISHING)
        assertThat(CipbiaSkill.toSkill(1)).isEqualTo(Skill.SKILL_MAGLEVEL)
        assertThat(CipbiaSkill.toSkill(0)).isNull()
        assertThat(CipbiaSkill.toSkill(5)).isNull()
    }

    @Test
    fun `get forge skill stat applies the tier chance and boot amplification`() {
        val inventory = mapOf(
            Slots_t.CONST_SLOT_LEFT to tierItem(1, SLOTP_LEFT, 1),
            Slots_t.CONST_SLOT_HEAD to tierItem(2, SLOTP_HEAD, 1),
            Slots_t.CONST_SLOT_LEGS to tierItem(3, SLOTP_LEGS, 1),
            Slots_t.CONST_SLOT_FEET to tierItem(4, SLOTP_FEET, 1),
            Slots_t.CONST_SLOT_ARMOR to tierItem(5, SLOTP_ARMOR, 1),
        )
        val computation = CyclopediaComputation(FakeCyclopediaPlayer(inventory = inventory))

        assertThat(computation.getForgeSkillStat(Slots_t.CONST_SLOT_LEFT)).isCloseTo(0.005125, within(1e-12))
        assertThat(computation.getForgeSkillStat(Slots_t.CONST_SLOT_LEFT, false)).isCloseTo(0.005, within(1e-12))
        assertThat(computation.getForgeSkillStat(Slots_t.CONST_SLOT_HEAD)).isCloseTo(0.0205, within(1e-12))
        assertThat(computation.getForgeSkillStat(Slots_t.CONST_SLOT_LEGS)).isCloseTo(0.00130175, within(1e-12))
        assertThat(computation.getForgeSkillStat(Slots_t.CONST_SLOT_FEET)).isCloseTo(0.025, within(1e-12))
        assertThat(computation.getForgeSkillStat(Slots_t.CONST_SLOT_ARMOR)).isCloseTo(0.00509890965, within(1e-12))
    }

    @Test
    fun `get forge skill stat is zero for old protocol and empty slots`() {
        val inventory = mapOf(Slots_t.CONST_SLOT_LEFT to tierItem(1, SLOTP_LEFT, 1))

        val oldProtocol = CyclopediaComputation(FakeCyclopediaPlayer(oldProtocol = true, inventory = inventory))
        assertThat(oldProtocol.getForgeSkillStat(Slots_t.CONST_SLOT_LEFT)).isEqualTo(0.0)

        val empty = CyclopediaComputation(FakeCyclopediaPlayer())
        assertThat(empty.getForgeSkillStat(Slots_t.CONST_SLOT_RING)).isEqualTo(0.0)
    }

    @Test
    fun `handle imbuement damage returns the first active damaging imbuement`() {
        val type = ItemType(
            id = 100,
            name = "sword",
            slotPosition = SLOTP_LEFT,
            weaponType = WeaponType_t.WEAPON_SWORD,
            imbuementSlot = 2,
        )
        val weapon = Item(id = 100, type = type)
        val fire = imbuement(10, CombatType.FIREDAMAGE, 250)
        val computation = CyclopediaComputation(
            FakeCyclopediaPlayer(inventory = mapOf(Slots_t.CONST_SLOT_LEFT to weapon)),
            imbuements = ImbuementInfoResolver { item, slot ->
                if (item.id == 100 && slot == 0) ImbuementInfo(fire, 100) else null
            },
        )

        val block = computation.handleImbuementDamage()

        assertThat(block).isNotNull
        assertThat(block!!.damage).isEqualTo(2.5)
        assertThat(block.element).isEqualTo(CipbiaElement.FIRE)
    }

    @Test
    fun `handle imbuement damage falls back to zero without a valid imbuement`() {
        val type = ItemType(
            id = 100,
            name = "sword",
            slotPosition = SLOTP_LEFT,
            weaponType = WeaponType_t.WEAPON_SWORD,
            imbuementSlot = 2,
        )
        val weapon = Item(id = 100, type = type)
        val player = FakeCyclopediaPlayer(inventory = mapOf(Slots_t.CONST_SLOT_LEFT to weapon))
        val expired = imbuement(10, CombatType.FIREDAMAGE, 250)
        val invalidCombat = imbuement(11, CombatType.NONE, 250)
        val tooHighCombat = imbuement(12, CombatType.COUNT, 250)

        val noWeapon = CyclopediaComputation(FakeCyclopediaPlayer())
        assertThat(noWeapon.handleImbuementDamage()).isNull()

        val noImbuement = CyclopediaComputation(player, imbuements = ImbuementInfoResolver { _, _ -> null })
        assertThat(noImbuement.handleImbuementDamage()).isNull()

        val noDuration = CyclopediaComputation(
            player,
            imbuements = ImbuementInfoResolver { _, _ -> ImbuementInfo(expired, 0) },
        )
        assertThat(noDuration.handleImbuementDamage()).isNull()

        val combatNone = CyclopediaComputation(
            player,
            imbuements = ImbuementInfoResolver { _, _ -> ImbuementInfo(invalidCombat, 100) },
        )
        assertThat(combatNone.handleImbuementDamage()).isNull()

        val combatCount = CyclopediaComputation(
            player,
            imbuements = ImbuementInfoResolver { _, _ -> ImbuementInfo(tooHighCombat, 100) },
        )
        assertThat(combatCount.handleImbuementDamage()).isNull()
    }

    @Test
    fun `calculate absorb values combines abilities imbuements player absorb and wheel`() {
        val abilities = Abilities()
        abilities.absorbPercent[CombatType.FIREDAMAGE.value] = 10
        val armor = Item(
            id = 200,
            type = ItemType(id = 200, name = "armor", slotPosition = SLOTP_ARMOR, abilities = abilities, imbuementSlot = 1),
        )
        val earthImbuement = imbuement(20, CombatType.EARTHDAMAGE, 0, CombatType.EARTHDAMAGE to 20)
        val player = FakeCyclopediaPlayer(
            inventory = mapOf(Slots_t.CONST_SLOT_ARMOR to armor),
            abilitiesEnabled = setOf(Slots_t.CONST_SLOT_ARMOR),
            absorbPercent = mapOf(CombatType.FIREDAMAGE to 10),
            wheel = FakeCyclopediaWheel(resistances = mapOf(CombatType.FIREDAMAGE to 500)),
        )
        val computation = CyclopediaComputation(
            player,
            imbuements = ImbuementInfoResolver { item, slot ->
                if (item.id == 200 && slot == 0) ImbuementInfo(earthImbuement, 60) else null
            },
        )

        val values = computation.calculateAbsorbValues()

        assertThat(values.entries).hasSize(2)
        assertThat(values.entries[0].element).isEqualTo(CipbiaElement.FIRE)
        assertThat(values.entries[0].modifier).isEqualTo(0.25)
        assertThat(values.entries[1].element).isEqualTo(CipbiaElement.EARTH)
        assertThat(values.entries[1].modifier).isCloseTo(0.2, within(1e-12))
    }

    @Test
    fun `calculate absorb values wraps like the uint16 arithmetic`() {
        val abilities = Abilities()
        abilities.absorbPercent[CombatType.FIREDAMAGE.value] = -400
        val armor = Item(
            id = 201,
            type = ItemType(id = 201, name = "armor", slotPosition = SLOTP_ARMOR, abilities = abilities),
        )
        val player = FakeCyclopediaPlayer(
            inventory = mapOf(Slots_t.CONST_SLOT_ARMOR to armor),
            abilitiesEnabled = setOf(Slots_t.CONST_SLOT_ARMOR),
        )
        val computation = CyclopediaComputation(player, wheelSystemEnabled = false)

        val values = computation.calculateAbsorbValues()

        assertThat(values.entries).hasSize(1)
        assertThat(values.entries[0].element).isEqualTo(CipbiaElement.FIRE)
        assertThat(values.entries[0].modifier).isCloseTo(2.5536, within(1e-12))
    }

    @Test
    fun `calculate absorb values skips slots whose item ability is disabled`() {
        val abilities = Abilities()
        abilities.absorbPercent[CombatType.FIREDAMAGE.value] = 10
        val armor = Item(
            id = 202,
            type = ItemType(id = 202, name = "armor", slotPosition = SLOTP_ARMOR, abilities = abilities),
        )
        val player = FakeCyclopediaPlayer(
            inventory = mapOf(Slots_t.CONST_SLOT_ARMOR to armor),
            abilitiesEnabled = emptySet(),
        )

        assertThat(CyclopediaComputation(player).calculateAbsorbValues().entries).isEmpty()
    }

    @Test
    fun `add cyclopedia skills computes the leech and critical damage blocks`() {
        val player = FakeCyclopediaPlayer(
            skills = mapOf(
                Skill.SKILL_LIFE_LEECH_AMOUNT to 15000,
                Skill.SKILL_CRITICAL_HIT_DAMAGE to 10000,
            ),
            wheel = FakeCyclopediaWheel(
                stats = mapOf(
                    WheelStat.LIFE_LEECH to 1000,
                    WheelStat.CRITICAL_DAMAGE to 2000,
                ),
                conditionals = mapOf(
                    ("Combat Mastery" to WheelMajor.CRITICAL_DMG_2) to 300,
                    ("Ballistic Mastery" to WheelMajor.CRITICAL_DMG) to 100,
                ),
                avatarSkills = mapOf(WheelAvatarSkill.CRITICAL_DAMAGE to 50),
            ),
            proficiency = FakeCyclopediaProficiency(
                skillBonuses = mapOf(
                    Skill.SKILL_LIFE_LEECH_AMOUNT to 5000,
                    Skill.SKILL_CRITICAL_HIT_DAMAGE to 10000,
                ),
            ),
        )
        val computation = CyclopediaComputation(player)

        val lifeLeech = computation.addCyclopediaSkills(
            Skill.SKILL_LIFE_LEECH_AMOUNT,
            SkillsEquipment(equipment = 0.25, imbuement = 0.1),
        )
        assertThat(lifeLeech.total).isEqualTo(1.5)
        assertThat(lifeLeech.equipment).isEqualTo(0.75)
        assertThat(lifeLeech.imbuement).isEqualTo(0.1)
        assertThat(lifeLeech.wheel).isEqualTo(0.1)
        assertThat(lifeLeech.event).isEqualTo(0.0)

        val criticalDamage = computation.addCyclopediaSkills(
            Skill.SKILL_CRITICAL_HIT_DAMAGE,
            SkillsEquipment(equipment = 0.0),
        )
        assertThat(criticalDamage.total).isEqualTo(1.0)
        assertThat(criticalDamage.equipment).isEqualTo(1.0)
        assertThat(criticalDamage.imbuement).isEqualTo(0.0)
        assertThat(criticalDamage.wheel).isCloseTo(0.245, within(1e-12))
        assertThat(criticalDamage.event).isEqualTo(0.0)
    }

    @Test
    fun `add cyclopedia critical skill computes the chance and damage blocks`() {
        val player = FakeCyclopediaPlayer(
            baseCritical = BaseCritical(chance = 0.05, damage = 0.1),
            wheel = FakeCyclopediaWheel(
                stats = mapOf(
                    WheelStat.CRITICAL_CHANCE to 500,
                    WheelStat.CRITICAL_DAMAGE to 10000,
                ),
            ),
            proficiency = FakeCyclopediaProficiency(
                generalCritical = WeaponProficiencyCriticalBonus(chance = 0.02, damage = 0.05),
            ),
        )
        val computation = CyclopediaComputation(player)

        val chance = computation.addCyclopediaCriticalSkill(
            Skill.SKILL_CRITICAL_HIT_CHANCE,
            SkillsEquipment(equipment = 0.01, imbuement = 0.03),
        )
        assertThat(chance.total).isCloseTo(0.16, within(1e-12))
        assertThat(chance.base).isEqualTo(0.05)
        assertThat(chance.equipment).isCloseTo(0.03, within(1e-12))
        assertThat(chance.imbuement).isEqualTo(0.03)
        assertThat(chance.wheel).isCloseTo(0.05, within(1e-12))
        assertThat(chance.concoction).isEqualTo(0.0)

        val damage = computation.addCyclopediaCriticalSkill(
            Skill.SKILL_CRITICAL_HIT_DAMAGE,
            SkillsEquipment(equipment = 0.01, imbuement = 0.03),
        )
        assertThat(damage.total).isCloseTo(1.19, within(1e-12))
        assertThat(damage.base).isEqualTo(0.1)
        assertThat(damage.equipment).isCloseTo(0.06, within(1e-12))
        assertThat(damage.imbuement).isEqualTo(0.03)
        assertThat(damage.wheel).isEqualTo(1.0)
        assertThat(damage.concoction).isEqualTo(0.0)
    }
}
