package org.opentibia.canary.systems.imbuement

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.Stat
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.ByteArrayInputStream
import java.nio.file.Files
import java.nio.file.Path
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

/** `ImbuementTypes_t` (`src/items/items_definitions.hpp:254`). */
enum class ImbuementTypes_t(val value: Int) {
    IMBUEMENT_NONE(-1),
    IMBUEMENT_ELEMENTAL_DAMAGE(0),
    IMBUEMENT_LIFE_LEECH(1),
    IMBUEMENT_MANA_LEECH(2),
    IMBUEMENT_CRITICAL_HIT(3),
    IMBUEMENT_ELEMENTAL_PROTECTION_DEATH(4),
    IMBUEMENT_ELEMENTAL_PROTECTION_EARTH(5),
    IMBUEMENT_ELEMENTAL_PROTECTION_FIRE(6),
    IMBUEMENT_ELEMENTAL_PROTECTION_ICE(7),
    IMBUEMENT_ELEMENTAL_PROTECTION_ENERGY(8),
    IMBUEMENT_ELEMENTAL_PROTECTION_HOLY(9),
    IMBUEMENT_INCREASE_SPEED(10),
    IMBUEMENT_SKILLBOOST_AXE(11),
    IMBUEMENT_SKILLBOOST_SWORD(12),
    IMBUEMENT_SKILLBOOST_CLUB(13),
    IMBUEMENT_SKILLBOOST_SHIELDING(14),
    IMBUEMENT_SKILLBOOST_DISTANCE(15),
    IMBUEMENT_SKILLBOOST_MAGIC_LEVEL(16),
    IMBUEMENT_INCREASE_CAPACITY(17),
    IMBUEMENT_SKILLBOOST_FIST(18),
    IMBUEMENT_PARALYSIS_DEFLECTION(19),
    ;

    companion object {
        fun fromValue(value: Int): ImbuementTypes_t? = entries.firstOrNull { it.value == value }
    }
}

/**
 * Mirror of Canary's `BaseImbuement` (src/creatures/players/imbuements/imbuements.hpp:22):
 * one shrine tier (`base` element of `data/XML/imbuements.xml`).
 */
data class BaseImbuement(
    val id: Int,
    val name: String,
    val price: Int,
    val protectionPrice: Int,
    val removeCost: Int,
    val duration: Int,
    val percent: Int
)

/**
 * Mirror of Canary's `CategoryImbuement` (src/creatures/players/imbuements/imbuements.hpp:35):
 * one `category` element; [agressive] drives the decay and shrine filters.
 */
data class CategoryImbuement(
    val id: Int,
    val name: String,
    val agressive: Boolean
)

/**
 * Mirror of Canary's `Imbuement` (src/creatures/players/imbuements/imbuements.hpp:80).
 *
 * `skills`/`stats` keep Canary's flat arrays indexed by the [Skill]/[Stat] values
 * (`maxSkillOrStatId + 1`, imbuements.hpp:20); `absorbPercent` is indexed by the
 * `combatTypeToIndex` result, which for `CombatType_t` equals [CombatType.value].
 * `soundEffect` keeps the raw `SoundEffect_t` value (the full sound enum is not
 * ported).
 */
class Imbuement(val id: Int, val baseId: Int) {
    var scrollId: Int = 0
    var icon: Int = 1
    val skills: IntArray = IntArray(Skill.LAST + 1)

    /** `stats[maxSkillOrStatId + 1]` where `maxSkillOrStatId = max(STAT_LAST, SKILL_LAST)` (imbuements.hpp:20). */
    val stats: IntArray = IntArray(maxOf(Stat.LAST, Skill.LAST) + 1)
    var speed: Int = 0
    var capacity: Int = 0

    /** Chance (0-100) to remove paralysis when a paralysis condition would be applied. */
    var paralysisRemoveChance: Int = 0

    /** When true, additional PvP paralyse attacks are deflected (ignored). */
    var pvpParalysisDeflect: Boolean = false

    val absorbPercent: IntArray = IntArray(CombatType.COUNT.value)
    var elementDamage: Int = 0
    var soundEffect: Int = 0

    var combatType: CombatType = CombatType.NONE

    var premium: Boolean = false
    var storage: Int = 0
    var category: Int = 0
    var name: String = ""
    var description: String = ""
    var subgroup: String = ""
    val items: MutableList<Pair<Int, Int>> = mutableListOf()

    /** `Imbuement::getIconID` (imbuements.cpp:465): `icon + (baseid - 1)`. */
    fun getIconID(): Int = icon + (baseId - 1)

    /** `Imbuement::getScrollItemID` (imbuements.cpp:636). */
    fun getScrollItemID(): Int = scrollId
}

/** Mirror of the `ImbuementInfo` struct (src/items/items_definitions.hpp:646). */
data class ImbuementInfo(val imbuement: Imbuement, val duration: Int)

/**
 * Mirror of Canary's `Imbuements` registry (src/creatures/players/imbuements/imbuements.hpp:44)
 * and its `loadFromXml`/`reload`/lookup behavior (imbuements.cpp:47-427).
 *
 * The C++ singleton is replaced by an injectable instance; [imbuementMap],
 * [basesImbuement] and [categoriesImbuement] expose read-only views of the state
 * the C++ class owns privately.
 */
class Imbuements {
    var loaded: Boolean = false
        private set

    private val _imbuementMap = linkedMapOf<Int, Imbuement>()
    private val _scrollIdMap = mutableMapOf<Int, Imbuement>()
    private val _basesImbuement = mutableListOf<BaseImbuement>()
    private val _categoriesImbuement = mutableListOf<CategoryImbuement>()
    private var runningid = 0

    val imbuementMap: Map<Int, Imbuement> get() = _imbuementMap
    val basesImbuement: List<BaseImbuement> get() = _basesImbuement
    val categoriesImbuement: List<CategoryImbuement> get() = _categoriesImbuement

    /**
     * `Imbuements::loadFromXml` (imbuements.cpp:47). Parsing failures return
     * false without touching the loaded state, exactly like the C++ pugixml
     * result check.
     */
    fun loadFromXml(xml: String, reloading: Boolean = false): Boolean {
        val doc = try {
            DocumentBuilderFactory.newInstance()
                .newDocumentBuilder()
                .parse(ByteArrayInputStream(xml.toByteArray(Charsets.UTF_8)))
        } catch (exception: Exception) {
            logger.error(exception) { "Failed to parse imbuements XML" }
            return false
        }

        if (reloading) {
            logger.debug { "Reloading imbuements from XML" }
        }

        loaded = true
        val root = doc.documentElement ?: return true
        if (root.tagName != "imbuements") {
            return true
        }

        val children = root.childNodes
        for (index in 0 until children.length) {
            val node = children.item(index)
            if (node.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            val element = node as Element
            when {
                element.tagName.equals("base", ignoreCase = true) -> loadBase(element)
                element.tagName.equals("category", ignoreCase = true) -> loadCategory(element)
                element.tagName.equals("imbuement", ignoreCase = true) -> loadImbuement(element)
            }
        }
        return true
    }

    fun loadFromFile(path: Path = DEFAULT_PATH): Boolean = loadFromXml(Files.readString(path))

    /**
     * `Imbuements::reload` (imbuements.cpp:366): clear every map, reset the
     * running id and load again.
     */
    fun reload(xml: String): Boolean {
        _imbuementMap.clear()
        _scrollIdMap.clear()
        _basesImbuement.clear()
        _categoriesImbuement.clear()
        runningid = 0
        loaded = false
        return loadFromXml(xml, reloading = true)
    }

    fun reloadFromFile(path: Path = DEFAULT_PATH): Boolean = reload(Files.readString(path))

    /** `Imbuements::getImbuement` (imbuements.cpp:26): id 0 is always null. */
    fun getImbuement(id: Int): Imbuement? {
        if (id == 0) {
            return null
        }
        val imbuement = _imbuementMap[id]
        if (imbuement == null) {
            logger.warn { "Imbuement $id not found" }
        }
        return imbuement
    }

    /** `Imbuements::getImbuementByScrollID` (imbuements.cpp:39). */
    fun getImbuementByScrollID(scrollId: Int): Imbuement? = _scrollIdMap[scrollId]

    /** `Imbuements::getBaseByID` (imbuements.cpp:378). */
    fun getBaseByID(id: Int): BaseImbuement? = _basesImbuement.firstOrNull { it.id == id }

    /** `Imbuements::getCategoryByID` (imbuements.cpp:386). */
    fun getCategoryByID(id: Int): CategoryImbuement? = _categoriesImbuement.firstOrNull { it.id == id }

    /**
     * `Imbuements::getImbuements` (imbuements.cpp:394): the window list. The
     * `TOGGLE_IMBUEMENT_SHRINE_STORAGE` config flag is a parameter defaulting to
     * its configmanager default (configmanager.cpp:156).
     */
    fun getImbuements(
        player: ImbuementPlayer = ImbuementPlayer.UNWIRED,
        item: ImbuementItem? = null,
        scroll: Boolean = false,
        toggleShrineStorage: Boolean = true
    ): List<Imbuement> {
        val imbuements = mutableListOf<Imbuement>()
        for (imbuement in _imbuementMap.values) {
            if (scroll && imbuement.getScrollItemID() == 0) {
                continue
            }

            if (toggleShrineStorage && imbuement.storage != 0 &&
                player.getStorageValue(imbuement.storage) == -1 &&
                imbuement.baseId in 1..3
            ) {
                continue
            }

            val category = getCategoryByID(imbuement.category) ?: continue
            if (item != null) {
                val imbuementType = ImbuementTypes_t.fromValue(category.id) ?: ImbuementTypes_t.IMBUEMENT_NONE
                if (!item.hasImbuementType(imbuementType, imbuement.baseId)) {
                    continue
                }
                if (itemHasImbuementCategory(item, category.id, this)) {
                    continue
                }
            }

            imbuements += imbuement
        }
        return imbuements
    }

    private fun loadBase(node: Element) {
        if (!node.hasAttribute("id")) {
            logger.warn { "Missing id for base entry" }
            return
        }
        _basesImbuement += BaseImbuement(
            id = node.intAttribute("id"),
            name = node.getAttribute("name"),
            price = node.intAttribute("price"),
            protectionPrice = node.intAttribute("protectionPrice"),
            removeCost = node.intAttribute("removecost"),
            duration = node.intAttribute("duration"),
            percent = node.intAttribute("percent")
        )
    }

    private fun loadCategory(node: Element) {
        if (!node.hasAttribute("id")) {
            logger.warn { "Missing id for category entry" }
            return
        }
        _categoriesImbuement += CategoryImbuement(
            id = node.intAttribute("id"),
            name = node.getAttribute("name"),
            agressive = node.boolAttribute("agressive", true)
        )
    }

    private fun loadImbuement(node: Element) {
        runningid++
        val imbuementId = runningid

        if (!node.hasAttribute("base")) {
            logger.warn { "Missing imbuement base id" }
            return
        }
        val baseId = node.intAttribute("base")
        if (getBaseByID(baseId) == null) {
            logger.warn { "Group base '$baseId' not exist" }
            return
        }

        val imbuement = Imbuement(imbuementId, baseId)
        if (_imbuementMap.putIfAbsent(imbuementId, imbuement) != null) {
            logger.warn { "Duplicate imbuement of Base ID: '$baseId' ignored" }
            return
        }

        if (!node.hasAttribute("iconid")) {
            logger.warn { "Missing 'iconid' for imbuement entry" }
            return
        }
        imbuement.icon = node.intAttribute("iconid")

        if (node.hasAttribute("sound")) {
            imbuement.soundEffect = node.intAttribute("sound")
        }
        if (node.hasAttribute("premium")) {
            imbuement.premium = node.boolAttribute("premium", false)
        }
        if (node.hasAttribute("storage")) {
            imbuement.storage = node.intAttribute("storage")
        }
        if (node.hasAttribute("subgroup")) {
            imbuement.subgroup = node.getAttribute("subgroup")
        }

        if (!node.hasAttribute("category")) {
            logger.warn { "Missing imbuement category" }
            return
        }
        val category = node.intAttribute("category")
        if (getCategoryByID(category) == null) {
            logger.warn { "Category imbuement $category not exist" }
            return
        }
        imbuement.category = category

        if (!node.hasAttribute("name")) {
            logger.warn { "Missing imbuement name" }
            return
        }
        imbuement.name = node.getAttribute("name")

        val children = node.childNodes
        for (index in 0 until children.length) {
            val child = children.item(index)
            if (child.nodeType != Node.ELEMENT_NODE) {
                continue
            }
            val element = child as Element
            if (!element.hasAttribute("key")) {
                logger.warn { "Missing key attribute in imbuement id: $imbuementId" }
                continue
            }
            when {
                element.getAttribute("key").equals("scroll", ignoreCase = true) -> loadScroll(imbuement, element)
                element.getAttribute("key").equals("item", ignoreCase = true) -> loadItem(imbuement, element)
                element.getAttribute("key").equals("description", ignoreCase = true) ->
                    imbuement.description = if (element.hasAttribute("value")) {
                        element.getAttribute("value")
                    } else {
                        imbuement.name
                    }

                element.getAttribute("key").equals("effect", ignoreCase = true) -> loadEffect(imbuement, element)
            }
        }

        if (imbuement.scrollId != 0) {
            val previous = _scrollIdMap.put(imbuement.scrollId, imbuement)
            if (previous != null) {
                logger.warn {
                    "Duplicate scroll ID ${imbuement.scrollId} for imbuement '${imbuement.name}', " +
                        "already mapped to '${previous.name}'"
                }
            }
        }
    }

    private fun loadScroll(imbuement: Imbuement, node: Element) {
        if (node.hasAttribute("value")) {
            imbuement.scrollId = node.intAttribute("value")
        } else {
            logger.warn { "Missing scroll ID for imbuement name '${imbuement.name}'" }
        }
    }

    private fun loadItem(imbuement: Imbuement, node: Element) {
        if (!node.hasAttribute("value")) {
            logger.warn { "Missing item ID for imbuement name '${imbuement.name}'" }
            return
        }
        val sourceId = node.intAttribute("value")
        val count = if (node.hasAttribute("count")) node.intAttribute("count") else 1
        if (imbuement.items.any { it.first == sourceId }) {
            logger.warn { "Duplicate item: $sourceId, imbument name: ${imbuement.name} ignored" }
            return
        }
        imbuement.items += sourceId to count
    }

    private fun loadEffect(imbuement: Imbuement, node: Element) {
        if (!node.hasAttribute("type")) {
            logger.warn { "Missing effect type for imbuement name: ${imbuement.name}" }
            return
        }
        when (node.getAttribute("type").lowercase()) {
            "skill" -> loadSkillEffect(imbuement, node)
            "damage" -> loadDamageEffect(imbuement, node)
            "reduction" -> loadReductionEffect(imbuement, node)
            "speed" -> loadSpeedEffect(imbuement, node)
            "capacity" -> loadCapacityEffect(imbuement, node)
            "paralysis", "vibrancy" -> loadParalysisEffect(imbuement, node)
        }
    }

    private fun loadSkillEffect(imbuement: Imbuement, node: Element) {
        if (!node.hasAttribute("value")) {
            logger.warn { "Missing effect value for imbuement name ${imbuement.name}" }
            return
        }

        // 1 = skill normal, 2 = magiclevel, 3 = leechs/crit
        var useNormalSkill = 1
        val skillId = when (val skillName = node.getAttribute("value").lowercase()) {
            "sword" -> Skill.SKILL_SWORD.value
            "axe" -> Skill.SKILL_AXE.value
            "club" -> Skill.SKILL_CLUB.value
            "dist", "distance" -> Skill.SKILL_DISTANCE.value
            "fish" -> Skill.SKILL_FISHING.value
            "shield" -> Skill.SKILL_SHIELD.value
            "fist" -> Skill.SKILL_FIST.value
            "magicpoints" -> {
                useNormalSkill = 2
                Stat.STAT_MAGICPOINTS.value
            }

            "critical" -> {
                useNormalSkill = 3
                Skill.SKILL_CRITICAL_HIT_DAMAGE.value
            }

            "lifeleech" -> {
                useNormalSkill = 3
                Skill.SKILL_LIFE_LEECH_AMOUNT.value
            }

            "manaleech" -> {
                useNormalSkill = 3
                Skill.SKILL_MANA_LEECH_AMOUNT.value
            }

            else -> {
                logger.warn { "Unknow skill name $skillName in imbuement name ${imbuement.name}" }
                return
            }
        }

        if (!node.hasAttribute("bonus")) {
            logger.warn { "Missing skill bonus for imbuement name ${imbuement.name}" }
            return
        }
        val bonus = node.intAttribute("bonus")

        when (useNormalSkill) {
            1 -> imbuement.skills[skillId] = bonus
            2 -> imbuement.stats[skillId] = bonus
            else -> {
                imbuement.skills[skillId] = bonus
                val chance = if (node.hasAttribute("chance")) minOf(10000, node.intAttribute("chance")) else 100
                imbuement.skills[skillId - 1] = chance
            }
        }
    }

    private fun loadDamageEffect(imbuement: Imbuement, node: Element) {
        if (!node.hasAttribute("combat")) {
            logger.warn { "Missing combat for imbuement name ${imbuement.name}" }
            return
        }
        val combatType = getCombatTypeByName(node.getAttribute("combat"))
        if (combatType == CombatType.NONE) {
            logger.warn { "Unknown combat type for element ${node.getAttribute("combat")}" }
            return
        }
        if (!node.hasAttribute("value")) {
            logger.warn { "Missing damage reduction percentage for imbuement name ${imbuement.name}" }
            return
        }
        val percent = minOf(100, node.intAttribute("value"))
        imbuement.combatType = combatType
        imbuement.elementDamage = minOf(100, percent)
    }

    private fun loadReductionEffect(imbuement: Imbuement, node: Element) {
        if (!node.hasAttribute("combat")) {
            logger.warn { "Missing combat for imbuement name ${imbuement.name}" }
            return
        }
        val combatType = getCombatTypeByName(node.getAttribute("combat"))
        if (combatType == CombatType.NONE) {
            logger.warn { "Unknown combat type for element ${node.getAttribute("combat")}" }
            return
        }
        if (!node.hasAttribute("value")) {
            logger.warn { "Missing damage reduction percentage for imbuement name ${imbuement.name}" }
            return
        }
        val percent = minOf(100, node.intAttribute("value"))
        imbuement.absorbPercent[combatType.value] = percent
    }

    private fun loadSpeedEffect(imbuement: Imbuement, node: Element) {
        if (!node.hasAttribute("value")) {
            logger.warn { "Missing speed value for imbuement name ${imbuement.name}" }
            return
        }
        imbuement.speed = node.intAttribute("value")
    }

    private fun loadCapacityEffect(imbuement: Imbuement, node: Element) {
        if (!node.hasAttribute("value")) {
            logger.warn { "Missing cap value for imbuement name ${imbuement.name}" }
            return
        }
        imbuement.capacity = node.intAttribute("value")
    }

    private fun loadParalysisEffect(imbuement: Imbuement, node: Element) {
        val chancePercent = when {
            node.hasAttribute("chance") -> minOf(100, node.intAttribute("chance"))
            node.hasAttribute("value") -> minOf(100, node.intAttribute("value"))
            else -> {
                logger.warn { "Missing paralysis chance/value for imbuement name ${imbuement.name}" }
                return
            }
        }
        imbuement.paralysisRemoveChance = chancePercent
        val pvpDeflect = if (node.hasAttribute("pvpDeflect")) node.intAttribute("pvpDeflect") else 0
        imbuement.pvpParalysisDeflect = pvpDeflect != 0
    }

    companion object {
        /** `CORE_DIRECTORY + "/XML/imbuements.xml"` (imbuements.cpp:49). */
        val DEFAULT_PATH: Path = Path.of("data", "XML", "imbuements.xml")

        /**
         * `getCombatTypeByName` (src/utils/tools.cpp:1207) over `combatTypeNames`
         * (tools.cpp:862); the lookup is an exact, case-sensitive name match.
         */
        private val COMBAT_BY_NAME: Map<String, CombatType> = mapOf(
            "drown" to CombatType.DROWNDAMAGE,
            "death" to CombatType.DEATHDAMAGE,
            "energy" to CombatType.ENERGYDAMAGE,
            "earth" to CombatType.EARTHDAMAGE,
            "fire" to CombatType.FIREDAMAGE,
            "healing" to CombatType.HEALING,
            "holy" to CombatType.HOLYDAMAGE,
            "ice" to CombatType.ICEDAMAGE,
            "undefined" to CombatType.UNDEFINEDDAMAGE,
            "lifedrain" to CombatType.LIFEDRAIN,
            "manadrain" to CombatType.MANADRAIN,
            "physical" to CombatType.PHYSICALDAMAGE,
            "agony" to CombatType.AGONYDAMAGE,
            "neutral" to CombatType.NEUTRALDAMAGE
        )

        private fun getCombatTypeByName(combatName: String): CombatType =
            COMBAT_BY_NAME[combatName] ?: CombatType.NONE
    }
}

/**
 * `pugi::cast<int32_t>`: an absent or unparsable attribute reads as 0.
 * `toIntOrNull` tolerates the whitespace real XML files sometimes carry.
 */
private fun Element.intAttribute(name: String): Int =
    if (hasAttribute(name)) getAttribute(name).trim().toIntOrNull() ?: 0 else 0

/**
 * `pugi::xml_attribute::as_bool(def)`: "1"/"true" and "0"/"false" (case
 * insensitive for the words), otherwise the provided default.
 */
private fun Element.boolAttribute(name: String, default: Boolean): Boolean {
    if (!hasAttribute(name)) {
        return default
    }
    return when {
        getAttribute(name) == "1" || getAttribute(name).equals("true", ignoreCase = true) -> true
        getAttribute(name) == "0" || getAttribute(name).equals("false", ignoreCase = true) -> false
        else -> default
    }
}
