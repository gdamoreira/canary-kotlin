package org.opentibia.canary.network.protocol.livestream

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.condition.PlayerIcon
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.livestream.CHANNEL_LIVESTREAM
import org.opentibia.canary.creatures.players.livestream.IconBakragore
import org.opentibia.canary.items.Container
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.livestream.LivestreamViewerWriter.LivestreamViewerAppearSink

class LivestreamViewerWriterTest {

    private sealed interface Call {
        data class AddCreature(val position: Position, val stackpos: Int, val isLogin: Boolean) : Call
        data class SendContainer(val cid: Int, val container: Container, val hasParent: Boolean, val firstIndex: Int) : Call
        data class Text(val type: MessageClass, val text: String) : Call
        data class Say(val type: SpeakClasses, val text: String) : Call
        data class Icons(val icons: Set<PlayerIcon>, val bakragore: IconBakragore) : Call
        data class Channel(val channelId: Int, val name: String) : Call
    }

    private class RecordingSink : LivestreamViewerAppearSink {
        val calls = mutableListOf<Call>()

        override fun sendAddCreature(position: Position, stackpos: Int, isLogin: Boolean) {
            calls.add(Call.AddCreature(position, stackpos, isLogin))
        }

        override fun sendContainer(cid: Int, container: Container, hasParent: Boolean, firstIndex: Int) {
            calls.add(Call.SendContainer(cid, container, hasParent, firstIndex))
        }

        override fun sendTextMessage(type: MessageClass, text: String) {
            calls.add(Call.Text(type, text))
        }

        override fun sendCreatureSay(type: SpeakClasses, text: String) {
            calls.add(Call.Say(type, text))
        }

        override fun sendIcons(icons: Set<PlayerIcon>, bakragore: IconBakragore) {
            calls.add(Call.Icons(icons, bakragore))
        }

        override fun sendChannel(channelId: Int, name: String) {
            calls.add(Call.Channel(channelId, name))
        }
    }

    private fun player(name: String = "Caster"): Player = Player(
        id = 42L,
        name = name,
        maxHealth = 100,
        health = 100,
        position = Position(100, 200, 7),
    )

    @Test
    fun `appears the viewer with the add creature containers messages icons and channel`() {
        val sink = RecordingSink()
        val caster = player()
        val bag = Container(id = 1987, capacity = 8)
        caster.addContainer(3, bag)
        caster.setContainerIndex(3, 5)

        LivestreamViewerWriter.writeLivestreamViewerAppear(
            sink,
            caster = caster,
            viewerCount = 7,
            broadcastTimeString = "00:01:02",
            description = "come watch",
            oldProtocol = false,
        )

        assertThat(sink.calls).containsExactly(
            Call.AddCreature(Position(100, 200, 7), 0, true),
            Call.SendContainer(3, bag, bag.hasParent(), 5),
            Call.Text(MessageClass.MESSAGE_LOOK, "Caster is broadcasting for 7 people.\nLivestream time: 00:01:02"),
            Call.Say(SpeakClasses.TALKTYPE_SAY, "come watch"),
            Call.Icons(setOf(PlayerIcon.Rooted), IconBakragore.None),
            Call.Channel(CHANNEL_LIVESTREAM, "Livestream"),
            Call.Text(MessageClass.MESSAGE_EVENT_ADVANCE, "Available commands: \n/name newname\n/show"),
        )
    }

    @Test
    fun `omits the description and icons for the old protocol`() {
        val sink = RecordingSink()

        LivestreamViewerWriter.writeLivestreamViewerAppear(
            sink,
            caster = player(),
            viewerCount = 0,
            broadcastTimeString = "00:00:00",
            description = "",
            oldProtocol = true,
        )

        assertThat(sink.calls).containsExactly(
            Call.AddCreature(Position(100, 200, 7), 0, true),
            Call.Text(MessageClass.MESSAGE_LOOK, "Caster is broadcasting for 0 people.\nLivestream time: 00:00:00"),
            Call.Channel(CHANNEL_LIVESTREAM, "Livestream"),
            Call.Text(MessageClass.MESSAGE_EVENT_ADVANCE, "Available commands: \n/name newname\n/show"),
        )
    }
}
