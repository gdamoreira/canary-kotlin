package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaString
import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Engine seam for the `Item` metatable family. Mirrors the primitives in
 * src/lua/functions/items/item_functions.cpp.
 *
 * Members whose state the Kotlin domain does not model yet (cloning, owner names,
 * imbuement payloads, tile residence) carry a documented default so scripts keep
 * running instead of failing; the custom-attribute trio is backed by the real
 * [Item] custom-attribute store.
 */
interface ItemOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    /** Resolves an item attribute name to its id, returning 0 when unknown. */
    fun itemAttributeByName(name: String): Int

    fun getId(item: Any): Int
    fun getCount(item: Any): Int
    fun getName(item: Any): String
    fun getSubType(item: Any): Int
    fun transform(item: Any, itemId: Int, subType: Int): Boolean
    fun decay(item: Any, decayId: Int): Boolean
    fun getActionId(item: Any): Int
    fun setActionId(item: Any, actionId: Int): Boolean
    fun getUniqueId(item: Any): Long
    fun getAttribute(item: Any, attribute: Int): Any?
    fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean

    /** Mirrors `item:isItem()`; false when the value is not a modeled item. */
    fun isItem(item: Any): Boolean = false

    /** Mirrors `item:getContainer()`; null means Canary's "not a container" (`false`). */
    fun getContainer(item: Any): Any? = null

    /** Mirrors `item:getParent()`; null when no parent linkage is modeled. */
    fun getParent(item: Any): Any? = null

    /** Mirrors `item:getTopParent()`; null when no parent linkage is modeled. */
    fun getTopParent(item: Any): Any? = null

    /** Mirrors `item:clone()`; null when cloning is not modeled. */
    fun clone(item: Any): Any? = null

    /** Mirrors `item:split([count])`; null when splitting is not modeled. */
    fun split(item: Any, count: Int): Any? = null

    /** Mirrors `item:remove([count = -1])`; false when removal is not modeled. */
    fun remove(item: Any, count: Int): Boolean = false

    /** Mirrors `item:getCharges()`; 0 when the attribute is absent. */
    fun getCharges(item: Any): Int = 0

    /** Mirrors `item:getFluidType()`; 0 when the attribute is absent. */
    fun getFluidType(item: Any): Int = 0

    /** Mirrors `item:getWeight()` in Canary's hundredths of an ounce; 0 when unknown. */
    fun getWeight(item: Any): Int = 0

    /** Mirrors `item:getPluralName()`; empty when the attribute/type carries none. */
    fun getPluralName(item: Any): String = ""

    /** Mirrors `item:getArticle()`; empty when the attribute/type carries none. */
    fun getArticle(item: Any): String = ""

    /** Mirrors `item:getPosition()`; the origin when the item has no modeled position. */
    fun getPosition(item: Any): Position = Position(0, 0, 0)

    /** Mirrors `item:getTile()`; null when the item has no modeled tile. */
    fun getTile(item: Any): Any? = null

    /** Mirrors `item:hasAttribute(key)`; false when the attribute is absent. */
    fun hasAttribute(item: Any, attribute: Int): Boolean = false

    /** Mirrors `item:removeAttribute(key)`; false for protected or absent attributes. */
    fun removeAttribute(item: Any, attribute: Int): Boolean = false

    /**
     * Mirrors `item:getCustomAttribute(key)` over the real [Item] store: the variant
     * is pushed like `CustomAttribute::pushToLua` (string, then integer/double as a
     * Lua number, then boolean); null (nil) when the key is absent or the receiver
     * is not a modeled item. The lookup is case-insensitive.
     */
    fun getCustomAttribute(item: Any, key: String): Any? {
        val customAttribute = (item as? Item)?.getCustomAttribute(key) ?: return null
        return when {
            customAttribute.hasValue<String>() -> customAttribute.getString()
            customAttribute.hasValue<Long>() -> customAttribute.getInteger()
            customAttribute.hasValue<Double>() -> customAttribute.getDouble()
            customAttribute.hasValue<Boolean>() -> customAttribute.getBool()
            else -> null
        }
    }

    /**
     * Mirrors `item:setCustomAttribute(key, value)` over the real [Item] store,
     * keeping the Lua variant (`int64_t` for integral numbers, `double`, string or
     * boolean). False only for a non-item receiver or an unsupported variant.
     */
    fun setCustomAttribute(item: Any, key: String, value: Any?): Boolean {
        val target = item as? Item ?: return false
        when (value) {
            is Boolean -> target.setCustomAttribute(key, value)
            is Int -> target.setCustomAttribute(key, value.toLong())
            is Long -> target.setCustomAttribute(key, value)
            is Float -> target.setCustomAttribute(key, value.toDouble())
            is Double -> target.setCustomAttribute(key, value)
            is String -> target.setCustomAttribute(key, value)
            else -> return false
        }
        return true
    }

    /** Mirrors `item:removeCustomAttribute(key)` over the real [Item] store. */
    fun removeCustomAttribute(item: Any, key: String): Boolean =
        (item as? Item)?.removeCustomAttribute(key) ?: false

    /** Mirrors `item:canBeMoved()`; false when movability is not modeled. */
    fun canBeMoved(item: Any): Boolean = false

    /**
     * Mirrors `item:moveTo(position or cylinder[, flags])`: [destination] is the resolved
     * cylinder userdata (Container/Player/Tile) and [position] the resolved map position.
     */
    fun moveTo(item: Any, destination: Any?, position: Position?, flags: Long): Boolean = false

    /** Mirrors `item:getDescription(distance)`; empty when no description is modeled. */
    fun getDescription(item: Any, distance: Int): String = ""

    /** Mirrors `item:isContainer()`; false when container status is not modeled. */
    fun isContainer(item: Any): Boolean = false

    /** Mirrors `item:setExpertPvpFieldContext(owner)`; false when no expert-PvP engine is wired. */
    fun setExpertPvpFieldContext(item: Any, owner: Any): Boolean = false

    /**
     * Mirrors `item:setOwner(creature or creatureId)`; [owner] is the resolved creature
     * userdata or the numeric creature id. False when no ownership store is modeled.
     */
    fun setOwner(item: Any, owner: Any): Boolean = false

    /** Mirrors `item:getOwnerId()`; 0 means Canary's `nil`. */
    fun getOwnerId(item: Any): Long = 0L

    /** Mirrors `item:isOwner(creature or creatureId)`; [owner] is a creature or numeric id. */
    fun isOwner(item: Any, owner: Any): Boolean = false

    /** Mirrors `item:getOwnerName()`; null means Canary's `nil`. */
    fun getOwnerName(item: Any): String? = null

    /** Mirrors `item:hasOwner()`; false when the owner attribute is absent. */
    fun hasOwner(item: Any): Boolean = false

    /** Mirrors `item:actor()`; false when no actor state is modeled. */
    fun hasActor(item: Any): Boolean = false

    /** Mirrors `item:actor(value)`'s setter branch; false when no actor state is modeled. */
    fun setActor(item: Any, actor: Boolean): Boolean = false

    /** Mirrors `item:serializeAttributes()`; null when serialization is not modeled. */
    fun serializeAttributes(item: Any): ByteArray? = null

    /** Mirrors `item:moveToSlot(player, slot = 0)`; false when slot movement is not modeled. */
    fun moveToSlot(item: Any, player: Any, slot: Int): Boolean = false

    /** Mirrors `item:hasProperty(property)`; false when the property is absent. */
    fun hasProperty(item: Any, property: Int): Boolean = false

    /** Mirrors `item:getImbuementSlot()`; 0 when no imbuement slots are modeled. */
    fun getImbuementSlot(item: Any): Int = 0

    /**
     * Mirrors the imbuement payloads `item:getImbuement()` can push: one map with the
     * `id`, `name` and `duration` fields per imbuement slot, in slot order.
     */
    fun getImbuements(item: Any): List<Map<String, Any>> = emptyList()

    /**
     * Mirrors `item:setDuration(minDuration[, maxDuration[, decayTo[, showDuration]]])`;
     * [maxDuration] is null when the call omits it. False when no type/decay store is wired.
     */
    fun setDuration(
        item: Any,
        minDuration: Long,
        maxDuration: Long?,
        decayTo: Int,
        showDuration: Boolean,
    ): Boolean = false

    /** Mirrors `item:isInsideDepot([includeInbox = false])`; false when depot containment is not modeled. */
    fun isInsideDepot(item: Any, includeInbox: Boolean): Boolean = false

    /** Mirrors `item:getTier()`; 0 when the tier attribute is absent. */
    fun getTier(item: Any): Int = 0

    /** Mirrors `item:setTier(tier)`; false when no tier store is wired. */
    fun setTier(item: Any, tier: Int): Boolean = false

    /** Mirrors `item:getClassification()`; 0 when the item has no upgrade classification. */
    fun getClassification(item: Any): Int = 0

    /** Mirrors `item:canReceiveAutoCarpet()`; false when carpet rules are not modeled. */
    fun canReceiveAutoCarpet(item: Any): Boolean = false

    /** Mirrors `item:setShader(shaderName)`; false when no shader store is wired. */
    fun setShader(item: Any, shader: String): Boolean = false

    /** Mirrors `item:getShader()`; empty when no shader is modeled. */
    fun getShader(item: Any): String = ""

    /** Mirrors `item:hasShader()`; false when no shader is modeled. */
    fun hasShader(item: Any): Boolean = false
}

/**
 * Registers the Canary `Item` userdata methods (src/lua/functions/items/item_functions.cpp:22):
 *
 *   item:isItem()
 *   item:getContainer()
 *   item:getParent()
 *   item:getTopParent()
 *   item:getId()
 *   item:clone()
 *   item:split([count = 1])
 *   item:remove([count = -1])
 *   item:getUniqueId()
 *   item:getActionId()
 *   item:setActionId(actionId)
 *   item:getCount()
 *   item:getCharges()
 *   item:getFluidType()
 *   item:getWeight()
 *   item:getSubType()
 *   item:getName()
 *   item:getPluralName()
 *   item:getArticle()
 *   item:getPosition()
 *   item:getTile()
 *   item:hasAttribute(key)
 *   item:getAttribute(key)
 *   item:setAttribute(key, value)
 *   item:removeAttribute(key)
 *   item:getCustomAttribute(key)
 *   item:setCustomAttribute(key, value)
 *   item:removeCustomAttribute(key)
 *   item:setExpertPvpFieldContext(owner)
 *   item:canBeMoved()
 *   item:setOwner(creature or creatureId)
 *   item:getOwnerId()
 *   item:isOwner(creature or creatureId)
 *   item:getOwnerName()
 *   item:hasOwner()
 *   item:actor([value])
 *   item:moveTo(position or cylinder[, flags])
 *   item:transform(itemId or name[, count/subType = -1])
 *   item:decay(decayId)
 *   item:serializeAttributes()
 *   item:moveToSlot(player[, slot = 0])
 *   item:getDescription(distance)
 *   item:hasProperty(property)
 *   item:getImbuementSlot()
 *   item:getImbuement()
 *   item:setDuration(minDuration[, maxDuration[, decayTo[, showDuration]]])
 *   item:isInsideDepot([includeInbox = false])
 *   item:isContainer()
 *   item:getTier()
 *   item:setTier(tier)
 *   item:getClassification()
 *   item:canReceiveAutoCarpet()
 *   item:setShader(shaderName)
 *   item:getShader()
 *   item:hasShader()
 *
 * `Container` and `Teleport` inherit these methods through the class-table `__index`
 * chain, mirroring Canary's shared `Item` hierarchy. Canary's `Item(uid)` constructor
 * stays Lua-side script environment bookkeeping, which the Kotlin domain does not model.
 *
 * Canary's missing-item branches mix `return 0` (no values), `return 1` without a push
 * (an uninitialized Lua stack slot) and an explicit `false`/`nil`; this binding
 * normalizes the uninitialized-stack branches to `nil` and preserves the explicit
 * `false`/`nil`/zero-result conventions.
 */
object ItemFunctions {
    const val ITEM_TYPE = "Item"

    /** Mirrors Canary `CylinderFlags_t` defaults used by `ItemFunctions::luaItemMoveTo`. */
    private const val DEFAULT_MOVE_TO_FLAGS = (1L shl 0) or (1L shl 1) or (1L shl 2) or (1L shl 6)

    /** `Slots_t::CONST_SLOT_WHEREEVER` (src/creatures/creatures_definitions.hpp:374). */
    private const val SLOT_WHEREEVER = 0

    fun install(env: LuaEnvironment, operations: ItemOperations = UnwiredItemOperations) {
        val registry = env.userdata
        registry.registerClass(ITEM_TYPE)
        val type = ITEM_TYPE
        registry.registerMethod(type, "isItem", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.isItem(item))
        })

        registry.registerMethod(type, "getContainer", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val container = operations.getContainer(item) ?: return@luaFunction LuaValue.FALSE
            env.userdata.wrap("Container", container)
        })

        registry.registerMethod(type, "getParent", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val parent = operations.getParent(item) ?: return@luaFunction LuaValue.NIL
            wrapCylinderUserdata(env, parent)
        })

        registry.registerMethod(type, "getTopParent", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val topParent = operations.getTopParent(item) ?: return@luaFunction LuaValue.NIL
            wrapCylinderUserdata(env, topParent)
        })

        registry.registerMethod(type, "getId", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getId(item))
        })

        registry.registerMethod(type, "clone", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val clone = operations.clone(item) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, clone)
        })

        registry.registerMethod(type, "split", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(2).optint(1)
            val split = operations.split(item, count) ?: return@luaFunction LuaValue.NIL
            wrapItemUserdata(env, split)
        })

        registry.registerMethod(type, "remove", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(2).optint(-1)
            LuaValue.valueOf(operations.remove(item, count))
        })

        registry.registerMethod(type, "getCount", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getCount(item))
        })

        registry.registerMethod(type, "getCharges", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getCharges(item))
        })

        registry.registerMethod(type, "getFluidType", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getFluidType(item))
        })

        registry.registerMethod(type, "getWeight", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getWeight(item))
        })

        registry.registerMethod(type, "getName", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getName(item))
        })

        registry.registerMethod(type, "getPluralName", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getPluralName(item))
        })

        registry.registerMethod(type, "getArticle", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getArticle(item))
        })

        registry.registerMethod(type, "getSubType", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getSubType(item))
        })

        registry.registerMethod(type, "getPosition", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap(PositionFunctions.POSITION_TYPE, operations.getPosition(item))
        })

        registry.registerMethod(type, "getTile", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val tile = operations.getTile(item) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Tile", tile)
        })

        registry.registerMethod(type, "hasAttribute", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val attribute = resolveItemAttribute(args.arg(2), operations::itemAttributeByName)
            LuaValue.valueOf(operations.hasAttribute(item, attribute))
        })

        registry.registerMethod(type, "getAttribute", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val attribute = resolveItemAttribute(args.arg(2), operations::itemAttributeByName)
            attributeValueToLua(operations.getAttribute(item, attribute))
        })

        registry.registerMethod(type, "setAttribute", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val attribute = resolveItemAttribute(args.arg(2), operations::itemAttributeByName)
            val value = luaToAttributeValue(args.arg(3))
            LuaValue.valueOf(operations.setAttribute(item, attribute, value))
        })

        registry.registerMethod(type, "removeAttribute", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val attribute = resolveItemAttribute(args.arg(2), operations::itemAttributeByName)
            LuaValue.valueOf(operations.removeAttribute(item, attribute))
        })

        registry.registerMethod(type, "getCustomAttribute", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val key = customAttributeKey(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            attributeValueToLua(operations.getCustomAttribute(item, key))
        })

        registry.registerMethod(type, "setCustomAttribute", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val key = customAttributeKey(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            val value = luaToAttributeValue(args.arg(3)) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.setCustomAttribute(item, key, value))
        })

        registry.registerMethod(type, "removeCustomAttribute", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val key = customAttributeKey(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.removeCustomAttribute(item, key))
        })

        registry.registerMethod(type, "setExpertPvpFieldContext", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            val owner = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.setExpertPvpFieldContext(item, owner))
        })

        registry.registerMethod(type, "canBeMoved", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.canBeMoved(item))
        })

        registry.registerMethod(type, "setOwner", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NONE
            if (args.arg(2).isuserdata()) {
                val creature = retrieveUserdata(args, 2)
                if (creature !is Creature) {
                    return@luaFunction LuaValue.NONE
                }
                LuaValue.valueOf(operations.setOwner(item, creature))
            } else {
                val creatureId = args.arg(2).optlong(0)
                if (creatureId == 0L) {
                    LuaValue.FALSE
                } else {
                    LuaValue.valueOf(operations.setOwner(item, creatureId))
                }
            }
        })

        registry.registerMethod(type, "getOwnerId", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NONE
            val ownerId = operations.getOwnerId(item)
            if (ownerId == 0L) LuaValue.NIL else LuaValue.valueOf(ownerId.toDouble())
        })

        registry.registerMethod(type, "isOwner", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NONE
            if (args.arg(2).isuserdata()) {
                val creature = retrieveUserdata(args, 2)
                if (creature !is Creature) {
                    return@luaFunction LuaValue.NONE
                }
                LuaValue.valueOf(operations.isOwner(item, creature))
            } else {
                val creatureId = args.arg(2).optlong(0)
                if (creatureId == 0L) {
                    LuaValue.FALSE
                } else {
                    LuaValue.valueOf(operations.isOwner(item, creatureId))
                }
            }
        })

        registry.registerMethod(type, "getOwnerName", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NONE
            val ownerName = operations.getOwnerName(item)
            if (ownerName.isNullOrEmpty()) LuaValue.NIL else LuaValue.valueOf(ownerName)
        })

        registry.registerMethod(type, "hasOwner", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.hasOwner(item))
        })

        registry.registerMethod(type, "actor", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(operations.hasActor(item))
            } else {
                LuaValue.valueOf(operations.setActor(item, luaBoolean(args.arg(2), false)))
            }
        })

        registry.registerMethod(type, "moveTo", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val rawTarget = args.arg(2)
            val target = retrieveUserdata(args, 2)
            val destination = target?.takeIf { it !is Position }
            val position = when {
                target is Position -> target
                destination == null -> luaToPosition(rawTarget)
                else -> null
            }
            val flags = args.arg(3).optlong(DEFAULT_MOVE_TO_FLAGS)
            LuaValue.valueOf(operations.moveTo(item, destination, position, flags))
        })

        registry.registerMethod(type, "transform", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val itemId = resolveItemId(args.arg(2), operations::itemIdByName) ?: return@luaFunction LuaValue.NIL
            val subType = args.arg(3).optint(-1)
            LuaValue.valueOf(operations.transform(item, itemId, subType))
        })

        registry.registerMethod(type, "decay", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val decayId = args.arg(2).toint()
            LuaValue.valueOf(operations.decay(item, decayId))
        })

        registry.registerMethod(type, "serializeAttributes", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val attributes = operations.serializeAttributes(item) ?: return@luaFunction LuaValue.NIL
            LuaString.valueOf(attributes)
        })

        registry.registerMethod(type, "moveToSlot", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val player = retrieveUserdata(args, 2)
            if (player !is Player) {
                return@luaFunction LuaValue.NIL
            }
            val slot = args.arg(3).optint(SLOT_WHEREEVER)
            LuaValue.valueOf(operations.moveToSlot(item, player, slot))
        })

        registry.registerMethod(type, "getDescription", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val distance = args.arg(2).toint()
            LuaValue.valueOf(operations.getDescription(item, distance))
        })

        registry.registerMethod(type, "hasProperty", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.hasProperty(item, args.arg(2).toint()))
        })

        registry.registerMethod(type, "getImbuementSlot", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.getImbuementSlot(item))
        })

        registry.registerMethod(type, "getImbuement", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            val imbuement = operations.getImbuements(item).lastOrNull() ?: return@luaFunction LuaValue.NIL
            imbuementTable(imbuement)
        })

        registry.registerMethod(type, "setDuration", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(
                operations.setDuration(
                    item = item,
                    minDuration = args.arg(2).optlong(0),
                    maxDuration = if (args.narg() > 2) args.arg(3).optlong(0) else null,
                    decayTo = if (args.narg() > 3) args.arg(4).optint(0) else 0,
                    showDuration = if (args.narg() > 4) luaBoolean(args.arg(5), true) else true,
                ),
            )
        })

        registry.registerMethod(type, "isInsideDepot", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.isInsideDepot(item, args.arg(2).optboolean(false)))
        })

        registry.registerMethod(type, "isContainer", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.isContainer(item))
        })

        registry.registerMethod(type, "getTier", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.getTier(item))
        })

        registry.registerMethod(type, "setTier", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.setTier(item, args.arg(2).toint()))
        })

        registry.registerMethod(type, "getClassification", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.getClassification(item))
        })

        registry.registerMethod(type, "canReceiveAutoCarpet", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.canReceiveAutoCarpet(item))
        })

        registry.registerMethod(type, "setShader", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.setShader(item, args.arg(2).optjstring("")))
        })

        registry.registerMethod(type, "getShader", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(operations.getShader(item))
        })

        registry.registerMethod(type, "hasShader", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.hasShader(item))
        })

        registry.registerMethod(type, "getActionId", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getActionId(item))
        })

        registry.registerMethod(type, "setActionId", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val actionId = args.arg(2).toint()
            LuaValue.valueOf(operations.setActionId(item, actionId))
        })

        registry.registerMethod(type, "getUniqueId", luaFunction { args ->
            val item = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(operations.getUniqueId(item).toDouble())
        })
    }

    /**
     * Parses Canary's `item:getAttribute/setAttribute/hasAttribute(key)` argument: a numeric
     * attribute id or a string attribute name resolved through [ItemOperations.itemAttributeByName].
     * Anything else mirrors Canary's `ItemAttribute_t::NONE` (0).
     */
    private fun resolveItemAttribute(value: LuaValue, byName: (String) -> Int): Int = when {
        value.isnumber() -> value.toint()
        value.isstring() -> byName(value.tojstring())
        else -> 0
    }

    /**
     * Mirrors `item:get/setCustomAttribute(key)`: numbers become their int64 decimal string,
     * strings are used verbatim, anything else is rejected.
     */
    private fun customAttributeKey(value: LuaValue): String? = when {
        value.isnumber() -> value.tolong().toString()
        value.isstring() -> value.tojstring()
        else -> null
    }

    /** Mirrors `Lua::getBoolean` (lua_toboolean: only nil and false are false). */
    private fun luaBoolean(value: LuaValue, defaultValue: Boolean): Boolean =
        if (value.isnil()) defaultValue else value.toboolean()

    /** Mirrors the `Lua::setField` payload of `ItemFunctions::luaItemGetImbuement`. */
    private fun imbuementTable(imbuement: Map<String, Any>): LuaTable =
        LuaTable().also { table ->
            table.set("id", LuaValue.valueOf((imbuement["id"] as? Number)?.toInt() ?: 0))
            table.set("name", LuaValue.valueOf(imbuement["name"] as? String ?: ""))
            table.set("duration", LuaValue.valueOf((imbuement["duration"] as? Number)?.toDouble() ?: 0.0))
        }
}

internal object UnwiredItemOperations : ItemOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("ItemFunctions.itemIdByName seam is not wired")

    override fun itemAttributeByName(name: String): Int =
        throw NotImplementedError("ItemFunctions.itemAttributeByName seam is not wired")

    override fun getId(item: Any): Int =
        throw NotImplementedError("ItemFunctions.getId seam is not wired")

    override fun getCount(item: Any): Int =
        throw NotImplementedError("ItemFunctions.getCount seam is not wired")

    override fun getName(item: Any): String =
        throw NotImplementedError("ItemFunctions.getName seam is not wired")

    override fun getSubType(item: Any): Int =
        throw NotImplementedError("ItemFunctions.getSubType seam is not wired")

    override fun transform(item: Any, itemId: Int, subType: Int): Boolean =
        throw NotImplementedError("ItemFunctions.transform seam is not wired")

    override fun decay(item: Any, decayId: Int): Boolean =
        throw NotImplementedError("ItemFunctions.decay seam is not wired")

    override fun getActionId(item: Any): Int =
        throw NotImplementedError("ItemFunctions.getActionId seam is not wired")

    override fun setActionId(item: Any, actionId: Int): Boolean =
        throw NotImplementedError("ItemFunctions.setActionId seam is not wired")

    override fun getUniqueId(item: Any): Long =
        throw NotImplementedError("ItemFunctions.getUniqueId seam is not wired")

    override fun getAttribute(item: Any, attribute: Int): Any? =
        throw NotImplementedError("ItemFunctions.getAttribute seam is not wired")

    override fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean =
        throw NotImplementedError("ItemFunctions.setAttribute seam is not wired")
}
