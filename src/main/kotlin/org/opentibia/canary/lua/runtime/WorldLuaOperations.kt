package org.opentibia.canary.lua.runtime

import org.opentibia.canary.combat.CombatActor
import org.opentibia.canary.combat.CombatEffects
import org.opentibia.canary.combat.CombatTargetState
import org.opentibia.canary.combat.CombatTile
import org.opentibia.canary.combat.condition.CombatType
import org.luaj.vm2.LuaError
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.AreaCombat
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.monsters.BosstiaryRarity_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.creatures.monsters.MonsterForge
import org.opentibia.canary.creatures.monsters.MonsterPipelinesHost
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.creatures.monsters.MonstersEvent
import org.opentibia.canary.creatures.monsters.RaceType
import org.opentibia.canary.creatures.monsters.RespawnPeriod
import org.opentibia.canary.creatures.monsters.RespawnType
import org.opentibia.canary.creatures.monsters.SpellBlock
import org.opentibia.canary.creatures.monsters.SummonBlock
import org.opentibia.canary.creatures.monsters.VoiceBlock
import org.opentibia.canary.creatures.monsters.LootBlock
import org.opentibia.canary.creatures.monsters.MonsterType as FullMonsterType
import org.opentibia.canary.creatures.monsters.MonsterTypeScriptInterface
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.creatures.spawns.MonsterSpawnPath
import org.opentibia.canary.creatures.spawns.MonsterType
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.modal.ModalWindow
import org.opentibia.canary.game.modal.ModalWindowManager
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.Teleport
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.weapons.Weapon
import org.opentibia.canary.items.weapons.Weapons
import org.opentibia.canary.io.PropWriteStream
import org.opentibia.canary.kv.KV
import org.opentibia.canary.kv.KVMemory
import org.opentibia.canary.lua.bindings.CharmFunctions
import org.opentibia.canary.lua.bindings.CombatFunctions
import org.opentibia.canary.lua.bindings.CombatOperations
import org.opentibia.canary.lua.bindings.LootData
import org.opentibia.canary.lua.bindings.LootFunctions
import org.opentibia.canary.lua.bindings.LootOperations
import org.opentibia.canary.lua.bindings.LuaCombat
import org.opentibia.canary.lua.bindings.LuaCombatActor
import org.opentibia.canary.lua.bindings.LuaCombatAreaRegistry
import org.opentibia.canary.lua.bindings.LuaCombatCallbackBridge
import org.opentibia.canary.lua.bindings.LuaCombatExecutor
import org.opentibia.canary.lua.bindings.LuaCombatNoEffects
import org.opentibia.canary.lua.bindings.LuaCombatTile
import org.opentibia.canary.lua.bindings.MonsterAttackInfo
import org.opentibia.canary.lua.bindings.MonsterFunctions
import org.opentibia.canary.lua.bindings.MonsterOperations
import org.opentibia.canary.lua.bindings.MonsterSpellData
import org.opentibia.canary.lua.bindings.MonsterSpellFunctions
import org.opentibia.canary.lua.bindings.MonsterSummonInfo
import org.opentibia.canary.lua.bindings.MonsterVoiceInfo
import org.opentibia.canary.lua.bindings.MonsterTypeFunctions
import org.opentibia.canary.lua.bindings.MonsterTypeOperations
import org.opentibia.canary.lua.bindings.NpcFunctions
import org.opentibia.canary.lua.bindings.NpcOperations
import org.opentibia.canary.lua.bindings.NpcTypeFunctions
import org.opentibia.canary.lua.bindings.NpcTypeOperations
import org.opentibia.canary.lua.bindings.ShopFunctions
import org.opentibia.canary.lua.bindings.WorldNpcOperations
import org.opentibia.canary.lua.bindings.WorldNpcTypeOperations
import org.opentibia.canary.lua.bindings.WorldShopOperations
import org.opentibia.canary.lua.bindings.SpellBindings
import org.opentibia.canary.lua.bindings.SpellOperations
import org.opentibia.canary.lua.bindings.VariantOperations
import org.opentibia.canary.lua.bindings.WeaponFunctions
import org.opentibia.canary.lua.bindings.WeaponOperations
import org.opentibia.canary.lua.bindings.core.ContainerFunctions
import org.opentibia.canary.lua.bindings.core.ContainerOperations
import org.opentibia.canary.lua.bindings.core.CreatureFunctions
import org.opentibia.canary.lua.bindings.core.CreatureOperations
import org.opentibia.canary.lua.bindings.core.GameFunctions
import org.opentibia.canary.lua.bindings.core.GameOperations
import org.opentibia.canary.lua.bindings.core.ItemFunctions
import org.opentibia.canary.lua.bindings.core.ItemOperations
import org.opentibia.canary.lua.bindings.core.ItemTypeFunctions
import org.opentibia.canary.lua.bindings.core.ItemTypeOperations
import org.opentibia.canary.lua.bindings.core.LuaMoneyResult
import org.opentibia.canary.lua.bindings.core.LuaTextMessage
import org.opentibia.canary.lua.bindings.core.PlayerFunctions
import org.opentibia.canary.lua.bindings.core.PlayerFunctionsExtra
import org.opentibia.canary.lua.bindings.core.PlayerOperations
import org.opentibia.canary.lua.bindings.core.PlayerSocialFunctions
import org.opentibia.canary.lua.bindings.core.WorldPlayerSocialOperations
import org.opentibia.canary.lua.bindings.core.PositionFunctions
import org.opentibia.canary.lua.bindings.core.PositionOperations
import org.opentibia.canary.lua.bindings.core.TileFunctions
import org.opentibia.canary.lua.bindings.core.TileOperations
import org.opentibia.canary.lua.bindings.creatures.GroupFunctions
import org.opentibia.canary.lua.bindings.creatures.GuildFunctions
import org.opentibia.canary.lua.bindings.creatures.MountFunctions
import org.opentibia.canary.lua.bindings.creatures.PartyFunctions
import org.opentibia.canary.lua.bindings.creatures.VocationFunctions
import org.opentibia.canary.lua.bindings.events.EventCallbackFunctions
import org.opentibia.canary.lua.bindings.events.EventsSchedulerFunctions
import org.opentibia.canary.lua.bindings.items.ImbuementFunctions
import org.opentibia.canary.lua.bindings.items.ImbuementOperations
import org.opentibia.canary.lua.bindings.library.BankFunctions
import org.opentibia.canary.lua.bindings.library.BatchUpdateFunctions
import org.opentibia.canary.lua.bindings.library.ConfigFunctions
import org.opentibia.canary.lua.bindings.library.DbFunctions
import org.opentibia.canary.lua.bindings.library.GlobalFunctions
import org.opentibia.canary.lua.bindings.library.GlobalOperations
import org.opentibia.canary.lua.bindings.library.KvFunctions
import org.opentibia.canary.lua.bindings.library.LoggerFunctions
import org.opentibia.canary.lua.bindings.library.LuaEnums
import org.opentibia.canary.lua.bindings.library.LuaScriptResults
import org.opentibia.canary.lua.bindings.library.MetricsFunctions
import org.opentibia.canary.lua.bindings.library.ModalWindowFunctions
import org.opentibia.canary.lua.bindings.library.ModalWindowOperations
import org.opentibia.canary.lua.bindings.library.NetworkMessageFunctions
import org.opentibia.canary.lua.bindings.library.ResultFunctions
import org.opentibia.canary.lua.bindings.library.WebhookFunctions
import org.opentibia.canary.lua.bindings.library.WorldKvOperations
import org.opentibia.canary.lua.bindings.library.ZoneFunctions
import org.opentibia.canary.lua.bindings.map.HouseFunctions
import org.opentibia.canary.lua.bindings.map.HouseOperations
import org.opentibia.canary.lua.bindings.map.TeleportFunctions
import org.opentibia.canary.lua.bindings.map.TeleportOperations
import org.opentibia.canary.lua.bindings.map.TownFunctions
import org.opentibia.canary.lua.bindings.map.TownOperations

import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.Town
import org.opentibia.canary.map.Towns
import org.opentibia.canary.map.house.House
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.systems.bestiary.BestiaryRace
import org.opentibia.canary.systems.forge.ItemClassifications
import org.opentibia.canary.systems.imbuement.BaseImbuement
import org.opentibia.canary.systems.imbuement.CategoryImbuement
import org.opentibia.canary.systems.imbuement.Imbuement
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.rewardchest.Reward
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.random.Random

/**
 * Real, non-no-op implementations of the Lua binding seams in
 * `org.opentibia.canary.lua.bindings.core`, backed directly by the Kotlin domain
 * objects (`Player`, `Creature`, `Item`, `Container`, `Position`).
 *
 * Mirrors the C++ primitives registered by Canary's
 * `src/lua/functions/creatures/player/player_functions.cpp`,
 * `creatures/creature_functions.cpp`,
 * `creatures/monster/monster_functions.cpp`,
 * `creatures/monster/monster_type_functions.cpp`,
 * `creatures/monster/loot_functions.cpp`, `items/item_functions.cpp`,
 * `items/item_type_functions.cpp`, `items/container_functions.cpp`,
 * `map/tile_functions.cpp`, `map/position_functions.cpp` and
 * `core/game/game_functions.cpp`. Capabilities the Kotlin domain does not model yet
 * (inventories, vocations, item attributes/action ids, decay timers, tile creatures,
 * magic-effect broadcasts, name catalogs) degrade to a documented no-op rather than
 * inventing state.
 *
 * The operation groups are exposed as separate values because `CreatureOperations`
 * and `ItemOperations` cannot be implemented by one class (their `getId` return types
 * differ, `Long` vs `Int`). [install] wires all groups into a [LuaEnvironment] through
 * the existing `*Functions.install(env, ops)` overloads; it never mutates
 * `LuaEnvironment` itself.
 *
 * @param itemTypes optional item-type catalog used by `Game.createItem` to validate the
 *   id and build the right [Item]/[Container] subtype.
 * @param itemIdsByName optional `name -> id` catalog for the `itemId or name` argument
 *   shared by `Game.createItem`, `player:addItem`, `item:transform`, loot
 *   `setIdFromName`, etc.
 * @param itemAttributeIdsByName optional `attribute name -> id` catalog mirroring
 *   `ItemFunctions::luaItemGetAttribute`/`setAttribute`.
 * @param monsterTypesByName optional `monster name -> MonsterType` catalog mirroring
 *   `g_monsters().getMonsterType(name)` for `MonsterType(name)` and `monster:getType`.
 * @param monsterTypesByRaceId optional `race id -> MonsterType` catalog mirroring
 *   `g_monsters().getMonsterTypeByRaceId(id)`.
 * @param npcTypesByName optional `npc name -> NpcType` catalog mirroring
 *   `g_npcs().getNpcType(name, true)` for `NpcType(name)`.
 * @param gameMap optional spatial grid backing `Tile(...)` and `Position:getTile`; when
 *   absent those primitives return `nil` like Canary's `g_game().map.getTile` miss.
 * @param itemClassifications optional forge classification catalog backing
 *   `Game.createItemClassification`.
 * @param towns optional town registry backing `Town(id or name)` and `house:getTown`.
 * @param imbuements optional imbuement registry backing `Imbuement(id)` and its
 *   base/category lookups.
 * @param teleportByUid optional script-environment UID resolver backing `Teleport(uid)`.
 * @param playerByGuid optional `g_game().getPlayerByGUID` resolver used by
 *   `house:setNewOwnerGuid`.
 * @param startTrade optional `g_game().internalStartTrade` backing `house:startTrade`.
 * @param kv the `g_kv()` root store backing the `kv` table and `KV` userdata class;
 *   defaults to the in-memory `KVMemory` when no SQL-backed `KVSQL` is injected.
 * @param isDay `g_game().gameIsDay()` read by `MonsterType::canSpawn`; defaults to day
 *   because the Lua seam owns no world-light clock (PARITY GAP).
 * @param combatTargetState the game-owned [CombatTargetState] of the production
 *   `CreatureCombatTarget` adapter behind the Lua combat entry points; inert by default.
 * @param combatEffects the `g_game()` effect fanout seam of the area dispatch.
 * @param creatureById `g_game().getCreatureByID(id)` backing `combat:execute`'s
 *   number variant; null resolves nothing.
 * @param playerByName `g_game().getPlayerByNameWildcard(name)` backing the
 *   string variant; null resolves nothing.
 * @param combatRandom the `uniform_random` source of the ported damage math.
 * @param combatValuesOf `Creature::getCombatValues(min, max)` seam; the monster
 *   behavior state owns the values, so the default resolves none (PARITY GAP).
 */
class WorldLuaOperations(
    private val itemTypes: ItemTypeRegistry? = null,
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    itemAttributeIdsByName: Map<String, Int> = emptyMap(),
    monsterTypesByName: Map<String, Any> = emptyMap(),
    monsterTypesByRaceId: Map<Int, Any> = emptyMap(),
    npcTypesByName: Map<String, Any> = emptyMap(),
    private val gameMap: GameMap? = null,
    itemClassifications: ItemClassifications? = null,
    towns: Towns? = null,
    imbuements: Imbuements? = null,
    teleportByUid: (Int) -> Teleport? = { null },
    playerByGuid: (Long) -> Player? = { null },
    startTrade: (Player, Player, Item) -> Boolean = { _, _, _ -> false },
    isDay: () -> Boolean = { true },
    private val kv: KV = KVMemory(),
    /**
     * The game-owned combat state seam of [LuaCombatExecutor]; defaults to the
     * wave-64 inert [CombatTargetState].
     */
    combatTargetState: CombatTargetState = CombatTargetState.Inert,
    /** The `g_game()` effect fanout seam of the area dispatch. */
    combatEffects: CombatEffects = LuaCombatNoEffects,
    /** `g_game().getCreatureByID(id)` backing `combat:execute`'s number variant. */
    creatureById: (Long) -> Creature? = { null },
    /** `g_game().getPlayerByNameWildcard(name)` backing the string variant. */
    playerByName: (String) -> Player? = { null },
    /** `Creature::blockHit`'s `uniform_random` (creature.cpp:968) source. */
    combatRandom: java.util.Random = java.util.Random(),
    /**
     * `Creature::getCombatValues(min, max)` seam (combat.cpp:186-190); the
     * monster behavior state owns the values, so the default resolves none.
     */
    combatValuesOf: (Creature) -> Pair<Int, Int>? = { null },
) {
    /**
     * The environment [install] registered the binding classes on; late-bound
     * entry points ([attachModalWindows]) re-install their operations on it
     * once the world exists.
     */
    private var installedEnvironment: LuaEnvironment? = null

    private val worldGame: WorldGameOperations =
        WorldGameOperations(itemIdsByName, itemTypes, gameMap, monsterTypesByName, itemClassifications)

    val game: GameOperations = worldGame

    private val worldPlayer: WorldPlayerOperations = WorldPlayerOperations(itemIdsByName, itemTypes)

    val player: PlayerOperations = worldPlayer

    val creature: CreatureOperations = WorldCreatureOperations()

    val item: ItemOperations = WorldItemOperations(itemIdsByName, itemAttributeIdsByName, itemTypes)

    val itemType: ItemTypeOperations = WorldItemTypeOperations(itemTypes, itemIdsByName)

    val container: ContainerOperations = WorldContainerOperations(itemIdsByName, itemTypes)

    val tile: TileOperations = WorldTileOperations(gameMap, itemTypes, itemIdsByName)

    val position: PositionOperations = WorldPositionOperations(gameMap)

    private val worldMonster: WorldMonsterOperations =
        WorldMonsterOperations(monsterTypesByName, monsterTypesByRaceId)

    val monster: MonsterOperations = worldMonster

    private val worldMonsterType: WorldMonsterTypeOperations =
        WorldMonsterTypeOperations(monsterTypesByName, monsterTypesByRaceId, isDay)

    val monsterType: MonsterTypeOperations = worldMonsterType

    /**
     * Wires the booted `g_monsters()` registry (`Monsters`, monsters.hpp:242) into the
     * already-installed `Game`/`Monster`/`MonsterType` seams. Canary's monster scripts
     * register into the global registry while `CanaryServer::loadModules` runs
     * (src/canary_server.cpp:599) and `GameReload::reloadMonsters` reloads it through
     * the same path (game_reload.cpp:211-226); the port installs the bindings before
     * the registry exists, so the boot attaches it here.
     */
    fun attachMonsters(monsters: Monsters) {
        worldGame.attachMonsters(monsters)
        worldMonster.attachMonsters(monsters)
        worldMonsterType.attachMonsters(monsters)
    }

    /**
     * Wires the booted `g_game()` player-insertion producers behind the Lua
     * `player:addItem`/`player:addItemEx` entry points
     * (`PlayerFunctions::luaPlayerAddItem` player_functions.cpp:2414 and
     * `luaPlayerAddItemEx` player_functions.cpp:2473), mirroring [attachCombat]:
     * `initState` registers the operations before the world exists, so a
     * composition root binds the composed `Game::internalPlayerAddItem`
     * (game.cpp:2981) and the `Game::internalAddItem` player branch
     * (game.cpp:2698) when it does. Without the bind, the direct domain
     * insertion is kept for embedded/test roots (PARITY GAP: no producers).
     */
    fun attachPlayerItems(
        internalPlayerAddItem: (Player, Item, Boolean, Slots_t) -> ReturnValue,
        internalAddItem: (Player, Item, Int, Int) -> ReturnValue,
    ) {
        worldPlayer.internalPlayerAddItem = internalPlayerAddItem
        worldPlayer.internalAddItem = internalAddItem
    }

    /**
     * Wires the live-monster half of `MonsterTypeFunctions::luaMonsterTypeRegisterEvent`
     * (monster_type_functions.cpp:1235-1242) once the world exists:
     * `g_game().getMonsters()` becomes [liveMonsterEvents] and the
     * `GAME_STATE_STARTUP` gate becomes [isStartup].
     */
    fun attachMonsterEvents(
        liveMonsterEvents: (FullMonsterType, String) -> Unit,
        isStartup: () -> Boolean,
    ) {
        worldMonsterType.attachMonsterEvents(liveMonsterEvents, isStartup)
    }

    /**
     * Wires the world-owned combat seams of the Lua entry points after binding
     * install, mirroring [attachMonsters]: the boot's `g_game().map`, the game
     * combat state/effect fanout and the creature/player lookups. Re-binds the
     * [MonsterSpellFunctions] `canThrowSpell` gate over the new map too.
     *
     * Canary always has the map through `g_game()`; the port's
     * `LuaEnvironment.initState` installs the bindings before the world exists,
     * so a composition root calls this when it does.
     */
    fun attachCombat(
        gameMap: GameMap,
        targetState: CombatTargetState = CombatTargetState.Inert,
        effects: CombatEffects = LuaCombatNoEffects,
        creatureById: (Long) -> Creature? = { null },
        playerByName: (String) -> Player? = { null },
    ) {
        combatExecutor.targetState = targetState
        combatExecutor.effects = effects
        combatExecutor.creatureById = creatureById
        combatExecutor.playerByName = playerByName
        combatExecutor.gameMap = gameMap
        MonsterSpellFunctions.attachCanThrowSpellWorld(gameMap)
    }

    /**
     * Wires the production monster placement path of the Lua
     * `Game.createMonster` (`GameFunctions::luaGameCreateMonster`,
     * src/lua/functions/core/game/game_functions.cpp:589) and
     * `Game.createSoulPitMonster` (game_functions.cpp:641) bindings after
     * install, mirroring [attachCombat]: `initState` registers the documented
     * unwired null operations because no world exists yet, so a composition
     * root binds the composed spawn path and monster host when it does.
     *
     * The Canary chain is `Monster::createMonster(name)`
     * (src/creatures/monsters/monster.cpp:82), `g_game().placeCreature(...)`
     * (src/game/game.cpp:1524), then `Monster::onSpawn(position)`
     * (monster.cpp:627-648); the soul-pit variant additionally latches
     * `Monster::setSoulPitStack(stack)` (monster.cpp:3201-3208) before the
     * spawn event. [monsterHost] dispatches `onSpawn` and the soul-pit stack
     * through the placed monster's host entry; without one the spawn event
     * degrades to a no-op (embedded/test roots), while the placement itself
     * stays live.
     *
     * CALL-SITE NEED (composition root): the world `CanaryServer` owns both
     * `monsterSpawnPath` and `monsterPipelinesHost`, so the boot must call
     * `worldOperations.attachMonsterSpawn(monsterSpawnPath, monsterPipelinesHost)`
     * where it already binds `attachCombat`/`attachModalWindows`
     * (bootstrap/ServerBootstrap.kt, network/CanaryServer.kt init); that wiring
     * belongs to the composition root, not this binding layer.
     */
    fun attachMonsterSpawn(
        spawnPath: MonsterSpawnPath,
        monsterHost: MonsterPipelinesHost? = null,
    ) {
        worldGame.attachMonsterSpawn(
            createMonster = spawnPath::createMonster,
            placeCreature = spawnPath::placeCreature,
            onSpawn = { monster, position -> monsterHost?.onSpawn(monster.id, position) },
            soulPitStack = { monster, stack ->
                monsterHost?.typeStateOf(monster.id)?.let { state ->
                    MonsterForge.setSoulPitStack(state, monster, stack)
                }
                // `Monster::setSoulPitStack` writes the `Monster::soulPit` latch
                // through `setSoulPit(true)` (monster.cpp:3204-3208); the port's
                // `MonsterForge.setSoulPitStack` updates only the split
                // `MonsterTypeState.soulPit` (creatures/monsters PARITY GAP,
                // outside this workstream's ownership), so the binding applies
                // the entity latch here. `PlayerKillTracking` reads it.
                monster.setSoulPit(true)
            },
        )
    }

    /**
     * Wires the `modalWindow:sendToPlayer` Lua entry point
     * (`ModalWindowFunctions::luaModalWindowSendToPlayer`,
     * src/lua/functions/core/game/modal_window_functions.cpp:234-252) after
     * binding install, mirroring [attachCombat]: `initState` registers the
     * documented unwired no-op operations because no world exists yet, so the
     * composition root re-installs the operations over [managerOf] (the world
     * `Game`'s session modal host resolution) when it does. The window then
     * emits through the target player's session `ProtocolGame` sink and its id
     * routes the answers through `Game::playerAnswerModalWindow`
     * (game.cpp:11144).
     */
    /**
     * Wave-74 M3 call site: re-installs the player-extra bindings over the
     * booted `g_livestream()` registry, mirroring the other `attach*` seams.
     */
    fun attachPlayerExtras(
        livestream: () -> org.opentibia.canary.creatures.players.livestream.LivestreamManager?,
        livestreamClientOf: (Player) -> org.opentibia.canary.creatures.players.livestream.LivestreamClient? =
            { it.protocolSink },
    ) {
        val environment = installedEnvironment ?: return
        PlayerFunctionsExtra.install(
            environment,
            org.opentibia.canary.lua.bindings.core.WorldPlayerExtraOperations(
                livestream = livestream,
                livestreamClientOf = livestreamClientOf,
            ),
        )
    }

    fun attachModalWindows(managerOf: (Player) -> ModalWindowManager?) {
        val env = installedEnvironment ?: return
        if (env.getLuaState() == null) {
            return
        }
        ModalWindowFunctions.install(env, SessionModalWindowOperations(managerOf))
    }

    /**
     * `Player::getShopOwner` (player.cpp:6123-6125): the merchant the player's
     * open shop window is bound to, recorded by the `npc:openShopWindow` path
     * ([WorldNpcOperations]). The composition root binds this to
     * `Game.getShopOwner` so `Game::playerLookInShop` (game.cpp:6328) and the
     * buy/sell handlers resolve the interacted merchant in production.
     */
    fun shopOwnerOf(player: Player): Npc? = worldNpc.shopOwnerOf(player)

    /**
     * `Player::closeShopWindow` (player.cpp:2933-2944): drops the recorded
     * owner and releases its shop entry; the composition root binds this to
     * `Game.closeShopWindow`.
     */
    fun closeShopOwner(player: Player): Boolean = worldNpc.closeShopOwner(player)

    val loot: LootOperations = WorldLootOperations(itemIdsByName)

    private val worldNpc: WorldNpcOperations = WorldNpcOperations(itemTypes)

    val npc: NpcOperations = worldNpc

    val npcType: NpcTypeOperations = WorldNpcTypeOperations(npcTypesByName, itemTypes)
    val weapon: WeaponOperations = WorldWeaponOperations(itemTypes)
    val spell: SpellOperations = WorldSpellOperations(itemTypes)

    /** `g_luaEnvironment()`'s area registry behind `createCombatArea`/`getAreaObject`. */
    private val combatAreas: LuaCombatAreaRegistry = LuaCombatAreaRegistry()

    /**
     * The Lua entry-point execution over the wave-64 combat core, shared by
     * `combat:execute` ([WorldCombatOperations]) and the global `do*Combat*`
     * functions ([WorldGlobalOperations]).
     */
    private val combatExecutor: LuaCombatExecutor = LuaCombatExecutor(combatRandom).apply {
        targetState = combatTargetState
        effects = combatEffects
        this.creatureById = creatureById
        this.playerByName = playerByName
        this.combatValuesOf = combatValuesOf
        gameMap = this@WorldLuaOperations.gameMap
        noPvpWorld = { this@WorldLuaOperations.worldGame.getWorldType() == WORLD_TYPE_NO_PVP }
    }

    val combat: CombatOperations = WorldCombatOperations(combatExecutor, combatAreas)

    /** `GlobalFunctions` world seam; only the combat entry points are live. */
    private val worldGlobal: WorldGlobalOperations = WorldGlobalOperations(combatExecutor, combatAreas)

    val variant: VariantOperations = WorldVariantOperations()

    val house: HouseOperations = WorldHouseOperations(gameMap, towns, playerByGuid, startTrade)

    val town: TownOperations = WorldTownOperations(towns)

    val teleport: TeleportOperations = WorldTeleportOperations(teleportByUid)

    val imbuement: ImbuementOperations = WorldImbuementOperations(imbuements)

    /**
     * Registers every real seam on [env], replacing the inert `NO_OP_*` placeholders
     * installed by `LuaEnvironment.initState()`. Safe to call after `initState()`.
     */
    fun install(env: LuaEnvironment) {
        installedEnvironment = env
        GameFunctions.install(env, game)
        PlayerFunctions.install(env, player)
        // SpellBindings is the canonical `Spell` owner: it creates the registry that
        // `LuaEventDispatcher` reads, and delegates the binding surface to SpellFunctions.
        // It installs before the spell-registry consumers below so they resolve the
        // same engine `g_spells()` instance the datapack scripts register into.
        val registry = SpellBindings.install(env, spell)
        env.installSpells(registry)
        // Wave 39 lua-player-2: `canLearnSpell`/`getInstantSpells`/`canCast`
        // resolve through the booted engine `g_spells()` registry
        // (`SpellBindings.spellRegistry`) the datapack scripts record into.
        PlayerSocialFunctions.install(
            env,
            WorldPlayerSocialOperations(itemIdsByName, spells = SpellBindings.spellRegistry()),
        )
        PlayerFunctionsExtra.install(env) // Wave 40 lua-player-4
        // Wave 40 lua-creatures
        GroupFunctions.install(env)
        GuildFunctions.install(env)
        MountFunctions.install(env)
        PartyFunctions.install(env)
        VocationFunctions.install(env)
        CreatureFunctions.install(env, creature)
        ItemFunctions.install(env, item)
        ItemTypeFunctions.install(env, itemType)
        ContainerFunctions.install(env, container)
        TileFunctions.install(env, tile)
        PositionFunctions.install(env, position)
        MonsterFunctions.install(env, monster)
        MonsterTypeFunctions.install(env, monsterType)
        LootFunctions.install(env, loot)
        // Wave 40 lua-monster-npc
        NpcFunctions.install(env, npc)
        NpcTypeFunctions.install(env, npcType)
        // The `canThrowSpell` gate binds the `Game::canThrowObjectTo` fold of
        // the wired map (spells.cpp:1299-1310).
        MonsterSpellFunctions.install(env, gameMap)
        CharmFunctions.install(env)
        ShopFunctions.install(env, WorldShopOperations(itemIdsByName))
        // Wave 40 lua-weapon-spell
        WeaponFunctions.install(env, weapon)
        // `combat:setCallback` scripts stay Lua: the executor resolves them
        // through the bridge of the environment they were registered in
        // (AGENTS.md 1.4).
        combatExecutor.callbackBridge = LuaCombatCallbackBridge(env)
        CombatFunctions.install(env, combat, variant)

        // Wave 40 lua-libraries
        val luaResults = LuaScriptResults()
        DbFunctions.install(env, results = luaResults)
        KvFunctions.install(env, WorldKvOperations(kv))
        LoggerFunctions.install(env)
        MetricsFunctions.install(env)
        ResultFunctions.install(env, luaResults)
        NetworkMessageFunctions.install(env)
        WebhookFunctions.install(env)
        BankFunctions.install(env)
        BatchUpdateFunctions.install(env)
        ConfigFunctions.install(env)
        GlobalFunctions.install(env, worldGlobal)
        ModalWindowFunctions.install(env)
        ZoneFunctions.install(env)
        LuaEnums.install(env)

        // Wave 40 lua-map-items
        HouseFunctions.install(env, house)
        TownFunctions.install(env, town)
        TeleportFunctions.install(env, teleport)
        ImbuementFunctions.install(env, imbuement)

        // Wave 40 lua-events
        worldGame.setEventCallbacks(EventCallbackFunctions.install(env))
        EventsSchedulerFunctions.install(env)
    }

    companion object {
        /** `WORLD_TYPE_NO_PVP` (`src/creatures/creatures_definitions.hpp`). */
        private const val WORLD_TYPE_NO_PVP = 0

        /** Convenience factory that installs the real seams on [env] immediately. */
        fun forEnvironment(env: LuaEnvironment): WorldLuaOperations =
            WorldLuaOperations(itemClassifications = env.itemClassifications).also { it.install(env) }
    }
}

/**
 * `Game` global seam backed by `Game.createItem`, the optional item/monster catalogs and
 * the optional [GameMap].
 *
 * Parity gaps: Canary's boosted-creature/boss rotations, bestiary, towns, houses,
 * charms, talkactions, achievements, raids, effects and the influence/fiendish monster
 * registries are not held by this operations object, so their lookups inherit the
 * documented empty defaults. `Game.createMonster`/`createSoulPitMonster` are live once
 * the composition root attaches the production placement path
 * ([attachMonsterSpawn]); without one they keep the documented null default.
 * `Game.createNpc` and the generated npc variant need the npc spawn engine, and
 * `loadMap`/`loadMapChunk` need the dispatcher; they inherit their seam defaults. The
 * `Game.createItem` split over `ceil(count / stackSize)` items is reproduced through the
 * item-type `stackSize`.
 */
class WorldGameOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    private val itemTypes: ItemTypeRegistry? = null,
    private val gameMap: GameMap? = null,
    private val monsterTypesByName: Map<String, Any> = emptyMap(),
    private val itemClassifications: ItemClassifications? = null,
) : GameOperations {
    private var gameState: Int = 0
    private var worldType: Int = 0

    /** The booted `g_monsters()` registry, attached by `WorldLuaOperations.attachMonsters`. */
    private var monsters: Monsters? = null

    /**
     * Production monster-placement seams behind `Game.createMonster`/
     * `Game.createSoulPitMonster`, bound by `WorldLuaOperations.attachMonsterSpawn`
     * once the world exists; the defaults keep the documented null/no-op
     * operations of an unbooted environment.
     */
    private var createMonsterByName: (String) -> Monster? = { null }
    private var placeMonster: (Monster, Position, Boolean, Boolean) -> Boolean = { _, _, _, _ -> false }
    private var dispatchMonsterSpawn: (Monster, Position) -> Unit = { _, _ -> }
    private var setSoulPitStack: (Monster, Int) -> Unit = { _, _ -> }

    /** Wires the booted `g_monsters()` registry after binding install. */
    fun attachMonsters(monsters: Monsters) {
        this.monsters = monsters
    }

    /** Binds the production `Monster::createMonster`/`Game::placeCreature`/`Monster::onSpawn` chain. */
    fun attachMonsterSpawn(
        createMonster: (String) -> Monster?,
        placeCreature: (Monster, Position, Boolean, Boolean) -> Boolean,
        onSpawn: (Monster, Position) -> Unit,
        soulPitStack: (Monster, Int) -> Unit,
    ) {
        this.createMonsterByName = createMonster
        this.placeMonster = placeCreature
        this.dispatchMonsterSpawn = onSpawn
        this.setSoulPitStack = soulPitStack
    }

    /**
     * `GameFunctions::luaGameCreateMonster` (game_functions.cpp:589):
     * `Monster::createMonster(name)` -> optional master -> `g_game().placeCreature`
     * -> `Monster::onSpawn(position)`. A failed placement clears the summon
     * master and resolves to nil, exactly like the C++ tail. The bosstiary
     * archfoe spectator cooldown timer broadcast (game_functions.cpp:605-611)
     * needs the spectating-player send seam the Kotlin `Player` does not model
     * (PARITY GAP).
     */
    override fun createMonster(
        name: String,
        position: Position,
        extended: Boolean,
        force: Boolean,
        master: Any?,
    ): Any? {
        val monster = createMonsterByName(name) ?: return null
        val isSummon = bindSummonMaster(monster, master)

        if (!placeMonster(monster, position, extended, force)) {
            if (isSummon) {
                monster.setMaster(null)
            }
            return null
        }

        dispatchMonsterSpawn(monster, position)
        return monster
    }

    /**
     * `GameFunctions::luaGameCreateSoulPitMonster` (game_functions.cpp:641):
     * the `Game.createMonster` chain plus `Monster::setSoulPitStack(stack)`
     * (monster.cpp:3201-3208) between the placement and `Monster::onSpawn`.
     */
    override fun createSoulPitMonster(
        name: String,
        position: Position,
        stack: Int,
        extended: Boolean,
        force: Boolean,
        master: Any?,
    ): Any? {
        val monster = createMonsterByName(name) ?: return null
        val isSummon = bindSummonMaster(monster, master)

        if (!placeMonster(monster, position, extended, force)) {
            if (isSummon) {
                monster.setMaster(null)
            }
            return null
        }

        setSoulPitStack(monster, stack)
        dispatchMonsterSpawn(monster, position)
        return monster
    }

    /** `monster->setMaster(master, true)` of the Lua crafters; returns whether a master was bound. */
    private fun bindSummonMaster(monster: Monster, master: Any?): Boolean {
        val masterCreature = master as? Creature ?: return false
        monster.setMaster(masterCreature, true)
        return true
    }

    /**
     * `GameFunctions::luaGameCreateMonsterType` (game_functions.cpp:118-168): a new
     * `MonsterType` is created, its variant/alternate names are expanded exactly like
     * the C++ `std::set<std::string>` path, and every expanded name is registered
     * through `Monsters::tryAddMonsterType`; a duplicate name raises the C++
     * `lua_error` branch. The created type is returned for the binding to wrap.
     */
    override fun createMonsterType(name: String, variant: String, alternateName: String): Any? {
        val registry = monsters ?: return null
        val monsterType = FullMonsterType(name)

        var variantName = variant
        val names = sortedSetOf<String>()
        if (variantName.startsWith("!")) {
            names += name
            variantName = variantName.substring(1)
        }
        var uniqueName = name
        if (variantName.isNotEmpty()) {
            uniqueName = "$variantName|$name"
        }
        names += uniqueName

        monsterType.name = name
        if (alternateName.isNotEmpty()) {
            names += alternateName
            monsterType.name = alternateName
        }
        monsterType.variantName = variantName
        monsterType.nameDescription = "a $name"

        for (registeredName in names) {
            if (!registry.tryAddMonsterType(registeredName, monsterType)) {
                throw LuaError("The monster with name $registeredName already registered")
            }
        }
        return monsterType
    }

    /**
     * `Game.getEventCallbacks()` loaders, populated by `EventCallbackFunctions.install`
     * with the `luaEventCallbackLoad` table Canary builds from `EventCallback_t`.
     */
    private val eventCallbacks: MutableMap<String, LuaValue> = linkedMapOf()

    /** Mirrors `GameFunctions::luaGameGetEventCallbacks`. */
    override fun getEventCallbacks(): Map<String, LuaValue> = eventCallbacks

    /** Installs the `EventCallback_t` name -> loader table produced by the binding. */
    fun setEventCallbacks(loaders: Map<String, LuaValue>) {
        eventCallbacks.clear()
        eventCallbacks.putAll(loaders)
    }

    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

    /**
     * Mirrors `GameFunctions::luaGameCreateItem`: a stackable type spreads `count` over
     * `ceil(count / stackSize)` items, a subtype type (fluid/splash/charges) keeps one
     * item with the requested subtype, and any other type creates `max(1, count)` items.
     */
    override fun createItem(itemId: Int, count: Int): List<Any> {
        val type = itemTypes?.get(itemId)
        if (itemTypes != null && type == null) {
            return emptyList()
        }
        if (type == null) {
            return listOf(Item.create(itemId, count, itemTypes))
        }

        var itemCount = 1
        var subType = 1
        if (type.hasSubType()) {
            if (type.isStackable) {
                itemCount = ceil(count / type.stackSize.coerceAtLeast(1).toDouble()).toInt()
            }
            subType = count
        } else {
            itemCount = max(1, count)
        }

        val items = mutableListOf<Any>()
        repeat(itemCount) {
            var stackCount = subType
            if (type.isStackable) {
                stackCount = minOf(stackCount, type.stackSize.coerceAtLeast(1))
                subType -= stackCount
            }
            items += Item.create(itemId, stackCount, itemTypes)
        }
        return items
    }

    /** Mirrors `Player::getExpForLevel`, exposed through `Game.getExperienceForLevel`. */
    override fun getExperienceForLevel(level: Int): Long = Player.getExpForLevel(level)

    /** Mirrors `Game::gameState` (`GAME_STATE_STARTUP` is 0). */
    override fun getGameState(): Int = gameState

    /** Mirrors `Game::setGameState`; the C++ setter has no failure path. */
    override fun setGameState(state: Int): Boolean {
        gameState = state
        return true
    }

    /** Mirrors `Game::worldType` (`WORLD_TYPE_NO_PVP` is 0). */
    override fun getWorldType(): Int = worldType

    /** Mirrors `Game::setWorldType`; the C++ setter has no failure path. */
    override fun setWorldType(type: Int): Boolean {
        worldType = type
        return true
    }

    /**
     * Mirrors `Item::CreateItemAsContainer(id, size)` plus the optional
     * `g_game().internalAddItem(tile, container, INDEX_WHEREEVER, FLAG_NOLIMIT)` placement.
     * An unknown id is rejected when the catalog is wired; the placement inherits
     * `Tile::addThing`'s append semantics (the Kotlin tile has no static query rules).
     */
    override fun createContainer(itemId: Int, size: Int, position: Position?): Any? {
        val type = itemTypes?.get(itemId)
        if (itemTypes != null && type == null) {
            return null
        }
        val container = Container(id = itemId, capacity = size, type = type)
        if (position == null) {
            return container
        }
        val tile = gameMap?.getTile(position) ?: return null
        tile.addItem(container)
        return container
    }

    /** Mirrors `Map::getOrCreateTile(position, isDynamic)`; the Kotlin tile has no dynamic flag. */
    override fun createTile(position: Position, isDynamic: Boolean): Any? = gameMap?.getOrCreateTile(position)

    /** Mirrors `g_monsters().getMonsterType(name)`; null when no monster catalog is wired. */
    override fun getMonsterTypeByName(name: String): Any? =
        monsters?.getMonsterType(name, silent = true) ?: monsterTypesByName[name]

    /** Mirrors `g_monsters().monsters`; empty when no monster catalog is wired. */
    override fun getMonsterTypes(): Map<String, Any> =
        monsters?.monsters ?: monsterTypesByName

    /** Mirrors `Game::getItemsClassification(id, create = true)`; null when no catalog is wired. */
    override fun createItemClassification(id: Int): Any? = itemClassifications?.createClassification(id)

    /** Mirrors `Item::items.getLadders()`. */
    override fun getLadderIds(): List<Int> = itemTypes?.getLadders() ?: emptyList()

    /** Mirrors `Item::items.getDummys()`. */
    override fun getDummies(): Map<Int, Int> = itemTypes?.getDummys() ?: emptyMap()

    /** Mirrors `Game.getSoulCoreItems` (`m_primaryType == "SoulCores"` or `ITEM_TYPE_SOULCORES`). */
    override fun getSoulCoreItems(): List<Any> =
        itemTypes?.getItems()?.values?.filter {
            it.primaryType == "SoulCores" || it.type == ItemTypes_t.ITEM_TYPE_SOULCORES
        } ?: emptyList()
}

/**
 * `Player` metatable seam backed by the Kotlin [Player] domain object.
 *
 * Parity gaps, all returning a sensible no-op because the Kotlin `Player` carries no
 * client sink or inventory:
 * - `addItem`/`removeItem`/`getItemCount`/`addSkillTries`: no inventory or skills model;
 *   `addItem` returns an empty list (Canary `nil` for failure) and the others `false`/`0`.
 * - `sendTextMessage`: no protocol client to deliver the message to; returns `false`.
 * - `getVocation`: `Player` has no vocation yet; returns `Unit`, which the binding still
 *   wraps as `Vocation` userdata.
 * - storage delegates to the composed `Player::m_storage` (`PlayerFeatures.storage`,
 *   player.hpp:1968): `getStorageValue` returns -1 for a missing key and
 *   `setStorageValue` reproduces the C++ `luaPlayerSetStorageValue` guards (key 0 and the
 *   reserved range are rejected) before `PlayerStorage::add`.
 * - `getMagicLevel` inherits the seam's 0 no-op default; the domain models no magic-level
 *   progression (Canary tracks a separate `Player::magLevel` field).
 * - `getGuid` returns [Player.id], the identity this port persists and keys storage by,
 *   which stands in for Canary's account-scoped `Player::getGUID`.
 * - `getCapacity`/`getFreeCapacity` truncate the domain's `Double` capacity to whole
 *   units, matching Canary's `uint32_t` return.
 * - `getTown`/`setTown` inherit the seam's no-op defaults; the domain has no town state.
 * - `getFightMode` maps the Kotlin [org.opentibia.canary.creatures.FightMode] ordinal to
 *   Canary's 1-based `FightMode_t`.
 * - batch 1 additions: money, bank balance, stamina, soul, spent mana, skill-percent and
 *   skill-level state are transient maps (or documented no-ops) because the Kotlin
 *   [Player] models them as private fields or not at all; see each override's KDoc.
 */
class WorldPlayerOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    private val itemTypes: ItemTypeRegistry? = null,
) : PlayerOperations {
    /**
     * `g_game().internalPlayerAddItem(player, item, dropOnMap, slot)`
     * (game.cpp:2981) seam bound by [WorldLuaOperations.attachPlayerItems]; the
     * `null` default keeps the pre-world direct `Player::addItem` insertion
     * (PARITY GAP: no `Player::postAddNotification` producers).
     */
    internal var internalPlayerAddItem: ((Player, Item, Boolean, Slots_t) -> ReturnValue)? = null

    /**
     * `g_game().internalAddItem(player, item, index, flags)` player branch
     * (game.cpp:2698) seam bound by [WorldLuaOperations.attachPlayerItems]; the
     * `null` default keeps the unported container-index routing
     * (`RETURNVALUE_NOTPOSSIBLE`).
     */
    internal var internalAddItem: ((Player, Item, Int, Int) -> ReturnValue)? = null

    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

    /**
     * `PlayerFunctions::luaPlayerAddItem` (player_functions.cpp:2346-2435):
     * `Item::CreateItem(itemId, count)` then
     * `g_game().internalPlayerAddItem(player, item, canDropOnMap, slot)`.
     */
    override fun addItem(
        player: Any,
        itemId: Int,
        count: Int,
        canDropOnMap: Boolean,
        subType: Int,
        slot: Int,
        tier: Int,
    ): List<Any> {
        val target = player as? Player ?: return emptyList()
        val item = Item(itemId, count.coerceAtLeast(1), itemTypes?.get(itemId))
        val resolvedSlot = Slots_t.fromValue(slot) ?: Slots_t.CONST_SLOT_WHEREEVER
        val ret = internalPlayerAddItem?.invoke(target, item, canDropOnMap, resolvedSlot)
            ?: target.addItem(item, resolvedSlot)
        return if (ret == ReturnValue.NOERROR) listOf(item) else emptyList()
    }

    override fun removeItem(
        player: Any,
        itemId: Int,
        count: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Boolean {
        val target = player as? Player ?: return false
        val item = org.opentibia.canary.creatures.Slots_t.entries
            .firstNotNullOfOrNull { target.getInventoryItem(it) }
            ?.takeIf { it.id == itemId }
            ?: return false
        return target.removeItem(item, count) == org.opentibia.canary.systems.rewardchest.ReturnValue.NOERROR
    }

    override fun getItemCount(
        player: Any,
        itemId: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Long = (player as? Player)?.getItemCount(itemId, subType, ignoreEquipped, ignoreStoreInbox) ?: 0L

    override fun addSkillTries(player: Any, skillType: Int, tries: Long): Boolean {
        val target = player as? Player ?: return false
        val skill = org.opentibia.canary.creatures.skills_t.entries.firstOrNull { it.value == skillType } ?: return false
        target.addSkillTries(skill, tries)
        return true
    }

    override fun sendTextMessage(player: Any, message: LuaTextMessage): Boolean = false

    override fun getStorageValue(player: Any, key: Int): Int {
        val target = player as? Player ?: return -1
        return target.features.storage.get(key)
    }

    override fun setStorageValue(player: Any, key: Int, value: Int): Boolean {
        if (key == 0) {
            return false
        }
        val target = player as? Player ?: return false
        if (PlayerStorage.isStorageKeyInRange(key, PlayerStorage.PSTRG_RESERVED_RANGE_START, PlayerStorage.PSTRG_RESERVED_RANGE_SIZE)) {
            return false
        }
        target.features.storage.add(key, value)
        return true
    }

    override fun getLevel(player: Any): Int = (player as? Player)?.level ?: 0

    override fun getExperience(player: Any): Long = (player as? Player)?.experience ?: 0L

    override fun addExperience(player: Any, experience: Long, sendText: Boolean): Boolean {
        val target = player as? Player ?: return false
        target.addExperience(experience)
        return true
    }

    override fun getMana(player: Any): Int = (player as? Player)?.mana ?: 0

    override fun addMana(player: Any, manaChange: Int, animationOnLoss: Boolean): Boolean {
        val target = player as? Player ?: return false
        target.mana = (target.mana + manaChange).coerceIn(0, target.maxMana)
        return true
    }

    override fun getVocation(player: Any): Any = Unit

    override fun getGuid(player: Any): Long? = (player as? Player)?.id

    override fun getSpeed(player: Any): Int? = (player as? Creature)?.speed

    override fun setSpeed(player: Any, speed: Int): Boolean {
        val target = player as? Creature ?: return false
        target.speed = speed
        return true
    }

    override fun getCapacity(player: Any): Long? = (player as? Player)?.capacity?.toLong()

    override fun setCapacity(player: Any, capacity: Long): Boolean {
        val target = player as? Player ?: return false
        target.capacity = capacity.toDouble()
        return true
    }

    override fun getFreeCapacity(player: Any): Long? = (player as? Player)?.getFreeCapacity()?.toLong()

    override fun getMaxMana(player: Any): Int? = (player as? Player)?.maxMana

    override fun setMaxMana(player: Any, maxMana: Int): Boolean {
        val target = player as? Player ?: return false
        target.maxMana = maxMana
        if (target.mana > maxMana) {
            target.mana = maxMana
        }
        return true
    }

    override fun getSkillLevel(player: Any, skillType: Int): Int? {
        val target = player as? Player ?: return null
        val skill = validSkill(skillType) ?: return null
        return target.getSkillLevel(skill)
    }

    override fun getSkillTries(player: Any, skillType: Int): Long? {
        val target = player as? Player ?: return null
        val skill = validSkill(skillType) ?: return null
        return target.getSkillTries(skill)
    }

    override fun getPremiumDays(player: Any): Int? {
        val target = player as? Player ?: return null
        val account = target.account ?: return null
        return account.getPremiumRemainingDays()
    }

    override fun getFightMode(player: Any): Int? = (player as? Player)?.fightMode?.ordinal?.plus(1)

    override fun hasChaseMode(player: Any): Boolean =
        (player as? Player)?.chaseMode == ChaseMode.Follow

    override fun hasSecureMode(player: Any): Boolean = (player as? Player)?.secureMode ?: false

    // --- batch 1: vitals, skills, inventory, money, conditions, stamina/soul -------

    /**
     * Transient per-player money, bank balance, stamina, soul and spent-mana state,
     * keyed by `Player::getGUID()` because the Kotlin [Player] models none of them
     * (Canary derives money from carried coin items and stores the rest as fields).
     */
    private val moneyHeld = mutableMapOf<Long, Long>()
    private val bankBalances = mutableMapOf<Long, Long>()
    private val staminaMinutes = mutableMapOf<Long, Int>()
    private val soulPoints = mutableMapOf<Long, Int>()
    private val spentMana = mutableMapOf<Long, Long>()

    override fun getBaseMagicLevel(player: Any): Int? = (player as? Player)?.getBaseMagicLevel()

    override fun getManaSpent(player: Any): Long? {
        val target = player as? Player ?: return null
        return spentMana[target.id] ?: 0L
    }

    override fun addManaSpent(player: Any, amount: Long): Boolean? {
        val target = player as? Player ?: return null
        spentMana[target.id] = (spentMana[target.id] ?: 0L) + amount
        return true
    }

    override fun getBaseMaxHealth(player: Any): Int? = (player as? Player)?.maxHealth

    override fun getBaseMaxMana(player: Any): Int? = (player as? Player)?.maxMana

    /**
     * Mirrors `Player::removeExperience` (player.cpp:3746-3805) for the experience and
     * level bookkeeping; the per-level health/mana/capacity loss and the client
     * `sendTextMessage` broadcast are unported (PARITY GAP).
     */
    override fun removeExperience(player: Any, experience: Long, sendText: Boolean): Boolean? {
        val target = player as? Player ?: return null
        if (target.experience == 0L || experience == 0L) {
            return true
        }
        target.experience = (target.experience - experience).coerceAtLeast(0L)
        var level = target.level
        while (level > 1 && target.experience < Player.getExpForLevel(level)) {
            level--
        }
        target.level = level
        return true
    }

    override fun setLevel(player: Any, level: Int): Boolean? {
        val target = player as? Player ?: return null
        val resolved = level.coerceAtLeast(0)
        target.level = resolved
        target.experience = Player.getExpForLevel(resolved)
        return true
    }

    /**
     * Mirrors `luaPlayerSetMagicLevel` (player_functions.cpp:1704-1726) except the
     * `magLevelPercent` bookkeeping, which needs `vocation->getReqMana` progression
     * the Kotlin [Player] does not model (PARITY GAP).
     */
    override fun setMagicLevel(player: Any, level: Int, manaSpent: Long): Boolean? {
        val target = player as? Player ?: return null
        target.magLevel = level.coerceAtLeast(0)
        spentMana[target.id] = if (manaSpent > 0) manaSpent else 0L
        return true
    }

    override fun getEffectiveSkillLevel(player: Any, skillType: Int): Int? {
        val target = player as? Player ?: return null
        val skill = validSkill(skillType) ?: return null
        return target.getSkillLevel(skill)
    }

    /** The domain tracks no skill-percent progression; valid skills read 0 (PARITY GAP). */
    override fun getSkillPercent(player: Any, skillType: Int): Double? {
        if (player !is Player) {
            return null
        }
        validSkill(skillType) ?: return null
        return 0.0
    }

    /**
     * `Player::skills` is private in the domain and exposes no level/tries setter, so
     * the Lua mutation cannot be applied; returns false for a valid player (PARITY GAP).
     */
    override fun setSkillLevel(player: Any, skillType: Int, level: Int, tries: Long): Boolean? {
        if (player !is Player) {
            return null
        }
        validSkill(skillType) ?: return null
        return false
    }

    /** Mirrors `Game::findItemOfType` (game.cpp:3013-3059) with a safety depth cap. */
    override fun getItemById(player: Any, itemId: Int, deepSearch: Boolean, subType: Int): Any? {
        val target = player as? Player ?: return null
        val containers = ArrayDeque<Container>()
        for (slot in Slots_t.entries) {
            if (slot.value < Slots_t.FIRST || slot.value > Slots_t.LAST) {
                continue
            }
            val item = target.getInventoryItem(slot) ?: continue
            if (matchesItem(item, itemId, subType)) {
                return item
            }
            if (deepSearch && item is Container) {
                containers.addLast(item)
            }
        }
        if (!deepSearch) {
            return null
        }
        var visited = 0
        while (containers.isNotEmpty() && visited < Player.MAX_CONTAINER_DEPTH) {
            val container = containers.removeFirst()
            visited++
            for (item in container.items) {
                if (matchesItem(item, itemId, subType)) {
                    return item
                }
                if (item is Container) {
                    containers.addLast(item)
                }
            }
        }
        return null
    }

    /**
     * Mirrors `luaPlayerAddItemEx` (player_functions.cpp:2447-2485): the
     * `canDropOnMap = true` branch routes through
     * `Game::internalPlayerAddItem(player, item, true, slot)` and the
     * `canDropOnMap = false` branch through the `Game::internalAddItem` player
     * branch with the decoded index/flags. Without the attach seam the
     * container-index routing is unported and returns `RETURNVALUE_NOTPOSSIBLE`
     * (PARITY GAP).
     */
    override fun addItemEx(
        player: Any,
        item: Any,
        canDropOnMap: Boolean,
        indexOrSlot: Int,
        flags: Int,
    ): Int? {
        val target = player as? Player ?: return null
        val candidate = item as? Item ?: return ReturnValue.NOTPOSSIBLE.ordinal
        if (canDropOnMap) {
            val slot = Slots_t.fromValue(indexOrSlot) ?: return ReturnValue.NOTPOSSIBLE.ordinal
            val ret = internalPlayerAddItem?.invoke(target, candidate, true, slot)
                ?: return target.addItem(candidate, slot).ordinal
            return ret.ordinal
        }

        val ret = internalAddItem?.invoke(target, candidate, indexOrSlot, flags)
            ?: return ReturnValue.NOTPOSSIBLE.ordinal
        return ret.ordinal
    }

    override fun getSlotItem(player: Any, slot: Int): Any? {
        val target = player as? Player ?: return null
        val resolved = Slots_t.fromValue(slot) ?: return null
        return target.getInventoryItem(resolved)
    }

    override fun getBackpack(player: Any): Any? = (player as? Player)?.getBackpack()

    override fun getMoney(player: Any): Long? {
        val target = player as? Player ?: return null
        return moneyHeld[target.id] ?: 0L
    }

    /** Simplified `Game::addMoney` (game.cpp:3061+): the `flags` routing is unported. */
    override fun addMoney(player: Any, money: Long, flags: Int): LuaMoneyResult? {
        val target = player as? Player ?: return null
        moneyHeld[target.id] = (moneyHeld[target.id] ?: 0L) + money
        return LuaMoneyResult(success = true, addedMoney = money, returnValue = ReturnValue.NOERROR.ordinal)
    }

    /** Simplified `Game::removeMoney` (game.cpp:3155+): the `flags` routing is unported. */
    override fun removeMoney(player: Any, money: Long, flags: Int, useBank: Boolean): Boolean? {
        val target = player as? Player ?: return null
        val purse = moneyHeld[target.id] ?: 0L
        val bank = bankBalances[target.id] ?: 0L
        if (purse + (if (useBank) bank else 0L) < money) {
            return false
        }
        val fromPurse = minOf(purse, money)
        moneyHeld[target.id] = purse - fromPurse
        val remaining = money - fromPurse
        if (remaining > 0) {
            bankBalances[target.id] = bank - remaining
        }
        return true
    }

    override fun getBankBalance(player: Any): Long? {
        val target = player as? Player ?: return null
        return bankBalances[target.id] ?: 0L
    }

    override fun setBankBalance(player: Any, bankBalance: Long): Boolean? {
        val target = player as? Player ?: return null
        bankBalances[target.id] = bankBalance.coerceAtLeast(0L)
        return true
    }

    /** Mirrors `CreatureFunctions::luaCreatureGetCondition` (creature_functions.cpp:747). */
    override fun getCondition(player: Any, conditionType: Int, conditionId: Int, subId: Int): Any? {
        val target = player as? Creature ?: return null
        val type = resolveConditionType(conditionType) ?: return null
        val id = resolveConditionId(conditionId) ?: return null
        return target.getCondition(type, id, subId)
    }

    /**
     * Mirrors `CreatureFunctions::luaCreatureAddCondition` (creature_functions.cpp:768)
     * including the `condition->clone()` copy.
     */
    override fun addCondition(player: Any, condition: Any): Boolean? {
        val target = player as? Creature ?: return null
        val candidate = condition as? Condition ?: return null
        return target.addCondition(candidate.clone())
    }

    /** Mirrors `CreatureFunctions::luaCreatureRemoveCondition` (creature_functions.cpp:780). */
    override fun removeCondition(
        player: Any,
        conditionType: Int,
        conditionId: Int,
        subId: Int,
        force: Boolean,
    ): Boolean? {
        val target = player as? Creature ?: return null
        val type = resolveConditionType(conditionType) ?: return null
        val id = resolveConditionId(conditionId) ?: return null
        val condition = target.getCondition(type, id, subId) ?: return null
        if (subId == 0) {
            target.removeCondition(type, id, force)
        } else {
            target.removeCondition(condition)
        }
        return true
    }

    /** Mirrors `CreatureFunctions::luaCreatureHasCondition` (creature_functions.cpp:806). */
    override fun hasCondition(player: Any, conditionType: Int, subId: Int): Boolean? {
        val target = player as? Creature ?: return null
        val type = resolveConditionType(conditionType) ?: return false
        return target.hasCondition(type, subId)
    }

    /** `Player::staminaMinutes` default (`player.hpp:1817`). */
    override fun getStamina(player: Any): Int? {
        val target = player as? Player ?: return null
        return staminaMinutes[target.id] ?: MAX_STAMINA_MINUTES
    }

    /** Mirrors `luaPlayerSetStamina` (player_functions.cpp:2224): clamped to 2520. */
    override fun setStamina(player: Any, stamina: Int): Boolean? {
        val target = player as? Player ?: return null
        staminaMinutes[target.id] = stamina.coerceIn(0, MAX_STAMINA_MINUTES)
        return true
    }

    override fun getSoul(player: Any): Int? {
        val target = player as? Player ?: return null
        return soulPoints[target.id] ?: 0
    }

    /** Mirrors `Player::changeSoul` (player.cpp:6773) with a regen rate of 1. */
    override fun addSoul(player: Any, soulChange: Int): Boolean? {
        val target = player as? Player ?: return null
        val current = soulPoints[target.id] ?: 0
        val updated = if (soulChange > 0) {
            (current + soulChange).coerceAtMost(getMaxSoul(target) ?: Player.UINT8_MAX)
        } else {
            (current + soulChange).coerceAtLeast(0)
        }
        soulPoints[target.id] = updated
        return true
    }

    /** `Vocation::getSoulMax` (vocation.hpp:136); nil without a wired vocation. */
    override fun getMaxSoul(player: Any): Int? {
        val target = player as? Player ?: return null
        return target.vocations?.getVocation(target.vocationId)?.getSoulMax()
    }

    private fun matchesItem(item: Item, itemId: Int, subType: Int): Boolean {
        if (item.id != itemId) {
            return false
        }
        return subType == -1 || itemSubType(item) == subType
    }

    private fun itemSubType(item: Item): Int =
        if (item.type?.isStackable == true) item.count else 0

    private fun resolveConditionType(value: Int): ConditionType? =
        ConditionType.entries.firstOrNull { it.value == value }

    private fun resolveConditionId(value: Int): ConditionId? =
        ConditionId.entries.firstOrNull { it.value == value }

    /** Mirrors Canary's `skillType <= SKILL_LAST` guard, also rejecting the negative sentinel. */
    private fun validSkill(skillType: Int): skills_t? =
        skills_t.fromValue(skillType)?.takeIf { it.value in skills_t.FIRST..skills_t.LAST }

    private companion object {
        /** `std::min<uint16_t>(2520, stamina)` (player_functions.cpp:2229). */
        const val MAX_STAMINA_MINUTES = 2520
    }
}

/**
 * `Creature` metatable family seam backed by the Kotlin [Creature] domain object.
 */
class WorldCreatureOperations : CreatureOperations {
    override fun getId(creature: Any): Long = (creature as? Creature)?.id ?: 0L

    override fun getName(creature: Any): String = (creature as? Creature)?.name ?: ""

    override fun getHealth(creature: Any): Int = (creature as? Creature)?.health ?: 0

    override fun getMaxHealth(creature: Any): Int = (creature as? Creature)?.maxHealth ?: 0

    override fun setHealth(creature: Any, health: Int): Boolean {
        val target = creature as? Creature ?: return false
        target.health = health.coerceIn(0, target.maxHealth)
        return true
    }

    override fun addHealth(creature: Any, healthChange: Int, combatType: Int): Boolean {
        val target = creature as? Creature ?: return false
        if (healthChange >= 0) {
            target.addHealth(healthChange)
        } else {
            target.drainHealth(-healthChange)
        }
        return true
    }

    override fun getPosition(creature: Any): Position = (creature as? Creature)?.position ?: Position(0, 0, 0)

    override fun getDirection(creature: Any): Int = (creature as? Creature)?.direction?.ordinal ?: 0

    override fun setDirection(creature: Any, direction: Int): Boolean {
        val target = creature as? Creature ?: return false
        val resolved = Direction.entries.getOrNull(direction) ?: return false
        target.direction = resolved
        return true
    }

    /**
     * `Creature::registerCreatureEvent` (creature.cpp:1674-1693): the host's
     * `g_creatureEvents().getEventByName` lookup gate (`creatureEventRegistered`)
     * and the attached-name set live on the domain [Creature].
     */
    override fun registerEvent(creature: Any, name: String): Boolean? =
        (creature as? Creature)?.registerCreatureEvent(name)

    /** `Creature::unregisterCreatureEvent` (creature.cpp:1695-1724). */
    override fun unregisterEvent(creature: Any, name: String): Boolean? =
        (creature as? Creature)?.unregisterCreatureEvent(name)
}

/**
 * `Item` metatable family seam backed by the Kotlin [Item] domain object. Item attributes,
 * action/unique/owner ids, tier/classification, container status, property flags,
 * charges/fluid type and Canary-hundredths weight delegate to the domain; the actor and
 * shader flags are per-instance script state (mirroring `Item::actor`/`Item::shader`),
 * and `serializeAttributes` streams through `Item::serializeAttr`.
 *
 * Parity gaps: parent/tile links, cloning, splitting, custom attribute bags and world
 * removal are still absent and inherit the seam's documented defaults. `transform`
 * reproduces only Canary's identity no-op branch because [Item.id] is immutable, `decay`
 * is a no-op, `setAttribute` does not reproduce Canary's `DURATION`/`DECAYSTATE` decay
 * scheduling beyond rejecting the protected `duration timestamp` attribute, and
 * `setDuration` mutates the shared type (`decayTime`/`showDuration`/`decayTo`) without
 * the Kotlin decay engine's scheduling. `getOwnerName`/`isInsideDepot`/`moveToSlot`/
 * `getImbuements` need the game player lookup, depot chain, inventory slots and
 * per-item imbuement store, which the domain does not carry.
 */
class WorldItemOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    private val itemAttributeIdsByName: Map<String, Int> = emptyMap(),
    private val itemTypes: ItemTypeRegistry? = null,
) : ItemOperations {
    /** `Item::setActor` flags keyed by instance identity; released with the item. */
    private val actors = WeakHashMap<Any, Boolean>()

    /** `Item::setShader` names keyed by instance identity; released with the item. */
    private val shaders = WeakHashMap<Any, String>()

    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

    override fun itemAttributeByName(name: String): Int = itemAttributeIdsByName[name] ?: 0

    override fun getId(item: Any): Int = (item as? Item)?.id ?: 0

    override fun getCount(item: Any): Int = (item as? Item)?.count ?: 0

    override fun getName(item: Any): String = (item as? Item)?.type?.name ?: ""

    /** Mirrors `Item::getSubType`: the stack count for stackable types, else 0. */
    override fun getSubType(item: Any): Int {
        val target = item as? Item ?: return 0
        return if (target.type?.isStackable == true) target.count else 0
    }

    /** Mirrors only Canary's identity no-op branch; retyping needs a mutable `Item.id`. */
    override fun transform(item: Any, itemId: Int, subType: Int): Boolean {
        val target = item as? Item ?: return false
        return target.id == itemId && (subType == -1 || subType == getSubType(target))
    }

    override fun decay(item: Any, decayId: Int): Boolean = false

    /** Mirrors `Item::getActionId` (the `ACTIONID` attribute masked to 16 bits). */
    override fun getActionId(item: Any): Int = (item as? Item)?.getActionId() ?: 0

    override fun setActionId(item: Any, actionId: Int): Boolean {
        val target = item as? Item ?: return false
        target.setActionId(actionId)
        return true
    }

    /** Mirrors `Item::getUniqueId` (the `UNIQUEID` attribute masked to 16 bits). */
    override fun getUniqueId(item: Any): Long = (item as? Item)?.getUniqueId() ?: 0L

    override fun isItem(item: Any): Boolean = item is Item

    /** Mirrors `Item::getContainer` returning the item itself when it is a container. */
    override fun getContainer(item: Any): Any? = item as? Container

    override fun getCharges(item: Any): Int = integerAttribute(item, ItemAttribute.CHARGES)

    override fun getFluidType(item: Any): Int = integerAttribute(item, ItemAttribute.FLUIDTYPE)

    /** Canary reports weight in hundredths of an ounce; the domain stores ounces. */
    override fun getWeight(item: Any): Int = (((item as? Item)?.weight ?: 0.0) * 100.0).roundToInt()

    override fun getPluralName(item: Any): String = stringAttribute(item, ItemAttribute.PLURALNAME)

    override fun getArticle(item: Any): String = stringAttribute(item, ItemAttribute.ARTICLE)

    /**
     * Mirrors `ItemFunctions::luaItemGetDescription` (item_functions.cpp:854-864)
     * over `Item::getDescription(int32_t)` (item.cpp:3352-3355): the receiver's
     * type resolves through the booted `Item::items` registry like the C++
     * `items[id]` lookup, falling back to the item's own type. The
     * `g_spells()`/`g_vocations()`/`Game::getObjectCategory` seams have no
     * composition at this layer (PARITY GAP) and stay absent.
     */
    override fun getDescription(item: Any, distance: Int): String {
        val target = item as? Item ?: return ""
        val itemType = itemTypes?.get(target.id) ?: target.type ?: ItemType(target.id, "")
        return Item.getDescription(itemType, distance, target, items = itemTypes)
    }

    override fun hasAttribute(item: Any, attribute: Int): Boolean {
        val target = item as? Item ?: return false
        val resolved = itemAttribute(attribute) ?: return false
        return target.hasAttribute(resolved)
    }

    /** Mirrors `ItemFunctions::luaItemGetAttribute`, including the `DURATION` special case. */
    override fun getAttribute(item: Any, attribute: Int): Any? {
        val target = item as? Item ?: return null
        val resolved = itemAttribute(attribute) ?: return null
        if (resolved == ItemAttribute.DURATION) {
            return target.getDuration()
        }
        if (!ItemAttribute.isAttributeInteger(resolved) && !ItemAttribute.isAttributeString(resolved)) {
            return null
        }
        return target.getAttribute(resolved)
    }

    override fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean {
        val target = item as? Item ?: return false
        val resolved = itemAttribute(attribute) ?: return false
        if (resolved == ItemAttribute.DURATION_TIMESTAMP) {
            return false
        }
        return when {
            ItemAttribute.isAttributeInteger(resolved) -> {
                val number = value as? Number ?: return false
                target.setAttribute(resolved, number.toLong())
                true
            }
            ItemAttribute.isAttributeString(resolved) -> {
                val text = value as? String ?: return false
                target.setAttribute(resolved, text)
                true
            }
            else -> false
        }
    }

    /** Mirrors `ItemFunctions::luaItemRemoveAttribute` protecting `uid`/`duration timestamp`. */
    override fun removeAttribute(item: Any, attribute: Int): Boolean {
        val target = item as? Item ?: return false
        val resolved = itemAttribute(attribute) ?: return false
        if (resolved == ItemAttribute.UNIQUEID || resolved == ItemAttribute.DURATION_TIMESTAMP) {
            return false
        }
        return target.removeAttribute(resolved)
    }

    /** Mirrors `Item::isContainer` (the type flag, for plain items and containers alike). */
    override fun isContainer(item: Any): Boolean =
        item is Container || (item as? Item)?.type?.isContainer == true

    /** Mirrors `Item::canReceiveAutoCarpet` (`isBlocking && isAlwaysOnTop && !hasHeight`). */
    override fun canReceiveAutoCarpet(item: Any): Boolean = (item as? Item)?.canReceiveAutoCarpet() ?: false

    /** Mirrors `Item::getClassification` (the type upgrade classification). */
    override fun getClassification(item: Any): Int = (item as? Item)?.getClassification() ?: 0

    /** Mirrors `Item::getTier` (the `TIER` attribute, 0 when absent). */
    override fun getTier(item: Any): Int = (item as? Item)?.getTier() ?: 0

    /** Mirrors `Item::setTier`; the domain enforces the upgrade-classification gate. */
    override fun setTier(item: Any, tier: Int): Boolean {
        val target = item as? Item ?: return false
        target.setTier(tier)
        return true
    }

    /** Mirrors `Item::getImbuementSlot` (the `IMBUEMENT_SLOT` attribute, else the type slot). */
    override fun getImbuementSlot(item: Any): Int = (item as? Item)?.getImbuementSlot() ?: 0

    /** Mirrors `Item::hasProperty` through the item-type flags. */
    override fun hasProperty(item: Any, property: Int): Boolean {
        val target = item as? Item ?: return false
        val resolved = ItemProperty.entries.firstOrNull { it.value == property } ?: return false
        return target.hasProperty(resolved)
    }

    /** Mirrors `Item::serializeAttr` (the `ATTR_*` property stream). */
    override fun serializeAttributes(item: Any): ByteArray? {
        val target = item as? Item ?: return null
        val stream = PropWriteStream()
        target.serializeAttr(stream)
        return stream.getStream()
    }

    /** Mirrors `Item::setOwner` for both the creature and the numeric-id overload. */
    override fun setOwner(item: Any, owner: Any): Boolean {
        val target = item as? Item ?: return false
        return when (owner) {
            is Number -> {
                if (owner.toLong() == 0L) {
                    false
                } else {
                    target.setOwner(owner.toLong())
                    true
                }
            }

            is Creature -> {
                target.setOwner(owner.id)
                true
            }

            else -> false
        }
    }

    /** Mirrors `Item::getOwnerId` (the `OWNER` attribute masked to 32 bits). */
    override fun getOwnerId(item: Any): Long = (item as? Item)?.getOwnerId() ?: 0L

    /** Mirrors `Item::isOwner` for both the creature and the numeric-id overload. */
    override fun isOwner(item: Any, owner: Any): Boolean {
        val target = item as? Item ?: return false
        return when (owner) {
            is Number -> target.isOwner(owner.toLong())
            is Creature -> target.isOwner(owner)
            else -> false
        }
    }

    /** Mirrors `Item::hasOwner`. */
    override fun hasOwner(item: Any): Boolean = (item as? Item)?.hasOwner() ?: false

    /** Mirrors `Item::hasActor`; the actor flag is per-instance script state. */
    override fun hasActor(item: Any): Boolean = actors[item] ?: false

    /** Mirrors `Item::setActor`. */
    override fun setActor(item: Any, actor: Boolean): Boolean {
        actors[item] = actor
        return true
    }

    /** Mirrors `Item::setShader`; the shader name is per-instance script state. */
    override fun setShader(item: Any, shader: String): Boolean {
        shaders[item] = shader
        return true
    }

    /** Mirrors `Item::getShader`. */
    override fun getShader(item: Any): String = shaders[item] ?: ""

    /** Mirrors `Item::hasShader`. */
    override fun hasShader(item: Any): Boolean = shaders.containsKey(item)

    /**
     * Mirrors `ItemFunctions::luaItemSetDuration`'s type mutation and random draw
     * (`uniform_random(min, max)` inclusive on both ends). `Item::startDecaying`'s
     * scheduling belongs to the decay engine and is not invoked here (PARITY GAP).
     */
    override fun setDuration(
        item: Any,
        minDuration: Long,
        maxDuration: Long?,
        decayTo: Int,
        showDuration: Boolean,
    ): Boolean {
        val target = item as? Item ?: return false
        val type = target.type ?: return false
        type.decayTime = if (maxDuration == null || maxDuration == 0L) {
            minDuration
        } else {
            uniformRandom(minDuration, maxDuration)
        }
        type.showDuration = showDuration
        type.decayTo = decayTo
        return true
    }

    /** `uniform_random(min, max)` (src/utils/tools.cpp): inclusive on both ends. */
    private fun uniformRandom(min: Long, max: Long): Long =
        if (max <= min) min else Random.nextLong(min, max + 1)

    private fun itemAttribute(attribute: Int): ItemAttribute? =
        ItemAttribute.entries.firstOrNull { it.value == attribute.toLong() }

    private fun integerAttribute(item: Any, attribute: ItemAttribute): Int {
        val target = item as? Item ?: return 0
        return ((target.getAttribute(attribute) as? Long) ?: 0L).toInt()
    }

    private fun stringAttribute(item: Any, attribute: ItemAttribute): String {
        val target = item as? Item ?: return ""
        return target.getAttribute(attribute) as? String ?: ""
    }
}

/**
 * `ItemType` metatable seam backed by the Kotlin [ItemType] domain object plus the
 * optional [ItemTypeRegistry] and name catalog for `ItemType(id or name)`. Every getter
 * reads the domain fields through [ItemTypeOperations]' documented implementations.
 *
 * Parity gap: `create` resolves only through the supplied registry/catalog (Canary's
 * global `Item::items` catalog is not wired into this class).
 */
class WorldItemTypeOperations(
    private val itemTypes: ItemTypeRegistry? = null,
    private val itemIdsByName: Map<String, Int> = emptyMap(),
) : ItemTypeOperations {
    override fun create(idOrName: Any): Any? = when (idOrName) {
        is Number -> itemTypes?.get(idOrName.toInt())
        is String -> itemIdsByName[idOrName]?.let { itemTypes?.get(it) }
        else -> null
    }
}

/**
 * `Tile` metatable seam backed by the Kotlin [Tile] domain object (ground, top/down
 * item list, creature list, tile flags, house link) and the optional [GameMap] for
 * `Tile(x, y, z)`/`Tile:create`.
 *
 * `addItem` builds the item through [Item.create] so container/door/bed subtypes and
 * the `ItemType::stackSize` cap match Canary's `Item::CreateItem` plus
 * `Game::internalAddItem`; the remaining methods inherit the domain-backed
 * implementations from [TileOperations].
 */
class WorldTileOperations(
    private val gameMap: GameMap? = null,
    private val itemTypes: ItemTypeRegistry? = null,
    private val itemIdsByName: Map<String, Int> = emptyMap(),
) : TileOperations {
    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

    override fun create(position: Position): Any? = gameMap?.getTile(position)

    override fun addItem(tile: Any, itemId: Int, count: Int, flags: Long): Any? {
        val target = tile as? Tile ?: return null
        val itemType = itemTypes?.get(itemId)
        if (itemTypes != null && itemType == null) {
            return null
        }
        val stackSize = itemType?.stackSize ?: 100
        val item = Item.create(itemId, minOf(count.coerceAtLeast(1), stackSize), itemTypes)
        if (target.queryAdd(INDEX_WHEREEVER, item, item.getItemCount(), flags.toInt()) != ReturnValue.NOERROR) {
            return null
        }
        target.internalAddThing(item)
        return item
    }
}

/**
 * `Container` metatable seam backed by the Kotlin [Container] domain object. Read-only
 * queries mirror Canary's `Container` semantics, including recursive descent; `addItem`,
 * `addItemEx` and `registerReward` mutate the domain container, while
 * `removeAllItems`/`removeItemById` are documented no-ops because [Container] exposes no
 * removal API.
 *
 * Parity gaps:
 * - `addItem` does not split stacks by `ItemType::stackSize` (not modeled) and ignores
 *   the insertion index (`Container.addItem` always appends).
 * - `getContentDescription` renders item type names only; Canary's `getNameDescription`
 *   count/plural/attribute rendering is not ported.
 * - `registerReward` sets the `DATE` attributes and appends an `ITEM_REWARD_CONTAINER`
 *   item but cannot mark the container as a reward corpse.
 */
class WorldContainerOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    private val itemTypes: ItemTypeRegistry? = null,
) : ContainerOperations {
    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

    override fun getSize(container: Any): Int = (container as? Container)?.items?.size ?: 0

    override fun getMaxCapacity(container: Any): Int = (container as? Container)?.capacity ?: 0

    /** Canary `Container::capacity()` minus the top-level size: the free slots. */
    override fun getCapacity(container: Any): Int {
        val target = container as? Container ?: return 0
        return (target.capacity - target.items.size).coerceAtLeast(0)
    }

    override fun getEmptySlots(container: Any, recursive: Boolean): Int {
        val target = container as? Container ?: return 0
        var slots = (target.capacity - target.items.size).coerceAtLeast(0)
        if (recursive) {
            for (item in flatten(target, true)) {
                if (item is Container) {
                    slots += (item.capacity - item.items.size).coerceAtLeast(0)
                }
            }
        }
        return slots
    }

    override fun getItemHoldingCount(container: Any): Int {
        val target = container as? Container ?: return 0
        return flatten(target, true).size
    }

    /** Mirrors `Container::getItemTypeCount`: top-level items matching [itemId]. */
    override fun getItemCountById(container: Any, itemId: Int, subType: Int): Int {
        val target = container as? Container ?: return 0
        var count = 0
        for (item in target.items) {
            if (item.id == itemId && (subType == -1 || subType == subtype(item))) {
                count += item.count
            }
        }
        return count
    }

    /** 0-based, mirroring `Container::getItemByIndex`. */
    override fun getItem(container: Any, index: Int): Any? {
        val target = container as? Container ?: return null
        return target.items.getOrNull(index)
    }

    override fun hasItem(container: Any, item: Any?): Boolean {
        val target = container as? Container ?: return false
        val sought = item as? Item ?: return false
        return flatten(target, true).any { it === sought }
    }

    override fun getItems(container: Any, recursive: Boolean): List<Any> {
        val target = container as? Container ?: return emptyList()
        return flatten(target, recursive)
    }

    /** Mirrors `Container::getContentDescription` for the modeled item names. */
    override fun getContentDescription(container: Any, oldProtocol: Boolean): String {
        val target = container as? Container ?: return ""
        val descriptions = mutableListOf<String>()
        collectDescriptions(target, oldProtocol, descriptions)
        return if (descriptions.isEmpty()) "nothing" else descriptions.joinToString(", ")
    }

    /** Appends through `Container::addItem`; the stack split and index insertion are not modeled. */
    override fun addItem(container: Any, itemId: Int, count: Int, index: Int, flags: Long): Any? {
        val target = container as? Container ?: return null
        val item = Item.create(itemId, count.coerceAtLeast(1), itemTypes)
        if (target.queryAdd(index, item, count.coerceAtLeast(1), flags.toInt()) != ReturnValue.NOERROR) {
            return null
        }
        target.addItem(item)
        return item
    }

    override fun addItemEx(container: Any, item: Any, index: Int, flags: Long): Int {
        val target = container as? Container ?: return ReturnValue.NOTPOSSIBLE.ordinal
        val candidate = item as? Item ?: return ReturnValue.NOTPOSSIBLE.ordinal
        if (target.items.any { it === candidate }) {
            return ReturnValue.NOTPOSSIBLE.ordinal
        }
        if (target.queryAdd(index, candidate, candidate.getItemCount(), flags.toInt()) != ReturnValue.NOERROR) {
            return ReturnValue.CONTAINERISFULL.ordinal
        }
        target.addItem(candidate)
        return ReturnValue.NOERROR.ordinal
    }

    /** Mirrors `Item::getCorpseOwner` (the `CORPSEOWNER` attribute). */
    override fun getCorpseOwner(container: Any): Long =
        ((container as? Item)?.getAttribute(ItemAttribute.CORPSEOWNER) as? Long) ?: 0L

    override fun registerReward(container: Any): Boolean {
        val target = container as? Container ?: return false
        val rewardId = System.currentTimeMillis()
        val reward = Item.create(Reward.ITEM_REWARD_CONTAINER, 1, itemTypes)
        reward.setAttribute(ItemAttribute.DATE, rewardId)
        target.setAttribute(ItemAttribute.DATE, rewardId)
        target.addItem(reward)
        return true
    }

    /** No-op: [Container] exposes no removal API (documented parity gap). */
    override fun removeAllItems(container: Any, actor: Any?, recursive: Boolean): Long = 0L

    /** No-op: [Container] exposes no removal API (documented parity gap). */
    override fun removeItemById(container: Any, itemId: Int, count: Int, subType: Int): Boolean = false

    private fun subtype(item: Item): Int =
        if (item.type?.isStackable == true) item.count else 0

    /**
     * Mirrors `Container::getContentDescription`: recursively walk the contents, skip
     * non-empty containers (their children are described instead) and render leaf item
     * names, `{id|name}` for the colored/old-protocol form.
     */
    private fun collectDescriptions(container: Container, oldProtocol: Boolean, out: MutableList<String>) {
        for (item in container.items) {
            val nested = item as? Container
            if (nested != null && nested.items.isNotEmpty()) {
                collectDescriptions(nested, oldProtocol, out)
                continue
            }
            val name = item.type?.name ?: ""
            if (name.isEmpty()) {
                continue
            }
            out += if (oldProtocol) "{${item.id}|$name}" else name
        }
    }

    private fun flatten(container: Container, recursive: Boolean): List<Item> {
        val result = mutableListOf<Item>()
        for (item in container.items) {
            result.add(item)
            if (recursive && item is Container) {
                result.addAll(flatten(item, true))
            }
        }
        return result
    }
}

/**
 * `Position` metatable seam. `getDistance` reproduces Canary's
 * `PositionFunctions::luaPositionGetDistance` max-of-|dx|,|dy|,|dz| formula; `getTile`
 * and `getZones` consult the optional [GameMap]. Magic-effect, distance-effect and sound
 * dispatch are no-ops because there is no spectator/protocol sink wired yet.
 *
 * Parity gaps:
 * - `getPathTo` returns null (Canary's `false`) because no pathfinder is wired.
 * - `isSightClear` reproduces only Canary's map-independent early decisions
 *   (`Map::isSightClear`); the tile sight-line check needs blocking properties the
 *   Kotlin [Tile] does not model, so it falls through to false.
 * - `getZones` returns an empty list for a real tile and null for a missing one; no zone
 *   engine is wired yet.
 */
class WorldPositionOperations(
    private val gameMap: GameMap? = null,
) : PositionOperations {
    override fun getDistance(position: Position, target: Position): Int =
        max(max(abs(position.x - target.x), abs(position.y - target.y)), abs(position.z - target.z))

    override fun sendMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean = false

    override fun removeMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean = false

    override fun getPathTo(
        position: Position,
        target: Position,
        minTargetDist: Int,
        maxTargetDist: Int,
        fullPathSearch: Boolean,
        clearSight: Boolean,
        maxSearchDist: Int,
    ): List<Int>? = null

    /** Mirrors `Map::isSightClear`'s map-independent gates. */
    override fun isSightClear(position: Position, target: Position, sameFloor: Boolean): Boolean {
        if (sameFloor && position.z != target.z) {
            return false
        }
        if (position.z == target.z && abs(position.x - target.x) <= 1 && abs(position.y - target.y) <= 1) {
            return true
        }
        if (position.z == target.z && !sameFloor && position.z == 0) {
            return true
        }
        if (position.z > target.z && position.z - target.z > 1) {
            return false
        }
        return false
    }

    override fun getTile(position: Position): Any? = gameMap?.getTile(position)

    override fun getZones(position: Position): List<Any>? =
        if (gameMap?.getTile(position) != null) emptyList() else null

    override fun sendDistanceEffect(
        position: Position,
        target: Position,
        distanceEffect: Int,
        spectator: Any?,
    ): Boolean = false

    override fun sendSingleSoundEffect(position: Position, soundId: Int, actor: Any?): Boolean = false

    override fun sendDoubleSoundEffect(
        position: Position,
        mainSoundId: Int,
        secondarySoundId: Int,
        actor: Any?,
    ): Boolean = false
}

/**
 * `Monster` metatable seam backed by the Kotlin [Monster] domain object plus per-monster
 * state the domain does not model yet (idle flag, friend/target lists, attacked
 * creature, master, challenge flag). The per-id state maps live as long as this
 * operations object.
 *
 * Parity gaps, all documented approximations:
 * - `getType` resolves the type from the name catalog because [Monster] carries no
 *   `m_monsterType`; Canary keeps the type across `monster:setType`/`setName`.
 * - `getSpawnPosition` returns the current position; Canary returns `Monster::masterPos`,
 *   which the spawn engine sets. The port does not link monsters to `SpawnMonster` yet.
 * - `isInSpawnRange` always returns true, matching Canary for a monster whose
 *   `spawnMonster` weak pointer expired.
 * - `isTarget` reproduces only the removed/self/z-level gates; Canary also checks
 *   attackability, protection zones and `canSeeCreature` (Monster targeting subsystem).
 * - `isOpponent`/`isFriend` approximate Canary's faction/master/party rules with the
 *   friend list and shared-master relation.
 * - `selectTarget` gates through `isTarget` and stores the attacked creature without
 *   Canary's login-protection and target-list reordering.
 */
class WorldMonsterOperations(
    private val monsterTypesByName: Map<String, Any> = emptyMap(),
    private val monsterTypesByRaceId: Map<Int, Any> = emptyMap(),
) : MonsterOperations {
    /** The booted `g_monsters()` registry, attached by `WorldLuaOperations.attachMonsters`. */
    private var monsters: Monsters? = null

    /** Wires the booted `g_monsters()` registry after binding install. */
    fun attachMonsters(monsters: Monsters) {
        this.monsters = monsters
    }

    private val idleStatus = mutableMapOf<Long, Boolean>()
    private val friendList = mutableMapOf<Long, MutableList<Any>>()
    private val targetList = mutableMapOf<Long, MutableList<Any>>()
    private val attackedTargets = mutableMapOf<Long, Any>()
    private val masters = mutableMapOf<Long, Any>()
    private val challenged = mutableMapOf<Long, Boolean>()
    private val typeOverrides = mutableMapOf<Long, Any>()
    private val nameOverrides = mutableMapOf<Long, String>()
    private val nameDescriptions = mutableMapOf<Long, String>()
    private val targetDistances = mutableMapOf<Long, Pair<Int, Int>>()
    private val searchTypes = mutableMapOf<Long, Int>()
    private val spawnPositions = mutableMapOf<Long, Position>()
    private val fiendishTimes = mutableMapOf<Long, Long>()
    private val forgeClassifications = mutableMapOf<Long, Int>()
    private val forgeStacks = mutableMapOf<Long, Int>()
    private val hazardFlags = mutableMapOf<Long, MutableMap<String, Boolean>>()
    private val reflectElements = mutableMapOf<Long, MutableMap<Int, Int>>()
    private val extraDefenses = mutableMapOf<Long, Int>()
    private val immunities = mutableMapOf<Long, Boolean>()
    private val criticalChances = mutableMapOf<Long, Int>()
    private val criticalDamages = mutableMapOf<Long, Int>()
    private val attackSpellLists = mutableMapOf<Long, MutableList<Any>>()
    private val defenseSpellLists = mutableMapOf<Long, MutableList<Any>>()

    private fun creatureKey(value: Any): Long? = (value as? Creature)?.id

    /** Resolves the monster's type: `setType` override first, then the name catalog. */
    private fun resolvedType(monster: Monster): Any? =
        typeOverrides[monster.id]
            ?: monsters?.getMonsterType(monster.name, silent = true)
            ?: monsterTypesByName[monster.name]

    private fun flagKey(monster: Monster, name: String): Boolean =
        hazardFlags[monster.id]?.get(name) ?: false

    private fun setFlag(monster: Monster, name: String, value: Boolean) {
        hazardFlags.getOrPut(monster.id) { mutableMapOf() }[name] = value
    }

    override fun isMonster(monster: Any): Boolean = monster is Monster

    override fun getType(monster: Any): Any? {
        val target = monster as? Monster ?: return null
        return resolvedType(target)
    }

    override fun getName(monster: Any): String? {
        val target = monster as? Creature ?: return null
        return nameOverrides[target.id] ?: target.name
    }

    override fun getSpawnPosition(monster: Any): Position? = (monster as? Creature)?.position

    override fun isInSpawnRange(monster: Any, position: Position?): Boolean? {
        if (monster !is Monster) {
            return null
        }
        return true
    }

    override fun isIdle(monster: Any): Boolean? {
        val id = creatureKey(monster) ?: return null
        return idleStatus[id] ?: false
    }

    override fun setIdle(monster: Any, idle: Boolean): Boolean? {
        val id = creatureKey(monster) ?: return null
        idleStatus[id] = idle
        return true
    }

    override fun isTarget(monster: Any, creature: Any?): Boolean? {
        val target = monster as? Monster ?: return null
        val candidate = creature as? Creature ?: return false
        if (candidate === target || candidate.isRemoved()) {
            return false
        }
        return candidate.position.z == target.position.z
    }

    override fun isOpponent(monster: Any, creature: Any?): Boolean? {
        val target = monster as? Monster ?: return null
        val candidate = creature as? Creature ?: return false
        if (candidate === target) {
            return false
        }
        return isFriend(target, candidate) != true
    }

    override fun isFriend(monster: Any, creature: Any?): Boolean? {
        val target = monster as? Monster ?: return null
        val candidate = creature ?: return false
        if (friendList[target.id]?.any { it === candidate } == true) {
            return true
        }
        val master = masters[target.id] ?: return false
        val candidateId = creatureKey(candidate) ?: return false
        return masters[candidateId] === master
    }

    override fun addFriend(monster: Any, creature: Any?): Boolean? {
        val target = monster as? Monster ?: return null
        val candidate = creature ?: return false
        val friends = friendList.getOrPut(target.id) { mutableListOf() }
        if (friends.none { it === candidate }) {
            friends += candidate
        }
        return true
    }

    override fun removeFriend(monster: Any, creature: Any?): Boolean? {
        val target = monster as? Monster ?: return null
        val candidate = creature ?: return false
        friendList[target.id]?.removeAll { it === candidate }
        return true
    }

    override fun getFriendList(monster: Any): List<Any>? {
        val target = monster as? Monster ?: return null
        return friendList[target.id]?.toList() ?: emptyList()
    }

    override fun addTarget(monster: Any, creature: Any?, pushFront: Boolean): Boolean? {
        val target = monster as? Monster ?: return null
        val candidate = creature ?: return false
        if (candidate === target) {
            return false
        }
        val targets = targetList.getOrPut(target.id) { mutableListOf() }
        if (targets.any { it === candidate }) {
            return false
        }
        if (pushFront) {
            targets.add(0, candidate)
        } else {
            targets += candidate
        }
        return true
    }

    override fun removeTarget(monster: Any, creature: Any?): Boolean? {
        val target = monster as? Monster ?: return null
        val candidate = creature ?: return false
        val targets = targetList[target.id] ?: return false
        return targets.removeAll { it === candidate }
    }

    override fun getTargetList(monster: Any): List<Any>? {
        val target = monster as? Monster ?: return null
        return targetList[target.id]?.toList() ?: emptyList()
    }

    override fun isChallenged(monster: Any): Boolean? {
        val id = creatureKey(monster) ?: return null
        return challenged[id] ?: false
    }

    override fun selectTarget(monster: Any, creature: Any?): Boolean? {
        val target = monster as? Monster ?: return null
        if (isTarget(target, creature) != true || creature == null) {
            return false
        }
        attackedTargets[target.id] = creature
        return true
    }

    override fun getTarget(monster: Any): Any? {
        val id = creatureKey(monster) ?: return null
        return attackedTargets[id]
    }

    override fun setTarget(monster: Any, target: Any?): Boolean? {
        val id = creatureKey(monster) ?: return null
        if (target == null) {
            attackedTargets.remove(id)
            return true
        }
        if (target === monster) {
            return false
        }
        attackedTargets[id] = target
        return true
    }

    override fun getMaster(monster: Any): Any? {
        val id = creatureKey(monster) ?: return null
        return masters[id]
    }

    override fun setMaster(monster: Any, master: Any?): Boolean? {
        val id = creatureKey(monster) ?: return null
        if (master == null) {
            masters.remove(id)
            return true
        }
        if (master === monster) {
            return false
        }
        masters[id] = master
        return true
    }

    // --- batch 2: constructor, forge/fiendish, hazard, defense, immunity ----------

    /** No live monster registry is wired; `Game::getMonsterByID` is a PARITY GAP. */
    override fun getMonsterById(id: Long): Any? = null

    override fun setType(monster: Any, nameOrRaceId: Any, restoreHealth: Boolean): Boolean? {
        val target = monster as? Monster ?: return null
        val type = when (nameOrRaceId) {
            is Number -> monsterTypesByRaceId[nameOrRaceId.toInt()]
            is String -> monsterTypesByName[nameOrRaceId]
            else -> null
        }
        if (type != null) {
            typeOverrides[target.id] = type
            if (restoreHealth && type is FullMonsterType) {
                target.maxHealth = type.info.healthMax
                target.health = type.info.health
            }
        }
        return true
    }

    /**
     * Mirrors `Monster::changeTargetDistance`'s reward-boss gate when the type is
     * resolvable; the summon check and `Game::updateCreatureIcon` are not modeled.
     */
    override fun changeTargetDistance(monster: Any, distance: Int, duration: Int): Boolean? {
        val target = monster as? Monster ?: return null
        val type = resolvedType(target) as? FullMonsterType
        if (type?.info?.isRewardBoss == true) {
            return false
        }
        targetDistances[target.id] = distance to duration
        return true
    }

    /** No target-search engine is wired; the request is recorded (PARITY GAP). */
    override fun searchTarget(monster: Any, searchType: Int): Boolean? {
        val target = monster as? Monster ?: return null
        searchTypes[target.id] = searchType
        return true
    }

    /** Records the current position as the spawn anchor; the spawn engine is a gap. */
    override fun setSpawnPosition(monster: Any, interval: Int): Boolean? {
        val target = monster as? Monster ?: return null
        spawnPositions[target.id] = target.position
        return true
    }

    /** Reads the resolved type's `RespawnType`; the default matches a value-initialized one. */
    override fun getRespawnType(monster: Any): RespawnType? {
        val target = monster as? Monster ?: return null
        return (resolvedType(target) as? FullMonsterType)?.info?.respawnType ?: RespawnType()
    }

    override fun getTimeToChangeFiendish(monster: Any): Long? {
        val id = creatureKey(monster) ?: return null
        return fiendishTimes[id] ?: 0L
    }

    override fun setTimeToChangeFiendish(monster: Any, endTime: Long): Boolean? {
        val id = creatureKey(monster) ?: return null
        fiendishTimes[id] = endTime
        return true
    }

    override fun getMonsterForgeClassification(monster: Any): Int? {
        val id = creatureKey(monster) ?: return null
        return forgeClassifications[id] ?: FORGE_NORMAL_MONSTER
    }

    override fun setMonsterForgeClassification(monster: Any, classification: Int): Boolean? {
        val id = creatureKey(monster) ?: return null
        forgeClassifications[id] = classification
        return true
    }

    override fun getForgeStack(monster: Any): Int? {
        val id = creatureKey(monster) ?: return null
        return forgeStacks[id] ?: 0
    }

    override fun setForgeStack(monster: Any, stack: Int): Boolean? {
        val id = creatureKey(monster) ?: return null
        forgeStacks[id] = stack
        return true
    }

    /**
     * Mirrors `Monster::configureForgeSystem`'s classification/stack assignment; the
     * forge icon update and the `ForgeSystemMonster` creature event registration are
     * not ported (PARITY GAP).
     */
    override fun configureForgeSystem(monster: Any, stack: Int): Boolean? {
        val target = monster as? Monster ?: return null
        if (isForgeable(target) != true) {
            return true
        }
        when (forgeClassifications[target.id] ?: FORGE_NORMAL_MONSTER) {
            FORGE_FIENDISH_MONSTER -> forgeStacks[target.id] = FIENDISH_STACK
            FORGE_INFLUENCED_MONSTER -> {
                forgeStacks[target.id] = if (stack == 0) Random.nextInt(1, INFLUENCED_MAX_STACK + 1) else stack
            }

            else -> Unit
        }
        return true
    }

    /** Clears the fiendish/forge bookkeeping; the health restore and icon are gaps. */
    override fun clearFiendishStatus(monster: Any): Boolean? {
        val target = monster as? Monster ?: return null
        fiendishTimes.remove(target.id)
        forgeStacks.remove(target.id)
        forgeClassifications[target.id] = FORGE_NORMAL_MONSTER
        return true
    }

    /**
     * Approximates `Monster::canBeForgeMonster` (`getForgeStack() == 0 &&
     * !isSummon() && !isRewardBoss() && canDropLoot() && isForgeCreature() &&
     * getRaceId() > 0`); the summon/reward-boss predicates need type state the Kotlin
     * [Monster] does not carry.
     */
    override fun isForgeable(monster: Any): Boolean? {
        val target = monster as? Monster ?: return null
        val type = resolvedType(target) as? FullMonsterType ?: return false
        return (forgeStacks[target.id] ?: 0) == 0 &&
            type.info.isForgeCreature &&
            type.info.lootItems.isNotEmpty() &&
            type.info.raceid > 0
    }

    override fun setName(monster: Any, name: String, nameDescription: String?): Boolean? {
        val target = monster as? Creature ?: return null
        nameOverrides[target.id] = name
        if (nameDescription != null) {
            nameDescriptions[target.id] = nameDescription
        }
        return true
    }

    override fun getHazard(monster: Any): Boolean? =
        (monster as? Monster)?.let { flagKey(it, "hazard") }

    override fun setHazard(monster: Any, value: Boolean): Boolean? {
        val target = monster as? Monster ?: return null
        setFlag(target, "hazard", value)
        return true
    }

    override fun getHazardCrit(monster: Any): Boolean? =
        (monster as? Monster)?.let { flagKey(it, "hazardCrit") }

    override fun setHazardCrit(monster: Any, value: Boolean): Boolean? {
        val target = monster as? Monster ?: return null
        setFlag(target, "hazardCrit", value)
        return true
    }

    override fun getHazardDodge(monster: Any): Boolean? =
        (monster as? Monster)?.let { flagKey(it, "hazardDodge") }

    override fun setHazardDodge(monster: Any, value: Boolean): Boolean? {
        val target = monster as? Monster ?: return null
        setFlag(target, "hazardDodge", value)
        return true
    }

    override fun getHazardDamageBoost(monster: Any): Boolean? =
        (monster as? Monster)?.let { flagKey(it, "hazardDamageBoost") }

    override fun setHazardDamageBoost(monster: Any, value: Boolean): Boolean? {
        val target = monster as? Monster ?: return null
        setFlag(target, "hazardDamageBoost", value)
        return true
    }

    override fun getHazardDefenseBoost(monster: Any): Boolean? =
        (monster as? Monster)?.let { flagKey(it, "hazardDefenseBoost") }

    override fun setHazardDefenseBoost(monster: Any, value: Boolean): Boolean? {
        val target = monster as? Monster ?: return null
        setFlag(target, "hazardDefenseBoost", value)
        return true
    }

    override fun getSoulPit(monster: Any): Boolean? =
        (monster as? Monster)?.let { flagKey(it, "soulPit") }

    override fun setSoulPit(monster: Any, value: Boolean): Boolean? {
        val target = monster as? Monster ?: return null
        setFlag(target, "soulPit", value)
        return true
    }

    /**
     * Mirrors `Monster::addReflectElement`'s accumulation; the type's `reflectMap` is
     * updated when the type resolves, otherwise the value stays per-monster.
     */
    override fun addReflectElement(monster: Any, element: Int, percent: Int): Boolean? {
        val target = monster as? Monster ?: return null
        val map = reflectElements.getOrPut(target.id) { mutableMapOf() }
        map[element] = (map[element] ?: 0) + percent
        val type = resolvedType(target) as? FullMonsterType
        val combatType = CombatType.entries.firstOrNull { it.value == element }
        if (type != null && combatType != null) {
            type.info.reflectMap[combatType] = (type.info.reflectMap[combatType] ?: 0) + percent
        }
        return true
    }

    override fun addDefense(monster: Any, defense: Int): Boolean? {
        val target = monster as? Monster ?: return null
        extraDefenses[target.id] = (extraDefenses[target.id] ?: 0) + defense
        return true
    }

    /** Mirrors `Monster::getDefense` (`type.info.defense + m_defense`); the multiplier is a gap. */
    override fun getDefense(monster: Any): Int? {
        val target = monster as? Monster ?: return null
        val typeDefense = ((resolvedType(target) as? FullMonsterType)?.info?.defense) ?: 0
        return typeDefense + (extraDefenses[target.id] ?: 0)
    }

    /** Mirrors `Creature::isDead` (`getHealth() <= 0`). */
    override fun isDead(monster: Any): Boolean? {
        val target = monster as? Creature ?: return null
        return target.health <= 0
    }

    override fun isImmune(monster: Any): Boolean? {
        val id = creatureKey(monster) ?: return null
        return immunities[id] ?: false
    }

    override fun setImmune(monster: Any, immune: Boolean): Boolean? {
        val id = creatureKey(monster) ?: return null
        immunities[id] = immune
        return true
    }

    override fun getCriticalChance(monster: Any): Int? {
        val id = creatureKey(monster) ?: return null
        return criticalChances[id] ?: 0
    }

    override fun setCriticalChance(monster: Any, chance: Int): Boolean? {
        val id = creatureKey(monster) ?: return null
        criticalChances[id] = chance
        return true
    }

    override fun getCriticalDamage(monster: Any): Int? {
        val id = creatureKey(monster) ?: return null
        return criticalDamages[id] ?: 0
    }

    override fun setCriticalDamage(monster: Any, damage: Int): Boolean? {
        val id = creatureKey(monster) ?: return null
        criticalDamages[id] = damage
        return true
    }

    /**
     * Records the spell block; `Monsters::deserializeSpell` and the attack-spell list
     * on the type are not modeled (PARITY GAP).
     */
    override fun addAttackSpell(monster: Any, spell: Any?): Boolean? {
        val id = creatureKey(monster) ?: return null
        if (spell != null) {
            attackSpellLists.getOrPut(id) { mutableListOf() } += spell
        }
        return true
    }

    /** Records the spell block; `Monsters::deserializeSpell` is not modeled (PARITY GAP). */
    override fun addDefenseSpell(monster: Any, spell: Any?): Boolean? {
        val id = creatureKey(monster) ?: return null
        if (spell != null) {
            defenseSpellLists.getOrPut(id) { mutableListOf() } += spell
        }
        return true
    }

    private companion object {
        /** `FORGE_NORMAL_MONSTER` (creatures_definitions.hpp:597). */
        const val FORGE_NORMAL_MONSTER = 0

        /** `FORGE_INFLUENCED_MONSTER` (creatures_definitions.hpp:598). */
        const val FORGE_INFLUENCED_MONSTER = 1

        /** `FORGE_FIENDISH_MONSTER` (creatures_definitions.hpp:599). */
        const val FORGE_FIENDISH_MONSTER = 2

        /** `setForgeStack(15)` for a fiendish monster (`monster.cpp:3872`). */
        const val FIENDISH_STACK = 15

        /** `normal_random(1, 5)` upper bound used by `configureForgeSystem`. */
        const val INFLUENCED_MAX_STACK = 5
    }
}

/**
 * `MonsterType` metatable seam backed by the domain [FullMonsterType]. Every setter
 * writes the very `MonsterType::info` fields the spawn/combat seams read
 * (`shield`/attack target selection, `MonsterTypeState` spell copies, the forge
 * hazard path), exactly like the C++ `MonsterTypeFunctions` setters
 * (src/lua/functions/creatures/monster/monster_type_functions.cpp).
 *
 * The seams not carried by `MonsterInfo` remain documented approximations:
 * - `sb.spell = g_spells().getSpellByName(name)` has no compiled spell registry here,
 *   so `addAttack`/`addDefense` always build the `CombatSpell` actor
 *   (`MonsterSpellFunctions.deserializeSpell`, `Monsters::deserializeSpell`,
 *   monsters.cpp:61-283);
 * - `registerEvent` pushes a late registration onto already-spawned monsters only
 *   after the world composition root attaches the live-monster walk
 *   ([attachMonsterEvents]); an unwired seam keeps the C++ startup behavior;
 * - `setOutfit`'s `WARN_UNSAFE_SCRIPTS` look-type gate needs `Game::isLookTypeRegistered`,
 *   which the appearance registry does not expose to this seam.
 *
 * `getMonstersByRace`/`getMonstersByBestiaryStars` and `MonsterType(raceId)` resolve
 * through the `Game::addBestiaryList` / `IOBosstiary::addBosstiaryMonster` side maps
 * recorded by the `raceId`/`bossRaceId` setters.
 */
class WorldMonsterTypeOperations(
    private val typesByName: Map<String, Any> = emptyMap(),
    private val typesByRaceId: Map<Int, Any> = emptyMap(),
    /**
     * `g_game().gameIsDay()` read by `MonsterType::canSpawn` (monsters.cpp:35-45). The
     * Lua seam owns no world-light clock (PARITY GAP), so it defaults to day; the spawn
     * path passes the real value through `MonsterSpawnPath.canSpawnAt`.
     */
    private val isDay: () -> Boolean = { true },
) : MonsterTypeOperations {
    /** The booted `g_monsters()` registry, attached by `WorldLuaOperations.attachMonsters`. */
    private var monsters: Monsters? = null

    /**
     * `g_game().getMonsters()` walk of `MonsterTypeFunctions::luaMonsterTypeRegisterEvent`
     * (monster_type_functions.cpp:1235-1242), attached by the world composition
     * root once live monsters exist. Before the attach (and without a wired
     * world) a late registration only updates `info.scripts`, exactly like the
     * C++ startup path.
     */
    private var liveMonsterEvents: (FullMonsterType, String) -> Unit = { _, _ -> }

    /** `g_game().getGameState() != GAME_STATE_STARTUP` gate (monster_type_functions.cpp:1235). */
    private var isStartup: () -> Boolean = { true }

    /** `Game::bestiaryList` (game.cpp): race id -> registered name, fed by `raceId`. */
    private val bestiaryList: MutableMap<Int, String> = linkedMapOf()

    /** `IOBosstiary::bosstiaryMonsters` subset: boss race id -> type name. */
    private val bosstiaryList: MutableMap<Int, String> = linkedMapOf()

    /** Wires the booted `g_monsters()` registry after binding install. */
    fun attachMonsters(monsters: Monsters) {
        this.monsters = monsters
    }

    /**
     * Wires the live-world half of `MonsterTypeFunctions::luaMonsterTypeRegisterEvent`
     * (monster_type_functions.cpp:1235-1242). [liveMonsterEvents] pushes a late
     * registration onto every already-spawned monster of the type (the port's
     * `g_game().getMonsters()` walk); [isStartup] is
     * `g_game().getGameState() == GAME_STATE_STARTUP`.
     */
    fun attachMonsterEvents(liveMonsterEvents: (FullMonsterType, String) -> Unit, isStartup: () -> Boolean) {
        this.liveMonsterEvents = liveMonsterEvents
        this.isStartup = isStartup
    }

    private fun type(value: Any): FullMonsterType? = value as? FullMonsterType

    private fun resolveByName(name: String): Any? =
        monsters?.getMonsterType(name, silent = true) ?: typesByName[name]

    private fun resolveByRaceId(raceId: Int): Any? =
        (bestiaryList[raceId] ?: bosstiaryList[raceId])?.let(::resolveByName) ?: typesByRaceId[raceId]

    override fun create(nameOrRaceId: Any): Any? = when (nameOrRaceId) {
        is Number -> resolveByRaceId(nameOrRaceId.toInt())
        is String -> resolveByName(nameOrRaceId)
        else -> null
    }

    override fun getName(monsterType: Any): String? = type(monsterType)?.name

    override fun setName(monsterType: Any, name: String): Boolean? {
        val target = type(monsterType) ?: return null
        target.name = name
        return true
    }

    override fun getHealth(monsterType: Any): Int? = type(monsterType)?.info?.health

    override fun setHealth(monsterType: Any, health: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.health = health
        return true
    }

    override fun getMaxHealth(monsterType: Any): Int? = type(monsterType)?.info?.healthMax

    override fun setMaxHealth(monsterType: Any, maxHealth: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.healthMax = maxHealth
        return true
    }

    override fun getExperience(monsterType: Any): Long? = type(monsterType)?.info?.experience

    override fun setExperience(monsterType: Any, experience: Long): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.experience = experience
        return true
    }

    override fun getBaseSpeed(monsterType: Any): Int? = type(monsterType)?.getBaseSpeed()

    override fun setBaseSpeed(monsterType: Any, speed: Int): Boolean? {
        val target = type(monsterType) ?: return null
        target.setBaseSpeed(speed)
        return true
    }

    override fun getRace(monsterType: Any): Int? = type(monsterType)?.info?.race?.value

    override fun setRace(monsterType: Any, race: Int): Boolean? {
        val target = type(monsterType) ?: return null
        target.info.race = RaceType.entries.firstOrNull { it.value == race } ?: return null
        return true
    }

    override fun getBestiaryClass(monsterType: Any): String? = type(monsterType)?.info?.bestiaryClass

    override fun setBestiaryClass(monsterType: Any, bestiaryClass: String): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.bestiaryClass = bestiaryClass
        return true
    }

    override fun getCorpseId(monsterType: Any): Int? = type(monsterType)?.info?.lookcorpse

    override fun setCorpseId(monsterType: Any, corpseId: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.lookcorpse = corpseId
        return true
    }

    override fun getTypeName(monsterType: Any): String? = type(monsterType)?.typeName

    override fun isAttackable(monsterType: Any): Boolean? = type(monsterType)?.info?.isAttackable

    override fun setAttackable(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isAttackable = value
        return true
    }

    override fun isSummonable(monsterType: Any): Boolean? = type(monsterType)?.info?.isSummonable

    override fun setSummonable(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isSummonable = value
        return true
    }

    override fun isConvinceable(monsterType: Any): Boolean? = type(monsterType)?.info?.isConvinceable

    override fun setConvinceable(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isConvinceable = value
        return true
    }

    override fun isHostile(monsterType: Any): Boolean? = type(monsterType)?.info?.isHostile

    override fun setHostile(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isHostile = value
        return true
    }

    override fun isBlockable(monsterType: Any): Boolean? = type(monsterType)?.info?.isBlockable

    override fun setBlockable(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isBlockable = value
        return true
    }

    override fun canSpawn(monsterType: Any, position: Position): Boolean? =
        type(monsterType)?.canSpawn(position, isDay())

    override fun getLoot(monsterType: Any): List<LootData>? =
        type(monsterType)?.info?.lootItems?.map { lootDataOf(it) }

    override fun addLoot(monsterType: Any, loot: LootData): Boolean? {
        val target = type(monsterType) ?: return null
        target.info.lootItems += lootBlockOf(loot)
        return true
    }

    override fun getAttackList(monsterType: Any): List<MonsterAttackInfo>? =
        type(monsterType)?.info?.attackSpells?.map { attackInfoOf(it) }

    override fun getDefenseList(monsterType: Any): List<MonsterAttackInfo>? =
        type(monsterType)?.info?.defenseSpells?.map { attackInfoOf(it) }

    override fun addAttack(monsterType: Any, spell: Any?): Boolean? = addSpell(monsterType, spell, attack = true)

    override fun addDefense(monsterType: Any, spell: Any?): Boolean? = addSpell(monsterType, spell, attack = false)

    private fun addSpell(monsterType: Any, spell: Any?, attack: Boolean): Boolean? {
        val target = type(monsterType) ?: return null
        val monsterSpell = spell as? MonsterSpellData ?: return null
        val block = MonsterSpellFunctions.deserializeSpell(monsterSpell) ?: return null
        if (attack) {
            target.info.attackSpells += block
        } else {
            target.info.defenseSpells += block
        }
        return true
    }

    private fun attackInfoOf(block: SpellBlock) = MonsterAttackInfo(
        chance = block.chance,
        isCombatSpell = block.combatSpell,
        isMelee = block.isMelee,
        minCombatValue = block.minCombatValue,
        maxCombatValue = block.maxCombatValue,
        range = block.range,
        speed = block.speed,
        spell = block.spell,
    )

    private fun lootDataOf(block: LootBlock): LootData = LootData(
        id = block.id,
        chance = block.chance,
        subType = block.subType,
        countMin = block.countMin,
        countMax = block.countMax,
        actionId = block.actionId,
        text = block.text,
        name = block.name,
        article = block.article,
        attack = block.attack,
        defense = block.defense,
        extraDefense = block.extraDefense,
        armor = block.armor,
        shootRange = block.shootRange,
        hitChance = block.hitChance,
        unique = block.unique,
        childLoot = block.childLoot.mapTo(mutableListOf()) { lootDataOf(it) },
    )

    private fun lootBlockOf(loot: LootData): LootBlock = LootBlock(
        id = loot.id,
        chance = loot.chance,
        subType = loot.subType,
        countMin = loot.countMin,
        countMax = loot.countMax,
        actionId = loot.actionId,
        text = loot.text,
        name = loot.name,
        article = loot.article,
        attack = loot.attack,
        defense = loot.defense,
        extraDefense = loot.extraDefense,
        armor = loot.armor,
        shootRange = loot.shootRange,
        hitChance = loot.hitChance,
        unique = loot.unique,
        childLoot = loot.childLoot.map { lootBlockOf(it) },
    )

    override fun isPreyable(monsterType: Any): Boolean? = type(monsterType)?.info?.isPreyable

    override fun setPreyable(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isPreyable = value
        return true
    }

    override fun isPreyExclusive(monsterType: Any): Boolean? = type(monsterType)?.info?.isPreyExclusive

    override fun setPreyExclusive(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isPreyExclusive = value
        return true
    }

    override fun isIllusionable(monsterType: Any): Boolean? = type(monsterType)?.info?.isIllusionable

    override fun setIllusionable(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isIllusionable = value
        return true
    }

    override fun isPushable(monsterType: Any): Boolean? = type(monsterType)?.info?.pushable

    override fun setPushable(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.pushable = value
        return true
    }

    override fun isHealthHidden(monsterType: Any): Boolean? = type(monsterType)?.info?.hiddenHealth

    override fun setHealthHidden(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.hiddenHealth = value
        return true
    }

    override fun isForgeCreature(monsterType: Any): Boolean? = type(monsterType)?.info?.isForgeCreature

    override fun setForgeCreature(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isForgeCreature = value
        return true
    }

    override fun isFamiliar(monsterType: Any): Boolean? = type(monsterType)?.info?.isFamiliar

    override fun setFamiliar(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isFamiliar = value
        return true
    }

    override fun isRewardBoss(monsterType: Any): Boolean? = type(monsterType)?.info?.isRewardBoss

    override fun setRewardBoss(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.isRewardBoss = value
        return true
    }

    override fun canPushItems(monsterType: Any): Boolean? = type(monsterType)?.info?.canPushItems

    override fun setCanPushItems(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.canPushItems = value
        return true
    }

    override fun canPushCreatures(monsterType: Any): Boolean? = type(monsterType)?.info?.canPushCreatures

    override fun setCanPushCreatures(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.canPushCreatures = value
        return true
    }

    override fun canWalkOnEnergy(monsterType: Any): Boolean? = type(monsterType)?.info?.canWalkOnEnergy

    override fun setCanWalkOnEnergy(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.canWalkOnEnergy = value
        return true
    }

    override fun canWalkOnFire(monsterType: Any): Boolean? = type(monsterType)?.info?.canWalkOnFire

    override fun setCanWalkOnFire(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.canWalkOnFire = value
        return true
    }

    override fun canWalkOnPoison(monsterType: Any): Boolean? = type(monsterType)?.info?.canWalkOnPoison

    override fun setCanWalkOnPoison(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.canWalkOnPoison = value
        return true
    }

    override fun isTargetPreferPlayer(monsterType: Any): Boolean? = type(monsterType)?.info?.targetPreferPlayer

    override fun setTargetPreferPlayer(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.targetPreferPlayer = value
        return true
    }

    override fun isTargetPreferMaster(monsterType: Any): Boolean? = type(monsterType)?.info?.targetPreferMaster

    override fun setTargetPreferMaster(monsterType: Any, value: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.targetPreferMaster = value
        return true
    }

    override fun getNameDescription(monsterType: Any): String? = type(monsterType)?.nameDescription

    override fun setNameDescription(monsterType: Any, value: String): Boolean? {
        val target = type(monsterType) ?: return null
        target.nameDescription = value
        return true
    }

    override fun getRunHealth(monsterType: Any): Int? = type(monsterType)?.info?.runAwayHealth

    override fun setRunHealth(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.runAwayHealth = value
        return true
    }

    override fun getCritChance(monsterType: Any): Int? = type(monsterType)?.info?.critChance

    override fun setCritChance(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.critChance = value
        return true
    }

    override fun getFaction(monsterType: Any): Int? = type(monsterType)?.info?.faction?.value

    override fun setFaction(monsterType: Any, value: Int): Boolean? {
        val target = type(monsterType) ?: return null
        target.info.faction = Faction.entries.firstOrNull { it.value == value } ?: return null
        return true
    }

    override fun getEnemyFactions(monsterType: Any): List<Int>? =
        type(monsterType)?.info?.enemyFactions?.map { it.value }

    override fun addEnemyFaction(monsterType: Any, faction: Int): Boolean? {
        val target = type(monsterType) ?: return null
        target.info.enemyFactions += Faction.entries.firstOrNull { it.value == faction } ?: return null
        return true
    }

    override fun getRaceId(monsterType: Any): Int? = type(monsterType)?.info?.raceid

    override fun setRaceId(monsterType: Any, raceId: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.raceid = raceId
        return true
    }

    override fun addBestiaryList(raceId: Int, name: String) {
        bestiaryList[raceId] = name
    }

    override fun getBestiaryOccurrence(monsterType: Any): Int? = type(monsterType)?.info?.bestiaryOccurrence

    override fun setBestiaryOccurrence(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.bestiaryOccurrence = value
        return true
    }

    override fun getBestiaryLocations(monsterType: Any): String? = type(monsterType)?.info?.bestiaryLocations

    override fun setBestiaryLocations(monsterType: Any, value: String): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.bestiaryLocations = value
        return true
    }

    override fun getBestiaryStars(monsterType: Any): Int? = type(monsterType)?.info?.bestiaryStars

    override fun setBestiaryStars(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.bestiaryStars = value
        return true
    }

    override fun getBestiaryCharmsPoints(monsterType: Any): Int? = type(monsterType)?.info?.bestiaryCharmsPoints

    override fun setBestiaryCharmsPoints(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.bestiaryCharmsPoints = value
        return true
    }

    override fun getBestiarySecondUnlock(monsterType: Any): Int? = type(monsterType)?.info?.bestiarySecondUnlock

    override fun setBestiarySecondUnlock(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.bestiarySecondUnlock = value
        return true
    }

    override fun getBestiaryFirstUnlock(monsterType: Any): Int? = type(monsterType)?.info?.bestiaryFirstUnlock

    override fun setBestiaryFirstUnlock(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.bestiaryFirstUnlock = value
        return true
    }

    override fun getBestiaryToKill(monsterType: Any): Int? = type(monsterType)?.info?.bestiaryToUnlock

    override fun setBestiaryToKill(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.bestiaryToUnlock = value
        return true
    }

    override fun getBestiaryRace(monsterType: Any): Int? = type(monsterType)?.info?.bestiaryRace?.value

    override fun setBestiaryRace(monsterType: Any, value: Int): Boolean? {
        val target = type(monsterType) ?: return null
        target.info.bestiaryRace = BestiaryRace.entries.firstOrNull { it.value == value } ?: return null
        return true
    }

    override fun getCombatImmunities(monsterType: Any): List<Boolean>? {
        val info = type(monsterType)?.info ?: return null
        return (0 until COMBAT_COUNT).map { index -> info.m_damageImmunities.any { it.ordinal == index } }
    }

    override fun setCombatImmunity(monsterType: Any, combatType: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.m_damageImmunities += CombatType.entries.firstOrNull { it.value == combatType } ?: return null
        return true
    }

    override fun getConditionImmunities(monsterType: Any): List<Boolean>? {
        val info = type(monsterType)?.info ?: return null
        return (0 until CONDITION_COUNT).map { index -> info.m_conditionImmunities.any { it.value == index } }
    }

    override fun setConditionImmunity(monsterType: Any, conditionType: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.m_conditionImmunities += ConditionType.entries.firstOrNull { it.value == conditionType } ?: return null
        return true
    }

    override fun getElementList(monsterType: Any): Map<Int, Int>? =
        type(monsterType)?.info?.elementMap?.mapKeys { it.key.value }

    override fun addElement(monsterType: Any, element: Int, percent: Int): Boolean? =
        addElementTo(monsterType, element) { info, combatType -> info.elementMap[combatType] = percent }

    override fun addReflect(monsterType: Any, element: Int, percent: Int): Boolean? =
        addElementTo(monsterType, element) { info, combatType -> info.reflectMap[combatType] = percent }

    override fun addHealing(monsterType: Any, element: Int, percent: Int): Boolean? =
        addElementTo(monsterType, element) { info, combatType -> info.healingMap[combatType] = percent }

    private fun addElementTo(
        monsterType: Any,
        element: Int,
        write: (org.opentibia.canary.creatures.monsters.MonsterInfo, CombatType) -> Unit,
    ): Boolean? {
        val info = type(monsterType)?.info ?: return null
        val combatType = CombatType.entries.firstOrNull { it.value == element } ?: return null
        write(info, combatType)
        return true
    }

    override fun getVoices(monsterType: Any): List<MonsterVoiceInfo>? =
        type(monsterType)?.info?.voiceVector?.map { MonsterVoiceInfo(text = it.text, yellText = it.yellText) }

    override fun addVoice(monsterType: Any, text: String, interval: Int, chance: Int, yell: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.yellSpeedTicks = interval
        info.yellChance = chance
        info.voiceVector += VoiceBlock(text = text, yellText = yell)
        return true
    }

    override fun getCreatureEvents(monsterType: Any): List<String>? = type(monsterType)?.info?.scripts?.toList()

    /**
     * `MonsterTypeFunctions::luaMonsterTypeRegisterEvent`
     * (monster_type_functions.cpp:1224-1246): records the event name on
     * `info.scripts` and, outside startup, pushes it onto every already-spawned
     * monster of the type through the attached world walk.
     */
    override fun registerEvent(monsterType: Any, name: String): Boolean? {
        val target = type(monsterType) ?: return null
        target.info.scripts += name
        if (!isStartup()) {
            liveMonsterEvents(target, name)
        }
        return true
    }

    /** `MonsterType::loadCallback` (monsters.cpp:285-322) over the captured script-interface event. */
    override fun loadCallback(monsterType: Any, scriptInterface: Any?): Boolean? {
        val target = type(monsterType) ?: return null
        return target.loadCallback(scriptInterface as? MonsterTypeScriptInterface)
    }

    override fun setEventType(monsterType: Any, eventType: Int): Boolean? {
        val target = type(monsterType) ?: return null
        target.info.eventType = MonstersEvent.entries.firstOrNull { it.value == eventType }
            ?: MonstersEvent.MONSTERS_EVENT_NONE
        return true
    }

    override fun getSummonList(monsterType: Any): List<MonsterSummonInfo>? =
        type(monsterType)?.info?.summons?.map { MonsterSummonInfo(name = it.name, speed = it.speed, chance = it.chance) }

    override fun addSummon(monsterType: Any, name: String, speed: Int, chance: Int, count: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.summons += SummonBlock(name = name, chance = chance, speed = speed, count = count)
        return true
    }

    override fun getMaxSummons(monsterType: Any): Int? = type(monsterType)?.info?.maxSummons

    override fun setMaxSummons(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.maxSummons = value
        return true
    }

    override fun getArmor(monsterType: Any): Int? = type(monsterType)?.info?.armor

    override fun setArmor(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.armor = value
        return true
    }

    override fun getMitigation(monsterType: Any): Float? = type(monsterType)?.info?.mitigation

    override fun setMitigation(monsterType: Any, value: Float): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.mitigation = value
        return true
    }

    override fun getDefense(monsterType: Any): Int? = type(monsterType)?.info?.defense

    override fun setDefense(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.defense = value
        return true
    }

    override fun getOutfit(monsterType: Any): Outfit? = type(monsterType)?.info?.outfit

    override fun setOutfit(monsterType: Any, outfit: Outfit): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.outfit = outfit.copy()
        return true
    }

    override fun getManaCost(monsterType: Any): Int? = type(monsterType)?.info?.manaCost

    override fun setManaCost(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.manaCost = value
        return true
    }

    override fun getLight(monsterType: Any): LightInfo? = type(monsterType)?.info?.light

    override fun setLight(monsterType: Any, color: Int, level: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.light.color = color
        info.light.level = level
        return true
    }

    override fun getStaticAttackChance(monsterType: Any): Int? = type(monsterType)?.info?.staticAttackChance

    override fun setStaticAttackChance(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.staticAttackChance = value
        return true
    }

    override fun getTargetDistance(monsterType: Any): Int? = type(monsterType)?.info?.targetDistance

    override fun setTargetDistance(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.targetDistance = value
        return true
    }

    override fun getYellChance(monsterType: Any): Int? = type(monsterType)?.info?.yellChance

    override fun setYellChance(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.yellChance = value
        return true
    }

    override fun getYellSpeedTicks(monsterType: Any): Int? = type(monsterType)?.info?.yellSpeedTicks

    override fun setYellSpeedTicks(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.yellSpeedTicks = value
        return true
    }

    override fun getChangeTargetChance(monsterType: Any): Int? = type(monsterType)?.info?.changeTargetChance

    override fun setChangeTargetChance(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.changeTargetChance = value
        return true
    }

    override fun getChangeTargetSpeed(monsterType: Any): Int? = type(monsterType)?.info?.changeTargetSpeed

    override fun setChangeTargetSpeed(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.changeTargetSpeed = value
        return true
    }

    override fun getStrategiesTargetNearest(monsterType: Any): Int? = type(monsterType)?.info?.strategiesTargetNearest

    override fun setStrategiesTargetNearest(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.strategiesTargetNearest = value
        return true
    }

    override fun getStrategiesTargetHealth(monsterType: Any): Int? = type(monsterType)?.info?.strategiesTargetHealth

    override fun setStrategiesTargetHealth(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.strategiesTargetHealth = value
        return true
    }

    override fun getStrategiesTargetDamage(monsterType: Any): Int? = type(monsterType)?.info?.strategiesTargetDamage

    override fun setStrategiesTargetDamage(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.strategiesTargetDamage = value
        return true
    }

    override fun getStrategiesTargetRandom(monsterType: Any): Int? = type(monsterType)?.info?.strategiesTargetRandom

    override fun setStrategiesTargetRandom(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.strategiesTargetRandom = value
        return true
    }

    override fun getRespawnTypePeriod(monsterType: Any): Int? = type(monsterType)?.info?.respawnType?.period?.value

    override fun setRespawnTypePeriod(monsterType: Any, period: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.respawnType.period = RespawnPeriod.entries.firstOrNull { it.value == period } ?: return null
        return true
    }

    override fun isRespawnTypeUnderground(monsterType: Any): Boolean? = type(monsterType)?.info?.respawnType?.underground

    override fun setRespawnTypeUnderground(monsterType: Any, underground: Boolean): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.respawnType.underground = underground
        return true
    }

    override fun getBossRace(monsterType: Any): String? = type(monsterType)?.info?.bosstiaryClass

    override fun setBossRace(monsterType: Any, raceId: Int, bossClass: String): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.bosstiaryRace = BosstiaryRarity_t.entries.firstOrNull { it.value == raceId } ?: return null
        info.bosstiaryClass = bossClass
        return true
    }

    override fun getBossRaceId(monsterType: Any): Int? {
        val info = type(monsterType)?.info ?: return null
        return if (info.bosstiaryClass.isEmpty()) 0 else info.bosstiaryRace.value
    }

    override fun setBossRaceId(monsterType: Any, raceId: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.raceid = raceId
        return true
    }

    override fun addBosstiaryMonster(raceId: Int, name: String) {
        bosstiaryList[raceId] = name
    }

    override fun getSoundChance(monsterType: Any): Int? = type(monsterType)?.info?.soundChance

    override fun setSoundChance(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.soundChance = value
        return true
    }

    override fun getSoundSpeedTicks(monsterType: Any): Int? = type(monsterType)?.info?.soundSpeedTicks

    override fun setSoundSpeedTicks(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.soundSpeedTicks = value
        return true
    }

    override fun addSound(monsterType: Any, soundId: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.soundVector += soundEffectOf(soundId)
        return true
    }

    override fun getSounds(monsterType: Any): List<Int>? = type(monsterType)?.info?.soundVector?.map { it.value }

    override fun getDeathSound(monsterType: Any): Int? = type(monsterType)?.info?.deathSound?.value

    override fun setDeathSound(monsterType: Any, value: Int): Boolean? {
        val info = type(monsterType)?.info ?: return null
        info.deathSound = soundEffectOf(value)
        return true
    }

    override fun getVariant(monsterType: Any): String? = type(monsterType)?.variantName

    override fun setVariant(monsterType: Any, value: String): Boolean? {
        val target = type(monsterType) ?: return null
        target.variantName = value
        return true
    }

    /**
     * `Monsters::getMonstersByRace` (monsters.cpp:401-412): walks the bestiary list
     * recorded by `raceId` and keeps the types whose `bestiaryRace` matches.
     */
    override fun getMonstersByRace(race: Int): List<Any> {
        val resolved = bestiaryList.values.mapNotNull(::resolveByName)
        return resolved.filter { (it as? FullMonsterType)?.info?.bestiaryRace?.value == race }
    }

    /** `Monsters::getMonstersByBestiaryStars` (monsters.cpp:414-425). */
    override fun getMonstersByBestiaryStars(stars: Int): List<Any> {
        val resolved = bestiaryList.values.mapNotNull(::resolveByName)
        return resolved.filter { (it as? FullMonsterType)?.info?.bestiaryStars == stars }
    }

    private fun soundEffectOf(value: Int): SoundEffect =
        SoundEffect.entries.firstOrNull { it.value == value } ?: SoundEffect.SILENCE

    private companion object {
        /** `COMBAT_COUNT` (creatures_definitions.hpp). */
        const val COMBAT_COUNT = 14

        /** `CONDITION_COUNT` (creatures_definitions.hpp). */
        const val CONDITION_COUNT = 40
    }
}

/** `Loot` seam resolving `loot:setIdFromName` through the item-name catalog. */
class WorldLootOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
) : LootOperations {
    /** `Item::items.getItemIdByName` lowercases the name before the `nameToItems` lookup. */
    override fun itemIdByName(name: String): Int = itemIdsByName[name.lowercase()] ?: 0
}

/**
 * `Weapon` seam backed by the item-type catalog and the [Weapons] registry.
 *
 * PARITY GAPS: the Lua `onUseWeapon` callback is captured by the binding but its
 * execution lives in the script bridge (`Weapon::executeUseWeapon`); the vocation
 * catalog is not wired into this operations object, so `weapon:vocation` inherits
 * the `-1` not-found sentinel the domain `addVocWeaponMap` rejects.
 */
class WorldWeaponOperations(
    private val itemTypes: ItemTypeRegistry? = null,
) : WeaponOperations {
    private val weapons = Weapons()
    private val localItemTypes = linkedMapOf<Int, ItemType>()

    /** `Item::items.getItemType(id)`; the C++ `operator[]` auto-creates missing ids. */
    override fun getItemType(id: Int): ItemType {
        val registry = itemTypes ?: return localItemTypes.getOrPut(id) { ItemType(id, "") }
        registry.get(id)?.let { return it }
        return ItemType(id = id, name = "").also { registry.register(it) }
    }

    /** `Weapons::registerLuaEvent(weapon)`. */
    override fun registerLuaEvent(weapon: Weapon, callback: LuaValue): Boolean =
        weapons.registerLuaEvent(weapon)

    /** `g_vocations().getVocationId(name)`; no vocation catalog is wired (PARITY GAP). */
    override fun vocationIdByName(name: String): Int = -1
}

/**
 * `Spell` seam backed by the item-type catalog.
 *
 * PARITY GAPS: no `Vocations` catalog is wired into this operations object, so
 * `spell:vocation` resolves every name to `VOCATION_NONE` (0, exactly what
 * `g_vocations().getVocationId` returns for an unknown name) and the vocation
 * getter cannot name the stored ids.
 */
class WorldSpellOperations(
    private val itemTypes: ItemTypeRegistry? = null,
) : SpellOperations {
    private val localItemTypes = linkedMapOf<Int, ItemType>()

    /** `Item::items.getItemType(rune->getRuneItemId())`; auto-creates like the C++ map. */
    override fun getItemType(id: Int): ItemType {
        val registry = itemTypes ?: return localItemTypes.getOrPut(id) { ItemType(id, "") }
        registry.get(id)?.let { return it }
        return ItemType(id = id, name = "").also { registry.register(it) }
    }

    /** `g_vocations().getVocationId(name)`; unknown names yield `VOCATION_NONE` (0). */
    override fun vocationIdByName(name: String): Int = Vocation.VOCATION_NONE

    /** `g_vocations().getVocation(id)->getVocName()`; no catalog is wired (PARITY GAP). */
    override fun vocationName(vocationId: Int): String? = null
}

/**
 * `Combat` seam over the wave-64 combat core.
 *
 * `combat:execute` (combat_functions.cpp:165-250) dispatches through
 * [LuaCombatExecutor] over `Combat::doCombat` (combat.cpp:1461-1505);
 * `combat:setArea` resolves the [LuaCombatAreaRegistry] behind
 * `g_luaEnvironment().getAreaObject` (lua_environment.cpp:97-109). The
 * script-id gate returns `EVENT_ID_LOADING`, so `combat:setArea` follows the
 * loading path (the C++ `EVENT_ID_LOADING` guard is not modelled; PARITY GAP).
 */
class WorldCombatOperations(
    private val executor: LuaCombatExecutor = LuaCombatExecutor(),
    private val areas: LuaCombatAreaRegistry = LuaCombatAreaRegistry(),
) : CombatOperations {
    override fun getScriptId(): Int = CombatFunctions.EVENT_ID_LOADING

    override fun getAreaObject(id: Int): AreaCombat? = areas.getAreaObject(id)

    /** `CombatFunctions::luaCombatExecute` over the ported execution. */
    override fun execute(combat: LuaCombat, creature: Any, variant: LuaVariant): Boolean =
        executor.execute(combat, creature as? Creature, variant)
}

/**
 * `GlobalOperations` world seam behind the `GlobalFunctions` globals
 * (src/lua/functions/core/game/global_functions.cpp).
 *
 * The combat entry points (`doTargetCombatHealth`/`Mana`/`Condition`/`Dispel`
 * and their area variants) run through [LuaCombatExecutor] over the wave-64
 * combat core; `createCombatArea` populates the [LuaCombatAreaRegistry] the
 * `area` argument resolves through
 * (`g_luaEnvironment().createAreaObject`, lua_environment.cpp:105-109).
 *
 * PARITY GAPS (inherited defaults): the UID registry, waypoints, war lists,
 * chat sends, depot lookups and map clean/save stay unwired, and the C++
 * `createCombatArea` `EVENT_ID_LOADING` guard is not modelled.
 */
class WorldGlobalOperations(
    private val executor: LuaCombatExecutor = LuaCombatExecutor(),
    private val areas: LuaCombatAreaRegistry = LuaCombatAreaRegistry(),
) : GlobalOperations {

    /** `GlobalFunctions::luaCreateCombatArea` (global_functions.cpp:269-308). */
    override fun createCombatArea(area: List<List<Int>>, extendedArea: List<List<Int>>?): Long? {
        if (area.isEmpty()) {
            return null
        }
        val id = areas.createAreaObject()
        val combatArea = areas.getAreaObject(id) ?: return null
        if (extendedArea != null) {
            combatArea.setupExtArea(extendedArea.flatten(), extendedArea.size)
        }
        combatArea.setupArea(area.flatten(), area.size)
        return id.toLong()
    }

    /** `GlobalFunctions::luaDoAreaCombatHealth` (global_functions.cpp:310-346). */
    override fun doAreaCombatHealth(
        creature: Any?,
        combatType: Int,
        position: Position,
        areaId: Long,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean {
        if (!areaFound(areaId)) {
            return false
        }
        return executor.doAreaCombatHealth(
            caster = creature as? Creature,
            position = position,
            area = areaOrNull(areaId),
            combatType = combatType,
            min = min,
            max = max,
            effect = effect,
            origin = origin,
            instantSpellName = instantSpellName,
            runeSpellName = runeSpellName,
        )
    }

    /** `GlobalFunctions::luaDoTargetCombatHealth` (global_functions.cpp:348-397). */
    override fun doTargetCombatHealth(
        creature: Any?,
        target: Any,
        combatType: Int,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean = executor.doTargetCombatHealth(
        caster = creature as? Creature,
        target = target as? Creature ?: return false,
        combatType = combatType,
        min = min,
        max = max,
        effect = effect,
        origin = origin,
        instantSpellName = instantSpellName,
        runeSpellName = runeSpellName,
    )

    /** `GlobalFunctions::luaDoAreaCombatMana` (global_functions.cpp:399-427). */
    override fun doAreaCombatMana(
        creature: Any?,
        position: Position,
        areaId: Long,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean {
        if (!areaFound(areaId)) {
            return false
        }
        return executor.doAreaCombatMana(
            caster = creature as? Creature,
            position = position,
            area = areaOrNull(areaId),
            min = min,
            max = max,
            effect = effect,
            origin = origin,
            instantSpellName = instantSpellName,
            runeSpellName = runeSpellName,
        )
    }

    /** `GlobalFunctions::luaDoTargetCombatMana` (global_functions.cpp:429-464). */
    override fun doTargetCombatMana(
        creature: Any?,
        target: Any,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean = executor.doTargetCombatMana(
        caster = creature as? Creature,
        target = target as? Creature ?: return false,
        min = min,
        max = max,
        effect = effect,
        origin = origin,
        instantSpellName = instantSpellName,
        runeSpellName = runeSpellName,
    )

    /** `GlobalFunctions::luaDoAreaCombatCondition` (global_functions.cpp:470-499). */
    override fun doAreaCombatCondition(
        creature: Any?,
        position: Position,
        areaId: Long,
        condition: Any,
        effect: Int,
    ): Boolean {
        if (!areaFound(areaId)) {
            return false
        }
        return executor.doAreaCombatCondition(
            caster = creature as? Creature,
            position = position,
            area = areaOrNull(areaId),
            condition = condition as? Condition ?: return false,
            effect = effect,
        )
    }

    /** `GlobalFunctions::luaDoTargetCombatCondition` (global_functions.cpp:501-513). */
    override fun doTargetCombatCondition(creature: Any?, target: Any, condition: Any, effect: Int): Boolean =
        executor.doTargetCombatCondition(
            caster = creature as? Creature,
            target = target as? Creature ?: return false,
            condition = condition as? Condition ?: return false,
            effect = effect,
        )

    /** `GlobalFunctions::luaDoAreaCombatDispel` (global_functions.cpp:515-531). */
    override fun doAreaCombatDispel(
        creature: Any?,
        position: Position,
        areaId: Long,
        dispelType: Int,
        effect: Int,
    ): Boolean {
        if (!areaFound(areaId)) {
            return false
        }
        return executor.doAreaCombatDispel(
            caster = creature as? Creature,
            position = position,
            area = areaOrNull(areaId),
            dispelType = dispelType,
            effect = effect,
        )
    }

    /** `GlobalFunctions::luaDoTargetCombatDispel` (global_functions.cpp:533-560). */
    override fun doTargetCombatDispel(creature: Any?, target: Any, dispelType: Int, effect: Int): Boolean =
        executor.doTargetCombatDispel(
            caster = creature as? Creature,
            target = target as? Creature ?: return false,
            dispelType = dispelType,
            effect = effect,
        )

    /**
     * `g_luaEnvironment().getAreaObject(areaId)`; the C++ `if (area || areaId == 0)`
     * gate admits the null-area strike on the bare target tile, so an unknown
     * non-zero id reports false.
     */
    private fun areaOrNull(areaId: Long): AreaCombat? =
        if (areaId == 0L) null else areas.getAreaObject(areaId.toInt())

    /** The C++ `(area || areaId == 0)` presence gate of the `doAreaCombat*` bindings. */
    private fun areaFound(areaId: Long): Boolean =
        areaId == 0L || areas.getAreaObject(areaId.toInt()) != null
}

/** `Variant` seam resolving `Lua::getThing(thing)->getPosition()` for creatures and tiles. */
class WorldVariantOperations : VariantOperations {
    override fun thingPosition(value: Any): Position? = when (value) {
        is Creature -> value.position
        is Tile -> value.position
        else -> null
    }
}

/**
 * `House` metatable seam backed by the optional [GameMap] house registry and [Towns]
 * registry, mirroring the `g_game()` lookups of
 * src/lua/functions/map/house_functions.cpp.
 *
 * Parity gaps, all documented no-ops because the Kotlin server does not wire the
 * corresponding engine yet:
 * - [startTrade] defaults to `false` (Canary's `Game::internalStartTrade` has no
 *   injectable trade manager here); a failed start makes `house:startTrade` reset the
 *   transfer item exactly like Canary.
 * - [playerByGuid] defaults to null (no offline/online player registry is wired), so
 *   `house:setNewOwnerGuid` cannot message a pending-transfer owner.
 * - [isTransferOnServerRestart] reads the flag from the house's injected [House.world],
 *   the port's config seam (`TOGGLE_HOUSE_TRANSFER_ON_SERVER_RESTART`).
 */
class WorldHouseOperations(
    private val gameMap: GameMap? = null,
    private val towns: Towns? = null,
    private val playerByGuid: (Long) -> Player? = { null },
    private val tradeStarter: (Player, Player, Item) -> Boolean = { _, _, _ -> false },
) : HouseOperations {
    override fun getHouse(id: Int): House? = gameMap?.houses?.getHouse(id)

    override fun getTown(townId: Int): Town? = towns?.getTown(townId)

    override fun getPlayerByGuid(guid: Long): Player? = playerByGuid(guid)

    override fun isTransferOnServerRestart(house: House): Boolean =
        house.world.toggleHouseTransferOnServerRestart

    /** Mirrors `Player::sendTextMessage(MESSAGE_EVENT_ADVANCE, message)`. */
    override fun sendTextMessage(player: Player, message: String) {
        player.sendTextMessageTyped(MessageClass.MESSAGE_EVENT_ADVANCE, message)
    }

    override fun getHouseByPlayerId(playerId: Long): House? = gameMap?.houses?.getHouseByPlayerId(playerId)

    override fun startTrade(player: Player, tradePartner: Player, item: Item): Boolean =
        tradeStarter(player, tradePartner, item)

    override fun getTile(position: Position): Tile? = gameMap?.getTile(position)
}

/** `Town` metatable seam backed by the optional [Towns] registry. */
class WorldTownOperations(
    private val towns: Towns? = null,
) : TownOperations {
    override fun getTown(id: Int): Town? = towns?.getTown(id)

    override fun getTown(name: String): Town? = towns?.getTown(name)
}

/**
 * `Teleport` metatable seam. The port does not model Canary's script environment UID
 * registry, so [getItemByUid] defaults to the identity resolver `null` (documented
 * PARITY GAP); callers inject the resolver that matches their script environment.
 */
class WorldTeleportOperations(
    private val teleportByUid: (Int) -> Teleport? = { null },
) : TeleportOperations {
    override fun getItemByUid(uid: Int): Teleport? = teleportByUid(uid)
}

/** `Imbuement` metatable seam backed by the optional [Imbuements] registry. */
class WorldImbuementOperations(
    private val imbuements: Imbuements? = null,
) : ImbuementOperations {
    override fun getImbuement(id: Int): Imbuement? = imbuements?.getImbuement(id)

    override fun getBase(imbuement: Imbuement): BaseImbuement? =
        imbuements?.getBaseByID(imbuement.baseId)

    override fun getCategory(imbuement: Imbuement): CategoryImbuement? =
        imbuements?.getCategoryByID(imbuement.category)
}

/**
 * [ModalWindowOperations] over the target player's modal-window host, resolved
 * by the composition root once the world exists ([WorldLuaOperations.attachModalWindows]).
 *
 * Mirrors `ModalWindowFunctions::luaModalWindowSendToPlayer`
 * (src/lua/functions/core/game/modal_window_functions.cpp:234-252): the
 * `Player::hasModalWindowOpen` gate skips an already-open window
 * (player.cpp:8314-8316) and `Player::sendModalWindow` (player.cpp:9021-9028)
 * emits through the session host. The C++ entry point pushes `true`
 * unconditionally; a player without a resolved host drops the send like a
 * `Player::client == nullptr` session.
 */
private class SessionModalWindowOperations(
    private val managerOf: (Player) -> ModalWindowManager?,
) : ModalWindowOperations {

    override fun sendToPlayer(player: Player, window: ModalWindow): Boolean {
        val manager = managerOf(player)
        if (manager != null && !manager.hasModalWindowOpen(player, window.id)) {
            manager.sendModalWindow(player, window)
        }
        return true
    }
}

/**
 * Production [CombatEffects] over the live world [Game]: the `g_game()` fanout
 * of the Lua combat dispatch (`Combat::combatTileEffects` and
 * `Combat::postCombatEffects`, src/creatures/combat/combat.cpp:1161-1275).
 *
 * The seam views map back through the [LuaCombatActor]/[LuaCombatTile]
 * adapters the executor builds ([LuaCombatWorld]), so the C++
 * `std::shared_ptr<Creature>`/`std::shared_ptr<Tile>` are recovered for the
 * `Game::addMagicEffect`/`Game::addDistanceEffect`/`Game::internalAddItem`
 * calls. Fields created here mirror `Item::CreateItem(itemId)`, the
 * `item->setOwner(caster)` stamp and `Item::startDecaying`'s duration half
 * (combat.cpp:1233-1247).
 *
 * PARITY GAPS (recorded, not invented):
 *  - `Combat::sendCombatEffect`'s Monk elemental-bond effect swap
 *    (combat.cpp:1143-1152) stays with the weapons subsystem (the
 *    [CombatEffects.addMagicEffect] contract records the same gap);
 *  - `ExpertPvp::attachFieldContext(item, caster)` (combat.cpp:1239-1241) is
 *    not modelled on the port's `ExpertPvp`;
 *  - `Item::startDecaying`'s `g_decay()` scheduling is not composed at the
 *    root yet, so only the default duration is applied.
 */
class GameCombatEffects(
    private val game: Game,
    /** `Item::items` catalog backing `Item::CreateItem` of the field item. */
    private val itemTypes: ItemTypeRegistry? = null,
) : CombatEffects {

    /** `g_game().addMagicEffect(position, effect, caster)` (combat.cpp:1254). */
    override fun addMagicEffect(caster: CombatActor?, position: Position, effect: Int) {
        game.addMagicEffect(position, effect, casterCreature(caster))
    }

    /** `g_game().sendDoubleSoundEffect(position, cast, impact, caster)` (combat.cpp:1260). */
    override fun sendDoubleSoundEffect(
        position: Position,
        castEffect: Int,
        impactEffect: Int,
        caster: CombatActor?,
    ) {
        game.sendDoubleSoundEffect(
            position,
            soundEffectOf(castEffect),
            soundEffectOf(impactEffect),
            casterCreature(caster),
        )
    }

    /** `g_game().sendSingleSoundEffect(position, effect, caster)` (combat.cpp:1262). */
    override fun sendSingleSoundEffect(position: Position, effect: Int, caster: CombatActor?) {
        game.sendSingleSoundEffect(position, soundEffectOf(effect), casterCreature(caster))
    }

    /** `Combat::addDistanceEffect(caster, fromPos, toPos, effect)` (combat.cpp:1265-1275). */
    override fun addDistanceEffect(caster: CombatActor, fromPos: Position, toPos: Position, effect: Int) {
        game.addDistanceEffect(fromPos, toPos, effect, casterCreature(caster))
    }

    /** `Combat::combatTileEffects`'s field-item branch (combat.cpp:1233-1247). */
    override fun createFieldItem(caster: CombatActor?, tile: CombatTile, itemId: Int) {
        val targetTile = (tile as? LuaCombatTile)?.tile ?: return
        val item = Item.create(itemId, registry = itemTypes)
        casterCreature(caster)?.let { item.setOwner(it.id) }
        val ret = game.internalAddItem(targetTile, item, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
        if (ret == ReturnValue.NOERROR) {
            item.setDefaultDuration()
        }
    }

    /** The C++ creature behind the executor's actor view; null for an absent caster. */
    private fun casterCreature(caster: CombatActor?): Creature? = (caster as? LuaCombatActor)?.creature

    /** `SoundEffect_t` value -> enum; an unknown value degrades to `SILENCE`. */
    private fun soundEffectOf(value: Int): SoundEffect =
        SoundEffect.entries.firstOrNull { it.value == value } ?: SoundEffect.SILENCE
}
