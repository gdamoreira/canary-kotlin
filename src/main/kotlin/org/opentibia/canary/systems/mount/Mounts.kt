package org.opentibia.canary.systems.mount

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Mirror of Canary's `class Mounts` (src/creatures/appearance/mounts/mounts.cpp).
 *
 * Canary's `loadFromXml()` reads `{CORE_DIRECTORY}/XML/mounts.xml` through the
 * global config manager. Following AGENTS.md 1.1 (no global singletons), the XML
 * source is injected here: [loadFromXml] accepts the document content directly,
 * so both production callers and tests feed the same `data/XML/mounts.xml`.
 *
 * XML field names match Canary exactly: `id`, `clientid`, `name`, `speed`,
 * `premium`, `type`. `premium` uses pugixml `as_bool()` semantics (first char in
 * `1`, `t`, `T`, `y`, `Y` is true), so `premium="yes"` -> true, `"no"` -> false.
 */
class Mounts {

    private val mounts = LinkedHashSet<Mount>()

    /** Canary `Mounts::reload()`: clear the registry, then load again. */
    fun reload(xmlContent: String): Boolean {
        mounts.clear()
        return loadFromXml(xmlContent)
    }

    /** Canary `Mounts::loadFromXml()` body, operating on the supplied document. */
    fun loadFromXml(xmlContent: String): Boolean {
        val factory = DocumentBuilderFactory.newInstance()
        val builder = factory.newDocumentBuilder()
        val doc = builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))

        val mountNodes = doc.getElementsByTagName("mount")
        for (i in 0 until mountNodes.length) {
            val mountNode = mountNodes.item(i) as Element

            val clientId = mountNode.getAttribute("clientid").toIntOrNull() ?: 0
            val id = (mountNode.getAttribute("id").toIntOrNull() ?: 0) and 0xFF
            val name = mountNode.getAttribute("name")
            val speed = mountNode.getAttribute("speed").toIntOrNull() ?: 0
            val premium = asBool(mountNode.getAttribute("premium"))
            val type = mountNode.getAttribute("type")

            mounts.add(
                Mount(
                    id = id,
                    clientId = clientId and 0xFFFF,
                    name = name,
                    speed = speed,
                    premium = premium,
                    type = type
                )
            )
        }
        return true
    }

    /** Canary `Mounts::getMountByID`. */
    fun getMountByID(id: Int): Mount? = mounts.firstOrNull { it.id == id }

    /** Canary `Mounts::getMountByName` (case-insensitive via `strcasecmp`). */
    fun getMountByName(name: String): Mount? =
        mounts.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Canary `Mounts::getMountByClientID`. */
    fun getMountByClientID(clientId: Int): Mount? = mounts.firstOrNull { it.clientId == clientId }

    /** Canary `Mounts::getMounts`. */
    fun getMounts(): Set<Mount> = mounts

    /** pugixml `xml_attribute::as_bool` (`impl::get_value_bool`). */
    private fun asBool(value: String): Boolean =
        value.isNotEmpty() && value[0] in "1tTyY"
}
