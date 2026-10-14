package org.opentibia.canary.creatures.players.animus_mastery

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream

/**
 * Save/load seam for the `players.animus_mastery` blob column.
 *
 * The codec itself lives on [AnimusMastery] (`serialize`/`unserialize`,
 * animus_mastery.cpp:62-73); this seam is the call site pair the
 * `IOLoginDataLoad`/`IOLoginDataSave` path uses, so the composed
 * `Player::animusMastery()` (player.cpp:12812) round-trips through the same
 * blob Canary persists.
 *
 * the io package wiring needs (owned outside this wave):
 *  - load: `IOLoginDataLoad::loadPlayerAnimusMastery`
 *    (iologindata_load_player.cpp:277-289) must call [loadPlayerAnimusMastery]
 *    with the `players.animus_mastery` column bytes right after
 *    `IOLoginDataLoad::loadPlayerConditions` (iologindata.cpp:117-119), or
 *    bind it through the `IOLoginDataSubloads`-style seam.
 *  - save: the inline block of `IOLoginDataSave::savePlayerFirst`
 *    (iologindata_save_player.cpp:293-299) must write
 *    [savePlayerAnimusMastery]'s bytes into the same column.
 *
 * PARITY NOTE: the port's wave-36 `player_animus_mastery` relational table
 * (`IOBestiaryPersistence.loadAnimusMastery`/`saveAnimusMastery`, V13) is an
 * interim store; Canary keeps the single `players.animus_mastery` blob, so the
 * io call sites above remain the parity path.
 */
class PlayerAnimusMasteryPersistence {

    /**
     * `IOLoginDataLoad::loadPlayerAnimusMastery`
     * (iologindata_load_player.cpp:277-289): the column blob is decoded through
     * `AnimusMastery::unserialize` (animus_mastery.cpp:68-73), which appends to
     * the composed masteries without clearing them.
     */
    fun loadPlayerAnimusMastery(player: Player, blob: ByteArray) {
        player.features.animusMastery().unserialize(PropStream(blob))
    }

    /**
     * Transcription of the inline `players.animus_mastery` write of
     * `IOLoginDataSave::savePlayerFirst` (iologindata_save_player.cpp:293-299):
     * a fresh `PropWriteStream` is filled by `AnimusMastery::serialize`
     * (animus_mastery.cpp:62-66) and its bytes are returned for the column
     * write. Canary has no extracted function for this half; the port names
     * the seam after the persisted column.
     */
    fun savePlayerAnimusMastery(player: Player): ByteArray {
        val stream = PropWriteStream()
        player.features.animusMastery().serialize(stream)
        return stream.getStream()
    }
}
