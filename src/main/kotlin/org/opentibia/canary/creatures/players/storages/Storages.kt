package org.opentibia.canary.creatures.players.storages

import io.github.oshai.kotlinlogging.KotlinLogging
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

/**
 * Mirror of Canary's `Storages` XML registry
 * (src/creatures/players/storages/storages.hpp:12, storages.cpp:19-74).
 *
 * Canary's `loadFromXML()` reads `{CORE_DIRECTORY}/XML/storages.xml` through the
 * global config manager and the class is a process-wide singleton
 * (`Storages::getInstance`). Following AGENTS.md 1.1 the XML source is injected:
 * [loadFromXml] accepts the document content so production callers and tests
 * feed the same `data/XML/storages.xml`.
 *
 * `uint32_t` keys are kept as [Long] so the full unsigned range survives the
 * range arithmetic (see PARITY GAPS in the wave report).
 */
class Storages {

    /** `std::map<std::string, uint32_t> m_storageMap` (storages.hpp:27). */
    private val storageMap = sortedMapOf<String, Long>()

    /** `Storages::getStorageMap` (storages.cpp:72). */
    fun getStorageMap(): Map<String, Long> = storageMap

    /** `Storages::loadFromXML` (storages.cpp:19) over [xmlContent]. */
    fun loadFromXml(xmlContent: String): Boolean {
        val doc = try {
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to parse storages XML" }
            return false
        }

        // pugixml `doc.child("storages").children("range")` is an exact-tag match.
        val root = doc.getElementsByTagName("storages").item(0) as? Element ?: return true

        val ranges = mutableListOf<Pair<Long, Long>>()
        val rangeNodes = root.childNodes
        for (index in 0 until rangeNodes.length) {
            val range = rangeNodes.item(index) as? Element ?: continue
            if (range.tagName != "range") {
                continue
            }

            val start = range.uintAttribute("start")
            val end = range.uintAttribute("end")

            for ((rangeStart, rangeEnd) in ranges) {
                if ((start >= rangeStart && start <= rangeEnd) || (end >= rangeStart && end <= rangeEnd)) {
                    // Faithful C++ quirk: the `continue` targets this inner
                    // range scan, so the conflicting range is still loaded.
                    logger.warn {
                        "Storage range from $start to $end conflicts with a previously defined range"
                    }
                }
            }

            ranges += start to end

            val storageNodes = range.childNodes
            for (storageIndex in 0 until storageNodes.length) {
                val storage = storageNodes.item(storageIndex) as? Element ?: continue
                if (storage.tagName != "storage") {
                    continue
                }

                val name = storage.getAttribute("name")
                var key = storage.uintAttribute("key")

                if (name.any { it.isUpperCase() }) {
                    logger.warn {
                        "Storage from storages.xml with name: $name, contains uppercase letters. " +
                            "Please use dot notation pattern"
                    }
                }

                // Add the start of the range to the key
                key += start

                if (key > end) {
                    logger.error {
                        "Storage from storages.xml with name: $name, has key outside of its range"
                    }
                    continue
                }

                storageMap[name] = key
            }
        }

        return true
    }

    /** File-backed entry point; `CORE_DIRECTORY + "/XML/storages.xml"` (storages.cpp:21). */
    fun loadFromFile(path: Path = DEFAULT_PATH): Boolean = loadFromXml(Files.readString(path))

    /** pugixml `attribute::as_uint`: absent or unparsable attributes read as 0. */
    private fun Element.uintAttribute(name: String): Long =
        getAttribute(name).toULongOrNull()?.toLong() ?: 0L

    companion object {
        /** `CORE_DIRECTORY + "/XML/storages.xml"` (storages.cpp:21). */
        val DEFAULT_PATH: Path = Path.of("data", "XML", "storages.xml")
    }
}
