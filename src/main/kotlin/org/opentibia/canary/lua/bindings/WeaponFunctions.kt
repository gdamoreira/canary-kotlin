package org.opentibia.canary.lua.bindings

import io.github.oshai.kotlinlogging.KotlinLogging
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.SLOTP_HAND
import org.opentibia.canary.items.SLOTP_TWO_HAND
import org.opentibia.canary.items.WIELDINFO_MAGLV
import org.opentibia.canary.items.WIELDINFO_PREMIUM
import org.opentibia.canary.items.WIELDINFO_VOCREQ
import org.opentibia.canary.items.WIELDINFO_LEVEL
import org.opentibia.canary.items.WeaponAction_t
import org.opentibia.canary.items.weapons.Weapon
import org.opentibia.canary.items.weapons.WeaponDistance
import org.opentibia.canary.items.weapons.WeaponMelee
import org.opentibia.canary.items.weapons.WeaponWand
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment

private val logger = KotlinLogging.logger {}

/**
 * Engine seam for the `Weapon` binding group
 * (src/lua/functions/items/weapon_functions.cpp). The setters that target item
 * types need the `Item::items` catalog and the registry needs
 * `g_weapons().registerLuaEvent`; `vocationIdByName` mirrors
 * `g_vocations().getVocationId` for `weapon:vocation`.
 */
interface WeaponOperations {
    /**
     * `Item::items.getItemType(id)`; the C++ map `operator[]` auto-creates the
     * entry, so an implementation returns the existing [ItemType] or registers a
     * fresh default one for the id.
     */
    fun getItemType(id: Int): ItemType

    /** `Weapons::registerLuaEvent(weapon)`; [callback] is the `onUseWeapon` handler. */
    fun registerLuaEvent(weapon: Weapon, callback: LuaValue): Boolean

    /** `g_vocations().getVocationId(name)`; `-1` is the Canary not-found sentinel. */
    fun vocationIdByName(name: String): Int
}

/**
 * The Lua-side `Weapon` userdata payload. Canary stores the shared `Weapon`
 * pointer in the userdata plus the loaded `onUseWeapon` script id; the Kotlin
 * binding keeps the [callback] value alongside the domain object.
 */
class LuaWeapon(
    val weapon: Weapon,
    var callback: LuaValue = LuaValue.NIL,
)

/**
 * Registers the Canary `Weapon` class from
 * src/lua/functions/items/weapon_functions.cpp:
 *
 *   Weapon(type)
 *   weapon:action(action)
 *   weapon:register()
 *   weapon:id(id)
 *   weapon:level(level)
 *   weapon:magicLevel(level)
 *   weapon:mana(mana)
 *   weapon:manaPercent(percent)
 *   weapon:health(health)
 *   weapon:healthPercent(percent)
 *   weapon:soul(soul)
 *   weapon:breakChance(percent)
 *   weapon:premium(bool)
 *   weapon:wieldUnproperly(bool)
 *   weapon:vocation(name[, showInDescription = false, lastVoc = false])
 *   weapon:onUseWeapon(callback)
 *   weapon:element(combatType)
 *   weapon:attack(atk)
 *   weapon:defense(defense[, extraDefense])
 *   weapon:range(range)
 *   weapon:charges(charges[, showCharges = true])
 *   weapon:duration(duration[, showDuration = true])
 *   weapon:decayTo([itemid = 0])
 *   weapon:transformEquipTo(itemid)
 *   weapon:transformDeEquipTo(itemid)
 *   weapon:slotType(slot)
 *   weapon:hitChance(chance)
 *   weapon:extraElement(atk, combatType)
 *   weapon:ammoType(type)
 *   weapon:maxHitChance(max)
 *   weapon:damage(damage[, max])
 *   weapon:shootType(type)
 *
 * Each method mirrors the exact C++ control flow, including the `Weapon:action`
 * unconditional `true` return and the `nil` results for subtype-restricted methods.
 */
object WeaponFunctions {
    const val WEAPON_TYPE = "Weapon"

    fun install(env: LuaEnvironment, operations: WeaponOperations = UnwiredWeaponOperations) {
        env.userdata.registerClass(WEAPON_TYPE, "", luaFunction { args ->
            createWeapon(env, args.arg(1), operations)
        })

        val registry = env.userdata
        registry.registerMetaMethod(WEAPON_TYPE, "__newindex", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon
            if (state != null && args.arg(2).tojstring() == "onUseWeapon") {
                state.callback = args.arg(3)
            }
            LuaValue.NONE
        })

        registry.registerMethod(WEAPON_TYPE, "action", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            when (luaString(args.arg(2)).lowercase()) {
                "removecount" -> state.weapon.action = WeaponAction_t.WEAPONACTION_REMOVECOUNT
                "removecharge" -> state.weapon.action = WeaponAction_t.WEAPONACTION_REMOVECHARGE
                "move" -> state.weapon.action = WeaponAction_t.WEAPONACTION_MOVE
                else -> logger.error {
                    "[WeaponFunctions::luaWeaponAction] - No valid action ${luaString(args.arg(2))}"
                }
            }
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "register", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            val weapon = when (state.weapon.weaponType) {
                WeaponType_t.WEAPON_DISTANCE,
                WeaponType_t.WEAPON_AMMO,
                WeaponType_t.WEAPON_MISSILE,
                -> state.weapon as? WeaponDistance
                WeaponType_t.WEAPON_WAND -> state.weapon as? WeaponWand
                else -> state.weapon as? WeaponMelee
            } ?: return@luaFunction LuaValue.NIL

            val itemType = operations.getItemType(weapon.getID())
            itemType.weaponType = weapon.weaponType

            if (weapon.getWieldInfo() != 0) {
                itemType.wieldInfo = weapon.getWieldInfo().toLong()
                itemType.vocationString = weapon.getVocationString()
                itemType.minReqLevel = weapon.getReqLevel().toLong()
                itemType.minReqMagicLevel = weapon.getReqMagLv().toLong()
            }

            weapon.configureWeapon(itemType)
            LuaValue.valueOf(operations.registerLuaEvent(weapon, state.callback))
        })

        registry.registerMethod(WEAPON_TYPE, "id", luaFunction { args ->
            setWeapon(args) { weapon, value -> weapon.setID(value) }
        })

        registry.registerMethod(WEAPON_TYPE, "level", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            state.weapon.setRequiredLevel(luaNumber(args.arg(2)))
            state.weapon.setWieldInfo(WIELDINFO_LEVEL)
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "magicLevel", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            state.weapon.setRequiredMagLevel(luaNumber(args.arg(2)))
            state.weapon.setWieldInfo(WIELDINFO_MAGLV)
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "mana", luaFunction { args ->
            setWeapon(args) { weapon, value -> weapon.setMana(value) }
        })

        registry.registerMethod(WEAPON_TYPE, "manaPercent", luaFunction { args ->
            setWeapon(args) { weapon, value -> weapon.setManaPercent(value) }
        })

        registry.registerMethod(WEAPON_TYPE, "health", luaFunction { args ->
            setWeapon(args) { weapon, value -> weapon.setHealth(value) }
        })

        registry.registerMethod(WEAPON_TYPE, "healthPercent", luaFunction { args ->
            setWeapon(args) { weapon, value -> weapon.setHealthPercent(value) }
        })

        registry.registerMethod(WEAPON_TYPE, "soul", luaFunction { args ->
            setWeapon(args) { weapon, value -> weapon.setSoul(value) }
        })

        registry.registerMethod(WEAPON_TYPE, "breakChance", luaFunction { args ->
            setWeapon(args) { weapon, value -> weapon.setBreakChance(value) }
        })

        registry.registerMethod(WEAPON_TYPE, "maxHitChance", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            operations.getItemType(state.weapon.getID()).maxHitChance = luaNumber(args.arg(2))
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "premium", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            state.weapon.setNeedPremium(args.arg(2).toboolean())
            state.weapon.setWieldInfo(WIELDINFO_PREMIUM)
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "wieldUnproperly", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            state.weapon.setWieldUnproperly(args.arg(2).toboolean())
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "vocation", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            val weapon = state.weapon
            weapon.addVocWeaponMap(luaString(args.arg(2)))
            weapon.setWieldInfo(WIELDINFO_VOCREQ)

            val showInDescription = args.arg(3).toboolean()
            val lastVoc = args.arg(4).toboolean()
            if (showInDescription) {
                val vocName = luaString(args.arg(2))
                val updated = if (weapon.getVocationString().isEmpty()) {
                    "${vocName.lowercase()}s"
                } else {
                    val separator = if (lastVoc) " and " else ", "
                    "${weapon.getVocationString()}$separator${vocName.lowercase()}s"
                }
                weapon.setVocationString(updated)
            }
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "onUseWeapon", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            val callback = args.arg(2)
            if (callback.isfunction()) {
                state.callback = callback
            }
            LuaValue.valueOf(state.callback.isfunction())
        })

        registry.registerMethod(WEAPON_TYPE, "element", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            resolveCombatType(args.arg(2), "luaWeaponElement")?.let { state.weapon.params.combatType = it }
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "attack", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            operations.getItemType(state.weapon.getID()).attack = luaNumber(args.arg(2))
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "defense", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            val itemType = operations.getItemType(state.weapon.getID())
            itemType.defense = luaNumber(args.arg(2))
            if (args.narg() > 2) {
                itemType.extraDefense = luaNumber(args.arg(3))
            }
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "range", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            operations.getItemType(state.weapon.getID()).shootRange = luaNumber(args.arg(2)) and 0xFF
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "charges", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            var showCharges = true
            if (args.narg() > 2) {
                showCharges = args.arg(3).toboolean()
            }
            val itemType = operations.getItemType(state.weapon.getID())
            itemType.charges = luaNumber(args.arg(2)) and 0xFF
            itemType.showCharges = showCharges
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "duration", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            var showDuration = true
            if (args.narg() > 2) {
                showDuration = args.arg(3).toboolean()
            }
            val itemType = operations.getItemType(state.weapon.getID())
            itemType.decayTime = (luaNumber(args.arg(2)) and 0xFF).toLong()
            itemType.showDuration = showDuration
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "decayTo", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            var itemId = 0
            if (args.narg() > 1) {
                itemId = luaNumber(args.arg(2))
            }
            operations.getItemType(state.weapon.getID()).decayTo = itemId
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "transformEquipTo", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            operations.getItemType(state.weapon.getID()).transformEquipTo = luaNumber(args.arg(2))
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "transformDeEquipTo", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            operations.getItemType(state.weapon.getID()).transformDeEquipTo = luaNumber(args.arg(2))
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "shootType", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            operations.getItemType(state.weapon.getID()).shootType = luaNumber(args.arg(2))
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "slotType", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            val itemType = operations.getItemType(state.weapon.getID())
            itemType.slotPosition = if (luaString(args.arg(2)) == "two-handed") SLOTP_TWO_HAND else SLOTP_HAND
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "hitChance", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            operations.getItemType(state.weapon.getID()).hitChance = luaNumber(args.arg(2)).toByte().toInt()
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "extraElement", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            val itemType = operations.getItemType(state.weapon.getID())
            val abilities = itemType.abilities ?: Abilities().also { itemType.abilities = it }
            abilities.elementDamage = luaNumber(args.arg(2))
            resolveCombatType(args.arg(3), "luaWeaponExtraElement")?.let { abilities.elementType = it }
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "ammoType", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            val distance = state.weapon as? WeaponDistance ?: return@luaFunction LuaValue.NIL
            val itemType = operations.getItemType(distance.getID())
            when (luaString(args.arg(2))) {
                "arrow" -> itemType.ammoType = Ammo_t.AMMO_ARROW.value
                "bolt" -> itemType.ammoType = Ammo_t.AMMO_BOLT.value
                else -> {
                    logger.warn {
                        "[WeaponFunctions:luaWeaponAmmoType] - Type ${luaString(args.arg(2))} does not exist"
                    }
                    return@luaFunction LuaValue.NIL
                }
            }
            LuaValue.TRUE
        })

        registry.registerMethod(WEAPON_TYPE, "damage", luaFunction { args ->
            val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return@luaFunction LuaValue.NIL
            val wand = state.weapon as? WeaponWand ?: return@luaFunction LuaValue.NIL
            wand.setMinChange(luaNumber(args.arg(2)))
            if (args.narg() > 2) {
                wand.setMaxChange(luaNumber(args.arg(3)))
            } else {
                wand.setMaxChange(luaNumber(args.arg(2)))
            }
            LuaValue.TRUE
        })
    }

    private fun createWeapon(env: LuaEnvironment, type: LuaValue, operations: WeaponOperations): LuaValue {
        val weaponType = WeaponType_t.entries.firstOrNull { it.value == luaNumber(type) } ?: return LuaValue.NIL
        val weapon: Weapon = when (weaponType) {
            WeaponType_t.WEAPON_SWORD,
            WeaponType_t.WEAPON_AXE,
            WeaponType_t.WEAPON_CLUB,
            -> WeaponMelee(
                vocationIdProvider = operations::vocationIdByName,
                itemTypeProvider = operations::getItemType,
            )

            WeaponType_t.WEAPON_MISSILE,
            WeaponType_t.WEAPON_DISTANCE,
            WeaponType_t.WEAPON_AMMO,
            -> WeaponDistance(
                vocationIdProvider = operations::vocationIdByName,
                itemTypeProvider = operations::getItemType,
            )

            WeaponType_t.WEAPON_WAND -> WeaponWand(
                vocationIdProvider = operations::vocationIdByName,
                itemTypeProvider = operations::getItemType,
            )

            else -> return LuaValue.NIL
        }
        weapon.weaponType = weaponType
        return env.userdata.wrap(WEAPON_TYPE, LuaWeapon(weapon))
    }

    private fun setWeapon(args: org.luaj.vm2.Varargs, setter: (Weapon, Int) -> Unit): LuaValue {
        val state = retrieveUserdata(args, 1) as? LuaWeapon ?: return LuaValue.NIL
        setter(state.weapon, luaNumber(args.arg(2)))
        return LuaValue.TRUE
    }

    /**
     * `luaWeaponElement`/`luaWeaponExtraElement` combat-type resolution: a
     * non-zero number is the `CombatType_t` value, otherwise the lower-cased
     * string is matched. An unknown string returns null so the caller leaves the
     * stored combat type untouched, exactly like the C++ `else` branch.
     */
    private fun resolveCombatType(value: LuaValue, context: String): CombatType? {
        val numeric = luaNumber(value)
        if (numeric != 0) {
            return CombatType.entries.firstOrNull { it.value == numeric } ?: CombatType.NONE
        }
        return when (luaString(value).lowercase()) {
            "earth" -> CombatType.EARTHDAMAGE
            "ice" -> CombatType.ICEDAMAGE
            "energy" -> CombatType.ENERGYDAMAGE
            "fire" -> CombatType.FIREDAMAGE
            "death" -> CombatType.DEATHDAMAGE
            "holy" -> CombatType.HOLYDAMAGE
            else -> {
                logger.warn { "[WeaponFunctions:$context] - Type ${luaString(value)} does not exist" }
                null
            }
        }
    }
}

/** `Lua::getNumber` (`lua_tonumber`); a non-number yields 0 like the C++ helper. */
internal fun luaNumber(value: LuaValue): Int {
    val number = value.tonumber()
    return if (number.isnil()) 0 else number.toint()
}

/** `Lua::getString` (`lua_tostring` converts numbers, everything else yields ""). */
internal fun luaString(value: LuaValue): String =
    if (value.isstring() || value.isnumber()) value.tojstring() else ""

/** `Lua::getNumber<double>`; a missing or non-numeric value yields 0.0 like the C++ helper. */
internal fun luaDouble(value: LuaValue): Double {
    val number = value.tonumber()
    return if (number.isnil()) 0.0 else number.todouble()
}

internal object UnwiredWeaponOperations : WeaponOperations {
    override fun getItemType(id: Int): ItemType =
        throw NotImplementedError("WeaponFunctions.getItemType seam is not wired")

    override fun registerLuaEvent(weapon: Weapon, callback: LuaValue): Boolean =
        throw NotImplementedError("WeaponFunctions.registerLuaEvent seam is not wired")

    override fun vocationIdByName(name: String): Int =
        throw NotImplementedError("WeaponFunctions.vocationIdByName seam is not wired")
}
