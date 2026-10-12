package org.opentibia.canary.systems.familiar

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Canary parity: `class Familiars` (src/creatures/players/grouping/familiars.cpp).
 *
 * The C++ class is a process-wide singleton (`Familiars::getInstance()`); this port
 * replaces that with an injectable instance, per the DI mandate. `loadFromXml` takes the
 * XML text instead of reading `CORE_DIRECTORY/XML/familiars.xml` through the global config
 * manager, matching the existing Kotlin XML loader convention.
 *
 * Behavioural note (verified against pugixml 1.15 + the Canary expression at
 * familiars.cpp:47): the `enabled` attribute check is a no-op because
 * `attr = familiarsNode.attribute("enabled") && !attr.as_bool()` assigns to a null
 * `xml_attribute` and never short-circuits. Disabled entries are therefore still loaded.
 */
class Familiars {

    private val familiars: Array<MutableList<Familiar>> = Array(VOCATION_LAST + 1) { mutableListOf() }

    fun loadFromXml(xmlContent: String): Boolean {
        val doc = try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            builder.parse(ByteArrayInputStream(xmlContent.toByteArray()))
        } catch (exception: Exception) {
            return false
        }

        val root = doc.getElementsByTagName("familiars").item(0) as? Element ?: return false

        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue

            val vocationAttr = node.getAttribute("vocation")
            if (vocationAttr.isEmpty()) {
                continue
            }
            val vocation = vocationAttr.toIntOrNull() ?: continue
            if (vocation > VOCATION_LAST) {
                continue
            }

            val lookTypeAttr = node.getAttribute("lookType")
            if (lookTypeAttr.isEmpty()) {
                continue
            }
            val lookType = lookTypeAttr.toIntOrNull() ?: continue

            familiars[vocation].add(
                Familiar(
                    name = node.getAttribute("name"),
                    lookType = lookType,
                    premium = parseBool(node.getAttribute("premium"), false),
                    unlocked = parseBool(node.getAttribute("unlocked"), true),
                    type = node.getAttribute("type")
                )
            )
        }

        return true
    }

    fun getFamiliars(vocation: Int): List<Familiar> = familiars[vocation]

    fun getFamiliarByLookType(vocation: Int, lookType: Int): Familiar? =
        familiars[vocation].firstOrNull { it.lookType == lookType }

    companion object {
        const val VOCATION_NONE = 0
        const val VOCATION_LAST = 10

        /**
         * Mirrors pugixml `as_bool()` for the values used by `data/XML/familiars.xml`.
         * An absent/empty attribute yields [default]; otherwise an unknown token yields false.
         */
        internal fun parseBool(value: String, default: Boolean): Boolean = when {
            value.isEmpty() -> default
            value.equals("1", true) || value.equals("true", true) ||
                value.equals("yes", true) || value.equals("on", true) -> true
            value.equals("0", true) || value.equals("false", true) ||
                value.equals("no", true) || value.equals("off", true) -> false
            else -> false
        }
    }
}
