package org.opentibia.canary.items.weapons

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.BlockType
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.proficiency.WeaponProficiency
import org.opentibia.canary.systems.proficiency.WeaponProficiencyBonus
import org.opentibia.canary.systems.proficiency.WeaponProficiencyPlayer
import java.util.Random

/**
 * Transcripts Canary's `Weapons`, `Weapon`, `WeaponMelee`, `WeaponDistance`
 * and `WeaponWand` behaviour.
 *
 * Reference: src/items/weapons/weapons.hpp/.cpp.
 */
class WeaponsTest {

    @Test
    fun `getMaxMeleeDamage rounds the monster formula up`() {
        assertThat(Weapons.getMaxMeleeDamage(100, 50)).isEqualTo(275)
        assertThat(Weapons.getMaxMeleeDamage(0, 50)).isEqualTo(25)
    }

    @Test
    fun `getMaxWeaponDamage matches the player melee and distance formulas`() {
        assertThat(Weapons.getMaxWeaponDamage(100, 100, 50, 1.0f, isMelee = true)).isEqualTo(445)
        assertThat(Weapons.getMaxWeaponDamage(100, 100, 50, 1.0f, isMelee = false)).isEqualTo(470)
        assertThat(Weapons.getMaxWeaponDamage(100, 100, 0, 1.0f, isMelee = true)).isEqualTo(0)
        assertThat(Weapons.getMaxWeaponDamage(100, 100, 0, 1.0f, isMelee = false)).isEqualTo(20)
    }

    @Test
    fun `registry resolves weapons by item id and marks XML weapons`() {
        val weapons = Weapons()
        val melee = WeaponMelee()

        assertThat(weapons.getWeapon(null)).isNull()
        assertThat(weapons.registerLuaEvent(melee)).isTrue()
        assertThat(melee.isFromXML()).isFalse()

        val xmlWeapon = WeaponMelee()
        xmlWeapon.setID(2400)
        xmlWeapon.configureWeapon(ItemType(id = 2400, name = "magic sword"))
        assertThat(weapons.registerLuaEvent(xmlWeapon, fromXML = true)).isTrue()
        assertThat(xmlWeapon.isFromXML()).isTrue()

        val item = FakeWeaponItem(id = 2400)
        assertThat(weapons.getWeapon(item)).isSameAs(xmlWeapon)
    }

    @Test
    fun `clear with fromXML removes only XML registered weapons`() {
        val weapons = Weapons()
        val luaWeapon = WeaponMelee().also { it.setID(1) }
        val xmlWeapon = WeaponMelee().also { it.setID(2) }
        weapons.registerLuaEvent(luaWeapon)
        weapons.registerLuaEvent(xmlWeapon, fromXML = true)

        weapons.clear(true)
        assertThat(weapons.getWeapon(FakeWeaponItem(id = 1))).isSameAs(luaWeapon)
        assertThat(weapons.getWeapon(FakeWeaponItem(id = 2))).isNull()

        weapons.clear()
        assertThat(weapons.getWeapon(FakeWeaponItem(id = 1))).isNull()
    }

    @Test
    fun `weapon defaults and accessors mirror the C++ fields`() {
        val weapon = WeaponMelee()

        assertThat(weapon.getID()).isEqualTo(0)
        assertThat(weapon.getReqLevel()).isEqualTo(0)
        assertThat(weapon.getReqMagLv()).isEqualTo(0)
        assertThat(weapon.isPremium()).isFalse()
        assertThat(weapon.isWieldedUnproperly()).isFalse()
        assertThat(weapon.getMana()).isEqualTo(0)
        assertThat(weapon.getManaPercent()).isEqualTo(0)
        assertThat(weapon.getHealth()).isEqualTo(0)
        assertThat(weapon.getHealthPercent()).isEqualTo(0)
        assertThat(weapon.getSoul()).isEqualTo(0)
        assertThat(weapon.getBreakChance()).isEqualTo(0)
        assertThat(weapon.isEnabled()).isTrue()
        assertThat(weapon.isChainDisabled()).isFalse()
        assertThat(weapon.getChainSkillValue()).isEqualTo(0.0)
        assertThat(weapon.getScriptId()).isEqualTo(0)
        assertThat(weapon.isLoadedScriptId()).isFalse()

        weapon.setID(8924)
        weapon.setRequiredLevel(20)
        weapon.setRequiredMagLevel(5)
        weapon.setNeedPremium(true)
        weapon.setWieldUnproperly(true)
        weapon.setMana(10)
        weapon.setManaPercent(2)
        weapon.setHealth(3)
        weapon.setHealthPercent(4)
        weapon.setSoul(5)
        weapon.setBreakChance(6)
        weapon.setIsEnabled(false)
        weapon.setScriptId(77)
        weapon.setChainSkillValue(1.5)
        weapon.setDisabledChain()

        assertThat(weapon.getID()).isEqualTo(8924)
        assertThat(weapon.getReqLevel()).isEqualTo(20)
        assertThat(weapon.getReqMagLv()).isEqualTo(5)
        assertThat(weapon.isPremium()).isTrue()
        assertThat(weapon.isWieldedUnproperly()).isTrue()
        assertThat(weapon.getMana()).isEqualTo(10)
        assertThat(weapon.getManaPercent()).isEqualTo(2)
        assertThat(weapon.getHealth()).isEqualTo(3)
        assertThat(weapon.getHealthPercent()).isEqualTo(4)
        assertThat(weapon.getSoul()).isEqualTo(5)
        assertThat(weapon.getBreakChance()).isEqualTo(6)
        assertThat(weapon.isEnabled()).isFalse()
        assertThat(weapon.getScriptId()).isEqualTo(77)
        assertThat(weapon.isLoadedScriptId()).isTrue()
        assertThat(weapon.getChainSkillValue()).isEqualTo(1.5)
        assertThat(weapon.isChainDisabled()).isTrue()
    }

    @Test
    fun `setWieldInfo accumulates with bitwise or`() {
        val weapon = WeaponMelee()

        assertThat(weapon.getWieldInfo()).isEqualTo(0)
        weapon.setWieldInfo(1)
        weapon.setWieldInfo(4)
        assertThat(weapon.getWieldInfo()).isEqualTo(5)
    }

    @Test
    fun `addVocWeaponMap resolves the vocation id through the registry seam`() {
        val weapon = WeaponMelee(vocationIdProvider = { name -> if (name == "knight") 4 else -1 })

        weapon.addVocWeaponMap("knight")
        weapon.addVocWeaponMap("unknown")

        val knight = FakeWeaponPlayer(vocationId = 4)
        val druid = FakeWeaponPlayer(vocationId = 2)
        val target = FakeWeaponTarget(position = Position(101, 100, 7))

        assertThat(weapon.playerWeaponCheck(knight, target, 10)).isEqualTo(100)
        assertThat(weapon.playerWeaponCheck(druid, target, 10)).isEqualTo(0)
    }

    @Test
    fun `configureWeapon copies the item id and melee abilities`() {
        val weapon = WeaponMelee()
        val itemType = ItemType(
            id = 2400,
            name = "magic sword",
            abilities = Abilities().also {
                it.elementType = CombatType.FIREDAMAGE
                it.elementDamage = 20
            },
        )

        weapon.configureWeapon(itemType)

        assertThat(weapon.getID()).isEqualTo(2400)
        assertThat(weapon.getElementType()).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(weapon.getElementDamageValue()).isEqualTo(20)
        assertThat(weapon.params.combatType).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(weapon.params.blockedByArmor).isTrue()
        assertThat(weapon.params.blockedByShield).isTrue()
        assertThat(weapon.params.aggressive).isTrue()
        assertThat(weapon.params.useCharges).isTrue()
    }

    @Test
    fun `configureWeapon without abilities leaves no element`() {
        val weapon = WeaponDistance()
        weapon.configureWeapon(ItemType(id = 2456, name = "bow", shootType = 3))

        assertThat(weapon.getElementType()).isEqualTo(CombatType.NONE)
        assertThat(weapon.getElementDamageValue()).isEqualTo(0)
        assertThat(weapon.params.distanceEffect).isEqualTo(3)
        assertThat(weapon.params.blockedByArmor).isTrue()
        assertThat(weapon.params.blockedByShield).isFalse()
    }

    @Test
    fun `wand configureWeapon writes combat type and max hit chance back to the item type`() {
        val weapon = WeaponWand()
        weapon.setMinChange(10)
        weapon.setMaxChange(31)
        val itemType = ItemType(id = 8924, name = "wand of vortex", shootType = 5)

        weapon.configureWeapon(itemType)

        assertThat(weapon.getID()).isEqualTo(8924)
        assertThat(weapon.params.distanceEffect).isEqualTo(5)
        assertThat(itemType.combatType).isEqualTo(weapon.params.combatType)
        assertThat(itemType.maxHitChance).isEqualTo(20)
    }

    @Test
    fun `playerWeaponCheck mirrors the C++ gates`() {
        val weapon = WeaponMelee()
        val target = FakeWeaponTarget(position = Position(102, 100, 7))
        val player = FakeWeaponPlayer(position = Position(100, 100, 7))

        assertThat(weapon.playerWeaponCheck(player, target, 2)).isEqualTo(100)
        assertThat(weapon.playerWeaponCheck(player, target, 1)).isEqualTo(0)
        assertThat(weapon.playerWeaponCheck(FakeWeaponPlayer(position = Position(100, 100, 6)), target, 2)).isEqualTo(0)

        weapon.setIsEnabled(false)
        assertThat(weapon.playerWeaponCheck(player, target, 2)).isEqualTo(0)
        weapon.setIsEnabled(true)

        weapon.setRequiredLevel(10)
        val lowLevel = FakeWeaponPlayer(position = Position(100, 100, 7), level = 5)
        assertThat(weapon.playerWeaponCheck(lowLevel, target, 2)).isEqualTo(0)
        weapon.setWieldUnproperly(true)
        assertThat(weapon.playerWeaponCheck(lowLevel, target, 2)).isEqualTo(50)
        weapon.setWieldUnproperly(false)
        weapon.setRequiredLevel(0)

        weapon.setMana(100)
        assertThat(weapon.playerWeaponCheck(FakeWeaponPlayer(position = Position(100, 100, 7), mana = 99), target, 2)).isEqualTo(0)
        assertThat(weapon.playerWeaponCheck(FakeWeaponPlayer(position = Position(100, 100, 7), mana = 100), target, 2)).isEqualTo(100)
        weapon.setMana(0)

        weapon.setManaPercent(50)
        assertThat(weapon.playerWeaponCheck(FakeWeaponPlayer(position = Position(100, 100, 7), mana = 100, maxMana = 200), target, 2)).isEqualTo(100)
        assertThat(weapon.playerWeaponCheck(FakeWeaponPlayer(position = Position(100, 100, 7), mana = 99, maxMana = 200), target, 2)).isEqualTo(0)
        weapon.setManaPercent(0)

        weapon.setNeedPremium(true)
        assertThat(weapon.playerWeaponCheck(FakeWeaponPlayer(position = Position(100, 100, 7), premium = false), target, 2)).isEqualTo(0)
        assertThat(weapon.playerWeaponCheck(FakeWeaponPlayer(position = Position(100, 100, 7), premium = true), target, 2)).isEqualTo(100)
        weapon.setNeedPremium(false)

        val ignored = FakeWeaponPlayer(position = Position(100, 100, 7), ignoreWeaponCheck = true)
        weapon.setIsEnabled(false)
        assertThat(weapon.playerWeaponCheck(ignored, target, 2)).isEqualTo(100)
    }

    @Test
    fun `melee getWeaponDamage follows the Canary max damage formula`() {
        val weapon = WeaponMelee()
        weapon.configureWeapon(ItemType(id = 2400, name = "magic sword"))

        val player = FakeWeaponPlayer(level = 100, weaponSkill = 100)
        val target = FakeWeaponTarget(position = Position(101, 100, 7))
        val item = FakeWeaponItem(id = 2400, attack = 50, weaponType = WeaponType_t.WEAPON_SWORD)

        assertThat(weapon.getWeaponDamage(player, target, item, maxDamage = true)).isEqualTo(-445)

        player.proficiency.addStat(WeaponProficiencyBonus.ATTACK_DAMAGE, 5.0)
        assertThat(weapon.getWeaponDamage(player, target, item, maxDamage = true)).isEqualTo(-488)
    }

    @Test
    fun `melee getWeaponDamage adds the elemental attack to the max damage`() {
        val weapon = WeaponMelee()
        weapon.configureWeapon(
            ItemType(
                id = 2400,
                name = "magic sword",
                abilities = Abilities().also {
                    it.elementType = CombatType.FIREDAMAGE
                    it.elementDamage = 10
                },
            ),
        )

        val player = FakeWeaponPlayer(level = 100, weaponSkill = 100)
        val target = FakeWeaponTarget(position = Position(101, 100, 7))
        val item = FakeWeaponItem(id = 2400, attack = 50, weaponType = WeaponType_t.WEAPON_SWORD)

        assertThat(weapon.getWeaponDamage(player, target, item, maxDamage = true)).isEqualTo(-530)
    }

    @Test
    fun `melee getWeaponDamage rolls inside the Canary bounds`() {
        val weapon = WeaponMelee(random = Random(42))
        val player = FakeWeaponPlayer(level = 100, weaponSkill = 100)
        val target = FakeWeaponTarget(position = Position(101, 100, 7))
        val item = FakeWeaponItem(id = 2400, attack = 50, weaponType = WeaponType_t.WEAPON_SWORD)

        val rolled = weapon.getWeaponDamage(player, target, item)

        assertThat(rolled).isBetween(-445, -20)
    }

    @Test
    fun `distance getWeaponDamage uses the distance formula`() {
        val weapon = WeaponDistance()
        weapon.configureWeapon(ItemType(id = 2456, name = "bow"))

        val player = FakeWeaponPlayer(level = 100, weaponSkill = 100)
        val target = FakeWeaponTarget(position = Position(105, 100, 7))
        val item = FakeWeaponItem(id = 2456, attack = 50, weaponType = WeaponType_t.WEAPON_DISTANCE)

        assertThat(weapon.getWeaponDamage(player, target, item, maxDamage = true)).isEqualTo(-470)

        val rolled = WeaponDistance(random = Random(7)).getWeaponDamage(player, target, item)
        assertThat(rolled).isBetween(-470, -20)
    }

    @Test
    fun `wand getWeaponDamage uses the min and max change`() {
        val weapon = WeaponWand(random = Random(1))
        weapon.setMinChange(10)
        weapon.setMaxChange(31)

        val player = FakeWeaponPlayer()
        val target = FakeWeaponTarget(position = Position(101, 100, 7))
        val item = FakeWeaponItem(id = 8924, attack = 0, weaponType = WeaponType_t.WEAPON_WAND)

        assertThat(weapon.getWeaponDamage(player, target, item, maxDamage = true)).isEqualTo(-31)
        assertThat(weapon.getWeaponDamage(player, target, item)).isBetween(-31, -10)
        assertThat(weapon.getElementDamage(player, target, item)).isEqualTo(0)
    }

    @Test
    fun `element damage returns zero without an element`() {
        val melee = WeaponMelee()
        melee.configureWeapon(ItemType(id = 2400, name = "sword"))
        val distance = WeaponDistance()
        distance.configureWeapon(ItemType(id = 2456, name = "bow"))

        val player = FakeWeaponPlayer(level = 100, weaponSkill = 100)
        val target = FakeWeaponTarget(position = Position(101, 100, 7))
        val item = FakeWeaponItem(id = 2400, attack = 50, weaponType = WeaponType_t.WEAPON_SWORD)

        assertThat(melee.getElementDamage(player, target, item)).isEqualTo(0)
        assertThat(distance.getElementDamage(player, target, item)).isEqualTo(0)
    }

    @Test
    fun `melee element damage rolls inside the Canary bounds`() {
        val weapon = WeaponMelee(random = Random(3))
        weapon.configureWeapon(
            ItemType(
                id = 2400,
                name = "magic sword",
                abilities = Abilities().also {
                    it.elementType = CombatType.FIREDAMAGE
                    it.elementDamage = 20
                },
            ),
        )

        val player = FakeWeaponPlayer(level = 100, weaponSkill = 100)
        val target = FakeWeaponTarget(position = Position(101, 100, 7))
        val item = FakeWeaponItem(id = 2400, attack = 50, weaponType = WeaponType_t.WEAPON_SWORD)

        assertThat(weapon.getElementDamage(player, target, item)).isBetween(-190, -20)
    }

    @Test
    fun `getCombatDamage splits the real damage by attack proportion`() {
        val weapon = WeaponMelee(random = Random(5))
        weapon.configureWeapon(
            ItemType(
                id = 2400,
                name = "magic sword",
                abilities = Abilities().also {
                    it.elementType = CombatType.FIREDAMAGE
                    it.elementDamage = 50
                },
            ),
        )

        val player = FakeWeaponPlayer(level = 100, weaponSkill = 100)
        val item = FakeWeaponItem(id = 2400, attack = 50, weaponType = WeaponType_t.WEAPON_SWORD)
        val combat = weapon.getCombatDamage(
            org.opentibia.canary.combat.CombatDamage(),
            player,
            item,
            damageModifier = 100,
        )

        assertThat(combat.primaryType).isEqualTo(CombatType.NONE)
        assertThat(combat.primaryValue).isBetween(20, 530)
        assertThat(combat.secondaryValue).isBetween(20, 530)
    }

    @Test
    fun `getCombatDamage keeps the full damage primary without an element`() {
        val weapon = WeaponMelee(random = Random(5))
        weapon.configureWeapon(ItemType(id = 2400, name = "sword"))

        val player = FakeWeaponPlayer(level = 100, weaponSkill = 100)
        val item = FakeWeaponItem(id = 2400, attack = 50, weaponType = WeaponType_t.WEAPON_SWORD)
        val combat = weapon.getCombatDamage(
            org.opentibia.canary.combat.CombatDamage(),
            player,
            item,
            damageModifier = 100,
        )

        assertThat(combat.primaryValue).isBetween(20, 445)
        assertThat(combat.secondaryValue).isEqualTo(0)
    }

    @Test
    fun `melee getSkillType maps the weapon type to the skill`() {
        val weapon = WeaponMelee()
        val player = FakeWeaponPlayer(addAttackSkill = true)

        assertThat(weapon.getSkillType(player, FakeWeaponItem(id = 1, weaponType = WeaponType_t.WEAPON_SWORD)))
            .isEqualTo(SkillType(skills_t.SKILL_SWORD, 1))
        assertThat(weapon.getSkillType(player, FakeWeaponItem(id = 1, weaponType = WeaponType_t.WEAPON_AXE)))
            .isEqualTo(SkillType(skills_t.SKILL_AXE, 1))
        assertThat(weapon.getSkillType(player, FakeWeaponItem(id = 1, weaponType = WeaponType_t.WEAPON_CLUB)))
            .isEqualTo(SkillType(skills_t.SKILL_CLUB, 1))
        assertThat(weapon.getSkillType(player, FakeWeaponItem(id = 1, weaponType = WeaponType_t.WEAPON_FIST)))
            .isEqualTo(SkillType(skills_t.SKILL_FIST, 1))
        assertThat(weapon.getSkillType(player, FakeWeaponItem(id = 1, weaponType = WeaponType_t.WEAPON_WAND)))
            .isNull()

        val blocked = FakeWeaponPlayer(addAttackSkill = true, lastAttackBlockType = BlockType.IMMUNITY)
        assertThat(weapon.getSkillType(blocked, FakeWeaponItem(id = 1, weaponType = WeaponType_t.WEAPON_SWORD)))
            .isEqualTo(SkillType(skills_t.SKILL_SWORD, 0))
    }

    @Test
    fun `distance getSkillType maps the block type to the skill point`() {
        val weapon = WeaponDistance()

        assertThat(weapon.getSkillType(FakeWeaponPlayer(), FakeWeaponItem(id = 1)))
            .isEqualTo(SkillType(skills_t.SKILL_DISTANCE, 0))
        assertThat(
            weapon.getSkillType(
                FakeWeaponPlayer(addAttackSkill = true, lastAttackBlockType = BlockType.NONE),
                FakeWeaponItem(id = 1),
            ),
        ).isEqualTo(SkillType(skills_t.SKILL_DISTANCE, 2))
        assertThat(
            weapon.getSkillType(
                FakeWeaponPlayer(addAttackSkill = true, lastAttackBlockType = BlockType.DEFENSE),
                FakeWeaponItem(id = 1),
            ),
        ).isEqualTo(SkillType(skills_t.SKILL_DISTANCE, 1))
        assertThat(
            weapon.getSkillType(
                FakeWeaponPlayer(addAttackSkill = true, lastAttackBlockType = BlockType.IMMUNITY),
                FakeWeaponItem(id = 1),
            ),
        ).isEqualTo(SkillType(skills_t.SKILL_DISTANCE, 0))
    }

    private class FakeProficiencyPlayer : WeaponProficiencyPlayer {
        override fun getSkillLevel(skill: Int): Int = 0

        override fun getWeapon(ignoreWeaponProficiency: Boolean): Boolean = false
    }

    private class FakeWeaponPlayer(
        private val position: Position = Position(100, 100, 7),
        private val mana: Int = 0,
        private val maxMana: Int = 0,
        private val health: Int = 100,
        private val maxHealth: Int = 100,
        private val soul: Int = 0,
        private val premium: Boolean = false,
        private val vocationId: Int = 0,
        level: Int = 1,
        private val magicLevel: Int = 0,
        val weaponSkill: Int = 0,
        attackFactor: Float = 1.0f,
        private val addAttackSkill: Boolean = false,
        private val lastAttackBlockType: BlockType = BlockType.NONE,
        private val ignoreWeaponCheck: Boolean = false,
        private val vocation: Vocation = Vocation(0),
    ) : WeaponPlayer {
        private val levelValue: Int = level
        private val attackFactorValue: Float = attackFactor

        val proficiency = WeaponProficiency(FakeProficiencyPlayer())

        override fun weaponProficiency(): WeaponProficiency = proficiency

        override fun getPosition(): Position = position

        override fun hasFlag(flag: PlayerFlags_t): Boolean =
            ignoreWeaponCheck && flag == PlayerFlags_t.IgnoreWeaponCheck

        override fun getMana(): Int = mana

        override fun getMaxMana(): Int = maxMana

        override fun getHealth(): Int = health

        override fun getMaxHealth(): Int = maxHealth

        override fun getSoul(): Int = soul

        override fun isPremium(): Boolean = premium

        override fun getVocationId(): Int = vocationId

        override fun getLevel(): Int = levelValue

        override fun getMagicLevel(): Int = magicLevel

        override fun getWeaponSkill(item: WeaponItem?): Int = weaponSkill

        override fun getAttackFactor(): Float = attackFactorValue

        override fun getVocation(): Vocation = vocation

        override fun getSkillLevel(skill: skills_t): Int =
            if (skill == skills_t.SKILL_DISTANCE) weaponSkill else 0

        override fun getAddAttackSkill(): Boolean = addAttackSkill

        override fun getLastAttackBlockType(): BlockType = lastAttackBlockType

        override fun getWeapon(ignoreAmmo: Boolean): WeaponItem? = null
    }

    private class FakeWeaponTarget(
        private val position: Position,
        private val player: Any? = null,
    ) : WeaponTarget {
        override fun getPosition(): Position = position

        override fun getPlayer(): Any? = player
    }

    private class FakeWeaponItem(
        private val id: Int,
        private val attack: Int = 0,
        private val weaponType: WeaponType_t = WeaponType_t.WEAPON_NONE,
        private val shootRange: Int = 1,
    ) : WeaponItem {
        override fun getID(): Int = id

        override fun getAttack(): Int = attack

        override fun getShootRange(): Int = shootRange

        override fun getWeaponType(): WeaponType_t = weaponType

        override fun getItemCount(): Int = 1

        override fun getCharges(): Int = 0

        override fun getName(): String = "item $id"

        override fun getHitChance(): Int = 0

        override fun getPerfectShotRange(): Int = 0

        override fun getPerfectShotDamage(): Int = 0
    }
}
