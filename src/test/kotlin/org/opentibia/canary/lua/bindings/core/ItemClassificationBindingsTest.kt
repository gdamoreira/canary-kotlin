package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.systems.forge.ItemClassifications
import org.opentibia.canary.systems.forge.TierInfo

/**
 * Transcript tests for the classification Lua bindings, mirroring:
 *
 *  - `GameFunctions::luaGameCreateItemClassification`
 *    (src/lua/functions/core/game/game_functions.cpp:739-749)
 *  - `ItemClassificationFunctions::luaItemClassificationAddTier`
 *    (src/lua/functions/items/item_classification_functions.cpp:38-55)
 *  - the `ItemClassification.register` helper of
 *    data/scripts/lib/register_item_tier.lua consumed by
 *    data/scripts/systems/item_tiers.lua
 *
 * Source of truth: ../.projects/canary
 */
class ItemClassificationBindingsTest {

    private fun environment(registry: ItemClassifications): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { ItemClassificationFunctions.install(it, registry) }

    @Test
    fun `Game createItemClassification registers a classification and wraps it as userdata`() {
        val registry = ItemClassifications()

        val environment = environment(registry)
        environment.loadChunk("classification = Game.createItemClassification(4)")

        assertThat(environment.getGlobal("classification").isuserdata()).isTrue()
        assertThat(registry.getClassification(4)).isNotNull()
        assertThat(registry.getClassification(4)!!.id).isEqualTo(4)
    }

    @Test
    fun `Game createItemClassification returns the existing classification on a repeated id`() {
        val registry = ItemClassifications()

        val environment = environment(registry)
        environment.loadChunk("first = Game.createItemClassification(4)")
        environment.loadChunk("second = Game.createItemClassification(4)")

        assertThat(registry.getAll().count { it.id == 4 }).isEqualTo(1)
    }

    @Test
    fun `ItemClassification addTier stores the Canary price columns`() {
        val registry = ItemClassifications()

        val environment = environment(registry)
        environment.loadChunk(
            """
            classification = Game.createItemClassification(4)
            classification:addTier(2, 2, 20000000, 110000000, 165000000)
            """.trimIndent(),
        )

        assertThat(registry.getClassification(4)!!.tiers[2]).isEqualTo(
            TierInfo(
                corePrice = 2,
                regularPrice = 20_000_000,
                convergenceFusionPrice = 110_000_000,
                convergenceTransferPrice = 165_000_000,
            ),
        )
    }

    @Test
    fun `the register lib populates the classification through addTier like item tiers lua`() {
        val registry = ItemClassifications()

        val environment = environment(registry)
        environment.loadChunk(
            """
            ItemClassification.register = function(self, mask)
                for _, value in ipairs(mask.Upgrades) do
                    self:addTier(value.TierId, value.Core, value.RegularPrice, value.ConvergenceFustionPrice, value.ConvergenceTransferPrice)
                end
            end
            local classification = Game.createItemClassification(1)
            classification:register({ Upgrades = {
                { TierId = 1, Core = 1, RegularPrice = 25000, ConvergenceFustionPrice = 0, ConvergenceTransferPrice = 0 },
            } })
            """.trimIndent(),
        )

        assertThat(registry.getClassification(1)!!.tiers[1]).isEqualTo(TierInfo(1, 25_000, 0, 0))
    }
}
