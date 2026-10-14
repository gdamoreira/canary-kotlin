package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.CreatureIcon
import org.opentibia.canary.creatures.CreatureIconModifications_t
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

/**
 * Transcribes the Player race-icon overlay state
 * (src/creatures/players/player.cpp:8915-8971):
 *
 * - `Player::setRaceIconOverlay` (player.cpp:8915-8932)
 * - `Player::clearRaceIconOverlays` (player.cpp:8934-8947)
 * - `Player::getRaceIconOverlays` (player.cpp:8949-8961)
 * - `Player::refreshVisibleCreatureIcons` (player.cpp:8963-8971)
 *
 * Canary keeps `m_raceIconOverlays` on `Player` (player.hpp:1930); the class
 * owns it and receives the `client` and spectator lookup as seams (AGENTS.md
 * 1.1). The C++ `magic_enum::enum_contains(icon)` guard is always true for a
 * Kotlin enum entry, so it collapses to the `None` check.
 */
class PlayerRaceIconStateTest {

    private val sentIcons = mutableListOf<Creature>()

    private fun spectator(id: Long): Player =
        Player(id = id, name = "Viewer", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun newState(
        hasClient: Boolean = true,
        oldProtocol: Boolean = false,
        visible: List<Creature> = emptyList(),
    ) = PlayerRaceIconState(
        hasClient = { hasClient },
        isOldProtocol = { oldProtocol },
        visibleCreatures = { visible },
        sendCreatureIcon = { creature -> sentIcons.add(creature) },
    )

    @Test
    fun `setRaceIconOverlay rejects the zero race and the None icon`() {
        val state = newState()

        assertThat(state.setRaceIconOverlay(0, CreatureIconModifications_t.Influenced, true)).isFalse()
        assertThat(state.setRaceIconOverlay(1, CreatureIconModifications_t.None, true)).isFalse()
        assertThat(state.getRaceIconOverlays(1)).isEmpty()
    }

    @Test
    fun `setRaceIconOverlay inserts once and reports duplicates`() {
        val state = newState()

        assertThat(state.setRaceIconOverlay(62, CreatureIconModifications_t.Influenced, true)).isTrue()
        assertThat(state.setRaceIconOverlay(62, CreatureIconModifications_t.Influenced, true)).isFalse()
        assertThat(state.getRaceIconOverlays(62))
            .containsExactly(CreatureIcon(CreatureIconModifications_t.Influenced))
    }

    @Test
    fun `setRaceIconOverlay removes the icon and prunes the empty race`() {
        val state = newState()
        state.setRaceIconOverlay(62, CreatureIconModifications_t.Influenced, true)
        state.setRaceIconOverlay(62, CreatureIconModifications_t.Fiendish, true)

        assertThat(state.setRaceIconOverlay(62, CreatureIconModifications_t.Influenced, false)).isTrue()
        assertThat(state.getRaceIconOverlays(62))
            .containsExactly(CreatureIcon(CreatureIconModifications_t.Fiendish))

        assertThat(state.setRaceIconOverlay(62, CreatureIconModifications_t.Fiendish, false)).isTrue()
        assertThat(state.getRaceIconOverlays(62)).isEmpty()

        assertThat(state.setRaceIconOverlay(62, CreatureIconModifications_t.Fiendish, false)).isFalse()
    }

    @Test
    fun `clearRaceIconOverlays removes one icon from every race`() {
        val state = newState()
        state.setRaceIconOverlay(1, CreatureIconModifications_t.HigherDamageReceived, true)
        state.setRaceIconOverlay(2, CreatureIconModifications_t.HigherDamageReceived, true)
        state.setRaceIconOverlay(2, CreatureIconModifications_t.LowerDamageDealt, true)

        assertThat(state.clearRaceIconOverlays(CreatureIconModifications_t.HigherDamageReceived)).isTrue()
        assertThat(state.getRaceIconOverlays(1)).isEmpty()
        assertThat(state.getRaceIconOverlays(2))
            .containsExactly(CreatureIcon(CreatureIconModifications_t.LowerDamageDealt))

        assertThat(state.clearRaceIconOverlays(CreatureIconModifications_t.HigherDamageReceived)).isFalse()
        assertThat(state.clearRaceIconOverlays(CreatureIconModifications_t.None)).isFalse()
    }

    @Test
    fun `refreshVisibleCreatureIcons needs a modern client`() {
        val visible = listOf(spectator(1), spectator(2))

        newState(hasClient = false, visible = visible).refreshVisibleCreatureIcons()
        newState(oldProtocol = true, visible = visible).refreshVisibleCreatureIcons()
        assertThat(sentIcons).isEmpty()

        newState(visible = visible).refreshVisibleCreatureIcons()
        assertThat(sentIcons).containsExactly(visible[0], visible[1])
    }
}
