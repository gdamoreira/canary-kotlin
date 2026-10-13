package org.opentibia.canary.lua.runtime

import org.opentibia.canary.creatures.ChaseMode
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.spawns.MonsterType
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.lua.bindings.LootData
import org.opentibia.canary.lua.bindings.LootFunctions
import org.opentibia.canary.lua.bindings.LootOperations
import org.opentibia.canary.lua.bindings.MonsterAttackInfo
import org.opentibia.canary.lua.bindings.MonsterFunctions
import org.opentibia.canary.lua.bindings.MonsterOperations
import org.opentibia.canary.lua.bindings.MonsterTypeFunctions
import org.opentibia.canary.lua.bindings.MonsterTypeOperations
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
import org.opentibia.canary.lua.bindings.core.LuaTextMessage
import org.opentibia.canary.lua.bindings.core.PlayerFunctions
import org.opentibia.canary.lua.bindings.core.PlayerOperations
import org.opentibia.canary.lua.bindings.core.PlayerSocialFunctions
import org.opentibia.canary.lua.bindings.core.PositionFunctions
import org.opentibia.canary.lua.bindings.core.PositionOperations
import org.opentibia.canary.lua.bindings.core.TileFunctions
import org.opentibia.canary.lua.bindings.core.TileOperations
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.rewardchest.Reward
import java.util.WeakHashMap
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

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
 * @param gameMap optional spatial grid backing `Tile(...)` and `Position:getTile`; when
 *   absent those primitives return `nil` like Canary's `g_game().map.getTile` miss.
 */
class WorldLuaOperations(
    itemTypes: ItemTypeRegistry? = null,
    itemIdsByName: Map<String, Int> = emptyMap(),
    itemAttributeIdsByName: Map<String, Int> = emptyMap(),
    monsterTypesByName: Map<String, Any> = emptyMap(),
    monsterTypesByRaceId: Map<Int, Any> = emptyMap(),
    gameMap: GameMap? = null,
) {
    val game: GameOperations = WorldGameOperations(itemIdsByName, itemTypes)

    val player: PlayerOperations = WorldPlayerOperations(itemIdsByName)

    val creature: CreatureOperations = WorldCreatureOperations()

    val item: ItemOperations = WorldItemOperations(itemIdsByName, itemAttributeIdsByName)

    val itemType: ItemTypeOperations = WorldItemTypeOperations(itemTypes, itemIdsByName)

    val container: ContainerOperations = WorldContainerOperations(itemIdsByName, itemTypes)

    val tile: TileOperations = WorldTileOperations(gameMap, itemTypes, itemIdsByName)

    val position: PositionOperations = WorldPositionOperations(gameMap)

    val monster: MonsterOperations = WorldMonsterOperations(monsterTypesByName)

    val monsterType: MonsterTypeOperations = WorldMonsterTypeOperations(monsterTypesByName, monsterTypesByRaceId)

    val loot: LootOperations = WorldLootOperations(itemIdsByName)

    /**
     * Registers every real seam on [env], replacing the inert `NO_OP_*` placeholders
     * installed by `LuaEnvironment.initState()`. Safe to call after `initState()`.
     */
    fun install(env: LuaEnvironment) {
        GameFunctions.install(env, game)
        PlayerFunctions.install(env, player)
        PlayerSocialFunctions.install(env) // Wave 39 lua-player-2
        CreatureFunctions.install(env, creature)
        ItemFunctions.install(env, item)
        ItemTypeFunctions.install(env, itemType)
        ContainerFunctions.install(env, container)
        TileFunctions.install(env, tile)
        PositionFunctions.install(env, position)
        MonsterFunctions.install(env, monster)
        MonsterTypeFunctions.install(env, monsterType)
        LootFunctions.install(env, loot)
    }

    companion object {
        /** Convenience factory that installs the real seams on [env] immediately. */
        fun forEnvironment(env: LuaEnvironment): WorldLuaOperations =
            WorldLuaOperations().also { it.install(env) }
    }
}

/**
 * `Game` global seam backed by `Game.createItem`.
 *
 * Parity gap: Canary splits a stack over `ceil(count / stackSize)` items using
 * `ItemType::stackSize`; the Kotlin [org.opentibia.canary.items.ItemType] does not carry
 * `stackSize`, so a single item with the requested count is produced here.
 */
class WorldGameOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    private val itemTypes: ItemTypeRegistry? = null,
) : GameOperations {
    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

    override fun createItem(itemId: Int, count: Int): List<Any> {
        if (itemTypes != null && itemTypes.get(itemId) == null) {
            return emptyList()
        }
        return listOf(Item.create(itemId, count, itemTypes))
    }

    /** Mirrors `Player::getExpForLevel`, exposed through `Game.getExperienceForLevel`. */
    override fun getExperienceForLevel(level: Int): Long = Player.getExpForLevel(level)
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

    /** Mirrors Canary's `skillType <= SKILL_LAST` guard, also rejecting the negative sentinel. */
    private fun validSkill(skillType: Int): skills_t? =
        skills_t.fromValue(skillType)?.takeIf { it.value in skills_t.FIRST..skills_t.LAST }
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
 * action/unique ids, container status, charges/fluid type and Canary-hundredths weight
 * delegate to the domain; parent/tile links, cloning, splitting, custom attribute bags and
 * world removal are still absent and inherit the seam's documented defaults.
 *
 * Parity gaps: `transform` reproduces only Canary's identity no-op branch because
 * [Item.id] is immutable, `decay` is a no-op, and `setAttribute` does not reproduce
 * Canary's `DURATION`/`DECAYSTATE` decay scheduling beyond rejecting the protected
 * `duration timestamp` attribute.
 */
class WorldItemOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
    private val itemAttributeIdsByName: Map<String, Int> = emptyMap(),
) : ItemOperations {
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
 * optional [ItemTypeRegistry] and name catalog for `ItemType(id or name)`. Fields the
 * Kotlin type does not model inherit the documented Canary `ItemType` struct defaults
 * from [ItemTypeOperations].
 *
 * Parity gaps: `create` resolves only through the supplied registry/catalog (Canary's
 * global `Item::items` catalog is not wired into this class), and `getWeight` converts
 * the domain's ounces to Canary's hundredths.
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

    override fun getId(itemType: Any): Int = (itemType as? ItemType)?.id ?: 0

    override fun getName(itemType: Any): String = (itemType as? ItemType)?.name ?: ""

    override fun isContainer(itemType: Any): Boolean = (itemType as? ItemType)?.isContainer ?: false

    override fun isStackable(itemType: Any): Boolean = (itemType as? ItemType)?.isStackable ?: false

    override fun isPickupable(itemType: Any): Boolean = (itemType as? ItemType)?.isPickupable ?: false

    override fun getCapacity(itemType: Any): Int = (itemType as? ItemType)?.capacity ?: 0

    /** Canary reports hundredths of an ounce; the domain stores ounces. */
    override fun getWeight(itemType: Any, count: Int): Int =
        (((itemType as? ItemType)?.weight ?: 0.0) * 100.0 * max(1, count)).roundToInt()

    override fun hasSubType(itemType: Any): Boolean = isStackable(itemType)
}

/**
 * `Tile` metatable seam backed by the Kotlin [Tile] domain object (ground plus item
 * list) and the optional [GameMap] for `Tile(x, y, z)`/`Position:getTile`.
 *
 * Parity gaps: the Kotlin tile models no creatures, top/down item ordering, tile flags,
 * `ItemProperty` data, magic-field classification, house link or reward sweep; those
 * methods inherit the documented defaults from [TileOperations]. `addItem` does not cap
 * the count by `ItemType::stackSize` (not modeled), `addItemEx` treats every item as
 * parentless, and `queryAdd` allows any item because the domain has no tile query rules.
 */
class WorldTileOperations(
    private val gameMap: GameMap? = null,
    private val itemTypes: ItemTypeRegistry? = null,
    private val itemIdsByName: Map<String, Int> = emptyMap(),
) : TileOperations {
    override fun itemIdByName(name: String): Int = itemIdsByName[name] ?: 0

    override fun create(position: Position): Any? = gameMap?.getTile(position)

    override fun getPosition(tile: Any): Position? = (tile as? Tile)?.position

    override fun getGround(tile: Any): Any? = (tile as? Tile)?.ground

    /** 0 is the ground item, then the tile item list in order. */
    override fun getThing(tile: Any, index: Int): Any? {
        val target = tile as? Tile ?: return null
        if (index < 0) {
            return null
        }
        if (index == 0 && target.ground != null) {
            return target.ground
        }
        val offset = if (target.ground != null) 1 else 0
        return target.items.getOrNull(index - offset)
    }

    override fun getThingCount(tile: Any): Int {
        val target = tile as? Tile ?: return 0
        return target.items.size + if (target.ground != null) 1 else 0
    }

    override fun getItems(tile: Any): List<Any> = (tile as? Tile)?.items?.toList() ?: emptyList()

    override fun getItemCount(tile: Any): Int = (tile as? Tile)?.items?.size ?: 0

    /** Mirrors `Tile::getItemTypeCount` (ground first, then the item list). */
    override fun getItemCountById(tile: Any, itemId: Int, subType: Int): Int {
        val target = tile as? Tile ?: return 0
        var count = 0
        val ground = target.ground
        if (ground != null && ground.id == itemId && (subType == -1 || subType == subtype(ground))) {
            count += ground.count
        }
        for (item in target.items) {
            if (item.id == itemId && (subType == -1 || subType == subtype(item))) {
                count += item.count
            }
        }
        return count
    }

    /** Mirrors `Game::findItemOfType` on a tile (ground first, then the item list). */
    override fun getItemById(tile: Any, itemId: Int, subType: Int): Any? {
        val target = tile as? Tile ?: return null
        val ground = target.ground
        if (ground != null && ground.id == itemId && (subType == -1 || subType == subtype(ground))) {
            return ground
        }
        return target.items.firstOrNull { it.id == itemId && (subType == -1 || subType == subtype(it)) }
    }

    override fun addItem(tile: Any, itemId: Int, count: Int, flags: Long): Any? {
        val target = tile as? Tile ?: return null
        val item = Item.create(itemId, count.coerceAtLeast(1), itemTypes)
        target.addItem(item)
        return item
    }

    override fun addItemEx(tile: Any, item: Any, flags: Long): Int {
        val target = tile as? Tile ?: return ReturnValue.NOTPOSSIBLE.ordinal
        val candidate = item as? Item ?: return ReturnValue.NOTPOSSIBLE.ordinal
        if (target.ground === candidate || target.items.any { it === candidate }) {
            return ReturnValue.NOTPOSSIBLE.ordinal
        }
        target.addItem(candidate)
        return ReturnValue.NOERROR.ordinal
    }

    /** Mirrors `Tile::queryAdd` for the domain, which accepts any item on any tile. */
    override fun queryAdd(tile: Any, thing: Any, flags: Long): Int =
        if (tile is Tile && thing is Item) ReturnValue.NOERROR.ordinal else ReturnValue.NOTPOSSIBLE.ordinal

    /** Mirrors `Tile::getThingIndex` for the modeled ground/item order. */
    override fun getThingIndex(tile: Any, thing: Any): Int? {
        val target = tile as? Tile ?: return null
        if (target.ground === thing) {
            return 0
        }
        val index = target.items.indexOfFirst { it === thing }
        if (index < 0) {
            return -1
        }
        return index + if (target.ground != null) 1 else 0
    }

    private fun subtype(item: Item): Int =
        if (item.type?.isStackable == true) item.count else 0
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
) : MonsterOperations {
    private val idleStatus = mutableMapOf<Long, Boolean>()
    private val friendList = mutableMapOf<Long, MutableList<Any>>()
    private val targetList = mutableMapOf<Long, MutableList<Any>>()
    private val attackedTargets = mutableMapOf<Long, Any>()
    private val masters = mutableMapOf<Long, Any>()
    private val challenged = mutableMapOf<Long, Boolean>()

    private fun creatureKey(value: Any): Long? = (value as? Creature)?.id

    override fun isMonster(monster: Any): Boolean = monster is Monster

    override fun getType(monster: Any): Any? {
        val target = monster as? Monster ?: return null
        return monsterTypesByName[target.name]
    }

    override fun getName(monster: Any): String? = (monster as? Creature)?.name

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
