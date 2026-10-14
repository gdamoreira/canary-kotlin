package org.opentibia.canary.network.protocol.livestream

import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.condition.PlayerIcon
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.livestream.CHANNEL_LIVESTREAM
import org.opentibia.canary.creatures.players.livestream.IconBakragore
import org.opentibia.canary.items.Container
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass

/**
 * `ProtocolGame::sendLivestreamViewerAppear` orchestration
 * (src/server/network/protocol/protocolgame.cpp:12405-12434).
 *
 * The C++ method composes already-ported senders, so the port keeps the exact
 * call order and delegates each frame to a [LivestreamViewerAppearSink] whose
 * methods are named after the Canary `ProtocolGame` senders they stand in for:
 * `sendAddCreature` (with `isLogin = true`), the `syncLivestreamViewerOpenContainers`
 * `sendContainer` loop, the `MESSAGE_LOOK` broadcast message,
 * `sendCreatureSay(TALKTYPE_SAY)` for a non-empty description, `sendIcons`
 * (`{PlayerIcon::Rooted}`, `IconBakragore::None`) for the current profile,
 * `sendChannel(CHANNEL_LIVESTREAM, "Livestream")` and the final
 * `MESSAGE_EVENT_ADVANCE` command list. The `player` pointer swap,
 * `m_isLivestreamViewer`/`acceptPackets` state and
 * `g_livestream().addViewer(...)` bookkeeping stay with the caller.
 */
object LivestreamViewerWriter {

    /** `CHANNEL_LIVESTREAM` (`src/utils/const.hpp:22`). */
    const val CHANNEL_LIVESTREAM_ID: Int = CHANNEL_LIVESTREAM

    /** The `ProtocolGame` senders `sendLivestreamViewerAppear` composes. */
    interface LivestreamViewerAppearSink {
        /** `ProtocolGame::sendAddCreature(player, pos, 0, true)` (protocolgame.cpp:12413). */
        fun sendAddCreature(position: Position, stackpos: Int, isLogin: Boolean)

        /** `ProtocolGame::sendContainer` (protocolgame.cpp:12443). */
        fun sendContainer(cid: Int, container: Container, hasParent: Boolean, firstIndex: Int)

        /** `ProtocolGame::sendTextMessage(TextMessage)` (protocolgame.cpp:12420, 12433). */
        fun sendTextMessage(type: MessageClass, text: String)

        /** `ProtocolGame::sendCreatureSay(player, TALKTYPE_SAY, description)` (protocolgame.cpp:12424). */
        fun sendCreatureSay(type: SpeakClasses, text: String)

        /** `ProtocolGame::sendIcons(iconSet, IconBakragore)` (protocolgame.cpp:12429). */
        fun sendIcons(icons: Set<PlayerIcon>, bakragore: IconBakragore)

        /** `ProtocolGame::sendChannel(CHANNEL_LIVESTREAM, "Livestream", nullptr, nullptr)` (protocolgame.cpp:12432). */
        fun sendChannel(channelId: Int, name: String)
    }

    /** `sendLivestreamViewerAppear` (protocolgame.cpp:12405-12434). */
    fun writeLivestreamViewerAppear(
        sink: LivestreamViewerAppearSink,
        caster: Player,
        viewerCount: Int,
        broadcastTimeString: String,
        description: String,
        oldProtocol: Boolean,
    ) {
        sink.sendAddCreature(caster.position, 0, true)

        for ((cid, openContainer) in caster.getOpenContainers()) {
            val container = openContainer.container ?: continue
            sink.sendContainer(cid, container, container.hasParent(), openContainer.index)
        }

        sink.sendTextMessage(
            MessageClass.MESSAGE_LOOK,
            "${caster.name} is broadcasting for $viewerCount people.\nLivestream time: $broadcastTimeString",
        )

        if (description.isNotEmpty()) {
            sink.sendCreatureSay(SpeakClasses.TALKTYPE_SAY, description)
        }

        if (!oldProtocol) {
            sink.sendIcons(setOf(PlayerIcon.Rooted), IconBakragore.None)
        }

        sink.sendChannel(CHANNEL_LIVESTREAM, "Livestream")
        sink.sendTextMessage(MessageClass.MESSAGE_EVENT_ADVANCE, "Available commands: \n/name newname\n/show")
    }
}
