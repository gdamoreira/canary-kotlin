package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.Fluids_t
import org.opentibia.canary.items.ItemGroup_t
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLuaOperations

/**
 * Transcript tests for `src/lua/functions/items/item_type_functions.cpp` driven
 * through real Lua chunks against real [ItemType] domain objects wired by
 * [WorldLuaOperations].
 */
class ItemTypeFunctionsTest {

    private fun envWith(types: List<ItemType>): LuaEnvironment {
        val registry = ItemTypeRegistry().apply { types.forEach { register(it) } }
        val byName = types.associate { it.name to it.id }
        return LuaEnvironment().apply { initState() }
            .also { WorldLuaOperations(itemTypes = registry, itemIdsByName = byName).install(it) }
    }

    @Test
    fun `ItemType classification predicates read the domain item type`() {
        val environment = envWith(
            listOf(
                ItemType(101, "corpse", isCorpse = true),
                ItemType(102, "door", isDoor = true, type = ItemTypes_t.ITEM_TYPE_DOOR),
                ItemType(103, "backpack", isContainer = true, group = ItemGroup_t.ITEM_GROUP_CONTAINER),
                ItemType(104, "mana potion", isFluidContainer = true, group = ItemGroup_t.ITEM_GROUP_FLUID),
                ItemType(105, "movable stone", isMovable = true),
                ItemType(106, "blank rune", type = ItemTypes_t.ITEM_TYPE_RUNE),
                ItemType(107, "key", type = ItemTypes_t.ITEM_TYPE_KEY),
                ItemType(108, "quiver", type = ItemTypes_t.ITEM_TYPE_QUIVER),
                ItemType(109, "podium", isPodium = true),
                ItemType(110, "magic sword", weaponType = WeaponType_t.WEAPON_SWORD),
                ItemType(111, "wooden shield", weaponType = WeaponType_t.WEAPON_SHIELD),
                ItemType(112, "grass", isGroundTile = true, group = ItemGroup_t.ITEM_GROUP_GROUND),
                ItemType(113, "spellbook", canReadText = true, canWriteText = true),
                ItemType(114, "fire field", type = ItemTypes_t.ITEM_TYPE_MAGICFIELD, multiUse = true),
                ItemType(115, "parcel", blockProjectile = true),
                ItemType(116, "crystal coin", isStackable = true, wareId = 2160),
            ),
        )

        environment.loadChunk("corpse = ItemType(101):isCorpse()")
        environment.loadChunk("door = ItemType(102):isDoor()")
        environment.loadChunk("container = ItemType(103):isContainer()")
        environment.loadChunk("fluid = ItemType(104):isFluidContainer()")
        environment.loadChunk("movable = ItemType(105):isMovable()")
        environment.loadChunk("rune = ItemType(106):isRune()")
        environment.loadChunk("key = ItemType(107):isKey()")
        environment.loadChunk("quiver = ItemType(108):isQuiver()")
        environment.loadChunk("podium = ItemType(109):isPodium()")
        environment.loadChunk("sword = ItemType(110):isWeapon()")
        environment.loadChunk("shield = ItemType(111):isWeapon()")
        environment.loadChunk("ground = ItemType(112):isGroundTile()")
        environment.loadChunk("readable = ItemType(113):isReadable()")
        environment.loadChunk("writable = ItemType(113):isWritable()")
        environment.loadChunk("magicField = ItemType(114):isMagicField()")
        environment.loadChunk("multiUse = ItemType(114):isMultiUse()")
        environment.loadChunk("blocking = ItemType(115):isBlocking()")
        environment.loadChunk("stowable = ItemType(116):isStowable()")

        for (name in listOf(
            "corpse", "door", "container", "fluid", "movable", "rune", "key", "quiver",
            "podium", "sword", "ground", "readable", "writable", "magicField", "multiUse",
            "blocking", "stowable",
        )) {
            assertThat(environment.getGlobal(name).toboolean()).describedAs(name).isTrue()
        }
        assertThat(environment.getGlobal("shield").toboolean()).isFalse()
    }

    @Test
    fun `ItemType hasSubType covers fluid splash charges and stackable`() {
        val environment = envWith(
            listOf(
                ItemType(120, "splash", isSplash = true, group = ItemGroup_t.ITEM_GROUP_SPLASH),
                ItemType(121, "fluid", isFluidContainer = true),
                ItemType(122, "charged", charges = 5),
                ItemType(123, "coins", isStackable = true),
                ItemType(124, "plain"),
            ),
        )

        environment.loadChunk("splash = ItemType(120):hasSubType()")
        environment.loadChunk("fluid = ItemType(121):hasSubType()")
        environment.loadChunk("charged = ItemType(122):hasSubType()")
        environment.loadChunk("coins = ItemType(123):hasSubType()")
        environment.loadChunk("plain = ItemType(124):hasSubType()")

        assertThat(environment.getGlobal("splash").toboolean()).isTrue()
        assertThat(environment.getGlobal("fluid").toboolean()).isTrue()
        assertThat(environment.getGlobal("charged").toboolean()).isTrue()
        assertThat(environment.getGlobal("coins").toboolean()).isTrue()
        assertThat(environment.getGlobal("plain").toboolean()).isFalse()
    }

    @Test
    fun `ItemType scalar getters surface the domain fields`() {
        val environment = envWith(
            listOf(
                ItemType(
                    2400,
                    "magic sword",
                    article = "a",
                    pluralName = "magic swords",
                    attack = 48,
                    defense = 35,
                    extraDefense = 4,
                    armor = 2,
                    weaponType = WeaponType_t.WEAPON_SWORD,
                    hitChance = 6,
                    shootRange = 3,
                    imbuementSlot = 2,
                    charges = 9,
                    fluidSource = Fluids_t.FLUID_WATER.value,
                    slotPosition = 48,
                    stackSize = 42,
                    maxItems = 24,
                    speed = 17,
                    showDuration = true,
                    wrapableTo = 23398,
                    decayTo = 5,
                    decayTime = 600,
                    minReqLevel = 80,
                    transformEquipTo = 11,
                    transformDeEquipTo = 12,
                    destroyTo = 13,
                    ammoType = Ammo_t.AMMO_BOLT.value,
                    elementalBond = CombatType.FIREDAMAGE,
                    vocationString = "for knights only",
                    type = ItemTypes_t.ITEM_TYPE_SWORD,
                ),
                ItemType(2160, "crystal coin", weight = 0.1, isStackable = true),
                ItemType(2161, "hidden thing", showCount = false),
            ),
        )

        environment.loadChunk("type = ItemType(2400):getType()")
        environment.loadChunk("article = ItemType(2400):getArticle()")
        environment.loadChunk("plural = ItemType(2400):getPluralName()")
        environment.loadChunk("coinPlural = ItemType(2160):getPluralName()")
        environment.loadChunk("hiddenPlural = ItemType(2161):getPluralName()")
        environment.loadChunk("slot = ItemType(2400):getSlotPosition()")
        environment.loadChunk("stackSize = ItemType(2400):getStackSize()")
        environment.loadChunk("capacity = ItemType(2400):getCapacity()")
        environment.loadChunk("charges = ItemType(2400):getCharges()")
        environment.loadChunk("fluidSource = ItemType(2400):getFluidSource()")
        environment.loadChunk("hitChance = ItemType(2400):getHitChance()")
        environment.loadChunk("shootRange = ItemType(2400):getShootRange()")
        environment.loadChunk("attack = ItemType(2400):getAttack()")
        environment.loadChunk("defense = ItemType(2400):getDefense()")
        environment.loadChunk("extraDefense = ItemType(2400):getExtraDefense()")
        environment.loadChunk("imbuementSlot = ItemType(2400):getImbuementSlot()")
        environment.loadChunk("armor = ItemType(2400):getArmor()")
        environment.loadChunk("weaponType = ItemType(2400):getWeaponType()")
        environment.loadChunk("ammoType = ItemType(2400):getAmmoType()")
        environment.loadChunk("transformEquip = ItemType(2400):getTransformEquipId()")
        environment.loadChunk("transformDeEquip = ItemType(2400):getTransformDeEquipId()")
        environment.loadChunk("destroy = ItemType(2400):getDestroyId()")
        environment.loadChunk("decay = ItemType(2400):getDecayId()")
        environment.loadChunk("decayTime = ItemType(2400):getDecayTime()")
        environment.loadChunk("requiredLevel = ItemType(2400):getRequiredLevel()")
        environment.loadChunk("showDuration = ItemType(2400):getShowDuration()")
        environment.loadChunk("wrapableTo = ItemType(2400):getWrapableTo()")
        environment.loadChunk("baseSpeed = ItemType(2400):getBaseSpeed()")
        environment.loadChunk("vocationString = ItemType(2400):getVocationString()")
        environment.loadChunk("elementalBond = ItemType(2400):getElementalBond()")
        environment.loadChunk("weight = ItemType(2160):getWeight()")
        environment.loadChunk("weighted = ItemType(2160):getWeight(5)")

        assertThat(environment.getGlobal("type").toint()).isEqualTo(ItemTypes_t.ITEM_TYPE_SWORD.value)
        assertThat(environment.getGlobal("article").tojstring()).isEqualTo("a")
        assertThat(environment.getGlobal("plural").tojstring()).isEqualTo("magic swords")
        assertThat(environment.getGlobal("coinPlural").tojstring()).isEqualTo("crystal coins")
        assertThat(environment.getGlobal("hiddenPlural").tojstring()).isEqualTo("hidden thing")
        assertThat(environment.getGlobal("slot").toint()).isEqualTo(48)
        assertThat(environment.getGlobal("stackSize").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("capacity").toint()).isEqualTo(24)
        assertThat(environment.getGlobal("charges").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("fluidSource").toint()).isEqualTo(Fluids_t.FLUID_WATER.value)
        assertThat(environment.getGlobal("hitChance").toint()).isEqualTo(6)
        assertThat(environment.getGlobal("shootRange").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("attack").toint()).isEqualTo(48)
        assertThat(environment.getGlobal("defense").toint()).isEqualTo(35)
        assertThat(environment.getGlobal("extraDefense").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("imbuementSlot").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("armor").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("weaponType").toint()).isEqualTo(WeaponType_t.WEAPON_SWORD.value)
        assertThat(environment.getGlobal("ammoType").toint()).isEqualTo(Ammo_t.AMMO_BOLT.value)
        assertThat(environment.getGlobal("transformEquip").toint()).isEqualTo(11)
        assertThat(environment.getGlobal("transformDeEquip").toint()).isEqualTo(12)
        assertThat(environment.getGlobal("destroy").toint()).isEqualTo(13)
        assertThat(environment.getGlobal("decay").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("decayTime").toint()).isEqualTo(600)
        assertThat(environment.getGlobal("requiredLevel").toint()).isEqualTo(80)
        assertThat(environment.getGlobal("showDuration").toboolean()).isTrue()
        assertThat(environment.getGlobal("wrapableTo").toint()).isEqualTo(23398)
        assertThat(environment.getGlobal("baseSpeed").toint()).isEqualTo(17)
        assertThat(environment.getGlobal("vocationString").tojstring()).isEqualTo("for knights only")
        assertThat(environment.getGlobal("elementalBond").toint()).isEqualTo(CombatType.FIREDAMAGE.value)
        assertThat(environment.getGlobal("weight").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("weighted").toint()).isEqualTo(50)
    }

    @Test
    fun `ItemType ability getters are nil without abilities and values with them`() {
        val environment = envWith(
            listOf(
                ItemType(2400, "magic sword"),
                ItemType(
                    2500,
                    "ice wand",
                    abilities = Abilities().apply {
                        elementType = CombatType.ICEDAMAGE
                        elementDamage = 33
                        speed = 12
                    },
                ),
            ),
        )

        environment.loadChunk("plainElement = ItemType(2400):getElementType()")
        environment.loadChunk("plainDamage = ItemType(2400):getElementDamage()")
        environment.loadChunk("plainSpeed = ItemType(2400):getSpeed()")
        environment.loadChunk("element = ItemType(2500):getElementType()")
        environment.loadChunk("damage = ItemType(2500):getElementDamage()")
        environment.loadChunk("speed = ItemType(2500):getSpeed()")

        assertThat(environment.getGlobal("plainElement").isnil()).isTrue()
        assertThat(environment.getGlobal("plainDamage").isnil()).isTrue()
        assertThat(environment.getGlobal("plainSpeed").isnil()).isTrue()
        assertThat(environment.getGlobal("element").toint()).isEqualTo(CombatType.ICEDAMAGE.value)
        assertThat(environment.getGlobal("damage").toint()).isEqualTo(33)
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(12)
    }

    @Test
    fun `ItemType constructor resolves numbers and names through the registry`() {
        val environment = envWith(listOf(ItemType(2400, "magic sword")))

        environment.loadChunk("byId = ItemType(2400)")
        environment.loadChunk("byName = ItemType('magic sword')")
        environment.loadChunk("id = byName:getId()")
        environment.loadChunk("name = byId:getName()")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(2400)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("magic sword")
    }
}
