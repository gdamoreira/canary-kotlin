package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the `ItemType` Lua binding library. The engine seam is
 * fake, but every method is invoked through real Lua chunks with the exact Canary
 * names (ItemType(2160), itemType:isStackable(), itemType:getWeight(5), ...).
 */
class ItemTypeBindingsTest {

    private class FakeItemType(val id: Int, val name: String)

    private class FakeItemTypeOperations : ItemTypeOperations {
        override fun create(idOrName: Any): Any? = when (idOrName) {
            is Number -> FakeItemType(idOrName.toInt(), "item ${idOrName.toInt()}")
            is String -> FakeItemType(2160, idOrName)
            else -> null
        }

        override fun getId(itemType: Any): Int = (itemType as FakeItemType).id

        override fun getName(itemType: Any): String = (itemType as FakeItemType).name

        override fun isContainer(itemType: Any): Boolean = false

        override fun isStackable(itemType: Any): Boolean = true

        override fun isPickupable(itemType: Any): Boolean = true

        override fun getCapacity(itemType: Any): Int = 20

        override fun getWeight(itemType: Any, count: Int): Int = 95 * maxOf(1, count)

        override fun hasSubType(itemType: Any): Boolean = true
    }

    /** Every method Canary registers in `ItemTypeFunctions::init`. */
    private val methodNames = listOf(
        "isCorpse", "isDoor", "isContainer", "isFluidContainer", "isMovable", "isRune",
        "isStackable", "isStowable", "isReadable", "isWritable", "isBlocking",
        "isGroundTile", "isMagicField", "isMultiUse", "isPickupable", "isKey",
        "isQuiver", "isPodium", "isWeapon",
        "getType", "getId", "getName", "getPluralName", "getArticle", "getDescription",
        "getSlotPosition",
        "getCharges", "getFluidSource", "getCapacity", "getWeight", "getStackSize",
        "getHitChance", "getShootRange",
        "getAttack", "getDefense", "getExtraDefense", "getImbuementSlot", "getArmor",
        "getWeaponType",
        "getElementType", "getElementDamage",
        "getTransformEquipId", "getTransformDeEquipId", "getDestroyId", "getDecayId",
        "getRequiredLevel", "getAmmoType",
        "getDecayTime", "getShowDuration", "getWrapableTo", "getSpeed", "getBaseSpeed",
        "getVocationString", "getElementalBond", "hasSubType",
    )

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `ItemType registers every Canary method name`() {
        val environment = env()
        ItemTypeFunctions.install(environment, FakeItemTypeOperations())

        val checks = methodNames.joinToString(" and ") {
            "(rawgetmetatable('ItemType').__metatable.$it ~= nil)"
        }
        environment.loadChunk("checks = $checks and (rawgetmetatable('ItemType').__eq ~= nil)")

        assertThat(methodNames).hasSize(55)
        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
    }

    @Test
    fun `ItemType constructor resolves ids and names through the seam`() {
        val environment = env()
        ItemTypeFunctions.install(environment, FakeItemTypeOperations())

        environment.loadChunk("byId = ItemType(2160)")
        environment.loadChunk("byName = ItemType('crystal coin')")
        environment.loadChunk("id = byId:getId()")
        environment.loadChunk("name = byName:getName()")
        environment.loadChunk("missing = ItemType(true)")

        assertThat(environment.getGlobal("byId").isuserdata()).isTrue()
        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("crystal coin")
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `ItemType domain-backed getters delegate to the seam`() {
        val environment = env()
        ItemTypeFunctions.install(environment, FakeItemTypeOperations())
        environment.globals.set("itemType", environment.userdata.wrap("ItemType", FakeItemType(2160, "crystal coin")))

        environment.loadChunk("id = itemType:getId()")
        environment.loadChunk("name = itemType:getName()")
        environment.loadChunk("container = itemType:isContainer()")
        environment.loadChunk("stackable = itemType:isStackable()")
        environment.loadChunk("pickupable = itemType:isPickupable()")
        environment.loadChunk("capacity = itemType:getCapacity()")
        environment.loadChunk("weight = itemType:getWeight()")
        environment.loadChunk("weighted = itemType:getWeight(5)")
        environment.loadChunk("subType = itemType:hasSubType()")

        assertThat(environment.getGlobal("id").toint()).isEqualTo(2160)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("crystal coin")
        assertThat(environment.getGlobal("container").toboolean()).isFalse()
        assertThat(environment.getGlobal("stackable").toboolean()).isTrue()
        assertThat(environment.getGlobal("pickupable").toboolean()).isTrue()
        assertThat(environment.getGlobal("capacity").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("weight").toint()).isEqualTo(95)
        assertThat(environment.getGlobal("weighted").toint()).isEqualTo(475)
        assertThat(environment.getGlobal("subType").toboolean()).isTrue()
    }

    @Test
    fun `ItemType unmodeled getters expose the documented Canary defaults`() {
        val environment = env()
        ItemTypeFunctions.install(environment, FakeItemTypeOperations())
        environment.globals.set("itemType", environment.userdata.wrap("ItemType", FakeItemType(2160, "crystal coin")))

        environment.loadChunk("corpse = itemType:isCorpse()")
        environment.loadChunk("door = itemType:isDoor()")
        environment.loadChunk("fluid = itemType:isFluidContainer()")
        environment.loadChunk("movable = itemType:isMovable()")
        environment.loadChunk("rune = itemType:isRune()")
        environment.loadChunk("stowable = itemType:isStowable()")
        environment.loadChunk("readable = itemType:isReadable()")
        environment.loadChunk("writable = itemType:isWritable()")
        environment.loadChunk("blocking = itemType:isBlocking()")
        environment.loadChunk("ground = itemType:isGroundTile()")
        environment.loadChunk("magicField = itemType:isMagicField()")
        environment.loadChunk("multiUse = itemType:isMultiUse()")
        environment.loadChunk("key = itemType:isKey()")
        environment.loadChunk("quiver = itemType:isQuiver()")
        environment.loadChunk("podium = itemType:isPodium()")
        environment.loadChunk("weapon = itemType:isWeapon()")
        environment.loadChunk("type = itemType:getType()")
        environment.loadChunk("plural = itemType:getPluralName()")
        environment.loadChunk("article = itemType:getArticle()")
        environment.loadChunk("description = itemType:getDescription()")
        environment.loadChunk("slotPosition = itemType:getSlotPosition()")
        environment.loadChunk("charges = itemType:getCharges()")
        environment.loadChunk("fluidSource = itemType:getFluidSource()")
        environment.loadChunk("stackSize = itemType:getStackSize()")
        environment.loadChunk("hitChance = itemType:getHitChance()")
        environment.loadChunk("shootRange = itemType:getShootRange()")
        environment.loadChunk("attack = itemType:getAttack()")
        environment.loadChunk("defense = itemType:getDefense()")
        environment.loadChunk("extraDefense = itemType:getExtraDefense()")
        environment.loadChunk("imbuementSlot = itemType:getImbuementSlot()")
        environment.loadChunk("armor = itemType:getArmor()")
        environment.loadChunk("weaponType = itemType:getWeaponType()")
        environment.loadChunk("elementType = itemType:getElementType()")
        environment.loadChunk("elementDamage = itemType:getElementDamage()")
        environment.loadChunk("transformEquip = itemType:getTransformEquipId()")
        environment.loadChunk("transformDeEquip = itemType:getTransformDeEquipId()")
        environment.loadChunk("destroy = itemType:getDestroyId()")
        environment.loadChunk("decay = itemType:getDecayId()")
        environment.loadChunk("requiredLevel = itemType:getRequiredLevel()")
        environment.loadChunk("ammoType = itemType:getAmmoType()")
        environment.loadChunk("decayTime = itemType:getDecayTime()")
        environment.loadChunk("showDuration = itemType:getShowDuration()")
        environment.loadChunk("wrapableTo = itemType:getWrapableTo()")
        environment.loadChunk("speed = itemType:getSpeed()")
        environment.loadChunk("baseSpeed = itemType:getBaseSpeed()")
        environment.loadChunk("vocationString = itemType:getVocationString()")
        environment.loadChunk("elementalBond = itemType:getElementalBond()")

        for (name in listOf(
            "corpse", "door", "fluid", "movable", "rune", "stowable", "readable", "writable",
            "blocking", "ground", "magicField", "multiUse", "key", "quiver", "podium", "weapon",
            "showDuration",
        )) {
            assertThat(environment.getGlobal(name).toboolean()).describedAs(name).isFalse()
        }
        assertThat(environment.getGlobal("plural").tojstring()).isEqualTo("crystal coins")
        assertThat(environment.getGlobal("article").tojstring()).isEmpty()
        assertThat(environment.getGlobal("description").tojstring()).isEmpty()
        assertThat(environment.getGlobal("vocationString").tojstring()).isEmpty()
        assertThat(environment.getGlobal("slotPosition").toint()).isEqualTo(48)
        assertThat(environment.getGlobal("stackSize").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("shootRange").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("decay").toint()).isEqualTo(-1)
        for (name in listOf(
            "type", "charges", "fluidSource", "hitChance", "attack", "defense", "extraDefense",
            "imbuementSlot", "armor", "weaponType", "transformEquip", "transformDeEquip",
            "destroy", "requiredLevel", "ammoType", "decayTime", "wrapableTo", "baseSpeed",
        )) {
            assertThat(environment.getGlobal(name).toint()).describedAs(name).isEqualTo(0)
        }
        assertThat(environment.getGlobal("elementalBond").toint()).isEqualTo(255)
        assertThat(environment.getGlobal("elementType").isnil()).isTrue()
        assertThat(environment.getGlobal("elementDamage").isnil()).isTrue()
        assertThat(environment.getGlobal("speed").isnil()).isTrue()
    }

    @Test
    fun `ItemType equality compares the underlying types`() {
        val environment = env()
        ItemTypeFunctions.install(environment, FakeItemTypeOperations())
        val itemType = FakeItemType(2160, "crystal coin")
        environment.globals.set("a", environment.userdata.wrap("ItemType", itemType))
        environment.globals.set("b", environment.userdata.wrap("ItemType", itemType))
        environment.globals.set("c", environment.userdata.wrap("ItemType", FakeItemType(2148, "gold coin")))

        environment.loadChunk("same = (a == b)")
        environment.loadChunk("different = (a == c)")

        assertThat(environment.getGlobal("same").toboolean()).isTrue()
        assertThat(environment.getGlobal("different").toboolean()).isFalse()
    }
}
