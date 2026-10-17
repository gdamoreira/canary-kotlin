package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Outfit-window request (client opcode `0xD2`), mirroring
 * `Game::playerRequestOutfit` (game.cpp:7206-7216): Canary reads no payload,
 * gates on `ALLOW_CHANGEOUTFIT` and calls `Player::sendOutfitWindow`, which the
 * session sink composes through `OutfitWindowComposer`.
 */
object OutfitWindowRequestCommand

/**
 * Decoder for the outfit-window client opcode.
 *
 *  - `0xD2` `Game::playerRequestOutfit` (game.cpp:7206)
 */
object OutfitRequestPacketHandler {

    const val OPCODE_PLAYER_REQUEST_OUTFIT: Int = 0xD2

    fun parse(opcode: Int, buffer: ByteBuf): OutfitWindowRequestCommand? {
        if (opcode != OPCODE_PLAYER_REQUEST_OUTFIT) {
            return null
        }
        return OutfitWindowRequestCommand
    }
}
