package org.opentibia.canary.lua.runtime

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.lua.bindings.core.ContainerFunctions
import org.opentibia.canary.lua.bindings.core.ContainerOperations
import org.opentibia.canary.lua.bindings.core.CreatureFunctions
import org.opentibia.canary.lua.bindings.core.CreatureOperations
import org.opentibia.canary.lua.bindings.core.GameFunctions
import org.opentibia.canary.lua.bindings.core.GameOperations
import org.opentibia.canary.lua.bindings.core.ItemFunctions
import org.opentibia.canary.lua.bindings.core.ItemOperations
import org.opentibia.canary.lua.bindings.core.LuaTextMessage
import org.opentibia.canary.lua.bindings.core.PlayerFunctions
import org.opentibia.canary.lua.bindings.core.PlayerOperations
import org.opentibia.canary.lua.bindings.core.PositionFunctions
import org.opentibia.canary.lua.bindings.core.PositionOperations
import org.opentibia.canary.map.Position
import kotlin.math.abs
import kotlin.math.max

/**
 * Real, non-no-op implementations of the Lua binding seams in
 * `org.opentibia.canary.lua.bindings.core`, backed directly by the Kotlin domain
 * objects (`Player`, `Creature`, `Item`, `Container`, `Position`).
 *
 * Mirrors the C++ primitives registered by Canary's
 * `src/lua/functions/creatures/player/player_functions.cpp`,
 * `creatures/creature_functions.cpp`, `items/item_functions.cpp`,
 * `items/container_functions.cpp`, `map/position_functions.cpp` and
 * `core/game/game_functions.cpp`. Capabilities the Kotlin domain does not model yet
 * (inventories, vocations, item attributes/action ids, decay timers, magic-effect
 * broadcasts, name catalogs) degrade to a documented no-op rather than inventing state.
 *
 * The six operation groups are exposed as separate values because `CreatureOperations`
 * and `ItemOperations` cannot be implemented by one class (their `getId` return types
 * differ, `Long` vs `Int`). [install] wires all six into a [LuaEnvironment] through the
 * existing `*Functions.install(env, ops)` overloads; it never mutates `LuaEnvironment`
 * itself.
 *
 * @param itemTypes optional item-type catalog used by `Game.createItem` to validate the
 *   id and build the right [Item]/[Container] subtype.
 * @param itemIdsByName optional `name -> id` catalog for the `itemId or name` argument
 *   shared by `Game.createItem`, `player:addItem`, `item:transform`, etc.
 * @param itemAttributeIdsByName optional `attribute name -> id` catalog mirroring
 *   `ItemFunctions::luaItemGetAttribute`/`setAttribute`.
 */
class WorldLuaOperations(
    itemTypes: ItemTypeRegistry? = null,
    itemIdsByName: Map<String, Int> = emptyMap(),
    itemAttributeIdsByName: Map<String, Int> = emptyMap(),
) {
    val game: GameOperations = WorldGameOperations(itemIdsByName, itemTypes)

    val player: PlayerOperations = WorldPlayerOperations(itemIdsByName)

    val creature: CreatureOperations = WorldCreatureOperations()

    val item: ItemOperations = WorldItemOperations(itemIdsByName, itemAttributeIdsByName)

    val container: ContainerOperations = WorldContainerOperations(itemIdsByName)

    val position: PositionOperations = WorldPositionOperations()

    /**
     * Registers every real seam on [env], replacing the inert `NO_OP_*` placeholders
     * installed by `LuaEnvironment.initState()`. Safe to call after `initState()`.
     */
    fun install(env: LuaEnvironment) {
        GameFunctions.install(env, game)
        PlayerFunctions.install(env, player)
        CreatureFunctions.install(env, creature)
        ItemFunctions.install(env, item)
        ContainerFunctions.install(env, container)
        PositionFunctions.install(env, position)
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
    ): List<Any> = emptyList()

    override fun removeItem(
        player: Any,
        itemId: Int,
        count: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Boolean = false

    override fun getItemCount(
        player: Any,
        itemId: Int,
        subType: Int,
        ignoreEquipped: Boolean,
        ignoreStoreInbox: Boolean,
    ): Long = 0L

    override fun addSkillTries(player: Any, skillType: Int, tries: Long): Boolean = false

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
 * `Item` metatable family seam backed by the Kotlin [Item] domain object.
 *
 * Parity gaps: the Kotlin `Item` models only `id`, `count` and its optional [ItemType];
 * it has no action/unique id, attribute bag, decay timer, or mutable id. `transform`
 * therefore reproduces only Canary's identity no-op branch, and `decay`, action-id and
 * attribute methods return their failure/no-op values.
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

    override fun getActionId(item: Any): Int = 0

    override fun setActionId(item: Any, actionId: Int): Boolean = false

    override fun getUniqueId(item: Any): Long = 0L

    override fun getAttribute(item: Any, attribute: Int): Any? = null

    override fun setAttribute(item: Any, attribute: Int, value: Any?): Boolean = false
}

/**
 * `Container` metatable seam backed by the Kotlin [Container] domain object. Read-only
 * queries mirror Canary's `Container` semantics, including recursive descent.
 */
class WorldContainerOperations(
    private val itemIdsByName: Map<String, Int> = emptyMap(),
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

    private fun subtype(item: Item): Int =
        if (item.type?.isStackable == true) item.count else 0

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
 * `PositionFunctions::luaPositionGetDistance` max-of-|dx|,|dy|,|dz| formula; magic-effect
 * dispatch is a no-op because there is no spectator/protocol sink wired yet.
 */
class WorldPositionOperations : PositionOperations {
    override fun getDistance(position: Position, target: Position): Int =
        max(max(abs(position.x - target.x), abs(position.y - target.y)), abs(position.z - target.z))

    override fun sendMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean = false

    override fun removeMagicEffect(position: Position, magicEffect: Int, spectator: Any?): Boolean = false
}
