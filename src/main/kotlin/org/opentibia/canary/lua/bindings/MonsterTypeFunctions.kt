package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterTypeScriptInterface
import org.opentibia.canary.creatures.monsters.MonstersEvent
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import java.util.WeakHashMap

/**
 * Port of the `spellBlock_t` fields exposed by
 * `MonsterTypeFunctions::luaMonsterTypeGetAttackList`
 * (src/lua/functions/creatures/monster/monster_type_functions.cpp:974). `spell` is the
 * optional `CombatSpell` userdata; the port does not model combat spells yet.
 */
data class MonsterAttackInfo(
    val chance: Int = 0,
    val isCombatSpell: Boolean = false,
    val isMelee: Boolean = false,
    val minCombatValue: Int = 0,
    val maxCombatValue: Int = 0,
    val range: Int = 0,
    val speed: Int = 0,
    val spell: Any? = null,
)

/**
 * Port of `voiceBlock_t` as exposed by `MonsterTypeFunctions::luaMonsterTypeGetVoices`
 * (src/lua/functions/creatures/monster/monster_type_functions.cpp:1159).
 */
data class MonsterVoiceInfo(
    val text: String = "",
    val yellText: Boolean = false,
)

/**
 * Port of `summonBlock_t` as exposed by `MonsterTypeFunctions::luaMonsterTypeGetSummonList`
 * (src/lua/functions/creatures/monster/monster_type_functions.cpp:1281). The list renderer
 * omits `count`, exactly like the C++ table.
 */
data class MonsterSummonInfo(
    val name: String = "",
    val speed: Int = 0,
    val chance: Int = 0,
)

/**
 * Engine seam for the `MonsterType` metatable. Mirrors the primitives in
 * src/lua/functions/creatures/monster/monster_type_functions.cpp.
 *
 * Getters return `null` when the receiver is not a type; setters return `null` for the
 * same case and `true`/`false` otherwise, matching Canary's `lua_pushnil` branches.
 */
interface MonsterTypeOperations {
    /** Mirrors `MonsterType(name or raceid)` resolving `g_monsters()`. */
    fun create(nameOrRaceId: Any): Any?
    fun getName(monsterType: Any): String?
    fun setName(monsterType: Any, name: String): Boolean?
    fun getHealth(monsterType: Any): Int?
    fun setHealth(monsterType: Any, health: Int): Boolean?
    fun getMaxHealth(monsterType: Any): Int?
    fun setMaxHealth(monsterType: Any, maxHealth: Int): Boolean?
    fun getExperience(monsterType: Any): Long?
    fun setExperience(monsterType: Any, experience: Long): Boolean?
    fun getBaseSpeed(monsterType: Any): Int?
    fun setBaseSpeed(monsterType: Any, speed: Int): Boolean?
    fun getRace(monsterType: Any): Int?
    fun setRace(monsterType: Any, race: Int): Boolean?
    fun getBestiaryClass(monsterType: Any): String?
    fun setBestiaryClass(monsterType: Any, bestiaryClass: String): Boolean?
    fun getCorpseId(monsterType: Any): Int?

    /** `monsterType:corpseId(id)`, the writable half of `getCorpseId`'s field. */
    fun setCorpseId(monsterType: Any, corpseId: Int): Boolean? = null
    fun getTypeName(monsterType: Any): String?
    fun isAttackable(monsterType: Any): Boolean?
    fun setAttackable(monsterType: Any, value: Boolean): Boolean?
    fun isSummonable(monsterType: Any): Boolean?
    fun setSummonable(monsterType: Any, value: Boolean): Boolean?
    fun isConvinceable(monsterType: Any): Boolean?
    fun setConvinceable(monsterType: Any, value: Boolean): Boolean?
    fun isHostile(monsterType: Any): Boolean?
    fun setHostile(monsterType: Any, value: Boolean): Boolean?
    fun isBlockable(monsterType: Any): Boolean?
    fun setBlockable(monsterType: Any, value: Boolean): Boolean?
    fun canSpawn(monsterType: Any, position: Position): Boolean?
    fun getLoot(monsterType: Any): List<LootData>?
    fun addLoot(monsterType: Any, loot: LootData): Boolean?
    fun getAttackList(monsterType: Any): List<MonsterAttackInfo>?

    /*
     * Methods below this point default to the not-found branch (`null`/empty) so seams
     * that do not model the state yet keep compiling; the Lua-visible contract of each
     * method is only complete once the implementing seam overrides it. All C++ line
     * references are into src/lua/functions/creatures/monster/monster_type_functions.cpp.
     */

    // Boolean state flags (get: monsterType:flag() set: monsterType:flag(bool)).
    fun isPreyable(monsterType: Any): Boolean? = null
    fun setPreyable(monsterType: Any, value: Boolean): Boolean? = null
    fun isPreyExclusive(monsterType: Any): Boolean? = null
    fun setPreyExclusive(monsterType: Any, value: Boolean): Boolean? = null
    fun isIllusionable(monsterType: Any): Boolean? = null
    fun setIllusionable(monsterType: Any, value: Boolean): Boolean? = null
    fun isPushable(monsterType: Any): Boolean? = null
    fun setPushable(monsterType: Any, value: Boolean): Boolean? = null
    fun isHealthHidden(monsterType: Any): Boolean? = null
    fun setHealthHidden(monsterType: Any, value: Boolean): Boolean? = null
    fun isForgeCreature(monsterType: Any): Boolean? = null
    fun setForgeCreature(monsterType: Any, value: Boolean): Boolean? = null
    fun isFamiliar(monsterType: Any): Boolean? = null
    fun setFamiliar(monsterType: Any, value: Boolean): Boolean? = null
    fun isRewardBoss(monsterType: Any): Boolean? = null
    fun setRewardBoss(monsterType: Any, value: Boolean): Boolean? = null
    fun canPushItems(monsterType: Any): Boolean? = null
    fun setCanPushItems(monsterType: Any, value: Boolean): Boolean? = null
    fun canPushCreatures(monsterType: Any): Boolean? = null
    fun setCanPushCreatures(monsterType: Any, value: Boolean): Boolean? = null
    fun canWalkOnEnergy(monsterType: Any): Boolean? = null
    fun setCanWalkOnEnergy(monsterType: Any, value: Boolean): Boolean? = null
    fun canWalkOnFire(monsterType: Any): Boolean? = null
    fun setCanWalkOnFire(monsterType: Any, value: Boolean): Boolean? = null
    fun canWalkOnPoison(monsterType: Any): Boolean? = null
    fun setCanWalkOnPoison(monsterType: Any, value: Boolean): Boolean? = null
    fun isTargetPreferPlayer(monsterType: Any): Boolean? = null
    fun setTargetPreferPlayer(monsterType: Any, value: Boolean): Boolean? = null
    fun isTargetPreferMaster(monsterType: Any): Boolean? = null
    fun setTargetPreferMaster(monsterType: Any, value: Boolean): Boolean? = null

    // Numeric and string attributes.
    fun getNameDescription(monsterType: Any): String? = null
    fun setNameDescription(monsterType: Any, value: String): Boolean? = null
    fun getRunHealth(monsterType: Any): Int? = null
    fun setRunHealth(monsterType: Any, value: Int): Boolean? = null
    fun getCritChance(monsterType: Any): Int? = null
    fun setCritChance(monsterType: Any, value: Int): Boolean? = null
    fun getFaction(monsterType: Any): Int? = null
    fun setFaction(monsterType: Any, value: Int): Boolean? = null
    fun getEnemyFactions(monsterType: Any): List<Int>? = null
    fun addEnemyFaction(monsterType: Any, faction: Int): Boolean? = null
    fun getRaceId(monsterType: Any): Int? = null
    fun setRaceId(monsterType: Any, raceId: Int): Boolean? = null

    /** Side effect of `monsterType:raceId(id)` mirroring `Game::addBestiaryList`. */
    fun addBestiaryList(raceId: Int, name: String) = Unit

    fun getBestiaryOccurrence(monsterType: Any): Int? = null
    fun setBestiaryOccurrence(monsterType: Any, value: Int): Boolean? = null
    fun getBestiaryLocations(monsterType: Any): String? = null
    fun setBestiaryLocations(monsterType: Any, value: String): Boolean? = null
    fun getBestiaryStars(monsterType: Any): Int? = null
    fun setBestiaryStars(monsterType: Any, value: Int): Boolean? = null
    fun getBestiaryCharmsPoints(monsterType: Any): Int? = null
    fun setBestiaryCharmsPoints(monsterType: Any, value: Int): Boolean? = null
    fun getBestiarySecondUnlock(monsterType: Any): Int? = null
    fun setBestiarySecondUnlock(monsterType: Any, value: Int): Boolean? = null
    fun getBestiaryFirstUnlock(monsterType: Any): Int? = null
    fun setBestiaryFirstUnlock(monsterType: Any, value: Int): Boolean? = null
    fun getBestiaryToKill(monsterType: Any): Int? = null
    fun setBestiaryToKill(monsterType: Any, value: Int): Boolean? = null
    fun getBestiaryRace(monsterType: Any): Int? = null
    fun setBestiaryRace(monsterType: Any, value: Int): Boolean? = null

    /** Indexed by `combatTypeToIndex`, one entry per `COMBAT_COUNT` (14). */
    fun getCombatImmunities(monsterType: Any): List<Boolean>? = null
    fun setCombatImmunity(monsterType: Any, combatType: Int): Boolean? = null

    /** Indexed by `ConditionType_t`, one entry per `CONDITION_COUNT` (40). */
    fun getConditionImmunities(monsterType: Any): List<Boolean>? = null
    fun setConditionImmunity(monsterType: Any, conditionType: Int): Boolean? = null

    fun getDefenseList(monsterType: Any): List<MonsterAttackInfo>? = null
    fun addAttack(monsterType: Any, spell: Any?): Boolean? = null
    fun addDefense(monsterType: Any, spell: Any?): Boolean? = null

    /** Keyed by `CombatType_t` value; ordered like the C++ `elementMap`. */
    fun getElementList(monsterType: Any): Map<Int, Int>? = null
    fun addElement(monsterType: Any, element: Int, percent: Int): Boolean? = null
    fun addReflect(monsterType: Any, element: Int, percent: Int): Boolean? = null
    fun addHealing(monsterType: Any, element: Int, percent: Int): Boolean? = null

    fun getVoices(monsterType: Any): List<MonsterVoiceInfo>? = null
    fun addVoice(monsterType: Any, text: String, interval: Int, chance: Int, yell: Boolean): Boolean? = null

    fun getCreatureEvents(monsterType: Any): List<String>? = null
    fun registerEvent(monsterType: Any, name: String): Boolean? = null

    /**
     * Mirrors `MonsterType::loadCallback` for the `onThink`/`onAppear`/... group:
     * the binding supplies the captured Lua callback as the script-interface
     * event (`LuaScriptInterface::getEvent()` hoisted out of the C++ call).
     */
    fun loadCallback(monsterType: Any, scriptInterface: Any?): Boolean? = null
    fun setEventType(monsterType: Any, eventType: Int): Boolean? = null

    fun getSummonList(monsterType: Any): List<MonsterSummonInfo>? = null
    fun addSummon(monsterType: Any, name: String, speed: Int, chance: Int, count: Int): Boolean? = null
    fun getMaxSummons(monsterType: Any): Int? = null
    fun setMaxSummons(monsterType: Any, value: Int): Boolean? = null

    fun getArmor(monsterType: Any): Int? = null
    fun setArmor(monsterType: Any, value: Int): Boolean? = null
    fun getMitigation(monsterType: Any): Float? = null
    fun setMitigation(monsterType: Any, value: Float): Boolean? = null
    fun getDefense(monsterType: Any): Int? = null
    fun setDefense(monsterType: Any, value: Int): Boolean? = null
    fun getOutfit(monsterType: Any): Outfit? = null

    /** Returns `false` when the look type is rejected like Canary's unsafe-script guard. */
    fun setOutfit(monsterType: Any, outfit: Outfit): Boolean? = null

    fun getManaCost(monsterType: Any): Int? = null
    fun setManaCost(monsterType: Any, value: Int): Boolean? = null
    fun getLight(monsterType: Any): LightInfo? = null
    fun setLight(monsterType: Any, color: Int, level: Int): Boolean? = null
    fun getStaticAttackChance(monsterType: Any): Int? = null
    fun setStaticAttackChance(monsterType: Any, value: Int): Boolean? = null
    fun getTargetDistance(monsterType: Any): Int? = null
    fun setTargetDistance(monsterType: Any, value: Int): Boolean? = null
    fun getYellChance(monsterType: Any): Int? = null
    fun setYellChance(monsterType: Any, value: Int): Boolean? = null
    fun getYellSpeedTicks(monsterType: Any): Int? = null
    fun setYellSpeedTicks(monsterType: Any, value: Int): Boolean? = null
    fun getChangeTargetChance(monsterType: Any): Int? = null
    fun setChangeTargetChance(monsterType: Any, value: Int): Boolean? = null
    fun getChangeTargetSpeed(monsterType: Any): Int? = null
    fun setChangeTargetSpeed(monsterType: Any, value: Int): Boolean? = null

    fun getStrategiesTargetNearest(monsterType: Any): Int? = null
    fun setStrategiesTargetNearest(monsterType: Any, value: Int): Boolean? = null
    fun getStrategiesTargetHealth(monsterType: Any): Int? = null
    fun setStrategiesTargetHealth(monsterType: Any, value: Int): Boolean? = null
    fun getStrategiesTargetDamage(monsterType: Any): Int? = null
    fun setStrategiesTargetDamage(monsterType: Any, value: Int): Boolean? = null
    fun getStrategiesTargetRandom(monsterType: Any): Int? = null
    fun setStrategiesTargetRandom(monsterType: Any, value: Int): Boolean? = null

    fun getRespawnTypePeriod(monsterType: Any): Int? = null
    fun setRespawnTypePeriod(monsterType: Any, period: Int): Boolean? = null
    fun isRespawnTypeUnderground(monsterType: Any): Boolean? = null
    fun setRespawnTypeUnderground(monsterType: Any, underground: Boolean): Boolean? = null

    /** `nil` when `bosstiaryClass` is empty, mirroring the C++ getter. */
    fun getBossRace(monsterType: Any): String? = null
    fun setBossRace(monsterType: Any, raceId: Int, bossClass: String): Boolean? = null
    fun getBossRaceId(monsterType: Any): Int? = null
    fun setBossRaceId(monsterType: Any, raceId: Int): Boolean? = null

    /** Side effect of `monsterType:bossRaceId(id)` mirroring `IOBosstiary::addBosstiaryMonster`. */
    fun addBosstiaryMonster(raceId: Int, name: String) = Unit

    fun getSoundChance(monsterType: Any): Int? = null
    fun setSoundChance(monsterType: Any, value: Int): Boolean? = null
    fun getSoundSpeedTicks(monsterType: Any): Int? = null
    fun setSoundSpeedTicks(monsterType: Any, value: Int): Boolean? = null
    fun addSound(monsterType: Any, soundId: Int): Boolean? = null
    fun getSounds(monsterType: Any): List<Int>? = null
    fun getDeathSound(monsterType: Any): Int? = null
    fun setDeathSound(monsterType: Any, value: Int): Boolean? = null
    fun getVariant(monsterType: Any): String? = null
    fun setVariant(monsterType: Any, value: String): Boolean? = null

    /** `getMonstersByRace`/`getMonstersByBestiaryStars` query the catalog, not a receiver. */
    fun getMonstersByRace(race: Int): List<Any> = emptyList()
    fun getMonstersByBestiaryStars(stars: Int): List<Any> = emptyList()
}

/**
 * Registers the Canary `MonsterType` methods, delegating every attribute to
 * [MonsterTypeOperations]:
 *
 *   MonsterType(name or raceid)
 *   monsterType:isAttackable/isConvinceable/isSummonable/isPreyable/isPreyExclusive
 *     /isIllusionable/isHostile/isPushable/isHealthHidden/isBlockable/isForgeCreature
 *     /familiar/isRewardBoss/canPushItems/canPushCreatures/canWalkOnEnergy/canWalkOnFire
 *     /canWalkOnPoison/targetPreferPlayer/targetPreferMaster([bool])
 *   monsterType:name/nameDescription/Bestiaryclass/BestiaryLocations([string])
 *   monsterType:health/maxHealth/runHealth/experience/raceId/BestiaryOccurrence
 *     /BestiaryStars/BestiaryCharmsPoints/BestiarySecondUnlock/BestiaryFirstUnlock
 *     /BestiarytoKill/Bestiaryrace/faction/critChance/armor/mitigation/defense
 *     /manaCost/baseSpeed/staticAttackChance/targetDistance/yellChance/yellSpeedTicks
 *     /changeTargetChance/changeTargetSpeed/strategiesTargetNearest/strategiesTargetHealth
 *     /strategiesTargetDamage/strategiesTargetRandom/respawnTypePeriod
 *     /respawnTypeIsUnderground/maxSummons/soundChance/soundSpeedTicks/deathSound
 *     /variant([number or string])
 *   monsterType:race([race])
 *   monsterType:corpseId([id])
 *   monsterType:getCorpseId()
 *   monsterType:getTypeName()
 *   monsterType:light([color, level]) -> getter returns level, color
 *   monsterType:outfit([table]) -> getter returns the Lua::pushOutfit table
 *   monsterType:enemyFactions([faction])
 *   monsterType:combatImmunities([immunity])/conditionImmunities([immunity])
 *   monsterType:canSpawn(position)
 *   monsterType:getAttackList()/getDefenseList()/addAttack(spell)/addDefense(spell)
 *   monsterType:getElementList()/addElement(type, percent)/addReflect(type, percent)
 *     /addHealing(type, percent)
 *   monsterType:getVoices()/addVoice(sentence, interval, chance, yell)
 *   monsterType:getLoot()/addLoot(loot)
 *   monsterType:getCreatureEvents()/registerEvent(name)
 *   monsterType:onThink/onAppear/onDisappear/onMove/onSay/onPlayerAttack/onSpawn(callback)
 *   monsterType.onThink/onAppear/... = callback (revscriptsys `MonsterTypeNewIndex`)
 *   monsterType:eventType(event)
 *   monsterType:getSummonList()/addSummon(name, interval, chance[, count = 1])
 *   monsterType:bossRace([raceId, class])/bossRaceId([raceId])
 *   monsterType:addSound(soundId)/getSounds()
 *   monsterType:getMonstersByRace(race)/getMonstersByBestiaryStars(stars)
 *
 * `getLoot`/`getAttackList`/`getDefenseList` render the same Lua tables as Canary's
 * `createMonsterTypeLootLuaTable`/`luaMonsterTypeGetAttackList`/
 * `luaMonsterTypeGetDefenseList`, including the `maxCount`/`minCount`/`childLoot` loot
 * shape and the `combatSpell` spell-block shape.
 *
 * Documented fidelity quirks kept from the C++ source:
 *  - `targetPreferMaster`'s getter returns `info.faction`
 *    (monster_type_functions.cpp:664-678);
 *  - `getSounds` returns only the last per-sound inner table because of the C++
 *    stack usage (monster_type_functions.cpp:1869-1886);
 *  - `getOutfit`'s `lookWing`/`lookEffect`/`lookShader` keys carry trailing spaces and
 *    `Lua::getOutfit` loads `lookWing` from the `lookShader` field;
 *  - `getMonstersByRace`/`getMonstersByBestiaryStars` read argument 1 instead of a
 *    receiver (monster_type_functions.cpp:1926-1952);
 *  - `addAttack`/`addDefense` always evaluate to `nil`, even on success, because the
 *    C++ never pushes a result.
 */
object MonsterTypeFunctions {
    const val MONSTER_TYPE_TYPE = "MonsterType"
    const val COMBAT_SPELL_TYPE = "CombatSpell"

    private const val RACE_VENOM = 1
    private const val RACE_BLOOD = 2
    private const val RACE_UNDEAD = 3
    private const val RACE_FIRE = 4
    private const val RACE_ENERGY = 5
    private const val RACE_INK = 6
    private const val RACE_CHOCOLATE = 7
    private const val RACE_CANDY = 8

    /** `CombatType_t` values used by `luaMonsterTypeCombatImmunities`'s name map. */
    private const val COMBAT_PHYSICALDAMAGE = 0
    private const val COMBAT_FIREDAMAGE = 1
    private const val COMBAT_EARTHDAMAGE = 2
    private const val COMBAT_ENERGYDAMAGE = 3
    private const val COMBAT_LIFEDRAIN = 5
    private const val COMBAT_MANADRAIN = 6
    private const val COMBAT_DROWNDAMAGE = 8
    private const val COMBAT_ICEDAMAGE = 9
    private const val COMBAT_HOLYDAMAGE = 10
    private const val COMBAT_DEATHDAMAGE = 11
    private const val COMBAT_AGONYDAMAGE = 12
    private const val COMBAT_NEUTRALDAMAGE = 13

    /** `ConditionType_t` values used by `luaMonsterTypeConditionImmunities`'s name map. */
    private const val CONDITION_POISON = 1
    private const val CONDITION_FIRE = 2
    private const val CONDITION_ENERGY = 3
    private const val CONDITION_BLEEDING = 4
    private const val CONDITION_PARALYZE = 6
    private const val CONDITION_OUTFIT = 7
    private const val CONDITION_INVISIBLE = 8
    private const val CONDITION_DRUNK = 12
    private const val CONDITION_DROWN = 16
    private const val CONDITION_FREEZING = 21
    private const val CONDITION_DAZZLED = 22
    private const val CONDITION_CURSED = 23

    /**
     * `MonsterTypeNewIndex` event keys (`data/libs/functions/revscriptsys.lua:272-304`):
     * the datapack assigns `mtype.onSpawn = function(...)` and the hook maps the key
     * onto `MonsterType:eventType` plus the matching `on<Event>` call.
     */
    private val MONSTER_EVENT_BY_KEY: Map<String, MonstersEvent> = mapOf(
        "onThink" to MonstersEvent.MONSTERS_EVENT_THINK,
        "onAppear" to MonstersEvent.MONSTERS_EVENT_APPEAR,
        "onDisappear" to MonstersEvent.MONSTERS_EVENT_DISAPPEAR,
        "onMove" to MonstersEvent.MONSTERS_EVENT_MOVE,
        "onSay" to MonstersEvent.MONSTERS_EVENT_SAY,
        "onPlayerAttack" to MonstersEvent.MONSTERS_EVENT_ATTACKED_BY_PLAYER,
        "onSpawn" to MonstersEvent.MONSTERS_EVENT_ON_SPAWN,
    )

    fun install(env: LuaEnvironment, operations: MonsterTypeOperations = UnwiredMonsterTypeOperations) {
        // One `LuaScriptInterface` per registration script, exactly like the C++
        // `LuaScriptInterface` owned by the file that registers the monster type:
        // every callback captured for one `MonsterType` shares the interface, so
        // the event ids of different callbacks do not collide. The weak keys keep
        // a discarded type from retaining the environment.
        val scriptInterfaces = WeakHashMap<Any, LuaMonsterTypeScriptInterface>()

        env.userdata.registerClass(MONSTER_TYPE_TYPE, "", luaFunction { args ->
            val argument = args.arg(1)
            val monsterType = when {
                argument.isnumber() -> operations.create(argument.toint())
                argument.isstring() -> operations.create(argument.tojstring())
                else -> null
            }
            monsterType?.let { env.userdata.wrap(MONSTER_TYPE_TYPE, it) } ?: LuaValue.NIL
        })

        registerStringAttribute(env, "name", operations::getName, operations::setName)
        registerIntAttribute(env, "health", operations::getHealth, operations::setHealth)
        registerIntAttribute(env, "maxHealth", operations::getMaxHealth, operations::setMaxHealth)
        registerLongAttribute(env, "experience", operations::getExperience, operations::setExperience)
        registerIntAttribute(env, "baseSpeed", operations::getBaseSpeed, operations::setBaseSpeed)
        registerStringAttribute(env, "Bestiaryclass", operations::getBestiaryClass, operations::setBestiaryClass)

        registerBooleanAttribute(env, "isAttackable", operations::isAttackable, operations::setAttackable)
        registerBooleanAttribute(env, "isSummonable", operations::isSummonable, operations::setSummonable)
        registerBooleanAttribute(env, "isConvinceable", operations::isConvinceable, operations::setConvinceable)
        registerBooleanAttribute(env, "isHostile", operations::isHostile, operations::setHostile)
        registerBooleanAttribute(env, "isBlockable", operations::isBlockable, operations::setBlockable)
        registerBooleanAttribute(env, "isPreyable", operations::isPreyable, operations::setPreyable)
        registerBooleanAttribute(env, "isPreyExclusive", operations::isPreyExclusive, operations::setPreyExclusive)
        registerBooleanAttribute(env, "isIllusionable", operations::isIllusionable, operations::setIllusionable)
        registerBooleanAttribute(env, "isPushable", operations::isPushable, operations::setPushable)
        registerBooleanAttribute(env, "isHealthHidden", operations::isHealthHidden, operations::setHealthHidden)
        registerBooleanAttribute(env, "isForgeCreature", operations::isForgeCreature, operations::setForgeCreature)
        registerBooleanAttribute(env, "familiar", operations::isFamiliar, operations::setFamiliar)
        registerBooleanAttribute(env, "isRewardBoss", operations::isRewardBoss, operations::setRewardBoss)
        registerBooleanAttribute(env, "canPushItems", operations::canPushItems, operations::setCanPushItems)
        registerBooleanAttribute(env, "canPushCreatures", operations::canPushCreatures, operations::setCanPushCreatures)
        registerBooleanAttribute(env, "canWalkOnEnergy", operations::canWalkOnEnergy, operations::setCanWalkOnEnergy)
        registerBooleanAttribute(env, "canWalkOnFire", operations::canWalkOnFire, operations::setCanWalkOnFire)
        registerBooleanAttribute(env, "canWalkOnPoison", operations::canWalkOnPoison, operations::setCanWalkOnPoison)
        registerBooleanAttribute(env, "targetPreferPlayer", operations::isTargetPreferPlayer, operations::setTargetPreferPlayer)

        // Canary's luaMonsterTypeTargetPreferMaster getter pushes info.faction
        // (monster_type_functions.cpp:669-673) instead of the stored boolean, so the
        // port reads the faction seam in the getter branch.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "targetPreferMaster", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                operations.getFaction(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                operations.setTargetPreferMaster(monsterType, args.arg(2).toboolean())?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.NIL
            }
        })

        registerStringAttribute(env, "nameDescription", operations::getNameDescription, operations::setNameDescription)
        registerIntAttribute(env, "runHealth", operations::getRunHealth, operations::setRunHealth)
        registerIntAttribute(env, "manaCost", operations::getManaCost, operations::setManaCost)
        registerIntAttribute(env, "armor", operations::getArmor, operations::setArmor)
        registerIntAttribute(env, "defense", operations::getDefense, operations::setDefense)
        registerIntAttribute(env, "staticAttackChance", operations::getStaticAttackChance, operations::setStaticAttackChance)
        registerIntAttribute(env, "targetDistance", operations::getTargetDistance, operations::setTargetDistance)
        registerIntAttribute(env, "yellChance", operations::getYellChance, operations::setYellChance)
        registerIntAttribute(env, "yellSpeedTicks", operations::getYellSpeedTicks, operations::setYellSpeedTicks)
        registerIntAttribute(env, "changeTargetChance", operations::getChangeTargetChance, operations::setChangeTargetChance)
        registerIntAttribute(env, "changeTargetSpeed", operations::getChangeTargetSpeed, operations::setChangeTargetSpeed)
        registerIntAttribute(
            env,
            "strategiesTargetNearest",
            operations::getStrategiesTargetNearest,
            operations::setStrategiesTargetNearest,
        )
        registerIntAttribute(
            env,
            "strategiesTargetHealth",
            operations::getStrategiesTargetHealth,
            operations::setStrategiesTargetHealth,
        )
        registerIntAttribute(
            env,
            "strategiesTargetDamage",
            operations::getStrategiesTargetDamage,
            operations::setStrategiesTargetDamage,
        )
        registerIntAttribute(
            env,
            "strategiesTargetRandom",
            operations::getStrategiesTargetRandom,
            operations::setStrategiesTargetRandom,
        )
        registerIntAttribute(env, "respawnTypePeriod", operations::getRespawnTypePeriod, operations::setRespawnTypePeriod)
        registerIntAttribute(env, "maxSummons", operations::getMaxSummons, operations::setMaxSummons)
        registerIntAttribute(env, "faction", operations::getFaction, operations::setFaction)
        registerIntAttribute(env, "corpseId", operations::getCorpseId, operations::setCorpseId)
        registerIntAttribute(env, "BestiaryOccurrence", operations::getBestiaryOccurrence, operations::setBestiaryOccurrence)
        registerStringAttribute(env, "BestiaryLocations", operations::getBestiaryLocations, operations::setBestiaryLocations)
        registerIntAttribute(env, "BestiaryStars", operations::getBestiaryStars, operations::setBestiaryStars)
        registerIntAttribute(
            env,
            "BestiaryCharmsPoints",
            operations::getBestiaryCharmsPoints,
            operations::setBestiaryCharmsPoints,
        )
        registerIntAttribute(
            env,
            "BestiarySecondUnlock",
            operations::getBestiarySecondUnlock,
            operations::setBestiarySecondUnlock,
        )
        registerIntAttribute(
            env,
            "BestiaryFirstUnlock",
            operations::getBestiaryFirstUnlock,
            operations::setBestiaryFirstUnlock,
        )
        registerIntAttribute(env, "BestiarytoKill", operations::getBestiaryToKill, operations::setBestiaryToKill)
        registerIntAttribute(env, "Bestiaryrace", operations::getBestiaryRace, operations::setBestiaryRace)

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "race", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                operations.getRace(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                val race = raceIdByName(args.arg(2).tojstring()) ?: return@luaFunction LuaValue.NIL
                operations.setRace(monsterType, race)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getCorpseId", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getCorpseId(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getTypeName", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getTypeName(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "canSpawn", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val position = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            operations.canSpawn(monsterType, position)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getLoot", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val loot = operations.getLoot(monsterType) ?: return@luaFunction LuaValue.NIL
            lootTable(loot)
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "addLoot", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val loot = retrieveUserdata(args, 2) as? LootData ?: return@luaFunction LuaValue.NIL
            operations.addLoot(monsterType, loot)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getAttackList", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val attacks = operations.getAttackList(monsterType) ?: return@luaFunction LuaValue.NIL
            attackListTable(env, attacks)
        })

        // luaMonsterTypeRaceid (monster_type_functions.cpp:680) writes the race id and
        // additionally feeds g_game().addBestiaryList with the type's name.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "raceId", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                operations.getRaceId(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                val raceId = args.arg(2).toint()
                val result = operations.setRaceId(monsterType, raceId) ?: return@luaFunction LuaValue.NIL
                operations.getName(monsterType)?.let { operations.addBestiaryList(raceId, it) }
                LuaValue.valueOf(result)
            }
        })

        // luaMonsterTypeCritChance (monster_type_functions.cpp:488) sets when called with
        // exactly one argument and always returns the current value.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "critChance", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 2) {
                operations.setCritChance(monsterType, args.arg(2).toint())
            }
            operations.getCritChance(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        // luaMonsterTypeMitigation (monster_type_functions.cpp:1350) reports `false` for
        // a missing receiver, unlike the other numeric attributes.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "mitigation", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            if (args.narg() == 1) {
                operations.getMitigation(monsterType)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.FALSE
            } else {
                operations.setMitigation(monsterType, args.arg(2).tofloat())?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.FALSE
            }
        })

        // luaMonsterTypeLight (monster_type_functions.cpp:1492) returns level then color.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "light", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                val light = operations.getLight(monsterType) ?: return@luaFunction LuaValue.NIL
                LuaValue.varargsOf(arrayOf(LuaValue.valueOf(light.level), LuaValue.valueOf(light.color)))
            } else {
                val color = args.arg(2).toint()
                val level = args.arg(3).toint()
                operations.setLight(monsterType, color, level)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })

        // luaMonsterTypeOutfit (monster_type_functions.cpp:1384) rejects nothing itself;
        // the unsafe-script guard lives in the seam, which returns `false` to mean
        // "blocked", and the binding then answers `nil` like the C++ branch.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "outfit", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                operations.getOutfit(monsterType)?.let { pushOutfit(it) } ?: LuaValue.NIL
            } else {
                val outfit = luaToOutfit(args.arg(2)) ?: return@luaFunction LuaValue.NIL
                operations.setOutfit(monsterType, outfit)?.let { if (it) LuaValue.TRUE else LuaValue.NIL } ?: LuaValue.NIL
            }
        })

        // luaMonsterTypeEnemyFactions (monster_type_functions.cpp:625) lists the set.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "enemyFactions", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                val factions = operations.getEnemyFactions(monsterType) ?: return@luaFunction LuaValue.NIL
                LuaTable().also { table ->
                    factions.forEachIndexed { index, faction -> table.set(index + 1, LuaValue.valueOf(faction)) }
                }
            } else {
                operations.addEnemyFaction(monsterType, args.arg(2).toint())?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.NIL
            }
        })

        // luaMonsterTypeCombatImmunities (monster_type_functions.cpp:858) reports
        // `false` for a missing receiver; the getter table is indexed from 0.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "combatImmunities", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            if (args.narg() == 1) {
                val immunities = operations.getCombatImmunities(monsterType) ?: return@luaFunction LuaValue.FALSE
                immunityTable(immunities)
            } else {
                val combatType = combatTypeIndexByName(args.arg(2).tojstring()) ?: return@luaFunction LuaValue.NIL
                operations.setCombatImmunity(monsterType, combatType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })

        // luaMonsterTypeConditionImmunities (monster_type_functions.cpp:915) mirrors the
        // combat variant with the ConditionType_t index space.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "conditionImmunities", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            if (args.narg() == 1) {
                val immunities = operations.getConditionImmunities(monsterType) ?: return@luaFunction LuaValue.FALSE
                immunityTable(immunities)
            } else {
                val conditionType = conditionIndexByName(args.arg(2).tojstring()) ?: return@luaFunction LuaValue.NIL
                operations.setConditionImmunity(monsterType, conditionType)?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.NIL
            }
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getDefenseList", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val defenses = operations.getDefenseList(monsterType) ?: return@luaFunction LuaValue.NIL
            attackListTable(env, defenses)
        })

        // luaMonsterTypeAddAttack/luaMonsterTypeAddDefense (monster_type_functions.cpp:1004,
        // 1066) never push a result, so the Lua call always evaluates to `nil`.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "addAttack", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1)
            val spell = retrieveUserdata(args, 2)
            if (monsterType != null && spell != null) {
                operations.addAttack(monsterType, spell)
            }
            LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "addDefense", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1)
            val spell = retrieveUserdata(args, 2)
            if (monsterType != null && spell != null) {
                operations.addDefense(monsterType, spell)
            }
            LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getElementList", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val elements = operations.getElementList(monsterType) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                elements.forEach { (element, percent) -> table.set(element, LuaValue.valueOf(percent)) }
            }
        })

        registerPercentAttribute(env, "addElement", operations::addElement)
        registerPercentAttribute(env, "addReflect", operations::addReflect)
        registerPercentAttribute(env, "addHealing", operations::addHealing)

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getVoices", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val voices = operations.getVoices(monsterType) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                voices.forEachIndexed { index, voice ->
                    table.set(
                        index + 1,
                        LuaTable().also { row ->
                            row.set("text", LuaValue.valueOf(voice.text))
                            row.set("yellText", LuaValue.valueOf(voice.yellText))
                        },
                    )
                }
            }
        })

        // luaMonsterTypeAddVoice (monster_type_functions.cpp:1142) stores the interval
        // and chance on the type, then appends the text block.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "addVoice", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addVoice(
                monsterType,
                args.arg(2).tojstring(),
                args.arg(3).toint(),
                args.arg(4).toint(),
                args.arg(5).toboolean(),
            )?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getCreatureEvents", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val events = operations.getCreatureEvents(monsterType) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                events.forEachIndexed { index, name -> table.set(index + 1, LuaValue.valueOf(name)) }
            }
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "registerEvent", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.registerEvent(monsterType, args.arg(2).tojstring())?.let { LuaValue.valueOf(it) }
                ?: LuaValue.NIL
        })

        registerCallback(env, "onThink", operations, scriptInterfaces)
        registerCallback(env, "onAppear", operations, scriptInterfaces)
        registerCallback(env, "onDisappear", operations, scriptInterfaces)
        registerCallback(env, "onMove", operations, scriptInterfaces)
        registerCallback(env, "onSay", operations, scriptInterfaces)
        registerCallback(env, "onPlayerAttack", operations, scriptInterfaces)
        registerCallback(env, "onSpawn", operations, scriptInterfaces)

        // Mirrors the `MonsterTypeNewIndex` hook of
        // data/libs/functions/revscriptsys.lua:272-304, which maps the datapack's
        // `mtype.onSpawn = function(...)` field assignment onto `eventType` plus the
        // matching `on<Event>` method. The C++ LuaJIT userdata turns that assignment
        // into this metamethod call; unknown keys are ignored (LuaJ cannot rawset a
        // userdata field), which is also what WeaponFunctions/SpellFunctions do.
        env.userdata.registerMetaMethod(MONSTER_TYPE_TYPE, "__newindex", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NONE
            val event = MONSTER_EVENT_BY_KEY[args.arg(2).tojstring()]
            if (event != null) {
                operations.setEventType(monsterType, event.value)
                operations.loadCallback(monsterType, scriptInterfaceOf(env, scriptInterfaces, monsterType, args.arg(3)))
            }
            LuaValue.NONE
        })

        // luaMonsterTypeEventType (monster_type_functions.cpp:1269) always reads
        // argument 2, so a getter-style call stores MonstersEvent_t 0 and returns true.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "eventType", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setEventType(monsterType, args.arg(2).toint())?.let { LuaValue.TRUE } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getSummonList", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val summons = operations.getSummonList(monsterType) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                summons.forEachIndexed { index, summon ->
                    table.set(
                        index + 1,
                        LuaTable().also { row ->
                            row.set("name", LuaValue.valueOf(summon.name))
                            row.set("speed", LuaValue.valueOf(summon.speed))
                            row.set("chance", LuaValue.valueOf(summon.chance))
                        },
                    )
                }
            }
        })

        // luaMonsterTypeAddSummon (monster_type_functions.cpp:1301) reads `count` from
        // argument 5, defaulting to 1 only when it is omitted.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "addSummon", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val count = if (args.narg() >= 5) args.arg(5).toint() else 1
            operations.addSummon(
                monsterType,
                args.arg(2).tojstring(),
                args.arg(3).toint(),
                args.arg(4).toint(),
                count,
            )?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "respawnTypeIsUnderground", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                operations.isRespawnTypeUnderground(monsterType)?.let { LuaValue.valueOf(if (it) 1 else 0) }
                    ?: LuaValue.NIL
            } else {
                // The C++ setter assigns getNumber<RespawnPeriod_t>(L, 2) to the bool
                // field, so any non-zero number selects underground.
                operations.setRespawnTypeUnderground(monsterType, args.arg(2).toint() != 0)?.let {
                    LuaValue.valueOf(it)
                } ?: LuaValue.NIL
            }
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "bossRace", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                val bossClass = operations.getBossRace(monsterType)
                if (bossClass.isNullOrEmpty()) LuaValue.NIL else LuaValue.valueOf(bossClass)
            } else {
                operations.setBossRace(monsterType, args.arg(2).toint(), args.arg(3).tojstring())?.let {
                    LuaValue.valueOf(it)
                } ?: LuaValue.NIL
            }
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "bossRaceId", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                operations.getBossRaceId(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                val raceId = args.arg(2).toint()
                val result = operations.setBossRaceId(monsterType, raceId) ?: return@luaFunction LuaValue.NIL
                operations.addBosstiaryMonster(raceId, operations.getTypeName(monsterType) ?: "")
                LuaValue.valueOf(result)
            }
        })

        registerFalseDefaultedInt(env, "soundChance", operations::getSoundChance, operations::setSoundChance)
        registerFalseDefaultedInt(env, "soundSpeedTicks", operations::getSoundSpeedTicks, operations::setSoundSpeedTicks)
        registerFalseDefaultedInt(env, "deathSound", operations::getDeathSound, operations::setDeathSound)

        // luaMonsterTypeAddSound (monster_type_functions.cpp:1855) reports `false` for
        // a missing receiver.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "addSound", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            operations.addSound(monsterType, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
        })

        // Mirrors the C++ stack behavior of luaMonsterTypeGetSounds
        // (monster_type_functions.cpp:1877-1885): every iteration allocates a new inner
        // table, so only the last one is returned; with no sounds the outer empty table
        // is returned.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getSounds", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val sounds = operations.getSounds(monsterType) ?: return@luaFunction LuaValue.NIL
            var returned: LuaTable? = null
            sounds.forEachIndexed { index, sound ->
                returned = LuaTable().also { it.set(index + 1, LuaValue.valueOf(sound)) }
            }
            returned ?: LuaTable()
        })

        // luaMonsterTypeVariant (monster_type_functions.cpp:1907) reports `false` for a
        // missing receiver.
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "variant", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            if (args.narg() == 1) {
                operations.getVariant(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
            } else {
                operations.setVariant(monsterType, args.arg(2).tojstring())?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.FALSE
            }
        })

        // Both catalog queries read argument 1 as the race/stars value: Canary never
        // retrieves a receiver (monster_type_functions.cpp:1926, 1940).
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getMonstersByRace", luaFunction { args ->
            monsterTypeCatalogTable(env, operations.getMonstersByRace(args.arg(1).toint()))
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getMonstersByBestiaryStars", luaFunction { args ->
            monsterTypeCatalogTable(env, operations.getMonstersByBestiaryStars(args.arg(1).toint()))
        })
    }

    /** Mirrors `MonsterTypeFunctions::luaMonsterTypeRace`'s string -> `RaceType_t` map. */
    private fun raceIdByName(race: String): Int? = when (race) {
        "venom" -> RACE_VENOM
        "blood" -> RACE_BLOOD
        "undead" -> RACE_UNDEAD
        "fire" -> RACE_FIRE
        "energy" -> RACE_ENERGY
        "ink" -> RACE_INK
        "chocolate" -> RACE_CHOCOLATE
        "candy" -> RACE_CANDY
        else -> null
    }

    private fun registerIntAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Int?,
        setter: (Any, Int) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(monsterType, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    private fun registerLongAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Long?,
        setter: (Any, Long) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(monsterType)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
            } else {
                setter(monsterType, args.arg(2).tolong())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    private fun registerStringAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> String?,
        setter: (Any, String) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(monsterType, args.arg(2).tojstring())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    private fun registerBooleanAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Boolean?,
        setter: (Any, Boolean) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(monsterType, args.arg(2).toboolean())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    /** Mirrors the integer attributes that report `false` (not `nil`) without a receiver. */
    private fun registerFalseDefaultedInt(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Int?,
        setter: (Any, Int) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            if (args.narg() == 1) {
                getter(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
            } else {
                setter(monsterType, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
            }
        })
    }

    /** `addElement`/`addReflect`/`addHealing` share the `(type, percent)` signature. */
    private fun registerPercentAttribute(
        env: LuaEnvironment,
        name: String,
        add: (Any, Int, Int) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            add(monsterType, args.arg(2).toint(), args.arg(3).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })
    }

    /**
     * Registers one of the `onThink`/`onAppear`/... callback methods. The C++
     * `luaMonsterTypeEventOnCallback` reads the event from the executing script
     * interface and ignores its argument; the port captures the passed Lua
     * function as that event (AGENTS.md 1.4: the callback stays Lua).
     */
    private fun registerCallback(
        env: LuaEnvironment,
        name: String,
        operations: MonsterTypeOperations,
        scriptInterfaces: MutableMap<Any, LuaMonsterTypeScriptInterface>,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val scriptInterface = scriptInterfaceOf(env, scriptInterfaces, monsterType, args.arg(2))
            operations.loadCallback(monsterType, scriptInterface)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })
    }

    private fun scriptInterfaceOf(
        env: LuaEnvironment,
        scriptInterfaces: MutableMap<Any, LuaMonsterTypeScriptInterface>,
        monsterType: Any,
        callback: LuaValue,
    ): LuaMonsterTypeScriptInterface =
        scriptInterfaces.getOrPut(monsterType) { LuaMonsterTypeScriptInterface(env) }.apply { capture(callback) }

    /** Mirrors `luaMonsterTypeCombatImmunities`'s string -> `CombatType_t` map. */
    private fun combatTypeIndexByName(immunity: String): Int? = when (immunity) {
        "physical" -> COMBAT_PHYSICALDAMAGE
        "energy" -> COMBAT_ENERGYDAMAGE
        "fire" -> COMBAT_FIREDAMAGE
        "poison", "earth" -> COMBAT_EARTHDAMAGE
        "drown" -> COMBAT_DROWNDAMAGE
        "ice" -> COMBAT_ICEDAMAGE
        "holy" -> COMBAT_HOLYDAMAGE
        "death" -> COMBAT_DEATHDAMAGE
        "lifedrain" -> COMBAT_LIFEDRAIN
        "manadrain" -> COMBAT_MANADRAIN
        "agony" -> COMBAT_AGONYDAMAGE
        "neutral" -> COMBAT_NEUTRALDAMAGE
        else -> null
    }

    /** Mirrors `luaMonsterTypeConditionImmunities`'s string -> `ConditionType_t` map. */
    private fun conditionIndexByName(immunity: String): Int? = when (immunity) {
        "physical" -> CONDITION_BLEEDING
        "energy" -> CONDITION_ENERGY
        "fire" -> CONDITION_FIRE
        "poison", "earth" -> CONDITION_POISON
        "drown" -> CONDITION_DROWN
        "ice" -> CONDITION_FREEZING
        "holy" -> CONDITION_DAZZLED
        "death" -> CONDITION_CURSED
        "paralyze" -> CONDITION_PARALYZE
        "outfit" -> CONDITION_OUTFIT
        "drunk" -> CONDITION_DRUNK
        "invisible", "invisibility" -> CONDITION_INVISIBLE
        "bleed" -> CONDITION_BLEEDING
        else -> null
    }

    /** Mirrors the indexed immunities table (`0..count-1`, values `0`/`1`). */
    private fun immunityTable(immunities: List<Boolean>): LuaTable =
        LuaTable().also { table ->
            immunities.forEachIndexed { index, value -> table.set(index, LuaValue.valueOf(if (value) 1 else 0)) }
        }

    /** Mirrors `Lua::pushOutfit` (src/lua/functions/lua_functions_loader.cpp:722). */
    private fun pushOutfit(outfit: Outfit): LuaTable =
        LuaTable().also { table ->
            table.set("lookType", LuaValue.valueOf(outfit.lookType))
            table.set("lookTypeEx", LuaValue.valueOf(outfit.lookTypeEx))
            table.set("lookHead", LuaValue.valueOf(outfit.lookHead))
            table.set("lookBody", LuaValue.valueOf(outfit.lookBody))
            table.set("lookLegs", LuaValue.valueOf(outfit.lookLegs))
            table.set("lookFeet", LuaValue.valueOf(outfit.lookFeet))
            table.set("lookAddons", LuaValue.valueOf(outfit.lookAddons))
            table.set("lookMount", LuaValue.valueOf(outfit.lookMount))
            table.set("lookMountHead", LuaValue.valueOf(outfit.lookMountHead))
            table.set("lookMountBody", LuaValue.valueOf(outfit.lookMountBody))
            table.set("lookMountLegs", LuaValue.valueOf(outfit.lookMountLegs))
            table.set("lookMountFeet", LuaValue.valueOf(outfit.lookMountFeet))
            table.set("lookFamiliarsType", LuaValue.valueOf(outfit.lookFamiliarsType))
            // The C++ keys carry trailing spaces (lua_functions_loader.cpp:735-737).
            table.set("lookWing ", LuaValue.valueOf(outfit.lookWing))
            table.set("lookAura", LuaValue.valueOf(outfit.lookAura))
            table.set("lookEffect ", LuaValue.valueOf(outfit.lookEffect))
            table.set("lookShader ", LuaValue.valueOf(outfit.lookShader))
        }

    /** Mirrors `Lua::getOutfit` (src/lua/functions/lua_functions_loader.cpp:439). */
    private fun luaToOutfit(value: LuaValue): Outfit? {
        if (!value.istable()) {
            return null
        }
        return Outfit(
            lookMountFeet = value.get("lookMountFeet").toint(),
            lookMountLegs = value.get("lookMountLegs").toint(),
            lookMountBody = value.get("lookMountBody").toint(),
            lookMountHead = value.get("lookMountHead").toint(),
            lookFamiliarsType = value.get("lookFamiliarsType").toint(),
            lookMount = value.get("lookMount").toint(),
            lookAddons = value.get("lookAddons").toint(),
            lookFeet = value.get("lookFeet").toint(),
            lookLegs = value.get("lookLegs").toint(),
            lookBody = value.get("lookBody").toint(),
            lookHead = value.get("lookHead").toint(),
            lookTypeEx = value.get("lookTypeEx").toint(),
            lookType = value.get("lookType").toint(),
            // The C++ reads lookShader into lookWing (lua_functions_loader.cpp:457).
            lookWing = value.get("lookShader").toint(),
            lookAura = value.get("lookAura").toint(),
            lookEffect = value.get("lookEffect").toint(),
            lookShader = value.get("lookShader").toint(),
        )
    }

    /** Mirrors `MonsterTypeFunctions::createMonsterTypeLootLuaTable` for spell blocks. */
    private fun attackListTable(env: LuaEnvironment, attacks: List<MonsterAttackInfo>): LuaTable =
        LuaTable().also { table ->
            attacks.forEachIndexed { index, attack -> table.set(index + 1, attackRow(env, attack)) }
        }

    /** `getMonstersByRace`/`getMonstersByBestiaryStars` result table of type userdata. */
    private fun monsterTypeCatalogTable(env: LuaEnvironment, monsters: List<Any>): LuaTable =
        LuaTable().also { table ->
            monsters.forEachIndexed { index, monsterType ->
                table.set(index + 1, env.userdata.wrap(MONSTER_TYPE_TYPE, monsterType))
            }
        }

    /** Mirrors `MonsterTypeFunctions::createMonsterTypeLootLuaTable`. */
    private fun lootTable(loot: List<LootData>): LuaTable =
        LuaTable().also { table ->
            loot.forEachIndexed { index, block -> table.set(index + 1, lootRow(block)) }
        }

    private fun lootRow(block: LootData): LuaTable =
        LuaTable().also { row ->
            row.set("itemId", LuaValue.valueOf(block.id))
            row.set("chance", LuaValue.valueOf(block.chance))
            row.set("subType", LuaValue.valueOf(block.subType))
            row.set("maxCount", LuaValue.valueOf(block.countMax))
            row.set("minCount", LuaValue.valueOf(block.countMin))
            row.set("actionId", LuaValue.valueOf(block.actionId))
            row.set("text", LuaValue.valueOf(block.text))
            row.set("unique", LuaValue.valueOf(block.unique))
            row.set("childLoot", lootTable(block.childLoot))
        }

    /** Mirrors `MonsterTypeFunctions::luaMonsterTypeGetAttackList`. */
    private fun attackRow(env: LuaEnvironment, attack: MonsterAttackInfo): LuaTable =
        LuaTable().also { row ->
            row.set("chance", LuaValue.valueOf(attack.chance))
            row.set("isCombatSpell", LuaValue.valueOf(if (attack.isCombatSpell) 1 else 0))
            row.set("isMelee", LuaValue.valueOf(if (attack.isMelee) 1 else 0))
            row.set("minCombatValue", LuaValue.valueOf(attack.minCombatValue))
            row.set("maxCombatValue", LuaValue.valueOf(attack.maxCombatValue))
            row.set("range", LuaValue.valueOf(attack.range))
            row.set("speed", LuaValue.valueOf(attack.speed))
            val spell = attack.spell
            row.set("spell", if (spell != null) env.userdata.wrap(COMBAT_SPELL_TYPE, spell) else LuaValue.NIL)
        }
}

internal object UnwiredMonsterTypeOperations : MonsterTypeOperations {
    override fun create(nameOrRaceId: Any): Any? =
        throw NotImplementedError("MonsterTypeFunctions.create seam is not wired")

    override fun getName(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getName seam is not wired")

    override fun setName(monsterType: Any, name: String): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setName seam is not wired")

    override fun getHealth(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getHealth seam is not wired")

    override fun setHealth(monsterType: Any, health: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setHealth seam is not wired")

    override fun getMaxHealth(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getMaxHealth seam is not wired")

    override fun setMaxHealth(monsterType: Any, maxHealth: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setMaxHealth seam is not wired")

    override fun getExperience(monsterType: Any): Long? =
        throw NotImplementedError("MonsterTypeFunctions.getExperience seam is not wired")

    override fun setExperience(monsterType: Any, experience: Long): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setExperience seam is not wired")

    override fun getBaseSpeed(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBaseSpeed seam is not wired")

    override fun setBaseSpeed(monsterType: Any, speed: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBaseSpeed seam is not wired")

    override fun getRace(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getRace seam is not wired")

    override fun setRace(monsterType: Any, race: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setRace seam is not wired")

    override fun getBestiaryClass(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiaryClass seam is not wired")

    override fun setBestiaryClass(monsterType: Any, bestiaryClass: String): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiaryClass seam is not wired")

    override fun getCorpseId(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getCorpseId seam is not wired")

    override fun setCorpseId(monsterType: Any, corpseId: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setCorpseId seam is not wired")

    override fun getTypeName(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getTypeName seam is not wired")

    override fun isAttackable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isAttackable seam is not wired")

    override fun setAttackable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setAttackable seam is not wired")

    override fun isSummonable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isSummonable seam is not wired")

    override fun setSummonable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setSummonable seam is not wired")

    override fun isConvinceable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isConvinceable seam is not wired")

    override fun setConvinceable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setConvinceable seam is not wired")

    override fun isHostile(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isHostile seam is not wired")

    override fun setHostile(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setHostile seam is not wired")

    override fun isBlockable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isBlockable seam is not wired")

    override fun setBlockable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBlockable seam is not wired")

    override fun canSpawn(monsterType: Any, position: Position): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.canSpawn seam is not wired")

    override fun getLoot(monsterType: Any): List<LootData>? =
        throw NotImplementedError("MonsterTypeFunctions.getLoot seam is not wired")

    override fun addLoot(monsterType: Any, loot: LootData): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addLoot seam is not wired")

    override fun getAttackList(monsterType: Any): List<MonsterAttackInfo>? =
        throw NotImplementedError("MonsterTypeFunctions.getAttackList seam is not wired")

    override fun isPreyable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isPreyable seam is not wired")

    override fun setPreyable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setPreyable seam is not wired")

    override fun isPreyExclusive(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isPreyExclusive seam is not wired")

    override fun setPreyExclusive(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setPreyExclusive seam is not wired")

    override fun isIllusionable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isIllusionable seam is not wired")

    override fun setIllusionable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setIllusionable seam is not wired")

    override fun isPushable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isPushable seam is not wired")

    override fun setPushable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setPushable seam is not wired")

    override fun isHealthHidden(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isHealthHidden seam is not wired")

    override fun setHealthHidden(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setHealthHidden seam is not wired")

    override fun isForgeCreature(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isForgeCreature seam is not wired")

    override fun setForgeCreature(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setForgeCreature seam is not wired")

    override fun isFamiliar(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isFamiliar seam is not wired")

    override fun setFamiliar(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setFamiliar seam is not wired")

    override fun isRewardBoss(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isRewardBoss seam is not wired")

    override fun setRewardBoss(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setRewardBoss seam is not wired")

    override fun canPushItems(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.canPushItems seam is not wired")

    override fun setCanPushItems(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setCanPushItems seam is not wired")

    override fun canPushCreatures(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.canPushCreatures seam is not wired")

    override fun setCanPushCreatures(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setCanPushCreatures seam is not wired")

    override fun canWalkOnEnergy(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.canWalkOnEnergy seam is not wired")

    override fun setCanWalkOnEnergy(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setCanWalkOnEnergy seam is not wired")

    override fun canWalkOnFire(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.canWalkOnFire seam is not wired")

    override fun setCanWalkOnFire(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setCanWalkOnFire seam is not wired")

    override fun canWalkOnPoison(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.canWalkOnPoison seam is not wired")

    override fun setCanWalkOnPoison(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setCanWalkOnPoison seam is not wired")

    override fun isTargetPreferPlayer(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isTargetPreferPlayer seam is not wired")

    override fun setTargetPreferPlayer(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setTargetPreferPlayer seam is not wired")

    override fun isTargetPreferMaster(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isTargetPreferMaster seam is not wired")

    override fun setTargetPreferMaster(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setTargetPreferMaster seam is not wired")

    override fun getNameDescription(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getNameDescription seam is not wired")

    override fun setNameDescription(monsterType: Any, value: String): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setNameDescription seam is not wired")

    override fun getRunHealth(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getRunHealth seam is not wired")

    override fun setRunHealth(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setRunHealth seam is not wired")

    override fun getCritChance(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getCritChance seam is not wired")

    override fun setCritChance(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setCritChance seam is not wired")

    override fun getFaction(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getFaction seam is not wired")

    override fun setFaction(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setFaction seam is not wired")

    override fun getEnemyFactions(monsterType: Any): List<Int>? =
        throw NotImplementedError("MonsterTypeFunctions.getEnemyFactions seam is not wired")

    override fun addEnemyFaction(monsterType: Any, faction: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addEnemyFaction seam is not wired")

    override fun getRaceId(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getRaceId seam is not wired")

    override fun setRaceId(monsterType: Any, raceId: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setRaceId seam is not wired")

    override fun addBestiaryList(raceId: Int, name: String): Unit =
        throw NotImplementedError("MonsterTypeFunctions.addBestiaryList seam is not wired")

    override fun getBestiaryOccurrence(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiaryOccurrence seam is not wired")

    override fun setBestiaryOccurrence(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiaryOccurrence seam is not wired")

    override fun getBestiaryLocations(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiaryLocations seam is not wired")

    override fun setBestiaryLocations(monsterType: Any, value: String): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiaryLocations seam is not wired")

    override fun getBestiaryStars(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiaryStars seam is not wired")

    override fun setBestiaryStars(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiaryStars seam is not wired")

    override fun getBestiaryCharmsPoints(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiaryCharmsPoints seam is not wired")

    override fun setBestiaryCharmsPoints(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiaryCharmsPoints seam is not wired")

    override fun getBestiarySecondUnlock(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiarySecondUnlock seam is not wired")

    override fun setBestiarySecondUnlock(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiarySecondUnlock seam is not wired")

    override fun getBestiaryFirstUnlock(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiaryFirstUnlock seam is not wired")

    override fun setBestiaryFirstUnlock(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiaryFirstUnlock seam is not wired")

    override fun getBestiaryToKill(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiaryToKill seam is not wired")

    override fun setBestiaryToKill(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiaryToKill seam is not wired")

    override fun getBestiaryRace(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiaryRace seam is not wired")

    override fun setBestiaryRace(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiaryRace seam is not wired")

    override fun getCombatImmunities(monsterType: Any): List<Boolean>? =
        throw NotImplementedError("MonsterTypeFunctions.getCombatImmunities seam is not wired")

    override fun setCombatImmunity(monsterType: Any, combatType: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setCombatImmunity seam is not wired")

    override fun getConditionImmunities(monsterType: Any): List<Boolean>? =
        throw NotImplementedError("MonsterTypeFunctions.getConditionImmunities seam is not wired")

    override fun setConditionImmunity(monsterType: Any, conditionType: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setConditionImmunity seam is not wired")

    override fun getDefenseList(monsterType: Any): List<MonsterAttackInfo>? =
        throw NotImplementedError("MonsterTypeFunctions.getDefenseList seam is not wired")

    override fun addAttack(monsterType: Any, spell: Any?): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addAttack seam is not wired")

    override fun addDefense(monsterType: Any, spell: Any?): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addDefense seam is not wired")

    override fun getElementList(monsterType: Any): Map<Int, Int>? =
        throw NotImplementedError("MonsterTypeFunctions.getElementList seam is not wired")

    override fun addElement(monsterType: Any, element: Int, percent: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addElement seam is not wired")

    override fun addReflect(monsterType: Any, element: Int, percent: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addReflect seam is not wired")

    override fun addHealing(monsterType: Any, element: Int, percent: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addHealing seam is not wired")

    override fun getVoices(monsterType: Any): List<MonsterVoiceInfo>? =
        throw NotImplementedError("MonsterTypeFunctions.getVoices seam is not wired")

    override fun addVoice(monsterType: Any, text: String, interval: Int, chance: Int, yell: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addVoice seam is not wired")

    override fun getCreatureEvents(monsterType: Any): List<String>? =
        throw NotImplementedError("MonsterTypeFunctions.getCreatureEvents seam is not wired")

    override fun registerEvent(monsterType: Any, name: String): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.registerEvent seam is not wired")

    override fun loadCallback(monsterType: Any, scriptInterface: Any?): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.loadCallback seam is not wired")

    override fun setEventType(monsterType: Any, eventType: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setEventType seam is not wired")

    override fun getSummonList(monsterType: Any): List<MonsterSummonInfo>? =
        throw NotImplementedError("MonsterTypeFunctions.getSummonList seam is not wired")

    override fun addSummon(monsterType: Any, name: String, speed: Int, chance: Int, count: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addSummon seam is not wired")

    override fun getMaxSummons(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getMaxSummons seam is not wired")

    override fun setMaxSummons(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setMaxSummons seam is not wired")

    override fun getArmor(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getArmor seam is not wired")

    override fun setArmor(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setArmor seam is not wired")

    override fun getMitigation(monsterType: Any): Float? =
        throw NotImplementedError("MonsterTypeFunctions.getMitigation seam is not wired")

    override fun setMitigation(monsterType: Any, value: Float): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setMitigation seam is not wired")

    override fun getDefense(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getDefense seam is not wired")

    override fun setDefense(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setDefense seam is not wired")

    override fun getOutfit(monsterType: Any): Outfit? =
        throw NotImplementedError("MonsterTypeFunctions.getOutfit seam is not wired")

    override fun setOutfit(monsterType: Any, outfit: Outfit): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setOutfit seam is not wired")

    override fun getManaCost(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getManaCost seam is not wired")

    override fun setManaCost(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setManaCost seam is not wired")

    override fun getLight(monsterType: Any): LightInfo? =
        throw NotImplementedError("MonsterTypeFunctions.getLight seam is not wired")

    override fun setLight(monsterType: Any, color: Int, level: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setLight seam is not wired")

    override fun getStaticAttackChance(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getStaticAttackChance seam is not wired")

    override fun setStaticAttackChance(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setStaticAttackChance seam is not wired")

    override fun getTargetDistance(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getTargetDistance seam is not wired")

    override fun setTargetDistance(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setTargetDistance seam is not wired")

    override fun getYellChance(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getYellChance seam is not wired")

    override fun setYellChance(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setYellChance seam is not wired")

    override fun getYellSpeedTicks(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getYellSpeedTicks seam is not wired")

    override fun setYellSpeedTicks(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setYellSpeedTicks seam is not wired")

    override fun getChangeTargetChance(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getChangeTargetChance seam is not wired")

    override fun setChangeTargetChance(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setChangeTargetChance seam is not wired")

    override fun getChangeTargetSpeed(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getChangeTargetSpeed seam is not wired")

    override fun setChangeTargetSpeed(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setChangeTargetSpeed seam is not wired")

    override fun getStrategiesTargetNearest(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getStrategiesTargetNearest seam is not wired")

    override fun setStrategiesTargetNearest(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setStrategiesTargetNearest seam is not wired")

    override fun getStrategiesTargetHealth(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getStrategiesTargetHealth seam is not wired")

    override fun setStrategiesTargetHealth(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setStrategiesTargetHealth seam is not wired")

    override fun getStrategiesTargetDamage(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getStrategiesTargetDamage seam is not wired")

    override fun setStrategiesTargetDamage(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setStrategiesTargetDamage seam is not wired")

    override fun getStrategiesTargetRandom(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getStrategiesTargetRandom seam is not wired")

    override fun setStrategiesTargetRandom(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setStrategiesTargetRandom seam is not wired")

    override fun getRespawnTypePeriod(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getRespawnTypePeriod seam is not wired")

    override fun setRespawnTypePeriod(monsterType: Any, period: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setRespawnTypePeriod seam is not wired")

    override fun isRespawnTypeUnderground(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isRespawnTypeUnderground seam is not wired")

    override fun setRespawnTypeUnderground(monsterType: Any, underground: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setRespawnTypeUnderground seam is not wired")

    override fun getBossRace(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getBossRace seam is not wired")

    override fun setBossRace(monsterType: Any, raceId: Int, bossClass: String): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBossRace seam is not wired")

    override fun getBossRaceId(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBossRaceId seam is not wired")

    override fun setBossRaceId(monsterType: Any, raceId: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBossRaceId seam is not wired")

    override fun addBosstiaryMonster(raceId: Int, name: String): Unit =
        throw NotImplementedError("MonsterTypeFunctions.addBosstiaryMonster seam is not wired")

    override fun getSoundChance(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getSoundChance seam is not wired")

    override fun setSoundChance(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setSoundChance seam is not wired")

    override fun getSoundSpeedTicks(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getSoundSpeedTicks seam is not wired")

    override fun setSoundSpeedTicks(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setSoundSpeedTicks seam is not wired")

    override fun addSound(monsterType: Any, soundId: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addSound seam is not wired")

    override fun getSounds(monsterType: Any): List<Int>? =
        throw NotImplementedError("MonsterTypeFunctions.getSounds seam is not wired")

    override fun getDeathSound(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getDeathSound seam is not wired")

    override fun setDeathSound(monsterType: Any, value: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setDeathSound seam is not wired")

    override fun getVariant(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getVariant seam is not wired")

    override fun setVariant(monsterType: Any, value: String): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setVariant seam is not wired")

    override fun getMonstersByRace(race: Int): List<Any> =
        throw NotImplementedError("MonsterTypeFunctions.getMonstersByRace seam is not wired")

    override fun getMonstersByBestiaryStars(stars: Int): List<Any> =
        throw NotImplementedError("MonsterTypeFunctions.getMonstersByBestiaryStars seam is not wired")
}

/**
 * Lua-backed [MonsterTypeScriptInterface]: the port of the `LuaScriptInterface`
 * slice a monster registration script exposes while its `onThink`/`onSpawn`/...
 * callbacks are captured (AGENTS.md 1.4 keeps the function itself Lua).
 *
 * Each captured callback is registered under a fresh event id, so the domain
 * `thinkEvent`/`spawnEvent`/... slots hold re-resolvable ids exactly like the
 * C++ `LuaScriptInterface::loadFile` bookkeeping.
 */
internal class LuaMonsterTypeScriptInterface(
    private val env: LuaEnvironment,
) : MonsterTypeScriptInterface {

    private val events: MutableMap<Int, LuaValue> = linkedMapOf()
    private var nextEventId: Int = 1
    private var currentEvent: Int = -1

    /** Registers [callback] as the current event, returning its new id. */
    fun capture(callback: LuaValue): Int {
        val id = nextEventId++
        events[id] = callback
        currentEvent = id
        return id
    }

    override fun getEvent(): Int = currentEvent

    override fun callEvent(eventId: Int, arguments: List<Any?>): Boolean {
        val callback = events[eventId] ?: return false
        if (!callback.isfunction()) {
            return false
        }

        val wrapped = arguments.map { argument ->
            when (argument) {
                null -> LuaValue.NIL
                is LuaValue -> argument
                is Position -> pushPosition(argument)
                is Player -> env.userdata.wrap("Player", argument)
                is Monster -> env.userdata.wrap("Monster", argument)
                is Creature -> env.userdata.wrap("Creature", argument)
                is Boolean -> LuaValue.valueOf(argument)
                is Int -> LuaValue.valueOf(argument)
                is Long -> LuaValue.valueOf(argument.toDouble())
                is Double -> LuaValue.valueOf(argument)
                is Float -> LuaValue.valueOf(argument.toDouble())
                is String -> LuaValue.valueOf(argument)
                else -> LuaValue.NIL
            }
        }
        val result = callback.invoke(LuaValue.varargsOf(wrapped.toTypedArray())).arg1()

        // `lua_toboolean`: false only for nil/false; LuaJ's nil toboolean is true.
        return !result.isnil() && result.toboolean()
    }

    /** `Lua::pushPosition` (`src/lua/functions/lua_functions_loader.cpp:707-719`). */
    private fun pushPosition(position: Position): LuaTable =
        LuaTable().also { table ->
            table.set("x", LuaValue.valueOf(position.x))
            table.set("y", LuaValue.valueOf(position.y))
            table.set("z", LuaValue.valueOf(position.z))
            table.set("stackpos", LuaValue.valueOf(0))
            table.setmetatable(env.userdata.metatable("Position"))
        }
}
