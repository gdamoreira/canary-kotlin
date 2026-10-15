package org.opentibia.canary.network.protocol.livestream

import io.netty.buffer.ByteBuf
import io.netty.channel.Channel
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.condition.PlayerIcon as ConditionPlayerIcon
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.PlayerSendSink
import org.opentibia.canary.creatures.players.livestream.IconBakragore as LivestreamIconBakragore
import org.opentibia.canary.creatures.players.livestream.LivestreamClient
import org.opentibia.canary.items.Container
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MapPacketWriter
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.NettyProtocolSink
import org.opentibia.canary.network.protocol.SelfAppearTail
import org.opentibia.canary.network.protocol.player.ChatWindowWriter
import org.opentibia.canary.network.protocol.protocolCanSeePosition

/**
 * The viewer `ProtocolGame` transport of `ProtocolGame::castViewerLogin`
 * (src/server/network/protocol/protocolgame.cpp:12380-12434).
 *
 * After `castViewerLogin` sets `player = foundPlayer` (protocolgame.cpp:12389),
 * the session's `player` is the caster while this transport is the session's
 * own `ProtocolGame` (`getThis()`): `sendAddCreature` writes the caster self
 * appear through the viewer channel, `sendLivestreamViewerAppear`'s container,
 * text, icon and channel sends go through the same channel, and the viewer
 * registers with `g_livestream()` as itself ([LivestreamClient]).
 *
 * The [PlayerSendSink] surface (and therefore the 0xCA
 * `resendLivestreamViewerContainer` sends, protocolgame.cpp:12448-12462) is
 * delegated to a [NettyProtocolSink] bound to the caster, mirroring the C++
 * `foundPlayer->client = getThis()` swap around `sendAddCreature`
 * (protocolgame.cpp:12410-12414): the frames serialize for the caster but leave
 * on the viewer's channel.
 *
 * PARITY GAPS:
 *  - `sendAddCreature`'s self-branch tail after the map description (inventory
 *    items, stats, skills, world light, weapon proficiency, blessings, VIP
 *    burst, basic data, houses, client check/game news/icons, open player
 *    containers, spell cooldowns; protocolgame.cpp:8696-8810) is not re-emitted
 *    for viewer sessions — the port's login tail lives in
 *    `GameHandler.sendLoginSuccess` and sends through the owner's transport.
 *  - The C++ temporary `foundPlayer->client` swap is not reproduced; the port's
 *    appear sequence is fully emitted through this transport, so no
 *    `Player::send*` world call happens inside it.
 */
class LivestreamViewerTransport private constructor(
    private val channel: Channel,
    /** `ProtocolGame::player` (protocolgame.hpp:621): the watched caster. */
    val caster: Player,
    private val map: GameMap,
    private val delegate: NettyProtocolSink,
) : PlayerSendSink by delegate,
    LivestreamViewerWriter.LivestreamViewerAppearSink,
    LivestreamClient {

    /** `ProtocolGame::sendCreatureSay`'s function-local `static uint32_t statementId` (protocolgame.cpp:8010). */
    private var statementId: Long = 0L

    constructor(
        channel: Channel,
        caster: Player,
        map: GameMap,
        /** `g_game().getCreatureByID` behind the known-set eviction (protocolgame.cpp:2346). */
        creatureById: (Long) -> Creature? = { null },
        /** The viewer session's negotiated self appear tail. */
        selfAppearTail: SelfAppearTail = SelfAppearTail(),
        /** `IOLoginData::getNameByGuid` behind the login-mode 0xCA whitelist. */
        playerNameByGuid: (Long) -> String = { "" },
        /** `g_game().getGuild(id, true)->getName()` behind the login-mode 0xCA whitelist. */
        guildNameById: (Long) -> String = { "" },
    ) : this(
        channel,
        caster,
        map,
        NettyProtocolSink(
            channel = channel,
            player = caster,
            map = map,
            creatureById = creatureById,
            selfAppearTail = selfAppearTail,
            playerNameByGuid = playerNameByGuid,
            guildNameById = guildNameById,
        ),
    )

    /** Queues one frame on the viewer channel (fire-and-forget; a closed channel releases it). */
    private inline fun emitViewerFrame(build: ByteBuf.() -> Unit) {
        val frame = channel.alloc().buffer()
        frame.build()
        if (channel.isActive) {
            channel.writeAndFlush(frame)
        } else {
            frame.release()
        }
    }

    /**
     * `ProtocolGame::sendAddCreature(player, player->getPosition(), 0, true)`
     * (protocolgame.cpp:12413) resolving the `creature == player` self branch
     * (protocolgame.cpp:8639-8694): the 0x17 self appear frame for the caster
     * plus the map description. The `canSee(pos)` guard matches the C++ entry.
     */
    override fun sendAddCreature(position: Position, stackpos: Int, isLogin: Boolean) {
        if (!protocolCanSeePosition(caster, position.x, position.y, position.z)) {
            return
        }

        delegate.sendCreatureAppear(caster, position, isLogin)
        emitViewerFrame { MapPacketWriter.writeMapDescription(this, map, position) }
    }

    /** `ProtocolGame::sendContainer` (protocolgame.cpp:12443) through the viewer channel. */
    override fun sendContainer(cid: Int, container: Container, hasParent: Boolean, firstIndex: Int) {
        delegate.sendContainer(cid, container, hasParent, firstIndex)
    }

    /** `ProtocolGame::sendTextMessage(TextMessage)` (protocolgame.cpp:12420, 12433) through the viewer channel. */
    override fun sendTextMessage(type: MessageClass, text: String) {
        delegate.sendTextMessage(type, text)
    }

    /**
     * `ProtocolGame::sendCreatureSay(creature, TALKTYPE_SAY, description)`
     * (protocolgame.cpp:12424) with the caster as the speaker: the
     * `canSee(creature)` guard then the current-profile 0xAA frame.
     */
    override fun sendCreatureSay(type: SpeakClasses, text: String) {
        if (!protocolCanSeePosition(caster, caster.position.x, caster.position.y, caster.position.z)) {
            return
        }

        statementId += 1
        emitViewerFrame {
            ChatWindowWriter.writeCreatureSay(
                this,
                statementId,
                caster.name,
                caster.level,
                type,
                caster.position,
                text,
            )
        }
    }

    /** `ProtocolGame::sendIcons(iconSet, IconBakragore)` (protocolgame.cpp:12429) through the viewer channel. */
    override fun sendIcons(icons: Set<ConditionPlayerIcon>, bakragore: LivestreamIconBakragore) {
        delegate.sendIcons(icons, bakragore)
    }

    /** `ProtocolGame::sendChannel(CHANNEL_LIVESTREAM, "Livestream", nullptr, nullptr)` (protocolgame.cpp:12432). */
    override fun sendChannel(channelId: Int, channelName: String) {
        delegate.sendChannel(channelId, channelName)
    }
}
