package org.opentibia.canary.creatures.players.animus_mastery

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.io.PropStream
import org.opentibia.canary.io.PropWriteStream

/**
 * Transcript tests for Canary `AnimusMastery`.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/animus_mastery/animus_mastery.hpp:16-45
 *  - src/creatures/players/animus_mastery/animus_mastery.cpp:17-74
 *  - src/io/fileloader.hpp:89-159 (`PropStream::readString` and
 *    `PropWriteStream::writeString`, the persistence codec)
 *  - src/config/configmanager.cpp:222-224, 390 (the four `ANIMUS_MASTERY_*`
 *    values read by `AnimusMastery::init`)
 *  - src/io/functions/iologindata_save_player.cpp:294-299 and
 *    iologindata_load_player.cpp:277-289 (the `players.animus_mastery` blob)
 */
class AnimusMasteryTest {

    private fun masteredNames(count: Int): AnimusMastery = AnimusMastery().also { mastery ->
        for (index in 0 until count) {
            mastery.add("Monster $index")
        }
    }

    /** Length-prefixed raw UTF-8 name as `PropWriteStream::writeString` emits it. */
    private fun nameBytes(name: String): ByteArray {
        val raw = name.toByteArray(Charsets.UTF_8)
        return byteArrayOf((raw.size and 0xFF).toByte(), (raw.size ushr 8).toByte()) + raw
    }

    private fun bytesOf(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    // --- add / remove / has (animus_mastery.cpp:27-46) ---------------------------

    @Test
    fun `add stores the monster type lowercased`() {
        val mastery = AnimusMastery()

        mastery.add("DrAgOn")

        assertThat(mastery.getAnimusMasteries()).containsExactly("dragon")
    }

    @Test
    fun `add ignores a monster type already mastered regardless of case`() {
        val mastery = AnimusMastery()
        mastery.add("Dragon")

        mastery.add("DRAGON")

        assertThat(mastery.getAnimusMasteries()).containsExactly("dragon")
    }

    @Test
    fun `has lowercases the searched monster type`() {
        val mastery = AnimusMastery()
        mastery.add("Dragon")

        assertThat(mastery.has("dragon")).isTrue()
        assertThat(mastery.has("DRAGON")).isTrue()
        assertThat(mastery.has("Rat")).isFalse()
    }

    @Test
    fun `remove erases every case-insensitive match`() {
        val mastery = AnimusMastery()
        mastery.unserialize(PropStream(nameBytes("Dragon") + nameBytes("dragon")))

        mastery.remove("DRAGON")

        assertThat(mastery.getAnimusMasteries()).isEmpty()
    }

    @Test
    fun `remove ignores a monster type that is not mastered`() {
        val mastery = AnimusMastery()
        mastery.add("Dragon")

        mastery.remove("Rat")

        assertThat(mastery.getAnimusMasteries()).containsExactly("dragon")
    }

    @Test
    fun `getAnimusMasteries preserves insertion order`() {
        assertThat(masteredNames(3).getAnimusMasteries())
            .containsExactly("monster 0", "monster 1", "monster 2")
    }

    @Test
    fun `getPoints counts the masteries`() {
        assertThat(AnimusMastery().getPoints()).isZero()
        assertThat(masteredNames(3).getPoints()).isEqualTo(3)
    }

    // --- getExperienceMultiplier (animus_mastery.cpp:48-52) ----------------------

    @Test
    fun `getExperienceMultiplier is the base bonus without masteries`() {
        assertThat(AnimusMastery().getExperienceMultiplier()).isEqualTo(1.02f)
    }

    @Test
    fun `getExperienceMultiplier stays at the base bonus below one monster group`() {
        assertThat(masteredNames(9).getExperienceMultiplier()).isEqualTo(1.02f)
    }

    @Test
    fun `getExperienceMultiplier adds one step per completed monster group`() {
        assertThat(masteredNames(10).getExperienceMultiplier()).isEqualTo(1.021f)
        assertThat(masteredNames(20).getExperienceMultiplier()).isEqualTo(1.022f)
    }

    @Test
    fun `getExperienceMultiplier is capped at maxMonsterXpMultiplier`() {
        val mastery = AnimusMastery(maxMonsterXpMultiplier = 1.05f, monstersAmountToMultiply = 1)
        repeat(100) { mastery.add("Monster $it") }

        assertThat(mastery.getExperienceMultiplier()).isEqualTo(1.05f)
    }

    @Test
    fun `getExperienceMultiplier honors the injected monster multipliers`() {
        val mastery = AnimusMastery(monsterXpMultiplier = 100f, monstersXpMultiplier = 0.1f)
        repeat(9) { mastery.add("Monster $it") }
        assertThat(mastery.getExperienceMultiplier()).isEqualTo(2.0f)

        mastery.add("Monster 9")

        assertThat(mastery.getExperienceMultiplier()).isEqualTo(2.001f)
    }

    // --- config injection (`AnimusMastery::init`, animus_mastery.cpp:20-25) ------

    @Test
    fun `monstersAmountToMultiply clamps zero to one`() {
        val mastery = AnimusMastery(monstersAmountToMultiply = 0)
        mastery.add("Dragon")

        assertThat(mastery.getExperienceMultiplier()).isEqualTo(1.021f)
    }

    @Test
    fun `monstersAmountToMultiply narrows to uint16 before clamping like Canary`() {
        // std::clamp<uint16_t>(70000, 1, 65535) wraps to 4464 before the clamp.
        val mastery = AnimusMastery(monstersAmountToMultiply = 70_000)
        repeat(4_463) { mastery.add("Monster $it") }
        assertThat(mastery.getExperienceMultiplier()).isEqualTo(1.02f)

        mastery.add("Monster 4463")

        assertThat(mastery.getExperienceMultiplier()).isEqualTo(1.021f)
    }

    @Test
    fun `monstersAmountToMultiply narrows a config value of uint16 zero to one`() {
        // std::clamp<uint16_t>(65536, 1, 65535): 65536 wraps to 0, then clamps to 1.
        val mastery = AnimusMastery(monstersAmountToMultiply = 65_536)
        mastery.add("Dragon")

        assertThat(mastery.getExperienceMultiplier()).isEqualTo(1.021f)
    }

    // --- serialize / unserialize (animus_mastery.cpp:62-73) ----------------------

    @Test
    fun `serialize writes each mastery as a uint16 length prefixed string`() {
        val mastery = AnimusMastery()
        mastery.add("Dragon")
        mastery.add("Rat")

        val stream = PropWriteStream()
        mastery.serialize(stream)

        assertThat(stream.getStream().toList().map { it.toInt() and 0xFF }).containsExactly(
            0x06, 0x00, 0x64, 0x72, 0x61, 0x67, 0x6F, 0x6E,
            0x03, 0x00, 0x72, 0x61, 0x74,
        )
    }

    @Test
    fun `serialize writes nothing when no monster is mastered`() {
        val stream = PropWriteStream()

        AnimusMastery().serialize(stream)

        assertThat(stream.getStream()).isEmpty()
    }

    @Test
    fun `unserialize reads every length prefixed monster name`() {
        val mastery = AnimusMastery()

        assertThat(mastery.unserialize(PropStream(nameBytes("dragon") + nameBytes("rat")))).isTrue()

        assertThat(mastery.getAnimusMasteries()).containsExactly("dragon", "rat")
    }

    @Test
    fun `unserialize appends to the existing masteries`() {
        val mastery = AnimusMastery()
        mastery.add("Rat")

        mastery.unserialize(PropStream(nameBytes("dragon")))

        assertThat(mastery.getAnimusMasteries()).containsExactly("rat", "dragon")
    }

    @Test
    fun `unserialize preserves the persisted spelling`() {
        val mastery = AnimusMastery()

        mastery.unserialize(PropStream(nameBytes("Dragon")))

        assertThat(mastery.getAnimusMasteries()).containsExactly("Dragon")
        // `has` always lowercases the searched name, so the raw spelling can never match.
        assertThat(mastery.has("dragon")).isFalse()
        assertThat(mastery.has("Dragon")).isFalse()
    }

    @Test
    fun `unserialize stops at the first truncated name and keeps the read ones`() {
        val mastery = AnimusMastery()

        val result = mastery.unserialize(PropStream(nameBytes("dragon") + bytesOf(0x03, 0x00, 0x72, 0x61)))

        assertThat(result).isTrue()
        assertThat(mastery.getAnimusMasteries()).containsExactly("dragon")
    }

    @Test
    fun `unserialize returns true for an empty stream`() {
        val mastery = AnimusMastery()

        assertThat(mastery.unserialize(PropStream(ByteArray(0)))).isTrue()
        assertThat(mastery.getAnimusMasteries()).isEmpty()
    }

    @Test
    fun `serialize and unserialize round trip the mastered names`() {
        val source = masteredNames(3)
        val stream = PropWriteStream()
        source.serialize(stream)

        val restored = AnimusMastery()
        restored.unserialize(PropStream(stream.getStream()))

        assertThat(restored.getAnimusMasteries()).containsExactlyElementsOf(source.getAnimusMasteries())
    }
}
