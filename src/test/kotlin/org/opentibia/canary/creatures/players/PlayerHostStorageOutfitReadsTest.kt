package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.map.Position

/**
 * Wave 58 A2: the host `Player` accessors that Canary exposes over the composed
 * feature state must read the same instances the Lua bindings and the storage
 * reserved ranges mutate.
 *
 * Canary source of truth: `/Users/damoreira/.projects/canary`
 *  - src/creatures/players/player.hpp:354 (`getBankBalance`), 1541-1542
 *    (`storage()`), 1564-1566 (`getOutfits()`)
 *  - src/creatures/players/player.cpp:8299-8301 (`Player::getBankBalance`)
 *  - src/creatures/players/player_storage.cpp:56-93 (reserved ranges)
 */
class PlayerHostStorageOutfitReadsTest {

    private fun newPlayer() = Player(
        id = 42,
        name = "Host Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `storage accessor returns the composed PlayerStorage instance`() {
        val player = newPlayer()

        assertThat(player.storage()).isSameAs(player.features.storage)
    }

    @Test
    fun `getOutfits reads the composed outfit state`() {
        val player = newPlayer()
        player.features.outfits.addOutfit(136, 3)

        assertThat(player.getOutfits()).containsExactly(OutfitEntry(136, 3))
    }

    @Test
    fun `getOutfits reflects the storage reserved-range outfitting`() {
        val player = newPlayer()

        player.features.storage.add(PlayerStorage.PSTRG_OUTFITS_RANGE_START + 1, (50 shl 16) or 2, true)

        assertThat(player.getOutfits()).containsExactly(OutfitEntry(50, 2))
    }

    @Test
    fun `getBankBalance reads the composed vitals state`() {
        val player = newPlayer()
        player.features.vitals.setBankBalance(12_345L)

        assertThat(player.getBankBalance()).isEqualTo(12_345L)
    }

    @Test
    fun `getBankBalance tracks later vitals writes`() {
        val player = newPlayer()

        assertThat(player.getBankBalance()).isZero()

        player.features.vitals.setBankBalance(99L)

        assertThat(player.getBankBalance()).isEqualTo(99L)
    }
}
