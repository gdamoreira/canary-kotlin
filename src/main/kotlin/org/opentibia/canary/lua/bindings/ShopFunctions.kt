package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam for the `Shop` binding group. The only engine dependency the Canary
 * setters need is the item-name catalog used by `shop:setIdFromName`.
 */
interface ShopOperations {
    /** Resolves an item name to its id, mirroring `Item::items.nameToItems`; 0 when unknown. */
    fun itemIdByName(name: String): Int
}

/**
 * Port of Canary's `ShopBlock` (src/creatures/creatures_definitions.hpp:1804) as mutated
 * by the `Shop` Lua methods. The C++ `Shop` wrapper stores one `shopBlock` plus its
 * `childShop` vector; this holder mirrors both.
 */
class ShopData(
    var itemId: Int = 0,
    var itemName: String = "",
    var itemSubType: Int = 0,
    var itemBuyPrice: Long = 0,
    var itemSellPrice: Long = 0,
    var itemStorageKey: Int = 0,
    var itemStorageValue: Int = 0,
    val childShop: MutableList<ShopData> = mutableListOf(),
) {
    /** Value copy matching `luaShopAddChildShop`'s by-value `shopBlock` push_back. */
    fun copy(): ShopData = ShopData(
        itemId = itemId,
        itemName = itemName,
        itemSubType = itemSubType,
        itemBuyPrice = itemBuyPrice,
        itemSellPrice = itemSellPrice,
        itemStorageKey = itemStorageKey,
        itemStorageValue = itemStorageValue,
    )
}

/**
 * Registers the Canary `Shop` class from
 * src/lua/functions/creatures/npc/shop_functions.cpp:
 *
 *   Shop()
 *   shop:setId(id)
 *   shop:setIdFromName(name)
 *   shop:setNameItem(name)
 *   shop:setCount(count)
 *   shop:setBuyPrice(price)
 *   shop:setSellPrice(price)
 *   shop:setStorageKey(storage)
 *   shop:setStorageValue(value)
 *   shop:addChildShop(shop)
 *
 * `addChildShop` stores a value copy of the child block and, like the C++, pushes no
 * result on success (the Lua call evaluates to `nil`).
 */
object ShopFunctions {
    const val SHOP_TYPE = "Shop"

    fun install(env: LuaEnvironment, operations: ShopOperations = WorldShopOperations()) {
        val registry = env.userdata

        env.globals.set(SHOP_TYPE, luaFunction {
            env.userdata.wrap(SHOP_TYPE, ShopData())
        })

        registry.registerMethod(SHOP_TYPE, "setId", luaFunction { args ->
            val shop = retrieveUserdata(args, 1) as? ShopData ?: return@luaFunction LuaValue.NIL
            if (!args.arg(2).isnumber()) {
                return@luaFunction LuaValue.NIL
            }
            shop.itemId = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(SHOP_TYPE, "setIdFromName", luaFunction { args ->
            val shop = retrieveUserdata(args, 1) as? ShopData ?: return@luaFunction LuaValue.NIL
            if (!args.arg(2).isstring()) {
                return@luaFunction LuaValue.NIL
            }
            val itemId = operations.itemIdByName(args.arg(2).tojstring())
            if (itemId == 0) {
                return@luaFunction LuaValue.NIL
            }
            shop.itemId = itemId
            LuaValue.TRUE
        })

        registry.registerMethod(SHOP_TYPE, "setNameItem", luaFunction { args ->
            val shop = retrieveUserdata(args, 1) as? ShopData ?: return@luaFunction LuaValue.NIL
            shop.itemName = args.arg(2).tojstring()
            LuaValue.TRUE
        })

        registry.registerMethod(SHOP_TYPE, "setCount", luaFunction { args ->
            val shop = retrieveUserdata(args, 1) as? ShopData ?: return@luaFunction LuaValue.NIL
            shop.itemSubType = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(SHOP_TYPE, "setBuyPrice", luaFunction { args ->
            val shop = retrieveUserdata(args, 1) as? ShopData ?: return@luaFunction LuaValue.NIL
            shop.itemBuyPrice = args.arg(2).tolong()
            LuaValue.TRUE
        })

        registry.registerMethod(SHOP_TYPE, "setSellPrice", luaFunction { args ->
            val shop = retrieveUserdata(args, 1) as? ShopData ?: return@luaFunction LuaValue.NIL
            shop.itemSellPrice = args.arg(2).tolong()
            LuaValue.TRUE
        })

        registry.registerMethod(SHOP_TYPE, "setStorageKey", luaFunction { args ->
            val shop = retrieveUserdata(args, 1) as? ShopData ?: return@luaFunction LuaValue.NIL
            shop.itemStorageKey = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(SHOP_TYPE, "setStorageValue", luaFunction { args ->
            val shop = retrieveUserdata(args, 1) as? ShopData ?: return@luaFunction LuaValue.NIL
            shop.itemStorageValue = args.arg(2).toint()
            LuaValue.TRUE
        })

        registry.registerMethod(SHOP_TYPE, "addChildShop", luaFunction { args ->
            val shop = retrieveUserdata(args, 1) as? ShopData ?: return@luaFunction LuaValue.NIL
            val child = retrieveUserdata(args, 2) as? ShopData ?: return@luaFunction LuaValue.NIL
            shop.childShop += child.copy()
            LuaValue.NIL
        })
    }
}

/**
 * [ShopOperations] seam resolving `shop:setIdFromName` through the optional
 * `name -> id` item catalog; an absent catalog misses like
 * `Item::items.nameToItems` without the name.
 */
class WorldShopOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
) : ShopOperations {
    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0
}
