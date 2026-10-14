package org.opentibia.canary.lua.bindings.core

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.WorldLuaOperations
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.forge.ItemClassifications
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript-style parity tests for the full Canary `Game` table
 * (src/lua/functions/core/game/game_functions.cpp, 59 registered methods).
 *
 * The first group pins the exact Canary names on the `Game` table; the remaining groups
 * install the documented no-op seams and pin the observable contract for engines the
 * Kotlin domain does not model yet (empty tables, nil, false and the Canary return
 * codes).
 */
class GameFunctionsParityTest {

    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `Game registers every Canary game_functions method`() {
        val environment = env()
        GameFunctions.install(environment)

        val checks = CANARY_GAME_METHODS.joinToString(" and ") { "(Game.$it ~= nil)" }
        environment.loadChunk("checks = $checks")

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
        assertThat(CANARY_GAME_METHODS).hasSize(59)
    }

    @Test
    fun `Game getClientVersion mirrors the Canary constants`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk(
            "version = Game.getClientVersion() " +
                "min = version.min max = version.max text = version.string",
        )

        assertThat(environment.getGlobal("min").toint()).isEqualTo(1525)
        assertThat(environment.getGlobal("max").toint()).isEqualTo(1525)
        assertThat(environment.getGlobal("text").tojstring()).isEqualTo("15.25")
    }

    @Test
    fun `Game getReturnMessage mirrors the Canary return value strings`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk("noError = Game.getReturnMessage(0)")
        environment.loadChunk("notPossible = Game.getReturnMessage(4)")
        environment.loadChunk("creature = Game.getReturnMessage(30)")
        environment.loadChunk("noRaid = Game.getReturnMessage(70)")
        environment.loadChunk("untradeable = Game.getReturnMessage(${ReturnValue.ITEMUNTRADEABLE.ordinal})")
        environment.loadChunk("unknown = Game.getReturnMessage(999)")

        assertThat(environment.getGlobal("noError").tojstring()).isEqualTo("No error.")
        assertThat(environment.getGlobal("notPossible").tojstring()).isEqualTo("Sorry, not possible.")
        assertThat(environment.getGlobal("creature").tojstring()).isEqualTo("Creature does not exist.")
        assertThat(environment.getGlobal("noRaid").tojstring()).isEqualTo("No such raid exists.")
        assertThat(environment.getGlobal("untradeable").tojstring()).isEqualTo("This item is untradeable.")
        assertThat(environment.getGlobal("unknown").tojstring()).isEqualTo("Unknown error.")
    }

    @Test
    fun `Game table lookups from unwired engines are empty`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk(
            "monsterTypes = Game.getMonsterTypes() " +
                "towns = Game.getTowns() " +
                "houses = Game.getHouses() " +
                "charms = Game.getBestiaryCharm() " +
                "ladders = Game.getLadderIds() " +
                "dummies = Game.getDummies() " +
                "talkActions = Game.getTalkActions() " +
                "eventCallbacks = Game.getEventCallbacks() " +
                "secret = Game.getSecretAchievements() " +
                "public = Game.getPublicAchievements() " +
                "achievements = Game.getAchievements() " +
                "soulCores = Game.getSoulCoreItems() " +
                "byRace = Game.getMonstersByRace(1) " +
                "byStars = Game.getMonstersByBestiaryStars(2) " +
                "influenced = Game.getInfluencedMonsters() " +
                "fiendish = Game.getFiendishMonsters()",
        )
        environment.loadChunk(
            "monsterTypesEmpty = next(monsterTypes) == nil " +
                "dummiesEmpty = next(dummies) == nil " +
                "talkActionsEmpty = next(talkActions) == nil " +
                "eventCallbacksEmpty = next(eventCallbacks) == nil " +
                "townsEmpty = #towns == 0 and #houses == 0 " +
                "charmsEmpty = #charms == 0 and #ladders == 0 " +
                "achievementsEmpty = #secret == 0 and #public == 0 and #achievements == 0 " +
                "soulCoresEmpty = #soulCores == 0 " +
                "monstersEmpty = #byRace == 0 and #byStars == 0 " +
                "forgeEmpty = #influenced == 0 and #fiendish == 0",
        )

        assertThat(environment.getGlobal("monsterTypesEmpty").toboolean()).isTrue()
        assertThat(environment.getGlobal("dummiesEmpty").toboolean()).isTrue()
        assertThat(environment.getGlobal("talkActionsEmpty").toboolean()).isTrue()
        assertThat(environment.getGlobal("eventCallbacksEmpty").toboolean()).isTrue()
        assertThat(environment.getGlobal("townsEmpty").toboolean()).isTrue()
        assertThat(environment.getGlobal("charmsEmpty").toboolean()).isTrue()
        assertThat(environment.getGlobal("achievementsEmpty").toboolean()).isTrue()
        assertThat(environment.getGlobal("soulCoresEmpty").toboolean()).isTrue()
        assertThat(environment.getGlobal("monstersEmpty").toboolean()).isTrue()
        assertThat(environment.getGlobal("forgeEmpty").toboolean()).isTrue()
    }

    @Test
    fun `Game bestiary list spreads the Canary race filter branches`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk(
            "full = Game.getBestiaryList() " +
                "flag = Game.getBestiaryList(true) " +
                "raceByNumber = Game.getBestiaryList(false, 5, true) " +
                "raceByName = Game.getBestiaryList(false, 'Demon', true)",
        )
        environment.loadChunk(
            "empty = next(full) == nil and next(flag) == nil and " +
                "next(raceByNumber) == nil and next(raceByName) == nil",
        )

        assertThat(environment.getGlobal("empty").toboolean()).isTrue()
    }

    @Test
    fun `Game string lookups from unwired engines use the Canary defaults`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk(
            "boosted = Game.getBoostedCreature() " +
                "boss = Game.getBoostedBoss() " +
                "guild = Game.getNormalizedGuildName('Wolves') " +
                "missingType = Game.getMonsterTypeByName('Rat')",
        )

        assertThat(environment.getGlobal("boosted").tojstring()).isEmpty()
        assertThat(environment.getGlobal("boss").tojstring()).isEmpty()
        assertThat(environment.getGlobal("guild").isnil()).isTrue()
        assertThat(environment.getGlobal("missingType").isboolean()).isTrue()
        assertThat(environment.getGlobal("missingType").toboolean()).isFalse()
    }

    @Test
    fun `Game creation methods from unwired engines return nil`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk(
            "npcType = Game.createNpcType('Sam') " +
                "monsterType = Game.createMonsterType('Rat') " +
                "nonStringType = Game.createMonsterType(5) " +
                "container = Game.createContainer(1987, 3) " +
                "soulPit = Game.createSoulPitMonster('Rat', { x = 1, y = 2, z = 7 }) " +
                "generated = Game.generateNpc('Sam') " +
                "tile = Game.createTile({ x = 1, y = 2, z = 7 }) " +
                "tileXyz = Game.createTile(1, 2, 7, true) " +
                "charm = Game.createBestiaryCharm(1) " +
                "classification = Game.createItemClassification(1)",
        )

        for (name in listOf(
            "npcType", "monsterType", "nonStringType", "container", "soulPit",
            "generated", "tile", "tileXyz", "charm", "classification",
        )) {
            assertThat(environment.getGlobal(name).isnil()).describedAs(name).isTrue()
        }
    }

    @Test
    fun `Game unwired setters reload and effect lookups keep scripts running`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk(
            "stateSet = Game.setGameState(2) " +
                "worldTypeValue = Game.getWorldType() " +
                "worldTypeValueSet = Game.setWorldType(1) " +
                "effect = Game.hasEffect(1) " +
                "distanceEffect = Game.hasDistanceEffect(1) " +
                "reloadNone = select('#', Game.reload(0)) " +
                "reloadLast = select('#', Game.reload(18)) " +
                "reloadCore = Game.reload(11) " +
                "mapLoad = select('#', Game.loadMap('map.otbm')) " +
                "chunkLoad = select('#', Game.loadMapChunk('map.otbm', { x = 1, y = 2, z = 7 }))",
        )

        assertThat(environment.getGlobal("stateSet").toboolean()).isFalse()
        assertThat(environment.getGlobal("worldTypeValue").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("worldTypeValueSet").toboolean()).isFalse()
        assertThat(environment.getGlobal("effect").toboolean()).isFalse()
        assertThat(environment.getGlobal("distanceEffect").toboolean()).isFalse()
        assertThat(environment.getGlobal("reloadNone").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("reloadLast").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("reloadCore").toboolean()).isFalse()
        assertThat(environment.getGlobal("mapLoad").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("chunkLoad").toint()).isEqualTo(0)
    }

    @Test
    fun `Game raid and forge queries return the Canary empty-registry codes`() {
        val environment = env()
        GameFunctions.install(environment)
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 30, position = Position(1, 1, 7))
        environment.globals.set("monster", environment.userdata.wrap("Monster", monster))

        environment.loadChunk(
            "raid = Game.startRaid('Rats') " +
                "added = Game.addInfluencedMonster(monster) " +
                "addedInvalid = select('#', Game.addInfluencedMonster({})) " +
                "removedInfluenced = Game.removeInfluencedMonster(5) " +
                "removedInfluencedCreate = Game.removeInfluencedMonster(5, true) " +
                "madeFiendish = Game.makeFiendishMonster() " +
                "madeFiendishById = Game.makeFiendishMonster(5, true) " +
                "removedFiendish = Game.removeFiendishMonster(5) " +
                "removedFiendishCreate = Game.removeFiendishMonster(5, true)",
        )

        assertThat(environment.getGlobal("raid").toint()).isEqualTo(ReturnValue.NOSUCHRAIDEXISTS.ordinal)
        assertThat(environment.getGlobal("added").toboolean()).isFalse()
        assertThat(environment.getGlobal("addedInvalid").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("removedInfluenced").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("removedInfluencedCreate").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("madeFiendish").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("madeFiendishById").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("removedFiendish").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("removedFiendishCreate").toint()).isEqualTo(0)
    }

    @Test
    fun `Game achievement methods use the unwired defaults`() {
        val environment = env()
        GameFunctions.install(environment)

        environment.loadChunk(
            "registered = Game.registerAchievement(1, 'First', 'Description', false, 1, 10) " +
                "registeredPartial = Game.registerAchievement(1) " +
                "byId = Game.getAchievementInfoById(1) " +
                "byName = Game.getAchievementInfoByName('First')",
        )

        assertThat(environment.getGlobal("registered").toboolean()).isFalse()
        assertThat(environment.getGlobal("registeredPartial").isnil()).isTrue()
        assertThat(environment.getGlobal("byId").isnil()).isTrue()
        assertThat(environment.getGlobal("byName").isnil()).isTrue()
    }

    @Test
    fun `Game creation methods forward the Canary signature and wrap userdata`() {
        val environment = env()
        val operations = FakeGameOps()
        val marker = Any()
        operations.npcType = marker
        operations.monsterType = marker
        operations.container = Container(id = 1987, capacity = 5)
        val soulPitMonster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 30, position = Position(1, 1, 7))
        operations.soulPit = soulPitMonster
        operations.generatedNpc = marker
        operations.tile = marker
        operations.charm = marker
        operations.classification = marker
        GameFunctions.install(environment, operations)
        val master = environment.userdata.wrap("Monster", soulPitMonster)
        environment.globals.set("master", master)

        environment.loadChunk(
            "npcType = Game.createNpcType('Sam') " +
                "npcTypeByNumber = Game.createNpcType(5) " +
                "monsterType = Game.createMonsterType('Rat', 'shiny', 'Giant Rat') " +
                "invalidType = Game.createMonsterType({}) " +
                "container = Game.createContainer(1987, 5, { x = 1, y = 2, z = 7 }) " +
                "soulPit = Game.createSoulPitMonster('Rat', { x = 3, y = 4, z = 7 }, 2, true, true, master) " +
                "generated = Game.generateNpc('Sam') " +
                "tile = Game.createTile({ x = 5, y = 6, z = 7 }, true) " +
                "tileXyz = Game.createTile(8, 9, 7) " +
                "charmDefault = Game.createBestiaryCharm() " +
                "charm = Game.createBestiaryCharm(3) " +
                "classification = Game.createItemClassification(4)",
        )
        environment.loadChunk(
            "npcTypeMt = getmetatable(npcType) == rawgetmetatable('NpcType').__metatable " +
                "monsterTypeMt = getmetatable(monsterType) == rawgetmetatable('MonsterType').__metatable " +
                "containerMt = getmetatable(container) == rawgetmetatable('Container').__metatable " +
                "soulPitMt = getmetatable(soulPit) == rawgetmetatable('Monster').__metatable " +
                "generatedMt = getmetatable(generated) == rawgetmetatable('Npc').__metatable " +
                "tileMt = getmetatable(tile) == rawgetmetatable('Tile').__metatable",
        )

        assertThat(operations.npcTypeName).isEqualTo("5")
        assertThat(operations.monsterTypeCall).isEqualTo(Triple("Rat", "shiny", "Giant Rat"))
        assertThat(operations.containerCall).isEqualTo(Triple(1987, 5, Position(1, 2, 7)))
        assertThat(operations.soulPitCall)
            .isEqualTo(SoulPitCall("Rat", Position(3, 4, 7), 2, true, true, operations.soulPit))
        assertThat(operations.generatedNpcName).isEqualTo("Sam")
        assertThat(operations.tileCalls).containsExactly(
            Position(5, 6, 7) to true,
            Position(8, 9, 7) to false,
        )
        assertThat(operations.charmIds).containsExactly(0, 3)
        assertThat(operations.classificationId).isEqualTo(4)
        assertThat(environment.getGlobal("invalidType").isnil()).isTrue()
        assertThat(environment.getGlobal("npcTypeMt").toboolean()).isTrue()
        assertThat(environment.getGlobal("monsterTypeMt").toboolean()).isTrue()
        assertThat(environment.getGlobal("containerMt").toboolean()).isTrue()
        assertThat(environment.getGlobal("soulPitMt").toboolean()).isTrue()
        assertThat(environment.getGlobal("generatedMt").toboolean()).isTrue()
        assertThat(environment.getGlobal("tileMt").toboolean()).isTrue()
    }

    @Test
    fun `Game lookups forward the Canary arguments and wrap userdata`() {
        val environment = env()
        val operations = FakeGameOps()
        operations.monsterTypeCatalog["Rat"] = Any()
        operations.monsterTypeLookup = Any()
        operations.bestiaryEntries[1] = "Rat"
        operations.townList += Any()
        operations.houseList += Any()
        operations.charmList += Any()
        operations.ladderIdList += 3031
        operations.dummyRates[3052] = 15
        operations.talkActionMap["/save"] = Any()
        operations.eventCallbackMap["onStartup"] = LuaValue.valueOf("loader")
        operations.secretAchievementList += mapOf("id" to 1, "name" to "a", "description" to "b", "points" to 3, "grade" to 2, "secret" to true)
        operations.publicAchievementList += mapOf("id" to 2, "name" to "c", "description" to "d", "points" to 4, "grade" to 5, "secret" to false)
        operations.achievementList += mapOf("id" to 3, "name" to "e", "description" to "f", "points" to 6, "grade" to 7, "secret" to false)
        operations.achievementById = mapOf("id" to 9, "name" to "Hero", "description" to "First", "points" to 10, "grade" to 1, "secret" to true)
        operations.achievementByName = mapOf("id" to 9, "name" to "Hero", "description" to "First", "points" to 10, "grade" to 1, "secret" to true)
        operations.soulCoreItemList += Any()
        operations.monstersByRaceList += Any()
        operations.monstersByStarsList += Any()
        operations.boosted = "Demon"
        operations.boostedBossName = "Ferumbras"
        operations.guild = "Wolves"
        GameFunctions.install(environment, operations)

        environment.loadChunk(
            "monsterTypeCatalog = Game.getMonsterTypes() " +
                "byName = Game.getMonsterTypeByName('Rat') " +
                "townList = Game.getTowns() " +
                "houseList = Game.getHouses() " +
                "full = Game.getBestiaryList() " +
                "named = Game.getBestiaryList(false, true) " +
                "byRace = Game.getBestiaryList(false, 5, true) " +
                "raceName = Game.getBestiaryList(false, 'Demon', true) " +
                "ladderIdList = Game.getLadderIds() " +
                "dummyRates = Game.getDummies() " +
                "talk = Game.getTalkActions() " +
                "callbacks = Game.getEventCallbacks() " +
                "secretAchievementList = Game.getSecretAchievements() " +
                "publicAchievementList = Game.getPublicAchievements() " +
                "achievementList = Game.getAchievements() " +
                "byId = Game.getAchievementInfoById(9) " +
                "byAchievementName = Game.getAchievementInfoByName('Hero') " +
                "soulCoreItemList = Game.getSoulCoreItems() " +
                "race = Game.getMonstersByRace(3) " +
                "stars = Game.getMonstersByBestiaryStars(4) " +
                "boosted = Game.getBoostedCreature() " +
                "boss = Game.getBoostedBoss() " +
                "guild = Game.getNormalizedGuildName('wolves')",
        )
        environment.loadChunk(
            "typesOk = (monsterTypeCatalog.Rat ~= nil) and (byName ~= nil) and " +
                "(getmetatable(byName) == rawgetmetatable('MonsterType').__metatable) " +
                "bestiaryOk = (full[1] == 1) and (named[1] == 'Rat') " +
                "collectionsOk = (#townList == 1) and (#houseList == 1) and (ladderIdList[1] == 3031) and (dummyRates[3052] == 15) " +
                "townOk = getmetatable(townList[1]) == rawgetmetatable('Town').__metatable " +
                "talkOk = (talk['/save'] ~= nil) and (callbacks.onStartup ~= nil) " +
                "achievementsOk = (#secretAchievementList == 1) and " +
                "(achievementList[1].id == 3 and achievementList[1].name == 'e' and achievementList[1].points == 6) and " +
                "(byId.id == 9 and byId.name == 'Hero' and byId.secret == true) and (byAchievementName.id == 9) " +
                "itemsOk = (soulCoreItemList[1] ~= nil) and (race[1] ~= nil) and (stars[1] ~= nil) " +
                "stringsOk = (boosted == 'Demon') and (boss == 'Ferumbras') and (guild == 'Wolves')",
        )

        assertThat(environment.getGlobal("typesOk").toboolean()).isTrue()
        assertThat(environment.getGlobal("bestiaryOk").toboolean()).isTrue()
        assertThat(environment.getGlobal("collectionsOk").toboolean()).isTrue()
        assertThat(environment.getGlobal("townOk").toboolean()).isTrue()
        assertThat(environment.getGlobal("talkOk").toboolean()).isTrue()
        assertThat(environment.getGlobal("achievementsOk").toboolean()).isTrue()
        assertThat(environment.getGlobal("itemsOk").toboolean()).isTrue()
        assertThat(environment.getGlobal("stringsOk").toboolean()).isTrue()
        assertThat(operations.monsterTypeLookupName).isEqualTo("Rat")
        assertThat(operations.raceType).isEqualTo(5)
        assertThat(operations.raceName).isEqualTo("Demon")
        assertThat(operations.raceArg).isEqualTo(3)
        assertThat(operations.starsArg).isEqualTo(4)
        assertThat(operations.guildName).isEqualTo("wolves")
    }

    @Test
    fun `Game state effect raid reload and forge methods forward to the seam`() {
        val environment = env()
        val operations = FakeGameOps()
        operations.raidResult = 70
        operations.raidFound = true
        operations.reloadResult = true
        operations.hasEffectResult = true
        operations.hasDistanceEffectResult = true
        operations.removeInfluencedResult = 1
        operations.makeFiendishResult = 55L
        operations.removeFiendishResult = 1
        operations.influencedIds += 11L
        operations.fiendishIds += 22L
        operations.influencedResult = true
        operations.achievementRegistrationResult = true
        GameFunctions.install(environment, operations)
        val monster = Monster(id = 7, name = "Rat", maxHealth = 100, health = 30, position = Position(1, 1, 7))
        environment.globals.set("monster", environment.userdata.wrap("Monster", monster))

        environment.loadChunk(
            "stateSet = Game.setGameState(3) " +
                "state = Game.getGameState() " +
                "worldSet = Game.setWorldType(2) " +
                "world = Game.getWorldType() " +
                "effect = Game.hasEffect(9) " +
                "distance = Game.hasDistanceEffect(10) " +
                "raid = Game.startRaid('Rats') " +
                "reloadCore = Game.reload(11) " +
                "influencedAdd = Game.addInfluencedMonster(monster, 4) " +
                "influencedRemove = Game.removeInfluencedMonster(7, true) " +
                "influencedIds = Game.getInfluencedMonsters() " +
                "influencedFirst = influencedIds[1] " +
                "fiendishAdd = Game.makeFiendishMonster(8, true) " +
                "fiendishRemove = Game.removeFiendishMonster(9, true) " +
                "fiendishIds = Game.getFiendishMonsters() " +
                "fiendishFirst = fiendishIds[1] " +
                "registered = Game.registerAchievement(1, 'First', 'Description', true, 2, 10) " +
                "mapLoad = select('#', Game.loadMap('map.otbm')) " +
                "chunkLoad = select('#', Game.loadMapChunk('map.otbm', { x = 1, y = 2, z = 7 }))",
        )

        assertThat(operations.stateValue).isEqualTo(3)
        assertThat(operations.worldTypeValue).isEqualTo(2)
        assertThat(operations.effectArg).isEqualTo(9)
        assertThat(operations.distanceEffectArg).isEqualTo(10)
        assertThat(operations.raidName).isEqualTo("Rats")
        assertThat(operations.reloadTypes).containsExactly(11)
        assertThat(operations.influencedCall).isEqualTo(monster to 4)
        assertThat(operations.removeInfluencedCall).isEqualTo(7L to true)
        assertThat(operations.makeFiendishCall).isEqualTo(8L to true)
        assertThat(operations.removeFiendishCall).isEqualTo(9L to true)
        assertThat(operations.achievementCall)
            .isEqualTo(AchievementCall(1, "First", "Description", true, 2, 10))
        assertThat(operations.loadMapPath).isEqualTo("map.otbm")
        assertThat(operations.loadMapChunkCall).isEqualTo("map.otbm" to Position(1, 2, 7))
        assertThat(environment.getGlobal("state").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("world").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("effect").toboolean()).isTrue()
        assertThat(environment.getGlobal("distance").toboolean()).isTrue()
        assertThat(environment.getGlobal("raid").toint()).isEqualTo(70)
        assertThat(environment.getGlobal("reloadCore").toboolean()).isTrue()
        assertThat(environment.getGlobal("influencedAdd").toboolean()).isTrue()
        assertThat(environment.getGlobal("influencedRemove").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("influencedFirst").toint()).isEqualTo(11)
        assertThat(environment.getGlobal("fiendishAdd").toint()).isEqualTo(55)
        assertThat(environment.getGlobal("fiendishRemove").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("fiendishFirst").toint()).isEqualTo(22)
        assertThat(environment.getGlobal("registered").toboolean()).isTrue()
        assertThat(environment.getGlobal("mapLoad").toint()).isEqualTo(0)
        assertThat(environment.getGlobal("chunkLoad").toint()).isEqualTo(0)
    }

    @Test
    fun `Game real domain wiring reflects the Kotlin catalogs and map`() {
        val registry = ItemTypeRegistry().apply {
            register(ItemType(1987, "backpack", isContainer = true, capacity = 20))
            register(ItemType(2160, "crystal coin", isStackable = true))
            register(ItemType(3031, "ladder", type = ItemTypes_t.ITEM_TYPE_LADDER))
            register(ItemType(3052, "exercise dummy", type = ItemTypes_t.ITEM_TYPE_DUMMY))
            register(ItemType(2000, "soul core", primaryType = "SoulCores"))
            addLadderId(3031)
            addDummyId(3052, 15)
        }
        val classifications = ItemClassifications()
        val map = GameMap()
        map.getOrCreateTile(Position(100, 100, 7))
        val environment = LuaEnvironment().apply { initState() }.also {
            WorldLuaOperations(
                itemTypes = registry,
                monsterTypesByName = mapOf("Rat" to Any()),
                gameMap = map,
                itemClassifications = classifications,
            ).install(it)
        }

        environment.loadChunk(
            "container = Game.createContainer(1987, 5) " +
                "capacity = container:getMaxCapacity() " +
                "unknown = Game.createContainer(9999, 5) " +
                "placed = Game.createContainer(1987, 5, { x = 100, y = 100, z = 7 }) " +
                "missingTile = Game.createContainer(1987, 5, { x = 1, y = 1, z = 1 }) " +
                "tile = Game.createTile({ x = 100, y = 100, z = 7 }) " +
                "tileXyz = Game.createTile(100, 100, 7, true) " +
                "monsterType = Game.getMonsterTypeByName('Rat') " +
                "missingType = Game.getMonsterTypeByName('Demon') " +
                "types = Game.getMonsterTypes() " +
                "ladderIdList = Game.getLadderIds() " +
                "dummyRates = Game.getDummies() " +
                "soulCoreItemList = Game.getSoulCoreItems() " +
                "soulCoreId = soulCoreItemList[1]:getId() " +
                "classification = Game.createItemClassification(7) " +
                "stateSet = Game.setGameState(2) " +
                "state = Game.getGameState() " +
                "worldSet = Game.setWorldType(1) " +
                "world = Game.getWorldType() " +
                "stackSplit = Game.createItem(2160, 250) " +
                "stackCount = #stackSplit " +
                "plainCopies = Game.createItem(1987, 3) " +
                "plainCount = #plainCopies " +
                "checks = (types.Rat ~= nil) and (ladderIdList[1] == 3031) and (dummyRates[3052] == 15) and " +
                "(stackSplit[1]:getCount() == 100) and (stackSplit[3]:getCount() == 50)",
        )

        assertThat(environment.getGlobal("checks").toboolean()).isTrue()
        assertThat(environment.getGlobal("capacity").toint()).isEqualTo(5)
        assertThat(environment.getGlobal("unknown").isnil()).isTrue()
        assertThat(environment.getGlobal("placed").isuserdata()).isTrue()
        assertThat(environment.getGlobal("missingTile").isnil()).isTrue()
        assertThat(environment.getGlobal("tile").isuserdata()).isTrue()
        assertThat(environment.getGlobal("tileXyz").isuserdata()).isTrue()
        assertThat(environment.getGlobal("monsterType").isuserdata()).isTrue()
        assertThat(environment.getGlobal("missingType").toboolean()).isFalse()
        assertThat(environment.getGlobal("soulCoreId").toint()).isEqualTo(2000)
        assertThat(environment.getGlobal("classification").isuserdata()).isTrue()
        assertThat(environment.getGlobal("state").toint()).isEqualTo(2)
        assertThat(environment.getGlobal("world").toint()).isEqualTo(1)
        assertThat(environment.getGlobal("stackCount").toint()).isEqualTo(3)
        assertThat(environment.getGlobal("plainCount").toint()).isEqualTo(3)
    }

    private data class SoulPitCall(
        val name: String,
        val position: Position,
        val stack: Int,
        val extended: Boolean,
        val force: Boolean,
        val master: Any?,
    )

    private data class AchievementCall(
        val id: Int,
        val name: String,
        val description: String,
        val secret: Boolean,
        val grade: Int,
        val points: Int,
    )

    private class FakeGameOps : GameOperations {
        var npcTypeName: String? = null
        var npcType: Any? = null
        var monsterTypeCall: Triple<String, String, String>? = null
        var monsterType: Any? = null
        var monsterTypeLookupName: String? = null
        var monsterTypeLookup: Any? = null
        var boosted = ""
        var boostedBossName = ""
        val monsterTypeCatalog = mutableMapOf<String, Any>()
        val bestiaryEntries = mutableMapOf<Int, String>()
        var raceType = -1
        var raceName: String? = null
        var raceArg = -1
        var starsArg = -1
        val townList = mutableListOf<Any>()
        val houseList = mutableListOf<Any>()
        var loadMapPath: String? = null
        var loadMapChunkCall: Pair<String, Position>? = null
        var stateValue = 0
        var worldTypeValue = 0
        var containerCall: Triple<Int, Int, Position?>? = null
        var container: Any? = null
        var soulPitCall: SoulPitCall? = null
        var soulPit: Any? = null
        var generatedNpcName: String? = null
        var generatedNpc: Any? = null
        val tileCalls = mutableListOf<Pair<Position, Boolean>>()
        var tile: Any? = null
        val charmIds = mutableListOf<Int>()
        var charm: Any? = null
        var classificationId: Int? = null
        var classification: Any? = null
        val charmList = mutableListOf<Any>()
        var raidName: String? = null
        var raidResult = 0
        var raidFound = false
        val reloadTypes = mutableListOf<Int>()
        var reloadResult = false
        var effectArg: Int? = null
        var hasEffectResult = false
        var distanceEffectArg: Int? = null
        var hasDistanceEffectResult = false
        var guildName: String? = null
        var guild: String? = null
        var influencedCall: Pair<Any, Int>? = null
        var influencedResult = false
        var removeInfluencedCall: Pair<Long, Boolean>? = null
        var removeInfluencedResult = 0
        val influencedIds = mutableListOf<Long>()
        var makeFiendishCall: Pair<Long, Boolean>? = null
        var makeFiendishResult = 0L
        var removeFiendishCall: Pair<Long, Boolean>? = null
        var removeFiendishResult = 0
        val fiendishIds = mutableListOf<Long>()
        val ladderIdList = mutableListOf<Int>()
        val dummyRates = mutableMapOf<Int, Int>()
        val talkActionMap = mutableMapOf<String, Any>()
        val eventCallbackMap = mutableMapOf<String, LuaValue>()
        var achievementCall: AchievementCall? = null
        var achievementRegistrationResult = false
        var achievementById: Map<String, Any>? = null
        var achievementByName: Map<String, Any>? = null
        val secretAchievementList = mutableListOf<Map<String, Any>>()
        val publicAchievementList = mutableListOf<Map<String, Any>>()
        val achievementList = mutableListOf<Map<String, Any>>()
        val soulCoreItemList = mutableListOf<Any>()
        val monstersByRaceList = mutableListOf<Any>()
        val monstersByStarsList = mutableListOf<Any>()

        override fun itemIdByName(name: String): Int = 0

        override fun createItem(itemId: Int, count: Int): List<Any> = emptyList()

        override fun getGameState(): Int = stateValue

        override fun setGameState(state: Int): Boolean {
            stateValue = state
            return true
        }

        override fun getWorldType(): Int = worldTypeValue

        override fun setWorldType(type: Int): Boolean {
            worldTypeValue = type
            return true
        }

        override fun createNpcType(name: String): Any? {
            npcTypeName = name
            return npcType
        }

        override fun createMonsterType(name: String, variant: String, alternateName: String): Any? {
            monsterTypeCall = Triple(name, variant, alternateName)
            return monsterType
        }

        override fun getMonsterTypeByName(name: String): Any? {
            monsterTypeLookupName = name
            return monsterTypeLookup
        }

        override fun getBoostedCreature(): String = boosted

        override fun getMonsterTypes(): Map<String, Any> = monsterTypeCatalog

        override fun getBestiaryList(): Map<Int, String> = bestiaryEntries

        override fun findBestiaryRaceByType(race: Int): Map<Int, String> {
            raceType = race
            return bestiaryEntries
        }

        override fun findBestiaryRaceByName(race: String): Map<Int, String> {
            raceName = race
            return bestiaryEntries
        }

        override fun getTowns(): List<Any> = townList

        override fun getHouses(): List<Any> = houseList

        override fun loadMap(path: String) {
            loadMapPath = path
        }

        override fun loadMapChunk(path: String, position: Position) {
            loadMapChunkCall = path to position
        }

        override fun createContainer(itemId: Int, size: Int, position: Position?): Any? {
            containerCall = Triple(itemId, size, position)
            return container
        }

        override fun createSoulPitMonster(
            name: String,
            position: Position,
            stack: Int,
            extended: Boolean,
            force: Boolean,
            master: Any?,
        ): Any? {
            soulPitCall = SoulPitCall(name, position, stack, extended, force, master)
            return soulPit
        }

        override fun generateNpc(name: String): Any? {
            generatedNpcName = name
            return generatedNpc
        }

        override fun createTile(position: Position, isDynamic: Boolean): Any? {
            tileCalls += position to isDynamic
            return tile
        }

        override fun createBestiaryCharm(id: Int): Any? {
            charmIds += id
            return charm
        }

        override fun createItemClassification(id: Int): Any? {
            classificationId = id
            return classification
        }

        override fun getBestiaryCharm(): List<Any> = charmList

        override fun startRaid(name: String): Int {
            raidName = name
            return if (raidFound) raidResult else ReturnValue.NOSUCHRAIDEXISTS.ordinal
        }

        override fun reload(reloadType: Int): Boolean {
            reloadTypes += reloadType
            return reloadResult
        }

        override fun hasEffect(effectId: Int): Boolean {
            effectArg = effectId
            return hasEffectResult
        }

        override fun hasDistanceEffect(effectId: Int): Boolean {
            distanceEffectArg = effectId
            return hasDistanceEffectResult
        }

        override fun getNormalizedGuildName(name: String): String? {
            guildName = name
            return guild
        }

        override fun addInfluencedMonster(monster: Any, stack: Int): Boolean {
            influencedCall = monster to stack
            return influencedResult
        }

        override fun removeInfluencedMonster(monsterId: Long, create: Boolean): Int {
            removeInfluencedCall = monsterId to create
            return removeInfluencedResult
        }

        override fun getInfluencedMonsters(): List<Long> = influencedIds

        override fun makeFiendishMonster(monsterId: Long, createForgeableMonsters: Boolean): Long {
            makeFiendishCall = monsterId to createForgeableMonsters
            return makeFiendishResult
        }

        override fun removeFiendishMonster(monsterId: Long, create: Boolean): Int {
            removeFiendishCall = monsterId to create
            return removeFiendishResult
        }

        override fun getFiendishMonsters(): List<Long> = fiendishIds

        override fun getBoostedBoss(): String = boostedBossName

        override fun getLadderIds(): List<Int> = ladderIdList

        override fun getDummies(): Map<Int, Int> = dummyRates

        override fun getTalkActions(): Map<String, Any> = talkActionMap

        override fun getEventCallbacks(): Map<String, LuaValue> = eventCallbackMap

        override fun registerAchievement(
            id: Int,
            name: String,
            description: String,
            secret: Boolean,
            grade: Int,
            points: Int,
        ): Boolean {
            achievementCall = AchievementCall(id, name, description, secret, grade, points)
            return achievementRegistrationResult
        }

        override fun getAchievementInfoById(id: Int): Map<String, Any>? = achievementById

        override fun getAchievementInfoByName(name: String): Map<String, Any>? = achievementByName

        override fun getSecretAchievements(): List<Map<String, Any>> = secretAchievementList

        override fun getPublicAchievements(): List<Map<String, Any>> = publicAchievementList

        override fun getAchievements(): List<Map<String, Any>> = achievementList

        override fun getSoulCoreItems(): List<Any> = soulCoreItemList

        override fun getMonstersByRace(race: Int): List<Any> {
            raceArg = race
            return monstersByRaceList
        }

        override fun getMonstersByBestiaryStars(stars: Int): List<Any> {
            starsArg = stars
            return monstersByStarsList
        }
    }

    private companion object {
        val CANARY_GAME_METHODS = listOf(
            "createNpcType",
            "createMonsterType",
            "getMonsterTypeByName",
            "getSpectators",
            "getBoostedCreature",
            "getBestiaryList",
            "getPlayers",
            "loadMap",
            "loadMapChunk",
            "getExperienceForLevel",
            "getMonsterCount",
            "getPlayerCount",
            "getNpcCount",
            "getMonsterTypes",
            "getTowns",
            "getHouses",
            "getGameState",
            "setGameState",
            "getWorldType",
            "setWorldType",
            "getReturnMessage",
            "createItem",
            "createContainer",
            "createMonster",
            "createSoulPitMonster",
            "createNpc",
            "generateNpc",
            "createTile",
            "createBestiaryCharm",
            "createItemClassification",
            "getBestiaryCharm",
            "startRaid",
            "getClientVersion",
            "reload",
            "hasDistanceEffect",
            "hasEffect",
            "getOfflinePlayer",
            "getNormalizedPlayerName",
            "getNormalizedGuildName",
            "addInfluencedMonster",
            "removeInfluencedMonster",
            "getInfluencedMonsters",
            "makeFiendishMonster",
            "removeFiendishMonster",
            "getFiendishMonsters",
            "getBoostedBoss",
            "getLadderIds",
            "getDummies",
            "getTalkActions",
            "getEventCallbacks",
            "registerAchievement",
            "getAchievementInfoById",
            "getAchievementInfoByName",
            "getSecretAchievements",
            "getPublicAchievements",
            "getAchievements",
            "getSoulCoreItems",
            "getMonstersByRace",
            "getMonstersByBestiaryStars",
        )
    }
}
