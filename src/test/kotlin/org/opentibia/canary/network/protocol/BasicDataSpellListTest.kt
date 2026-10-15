package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.channel.embedded.EmbeddedChannel
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.lua.bindings.SpellBindings
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.misc.MiscDataWriter

/**
 * Wave 67 D4 transcript test for the `ProtocolGame::sendBasicData`
 * (protocolgame.cpp:5823-5890) spell list: the session sink must fill the
 * `g_spells().getSpellsByVocation(vocationId)` entries the datapack scripts
 * registered instead of the empty seam default.
 */
class BasicDataSpellListTest {

    @Test
    fun `sendBasicData lists the registered vocation spells`() {
        LuaEnvironment().apply { initState() }.loadChunk(
            """
            local spell = Spell("instant")
            spell:name("Ice Strike")
            spell:id(3)
            spell:words("exori frigo")
            spell:level(1)
            spell:vocation("sorcerer;true")
            function spell.onCastSpell(creature, variant) return true end
            spell:register()
            """.trimIndent(),
        )
        assertThat(SpellBindings.recordRegisteredSpells()).isEqualTo(1)

        val channel = EmbeddedChannel()
        try {
            val player = Player(id = 1L, name = "Hero", maxHealth = 100, health = 100, position = Position(100, 100, 7))
            player.level = 8
            val sink = NettyProtocolSink(channel, player, GameMap())

            sink.sendBasicData()

            val frame = channel.readOutbound<ByteBuf>()
            assertThat(frame.readUnsignedByte().toInt()).isEqualTo(MiscDataWriter.PACKET_BASIC_DATA)
            frame.readUnsignedByte() // premium byte
            frame.readUnsignedIntLE() // premium expiry
            frame.readUnsignedByte() // vocation client id
            frame.readUnsignedByte() // prey window unlocked
            assertThat(frame.readUnsignedShortLE()).isEqualTo(1)
            assertThat(frame.readUnsignedShortLE()).isEqualTo(3)
            frame.readUnsignedByte() // magic shield
            assertThat(frame.isReadable).isFalse()
            frame.release()
        } finally {
            channel.finishAndReleaseAll()
        }
    }
}
