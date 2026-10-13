package org.opentibia.canary.creatures.players.storages

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transcript tests for Canary's `Storages` XML registry
 * (src/creatures/players/storages/storages.cpp:19-74), including the
 * `loadFromXML` range/key arithmetic and the conflict handling quirk where the
 * `continue` sits in the inner range loop, so a conflicting range is still
 * loaded after warning.
 */
class StoragesTest {

    private fun realXml(): String {
        val candidates = listOf(
            Path.of("data/XML/storages.xml"),
            Path.of("../data/XML/storages.xml"),
            Path.of("../../data/XML/storages.xml"),
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("data/XML/storages.xml not found from ${System.getProperty("user.dir")}")
        return Files.readString(path)
    }

    @Test
    fun `loads the real storages xml`() {
        val storages = Storages()

        assertThat(storages.loadFromXml(realXml())).isTrue()
        assertThat(storages.getStorageMap()).hasSize(5)
        assertThat(storages.getStorageMap()).containsEntry("wheel.scroll.abridged", 1_000_001L)
        assertThat(storages.getStorageMap()).containsEntry("wheel.scroll.advanced", 1_000_005L)
    }

    @Test
    fun `storage keys are the range start plus the storage key`() {
        val storages = load(
            """
            <storages>
                <range start="30000000" end="40000000">
                    <storage name="custom.name1" key="1" />
                    <storage name="custom.name2" key="42" />
                </range>
            </storages>
            """
        )

        assertThat(storages.getStorageMap()).containsExactlyEntriesOf(
            mapOf(
                "custom.name1" to 30_000_001L,
                "custom.name2" to 30_000_042L,
            )
        )
    }

    @Test
    fun `missing storage key resolves to the range start`() {
        val storages = load(
            """
            <storages>
                <range start="100" end="200">
                    <storage name="custom.name" />
                </range>
            </storages>
            """
        )

        assertThat(storages.getStorageMap()).containsExactlyEntriesOf(mapOf("custom.name" to 100L))
    }

    @Test
    fun `storage key outside its range is skipped`() {
        val storages = load(
            """
            <storages>
                <range start="100" end="105">
                    <storage name="custom.inside" key="5" />
                    <storage name="custom.outside" key="6" />
                </range>
            </storages>
            """
        )

        assertThat(storages.getStorageMap()).containsExactlyEntriesOf(mapOf("custom.inside" to 105L))
    }

    @Test
    fun `overlapping ranges warn but still load because continue targets the inner loop`() {
        val storages = load(
            """
            <storages>
                <range start="100" end="200">
                    <storage name="custom.first" key="1" />
                </range>
                <range start="150" end="250">
                    <storage name="custom.second" key="1" />
                </range>
            </storages>
            """
        )

        assertThat(storages.getStorageMap()).containsExactlyEntriesOf(
            mapOf(
                "custom.first" to 101L,
                "custom.second" to 151L,
            )
        )
    }

    @Test
    fun `uppercase storage names are loaded after the warning`() {
        val storages = load(
            """
            <storages>
                <range start="100" end="200">
                    <storage name="Custom.Name" key="1" />
                </range>
            </storages>
            """
        )

        assertThat(storages.getStorageMap()).containsExactlyEntriesOf(mapOf("Custom.Name" to 101L))
    }

    @Test
    fun `ranges from a document without a storages root are ignored`() {
        val storages = Storages()

        assertThat(storages.loadFromXml("<?xml version=\"1.0\"?><other/>")).isTrue()
        assertThat(storages.getStorageMap()).isEmpty()
    }

    @Test
    fun `malformed xml fails to load`() {
        val storages = Storages()

        assertThat(storages.loadFromXml("<storages><range")).isFalse()
        assertThat(storages.getStorageMap()).isEmpty()
    }

    private fun load(xml: String): Storages = Storages().apply {
        assertThat(loadFromXml(xml.trimIndent())).isTrue()
    }
}
