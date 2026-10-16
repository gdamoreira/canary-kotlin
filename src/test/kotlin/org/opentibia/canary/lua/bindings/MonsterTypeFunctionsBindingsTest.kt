package org.opentibia.canary.lua.bindings

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.lua.bindings.core.pushPosition

/**
 * Transcript-style tests for the Canary `MonsterType` binding group. Each test installs
 * [MonsterTypeFunctions] with a fake [MonsterTypeOperations] seam and drives it through
 * real Lua chunks using the exact method names from
 * src/lua/functions/creatures/monster/monster_type_functions.cpp.
 */
class MonsterTypeFunctionsBindingsTest {

    private class FakeType(
        var name: String? = "Rat",
        var health: Int? = 100,
        var maxHealth: Int? = 100,
        var experience: Long? = 10,
        var baseSpeed: Int? = 110,
        var race: Int? = 2,
        var bestiaryClass: String? = "Mammal",
        var corpseId: Int? = 5964,
        var typeName: String? = "Rat",
        var attackable: Boolean? = true,
        var summonable: Boolean? = false,
        var convinceable: Boolean? = false,
        var hostile: Boolean? = true,
        var blockable: Boolean? = false,
        var canSpawn: Boolean? = true,
        var nameDescription: String? = "a rat",
        var runHealth: Int? = 0,
        var critChance: Int? = 0,
        var faction: Int? = 7,
        val enemyFactions: MutableList<Int> = mutableListOf(),
        var raceId: Int? = 0,
        var preferPlayer: Boolean? = false,
        var preferMaster: Boolean? = false,
        var preyable: Boolean? = true,
        var preyExclusive: Boolean? = false,
        var illusionable: Boolean? = false,
        var pushable: Boolean? = true,
        var healthHidden: Boolean? = false,
        var forgeCreature: Boolean? = false,
        var familiar: Boolean? = false,
        var rewardBoss: Boolean? = false,
        var canPushItems: Boolean? = false,
        var canPushCreatures: Boolean? = false,
        var walkEnergy: Boolean? = false,
        var walkFire: Boolean? = false,
        var walkPoison: Boolean? = false,
        var bestiaryOccurrence: Int? = 0,
        var bestiaryLocations: String? = "",
        var bestiaryStars: Int? = 0,
        var bestiaryCharms: Int? = 0,
        var bestiarySecondUnlock: Int? = 0,
        var bestiaryFirstUnlock: Int? = 0,
        var bestiaryToKill: Int? = 0,
        var bestiaryRace: Int? = 0,
        val combatImmunities: MutableList<Boolean> = MutableList(14) { false },
        val conditionImmunities: MutableList<Boolean> = MutableList(40) { false },
        val defenses: MutableList<MonsterAttackInfo> = mutableListOf(),
        val elements: MutableMap<Int, Int> = linkedMapOf(),
        val reflects: MutableMap<Int, Int> = linkedMapOf(),
        val healings: MutableMap<Int, Int> = linkedMapOf(),
        val voices: MutableList<MonsterVoiceInfo> = mutableListOf(),
        val creatureEvents: MutableList<String> = mutableListOf(),
        var callbackLoaded: Boolean? = true,
        var loadedScriptInterface: Any? = null,
        var eventType: Int? = 0,
        val summons: MutableList<MonsterSummonInfo> = mutableListOf(),
        var lastSummonCount: Int? = null,
        var maxSummons: Int? = 0,
        var armor: Int? = 0,
        var mitigation: Float? = 0f,
        var defense: Int? = 0,
        var outfit: Outfit = Outfit(),
        var outfitAccepted: Boolean? = true,
        var manaCost: Int? = 0,
        var light: LightInfo = LightInfo(),
        var staticAttackChance: Int? = 95,
        var targetDistance: Int? = 1,
        var yellChance: Int? = 0,
        var yellSpeedTicks: Int? = 0,
        var changeTargetChance: Int? = 0,
        var changeTargetSpeed: Int? = 0,
        var strategyNearest: Int? = 0,
        var strategyHealth: Int? = 0,
        var strategyDamage: Int? = 0,
        var strategyRandom: Int? = 0,
        var respawnPeriod: Int? = 0,
        var respawnUnderground: Boolean? = false,
        var bossRaceClass: String? = "",
        var bosstiaryRace: Int? = 0,
        var soundChance: Int? = 0,
        var soundSpeedTicks: Int? = 0,
        val sounds: MutableList<Int> = mutableListOf(),
        var deathSound: Int? = 0,
        var variant: String? = "",
        val loot: MutableList<LootData> = mutableListOf(),
        val attacks: MutableList<MonsterAttackInfo> = mutableListOf(),
        var lastAttackSpell: Any? = null,
        var lastDefenseSpell: Any? = null,
    )

    private class FakeMonsterTypeOps : MonsterTypeOperations {
        val typesByName = mutableMapOf<String, Any>()
        val typesByRaceId = mutableMapOf<Int, Any>()
        val bestiaryList = mutableListOf<Pair<Int, String>>()
        val bosstiaryMonsters = mutableListOf<Pair<Int, String>>()
        val monstersByRace = mutableMapOf<Int, List<Any>>()
        val monstersByBestiaryStars = mutableMapOf<Int, List<Any>>()
        var lastLoot: LootData? = null
        val spawnPositions = mutableListOf<Position>()

        private fun fake(monsterType: Any): FakeType? = monsterType as? FakeType

        override fun create(nameOrRaceId: Any): Any? = when (nameOrRaceId) {
            is Number -> typesByRaceId[nameOrRaceId.toInt()]
            is String -> typesByName[nameOrRaceId]
            else -> null
        }

        override fun getName(monsterType: Any): String? = fake(monsterType)?.name

        override fun setName(monsterType: Any, name: String): Boolean? {
            val type = fake(monsterType) ?: return null
            type.name = name
            return true
        }

        override fun getHealth(monsterType: Any): Int? = fake(monsterType)?.health

        override fun setHealth(monsterType: Any, health: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.health = health
            return true
        }

        override fun getMaxHealth(monsterType: Any): Int? = fake(monsterType)?.maxHealth

        override fun setMaxHealth(monsterType: Any, maxHealth: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.maxHealth = maxHealth
            return true
        }

        override fun getExperience(monsterType: Any): Long? = fake(monsterType)?.experience

        override fun setExperience(monsterType: Any, experience: Long): Boolean? {
            val type = fake(monsterType) ?: return null
            type.experience = experience
            return true
        }

        override fun getBaseSpeed(monsterType: Any): Int? = fake(monsterType)?.baseSpeed

        override fun setBaseSpeed(monsterType: Any, speed: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.baseSpeed = speed
            return true
        }

        override fun getRace(monsterType: Any): Int? = fake(monsterType)?.race

        override fun setRace(monsterType: Any, race: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.race = race
            return true
        }

        override fun getBestiaryClass(monsterType: Any): String? = fake(monsterType)?.bestiaryClass

        override fun setBestiaryClass(monsterType: Any, bestiaryClass: String): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bestiaryClass = bestiaryClass
            return true
        }

        override fun getCorpseId(monsterType: Any): Int? = fake(monsterType)?.corpseId

        override fun setCorpseId(monsterType: Any, corpseId: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.corpseId = corpseId
            return true
        }

        override fun getTypeName(monsterType: Any): String? = fake(monsterType)?.typeName

        override fun isAttackable(monsterType: Any): Boolean? = fake(monsterType)?.attackable

        override fun setAttackable(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.attackable = value
            return true
        }

        override fun isSummonable(monsterType: Any): Boolean? = fake(monsterType)?.summonable

        override fun setSummonable(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.summonable = value
            return true
        }

        override fun isConvinceable(monsterType: Any): Boolean? = fake(monsterType)?.convinceable

        override fun setConvinceable(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.convinceable = value
            return true
        }

        override fun isHostile(monsterType: Any): Boolean? = fake(monsterType)?.hostile

        override fun setHostile(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.hostile = value
            return true
        }

        override fun isBlockable(monsterType: Any): Boolean? = fake(monsterType)?.blockable

        override fun setBlockable(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.blockable = value
            return true
        }

        override fun canSpawn(monsterType: Any, position: Position): Boolean? {
            spawnPositions += position
            return fake(monsterType)?.canSpawn
        }

        override fun getLoot(monsterType: Any): List<LootData>? = fake(monsterType)?.loot

        override fun addLoot(monsterType: Any, loot: LootData): Boolean? {
            val type = fake(monsterType) ?: return null
            lastLoot = loot
            type.loot += loot
            return true
        }

        override fun getAttackList(monsterType: Any): List<MonsterAttackInfo>? = fake(monsterType)?.attacks

        override fun isPreyable(monsterType: Any): Boolean? = fake(monsterType)?.preyable

        override fun setPreyable(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.preyable = value
            return true
        }

        override fun isPreyExclusive(monsterType: Any): Boolean? = fake(monsterType)?.preyExclusive

        override fun setPreyExclusive(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.preyExclusive = value
            return true
        }

        override fun isIllusionable(monsterType: Any): Boolean? = fake(monsterType)?.illusionable

        override fun setIllusionable(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.illusionable = value
            return true
        }

        override fun isPushable(monsterType: Any): Boolean? = fake(monsterType)?.pushable

        override fun setPushable(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.pushable = value
            return true
        }

        override fun isHealthHidden(monsterType: Any): Boolean? = fake(monsterType)?.healthHidden

        override fun setHealthHidden(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.healthHidden = value
            return true
        }

        override fun isForgeCreature(monsterType: Any): Boolean? = fake(monsterType)?.forgeCreature

        override fun setForgeCreature(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.forgeCreature = value
            return true
        }

        override fun isFamiliar(monsterType: Any): Boolean? = fake(monsterType)?.familiar

        override fun setFamiliar(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.familiar = value
            return true
        }

        override fun isRewardBoss(monsterType: Any): Boolean? = fake(monsterType)?.rewardBoss

        override fun setRewardBoss(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.rewardBoss = value
            return true
        }

        override fun canPushItems(monsterType: Any): Boolean? = fake(monsterType)?.canPushItems

        override fun setCanPushItems(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.canPushItems = value
            return true
        }

        override fun canPushCreatures(monsterType: Any): Boolean? = fake(monsterType)?.canPushCreatures

        override fun setCanPushCreatures(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.canPushCreatures = value
            return true
        }

        override fun canWalkOnEnergy(monsterType: Any): Boolean? = fake(monsterType)?.walkEnergy

        override fun setCanWalkOnEnergy(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.walkEnergy = value
            return true
        }

        override fun canWalkOnFire(monsterType: Any): Boolean? = fake(monsterType)?.walkFire

        override fun setCanWalkOnFire(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.walkFire = value
            return true
        }

        override fun canWalkOnPoison(monsterType: Any): Boolean? = fake(monsterType)?.walkPoison

        override fun setCanWalkOnPoison(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.walkPoison = value
            return true
        }

        override fun isTargetPreferPlayer(monsterType: Any): Boolean? = fake(monsterType)?.preferPlayer

        override fun setTargetPreferPlayer(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.preferPlayer = value
            return true
        }

        override fun isTargetPreferMaster(monsterType: Any): Boolean? = fake(monsterType)?.preferMaster

        override fun setTargetPreferMaster(monsterType: Any, value: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.preferMaster = value
            return true
        }

        override fun getNameDescription(monsterType: Any): String? = fake(monsterType)?.nameDescription

        override fun setNameDescription(monsterType: Any, value: String): Boolean? {
            val type = fake(monsterType) ?: return null
            type.nameDescription = value
            return true
        }

        override fun getRunHealth(monsterType: Any): Int? = fake(monsterType)?.runHealth

        override fun setRunHealth(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.runHealth = value
            return true
        }

        override fun getCritChance(monsterType: Any): Int? = fake(monsterType)?.critChance

        override fun setCritChance(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.critChance = value
            return true
        }

        override fun getFaction(monsterType: Any): Int? = fake(monsterType)?.faction

        override fun setFaction(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.faction = value
            return true
        }

        override fun getEnemyFactions(monsterType: Any): List<Int>? = fake(monsterType)?.enemyFactions

        override fun addEnemyFaction(monsterType: Any, faction: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.enemyFactions += faction
            return true
        }

        override fun getRaceId(monsterType: Any): Int? = fake(monsterType)?.raceId

        override fun setRaceId(monsterType: Any, raceId: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.raceId = raceId
            return true
        }

        override fun addBestiaryList(raceId: Int, name: String) {
            bestiaryList += raceId to name
        }

        override fun getBestiaryOccurrence(monsterType: Any): Int? = fake(monsterType)?.bestiaryOccurrence

        override fun setBestiaryOccurrence(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bestiaryOccurrence = value
            return true
        }

        override fun getBestiaryLocations(monsterType: Any): String? = fake(monsterType)?.bestiaryLocations

        override fun setBestiaryLocations(monsterType: Any, value: String): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bestiaryLocations = value
            return true
        }

        override fun getBestiaryStars(monsterType: Any): Int? = fake(monsterType)?.bestiaryStars

        override fun setBestiaryStars(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bestiaryStars = value
            return true
        }

        override fun getBestiaryCharmsPoints(monsterType: Any): Int? = fake(monsterType)?.bestiaryCharms

        override fun setBestiaryCharmsPoints(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bestiaryCharms = value
            return true
        }

        override fun getBestiarySecondUnlock(monsterType: Any): Int? = fake(monsterType)?.bestiarySecondUnlock

        override fun setBestiarySecondUnlock(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bestiarySecondUnlock = value
            return true
        }

        override fun getBestiaryFirstUnlock(monsterType: Any): Int? = fake(monsterType)?.bestiaryFirstUnlock

        override fun setBestiaryFirstUnlock(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bestiaryFirstUnlock = value
            return true
        }

        override fun getBestiaryToKill(monsterType: Any): Int? = fake(monsterType)?.bestiaryToKill

        override fun setBestiaryToKill(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bestiaryToKill = value
            return true
        }

        override fun getBestiaryRace(monsterType: Any): Int? = fake(monsterType)?.bestiaryRace

        override fun setBestiaryRace(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bestiaryRace = value
            return true
        }

        override fun getCombatImmunities(monsterType: Any): List<Boolean>? = fake(monsterType)?.combatImmunities

        override fun setCombatImmunity(monsterType: Any, combatType: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.combatImmunities[combatType] = true
            return true
        }

        override fun getConditionImmunities(monsterType: Any): List<Boolean>? = fake(monsterType)?.conditionImmunities

        override fun setConditionImmunity(monsterType: Any, conditionType: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.conditionImmunities[conditionType] = true
            return true
        }

        override fun getDefenseList(monsterType: Any): List<MonsterAttackInfo>? = fake(monsterType)?.defenses

        override fun addAttack(monsterType: Any, spell: Any?): Boolean? {
            val type = fake(monsterType) ?: return null
            type.lastAttackSpell = spell
            return spell != null
        }

        override fun addDefense(monsterType: Any, spell: Any?): Boolean? {
            val type = fake(monsterType) ?: return null
            type.lastDefenseSpell = spell
            return spell != null
        }

        override fun getElementList(monsterType: Any): Map<Int, Int>? = fake(monsterType)?.elements

        override fun addElement(monsterType: Any, element: Int, percent: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.elements[element] = percent
            return true
        }

        override fun addReflect(monsterType: Any, element: Int, percent: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.reflects[element] = percent
            return true
        }

        override fun addHealing(monsterType: Any, element: Int, percent: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.healings[element] = percent
            return true
        }

        override fun getVoices(monsterType: Any): List<MonsterVoiceInfo>? = fake(monsterType)?.voices

        override fun addVoice(monsterType: Any, text: String, interval: Int, chance: Int, yell: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.yellSpeedTicks = interval
            type.yellChance = chance
            type.voices += MonsterVoiceInfo(text = text, yellText = yell)
            return true
        }

        override fun getCreatureEvents(monsterType: Any): List<String>? = fake(monsterType)?.creatureEvents

        override fun registerEvent(monsterType: Any, name: String): Boolean? {
            val type = fake(monsterType) ?: return null
            type.creatureEvents += name
            return true
        }

        override fun loadCallback(monsterType: Any, scriptInterface: Any?): Boolean? {
            val type = fake(monsterType) ?: return null
            type.loadedScriptInterface = scriptInterface
            return type.callbackLoaded
        }

        override fun setEventType(monsterType: Any, eventType: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.eventType = eventType
            return true
        }

        override fun getSummonList(monsterType: Any): List<MonsterSummonInfo>? = fake(monsterType)?.summons

        override fun addSummon(monsterType: Any, name: String, speed: Int, chance: Int, count: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.summons += MonsterSummonInfo(name = name, speed = speed, chance = chance)
            type.lastSummonCount = count
            return true
        }

        override fun getMaxSummons(monsterType: Any): Int? = fake(monsterType)?.maxSummons

        override fun setMaxSummons(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.maxSummons = value
            return true
        }

        override fun getArmor(monsterType: Any): Int? = fake(monsterType)?.armor

        override fun setArmor(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.armor = value
            return true
        }

        override fun getMitigation(monsterType: Any): Float? = fake(monsterType)?.mitigation

        override fun setMitigation(monsterType: Any, value: Float): Boolean? {
            val type = fake(monsterType) ?: return null
            type.mitigation = value
            return true
        }

        override fun getDefense(monsterType: Any): Int? = fake(monsterType)?.defense

        override fun setDefense(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.defense = value
            return true
        }

        override fun getOutfit(monsterType: Any): Outfit? = fake(monsterType)?.outfit

        override fun setOutfit(monsterType: Any, outfit: Outfit): Boolean? {
            val type = fake(monsterType) ?: return null
            if (type.outfitAccepted != true) {
                return false
            }
            type.outfit = outfit.copy()
            return true
        }

        override fun getManaCost(monsterType: Any): Int? = fake(monsterType)?.manaCost

        override fun setManaCost(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.manaCost = value
            return true
        }

        override fun getLight(monsterType: Any): LightInfo? = fake(monsterType)?.light

        override fun setLight(monsterType: Any, color: Int, level: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.light = LightInfo(level = level, color = color)
            return true
        }

        override fun getStaticAttackChance(monsterType: Any): Int? = fake(monsterType)?.staticAttackChance

        override fun setStaticAttackChance(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.staticAttackChance = value
            return true
        }

        override fun getTargetDistance(monsterType: Any): Int? = fake(monsterType)?.targetDistance

        override fun setTargetDistance(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.targetDistance = value
            return true
        }

        override fun getYellChance(monsterType: Any): Int? = fake(monsterType)?.yellChance

        override fun setYellChance(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.yellChance = value
            return true
        }

        override fun getYellSpeedTicks(monsterType: Any): Int? = fake(monsterType)?.yellSpeedTicks

        override fun setYellSpeedTicks(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.yellSpeedTicks = value
            return true
        }

        override fun getChangeTargetChance(monsterType: Any): Int? = fake(monsterType)?.changeTargetChance

        override fun setChangeTargetChance(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.changeTargetChance = value
            return true
        }

        override fun getChangeTargetSpeed(monsterType: Any): Int? = fake(monsterType)?.changeTargetSpeed

        override fun setChangeTargetSpeed(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.changeTargetSpeed = value
            return true
        }

        override fun getStrategiesTargetNearest(monsterType: Any): Int? = fake(monsterType)?.strategyNearest

        override fun setStrategiesTargetNearest(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.strategyNearest = value
            return true
        }

        override fun getStrategiesTargetHealth(monsterType: Any): Int? = fake(monsterType)?.strategyHealth

        override fun setStrategiesTargetHealth(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.strategyHealth = value
            return true
        }

        override fun getStrategiesTargetDamage(monsterType: Any): Int? = fake(monsterType)?.strategyDamage

        override fun setStrategiesTargetDamage(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.strategyDamage = value
            return true
        }

        override fun getStrategiesTargetRandom(monsterType: Any): Int? = fake(monsterType)?.strategyRandom

        override fun setStrategiesTargetRandom(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.strategyRandom = value
            return true
        }

        override fun getRespawnTypePeriod(monsterType: Any): Int? = fake(monsterType)?.respawnPeriod

        override fun setRespawnTypePeriod(monsterType: Any, period: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.respawnPeriod = period
            return true
        }

        override fun isRespawnTypeUnderground(monsterType: Any): Boolean? = fake(monsterType)?.respawnUnderground

        override fun setRespawnTypeUnderground(monsterType: Any, underground: Boolean): Boolean? {
            val type = fake(monsterType) ?: return null
            type.respawnUnderground = underground
            return true
        }

        override fun getBossRace(monsterType: Any): String? = fake(monsterType)?.bossRaceClass

        override fun setBossRace(monsterType: Any, raceId: Int, bossClass: String): Boolean? {
            val type = fake(monsterType) ?: return null
            type.bosstiaryRace = raceId
            type.bossRaceClass = bossClass
            return true
        }

        override fun getBossRaceId(monsterType: Any): Int? {
            val type = fake(monsterType) ?: return null
            return if (type.bossRaceClass.isNullOrEmpty()) 0 else type.bosstiaryRace
        }

        override fun setBossRaceId(monsterType: Any, raceId: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.raceId = raceId
            return true
        }

        override fun addBosstiaryMonster(raceId: Int, name: String) {
            bosstiaryMonsters += raceId to name
        }

        override fun getSoundChance(monsterType: Any): Int? = fake(monsterType)?.soundChance

        override fun setSoundChance(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.soundChance = value
            return true
        }

        override fun getSoundSpeedTicks(monsterType: Any): Int? = fake(monsterType)?.soundSpeedTicks

        override fun setSoundSpeedTicks(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.soundSpeedTicks = value
            return true
        }

        override fun addSound(monsterType: Any, soundId: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.sounds += soundId
            return true
        }

        override fun getSounds(monsterType: Any): List<Int>? = fake(monsterType)?.sounds

        override fun getDeathSound(monsterType: Any): Int? = fake(monsterType)?.deathSound

        override fun setDeathSound(monsterType: Any, value: Int): Boolean? {
            val type = fake(monsterType) ?: return null
            type.deathSound = value
            return true
        }

        override fun getVariant(monsterType: Any): String? = fake(monsterType)?.variant

        override fun setVariant(monsterType: Any, value: String): Boolean? {
            val type = fake(monsterType) ?: return null
            type.variant = value
            return true
        }

        override fun getMonstersByRace(race: Int): List<Any> = monstersByRace[race] ?: emptyList()

        override fun getMonstersByBestiaryStars(stars: Int): List<Any> =
            monstersByBestiaryStars[stars] ?: emptyList()
    }

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    private fun installed(
        fake: FakeMonsterTypeOps = FakeMonsterTypeOps(),
        type: FakeType = FakeType(),
    ): Triple<LuaEnvironment, FakeMonsterTypeOps, FakeType> {
        val environment = env()
        MonsterTypeFunctions.install(environment, fake)
        environment.globals.set("t", environment.userdata.wrap("MonsterType", type))
        return Triple(environment, fake, type)
    }

    @Test
    fun `MonsterType global constructor resolves a name through the seam`() {
        val fake = FakeMonsterTypeOps()
        val type = FakeType(name = "Rat")
        fake.typesByName["Rat"] = type
        val environment = env()
        MonsterTypeFunctions.install(environment, fake)

        environment.loadChunk("found = MonsterType('Rat')")
        environment.loadChunk("missing = MonsterType('Dragon')")

        assertThat(environment.getGlobal("found").touserdata()).isSameAs(type)
        assertThat(environment.getGlobal("missing").isnil()).isTrue()
    }

    @Test
    fun `MonsterType global constructor resolves a race id through the seam`() {
        val fake = FakeMonsterTypeOps()
        val type = FakeType(name = "Rat")
        fake.typesByRaceId[44] = type
        val environment = env()
        MonsterTypeFunctions.install(environment, fake)

        environment.loadChunk("found = MonsterType(44)")

        assertThat(environment.getGlobal("found").touserdata()).isSameAs(type)
    }

    @Test
    fun `MonsterType name health and maxHealth use the Canary names`() {
        val (environment, _, type) = installed()

        environment.loadChunk("name = t:name()")
        environment.loadChunk("setName = t:name('Giant Rat')")
        environment.loadChunk("newName = t:name()")
        environment.loadChunk("health = t:health()")
        environment.loadChunk("setHealth = t:health(500)")
        environment.loadChunk("newHealth = t:health()")
        environment.loadChunk("maxHealth = t:maxHealth()")
        environment.loadChunk("setMaxHealth = t:maxHealth(600)")
        environment.loadChunk("newMaxHealth = t:maxHealth()")

        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("setName").toboolean()).isTrue()
        assertThat(environment.getGlobal("newName").tojstring()).isEqualTo("Giant Rat")
        assertThat(environment.getGlobal("health").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setHealth").toboolean()).isTrue()
        assertThat(environment.getGlobal("newHealth").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("maxHealth").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("setMaxHealth").toboolean()).isTrue()
        assertThat(environment.getGlobal("newMaxHealth").toint()).isEqualTo(600)
        assertThat(type.name).isEqualTo("Giant Rat")
    }

    @Test
    fun `MonsterType experience and baseSpeed use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk("experience = t:experience()")
        environment.loadChunk("setExperience = t:experience(250)")
        environment.loadChunk("newExperience = t:experience()")
        environment.loadChunk("speed = t:baseSpeed()")
        environment.loadChunk("setSpeed = t:baseSpeed(150)")
        environment.loadChunk("newSpeed = t:baseSpeed()")

        assertThat(environment.getGlobal("experience").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("setExperience").toboolean()).isTrue()
        assertThat(environment.getGlobal("newExperience").toint()).isEqualTo(250)
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(110)
        assertThat(environment.getGlobal("setSpeed").toboolean()).isTrue()
        assertThat(environment.getGlobal("newSpeed").toint()).isEqualTo(150)
    }

    @Test
    fun `MonsterType race maps the Canary race names`() {
        val (environment, _, _) = installed()

        environment.loadChunk("before = t:race()")
        environment.loadChunk("blood = t:race('blood')")
        environment.loadChunk("afterBlood = t:race()")
        environment.loadChunk("fire = t:race('fire')")
        environment.loadChunk("afterFire = t:race()")
        environment.loadChunk("unknown = t:race('plasma')")

        assertThat(environment.getGlobal("before").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("blood").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterBlood").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("fire").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterFire").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
    }

    @Test
    fun `MonsterType Bestiaryclass and getCorpseId use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk("bestiary = t:Bestiaryclass()")
        environment.loadChunk("setBestiary = t:Bestiaryclass('Amphibic')")
        environment.loadChunk("newBestiary = t:Bestiaryclass()")
        environment.loadChunk("corpse = t:getCorpseId()")
        environment.loadChunk("setCorpse = t:corpseId(5965)")
        environment.loadChunk("newCorpse = t:getCorpseId()")

        assertThat(environment.getGlobal("bestiary").tojstring()).isEqualTo("Mammal")
        assertThat(environment.getGlobal("setBestiary").toboolean()).isTrue()
        assertThat(environment.getGlobal("newBestiary").tojstring()).isEqualTo("Amphibic")
        assertThat(environment.getGlobal("corpse").toint()).isEqualTo(5964)
        assertThat(environment.getGlobal("setCorpse").toboolean()).isTrue()
        assertThat(environment.getGlobal("newCorpse").toint()).isEqualTo(5965)
    }

    @Test
    fun `MonsterType flag getters and setters use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk("attackable = t:isAttackable()")
        environment.loadChunk("summonable = t:isSummonable()")
        environment.loadChunk("hostile = t:isHostile()")
        environment.loadChunk("blockable = t:isBlockable()")
        environment.loadChunk("setAttackable = t:isAttackable(false)")
        environment.loadChunk("setSummonable = t:isSummonable(true)")
        environment.loadChunk("setHostile = t:isHostile(false)")
        environment.loadChunk("setBlockable = t:isBlockable(true)")
        environment.loadChunk("convinceable = t:isConvinceable()")
        environment.loadChunk("setConvinceable = t:isConvinceable(true)")
        environment.loadChunk("newConvinceable = t:isConvinceable()")
        environment.loadChunk("newAttackable = t:isAttackable()")
        environment.loadChunk("newSummonable = t:isSummonable()")
        environment.loadChunk("newHostile = t:isHostile()")
        environment.loadChunk("newBlockable = t:isBlockable()")

        assertThat(environment.getGlobal("attackable").toboolean()).isTrue()
        assertThat(environment.getGlobal("summonable").toboolean()).isFalse()
        assertThat(environment.getGlobal("hostile").toboolean()).isTrue()
        assertThat(environment.getGlobal("blockable").toboolean()).isFalse()
        assertThat(environment.getGlobal("setAttackable").toboolean()).isTrue()
        assertThat(environment.getGlobal("setSummonable").toboolean()).isTrue()
        assertThat(environment.getGlobal("setHostile").toboolean()).isTrue()
        assertThat(environment.getGlobal("setBlockable").toboolean()).isTrue()
        assertThat(environment.getGlobal("convinceable").toboolean()).isFalse()
        assertThat(environment.getGlobal("setConvinceable").toboolean()).isTrue()
        assertThat(environment.getGlobal("newConvinceable").toboolean()).isTrue()
        assertThat(environment.getGlobal("newAttackable").toboolean()).isFalse()
        assertThat(environment.getGlobal("newSummonable").toboolean()).isTrue()
        assertThat(environment.getGlobal("newHostile").toboolean()).isFalse()
        assertThat(environment.getGlobal("newBlockable").toboolean()).isTrue()
    }

    @Test
    fun `MonsterType getTypeName and canSpawn use the Canary names`() {
        val (environment, fake, _) = installed()
        environment.globals.set("pos", pushPosition(environment, Position(10, 20, 7)))

        environment.loadChunk("typeName = t:getTypeName()")
        environment.loadChunk("spawn = t:canSpawn(pos)")

        assertThat(environment.getGlobal("typeName").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("spawn").toboolean()).isTrue()
        assertThat(fake.spawnPositions).containsExactly(Position(10, 20, 7))
    }

    @Test
    fun `MonsterType getLoot renders the loot block table`() {
        val type = FakeType(
            loot = mutableListOf(
                LootData(
                    id = 2148,
                    chance = 50_000,
                    subType = -1,
                    countMin = 1,
                    countMax = 5,
                    actionId = -1,
                    unique = false,
                    childLoot = mutableListOf(LootData(id = 2160, chance = 1000)),
                ),
            ),
        )
        val (environment, _, _) = installed(type = type)

        environment.loadChunk(
            """
            loot = t:getLoot()
            itemId = loot[1].itemId
            chance = loot[1].chance
            subType = loot[1].subType
            maxCount = loot[1].maxCount
            minCount = loot[1].minCount
            actionId = loot[1].actionId
            unique = loot[1].unique
            childId = loot[1].childLoot[1].itemId
            count = #loot
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("itemId").toint()).isEqualTo(2148)
        assertThat(environment.getGlobal("chance").toint()).isEqualTo(50_000)
        assertThat(environment.getGlobal("subType").toint()).isEqualTo(-1)
        assertThat(environment.getGlobal("maxCount").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("minCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("actionId").toint()).isEqualTo(-1)
        assertThat(environment.getGlobal("unique").toboolean()).isFalse()
        assertThat(environment.getGlobal("childId").toint()).isEqualTo(2160)
    }

    @Test
    fun `MonsterType addLoot loads a Loot userdata into the seam`() {
        val (environment, fake, type) = installed()
        LootFunctions.install(environment, UnwiredLootOperations)

        environment.loadChunk(
            """
            loot = Loot('Rat')
            loot:setId(2160)
            loot:setMaxCount(3)
            added = t:addLoot(loot)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("added").toboolean()).isTrue()
        assertThat(fake.lastLoot?.id).isEqualTo(2160)
        assertThat(fake.lastLoot?.countMax).isEqualTo(3)
        assertThat(type.loot.single().id).isEqualTo(2160)
    }

    @Test
    fun `MonsterType getAttackList renders the attack table`() {
        val type = FakeType(
            attacks = mutableListOf(
                MonsterAttackInfo(
                    chance = 20,
                    isCombatSpell = true,
                    isMelee = false,
                    minCombatValue = 10,
                    maxCombatValue = 20,
                    range = 7,
                    speed = 2000,
                ),
            ),
        )
        val (environment, _, _) = installed(type = type)

        environment.loadChunk(
            """
            attacks = t:getAttackList()
            chance = attacks[1].chance
            isCombatSpell = attacks[1].isCombatSpell
            isMelee = attacks[1].isMelee
            minValue = attacks[1].minCombatValue
            maxValue = attacks[1].maxCombatValue
            range = attacks[1].range
            speed = attacks[1].speed
            spell = attacks[1].spell
            count = #attacks
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("chance").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("isCombatSpell").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("isMelee").toint()).isZero()
        assertThat(environment.getGlobal("minValue").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("maxValue").toint()).isEqualTo(20)
        assertThat(environment.getGlobal("range").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("spell").isnil()).isTrue()
    }

    @Test
    fun `MonsterType boolean state flags use the Canary names`() {
        val (environment, _, type) = installed()

        environment.loadChunk(
            """
            preyable = t:isPreyable()
            preyExclusive = t:isPreyExclusive()
            illusionable = t:isIllusionable()
            pushable = t:isPushable()
            healthHidden = t:isHealthHidden()
            forge = t:isForgeCreature()
            familiar = t:familiar()
            rewardBoss = t:isRewardBoss()
            pushItems = t:canPushItems()
            pushCreatures = t:canPushCreatures()
            walkEnergy = t:canWalkOnEnergy()
            walkFire = t:canWalkOnFire()
            walkPoison = t:canWalkOnPoison()
            preferPlayer = t:targetPreferPlayer()
            preferMaster = t:targetPreferMaster()
            setPrey = t:isPreyable(false)
            setPreyExclusive = t:isPreyExclusive(true)
            setIllusion = t:isIllusionable(true)
            setPush = t:isPushable(false)
            setHidden = t:isHealthHidden(true)
            setForge = t:isForgeCreature(true)
            setFamiliar = t:familiar(true)
            setReward = t:isRewardBoss(true)
            setPushItems = t:canPushItems(true)
            setPushCreatures = t:canPushCreatures(true)
            setWalkEnergy = t:canWalkOnEnergy(true)
            setWalkFire = t:canWalkOnFire(false)
            setWalkPoison = t:canWalkOnPoison(false)
            setPreferPlayer = t:targetPreferPlayer(true)
            setPreferMaster = t:targetPreferMaster(true)
            newPrey = t:isPreyable()
            newPreyExclusive = t:isPreyExclusive()
            newIllusion = t:isIllusionable()
            newPush = t:isPushable()
            newHidden = t:isHealthHidden()
            newForge = t:isForgeCreature()
            newFamiliar = t:familiar()
            newReward = t:isRewardBoss()
            newPushItems = t:canPushItems()
            newPushCreatures = t:canPushCreatures()
            newWalkEnergy = t:canWalkOnEnergy()
            newWalkFire = t:canWalkOnFire()
            newWalkPoison = t:canWalkOnPoison()
            newPreferPlayer = t:targetPreferPlayer()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("preyable").toboolean()).isTrue()
        assertThat(environment.getGlobal("preyExclusive").toboolean()).isFalse()
        assertThat(environment.getGlobal("illusionable").toboolean()).isFalse()
        assertThat(environment.getGlobal("pushable").toboolean()).isTrue()
        assertThat(environment.getGlobal("healthHidden").toboolean()).isFalse()
        assertThat(environment.getGlobal("forge").toboolean()).isFalse()
        assertThat(environment.getGlobal("familiar").toboolean()).isFalse()
        assertThat(environment.getGlobal("rewardBoss").toboolean()).isFalse()
        assertThat(environment.getGlobal("pushItems").toboolean()).isFalse()
        assertThat(environment.getGlobal("pushCreatures").toboolean()).isFalse()
        assertThat(environment.getGlobal("walkEnergy").toboolean()).isFalse()
        assertThat(environment.getGlobal("walkFire").toboolean()).isFalse()
        assertThat(environment.getGlobal("walkPoison").toboolean()).isFalse()
        assertThat(environment.getGlobal("preferPlayer").toboolean()).isFalse()
        // Canary's luaMonsterTypeTargetPreferMaster getter pushes info.faction
        // (monster_type_functions.cpp:669) instead of the boolean; the port keeps that.
        assertThat(environment.getGlobal("preferMaster").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("setPrey").toboolean()).isTrue()
        assertThat(environment.getGlobal("setPreyExclusive").toboolean()).isTrue()
        assertThat(environment.getGlobal("setIllusion").toboolean()).isTrue()
        assertThat(environment.getGlobal("setPush").toboolean()).isTrue()
        assertThat(environment.getGlobal("setHidden").toboolean()).isTrue()
        assertThat(environment.getGlobal("setForge").toboolean()).isTrue()
        assertThat(environment.getGlobal("setFamiliar").toboolean()).isTrue()
        assertThat(environment.getGlobal("setReward").toboolean()).isTrue()
        assertThat(environment.getGlobal("setPushItems").toboolean()).isTrue()
        assertThat(environment.getGlobal("setPushCreatures").toboolean()).isTrue()
        assertThat(environment.getGlobal("setWalkEnergy").toboolean()).isTrue()
        assertThat(environment.getGlobal("setWalkFire").toboolean()).isTrue()
        assertThat(environment.getGlobal("setWalkPoison").toboolean()).isTrue()
        assertThat(environment.getGlobal("setPreferPlayer").toboolean()).isTrue()
        assertThat(environment.getGlobal("setPreferMaster").toboolean()).isTrue()
        assertThat(environment.getGlobal("newPrey").toboolean()).isFalse()
        assertThat(environment.getGlobal("newPreyExclusive").toboolean()).isTrue()
        assertThat(environment.getGlobal("newIllusion").toboolean()).isTrue()
        assertThat(environment.getGlobal("newPush").toboolean()).isFalse()
        assertThat(environment.getGlobal("newHidden").toboolean()).isTrue()
        assertThat(environment.getGlobal("newForge").toboolean()).isTrue()
        assertThat(environment.getGlobal("newFamiliar").toboolean()).isTrue()
        assertThat(environment.getGlobal("newReward").toboolean()).isTrue()
        assertThat(environment.getGlobal("newPushItems").toboolean()).isTrue()
        assertThat(environment.getGlobal("newPushCreatures").toboolean()).isTrue()
        assertThat(environment.getGlobal("newWalkEnergy").toboolean()).isTrue()
        assertThat(environment.getGlobal("newWalkFire").toboolean()).isFalse()
        assertThat(environment.getGlobal("newWalkPoison").toboolean()).isFalse()
        assertThat(environment.getGlobal("newPreferPlayer").toboolean()).isTrue()
        assertThat(type.preferMaster).isTrue()
    }

    @Test
    fun `MonsterType numeric and string attributes use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            description = t:nameDescription()
            setDescription = t:nameDescription('A giant rat')
            newDescription = t:nameDescription()
            runHealth = t:runHealth()
            setRunHealth = t:runHealth(5)
            newRunHealth = t:runHealth()
            manaCost = t:manaCost()
            setManaCost = t:manaCost(120)
            newManaCost = t:manaCost()
            armor = t:armor()
            setArmor = t:armor(7)
            newArmor = t:armor()
            defense = t:defense()
            setDefense = t:defense(9)
            newDefense = t:defense()
            staticChance = t:staticAttackChance()
            setStaticChance = t:staticAttackChance(80)
            newStaticChance = t:staticAttackChance()
            distance = t:targetDistance()
            setDistance = t:targetDistance(5)
            newDistance = t:targetDistance()
            yellChance = t:yellChance()
            setYellChance = t:yellChance(30)
            newYellChance = t:yellChance()
            yellSpeed = t:yellSpeedTicks()
            setYellSpeed = t:yellSpeedTicks(4000)
            newYellSpeed = t:yellSpeedTicks()
            changeChance = t:changeTargetChance()
            setChangeChance = t:changeTargetChance(10)
            newChangeChance = t:changeTargetChance()
            changeSpeed = t:changeTargetSpeed()
            setChangeSpeed = t:changeTargetSpeed(1500)
            newChangeSpeed = t:changeTargetSpeed()
            nearest = t:strategiesTargetNearest()
            setNearest = t:strategiesTargetNearest(1)
            newNearest = t:strategiesTargetNearest()
            healthStrategy = t:strategiesTargetHealth()
            setHealthStrategy = t:strategiesTargetHealth(2)
            newHealthStrategy = t:strategiesTargetHealth()
            damageStrategy = t:strategiesTargetDamage()
            setDamageStrategy = t:strategiesTargetDamage(3)
            newDamageStrategy = t:strategiesTargetDamage()
            randomStrategy = t:strategiesTargetRandom()
            setRandomStrategy = t:strategiesTargetRandom(4)
            newRandomStrategy = t:strategiesTargetRandom()
            period = t:respawnTypePeriod()
            setPeriod = t:respawnTypePeriod(2)
            newPeriod = t:respawnTypePeriod()
            underground = t:respawnTypeIsUnderground()
            setUnderground = t:respawnTypeIsUnderground(1)
            newUnderground = t:respawnTypeIsUnderground()
            chance = t:soundChance()
            setChance = t:soundChance(25)
            newChance = t:soundChance()
            soundSpeed = t:soundSpeedTicks()
            setSoundSpeed = t:soundSpeedTicks(2500)
            newSoundSpeed = t:soundSpeedTicks()
            death = t:deathSound()
            setDeath = t:deathSound(2600)
            newDeath = t:deathSound()
            variant = t:variant()
            setVariant = t:variant('shiny')
            newVariant = t:variant()
            maxSummons = t:maxSummons()
            setMaxSummons = t:maxSummons(3)
            newMaxSummons = t:maxSummons()
            raceId = t:raceId()
            setRaceId = t:raceId(44)
            newRaceId = t:raceId()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("description").tojstring()).isEqualTo("a rat")
        assertThat(environment.getGlobal("setDescription").toboolean()).isTrue()
        assertThat(environment.getGlobal("newDescription").tojstring()).isEqualTo("A giant rat")
        assertThat(environment.getGlobal("runHealth").toint()).isZero()
        assertThat(environment.getGlobal("newRunHealth").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("manaCost").toint()).isZero()
        assertThat(environment.getGlobal("newManaCost").toint()).isEqualTo(120)
        assertThat(environment.getGlobal("armor").toint()).isZero()
        assertThat(environment.getGlobal("newArmor").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("defense").toint()).isZero()
        assertThat(environment.getGlobal("newDefense").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("staticChance").toint()).isEqualTo(95)
        assertThat(environment.getGlobal("newStaticChance").toint()).isEqualTo(80)
        assertThat(environment.getGlobal("distance").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("newDistance").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("yellChance").toint()).isZero()
        assertThat(environment.getGlobal("newYellChance").toint()).isEqualTo(30)
        assertThat(environment.getGlobal("yellSpeed").toint()).isZero()
        assertThat(environment.getGlobal("newYellSpeed").toint()).isEqualTo(4000)
        assertThat(environment.getGlobal("changeChance").toint()).isZero()
        assertThat(environment.getGlobal("newChangeChance").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("changeSpeed").toint()).isZero()
        assertThat(environment.getGlobal("newChangeSpeed").toint()).isEqualTo(1500)
        assertThat(environment.getGlobal("nearest").toint()).isZero()
        assertThat(environment.getGlobal("newNearest").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("healthStrategy").toint()).isZero()
        assertThat(environment.getGlobal("newHealthStrategy").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("damageStrategy").toint()).isZero()
        assertThat(environment.getGlobal("newDamageStrategy").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("randomStrategy").toint()).isZero()
        assertThat(environment.getGlobal("newRandomStrategy").toint()).isEqualTo(4)
        assertThat(environment.getGlobal("period").toint()).isZero()
        assertThat(environment.getGlobal("newPeriod").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("underground").toint()).isZero()
        assertThat(environment.getGlobal("newUnderground").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("chance").toint()).isZero()
        assertThat(environment.getGlobal("newChance").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("soundSpeed").toint()).isZero()
        assertThat(environment.getGlobal("newSoundSpeed").toint()).isEqualTo(2500)
        assertThat(environment.getGlobal("death").toint()).isZero()
        assertThat(environment.getGlobal("newDeath").toint()).isEqualTo(2600)
        assertThat(environment.getGlobal("variant").tojstring()).isEmpty()
        assertThat(environment.getGlobal("newVariant").tojstring()).isEqualTo("shiny")
        assertThat(environment.getGlobal("maxSummons").toint()).isZero()
        assertThat(environment.getGlobal("newMaxSummons").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("raceId").toint()).isZero()
        assertThat(environment.getGlobal("newRaceId").toint()).isEqualTo(44)
    }

    @Test
    fun `MonsterType critChance uses one call for get and set`() {
        val (environment, _, type) = installed()

        environment.loadChunk("before = t:critChance()")
        environment.loadChunk("set = t:critChance(25)")
        environment.loadChunk("after = t:critChance()")

        assertThat(environment.getGlobal("before").toint()).isZero()
        assertThat(environment.getGlobal("set").toint()).isEqualTo(25)
        assertThat(environment.getGlobal("after").toint()).isEqualTo(25)
        assertThat(type.critChance).isEqualTo(25)
    }

    @Test
    fun `MonsterType mitigation uses floating point values`() {
        val (environment, _, type) = installed()

        environment.loadChunk("before = t:mitigation()")
        environment.loadChunk("set = t:mitigation(1.5)")
        environment.loadChunk("after = t:mitigation()")

        assertThat(environment.getGlobal("before").todouble()).isZero()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("after").todouble()).isEqualTo(1.5)
        assertThat(type.mitigation).isEqualTo(1.5f)
    }

    @Test
    fun `MonsterType light getter returns level and color in Canary order`() {
        val (environment, _, _) = installed()

        environment.loadChunk("level, color = t:light()")
        environment.loadChunk("set = t:light(7, 3)")
        environment.loadChunk("newLevel, newColor = t:light()")

        assertThat(environment.getGlobal("level").toint()).isZero()
        assertThat(environment.getGlobal("color").toint()).isEqualTo(215)
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("newLevel").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("newColor").toint()).isEqualTo(7)
    }

    @Test
    fun `MonsterType outfit round-trips the Canary table fields`() {
        val type = FakeType(outfit = Outfit(lookType = 136, lookWing = 5, lookEffect = 6, lookShader = 7, lookAura = 8))
        val (environment, _, _) = installed(type = type)

        environment.loadChunk(
            """
            initial = t:outfit()
            initialType = initial.lookType
            initialWing = initial['lookWing ']
            initialAura = initial.lookAura
            initialEffect = initial['lookEffect ']
            initialShader = initial['lookShader ']
            accepted = t:outfit({lookType = 21, lookHead = 19, lookBody = 20, lookLegs = 21, lookFeet = 22, lookAddons = 3, lookMount = 45, lookShader = 9})
            outfit = t:outfit()
            lookType = outfit.lookType
            lookHead = outfit.lookHead
            lookMount = outfit.lookMount
            wing = outfit['lookWing ']
            aura = outfit.lookAura
            effect = outfit['lookEffect ']
            shader = outfit['lookShader ']
            plainWing = outfit.lookWing
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("initialType").toint()).isEqualTo(136)
        assertThat(environment.getGlobal("initialWing").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("initialAura").toint()).isEqualTo(8)
        assertThat(environment.getGlobal("initialEffect").toint()).isEqualTo(6)
        assertThat(environment.getGlobal("initialShader").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("accepted").toboolean()).isTrue()
        assertThat(environment.getGlobal("lookType").toint()).isEqualTo(21)
        assertThat(environment.getGlobal("lookHead").toint()).isEqualTo(19)
        assertThat(environment.getGlobal("lookMount").toint()).isEqualTo(45)
        // Lua::getOutfit reads lookShader into lookWing, and pushOutfit stores lookWing
        // under the "lookWing " key (trailing space), so the round trip yields 9.
        assertThat(environment.getGlobal("wing").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("aura").toint()).isZero()
        assertThat(environment.getGlobal("effect").toint()).isZero()
        assertThat(environment.getGlobal("shader").toint()).isEqualTo(9)
        assertThat(environment.getGlobal("plainWing").isnil()).isTrue()
    }

    @Test
    fun `MonsterType outfit setter can reject an unsafe look type`() {
        val type = FakeType(outfitAccepted = false)
        val (environment, _, _) = installed(type = type)

        environment.loadChunk("blocked = t:outfit({lookType = 999999})")

        assertThat(environment.getGlobal("blocked").isnil()).isTrue()
        assertThat(type.outfit.lookType).isZero()
    }

    @Test
    fun `MonsterType element reflect and healing maps use the Canary names`() {
        val (environment, _, type) = installed()

        environment.loadChunk(
            """
            fire = t:addElement(1, 100)
            earth = t:addElement(2, 50)
            reflect = t:addReflect(3, 20)
            healing = t:addHealing(7, 10)
            elements = t:getElementList()
            fireValue = elements[1]
            earthValue = elements[2]
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("fire").toboolean()).isTrue()
        assertThat(environment.getGlobal("earth").toboolean()).isTrue()
        assertThat(environment.getGlobal("reflect").toboolean()).isTrue()
        assertThat(environment.getGlobal("healing").toboolean()).isTrue()
        assertThat(environment.getGlobal("fireValue").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("earthValue").toint()).isEqualTo(50)
        assertThat(type.reflects[3]).isEqualTo(20)
        assertThat(type.healings[7]).isEqualTo(10)
    }

    @Test
    fun `MonsterType combat and condition immunities use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            physical = t:combatImmunities('physical')
            energy = t:combatImmunities('energy')
            fire = t:combatImmunities('fire')
            earth = t:combatImmunities('earth')
            drown = t:combatImmunities('drown')
            ice = t:combatImmunities('ice')
            holy = t:combatImmunities('holy')
            death = t:combatImmunities('death')
            life = t:combatImmunities('lifedrain')
            mana = t:combatImmunities('manadrain')
            agony = t:combatImmunities('agony')
            neutral = t:combatImmunities('neutral')
            unknown = t:combatImmunities('plasma')
            combat = t:combatImmunities()
            combatPhysical = combat[0]
            combatFire = combat[1]
            combatEnergy = combat[3]
            combatEarth = combat[2]
            combatDrown = combat[8]
            combatIce = combat[9]
            combatHoly = combat[10]
            combatDeath = combat[11]
            combatLife = combat[5]
            combatMana = combat[6]
            combatAgony = combat[12]
            combatNeutral = combat[13]
            bleedPhysical = t:conditionImmunities('physical')
            condEnergy = t:conditionImmunities('energy')
            condFire = t:conditionImmunities('fire')
            condPoison = t:conditionImmunities('poison')
            condDrown = t:conditionImmunities('drown')
            condIce = t:conditionImmunities('ice')
            condHoly = t:conditionImmunities('holy')
            condDeath = t:conditionImmunities('death')
            condParalyze = t:conditionImmunities('paralyze')
            condOutfit = t:conditionImmunities('outfit')
            condDrunk = t:conditionImmunities('drunk')
            condInvisible = t:conditionImmunities('invisibility')
            condBleed = t:conditionImmunities('bleed')
            condUnknown = t:conditionImmunities('plasma')
            conditions = t:conditionImmunities()
            condPhysical = conditions[4]
            condEnergy = conditions[3]
            condFire = conditions[2]
            condPoison = conditions[1]
            condDrown = conditions[16]
            condFreezing = conditions[21]
            condDazzled = conditions[22]
            condCursed = conditions[23]
            condParalyzeIndex = conditions[6]
            condOutfitIndex = conditions[7]
            condDrunkIndex = conditions[12]
            condInvisibleIndex = conditions[8]
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("physical").toboolean()).isTrue()
        assertThat(environment.getGlobal("energy").toboolean()).isTrue()
        assertThat(environment.getGlobal("fire").toboolean()).isTrue()
        assertThat(environment.getGlobal("earth").toboolean()).isTrue()
        assertThat(environment.getGlobal("drown").toboolean()).isTrue()
        assertThat(environment.getGlobal("ice").toboolean()).isTrue()
        assertThat(environment.getGlobal("holy").toboolean()).isTrue()
        assertThat(environment.getGlobal("death").toboolean()).isTrue()
        assertThat(environment.getGlobal("life").toboolean()).isTrue()
        assertThat(environment.getGlobal("mana").toboolean()).isTrue()
        assertThat(environment.getGlobal("agony").toboolean()).isTrue()
        assertThat(environment.getGlobal("neutral").toboolean()).isTrue()
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
        assertThat(environment.getGlobal("combatPhysical").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatFire").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatEnergy").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatEarth").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatDrown").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatIce").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatHoly").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatDeath").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatLife").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatMana").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatAgony").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("combatNeutral").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("bleedPhysical").toboolean()).isTrue()
        assertThat(environment.getGlobal("condEnergy").toboolean()).isTrue()
        assertThat(environment.getGlobal("condFire").toboolean()).isTrue()
        assertThat(environment.getGlobal("condPoison").toboolean()).isTrue()
        assertThat(environment.getGlobal("condDrown").toboolean()).isTrue()
        assertThat(environment.getGlobal("condIce").toboolean()).isTrue()
        assertThat(environment.getGlobal("condHoly").toboolean()).isTrue()
        assertThat(environment.getGlobal("condDeath").toboolean()).isTrue()
        assertThat(environment.getGlobal("condParalyze").toboolean()).isTrue()
        assertThat(environment.getGlobal("condOutfit").toboolean()).isTrue()
        assertThat(environment.getGlobal("condDrunk").toboolean()).isTrue()
        assertThat(environment.getGlobal("condInvisible").toboolean()).isTrue()
        assertThat(environment.getGlobal("condBleed").toboolean()).isTrue()
        assertThat(environment.getGlobal("condUnknown").isnil()).isTrue()
        assertThat(environment.getGlobal("condPhysical").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condEnergy").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condFire").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condPoison").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condDrown").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condFreezing").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condDazzled").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condCursed").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condParalyzeIndex").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condOutfitIndex").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condDrunkIndex").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("condInvisibleIndex").toint()).isEqualTo(1)
    }

    @Test
    fun `MonsterType defense list and attack and defense registration use the Canary names`() {
        val type = FakeType(
            defenses = mutableListOf(
                MonsterAttackInfo(chance = 15, isMelee = true, range = 1, speed = 2000),
            ),
        )
        val (environment, _, _) = installed(type = type)
        environment.globals.set("spell", environment.userdata.wrap("MonsterSpell", "exori"))

        environment.loadChunk(
            """
            attack = t:addAttack(spell)
            defense = t:addDefense(spell)
            list = t:getDefenseList()
            chance = list[1].chance
            isMelee = list[1].isMelee
            range = list[1].range
            count = #list
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("attack").isnil()).isTrue()
        assertThat(environment.getGlobal("defense").isnil()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("chance").toint()).isEqualTo(15)
        assertThat(environment.getGlobal("isMelee").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("range").toint()).isEqualTo(1)
        assertThat(type.lastAttackSpell).isEqualTo("exori")
        assertThat(type.lastDefenseSpell).isEqualTo("exori")
    }

    @Test
    fun `MonsterType voices use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            add = t:addVoice('Grrr', 3000, 25, true)
            voices = t:getVoices()
            text = voices[1].text
            yellText = voices[1].yellText
            count = #voices
            yellSpeed = t:yellSpeedTicks()
            yellChance = t:yellChance()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("add").toboolean()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("text").tojstring()).isEqualTo("Grrr")
        assertThat(environment.getGlobal("yellText").toboolean()).isTrue()
        assertThat(environment.getGlobal("yellSpeed").toint()).isEqualTo(3000)
        assertThat(environment.getGlobal("yellChance").toint()).isEqualTo(25)
    }

    @Test
    fun `MonsterType summons use the Canary names`() {
        val (environment, _, type) = installed()

        environment.loadChunk(
            """
            first = t:addSummon('Rat', 2000, 15)
            second = t:addSummon('Wolf', 3000, 5, 3)
            list = t:getSummonList()
            name = list[1].name
            speed = list[1].speed
            chance = list[1].chance
            secondName = list[2].name
            secondSpeed = list[2].speed
            secondChance = list[2].chance
            count = #list
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("first").toboolean()).isTrue()
        assertThat(environment.getGlobal("second").toboolean()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("name").tojstring()).isEqualTo("Rat")
        assertThat(environment.getGlobal("speed").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("chance").toint()).isEqualTo(15)
        assertThat(environment.getGlobal("secondName").tojstring()).isEqualTo("Wolf")
        assertThat(environment.getGlobal("secondSpeed").toint()).isEqualTo(3000)
        assertThat(environment.getGlobal("secondChance").toint()).isEqualTo(5)
        assertThat(type.lastSummonCount).isEqualTo(3)
    }

    @Test
    fun `MonsterType creature events and callbacks use the Canary names`() {
        val (environment, _, type) = installed()

        environment.loadChunk(
            """
            register = t:registerEvent('onDeath')
            events = t:getCreatureEvents()
            first = events[1]
            count = #events
            t:eventType(1)
            think = t:onThink(function() return true end)
            t:eventType(2)
            appear = t:onAppear(function() return true end)
            t:eventType(3)
            disappear = t:onDisappear(function() return true end)
            t:eventType(4)
            move = t:onMove(function() return true end)
            t:eventType(5)
            say = t:onSay(function() return true end)
            t:eventType(6)
            attack = t:onPlayerAttack(function() return true end)
            t:eventType(7)
            spawn = t:onSpawn(function() return true end)
            eventType = t:eventType(5)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("register").toboolean()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("first").tojstring()).isEqualTo("onDeath")
        assertThat(environment.getGlobal("think").toboolean()).isTrue()
        assertThat(environment.getGlobal("appear").toboolean()).isTrue()
        assertThat(environment.getGlobal("disappear").toboolean()).isTrue()
        assertThat(environment.getGlobal("move").toboolean()).isTrue()
        assertThat(environment.getGlobal("say").toboolean()).isTrue()
        assertThat(environment.getGlobal("attack").toboolean()).isTrue()
        assertThat(environment.getGlobal("spawn").toboolean()).isTrue()
        assertThat(environment.getGlobal("eventType").toboolean()).isTrue()
        assertThat(type.eventType).isEqualTo(5)
    }

    @Test
    fun `MonsterType callbacks report a failed load`() {
        val type = FakeType(callbackLoaded = false)
        val (environment, _, _) = installed(type = type)

        environment.loadChunk("think = t:onThink(function() return true end)")

        assertThat(environment.getGlobal("think").toboolean()).isFalse()
    }

    @Test
    fun `MonsterType sounds mirror the Canary getSounds stack behavior`() {
        val (environment, _, type) = installed()

        environment.loadChunk(
            """
            first = t:addSound(2518)
            second = t:addSound(2532)
            sounds = t:getSounds()
            last = sounds[2]
            missingFirst = sounds[1]
            length = #sounds
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("first").toboolean()).isTrue()
        assertThat(environment.getGlobal("second").toboolean()).isTrue()
        assertThat(environment.getGlobal("last").toint()).isEqualTo(2532)
        assertThat(environment.getGlobal("missingFirst").isnil()).isTrue()
        assertThat(environment.getGlobal("length").toint()).isZero()
        assertThat(type.sounds).containsExactly(2518, 2532)
    }

    @Test
    fun `MonsterType boss race and boss race id use the Canary names`() {
        val (environment, fake, type) = installed()

        environment.loadChunk(
            """
            emptyClass = t:bossRace()
            emptyId = t:bossRaceId()
            set = t:bossRace(1, 'Nemesis')
            class = t:bossRace()
            id = t:bossRaceId()
            setRace = t:bossRaceId(44)
            newRaceId = t:raceId()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("emptyClass").isnil()).isTrue()
        assertThat(environment.getGlobal("emptyId").toint()).isZero()
        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("class").tojstring()).isEqualTo("Nemesis")
        assertThat(environment.getGlobal("id").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("setRace").toboolean()).isTrue()
        assertThat(environment.getGlobal("newRaceId").toint()).isEqualTo(44)
        assertThat(type.typeName).isEqualTo("Rat")
        assertThat(fake.bosstiaryMonsters).containsExactly(44 to "Rat")
    }

    @Test
    fun `MonsterType faction and enemy factions use the Canary names`() {
        val (environment, _, type) = installed()

        environment.loadChunk(
            """
            faction = t:faction()
            setFaction = t:faction(3)
            newFaction = t:faction()
            addEnemy = t:enemyFactions(2)
             addEnemyTwo = t:enemyFactions(5)
            enemies = t:enemyFactions()
            first = enemies[1]
            second = enemies[2]
            count = #enemies
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("faction").toint()).isEqualTo(7)
        assertThat(environment.getGlobal("setFaction").toboolean()).isTrue()
        assertThat(environment.getGlobal("newFaction").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("addEnemy").toboolean()).isTrue()
        assertThat(environment.getGlobal("addEnemyTwo").toboolean()).isTrue()
        assertThat(environment.getGlobal("count").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("first").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("second").toint()).isEqualTo(5)
        assertThat(type.enemyFactions).containsExactly(2, 5)
    }

    @Test
    fun `MonsterType raceId also registers the bestiary list entry`() {
        val (environment, fake, _) = installed()

        environment.loadChunk("set = t:raceId(44)")

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(fake.bestiaryList).containsExactly(44 to "Rat")
    }

    @Test
    fun `MonsterType bestiary attributes use the Canary names`() {
        val (environment, _, _) = installed()

        environment.loadChunk(
            """
            occurrence = t:BestiaryOccurrence()
            setOccurrence = t:BestiaryOccurrence(10)
            newOccurrence = t:BestiaryOccurrence()
            locations = t:BestiaryLocations()
            setLocations = t:BestiaryLocations('Rookgaard')
            newLocations = t:BestiaryLocations()
            stars = t:BestiaryStars()
            setStars = t:BestiaryStars(3)
            newStars = t:BestiaryStars()
            charms = t:BestiaryCharmsPoints()
            setCharms = t:BestiaryCharmsPoints(50)
            newCharms = t:BestiaryCharmsPoints()
            second = t:BestiarySecondUnlock()
            setSecond = t:BestiarySecondUnlock(500)
            newSecond = t:BestiarySecondUnlock()
            first = t:BestiaryFirstUnlock()
            setFirst = t:BestiaryFirstUnlock(100)
            newFirst = t:BestiaryFirstUnlock()
            toKill = t:BestiarytoKill()
            setToKill = t:BestiarytoKill(250)
            newToKill = t:BestiarytoKill()
            race = t:Bestiaryrace()
            setRace = t:Bestiaryrace(14)
            newRace = t:Bestiaryrace()
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("occurrence").toint()).isZero()
        assertThat(environment.getGlobal("newOccurrence").toint()).isEqualTo(10)
        assertThat(environment.getGlobal("locations").tojstring()).isEmpty()
        assertThat(environment.getGlobal("setLocations").toboolean()).isTrue()
        assertThat(environment.getGlobal("newLocations").tojstring()).isEqualTo("Rookgaard")
        assertThat(environment.getGlobal("stars").toint()).isZero()
        assertThat(environment.getGlobal("newStars").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("charms").toint()).isZero()
        assertThat(environment.getGlobal("newCharms").toint()).isEqualTo(50)
        assertThat(environment.getGlobal("second").toint()).isZero()
        assertThat(environment.getGlobal("newSecond").toint()).isEqualTo(500)
        assertThat(environment.getGlobal("first").toint()).isZero()
        assertThat(environment.getGlobal("newFirst").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("toKill").toint()).isZero()
        assertThat(environment.getGlobal("newToKill").toint()).isEqualTo(250)
        assertThat(environment.getGlobal("race").toint()).isZero()
        assertThat(environment.getGlobal("newRace").toint()).isEqualTo(14)
    }

    @Test
    fun `MonsterType getMonstersByRace and getMonstersByBestiaryStars wrap the catalog`() {
        val fake = FakeMonsterTypeOps()
        val rat = FakeType(name = "Rat")
        val wolf = FakeType(name = "Wolf")
        fake.monstersByRace[14] = listOf(rat, wolf)
        fake.monstersByBestiaryStars[3] = listOf(rat)

        val environment = env()
        MonsterTypeFunctions.install(environment, fake)
        environment.globals.set("t", environment.userdata.wrap("MonsterType", rat))

        environment.loadChunk(
            """
            byRace = getmetatable(t).getMonstersByRace(14)
            raceCount = #byRace
            raceFirst = byRace[1]
            raceSecond = byRace[2]
            byStars = getmetatable(t).getMonstersByBestiaryStars(3)
            starsCount = #byStars
            starsFirst = byStars[1]
            colonRace = t:getMonstersByRace(14)
            colonCount = #colonRace
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("raceCount").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("raceFirst").touserdata()).isSameAs(rat)
        assertThat(environment.getGlobal("raceSecond").touserdata()).isSameAs(wolf)
        assertThat(environment.getGlobal("starsCount").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("starsFirst").touserdata()).isSameAs(rat)
        // The C++ methods read argument 1 as the race/stars value, so a colon call
        // passes the userdata and lua_tonumber(userdata) is 0 (BESTY_RACE_NONE).
        assertThat(environment.getGlobal("colonCount").toint()).isZero()
    }

    @Test
    fun `MonsterType invalid receivers mirror the Canary nil and false branches`() {
        val (environment, _, _) = installed()
        environment.globals.set("other", environment.userdata.wrap("Loot", LootData()))

        // Reached through the MonsterType metatable so the argument is a foreign
        // userdata, exactly the shape Canary's getUserdataShared<MonsterType> rejects.
        environment.loadChunk(
            """
            methods = getmetatable(t)
            name = methods.name(other)
            forge = methods.isForgeCreature(other)
            mitigation = methods.mitigation(other)
            combat = methods.combatImmunities(other)
            conditions = methods.conditionImmunities(other)
            soundChance = methods.soundChance(other)
            soundSpeed = methods.soundSpeedTicks(other)
            addSound = methods.addSound(other, 2518)
            sounds = methods.getSounds(other)
            deathSound = methods.deathSound(other)
            variant = methods.variant(other)
            boss = methods.bossRace(other)
            bossId = methods.bossRaceId(other)
            light = methods.light(other)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("name").isnil()).isTrue()
        assertThat(environment.getGlobal("forge").isnil()).isTrue()
        assertThat(environment.getGlobal("mitigation").toboolean()).isFalse()
        assertThat(environment.getGlobal("combat").toboolean()).isFalse()
        assertThat(environment.getGlobal("conditions").toboolean()).isFalse()
        assertThat(environment.getGlobal("soundChance").toboolean()).isFalse()
        assertThat(environment.getGlobal("soundSpeed").toboolean()).isFalse()
        assertThat(environment.getGlobal("addSound").toboolean()).isFalse()
        assertThat(environment.getGlobal("sounds").isnil()).isTrue()
        assertThat(environment.getGlobal("deathSound").toboolean()).isFalse()
        assertThat(environment.getGlobal("variant").toboolean()).isFalse()
        assertThat(environment.getGlobal("boss").isnil()).isTrue()
        assertThat(environment.getGlobal("bossId").isnil()).isTrue()
        assertThat(environment.getGlobal("light").isnil()).isTrue()
    }

}
