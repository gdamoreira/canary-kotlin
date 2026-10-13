package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Transcript-style tests for the Canary `Shop` binding group, ported from
 * src/lua/functions/creatures/npc/shop_functions.cpp.
 */
class ShopFunctionsTest {

    private class FakeOps : ShopOperations {
        override fun itemIdByName(name: String): Int = when (name) {
            "crystal coin" -> 2160
            else -> 0
        }
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply {
        initState()
        ShopFunctions.install(this, FakeOps())
    }

    @Test
    fun `Shop constructor creates a fresh shop data userdata`() {
        val environment = env()

        environment.loadChunk("shop = Shop()")
        environment.loadChunk("other = Shop()")

        assertThat(environment.getGlobal("shop").touserdata()).isInstanceOf(ShopData::class.java)
        assertThat(environment.getGlobal("other").touserdata()).isNotSameAs(environment.getGlobal("shop").touserdata())
    }

    @Test
    fun `Shop setters store the Canary shop block fields`() {
        val environment = env()

        environment.loadChunk(
            """
            shop = Shop()
            id = shop:setId(2160)
            name = shop:setNameItem('crystal coin')
            count = shop:setCount(5)
            buy = shop:setBuyPrice(100)
            sell = shop:setSellPrice(50)
            key = shop:setStorageKey(7)
            value = shop:setStorageValue(3)
            """.trimIndent(),
        )

        val shop = environment.getGlobal("shop").touserdata() as ShopData
        assertThat(environment.getGlobal("id").toboolean()).isTrue()
        assertThat(environment.getGlobal("name").toboolean()).isTrue()
        assertThat(environment.getGlobal("count").toboolean()).isTrue()
        assertThat(environment.getGlobal("buy").toboolean()).isTrue()
        assertThat(environment.getGlobal("sell").toboolean()).isTrue()
        assertThat(environment.getGlobal("key").toboolean()).isTrue()
        assertThat(environment.getGlobal("value").toboolean()).isTrue()
        assertThat(shop.itemId).isEqualTo(2160)
        assertThat(shop.itemName).isEqualTo("crystal coin")
        assertThat(shop.itemSubType).isEqualTo(5)
        assertThat(shop.itemBuyPrice).isEqualTo(100)
        assertThat(shop.itemSellPrice).isEqualTo(50)
        assertThat(shop.itemStorageKey).isEqualTo(7)
        assertThat(shop.itemStorageValue).isEqualTo(3)
    }

    @Test
    fun `Shop setId rejects non numeric values`() {
        val environment = env()

        environment.loadChunk(
            """
            shop = Shop()
            ok = shop:setId(21)
            rejected = shop:setId('crystal coin')
            """.trimIndent(),
        )

        val shop = environment.getGlobal("shop").touserdata() as ShopData
        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(environment.getGlobal("rejected").isnil()).isTrue()
        assertThat(shop.itemId).isEqualTo(21)
    }

    @Test
    fun `Shop setIdFromName resolves through the item catalog`() {
        val environment = env()

        environment.loadChunk(
            """
            shop = Shop()
            ok = shop:setIdFromName('crystal coin')
            missing = shop:setIdFromName('nothing')
            """.trimIndent(),
        )

        val shop = environment.getGlobal("shop").touserdata() as ShopData
        assertThat(environment.getGlobal("ok").toboolean()).isTrue()
        assertThat(shop.itemId).isEqualTo(2160)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `Shop addChildShop stores a copy of the child block`() {
        val environment = env()

        environment.loadChunk(
            """
            parent = Shop()
            child = Shop()
            parent:setId(100)
            child:setId(200)
            added = parent:addChildShop(child)
            child:setId(300)
            """.trimIndent(),
        )

        val parent = environment.getGlobal("parent").touserdata() as ShopData
        assertThat(environment.getGlobal("added").isnil()).isTrue()
        assertThat(parent.childShop).hasSize(1)
        assertThat(parent.childShop[0].itemId).isEqualTo(200)
    }
}
