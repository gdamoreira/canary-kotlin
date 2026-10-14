package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFeatures
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.map.Position

/**
 * Wave 57 A4: `Player::m_storage` (player.hpp:1968) is composed on
 * [PlayerFeatures] and must be backed by the same outfit/familiar state the
 * player owns, exactly like Canary's `friend class PlayerStorage`
 * (player.hpp:1956) reaching `m_player.outfits`/`m_player.familiars`
 * (player_storage.cpp:59-66).
 *
 * Canary source of truth: `../.projects/canary`
 *  - src/creatures/players/components/player_storage.hpp:35-185
 *  - src/creatures/players/components/player_storage.cpp:27-161
 *  - src/creatures/players/player.hpp:1541-1542, 1686-1687, 1956-1968
 *  - src/creatures/players/player.cpp:1390-1396
 */
class PlayerStorageCompositionTest {

    private fun newPlayer() = Player(
        id = 42,
        name = "Storage Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `one storage instance is composed per player`() {
        val player = newPlayer()

        assertThat(player.features.storage).isSameAs(player.features.storage)
    }

    @Test
    fun `the composed storage appends familiar reserved keys to the composed familiars`() {
        val player = newPlayer()
        val key = PlayerStorage.PSTRG_FAMILIARS_RANGE_START + 1

        player.features.storage.add(key, 77 shl 16, true)

        assertThat(player.features.familiars.familiars.map { it.lookType }).containsExactly(77)
        assertThat(player.features.storage.has(key)).isFalse()
    }

    @Test
    fun `the composed storage appends outfit reserved keys to the composed outfits`() {
        val player = newPlayer()
        val key = PlayerStorage.PSTRG_OUTFITS_RANGE_START + 1

        player.features.storage.add(key, (50 shl 16) or 3, true)

        assertThat(player.features.outfits.getOutfits()).containsExactly(OutfitEntry(50, 3))
        assertThat(player.features.storage.has(key)).isFalse()
    }

    @Test
    fun `prepareForPersist serializes the composed outfits and familiars`() {
        val player = newPlayer()
        player.features.outfits.addOutfit(50, 3)
        player.features.familiars.addFamiliar(77)

        player.features.storage.prepareForPersist()

        assertThat(player.features.storage.getStorageMap())
            .containsEntry(PlayerStorage.PSTRG_OUTFITS_RANGE_START + 1, (50 shl 16) or 3)
            .containsEntry(PlayerStorage.PSTRG_FAMILIARS_RANGE_START + 1, 77 shl 16)
    }

    @Test
    fun `attached effects read the composed storage`() {
        val player = newPlayer()
        player.features.storage.add(PlayerStorage.PSTRG_WING_CURRENTWING, 5, true)

        assertThat(player.features.attachedEffects.getCurrentWing()).isEqualTo(5)
        assertThat(player.features.attachedEffects.getLastWing()).isEqualTo(5)
    }

    @Test
    fun `attached effects write through to the composed storage`() {
        val player = newPlayer()

        player.features.attachedEffects.setCurrentWing(5)

        assertThat(player.features.storage.get(PlayerStorage.PSTRG_WING_CURRENTWING)).isEqualTo(5)
    }

    @Test
    fun `the composed storage routes updates through the injected seams`() {
        val player = newPlayer()
        val events = mutableListOf<String>()
        val features = PlayerFeatures(
            playerId = player.id,
            wheelPlayer = player,
            currentFrameTime = { 777L },
            eventOnStorageUpdate = { _, key, value, oldValue, frameTime ->
                events += "event:$key:$value:$oldValue:$frameTime"
            },
            playerOnStorageUpdateCallback = { _, key, value, oldValue, frameTime ->
                events += "callback:$key:$value:$oldValue:$frameTime"
            },
        )

        features.storage.add(555, 9)

        assertThat(events).containsExactly(
            "event:555:9:-1:777",
            "callback:555:9:-1:777",
        )
    }
}
