package org.opentibia.canary.systems.proficiency

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import org.opentibia.canary.config.FeatureFlags

private val logger = KotlinLogging.logger {}

// CombatType_t, ported verbatim from Canary src/creatures/creatures_definitions.hpp.
const val COMBAT_PHYSICALDAMAGE = 0
const val COMBAT_FIREDAMAGE = 1
const val COMBAT_EARTHDAMAGE = 2
const val COMBAT_ENERGYDAMAGE = 3
const val COMBAT_LIFEDRAIN = 5
const val COMBAT_MANADRAIN = 6
const val COMBAT_HEALING = 7
const val COMBAT_DROWNDAMAGE = 8
const val COMBAT_ICEDAMAGE = 9
const val COMBAT_HOLYDAMAGE = 10
const val COMBAT_DEATHDAMAGE = 11
const val COMBAT_AGONYDAMAGE = 12
const val COMBAT_NONE = 255

// skills_t, ported verbatim from Canary src/creatures/creatures_definitions.hpp.
const val SKILL_NONE = -1
const val SKILL_FIST = 0
const val SKILL_CLUB = 1
const val SKILL_SWORD = 2
const val SKILL_AXE = 3
const val SKILL_DISTANCE = 4
const val SKILL_SHIELD = 5
const val SKILL_FISHING = 6
const val SKILL_MAGLEVEL = 13

/**
 * Weapon Proficiencies JSON loader, ported from Canary
 * src/creatures/players/components/weapon_proficiency.cpp (loadFromJson / registerLevels /
 * registerPerks) plus the Cipbia helpers in src/utils/tools.cpp
 * (getCombatFromCipbiaElement / getSkillsFromCipbiaSkill / undoShift).
 *
 * The C++ side keeps a process-wide `proficiencies` map that reload() clears and refills; this
 * port returns the map instead of mutating global state, which keeps it dependency-injectable and
 * hermetic for tests. `maxLevels`/`maxPerksPerLevel` mirror the WEAPON_PROFICIENCY_MAX_LEVELS and
 * WEAPON_PROFICIENCY_MAX_PERKS_PER_LEVEL config numbers.
 */
object WeaponProficiencyLoader {
    const val DEFAULT_MAX_LEVELS = 10
    const val DEFAULT_MAX_PERKS_PER_LEVEL = 6

    val DEFAULT_PATH: Path = Path.of("data", "items", "proficiencies.json")

    private val mapper = ObjectMapper()

    fun loadFromJson(
        json: String,
        maxLevels: Int = DEFAULT_MAX_LEVELS,
        maxPerksPerLevel: Int = DEFAULT_MAX_PERKS_PER_LEVEL,
    ): Map<Int, Proficiency> {
        val root = mapper.readTree(json)
        val proficiencies = LinkedHashMap<Int, Proficiency>()
        for (proficiencyJson in root) {
            val proficiency = Proficiency()
            proficiency.id = proficiencyJson["ProficiencyId"].asInt()
            registerLevels(proficiencyJson["Levels"], proficiency, maxLevels, maxPerksPerLevel)
            proficiencies[proficiency.id] = proficiency
        }
        return proficiencies
    }

    fun loadFromFile(
        path: Path = DEFAULT_PATH,
        maxLevels: Int = DEFAULT_MAX_LEVELS,
        maxPerksPerLevel: Int = DEFAULT_MAX_PERKS_PER_LEVEL,
    ): Map<Int, Proficiency> = loadFromJson(Files.readString(path), maxLevels, maxPerksPerLevel)

    /**
     * `loadFromJson` driven by `WEAPON_PROFICIENCY_MAX_LEVELS` /
     * `WEAPON_PROFICIENCY_MAX_PERKS_PER_LEVEL` (configmanager.cpp:391-392).
     */
    fun loadFromJson(json: String, flags: FeatureFlags): Map<Int, Proficiency> =
        loadFromJson(json, flags.weaponProficiencyMaxLevels, flags.weaponProficiencyMaxPerksPerLevel)

    fun loadFromFile(path: Path = DEFAULT_PATH, flags: FeatureFlags): Map<Int, Proficiency> =
        loadFromFile(path, flags.weaponProficiencyMaxLevels, flags.weaponProficiencyMaxPerksPerLevel)

    fun reload(
        path: Path = DEFAULT_PATH,
        maxLevels: Int = DEFAULT_MAX_LEVELS,
        maxPerksPerLevel: Int = DEFAULT_MAX_PERKS_PER_LEVEL,
    ): Map<Int, Proficiency> = loadFromFile(path, maxLevels, maxPerksPerLevel)

    fun reload(path: Path = DEFAULT_PATH, flags: FeatureFlags): Map<Int, Proficiency> =
        reload(path, flags.weaponProficiencyMaxLevels, flags.weaponProficiencyMaxPerksPerLevel)

    private fun registerLevels(
        levelsJson: JsonNode,
        proficiency: Proficiency,
        maxLevels: Int,
        maxPerksPerLevel: Int,
    ) {
        var levelIndex = 0
        for (levelJson in levelsJson) {
            if (levelIndex >= maxLevels) {
                logger.error {
                    "registerLevels - Proficiency '${proficiency.id}' exceeded the maximum level, skipping levels above ${levelIndex + 1}"
                }
                break
            }

            val proficiencyLevel = ProficiencyLevel()
            registerPerks(levelJson["Perks"], proficiencyLevel, maxPerksPerLevel)

            for (perk in proficiencyLevel.perks) {
                perk.level = levelIndex
            }

            proficiency.level.add(proficiencyLevel)
            levelIndex++
        }

        proficiency.maxLevel = levelIndex
    }

    private fun registerPerks(perksJson: JsonNode, proficiencyLevel: ProficiencyLevel, maxPerks: Int) {
        var perkIndex = 0
        for (perkJson in perksJson) {
            if (perkIndex >= maxPerks) {
                logger.error { "registerPerks - Proficiency level exceeded the maximum perks, skipping perk index above ${perkIndex + 1}" }
                break
            }

            val proficiencyPerk = ProficiencyPerk(element = COMBAT_NONE, skillId = SKILL_NONE)
            proficiencyPerk.type = bonusFromValue(perkJson["Type"].asInt())
            proficiencyPerk.value = perkJson["Value"].asDouble()

            var shiftedValue = 0L
            if (proficiencyPerk.type == WeaponProficiencyBonus.SPECIALIZED_MAGIC_LEVEL) {
                shiftedValue = perkJson["DamageType"]?.asLong() ?: 0L
            } else if (
                proficiencyPerk.type == WeaponProficiencyBonus.ELEMENTAL_HIT_CHANCE ||
                proficiencyPerk.type == WeaponProficiencyBonus.ELEMENTAL_CRITICAL_EXTRA_DAMAGE ||
                proficiencyPerk.type == WeaponProficiencyBonus.ELEMENTAL_PIERCE
            ) {
                shiftedValue = perkJson["ElementId"]?.asLong() ?: 0L
            }

            if (shiftedValue > 0) {
                val unshiftedValue = undoShift(shiftedValue)
                proficiencyPerk.element = getCombatFromCipbiaElement(unshiftedValue)
            }

            if (perkJson.has("Range")) {
                proficiencyPerk.range = perkJson["Range"].asInt()
            }
            if (perkJson.has("AugmentType")) {
                proficiencyPerk.augmentType = perkJson["AugmentType"].asInt()
            }
            if (perkJson.has("SpellId")) {
                proficiencyPerk.spellId = perkJson["SpellId"].asInt()
            }
            if (perkJson.has("SkillId")) {
                val skill = perkJson["SkillId"].asInt()
                if (skill in CIPBIA_SKILLS) {
                    proficiencyPerk.skillId = getSkillsFromCipbiaSkill(skill)
                } else {
                    logger.error { "registerPerks - Invalid skill id $skill, skipping skill register" }
                }
            }
            if (perkJson.has("BestiaryId")) {
                proficiencyPerk.bestiaryId = perkJson["BestiaryId"].asInt()
            }
            if (perkJson.has("BestiaryName")) {
                proficiencyPerk.bestiaryName = perkJson["BestiaryName"].asText()
            }

            proficiencyPerk.index = perkIndex

            proficiencyLevel.perks.add(proficiencyPerk)
            perkIndex++
        }
    }

    private val BONUSES_BY_VALUE: Map<Int, WeaponProficiencyBonus> =
        WeaponProficiencyBonus.entries.associateBy { it.value }

    private fun bonusFromValue(value: Int): WeaponProficiencyBonus =
        BONUSES_BY_VALUE[value] ?: throw IllegalArgumentException("Invalid weapon proficiency bonus type: $value")

    // CipbiaSkills_t values, from Canary src/utils/utils_definitions.hpp.
    private val CIPBIA_SKILLS = setOf(0, 1, 6, 7, 8, 9, 10, 11, 13)

    // tools.cpp getSkillsFromCipbiaSkill.
    private fun getSkillsFromCipbiaSkill(cipbiaSkill: Int): Int = when (cipbiaSkill) {
        1 -> SKILL_MAGLEVEL
        6 -> SKILL_SHIELD
        7 -> SKILL_DISTANCE
        8 -> SKILL_SWORD
        9 -> SKILL_CLUB
        10 -> SKILL_AXE
        11 -> SKILL_FIST
        13 -> SKILL_FISHING
        else -> SKILL_NONE
    }

    // tools.cpp getCombatFromCipbiaElement, keyed by Cipbia_Elementals_t.
    private fun getCombatFromCipbiaElement(cipbiaElement: Int): Int = when (cipbiaElement) {
        0 -> COMBAT_PHYSICALDAMAGE
        1 -> COMBAT_FIREDAMAGE
        2 -> COMBAT_EARTHDAMAGE
        3 -> COMBAT_ENERGYDAMAGE
        4 -> COMBAT_ICEDAMAGE
        5 -> COMBAT_HOLYDAMAGE
        6 -> COMBAT_DEATHDAMAGE
        7 -> COMBAT_HEALING
        8 -> COMBAT_DROWNDAMAGE
        9 -> COMBAT_LIFEDRAIN
        10 -> COMBAT_MANADRAIN
        11 -> COMBAT_AGONYDAMAGE
        18 -> COMBAT_HEALING
        else -> COMBAT_NONE
    }

    // tools.cpp undoShift: strip the two low bits of the shifted Cipbia element index.
    private fun undoShift(value: Long): Int {
        if (value == 0L) {
            return 0
        }
        val trailingZeros = java.lang.Long.numberOfTrailingZeros(value)
        if (trailingZeros < 2) {
            return 0
        }
        return trailingZeros - 2
    }
}
