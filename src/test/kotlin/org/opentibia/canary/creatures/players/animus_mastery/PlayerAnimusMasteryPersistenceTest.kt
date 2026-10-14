package org.opentibia.canary.creatures.players.animus_mastery

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.map.Position

/**
 * Wave 57 A4: the `players.animus_mastery` blob (iologindata_save_player.cpp:
 * 293-299, iologindata_load_player.cpp:277-289) must persist the composed
 * `Player::animusMastery()` state (player.cpp:12812) through the
 * [PlayerAnimusMasteryPersistence] save/load seam.
 *
 * Canary source of truth: `../.projects/canary`
 *  - src/creatures/players/animus_mastery/animus_mastery.hpp:16-45
 *  - src/creatures/players/animus_mastery/animus_mastery.cpp:62-73
 *  - src/io/functions/iologindata_save_player.cpp:293-299
 *  - src/io/functions/iologindata_load_player.cpp:277-289
 */
class PlayerAnimusMasteryPersistenceTest {

    private fun newPlayer(id: Long, name: String) = Player(
        id = id,
        name = name,
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `savePlayerAnimusMastery serializes the composed component`() {
        val player = newPlayer(1, "Animus Source")
        player.features.animusMastery.add("Dragon")
        player.features.animusMastery.add("Rat")

        val blob = PlayerAnimusMasteryPersistence().savePlayerAnimusMastery(player)

        val expected = PropWriteStream().also { player.features.animusMastery.serialize(it) }.getStream()
        assertThat(blob).isEqualTo(expected)
    }

    @Test
    fun `savePlayerAnimusMastery writes an empty blob without masteries`() {
        val player = newPlayer(1, "Animus Empty")

        assertThat(PlayerAnimusMasteryPersistence().savePlayerAnimusMastery(player)).isEmpty()
    }

    @Test
    fun `loadPlayerAnimusMastery unserializes the blob into the composed component`() {
        val player = newPlayer(2, "Animus Target")
        val stream = PropWriteStream()
        AnimusMastery().apply {
            add("Dragon")
            add("Rat")
        }.serialize(stream)

        PlayerAnimusMasteryPersistence().loadPlayerAnimusMastery(player, stream.getStream())

        assertThat(player.features.animusMastery.getAnimusMasteries()).containsExactly("dragon", "rat")
        assertThat(player.features.animusMastery.has("Dragon")).isTrue()
    }

    @Test
    fun `loadPlayerAnimusMastery keeps the existing masteries like the C++ append`() {
        val player = newPlayer(3, "Animus Append")
        player.features.animusMastery.add("Demon")
        val stream = PropWriteStream().also { AnimusMastery().apply { add("Rat") }.serialize(it) }

        PlayerAnimusMasteryPersistence().loadPlayerAnimusMastery(player, stream.getStream())

        assertThat(player.features.animusMastery.getAnimusMasteries()).containsExactly("demon", "rat")
    }

    @Test
    fun `loadPlayerAnimusMastery with an empty blob leaves the component untouched`() {
        val player = newPlayer(4, "Animus Untouched")
        player.features.animusMastery.add("Demon")

        PlayerAnimusMasteryPersistence().loadPlayerAnimusMastery(player, ByteArray(0))

        assertThat(player.features.animusMastery.getAnimusMasteries()).containsExactly("demon")
    }

    @Test
    fun `the save and load seam round trips the composed masteries`() {
        val source = newPlayer(5, "Animus RoundTrip Source")
        source.features.animusMastery.add("Demon")
        source.features.animusMastery.add("Dragon Lord")

        val blob = PlayerAnimusMasteryPersistence().savePlayerAnimusMastery(source)

        val target = newPlayer(6, "Animus RoundTrip Target")
        PlayerAnimusMasteryPersistence().loadPlayerAnimusMastery(target, blob)

        assertThat(target.features.animusMastery.getAnimusMasteries())
            .containsExactlyElementsOf(source.features.animusMastery.getAnimusMasteries())
    }
}
