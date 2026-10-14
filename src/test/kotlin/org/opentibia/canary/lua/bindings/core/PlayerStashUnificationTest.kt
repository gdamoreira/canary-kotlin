package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Single-source-of-truth tests for the supply stash: the `Player` metatable
 * stash primitives (`PlayerFunctions` batch 3) must operate on
 * `Player::stashItems` (`Player.stash`), the same state the market escrow
 * (`Game::removeOfferItems`, game.cpp:10338) and the imbuement engine
 * (`Player::createScrollImbuement`, player.cpp:2602) read.
 *
 * The Lua surface must stay byte-identical to Canary
 * (src/lua/functions/creatures/player/player_functions.cpp).
 */
class PlayerStashUnificationTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun hero(id: Long = 42L): Player = Player(
        id = id,
        name = "Stash Hero",
        maxHealth = 150,
        health = 150,
        position = Position(10, 10, 7),
    )

    @Test
    fun `addItemStash writes through to the Player stash`() {
        val operations = WorldPlayerBatch3Operations()
        val player = hero()

        assertThat(operations.addItemStash(player, 2160, 5)).isTrue()

        assertThat(player.stash.getStashItemCount(2160)).isEqualTo(5)
    }

    @Test
    fun `two operations instances share the same Player stash`() {
        val first = WorldPlayerBatch3Operations()
        val second = WorldPlayerBatch3Operations()
        val player = hero()

        first.addItemStash(player, 2160, 5)

        assertThat(second.getStashItemCount(player, 2160)).isEqualTo(5L)

        second.addItemStash(player, 2160, 3)

        assertThat(first.getStashItemCount(player, 2160)).isEqualTo(8L)
        assertThat(first.getStashCount(player)).isEqualTo(8)
    }

    @Test
    fun `getStashItemCount and removeStashItem read the direct Player stash state`() {
        val operations = WorldPlayerBatch3Operations()
        val player = hero()
        player.stash.addItemOnStash(2160, 10)

        assertThat(operations.getStashItemCount(player, 2160)).isEqualTo(10L)
        assertThat(operations.removeStashItem(player, 2160, 4)).isTrue()
        assertThat(player.stash.getStashItemCount(2160)).isEqualTo(6)
        assertThat(operations.getStashCount(player)).isEqualTo(6)
    }

    @Test
    fun `removeStashItem refuses more than the Player stash holds`() {
        val operations = WorldPlayerBatch3Operations()
        val player = hero()
        player.stash.addItemOnStash(2160, 2)

        assertThat(operations.removeStashItem(player, 2160, 3)).isFalse()
        assertThat(player.stash.getStashItemCount(2160)).isEqualTo(2)
    }

    @Test
    fun `players keep independent stashes`() {
        val operations = WorldPlayerBatch3Operations()
        val first = hero(1L)
        val second = hero(2L)

        operations.addItemStash(first, 2160, 5)
        operations.addItemStash(second, 2160, 7)

        assertThat(first.stash.getStashItemCount(2160)).isEqualTo(5)
        assertThat(second.stash.getStashItemCount(2160)).isEqualTo(7)
        assertThat(operations.getStashCount(first)).isEqualTo(5)
        assertThat(operations.getStashCount(second)).isEqualTo(7)
    }

    @Test
    fun `the Lua stash surface stays identical while writing Player stash`() {
        val environment = env()
        PlayerFunctions.install(environment, StubPlayerOperations(), WorldPlayerBatch3Operations())
        val player = hero()
        environment.globals.set("p", environment.userdata.wrap("Player", player))

        environment.loadChunk(
            """
            added = p:addItemStash(2160, 5)
            count = p:getStashItemCount(2160)
            withdrawn = p:removeStashItem(2160, 2)
            after = p:getStashItemCount(2160)
            total = p:getStashCount()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("withdrawn").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("total").toint()).isEqualTo(3)
        assertThat(player.stash.getStashItemCount(2160)).isEqualTo(3)
    }
}
