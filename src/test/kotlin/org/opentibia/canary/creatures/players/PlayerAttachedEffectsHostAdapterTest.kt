package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.appearance.effects.AttachedEffects
import org.opentibia.canary.creatures.appearance.outfit.Outfits
import org.opentibia.canary.kv.ValueWrapper
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.InMemoryProtocolSink
import org.opentibia.canary.network.protocol.outfit.OutfitWindowComposer
import org.opentibia.canary.network.protocol.outfit.OutfitWindowWriter
import org.opentibia.canary.systems.mount.Mounts

/**
 * The live `m_player` adapter of `PlayerAttachedEffects`
 * (player_attached_effects.cpp:102, 106, 118, 291, 295, 404, 408, 416):
 * `Player::sendOutfitWindow()` (player.cpp:2541-2543) and
 * `Player::isRandomMounted()` (player.cpp:12943-12945) must resolve the
 * composed session transport and the live mount state instead of the inert
 * defaults.
 */
class PlayerAttachedEffectsHostAdapterTest {

    private val attachedEffectsXml = """
        <attachedeffects>
            <wing id="5" name="Wing Five"/>
        </attachedeffects>
    """.trimIndent()

    private val outfitsXml = """
        <outfits>
            <outfit type="0" looktype="136" name="Citizen" premium="0" unlocked="1"/>
        </outfits>
    """.trimIndent()

    private fun player(id: Long, effects: AttachedEffects): Player = Player(
        id = id,
        name = "Hero$id",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
        attachedOutfits = Outfits().also { it.loadFromXml(outfitsXml) },
        attachedEffectDefinitions = effects,
    ).also { player ->
        // `getOutfitByLookType(player, defaultOutfit.lookType)` must resolve
        // before the wing pipeline (player_attached_effects.cpp:95-98).
        player.setDefaultOutfit(Outfit(lookType = 136))
    }

    private fun recordingSink(player: Player): InMemoryProtocolSink =
        InMemoryProtocolSink(player, GameMap()) { _, _ ->
            OutfitWindowComposer.CurrentPayload(
                OutfitWindowWriter.OutfitWindowFrame(
                    currentOutfit = player.getDefaultOutfit(),
                    supportOutfitOrNoMount = true,
                    mounted = false,
                    randomMounted = false,
                    outfits = emptyList(),
                    mounts = emptyList(),
                    familiars = emptyList(),
                ),
            )
        }

    @Test
    fun `a wing toggle without a last wing requests the outfit window`() {
        val effects = AttachedEffects().also { it.loadFromXml(attachedEffectsXml) }
        val player = player(1, effects)
        val sink = recordingSink(player)
        player.protocolSink = sink

        // `getLastWing() == 0` -> `m_player.sendOutfitWindow()` then false
        // (player_attached_effects.cpp:101-104).
        assertThat(player.features.attachedEffects.toggleWing(true)).isFalse()

        assertThat(sink.frames).hasSize(1)
        assertThat(sink.frames.single().first()).isEqualTo(0xC8.toByte())
    }

    @Test
    fun `a randomly mounted player samples an owned wing instead of the stored one`() {
        val effects = AttachedEffects().also { it.loadFromXml(attachedEffectsXml) }
        val player = player(2, effects)
        val sink = recordingSink(player)
        player.protocolSink = sink
        // The stored wing (5) exists in the registry but is not owned; with the
        // random-mount flag the component samples the owned set instead
        // (`isRandomMounted()`, player_attached_effects.cpp:105-107), which is
        // empty here, so wing 0 resolves to no wing and nothing is cleared.
        player.kv.set("last-wing", ValueWrapper(5))
        player.features.mounts.setRandomMount(1)
        assertThat(player.features.mounts.isRandomMounted()).isTrue()

        assertThat(player.features.attachedEffects.toggleWing(true)).isFalse()

        // The sampled wing (0, the empty owned set) resolves to no registry
        // entry, so the stored wing is left untouched
        // (player_attached_effects.cpp:108-111) and nothing is sent.
        assertThat(player.kv.get("last-wing")?.getNumber()?.toInt()).isEqualTo(5)
        assertThat(player.features.attachedEffects.getCurrentWing()).isEqualTo(0)
        assertThat(sink.frames).isEmpty()
    }

    @Test
    fun `an unowned stored wing is cleared when the mount is not randomized`() {
        val effects = AttachedEffects().also { it.loadFromXml(attachedEffectsXml) }
        val player = player(3, effects)
        val sink = recordingSink(player)
        player.protocolSink = sink
        player.kv.set("last-wing", ValueWrapper(5))

        assertThat(player.features.attachedEffects.toggleWing(true)).isFalse()

        // `!hasWing(currentWing)` -> clear the stored wing and request the
        // window (player_attached_effects.cpp:115-120).
        assertThat(player.kv.get("last-wing")?.getNumber()?.toInt()).isEqualTo(0)
        assertThat(player.features.attachedEffects.getCurrentWing()).isEqualTo(0)
        assertThat(sink.frames).hasSize(1)
    }
}
