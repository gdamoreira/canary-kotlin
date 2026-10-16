package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLuaOperations
import org.opentibia.canary.map.Position

/**
 * Transcript-style parity tests for the full Canary `Item`/`Container` userdata method
 * set (src/lua/functions/items/item_functions.cpp, 55 registered methods).
 *
 * The first group pins the exact Canary names on both metatables; the remaining groups
 * install the documented no-op seams and pin the observable contract for item state the
 * Kotlin domain does not model yet (ownership names, actors, shaders, imbuements,
 * serialization), including the missing-item guards.
 */
class ItemFunctionsParityTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Item and Container register every Canary item_functions method`() {
        val environment = env()
        ItemFunctions.install(environment)

        val itemChecks = CANARY_ITEM_METHODS.joinToString(" and ") { "(rawgetmetatable('Item').__metatable.$it ~= nil)" }
        val containerChecks = CANARY_ITEM_METHODS.joinToString(" and ") { "(rawgetmetatable('Container').__metatable.$it ~= nil)" }
        environment.loadChunk("checks = ($itemChecks) and ($containerChecks)")

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
        assertThat(CANARY_ITEM_METHODS).hasSize(55)
    }

    @Test
    fun `Item ownership methods use the unwired defaults`() {
        val environment = env()
        ItemFunctions.install(environment)
        environment.globals.set("item", environment.userdata.wrap("Item", Any()))

        environment.loadChunk(
            "setOwner = item:setOwner(10) " +
                "ownerId = item:getOwnerId() " +
                "isOwner = item:isOwner(10) " +
                "ownerName = item:getOwnerName() " +
                "hasOwner = item:hasOwner()",
        )

        assertThat(environment.getGlobal("setOwner").toboolean()).isFalse()
        assertThat(environment.getGlobal("ownerId").isnil()).isTrue()
        assertThat(environment.getGlobal("isOwner").toboolean()).isFalse()
        assertThat(environment.getGlobal("ownerName").isnil()).isTrue()
        assertThat(environment.getGlobal("hasOwner").toboolean()).isFalse()
    }

    @Test
    fun `Item actor and shader methods use the unwired defaults`() {
        val environment = env()
        ItemFunctions.install(environment)
        environment.globals.set("item", environment.userdata.wrap("Item", Any()))

        environment.loadChunk(
            "actor = item:actor() " +
                "actorSet = item:actor(true) " +
                "hasShader = item:hasShader() " +
                "shader = item:getShader() " +
                "shaderSet = item:setShader('outline')",
        )

        assertThat(environment.getGlobal("actor").toboolean()).isFalse()
        assertThat(environment.getGlobal("actorSet").toboolean()).isFalse()
        assertThat(environment.getGlobal("hasShader").toboolean()).isFalse()
        assertThat(environment.getGlobal("shader").tojstring()).isEmpty()
        assertThat(environment.getGlobal("shaderSet").toboolean()).isFalse()
    }

    @Test
    fun `Item tier classification and carpet methods use the unwired defaults`() {
        val environment = env()
        ItemFunctions.install(environment)
        environment.globals.set("item", environment.userdata.wrap("Item", Any()))

        environment.loadChunk(
            "tier = item:getTier() " +
                "tierSet = item:setTier(3) " +
                "classification = item:getClassification() " +
                "carpet = item:canReceiveAutoCarpet() " +
                "property = item:hasProperty(0)",
        )

        assertThat(environment.getGlobal("tier").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("tierSet").toboolean()).isFalse()
        assertThat(environment.getGlobal("classification").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("carpet").toboolean()).isFalse()
        assertThat(environment.getGlobal("property").toboolean()).isFalse()
    }

    @Test
    fun `Item imbuement duration and depot methods use the unwired defaults`() {
        val environment = env()
        ItemFunctions.install(environment)
        environment.globals.set("item", environment.userdata.wrap("Item", Any()))

        environment.loadChunk(
            "slots = item:getImbuementSlot() " +
                "imbuement = item:getImbuement() " +
                "duration = item:setDuration(10000) " +
                "durationRange = item:setDuration(10000, 20000, 2129, false) " +
                "depot = item:isInsideDepot() " +
                "inbox = item:isInsideDepot(true)",
        )

        assertThat(environment.getGlobal("slots").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("imbuement").isnil()).isTrue()
        assertThat(environment.getGlobal("duration").toboolean()).isFalse()
        assertThat(environment.getGlobal("durationRange").toboolean()).isFalse()
        assertThat(environment.getGlobal("depot").toboolean()).isFalse()
        assertThat(environment.getGlobal("inbox").toboolean()).isFalse()
    }

    @Test
    fun `Item movement serialization and expert pvp methods use the unwired defaults`() {
        val environment = env()
        ItemFunctions.install(environment)
        environment.globals.set("item", environment.userdata.wrap("Item", Any()))
        environment.globals.set("player", environment.userdata.wrap("Player", Any()))

        environment.loadChunk(
            "moved = item:moveToSlot(player, 3) " +
                "serialized = item:serializeAttributes() " +
                "expertPvp = item:setExpertPvpFieldContext(player)",
        )

        assertThat(environment.getGlobal("moved").toboolean()).isFalse()
        assertThat(environment.getGlobal("serialized").isnil()).isTrue()
        assertThat(environment.getGlobal("expertPvp").toboolean()).isFalse()
    }

    @Test
    fun `Item missing-item guards mirror the Canary push conventions`() {
        val environment = env()
        ItemFunctions.install(environment)

        environment.loadChunk(
            "noOwnerId = select('#', rawgetmetatable('Item').__metatable.getOwnerId(5)) " +
                "noOwnerName = select('#', rawgetmetatable('Item').__metatable.getOwnerName(5)) " +
                "noSetOwner = select('#', rawgetmetatable('Item').__metatable.setOwner(5, 1)) " +
                "noIsOwner = select('#', rawgetmetatable('Item').__metatable.isOwner(5, 1)) " +
                "noHasOwner = rawgetmetatable('Item').__metatable.hasOwner(5) " +
                "noActor = rawgetmetatable('Item').__metatable.actor(5) " +
                "noHasShader = rawgetmetatable('Item').__metatable.hasShader(5) " +
                "noGetShader = rawgetmetatable('Item').__metatable.getShader(5) " +
                "noSetShader = rawgetmetatable('Item').__metatable.setShader(5, 'x') " +
                "noSlots = rawgetmetatable('Item').__metatable.getImbuementSlot(5) " +
                "noImbuement = rawgetmetatable('Item').__metatable.getImbuement(5) " +
                "noDuration = rawgetmetatable('Item').__metatable.setDuration(5, 1) " +
                "noDepot = rawgetmetatable('Item').__metatable.isInsideDepot(5) " +
                "noTier = rawgetmetatable('Item').__metatable.getTier(5) " +
                "noSetTier = rawgetmetatable('Item').__metatable.setTier(5, 1) " +
                "noClassification = rawgetmetatable('Item').__metatable.getClassification(5) " +
                "noCarpet = rawgetmetatable('Item').__metatable.canReceiveAutoCarpet(5) " +
                "noProperty = rawgetmetatable('Item').__metatable.hasProperty(5, 0) " +
                "noMoveSlot = rawgetmetatable('Item').__metatable.moveToSlot(5, 1, 2) " +
                "noSerialize = rawgetmetatable('Item').__metatable.serializeAttributes(5)",
        )

        assertThat(environment.getGlobal("noOwnerId").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("noOwnerName").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("noSetOwner").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("noIsOwner").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("noHasOwner").isnil()).isTrue()
        assertThat(environment.getGlobal("noActor").isnil()).isTrue()
        assertThat(environment.getGlobal("noHasShader").isnil()).isTrue()
        assertThat(environment.getGlobal("noGetShader").toboolean()).isFalse()
        assertThat(environment.getGlobal("noSetShader").toboolean()).isFalse()
        assertThat(environment.getGlobal("noSlots").toboolean()).isFalse()
        assertThat(environment.getGlobal("noImbuement").toboolean()).isFalse()
        assertThat(environment.getGlobal("noDuration").toboolean()).isFalse()
        assertThat(environment.getGlobal("noDepot").toboolean()).isFalse()
        assertThat(environment.getGlobal("noTier").toboolean()).isFalse()
        assertThat(environment.getGlobal("noSetTier").toboolean()).isFalse()
        assertThat(environment.getGlobal("noClassification").toboolean()).isFalse()
        assertThat(environment.getGlobal("noCarpet").toboolean()).isFalse()
        assertThat(environment.getGlobal("noProperty").toboolean()).isFalse()
        assertThat(environment.getGlobal("noMoveSlot").isnil()).isTrue()
        assertThat(environment.getGlobal("noSerialize").isnil()).isTrue()
    }

    @Test
    fun `Item owner and movement arguments are validated like Canary`() {
        val environment = env()
        ItemFunctions.install(environment)
        environment.globals.set("item", environment.userdata.wrap("Item", Any()))
        environment.globals.set("notACreature", environment.userdata.wrap("Item", Any()))

        environment.loadChunk(
            "zeroId = item:setOwner(0) " +
                "ownerIsZero = item:isOwner(0) " +
                "actorGet = item:actor() " +
                "actorSet = item:actor(true) " +
                "badCreature = select('#', item:setOwner(notACreature)) " +
                "badOwnerCheck = select('#', item:isOwner(notACreature))",
        )

        assertThat(environment.getGlobal("zeroId").toboolean()).isFalse()
        assertThat(environment.getGlobal("ownerIsZero").toboolean()).isFalse()
        assertThat(environment.getGlobal("actorGet").toboolean()).isFalse()
        assertThat(environment.getGlobal("actorSet").toboolean()).isFalse()
        assertThat(environment.getGlobal("badCreature").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("badOwnerCheck").toint()).isEqualTo(0)
    }

    @Test
    fun `Item ownership actor and shader methods forward to the seam`() {
        val environment = env()
        val operations = FakeParityItemOps()
        operations.ownerId = 42L
        operations.isOwnerResult = true
        operations.ownerName = "Hero"
        operations.hasOwnerResult = true
        operations.hasActorResult = true
        operations.shaderName = "outline"
        operations.hasShaderResult = true
        ItemFunctions.install(environment, operations)
        val creature = Player(id = 7, name = "Hero", maxHealth = 100, health = 100, position = Position(0, 0, 7))
        environment.globals.set("item", environment.userdata.wrap("Item", operations.item))
        environment.globals.set("creature", environment.userdata.wrap("Player", creature))

        environment.loadChunk(
            "ownerById = item:setOwner(10) " +
                "ownerByCreature = item:setOwner(creature) " +
                "ownerId = item:getOwnerId() " +
                "isById = item:isOwner(10) " +
                "isByCreature = item:isOwner(creature) " +
                "ownerName = item:getOwnerName() " +
                "hasOwner = item:hasOwner() " +
                "actor = item:actor() " +
                "actorSet = item:actor(false) " +
                "shaderSet = item:setShader('outline') " +
                "shader = item:getShader() " +
                "hasShader = item:hasShader()",
        )

        assertThat(operations.ownerCalls).containsExactly(10L, creature)
        assertThat(operations.ownerCheckCalls).containsExactly(10L, creature)
        assertThat(operations.actorSet).isFalse()
        assertThat(environment.getGlobal("ownerById").toboolean()).isTrue()
        assertThat(environment.getGlobal("ownerByCreature").toboolean()).isTrue()
        assertThat(environment.getGlobal("ownerId").toint()).isEqualTo(42)
        assertThat(environment.getGlobal("isById").toboolean()).isTrue()
        assertThat(environment.getGlobal("isByCreature").toboolean()).isTrue()
        assertThat(environment.getGlobal("ownerName").tojstring()).isEqualTo("Hero")
        assertThat(environment.getGlobal("hasOwner").toboolean()).isTrue()
        assertThat(environment.getGlobal("actor").toboolean()).isTrue()
        assertThat(environment.getGlobal("actorSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("shaderSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("shader").tojstring()).isEqualTo("outline")
        assertThat(environment.getGlobal("hasShader").toboolean()).isTrue()
    }

    @Test
    fun `Item tier property imbuement duration and serialization forward to the seam`() {
        val environment = env()
        val operations = FakeParityItemOps()
        operations.tierValue = 3
        operations.classification = 5
        operations.carpet = true
        operations.propertyResult = true
        operations.imbuementSlot = 2
        operations.imbuements += mapOf("id" to 1, "name" to "Strike", "duration" to 1000)
        operations.imbuements += mapOf("id" to 2, "name" to "Vampirism", "duration" to 2000)
        operations.serialized = byteArrayOf(1, 2, 3)
        operations.setDurationResult = true
        operations.insideDepotResult = true
        operations.moveToSlotResult = true
        operations.expertPvpResult = true
        ItemFunctions.install(environment, operations)
        val player = Player(id = 3, name = "Hero", maxHealth = 100, health = 100, position = Position(0, 0, 7))
        environment.globals.set("item", environment.userdata.wrap("Item", operations.item))
        environment.globals.set("player", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            "tier = item:getTier() " +
                "tierSet = item:setTier(4) " +
                "classification = item:getClassification() " +
                "carpet = item:canReceiveAutoCarpet() " +
                "property = item:hasProperty(2) " +
                "slots = item:getImbuementSlot() " +
                "imbuement = item:getImbuement() " +
                "duration = item:setDuration(10000, 20000, 2129, false) " +
                "durationOnly = item:setDuration(5000) " +
                "insideDepot = item:isInsideDepot(true) " +
                "moved = item:moveToSlot(player, 3) " +
                "movedInvalid = item:moveToSlot(5, 3) " +
                "expertPvp = item:setExpertPvpFieldContext(player) " +
                "serialized = item:serializeAttributes() " +
                "serializedLen = #serialized",
        )

        assertThat(operations.tierSet).isEqualTo(4)
        assertThat(operations.propertyArg).isEqualTo(2)
        assertThat(operations.durationCalls).containsExactly(
            DurationCall(10000L, 20000L, 2129, false),
            DurationCall(5000L, null, 0, true),
        )
        assertThat(operations.insideDepot).isTrue()
        assertThat(operations.moveToSlotCalls).containsExactly(3)
        assertThat(operations.expertPvpOwner).isSameAs(player)
        assertThat(environment.getGlobal("tier").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("tierSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("classification").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("carpet").toboolean()).isTrue()
        assertThat(environment.getGlobal("property").toboolean()).isTrue()
        assertThat(environment.getGlobal("slots").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("imbuement").istable()).isTrue()
        assertThat(environment.getGlobal("imbuement").get("id").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("imbuement").get("name").tojstring()).isEqualTo("Vampirism")
        assertThat(environment.getGlobal("imbuement").get("duration").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("duration").toboolean()).isTrue()
        assertThat(environment.getGlobal("durationOnly").toboolean()).isTrue()
        assertThat(environment.getGlobal("insideDepot").toboolean()).isTrue()
        assertThat(environment.getGlobal("moved").toboolean()).isTrue()
        assertThat(environment.getGlobal("movedInvalid").isnil()).isTrue()
        assertThat(environment.getGlobal("expertPvp").toboolean()).isTrue()
        assertThat(environment.getGlobal("serializedLen").toint()).isEqualTo(3)
    }

    @Test
    fun `Item real domain wiring reflects the Kotlin item state`() {
        val type = ItemType(
            id = 2400,
            name = "magic sword",
            upgradeClassification = 9,
            blockSolid = true,
            alwaysOnTopOrder = 1,
            isMovable = true,
            imbuementSlot = 2,
            decayTo = 2129,
        )
        val item = Item(id = 2400, type = type)
        val environment = LuaEnvironment().apply { initState() }.also {
            WorldLuaOperations(itemTypes = org.opentibia.canary.items.ItemTypeRegistry()).install(it)
        }
        val owner = Player(id = 77, name = "Hero", maxHealth = 100, health = 100, position = Position(0, 0, 7))
        environment.globals.set("item", environment.userdata.wrap("Item", item))
        environment.globals.set("owner", environment.userdata.wrap("Player", owner))

        environment.loadChunk(
            "tierSet = item:setTier(3) " +
                "tier = item:getTier() " +
                "classification = item:getClassification() " +
                "carpet = item:canReceiveAutoCarpet() " +
                "blockSolid = item:hasProperty(0) " +
                "movable = item:hasProperty(6) " +
                "slots = item:getImbuementSlot() " +
                "ownerSet = item:setOwner(owner) " +
                "ownerId = item:getOwnerId() " +
                "isOwner = item:isOwner(owner) " +
                "isOtherOwner = item:isOwner(78) " +
                "hasOwner = item:hasOwner() " +
                "actorBefore = item:actor() " +
                "actorSet = item:actor(true) " +
                "actorAfter = item:actor() " +
                "shaderSet = item:setShader('outline') " +
                "shader = item:getShader() " +
                "hasShader = item:hasShader() " +
                "durationSet = item:setDuration(10000, 10000, 2129, false) " +
                "serialized = item:serializeAttributes() " +
                "serializedLen = #serialized",
        )

        assertThat(environment.getGlobal("tierSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("tier").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("classification").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("carpet").toboolean()).isTrue()
        assertThat(environment.getGlobal("blockSolid").toboolean()).isTrue()
        assertThat(environment.getGlobal("movable").toboolean()).isTrue()
        assertThat(environment.getGlobal("slots").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("ownerSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("ownerId").toint()).isEqualTo(77)
        assertThat(environment.getGlobal("isOwner").toboolean()).isTrue()
        assertThat(environment.getGlobal("isOtherOwner").toboolean()).isFalse()
        assertThat(environment.getGlobal("hasOwner").toboolean()).isTrue()
        assertThat(environment.getGlobal("actorBefore").toboolean()).isFalse()
        assertThat(environment.getGlobal("actorSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("actorAfter").toboolean()).isTrue()
        assertThat(environment.getGlobal("shaderSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("shader").tojstring()).isEqualTo("outline")
        assertThat(environment.getGlobal("hasShader").toboolean()).isTrue()
        assertThat(environment.getGlobal("durationSet").toboolean()).isTrue()
        assertThat(environment.getGlobal("serializedLen").toint()).isGreaterThan(0)
        assertThat(type.decayTime).isEqualTo(10000L)
        assertThat(type.showDuration).isFalse()
        assertThat(type.decayTo).isEqualTo(2129)
        assertThat(item.getOwnerId()).isEqualTo(77L)
    }

    private data class DurationCall(
        val minDuration: Long,
        val maxDuration: Long?,
        val decayTo: Int,
        val showDuration: Boolean,
    )

    private class FakeParityItemOps : ItemOperations {
        val item = Item(id = 2160, type = ItemType(2160, "crystal coin", isStackable = true))
        var ownerCalls = mutableListOf<Any>()
        var ownerCheckCalls = mutableListOf<Any>()
        var ownerId = 0L
        var isOwnerResult = false
        var ownerName: String? = null
        var hasOwnerResult = false
        var hasActorResult = false
        var actorSet: Boolean? = null
        var shaderName: String? = null
        var hasShaderResult = false
        var tierValue = 0
        var tierSet: Int? = null
        var classification = 0
        var carpet = false
        var propertyArg: Int? = null
        var propertyResult = false
        var imbuementSlot = 0
        val imbuements = mutableListOf<Map<String, Any>>()
        var serialized: ByteArray? = null
        var setDurationResult = false
        val durationCalls = mutableListOf<DurationCall>()
        var insideDepot: Boolean? = null
        var insideDepotResult = false
        val moveToSlotCalls = mutableListOf<Int>()
        var moveToSlotResult = false
        var expertPvpOwner: Any? = null
        var expertPvpResult = false

        override fun itemIdByName(name: String): Int = 0
        override fun itemAttributeByName(name: String): Int = 0
        override fun getId(item: Any): Int = 0
        override fun getCount(item: Any): Int = 0
        override fun getName(item: Any): String = ""
        override fun getSubType(item: Any): Int = 0
        override fun transform(item: Any, itemId: Int, subType: Int): Boolean = false
        override fun decay(item: Any, decayId: Int): Boolean = false
        override fun getActionId(item: Any): Int = 0
        override fun setActionId(item: Any, actionId: Int): Boolean = false
        override fun getUniqueId(item: Any): Long = 0L
        override fun getAttribute(item: Any, attribute: Int): Any? = null
        override fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean = false

        override fun setOwner(item: Any, owner: Any): Boolean {
            ownerCalls += owner
            return true
        }

        override fun getOwnerId(item: Any): Long = ownerId

        override fun isOwner(item: Any, owner: Any): Boolean {
            ownerCheckCalls += owner
            return isOwnerResult
        }

        override fun getOwnerName(item: Any): String? = ownerName

        override fun hasOwner(item: Any): Boolean = hasOwnerResult

        override fun hasActor(item: Any): Boolean = hasActorResult

        override fun setActor(item: Any, actor: Boolean): Boolean {
            actorSet = actor
            return true
        }

        override fun setShader(item: Any, shader: String): Boolean {
            shaderName = shader
            return true
        }

        override fun getShader(item: Any): String = shaderName ?: ""

        override fun hasShader(item: Any): Boolean = hasShaderResult

        override fun getTier(item: Any): Int = tierValue

        override fun setTier(item: Any, tier: Int): Boolean {
            tierSet = tier
            return true
        }

        override fun getClassification(item: Any): Int = classification

        override fun canReceiveAutoCarpet(item: Any): Boolean = carpet

        override fun hasProperty(item: Any, property: Int): Boolean {
            propertyArg = property
            return propertyResult
        }

        override fun getImbuementSlot(item: Any): Int = imbuementSlot

        override fun getImbuements(item: Any): List<Map<String, Any>> = imbuements

        override fun serializeAttributes(item: Any): ByteArray? = serialized

        override fun setDuration(
            item: Any,
            minDuration: Long,
            maxDuration: Long?,
            decayTo: Int,
            showDuration: Boolean,
        ): Boolean {
            durationCalls += DurationCall(minDuration, maxDuration, decayTo, showDuration)
            return setDurationResult
        }

        override fun isInsideDepot(item: Any, includeInbox: Boolean): Boolean {
            insideDepot = includeInbox
            return insideDepotResult
        }

        override fun moveToSlot(item: Any, player: Any, slot: Int): Boolean {
            moveToSlotCalls += slot
            return moveToSlotResult
        }

        override fun setExpertPvpFieldContext(item: Any, owner: Any): Boolean {
            expertPvpOwner = owner
            return expertPvpResult
        }
    }

    private companion object {
        val CANARY_ITEM_METHODS = listOf(
            "isItem",
            "getContainer",
            "getParent",
            "getTopParent",
            "getId",
            "clone",
            "split",
            "remove",
            "getUniqueId",
            "getActionId",
            "setActionId",
            "getCount",
            "getCharges",
            "getFluidType",
            "getWeight",
            "getSubType",
            "getName",
            "getPluralName",
            "getArticle",
            "getPosition",
            "getTile",
            "hasAttribute",
            "getAttribute",
            "setAttribute",
            "removeAttribute",
            "getCustomAttribute",
            "setCustomAttribute",
            "removeCustomAttribute",
            "setExpertPvpFieldContext",
            "canBeMoved",
            "setOwner",
            "getOwnerId",
            "isOwner",
            "getOwnerName",
            "hasOwner",
            "actor",
            "moveTo",
            "transform",
            "decay",
            "serializeAttributes",
            "moveToSlot",
            "getDescription",
            "hasProperty",
            "getImbuementSlot",
            "getImbuement",
            "setDuration",
            "isInsideDepot",
            "isContainer",
            "getTier",
            "setTier",
            "getClassification",
            "canReceiveAutoCarpet",
            "setShader",
            "getShader",
            "hasShader",
        )
    }
}
