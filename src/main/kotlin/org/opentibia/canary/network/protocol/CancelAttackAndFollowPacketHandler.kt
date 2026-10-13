package org.opentibia.canary.network.protocol

/**
 * `0xBE` `Game::playerCancelAttackAndFollow` (src/game/game.cpp:6990-6997);
 * Canary reads no payload (`protocolgame.cpp:1971-1973`).
 */
object CancelAttackAndFollowCommand

/** Decoder for the payload-less cancel attack/follow opcode `0xBE`. */
object CancelAttackAndFollowPacketHandler {

    /** protocolgame.cpp:1971-1973 dispatches this opcode to `playerCancelAttackAndFollow`. */
    const val OPCODE_CANCEL_ATTACK_AND_FOLLOW: Int = 0xBE

    fun parse(): CancelAttackAndFollowCommand = CancelAttackAndFollowCommand
}
