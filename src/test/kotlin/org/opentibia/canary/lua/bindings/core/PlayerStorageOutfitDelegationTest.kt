package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldPlayerOperations
import org.opentibia.canary.map.Position

/**
 * Wave 58 A2: the `player:getStorageValue`/`setStorageValue` and
 * `player:addOutfit`/`removeOutfit`/`removeOutfitAddon` bindings must delegate
 * to the composed `PlayerFeatures.storage`/`PlayerFeatures.outfits` state
 * (player.hpp:1968, 1686) instead of keeping their own per-player shadow maps.
 * The host accessors (`Player::storage()`/`getOutfits()`) are the read side of
 * the same instances, so a Lua write and a host read must agree.
 *
 * Canary source of truth: `/Users/damoreira/.projects/canary`
 *  - src/lua/functions/creatures/player/player_functions.cpp:2296-2334
 *    (`luaPlayerGetStorageValue`/`luaPlayerSetStorageValue`)
 *  - src/lua/functions/creatures/player/player_functions.cpp:2922-2997
 *    (`luaPlayerAddOutfit`/`...AddOutfitAddon`/`...RemoveOutfit`/
 *     `...RemoveOutfitAddon`/`...HasOutfit`)
 *  - src/creatures/players/player.cpp:1387-1396
 *    (`Player::addStorageValue`/`getStorageValue`)
 *  - src/creatures/players/components/player_storage.cpp:56-93
 */
class PlayerStorageOutfitDelegationTest {

    private fun environment(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun newPlayer(id: Long = 42L) = Player(
        id = id,
        name = "Composed Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    private fun installedPlayer(): Pair<LuaEnvironment, Player> {
        val environment = environment()
        PlayerFunctions.install(environment, WorldPlayerOperations())
        val player = newPlayer()
        environment.globals.set("p", environment.userdata.wrap("Player", player))
        return environment to player
    }

    @Test
    fun `Lua setStorageValue writes the composed storage the host reads`() {
        val (environment, player) = installedPlayer()

        environment.loadChunk("stored = p:setStorageValue(1000, 42)")

        assertThat(environment.getGlobal("stored").toboolean()).isTrue()
        assertThat(player.storage().get(1000)).isEqualTo(42)
        assertThat(player.features.storage.has(1000)).isTrue()
    }

    @Test
    fun `Lua getStorageValue reads the composed storage the host writes`() {
        val (environment, player) = installedPlayer()
        player.features.storage.add(2000, 7)

        environment.loadChunk("value = p:getStorageValue(2000)")
        environment.loadChunk("missing = p:getStorageValue(2001)")

        assertThat(environment.getGlobal("value").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("missing").toint()).isEqualTo(-1)
    }

    @Test
    fun `Lua setStorageValue with -1 removes the composed key`() {
        val (environment, player) = installedPlayer()
        player.features.storage.add(3000, 9)

        environment.loadChunk("cleared = p:setStorageValue(3000, -1)")

        assertThat(environment.getGlobal("cleared").toboolean()).isTrue()
        assertThat(player.features.storage.has(3000)).isFalse()
    }

    @Test
    fun `Lua setStorageValue rejects key zero like the C++ binding`() {
        val (environment, player) = installedPlayer()

        environment.loadChunk("rejected = p:setStorageValue(0, 42)")

        assertThat(environment.getGlobal("rejected").toboolean()).isFalse()
        assertThat(player.features.storage.has(0)).isFalse()
    }

    @Test
    fun `Lua setStorageValue rejects reserved-range keys like the C++ binding`() {
        val (environment, player) = installedPlayer()
        val reservedKey = PlayerStorage.PSTRG_OUTFITS_RANGE_START + 1

        environment.loadChunk("rejected = p:setStorageValue($reservedKey, 42)")

        assertThat(environment.getGlobal("rejected").toboolean()).isFalse()
        assertThat(player.getOutfits()).isEmpty()
        assertThat(player.features.storage.has(reservedKey)).isFalse()
    }

    @Test
    fun `Lua addOutfit and removeOutfit write the composed outfit state the host reads`() {
        val (environment, player) = installedPlayer()

        environment.loadChunk("added = p:addOutfit(136, 3)")
        environment.loadChunk("ownedOutfits = p:addOutfit(137, 0)")
        environment.loadChunk("removed = p:removeOutfit(137)")

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("ownedOutfits").toboolean()).isTrue()
        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(player.getOutfits()).containsExactly(OutfitEntry(136, 3))
    }

    @Test
    fun `Lua addOutfitAddon merges into the composed outfit entry the host writes`() {
        val (environment, player) = installedPlayer()
        player.features.outfits.addOutfit(136, 1)

        environment.loadChunk("added = p:addOutfitAddon(136, 2)")

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(player.getOutfits()).containsExactly(OutfitEntry(136, 3))
    }

    @Test
    fun `Lua removeOutfitAddon clears the composed addon`() {
        val (environment, player) = installedPlayer()
        player.features.outfits.addOutfit(136, 3)

        environment.loadChunk("removed = p:removeOutfitAddon(136, 1)")

        assertThat(environment.getGlobal("removed").toboolean()).isTrue()
        assertThat(player.getOutfits()).containsExactly(OutfitEntry(136, 2))
    }

    @Test
    fun `Lua hasOutfit answers from the composed outfit state`() {
        val (environment, player) = installedPlayer()
        player.group = Group(access = true)

        assertThat(player.features.outfits.canWear(136, 0)).isTrue()

        environment.loadChunk("has = p:hasOutfit(136, 0)")

        assertThat(environment.getGlobal("has").toboolean()).isTrue()
    }
}
