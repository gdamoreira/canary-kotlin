package org.opentibia.canary.network.protocol.livestream

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.livestream.CanWatchResult
import org.opentibia.canary.creatures.players.livestream.LivestreamClient
import org.opentibia.canary.creatures.players.livestream.LivestreamManager

/**
 * `ProtocolGame::castViewerLogin` (src/server/network/protocol/protocolgame.cpp:12380-12403),
 * the `@livestream` account-descriptor entry of `ProtocolGame::onRecvFirstMessage`
 * (protocolgame.cpp:1409-1417).
 *
 * The C++ method sends the login preamble, resolves the caster through
 * `g_game().getPlayerByName`, gates on `g_livestream().canWatch`, then sets
 * `m_isLivestreamViewer`/`player`/`acceptPackets` and runs
 * `sendLivestreamViewerAppear`. The port keeps the session state (the viewer
 * flag, the caster as the session `player`, the accept-packets state) with the
 * caller ([org.opentibia.canary.network.protocol.GameHandler]) and returns the
 * folded gate result; [sink] is the viewer session transport.
 *
 * PARITY GAPS:
 *  - `sendClientLoginPreamble` (protocolgame.cpp:12362-12378) is not ported:
 *    the OTCv8 feature sends (`sendFeatures`/`sendOTCRFeatures`) and the
 *    `CLIENTOS_OTCLIENT_*` magic frame have no Kotlin transport; an OTC viewer
 *    login behaves like a non-OTC one.
 *  - `OutputMessagePool::addProtocolToAutosend` (protocolgame.cpp:12392) has
 *    no port registry; the broadcast fanout is not wired.
 */
object LivestreamViewerLogin {

    /** The `@livestream` account descriptor (protocolgame.cpp:1411). */
    const val ACCOUNT_DESCRIPTOR: String = "@livestream"

    /**
     * Folded [LivestreamManager.canWatch] result: `accepted` mirrors
     * `canWatchLivestream`'s bool and `reason` the `disconnectClient(reason)`
     * argument (`CanWatchResult` folds the C++ `std::string &reason`
     * out-parameter).
     */
    data class ViewerLoginResult(
        val accepted: Boolean,
        val reason: String = "",
    )

    /**
     * Port of `castViewerLogin` (protocolgame.cpp:12380-12393) and
     * `canWatchLivestream` (protocolgame.cpp:12395-12403): resolves the caster,
     * runs the gate, and on success registers the viewer and drives
     * [LivestreamViewerWriter.writeLivestreamViewerAppear]. On rejection the
     * caller disconnects with [ViewerLoginResult.reason] like the C++
     * `disconnectClient(reason)`.
     */
    fun castViewerLogin(
        /** `g_game().getPlayerByName(name)` (game.cpp:1394). */
        playerByName: (String) -> Player?,
        /** `g_livestream()` (livestream.hpp:35-95); `null` = the singleton's absent state. */
        livestream: LivestreamManager?,
        /** `getThis()`: the viewer session (its `getIP`/send surface). */
        viewerClient: LivestreamClient,
        casterName: String,
        password: String,
        /** `ProtocolGame::oldProtocol` (protocolgame.hpp:640) session flag. */
        oldProtocol: Boolean,
        /** The viewer session transport (`sendLivestreamViewerAppear`'s `this`). */
        sink: LivestreamViewerWriter.LivestreamViewerAppearSink,
    ): ViewerLoginResult {
        val caster = playerByName(casterName)
        val manager = livestream
        val canWatch = manager?.canWatch(caster, viewerClient, password)
            ?: CanWatchResult(allowed = false, reason = "Livestream unavailable.")
        if (manager == null || caster == null || !canWatch.allowed) {
            return ViewerLoginResult(false, canWatch.reason.ifEmpty { "Livestream unavailable." })
        }

        // C++ order: `g_livestream().addViewer(player, getThis())` inside
        // `sendLivestreamViewerAppear` precedes the viewer-count read
        // (protocolgame.cpp:12417-12419), so the count includes this viewer.
        manager.addViewer(caster, viewerClient)
        LivestreamViewerWriter.writeLivestreamViewerAppear(
            sink = sink,
            caster = caster,
            viewerCount = manager.getViewerCount(caster),
            broadcastTimeString = manager.getBroadcastTimeString(caster),
            description = manager.getDescription(caster),
            oldProtocol = oldProtocol,
        )
        return ViewerLoginResult(true)
    }
}
