package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the `ItemType` metatable. Mirrors the primitives in
 * src/lua/functions/items/item_type_functions.cpp.
 *
 * The Kotlin [org.opentibia.canary.items.ItemType] only models id, name, stackable,
 * container, capacity, weight and pickupable flags. Every other getter keeps Canary's
 * `ItemType` struct default (src/items/items.hpp:262) so scripts keep running instead
 * of failing; [WorldItemTypeOperations] overrides the modeled fields.
 */
interface ItemTypeOperations {
    /** Mirrors `ItemType(id or name)` resolving `Item::items[id]`. */
    fun create(idOrName: Any): Any?

    fun getId(itemType: Any): Int
    fun getName(itemType: Any): String
    fun isContainer(itemType: Any): Boolean
    fun isStackable(itemType: Any): Boolean
    fun isPickupable(itemType: Any): Boolean
    fun getCapacity(itemType: Any): Int

    /** Mirrors `itemType:getWeight([count = 1])` in Canary's hundredths of an ounce. */
    fun getWeight(itemType: Any, count: Int): Int

    /** Mirrors `ItemType::hasSubType` (`isFluidContainer || isSplash || stackable || charges != 0`). */
    fun hasSubType(itemType: Any): Boolean

    /** Mirrors `ItemType::isCorpse`; false until the corpse flag is modeled. */
    fun isCorpse(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isDoor`; false until `ItemTypes_t` is modeled. */
    fun isDoor(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isFluidContainer`; false until the item group is modeled. */
    fun isFluidContainer(itemType: Any): Boolean = false

    /** Mirrors `ItemType::movable`; Canary's struct default is false. */
    fun isMovable(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isRune`; false until `ItemTypes_t` is modeled. */
    fun isRune(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isStowable` (`stackable && wareId > 0`); false until `wareId` is modeled. */
    fun isStowable(itemType: Any): Boolean = false

    /** Mirrors `ItemType::canReadText`; Canary's struct default is false. */
    fun isReadable(itemType: Any): Boolean = false

    /** Mirrors `ItemType::canWriteText`; Canary's struct default is false. */
    fun isWritable(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isBlocking` (`blockProjectile || blockSolid`); both default false. */
    fun isBlocking(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isGroundTile`; false until the item group is modeled. */
    fun isGroundTile(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isMagicField`; false until `ItemTypes_t` is modeled. */
    fun isMagicField(itemType: Any): Boolean = false

    /** Mirrors `ItemType::multiUse`; Canary's struct default is false. */
    fun isMultiUse(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isKey`; false until `ItemTypes_t` is modeled. */
    fun isKey(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isQuiver`; false until `ItemTypes_t` is modeled. */
    fun isQuiver(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isPodium`; Canary's struct default is false. */
    fun isPodium(itemType: Any): Boolean = false

    /** Mirrors `ItemType::isWeapon`; false until `weaponType` is modeled. */
    fun isWeapon(itemType: Any): Boolean = false

    /** Mirrors `ItemType::type`; `ITEM_TYPE_NONE` (0) until `ItemTypes_t` is modeled. */
    fun getType(itemType: Any): Int = 0

    /**
     * Mirrors `ItemType::getPluralName`: the stored plural name, else `name + "s"` when
     * `showCount` is set (Canary's struct default is true). The Kotlin type stores no
     * plural name, so the fallback is used.
     */
    fun getPluralName(itemType: Any): String = getName(itemType) + "s"

    /** Mirrors `ItemType::article`; Canary's struct default is empty. */
    fun getArticle(itemType: Any): String = ""

    /**
     * Mirrors `itemType:getDescription([count = -1])`; empty because Canary's
     * `Item::getDescription` renderer is not ported for item types.
     */
    fun getDescription(itemType: Any, count: Int): String = ""

    /** Mirrors `ItemType::slotPosition`; Canary's struct default is `SLOTP_HAND` (48). */
    fun getSlotPosition(itemType: Any): Int = SLOTP_HAND

    /** Mirrors `ItemType::charges`; Canary's struct default is 0. */
    fun getCharges(itemType: Any): Int = 0

    /** Mirrors `ItemType::fluidSource`; Canary's struct default is `FLUID_NONE` (0). */
    fun getFluidSource(itemType: Any): Int = 0

    /** Mirrors `ItemType::stackSize`; Canary's struct default is 100. */
    fun getStackSize(itemType: Any): Int = 100

    /** Mirrors `ItemType::hitChance`; Canary's struct default is 0. */
    fun getHitChance(itemType: Any): Int = 0

    /** Mirrors `ItemType::shootRange`; Canary's struct default is 1. */
    fun getShootRange(itemType: Any): Int = 1

    /** Mirrors `ItemType::attack`; Canary's struct default is 0. */
    fun getAttack(itemType: Any): Int = 0

    /** Mirrors `ItemType::defense`; Canary's struct default is 0. */
    fun getDefense(itemType: Any): Int = 0

    /** Mirrors `ItemType::extraDefense`; Canary's struct default is 0. */
    fun getExtraDefense(itemType: Any): Int = 0

    /** Mirrors `ItemType::imbuementSlot`; Canary's struct default is 0. */
    fun getImbuementSlot(itemType: Any): Int = 0

    /** Mirrors `ItemType::armor`; Canary's struct default is 0. */
    fun getArmor(itemType: Any): Int = 0

    /** Mirrors `ItemType::weaponType`; Canary's struct default is `WEAPON_NONE` (0). */
    fun getWeaponType(itemType: Any): Int = 0

    /** Mirrors `getElementType`; nil while `abilities` is not modeled. */
    fun getElementType(itemType: Any): Int? = null

    /** Mirrors `getElementDamage`; nil while `abilities` is not modeled. */
    fun getElementDamage(itemType: Any): Int? = null

    /** Mirrors `ItemType::transformEquipTo`; Canary's struct default is 0. */
    fun getTransformEquipId(itemType: Any): Int = 0

    /** Mirrors `ItemType::transformDeEquipTo`; Canary's struct default is 0. */
    fun getTransformDeEquipId(itemType: Any): Int = 0

    /** Mirrors `ItemType::destroyTo`; Canary's struct default is 0. */
    fun getDestroyId(itemType: Any): Int = 0

    /** Mirrors `ItemType::decayTo`; Canary's struct default is -1. */
    fun getDecayId(itemType: Any): Int = -1

    /** Mirrors `ItemType::minReqLevel`; Canary's struct default is 0. */
    fun getRequiredLevel(itemType: Any): Int = 0

    /** Mirrors `ItemType::ammoType`; Canary's struct default is `AMMO_NONE` (0). */
    fun getAmmoType(itemType: Any): Int = 0

    /** Mirrors `ItemType::decayTime`; Canary's struct default is 0. */
    fun getDecayTime(itemType: Any): Int = 0

    /** Mirrors `ItemType::showDuration`; Canary's struct default is false. */
    fun getShowDuration(itemType: Any): Boolean = false

    /** Mirrors `ItemType::wrapableTo`; Canary's struct default is 0. */
    fun getWrapableTo(itemType: Any): Int = 0

    /** Mirrors `getSpeed`; nil while `abilities` is not modeled. */
    fun getSpeed(itemType: Any): Int? = null

    /** Mirrors `ItemType::speed`; Canary's struct default is 0. */
    fun getBaseSpeed(itemType: Any): Int = 0

    /** Mirrors `ItemType::vocationString`; Canary's struct default is empty. */
    fun getVocationString(itemType: Any): String = ""

    /** Mirrors `ItemType::elementalBond`; Canary's struct default is `COMBAT_NONE` (0). */
    fun getElementalBond(itemType: Any): Int = 0
}

/** `SLOTP_LEFT | SLOTP_RIGHT` (src/items/items_definitions.hpp:440). */
private const val SLOTP_HAND = 48

/**
 * Registers the Canary `ItemType` userdata methods plus the `ItemType(id or name)`
 * global class function and `__eq` metamethod (src/lua/functions/items/item_type_functions.cpp):
 *
 *   ItemType(id or name)
 *   itemType:isCorpse() ... itemType:isWeapon()
 *   itemType:getType() ... itemType:getSlotPosition()
 *   itemType:getCharges() ... itemType:getStackSize()
 *   itemType:getHitChance()/getShootRange()
 *   itemType:getAttack() ... itemType:getWeaponType()
 *   itemType:getElementType()/getElementDamage()
 *   itemType:getTransformEquipId() ... itemType:getAmmoType()
 *   itemType:getDecayTime() ... itemType:getElementalBond()
 *   itemType:hasSubType()
 */
object ItemTypeFunctions {
    const val ITEM_TYPE_TYPE = "ItemType"

    fun install(env: LuaEnvironment, operations: ItemTypeOperations = UnwiredItemTypeOperations) {
        val registry = env.userdata

        env.globals.set(ITEM_TYPE_TYPE, luaFunction { args ->
            val argument = args.arg(1)
            val itemType = when {
                argument.isnumber() -> operations.create(argument.toint())
                argument.isstring() -> operations.create(argument.tojstring())
                else -> null
            }
            itemType?.let { env.userdata.wrap(ITEM_TYPE_TYPE, it) } ?: LuaValue.NIL
        })

        registry.registerMethod(ITEM_TYPE_TYPE, "__eq", luaFunction { args ->
            val itemType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val other = retrieveUserdata(args, 2) ?: return@luaFunction LuaValue.FALSE
            LuaValue.valueOf(itemType == other)
        })

        registerBoolean(env, "isCorpse", operations::isCorpse)
        registerBoolean(env, "isDoor", operations::isDoor)
        registerBoolean(env, "isContainer", operations::isContainer)
        registerBoolean(env, "isFluidContainer", operations::isFluidContainer)
        registerBoolean(env, "isMovable", operations::isMovable)
        registerBoolean(env, "isRune", operations::isRune)
        registerBoolean(env, "isStackable", operations::isStackable)
        registerBoolean(env, "isStowable", operations::isStowable)
        registerBoolean(env, "isReadable", operations::isReadable)
        registerBoolean(env, "isWritable", operations::isWritable)
        registerBoolean(env, "isBlocking", operations::isBlocking)
        registerBoolean(env, "isGroundTile", operations::isGroundTile)
        registerBoolean(env, "isMagicField", operations::isMagicField)
        registerBoolean(env, "isMultiUse", operations::isMultiUse)
        registerBoolean(env, "isPickupable", operations::isPickupable)
        registerBoolean(env, "isKey", operations::isKey)
        registerBoolean(env, "isQuiver", operations::isQuiver)
        registerBoolean(env, "isPodium", operations::isPodium)
        registerBoolean(env, "isWeapon", operations::isWeapon)

        registerInt(env, "getType", operations::getType)
        registerInt(env, "getId", operations::getId)
        registerString(env, "getName", operations::getName)
        registerString(env, "getPluralName", operations::getPluralName)
        registerString(env, "getArticle", operations::getArticle)

        registry.registerMethod(ITEM_TYPE_TYPE, "getDescription", luaFunction { args ->
            val itemType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(2).optint(-1)
            LuaValue.valueOf(operations.getDescription(itemType, count))
        })

        registerInt(env, "getSlotPosition", operations::getSlotPosition)
        registerInt(env, "getCharges", operations::getCharges)
        registerInt(env, "getFluidSource", operations::getFluidSource)
        registerInt(env, "getCapacity", operations::getCapacity)

        registry.registerMethod(ITEM_TYPE_TYPE, "getWeight", luaFunction { args ->
            val itemType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val count = args.arg(2).optint(1)
            LuaValue.valueOf(operations.getWeight(itemType, count))
        })

        registerInt(env, "getStackSize", operations::getStackSize)
        registerInt(env, "getHitChance", operations::getHitChance)
        registerInt(env, "getShootRange", operations::getShootRange)
        registerInt(env, "getAttack", operations::getAttack)
        registerInt(env, "getDefense", operations::getDefense)
        registerInt(env, "getExtraDefense", operations::getExtraDefense)
        registerInt(env, "getImbuementSlot", operations::getImbuementSlot)
        registerInt(env, "getArmor", operations::getArmor)
        registerInt(env, "getWeaponType", operations::getWeaponType)
        registerNullableInt(env, "getElementType", operations::getElementType)
        registerNullableInt(env, "getElementDamage", operations::getElementDamage)
        registerInt(env, "getTransformEquipId", operations::getTransformEquipId)
        registerInt(env, "getTransformDeEquipId", operations::getTransformDeEquipId)
        registerInt(env, "getDestroyId", operations::getDestroyId)
        registerInt(env, "getDecayId", operations::getDecayId)
        registerInt(env, "getRequiredLevel", operations::getRequiredLevel)
        registerInt(env, "getAmmoType", operations::getAmmoType)
        registerInt(env, "getDecayTime", operations::getDecayTime)
        registerBoolean(env, "getShowDuration", operations::getShowDuration)
        registerInt(env, "getWrapableTo", operations::getWrapableTo)
        registerNullableInt(env, "getSpeed", operations::getSpeed)
        registerInt(env, "getBaseSpeed", operations::getBaseSpeed)
        registerString(env, "getVocationString", operations::getVocationString)
        registerInt(env, "getElementalBond", operations::getElementalBond)
        registerBoolean(env, "hasSubType", operations::hasSubType)
    }

    private fun registerBoolean(env: LuaEnvironment, name: String, getter: (Any) -> Boolean) {
        env.userdata.registerMethod(ITEM_TYPE_TYPE, name, luaFunction { args ->
            val itemType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(getter(itemType))
        })
    }

    private fun registerInt(env: LuaEnvironment, name: String, getter: (Any) -> Int) {
        env.userdata.registerMethod(ITEM_TYPE_TYPE, name, luaFunction { args ->
            val itemType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(getter(itemType))
        })
    }

    private fun registerNullableInt(env: LuaEnvironment, name: String, getter: (Any) -> Int?) {
        env.userdata.registerMethod(ITEM_TYPE_TYPE, name, luaFunction { args ->
            val itemType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            getter(itemType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })
    }

    private fun registerString(env: LuaEnvironment, name: String, getter: (Any) -> String) {
        env.userdata.registerMethod(ITEM_TYPE_TYPE, name, luaFunction { args ->
            val itemType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            LuaValue.valueOf(getter(itemType))
        })
    }
}

internal object UnwiredItemTypeOperations : ItemTypeOperations {
    override fun create(idOrName: Any): Any? =
        throw NotImplementedError("ItemTypeFunctions.create seam is not wired")

    override fun getId(itemType: Any): Int =
        throw NotImplementedError("ItemTypeFunctions.getId seam is not wired")

    override fun getName(itemType: Any): String =
        throw NotImplementedError("ItemTypeFunctions.getName seam is not wired")

    override fun isContainer(itemType: Any): Boolean =
        throw NotImplementedError("ItemTypeFunctions.isContainer seam is not wired")

    override fun isStackable(itemType: Any): Boolean =
        throw NotImplementedError("ItemTypeFunctions.isStackable seam is not wired")

    override fun isPickupable(itemType: Any): Boolean =
        throw NotImplementedError("ItemTypeFunctions.isPickupable seam is not wired")

    override fun getCapacity(itemType: Any): Int =
        throw NotImplementedError("ItemTypeFunctions.getCapacity seam is not wired")

    override fun getWeight(itemType: Any, count: Int): Int =
        throw NotImplementedError("ItemTypeFunctions.getWeight seam is not wired")

    override fun hasSubType(itemType: Any): Boolean =
        throw NotImplementedError("ItemTypeFunctions.hasSubType seam is not wired")
}
