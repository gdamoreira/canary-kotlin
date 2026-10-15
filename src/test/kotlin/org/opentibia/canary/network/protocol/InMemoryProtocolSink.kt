package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.UnpooledByteBufAllocator
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.players.PlayerFrameSink
import org.opentibia.canary.creatures.players.PlayerSendSink
import org.opentibia.canary.map.GameMap

/**
 * Test double for the [PlayerSendSink] transport that records every emitted
 * frame as a byte array instead of writing it to a channel. It delegates to a
 * recording [PlayerFrameSink], mirroring [NettyProtocolSink], so the in-memory
 * and live transports share every sender including the
 * `Player::sendCreatureAppear`/`Player::sendCreatureTurn` tile-refresh
 * fallbacks. Used by the sink unit tests and by the state-change wiring tests
 * (AGENTS.md 4: no live sockets).
 */
class InMemoryProtocolSink private constructor(
    private val recorder: FrameRecorder,
    player: Player,
    map: GameMap,
) : PlayerSendSink by PlayerFrameSink(
    player,
    map,
    frameAllocatorProvider = { UnpooledByteBufAllocator.DEFAULT },
    frameWriter = { frame -> recorder.record(frame) },
) {

    constructor(player: Player, map: GameMap = GameMap()) : this(FrameRecorder(), player, map)

    /** Every frame emitted in call order, copied out of its `ByteBuf`. */
    val frames: MutableList<ByteArray> get() = recorder.frames

    private class FrameRecorder {
        val frames = mutableListOf<ByteArray>()

        fun record(frame: ByteBuf) {
            val bytes = ByteArray(frame.readableBytes())
            frame.readBytes(bytes)
            frame.release()
            frames.add(bytes)
        }
    }
}
