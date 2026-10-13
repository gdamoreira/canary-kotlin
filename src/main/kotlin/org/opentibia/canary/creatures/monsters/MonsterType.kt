package org.opentibia.canary.creatures.monsters

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.combat.condition.CombatType
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.systems.bestiary.BestiaryRace
import java.util.TreeMap

private val logger = KotlinLogging.logger {}

/**
 * `enum RaceType_t` (`src/creatures/creatures_definitions.hpp:504-514`).
 */
enum class RaceType(val value: Int) {
    RACE_NONE(0),
    RACE_VENOM(1),
    RACE_BLOOD(2),
    RACE_UNDEAD(3),
    RACE_FIRE(4),
    RACE_ENERGY(5),
    RACE_INK(6),
    RACE_CHOCOLATE(7),
    RACE_CANDY(8),
}

/**
 * `enum RespawnPeriod_t` (`src/creatures/creatures_definitions.hpp:367-371`).
 */
enum class RespawnPeriod(val value: Int) {
    RESPAWNPERIOD_ALL(0),
    RESPAWNPERIOD_DAY(1),
    RESPAWNPERIOD_NIGHT(2),
}

/**
 * `enum MonstersEvent_t` (`src/creatures/creatures_definitions.hpp:553-562`).
 */
enum class MonstersEvent(val value: Int) {
    MONSTERS_EVENT_NONE(0),
    MONSTERS_EVENT_THINK(1),
    MONSTERS_EVENT_APPEAR(2),
    MONSTERS_EVENT_DISAPPEAR(3),
    MONSTERS_EVENT_MOVE(4),
    MONSTERS_EVENT_SAY(5),
    MONSTERS_EVENT_ATTACKED_BY_PLAYER(6),
    MONSTERS_EVENT_ON_SPAWN(7),
}

/**
 * Full `enum class BosstiaryRarity_t` (`src/io/io_bosstiary.hpp:12-19`), including
 * `BOSS_INVALID` (the `MonsterInfo.bosstiaryRace` default) which the
 * weapon-proficiency subset does not carry.
 */
enum class BosstiaryRarity_t(val value: Int) {
    RARITY_BANE(0),
    RARITY_ARCHFOE(1),
    RARITY_NEMESIS(2),
    BOSS_INVALID(10),
}

/**
 * `struct RespawnType` (`src/creatures/creatures_definitions.hpp:1760-1763`).
 * The C++ aggregate value-initializes to `RESPAWNPERIOD_ALL`/`false`.
 */
data class RespawnType(
    var period: RespawnPeriod = RespawnPeriod.RESPAWNPERIOD_ALL,
    var underground: Boolean = false,
)

/**
 * `struct voiceBlock_t` (`src/creatures/monsters/monsters.hpp:1852-1855`).
 */
data class VoiceBlock(
    var text: String = "",
    var yellText: Boolean = false,
)

/**
 * `struct summonBlock_t` (`src/creatures/monsters/monsters.hpp:1824-1830`).
 */
data class SummonBlock(
    var name: String = "",
    var chance: Int = 0,
    var speed: Int = 0,
    var count: Int = 0,
    var force: Boolean = false,
)

/**
 * `struct spellBlock_t` (`src/creatures/monsters/monsters.hpp:35-47`).
 *
 * [spell] is the `std::shared_ptr<BaseSpell>` slot. The `BaseSpell`/`CombatSpell`
 * hierarchy is not ported yet, so the slot stays an opaque reference (PARITY GAP).
 */
data class SpellBlock(
    var spell: Any? = null,
    var chance: Int = 100,
    var speed: Int = 2000,
    var range: Int = 0,
    var minCombatValue: Int = 0,
    var maxCombatValue: Int = 0,
    var combatSpell: Boolean = false,
    var isMelee: Boolean = false,
    var soundImpactEffect: SoundEffect = SoundEffect.SILENCE,
    var soundCastEffect: SoundEffect = SoundEffect.SILENCE,
)

/**
 * `struct MonsterInfo` (`src/creatures/monsters/monsters.hpp:50-157`).
 *
 * Every field keeps its Canary name and default. `scriptInterface` is the
 * `LuaScriptInterface*` used by `MonsterType::loadCallback`; the Lua registration
 * itself stays Lua (AGENTS.md 1.4), so the slot is an opaque reference.
 *
 * `m_conditionImmunities`/`m_damageImmunities` mirror the C++
 * `std::bitset<CONDITION_COUNT>`/`std::bitset<COMBAT_COUNT>` as sets.
 */
class MonsterInfo {
    var scriptInterface: Any? = null

    var elementMap: MutableMap<CombatType, Int> = TreeMap()
    var reflectMap: MutableMap<CombatType, Int> = TreeMap()
    var healingMap: MutableMap<CombatType, Int> = TreeMap()

    var voiceVector: MutableList<VoiceBlock> = mutableListOf()

    var lootItems: MutableList<LootBlock> = mutableListOf()
    var scripts: MutableSet<String> = sortedSetOf()
    var attackSpells: MutableList<SpellBlock> = mutableListOf()
    var defenseSpells: MutableList<SpellBlock> = mutableListOf()
    var summons: MutableList<SummonBlock> = mutableListOf()

    var skull: Skulls_t = Skulls_t.SKULL_NONE
    var outfit: Outfit = Outfit()
    var race: RaceType = RaceType.RACE_BLOOD
    var respawnType: RespawnType = RespawnType()

    var light: LightInfo = LightInfo()
    var lookcorpse: Int = 0
    var baseSpeed: Int = 110

    var experience: Long = 0

    var manaCost: Int = 0
    var yellChance: Int = 0
    var yellSpeedTicks: Int = 0
    var staticAttackChance: Int = 95
    var maxSummons: Int = 0
    var changeTargetSpeed: Int = 0

    var soulCore: Int = 0

    var m_conditionImmunities: MutableSet<ConditionType> = linkedSetOf()
    var m_damageImmunities: MutableSet<CombatType> = linkedSetOf()

    var bestiaryOccurrence: Int = 0
    var bestiaryStars: Int = 0
    var bestiaryToUnlock: Int = 0
    var bestiaryFirstUnlock: Int = 0
    var bestiarySecondUnlock: Int = 0
    var bestiaryCharmsPoints: Int = 0
    var raceid: Int = 0
    var bestiaryLocations: String = ""
    var bestiaryClass: String = ""
    var bestiaryRace: BestiaryRace = BestiaryRace.BESTY_RACE_NONE

    var bosstiaryRace: BosstiaryRarity_t = BosstiaryRarity_t.BOSS_INVALID
    var bosstiaryClass: String = ""

    var mitigation: Float = 0f

    var soundChance: Int = 0
    var soundSpeedTicks: Int = 0
    var soundVector: MutableList<SoundEffect> = mutableListOf()
    var deathSound: SoundEffect = SoundEffect.SILENCE

    var creatureAppearEvent: Int = -1
    var creatureDisappearEvent: Int = -1
    var creatureMoveEvent: Int = -1
    var creatureSayEvent: Int = -1
    var monsterAttackedByPlayerEvent: Int = -1
    var thinkEvent: Int = -1
    var spawnEvent: Int = -1
    var targetDistance: Int = 1
    var runAwayHealth: Int = 0
    var health: Int = 100
    var healthMax: Int = 100
    var changeTargetChance: Int = 0
    var defense: Int = 0
    var armor: Int = 0
    var critChance: Int = 0
    var strategiesTargetNearest: Int = 0
    var strategiesTargetHealth: Int = 0
    var strategiesTargetDamage: Int = 0
    var strategiesTargetRandom: Int = 0
    var targetPreferPlayer: Boolean = false
    var targetPreferMaster: Boolean = false

    var faction: Faction = Faction.Default
    var enemyFactions: MutableSet<Faction> = linkedSetOf()

    var canPushItems: Boolean = false
    var canPushCreatures: Boolean = false
    var pushable: Boolean = true
    var isSummonable: Boolean = false
    var isIllusionable: Boolean = false
    var isConvinceable: Boolean = false
    var isAttackable: Boolean = true
    var isHostile: Boolean = true
    var hiddenHealth: Boolean = false
    var isBlockable: Boolean = false
    var isFamiliar: Boolean = false
    var isRewardBoss: Boolean = false
    var canWalkOnEnergy: Boolean = true
    var canWalkOnFire: Boolean = true
    var canWalkOnPoison: Boolean = true
    var isForgeCreature: Boolean = true
    var isPreyable: Boolean = true
    var isPreyExclusive: Boolean = false

    var eventType: MonstersEvent = MonstersEvent.MONSTERS_EVENT_NONE
}

/**
 * `class MonsterType` (`src/creatures/monsters/monsters.hpp:49-192`).
 *
 * The C++ constructor seeds `name`, `typeName` and `nameDescription` from the
 * registration name (`monsters.hpp:161-162`).
 *
 * `getHealthMultiplier`/`getAttackMultiplier`/`getDefenseMultiplier`,
 * `loadLoot` and `canSpawn` are not ported yet (PARITY GAPS): they need the
 * `RATE_*` config, the item registry and the day/night game state.
 */
class MonsterType(name: String) {
    var name: String = name
    var typeName: String = name
    var nameDescription: String = name
    var variantName: String = ""

    val info: MonsterInfo = MonsterInfo()

    /** `MonsterType::getBaseSpeed` (`src/creatures/monsters/monsters.cpp:324-326`). */
    fun getBaseSpeed(): Int = info.baseSpeed

    /** `MonsterType::setBaseSpeed` (`src/creatures/monsters/monsters.cpp:328-330`). */
    fun setBaseSpeed(initBaseSpeed: Int) {
        info.baseSpeed = initBaseSpeed
    }

    /** `MonsterType::isBoss` (`src/creatures/monsters/monsters.cpp:344-346`). */
    fun isBoss(): Boolean = info.bosstiaryClass.isNotEmpty()

    /**
     * `MonsterType::canSpawn` (`src/creatures/monsters/monsters.cpp:35-45`): a
     * day/night-gated spawn only survives off-period when underground below the
     * surface layer (`MAP_INIT_SURFACE_LAYER`), see [org.opentibia.canary.creatures.Creature].
     */
    fun canSpawn(pos: Position, isDay: Boolean): Boolean {
        var canSpawn = true

        if ((isDay && info.respawnType.period == RespawnPeriod.RESPAWNPERIOD_NIGHT) ||
            (!isDay && info.respawnType.period == RespawnPeriod.RESPAWNPERIOD_DAY)
        ) {
            // It will ignore day and night if underground
            canSpawn = pos.z > Creature.MAP_INIT_SURFACE_LAYER && info.respawnType.underground
        }

        return canSpawn
    }
}

/**
 * `class Monsters` (`src/creatures/monsters/monsters.hpp:242-267`).
 *
 * Replaces the `g_monsters()` singleton with a constructor-injected registry
 * (AGENTS.md 1.1). `monsters` is a name-ordered map keyed by the lowercased
 * registration name, exactly like the C++ `std::map`.
 *
 * Reload semantics: `GameReload::reloadMonsters` (`src/game/functions/game_reload.cpp:211-226`)
 * calls [clear] and then re-runs the Lua monster scripts, which re-register through
 * [tryAddMonsterType]. [getReloadEpoch] is the generation the asynchronous target
 * pipeline checks (`src/creatures/monsters/monster.cpp:1773-1944`).
 *
 * `getMonsterTypeByRaceId`, `getMonstersByRace` and `getMonstersByBestiaryStars`
 * are not ported yet (PARITY GAPS): they need the bosstiary and bestiary stores.
 */
class Monsters {
    private val monsters: MutableMap<String, MonsterType> = TreeMap()

    /**
     * `Monsters::reloadEpoch` (`src/creatures/monsters/monsters.hpp:266`), a
     * `uint64_t` that wraps to 0 on increment and is reset to 1. The two's
     * complement `Long` representation wraps identically (`-1L + 1 == 0L`).
     * Visible for tests to exercise the wrap.
     */
    internal var reloadEpoch: Long = 1L

    /** `Monsters::clear` (`src/creatures/monsters/monsters.cpp:352-357`). */
    fun clear() {
        monsters.clear()
        if (++reloadEpoch == 0L) {
            reloadEpoch = 1L
        }
    }

    /** `Monsters::getReloadEpoch` (`src/creatures/monsters/monsters.cpp:359-361`). */
    fun getReloadEpoch(): Long = reloadEpoch

    /** `Monsters::getMonsterType` (`src/creatures/monsters/monsters.cpp:363-373`). */
    fun getMonsterType(name: String, silent: Boolean = false): MonsterType? {
        val lowerCaseName = name.lowercase()
        monsters[lowerCaseName]?.let { return it }
        if (!silent) {
            logger.error { "[Monsters::getMonsterType] - Monster with name $lowerCaseName not exist" }
        }
        return null
    }

    /** `Monsters::tryAddMonsterType` (`src/creatures/monsters/monsters.cpp:390-399`). */
    fun tryAddMonsterType(name: String, mType: MonsterType): Boolean {
        val lowerName = name.lowercase()
        if (monsters.containsKey(lowerName)) {
            logger.debug { "[tryAddMonsterType] the monster with name '$name' already exist" }
            return false
        }

        monsters[lowerName] = mType
        return true
    }
}
