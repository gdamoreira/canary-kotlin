package org.opentibia.canary.creatures.appearance.effects

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Mirror of Canary's `class AttachedEffects`
 * (src/creatures/appearance/attached_effects/attached_effects.cpp).
 *
 * Canary's `loadFromXml()` reads `{CORE_DIRECTORY}/XML/attachedeffects.xml`
 * through the global config manager. Following AGENTS.md 1.1 (no global
 * singletons), the XML source is injected: [loadFromXml] accepts the document
 * content directly, so production callers and tests feed the same
 * `data/XML/attachedeffects.xml`.
 *
 * XML shape matches Canary exactly: an `attachedeffects` root whose direct
 * children are `aura`, `shader`, `effect` and `wing` elements carrying `id`
 * and `name`.
 */
class AttachedEffects {

    private val auras = ArrayList<Aura>()
    private val shaders = ArrayList<Shader>()
    private val effects = ArrayList<Effect>()
    private val wings = ArrayList<Wing>()

    /** Canary `AttachedEffects::reload()`: clear the registry, then load again. */
    fun reload(xmlContent: String): Boolean {
        auras.clear()
        shaders.clear()
        effects.clear()
        wings.clear()
        return loadFromXml(xmlContent)
    }

    /**
     * Canary `AttachedEffects::loadFromXml()` body, operating on the supplied
     * document. Returns false on malformed XML (Canary's `printXMLError` path)
     * without touching the registry already loaded, exactly like the C++
     * function which returns before pushing any entry.
     */
    fun loadFromXml(xmlContent: String): Boolean {
        val doc = try {
            DocumentBuilderFactory.newInstance().newDocumentBuilder()
                .parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        } catch (_: Exception) {
            return false
        }

        val root = doc.documentElement ?: return true
        if (root.tagName != "attachedeffects") {
            return true
        }

        var node = root.firstChild
        while (node != null) {
            if (node is Element) {
                val id = node.getAttribute("id")
                val name = node.getAttribute("name")
                when (node.tagName) {
                    "aura" -> auras.add(Aura(castUint16(id), name))
                    "shader" -> shaders.add(Shader(castUint16(id) and 0xFF, name))
                    "effect" -> effects.add(Effect(castUint16(id), name))
                    "wing" -> wings.add(Wing(castUint16(id), name))
                }
            }
            node = node.nextSibling
        }
        return true
    }

    /** Canary `AttachedEffects::getAuraByID(uint8_t id)`. */
    fun getAuraByID(id: Int): Aura? = auras.firstOrNull { it.id == (id and 0xFF) }

    /** Canary `AttachedEffects::getEffectByID(uint8_t id)`. */
    fun getEffectByID(id: Int): Effect? = effects.firstOrNull { it.id == (id and 0xFF) }

    /** Canary `AttachedEffects::getWingByID(uint8_t id)`. */
    fun getWingByID(id: Int): Wing? = wings.firstOrNull { it.id == (id and 0xFF) }

    /** Canary `AttachedEffects::getShaderByID(uint8_t id)`. */
    fun getShaderByID(id: Int): Shader? = shaders.firstOrNull { it.id == (id and 0xFF) }

    /** Canary `AttachedEffects::getAuraByName` (case-insensitive via `strcasecmp`). */
    fun getAuraByName(name: String): Aura? =
        auras.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Canary `AttachedEffects::getShaderByName` (case-insensitive via `strcasecmp`). */
    fun getShaderByName(name: String): Shader? =
        shaders.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Canary `AttachedEffects::getEffectByName` (case-insensitive via `strcasecmp`). */
    fun getEffectByName(name: String): Effect? =
        effects.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Canary `AttachedEffects::getWingByName` (case-insensitive via `strcasecmp`). */
    fun getWingByName(name: String): Wing? =
        wings.firstOrNull { it.name.equals(name, ignoreCase = true) }

    /** Canary `AttachedEffects::getAuras`. */
    fun getAuras(): List<Aura> = auras

    /** Canary `AttachedEffects::getShaders`. */
    fun getShaders(): List<Shader> = shaders

    /** Canary `AttachedEffects::getEffects`. */
    fun getEffects(): List<Effect> = effects

    /** Canary `AttachedEffects::getWings`. */
    fun getWings(): List<Wing> = wings

    /**
     * Mirrors Canary's `pugi::cast<uint16_t>` (src/utils/pugicast.hpp): the
     * whole attribute value must parse as an unsigned decimal integer inside
     * the `uint16_t` range, otherwise the cast yields 0.
     */
    private fun castUint16(value: String): Int {
        if (value.isEmpty() || value.any { it !in '0'..'9' }) {
            return 0
        }
        val digits = value.trimStart('0')
        if (digits.isEmpty() || digits.length > 5) {
            return 0
        }
        val parsed = digits.toInt()
        return if (parsed <= 0xFFFF) parsed else 0
    }
}
