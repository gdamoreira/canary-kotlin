package org.opentibia.canary.lua.bindings.core

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.game.reload.Reload_t
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.CLIENT_VERSION
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Engine seam for the global `Game` table. Mirrors the primitives used by
 * `GameFunctions::luaGame*` in src/lua/functions/core/game/game_functions.cpp and, for
 * the timer/time globals, `GlobalFunctions::luaAddEvent`/`luaStopEvent`/`luaGetWorldTime`
 * in src/lua/functions/core/game/global_functions.cpp.
 *
 * Members whose state the Kotlin domain does not model yet carry a documented default
 * (empty list, empty map, 0, false, empty string or nil) so scripts keep running instead
 * of failing. The Canary empty-registry return codes are preserved where the C++ code
 * has one (`RETURNVALUE_NOSUCHRAIDEXISTS` for `startRaid`, `0` for the forge-monster
 * lookups).
 */
interface GameOperations {
    /** Resolves an item name to its id, returning 0 when unknown. */
    fun itemIdByName(name: String): Int

    /** Creates [count] of the item, returning the created instances (empty on failure). */
    fun createItem(itemId: Int, count: Int): List<Any>

    /** Mirrors `Game.getSpectators`; empty when no world spectator index is wired. */
    fun getSpectators(
        position: Position,
        multifloor: Boolean,
        onlyPlayers: Boolean,
        minRangeX: Int,
        maxRangeX: Int,
        minRangeY: Int,
        maxRangeY: Int,
    ): List<Any> = emptyList()

    /** Mirrors `Game.getPlayers`; empty when no online-player index is wired. */
    fun getPlayers(): List<Any> = emptyList()

    /** Mirrors `Game.getGameState` (Canary's default `GAME_STATE_STARTUP` is 0). */
    fun getGameState(): Int = 0

    /** Mirrors `Game.getMonsterCount`; 0 when no monster index is wired. */
    fun getMonsterCount(): Int = 0

    /** Mirrors `Game.getPlayerCount`; 0 when no player index is wired. */
    fun getPlayerCount(): Int = 0

    /** Mirrors `Game.getNpcCount`; 0 when no npc index is wired. */
    fun getNpcCount(): Int = 0

    /** Mirrors `Game.getExperienceForLevel`; 0 when the level formula is not wired. */
    fun getExperienceForLevel(level: Int): Long = 0L

    /** Mirrors `Game.createMonster`; null when the monster catalog/placing is not wired. */
    fun createMonster(name: String, position: Position, extended: Boolean, force: Boolean, master: Any?): Any? = null

    /** Mirrors `Game.createNpc`; null when the npc catalog/placing is not wired. */
    fun createNpc(name: String, position: Position, extended: Boolean, force: Boolean): Any? = null

    /** Mirrors `Game.getOfflinePlayer(name or id)`; null when no persistence lookup is wired. */
    fun getOfflinePlayer(nameOrId: Any): Any? = null

    /** Mirrors `Game.getNormalizedPlayerName`; null when no name service is wired. */
    fun getNormalizedPlayerName(name: String, isNewName: Boolean): String? = null

    /**
     * Mirrors the global `getWorldTime`; 0 when no world-light clock is wired.
     */
    fun getWorldTime(): Int = 0

    /**
     * Mirrors the global `addEvent(callback, delay, ...)`: returns the scheduled event id,
     * or 0 when scheduling is unavailable (the binding then returns `false`).
     */
    fun addEvent(delay: Int, callback: LuaValue, parameters: List<LuaValue>): Int = 0

    /** Mirrors the global `stopEvent(eventid)`; false when the event is unknown or unsupported. */
    fun stopEvent(eventId: Int): Boolean = false

    /** Mirrors `Game.createNpcType(name)`; null when the npc-type catalog is not wired. */
    fun createNpcType(name: String): Any? = null

    /**
     * Mirrors `Game.createMonsterType(name[, variant[, alternateName]])`; null when the
     * monster-type registry is not wired.
     */
    fun createMonsterType(name: String, variant: String, alternateName: String): Any? = null

    /** Mirrors `Game.getMonsterTypeByName(name)`; null when the monster-type catalog is not wired. */
    fun getMonsterTypeByName(name: String): Any? = null

    /** Mirrors `Game.getBoostedCreature()`; empty when no boosted-monster rotation is wired. */
    fun getBoostedCreature(): String = ""

    /** Mirrors `Game.getMonsterTypes()`; empty when no monster-type catalog is wired. */
    fun getMonsterTypes(): Map<String, Any> = emptyMap()

    /** Mirrors `Game.getBestiaryList()`: the bestiary id -> name catalog. */
    fun getBestiaryList(): Map<Int, String> = emptyMap()

    /** Mirrors `g_iobestiary().findRaceByName` for the `BestiaryType_t` overload. */
    fun findBestiaryRaceByType(race: Int): Map<Int, String> = emptyMap()

    /** Mirrors `g_iobestiary().findRaceByName` for the string overload. */
    fun findBestiaryRaceByName(race: String): Map<Int, String> = emptyMap()

    /** Mirrors `Game.getTowns()`; empty when no town registry is wired. */
    fun getTowns(): List<Any> = emptyList()

    /** Mirrors `Game.getHouses()`; empty when no house registry is wired. */
    fun getHouses(): List<Any> = emptyList()

    /** Mirrors `Game.loadMap(path)`; a no-op when no map loader is wired. */
    fun loadMap(path: String) {}

    /** Mirrors `Game.loadMapChunk(path, position)`; a no-op when no map loader is wired. */
    fun loadMapChunk(path: String, position: Position) {}

    /** Mirrors `Game.setGameState(state)`; false when no game-state holder is wired. */
    fun setGameState(state: Int): Boolean = false

    /** Mirrors `Game.getWorldType()` (Canary's `WORLD_TYPE_NO_PVP` default is 0). */
    fun getWorldType(): Int = 0

    /** Mirrors `Game.setWorldType(type)`; false when no world-type holder is wired. */
    fun setWorldType(type: Int): Boolean = false

    /**
     * Mirrors `Game.createContainer(itemId, size[, position])`. [position] is null when
     * the call omits it; the returned object is wrapped as `Container`.
     */
    fun createContainer(itemId: Int, size: Int, position: Position?): Any? = null

    /**
     * Mirrors `Game.createSoulPitMonster(name, position[, stack[, extended[, force[, master]]]])`;
     * null when the monster catalog/placing is not wired.
     */
    fun createSoulPitMonster(
        name: String,
        position: Position,
        stack: Int,
        extended: Boolean,
        force: Boolean,
        master: Any?,
    ): Any? = null

    /** Mirrors `Game.generateNpc(npcName)`; null when the npc catalog is not wired. */
    fun generateNpc(name: String): Any? = null

    /** Mirrors `Game.createTile(position[, isDynamic])`; null when no tile factory is wired. */
    fun createTile(position: Position, isDynamic: Boolean): Any? = null

    /** Mirrors `Game.createBestiaryCharm(id)`; null when no charm registry is wired. */
    fun createBestiaryCharm(id: Int): Any? = null

    /** Mirrors `Game.createItemClassification(id)`; null when no classification catalog is wired. */
    fun createItemClassification(id: Int): Any? = null

    /** Mirrors `Game.getBestiaryCharm()`; empty when no charm registry is wired. */
    fun getBestiaryCharm(): List<Any> = emptyList()

    /**
     * Mirrors `Game.startRaid(raidName)`: `RETURNVALUE_NOSUCHRAIDEXISTS` by default, the
     * observable Canary result when no raid is loaded.
     */
    fun startRaid(name: String): Int = ReturnValue.NOSUCHRAIDEXISTS.ordinal

    /** Mirrors `Game.reload(reloadType)`; false when the reload surface rejects the type. */
    fun reload(reloadType: Int): Boolean = false

    /** Mirrors `Game.hasDistanceEffect(effectId)`; false when no distance-effect registry is wired. */
    fun hasDistanceEffect(effectId: Int): Boolean = false

    /** Mirrors `Game.hasEffect(effectId)`; false when no magic-effect registry is wired. */
    fun hasEffect(effectId: Int): Boolean = false

    /** Mirrors `Game.getNormalizedGuildName(name)`; null when no guild registry is wired. */
    fun getNormalizedGuildName(name: String): String? = null

    /** Mirrors `Game.addInfluencedMonster(monster[, stack])`; false when no registry is wired. */
    fun addInfluencedMonster(monster: Any, stack: Int): Boolean = false

    /** Mirrors `Game.removeInfluencedMonster(monsterId[, create])`; Canary's `bool` result as a number. */
    fun removeInfluencedMonster(monsterId: Long, create: Boolean): Int = 0

    /** Mirrors `Game.getInfluencedMonsters()`; empty when no registry is wired. */
    fun getInfluencedMonsters(): List<Long> = emptyList()

    /** Mirrors `Game.makeFiendishMonster([monsterId[, createForgeableMonsters]])`; 0 when none is made. */
    fun makeFiendishMonster(monsterId: Long, createForgeableMonsters: Boolean): Long = 0L

    /** Mirrors `Game.removeFiendishMonster(monsterId[, create])`; Canary's `bool` result as a number. */
    fun removeFiendishMonster(monsterId: Long, create: Boolean): Int = 0

    /** Mirrors `Game.getFiendishMonsters()`; empty when no registry is wired. */
    fun getFiendishMonsters(): List<Long> = emptyList()

    /** Mirrors `Game.getBoostedBoss()`; empty when no bosstiary rotation is wired. */
    fun getBoostedBoss(): String = ""

    /** Mirrors `Game.getLadderIds()`; empty when no item catalog is wired. */
    fun getLadderIds(): List<Int> = emptyList()

    /** Mirrors `Game.getDummies()`: dummy item id -> rate; empty when no item catalog is wired. */
    fun getDummies(): Map<Int, Int> = emptyMap()

    /** Mirrors `Game.getTalkActions()`; empty when no talkaction registry is wired. */
    fun getTalkActions(): Map<String, Any> = emptyMap()

    /** Mirrors `Game.getEventCallbacks()`; empty when the event-callback loader is not wired. */
    fun getEventCallbacks(): Map<String, LuaValue> = emptyMap()

    /** Mirrors `Game.registerAchievement(...)`; false when no achievement registry is wired. */
    fun registerAchievement(
        id: Int,
        name: String,
        description: String,
        secret: Boolean,
        grade: Int,
        points: Int,
    ): Boolean = false

    /** Mirrors `Game.getAchievementInfoById(id)`; null when no achievement registry is wired. */
    fun getAchievementInfoById(id: Int): Map<String, Any>? = null

    /** Mirrors `Game.getAchievementInfoByName(name)`; null when no achievement registry is wired. */
    fun getAchievementInfoByName(name: String): Map<String, Any>? = null

    /** Mirrors `Game.getSecretAchievements()`; empty when no achievement registry is wired. */
    fun getSecretAchievements(): List<Map<String, Any>> = emptyList()

    /** Mirrors `Game.getPublicAchievements()`; empty when no achievement registry is wired. */
    fun getPublicAchievements(): List<Map<String, Any>> = emptyList()

    /** Mirrors `Game.getAchievements()`; empty when no achievement registry is wired. */
    fun getAchievements(): List<Map<String, Any>> = emptyList()

    /** Mirrors `Game.getSoulCoreItems()`; empty when no item catalog is wired. */
    fun getSoulCoreItems(): List<Any> = emptyList()

    /** Mirrors `Game.getMonstersByRace(race)`; empty when no monster catalog is wired. */
    fun getMonstersByRace(race: Int): List<Any> = emptyList()

    /** Mirrors `Game.getMonstersByBestiaryStars(stars)`; empty when no monster catalog is wired. */
    fun getMonstersByBestiaryStars(stars: Int): List<Any> = emptyList()
}

/**
 * Registers the Canary global `Game` table (Lua::registerTable(L, "Game")) with every
 * method registered by `GameFunctions::init` in
 * src/lua/functions/core/game/game_functions.cpp:
 *
 *   Game.createNpcType(name)
 *   Game.createMonsterType(name[, variant = ""[, alternateName = ""]])
 *   Game.getMonsterTypeByName(name)
 *   Game.getSpectators(position[, multifloor = false[, onlyPlayers = false[, minRangeX = 0[, maxRangeX = 0[, minRangeY = 0[, maxRangeY = 0]]]]]])
 *   Game.getBoostedCreature()
 *   Game.getBestiaryList([names[, race[, names]]])
 *   Game.getPlayers()
 *   Game.loadMap(path)
 *   Game.loadMapChunk(path, position)
 *   Game.getExperienceForLevel(level)
 *   Game.getMonsterCount()
 *   Game.getPlayerCount()
 *   Game.getNpcCount()
 *   Game.getMonsterTypes()
 *   Game.getTowns()
 *   Game.getHouses()
 *   Game.getGameState()
 *   Game.setGameState(state)
 *   Game.getWorldType()
 *   Game.setWorldType(type)
 *   Game.getReturnMessage(value)
 *   Game.createItem(itemId or name[, count[, position]])
 *   Game.createContainer(itemId or name, size[, position])
 *   Game.createMonster(monsterName, position[, extended = false[, force = false[, master = nil]]])
 *   Game.createSoulPitMonster(monsterName, position[, stack = 1[, extended = false[, force = false[, master = nil]]]])
 *   Game.createNpc(npcName, position[, extended = false[, force = false]]])
 *   Game.generateNpc(npcName)
 *   Game.createTile(position or x, y, z[, isDynamic = false])
 *   Game.createBestiaryCharm(id)
 *   Game.createItemClassification(id)
 *   Game.getBestiaryCharm()
 *   Game.startRaid(raidName)
 *   Game.getClientVersion()
 *   Game.reload(reloadType)
 *   Game.hasDistanceEffect(effectId)
 *   Game.hasEffect(effectId)
 *   Game.getOfflinePlayer(name or id)
 *   Game.getNormalizedPlayerName(name[, isNewName = false])
 *   Game.getNormalizedGuildName(name)
 *   Game.addInfluencedMonster(monster[, stack])
 *   Game.removeInfluencedMonster(monsterId[, create = false])
 *   Game.getInfluencedMonsters()
 *   Game.makeFiendishMonster([monsterId = 0[, createForgeableMonsters = false]])
 *   Game.removeFiendishMonster(monsterId[, create = false])
 *   Game.getFiendishMonsters()
 *   Game.getBoostedBoss()
 *   Game.getLadderIds()
 *   Game.getDummies()
 *   Game.getTalkActions()
 *   Game.getEventCallbacks()
 *   Game.registerAchievement(id, name, description, secret, grade, points)
 *   Game.getAchievementInfoById(id)
 *   Game.getAchievementInfoByName(name)
 *   Game.getSecretAchievements()
 *   Game.getPublicAchievements()
 *   Game.getAchievements()
 *   Game.getSoulCoreItems()
 *   Game.getMonstersByRace(race)
 *   Game.getMonstersByBestiaryStars(stars)
 *
 * Canary registers the timer/time helpers as globals (`GlobalFunctions::init`), not as
 * `Game` methods, so they are kept as globals here too, with their exact names:
 *
 *   getWorldTime()
 *   addEvent(callback, delay, ...)
 *   stopEvent(eventid)
 *
 * `Game.getClientVersion` and `Game.getReturnMessage` are pure engine constants
 * (`core.hpp`, `utils/tools.cpp:1350`) and are served directly by this binding.
 *
 * Only the engine primitives are bound here; the world work is delegated to
 * [GameOperations].
 */
object GameFunctions {
    const val GAME_GLOBAL = "Game"

    fun install(env: LuaEnvironment, operations: GameOperations = UnwiredGameOperations) {
        val game = (env.globals.get(GAME_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(GAME_GLOBAL, it) }

        game.set("createNpcType", luaFunction { args ->
            val name = luaString(args.arg(1))
            operations.createNpcType(name)?.let { env.userdata.wrap("NpcType", it) } ?: LuaValue.NIL
        })

        game.set("createMonsterType", luaFunction { args ->
            val nameValue = args.arg(1)
            if (!isStringLike(nameValue)) {
                return@luaFunction LuaValue.NIL
            }
            val monsterType = operations.createMonsterType(
                name = nameValue.tojstring(),
                variant = args.arg(2).optjstring(""),
                alternateName = args.arg(3).optjstring(""),
            ) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("MonsterType", monsterType)
        })

        game.set("getMonsterTypeByName", luaFunction { args ->
            val nameValue = args.arg(1)
            if (!isStringLike(nameValue)) {
                return@luaFunction LuaValue.FALSE
            }
            val monsterType = operations.getMonsterTypeByName(nameValue.tojstring())
                ?: return@luaFunction LuaValue.FALSE
            env.userdata.wrap("MonsterType", monsterType)
        })

        game.set("getSpectators", luaFunction { args ->
            val position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaTable()
            val spectators = operations.getSpectators(
                position = position,
                multifloor = args.arg(2).optboolean(false),
                onlyPlayers = args.arg(3).optboolean(false),
                minRangeX = args.arg(4).optint(0),
                maxRangeX = args.arg(5).optint(0),
                minRangeY = args.arg(6).optint(0),
                maxRangeY = args.arg(7).optint(0),
            )
            creatureTable(env, spectators)
        })

        game.set("getBoostedCreature", luaFunction { LuaValue.valueOf(operations.getBoostedCreature()) })

        game.set("getBestiaryList", luaFunction { args ->
            val names = luaBoolean(args.arg(2), false)
            val list = when {
                args.narg() <= 2 -> operations.getBestiaryList()
                args.arg(2).isnumber() -> operations.findBestiaryRaceByType(args.arg(2).toint())
                else -> operations.findBestiaryRaceByName(args.arg(2).tojstring())
            }
            LuaTable().also { table ->
                list.entries.sortedBy { it.key }.forEachIndexed { index, (id, name) ->
                    table.set(index + 1, if (names) LuaValue.valueOf(name) else LuaValue.valueOf(id))
                }
            }
        })

        game.set("getPlayers", luaFunction { args ->
            val players = operations.getPlayers()
            LuaTable().also { table ->
                players.forEachIndexed { index, player -> table.set(index + 1, env.userdata.wrap("Player", player)) }
            }
        })

        game.set("loadMap", luaFunction { args ->
            operations.loadMap(luaString(args.arg(1)))
            LuaValue.NONE
        })

        game.set("loadMapChunk", luaFunction { args ->
            val position = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NONE
            operations.loadMapChunk(luaString(args.arg(1)), position)
            LuaValue.NONE
        })

        game.set("getExperienceForLevel", luaFunction { args ->
            val level = args.arg(1).toint()
            if (level == 0) {
                return@luaFunction LuaValue.NIL
            }
            LuaValue.valueOf(operations.getExperienceForLevel(level).toDouble())
        })

        game.set("getMonsterCount", luaFunction { LuaValue.valueOf(operations.getMonsterCount()) })

        game.set("getPlayerCount", luaFunction { LuaValue.valueOf(operations.getPlayerCount()) })

        game.set("getNpcCount", luaFunction { LuaValue.valueOf(operations.getNpcCount()) })

        game.set("getMonsterTypes", luaFunction { args ->
            LuaTable().also { table ->
                for ((typeName, monsterType) in operations.getMonsterTypes()) {
                    table.set(typeName, env.userdata.wrap("MonsterType", monsterType))
                }
            }
        })

        game.set("getTowns", luaFunction { args ->
            userdataTable(env, "Town", operations.getTowns())
        })

        game.set("getHouses", luaFunction { args ->
            userdataTable(env, "House", operations.getHouses())
        })

        game.set("getGameState", luaFunction { LuaValue.valueOf(operations.getGameState()) })

        game.set("setGameState", luaFunction { args ->
            LuaValue.valueOf(operations.setGameState(args.arg(1).toint()))
        })

        game.set("getWorldType", luaFunction { LuaValue.valueOf(operations.getWorldType()) })

        game.set("setWorldType", luaFunction { args ->
            LuaValue.valueOf(operations.setWorldType(args.arg(1).toint()))
        })

        game.set("getReturnMessage", luaFunction { args ->
            LuaValue.valueOf(returnMessage(args.arg(1).toint()))
        })

        game.set("createItem", luaFunction { args ->
            val idValue = args.arg(1)
            val itemId = if (idValue.isnumber()) idValue.toint() else operations.itemIdByName(idValue.checkjstring())
            if (itemId == 0) {
                return@luaFunction LuaValue.NIL
            }
            val count = args.arg(2).optint(1)
            wrapItems(env, operations.createItem(itemId, count))
        })

        game.set("createContainer", luaFunction { args ->
            val idValue = args.arg(1)
            val itemId = if (idValue.isnumber()) idValue.toint() else operations.itemIdByName(luaString(idValue))
            if (itemId == 0) {
                return@luaFunction LuaValue.NIL
            }
            val position = if (args.narg() >= 3) {
                luaToPosition(args.arg(3)) ?: return@luaFunction LuaValue.NIL
            } else {
                null
            }
            val container = operations.createContainer(itemId, args.arg(2).optint(0), position)
                ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Container", container)
        })

        game.set("createMonster", luaFunction { args ->
            val name = args.arg(1).checkjstring()
            val position = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            val monster = operations.createMonster(
                name = name,
                position = position,
                extended = args.arg(3).optboolean(false),
                force = args.arg(4).optboolean(false),
                master = retrieveUserdata(args, 5),
            ) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Monster", monster)
        })

        game.set("createSoulPitMonster", luaFunction { args ->
            val position = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            val monster = operations.createSoulPitMonster(
                name = luaString(args.arg(1)),
                position = position,
                stack = args.arg(3).optint(1),
                extended = luaBoolean(args.arg(4), false),
                force = luaBoolean(args.arg(5), false),
                master = retrieveUserdata(args, 6),
            ) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Monster", monster)
        })

        game.set("createNpc", luaFunction { args ->
            val name = args.arg(1).checkjstring()
            val position = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            val npc = operations.createNpc(
                name = name,
                position = position,
                extended = args.arg(3).optboolean(false),
                force = args.arg(4).optboolean(false),
            ) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Npc", npc)
        })

        game.set("generateNpc", luaFunction { args ->
            val npc = operations.generateNpc(luaString(args.arg(1))) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Npc", npc)
        })

        game.set("createTile", luaFunction { args ->
            val position: Position
            val isDynamic: Boolean
            if (args.arg(1).istable()) {
                position = luaToPosition(args.arg(1)) ?: return@luaFunction LuaValue.NIL
                isDynamic = luaBoolean(args.arg(2), false)
            } else {
                position = Position(args.arg(1).optint(0), args.arg(2).optint(0), args.arg(3).optint(0))
                isDynamic = luaBoolean(args.arg(4), false)
            }
            val tile = operations.createTile(position, isDynamic) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Tile", tile)
        })

        game.set("createBestiaryCharm", luaFunction { args ->
            val charm = operations.createBestiaryCharm(args.arg(1).optint(0)) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Charm", charm)
        })

        game.set("createItemClassification", luaFunction { args ->
            val classification = operations.createItemClassification(args.arg(1).optint(0))
                ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("ItemClassification", classification)
        })

        game.set("getBestiaryCharm", luaFunction { args ->
            userdataTable(env, "Charm", operations.getBestiaryCharm())
        })

        game.set("startRaid", luaFunction { args ->
            LuaValue.valueOf(operations.startRaid(luaString(args.arg(1))))
        })

        game.set("getClientVersion", luaFunction {
            LuaTable().also { table ->
                table.set("min", LuaValue.valueOf(CLIENT_VERSION))
                table.set("max", LuaValue.valueOf(CLIENT_VERSION))
                table.set("string", LuaValue.valueOf("${CLIENT_VERSION / 100}.${CLIENT_VERSION % 100}"))
            }
        })

        game.set("reload", luaFunction { args ->
            val reloadType = args.arg(1).toint()
            if (reloadType == Reload_t.RELOAD_TYPE_NONE.value || reloadType >= Reload_t.RELOAD_TYPE_LAST.value) {
                return@luaFunction LuaValue.NONE
            }
            LuaValue.valueOf(operations.reload(reloadType))
        })

        game.set("hasDistanceEffect", luaFunction { args ->
            LuaValue.valueOf(operations.hasDistanceEffect(args.arg(1).toint()))
        })

        game.set("hasEffect", luaFunction { args ->
            LuaValue.valueOf(operations.hasEffect(args.arg(1).toint()))
        })

        game.set("getOfflinePlayer", luaFunction { args ->
            val argument = args.arg(1)
            val nameOrId: Any = when {
                argument.isnumber() -> argument.toint()
                argument.isstring() -> argument.tojstring()
                else -> return@luaFunction LuaValue.NIL
            }
            val player = operations.getOfflinePlayer(nameOrId) ?: return@luaFunction LuaValue.NIL
            env.userdata.wrap("Player", player)
        })

        game.set("getNormalizedPlayerName", luaFunction { args ->
            val name = args.arg(1).checkjstring()
            val normalized = operations.getNormalizedPlayerName(name, args.arg(2).optboolean(false))
            normalized?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        game.set("getNormalizedGuildName", luaFunction { args ->
            val normalized = operations.getNormalizedGuildName(luaString(args.arg(1)))
            normalized?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        game.set("addInfluencedMonster", luaFunction { args ->
            val stack = args.arg(2).optint(0)
            val monster = retrieveUserdata(args, 1)
            if (monster !is Monster) {
                return@luaFunction LuaValue.NONE
            }
            LuaValue.valueOf(operations.addInfluencedMonster(monster, stack))
        })

        game.set("removeInfluencedMonster", luaFunction { args ->
            val monsterId = args.arg(1).optlong(0)
            val create = luaBoolean(args.arg(2), false)
            LuaValue.valueOf(operations.removeInfluencedMonster(monsterId, create))
        })

        game.set("getInfluencedMonsters", luaFunction { args ->
            numberTable(operations.getInfluencedMonsters().map { it.toDouble() })
        })

        game.set("makeFiendishMonster", luaFunction { args ->
            val monsterId = args.arg(1).optlong(0)
            val createForgeableMonsters = luaBoolean(args.arg(2), false)
            LuaValue.valueOf(operations.makeFiendishMonster(monsterId, createForgeableMonsters).toDouble())
        })

        game.set("removeFiendishMonster", luaFunction { args ->
            val monsterId = args.arg(1).optlong(0)
            val create = luaBoolean(args.arg(2), false)
            LuaValue.valueOf(operations.removeFiendishMonster(monsterId, create))
        })

        game.set("getFiendishMonsters", luaFunction { args ->
            numberTable(operations.getFiendishMonsters().map { it.toDouble() })
        })

        game.set("getBoostedBoss", luaFunction { LuaValue.valueOf(operations.getBoostedBoss()) })

        game.set("getLadderIds", luaFunction { args ->
            numberTable(operations.getLadderIds().map { it.toDouble() })
        })

        game.set("getDummies", luaFunction { args ->
            LuaTable().also { table ->
                for ((dummyId, rate) in operations.getDummies()) {
                    table.set(dummyId, LuaValue.valueOf(rate))
                }
            }
        })

        game.set("getTalkActions", luaFunction { args ->
            LuaTable().also { table ->
                for ((talkName, talkAction) in operations.getTalkActions()) {
                    table.set(talkName, env.userdata.wrap("TalkAction", talkAction))
                }
            }
        })

        game.set("getEventCallbacks", luaFunction { args ->
            LuaTable().also { table ->
                for ((callbackName, loader) in operations.getEventCallbacks()) {
                    table.set(callbackName, loader)
                }
            }
        })

        game.set("registerAchievement", luaFunction { args ->
            if (args.narg() < 6) {
                return@luaFunction LuaValue.NIL
            }
            LuaValue.valueOf(
                operations.registerAchievement(
                    id = args.arg(1).toint(),
                    name = luaString(args.arg(2)),
                    description = luaString(args.arg(3)),
                    secret = luaBoolean(args.arg(4), false),
                    grade = args.arg(5).toint(),
                    points = args.arg(6).toint(),
                ),
            )
        })

        game.set("getAchievementInfoById", luaFunction { args ->
            achievementInfo(operations.getAchievementInfoById(args.arg(1).toint()))
        })

        game.set("getAchievementInfoByName", luaFunction { args ->
            achievementInfo(operations.getAchievementInfoByName(luaString(args.arg(1))))
        })

        game.set("getSecretAchievements", luaFunction { args ->
            achievementTableOf(operations.getSecretAchievements())
        })

        game.set("getPublicAchievements", luaFunction { args ->
            achievementTableOf(operations.getPublicAchievements())
        })

        game.set("getAchievements", luaFunction { args ->
            achievementTableOf(operations.getAchievements())
        })

        game.set("getSoulCoreItems", luaFunction { args ->
            userdataTable(env, "ItemType", operations.getSoulCoreItems())
        })

        game.set("getMonstersByRace", luaFunction { args ->
            userdataTable(env, "MonsterType", operations.getMonstersByRace(args.arg(1).toint()))
        })

        game.set("getMonstersByBestiaryStars", luaFunction { args ->
            userdataTable(env, "MonsterType", operations.getMonstersByBestiaryStars(args.arg(1).toint()))
        })

        env.globals.set("getWorldTime", luaFunction { LuaValue.valueOf(operations.getWorldTime()) })

        env.globals.set("addEvent", luaFunction { args ->
            val callback = args.arg(1)
            if (!callback.isfunction()) {
                return@luaFunction LuaValue.FALSE
            }
            val delay = maxOf(100, args.arg(2).optint(0))
            val parameters = (3..args.narg()).map { args.arg(it) }
            val eventId = operations.addEvent(delay, callback, parameters)
            if (eventId == 0) LuaValue.FALSE else LuaValue.valueOf(eventId)
        })

        env.globals.set("stopEvent", luaFunction { args ->
            LuaValue.valueOf(operations.stopEvent(args.arg(1).toint()))
        })
    }

    private fun creatureTable(env: LuaEnvironment, creatures: List<Any>): LuaTable =
        LuaTable().also { table ->
            creatures.forEachIndexed { index, creature ->
                table.set(index + 1, wrapCreatureUserdata(env, creature))
            }
        }

    private fun userdataTable(env: LuaEnvironment, typeName: String, values: List<Any>): LuaTable =
        LuaTable().also { table ->
            values.forEachIndexed { index, value ->
                table.set(index + 1, env.userdata.wrap(typeName, value))
            }
        }

    private fun numberTable(values: List<Double>): LuaTable =
        LuaTable().also { table ->
            values.forEachIndexed { index, value -> table.set(index + 1, LuaValue.valueOf(value)) }
        }

    private fun achievementInfo(achievement: Map<String, Any>?): LuaValue =
        achievement?.let { achievementTable(it) } ?: LuaValue.NIL

    private fun achievementTableOf(achievements: List<Map<String, Any>>): LuaTable =
        LuaTable().also { table ->
            achievements.forEachIndexed { index, achievement ->
                table.set(index + 1, achievementTable(achievement))
            }
        }

    /** Mirrors the six `Lua::setField` entries of `Game.getAchievementInfoById`. */
    private fun achievementTable(achievement: Map<String, Any>): LuaTable =
        LuaTable().also { table ->
            table.set("id", LuaValue.valueOf((achievement["id"] as? Number)?.toInt() ?: 0))
            table.set("name", LuaValue.valueOf(achievement["name"] as? String ?: ""))
            table.set("description", LuaValue.valueOf(achievement["description"] as? String ?: ""))
            table.set("points", LuaValue.valueOf((achievement["points"] as? Number)?.toInt() ?: 0))
            table.set("grade", LuaValue.valueOf((achievement["grade"] as? Number)?.toInt() ?: 0))
            table.set("secret", LuaValue.valueOf(achievement["secret"] as? Boolean ?: false))
        }
}

/**
 * `utils/tools.cpp:1350` `getReturnMessage`: the exact Canary message for every
 * `ReturnValue` the C++ switch handles; unhandled codes (including out-of-range numbers)
 * return `"Unknown error."`.
 */
internal fun returnMessage(value: Int): String = when (ReturnValue.entries.getOrNull(value)) {
    null -> "Unknown error."
    ReturnValue.NOERROR -> "No error."
    ReturnValue.NOTBOUGHTINSTORE ->
        "You cannot move this item into your store inbox as it was not bought in the store."
    ReturnValue.ITEMCANNOTBEMOVEDPOUCH ->
        "This item cannot be moved there. You can only place gold, platinum and crystal coins in your gold pouch."
    ReturnValue.ITEMCANNOTBEMOVEDTHERE -> "This item cannot be moved there."
    ReturnValue.REWARDCHESTISEMPTY ->
        "The chest is currently empty. You did not take part in any battles in the last seven days or already claimed your reward."
    ReturnValue.DESTINATIONOUTOFREACH -> "Destination is out of reach."
    ReturnValue.NOTMOVABLE -> "You cannot move this object."
    ReturnValue.DROPTWOHANDEDITEM -> "Drop the double-handed object first."
    ReturnValue.BOTHHANDSNEEDTOBEFREE -> "Both hands need to be free."
    ReturnValue.CANNOTBEDRESSED -> "You cannot dress this object there."
    ReturnValue.PUTTHISOBJECTINYOURHAND -> "Put this object in your hand."
    ReturnValue.PUTTHISOBJECTINBOTHHANDS -> "Put this object in both hands."
    ReturnValue.CANONLYUSEONEWEAPON -> "You may only use one weapon."
    ReturnValue.TOOFARAWAY -> "Too far away."
    ReturnValue.FIRSTGODOWNSTAIRS -> "First go downstairs."
    ReturnValue.FIRSTGOUPSTAIRS -> "First go upstairs."
    ReturnValue.NOTENOUGHCAPACITY -> "This object is too heavy for you to carry."
    ReturnValue.CONTAINERNOTENOUGHROOM -> "You cannot put more objects in this container."
    ReturnValue.ONLYAMMOINQUIVER -> "This quiver only holds arrows and bolts.\nYou cannot put any other items in it."
    ReturnValue.CREATUREBLOCK, ReturnValue.NEEDEXCHANGE, ReturnValue.NOTENOUGHROOM -> "There is not enough room."
    ReturnValue.CANNOTPICKUP -> "You cannot take this object."
    ReturnValue.CANNOTTHROW -> "You cannot throw there."
    ReturnValue.THEREISNOWAY -> "There is no way."
    ReturnValue.THISISIMPOSSIBLE -> "This is impossible."
    ReturnValue.PLAYERISPZLOCKED -> "You can not enter a protection zone after attacking another player."
    ReturnValue.PLAYERISNOTINVITED -> "You are not invited."
    ReturnValue.CREATUREDOESNOTEXIST -> "Creature does not exist."
    ReturnValue.DEPOTISFULL -> "You cannot put more items in this depot."
    ReturnValue.CONTAINERISFULL -> "You cannot put more items in this container."
    ReturnValue.CANNOTUSETHISOBJECT -> "You cannot use this object."
    ReturnValue.PLAYERWITHTHISNAMEISNOTONLINE -> "A player with this name is not online."
    ReturnValue.NOTREQUIREDLEVELTOUSERUNE -> "You do not have the required magic level to use this rune."
    ReturnValue.YOUAREALREADYTRADING -> "You are already trading. Finish this trade first."
    ReturnValue.THISPLAYERISALREADYTRADING -> "This player is already trading."
    ReturnValue.YOUMAYNOTLOGOUTDURINGAFIGHT -> "You may not logout during or immediately after a fight!"
    ReturnValue.DIRECTPLAYERSHOOT -> "You are not allowed to shoot directly on players."
    ReturnValue.NOTENOUGHLEVEL -> "You do not have enough level."
    ReturnValue.NOTENOUGHMAGICLEVEL -> "You do not have enough magic level."
    ReturnValue.NOTENOUGHMANA -> "You do not have enough mana."
    ReturnValue.NOTENOUGHSOUL -> "You do not have enough soul."
    ReturnValue.YOUAREEXHAUSTED -> "You are exhausted."
    ReturnValue.CANONLYUSETHISRUNEONCREATURES -> "You can only use this rune on creatures."
    ReturnValue.PLAYERISNOTREACHABLE -> "Player is not reachable."
    ReturnValue.CREATUREISNOTREACHABLE -> "Creature is not reachable."
    ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE -> "This action is not permitted in a protection zone."
    ReturnValue.YOUMAYNOTATTACKTHISPLAYER -> "You may not attack this player."
    ReturnValue.YOUMAYNOTATTACKTHISCREATURE -> "You may not attack this creature."
    ReturnValue.YOUMAYNOTATTACKAPERSONINPROTECTIONZONE -> "You may not attack a person in a protection zone."
    ReturnValue.YOUMAYNOTATTACKAPERSONWHILEINPROTECTIONZONE ->
        "You may not attack a person while you are in a protection zone."
    ReturnValue.YOUCANONLYUSEITONCREATURES -> "You can only use it on creatures."
    ReturnValue.TURNSECUREMODETOATTACKUNMARKEDPLAYERS ->
        "Turn secure mode off if you really want to attack unmarked players."
    ReturnValue.YOUNEEDPREMIUMACCOUNT -> "You need a premium account."
    ReturnValue.YOUNEEDTOLEARNTHISSPELL -> "You need to learn this spell first."
    ReturnValue.YOURVOCATIONCANNOTUSETHISSPELL -> "Your vocation cannot use this spell."
    ReturnValue.YOUNEEDAWEAPONTOUSETHISSPELL -> "You need to equip a weapon to use this spell."
    ReturnValue.PLAYERISPZLOCKEDLEAVEPVPZONE -> "You can not leave a pvp zone after attacking another player."
    ReturnValue.PLAYERISPZLOCKEDENTERPVPZONE -> "You can not enter a pvp zone after attacking another player."
    ReturnValue.ACTIONNOTPERMITTEDINANOPVPZONE -> "This action is not permitted in a non pvp zone."
    ReturnValue.YOUCANNOTLOGOUTHERE -> "You can not logout here."
    ReturnValue.YOUNEEDAMAGICITEMTOCASTSPELL -> "You need a magic item to cast this spell."
    ReturnValue.CANNOTCONJUREITEMHERE -> "You cannot conjure items here."
    ReturnValue.YOUNEEDTOSPLITYOURSPEARS -> "You need to split your spears first."
    ReturnValue.NAMEISTOOAMBIGUOUS -> "Player name is ambiguous."
    ReturnValue.CANONLYUSEONESHIELD -> "You may use only one shield."
    ReturnValue.NOPARTYMEMBERSINRANGE -> "No party members in range."
    ReturnValue.YOUARENOTTHEOWNER -> "You are not the owner."
    ReturnValue.YOUCANTOPENCORPSEADM -> "You can't open this corpse, because you are an Admin."
    ReturnValue.NOSUCHRAIDEXISTS -> "No such raid exists."
    ReturnValue.ANOTHERRAIDISALREADYEXECUTING -> "Another raid is already executing."
    ReturnValue.TRADEPLAYERFARAWAY -> "Trade player is too far away."
    ReturnValue.YOUDONTOWNTHISHOUSE -> "You don't own this house."
    ReturnValue.TRADEPLAYERALREADYOWNSAHOUSE -> "Trade player already owns a house."
    ReturnValue.TRADEPLAYERHIGHESTBIDDER -> "Trade player is currently the highest bidder of an auctioned house."
    ReturnValue.YOUCANNOTTRADETHISHOUSE -> "You can not trade this house."
    ReturnValue.YOUDONTHAVEREQUIREDPROFESSION -> "You don't have the required profession."
    ReturnValue.NOTENOUGHFISTLEVEL -> "You do not have enough fist level"
    ReturnValue.NOTENOUGHCLUBLEVEL -> "You do not have enough club level"
    ReturnValue.NOTENOUGHSWORDLEVEL -> "You do not have enough sword level"
    ReturnValue.NOTENOUGHAXELEVEL -> "You do not have enough axe level"
    ReturnValue.NOTENOUGHDISTANCELEVEL -> "You do not have enough distance level"
    ReturnValue.NOTENOUGHSHIELDLEVEL -> "You do not have enough shielding level"
    ReturnValue.NOTENOUGHFISHLEVEL -> "You do not have enough fishing level"
    ReturnValue.NOTPOSSIBLE -> "Sorry, not possible."
    ReturnValue.REWARDCONTAINERISEMPTY -> "You already claimed your reward."
    ReturnValue.CONTACTADMINISTRATOR -> "An error has occurred, please contact your administrator."
    ReturnValue.ITEMISNOTYOURS -> "This item is not yours."
    ReturnValue.ITEMUNTRADEABLE -> "This item is untradeable."
    else -> "Unknown error."
}

/** Mirrors `Lua::isString` (lua_isstring accepts numbers because they are convertible). */
private fun isStringLike(value: LuaValue): Boolean = value.isstring() || value.isnumber()

/** Mirrors `Lua::getString` (lua_tolstring converts numbers; other types become empty). */
private fun luaString(value: LuaValue): String = if (isStringLike(value)) value.tojstring() else ""

/** Mirrors `Lua::getBoolean` (lua_toboolean: only nil and false are false). */
private fun luaBoolean(value: LuaValue, defaultValue: Boolean): Boolean =
    if (value.isnil()) defaultValue else value.toboolean()

internal object UnwiredGameOperations : GameOperations {
    override fun itemIdByName(name: String): Int =
        throw NotImplementedError("GameFunctions.itemIdByName seam is not wired")

    override fun createItem(itemId: Int, count: Int): List<Any> =
        throw NotImplementedError("GameFunctions.createItem seam is not wired")
}
