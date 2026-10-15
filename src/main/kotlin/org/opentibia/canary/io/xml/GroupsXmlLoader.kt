package org.opentibia.canary.io.xml

import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Group
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * `data/XML/groups.xml` document parse, transcribing the body of
 * `Groups::load` plus `parseGroupFlags`
 * (src/creatures/players/grouping/groups.cpp:51-99).
 *
 * Canary reads the file through `g_configManager().getString(CORE_DIRECTORY)`
 * inside `Groups::load`; the port injects the document content (AGENTS.md 1.1)
 * so production and tests feed the same XML. Every attribute name, default and
 * `as_bool`/`as_uint` conversion follows the C++ lines referenced below.
 *
 * PARITY NOTE: `pugi::cast<uint32_t>` saturates a non-numeric value to `0`,
 * which [unsignedAttribute] reproduces; `flags` is truncated to `uint8_t`
 * before the bitset conversion (groups.cpp:84), so only bits 0..7 can be set
 * by the integer attribute.
 */
object GroupsXmlLoader {

    /**
     * Parses [xmlContent] into the loaded groups. Returns `null` when the
     * document cannot be parsed or has no `groups` root, mirroring the
     * `false` return of `Groups::load` (groups.cpp:73-76).
     */
    fun load(xmlContent: String): List<Group>? {
        val doc = try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        } catch (exception: Exception) {
            return null
        }

        val root = doc.getElementsByTagName("groups").item(0) as? Element ?: return null

        val groups = mutableListOf<Group>()
        val children = root.childNodes
        for (i in 0 until children.length) {
            val groupNode = children.item(i) as? Element ?: continue
            if (!groupNode.tagName.equals("group", ignoreCase = true)) {
                continue
            }

            groups.add(parseGroup(groupNode))
        }
        return groups
    }

    /** One `<group>` element (groups.cpp:78-98). */
    private fun parseGroup(groupNode: Element): Group {
        val flags = mutableSetOf<PlayerFlags_t>()

        // `std::bitset<FlagLast>(static_cast<uint8_t>(flags))` (groups.cpp:84-89):
        // the integer attribute is a raw bitmask over the flag numbers, truncated
        // to `uint8_t`, so only bits 0..7 can ever be raised.
        val mask = unsignedAttribute(groupNode, "flags").toLong() and 0xFF
        for (number in 0 until PlayerFlags_t.FlagLast.value) {
            if ((mask shr number) and 1L == 1L) {
                GroupsFlagNumbers.fromNumber(number)?.let { flags.add(it) }
            }
        }

        parseGroupFlags(flags, groupNode)

        return Group(
            name = groupNode.getAttribute("name"),
            id = unsignedAttribute(groupNode, "id"),
            access = boolAttribute(groupNode, "access") ?: false,
            flags = flags,
            maxDepotItems = unsignedAttribute(groupNode, "maxdepotitems"),
            maxVipEntries = unsignedAttribute(groupNode, "maxvipentries"),
        )
    }

    /**
     * `parseGroupFlags` (groups.cpp:51-66): every child of `<flags>` contributes
     * its first attribute when `as_bool` is truthy; the attribute name is
     * lowercased and matched against the `PlayerFlags_t` enum names.
     */
    private fun parseGroupFlags(flags: MutableSet<PlayerFlags_t>, groupNode: Element) {
        val flagsNode = childElement(groupNode, "flags") ?: return

        val children = flagsNode.childNodes
        for (i in 0 until children.length) {
            val flagNode = children.item(i) as? Element ?: continue
            val attributes = flagNode.attributes
            if (attributes.length == 0) {
                continue
            }
            val attribute = attributes.item(0)
            if (!asBool(attribute.nodeValue ?: "")) {
                continue
            }

            val flag = GroupsFlagNumbers.byName(attribute.nodeName) ?: continue
            flags.add(flag)
        }
    }

    private fun childElement(parent: Element, tagName: String): Element? {
        val children = parent.childNodes
        for (i in 0 until children.length) {
            val child = children.item(i) as? Element ?: continue
            if (child.tagName.equals(tagName, ignoreCase = true)) {
                return child
            }
        }
        return null
    }

    /** `pugi::cast<uint32_t>(attribute.value())`: missing/invalid -> 0. */
    private fun unsignedAttribute(node: Element, name: String): Int {
        val value = node.getAttribute(name)
        if (value.isEmpty()) {
            return 0
        }
        return value.toLongOrNull()?.toUInt()?.toInt() ?: 0
    }

    private fun boolAttribute(node: Element, name: String): Boolean? {
        val value = node.getAttribute(name)
        return if (value.isEmpty()) null else asBool(value)
    }

    /** pugixml `xml_attribute::as_bool` (`impl::get_value_bool`), like `Vocations`. */
    private fun asBool(value: String): Boolean =
        value.isNotEmpty() && value[0] in "1tTyY"
}

/**
 * `Groups::getFlagNumber` / `Groups::getFlagFromNumber` plus the
 * `ParsePlayerFlagMap` lowercase-name lookup (groups.cpp:17-34), shared by the
 * XML parse and the [org.opentibia.canary.creatures.players.grouping.Groups]
 * registry statics.
 */
internal object GroupsFlagNumbers {

    /** `ParsePlayerFlagMap::parsePlayerFlagMap` (groups.cpp:19-31). */
    private val byLowercaseName: Map<String, PlayerFlags_t> =
        PlayerFlags_t.entries.associateBy { it.name.lowercase() }

    fun byName(attributeName: String): PlayerFlags_t? = byLowercaseName[attributeName.lowercase()]

    /** `Groups::getFlagFromNumber` (groups.cpp:41-43). */
    fun fromNumber(value: Int): PlayerFlags_t? = PlayerFlags_t.entries.firstOrNull { it.value == value }
}
