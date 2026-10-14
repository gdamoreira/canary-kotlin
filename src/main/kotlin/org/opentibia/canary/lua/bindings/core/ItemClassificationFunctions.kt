package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.systems.forge.ItemClassification
import org.opentibia.canary.systems.forge.ItemClassifications

/**
 * Registers Canary's classification Lua interface:
 *
 *  - the `ItemClassification` userdata class and its `addTier` method
 *    (src/lua/functions/items/item_classification_functions.cpp:16-55);
 *  - the `Game.createItemClassification(id)` primitive
 *    (src/lua/functions/core/game/game_functions.cpp:739-749).
 *
 * Like Canary's `Lua::registerClass`, the global `ItemClassification` table is the
 * same table used as the userdata method table, so the Lua helper
 * `data/scripts/lib/register_item_tier.lua` can attach `ItemClassification.register`
 * and `data/scripts/systems/item_tiers.lua` can call it on the returned userdata.
 */
object ItemClassificationFunctions {
    const val ITEM_CLASSIFICATION = "ItemClassification"

    fun install(env: LuaEnvironment, classifications: ItemClassifications) {
        env.userdata.registerClass(ITEM_CLASSIFICATION)

        env.userdata.registerMethod(ITEM_CLASSIFICATION, "addTier", luaFunction { args ->
            val classification = retrieveUserdata(args, 1) as? ItemClassification
            if (classification == null) {
                return@luaFunction LuaValue.NIL
            }
            classification.addTier(
                tierId = args.arg(2).optint(0),
                corePrice = args.arg(3).optint(0),
                regularPrice = args.arg(4).optlong(0),
                convergenceFusionPrice = args.arg(5).optlong(0),
                convergenceTransferPrice = args.arg(6).optlong(0),
            )
            LuaValue.TRUE
        })

        val game = (env.globals.get(GameFunctions.GAME_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(GameFunctions.GAME_GLOBAL, it) }
        game.set("createItemClassification", luaFunction { args ->
            val classification = classifications.createClassification(args.arg(1).optint(0))
            env.userdata.wrap(ITEM_CLASSIFICATION, classification)
        })
    }
}
