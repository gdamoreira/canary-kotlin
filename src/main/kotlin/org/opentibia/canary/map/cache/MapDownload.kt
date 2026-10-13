package org.opentibia.canary.map.cache

/** `MapDownload::CanaryReleaseTagDiff` (src/map/map_download.hpp:20-23). */
data class CanaryReleaseTagDiff(
    val configuredTag: String,
    val currentTag: String,
)

/**
 * Port of namespace `MapDownload` (src/map/map_download.hpp:18-31,
 * src/map/map_download.cpp).
 *
 * The C++ `g_logger()` warn and the libcurl `fetchTextUrl`/`warnIfOutdatedMapDownloadUrl`
 * network flow are constructor-injected seams (AGENTS.md 1.1: no global
 * singletons); the three pure string functions live in the companion exactly
 * like the C++ namespace functions.
 */
class MapDownload(
    private val fetchTextUrl: (url: String) -> String?,
    private val warn: (message: String) -> Unit = {},
    private val serverReleaseVersion: String = SERVER_RELEASE_VERSION,
) {

    /**
     * `MapDownload::warnIfOutdatedMapDownloadUrl` (src/map/map_download.cpp:146-168).
     *
     * Only a configured URL that carries a Canary release tag is checked; the
     * release `config.lua.dist` is fetched first and the `main` one only when
     * the first fetch fails or carries no `mapDownloadUrl`.
     */
    fun warnIfOutdatedMapDownloadUrl(configuredMapDownloadUrl: String) {
        if (extractCanaryReleaseTag(configuredMapDownloadUrl) == null) {
            return
        }

        val currentMapDownloadUrl = fetchCurrentConfigDistMapDownloadUrl() ?: return
        val releaseTagDiff = compareCanaryReleaseTags(configuredMapDownloadUrl, currentMapDownloadUrl) ?: return

        warn(
            "mapDownloadUrl points to Canary release ${releaseTagDiff.configuredTag}, but the current config.lua.dist points to " +
                "${releaseTagDiff.currentTag}. Update mapDownloadUrl to '$currentMapDownloadUrl' before downloading the main map, " +
                "otherwise a map from a different release may be used.",
        )
    }

    /** `getReleaseConfigDistUrl` (map_download.cpp:73-75). */
    private fun getReleaseConfigDistUrl(): String =
        "https://raw.githubusercontent.com/opentibiabr/canary/v$serverReleaseVersion/config.lua.dist"

    /** `fetchCurrentConfigDistMapDownloadUrl` (map_download.cpp:77-96). */
    private fun fetchCurrentConfigDistMapDownloadUrl(): String? {
        val configDistUrls = arrayOf(getReleaseConfigDistUrl(), MAIN_CONFIG_DIST_URL)
        for (configDistUrl in configDistUrls) {
            val currentConfigDist = fetchTextUrl(configDistUrl) ?: continue
            val mapDownloadUrl = extractMapDownloadUrl(currentConfigDist)
            if (mapDownloadUrl != null) {
                return mapDownloadUrl
            }
        }

        return null
    }

    companion object {
        /** `SERVER_RELEASE_VERSION` (`src/core.hpp:17`). */
        const val SERVER_RELEASE_VERSION: String = "3.6.1"

        /** `MAIN_CONFIG_DIST_URL` (map_download.cpp:24). */
        internal const val MAIN_CONFIG_DIST_URL: String =
            "https://raw.githubusercontent.com/opentibiabr/canary/main/config.lua.dist"

        /** `CANARY_RELEASE_MAP_URL_PREFIX` (map_download.cpp:25). */
        internal const val CANARY_RELEASE_MAP_URL_PREFIX: String =
            "https://github.com/opentibiabr/canary/releases/download/"

        private val DOUBLE_QUOTED_MAP_DOWNLOAD_URL =
            Regex("\\bmapDownloadUrl\\s*=\\s*\"([^\"\\r\\n]*)\"")

        private val SINGLE_QUOTED_MAP_DOWNLOAD_URL =
            Regex("\\bmapDownloadUrl\\s*=\\s*'([^'\\r\\n]*)'")

        /** `MapDownload::extractMapDownloadUrl` (map_download.cpp:102-116). */
        fun extractMapDownloadUrl(luaConfig: String): String? {
            DOUBLE_QUOTED_MAP_DOWNLOAD_URL.find(luaConfig)?.let { match ->
                return match.groupValues[1]
            }

            SINGLE_QUOTED_MAP_DOWNLOAD_URL.find(luaConfig)?.let { match ->
                return match.groupValues[1]
            }

            return null
        }

        /** `MapDownload::extractCanaryReleaseTag` (map_download.cpp:118-131). */
        fun extractCanaryReleaseTag(mapDownloadUrl: String): String? {
            val tagStart = mapDownloadUrl.indexOf(CANARY_RELEASE_MAP_URL_PREFIX)
            if (tagStart == -1) {
                return null
            }

            val tagValueStart = tagStart + CANARY_RELEASE_MAP_URL_PREFIX.length
            val tagValueEnd = mapDownloadUrl.indexOf('/', tagValueStart)
            if (tagValueEnd == -1 || tagValueEnd == tagValueStart) {
                return null
            }

            return mapDownloadUrl.substring(tagValueStart, tagValueEnd)
        }

        /** `MapDownload::compareCanaryReleaseTags` (map_download.cpp:133-144). */
        fun compareCanaryReleaseTags(
            configuredMapDownloadUrl: String,
            currentMapDownloadUrl: String,
        ): CanaryReleaseTagDiff? {
            val configuredTag = extractCanaryReleaseTag(configuredMapDownloadUrl)
            val currentTag = extractCanaryReleaseTag(currentMapDownloadUrl)
            if (configuredTag == null || currentTag == null || configuredTag == currentTag) {
                return null
            }

            return CanaryReleaseTagDiff(configuredTag = configuredTag, currentTag = currentTag)
        }
    }
}
