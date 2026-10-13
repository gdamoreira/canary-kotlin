package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the `Loot` binding group. The only engine dependency the Canary
 * setters need is the item-name catalog used by `loot:setIdFromName`.
 */
interface LootOperations {
    /** Resolves an item name to its id, mirroring `Item::items.nameToItems`; 0 when unknown. */
    fun itemIdByName(name: String): Int
}

/**
 * Port of Canary's `LootBlock` (src/creatures/creatures_definitions.hpp:1765) as mutated
 * by the `Loot` Lua methods. Defaults match `LootBlock()`'s constructor.
 */
class LootData(
    val monsterName: String? = null,
    var id: Int = 0,
    var chance: Int = 0,
    var subType: Int = -1,
    var countMin: Int = 1,
    var countMax: Int = 1,
    var actionId: Int = -1,
    var text: String = "",
    var name: String = "",
    var article: String = "",
    var attack: Int = -1,
    var defense: Int = -1,
    var extraDefense: Int = -1,
    var armor: Int = -1,
    var shootRange: Int = -1,
    var hitChance: Int = -1,
    var unique: Boolean = false,
    val childLoot: MutableList<LootData> = mutableListOf(),
) {
    /** Value copy matching `MonsterType::loadLoot`'s by-value `LootBlock` parameter. */
    fun deepCopy(): LootData = LootData(
        id = id,
        chance = chance,
        subType = subType,
        countMin = countMin,
        countMax = countMax,
        actionId = actionId,
        text = text,
        name = name,
        article = article,
        attack = attack,
        defense = defense,
        extraDefense = extraDefense,
        armor = armor,
        shootRange = shootRange,
        hitChance = hitChance,
        unique = unique,
        childLoot = childLoot.mapTo(mutableListOf()) { it.deepCopy() },
    )
}

/**
 * Registers the Canary `Loot` class from
 * src/lua/functions/creatures/monster/loot_functions.cpp:
 *
 *   Loot([monsterName])
 *   loot:setId(id)
 *   loot:setIdFromName(name)
 *   loot:setMinCount(count)
 *   loot:setMaxCount(count)
 *   loot:setSubType(subType)
 *   loot:setChance(chance)
 *   loot:setActionId(actionId)
 *   loot:setText(text)
 *   loot:setNameItem(name)
 *   loot:setArticle(article)
 *   loot:setAttack(attack)
 *   loot:setDefense(defense)
 *   loot:setExtraDefense(extraDefense)
 *   loot:setArmor(armor)
 *   loot:setShootRange(shootRange)
 *   loot:setHitChance(hitChance)
 *   loot:setUnique([unique])
 *   loot:addChildLoot(loot)
 *
 * The `Loot` userdata carries a [LootData]; the global constructor mirrors
 * `Lua::registerSharedClass<Loot>` and rejects a non-string `monsterName` exactly like
 * `LootFunctions::luaCreateLoot`.
 */
object LootFunctions {
    const val LOOT_TYPE = "Loot"

    fun install(env: LuaEnvironment, operations: LootOperations = UnwiredLootOperations) {
        val registry = env.userdata

        env.globals.set(LOOT_TYPE, luaFunction { args ->
            val monsterName = when {
                args.arg(1).isnil() -> null
                args.arg(1).isstring() -> args.arg(1).tojstring()
                else -> throw LuaError("Loot([monsterName]) expects argument #1 to be a string")
            }
            env.userdata.wrap(LOOT_TYPE, LootData(monsterName = monsterName))
        })

        registry.registerMethod(LOOT_TYPE, "setId", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.id = value }
        })

        registry.registerMethod(LOOT_TYPE, "setIdFromName", luaFunction { args ->
            val loot = retrieveUserdata(args, 1) as? LootData ?: return@luaFunction LuaValue.NIL
            if (!args.arg(2).isstring()) {
                return@luaFunction LuaValue.NIL
            }
            val itemId = operations.itemIdByName(args.arg(2).tojstring())
            if (itemId == 0) {
                return@luaFunction LuaValue.NIL
            }
            loot.id = itemId
            LuaValue.TRUE
        })

        registry.registerMethod(LOOT_TYPE, "setMinCount", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.countMin = value }
        })

        registry.registerMethod(LOOT_TYPE, "setMaxCount", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.countMax = value }
        })

        registry.registerMethod(LOOT_TYPE, "setSubType", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.subType = value }
        })

        registry.registerMethod(LOOT_TYPE, "setChance", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.chance = value }
        })

        registry.registerMethod(LOOT_TYPE, "setActionId", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.actionId = value }
        })

        registry.registerMethod(LOOT_TYPE, "setText", luaFunction { args ->
            setLootString(args) { loot, value -> loot.text = value }
        })

        registry.registerMethod(LOOT_TYPE, "setNameItem", luaFunction { args ->
            setLootString(args) { loot, value -> loot.name = value }
        })

        registry.registerMethod(LOOT_TYPE, "setArticle", luaFunction { args ->
            setLootString(args) { loot, value -> loot.article = value }
        })

        registry.registerMethod(LOOT_TYPE, "setAttack", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.attack = value }
        })

        registry.registerMethod(LOOT_TYPE, "setDefense", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.defense = value }
        })

        registry.registerMethod(LOOT_TYPE, "setExtraDefense", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.extraDefense = value }
        })

        registry.registerMethod(LOOT_TYPE, "setArmor", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.armor = value }
        })

        registry.registerMethod(LOOT_TYPE, "setShootRange", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.shootRange = value }
        })

        registry.registerMethod(LOOT_TYPE, "setHitChance", luaFunction { args ->
            setLootInt(args) { loot, value -> loot.hitChance = value }
        })

        registry.registerMethod(LOOT_TYPE, "setUnique", luaFunction { args ->
            val loot = retrieveUserdata(args, 1) as? LootData ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                LuaValue.valueOf(loot.unique)
            } else {
                loot.unique = args.arg(2).toboolean()
                LuaValue.TRUE
            }
        })

        registry.registerMethod(LOOT_TYPE, "addChildLoot", luaFunction { args ->
            val loot = retrieveUserdata(args, 1) as? LootData ?: return@luaFunction LuaValue.NIL
            val child = retrieveUserdata(args, 2) as? LootData ?: return@luaFunction LuaValue.FALSE
            loot.childLoot += child.deepCopy()
            LuaValue.TRUE
        })
    }

    private fun setLootInt(args: Varargs, setter: (LootData, Int) -> Unit): LuaValue {
        val loot = retrieveUserdata(args, 1) as? LootData ?: return LuaValue.NIL
        setter(loot, args.arg(2).toint())
        return LuaValue.TRUE
    }

    private fun setLootString(args: Varargs, setter: (LootData, String) -> Unit): LuaValue {
        val loot = retrieveUserdata(args, 1) as? LootData ?: return LuaValue.NIL
        setter(loot, args.arg(2).tojstring())
        return LuaValue.TRUE
    }
}

internal object UnwiredLootOperations : LootOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("LootFunctions.itemIdByName seam is not wired")
}
