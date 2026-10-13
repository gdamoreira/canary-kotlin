package org.opentibia.canary.game

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.Zone
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.SourceEffect
import org.opentibia.canary.persistence.PlayerStorage
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.lang.ref.WeakReference
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.random.Random

private val logger = KotlinLogging.logger {}

/** `LightState_t` (`src/game/game_definitions.hpp:57-62`). */
enum class LightState {
    LIGHT_STATE_DAY,
    LIGHT_STATE_NIGHT,
    LIGHT_STATE_SUNSET,
    LIGHT_STATE_SUNRISE,
}

/**
 * Faithful facade over the Kotlin world model mirroring Canary's `Game`
 * (src/game/game.hpp:95). Every method transcribes an existing `Game::` member;
 * globals (`g_game()`) are replaced by constructor injection of the [GameMap] and
 * [GameDispatcher] per AGENTS.md 1.1.
 *
 * Implemented surface:
 *  - `internalAddItem` (game.cpp:2693, 2698) / `internalRemoveItem` (game.cpp:2785)
 *  - `internalMoveItem` (game.cpp:2436)
 *  - `internalPlaceCreature` (game.cpp:1495) / `placeCreature` (game.cpp:1524)
 *  - `removeCreature` (game.cpp:1551) / `addCreatureCheck` (game.cpp:7659)
 *  - `removeCreatureCheck` (game.cpp:7687)
 *  - `getPlayers` (game.hpp:577) / `getPlayerByID` (game.cpp:1339)
 *  - `getPlayerByName` (game.cpp:1394)
 *  - `addPlayer` (game.cpp:11624) / `removePlayer` (game.cpp:11631)
 *  - `broadcastMessage` (game.cpp:9526)
 *  - `getWorldLightInfo` (game.cpp:9479) / `gameIsDay` (game.cpp:9483) /
 *    `checkLight` (game.cpp:9402)
 *  - `sendSingleSoundEffect` (game.cpp:7833) / `sendDoubleSoundEffect` (game.cpp:7853)
 *  - `playerStopAutoWalk` (game.cpp:4567)
 *  - `beforeCreatureZoneChange` (game.cpp:12417) / `afterCreatureZoneChange` (game.cpp:12447)
 *
 * All state is thread-confined: mutating methods must run inside the injected
 * [dispatcher]'s single-threaded `WorldCommit` lane (AGENTS.md 1.2).
 */
class Game(
    val map: GameMap,
    val dispatcher: GameDispatcher,
    private val playerStorage: PlayerStorage? = null,
) {
    private val players = mutableMapOf<Long, Player>()

    /** `Creature::getParent()` equivalent (src/creatures/creature.hpp): the tile each placed creature lives on. */
    private val placedCreatures = IdentityHashMap<Creature, Tile>()

    /** `Item::getParent()` (src/items/item.hpp): the tile each item added through this facade lives on. */
    private val itemParents = IdentityHashMap<Item, Tile>()

    /**
     * `Game::checkCreatureLists` (`src/game/game.cpp:78`),
     * `EVENT_CREATURECOUNT` think buckets. Canary stores `std::weak_ptr`
     * observers so a removed creature is not kept alive; the port mirrors that
     * with [WeakReference] until `Game::checkCreatures` prunes the buckets.
     */
    private val checkCreatureLists =
        List(EVENT_CREATURECOUNT) { mutableListOf<WeakReference<Creature>>() }

    /** `Game::isDay` (game.hpp:923). */
    private var isDay: Boolean = false

    /** `Game::lightState` / `Game::currentLightState` (game.hpp:929-930). */
    private var lightState: LightState = LightState.LIGHT_STATE_DAY
    private var currentLightState: LightState = lightState

    /** `Game::lightLevel` (game.hpp:931). */
    private var lightLevel: Int = LIGHT_LEVEL_DAY

    /** `Game::lightHour` (game.hpp:932). */
    private var lightHour: Int = SUNRISE + (SUNSET - SUNRISE) / 2

    /** `Game::lightHourDelta` (game.hpp:934). */
    private val lightHourDelta: Int = (LIGHT_DAY_LENGTH * (EVENT_LIGHTINTERVAL_MS / 1000)) / DAY_LENGTH_SECONDS

    /**
     * `Tile::getZones()` seam (`src/items/tile.hpp`) consumed by the
     * `internalPlaceCreature`/`removeCreature` zone transitions. The Kotlin
     * [Tile] does not index zones yet, so the default is empty and a host wires
     * the [org.opentibia.canary.game.zones.Zones] registry here.
     */
    var tileZones: (Position) -> Set<Zone> = { emptySet() }

    /**
     * `g_callbacks().checkCallback(EventCallback_t::zoneBeforeCreatureLeave)`
     * seam (`src/game/game.cpp:12432-12435`). Returns `true` when the creature
     * may leave.
     */
    var checkZoneBeforeCreatureLeave: (Zone, Creature) -> Boolean = { _, _ -> true }

    /**
     * `g_callbacks().checkCallback(EventCallback_t::zoneBeforeCreatureEnter)`
     * seam (`src/game/game.cpp:12438-12443`). Returns `true` when the creature
     * may enter.
     */
    var checkZoneBeforeCreatureEnter: (Zone, Creature) -> Boolean = { _, _ -> true }

    /** `g_callbacks().executeCallback(EventCallback_t::zoneAfterCreatureLeave)` seam (game.cpp:12465-12467). */
    var executeZoneAfterCreatureLeave: (Zone, Creature) -> Unit = { _, _ -> }

    /** `g_callbacks().executeCallback(EventCallback_t::zoneAfterCreatureEnter)` seam (game.cpp:12468-12470). */
    var executeZoneAfterCreatureEnter: (Zone, Creature) -> Unit = { _, _ -> }

    /**
     * `g_globalEvents().getEventMap(GLOBALEVENT_PERIODCHANGE)` dispatch seam
     * (`src/game/game.cpp:9455-9460`); the global-event engine is not ported yet.
     */
    var executePeriodChange: (LightState, LightInfo) -> Unit = { _, _ -> }

    /** `Game::addPlayer` (src/game/game.cpp:11624). */
    fun addPlayer(player: Player) {
        players[player.id] = player
    }

    /** `Game::removePlayer` (src/game/game.cpp:11631). */
    fun removePlayer(player: Player) {
        players.remove(player.id)
    }

    /** `Game::getPlayers` (src/game/game.hpp:577): online players keyed by creature id. */
    fun getPlayers(): Map<Long, Player> = players

    /**
     * `Game::getPlayerByName` (src/game/game.cpp:1394): empty names return null and
     * lookups are case-insensitive (`asLowerCaseString`). When the player is not
     * online, `allowOffline` falls back to [playerStorage] (`IOLoginData::loadPlayerByName`
     * equivalent); no online flag exists on the Kotlin player yet.
     */
    fun getPlayerByName(name: String, allowOffline: Boolean = false): Player? {
        if (name.isEmpty()) {
            return null
        }

        players.values.firstOrNull { it.name.equals(name, ignoreCase = true) }?.let { return it }
        if (!allowOffline) {
            return null
        }
        return playerStorage?.loadByName(name)
    }

    /**
     * `Game::getPlayerByID` (src/game/game.cpp:1339). The `allowOffline` branch
     * loads through `IOLoginData::loadPlayerById` in Canary; the Kotlin
     * [PlayerStorage] only models name lookups, so an offline id resolves to
     * `null` (PARITY GAP).
     */
    fun getPlayerByID(playerId: Long, allowOffline: Boolean = false): Player? {
        players[playerId]?.let { return it }
        if (!allowOffline) {
            return null
        }
        return null
    }

    /**
     * Kotlin accessor for Canary's public `Game::map` member (src/game/game.hpp:627) as
     * used at call sites such as `g_game().map.getTile(pos)` (src/game/game.cpp:1117);
     * `Map::getTile` itself is defined at src/map/map.hpp:77.
     */
    fun getTile(position: Position): Tile? = map.getTile(position)

    /**
     * `Game::internalPlaceCreature` (src/game/game.cpp:1495) with the placement
     * fallback of `Map::placeCreature` (src/map/map.cpp:559).
     *
     * A creature is rejected when it already has a parent tile (creature.hpp
     * `getParent()`), and placement falls back to the surrounding tiles of
     * `Map::placeCreature`'s `normalRelList`/`extendedRelList`. `forced` mirrors
     * `forceLogin` and bypasses the tile `queryAdd` result. `creatureCheck` maps to
     * `addCreatureCheck`/`onPlacedCreature` (game.cpp:1516-1519) and the tile-zone
     * transitions run through [beforeCreatureZoneChange]/[afterCreatureZoneChange]
     * with the [tileZones] resolver.
     *
     * Parity gaps: protection-zone, teleport and `isSightClear` checks need tile
     * flags/raycasting that the Kotlin [Tile] does not model; monsters' push/field
     * rules are approximated by a plain occupancy check. `Creature::setID`,
     * `Creature::addList` and `Creature::updateCalculatedStepSpeed`
     * (game.cpp:1512-1514) have no Kotlin counterpart.
     */
    fun internalPlaceCreature(
        creature: Creature,
        pos: Position,
        extendedPos: Boolean = false,
        forced: Boolean = false,
        creatureCheck: Boolean = false,
    ): Boolean {
        if (placedCreatures.containsKey(creature)) {
            return false
        }

        val centerTile = map.getTile(pos)
        val destination = if (centerTile != null && (forced || canAddCreature(centerTile))) {
            centerTile to pos
        } else {
            findFallbackTile(pos, extendedPos, forced)
        }
        if (destination == null) {
            return false
        }

        val toZones = tileZones(destination.second)
        if (beforeCreatureZoneChange(creature, emptySet(), toZones) != ReturnValue.NOERROR) {
            return false
        }

        creature.position = destination.second
        placedCreatures[creature] = destination.first

        if (creatureCheck) {
            addCreatureCheck(creature)
            creature.onPlacedCreature()
        }

        afterCreatureZoneChange(creature, emptySet(), toZones)
        return true
    }

    /**
     * `Game::placeCreature` (src/game/game.cpp:1524): place through
     * [internalPlaceCreature], then fan out the appear notification to every
     * spectator. A player spectator forces [addCreatureCheck], and
     * `Creature::onPlacedCreature` closes the placement.
     *
     * Parity gaps: `Cylinder::postAddNotification` (game.cpp:1543-1546) and the
     * `MONSTER_PERF_TEST_FORCE_ACTIVE` creature-check arm (game.cpp:1539) are
     * not modeled; the spectator search is the facade's [spectatorsOf] subset of
     * `Spectators().find<Creature>(pos, multifloor = true)`.
     */
    fun placeCreature(
        creature: Creature,
        pos: Position,
        extendedPos: Boolean = false,
        forced: Boolean = false,
    ): Boolean {
        if (!internalPlaceCreature(creature, pos, extendedPos, forced)) {
            return false
        }

        var hasPlayerSpectators = false
        for (spectator in spectatorsOf(creature.position, multifloor = true)) {
            val tmpPlayer = spectator as? Player
            if (tmpPlayer != null) {
                tmpPlayer.sendCreatureAppear(creature, creature.position, true)
                hasPlayerSpectators = true
            }
            spectator.onCreatureAppear(creature, true)
        }

        if (hasPlayerSpectators) {
            addCreatureCheck(creature)
        }

        creature.onPlacedCreature()
        return true
    }

    /**
     * `Game::removeCreature` (src/game/game.cpp:1551): rejected for creatures
     * already flagged by `Creature::isRemoved()`, otherwise the tile spectators
     * receive the removal, the master link is cleared, the zone transition and
     * `Creature::setRemoved()` run, the creature leaves the think buckets and
     * every summon is removed first with `setSkillLoss(false)`.
     *
     * Parity gaps: `Tile::getClientIndexOfCreature` is not modeled, so the
     * `sendRemoveTileThing` fanout always uses the no-op `-1` stack position;
     * `Cylinder::postRemoveNotification`, `Creature::removeList` and the
     * team-finder logout cleanup (game.cpp:1599-1617) are not modeled. Canary
     * keeps going when `getTile()` is null; the facade rejects a creature that
     * was never placed instead.
     */
    fun removeCreature(creature: Creature, isLogout: Boolean = true): Boolean {
        if (creature.isRemoved()) {
            return false
        }
        val tile = placedCreatures[creature] ?: return false
        val fromZones = tileZones(tile.position)
        val spectators = spectatorsOf(tile.position, multifloor = true)

        placedCreatures.remove(creature)

        for (spectator in spectators) {
            if (spectator is Player) {
                spectator.sendRemoveTileThing(tile.position, -1)
            }
        }

        for (spectator in spectators) {
            spectator.onRemoveCreature(creature, isLogout)
        }

        val master = creature.getMaster()
        if (master != null && !master.isRemoved()) {
            creature.setMaster(null)
        }

        afterCreatureZoneChange(creature, fromZones, emptySet())

        creature.setRemoved()
        removeCreatureCheck(creature)

        for (summon in creature.getSummons().toList()) {
            summon.setSkillLoss(false)
            removeCreature(summon)
        }

        return true
    }

    /**
     * `Game::addCreatureCheck` (src/game/game.cpp:7659): flags the creature and
     * admits it once into a random `checkCreatureLists` bucket. The C++ event is
     * scheduled on the dispatcher when the caller is not already confined and
     * committed inline otherwise (game.cpp:7676-7684); the port mirrors that
     * with [GameDispatcher.isRunning].
     *
     * Parity gap: when the dispatcher rejects the event Canary resets
     * `inCheckCreaturesVector`; [GameDispatcher.dispatch] has no rejection
     * signal, so a dropped task leaves the flag set.
     */
    fun addCreatureCheck(creature: Creature) {
        if (creature.isRemoved()) {
            return
        }

        creature.creatureCheck = true

        if (creature.inCheckCreaturesVector) {
            return
        }
        creature.inCheckCreaturesVector = true

        val commit = {
            val index = Random.nextInt(EVENT_CREATURECOUNT)
            checkCreatureLists[index].add(WeakReference(creature))
        }
        if (dispatcher.isRunning) {
            dispatcher.dispatch(DispatcherLane.WorldCommit) { commit() }
        } else {
            commit()
        }
    }

    /** `Game::removeCreatureCheck` (src/game/game.cpp:7687). */
    fun removeCreatureCheck(creature: Creature) {
        if (creature.inCheckCreaturesVector) {
            creature.creatureCheck = false
        }
    }

    /**
     * `Game::internalAddItem` (src/game/game.cpp:2693 and 2698 remainder overload).
     *
     * The `Cylinder` flow is reduced to its [Tile] cases: `queryDestination`
     * (tile.cpp:1009, no floor change), `queryAdd` (tile.cpp:691), `queryMaxCount`
     * (tile.cpp:978) and stackable merge. The full-count remainder recursion is
     * inlined because a tile's `queryMaxCount` never caps a single call.
     *
     * Parity gaps: container destinations, mailboxes, hangable/always-on-top
     * ordering, blocking item checks and the `addedItem`/quiver branch of game.cpp
     * need item type data (ground/blockSolid/movable/stackSize/attributes) that the
     * Kotlin [Item]/[org.opentibia.canary.items.ItemType] do not model yet. The stack
     * cap uses Canary's `ItemType::stackSize` default of 100 (src/items/items.hpp:335)
     * because the Kotlin item type does not carry a per-item value.
     */
    fun internalAddItem(
        toTile: Tile?,
        item: Item?,
        index: Int = INDEX_WHEREEVER,
        flags: Int = 0,
        test: Boolean = false,
    ): ReturnValue {
        if (toTile == null || item == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val toItem = topDownItem(toTile)

        val ret = queryAdd(toTile, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        val maxQueryCount = queryMaxCount(item.count)
        if (test) {
            return ReturnValue.NOERROR
        }

        if (isStackable(item) && toItem != null && sameItemType(item, toItem)) {
            val m = minOf(item.count, maxQueryCount)
            val n = mergeCapacity(toItem, m)
            toItem.count += n

            val remainder = m - n
            if (remainder > 0) {
                if (item.count == remainder) {
                    addThing(toTile, item, index)
                } else {
                    addThing(toTile, cloneItem(item, remainder), index)
                }
            }
        } else {
            addThing(toTile, item, index)
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Game::internalRemoveItem` (src/game/game.cpp:2785). The parent `Cylinder` is
     * resolved from the item's tracked parent tile (the facade's `Item::getParent()`);
     * `count == -1` removes the whole stack, partial stackable counts decrement in
     * place and a full removal unlinks the item.
     *
     * Parity gaps: `Item::canRemove`, map-loaded decay protection and the
     * `FLAG_IGNORENOTMOVABLE`/`Item::isMovable` check cannot be evaluated because the
     * Kotlin [ItemType] does not model movable/decay state.
     */
    fun internalRemoveItem(
        item: Item?,
        count: Int = -1,
        test: Boolean = false,
        flags: Int = 0,
        force: Boolean = false,
    ): ReturnValue {
        if (item == null) {
            return ReturnValue.NOTPOSSIBLE
        }
        val tile = itemParents[item] ?: return ReturnValue.NOTPOSSIBLE

        val removeCount = if (count == -1) item.count else count
        val ret = queryRemove(tile, item, removeCount, flags or CylinderFlags.FLAG_IGNORENOTMOVABLE)
        if (!force && ret != ReturnValue.NOERROR) {
            return ret
        }

        if (!test) {
            removeThing(tile, item, removeCount)
        }
        return ReturnValue.NOERROR
    }

    /**
     * `Game::internalMoveItem` (src/game/game.cpp:2436).
     *
     * Transcribes the straight tile-to-tile path: same-top-item short circuit,
     * `queryRemove`/`queryAdd`/`queryMaxCount`, source removal, stackable merge with
     * the cap of `Item::getStackSize()` and clone-on-partial-move
     * (game.cpp:2566-2611). The `movedItem` out parameter becomes unnecessary because
     * a partial stack move leaves the original on the source and adds a clone on the
     * destination.
     *
     * Parity gaps: the `browseFields` remap (game.cpp:2447-2453),
     * `RETURNVALUE_NEEDEXCHANGE` (hangable/supports, game.cpp:2494-2524), the
     * `count == 255` client fix (game.cpp:2481), `tradeItem`, `actor` notifications and
     * the clone's attributes/decay state are not modeled.
     */
    fun internalMoveItem(
        fromTile: Tile?,
        toTile: Tile?,
        index: Int = INDEX_WHEREEVER,
        item: Item?,
        count: Int = 1,
        flags: Int = 0,
    ): ReturnValue {
        if (fromTile == null || toTile == null || item == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val toItem = topDownItem(toTile)
        if (item === toItem) {
            return ReturnValue.NOERROR
        }

        var ret = queryRemove(fromTile, item, 1, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        ret = queryAdd(toTile, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        val maxQueryCount = queryMaxCount(count)
        val m = if (isStackable(item)) minOf(count, maxQueryCount) else maxQueryCount

        ret = queryRemove(fromTile, item, m, flags)
        if (ret != ReturnValue.NOERROR) {
            return ret
        }

        removeThing(fromTile, item, m)

        if (isStackable(item)) {
            val n = if (toItem != null && sameItemType(item, toItem)) mergeCapacity(toItem, m) else 0
            if (n > 0 && toItem != null) {
                toItem.count += n
            }

            val newCount = m - n
            if (newCount > 0) {
                addThing(toTile, cloneItem(item, newCount), index)
            }
        } else {
            addThing(toTile, item, index)
        }

        return ReturnValue.NOERROR
    }

    /**
     * `Game::broadcastMessage` (src/game/game.cpp:9526): an empty text is
     * discarded, otherwise every online player receives the typed message
     * through [Player.sendTextMessageTyped].
     */
    fun broadcastMessage(text: String, type: MessageClass) {
        if (text.isEmpty()) {
            return
        }

        logger.info { "Broadcasted message: $text" }
        for (player in players.values.toList()) {
            player.sendTextMessageTyped(type, text)
        }
    }

    /** `Game::getWorldLightInfo` (src/game/game.cpp:9479): `{ lightLevel, 0xD7 }`. */
    fun getWorldLightInfo(): LightInfo = LightInfo(lightLevel, 0xD7)

    /** `Game::gameIsDay` (src/game/game.cpp:9483): day between 06:00 and 18:00. */
    fun gameIsDay(): Boolean {
        isDay = lightHour >= (6 * 60) && lightHour <= (18 * 60)
        return isDay
    }

    /**
     * `Game::checkLight` (src/game/game.cpp:9402): advances the tibian clock and
     * light level, then fans the world light and/or time out to every online
     * player. The `GLOBALEVENT_PERIODCHANGE` execution is routed through
     * [executePeriodChange] (PARITY GAP: the global-event engine is not ported).
     */
    fun checkLight() {
        lightHour += lightHourDelta
        if (lightHour > LIGHT_DAY_LENGTH) {
            lightHour -= LIGHT_DAY_LENGTH
        }

        if (abs(lightHour - SUNRISE) < 2 * lightHourDelta) {
            lightState = LightState.LIGHT_STATE_SUNRISE
        } else if (abs(lightHour - SUNSET) < 2 * lightHourDelta) {
            lightState = LightState.LIGHT_STATE_SUNSET
        }

        var newLightLevel = lightLevel
        var lightChange = false

        when (lightState) {
            LightState.LIGHT_STATE_SUNRISE -> {
                newLightLevel += (LIGHT_LEVEL_DAY - LIGHT_LEVEL_NIGHT) / 30
                lightChange = true
            }

            LightState.LIGHT_STATE_SUNSET -> {
                newLightLevel -= (LIGHT_LEVEL_DAY - LIGHT_LEVEL_NIGHT) / 30
                lightChange = true
            }

            else -> Unit
        }

        if (newLightLevel <= LIGHT_LEVEL_NIGHT) {
            lightLevel = LIGHT_LEVEL_NIGHT
            lightState = LightState.LIGHT_STATE_NIGHT
        } else if (newLightLevel >= LIGHT_LEVEL_DAY) {
            lightLevel = LIGHT_LEVEL_DAY
            lightState = LightState.LIGHT_STATE_DAY
        } else {
            lightLevel = newLightLevel
        }

        val lightInfo = getWorldLightInfo()
        if (lightChange) {
            for (player in players.values.toList()) {
                player.sendWorldLight(lightInfo)
                player.sendTibiaTime(lightHour)
            }
        } else {
            for (player in players.values.toList()) {
                player.sendTibiaTime(lightHour)
            }
        }

        if (currentLightState != lightState) {
            currentLightState = lightState
            executePeriodChange(lightState, lightInfo)
        }
    }

    /**
     * `Game::sendSingleSoundEffect` (src/game/game.cpp:7833): `SILENCE` is
     * discarded, otherwise every player spectator receives the effect with the
     * `SourceEffect_t` classification of [classifyGraphicalEffectSource].
     */
    fun sendSingleSoundEffect(pos: Position, soundId: SoundEffect, actor: Creature? = null) {
        if (soundId == SoundEffect.SILENCE) {
            return
        }

        for (spectator in spectatorsOf(pos, multifloor = false)) {
            val player = spectator as? Player ?: continue
            player.sendSingleSoundEffect(pos, soundId, classifyGraphicalEffectSource(actor, spectator))
        }
    }

    /**
     * `Game::sendDoubleSoundEffect` (src/game/game.cpp:7853): a silent secondary
     * effect degrades to [sendSingleSoundEffect], otherwise every player
     * spectator receives both effects with the shared source classification.
     */
    fun sendDoubleSoundEffect(
        pos: Position,
        mainSoundEffect: SoundEffect,
        secondarySoundEffect: SoundEffect,
        actor: Creature? = null,
    ) {
        if (secondarySoundEffect == SoundEffect.SILENCE) {
            sendSingleSoundEffect(pos, mainSoundEffect, actor)
            return
        }

        for (spectator in spectatorsOf(pos, multifloor = false)) {
            val player = spectator as? Player ?: continue
            val source = classifyGraphicalEffectSource(actor, spectator)
            player.sendDoubleSoundEffect(pos, mainSoundEffect, source, secondarySoundEffect, source)
        }
    }

    /**
     * `Game::playerStopAutoWalk` (src/game/game.cpp:4567): unknown ids are
     * ignored, otherwise `Player::stopWalk` latches `cancelNextWalk`.
     */
    fun playerStopAutoWalk(playerId: Long) {
        val player = getPlayerByID(playerId) ?: return
        player.stopWalk()
    }

    /**
     * `Game::beforeCreatureZoneChange` (src/game/game.cpp:12417): the zone
     * callbacks may veto a leave/enter transition unless [force] is set.
     */
    fun beforeCreatureZoneChange(
        creature: Creature?,
        fromZones: Set<Zone>,
        toZones: Set<Zone>,
        force: Boolean = false,
    ): ReturnValue {
        if (creature == null) {
            return ReturnValue.NOTPOSSIBLE
        }

        val zonesLeaving = fromZones - toZones
        val zonesEntering = toZones - fromZones
        if (zonesLeaving.isEmpty() && zonesEntering.isEmpty()) {
            return ReturnValue.NOERROR
        }

        for (zone in zonesLeaving) {
            val allowed = checkZoneBeforeCreatureLeave(zone, creature)
            if (!force && !allowed) {
                return ReturnValue.NOTPOSSIBLE
            }
        }

        for (zone in zonesEntering) {
            val allowed = checkZoneBeforeCreatureEnter(zone, creature)
            if (!force && !allowed) {
                return ReturnValue.NOTPOSSIBLE
            }
        }
        return ReturnValue.NOERROR
    }

    /**
     * `Game::afterCreatureZoneChange` (src/game/game.cpp:12447): runs the
     * after-leave/enter callbacks for the zones the creature actually changed.
     * `Zone::creatureRemoved` / `Zone::creatureAdded` (`zone.cpp`) are not
     * ported on [Zone] yet (PARITY GAP).
     */
    fun afterCreatureZoneChange(creature: Creature?, fromZones: Set<Zone>, toZones: Set<Zone>) {
        if (creature == null) {
            return
        }

        for (zone in fromZones - toZones) {
            executeZoneAfterCreatureLeave(zone, creature)
        }
        for (zone in toZones - fromZones) {
            executeZoneAfterCreatureEnter(zone, creature)
        }
    }

    /** `Tile::getTopDownItem` equivalent (src/items/tile.cpp:408): last inserted down item. */
    private fun topDownItem(tile: Tile): Item? = tile.items.lastOrNull()

    /** `Tile::getThingIndex` equivalent (src/items/tile.cpp:1443): ground is 0, then item list. */
    private fun thingIndex(tile: Tile, item: Item): Int {
        if (item === tile.ground) {
            return 0
        }
        val index = tile.items.indexOfFirst { it === item }
        return if (index < 0) -1 else index + if (tile.ground != null) 1 else 0
    }

    /** `Item::isStackable` (src/items/item.hpp:488). */
    private fun isStackable(item: Item): Boolean = item.type?.isStackable == true

    /** Subset of `Item::equals` (src/items/item.cpp:730): id and stackable flag. */
    private fun sameItemType(item: Item, other: Item?): Boolean =
        other != null && item.id == other.id && isStackable(item) && isStackable(other)

    /** `Item::getStackSize` (src/items/item.hpp:595) with Canary's `stackSize` default of 100. */
    private fun mergeCapacity(toItem: Item, m: Int): Int {
        val capacity = stackSize(toItem) - toItem.count
        return if (capacity <= 0) m else minOf(capacity, m)
    }

    private fun stackSize(item: Item): Int = if (isStackable(item)) DEFAULT_STACK_SIZE else 1

    private fun cloneItem(item: Item, count: Int): Item = Item(id = item.id, count = count, type = item.type)

    /**
     * `Tile::addThing(int32_t, Thing)` (src/items/tile.cpp:1131). The index is accepted
     * for parity but ignored, exactly like the C++ overload does for plain down items.
     */
    private fun addThing(tile: Tile, item: Item, index: Int) {
        tile.addItem(item)
        itemParents[item] = tile
    }

    /** `Tile::queryAdd` item branch (src/items/tile.cpp:691-962). */
    private fun queryAdd(tile: Tile, flags: Int): ReturnValue {
        if (flags and CylinderFlags.FLAG_NOLIMIT != 0) {
            return ReturnValue.NOERROR
        }
        if (tile.items.size >= MAX_TILE_ITEMS) {
            return ReturnValue.NOTPOSSIBLE
        }
        if (tile.ground == null) {
            return ReturnValue.NOTPOSSIBLE
        }
        return ReturnValue.NOERROR
    }

    /** `Tile::queryMaxCount` (src/items/tile.cpp:978). */
    private fun queryMaxCount(count: Int): Int = maxOf(1, count)

    /** `Tile::queryRemove` (src/items/tile.cpp:983). */
    private fun queryRemove(tile: Tile, item: Item, count: Int, flags: Int): ReturnValue {
        if (thingIndex(tile, item) < 0) {
            return ReturnValue.NOTPOSSIBLE
        }
        if (count == 0) {
            return ReturnValue.NOTPOSSIBLE
        }
        if (isStackable(item) && count > item.count) {
            return ReturnValue.NOTPOSSIBLE
        }
        return ReturnValue.NOERROR
    }

    /** `Tile::removeThing` (src/items/tile.cpp:1344): stackable partial decrement, otherwise unlink. */
    private fun removeThing(tile: Tile, item: Item, count: Int) {
        if (item === tile.ground) {
            tile.ground = null
            item.parent = null
            itemParents.remove(item)
            return
        }

        if (isStackable(item) && count != item.count) {
            item.count = maxOf(0, item.count - count)
        } else {
            tile.removeItem(item)
            item.parent = null
            itemParents.remove(item)
        }
    }

    /**
     * Minimal port of `Spectators::getSpectators` creature query
     * (src/map/spectators.cpp:121-201): the creatures placed through this facade
     * inside the default viewport rectangle (`MAP_MAX_VIEW_PORT_X/Y`) of
     * `Spectators::find` (spectators.cpp:205-212), honouring the `multifloor`
     * z-range and the per-floor offset shift (spectators.cpp:122-138, 182-185).
     * Canary reads the map-sector creature lists; the port indexes the facade's
     * own [placedCreatures] until the sector lists are modeled.
     */
    private fun spectatorsOf(centerPos: Position, multifloor: Boolean): List<Creature> {
        val minRangeZ: Int
        val maxRangeZ: Int
        if (!multifloor) {
            minRangeZ = centerPos.z
            maxRangeZ = centerPos.z
        } else if (centerPos.z > Creature.MAP_INIT_SURFACE_LAYER) {
            minRangeZ = maxOf(centerPos.z - Creature.MAP_LAYER_VIEW_LIMIT, 0)
            maxRangeZ = minOf(centerPos.z + Creature.MAP_LAYER_VIEW_LIMIT, MAP_MAX_LAYERS - 1)
        } else if (centerPos.z == Creature.MAP_INIT_SURFACE_LAYER - 1) {
            minRangeZ = 0
            maxRangeZ = Creature.MAP_INIT_SURFACE_LAYER - 1 + Creature.MAP_LAYER_VIEW_LIMIT
        } else if (centerPos.z == Creature.MAP_INIT_SURFACE_LAYER) {
            minRangeZ = 0
            maxRangeZ = Creature.MAP_INIT_SURFACE_LAYER + Creature.MAP_LAYER_VIEW_LIMIT
        } else {
            minRangeZ = 0
            maxRangeZ = Creature.MAP_INIT_SURFACE_LAYER
        }

        val minX = centerPos.x - Creature.MAP_MAX_VIEW_PORT_X
        val maxX = centerPos.x + Creature.MAP_MAX_VIEW_PORT_X
        val minY = centerPos.y - Creature.MAP_MAX_VIEW_PORT_Y
        val maxY = centerPos.y + Creature.MAP_MAX_VIEW_PORT_Y

        val spectators = mutableListOf<Creature>()
        for (creature in placedCreatures.keys) {
            val pos = creature.position
            if (pos.z - minRangeZ !in 0..(maxRangeZ - minRangeZ)) {
                continue
            }
            val offsetZ = centerPos.z - pos.z
            if (pos.x - offsetZ < minX || pos.x - offsetZ > maxX) {
                continue
            }
            if (pos.y - offsetZ < minY || pos.y - offsetZ > maxY) {
                continue
            }
            spectators.add(creature)
        }
        return spectators
    }

    /** `Tile::queryAdd` creature branch (src/items/tile.cpp:700-854). */
    private fun canAddCreature(tile: Tile): Boolean {
        if (tile.ground == null) {
            return false
        }
        return placedCreatures.values.none { it === tile }
    }

    /** `Map::placeCreature` fallback search (src/map/map.cpp:587-663). */
    private fun findFallbackTile(centerPos: Position, extendedPos: Boolean, forced: Boolean): Pair<Tile, Position>? {
        val relList = if (extendedPos) EXTENDED_REL_LIST else NORMAL_REL_LIST
        for ((dx, dy) in relList.shuffled()) {
            val tryPos = Position(centerPos.x + dx, centerPos.y + dy, centerPos.z)
            val tile = map.getTile(tryPos) ?: continue
            if (forced || canAddCreature(tile)) {
                return tile to tryPos
            }
        }
        return null
    }

    companion object {
        /** `INDEX_WHEREEVER` (src/items/cylinder.hpp:18). */
        const val INDEX_WHEREEVER = -1

        /** `Game::DAY_LENGTH_SECONDS` (src/game/game.hpp:916). */
        private const val DAY_LENGTH_SECONDS = 3600

        /** `Game::LIGHT_DAY_LENGTH` (src/game/game.hpp:917). */
        private const val LIGHT_DAY_LENGTH = 1440

        /** `Game::LIGHT_LEVEL_DAY` (src/game/game.hpp:918). */
        private const val LIGHT_LEVEL_DAY = 250

        /** `Game::LIGHT_LEVEL_NIGHT` (src/game/game.hpp:919). */
        private const val LIGHT_LEVEL_NIGHT = 40

        /** `Game::SUNSET` (src/game/game.hpp:920). */
        private const val SUNSET = 1050

        /** `Game::SUNRISE` (src/game/game.hpp:921). */
        private const val SUNRISE = 360

        /** `EVENT_LIGHTINTERVAL_MS` (src/game/game.hpp:66). */
        private const val EVENT_LIGHTINTERVAL_MS = 10000

        /** `Creature::EVENT_CREATURECOUNT` (src/creatures/creature.hpp:46). */
        private const val EVENT_CREATURECOUNT = 10

        /** `MAP_MAX_LAYERS` (src/map/map_const.hpp:17). */
        private const val MAP_MAX_LAYERS = 16

        /**
         * `classifyGraphicalEffectSource` (`src/game/game.cpp:96-108`,
         * anonymous namespace): the `SourceEffect_t` a spectator receives for a
         * graphical effect source.
         */
        private fun classifyGraphicalEffectSource(source: Creature?, spectator: Creature): SourceEffect {
            if (source == null || source is Npc) {
                return SourceEffect.GLOBAL
            }
            if (source === spectator) {
                return SourceEffect.OWN
            }
            if (source is Player) {
                return SourceEffect.OTHERS
            }
            return SourceEffect.CREATURES
        }

        /** `ItemType::stackSize` default (src/items/items.hpp:335). */
        private const val DEFAULT_STACK_SIZE = 100

        /** `Tile::queryAdd` item list ceiling (src/items/tile.cpp:899). */
        private const val MAX_TILE_ITEMS = 0x3E8

        /** `Map::placeCreature` extended search ring (src/map/map.cpp:588). */
        private val EXTENDED_REL_LIST = listOf(
            0 to -2,
            -1 to -1, 0 to -1, 1 to -1,
            -2 to 0, -1 to 0, 1 to 0, 2 to 0,
            -1 to 1, 0 to 1, 1 to 1,
            0 to 2,
        )

        /** `Map::placeCreature` normal search ring (src/map/map.cpp:603). */
        private val NORMAL_REL_LIST = listOf(
            -1 to -1, 0 to -1, 1 to -1,
            -1 to 0, 1 to 0,
            -1 to 1, 0 to 1, 1 to 1,
        )
    }
}
