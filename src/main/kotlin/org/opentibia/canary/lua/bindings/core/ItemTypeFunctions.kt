package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaValue
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.runtime.LuaEnvironment
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Engine seam for the `ItemType` metatable. Mirrors the primitives in
 * src/lua/functions/items/item_type_functions.cpp over the Kotlin
 * [org.opentibia.canary.items.ItemType] domain object.
 *
 * Every member reads the field or `ItemType` method Canary reads
 * (src/items/items.hpp:96-387). Operations backed by another object fall back to
 * Canary's `ItemType` struct defaults (src/items/items.hpp:262-386) so scripts keep
 * running against an unwired catalog.
 */
interface ItemTypeOperations {
    /** Mirrors `ItemType(id or name)` resolving `Item::items[id]`. */
    fun create(idOrName: Any): Any?

    /** Mirrors `ItemType::id`. */
    fun getId(itemType: Any): Int = (itemType as? ItemType)?.id ?: 0

    /** Mirrors `ItemType::name`. */
    fun getName(itemType: Any): String = (itemType as? ItemType)?.name ?: ""

    /** Mirrors `ItemType::isContainer` (`group == ITEM_GROUP_CONTAINER`). */
    fun isContainer(itemType: Any): Boolean = (itemType as? ItemType)?.isContainer ?: false

    /** Mirrors `ItemType::stackable`. */
    fun isStackable(itemType: Any): Boolean = (itemType as? ItemType)?.isStackable ?: false

    /** Mirrors `ItemType::pickupable`; Canary's struct default is false. */
    fun isPickupable(itemType: Any): Boolean = (itemType as? ItemType)?.isPickupable ?: false

    /** Mirrors `ItemType::maxItems`; Canary's struct default is 8. */
    fun getCapacity(itemType: Any): Int = (itemType as? ItemType)?.maxItems ?: 8

    /** Mirrors `itemType:getWeight([count = 1])` in Canary's hundredths of an ounce. */
    fun getWeight(itemType: Any, count: Int): Int {
        val weight = (itemType as? ItemType)?.weight ?: return 0
        return (weight * 100.0).roundToInt() * max(1, count)
    }

    /** Mirrors `ItemType::hasSubType` (`isFluidContainer || isSplash || stackable || charges != 0`). */
    fun hasSubType(itemType: Any): Boolean = (itemType as? ItemType)?.hasSubType() ?: false

    /** Mirrors `ItemType::isCorpse`. */
    fun isCorpse(itemType: Any): Boolean = (itemType as? ItemType)?.isCorpse ?: false

    /** Mirrors `ItemType::isDoor` (`type == ITEM_TYPE_DOOR`). */
    fun isDoor(itemType: Any): Boolean = (itemType as? ItemType)?.isDoor ?: false

    /** Mirrors `ItemType::isFluidContainer` (`group == ITEM_GROUP_FLUID`). */
    fun isFluidContainer(itemType: Any): Boolean = (itemType as? ItemType)?.isFluidContainer ?: false

    /** Mirrors `ItemType::movable`; Canary's struct default is false. */
    fun isMovable(itemType: Any): Boolean = (itemType as? ItemType)?.isMovable ?: false

    /** Mirrors `ItemType::isRune` (`type == ITEM_TYPE_RUNE`). */
    fun isRune(itemType: Any): Boolean = (itemType as? ItemType)?.isRune() ?: false

    /** Mirrors `ItemType::isStowable` (`stackable && wareId > 0`). */
    fun isStowable(itemType: Any): Boolean {
        val type = itemType as? ItemType ?: return false
        return type.isStackable && type.wareId > 0
    }

    /** Mirrors `ItemType::canReadText`; Canary's struct default is false. */
    fun isReadable(itemType: Any): Boolean = (itemType as? ItemType)?.canReadText ?: false

    /** Mirrors `ItemType::canWriteText`; Canary's struct default is false. */
    fun isWritable(itemType: Any): Boolean = (itemType as? ItemType)?.canWriteText ?: false

    /** Mirrors `ItemType::isBlocking` (`blockProjectile || blockSolid`). */
    fun isBlocking(itemType: Any): Boolean {
        val type = itemType as? ItemType ?: return false
        return type.blockProjectile || type.blockSolid
    }

    /** Mirrors `ItemType::isGroundTile` (`group == ITEM_GROUP_GROUND`). */
    fun isGroundTile(itemType: Any): Boolean = (itemType as? ItemType)?.isGroundTile ?: false

    /** Mirrors `ItemType::isMagicField` (`type == ITEM_TYPE_MAGICFIELD`). */
    fun isMagicField(itemType: Any): Boolean = (itemType as? ItemType)?.isMagicField() ?: false

    /** Mirrors `ItemType::multiUse`; Canary's struct default is false. */
    fun isMultiUse(itemType: Any): Boolean = (itemType as? ItemType)?.isMultiUse() ?: false

    /** Mirrors `ItemType::isKey` (`type == ITEM_TYPE_KEY`). */
    fun isKey(itemType: Any): Boolean = (itemType as? ItemType)?.isKey() ?: false

    /** Mirrors `ItemType::isQuiver` (`type == ITEM_TYPE_QUIVER`). */
    fun isQuiver(itemType: Any): Boolean = (itemType as? ItemType)?.isQuiver() ?: false

    /** Mirrors `ItemType::isPodium`; Canary's struct default is false. */
    fun isPodium(itemType: Any): Boolean = (itemType as? ItemType)?.isPodium ?: false

    /** Mirrors `ItemType::isWeapon` (`weaponType not in NONE/SHIELD/AMMO`). */
    fun isWeapon(itemType: Any): Boolean = (itemType as? ItemType)?.isWeapon() ?: false

    /** Mirrors `ItemType::type`; `ITEM_TYPE_NONE` (0) when unset. */
    fun getType(itemType: Any): Int = (itemType as? ItemType)?.type?.value ?: 0

    /** Mirrors `ItemType::getPluralName` (stored plural, else `name + "s"` while `showCount`). */
    fun getPluralName(itemType: Any): String =
        (itemType as? ItemType)?.getPluralName() ?: getName(itemType) + "s"

    /** Mirrors `ItemType::article`; Canary's struct default is empty. */
    fun getArticle(itemType: Any): String = (itemType as? ItemType)?.article ?: ""

    /**
     * Mirrors `itemType:getDescription([count = -1])`; empty because Canary's
     * `Item::getDescription` renderer is not ported for item types.
     */
    fun getDescription(itemType: Any, count: Int): String = ""

    /** Mirrors `ItemType::slotPosition`; Canary's struct default is `SLOTP_HAND` (48). */
    fun getSlotPosition(itemType: Any): Int = (itemType as? ItemType)?.slotPosition ?: SLOTP_HAND

    /** Mirrors `ItemType::charges`; Canary's struct default is 0. */
    fun getCharges(itemType: Any): Int = (itemType as? ItemType)?.charges ?: 0

    /** Mirrors `ItemType::fluidSource`; Canary's struct default is `FLUID_NONE` (0). */
    fun getFluidSource(itemType: Any): Int = (itemType as? ItemType)?.fluidSource ?: 0

    /** Mirrors `ItemType::stackSize`; Canary's struct default is 100. */
    fun getStackSize(itemType: Any): Int = (itemType as? ItemType)?.stackSize ?: 100

    /** Mirrors `ItemType::hitChance`; Canary's struct default is 0. */
    fun getHitChance(itemType: Any): Int = (itemType as? ItemType)?.hitChance ?: 0

    /** Mirrors `ItemType::shootRange`; Canary's struct default is 1. */
    fun getShootRange(itemType: Any): Int = (itemType as? ItemType)?.shootRange ?: 1

    /** Mirrors `ItemType::attack`; Canary's struct default is 0. */
    fun getAttack(itemType: Any): Int = (itemType as? ItemType)?.attack ?: 0

    /** Mirrors `ItemType::defense`; Canary's struct default is 0. */
    fun getDefense(itemType: Any): Int = (itemType as? ItemType)?.defense ?: 0

    /** Mirrors `ItemType::extraDefense`; Canary's struct default is 0. */
    fun getExtraDefense(itemType: Any): Int = (itemType as? ItemType)?.extraDefense ?: 0

    /** Mirrors `ItemType::imbuementSlot`; Canary's struct default is 0. */
    fun getImbuementSlot(itemType: Any): Int = (itemType as? ItemType)?.imbuementSlot ?: 0

    /** Mirrors `ItemType::armor`; Canary's struct default is 0. */
    fun getArmor(itemType: Any): Int = (itemType as? ItemType)?.armor ?: 0

    /** Mirrors `ItemType::weaponType`; Canary's struct default is `WEAPON_NONE` (0). */
    fun getWeaponType(itemType: Any): Int = (itemType as? ItemType)?.weaponType?.value ?: 0

    /** Mirrors `getElementType`; nil while `abilities` is unset. */
    fun getElementType(itemType: Any): Int? = (itemType as? ItemType)?.abilities?.elementType?.value

    /** Mirrors `getElementDamage`; nil while `abilities` is unset. */
    fun getElementDamage(itemType: Any): Int? = (itemType as? ItemType)?.abilities?.elementDamage

    /** Mirrors `ItemType::transformEquipTo`; Canary's struct default is 0. */
    fun getTransformEquipId(itemType: Any): Int = (itemType as? ItemType)?.transformEquipTo ?: 0

    /** Mirrors `ItemType::transformDeEquipTo`; Canary's struct default is 0. */
    fun getTransformDeEquipId(itemType: Any): Int = (itemType as? ItemType)?.transformDeEquipTo ?: 0

    /** Mirrors `ItemType::destroyTo`; Canary's struct default is 0. */
    fun getDestroyId(itemType: Any): Int = (itemType as? ItemType)?.destroyTo ?: 0

    /** Mirrors `ItemType::decayTo`; Canary's struct default is -1. */
    fun getDecayId(itemType: Any): Int = (itemType as? ItemType)?.decayTo ?: -1

    /** Mirrors `ItemType::minReqLevel`; Canary's struct default is 0. */
    fun getRequiredLevel(itemType: Any): Int = (itemType as? ItemType)?.let { it.minReqLevel.toInt() } ?: 0

    /** Mirrors `ItemType::ammoType`; Canary's struct default is `AMMO_NONE` (0). */
    fun getAmmoType(itemType: Any): Int = (itemType as? ItemType)?.ammoType ?: 0

    /** Mirrors `ItemType::decayTime`; Canary's struct default is 0. */
    fun getDecayTime(itemType: Any): Int = (itemType as? ItemType)?.let { it.decayTime.toInt() } ?: 0

    /** Mirrors `ItemType::showDuration`; Canary's struct default is false. */
    fun getShowDuration(itemType: Any): Boolean = (itemType as? ItemType)?.showDuration ?: false

    /** Mirrors `ItemType::wrapableTo`; Canary's struct default is 0. */
    fun getWrapableTo(itemType: Any): Int = (itemType as? ItemType)?.wrapableTo ?: 0

    /** Mirrors `getSpeed`; nil while `abilities` is unset. */
    fun getSpeed(itemType: Any): Int? = (itemType as? ItemType)?.abilities?.speed

    /** Mirrors `ItemType::speed`; Canary's struct default is 0. */
    fun getBaseSpeed(itemType: Any): Int = (itemType as? ItemType)?.speed ?: 0

    /** Mirrors `ItemType::vocationString`; Canary's struct default is empty. */
    fun getVocationString(itemType: Any): String = (itemType as? ItemType)?.vocationString ?: ""

    /** Mirrors `ItemType::elementalBond`; Canary's struct default is `COMBAT_NONE` (255). */
    fun getElementalBond(itemType: Any): Int = (itemType as? ItemType)?.elementalBond?.value ?: 255
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
}
