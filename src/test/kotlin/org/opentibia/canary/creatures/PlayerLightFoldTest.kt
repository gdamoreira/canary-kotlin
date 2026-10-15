package org.opentibia.canary.creatures

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.buffer.UnpooledByteBufAllocator
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.ProtocolFrameSink

/**
 * Tests for the `Player::getCreatureLight` fold
 * (src/creatures/players/player.cpp:6277-6282): the light sent to clients is
 * the stronger of `Creature::internalLight` (creature.hpp:922) and the
 * composed `Player::itemsLight` (player.hpp:1712, player.cpp:6284-6305).
 */
class PlayerLightFoldTest {

    private fun hero(): Player = Player(
        id = 1L,
        name = "Hero",
        maxHealth = 150,
        health = 100,
        maxMana = 50,
        mana = 50,
        position = Position(1000, 1000, 7),
    )

    private fun lamp(level: Int, color: Int): Item =
        Item(2159, 1, ItemType(id = 2159, name = "lamp", lightLevel = level, lightColor = color))

    /** Resolves the `AddCreature` input the appearance senders serialize. */
    private class AppearanceProbe(player: Player, map: GameMap) : ProtocolFrameSink(player, map) {
        override fun frameAllocator(): ByteBufAllocator = UnpooledByteBufAllocator.DEFAULT

        override fun writeFrame(frame: ByteBuf) {
            frame.release()
        }

        fun lightOf(creature: Creature): LightInfo = appearanceOf(creature).light
    }

    @Test
    fun `getCreatureLight folds an equipped item light over the neutral internal light`() {
        val hero = hero()
        hero.internalAddThing(Slots_t.CONST_SLOT_HEAD.value, lamp(level = 8, color = 200))

        assertThat(hero.getCreatureLight()).isEqualTo(LightInfo(level = 8, color = 200))
    }

    @Test
    fun `getCreatureLight keeps the strictly stronger internal light`() {
        val hero = hero()
        hero.internalAddThing(Slots_t.CONST_SLOT_HEAD.value, lamp(level = 8, color = 200))
        hero.setCreatureLight(LightInfo(level = 12, color = 44))

        assertThat(hero.getCreatureLight()).isEqualTo(LightInfo(level = 12, color = 44))
    }

    @Test
    fun `the appearance sent for a player carries the folded items light`() {
        val observer = hero()
        val target = hero()
        target.internalAddThing(Slots_t.CONST_SLOT_HEAD.value, lamp(level = 8, color = 200))
        val probe = AppearanceProbe(observer, GameMap())

        assertThat(probe.lightOf(target)).isEqualTo(LightInfo(level = 8, color = 200))
    }

    @Test
    fun `the appearance sent for a player keeps an internal-only light`() {
        val observer = hero()
        val target = hero()
        target.setCreatureLight(LightInfo(level = 6, color = 9))
        val probe = AppearanceProbe(observer, GameMap())

        assertThat(probe.lightOf(target)).isEqualTo(LightInfo(level = 6, color = 9))
    }
}
