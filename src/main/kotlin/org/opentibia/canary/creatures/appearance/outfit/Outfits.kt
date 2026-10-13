package org.opentibia.canary.creatures.appearance.outfit

import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Mirror of Canary's `class Outfits` (`src/creatures/appearance/outfit/outfit.cpp/.hpp`).
 *
 * The C++ class is a process-wide singleton (`Outfits::getInstance()`) backed by
 * the global `outfits[PLAYERSEX_LAST + 1]` vectors. Following AGENTS.md 1.1 the
 * port is an injectable instance, and [loadFromXml] takes the XML text instead of
 * reading `{CORE_DIRECTORY}/XML/outfits.xml` through the global config manager.
 *
 * Attribute semantics match pugixml exactly:
 *  - `enabled` is skipped only when the attribute is present and `as_bool()` is
 *    false (absent -> loaded);
 *  - `type` is required (missing -> skipped), cast through `pugi::cast<uint16_t>`
 *    (unparsable/out-of-range -> 0) and values above `PLAYERSEX_LAST` are skipped;
 *  - `looktype` is required (missing -> skipped) and cast the same way;
 *  - `premium` defaults to false, `unlocked` to true, `name`/`from` to empty.
 *
 * `g_configManager().getBoolean(WARN_UNSAFE_SCRIPTS)` (default true) and
 * `Game::isLookTypeRegistered` (game.hpp:667) are constructor-injected seams; with
 * the default predicate no look type is filtered.
 */
class Outfits(
    private val warnUnsafeScripts: Boolean = true,
    private val isLookTypeRegistered: (Int) -> Boolean = { true },
) {

    private val outfitsBySex: Map<PlayerSex_t, MutableList<Outfit>> =
        PlayerSex_t.entries.associateWith { mutableListOf() }

    /** `Outfits::reload()` (outfit.cpp:25-30): clear both sex vectors, then load. */
    fun reload(xmlContent: String): Boolean {
        outfitsBySex.values.forEach { it.clear() }
        return loadFromXml(xmlContent)
    }

    /** `Outfits::loadFromXml()` (outfit.cpp:32-83). */
    fun loadFromXml(xmlContent: String): Boolean {
        val doc = try {
            val factory = DocumentBuilderFactory.newInstance()
            val builder = factory.newDocumentBuilder()
            builder.parse(ByteArrayInputStream(xmlContent.toByteArray(Charsets.UTF_8)))
        } catch (_: Exception) {
            return false
        }

        // C++ `doc.child("outfits").children()`: a missing/other root yields an
        // empty range and still returns true.
        val root = doc.documentElement ?: return true
        if (root.tagName != "outfits") {
            return true
        }

        val children = root.childNodes
        for (i in 0 until children.length) {
            val node = children.item(i) as? Element ?: continue

            if (node.hasAttribute("enabled") && !asBool(node.getAttribute("enabled"))) {
                continue
            }

            if (!node.hasAttribute("type")) {
                continue
            }
            val type = castUint16(node.getAttribute("type"))
            if (type > PLAYERSEX_LAST) {
                continue
            }

            if (!node.hasAttribute("looktype")) {
                continue
            }
            val lookType = castUint16(node.getAttribute("looktype"))
            if (warnUnsafeScripts && lookType != 0 && !isLookTypeRegistered(lookType)) {
                continue
            }

            outfitsBySex.getValue(PlayerSex_t.entries[type]).add(
                Outfit(
                    name = node.getAttribute("name"),
                    lookType = lookType,
                    premium = asBool(node.getAttribute("premium")),
                    unlocked = !node.hasAttribute("unlocked") || asBool(node.getAttribute("unlocked")),
                    from = node.getAttribute("from"),
                )
            )
        }

        return true
    }

    /** `Outfits::getOutfits(sex)` (outfit.cpp:111-113). */
    fun getOutfits(sex: PlayerSex_t): List<Outfit> = outfitsBySex.getValue(sex)

    /**
     * `Outfits::getOutfitByLookType(player, lookType, isOppositeOutfit)`
     * (outfit.cpp:85-109). A null player or a look type missing for the resolved
     * sex yields null; `isOppositeOutfit` flips the player's sex first.
     *
     * PARITY NOTE: the C++ invalid-sex guard cannot be reached from the Kotlin
     * [PlayerSex_t] enum.
     */
    fun getOutfitByLookType(player: OutfitPlayer?, lookType: Int, isOppositeOutfit: Boolean = false): Outfit? {
        if (player == null) {
            return null
        }

        var sex = player.getSex()
        if (isOppositeOutfit) {
            sex = if (sex == PlayerSex_t.MALE) PlayerSex_t.FEMALE else PlayerSex_t.MALE
        }

        return outfitsBySex.getValue(sex).firstOrNull { it.lookType == lookType }
    }

    /** `Outfits::getOutfitByName(sex, name)` (outfit.cpp:115-123), exact match. */
    fun getOutfitByName(sex: PlayerSex_t, name: String): Outfit? =
        outfitsBySex.getValue(sex).firstOrNull { it.name == name }

    companion object {
        /** `PLAYERSEX_LAST` (`src/creatures/creatures_definitions.hpp:452`). */
        const val PLAYERSEX_LAST: Int = 1

        /** pugixml `xml_attribute::as_bool`: only a leading `1 t T y Y` is true. */
        internal fun asBool(value: String): Boolean = value.isNotEmpty() && value[0] in "1tTyY"

        /** `pugi::cast<uint16_t>` (`src/utils/pugicast.hpp:17-51`): unparsable -> 0. */
        internal fun castUint16(value: String): Int = value.toUShortOrNull()?.toInt() ?: 0
    }
}

/**
 * Mirror of the `Player` outfit storage and its addon API
 * (`Player::outfits` player.hpp:1686, player.cpp:6783-6889).
 *
 * `Player::canWear` and `Player::getOutfitAddons` read the global `Outfits`
 * singleton, `Player::group->access` and `Player::isPremium`; per AGENTS.md 1.1
 * those dependencies are injected here. `Player::getOutfitAddons`
 * (player.cpp:6864) returns `bool` with an out-parameter `addons`; the port
 * returns the addons or `null` for false.
 */
class PlayerOutfits(
    private val outfits: Outfits,
    private val player: OutfitPlayer? = null,
    private val isPremium: () -> Boolean = { false },
    private val hasAccess: () -> Boolean = { false },
    private val warnUnsafeScripts: Boolean = true,
    private val isLookTypeRegistered: (Int) -> Boolean = { true },
) {

    private val entries = mutableListOf<OutfitEntry>()

    /** Read-only view of `Player::outfits` (player.hpp:1686). */
    val outfitEntries: List<OutfitEntry> get() = entries

    /** `Player::canWear(lookType, addons)` (player.cpp:6783-6813). */
    fun canWear(lookType: Int, addons: Int): Boolean {
        if (warnUnsafeScripts && lookType != 0 && !isLookTypeRegistered(lookType)) {
            return false
        }

        if (hasAccess()) {
            return true
        }

        val outfit = outfits.getOutfitByLookType(player, lookType) ?: return false
        if (outfit.premium && !isPremium()) {
            return false
        }

        if (outfit.unlocked && addons == 0) {
            return true
        }

        val entry = entries.firstOrNull { it.lookType == lookType } ?: return false
        return (entry.addons and addons) == addons
    }

    /** `Player::addOutfit(lookType, addons)` (player.cpp:6833-6841). */
    fun addOutfit(lookType: Int, addons: Int) {
        val entry = entries.firstOrNull { it.lookType == lookType }
        if (entry != null) {
            entry.addons = entry.addons or addons
            return
        }

        entries.add(OutfitEntry(lookType = lookType, addons = addons))
    }

    /** `Player::removeOutfit(lookType)` (player.cpp:6843-6852). */
    fun removeOutfit(lookType: Int): Boolean = entries.removeAll { it.lookType == lookType }

    /** `Player::removeOutfitAddon(lookType, addons)` (player.cpp:6854-6862). */
    fun removeOutfitAddon(lookType: Int, addons: Int): Boolean {
        val entry = entries.firstOrNull { it.lookType == lookType } ?: return false
        entry.addons = entry.addons and addons.inv()
        return true
    }

    /** `Player::getOutfitAddons(outfit)` (player.cpp:6864-6889): addons or null. */
    fun getOutfitAddons(outfit: Outfit): Int? {
        if (hasAccess()) {
            return 3
        }

        if (outfit.premium && !isPremium()) {
            return null
        }

        entries.firstOrNull { it.lookType == outfit.lookType }?.let { return it.addons }

        if (!outfit.unlocked) {
            return null
        }

        return 0
    }
}
