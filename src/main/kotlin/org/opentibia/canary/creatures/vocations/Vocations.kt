package org.opentibia.canary.creatures.vocations

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Mirror of Canary's `class Vocations`
 * (src/creatures/players/vocations/vocation.hpp:122, vocation.cpp:19-234).
 *
 * Canary's `loadFromXml()` reads `{CORE_DIRECTORY}/XML/vocations.xml` through the
 * global config manager and the class is a process-wide singleton
 * (`Vocations::getInstance`). Following AGENTS.md 1.1 (no global singletons and
 * dependency injection), the XML source is injected here: [loadFromXml] takes the
 * document content directly, so production callers and tests feed the same
 * `data/XML/vocations.xml`.
 *
 * Every attribute name, default and `<skill>`/`<mitigation>`/`<formula>`/`<pvp>`/
 * `<gem>` child parse is transcribed from `Vocations::loadFromXml`
 * (vocation.cpp:28-201), including `gaincap` being stored multiplied by 100 and
 * pugixml `as_bool()` semantics.
 *
 * [rates] and [itemIdByName] are the dependency-injected replacements for the
 * `g_configManager()`/`Item::items` reads the `Vocation` runtime performs
 * (vocation.cpp:337-411); each loaded [Vocation] receives them plus a
 * `g_vocations().getVocation` back-reference for the supreme gem modifiers.
 */
class Vocations(
    private val rates: VocationRates = VocationRates(),
    private val itemIdByName: (String) -> Int = { 0 },
) {

    private val vocationsMap = sortedMapOf<Int, Vocation>()

    /**
     * Canary `Vocations::reload()` (vocation.cpp:19): clear the registry, then
     * load again.
     */
    fun reload(xmlContent: String): Boolean {
        vocationsMap.clear()
        return loadFromXml(xmlContent)
    }

    /** Canary `Vocations::loadFromXml()` body (vocation.cpp:28), over [xmlContent]. */
    fun loadFromXml(xmlContent: String): Boolean {
        val doc = try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        } catch (exception: Exception) {
            return false
        }

        val root = doc.getElementsByTagName("vocations").item(0) as? Element ?: return false

        val children = root.childNodes
        for (i in 0 until children.length) {
            val vocationNode = children.item(i) as? Element ?: continue
            if (!vocationNode.tagName.equals("vocation", ignoreCase = true)) {
                continue
            }

            val idAttr = vocationNode.getAttribute("id")
            if (idAttr.isEmpty()) {
                continue
            }
            val id = idAttr.toIntOrNull() ?: continue

            val vocation = vocationsMap.getOrPut(id) { Vocation(id, rates) }
            vocation.itemIdByName = itemIdByName
            vocation.baseVocationProvider = { baseId -> vocationsMap[baseId] }

            stringAttribute(vocationNode, "name")?.let { vocation.name = it }
            intAttribute(vocationNode, "clientid")?.let { vocation.clientId = it }
            intAttribute(vocationNode, "baseid")?.let { vocation.baseId = it }
            stringAttribute(vocationNode, "description")?.let { vocation.description = it }
            boolAttribute(vocationNode, "magicshield")?.let { vocation.magicShield = it }
            intAttribute(vocationNode, "gaincap")?.let { vocation.gainCap = it * 100 }
            intAttribute(vocationNode, "gainhp")?.let { vocation.gainHP = it }
            intAttribute(vocationNode, "gainmana")?.let { vocation.gainMana = it }
            intAttribute(vocationNode, "gainhpticks")?.let { vocation.gainHealthTicks = it }
            intAttribute(vocationNode, "gainhpamount")?.let { vocation.gainHealthAmount = it }
            intAttribute(vocationNode, "gainmanaticks")?.let { vocation.gainManaTicks = it }
            intAttribute(vocationNode, "gainmanaamount")?.let { vocation.gainManaAmount = it }
            floatAttribute(vocationNode, "manamultiplier")?.let { vocation.manaMultiplier = it }
            intAttribute(vocationNode, "attackspeed")?.let { vocation.attackSpeed = it }
            intAttribute(vocationNode, "basespeed")?.let { vocation.baseSpeed = it }
            intAttribute(vocationNode, "soulmax")?.let { vocation.soulMax = it }
            intAttribute(vocationNode, "gainsoulticks")?.let { vocation.gainSoulTicks = it }
            intAttribute(vocationNode, "fromvoc")?.let { vocation.fromVocation = it }
            boolAttribute(vocationNode, "canCombat")?.let { vocation.combat = it }
            intAttribute(vocationNode, "avatarlooktype")?.let { vocation.avatarLookType = it }

            val childNodes = vocationNode.childNodes
            for (j in 0 until childNodes.length) {
                val childNode = childNodes.item(j) as? Element ?: continue
                when {
                    childNode.tagName.equals("skill", ignoreCase = true) -> {
                        val skillIdText = childNode.getAttribute("id")
                        val skillId = skillIdText.toIntOrNull()
                        if (skillId != null && skillId in 0..Vocation.SKILL_LAST) {
                            val multiplier = childNode.getAttribute("multiplier").toFloatOrNull() ?: 0.0f
                            vocation.skillMultipliers[skillId] = multiplier
                        }
                    }

                    childNode.tagName.equals("mitigation", ignoreCase = true) -> {
                        floatAttribute(childNode, "multiplier")?.let { vocation.mitigationFactor = it }
                        floatAttribute(childNode, "primaryShield")?.let { vocation.mitigationPrimaryShield = it }
                        floatAttribute(childNode, "secondaryShield")?.let { vocation.mitigationSecondaryShield = it }
                    }

                    childNode.tagName.equals("formula", ignoreCase = true) -> {
                        floatAttribute(childNode, "meleeDamage")?.let { vocation.meleeDamageMultiplier = it }
                        floatAttribute(childNode, "distDamage")?.let { vocation.distDamageMultiplier = it }
                        floatAttribute(childNode, "defense")?.let { vocation.defenseMultiplier = it }
                        floatAttribute(childNode, "armor")?.let { vocation.armorMultiplier = it }
                    }

                    childNode.tagName.equals("pvp", ignoreCase = true) -> {
                        floatAttribute(childNode, "damageReceivedMultiplier")?.let { vocation.pvpDamageReceivedMultiplier = it }
                        floatAttribute(childNode, "damageDealtMultiplier")?.let { vocation.pvpDamageDealtMultiplier = it }
                    }

                    childNode.tagName.equals("gem", ignoreCase = true) -> {
                        val quality = childNode.getAttribute("quality").toIntOrNull() ?: 0
                        vocation.wheelGems[quality] = childNode.getAttribute("name")
                    }
                }
            }
        }
        return true
    }

    /** Canary `Vocations::getVocation` (vocation.cpp:203); null when unknown. */
    fun getVocation(id: Int): Vocation? = vocationsMap[id]

    /** Canary `Vocations::getVocations` (vocation.cpp:214). */
    fun getVocations(): Map<Int, Vocation> = vocationsMap

    /**
     * Convenience over Canary `Vocations::getVocationId` (vocation.cpp:218),
     * returning the [Vocation] itself instead of its id. Null when no vocation
     * carries [name] (note id 0 is itself a valid "None" vocation).
     */
    fun getVocationByName(name: String): Vocation? =
        vocationsMap.values.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Canary `Vocations::getVocationId` (vocation.cpp:218), case-insensitive. */
    fun getVocationId(name: String): Int =
        vocationsMap.entries.firstOrNull { it.value.name.equals(name, ignoreCase = true) }?.key
            ?: Vocation.VOCATION_NONE

    /** Canary `Vocations::getPromotedVocation` (vocation.cpp:227). */
    fun getPromotedVocation(vocationId: Int): Int =
        vocationsMap.entries.firstOrNull {
            it.value.fromVocation == vocationId && it.key != vocationId
        }?.key ?: Vocation.VOCATION_NONE

    /**
     * Attribute lookup that treats a missing/empty attribute as absent, mirroring
     * the `if ((attr = node.attribute(...)))` guards. An invalid value casts to 0
     * exactly like `pugi::cast` (`strtol`/`strtod` behaviour).
     */
    private fun intAttribute(node: Element, name: String): Int? {
        val value = node.getAttribute(name)
        return if (value.isEmpty()) null else value.toIntOrNull() ?: 0
    }

    private fun floatAttribute(node: Element, name: String): Float? {
        val value = node.getAttribute(name)
        return if (value.isEmpty()) null else value.toFloatOrNull() ?: 0.0f
    }

    private fun stringAttribute(node: Element, name: String): String? {
        val value = node.getAttribute(name)
        return if (value.isEmpty()) null else value
    }

    private fun boolAttribute(node: Element, name: String): Boolean? {
        val value = node.getAttribute(name)
        return if (value.isEmpty()) null else asBool(value)
    }

    /** pugixml `xml_attribute::as_bool` (`impl::get_value_bool`). */
    private fun asBool(value: String): Boolean =
        value.isNotEmpty() && value[0] in "1tTyY"
}
