package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.SLOTP_HAND
import org.opentibia.canary.items.SLOTP_TWO_HAND
import org.opentibia.canary.items.WIELDINFO_LEVEL
import org.opentibia.canary.items.WIELDINFO_MAGLV
import org.opentibia.canary.items.WIELDINFO_PREMIUM
import org.opentibia.canary.items.WIELDINFO_VOCREQ
import org.opentibia.canary.items.WeaponAction_t
import org.opentibia.canary.items.weapons.Weapon
import org.opentibia.canary.items.weapons.WeaponDistance
import org.opentibia.canary.items.weapons.WeaponMelee
import org.opentibia.canary.items.weapons.WeaponWand
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the Canary `Weapon` binding group
 * (src/lua/functions/items/weapon_functions.cpp). Each test installs
 * [WeaponFunctions] with a fake [WeaponOperations] seam and drives the exact
 * Canary method names through real Lua chunks.
 */
class WeaponFunctionsBindingsTest {

    private class FakeWeaponOps : WeaponOperations {
        val itemTypes = linkedMapOf<Int, ItemType>()
        val registered = mutableListOf<Pair<Weapon, LuaValue>>()

        override fun getItemType(id: Int): ItemType = itemTypes.getOrPut(id) { ItemType(id, "") }

        override fun registerLuaEvent(weapon: Weapon, callback: LuaValue): Boolean {
            registered += weapon to callback
            return true
        }

        override fun vocationIdByName(name: String): Int = mapOf(
            "knight" to 4,
            "druid" to 2,
            "sorcerer" to 1,
        )[name.lowercase()] ?: -1
    }

    private fun installed(): Pair<LuaEnvironment, FakeWeaponOps> {
        val environment = LuaEnvironment().apply { initState() }
        val operations = FakeWeaponOps()
        WeaponFunctions.install(environment, operations)
        return environment to operations
    }

    private fun state(environment: LuaEnvironment, name: String): LuaWeapon =
        environment.getGlobal(name).touserdata() as LuaWeapon

    @Test
    fun `Weapon constructor builds the melee distance and wand subtypes`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            sword = Weapon(1)
            club = Weapon(2)
            axe = Weapon(3)
            missile = Weapon(8)
            distance = Weapon(5)
            ammo = Weapon(7)
            wand = Weapon(6)
            none = Weapon(0)
            unknown = Weapon(99)
            """.trimIndent()
        )

        assertThat(state(environment, "sword").weapon).isInstanceOf(WeaponMelee::class.java)
        assertThat(state(environment, "club").weapon).isInstanceOf(WeaponMelee::class.java)
        assertThat(state(environment, "axe").weapon).isInstanceOf(WeaponMelee::class.java)
        assertThat(state(environment, "missile").weapon).isInstanceOf(WeaponDistance::class.java)
        assertThat(state(environment, "distance").weapon).isInstanceOf(WeaponDistance::class.java)
        assertThat(state(environment, "ammo").weapon).isInstanceOf(WeaponDistance::class.java)
        assertThat(state(environment, "wand").weapon).isInstanceOf(WeaponWand::class.java)
        assertThat(environment.getGlobal("none").isnil()).isTrue()
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
    }

    @Test
    fun `Weapon item type setters write the ItemType fields`() {
        val (environment, operations) = installed()

        environment.loadChunk(
            """
            w = Weapon(1)
            w:id(100)
            w:attack(7)
            w:defense(4, 2)
            w:range(3)
            w:charges(5, false)
            w:duration(6, false)
            w:decayTo(7)
            w:transformEquipTo(8)
            w:transformDeEquipTo(9)
            w:shootType(10)
            w:hitChance(11)
            w:maxHitChance(12)
            w:slotType("two-handed")
            """.trimIndent()
        )

        val itemType = operations.itemTypes.getValue(100)
        assertThat(itemType.attack).isEqualTo(7)
        assertThat(itemType.defense).isEqualTo(4)
        assertThat(itemType.extraDefense).isEqualTo(2)
        assertThat(itemType.shootRange).isEqualTo(3)
        assertThat(itemType.charges).isEqualTo(5)
        assertThat(itemType.showCharges).isFalse()
        assertThat(itemType.decayTime).isEqualTo(6)
        assertThat(itemType.showDuration).isFalse()
        assertThat(itemType.decayTo).isEqualTo(7)
        assertThat(itemType.transformEquipTo).isEqualTo(8)
        assertThat(itemType.transformDeEquipTo).isEqualTo(9)
        assertThat(itemType.shootType).isEqualTo(10)
        assertThat(itemType.hitChance).isEqualTo(11)
        assertThat(itemType.maxHitChance).isEqualTo(12)
        assertThat(itemType.slotPosition).isEqualTo(SLOTP_TWO_HAND)

        environment.loadChunk("w:slotType('one-handed')")
        assertThat(itemType.slotPosition).isEqualTo(SLOTP_HAND)
    }

    @Test
    fun `Weapon setters and register fill the registry and item wield info`() {
        val (environment, operations) = installed()

        environment.loadChunk(
            """
            w = Weapon(1)
            w:id(110)
            w:level(20)
            w:magicLevel(5)
            w:mana(30)
            w:manaPercent(10)
            w:health(40)
            w:healthPercent(5)
            w:soul(2)
            w:breakChance(3)
            w:premium(true)
            w:wieldUnproperly(true)
            w:vocation("knight", true)
            result = w:register()
            """.trimIndent()
        )

        val weapon = state(environment, "w").weapon
        assertThat(weapon.getID()).isEqualTo(110)
        assertThat(weapon.getReqLevel()).isEqualTo(20)
        assertThat(weapon.getReqMagLv()).isEqualTo(5)
        assertThat(weapon.getMana()).isEqualTo(30)
        assertThat(weapon.getManaPercent()).isEqualTo(10)
        assertThat(weapon.getHealth()).isEqualTo(40)
        assertThat(weapon.getHealthPercent()).isEqualTo(5)
        assertThat(weapon.getSoul()).isEqualTo(2)
        assertThat(weapon.getBreakChance()).isEqualTo(3)
        assertThat(weapon.isPremium()).isTrue()
        assertThat(weapon.isWieldedUnproperly()).isTrue()
        assertThat(weapon.getVocationString()).isEqualTo("knights")

        assertThat(environment.getGlobal("result").toboolean()).isTrue()
        assertThat(operations.registered).hasSize(1)
        assertThat(operations.registered.single().first).isSameAs(weapon)

        val itemType = operations.itemTypes.getValue(110)
        assertThat(itemType.weaponType).isEqualTo(org.opentibia.canary.creatures.WeaponType_t.WEAPON_SWORD)
        assertThat(itemType.wieldInfo).isEqualTo(
            (WIELDINFO_LEVEL or WIELDINFO_MAGLV or WIELDINFO_VOCREQ or WIELDINFO_PREMIUM).toLong(),
        )
        assertThat(itemType.vocationString).isEqualTo("knights")
        assertThat(itemType.minReqLevel).isEqualTo(20)
        assertThat(itemType.minReqMagicLevel).isEqualTo(5)
    }

    @Test
    fun `Weapon action accepts the Canary names and always returns true for a weapon`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            w = Weapon(1)
            a = w:action("removecount")
            b = w:action("RemoveCharge")
            c = w:action("move")
            """.trimIndent()
        )

        assertThat(environment.getGlobal("a").toboolean()).isTrue()
        assertThat(environment.getGlobal("b").toboolean()).isTrue()
        assertThat(environment.getGlobal("c").toboolean()).isTrue()
        assertThat(state(environment, "w").weapon.action).isEqualTo(WeaponAction_t.WEAPONACTION_MOVE)
    }

    @Test
    fun `Weapon element and extraElement resolve strings and numeric combat types`() {
        val (environment, operations) = installed()

        environment.loadChunk(
            """
            w = Weapon(1)
            w:id(120)
            e1 = w:element("fire")
            unknown = w:element("bogus")
            e2 = w:element(2)
            extra = w:extraElement(5, "holy")
            """.trimIndent()
        )

        assertThat(environment.getGlobal("e1").toboolean()).isTrue()
        assertThat(environment.getGlobal("e2").toboolean()).isTrue()
        assertThat(environment.getGlobal("extra").toboolean()).isTrue()
        assertThat(state(environment, "w").weapon.params.combatType).isEqualTo(CombatType.EARTHDAMAGE)

        val abilities = operations.itemTypes.getValue(120).abilities
        assertThat(abilities).isNotNull
        assertThat(abilities!!.elementDamage).isEqualTo(5)
        assertThat(abilities.elementType).isEqualTo(CombatType.HOLYDAMAGE)

        environment.loadChunk(
            """
            w2 = Weapon(1)
            w2:id(121)
            w2:element("fire")
            w2:element("bogus")
            """.trimIndent()
        )
        assertThat(environment.getGlobal("unknown").toboolean()).isTrue()
        assertThat(state(environment, "w2").weapon.params.combatType).isEqualTo(CombatType.FIREDAMAGE)
    }

    @Test
    fun `Weapon damage is wand-only and feeds the registered maxHitChance`() {
        val (environment, operations) = installed()

        environment.loadChunk(
            """
            wand = Weapon(6)
            wand:id(130)
            wand:damage(10, 20)
            wand:damage(7)
            wandResult = wand:register()

            melee = Weapon(1)
            meleeResult = melee:damage(5)
            """.trimIndent()
        )

        assertThat(environment.getGlobal("wandResult").toboolean()).isTrue()
        assertThat(environment.getGlobal("meleeResult").isnil()).isTrue()
        assertThat(operations.itemTypes.getValue(130).maxHitChance).isEqualTo(7)
    }

    @Test
    fun `Weapon ammoType is distance-only and rejects unknown names`() {
        val (environment, operations) = installed()

        environment.loadChunk(
            """
            dist = Weapon(5)
            dist:id(140)
            arrow = dist:ammoType("arrow")

            dist2 = Weapon(5)
            dist2:id(141)
            bolt = dist2:ammoType("bolt")

            dist3 = Weapon(5)
            dist3:id(142)
            invalid = dist3:ammoType("snowball")

            melee = Weapon(1)
            meleeResult = melee:ammoType("arrow")
            """.trimIndent()
        )

        assertThat(environment.getGlobal("arrow").toboolean()).isTrue()
        assertThat(environment.getGlobal("bolt").toboolean()).isTrue()
        assertThat(environment.getGlobal("invalid").isnil()).isTrue()
        assertThat(environment.getGlobal("meleeResult").isnil()).isTrue()
        assertThat(operations.itemTypes.getValue(140).ammoType).isEqualTo(Ammo_t.AMMO_ARROW.value)
        assertThat(operations.itemTypes.getValue(141).ammoType).isEqualTo(Ammo_t.AMMO_BOLT.value)
        assertThat(operations.itemTypes.getValue(142).ammoType).isEqualTo(0)
    }

    @Test
    fun `Weapon onUseWeapon captures the callback and register forwards it`() {
        val (environment, operations) = installed()

        environment.loadChunk(
            """
            w = Weapon(1)
            w:id(150)
            stored = w:onUseWeapon(function() return true end)
            registered = w:register()

            empty = Weapon(1)
            empty:id(151)
            missing = empty:onUseWeapon()
            emptyResult = empty:register()

            direct = Weapon(1)
            direct:id(152)
            function direct.onUseWeapon(callback) return "direct" end
            directResult = direct:register()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("stored").toboolean()).isTrue()
        assertThat(environment.getGlobal("registered").toboolean()).isTrue()
        assertThat(environment.getGlobal("missing").toboolean()).isFalse()
        assertThat(environment.getGlobal("emptyResult").toboolean()).isTrue()
        assertThat(environment.getGlobal("directResult").toboolean()).isTrue()
        assertThat(operations.registered).hasSize(3)
        assertThat(operations.registered[0].second.isfunction()).isTrue()
        assertThat(operations.registered[1].second.isfunction()).isFalse()
        assertThat(operations.registered[2].second.isfunction()).isTrue()
    }

    @Test
    fun `Weapon vocation accumulates the description with the and separator on the last`() {
        val (environment, _) = installed()

        environment.loadChunk(
            """
            w = Weapon(1)
            w:id(160)
            w:vocation("knight", true)
            w:vocation("druid", true, false)
            w:vocation("sorcerer", true, true)
            """.trimIndent()
        )

        assertThat(state(environment, "w").weapon.getVocationString()).isEqualTo("knights, druids and sorcerers")
    }
}
