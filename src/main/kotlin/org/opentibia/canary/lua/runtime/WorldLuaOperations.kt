package org.opentibia.canary.lua.runtime

import org.opentibia.canary.combat.condition.CombatType
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.AreaCombat
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.monsters.RespawnType
import org.opentibia.canary.creatures.monsters.MonsterType as FullMonsterType
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.creatures.spawns.MonsterType
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
import org.opentibia.canary.lua.bindings.MonsterAttackInfo
import org.opentibia.canary.lua.bindings.MonsterFunctions
import org.opentibia.canary.lua.bindings.MonsterOperations
import org.opentibia.canary.lua.bindings.MonsterSpellFunctions
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
import org.opentibia.canary.lua.bindings.library.KvFunctions
import org.opentibia.canary.lua.bindings.library.LoggerFunctions
import org.opentibia.canary.lua.bindings.library.LuaEnums
import org.opentibia.canary.lua.bindings.library.LuaScriptResults
import org.opentibia.canary.lua.bindings.library.MetricsFunctions
import org.opentibia.canary.lua.bindings.library.ModalWindowFunctions
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
 */
class WorldLuaOperations(
    private val itemTypes: ItemTypeRegistry? = null,
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    itemAttributeIdsByName: Map<String, Int> = emptyMap(),
    monsterTypesByName: Map<String, Any> = emptyMap(),
    monsterTypesByRaceId: Map<Int, Any> = emptyMap(),
    npcTypesByName: Map<String, Any> = emptyMap(),
    gameMap: GameMap? = null,
    itemClassifications: ItemClassifications? = null,
    towns: Towns? = null,
    imbuements: Imbuements? = null,
    teleportByUid: (Int) -> Teleport? = { null },
    playerByGuid: (Long) -> Player? = { null },
    startTrade: (Player, Player, Item) -> Boolean = { _, _, _ -> false },
    private val kv: KV = KVMemory(),
) {
    private val worldGame: WorldGameOperations =
        WorldGameOperations(itemIdsByName, itemTypes, gameMap, monsterTypesByName, itemClassifications)

    val game: GameOperations = worldGame

    val player: PlayerOperations = WorldPlayerOperations(itemIdsByName)

    val creature: CreatureOperations = WorldCreatureOperations()

    val item: ItemOperations = WorldItemOperations(itemIdsByName, itemAttributeIdsByName)

    val itemType: ItemTypeOperations = WorldItemTypeOperations(itemTypes, itemIdsByName)

    val container: ContainerOperations = WorldContainerOperations(itemIdsByName, itemTypes)

    val tile: TileOperations = WorldTileOperations(gameMap, itemTypes, itemIdsByName)

    val position: PositionOperations = WorldPositionOperations(gameMap)

    val monster: MonsterOperations = WorldMonsterOperations(monsterTypesByName, monsterTypesByRaceId)

    val monsterType: MonsterTypeOperations = WorldMonsterTypeOperations(monsterTypesByName, monsterTypesByRaceId)

    val loot: LootOperations = WorldLootOperations(itemIdsByName)

    val npc: NpcOperations = WorldNpcOperations(itemTypes)

    val npcType: NpcTypeOperations = WorldNpcTypeOperations(npcTypesByName, itemTypes)
    val weapon: WeaponOperations = WorldWeaponOperations(itemTypes)
    val spell: SpellOperations = WorldSpellOperations(itemTypes)
    val combat: CombatOperations = WorldCombatOperations()
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
        GameFunctions.install(env, game)
        PlayerFunctions.install(env, player)
        PlayerSocialFunctions.install(env) // Wave 39 lua-player-2
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
        MonsterSpellFunctions.install(env)
        CharmFunctions.install(env)
        ShopFunctions.install(env, WorldShopOperations(itemIdsByName))
        // Wave 40 lua-weapon-spell
        // SpellBindings is the canonical `Spell` owner: it creates the registry that
        // `LuaEventDispatcher` reads, and delegates the binding surface to SpellFunctions.
        env.installSpells(SpellBindings.install(env, spell))
        WeaponFunctions.install(env, weapon)
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
        GlobalFunctions.install(env)
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
 * documented empty defaults. `Game.createMonster`/`createNpc` and the soul-pit/generated
 * npc variants need the spawn engine, and `loadMap`/`loadMapChunk` need the dispatcher;
 * all inherit their seam defaults. The `Game.createItem` split over
 * `ceil(count / stackSize)` items is reproduced through the item-type `stackSize`.
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
    override fun getMonsterTypeByName(name: String): Any? = monsterTypesByName[name]

    /** Mirrors `g_monsters().monsters`; empty when no monster catalog is wired. */
    override fun getMonsterTypes(): Map<String, Any> = monsterTypesByName

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
 * - storage keys in Canary's reserved ranges are rejected by `setStorageValue`; only the
 *   `key == 0` rejection is reproduced here.
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
) : PlayerOperations {
    /** Per-player storage keyed by `Player::getGUID()`, returning -1 when unset. */
    private val storage = mutableMapOf<Long, MutableMap<Int, Int>>()

    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

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
        val item = org.opentibia.canary.items.Item(itemId, count.coerceAtLeast(1))
        val result = target.addItem(item)
        return if (result == org.opentibia.canary.systems.rewardchest.ReturnValue.NOERROR) listOf(item) else emptyList()
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
        return storage[target.id]?.get(key) ?: -1
    }

    override fun setStorageValue(player: Any, key: Int, value: Int): Boolean {
        if (key == 0) {
            return false
        }
        val target = player as? Player ?: return false
        storage.getOrPut(target.id) { mutableMapOf() }[key] = value
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
     * `canDropOnMap = true` branch routes through `Game::internalPlayerAddItem`. The
     * `canDropOnMap = false` branch needs `Game::internalAddItem` container-index
     * routing (unported) and returns `RETURNVALUE_NOTPOSSIBLE` (PARITY GAP).
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
        if (!canDropOnMap) {
            return ReturnValue.NOTPOSSIBLE.ordinal
        }
        val slot = Slots_t.fromValue(indexOrSlot) ?: return ReturnValue.NOTPOSSIBLE.ordinal
        return target.addItem(candidate, slot).ordinal
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
 * creature, master, challenge flag). The per-id state maps follow the existing
 * [WorldPlayerOperations] storage convention and live as long as this operations object.
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
        typeOverrides[monster.id] ?: monsterTypesByName[monster.name]

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
 * `MonsterType` metatable seam backed by the spawn-engine [MonsterType] plus the
 * mutable attribute bag Canary stores in `MonsterType::MonsterInfo`. The bag lives in a
 * [WeakHashMap] keyed by the type identity, so script-created attributes are released
 * with the type.
 *
 * Defaults mirror `MonsterInfo`'s C++ defaults (health/healthMax 100, baseSpeed 110,
 * race `RACE_BLOOD`, attackable/hostile true, summonable/convinceable/blockable false),
 * and `blockable` is seeded from the domain type's `isBlockable` flag. The domain's
 * `canSpawn` callback is used for `canSpawn`.
 */
class WorldMonsterTypeOperations(
    private val typesByName: Map<String, Any> = emptyMap(),
    private val typesByRaceId: Map<Int, Any> = emptyMap(),
) : MonsterTypeOperations {
    private class Info {
        var nameOverride: String? = null
        var health = 100
        var maxHealth = 100
        var experience = 0L
        var baseSpeed = 110
        var race = RACE_BLOOD
        var bestiaryClass = ""
        var corpseId = 0
        var attackable = true
        var summonable = false
        var convinceable = false
        var hostile = true
        var blockable = false
        val loot = mutableListOf<LootData>()
        val attacks = mutableListOf<MonsterAttackInfo>()
    }

    private val infos = WeakHashMap<Any, Info>()

    override fun create(nameOrRaceId: Any): Any? = when (nameOrRaceId) {
        is Number -> typesByRaceId[nameOrRaceId.toInt()]
        is String -> typesByName[nameOrRaceId]
        else -> null
    }

    override fun getName(monsterType: Any): String? {
        val domain = monsterType as? MonsterType ?: return null
        return info(domain).nameOverride ?: domain.name
    }

    override fun setName(monsterType: Any, name: String): Boolean? {
        info(monsterType as? MonsterType ?: return null).nameOverride = name
        return true
    }

    override fun getHealth(monsterType: Any): Int? = infoOrNull(monsterType)?.health

    override fun setHealth(monsterType: Any, health: Int): Boolean? {
        info(monsterType as? MonsterType ?: return null).health = health
        return true
    }

    override fun getMaxHealth(monsterType: Any): Int? = infoOrNull(monsterType)?.maxHealth

    override fun setMaxHealth(monsterType: Any, maxHealth: Int): Boolean? {
        info(monsterType as? MonsterType ?: return null).maxHealth = maxHealth
        return true
    }

    override fun getExperience(monsterType: Any): Long? = infoOrNull(monsterType)?.experience

    override fun setExperience(monsterType: Any, experience: Long): Boolean? {
        info(monsterType as? MonsterType ?: return null).experience = experience
        return true
    }

    override fun getBaseSpeed(monsterType: Any): Int? = infoOrNull(monsterType)?.baseSpeed

    override fun setBaseSpeed(monsterType: Any, speed: Int): Boolean? {
        info(monsterType as? MonsterType ?: return null).baseSpeed = speed
        return true
    }

    override fun getRace(monsterType: Any): Int? = infoOrNull(monsterType)?.race

    override fun setRace(monsterType: Any, race: Int): Boolean? {
        info(monsterType as? MonsterType ?: return null).race = race
        return true
    }

    override fun getBestiaryClass(monsterType: Any): String? = infoOrNull(monsterType)?.bestiaryClass

    override fun setBestiaryClass(monsterType: Any, bestiaryClass: String): Boolean? {
        info(monsterType as? MonsterType ?: return null).bestiaryClass = bestiaryClass
        return true
    }

    override fun getCorpseId(monsterType: Any): Int? = infoOrNull(monsterType)?.corpseId

    override fun getTypeName(monsterType: Any): String? = (monsterType as? MonsterType)?.name

    override fun isAttackable(monsterType: Any): Boolean? = infoOrNull(monsterType)?.attackable

    override fun setAttackable(monsterType: Any, value: Boolean): Boolean? {
        info(monsterType as? MonsterType ?: return null).attackable = value
        return true
    }

    override fun isSummonable(monsterType: Any): Boolean? = infoOrNull(monsterType)?.summonable

    override fun setSummonable(monsterType: Any, value: Boolean): Boolean? {
        info(monsterType as? MonsterType ?: return null).summonable = value
        return true
    }

    override fun isConvinceable(monsterType: Any): Boolean? = infoOrNull(monsterType)?.convinceable

    override fun setConvinceable(monsterType: Any, value: Boolean): Boolean? {
        info(monsterType as? MonsterType ?: return null).convinceable = value
        return true
    }

    override fun isHostile(monsterType: Any): Boolean? = infoOrNull(monsterType)?.hostile

    override fun setHostile(monsterType: Any, value: Boolean): Boolean? {
        info(monsterType as? MonsterType ?: return null).hostile = value
        return true
    }

    override fun isBlockable(monsterType: Any): Boolean? = infoOrNull(monsterType)?.blockable

    override fun setBlockable(monsterType: Any, value: Boolean): Boolean? {
        info(monsterType as? MonsterType ?: return null).blockable = value
        return true
    }

    override fun canSpawn(monsterType: Any, position: Position): Boolean? =
        (monsterType as? MonsterType)?.canSpawn(position)

    override fun getLoot(monsterType: Any): List<LootData>? = infoOrNull(monsterType)?.loot?.toList()

    override fun addLoot(monsterType: Any, loot: LootData): Boolean? {
        val info = info(monsterType as? MonsterType ?: return null)
        info.loot += loot.deepCopy()
        return true
    }

    override fun getAttackList(monsterType: Any): List<MonsterAttackInfo>? = infoOrNull(monsterType)?.attacks?.toList()

    private fun infoOrNull(monsterType: Any): Info? = (monsterType as? MonsterType)?.let(::info)

    private fun info(monsterType: MonsterType): Info = infos.getOrPut(monsterType) {
        Info().also { it.blockable = monsterType.isBlockable }
    }

    private companion object {
        const val RACE_BLOOD = 2
    }
}

/** `Loot` seam resolving `loot:setIdFromName` through the item-name catalog. */
class WorldLootOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
) : LootOperations {
    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0
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
 * `Combat` seam. The Kotlin `Combat` port is a stateless damage core, so the
 * world-backed `execute` dispatch is not wired yet.
 *
 * PARITY GAPS: `g_luaEnvironment().getAreaObject(id)` is owned by the (not yet
 * ported) area registry and returns null; `execute` does not run
 * `Combat::doCombat` over the map; the script-id gate returns
 * `EVENT_ID_LOADING`, so `combat:setArea` follows the loading path.
 */
class WorldCombatOperations : CombatOperations {
    override fun getScriptId(): Int = CombatFunctions.EVENT_ID_LOADING

    override fun getAreaObject(id: Int): AreaCombat? = null

    override fun execute(combat: LuaCombat, creature: Any, variant: LuaVariant): Boolean = false
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
