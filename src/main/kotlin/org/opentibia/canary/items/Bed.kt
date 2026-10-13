package org.opentibia.canary.items

import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.movement.getNextPosition
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.house.AccessHouseLevel
import java.lang.ref.WeakReference

/** `CONST_ME_POFF` (src/utils/utils_definitions.hpp:63). */
internal const val CONST_ME_POFF = 3

/** `CONST_ME_SLEEP` (src/utils/utils_definitions.hpp:93). */
internal const val CONST_ME_SLEEP = 33

/** `BedItemPart_t` (src/utils/utils_definitions.hpp:764-768). */
enum class BedItemPart(val value: Int) {
    BED_NONE_PART(0),
    BED_PILLOW_PART(1),
    BED_BLANKET_PART(2),
}

/**
 * Injected view of the `g_game()` / `g_dispatcher()` / `g_configManager()` /
 * `g_saveManager()` / `IOLoginData` calls the `BedItem` lifecycle makes in
 * src/items/bed.cpp:26-312. Every member transcribes one C++ call so the sleep
 * and regeneration logic stays testable without a live world.
 */
interface BedWorld {
    /** `Game::getPlayerByID(playerId)` (bed.cpp:180-189). */
    fun getPlayerById(playerId: Long): Player?

    /** `Map::getTile` + `Tile::getBedItem` (bed.cpp:90-94). */
    fun getBedItem(position: Position): BedItem?

    /** `Item::getPosition` (bed.cpp:88, 90). */
    fun getPosition(bed: BedItem): Position?

    /** `Item::items[id]` (bed.cpp:287-294). */
    fun getItemType(id: Int): ItemType?

    /** `time(nullptr)` (bed.cpp:258, 304). */
    fun timeNow(): Long

    /** `IOLoginData::loadPlayerById` (bed.cpp:125-127, 226-230). */
    fun loadPlayerById(guid: Long): Player?

    /** `g_saveManager().savePlayer(player)` (bed.cpp:230). */
    fun savePlayer(player: Player)

    /** `g_game().setBedSleeper(bed, guid)` (bed.cpp:201). */
    fun setBedSleeper(bed: BedItem, guid: Long)

    /** `g_game().removeBedSleeper(guid)` (bed.cpp:28, 238). */
    fun removeBedSleeper(guid: Long)

    /** `g_game().addMagicEffect(pos, effect)` (bed.cpp:162, 204). */
    fun addMagicEffect(position: Position, effect: Int)

    /** `g_dispatcher().addWalkEvent(event, 0, playerId)` (bed.cpp:180-189). */
    fun addWalkEvent(playerId: Long, event: () -> Unit): Boolean

    /** `g_dispatcher().scheduleEvent(SCHEDULER_MINTICKS, logout, ...)`, `false` models event id 0. */
    fun scheduleLogout(playerId: Long): Boolean

    /** `client->logout(false, false)` (bed.cpp:183, 187). */
    fun logoutPlayer(playerId: Long)

    /** `g_game().map.moveCreature(player, self->getTile())` (bed.cpp:181). */
    fun moveCreature(player: Player, bed: BedItem)

    /** `Player::changeMana(amount)` (bed.cpp:276). */
    fun changeMana(player: Player, amount: Int)

    /** `Player::changeSoul(amount)` (bed.cpp:280). */
    fun changeSoul(player: Player, amount: Int)

    /** `g_game().addCreatureHealth(player)` (bed.cpp:233). */
    fun addCreatureHealth(player: Player)

    /** `g_game().transformItem(bed, itemId)` (bed.cpp:289, 294). */
    fun transformItem(bed: BedItem, itemId: Int)

    /** `g_configManager().getFloat(RATE_HEALTH_REGEN)` (bed.cpp:275). */
    val rateHealthRegen: Double

    /** `g_configManager().getFloat(RATE_MANA_REGEN)` (bed.cpp:276). */
    val rateManaRegen: Double
}

/**
 * `BedItem::getNextBedItem` (src/items/bed.cpp:86-95). `bedPartnerDir == null`
 * is Canary's `DIRECTION_NONE`, whose `getNextPosition` default branch keeps
 * the own position (src/utils/tools.cpp:625).
 */
fun BedItem.getNextBedItem(world: BedWorld): BedItem? {
    val directionValue = type?.bedPartnerDir ?: DIRECTION_NONE
    val direction = Direction.entries.firstOrNull { it.byteValue.toInt() == directionValue }
    val position = world.getPosition(this) ?: return null
    val targetPosition = if (direction == null) position else getNextPosition(direction, position)
    return world.getBedItem(targetPosition)
}

/** `BedItem::canUse` (src/items/bed.cpp:97-133). */
fun BedItem.canUse(player: Player?, world: BedWorld): Boolean {
    if (player == null || house == null || !player.isPremium()) {
        return false
    }

    val nextBedItem = getNextBedItem(world) ?: return false
    val itemType = type ?: return false
    if (itemType.bedPart != BedItemPart.BED_PILLOW_PART) {
        return false
    }

    if (!isMovable() || !nextBedItem.isMovable() || !isBedComplete(nextBedItem)) {
        return false
    }

    if (sleeperGUID == 0L) {
        return true
    }

    if (house?.getHouseAccessLevel(player) == AccessHouseLevel.HOUSE_OWNER) {
        return true
    }

    val sleeper = world.loadPlayerById(sleeperGUID) ?: return false
    val sleeperLevel = house?.getHouseAccessLevel(sleeper) ?: return false
    val playerLevel = house?.getHouseAccessLevel(player) ?: return false
    return sleeperLevel <= playerLevel
}

/** `BedItem::trySleep` (src/items/bed.cpp:152-166). */
fun BedItem.trySleep(player: Player, world: BedWorld): Boolean {
    if (house == null || player.isRemoved()) {
        return false
    }

    if (sleeperGUID != 0L) {
        if ((type?.transformToFree ?: 0) != 0 && house?.owner == player.getGUID()) {
            wakeUp(null, world)
        }

        world.addMagicEffect(player.position, CONST_ME_POFF)
        return false
    }
    return true
}

/**
 * `BedItem::sleep` (src/items/bed.cpp:168-214).
 *
 * The deferred walk event follows AGENTS.md's deferred-callback lifetime rule:
 * it captures only the player GUID, the bed id/position and weak references,
 * and re-resolves both through [world] before touching anything. A removed or
 * identity-reused player or bed turns the event into a no-op.
 */
fun BedItem.sleep(player: Player, world: BedWorld): Boolean {
    if (house == null) {
        return false
    }

    if (sleeperGUID != 0L) {
        return false
    }

    val nextBedItem = getNextBedItem(world)
    val bedRef = WeakReference(this)
    val playerRef = WeakReference(player)
    val capturedBedId = id
    val capturedPosition = world.getPosition(this)
    val walkAccepted = world.addWalkEvent(player.getGUID()) {
        val currentBed = bedRef.get() ?: return@addWalkEvent
        val currentPlayer = playerRef.get() ?: return@addWalkEvent
        if (world.getPlayerById(currentPlayer.getGUID()) !== currentPlayer) {
            return@addWalkEvent
        }
        if (currentBed.id != capturedBedId) {
            return@addWalkEvent
        }
        if (capturedPosition != null && world.getPosition(currentBed) != capturedPosition) {
            return@addWalkEvent
        }

        world.moveCreature(currentPlayer, currentBed)
        if (!world.scheduleLogout(currentPlayer.getGUID())) {
            world.logoutPlayer(currentPlayer.getGUID())
        }
    }
    if (!walkAccepted) {
        return false
    }

    internalSetSleeper(player, world)

    if (nextBedItem != null) {
        nextBedItem.internalSetSleeper(player, world)
    }

    world.setBedSleeper(this, player.getGUID())
    world.addMagicEffect(player.position, CONST_ME_SLEEP)

    updateAppearance(player, world)

    if (nextBedItem != null) {
        nextBedItem.updateAppearance(player, world)
    }

    return true
}

/** `BedItem::wakeUp` (src/items/bed.cpp:216-255). */
fun BedItem.wakeUp(player: Player?, world: BedWorld) {
    if (house == null) {
        return
    }
    if (sleeperGUID == 0L) {
        return
    }

    if (player == null) {
        val regenPlayer = world.loadPlayerById(sleeperGUID)
        if (regenPlayer != null) {
            regeneratePlayer(regenPlayer, world)
            world.savePlayer(regenPlayer)
        }
    } else {
        regeneratePlayer(player, world)
        world.addCreatureHealth(player)
    }

    world.removeBedSleeper(sleeperGUID)

    val nextBedItem = getNextBedItem(world)

    internalRemoveSleeper()

    if (nextBedItem != null) {
        nextBedItem.internalRemoveSleeper()
    }

    updateAppearance(null, world)

    if (nextBedItem != null) {
        nextBedItem.updateAppearance(null, world)
    }
}

/** `BedItem::regeneratePlayer` (src/items/bed.cpp:257-281). */
internal fun BedItem.regeneratePlayer(player: Player, world: BedWorld) {
    val sleptTime = (world.timeNow() - sleepStart).coerceAtLeast(0L)

    val condition = player.getCondition(ConditionType.CONDITION_REGENERATION, ConditionId.CONDITIONID_DEFAULT)
    if (condition != null) {
        val regen: Long
        if (condition.ticks != -1) {
            regen = minOf((condition.ticks / 1_000).toLong(), sleptTime) / 30
            val newRegenTicks = condition.ticks - (regen * 30_000L).toInt()
            if (newRegenTicks <= 0) {
                player.removeCondition(condition)
            } else {
                condition.setTicks(newRegenTicks)
            }
        } else {
            regen = sleptTime / 30
        }

        player.changeHealth((regen * world.rateHealthRegen).toInt(), false)
        world.changeMana(player, (regen * world.rateManaRegen).toInt())
    }

    val soulRegen = (sleptTime / (60 * 15)).toInt()
    world.changeSoul(player, soulRegen)
}

/** `BedItem::updateAppearance` (src/items/bed.cpp:283-298). */
internal fun BedItem.updateAppearance(player: Player?, world: BedWorld) {
    val itemType = type ?: return
    if (!itemType.isBed) {
        return
    }

    val sexIndex = player?.getSex()?.value ?: 0
    val onUseId = itemType.transformToOnUse.getOrElse(sexIndex) { 0 }
    if (player != null && onUseId != 0) {
        if (world.getItemType(onUseId)?.isBed == true) {
            world.transformItem(this, onUseId)
        }
    } else if (itemType.transformToFree != 0) {
        if (world.getItemType(itemType.transformToFree)?.isBed == true) {
            world.transformItem(this, itemType.transformToFree)
        }
    }
}

/** `BedItem::internalSetSleeper` (src/items/bed.cpp:300-306). */
internal fun BedItem.internalSetSleeper(player: Player, world: BedWorld) {
    sleeperGUID = player.getGUID()
    sleepStart = world.timeNow()
    setAttribute(ItemAttribute.DESCRIPTION, "${player.name} is sleeping there.")
}

/** `BedItem::internalRemoveSleeper` (src/items/bed.cpp:308-312). */
internal fun BedItem.internalRemoveSleeper() {
    sleeperGUID = 0
    sleepStart = 0
    setAttribute(ItemAttribute.DESCRIPTION, "Nobody is sleeping there.")
}

/** `BedItem::forceClearSleeper` (src/items/bed.cpp:26-36). */
internal fun BedItem.forceClearSleeper(world: BedWorld) {
    if (sleeperGUID != 0L) {
        world.removeBedSleeper(sleeperGUID)
    }

    internalRemoveSleeper()

    updateAppearance(null, world)
}
