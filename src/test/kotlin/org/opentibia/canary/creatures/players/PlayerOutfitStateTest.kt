package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.appearance.outfit.Outfit
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry

/**
 * Transcribes the Player outfit state
 * (src/creatures/players/player.cpp:6783-6890):
 *
 * - `Player::canWear` (player.cpp:6783-6806)
 * - `Player::addOutfit` / `removeOutfit` / `removeOutfitAddon`
 *   (player.cpp:6833-6862)
 * - `Player::getOutfitAddons` (player.cpp:6864-6890)
 * - `Player::getOutfits` (player.hpp:1564-1566)
 *
 * `Outfit`/`OutfitEntry` are the port's registry record and
 * `Player::outfits` vector entries
 * (src/creatures/appearance/outfit/outfit.hpp:15-32). `g_configManager()
 * .getBoolean(WARN_UNSAFE_SCRIPTS)` (default true, configmanager.cpp:425) and
 * `g_game().isLookTypeRegistered` (game.hpp:667) are constructor seams.
 */
class PlayerOutfitStateTest {

    private fun outfit(
        lookType: Int,
        premium: Boolean = false,
        unlocked: Boolean = false,
        name: String = "Citizen",
    ) = Outfit(name = name, lookType = lookType, premium = premium, unlocked = unlocked, from = "test")

    private class OutfitSink {
        var access = false
        var premium = false
        var registered = true
        val warnings = mutableListOf<String>()
    }

    private fun newState(
        sink: OutfitSink = OutfitSink(),
        registry: (Int) -> Outfit? = { null },
    ) = PlayerOutfitState(
        isAccessPlayer = { sink.access },
        isPremium = { sink.premium },
        isLookTypeRegistered = { sink.registered },
        getOutfitByLookType = registry,
        logWarn = { sink.warnings.add(it) },
    )

    @Test
    fun `addOutfit appends a new entry and merges addons in place`() {
        // Player::addOutfit (player.cpp:6833-6841).
        val state = newState()

        state.addOutfit(128, 1)
        state.addOutfit(128, 2)
        state.addOutfit(136, 3)

        assertThat(state.getOutfits()).containsExactly(
            OutfitEntry(128, 3),
            OutfitEntry(136, 3),
        )
    }

    @Test
    fun `removeOutfit removes the matching entry only`() {
        // Player::removeOutfit (player.cpp:6843-6852).
        val state = newState()
        state.addOutfit(128, 1)
        state.addOutfit(136, 3)

        assertThat(state.removeOutfit(128)).isTrue()
        assertThat(state.removeOutfit(128)).isFalse()
        assertThat(state.getOutfits()).containsExactly(OutfitEntry(136, 3))
    }

    @Test
    fun `removeOutfitAddon clears the bits of the matching entry`() {
        // Player::removeOutfitAddon (player.cpp:6854-6862).
        val state = newState()
        state.addOutfit(128, 3)

        assertThat(state.removeOutfitAddon(128, 1)).isTrue()
        assertThat(state.getOutfits().single().addons).isEqualTo(2)

        assertThat(state.removeOutfitAddon(999, 1)).isFalse()
    }

    @Test
    fun `getOutfitAddons grants all addons to access players`() {
        // Player::getOutfitAddons (player.cpp:6865-6868).
        val sink = OutfitSink()
        sink.access = true
        val state = newState(sink)

        assertThat(state.getOutfitAddons(outfit(128))).isEqualTo(3)
    }

    @Test
    fun `getOutfitAddons refuses premium outfits without premium`() {
        // player.cpp:6870-6872.
        val state = newState()

        assertThat(state.getOutfitAddons(outfit(128, premium = true))).isNull()
    }

    @Test
    fun `getOutfitAddons returns the owned addons`() {
        // player.cpp:6874-6881.
        val state = newState()
        state.addOutfit(128, 2)

        assertThat(state.getOutfitAddons(outfit(128))).isEqualTo(2)
    }

    @Test
    fun `getOutfitAddons returns zero for unlocked outfits`() {
        // player.cpp:6883-6887.
        val state = newState()

        assertThat(state.getOutfitAddons(outfit(128, unlocked = true))).isEqualTo(0)
    }

    @Test
    fun `getOutfitAddons refuses locked unowned outfits`() {
        // player.cpp:6883-6885.
        val state = newState()

        assertThat(state.getOutfitAddons(outfit(128, unlocked = false))).isNull()
    }

    @Test
    fun `canWear blocks unregistered look types when WARN_UNSAFE_SCRIPTS is on`() {
        // Player::canWear (player.cpp:6784-6788).
        val sink = OutfitSink()
        sink.registered = false
        val state = newState(sink, registry = { outfit(it, unlocked = true) })

        assertThat(state.canWear(128, 1)).isFalse()
        assertThat(sink.warnings.single()).contains("unregistered creature looktype")

        // lookType 0 is exempt from the registration gate.
        assertThat(state.canWear(0, 0)).isTrue()
        assertThat(sink.warnings).hasSize(1)
    }

    @Test
    fun `canWear grants access players any outfit`() {
        // player.cpp:6790-6792.
        val sink = OutfitSink()
        sink.access = true
        val state = newState(sink)

        assertThat(state.canWear(999, 3)).isTrue()
    }

    @Test
    fun `canWear rejects unknown and premium-locked outfits`() {
        // player.cpp:6794-6800.
        val unknown = newState()
        assertThat(unknown.canWear(128, 0)).isFalse()

        val premiumLocked = newState(registry = { outfit(it, premium = true) })
        assertThat(premiumLocked.canWear(128, 0)).isFalse()
    }

    @Test
    fun `canWear accepts an unlocked outfit with no addons`() {
        // player.cpp:6802-6804.
        val state = newState(registry = { outfit(it, unlocked = true) })

        assertThat(state.canWear(128, 0)).isTrue()
    }

    @Test
    fun `canWear requires the requested addons to be owned`() {
        // player.cpp:6806-6812.
        val state = newState(registry = { outfit(it) })
        state.addOutfit(128, 1)

        assertThat(state.canWear(128, 1)).isTrue()
        assertThat(state.canWear(128, 2)).isFalse()
    }
}
