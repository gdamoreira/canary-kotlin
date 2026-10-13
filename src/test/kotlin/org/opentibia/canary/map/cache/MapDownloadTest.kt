package org.opentibia.canary.map.cache

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript of the Canary `MapDownload` unit tests
 * (tests/unit/utils/map_download_test.cpp) plus the network-flow cases of
 * `warnIfOutdatedMapDownloadUrl` (src/map/map_download.cpp:146-168) through the
 * injected fetch/warn seams.
 */
class MapDownloadTest {

    private val releaseUrl = "https://raw.githubusercontent.com/opentibiabr/canary/v3.6.1/config.lua.dist"
    private val mainUrl = "https://raw.githubusercontent.com/opentibiabr/canary/main/config.lua.dist"

    private class RecordingDownload(
        responses: Map<String, String?>,
        private val releaseVersion: String = "3.6.1",
    ) {
        val fetched = mutableListOf<String>()
        val warned = mutableListOf<String>()
        val download = MapDownload(
            fetchTextUrl = { url ->
                fetched.add(url)
                responses[url]
            },
            warn = { message -> warned.add(message) },
            serverReleaseVersion = releaseVersion,
        )
    }

    @Test
    fun `extracts a double quoted mapDownloadUrl`() {
        val luaConfig = """
            toggleDownloadMap = true
            mapDownloadUrl = "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm"
        """

        assertThat(MapDownload.extractMapDownloadUrl(luaConfig))
            .isEqualTo("https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm")
    }

    @Test
    fun `extracts a single quoted mapDownloadUrl`() {
        val luaConfig = """
            mapDownloadUrl = 'https://github.com/opentibiabr/canary/releases/download/v3.4.0/otservbr.otbm'
        """

        assertThat(MapDownload.extractMapDownloadUrl(luaConfig))
            .isEqualTo("https://github.com/opentibiabr/canary/releases/download/v3.4.0/otservbr.otbm")
    }

    @Test
    fun `prefers the double quoted assignment over the single quoted one`() {
        val luaConfig = """
            mapDownloadUrl = 'https://example.org/single.otbm'
            mapDownloadUrl = "https://example.org/double.otbm"
        """

        assertThat(MapDownload.extractMapDownloadUrl(luaConfig))
            .isEqualTo("https://example.org/double.otbm")
    }

    @Test
    fun `returns null when the assignment is missing or prefixed by another word`() {
        assertThat(MapDownload.extractMapDownloadUrl("toggleDownloadMap = true")).isNull()
        assertThat(MapDownload.extractMapDownloadUrl("notmapDownloadUrl = \"https://example.org/map.otbm\""))
            .isNull()
    }

    @Test
    fun `extracts the canary release tag`() {
        val releaseTag = MapDownload.extractCanaryReleaseTag(
            "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm",
        )

        assertThat(releaseTag).isEqualTo("v3.5.0")
    }

    @Test
    fun `returns null when extracting a canary release tag from a custom url`() {
        assertThat(MapDownload.extractCanaryReleaseTag("https://example.org/maps/otservbr.otbm")).isNull()
    }

    @Test
    fun `returns null when the release tag has no following path segment`() {
        assertThat(
            MapDownload.extractCanaryReleaseTag(
                "https://github.com/opentibiabr/canary/releases/download/v3.5.0",
            ),
        ).isNull()
        assertThat(
            MapDownload.extractCanaryReleaseTag(
                "https://github.com/opentibiabr/canary/releases/download//otservbr.otbm",
            ),
        ).isNull()
    }

    @Test
    fun `detects different canary release tags`() {
        val releaseTagDiff = MapDownload.compareCanaryReleaseTags(
            "https://github.com/opentibiabr/canary/releases/download/v3.4.0/otservbr.otbm",
            "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm",
        )

        assertThat(releaseTagDiff).isNotNull()
        assertThat(releaseTagDiff!!.configuredTag).isEqualTo("v3.4.0")
        assertThat(releaseTagDiff.currentTag).isEqualTo("v3.5.0")
    }

    @Test
    fun `ignores matching canary release tags`() {
        assertThat(
            MapDownload.compareCanaryReleaseTags(
                "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm",
                "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm",
            ),
        ).isNull()
    }

    @Test
    fun `ignores a custom configured url without fetching`() {
        val recording = RecordingDownload(emptyMap())

        recording.download.warnIfOutdatedMapDownloadUrl("https://example.org/maps/custom.otbm")

        assertThat(recording.fetched).isEmpty()
        assertThat(recording.warned).isEmpty()
    }

    @Test
    fun `follows the release url then the main url when fetches fail`() {
        val recording = RecordingDownload(mapOf(releaseUrl to null, mainUrl to null))

        recording.download.warnIfOutdatedMapDownloadUrl(
            "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm",
        )

        assertThat(recording.fetched).containsExactly(releaseUrl, mainUrl)
        assertThat(recording.warned).isEmpty()
    }

    @Test
    fun `uses the release config url first and stops at the first mapDownloadUrl`() {
        val releaseConfig = """
            mapDownloadUrl = "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm"
        """
        val recording = RecordingDownload(mapOf(releaseUrl to releaseConfig, mainUrl to null))

        recording.download.warnIfOutdatedMapDownloadUrl(
            "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm",
        )

        assertThat(recording.fetched).containsExactly(releaseUrl)
        assertThat(recording.warned).isEmpty()
    }

    @Test
    fun `falls back to the main config url when the release config carries no mapDownloadUrl`() {
        val recording = RecordingDownload(
            mapOf(
                releaseUrl to "toggleDownloadMap = true",
                mainUrl to "mapDownloadUrl = 'https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm'",
            ),
        )

        recording.download.warnIfOutdatedMapDownloadUrl(
            "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm",
        )

        assertThat(recording.fetched).containsExactly(releaseUrl, mainUrl)
        assertThat(recording.warned).isEmpty()
    }

    @Test
    fun `warns with both release tags when the configured url is outdated`() {
        val recording = RecordingDownload(
            mapOf(
                releaseUrl to "mapDownloadUrl = \"https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm\"",
                mainUrl to null,
            ),
        )

        recording.download.warnIfOutdatedMapDownloadUrl(
            "https://github.com/opentibiabr/canary/releases/download/v3.4.0/otservbr.otbm",
        )

        assertThat(recording.warned).hasSize(1)
        assertThat(recording.warned.single())
            .contains("v3.4.0")
            .contains("v3.5.0")
            .contains("https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm")
    }

    @Test
    fun `does not warn when the tags match`() {
        val recording = RecordingDownload(
            mapOf(
                releaseUrl to "mapDownloadUrl = \"https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm\"",
                mainUrl to null,
            ),
        )

        recording.download.warnIfOutdatedMapDownloadUrl(
            "https://github.com/opentibiabr/canary/releases/download/v3.5.0/otservbr.otbm",
        )

        assertThat(recording.warned).isEmpty()
    }
}
