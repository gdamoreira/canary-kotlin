package org.opentibia.canary.creatures

import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.account.getTimeNow
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSuppression
import org.opentibia.canary.combat.condition.ConditionTarget
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.MonkData
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.combat.condition.Skill
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.combat.condition.Stat
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.grouping.PartyShields_t
import org.opentibia.canary.creatures.monsters.MonsterBehavior
import org.opentibia.canary.creatures.monsters.MonsterDeathHost
import org.opentibia.canary.creatures.monsters.MonsterLoot
import org.opentibia.canary.creatures.monsters.MonsterThink
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.RaceType
import org.opentibia.canary.creatures.monsters.targetCreatureFactsOf
import org.opentibia.canary.creatures.players.CountBlock
import org.opentibia.canary.creatures.players.ExivaRestrictions
import org.opentibia.canary.creatures.players.PlayerActionTimers
import org.opentibia.canary.creatures.players.PlayerDeathState
import org.opentibia.canary.creatures.players.PlayerExperienceState
import org.opentibia.canary.creatures.players.PlayerTask
import org.opentibia.canary.creatures.players.PlayerTownState
import org.opentibia.canary.creatures.players.PlayerWalkState
import org.opentibia.canary.creatures.players.WalkStartPolicy
import org.opentibia.canary.creatures.players.components.CyclopediaPlayer
import org.opentibia.canary.creatures.players.components.PlayerAsyncTask
import org.opentibia.canary.creatures.players.components.PlayerCyclopedia
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.creatures.players.components.RecentDeathEntry
import org.opentibia.canary.creatures.players.components.RecentPvPKillEntry
import org.opentibia.canary.creatures.players.getCapacity
import org.opentibia.canary.creatures.players.pvp.PlayerPvp
import org.opentibia.canary.creatures.players.storages.Stash
import org.opentibia.canary.creatures.players.vip.InMemoryVipRepository
import org.opentibia.canary.creatures.players.vip.VipRepository
import org.opentibia.canary.game.QuickLootFilter_t
import org.opentibia.canary.game.death.CreatureDeathPipeline
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.scheduling.barrierContinuationLane
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRank
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Cylinder
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ObjectCategory_t
import org.opentibia.canary.items.Thing
import org.opentibia.canary.items.containers.Inbox
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.items.containers.depot.DepotLocker
import org.opentibia.canary.kv.KV
import org.opentibia.canary.kv.KVMemory
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.creatures.vocations.VocationRequirementsAdapter
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.Town
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.protocol.ProtocolSink
import org.opentibia.canary.network.protocol.SourceEffect
import org.opentibia.canary.network.protocol.creature.CreaturePacketWriter
import org.opentibia.canary.systems.bestiary.Charm
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.forge.PlayerForgeHistory
import org.opentibia.canary.systems.prey.PreySlot
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.rewardchest.RewardChest
import org.opentibia.canary.systems.taskhunting.TaskHuntingSlot
import org.opentibia.canary.systems.taskhunting.TaskMonsterInfo
import org.opentibia.canary.systems.wheel.PlayerWheel
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.spells.SpellBook
import org.opentibia.canary.spells.Spells
import org.opentibia.canary.spells.WorldType
import org.opentibia.canary.utils.BatchUpdateActor
import java.lang.ref.WeakReference
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.pow

/** `Creature::FlagAsyncClass_t` (`src/creatures/creature.hpp:819-827`). */
enum class FlagAsyncClass_t(val value: Int) {
    AsyncTaskRunning(1 shl 0),
    UpdateTargetList(1 shl 1),
    UpdateIdleStatus(1 shl 2),
    Pathfinder(1 shl 3),
    OnThink(1 shl 4),
    TargetRanking(1 shl 5),
    CombatIntention(1 shl 6),
}

/**
 * `ProtocolGame` cooldown senders the `Player` forwards to
 * (`src/server/network/protocol/protocolgame.hpp:469-470`,
 * player.cpp:8997-9007). A `null` client models `Player::client == nullptr`.
 */
interface SpellCooldownClient {
    /** `ProtocolGame::sendSpellCooldown` (player.cpp:8999). */
    fun sendSpellCooldown(spellId: Int, time: Int)

    /** `ProtocolGame::sendSpellGroupCooldown` (player.cpp:9005). */
    fun sendSpellGroupCooldown(groupId: SpellGroup, time: Int)
}

/**
 * `ProtocolGame` senders the `Player` cyclopedia monster-tracker hooks forward
 * to (`player.cpp:951-990, 992-996`). A `null` client models
 * `Player::client == nullptr`, where the tracker list updates early-return.
 */
interface CyclopediaMonsterTrackerClient {
    /** `ProtocolGame::parseSendBosstiary` (player.cpp:959). */
    fun parseSendBosstiary()

    /** `ProtocolGame::sendBestiaryEntryChanged` (player.cpp:961). */
    fun sendBestiaryEntryChanged(raceId: Int)

    /** `ProtocolGame::refreshCyclopediaMonsterTracker` (player.cpp:964, 987, 994). */
    fun refreshCyclopediaMonsterTracker(tracker: Set<MonsterType>, isBoss: Boolean)
}

abstract class Creature(
    val id: Long,
    val name: String,
    var maxHealth: Int,
    var health: Int,
    var maxMana: Int = 0,
    var mana: Int = 0,
    speed: Int = 200,
    override var position: Position,
    var direction: org.opentibia.canary.game.movement.Direction = org.opentibia.canary.game.movement.Direction.South
) : Thing {
    val isDead: Boolean get() = health <= 0

    /** `Creature::isAlive` (creature.hpp:275-277): `!isLifeless()`. */
    fun isAlive(): Boolean = health > 0

    private var internalRemoved = false

    /**
     * Port generation guard for deferred async work. It advances whenever the
     * owner is removed, invalidating every batch or completion queued before
     * (AGENTS.md deferred-callback lifetime safety). Canary models the same
     * safety with the scheduler's `weak_ptr` + id lookup
     * (`Creature::enqueueAsyncTask`, creature.cpp:2048-2054).
     */
    private var asyncTaskGeneration: Long = 0

    /** `Creature::setRemoved()` (src/creatures/creature.hpp:152). */
    fun setRemoved() {
        internalRemoved = true
        asyncTaskGeneration++
    }

    /** `Creature::isRemoved()` (src/creatures/creature.hpp:210). */
    override fun isRemoved(): Boolean = internalRemoved

    /**
     * `Creature::m_tile` (src/creatures/creature.hpp:656): the tile the creature
     * stands on, set through [setParent].
     */
    private var parentTile: Tile? = null

    /** `Creature::getParent()` (src/creatures/creature.cpp:1728-1730). */
    override fun getParent(): Cylinder? = getTile()

    /** `Creature::setParent()` (src/creatures/creature.cpp:1732+): the parent tile becomes the creature position. */
    override fun setParent(cylinder: Cylinder?) {
        val newParent = cylinder?.getTile()
        parentTile = newParent
        if (newParent != null) {
            position = newParent.position
        }
    }

    /** `Creature::getTile()` (src/creatures/creature.hpp:667-669). */
    override fun getTile(): Tile? = parentTile

    /** `Creature::getCreature()` (src/creatures/creature.hpp:86-88). */
    override fun getCreature(): Creature = this

    /** `Creature::getThrowRange()` (src/creatures/creature.hpp:204-206). */
    override fun getThrowRange(): Int = 1

    /** `Creature::isPushable()` (src/creatures/creature.hpp:207-209); `getWalkDelay()` is a PARITY GAP. */
    override fun isPushable(): Boolean = true

    /** `Creature::moveLocked` (src/creatures/creature.hpp:939). */
    private var moveLocked: Boolean = false

    /** `Creature::isMoveLocked()` (src/creatures/creature.hpp:189-191). */
    fun isMoveLocked(): Boolean = moveLocked

    /** `Creature::setMoveLocked()` (src/creatures/creature.hpp:192-194). */
    fun setMoveLocked(locked: Boolean) {
        moveLocked = locked
    }

    /**
     * `Creature::eventsList` (src/creatures/creature.hpp:842): the attached named
     * creature events, in attachment order. `Monster` keeps its type-derived
     * attachments in the world host's `MonsterTypeState.creatureEvents`
     * (`Monster::Monster`, monster.cpp:107-114); this list carries the ones
     * attached through [registerCreatureEvent] (players, NPCs and the
     * `Creature:registerEvent` call path, creature_functions.cpp:151-162).
     */
    val creatureEvents: MutableSet<String> = linkedSetOf()

    /**
     * `g_creatureEvents().getEventByName(name)`
     * (`CreatureEvents::getEventByName`, src/lua/creature/creatureevent.cpp:43-52)
     * registry lookup consulted by [registerCreatureEvent]
     * (src/creatures/creature.cpp:1674-1680). The registry lives in the Lua
     * environment (AGENTS.md 1.4), so the composition root injects
     * `luaEnvironment.creatureEvents.get(name) != null`; the unwired default
     * rejects every name exactly like an empty registry.
     */
    var creatureEventRegistered: (String) -> Boolean = { false }

    /**
     * `Creature::registerCreatureEvent` (src/creatures/creature.cpp:1674-1693):
     * attaches [name]; false when the registry does not know the name or it was
     * already attached (the C++ duplicate branch).
     */
    fun registerCreatureEvent(name: String): Boolean {
        if (!creatureEventRegistered(name) || creatureEvents.contains(name)) {
            return false
        }
        creatureEvents.add(name)
        return true
    }

    /**
     * `Creature::unregisterCreatureEvent` (src/creatures/creature.cpp:1695-1724):
     * detaches [name]; false when the registry does not know it. PARITY GAP: the
     * C++ returns true when the event's type was attached even if the event
     * itself was not (the `scriptEventsBitField` early-out); the port tracks
     * names only, so an unknown attachment reports false.
     */
    fun unregisterCreatureEvent(name: String): Boolean {
        if (!creatureEventRegistered(name)) {
            return false
        }
        return creatureEvents.remove(name)
    }

    /** `Creature::getCreatureEvents()` (src/creatures/creature.hpp:952) over the attached list. */
    fun getCreatureEvents(): Collection<String> = creatureEvents

    /** `Creature::getDescription(int32_t)` is implemented by Player/Monster/Npc; the base placeholder is a PARITY GAP. */
    override fun getDescription(lookDistance: Int): String = ""

    /** `Creature::creatureCheck` (src/creatures/creature.hpp:927). */
    var creatureCheck: Boolean = false

    /** `Creature::inCheckCreaturesVector` (src/creatures/creature.hpp:928). */
    var inCheckCreaturesVector: Boolean = false

    /** `Creature::lastPosition` (src/creatures/creature.hpp:672). */
    private var lastPosition: Position = position

    /** `Creature::getLastPosition()` (src/creatures/creature.hpp:675-677). */
    fun getLastPosition(): Position = lastPosition

    /** `Creature::setLastPosition()` (src/creatures/creature.hpp:678-680). */
    fun setLastPosition(newLastPos: Position) {
        lastPosition = newLastPos
    }

    /**
     * `Creature::onCreatureAppear` (`creature.cpp:403-410`). The base body only
     * records the last position when a creature appears to itself on login;
     * `Player::onCreatureAppear` (`player.cpp:12220`) extends it with inventory,
     * tracker and condition restore (PARITY GAP).
     */
    open fun onCreatureAppear(creature: Creature, isLogin: Boolean) {
        if (creature === this && isLogin) {
            setLastPosition(position)
        }
    }

    /**
     * `Creature::onRemoveCreature` (`creature.cpp:412-421`). The C++ body
     * ignores its second parameter and always reports the disappearance; a
     * monster whose attacked creature was removed re-points its target and
     * follow to the removed creature exactly like the C++ tail.
     */
    open fun onRemoveCreature(creature: Creature, isLogout: Boolean) {
        onCreatureDisappear(creature, true)

        // Update player from monster target list (avoid memory usage after clean)
        if (this is Monster && getAttackedCreature() === creature) {
            setAttackedCreature(creature)
            setFollowCreature(creature)
        }
    }

    /** `Creature::onCreatureDisappear` (`creature.cpp:423-434`). */
    open fun onCreatureDisappear(creature: Creature, isLogout: Boolean) {
        if (getAttackedCreature() === creature) {
            setAttackedCreature(null)
            onAttackedCreatureDisappear(isLogout)
        }

        if (getFollowCreature() === creature) {
            setFollowCreature(null)
            onFollowCreatureDisappear(isLogout)
        }
    }

    /** `Creature::onAttackedCreatureDisappear` (creature.hpp:613) virtual hook. */
    open fun onAttackedCreatureDisappear(isLogout: Boolean) {
        // No behaviour.
    }

    /** `Creature::onFollowCreatureDisappear` (creature.hpp:614) virtual hook. */
    open fun onFollowCreatureDisappear(isLogout: Boolean) {
        // No behaviour.
    }

    /**
     * `Creature::onCreatureSay` (`creature.hpp:616`) virtual hook; the base
     * creature has no behaviour. `Player::saySpell` fires it for every player
     * spectator (player.cpp:11076).
     */
    open fun onCreatureSay(creature: Creature, type: SpeakClasses, text: String) {
        // No behaviour.
    }

    // --- movement/zone hooks (creature.cpp:303-313, 436-577, 1856-1862) -----------

    /** `Creature::lastStep` (creature.hpp:892). */
    var lastStep: Long = 0

    /** `Creature::lastStepCost` (creature.hpp:900), default 1. */
    var lastStepCost: Int = 1

    /** `Creature::eventWalk` (creature.hpp:895). */
    open var eventWalk: Long = 0

    /**
     * `g_dispatcher().stopEvent(eventWalk)` (`creature.cpp:387`) admission
     * seam; `Player` overrides the member with its dispatcher binding.
     */
    open var stopEvent: (Long) -> Unit = {}

    /** `Creature::stopEventWalk` (creature.cpp:385-390). */
    fun stopEventWalk() {
        if (eventWalk != 0L) {
            stopEvent(eventWalk)
            eventWalk = 0
        }
    }

    /**
     * `Creature::resetMovementState` (creature.cpp:303-313). The walk queue and
     * the player update-tile send live in the movement subsystem; the base
     * creature owns no queue, so only `Player` overrides this hook.
     */
    open fun resetMovementState() {
        // No behaviour.
    }

    /** `Creature::getZoneType` (creature.cpp:1856-1862). */
    fun getZoneType(): ZoneType = getTile()?.getZoneType() ?: ZoneType.ZONE_NORMAL

    /** `Creature::onChangeZone` (creature.cpp:436-442). */
    open fun onChangeZone(zone: ZoneType) {
        val attacked = getAttackedCreature()
        if (attacked != null && zone == ZoneType.ZONE_PROTECTION) {
            onCreatureDisappear(attacked, false)
        }
    }

    /** `Creature::onAttackedCreatureChangeZone` (creature.cpp:444-452). */
    open fun onAttackedCreatureChangeZone(zone: ZoneType) {
        if (zone == ZoneType.ZONE_PROTECTION) {
            val attacked = getAttackedCreature()
            if (attacked != null) {
                onCreatureDisappear(attacked, false)
            }
        }
    }

    /** `Creature::hasExtraSwing` (creature.hpp:956) virtual hook. */
    open fun hasExtraSwing(): Boolean = false

    /** `g_game().checkCreatureAttack(creature)` (creature.hpp:587) coordinator seam. */
    var checkCreatureAttack: () -> Unit = {}

    /** `Creature::updateCreatureWalk` (creature.hpp:412-414). */
    open fun updateCreatureWalk() {
        goToFollowCreature_async()
    }

    /**
     * `Creature::onCreatureMove` (`creature.cpp:501-577`).
     *
     * PARITY GAPS: the summon relocation chain
     * `checkSummonMove`/`isLostSummon`/`handleLostSummon` (creature.cpp:454-500,
     * 1911-1926) is owned by the summon subsystem, and the
     * `Player::isExerciseTraining` reset (creature.cpp:539-543) needs the
     * unported exercise-training state; both are skipped. The rooted guard uses
     * `hasCondition` directly (the C++ `hasTrackedConditionType` is the same
     * list behind a derived cache, creature.hpp:856-860).
     */
    open fun onCreatureMove(
        creature: Creature,
        newTile: Tile?,
        newPos: Position,
        oldTile: Tile?,
        oldPos: Position,
        teleport: Boolean,
    ) {
        if (hasCondition(ConditionType.CONDITION_ROOTED)) {
            resetMovementState()
            return
        }

        val selfMove = creature === this
        if (!selfMove && getFollowCreature() !== creature && getAttackedCreature() !== creature) {
            return
        }

        if (selfMove) {
            lastStep = now()
            lastStepCost = 1

            if (!teleport) {
                if (oldPos.z != newPos.z) {
                    lastStepCost = WALK_FLOOR_CHANGE_EXTRA_COST
                } else if (abs(newPos.x - oldPos.x) >= 1 && abs(newPos.y - oldPos.y) >= 1) {
                    lastStepCost = WALK_DIAGONAL_EXTRA_COST
                }
            } else {
                stopEventWalk()
            }

            if (newTile != null && oldTile != null && newTile.getZoneType() != oldTile.getZoneType()) {
                onChangeZone(getZoneType())
            }
        }

        val followCreature = getFollowCreature()
        if (followCreature != null && (selfMove || creature === followCreature)) {
            if (hasFollowPath) {
                isUpdatingPath = true
                updateCreatureWalk()
            }

            if (newPos.z != oldPos.z || !canSee(followCreature.position)) {
                onCreatureDisappear(followCreature, false)
            }
        }

        val attackedCreature = getAttackedCreature()
        if (attackedCreature != null && (creature === attackedCreature || selfMove)) {
            if (newPos.z != oldPos.z || !canSee(attackedCreature.position)) {
                onCreatureDisappear(attackedCreature, false)
            } else {
                if (hasExtraSwing()) {
                    checkCreatureAttack()
                }

                if (newTile != null && oldTile != null && newTile.getZoneType() != oldTile.getZoneType()) {
                    onAttackedCreatureChangeZone(attackedCreature.getZoneType())
                }
            }
        }
    }

    /** `Creature::onPlacedCreature` (creature.hpp:618) virtual hook. */
    open fun onPlacedCreature() {
        // No behaviour.
    }

    /**
     * `Creature::onWalk` (creature.cpp:291-301) — the drunk-creature direction
     * roll. The movement subsystem models the C++ `Direction &dir`
     * out-parameter by returning the effective direction
     * (`MonsterWalk.onWalk`); this base hook is the virtual seam
     * `Player::onWalk` overrides. PARITY GAP: the drunk step replacement is not
     * applied to players.
     */
    open fun onWalk(dir: Direction) {
        // No behaviour; see KDoc.
    }

    /**
     * `Creature::goToFollowCreature` (creature.cpp:1090-1120) — the follow
     * subsystem's path recompute. PARITY GAP: the pathfinding body is not
     * ported; `Player::goToFollowCreature` delegates the call to the walk
     * state's seam.
     */
    open fun goToFollowCreature() {
        // No behaviour.
    }

    // --- async task queue (creature.hpp:977-1076, creature.cpp:2148-2217) --------

    /** `Creature::asyncTasks` (creature.hpp:1040). */
    private val asyncTasks = mutableListOf<() -> Unit>()

    /** `Creature::m_flagAsyncTask` (creature.hpp:1071). */
    private var flagAsyncTask: Int = 0

    /** `Creature::m_deferredAsyncTaskFlags` (creature.hpp:1073). */
    private var deferredAsyncTaskFlags: Int = 0

    /** `Creature::m_isExecutingAsyncTasks` (creature.hpp:1076). */
    private var isExecutingAsyncTasks: Boolean = false

    /**
     * `g_dispatcher()` hand-back seam for the creature async queue
     * (`Creature::enqueueAsyncTask`, creature.cpp:2031-2055). The port
     * eliminates the global: the production composition attaches the server
     * dispatcher to each creature entering the world, and
     * [scheduleAsyncTaskBatch]/[scheduleAsyncCallback] then hand their work
     * back through [DispatcherLane.WorkerCompletion] — the serial
     * world-worker lane — instead of running it on the producer's thread
     * (AGENTS.md 1.2). A `null` dispatcher keeps the world-confined inline
     * default for embedded use.
     *
     * CALL-SITE NEEDS (files outside this port's ownership):
     *  - `Game.internalPlaceCreature` (Game.kt) binds this for every creature
     *    it places, next to the existing `creature.addCreatureHealth` bind.
     *  - `MonsterPipelinesHost.register` (MonsterPipelinesHost.kt) binds this
     *    for registered monsters from its injected dispatcher so
     *    `Monster`-owned batches no longer run inline.
     *  - `GameHandler` binds the session player at login.
     */
    var asyncTaskDispatcher: GameDispatcher? = null

    /**
     * `g_dispatcher().addCreatureAsyncEvent` admission seam
     * (`creature.cpp:2048-2054`). The port runs world work confined to the
     * single world lane (AGENTS.md 1.2): with [asyncTaskDispatcher] attached
     * the batch is handed back through [DispatcherLane.WorkerCompletion]; a
     * host can also rebind the member directly, and without a dispatcher the
     * batch pumps synchronously.
     */
    var scheduleAsyncTaskBatch: (() -> Unit) -> Unit = { task -> dispatchAsyncTaskHandBack(task) }

    /**
     * `g_dispatcher().addEvent` admission seam used by
     * `Creature::goToFollowCreature_async` for its `onComplete` barrier
     * (`creature.cpp:1052`). The default mirrors [scheduleAsyncTaskBatch] and
     * hands the callback back through [asyncTaskDispatcher]; a host can inject
     * the dispatcher hop.
     */
    var scheduleAsyncCallback: (() -> Unit) -> Unit = { task -> dispatchAsyncTaskHandBack(task) }

    /**
     * [asyncTaskDispatcher] hand-back with the inline fallback: a stopped or
     * absent dispatcher pumps on the caller's thread. The deferred callback
     * re-reads [asyncTaskDispatcher] at admission time; the queued task keeps
     * its own generation guard (see [sendAsyncTasks]).
     */
    private fun dispatchAsyncTaskHandBack(task: () -> Unit) {
        val dispatcher = asyncTaskDispatcher
        if (dispatcher != null && dispatcher.isRunning) {
            dispatcher.dispatch(DispatcherLane.WorkerCompletion) { task() }
        } else {
            task()
        }
    }

    /** `Creature::addAsyncTask` (creature.hpp:984-989). */
    fun addAsyncTask(fnc: () -> Unit) {
        asyncTasks.add(fnc)
        if (!isExecutingAsyncTasks && !hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)) {
            sendAsyncTasks()
        }
    }

    /** `Creature::hasAsyncTaskFlag` (creature.hpp:991-993). */
    fun hasAsyncTaskFlag(prop: FlagAsyncClass_t): Boolean = (flagAsyncTask and prop.value) != 0

    /** `Creature::setAsyncTaskFlag` (creature.hpp:995-1014). */
    fun setAsyncTaskFlag(taskFlag: FlagAsyncClass_t, v: Boolean) {
        if (v) {
            val isAsyncTaskAlreadyScheduled = hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)
            flagAsyncTask = flagAsyncTask or taskFlag.value
            if (isExecutingAsyncTasks && taskFlag != FlagAsyncClass_t.AsyncTaskRunning) {
                deferredAsyncTaskFlags = deferredAsyncTaskFlags or taskFlag.value
                return
            }
            if (!isAsyncTaskAlreadyScheduled) {
                sendAsyncTasks()
            }
        } else {
            flagAsyncTask = flagAsyncTask and taskFlag.value.inv()
            if (isExecutingAsyncTasks) {
                deferredAsyncTaskFlags = deferredAsyncTaskFlags and taskFlag.value.inv()
            }
        }
    }

    /** `Creature::onExecuteAsyncTasks` (creature.hpp:1016) — virtual hook. */
    open fun onExecuteAsyncTasks() {
        // No behaviour.
    }

    /**
     * `Creature::executeAsyncTasks` (`creature.cpp:2148-2184`). The
     * `Pathfinder` flag reruns the follow path recompute
     * (`creature.cpp:2162-2164`).
     */
    fun executeAsyncTasks() {
        val currentTasks = asyncTasks.toList()
        asyncTasks.clear()

        val currentFlags = flagAsyncTask and FlagAsyncClass_t.AsyncTaskRunning.value.inv()
        deferredAsyncTaskFlags = 0
        isExecutingAsyncTasks = true

        if (!isRemoved() && isAlive()) {
            for (task in currentTasks) {
                task()
            }

            flagAsyncTask = FlagAsyncClass_t.AsyncTaskRunning.value or currentFlags
            if ((currentFlags and FlagAsyncClass_t.Pathfinder.value) != 0) {
                goToFollowCreature()
            }
            onExecuteAsyncTasks()
        }

        isExecutingAsyncTasks = false

        val deferredFlags = deferredAsyncTaskFlags
        deferredAsyncTaskFlags = 0
        flagAsyncTask = deferredFlags

        if (asyncTasks.isNotEmpty() || deferredFlags != 0) {
            sendAsyncTasks()
        } else {
            flagAsyncTask = 0
        }
    }

    /**
     * `Creature::goToFollowCreature_async` (`creature.cpp:1046-1056`).
     *
     * A dead creature ignores the request. The `onComplete` barrier is queued
     * only while no `Pathfinder` task is already pending, then the flag is set
     * so the next [executeAsyncTasks] reruns [goToFollowCreature].
     *
     * The queued barrier validates the generation captured at scheduling time,
     * so a callback queued before removal/replacement of the owner becomes a
     * no-op (AGENTS.md deferred-callback lifetime safety).
     */
    fun goToFollowCreature_async(onComplete: (() -> Unit)? = null) {
        if (isDead) {
            return
        }

        if (!hasAsyncTaskFlag(FlagAsyncClass_t.Pathfinder) && onComplete != null) {
            val scheduledGeneration = asyncTaskGeneration
            scheduleAsyncCallback {
                if (scheduledGeneration != asyncTaskGeneration || isRemoved()) {
                    return@scheduleAsyncCallback
                }
                onComplete()
            }
        }

        setAsyncTaskFlag(FlagAsyncClass_t.Pathfinder, true)
    }

    /** `Creature::sendAsyncTasks` (`creature.cpp:2186-2193`). */
    fun sendAsyncTasks() {
        if (hasAsyncTaskFlag(FlagAsyncClass_t.AsyncTaskRunning)) {
            return
        }

        flagAsyncTask = flagAsyncTask or FlagAsyncClass_t.AsyncTaskRunning.value
        val scheduledGeneration = asyncTaskGeneration
        scheduleAsyncTaskBatch {
            if (scheduledGeneration != asyncTaskGeneration) {
                dropStaleAsyncTaskBatch()
                return@scheduleAsyncTaskBatch
            }
            executeAsyncTasks()
        }
    }

    /**
     * Drops a batch scheduled before its owner was removed. The queued work can
     * no longer reach the world, but the `AsyncTaskRunning` latch must be
     * cleared so a later replacement of the owner object starts clean.
     */
    private fun dropStaleAsyncTaskBatch() {
        asyncTasks.clear()
        deferredAsyncTaskFlags = 0
        if (flagAsyncTask == FlagAsyncClass_t.AsyncTaskRunning.value) {
            flagAsyncTask = 0
        }
    }

    /**
     * `Creature::safeCall` (`creature.cpp:2195-2217`). Inside a barrier-parallel
     * dispatcher context the action hops to
     * `barrierContinuationLane(context lane)` through `Dispatcher::addEvent`
     * (creature.cpp:2196-2209); the deferred callback re-resolves the owner
     * through a weak reference and drops the action once it was internally
     * removed (AGENTS.md deferred-callback lifetime safety). Outside that
     * context the action runs immediately, and a removed creature refuses it
     * (creature.cpp:2210-2216).
     */
    fun safeCall(action: () -> Unit): Boolean {
        val dispatcher = asyncTaskDispatcher
        if (dispatcher != null) {
            val context = dispatcher.context()
            if (context.isBarrierParallel()) {
                val weakSelf = WeakReference(this)
                return dispatcher.dispatch(barrierContinuationLane(context.lane), context.taskName) {
                    val self = weakSelf.get() ?: return@dispatch
                    if (!self.isRemoved()) {
                        action()
                    }
                }
            }
        }

        if (isRemoved()) {
            return false
        }

        action()
        return true
    }

    /**
     * `Creature::drainHealth` (`creature.cpp:893-899`) reduced to its
     * `changeHealth(-damage, false)` core for the call sites that carry no
     * attacker (the field/Lua/condition reductions). The full C++ sequence
     * including the attacker credit is [drainHealth] with an attacker.
     */
    fun drainHealth(amount: Int) {
        changeHealth(-amount, false)
    }

    /**
     * `Creature::drainHealth(attacker, damage)` (`creature.cpp:893-899`):
     * `changeHealth(-damage, false)`, then the
     * `attacker->onAttackedCreatureDrainHealth(this, damage)` credit that
     * stamps the real damage in the target's damage map
     * (`creature.cpp:1277-1283`), so the death pipeline's last-hit and
     * most-damage resolution sees combat kills.
     *
     * ORDERING: Canary queues `onDeath` through the dispatcher
     * (`creature.cpp:861-875`), so the credit lands before the death body reads
     * the damage map. The port runs the death synchronously inside
     * [changeHealth], so the credit must run first to keep the killing blow
     * (and the last-hit stamp) observable to `Creature::onDeath`.
     */
    fun drainHealth(attacker: Creature?, damage: Int) {
        if (attacker != null) {
            attacker.onAttackedCreatureDrainHealth(this, damage)
        }

        changeHealth(-damage, false)
    }

    /**
     * `ConditionTarget.addHealth` maps to `Creature::changeHealth(positive)`
     * (`condition.cpp:1507-1510`), keeping the broadcast/death sink active.
     */
    fun addHealth(amount: Int) {
        changeHealth(amount)
    }

    /**
     * `g_game().addCreatureHealth(creature)` broadcast seam
     * (`creature.cpp:859-861`, `game.cpp:7826`). The `Game` facade binds this to
     * its spectator fanout when the creature is placed; an unbound creature
     * (or one outside a `Game`) keeps the C++ `sendHealthChange = false`
     * silence.
     */
    var addCreatureHealth: (Creature) -> Unit = {}

    /**
     * `Creature::changeHealth` (`creature.cpp:845-876`). [addCreatureHealth] is
     * the `g_game().addCreatureHealth` broadcast, and Canary queues `onDeath`
     * through the dispatcher (`creature.cpp:862-874`); the port runs confined on
     * the world lane and invokes [onDeath] directly when the lifeless guard lets
     * the transition through.
     */
    open fun changeHealth(healthChange: Int, sendHealthChange: Boolean = true) {
        if (isDead) {
            return
        }

        val oldHealth = health

        if (healthChange > 0) {
            health += minOf(healthChange, maxHealth - health)
        } else {
            health = maxOf(0, health + healthChange)
        }

        if (sendHealthChange && oldHealth != health) {
            addCreatureHealth(this)
        }

        if (health <= 0 && !isRemoved()) {
            onDeath()
        }
    }

    /** `Creature::gainHealth` (`creature.cpp:886-891`). */
    fun gainHealth(healer: Creature?, healthGain: Int) {
        changeHealth(healthGain)
        healer?.onTargetCreatureGainHealth(this, healthGain)
    }

    /**
     * `Creature::baseSpeed` (creature.hpp:901) seeded from the constructor's
     * `speed`; `Creature::varSpeed` (creature.hpp:903) starts at zero.
     */
    private var baseSpeedValue: Int = speed.coerceIn(0, PLAYER_MAX_SPEED)
    private var varSpeedValue: Int = 0

    /** `Creature::getSpeed` (`creature.cpp:1643-1645`). */
    fun getSpeed(): Int = (baseSpeedValue + varSpeedValue).coerceIn(0, PLAYER_MAX_SPEED)

    /**
     * `Creature::setSpeed` (`creature.cpp:1647-1664`). The walk rescheduling side
     * effects (`stopEventWalk`/`addEventWalk`/`updateCalculatedStepSpeed`) belong
     * to the movement subsystem in this port (PARITY GAP); the variance state is
     * applied exactly.
     */
    fun setSpeed(varSpeedDelta: Int) {
        if (getSpeed() >= PLAYER_MAX_SPEED) {
            return
        }
        varSpeedValue = varSpeedDelta
    }

    /** `Creature::getBaseSpeed` (creature.hpp:267-269). */
    fun getBaseSpeed(): Int = baseSpeedValue

    /**
     * `Creature::setBaseSpeed` (creature.hpp:264-266). Canary stores into a
     * `uint16_t` (implicit wrap through `Game::setCreatureSpeed`); this port
     * clamps to the representable range instead of wrapping (PARITY GAP).
     */
    fun setBaseSpeed(newBaseSpeed: Int) {
        baseSpeedValue = newBaseSpeed.coerceIn(0, PLAYER_MAX_SPEED)
    }

    /** `Creature::getStepSpeed` (creature.hpp:258-260); `Player` overrides it. */
    open fun getStepSpeed(): Int = getSpeed()

    /**
     * Port seam kept for the existing storage/Lua call sites: reads map to
     * [getSpeed] and writes map to [setBaseSpeed]
     * (`g_game().setCreatureSpeed`, game.cpp:7759-7761). The accessors are
     * renamed at the JVM level so they do not clash with the Canary-parity
     * `getSpeed`/`setSpeed` methods.
     */
    @get:JvmName("getSpeedValue")
    @set:JvmName("setSpeedValue")
    var speed: Int
        get() = getSpeed()
        set(value) {
            setBaseSpeed(value)
        }

    /** `Creature::currentOutfit` / `Creature::defaultOutfit` (creature.hpp:918-919). */
    private var currentOutfit = Outfit()
    private var defaultOutfit = Outfit()

    /** `Creature::getCurrentOutfit` (creature.hpp:364-366). */
    fun getCurrentOutfit(): Outfit = currentOutfit.copy()

    /** `Creature::setCurrentOutfit` (creature.hpp:367-369). */
    fun setCurrentOutfit(outfit: Outfit) {
        currentOutfit = outfit.copy()
    }

    /** `Creature::getDefaultOutfit` (creature.hpp:370-372). */
    fun getDefaultOutfit(): Outfit = defaultOutfit.copy()

    /** `Creature::setDefaultOutfit` (creature.hpp:373-375). */
    fun setDefaultOutfit(outfit: Outfit) {
        defaultOutfit = outfit.copy()
    }

    /** `Creature::isWearingSupportOutfit` (creature.hpp:376-379). */
    fun isWearingSupportOutfit(): Boolean =
        currentOutfit.lookType == 75 || currentOutfit.lookType == 266 || currentOutfit.lookType == 302

    /** `Creature::internalLight` (creature.hpp:922); default color 215. */
    private var internalLight: LightInfo = LightInfo()

    /** `Creature::getCreatureLight` (`creature.cpp:1639-1641`). */
    open fun getCreatureLight(): LightInfo = internalLight.copy()

    /** `Creature::setCreatureLight` (`creature.cpp:1666-1668`). */
    fun setCreatureLight(lightInfo: LightInfo) {
        internalLight = lightInfo.copy()
    }

    /** `Creature::setNormalCreatureLight` (`creature.cpp:1670-1672`). */
    open fun setNormalCreatureLight() {
        internalLight = LightInfo()
    }

    // --- buffs (creature.hpp:309-323, 909) ---------------------------------------

    /** `Creature::varBuffs` (creature.hpp:909), every entry seeded with 100. */
    private val varBuffs = IntArray(Buff.LAST + 1) { 100 }

    /** `Creature::getBuff(int32_t)` (creature.hpp:309-315). */
    fun getBuff(buff: Int): Int = varBuffs[buff]

    /** `Creature::setBuff(buffs_t, int32_t)` (creature.hpp:317-319). */
    fun setBuff(buff: Buff, modifier: Int) {
        varBuffs[buff.value] += modifier
    }

    /** `Creature::resetBuff(buffs_t)` (creature.hpp:321-323). */
    fun resetBuff(buff: Buff) {
        varBuffs[buff.value] = 100
    }

    // --- loot/skill-loss flags (creature.hpp:641-646, 932-933) --------------------

    /**
     * `Creature::skillLoss` (creature.hpp:932, protected), default true. The
     * setter is renamed at the JVM level so it does not clash with the
     * Canary-parity [setSkillLoss] method.
     */
    @set:JvmName("setSkillLossValue")
    protected var skillLoss: Boolean = true

    /** `Creature::setSkillLoss` (creature.hpp:644-646). */
    fun setSkillLoss(newSkillLoss: Boolean) {
        skillLoss = newSkillLoss
    }

    /** `Creature::lootDrop` (creature.hpp:933), default true. */
    var lootDrop: Boolean = true

    /** `Creature::setDropLoot` (creature.hpp:641-643). */
    fun setDropLoot(newLootDrop: Boolean) {
        lootDrop = newLootDrop
    }

    /**
     * `Creature::getLostExperience` (creature.hpp:960-962) — the base creature
     * loses nothing; `Player` overrides it (player.hpp:1921-1923).
     */
    open fun getLostExperience(): Long = 0L

    // --- damage tracking (creature.hpp:549, 694, 839, 897) -----------------------

    /**
     * `Creature::damageMap` (creature.hpp:839), the `std::map<uint32_t,
     * CountBlock_t>` keyed by attacker id.
     */
    private val damageMap: MutableMap<Long, CountBlock> = sortedMapOf()

    /** `Creature::lastHitCreatureId` (creature.hpp:897). */
    var lastHitCreatureId: Long = 0

    /** `Creature::getDamageMap` (creature.hpp:694-696). */
    fun getDamageMap(): Map<Long, CountBlock> = damageMap

    /** `Creature::addDamagePoints` (creature.cpp:1195-1211). */
    fun addDamagePoints(attacker: Creature, damagePoints: Int) {
        if (damagePoints <= 0) {
            return
        }

        val existing = damageMap[attacker.id]
        damageMap[attacker.id] = if (existing == null) {
            CountBlock(total = damagePoints, ticks = now())
        } else {
            existing.copy(total = existing.total + damagePoints, ticks = now())
        }

        lastHitCreatureId = attacker.id
    }

    /**
     * `Creature::getDamageRatio` (creature.cpp:1168-1190): the attacker's share
     * of the total damage recorded on the map, skipping the zero id and zero
     * totals.
     */
    fun getDamageRatio(attacker: Creature): Double {
        var totalDamage = 0L
        var attackerDamage = 0L

        for ((attackerId, damageInfo) in damageMap) {
            if (attackerId == 0L || damageInfo.total == 0) {
                continue
            }

            totalDamage += damageInfo.total
            if (attackerId == attacker.id) {
                attackerDamage += damageInfo.total
            }
        }

        if (totalDamage == 0L) {
            return 0.0
        }

        return attackerDamage.toDouble() / totalDamage
    }

    /**
     * `Creature::getGainedExperience` (creature.cpp:1192-1194):
     * `floor(getDamageRatio(attacker) * getLostExperience())`. The base
     * `getLostExperience` is zero; `Monster` and `Player` override both members.
     */
    open fun getGainedExperience(attacker: Creature): Long =
        floor(getDamageRatio(attacker) * getLostExperience()).toLong()

    /**
     * `Creature::hasBeenAttacked` (creature.cpp:830-836): the attacker is still
     * inside the `PZ_LOCKED` window.
     */
    fun hasBeenAttacked(attackerId: Long): Boolean {
        val attackInfo = damageMap[attackerId] ?: return false
        return (now() - attackInfo.ticks) <= pzLockedProvider()
    }

    /**
     * `g_configManager().getNumber(PZ_LOCKED)` (configmanager.cpp:340) seam for
     * [hasBeenAttacked]. The world composition binds the configured window; the
     * unbound default keeps the Canary config default.
     */
    var pzLockedProvider: () -> Int = { CreatureDeathPipeline.PZ_LOCKED_DEFAULT }

    /** `Creature::skull` (creature.hpp:925), default `SKULL_NONE`. */
    private var skull: Skulls_t = Skulls_t.SKULL_NONE

    /** `Creature::getSkull` (creature.hpp:168-170). */
    open fun getSkull(): Skulls_t = skull

    /**
     * `Creature::setSkull` (creature.cpp:100-103); the
     * `g_game().updateCreatureSkull` broadcast is a world side effect that lands
     * with the protocol senders.
     */
    fun setSkull(newSkull: Skulls_t) {
        skull = newSkull
    }

    /** `Creature::canSee(pos)` (`creature.cpp:89-91`). */
    open fun canSee(pos: Position): Boolean =
        canSee(position, pos, MAP_MAX_VIEW_PORT_X, MAP_MAX_VIEW_PORT_Y)

    /** `Creature::canSeeCreature` (`creature.cpp:93-98`). */
    open fun canSeeCreature(creature: Creature): Boolean =
        !(!canSeeInvisibility() && creature.isInvisible())

    /** `Creature::canSeeInvisibility` (creature.hpp:213-215); base is false. */
    open fun canSeeInvisibility(): Boolean = false

    /**
     * `Creature::isInGhostMode` (creature.hpp:212-214); base is false.
     * `Player::isInGhostMode` (player.hpp:566-569) overrides it with the
     * modelled `ghostMode` flag.
     */
    open fun isInGhostMode(): Boolean = false

    /**
     * `Creature::isInvisible` (`creature.cpp:1849-1853`): any stored
     * `CONDITION_INVISIBLE`, without an end-time check.
     */
    fun isInvisible(): Boolean = conditionList.any { it.type == ConditionType.CONDITION_INVISIBLE }

    /**
     * `Creature::m_master` / `Creature::m_summons` (creature.hpp:879, 482-484)
     * and the `summoned` latch (creature.hpp:890). Canary holds the master as a
     * `std::weak_ptr`; the port keeps strong references because world entities
     * are released through explicit removal (`setRemoved`), not GC reachability
     * (PARITY GAP).
     */
    private var master: Creature? = null
    private val summonList = mutableListOf<Creature>()
    private var summoned = false

    /**
     * `Creature::setMaster` (`creature.cpp:1339-1369`). The `reloadCreature`
     * branch (`setFollowCreature`/`setDropLoot`/`setSkillLoss`/`reloadCreature`)
     * belongs to the follow and protocol subsystems (PARITY GAP).
     */
    fun setMaster(newMaster: Creature?, reloadCreature: Boolean = false): Boolean {
        summoned = true
        val oldMaster = master

        if (newMaster == null && oldMaster == null) {
            return false
        }

        if (newMaster != null) {
            newMaster.summonList.add(this)
        }

        master = newMaster

        if (oldMaster != null) {
            oldMaster.summonList.remove(this)
        }

        return true
    }

    /** `Creature::removeMaster` (creature.hpp:462-466) — only resets the pointer. */
    fun removeMaster() {
        if (master != null) {
            master = null
        }
    }

    /** `Creature::isSummon` (creature.hpp:468-470). */
    fun isSummon(): Boolean = master != null

    /** `Creature::hasBeenSummoned` (creature.hpp:475-477). */
    fun hasBeenSummoned(): Boolean = summoned

    /** `Creature::getMaster` (creature.hpp:478-480). */
    fun getMaster(): Creature? = master

    /** `Creature::getSummons` (creature.hpp:482-484). */
    fun getSummons(): List<Creature> = summonList

    /**
     * `Monster::death`'s `m_summons.clear()` (monster.cpp:3256-3262): the C++
     * direct member clear; exposed to the death composition because the list is
     * private in the port.
     */
    internal fun clearSummons() {
        summonList.clear()
    }

    /** `Creature::getSummonCount` (creature.hpp:624-626). */
    fun getSummonCount(): Int = summonList.size

    /** `Creature::hasSummons` (creature.hpp:634-639). */
    fun hasSummons(): Boolean = summonList.isNotEmpty()

    /** `Creature::m_attackedCreature` (creature.hpp:878). */
    private var attackedCreature: Creature? = null

    /** `Creature::getAttackedCreature` (creature.hpp:438-440). */
    fun getAttackedCreature(): Creature? = attackedCreature

    /**
     * `Creature::setAttackedCreature` (`creature.cpp:1011-1036`). The
     * familiar-in-protection-zone rejection (`creature.cpp:1013-1017`) needs the
     * tile and monster-type state and is not ported (PARITY GAP).
     */
    open fun setAttackedCreature(creature: Creature?): Boolean {
        if (creature != null) {
            if (creature.position.z != position.z || !canSee(creature.position)) {
                attackedCreature = null
                return false
            }

            attackedCreature = creature
            onAttackedCreature(creature)
            creature.onAttacked()
        } else {
            attackedCreature = null
        }

        for (summon in summonList.toList()) {
            summon.setAttackedCreature(creature)
        }

        return true
    }

    /** `Creature::m_followCreature` (creature.hpp:918); Canary holds a `std::weak_ptr`. */
    private var followCreature: Creature? = null

    /** `Creature::getFollowCreature` (creature.hpp:423-425). */
    fun getFollowCreature(): Creature? = followCreature

    /** `Creature::isUpdatingPath` (creature.hpp:930). */
    var isUpdatingPath: Boolean = false

    /** `Creature::hasFollowPath` (creature.hpp:929). */
    var hasFollowPath: Boolean = false

    /** `Creature::forceUpdateFollowPath` (creature.hpp:931). */
    var forceUpdateFollowPath: Boolean = false

    /**
     * `Creature::setFollowCreature` (src/creatures/creature.cpp:1132-1166). The
     * walk-queue reset (`listWalkDir.clear()` / `onWalkAborted()`,
     * creature.cpp:1152-1155) belongs to the unported walk state (PARITY GAP).
     */
    open fun setFollowCreature(creature: Creature?): Boolean {
        if (creature != null) {
            if (getFollowCreature() === creature) {
                return true
            }

            if (hasCondition(ConditionType.CONDITION_FEARED)) {
                followCreature = null
                return false
            }

            val creaturePos = creature.position
            if (creaturePos.z != position.z || !canSee(creaturePos)) {
                followCreature = null
                return false
            }

            hasFollowPath = true
            forceUpdateFollowPath = false
            followCreature = creature
            isUpdatingPath = true
        } else {
            isUpdatingPath = false
            followCreature = null
        }

        onFollowCreature(creature)
        return true
    }

    /** `Creature::onFollowCreature` (creature.hpp:430-432) virtual hook. */
    open fun onFollowCreature(creature: Creature?) {
        // No behaviour.
    }

    /**
     * `Creature::iconChanged` (`creature.cpp:2020-2029`); the spectator
     * broadcast (`sendCreatureIcon`) is a world side effect owned by the
     * protocol senders.
     */
    open fun iconChanged() {
        // No behaviour without a world/protocol layer.
    }

    /** `Creature::creatureIcons` (creature.hpp:948) — a `std::map`, key ordered. */
    private val creatureIcons: MutableMap<String, CreatureIcon> = sortedMapOf()

    /** `Creature::getIcons` (creature.hpp:325-334). */
    open fun getIcons(): List<CreatureIcon> = creatureIcons.values.filter { it.isSet() }

    /** `Creature::getIcon` (creature.hpp:336-341). */
    open fun getIcon(key: String): CreatureIcon = creatureIcons[key] ?: CreatureIcon()

    /** `Creature::hasIcon` (creature.hpp:343-345). */
    fun hasIcon(key: String): Boolean = creatureIcons.containsKey(key)

    /** `Creature::setIcon` (creature.hpp:347-350). */
    fun setIcon(key: String, icon: CreatureIcon) {
        creatureIcons[key] = icon
        iconChanged()
    }

    /** `Creature::removeIcon` (creature.hpp:352-355). */
    fun removeIcon(key: String) {
        creatureIcons.remove(key)
        iconChanged()
    }

    /** `Creature::clearIcons` (creature.hpp:357-360). */
    fun clearIcons() {
        creatureIcons.clear()
        iconChanged()
    }

    /** `Creature::conditions` (creature.hpp:843) — kept in insertion order. */
    private val conditionList = mutableListOf<Condition>()

    /** Read-only view of the stored conditions for inspecting systems. */
    val conditions: List<Condition> get() = conditionList

    /**
     * `OTSYS_TIME()` millisecond clock. Injected per AGENTS.md 1.1 so condition
     * lifetime checks are deterministic; conditions keep the clock they were
     * created with.
     */
    open var now: () -> Long = { System.currentTimeMillis() }

    /**
     * `Creature::getCreature()` self-view handed to the condition engine.
     * Injected per AGENTS.md 1.1: when null, conditions still attach, tick and
     * expire, but engine side effects (healing, speed, icons) are skipped.
     */
    open var conditionTarget: ConditionTarget? = null

    /**
     * `Creature::addCondition` (creature.cpp:1372-1395). A condition with the
     * same `(type, id, subId)` already stored is refreshed through the stored
     * instance; otherwise it must `startCondition` before it is retained.
     */
    fun addCondition(condition: Condition?, attackerPlayer: Boolean = false): Boolean {
        if (condition == null) {
            return false
        }

        val type = condition.type
        if (isSuppress(type, attackerPlayer)) {
            return false
        }

        val prevCond = getCondition(type, condition.id, condition.subId)
        if (prevCond != null) {
            prevCond.addCondition(conditionTarget, condition)
            return true
        }

        if (condition.startCondition(conditionTarget)) {
            conditionList.add(condition)
            onAddCondition(type)
            return true
        }

        return false
    }

    /**
     * `Creature::addCombatCondition` (`creature.cpp:1393-1404`): a successful
     * [addCondition] (including a refresh of an existing type) runs the combat
     * hook afterwards.
     */
    fun addCombatCondition(condition: Condition?, attackerPlayer: Boolean = false): Boolean {
        if (condition == null) {
            return false
        }

        if (!addCondition(condition, attackerPlayer)) {
            return false
        }

        onAddCombatCondition(condition.type)
        return true
    }

    /**
     * `Creature::isSuppress` (creature.hpp:525-527) — the base creature never
     * suppresses. `Player::isSuppress` suppression counts and the
     * `MIN_DELAY_BETWEEN_CONDITIONS` gate (player.cpp:356-364) are not ported
     * (PARITY GAP).
     */
    open fun isSuppress(type: ConditionType, attackerPlayer: Boolean): Boolean = false

    /**
     * `Creature::onAddCondition` (creature.cpp:1216-1222): haste and paralyze
     * are mutually exclusive.
     */
    open fun onAddCondition(type: ConditionType) {
        if (type == ConditionType.CONDITION_PARALYZE && hasCondition(ConditionType.CONDITION_HASTE)) {
            removeCondition(ConditionType.CONDITION_HASTE)
        } else if (type == ConditionType.CONDITION_HASTE && hasCondition(ConditionType.CONDITION_PARALYZE)) {
            removeCondition(ConditionType.CONDITION_PARALYZE)
        }
    }

    /** `Creature::onAddCombatCondition` (creature.cpp:1224-1226) — base no-op hook. */
    open fun onAddCombatCondition(type: ConditionType) {
        // No behaviour.
    }

    /** `Creature::onEndCondition` (creature.cpp:1228-1230) — base no-op hook. */
    open fun onEndCondition(type: ConditionType) {
        // No behaviour.
    }

    /**
     * `Creature::onIdleStatus` (`creature.cpp:210-215`): a living creature
     * drops every damage-map entry and forgets the last hit. The base body is
     * the virtual seam `Player` overrides.
     */
    open fun onIdleStatus() {
        if (health > 0) {
            damageMap.clear()
            lastHitCreatureId = 0
        }
    }

    /** `Creature::removeCondition(type)` (creature.cpp:1412-1433). */
    fun removeCondition(type: ConditionType) {
        val iterator = conditionList.iterator()
        while (iterator.hasNext()) {
            val condition = iterator.next()
            if (condition.type != type) {
                continue
            }

            iterator.remove()
            condition.endCondition(conditionTarget)
            onEndCondition(type)
        }
    }

    /**
     * `Creature::removeCondition(type, conditionId, force)` (creature.cpp:1435-1467).
     *
     * The Canary paralysed walk-delay reschedule (`g_dispatcher().scheduleEvent`,
     * 1450-1458) is not ported, so removal always applies immediately; `force`
     * is kept for signature parity (PARITY GAP).
     */
    fun removeCondition(conditionType: ConditionType, conditionId: ConditionId, force: Boolean = false) {
        val iterator = conditionList.iterator()
        while (iterator.hasNext()) {
            val condition = iterator.next()
            if (condition.type != conditionType || condition.id != conditionId) {
                continue
            }

            iterator.remove()
            condition.endCondition(conditionTarget)
            onEndCondition(conditionType)
        }
    }

    /** `Creature::removeCondition(condition)` (creature.cpp:1486-1498). */
    fun removeCondition(condition: Condition) {
        if (!conditionList.remove(condition)) {
            return
        }

        condition.endCondition(conditionTarget)
        onEndCondition(condition.type)
    }

    /** `Creature::getCondition(type)` (creature.cpp:1500-1511). */
    fun getCondition(type: ConditionType): Condition? = conditionList.firstOrNull { it.type == type }

    /** `Creature::getCondition(type, conditionId, subId)` (creature.cpp:1513-1525). */
    fun getCondition(type: ConditionType, conditionId: ConditionId, subId: Int = 0): Condition? =
        conditionList.firstOrNull { it.type == type && it.id == conditionId && it.subId == subId }

    /** `Creature::getConditionsByType(type)` (creature.cpp:1551-1563). */
    fun getConditionsByType(type: ConditionType): List<Condition> =
        conditionList.filter { it.type == type }

    /**
     * `Creature::executeConditions(interval)` (creature.cpp:1565-1583). Every
     * stored condition ticks; a rejected tick ends and drops it.
     */
    fun executeConditions(interval: Int) {
        val iterator = conditionList.iterator()
        while (iterator.hasNext()) {
            val condition = iterator.next()
            if (!condition.executeCondition(conditionTarget, interval)) {
                iterator.remove()
                condition.endCondition(conditionTarget)
                onEndCondition(condition.type)
            }
        }
    }

    /** `Creature::hasCondition(type, subId)` (creature.cpp:1585-1605). */
    fun hasCondition(type: ConditionType, subId: Int = 0): Boolean {
        if (isSuppress(type, false)) {
            return false
        }

        val timeNow = now()
        return conditionList.any {
            it.type == type && it.subId == subId && (it.endTime >= timeNow || it.ticks == -1)
        }
    }

    /**
     * `Creature::onThink` scripting lambda (src/creatures/creature.cpp:157-163):
     * executes the `think` creature events attached to this creature through
     * [registerCreatureEvent], in attachment order. Canary runs the loop inline;
     * the port routes the Lua dispatch through the composition root's
     * `ScriptingDispatch.creatureOnThink`, which confines the callback to the
     * WorldCommit lane (AGENTS.md 1.2). The unbound default keeps the loop
     * inert for embedded/test roots without a Lua environment.
     *
     * CALL-SITE NEED (files outside this wave's ownership): the network
     * composition root (`CanaryServer`/`GameHandler`, where `scriptingDispatch`
     * lives) must bind this for every creature entering the world, e.g.
     * `creature.onThinkEventsHost = { c, interval -> scriptingDispatch.creatureOnThink(c, interval.toLong()) }`.
     * Until then the production loop stays inert (PARITY GAP).
     */
    var onThinkEventsHost: (Creature, Int) -> Unit = { _, _ -> }

    /**
     * `Creature::onThink` (creature.cpp:128-172) folded with the condition tick
     * `Game::checkCreatures` performs on the creature right after it
     * (game.cpp:7726-7733). The attached `think` events run before the
     * condition tick, exactly like the C++ `onThink` lambda that
     * `Game::checkCreatures` calls before `executeConditions`; the
     * follow/block steps of the C++ body belong to their own subsystems in
     * this port.
     */
    open fun onThink(interval: Int) {
        onThinkEventsHost(this, interval)
        executeConditions(interval)
    }

    // --- combat event hooks (creature.hpp:552-566) --------------------------------

    /**
     * Listener seams replacing the scripting dispatch Canary performs around the
     * virtual hooks (AGENTS.md 1.1): the C++ base bodies are empty
     * (`creature.cpp:1273-1275`, creature.hpp:558, 561) and the Lua layer is
     * driven through `ScriptingDispatch`; the creature itself only owns the
     * registration the port needs to observe them.
     */
    private val onAttackedHandlers = mutableListOf<(Creature) -> Unit>()
    private val onAttackedCreatureHandlers = mutableListOf<(Creature, Creature?) -> Unit>()
    private val onTargetCreatureGainHealthHandlers = mutableListOf<(Creature, Creature?, Int) -> Unit>()
    private val onDeathHandlers = mutableListOf<(Creature) -> Unit>()

    /** Registers a listener for [onAttacked]. */
    fun registerOnAttackedHandler(handler: (Creature) -> Unit) {
        onAttackedHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnAttackedHandler]. */
    fun unregisterOnAttackedHandler(handler: (Creature) -> Unit) {
        onAttackedHandlers.remove(handler)
    }

    /** Registers a listener for [onAttackedCreature]. */
    fun registerOnAttackedCreatureHandler(handler: (Creature, Creature?) -> Unit) {
        onAttackedCreatureHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnAttackedCreatureHandler]. */
    fun unregisterOnAttackedCreatureHandler(handler: (Creature, Creature?) -> Unit) {
        onAttackedCreatureHandlers.remove(handler)
    }

    /** Registers a listener for [onTargetCreatureGainHealth]. */
    fun registerOnTargetCreatureGainHealthHandler(handler: (Creature, Creature?, Int) -> Unit) {
        onTargetCreatureGainHealthHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnTargetCreatureGainHealthHandler]. */
    fun unregisterOnTargetCreatureGainHealthHandler(handler: (Creature, Creature?, Int) -> Unit) {
        onTargetCreatureGainHealthHandlers.remove(handler)
    }

    /** Registers a listener for [onDeath]. */
    fun registerOnDeathHandler(handler: (Creature) -> Unit) {
        onDeathHandlers.add(handler)
    }

    /** Removes a listener registered with [registerOnDeathHandler]. */
    fun unregisterOnDeathHandler(handler: (Creature) -> Unit) {
        onDeathHandlers.remove(handler)
    }

    /** `Creature::onAttacked` (`creature.cpp:1273-1275`). */
    open fun onAttacked() {
        onAttackedHandlers.toList().forEach { it(this) }
    }

    /** `Creature::onAttackedCreature` (creature.hpp:558) — the target hook. */
    open fun onAttackedCreature(target: Creature?) {
        onAttackedCreatureHandlers.toList().forEach { it(this, target) }
    }

    /** `Creature::onTargetCreatureGainHealth` (creature.hpp:561). */
    open fun onTargetCreatureGainHealth(target: Creature?, points: Int) {
        onTargetCreatureGainHealthHandlers.toList().forEach { it(this, target, points) }
    }

    /**
     * `Creature::onAttackedCreatureDrainHealth` (`creature.cpp:1277-1283`):
     * records the drain on the target's damage map through [addDamagePoints],
     * keyed by this creature's id, and stamps [lastHitCreatureId].
     *
     * `Player::onAttackedCreatureDrainHealth`'s party-tick override
     * (player.cpp:6563-6575) is the `PlayerCombatHooksState.kt` extension seam;
     * it is not hooked into this virtual dispatch (PARITY GAP, mirroring
     * [onTargetCreatureGainHealth]).
     */
    open fun onAttackedCreatureDrainHealth(target: Creature?, points: Int) {
        if (target == null || points <= 0) {
            return
        }

        target.addDamagePoints(this, points)
    }

    /**
     * `CreatureEvent::executeOnDeath` argument set
     * (src/lua/creature/creatureevent.cpp:292-326) carried by the
     * `Creature::dropCorpse` death-event loop (src/creatures/creature.cpp:819-824):
     * `(creature, corpse, killer, mostDamageKiller, lastHitUnjustified,
     * mostDamageUnjustified)`, exactly the arguments the Lua `onDeath` callback
     * receives. The events attached through [registerCreatureEvent] execute in
     * attachment order, after the corpse exists and before `Creature::death`
     * (creature.cpp:720-722). [deathPipeline] fires the loop with the resolved
     * corpse and killers; a creature outside a world keeps the inert defaults.
     * The network composition root (`CanaryServer`/`GameHandler`) binds the
     * world `ScriptingDispatch` for every creature entering the world; the
     * unbound default keeps the loop inert for embedded/test roots without a
     * Lua environment.
     */
    var onDeathEventsHost: (Creature, Item?, Creature?, Creature?, Boolean, Boolean) -> Unit =
        { _, _, _, _, _, _ -> }

    /**
     * `Creature::onDeath` world composition
     * (`game/death/CreatureDeathPipeline.kt`, `creature.cpp:579-720`): the
     * damage-map last-hit/most-damage resolution, the `dropCorpse` corpse/loot
     * pipeline, the experience map and the kill hooks. The world composition
     * (`Game.internalPlaceCreature`) binds one pipeline per creature; a creature
     * outside a world (embedded/tests) keeps the inert tail and no resolution.
     */
    var deathPipeline: CreatureDeathPipeline? = null

    /**
     * `Creature::onDeath` (creature.cpp:577-720). The registered death
     * listeners run first (the port's observation seam), then the bound
     * [deathPipeline] runs the C++ damage-map/corpse/experience body. Without a
     * bound pipeline the portable tail only fires the death-event loop with the
     * inert payload, the `death(lastHitCreature)` hook and `removeMaster()`
     * (`creature.cpp:671-673`).
     */
    fun onDeath() {
        onDeathHandlers.toList().forEach { it(this) }

        val pipeline = deathPipeline
        if (pipeline == null) {
            onDeathEventsHost(this, null, null, null, false, false)
            death(null)
            if (getMaster() != null) {
                removeMaster()
            }
            return
        }

        pipeline.onDeath(this)
    }

    /** `Creature::death` (creature.hpp:968) — virtual base no-op hook. */
    open fun death(lastHitCreature: Creature? = null) {
        // No behaviour.
    }

    /**
     * `Creature::getRace` (creature.hpp:165-167) — the base returns `RACE_NONE`,
     * so a plain creature drops no splash; `Monster` overrides it with
     * `m_monsterType->info.race` (monster.cpp:1386-1388).
     */
    open fun getRace(): RaceType = RaceType.RACE_NONE

    /**
     * `Creature::getCorpse` (creature.cpp:838-843). The C++ base creates
     * `Item::CreateItem(getLookCorpse())` and returns `null` for the zero id;
     * the port keeps the base body inert because no ported family relies on it
     * (`Monster` overrides it with `m_monsterType->info.lookcorpse` and the
     * owner stamp, monster.cpp:3305-3320; the player corpse
     * description/container stays with the reduced player-death path,
     * PARITY GAP).
     */
    open fun getCorpse(lastHitCreature: Creature?, mostDamageCreature: Creature?): Item? = null

    /** `Creature::dropLoot` (creature.hpp:963) — base no-op; `Monster` drops the loot table. */
    open fun dropLoot(corpse: Container?, lastHitCreature: Creature?) {
        // No behaviour.
    }

    companion object {
        /** `MAP_MAX_CLIENT_VIEW_PORT_X/Y` (`src/map/map_const.hpp:12-13`). */
        const val MAP_MAX_CLIENT_VIEW_PORT_X = 8
        const val MAP_MAX_CLIENT_VIEW_PORT_Y = 6

        /** `MAP_MAX_VIEW_PORT_X/Y` (`src/map/map_const.hpp:14-15`). */
        const val MAP_MAX_VIEW_PORT_X = MAP_MAX_CLIENT_VIEW_PORT_X + 3
        const val MAP_MAX_VIEW_PORT_Y = MAP_MAX_CLIENT_VIEW_PORT_Y + 5

        /** `MAP_INIT_SURFACE_LAYER` (`src/map/map_const.hpp:18`). */
        const val MAP_INIT_SURFACE_LAYER = 7

        /** `MAP_LAYER_VIEW_LIMIT` (`src/map/map_const.hpp:19`). */
        const val MAP_LAYER_VIEW_LIMIT = 2

        /** `PLAYER_MAX_SPEED` (`src/creatures/players/player.hpp:125`). */
        const val PLAYER_MAX_SPEED = 65535

        /** `WALK_FLOOR_CHANGE_EXTRA_COST` (`src/creatures/creature.hpp:44`). */
        const val WALK_FLOOR_CHANGE_EXTRA_COST = 2

        /** `WALK_DIAGONAL_EXTRA_COST` (`src/creatures/creature.hpp:45`). */
        const val WALK_DIAGONAL_EXTRA_COST = 3

        /**
         * `Creature::canSee(myPos, pos, viewRangeX, viewRangeY)`
         * (`creature.cpp:68-87`).
         */
        fun canSee(myPos: Position, pos: Position, viewRangeX: Int, viewRangeY: Int): Boolean {
            if (myPos.z <= MAP_INIT_SURFACE_LAYER) {
                // On ground level or above (7 -> 0) the view only reaches down
                // to the surface layer.
                if (pos.z > MAP_INIT_SURFACE_LAYER) {
                    return false
                }
            } else if (myPos.z >= MAP_INIT_SURFACE_LAYER + 1) {
                // Underground the view is +/- MAP_LAYER_VIEW_LIMIT floors.
                if (kotlin.math.abs(myPos.z - pos.z) > MAP_LAYER_VIEW_LIMIT) {
                    return false
                }
            }

            val offsetz = myPos.z - pos.z
            return pos.x >= myPos.x - viewRangeX + offsetz &&
                pos.x <= myPos.x + viewRangeX + offsetz &&
                pos.y >= myPos.y - viewRangeY + offsetz &&
                pos.y <= myPos.y + viewRangeY + offsetz
        }
    }
}

class Monster(
    id: Long,
    name: String,
    maxHealth: Int,
    health: Int,
    maxMana: Int = 0,
    mana: Int = 0,
    speed: Int = 200,
    position: Position
) : Creature(id, name, maxHealth, health, maxMana, mana, speed, position) {

    /**
     * `MonsterPipelinesHost.onCreatureAppear`/`onRemoveCreature`/`onCreatureMove`
     * coordinator seams for the non-Lua virtual-hook tails
     * (monster.cpp:348-477). Bound where the world pipelines are composed
     * (AGENTS.md 1.1); an unbound monster keeps the tail inert.
     */
    var onCreatureAppearHost: (Creature, Boolean) -> Unit = { _, _ -> }
    var onRemoveCreatureHost: (Creature, Boolean) -> Unit = { _, _ -> }
    var onCreatureMoveHost: (Creature, Position, Position) -> Unit = { _, _, _ -> }

    /** `MonsterBehavior::hasExtraSwing` state projection (monster.cpp:2091-2093). */
    var hasExtraSwingProvider: () -> Boolean = { false }

    /**
     * `Monster::m_monsterType` (src/creatures/monsters/monster.hpp:363)
     * `info.pushable` read by [isPushable]. Bound by
     * `MonsterPipelinesHost.bindMonsterHooks` when the monster registers, so
     * the flag stays live like the C++ pointer dereference; the unbound
     * default keeps the `MonsterType::info.pushable` seed (`true`,
     * src/creatures/monsters/monsters.hpp) for a directly constructed monster.
     */
    var isPushableProvider: () -> Boolean = { true }

    /** `Monster::isPushable()` (src/creatures/monsters/monster.cpp:275-277): `m_monsterType->info.pushable && baseSpeed != 0`. */
    override fun isPushable(): Boolean = isPushableProvider() && getBaseSpeed() != 0

    /** `Monster::soulPit` (monster.hpp:466). */
    private var soulPit: Boolean = false

    /** `Monster::getSoulPit` (monster.cpp:3193-3195). */
    fun getSoulPit(): Boolean = soulPit

    /** `Monster::setSoulPit` (monster.cpp:3197-3199). */
    fun setSoulPit(value: Boolean) {
        soulPit = value
    }

    /** `Monster::onCreatureAppear` tail (monster.cpp:348-389). */
    override fun onCreatureAppear(creature: Creature, isLogin: Boolean) {
        super.onCreatureAppear(creature, isLogin)
        onCreatureAppearHost(creature, isLogin)
    }

    /** `Monster::onRemoveCreature` tail (monster.cpp:391-432). */
    override fun onRemoveCreature(creature: Creature, isLogout: Boolean) {
        super.onRemoveCreature(creature, isLogout)
        onRemoveCreatureHost(creature, isLogout)
    }

    /** `Monster::onCreatureMove` tail (monster.cpp:434-477). */
    override fun onCreatureMove(
        creature: Creature,
        newTile: Tile?,
        newPos: Position,
        oldTile: Tile?,
        oldPos: Position,
        teleport: Boolean,
    ) {
        super.onCreatureMove(creature, newTile, newPos, oldTile, oldPos, teleport)
        onCreatureMoveHost(creature, oldPos, newPos)
    }

    /** `Monster::hasExtraSwing` (monster.cpp:2091-2093). */
    override fun hasExtraSwing(): Boolean = hasExtraSwingProvider()

    /**
     * Live death-side facts and world sinks of this monster
     * ([MonsterDeathHost]): `Monster::getRace`/`getLookCorpse`/
     * `getLostExperience`/`getCorpse`/`dropLoot`/`death`
     * (monster.cpp:873-881, 1386-1388, 3227-3436). The world composition
     * (`Game.internalPlaceCreature` over the registered host entry) binds one
     * instance per placed monster; an unbound monster keeps the inert values so
     * an embedded monster drops no corpse and awards no experience.
     */
    var monsterDeathHost: MonsterDeathHost? = null

    /** `Monster::getRace` (monster.cpp:1386-1388) over `m_monsterType->info.race`. */
    override fun getRace(): RaceType = monsterDeathHost?.race ?: RaceType.RACE_NONE

    /** `Monster::getLookCorpse` (monster.cpp:878-881) over `m_monsterType->info.lookcorpse`. */
    fun getLookCorpse(): Int = monsterDeathHost?.lookCorpse ?: 0

    /**
     * `Monster::getLostExperience` (monster.cpp:873-876): a skill-loss monster
     * returns `round(info.experience * (forgeStack + 10) / 10)` up to stack 15,
     * then `info.experience * 28` (see [MonsterThink.getLostExperience]).
     */
    override fun getLostExperience(): Long {
        val host = monsterDeathHost ?: return 0L
        return MonsterThink.getLostExperience(host.forgeStack(), skillLoss, host.experience)
    }

    /** `Monster::getCorpse` (monster.cpp:3305-3320) stamping the most-damage player as `CORPSEOWNER`. */
    override fun getCorpse(lastHitCreature: Creature?, mostDamageCreature: Creature?): Item? {
        val host = monsterDeathHost ?: return null
        return MonsterBehavior.getCorpse(
            lookCorpse = host.lookCorpse,
            createCorpse = host.createCorpse,
            mostDamageCreature = mostDamageCreature?.let { targetCreatureFactsOf(it) },
        )
    }

    /** `Monster::dropLoot` (monster.cpp:3415-3436) over the live loot table. */
    override fun dropLoot(corpse: Container?, lastHitCreature: Creature?) {
        val host = monsterDeathHost ?: return
        MonsterLoot.dropLoot(corpse, host.lootRequest(), host.nextRoll, host.nextSliverCount)
    }

    /**
     * `Monster::death` (monster.cpp:3227-3303): forge removal, summon drain,
     * target/friend list clear, idle status, the dead latch, the death sound and
     * the player-progression tail, run after `Creature::dropCorpse`.
     */
    override fun death(lastHitCreature: Creature?) {
        monsterDeathHost?.die(this, lastHitCreature)
    }

    /**
     * `MonsterPipelinesHost.onExecuteAsyncTasks` coordinator seam for
     * `Monster::onExecuteAsyncTasks` (monster.cpp:3972-3996), bound where the
     * world pipelines are composed (AGENTS.md 1.1); an unbound monster keeps
     * the tail inert.
     *
     * CALL-SITE NEED: `MonsterPipelinesHost.bindMonsterHooks`
     * (MonsterPipelinesHost.kt:280-294) must bind this to
     * `onExecuteAsyncTasks(monsterId)` under the existing `isCurrentOwner`
     * guard, next to the `onCreatureAppearHost`/`onRemoveCreatureHost` binds.
     */
    var onExecuteAsyncTasksHost: () -> Unit = {}

    /** `Monster::onExecuteAsyncTasks` (monster.cpp:3972-3996). */
    override fun onExecuteAsyncTasks() {
        onExecuteAsyncTasksHost()
    }
}

enum class FightMode {
    Attack,
    Balanced,
    Defense
}

enum class ChaseMode {
    StandStill,
    Follow
}

/** `Blessings` (`src/enums/player_blessings.hpp:16-25`). */
enum class Blessings(val value: Int) {
    TwistOfFate(1),
    TheWisdomOfSolitude(2),
    TheSparkOfThePhoenix(3),
    TheFireOfTheSuns(4),
    TheSpiritualShielding(5),
    TheEmbraceOfTibia(6),
    HearthOfTheMountain(7),
    BloodOfTheMountain(8),
}

/** `Virtue_t` (`src/creatures/creatures_definitions.hpp:44-49`). */
enum class Virtue_t(val value: Int) {
    None(0),
    Harmony(1),
    Justice(2),
    Sustain(3),
}

/** `OpenContainer` (`src/creatures/players/player.hpp:118-121`). */
class OpenContainer(
    var container: Container?,
    var index: Int = 0,
)

class Player(
    id: Long,
    name: String,
    maxHealth: Int,
    health: Int,
    maxMana: Int = 0,
    mana: Int = 0,
    var capacity: Double = 400.0,
    speed: Int = 220,
    position: Position,
    var experience: Long = 0,
    var level: Int = 1,
    var fightMode: FightMode = FightMode.Attack,
    var chaseMode: ChaseMode = ChaseMode.StandStill,
    var secureMode: Boolean = true,
    var targetCreatureId: Long? = null,
    var inCombat: Boolean = false,
    /**
     * `Player::muteCountMap` (player.hpp:281): Canary keeps it as a static map
     * shared by every player. Per AGENTS.md 1.1 the static is replaced by the
     * composition root's instance, injected here (and forwarded to [features]);
     * direct construction (tests/tooling) keeps a per-player default.
     */
    val muteCountMap: MutableMap<Long, Int> = mutableMapOf(),
    /**
     * `g_game().transformItem(item, item->getID(), charges - 1)` seam
     * (player.cpp:1065, 7588, 7697, 7718) forwarded to [features]; the
     * composition root binds the single world lambda over its `Game`.
     */
    val transformItemCharges: (Item) -> Unit = {},
    /**
     * `IOLoginData` VIP persistence (`account_viplist`, `account_vipgroups`,
     * `account_vipgrouplist`; iologindata.cpp:383-497) forwarded to
     * [PlayerFeatures.vipRepository]: the composition root injects the JDBC
     * repository so the `PlayerVIP` mutators (player_vip.cpp:69-219) persist,
     * while direct construction (tests/tooling) keeps a per-player in-memory
     * default self-contained.
     */
    val vipRepository: VipRepository = InMemoryVipRepository(),
) : Creature(id, name, maxHealth, health, maxMana, mana, speed, position), BatchUpdateActor {

    /**
     * `Player::vocation` (player.hpp). Canary stores the internal [Vocations] id
     * (0..10); without a wired registry this Kotlin port historically held a CIP
     * id directly, so [getPlayerVocationEnum] passes it through unchanged. The
     * getter is renamed at the JVM level so it does not clash with the
     * Canary-parity [getVocationId] method.
     */
    @get:JvmName("getVocationIdValue")
    var vocationId: Int = 0

    /**
     * `Vocations` registry used by [getPlayerVocationEnum] to normalize the
     * internal vocation id to the CIP client id. Canary resolves this through the
     * global `g_vocations()`; per AGENTS.md 1.1 it is injected here and defaults to
     * absent, which keeps [getPlayerVocationEnum] a pass-through.
     */
    var vocations: Vocations? = null

    /** Port's `PlayerFlags_t::IsAlwaysPremium` seam; see [isPremium] (player.cpp:7533). */
    var premium: Boolean = false

    /** `Player::account` (player.hpp:1756); premium time is derived from it. */
    var account: Account? = null

    /** `g_configManager().getBoolean(FREE_PREMIUM)` (configmanager.cpp:103). */
    var freePremium: Boolean = false

    /** `std::shared_ptr<Group> group` (player.hpp:1759); `Player::isAccessPlayer()` reads `group->access`. */
    var group: Group? = null

    /**
     * `g_configManager().getNumber(MAX_MESSAGEBUFFER)` (configmanager.cpp:315)
     * host value read by `Player::removeMessageBuffer` (player.cpp:3514-3543)
     * through [features]. The composition root sets it from the config; `0`
     * keeps the mute pipeline inert.
     */
    var maxMessageBuffer: Int = 0

    val features: PlayerFeatures = PlayerFeatures(
        playerId = id,
        levelProvider = { level },
        vocationIdProvider = { vocationId },
        isPremiumProvider = { isPremium() },
        hasAccessProvider = { isAccessPlayer() },
        wheelPlayer = this,
        vocationRequirementsProvider = {
            vocations?.getVocation(vocationId)
                ?.let { VocationRequirementsAdapter(it) }
                ?: UnwiredVocationRequirements
        },
        cyclopediaPlayer = PlayerCyclopediaAdapter(this),
        cyclopediaTrackerClient = { cyclopediaMonsterTrackerClient },
        maxMessageBuffer = { maxMessageBuffer },
        muteCountMap = muteCountMap,
        transformItemCharges = transformItemCharges,
        vipRepository = vipRepository,
    )

    /**
     * `Player::getPlayerVocationEnum()` (player.cpp:13030): maps the internal
     * vocation id through the [Vocations] registry's `clientid` to the CIP
     * `Vocation_t` id (1..5). Without a wired registry the raw [vocationId] is
     * returned unchanged, preserving the previous behavior.
     */
    fun getPlayerVocationEnum(): Int {
        val registry = vocations ?: return vocationId
        val vocation = registry.getVocation(vocationId) ?: return Vocation.VOCATION_NONE
        return when (vocation.getClientId()) {
            1, 11 -> Vocation.VOCATION_KNIGHT_CIP
            2, 12 -> Vocation.VOCATION_PALADIN_CIP
            3, 13 -> Vocation.VOCATION_SORCERER_CIP
            4, 14 -> Vocation.VOCATION_DRUID_CIP
            5, 15 -> Vocation.VOCATION_MONK_CIP
            else -> Vocation.VOCATION_NONE
        }
    }

    /** `Player::getVocationId()` (player.cpp:201-203): `vocation->getId()`. */
    fun getVocationId(): Int = vocationId

    /** `Player::m_batching` (player.hpp:1973). */
    private var mBatching = 0

    /** `Player::beginBatchUpdate()` (player.cpp:9140-9142). */
    override fun beginBatchUpdate() {
        ++mBatching
    }

    /**
     * `Player::endBatchUpdate()` (player.cpp:9144-9156). The final `updateState()`
     * and `closeContainersOutOfRange()` calls are protocol seams that the Kotlin
     * player does not model yet (PARITY GAP).
     */
    override fun endBatchUpdate() {
        if (mBatching == 0) {
            return
        }

        --mBatching
        if (mBatching > 0) {
            return
        }
    }

    /** `Player::isBatching()` (player.hpp:772-774). */
    fun isBatching(): Boolean = mBatching > 0

    /**
     * `Player::isPromoted()` (player.cpp:7341-7344). A vocation is promoted when
     * no other vocation promotes from it: the injected [Vocations] registry
     * returns `VOCATION_NONE` for the promoted base vocations. Without a wired
     * registry the lookup yields `VOCATION_NONE` exactly like an empty registry,
     * so any non-`VOCATION_NONE` id reads as promoted.
     */
    fun isPromoted(): Boolean {
        val promotedVocation = vocations?.getPromotedVocation(vocationId) ?: Vocation.VOCATION_NONE
        return promotedVocation == Vocation.VOCATION_NONE && vocationId != promotedVocation
    }

    /**
     * `Player::isPremium()` (player.cpp:7532). Mirrors Canary: the FREE_PREMIUM
     * config and the always-premium flag short-circuit true, otherwise the
     * account's premium time decides. [premium] is the port's
     * `PlayerFlags_t::IsAlwaysPremium` seam.
     */
    fun isPremium(now: Long = getTimeNow()): Boolean {
        if (freePremium || premium) {
            return true
        }
        val account = account ?: return false
        return account.getPremiumRemainingDays(now) > 0 || account.getPremiumLastDay() > now
    }

    /** `Player::isAccessPlayer()` (player.cpp:7524). */
    fun isAccessPlayer(): Boolean = group?.access ?: false

    /** `Player::ghostMode` (player.hpp:1868). */
    var ghostMode: Boolean = false

    /** `Player::isInGhostMode()` (player.hpp:566-569). */
    override fun isInGhostMode(): Boolean = ghostMode

    /** `Player::switchGhostMode()` (player.hpp:571-573). */
    fun switchGhostMode() {
        ghostMode = !ghostMode
    }

    /**
     * `Player::canSee(pos)` (player.cpp:1402-1407): a clientless player sees
     * nothing, otherwise the attached client's viewport decides. This replaces
     * the [Creature.canSee] map viewport (`MAP_MAX_VIEW_PORT_X/Y` = 11/11)
     * with `ProtocolGame::canSee` (protocolgame.cpp:2392-2414): the asymmetric
     * `MAP_MAX_CLIENT_VIEW_PORT_X/Y` = 8/6 window with the +1 east/south
     * margin, so the attack/follow sight gates are tighter for players.
     */
    override fun canSee(pos: Position): Boolean =
        hasClient() && CreaturePacketWriter.canSee(this, pos)

    /** `Player::canSeeInvisibility()` (player.cpp:12989-12991). */
    override fun canSeeInvisibility(): Boolean =
        hasFlag(PlayerFlags_t.CanSenseInvisibility) || isAccessPlayer()

    /**
     * `Player::canSeeCreature` (player.cpp:1409-1423). The player always sees
     * themselves, a ghost creature stays hidden unless the observer has
     * `group->access`, and invisibility hides only non-player creatures when
     * the observer lacks `canSeeInvisibility`.
     */
    override fun canSeeCreature(creature: Creature): Boolean {
        if (creature === this) {
            return true
        }

        if (creature.isInGhostMode() && !isAccessPlayer()) {
            return false
        }

        if (creature !is Player && !canSeeInvisibility() && creature.isInvisible()) {
            return false
        }
        return true
    }

    /**
     * `Player::getStepSpeed()` (player.hpp:1909-1913):
     * `max(PLAYER_MIN_SPEED, min(maxStepSpeed, getSpeed()))` with the
     * `PLAYER_MAX_STAFF_SPEED` cap (both max constants are 65535,
     * player.hpp:125-127).
     */
    override fun getStepSpeed(): Int {
        val maxStepSpeed =
            if (hasFlag(PlayerFlags_t.SetMaxSpeed)) PLAYER_MAX_STAFF_SPEED else PLAYER_MAX_SPEED
        return maxOf(PLAYER_MIN_SPEED, minOf(maxStepSpeed, getSpeed()))
    }

    /** `Player::getAccountType()` (player.cpp:12627-12629): `NORMAL` without an account. */
    fun getAccountType(): AccountType = account?.accountType ?: AccountType.ACCOUNT_TYPE_NORMAL

    /** `Player::getGUID()`; the port's identity is [id]. */
    fun getGUID(): Long = id

    /** `Player::sex` (player.hpp:1853), default `PLAYERSEX_FEMALE`. */
    private var sex: PlayerSex_t = PlayerSex_t.FEMALE

    /** `Player::getSex()` (player.hpp:610-612). */
    fun getSex(): PlayerSex_t = sex

    /** `Player::setSex()` (player.cpp:7108-7110). */
    fun setSex(newSex: PlayerSex_t) {
        sex = newSex
    }

    /** `Player::pronoun`; `PLAYERPRONOUN_UNSET` until set. */
    private var pronoun: PlayerPronoun_t = PlayerPronoun_t.PLAYERPRONOUN_UNSET

    /** `Player::getPronoun()` (player.hpp:613-615). */
    fun getPronoun(): PlayerPronoun_t = pronoun

    /** `Player::setPronoun()` (player.cpp:7112-7114). */
    fun setPronoun(newPronoun: PlayerPronoun_t) {
        pronoun = newPronoun
    }

    /**
     * `Player::getPossessivePronoun()` (player.cpp:7096-7098) through
     * `getPlayerPossessivePronoun` (tools.cpp:1929-1944). Read by the private
     * chat channel invite message (chat.cpp:49).
     */
    fun getPossessivePronoun(): String = when (pronoun) {
        PlayerPronoun_t.PLAYERPRONOUN_THEY -> "their"
        PlayerPronoun_t.PLAYERPRONOUN_SHE -> "her"
        PlayerPronoun_t.PLAYERPRONOUN_HE -> "his"
        PlayerPronoun_t.PLAYERPRONOUN_ZE -> "zir"
        PlayerPronoun_t.PLAYERPRONOUN_NAME -> "$name's"
        PlayerPronoun_t.PLAYERPRONOUN_UNSET -> if (sex == PlayerSex_t.FEMALE) "her" else "his"
    }

    /** `Player::guild` (player.hpp:1851). */
    private var mGuild: Guild? = null

    /** `Player::getGuild()` (player.cpp:9312-9314). */
    fun getGuild(): Guild? = mGuild

    /**
     * `Player::setGuild()` (player.cpp:9316-9347). The `ExpertPvp` broadcast is
     * not ported (PARITY GAP); the port resolves the level-1 rank through the
     * injected [Guild] instead of Canary's data-loaded rank table.
     */
    fun setGuild(newGuild: Guild?) {
        if (newGuild === mGuild) {
            return
        }

        mGuild?.removeMember(this)
        mGuild = null
        mGuildRank = null

        if (newGuild != null) {
            val rank = newGuild.getRankByLevel(1) ?: return
            mGuild = newGuild
            mGuildRank = rank
            newGuild.addMember(this, rank)
        }
    }

    /** `Player::guildRank` (player.hpp:1852). */
    private var mGuildRank: GuildRank? = null

    /** `Player::getGuildRank()` (player.cpp:9349-9351). */
    fun getGuildRank(): GuildRank? = mGuildRank

    /** `Player::setGuildRank()` (player.cpp:9353-9355). */
    fun setGuildRank(newGuildRank: GuildRank?) {
        mGuildRank = newGuildRank
    }

    /** `Player::exivaRestrictions` (player.hpp:1862). */
    @get:JvmName("getExivaRestrictionsValue")
    val exivaRestrictions: ExivaRestrictions = ExivaRestrictions()

    /** `Player::getExivaRestrictions()` (player.cpp:13440-13446). */
    fun getExivaRestrictions(): ExivaRestrictions = exivaRestrictions

    /**
     * `Player::isMuted()` (player.cpp:3494-3506): the longest remaining
     * `CONDITION_MUTED` in seconds, or 0 for `CannotBeMuted` players.
     */
    fun isMuted(): Long {
        if (hasFlag(PlayerFlags_t.CannotBeMuted)) {
            return 0
        }

        var muteTicks = 0
        for (condition in conditions) {
            if (condition.type == ConditionType.CONDITION_MUTED && condition.ticks > muteTicks) {
                muteTicks = condition.ticks
            }
        }
        return (muteTicks / 1000).toLong()
    }

    /**
     * `Player::removeMessageBuffer` (player.cpp:3514-3543) over the composed
     * [PlayerFeatures.messageBuffer] state; `Game::playerSay` calls it for
     * every accepted say except `TALKTYPE_PRIVATE_PN` (game.cpp:7436-7438).
     */
    fun removeMessageBuffer() {
        features.messageBuffer.removeMessageBuffer()
    }

    // --- condition integration (player.cpp:356-394, 6291-6436) -------------------

    /**
     * `Player::client` (`ProtocolGame*`, player.hpp:1955) replaced by the
     * per-player [ProtocolSink]. Every unbound seam below falls back to the
     * `nullptr` client behavior; binding the sink wires them all at once.
     */
    var protocolSink: ProtocolSink? = null

    /** `Player::sendIcons()` (player.hpp:1085) — protocol send seam. */
    var sendIcons: () -> Unit = {}

    /** `Player::sendTextMessage(MessageType, text)` (`player.cpp:6350-6407`) seam. */
    var sendTextMessage: (String) -> Unit = {}

    /** `Player::sendTextMessage(MessageClasses, text)` (`player.cpp:6350-6407`) typed protocol seam. */
    var sendTextMessageTyped: (MessageClass, String) -> Unit = { type, text ->
        protocolSink?.sendTextMessage(type, text)
    }

    /** `Player::sendCreatureAppear(creature, pos, isLogin)` (`player.cpp:8785-8802`) protocol seam. */
    var sendCreatureAppear: (Creature, Position, Boolean) -> Unit = { creature, position, isLogin ->
        protocolSink?.sendCreatureAppear(creature, position, isLogin)
    }

    /** `Player::sendRemoveTileThing(pos, stackpos)` (`player.cpp:7076-7080`) protocol seam. */
    var sendRemoveTileThing: (Position, Int) -> Unit = { position, stackpos ->
        protocolSink?.sendRemoveTileThing(position, stackpos)
    }

    /** `Player::sendCreatureMove(creature, newPos, newStackPos, oldPos, oldStackPos, teleport)` (`player.cpp:8803`) protocol seam. */
    var sendCreatureMove: (Creature, Position, Int, Position, Int, Boolean) -> Unit =
        { creature, newPos, newStackPos, oldPos, oldStackPos, teleport ->
            protocolSink?.sendCreatureMove(creature, newPos, newStackPos, oldPos, oldStackPos, teleport)
        }

    /** `Player::sendCreatureHealth(creature)` (`player.cpp:2139`) protocol seam. */
    var sendCreatureHealth: (Creature) -> Unit = { creature ->
        protocolSink?.sendCreatureHealth(creature)
    }

    /** `Player::sendCreatureChangeOutfit(creature, outfit)` (`player.cpp:8861`) protocol seam. */
    var sendCreatureOutfit: (Creature, Outfit) -> Unit = { creature, outfit ->
        protocolSink?.sendCreatureOutfit(creature, outfit)
    }

    /** `Player::sendChangeSpeed(creature, stepSpeed)` (`player.hpp:1097`) protocol seam. */
    var sendChangeSpeed: (Creature, Int) -> Unit = { creature, stepSpeed ->
        protocolSink?.sendChangeSpeed(creature, stepSpeed)
    }

    /** `Player::sendStats()` (`player.cpp:2347`) protocol seam. */
    var sendStats: () -> Unit = { protocolSink?.sendStats(this) }

    /** `Player::sendInventoryItem(slot, item)` (`player.cpp:9281`) protocol seam. */
    var sendInventoryItem: (Slots_t, Item?) -> Unit = { slot, item ->
        protocolSink?.sendInventoryItem(slot, item)
    }

    /** `Player::sendWorldLight(lightInfo)` (`player.cpp:2511-2515`) protocol seam. */
    var sendWorldLight: (LightInfo) -> Unit = { _ -> }

    /** `Player::sendTibiaTime(time)` (`player.cpp:2517-2521`) protocol seam. */
    var sendTibiaTime: (Int) -> Unit = { _ -> }

    /** `Player::sendSingleSoundEffect(pos, id, source)` (`player.cpp:12076-12080`) protocol seam. */
    var sendSingleSoundEffect: (Position, SoundEffect, SourceEffect) -> Unit = { _, _, _ -> }

    /** `Player::sendDoubleSoundEffect(pos, main, mainSource, secondary, secondarySource)` (`player.cpp:12082-12086`) protocol seam. */
    var sendDoubleSoundEffect: (Position, SoundEffect, SourceEffect, SoundEffect, SourceEffect) -> Unit =
        { _, _, _, _, _ -> }

    /** `Player::cancelNextWalk` (player.hpp); `Player::stopWalk` latches it. */
    var cancelNextWalk: Boolean = false

    /** `Player::stopWalk()` (player.cpp:6273-6275). */
    fun stopWalk() {
        cancelNextWalk = true
    }

    /** `Player::sendCancelMessage(ReturnValue)` (player.cpp:2117) protocol seam. */
    var sendCancelMessage: (ReturnValue) -> Unit = { returnValue ->
        protocolSink?.sendCancelMessage(returnValue)
    }

    /** `Player::sendCancelMessage(const std::string&)` (player.cpp:2111) protocol seam. */
    var sendCancelMessageText: (String) -> Unit = { message ->
        protocolSink?.sendCancelMessage(message)
    }

    /** `Player::sendCancelTarget` (player.cpp:2121) protocol seam. */
    var sendCancelTarget: () -> Unit = {}

    /** `Player::sendCloseContainer` (player.cpp:8378) protocol seam. */
    var sendCloseContainer: (Int) -> Unit = { cid -> protocolSink?.sendCloseContainer(cid) }

    /** `Player::sendContainer` (player.cpp:9134) protocol seam. */
    var sendContainer: (Int, Container, Boolean, Int) -> Unit = { cid, container, hasParent, firstIndex ->
        protocolSink?.sendContainer(cid, container, hasParent, firstIndex)
    }

    /**
     * `Player::sendAddContainerItem(container, item)` (player.cpp:9056): maps
     * the container to every open cid and applies the browse-field page and
     * out-of-window item substitution before the sink send.
     */
    var sendAddContainerItem: (Container, Item) -> Unit = { container, item ->
        for ((cid, info) in openContainers) {
            if (info.container !== container) {
                continue
            }

            var slot = info.index
            var resolvedItem = item
            if (container.id == Container.ITEM_BROWSEFIELD) {
                val containerSize = container.size() - 1
                val pageEnd = info.index + container.capacity - 1
                if (containerSize > pageEnd) {
                    slot = pageEnd
                    container.getItemByIndex(pageEnd)?.let { resolvedItem = it }
                } else {
                    slot = containerSize
                }
            } else if (info.index >= container.capacity) {
                container.getItemByIndex(info.index - 1)?.let { resolvedItem = it }
            }
            protocolSink?.sendAddContainerItem(cid, slot, resolvedItem)
        }
    }

    /** `Player::sendUpdateContainerItem(container, slot, newItem)` (player.cpp:9087). */
    var sendUpdateContainerItem: (Container, Int, Item) -> Unit = { container, slot, newItem ->
        for ((cid, info) in openContainers) {
            if (info.container !== container) {
                continue
            }
            if (slot < info.index || slot >= info.index + container.capacity) {
                continue
            }
            protocolSink?.sendUpdateContainerItem(cid, slot, newItem)
        }
    }

    /** `Player::sendRemoveContainerItem(container, slot)` (player.cpp:9110). */
    var sendRemoveContainerItem: (Container, Int) -> Unit = { container, slot ->
        for ((cid, info) in openContainers) {
            if (info.container !== container) {
                continue
            }

            var firstIndex = info.index
            if (firstIndex > 0 && firstIndex >= container.size() - 1) {
                firstIndex -= container.capacity
                info.index = firstIndex
                sendContainer(cid, container, false, firstIndex)
            }
            protocolSink?.sendRemoveContainerItem(
                cid,
                maxOf(slot, firstIndex),
                container.getItemByIndex(container.capacity + firstIndex),
            )
        }
    }

    /**
     * `Player::setFollowCreature` (src/creatures/players/player.cpp:6127): a
     * failed base set clears the follow and attack targets, reports
     * `RETURNVALUE_THEREISNOWAY`, cancels the target window and stops the walk.
     */
    override fun setFollowCreature(creature: Creature?): Boolean {
        if (!super.setFollowCreature(creature)) {
            super.setFollowCreature(null)
            setAttackedCreature(null)
            sendCancelMessage(ReturnValue.THEREISNOWAY)
            sendCancelTarget()
            stopWalk()
            return false
        }
        return true
    }

    // --- creature appear/remove/move hooks (player.cpp:12220-12425) ----------------

    /**
     * `Player::onCreatureAppear` (player.cpp:12220-12316).
     *
     * PARITY GAPS: `onEquipInventory`, the bed `wakeUp`, the protocol/livestream
     * login block, the offline mute-condition decay,
     * `Game::checkPlayersRecord` (owned by the login facade), the adventurer
     * blessings, the current-mount re-toggle and `Game::changePlayerSpeed`
     * (player.cpp:12224-12314) need their unported subsystems. The guild
     * membership is already registered by the port's `setGuild`.
     */
    override fun onCreatureAppear(creature: Creature, isLogin: Boolean) {
        super.onCreatureAppear(creature, isLogin)

        if (!isLogin || creature !== this) {
            return
        }

        refreshCyclopediaMonsterTracker(true)
        refreshCyclopediaMonsterTracker(false)

        for (condition in storedConditionList.toList()) {
            addCondition(condition)
        }
        storedConditionList.clear()
    }

    /**
     * `Player::onRemoveCreature` (player.cpp:12318-12360).
     *
     * PARITY GAPS: `onDeEquipInventory` (player.cpp:12323), the trade-window
     * close (player.cpp:12347-12349), `closeShopWindow` (player.cpp:12351) and
     * the `shopOwner` reset (player.cpp:12356-12359) need their unported state.
     */
    override fun onRemoveCreature(creature: Creature, isLogout: Boolean) {
        super.onRemoveCreature(creature, isLogout)

        if (creature === this) {
            if (isLogout) {
                getParty()?.leaveParty(this, true)
                getGuild()?.removeMember(this)

                setLoginPosition(if (isDead) getTemplePosition() else position)
                lastLogout = now() / 1_000
                clearPartyInvitations()
            }

            if (eventWalk != 0L) {
                setFollowCreature(null)
            }

            savePlayer()
        }
    }

    /**
     * `Player::onCreatureMove` (player.cpp:12362-12425).
     *
     * PARITY GAPS: the shop-owner interaction close (player.cpp:12371-12374),
     * the trade-range checks (player.cpp:12384-12393), the modal-window clear
     * (player.cpp:12395-12405) and `Party::updatePlayerStatus`
     * (player.cpp:12414) need their unported state.
     */
    override fun onCreatureMove(
        creature: Creature,
        newTile: Tile?,
        newPos: Position,
        oldTile: Tile?,
        oldPos: Position,
        teleport: Boolean,
    ) {
        super.onCreatureMove(creature, newTile, newPos, oldTile, oldPos, teleport)

        val followCreature = getFollowCreature()
        if (hasFollowPath && (creature === followCreature || (creature === this && followCreature != null))) {
            isUpdatingPath = false
            updateCreatureWalk()
        }

        if (creature !== this) {
            return
        }

        if (!teleport && oldPos.z == newPos.z) {
            updateParalyzeWalkExhaust()
        }

        if (isInMarket()) {
            setInMarket(false)
        }

        getParty()?.updateSharedExperience()

        if (teleport || oldPos.z != newPos.z) {
            val ticks = stairhopDelay
            if (ticks > 0) {
                addCondition(
                    Condition.createCondition(
                        ConditionId.CONDITIONID_DEFAULT,
                        ConditionType.CONDITION_PACIFIED,
                        ticks,
                        0,
                        now = now,
                    ),
                )
            }
        }
    }

    /** `Player::onAttackedCreatureDisappear` (player.cpp:12448-12453). */
    override fun onAttackedCreatureDisappear(isLogout: Boolean) {
        sendCancelTarget()

        if (!isLogout) {
            sendTextMessage("Target lost.")
        }
    }

    /** `Player::onFollowCreatureDisappear` (player.cpp:12456-12461). */
    override fun onFollowCreatureDisappear(isLogout: Boolean) {
        sendCancelTarget()

        if (!isLogout) {
            sendTextMessage("Target lost.")
        }
    }

    /**
     * `Player::storedConditionList` (player.hpp:1698). Canary keeps the vector
     * private and grants `IOLoginDataLoad` friend access (player.hpp:1948);
     * the port models that friendship as `internal`, exactly like the
     * friend-parity `skills` view, and
     * [org.opentibia.canary.io.iologindata.IOLoginDataSubloads.loadPlayerConditions]
     * fills it from the persisted `conditions` blob. The
     * [onCreatureAppear] replay keeps applying them on login
     * (player.cpp:12220-12316).
     */
    internal val storedConditionList = mutableListOf<Condition>()

    /** `Player::clearPartyInvitations` (player.cpp:7816-7821). */
    fun clearPartyInvitations() {
        for (invitingParty in partyInvitations.toList()) {
            invitingParty.removeInvite(this, false)
        }
        partyInvitations.clear()
    }

    /** `g_saveManager().savePlayer(player)` (player.cpp:12353) persistence seam. */
    var savePlayer: () -> Unit = {}

    /** `g_configManager().getNumber(STAIRHOP_DELAY)` (player.cpp:12418) config seam. */
    var stairhopDelay: Int = 0

    /** `Player::quickLootFallbackToMainContainer` (player.hpp:1875). */
    var quickLootFallbackToMainContainer: Boolean = false

    /** `Player::quickLootFilter` (player.hpp:1859). */
    var quickLootFilter: QuickLootFilter_t = QuickLootFilter_t.QUICKLOOTFILTER_SKIPPEDLOOT

    /** `Player::quickLootListItemIds` (player.hpp:1684). */
    var quickLootListItemIds: List<Int> = emptyList()

    /** `Player::depotSearch` (player.hpp:1830), set by `setSpecialMenuAvailable`. */
    var depotSearch: Boolean = false

    /** `Player::stashMenu` (player.hpp:1829); `Player::isStashMenuAvailable` reads it. */
    var stashMenu: Boolean = false

    /** `Player::marketMenu` (player.hpp:1828). */
    var marketMenu: Boolean = false

    /** `Player::isStashMenuAvailable` (player.hpp:533-535). */
    fun isStashMenuAvailable(): Boolean = stashMenu

    /** `Player::client->sendCloseDepotSearch()` (player.cpp:6820) protocol seam. */
    var sendCloseDepotSearch: () -> Unit = {}

    /** `Player::client->sendSpecialContainersAvailable()` (player.cpp:6831) protocol seam. */
    var sendSpecialContainersAvailable: () -> Unit = {}

    /**
     * `Player::setSpecialMenuAvailable` (player.cpp:6815-6833): disabling depot
     * search closes an open search window, then the three menu gates are
     * stored and the client is told the new availability.
     */
    fun setSpecialMenuAvailable(stashBool: Boolean, marketMenuBool: Boolean, depotSearchBool: Boolean) {
        if (isDepotSearchOpen() && !depotSearchBool && depotSearch) {
            setDepotSearchIsOpen(0, 0)
            sendCloseDepotSearch()
        }

        stashMenu = stashBool
        marketMenu = marketMenuBool
        depotSearch = depotSearchBool
        sendSpecialContainersAvailable()
    }

    /**
     * `Player::getRewardChest()` (player.cpp:2058-2063): lazily built and owned
     * by [features].
     */
    fun getRewardChest(): RewardChest = features.rewardChest

    /**
     * `Player::stashItems` supply stash (player.hpp:1826). Canary stores the
     * `std::map<uint16_t, uint32_t>` directly on the player; the port keeps the
     * [Stash] state holder as an injected dependency (AGENTS.md 1.1).
     */
    val stash: Stash = Stash()

    /** `Player::depotSearchOnItem` (player.hpp:1831). */
    private var depotSearchItemId: Int = 0
    private var depotSearchItemTier: Int = 0

    /** `Player::isDepotSearchOpen` (player.hpp:533-535). */
    fun isDepotSearchOpen(): Boolean = depotSearchItemId != 0

    /** `Player::isDepotSearchOpenOnItem` (player.hpp:536-538). */
    fun isDepotSearchOpenOnItem(itemId: Int): Boolean = depotSearchItemId == itemId

    /** `Player::depotSearchOnItem.second` (player.hpp:1831), the tier of the open search. */
    fun getDepotSearchItemTier(): Int = depotSearchItemTier

    /** `Player::depotSearchOnItem.first` (player.hpp:1831), the item of the open search. */
    fun getDepotSearchItemId(): Int = depotSearchItemId

    /** `Player::setDepotSearchIsOpen` (player.hpp:539-541). */
    fun setDepotSearchIsOpen(itemId: Int, tier: Int) {
        depotSearchItemId = itemId
        depotSearchItemTier = tier
    }

    /** `Player::isDepotSearchAvailable` (player.hpp:542-544). */
    fun isDepotSearchAvailable(): Boolean = depotSearch

    /** `Player::inMarket` (player.hpp:1866). */
    private var inMarket: Boolean = false

    /** `Player::setInMarket` (player.hpp:526-528). */
    fun setInMarket(value: Boolean) {
        inMarket = value
    }

    /** `Player::isInMarket` (player.hpp:529-531). */
    fun isInMarket(): Boolean = inMarket

    /** `Player::imbuingItem` (player.hpp:1761). */
    private var imbuingItem: Item? = null

    /** `Player::hasImbuingItem` (player.cpp:2414-2416). */
    fun hasImbuingItem(): Boolean = imbuingItem != null

    /** `Player::imbuingItem` read accessor (player.hpp:1761); the C++ field is public. */
    fun getImbuingItem(): Item? = imbuingItem

    /** `Player::setImbuingItem` (player.cpp:2418-2420). */
    fun setImbuingItem(item: Item?) {
        imbuingItem = item
    }

    /** `Player::lastDepotId` (player.hpp:1825). */
    private var lastDepotId: Int = -1

    /** `Player::getLastDepotId` (player.hpp:560-562). */
    fun getLastDepotId(): Int = lastDepotId

    /** `Player::setLastDepotId` (player.hpp:557-559). */
    fun setLastDepotId(newId: Int) {
        lastDepotId = newId
    }

    /** `Player::inbox` (player.hpp:1760), built by `IOLoginDataLoad::loadPlayer`. */
    val inbox: Inbox = Inbox(Inbox.ITEM_INBOX)

    /** `Player::depotLockerMap` (player.hpp:1672). */
    private val depotLockerMap = mutableMapOf<Int, DepotLocker>()

    /**
     * `Player::depotChests` (player.hpp:1673): depot-box id -> chest, the map
     * `IOLoginDataLoad::loadPlayerDepotItems` and
     * `IOLoginDataSave::savePlayerDepotItems` walk (iologindata_save_player.cpp:
     * 583-586). Public accessor because the C++ field is public.
     */
    private val depotChests = mutableMapOf<Int, DepotChest>()

    /** `Player::depotChests` (player.hpp:1673) read view; the C++ field is public. */
    fun getDepotChests(): Map<Int, DepotChest> = depotChests

    /**
     * `Player::getDepotChest` (player.cpp:1998-2022): the map-scoped chest for
     * [depotId], materialized when [autoCreate] is set. The port selector lives
     * in [DepotChest.depotChestItemId].
     */
    fun getDepotChest(depotId: Int, autoCreate: Boolean): DepotChest? {
        depotChests[depotId]?.let { return it }
        if (!autoCreate) {
            return null
        }

        val depotChest = DepotChest(DepotChest.depotChestItemId(depotId))
        depotChests[depotId] = depotChest
        return depotChest
    }

    /**
     * `Player::getDepotLocker` (player.cpp:2024-2058). The port always builds
     * the locker for the current (non-old) protocol, so it fills the stash slot
     * exactly like `!client->oldProtocol`; the `ITEM_DEPOT` box container holds
     * the [getDepotChest] instances, so the locker, the depot search and the
     * login/save walk all resolve the same chests.
     *
     * PARITY GAPS: `DEPOT_BOXES` is the [DepotLocker.DEPOT_BOXES] default instead
     * of `g_configManager().getNumber(DEPOT_BOXES)` (configmanager.cpp:55), and
     * `IOLoginData` never assigns [getLastDepotId].
     *
     * PARITY NOTE: the C++ cached branch reparents the boxes through
     * `getItemByIndex(0)->getContainer()`, which resolves to the market item
     * (`null` container); the port re-parents them to the `ITEM_DEPOT` container
     * so the chests stay reachable through the locker tree.
     */
    fun getDepotLocker(depotId: Int): DepotLocker {
        depotLockerMap[depotId]?.let { locker ->
            inbox.setParent(locker)
            val depotContainer = locker.items.firstOrNull { it.id == DepotLocker.ITEM_DEPOT }?.getContainer()
            for (i in DepotLocker.DEPOT_BOXES downTo 1) {
                getDepotChest(i, false)?.setParent(depotContainer)
            }
            return locker
        }

        val locker = DepotLocker(DepotLocker.ITEM_LOCKER, size = 4)
        locker.setDepotId(depotId)
        locker.internalAddThing(Item(DepotLocker.ITEM_MARKET))
        locker.internalAddThing(inbox)
        locker.internalAddThing(Item(DepotLocker.ITEM_STASH))
        val depotContainer = Container(DepotLocker.ITEM_DEPOT, capacity = DepotLocker.DEPOT_BOXES)
        for (i in DepotLocker.DEPOT_BOXES downTo 1) {
            val depotBox = getDepotChest(i, true) ?: continue
            depotContainer.internalAddThing(depotBox)
            depotBox.setParent(depotContainer)
        }
        locker.internalAddThing(depotContainer)
        depotLockerMap[depotId] = locker
        return locker
    }

    // --- action/UI timers (player.cpp:3084-3477, 8540) ----------------------------

    /**
     * [PlayerActionTimers] host instance: `Player::isUIExhausted`,
     * `Player::setNextAction`, the necklace/ring/potion gates, module delays,
     * login protection and `Player::sendPing` (player.cpp:3084-3477, 8540).
     * Constructed lazily so the injected [now] clock is read after composition;
     * the world-lane confinement makes `NONE` the correct mode (AGENTS.md 1.2).
     */
    private val actionTimers: PlayerActionTimers by lazy(LazyThreadSafetyMode.NONE) {
        PlayerActionTimers(
            now = { now() },
            hasClient = { hasClient() },
            sendPingPacket = { sendPingPacket() },
            clearAttackedCreature = { setAttackedCreature(null) },
            canLogout = { canLogout() },
            playerLogoutEvent = { playerLogoutEvent() },
            logout = { logoutClient() },
            removeCreature = { removeCreatureOnTimeout() },
            logInfo = { logDebug(it) },
            getPlayerName = { name },
            hasAttackedCreature = { getAttackedCreature() != null },
        )
    }

    /** `Player::client != nullptr` (player.cpp:2314, 2333) for [actionTimers]. */
    var hasClient: () -> Boolean = { protocolSink != null }

    /** `Player::client->sendPing()` (player.cpp:2315) for [actionTimers]. */
    var sendPingPacket: () -> Unit = {}

    /** `Player::canLogout()` (player.cpp:2329) for [actionTimers]. */
    var canLogout: () -> Boolean = { false }

    /** `g_creatureEvents().playerLogout(player)` (player.cpp:2329) for [actionTimers]. */
    var playerLogoutEvent: () -> Boolean = { false }

    /** `Player::client->logout(true, true)` (player.cpp:2332) for [actionTimers]. */
    var logoutClient: () -> Unit = {}

    /** `g_game().removeCreature(player, true)` (player.cpp:2334) for [actionTimers]. */
    var removeCreatureOnTimeout: () -> Unit = {}

    /**
     * `Player::isUIExhausted` (player.cpp:3412-3414) with the header default
     * `exhaustionTime = 250` (player.hpp:1263).
     */
    fun isUIExhausted(exhaustionTime: Int = 250): Boolean = actionTimers.isUIExhausted(exhaustionTime)

    /** `Player::updateUIExhausted` (player.cpp:3416-3418). */
    fun updateUIExhausted() {
        actionTimers.updateUIExhausted()
    }

    /** `Player::setNextAction` (player.cpp:3429-3433). */
    fun setNextAction(time: Long) {
        actionTimers.setNextAction(time)
    }

    /** `Player::canDoAction` (player.cpp:3435-3437). */
    fun canDoAction(): Boolean = actionTimers.canDoAction()

    /** `Player::getNextActionTime` (player.cpp:3084-3086). */
    fun getNextActionTime(): Long = actionTimers.getNextActionTime()

    /** `Player::setNextNecklaceAction` (player.cpp:3439-3443). */
    fun setNextNecklaceAction(time: Long) {
        actionTimers.setNextNecklaceAction(time)
    }

    /** `Player::setNextRingAction` (player.cpp:3445-3449). */
    fun setNextRingAction(time: Long) {
        actionTimers.setNextRingAction(time)
    }

    /** `Player::canEquipNecklace` (player.cpp:3451-3453). */
    fun canEquipNecklace(): Boolean = actionTimers.canEquipNecklace()

    /** `Player::canEquipRing` (player.cpp:3455-3457). */
    fun canEquipRing(): Boolean = actionTimers.canEquipRing()

    /** `Player::setNextPotionAction` (player.cpp:3459-3463). */
    fun setNextPotionAction(time: Long) {
        actionTimers.setNextPotionAction(time)
    }

    /** `Player::canDoPotionAction` (player.cpp:3465-3467). */
    fun canDoPotionAction(): Boolean = actionTimers.canDoPotionAction()

    /** `Player::getNextPotionActionTime` (player.cpp:3088-3090). */
    fun getNextPotionActionTime(): Long = actionTimers.getNextPotionActionTime()

    /** `Player::setLoginProtection` (player.cpp:3469-3471). */
    fun setLoginProtection(time: Long) {
        actionTimers.setLoginProtection(time)
    }

    /** `Player::isLoginProtected` (player.cpp:3472-3474). */
    fun isLoginProtected(): Boolean = actionTimers.isLoginProtected()

    /** `Player::resetLoginProtection` (player.cpp:3475-3477). */
    fun resetLoginProtection() {
        actionTimers.resetLoginProtection()
    }

    /** `Player::setModuleDelay` (player.cpp:3078-3080). */
    fun setModuleDelay(byteortype: Int, delay: Int) {
        actionTimers.setModuleDelay(byteortype, delay)
    }

    /** `Player::canRunModule` (player.cpp:3082-3085). */
    fun canRunModule(byteortype: Int): Boolean = actionTimers.canRunModule(byteortype)

    /** `Player::receivePing` (player.cpp:8540-8542). */
    fun receivePing() {
        actionTimers.receivePing()
    }

    /** `Player::sendPing` (player.cpp:2308-2345). */
    fun sendPing() {
        actionTimers.sendPing()
    }

    /**
     * `Player::requestDepotItems` (player.cpp:10707-10761). PARITY GAP: the
     * `sendDepotItems` payload that the locker/stash scan feeds is not ported,
     * so only the locker build and the `setDepotSearchIsOpen(1, 0)` state
     * transition are applied.
     */
    fun requestDepotItems() {
        getDepotLocker(getLastDepotId())
        setDepotSearchIsOpen(1, 0)
    }

    /**
     * `Player::requestDepotSearchItem` (player.cpp:10763-10811). PARITY GAP:
     * `sendDepotSearchResultDetail` and its depot/inbox/stash scan are not
     * ported; the requested item and tier are still recorded.
     */
    fun requestDepotSearchItem(itemId: Int, tier: Int) {
        getDepotLocker(getLastDepotId())
        setDepotSearchIsOpen(itemId, tier)
    }

    /**
     * `Player::ManagedContainerMap m_managedContainers` (player.hpp:1682): the
     * per-category `(lootContainer, obtainContainer)` pair.
     */
    private val managedContainers = mutableMapOf<ObjectCategory_t, Pair<Container?, Container?>>()

    /**
     * `Player::refreshManagedContainer` (player.cpp:1595-1649), including the
     * `QUICKLOOTCONTAINER`/`OBTAINCONTAINER` attribute bit for the category.
     * PARITY GAPS: the C++ `previousContainer` client refresh of the parent
     * cylinders stays with the caller (no spectator sink here), and
     * `checkLootContainers` (player.cpp:1670) is not ported.
     */
    fun refreshManagedContainer(
        category: ObjectCategory_t,
        container: Container?,
        isLootContainer: Boolean,
        loading: Boolean = false,
    ): Container? {
        var previousContainer: Container? = null
        val toSetAttribute =
            if (isLootContainer) ItemAttribute.QUICKLOOTCONTAINER else ItemAttribute.OBTAINCONTAINER

        val existing = managedContainers[category]
        if (existing != null && !loading) {
            previousContainer = if (isLootContainer) existing.first else existing.second
            if (previousContainer != null) {
                val flags = (previousContainer.getAttribute(toSetAttribute) as? Number)?.toLong() ?: 0L
                val cleared = flags and (1L shl category.value).inv()
                if (cleared == 0L) {
                    previousContainer.removeAttribute(toSetAttribute)
                } else {
                    previousContainer.setAttribute(toSetAttribute, cleared)
                }
            }

            val first = if (isLootContainer) null else existing.first
            val second = if (isLootContainer) existing.second else null
            if (first == null && second == null) {
                managedContainers.remove(category)
            } else {
                managedContainers[category] = first to second
            }
        }

        if (container != null) {
            previousContainer = container
            val current = managedContainers[category]
            managedContainers[category] = if (current != null) {
                if (isLootContainer) container to current.second else current.first to container
            } else {
                if (isLootContainer) container to null else null to container
            }

            if (!loading) {
                val flags = (container.getAttribute(toSetAttribute) as? Number)?.toLong() ?: 0L
                container.setAttribute(toSetAttribute, flags or (1L shl category.value))
            }
        }

        return previousContainer
    }

    /**
     * `Player::getManagedContainer` (player.cpp:1651-1668): a non-default
     * category falls back to `OBJECTCATEGORY_DEFAULT` for non-premium players
     * and to the default category's container when the category has none.
     */
    fun getManagedContainer(category: ObjectCategory_t, isLootContainer: Boolean): Container? {
        var resolvedCategory = category
        if (resolvedCategory != ObjectCategory_t.OBJECTCATEGORY_DEFAULT && !isPremium()) {
            resolvedCategory = ObjectCategory_t.OBJECTCATEGORY_DEFAULT
        }

        val pair = managedContainers[resolvedCategory]
        val container = if (isLootContainer) pair?.first else pair?.second
        if (container == null && resolvedCategory != ObjectCategory_t.OBJECTCATEGORY_DEFAULT) {
            return getManagedContainer(ObjectCategory_t.OBJECTCATEGORY_DEFAULT, isLootContainer)
        }
        return container
    }

    /**
     * `Player::m_conditionSuppressionCount` / `Player::lastConditionTime`
     * (player.hpp:1724, 1784) through the wave-29 [ConditionSuppression] holder.
     * The clock follows [Creature.now] and icon broadcasts follow [sendIcons];
     * a host may replace the holder to inject `MIN_DELAY_BETWEEN_CONDITIONS`
     * (configmanager.cpp:321).
     */
    var conditionSuppression: ConditionSuppression = ConditionSuppression(
        now = { now() },
        sendIcons = { sendIcons() },
    )

    /** `Player::isSuppress` (`player.cpp:356-364`). */
    override fun isSuppress(type: ConditionType, attackerPlayer: Boolean): Boolean =
        conditionSuppression.isSuppress(type, attackerPlayer)

    /** `Player::addConditionSuppressions` (`player.cpp:366-376`). */
    fun addConditionSuppressions(addConditions: Array<ConditionType>) {
        conditionSuppression.addConditionSuppressions(addConditions)
    }

    /** `Player::removeConditionSuppressions` (`player.cpp:378-394`). */
    fun removeConditionSuppressions(toRemoveConditions: List<ConditionType>) {
        conditionSuppression.removeConditionSuppressions(toRemoveConditions)
    }

    /** `Player::getLastConditionTime` (`player.cpp:1143-1149`). */
    fun getLastConditionTime(type: ConditionType): Long =
        conditionSuppression.getLastConditionTime(type)

    /** `Player::updateLastConditionTime` (`player.cpp:1150-1152`). */
    fun updateLastConditionTime(type: ConditionType) {
        conditionSuppression.updateLastConditionTime(type)
    }

    /** `Player::checkLastConditionTimeWithin` (`player.cpp:1154-1160`). */
    fun checkLastConditionTimeWithin(type: ConditionType, interval: Int): Boolean =
        conditionSuppression.checkLastConditionTimeWithin(type, interval)

    /**
     * `Player::onAddCondition` (`player.cpp:6291-6310`). PARITY GAPS: the
     * `CONDITION_PARALYZE` walk-exhaust refresh needs the movement subsystem's
     * walk-delay state, and the `CONDITION_OUTFIT`/mount branch belongs to the
     * mount subsystem.
     */
    override fun onAddCondition(type: ConditionType) {
        super.onAddCondition(type)
        sendIcons()
    }

    /**
     * `Player::onAddCombatCondition` (`player.cpp:6342-6408`). PARITY GAP:
     * `updateParalyzeWalkExhaust()` (movement subsystem).
     */
    override fun onAddCombatCondition(type: ConditionType) {
        if (type.isSuppressible) {
            updateLastConditionTime(type)
        }

        when (type) {
            ConditionType.CONDITION_POISON -> sendTextMessage("You are poisoned.")
            ConditionType.CONDITION_DROWN -> sendTextMessage("You are drowning.")
            ConditionType.CONDITION_PARALYZE -> sendTextMessage("You are paralyzed.")
            ConditionType.CONDITION_DRUNK -> sendTextMessage("You are drunk.")
            ConditionType.CONDITION_LESSERHEX,
            ConditionType.CONDITION_INTENSEHEX,
            ConditionType.CONDITION_GREATERHEX,
            -> sendTextMessage("You are hexed.")

            ConditionType.CONDITION_ROOTED -> sendTextMessage("You are rooted.")
            ConditionType.CONDITION_FEARED -> sendTextMessage("You are feared.")
            ConditionType.CONDITION_CURSED -> sendTextMessage("You are cursed.")
            ConditionType.CONDITION_FREEZING -> sendTextMessage("You are freezing.")
            ConditionType.CONDITION_DAZZLED -> sendTextMessage("You are dazzled.")
            ConditionType.CONDITION_BLEEDING -> sendTextMessage("You are bleeding.")
            ConditionType.CONDITION_AGONY -> sendTextMessage("You are in agony.")
            else -> Unit
        }
    }

    /**
     * `Player::onEndCondition` (`player.cpp:6411-6436`). PARITY GAP: the
     * mounted-outfit restore (`wasMounted`/`toggleMount`) belongs to the mount
     * subsystem.
     */
    override fun onEndCondition(type: ConditionType) {
        super.onEndCondition(type)

        if (type == ConditionType.CONDITION_INFIGHT &&
            getCondition(ConditionType.CONDITION_INFIGHT) == null
        ) {
            onIdleStatus()
            val hasPzLockOnly = pzLockOnlyUntil > now()
            if (hasPzLockOnly) {
                pzLocked = true
            } else {
                pzLockOnlyUntil = 0
                pzLocked = false
            }
            pvpPlayer.clearAttacked()
            pvpPlayer.sendOpenPvpSituations()

            if (!hasPzLockOnly &&
                getSkull() != Skulls_t.SKULL_RED &&
                getSkull() != Skulls_t.SKULL_BLACK
            ) {
                setSkull(Skulls_t.SKULL_NONE)
            }
        }

        sendIcons()
    }

    /** `Player::onIdleStatus` (`player.cpp:6528-6533`). */
    override fun onIdleStatus() {
        super.onIdleStatus()
        mParty?.clearPlayerPoints(this)
    }

    // --- PvP state (player.hpp:1869-1871, 1960) -----------------------------------

    /** `Player::pzLockOnlyUntil` (player.hpp:1870). */
    internal var pzLockOnlyUntil: Long = 0

    /** `Player::pzLocked` (player.hpp:1869). */
    internal var pzLocked: Boolean = false

    /** `Player::pzLockEventId` (player.hpp:1871). */
    internal var pzLockEventId: Long = 0

    private var pvpPlayerField: PlayerPvp? = null

    /**
     * `Player::m_pvpPlayer` (player.hpp:1960). Canary embeds the component; the
     * port builds it on first access so a host can install a fully wired
     * component (dispatcher/client seams) before PvP state is touched
     * (AGENTS.md 1.1).
     */
    var pvpPlayer: PlayerPvp
        get() = pvpPlayerField ?: PlayerPvp(this).also { pvpPlayerField = it }
        set(value) {
            pvpPlayerField = value
        }

    /** `Player::isPzLocked` (`player.cpp:3900-3902`). */
    fun isPzLocked(): Boolean = pzLocked

    /** `g_configManager().getNumber(PZ_LOCKED)` (configmanager.cpp:340). */
    var pzLockedDuration: Int = 60_000

    /**
     * `Player::addPzLockTicks` (`player.cpp:4442-4483`); the component owns the
     * scheduled expiry seams.
     */
    fun addPzLockTicks() {
        pvpPlayer.addPzLockTicks()
    }

    /**
     * `Player::addInFightTicks` (`player.cpp:4487-4505`). PARITY GAPS:
     * `wheel().checkAbilities()` and `updateImbuementTrackerStats()` belong to
     * the wheel/imbuement subsystems.
     */
    fun addInFightTicks(pzlock: Boolean = false) {
        if (hasFlag(PlayerFlags_t.NotGainInFight)) {
            return
        }

        if (pzlock) {
            pzLockOnlyUntil = 0
            pzLocked = true
            sendIcons()
        }

        safeCall {
            addCondition(
                Condition.createCondition(
                    ConditionId.CONDITIONID_DEFAULT,
                    ConditionType.CONDITION_INFIGHT,
                    pzLockedDuration,
                    now = { now() },
                ),
            )
        }
    }

    /** `Player::onAttacked` (`player.cpp:6522-6526`). */
    override fun onAttacked() {
        super.onAttacked()
        addInFightTicks()
        pvpPlayer.sendOpenPvpSituations()
    }

    // --- blessings (player.hpp:1818, player.cpp:2422-2440, 10544-10579, 12993) ----

    /**
     * `Player::kv()` (player.cpp:1398-1400): the full
     * `g_kv().scoped("player").scoped(guid)` `ValueWrapper` store. The default
     * is the per-player in-memory store (AGENTS.md 1.1); the composition root
     * rebinds it to the SQL-backed root scope so every component
     * ([PlayerFeatures.achievement]/[PlayerFeatures.badge]/
     * [PlayerFeatures.title]/[PlayerFeatures.attachedEffects]) and the
     * `loadPlayerExivaRestrictions` / `savePlayerExivaRestrictions` pair share
     * the persisted keys.
     */
    var kv: KV = KVMemory()

    /**
     * `g_configManager().getNumber(DEATH_LOSE_PERCENT)` (configmanager.cpp:245),
     * default -1 selects the level formula in [getLostPercent].
     */
    var deathLosePercent: Int = -1

    /** `g_configManager().getNumber(ADVENTURERSBLESSING_LEVEL)` (configmanager.cpp:227). */
    var adventurersBlessingLevel: Int = 21

    /**
     * `ExpertPvp::isRetroPvpWorldType()` (expert_pvp.cpp:369-371) reading the
     * configured `WORLD_TYPE`; injected per AGENTS.md 1.1, defaults to the
     * `configmanager.cpp:413` regular PvP behavior.
     */
    var isRetroPvpWorldType: Boolean = false

    /** `Player::sendBlessStatus()` protocol seam. */
    var sendBlessStatus: () -> Unit = {}

    /** `Player::blessings` (player.hpp:1818): ten `uint8_t` slots, indexed 1..10. */
    private val blessings = IntArray(10)

    /**
     * `Player::addBlessing` (`player.cpp:2422-2428`). Canary stores into a
     * `uint8_t`, so the addition wraps; a slot already at 255 refuses the call.
     * Out-of-range indices are UB in C++ and are ignored here (PARITY GAP).
     */
    fun addBlessing(index: Int, count: Int) {
        if (index < 1 || index > blessings.size) {
            return
        }
        if (blessings[index - 1] == UINT8_MAX) {
            return
        }
        blessings[index - 1] = (blessings[index - 1] + (count and 0xFF)) and 0xFF
    }

    /** `Player::removeBlessing` (`player.cpp:2430-2436`); see [addBlessing]. */
    fun removeBlessing(index: Int, count: Int) {
        if (index < 1 || index > blessings.size) {
            return
        }
        if (blessings[index - 1] == 0) {
            return
        }
        blessings[index - 1] = (blessings[index - 1] - (count and 0xFF)) and 0xFF
    }

    /** `Player::hasBlessing` (`player.cpp:2438-2440`). */
    fun hasBlessing(index: Int): Boolean {
        if (index < 1 || index > blessings.size) {
            return false
        }
        return blessings[index - 1] != 0
    }

    /**
     * `Player::getBlessingCount` (`player.cpp:10544-10555`). The `storeCount`
     * branch reads `kv()->scoped("summary")->scoped("blessings")->scoped(index)
     * ->get("amount")` with the C++ `uint8_t` truncation.
     */
    fun getBlessingCount(index: Int, storeCount: Boolean = false): Int {
        if (!storeCount) {
            if (index > 0 && index <= blessings.size) {
                return blessings[index - 1]
            }
            return 0
        }
        val amount = kv.scoped("summary").scoped("blessings").scoped("$index").get("amount") ?: return 0
        return amount.getNumber().toInt() and 0xFF
    }

    /**
     * `Player::getBlessingsName` (`player.cpp:10557-10579`): the held blessing
     * names in enum order, start-cased with spaces and joined by ", " and " and ".
     */
    fun getBlessingsName(): String {
        val blessingNames = Blessings.entries
            .filter { hasBlessing(it.value) }
            .map { toStartCaseWithSpace(it.name) }

        val output = StringBuilder()
        if (blessingNames.isNotEmpty()) {
            for (i in 0 until blessingNames.size - 1) {
                output.append(blessingNames[i]).append(", ")
            }
            if (blessingNames.size > 1) {
                output.append("and ")
            }
            output.append(blessingNames.last()).append(".")
        }
        return output.toString()
    }

    /** `toStartCaseWithSpace` (`tools.cpp:412-426`) over an enum name. */
    private fun toStartCaseWithSpace(str: String): String {
        val result = StringBuilder()
        for ((i, ch) in str.withIndex()) {
            if (i == 0 || ch.isUpperCase()) {
                if (i > 0) {
                    result.append(' ')
                }
                result.append(ch.uppercaseChar())
            } else {
                result.append(ch.lowercaseChar())
            }
        }
        return result.toString()
    }

    /**
     * `Player::checkAndShowBlessingMessage` (`player.cpp:12993-13018`). The
     * `MESSAGE_EVENT_ADVANCE` type is folded into [sendTextMessage] like the
     * other ported condition messages.
     */
    fun checkAndShowBlessingMessage() {
        val willNotLoseBless = level < adventurersBlessingLevel && getVocationId() > Vocation.VOCATION_NONE
        val bless = getBlessingsName()

        val blessOutput: String
        if (willNotLoseBless) {
            var addedBless = false
            for (i in 2..6) {
                if (!hasBlessing(i)) {
                    addBlessing(i, 1)
                    addedBless = true
                }
            }
            sendBlessStatus()
            blessOutput = if (addedBless) {
                "You have received adventurer's blessings for being level lower than $adventurersBlessingLevel!\n" +
                    "You are still blessed with $bless"
            } else {
                ""
            }
        } else {
            blessOutput = if (bless.isEmpty()) {
                "You lost all your blessings."
            } else {
                "You are still blessed with $bless"
            }
        }

        if (blessOutput.isNotEmpty()) {
            sendTextMessage(blessOutput)
        }
    }

    /**
     * `Player::getLostPercent` (`player.cpp:7350-7393`). The configured
     * `DEATH_LOSE_PERCENT` path subtracts 3 for a promoted vocation and one
     * point per counted blessing; otherwise the level formula applies with 8%
     * (6.31% on retro PvP worlds) reduction per blessing and a flat 30% for
     * promoted players.
     */
    fun getLostPercent(): Double {
        var blessingCount = 0
        for (i in 2..MAX_BLESSING) {
            if (hasBlessing(i)) {
                blessingCount++
            }
        }

        val configuredDeathLosePercent = deathLosePercent
        if (configuredDeathLosePercent != -1) {
            var losePercent = configuredDeathLosePercent
            if (isPromoted()) {
                losePercent -= 3
            }
            losePercent -= blessingCount
            return maxOf(0, losePercent) / 100.0
        }

        val factor = if (isRetroPvpWorldType) 6.31 else 8.0
        var percentReduction = blessingCount * factor / 100.0

        val lossPercent: Double
        if (level >= 24) {
            val tmpLevel = level + (levelPercent / 100.0)
            lossPercent = ((tmpLevel + 50) * 50 * ((tmpLevel * tmpLevel) - (5 * tmpLevel) + 8)) /
                experience.toDouble()
        } else {
            percentReduction = if (percentReduction >= 0.40) 0.50 else percentReduction
            lossPercent = 10.0
        }

        if (isPromoted()) {
            percentReduction += 30 / 100.0
        }

        return (lossPercent * (1 - percentReduction)) / 100.0
    }

    /** `Player::getLostExperience` (`player.hpp:1921-1923`) over `Creature::skillLoss`. */
    override fun getLostExperience(): Long =
        if (skillLoss) (experience * getLostPercent()).toLong() else 0L

    /**
     * `Player::getRace` (player.cpp:1528-1530): players always drop the blood
     * splash; the player corpse itself (`Player::getCorpse`, player.cpp:4370)
     * stays with the reduced player-death path (PARITY GAP).
     */
    override fun getRace(): RaceType = RaceType.RACE_BLOOD

    /**
     * `Player::getGainedExperience` (player.cpp:6180-6189): only under
     * `EXPERIENCE_FROM_PLAYERS` does an attacking player different from the
     * victim, within `EXP_FROM_PLAYERS_LEVEL_RANGE` levels, award the 75% share
     * of the lost experience; every other attacker awards nothing.
     */
    override fun getGainedExperience(attacker: Creature): Long =
        PlayerExperienceState(
            player = this,
            experienceFromPlayers = experienceFromPlayersProvider(),
            expFromPlayersLevelRange = expFromPlayersLevelRangeProvider(),
            getSkillLoss = { skillLoss },
            getDamageMap = { getDamageMap() },
        ).getGainedExperience(attacker)

    /** `g_configManager().getBoolean(EXPERIENCE_FROM_PLAYERS)` (configmanager.cpp:123) seam. */
    var experienceFromPlayersProvider: () -> Boolean = { false }

    /** `g_configManager().getNumber(EXP_FROM_PLAYERS_LEVEL_RANGE)` (configmanager.cpp:122) seam. */
    var expFromPlayersLevelRangeProvider: () -> Int = { 75 }

    /**
     * `Player::levelPercent` (player.hpp:1849) recomputed by
     * `Player::addExperience` (`player.cpp:3737-3741`).
     */
    private var levelPercent: Int = 0

    /** `Player::getLevelPercent` (player.hpp:580-582). */
    fun getLevelPercent(): Int = levelPercent

    // --- learned spells (player.cpp:3122-3145) -------------------------------------

    /** `Player::learnedInstantSpellList` (player.hpp:1696) through the wave-29 holder. */
    val spellBook: SpellBook = SpellBook()

    /** `Player::learnedInstantSpellList` storage view (player.hpp:1696). */
    val learnedInstantSpellList: MutableList<String> get() = spellBook.learnedInstantSpellList

    /** `Player::learnInstantSpell` (`player.cpp:3122-3126`). */
    fun learnInstantSpell(spellName: String) {
        if (!hasLearnedInstantSpell(spellName)) {
            spellBook.learnInstantSpell(spellName)
        }
    }

    /** `Player::forgetInstantSpell` (`player.cpp:3128-3130`) — `std::erase`. */
    fun forgetInstantSpell(spellName: String) {
        spellBook.forgetInstantSpell(spellName)
    }

    /**
     * `Player::hasLearnedInstantSpell` (`player.cpp:3132-3145`); the list
     * membership mirrors the C++ `strcasecmp` comparison.
     */
    fun hasLearnedInstantSpell(spellName: String): Boolean {
        if (hasFlag(PlayerFlags_t.CannotUseSpells)) {
            return false
        }

        if (hasFlag(PlayerFlags_t.IgnoreSpellCheck)) {
            return true
        }

        return spellBook.hasLearned(spellName)
    }

    // --- spell/group cooldowns (player.cpp:8997-9007, 13405-13437) ------------------

    /** `Player::client` cooldown send surface (player.cpp:8997-9007). */
    var spellCooldownClient: SpellCooldownClient? = null

    /** `g_spells()` registry; injected per AGENTS.md 1.1, absent by default. */
    var spells: Spells? = null

    /** `Player::sendSpellCooldown` (`player.cpp:8997-9001`). */
    fun sendSpellCooldown(spellId: Int, time: Int) {
        spellCooldownClient?.sendSpellCooldown(spellId, time)
    }

    /** `Player::sendSpellGroupCooldown` (`player.cpp:9003-9007`). */
    fun sendSpellGroupCooldown(groupId: SpellGroup, time: Int) {
        spellCooldownClient?.sendSpellGroupCooldown(groupId, time)
    }

    /**
     * `Player::sendSpellCooldowns` (`player.cpp:13405-13437`): resend the live
     * `CONDITION_SPELLCOOLDOWN`/`CONDITION_SPELLGROUPCOOLDOWN` entries, skipping
     * unknown spell ids and already elapsed windows, with the C++ uint16
     * sub-id truncation.
     */
    fun sendSpellCooldowns() {
        val client = spellCooldownClient ?: return

        for (condition in conditions) {
            val type = condition.type
            if (type != ConditionType.CONDITION_SPELLCOOLDOWN &&
                type != ConditionType.CONDITION_SPELLGROUPCOOLDOWN
            ) {
                continue
            }

            val ticks = max(0, condition.ticks)
            if (ticks == 0) {
                continue
            }

            if (type == ConditionType.CONDITION_SPELLGROUPCOOLDOWN) {
                val groupId = if (condition.subId > UINT16_MAX) 0 else condition.subId
                client.sendSpellGroupCooldown(SpellGroup.fromValue(groupId), ticks)
                continue
            }

            val spellId = if (condition.subId > UINT16_MAX) 0 else condition.subId
            val spell = spells?.getInstantSpellById(spellId) ?: continue
            client.sendSpellCooldown(spellId, ticks)
        }
    }

    /** `Player::m_party` (player.hpp:1767). */
    private var mParty: Party? = null

    /**
     * `Player::setParty` (player.cpp:1038-1045); the
     * `ExpertPvp::refreshAllVisibleSituationMarks()` side effect is not ported.
     */
    fun setParty(newParty: Party?) {
        if (mParty === newParty) {
            return
        }
        mParty = newParty
    }

    /** `Player::getParty` (player.cpp:1047-1049). */
    fun getParty(): Party? = mParty

    /** `Player::invitePartyList` (player.hpp:1694); read-only view for inspecting systems. */
    private val partyInvitations = mutableListOf<Party>()

    /** Read-only view of the pending `Player::invitePartyList` entries. */
    val invitePartyList: List<Party> get() = partyInvitations

    /** `Player::addPartyInvitation` (player.cpp:7802-7810). */
    fun addPartyInvitation(newParty: Party): Boolean {
        if (partyInvitations.contains(newParty)) {
            return false
        }
        partyInvitations.add(newParty)
        return true
    }

    /** `Player::removePartyInvitation` (player.cpp:7812-7814). */
    fun removePartyInvitation(remParty: Party) {
        partyInvitations.remove(remParty)
    }

    /** `Player::isInviting` (player.cpp:7783-7788). */
    fun isInviting(player: Player?): Boolean {
        val party = mParty
        if (player == null || party == null || party.getLeader() !== this) {
            return false
        }
        return party.isPlayerInvited(player)
    }

    /**
     * `Player::getPartyShield` (player.cpp:7729-7781): the party shield shown
     * for [player], derived from the party shared-experience state and the
     * pending invitations.
     */
    fun getPartyShield(player: Player?): PartyShields_t {
        if (player == null) {
            return PartyShields_t.SHIELD_NONE
        }

        val party = mParty
        if (party != null) {
            if (party.getLeader() === player) {
                if (party.isSharedExperienceActive()) {
                    if (party.isSharedExperienceEnabled()) {
                        return PartyShields_t.SHIELD_YELLOW_SHAREDEXP
                    }

                    if (party.canUseSharedExperience(player)) {
                        return PartyShields_t.SHIELD_YELLOW_NOSHAREDEXP
                    }

                    return PartyShields_t.SHIELD_YELLOW_NOSHAREDEXP_BLINK
                }

                return PartyShields_t.SHIELD_YELLOW
            }

            if (player.getParty() === party) {
                if (party.isSharedExperienceActive()) {
                    if (party.isSharedExperienceEnabled()) {
                        return PartyShields_t.SHIELD_BLUE_SHAREDEXP
                    }

                    if (party.canUseSharedExperience(player)) {
                        return PartyShields_t.SHIELD_BLUE_NOSHAREDEXP
                    }

                    return PartyShields_t.SHIELD_BLUE_NOSHAREDEXP_BLINK
                }

                return PartyShields_t.SHIELD_BLUE
            }

            if (isInviting(player)) {
                return PartyShields_t.SHIELD_WHITEBLUE
            }
        }

        if (player.isInviting(this)) {
            return PartyShields_t.SHIELD_WHITEYELLOW
        }

        if (player.getParty() != null) {
            return PartyShields_t.SHIELD_GRAY
        }

        return PartyShields_t.SHIELD_NONE
    }

    /**
     * Fallback flag store used only while no [Group] is resolved; Canary always
     * has a group (`Player::group`, player.hpp:1759) and never models a local
     * set, so this is a port-only stand-in for directly constructed players
     * (tooling/tests).
     */
    private val playerFlags = mutableSetOf<PlayerFlags_t>()

    /** `Player::hasFlag` (player.cpp:7853-7855): `group->flags[flag]`. */
    fun hasFlag(flag: PlayerFlags_t): Boolean =
        group?.flags?.contains(flag) ?: (flag in playerFlags)

    /** `Player::setFlag` (player.cpp:7857-7859): raises the shared group flag. */
    fun setFlag(flag: PlayerFlags_t) {
        val currentGroup = group
        if (currentGroup != null) {
            currentGroup.flags.add(flag)
        } else {
            playerFlags.add(flag)
        }
    }

    /** `Player::removeFlag` (player.cpp:7861-7863): clears the shared group flag. */
    fun removeFlag(flag: PlayerFlags_t) {
        val currentGroup = group
        if (currentGroup != null) {
            currentGroup.flags.remove(flag)
        } else {
            playerFlags.remove(flag)
        }
    }

    /** `Player::getSkull` (player.cpp:7116-7121): `NotGainInFight` hides the skull. */
    override fun getSkull(): Skulls_t =
        if (hasFlag(PlayerFlags_t.NotGainInFight)) Skulls_t.SKULL_NONE else super.getSkull()

    /** `Player::magLevel` (player.hpp:1787). */
    var magLevel: Int = 0

    /** `Player::varStats[STAT_LAST + 1]` (player.hpp:1802). */
    val varStats = IntArray(Stat.LAST + 1)

    /** `Player::setVarStats` (player.cpp:1251-1253) — accumulates the modifier. */
    fun setVarStats(stat: Stat, modifier: Int) {
        varStats[stat.value] += modifier
    }

    /** `Player::varSkills[SKILL_LAST + 1]` (player.hpp:1801). */
    val varSkills = IntArray(Skill.LAST + 1)

    /** `Player::setVarSkill` (player.cpp:352-354) — accumulates the modifier. */
    fun setVarSkill(skill: Skill, modifier: Int) {
        varSkills[skill.value] += modifier
    }

    /** `Player::bonusCapacity` (player.hpp:1780), `uint32_t`; set from `%` imbuements (player.cpp:3319). */
    var bonusCapacity: Int = 0

    /** `Player::getBaseMagicLevel` (player.hpp:585-587). */
    fun getBaseMagicLevel(): Int = magLevel

    /**
     * `Player::getMagicLevel` (player.cpp:7420-7427):
     * `max(0, getLoyaltyMagicLevel() + varStats[STAT_MAGICPOINTS])`. The Wheel
     * of Destiny and weapon-proficiency terms are unported subsystems
     * (PARITY GAP).
     */
    fun getMagicLevel(): Int = maxOf(0, getLoyaltyMagicLevel() + varStats[Stat.STAT_MAGICPOINTS.value])

    /**
     * `Player::getLoyaltyMagicLevel` (player.cpp:7429-7455). The account loyalty
     * bonus and `manaSpent` progression are not ported (PARITY GAP); without a
     * loyalty bonus Canary's loop cannot advance either, so the base magic level
     * is returned.
     */
    fun getLoyaltyMagicLevel(): Int = magLevel

    /**
     * `Item::items[id].weaponType` lookup (`Item::getWeaponType`, src/items/item.hpp:340).
     * `ItemType` has no weapon classification in this port yet, so per
     * AGENTS.md 1.1 the lookup is injected; unwired, every item resolves
     * `WEAPON_NONE`.
     */
    var weaponTypeProvider: (Int) -> WeaponType_t = { _ -> WeaponType_t.WEAPON_NONE }

    /** `Player::getWeaponId` (player.cpp:396-403). */
    fun getWeaponId(ignoreAmmo: Boolean = false): Int = getWeapon(ignoreAmmo)?.id ?: 0

    /** `Player::getWeapon(ignoreAmmo)` (player.cpp:453-464) — left hand first. */
    fun getWeapon(ignoreAmmo: Boolean = false): Item? =
        getWeapon(Slots_t.CONST_SLOT_LEFT, ignoreAmmo) ?: getWeapon(Slots_t.CONST_SLOT_RIGHT, ignoreAmmo)

    /**
     * `Player::getWeapon(slot, ignoreAmmo)` (player.cpp:405-424). Shields and
     * ammo are never weapons.
     */
    fun getWeapon(slot: Slots_t, ignoreAmmo: Boolean = false): Item? {
        val item = getInventoryItem(slot) ?: return null
        val weaponType = weaponTypeProvider(item.id)
        if (weaponType == WeaponType_t.WEAPON_NONE ||
            weaponType == WeaponType_t.WEAPON_SHIELD ||
            weaponType == WeaponType_t.WEAPON_AMMO
        ) {
            return null
        }

        if (!ignoreAmmo &&
            (weaponType == WeaponType_t.WEAPON_DISTANCE || weaponType == WeaponType_t.WEAPON_MISSILE)
        ) {
            // `getQuiverAmmoOfType` (player.cpp:436-451) is not ported (PARITY
            // GAP): Canary returns the matching quiver ammo here, the port
            // returns the equipped weapon itself.
        }

        return item
    }

    /** `Player::inventory[CONST_SLOT_LAST + 1]` (player.hpp:1763). */
    private val inventory = arrayOfNulls<Item>(Slots_t.LAST + 1)

    /**
     * `Player::skills[SKILL_LAST + 1]` (player.hpp:1711). Canary keeps the
     * array private and grants `IOLoginDataLoad`, `IOLoginDataSave` and
     * `PlayerFunctions` friend access (player.hpp:1939, 1948-1949); the port
     * models that friendship as `internal` while the public readers
     * [getBaseSkill]/[getSkillTries]/[getSkillPercent] stay the only source
     * for other modules.
     */
    internal val skills = Array(skills_t.LAST + 1) { SkillState() }

    /** `Player::getInventoryItem` (player.cpp:337). */
    fun getInventoryItem(slot: Slots_t): Item? {
        if (slot.value < Slots_t.FIRST || slot.value > Slots_t.LAST) {
            return null
        }
        return inventory[slot.value]
    }

    /**
     * `Player::internalAddThing(index, thing)` (player.cpp:6082-6101): places
     * a bare item into the given inventory slot with no capacity or protocol
     * side effects, used by `IOLoginDataLoad::loadPlayerInventoryItems`
     * (iologindata_load_player.cpp:582) and `loadPlayerStoreInbox`.
     *
     * PARITY GAP: the C++ body also parents the item to the player
     * (`item->setParent(...)`); the port's `Player` does not implement the
     * [Cylinder] contract yet, so the slot is filled without the parent link.
     */
    fun internalAddThing(index: Int, thing: Thing?) {
        if (index < Slots_t.FIRST || index > Slots_t.LAST) {
            return
        }

        val item = thing?.getItem() ?: return
        if (inventory[index] != null) {
            return
        }

        inventory[index] = item
    }

    /**
     * Inventory insertion, mirroring `Game::internalPlayerAddItem` / `Game::internalAddItem`
     * (game.cpp:2693-3003) for the carried-in-inventory case.
     */
    fun addItem(item: Item, slot: Slots_t = Slots_t.CONST_SLOT_WHEREEVER): ReturnValue {
        val target = when {
            slot == Slots_t.CONST_SLOT_WHEREEVER -> firstFreeSlot() ?: return ReturnValue.NOTENOUGHROOM
            slot.value < Slots_t.FIRST || slot.value > Slots_t.LAST -> return ReturnValue.NOTPOSSIBLE
            else -> slot
        }
        if (inventory[target.value] != null) {
            return ReturnValue.NOTENOUGHROOM
        }
        if (itemWeight(item) > getFreeCapacity()) {
            return ReturnValue.NOTENOUGHCAPACITY
        }
        inventory[target.value] = item
        updateInventoryWeight()
        sendInventoryItem(target, item)
        return ReturnValue.NOERROR
    }

    /** `Player::removeItem` (player.cpp:10251-10281); `Player::removeThing` refresh included. */
    fun removeItem(item: Item, count: Int = 0): ReturnValue {
        val slot = inventory.indexOfFirst { it === item }
        if (slot < 0) {
            return ReturnValue.NOTPOSSIBLE
        }
        val toRemove = if (count == 0 || count > item.count) item.count else count
        item.count -= toRemove
        if (item.count <= 0) {
            inventory[slot] = null
        }
        updateInventoryWeight()
        sendInventoryItem(Slots_t.fromValue(slot) ?: return ReturnValue.NOTPOSSIBLE, if (item.count <= 0) null else item)
        return ReturnValue.NOERROR
    }

    /** `Player::getItemTypeCount` (player.cpp:5151-5179); Lua `player:getItemCount`. */
    fun getItemCount(
        itemId: Int,
        subType: Int = -1,
        ignoreEquipped: Boolean = false,
        ignoreStoreInbox: Boolean = false,
    ): Long {
        var count = 0L
        for (slot in Slots_t.FIRST..Slots_t.LAST) {
            if (ignoreStoreInbox && slot == Slots_t.CONST_SLOT_STORE_INBOX.value) {
                continue
            }
            val item = inventory[slot] ?: continue
            if (!ignoreEquipped && item.id == itemId) {
                count += countByType(item, subType)
            }
            if (item is Container) {
                count += countContainer(item, itemId, subType)
            }
        }
        return count
    }

    /**
     * `Player::getFreeCapacity` (player.cpp:5468-5476): the `CannotPickupItem` /
     * `HasInfiniteCapacity` gates and `getCapacity() - inventoryWeight`, over the
     * [bonusCapacity] field and `varStats[STAT_CAPACITY]`. The wheel capacity
     * stat is unported (PARITY GAP).
     */
    fun getFreeCapacity(): Double = max(0.0, getCapacity(bonusCapacity.toDouble()) - inventoryWeight)

    /**
     * `Player::getSkillLevel` (player.cpp:7468-7520): `SKILL_MAGLEVEL` reads
     * [getMagicLevel]; every other skill is `max(0, getLoyaltySkill(skill) +
     * varSkills[skill])`. The `getMaxValuePerSkill` cap, the Wheel of Destiny
     * and the weapon-proficiency terms are unported (PARITY GAP).
     */
    fun getSkillLevel(skill: skills_t): Int {
        if (skill == skills_t.SKILL_MAGLEVEL) {
            return minOf(getMagicLevel(), 65535)
        }
        if (skill.value < skills_t.FIRST || skill.value > skills_t.LAST) {
            return 0
        }
        return maxOf(0, getLoyaltySkill(skill) + varSkills[skill.value])
    }

    /** `Player::getBaseSkill` (player.cpp:1127). */
    fun getBaseSkill(skill: skills_t): Int {
        if (skill.value < skills_t.FIRST || skill.value > skills_t.LAST) {
            return 0
        }
        return skills[skill.value].level
    }

    /**
     * `Player::getLoyaltySkill` (player.cpp:1100). The account loyalty bonus is
     * not ported (PARITY GAP), so the base skill level is returned.
     */
    fun getLoyaltySkill(skill: skills_t): Int = getBaseSkill(skill)

    /** `Player::getSkillPercent` (player.cpp:1131). */
    fun getSkillPercent(skill: skills_t): Double {
        if (skill.value < skills_t.FIRST || skill.value > skills_t.LAST) {
            return 0.0
        }
        return skills[skill.value].percent
    }

    /**
     * Lua `player:addSkillTries` -> `Player::addSkillAdvance` (player.cpp:1190-1249).
     *
     * The skill events (`g_events().eventPlayerOnGainSkillTries`), the
     * screenshots and the `playerAdvance` creature events are Lua/host side
     * (AGENTS.md 1.4) and not ported. Without a wired vocation registry the
     * required-tries table is unknown, so the legacy accumulate-only behavior
     * is kept (PARITY GAP).
     */
    fun addSkillTries(skill: skills_t, tries: Long) {
        if (skill.value < skills_t.FIRST || skill.value > skills_t.LAST) {
            return
        }

        val vocation = vocations?.getVocation(vocationId)
        if (vocation == null) {
            skills[skill.value].tries += tries
            return
        }

        var count = tries
        var currReqTries = vocation.getReqSkillTries(skill.value, skills[skill.value].level)
        var nextReqTries = vocation.getReqSkillTries(skill.value, skills[skill.value].level + 1)
        if (currReqTries >= nextReqTries) {
            return
        }
        if (count == 0L) {
            return
        }

        var sendUpdateSkills = false
        while (skills[skill.value].tries + count >= nextReqTries) {
            count -= nextReqTries - skills[skill.value].tries
            skills[skill.value].level++
            skills[skill.value].tries = 0
            skills[skill.value].percent = 0.0

            sendTextMessageTyped(
                MessageClass.MESSAGE_EVENT_ADVANCE,
                "You advanced to ${getSkillName(skill)} level ${skills[skill.value].level}.",
            )
            sendUpdateSkills = true

            currReqTries = nextReqTries
            nextReqTries = vocation.getReqSkillTries(skill.value, skills[skill.value].level + 1)
            if (currReqTries >= nextReqTries) {
                count = 0
                break
            }
        }

        skills[skill.value].tries += count

        val newPercent = if (nextReqTries > currReqTries) {
            getPercentLevel(skills[skill.value].tries, nextReqTries)
        } else {
            0.0
        }
        if (skills[skill.value].percent != newPercent) {
            skills[skill.value].percent = newPercent
            sendUpdateSkills = true
        }

        if (sendUpdateSkills) {
            sendSkills()
            sendStats()
        }
    }

    /** Lua `player:getSkillTries` (player_functions.cpp:1662-1672). */
    fun getSkillTries(skill: skills_t): Long {
        if (skill.value < skills_t.FIRST || skill.value > skills_t.LAST) {
            return 0L
        }
        return skills[skill.value].tries
    }

    private fun firstFreeSlot(): Slots_t? =
        Slots_t.entries.firstOrNull { it.value in Slots_t.FIRST..Slots_t.LAST && inventory[it.value] == null }

    /** `Player::inventoryWeight` (player.hpp:1798), summed by [updateInventoryWeight]. */
    private var inventoryWeight: Double = 0.0

    /**
     * `Player::updateInventoryWeight` (player.cpp:1072-1084). An
     * infinite-capacity player keeps the previous value, exactly like the C++
     * early return.
     */
    fun updateInventoryWeight() {
        if (hasFlag(PlayerFlags_t.HasInfiniteCapacity)) {
            return
        }

        inventoryWeight = 0.0
        for (slot in Slots_t.FIRST..Slots_t.LAST) {
            val item = inventory[slot] ?: continue
            inventoryWeight += itemWeight(item)
        }
    }

    private fun itemWeight(item: Item): Double = item.weight

    private fun countByType(item: Item, subType: Int): Long {
        if (subType >= 0 && item.count != subType) {
            return 0L
        }
        return item.count.toLong()
    }

    private fun countContainer(container: Container, itemId: Int, subType: Int): Long {
        var count = 0L
        for (item in container.items) {
            if (item.id == itemId) {
                count += countByType(item, subType)
            }
            if (item is Container) {
                count += countContainer(item, itemId, subType)
            }
        }
        return count
    }

    // --- open container registry (player.hpp:118-121, 502-510, 1671) -------------

    /** `Player::openContainers` (player.hpp:1671) — a `std::map<uint8_t, ...>`. */
    private val openContainers = sortedMapOf<Int, OpenContainer>()

    /** `Player::getOpenContainers` (player.cpp:3199-3201). */
    fun getOpenContainers(): Map<Int, OpenContainer> = openContainers

    /** `Player::addContainer` (`player.cpp:1292-1312`); `cid` is a `uint8_t`. */
    fun addContainer(cid: Int, container: Container?) {
        val id = cid and 0xFF
        if (id > 0xF) {
            return
        }
        if (container == null) {
            return
        }

        val existing = openContainers[id]
        if (existing != null) {
            existing.container = container
            existing.index = 0
        } else {
            openContainers[id] = OpenContainer(container, 0)
        }
    }

    /**
     * `Player::closeContainer` (`player.cpp:1314-1327`). PARITY GAP: the
     * `isAnyKindOfRewardContainer()` cleanup (`removeEmptyRewards`,
     * `hasOtherRewardContainerOpen`) needs the reward-chest container tree.
     */
    fun closeContainer(cid: Int) {
        openContainers.remove(cid and 0xFF)
    }

    /** `Player::setContainerIndex` (`player.cpp:1346-1352`). */
    fun setContainerIndex(cid: Int, index: Int) {
        openContainers[cid and 0xFF]?.index = index
    }

    /** `Player::getContainerByID` (`player.cpp:1354-1360`). */
    fun getContainerByID(cid: Int): Container? = openContainers[cid and 0xFF]?.container

    /** `Player::getContainerID` (`player.cpp:1362-1369`), -1 when not open. */
    fun getContainerID(container: Container): Int {
        for ((cid, info) in openContainers) {
            if (info.container === container) {
                return cid
            }
        }
        return -1
    }

    /** `Player::getContainerIndex` (`player.cpp:1371-1377`). */
    fun getContainerIndex(cid: Int): Int = openContainers[cid and 0xFF]?.index ?: 0

    // --- inventory container queries (player.cpp:1086, 5714, 5861, 9631) ----------

    /** `Player::getBackpack` (`player.cpp:9631-9639`). */
    fun getBackpack(): Container? = getInventoryItem(Slots_t.CONST_SLOT_BACKPACK) as? Container

    /** `Player::getAllSlotItems` (`player.cpp:1086-1098`). */
    fun getAllSlotItems(): Map<Slots_t, Item> {
        val itemMap = linkedMapOf<Slots_t, Item>()
        for (slot in Slots_t.entries) {
            if (slot.value < Slots_t.FIRST || slot.value > Slots_t.LAST) {
                continue
            }
            inventory[slot.value]?.let { itemMap[slot] = it }
        }
        return itemMap
    }

    /** `Player::getInventoryItemsFromId` (`player.cpp:5714-5737`). */
    fun getInventoryItemsFromId(itemId: Int, ignore: Boolean = true): List<Item> {
        val itemVector = mutableListOf<Item>()
        for (slot in Slots_t.FIRST..Slots_t.LAST) {
            val item = inventory[slot] ?: continue

            if (!ignore && item.id == itemId) {
                itemVector.add(item)
            }

            if (item is Container) {
                for (containerItem in containerItems(item)) {
                    if (containerItem.id == itemId) {
                        itemVector.add(containerItem)
                    }
                }
            }
        }
        return itemVector
    }

    /** `Player::getAllInventoryItems` (`player.cpp:5861-5890`). */
    fun getAllInventoryItems(ignoreEquiped: Boolean = false, ignoreItemWithTier: Boolean = false): List<Item> {
        val itemVector = mutableListOf<Item>()
        for (slot in Slots_t.FIRST..Slots_t.LAST) {
            val item = inventory[slot] ?: continue

            if (!ignoreEquiped) {
                itemVector.add(item)
            }

            if (item is Container) {
                for (containedItem in containerItems(item)) {
                    if (ignoreItemWithTier &&
                        ((containedItem.getAttribute(ItemAttribute.TIER) as? Long ?: 0L) > 0L)
                    ) {
                        continue
                    }
                    itemVector.add(containedItem)
                }
            }
        }
        return itemVector
    }

    /**
     * Depth-first `ContainerIterator` (`container.cpp:1222-1298`): every item
     * once, container entries yielded before their contents, with the
     * `MAX_CONTAINER_DEPTH` cap and cycle detection of the C++ iterator.
     */
    private fun containerItems(root: Container, maxDepth: Int = MAX_CONTAINER_DEPTH): List<Item> {
        val result = mutableListOf<Item>()
        val visited = Collections.newSetFromMap(IdentityHashMap<Container, Boolean>())
        val containers = ArrayDeque<Container>()
        val indices = ArrayDeque<Int>()
        val depths = ArrayDeque<Int>()

        containers.addLast(root)
        indices.addLast(0)
        depths.addLast(1)
        visited.add(root)

        while (containers.isNotEmpty()) {
            val index = indices.last()
            val items = containers.last().items
            if (index >= items.size) {
                containers.removeLast()
                indices.removeLast()
                depths.removeLast()
                continue
            }

            indices[indices.size - 1] = index + 1
            val current = items[index]
            result.add(current)

            if (current is Container && current.items.isNotEmpty()) {
                val newDepth = depths.last() + 1
                if (newDepth <= maxDepth && visited.add(current)) {
                    containers.addLast(current)
                    indices.addLast(0)
                    depths.addLast(newDepth)
                }
            }
        }
        return result
    }

    /**
     * `Player::addExperience` (player.cpp:3613-3742). The rate/message/stat
     * machinery is a PARITY GAP (see [onGainSharedExperience]); the port keeps
     * the experience gain, the level-up loop, the `levelPercent` bookkeeping
     * [getLostPercent] reads and the trailing `Player::sendStats()` refresh.
     */
    fun addExperience(exp: Long) {
        experience += exp
        updateLevel()

        val currLevelExp = getExpForLevel(level)
        val nextLevelExp = getExpForLevel(level + 1)
        levelPercent = if (nextLevelExp > currLevelExp) {
            getPercentLevel(experience - currLevelExp, nextLevelExp - currLevelExp).toInt()
        } else {
            0
        }

        sendStats()
    }

    /**
     * `Player::staminaXpBoost` (player.hpp:1824, default 100);
     * `Player::setStaminaXpBoost` (player.cpp:3252-3254) clamps to the uint16
     * maximum and `getStaminaXpBoost` (player.cpp:3248-3250) reads it back.
     */
    var staminaXpBoost: Int = 100
        set(value) {
            field = minOf(65535, value)
        }

    /**
     * `Player::onGainSharedExperience` (player.cpp:6724-6726) credits the shared
     * party experience through `Player::gainExperience`; the port folds that into
     * [addExperience] (rate/message/stat machinery is a PARITY GAP).
     */
    fun onGainSharedExperience(gainExp: Long, target: Creature? = null) {
        addExperience(gainExp)
    }

    private fun updateLevel() {
        while (experience >= getExpForLevel(level + 1)) {
            level++
        }
    }

    // --- harmony / virtue / serenity (player.hpp:1703-1704, player.cpp:13244-13403) --

    /** `Player::sendMonkData` (`player.cpp:9249-9253`) protocol seam. */
    var sendMonkData: (MonkData, Int) -> Unit = { _, _ -> }

    /** `Player::sendSkills()` (player.cpp:2366) protocol seam used by virtue/serenity/skill changes. */
    var sendSkills: () -> Unit = { protocolSink?.sendSkills(this) }

    /**
     * `Combat::harmonyHeal(caster, target, charges)` (`combat.cpp:761-792`)
     * seam; the heal combat plumbing lives with the combat subsystem.
     */
    var harmonyHeal: (Player, Int) -> Unit = { _, _ -> }

    /**
     * `m_wheelPlayer.getStage(WheelStage_t::ASCETIC)` (`player.cpp:13340`); the
     * wheel stage state belongs to the wheel subsystem, injected per AGENTS.md 1.1.
     */
    var wheelStageAsceticProvider: () -> Int = { 0 }

    /**
     * `Spectators().find<Creature>(player.getPosition(), false, 1, 1, 1, 1)`
     * (`player.cpp:8584`) used by `hasTooManyCreaturesNearby`; the spatial grid
     * belongs to the map subsystem, injected per AGENTS.md 1.1.
     */
    var nearbyCreaturesProvider: (Position) -> List<Creature> = { emptyList() }

    /** `Player::harmony` (player.hpp:1703), a `uint8_t` charge counter. */
    private var harmony = 0

    /** `Player::virtue` (player.hpp:1704), default `None`. */
    private var virtue: Virtue_t = Virtue_t.None

    /** `Player::getVirtue` (`player.cpp:13244-13246`). */
    fun getVirtue(): Virtue_t = virtue

    /** `Player::setVirtue` (`player.cpp:13248-13259`). */
    fun setVirtue(newVirtue: Virtue_t, notifyClient: Boolean = true) {
        if (virtue == newVirtue) {
            return
        }

        virtue = newVirtue

        if (notifyClient) {
            sendSkills()
            sendMonkData(MonkData.Virtue, newVirtue.value)
        }
    }

    /**
     * `Player::setSerene` (`player.cpp:13261-13280`): a still-running
     * `CONDITION_SERENE` (`ticks > 0`) refuses the new value; otherwise the
     * condition is removed and, when `b`, re-created with `ticks`.
     */
    fun setSerene(b: Boolean, ticks: Int = -1) {
        val condition = getCondition(ConditionType.CONDITION_SERENE)
        if (condition != null && condition.ticks > 0) {
            return
        }

        if (condition != null) {
            removeCondition(condition)
        }

        if (b) {
            addCondition(
                Condition.createCondition(
                    ConditionId.CONDITIONID_DEFAULT,
                    ConditionType.CONDITION_SERENE,
                    ticks,
                    now = { now() },
                ),
            )
        }

        if (virtue == Virtue_t.Justice) {
            sendSkills()
        }
    }

    /** `Player::emptyHarmony` (`player.cpp:13282-13285`). */
    fun emptyHarmony() {
        harmony = 0
        sendMonkData(MonkData.Harmony, harmony)
    }

    /** `Player::fillHarmony` (`player.cpp:13287-13290`). */
    fun fillHarmony() {
        buildHarmony(MAX_HARMONY)
        sendMonkData(MonkData.Harmony, harmony)
    }

    /** `Player::buildHarmony` (`player.cpp:13292-13307`). */
    fun buildHarmony(charges: Int = 1) {
        if (harmony == MAX_HARMONY) {
            return
        }

        val appliedCharges = minOf(charges, MAX_HARMONY - harmony)
        if (appliedCharges == 0) {
            return
        }

        healFromHarmony(appliedCharges)
        harmony += appliedCharges
        sendMonkData(MonkData.Harmony, harmony)
    }

    /** `Player::spendHarmony` (`player.cpp:13309-13319`). */
    fun spendHarmony() {
        healFromHarmony(harmony)

        harmony = 0

        if (virtue == Virtue_t.Harmony) {
            buildHarmony()
        }

        sendMonkData(MonkData.Harmony, harmony)
    }

    /** `Player::getHarmony` (`player.cpp:13321-13323`). */
    fun getHarmony(): Int = harmony

    /** `Player::setHarmony` (`player.cpp:13325-13327`), a `uint8_t`. */
    fun setHarmony(newHarmony: Int) {
        harmony = newHarmony and 0xFF
    }

    /** `Player::getHarmonyDamage` (`player.cpp:13356-13359`). */
    fun getHarmonyDamage(min: Double, max: Double): Pair<Long, Long> {
        val harmonyBonus = getHarmonyBonus()
        return (min * harmonyBonus).toLong() to (max * harmonyBonus).toLong()
    }

    /**
     * `Player::getHarmonyBonus` (`player.cpp:13329-13354`): an 8% base per
     * charge that doubles per charge, plus the virtue/serenity bonus, the
     * ascetic wheel stage and the harmony buff modifier.
     */
    private fun getHarmonyBonus(): Double {
        if (harmony == 0) {
            return 1.0
        }

        var harmonyBaseBonus = 8.0

        if (virtue == Virtue_t.Harmony) {
            harmonyBaseBonus += if (hasCondition(ConditionType.CONDITION_SERENE)) 8.0 else 4.0
        }

        harmonyBaseBonus += wheelStageAsceticProvider()

        val rawHarmonyBuff = getBuff(Buff.BUFF_HARMONYBONUS.value)
        if (rawHarmonyBuff != 0) {
            harmonyBaseBonus += rawHarmonyBuff - 100
        }

        if (harmonyBaseBonus <= 0) {
            return 1.0
        }

        val bonusPercent = harmonyBaseBonus * 2.0.pow(harmony - 1) / 100.0
        return 1 + bonusPercent
    }

    /**
     * `Player::healFromHarmony` (`player.cpp:13373-13403`): the most injured
     * live party member inside the client view, otherwise the player itself.
     */
    private fun healFromHarmony(charges: Int = 1) {
        var targetPlayer: Player = this

        val party = getParty()
        if (party != null) {
            for (partyMember in party.getPlayers()) {
                if (partyMember.isRemoved() || partyMember.isDead) {
                    continue
                }

                if (this === partyMember) {
                    continue
                }

                // `Position::areInRange<MAP_MAX_CLIENT_VIEW_PORT_X, MAP_MAX_CLIENT_VIEW_PORT_Y, 0>`
                // (player.cpp:13390).
                if (!position.isWithinRange(
                        partyMember.position,
                        Creature.MAP_MAX_CLIENT_VIEW_PORT_X,
                        Creature.MAP_MAX_CLIENT_VIEW_PORT_Y,
                    )
                ) {
                    continue
                }

                if (partyMember.health < targetPlayer.health) {
                    targetPlayer = partyMember
                }
            }
        }

        harmonyHeal(targetPlayer, charges)
    }

    /**
     * `Player::onThink` (`player.cpp:8618-8720`). PARITY GAPS: message buffer,
     * ping, idle logout, transcendence/momentum and the login/logout checks
     * belong to their owning subsystems; the port keeps the condition tick from
     * [Creature.onThink] and the serenity refresh (`player.cpp:8629`).
     */
    override fun onThink(interval: Int) {
        super.onThink(interval)
        updateSerenityState()
    }

    /** `updateSerenityState` (`player.cpp:8603-8616`) — private in Canary. */
    private fun updateSerenityState() {
        if (getPlayerVocationEnum() != Vocation.VOCATION_MONK_CIP) {
            return
        }

        val isSerene = hasCondition(ConditionType.CONDITION_SERENE)
        val visiblePartyMember = hasVisiblePartyMember(this)
        val tooManyCreaturesNearby = visiblePartyMember && hasTooManyCreaturesNearby(this)
        val checkSerenity = !(visiblePartyMember && tooManyCreaturesNearby)

        if (isSerene != checkSerenity) {
            setSerene(!isSerene)
        }
    }

    /** File-local `hasVisiblePartyMember` (`player.cpp:8557-8580`). */
    private fun hasVisiblePartyMember(player: Player): Boolean {
        val party = player.getParty() ?: return false
        val playerPosition = player.position

        for (member in party.getPlayers()) {
            if (member === player) {
                continue
            }

            val memberPosition = member.position
            if (abs(playerPosition.z - memberPosition.z) > 0) {
                continue
            }

            val offsetX = abs(playerPosition.x - memberPosition.x)
            val offsetY = abs(playerPosition.y - memberPosition.y)
            if (offsetX <= Creature.MAP_MAX_CLIENT_VIEW_PORT_X &&
                offsetY <= Creature.MAP_MAX_CLIENT_VIEW_PORT_Y
            ) {
                return true
            }
        }
        return false
    }

    /** File-local `hasTooManyCreaturesNearby` (`player.cpp:8582-8600`). */
    private fun hasTooManyCreaturesNearby(player: Player): Boolean {
        var count = 0
        for (creature in nearbyCreaturesProvider(player.position)) {
            if (creature === player) {
                continue
            }
            if (creature is Npc) {
                continue
            }
            if (++count >= 6) {
                return true
            }
        }
        return false
    }

    // --- advanced feature hooks (player.cpp:941-996, 10392-10681, 11825-11866, 12749-12798) ---

    /** `PlayerWheel m_wheelPlayer` (player.hpp:1959); `Player::wheel()` (player.cpp:12749-12751). */
    fun wheel(): PlayerWheel = features.wheel

    /** `PlayerCyclopedia m_playerCyclopedia` (player.hpp:1963); `Player::cyclopedia()` (player.cpp:12785-12787). */
    fun cyclopedia(): PlayerCyclopedia = features.cyclopedia

    /** `PlayerForgeHistory m_forgeHistoryPlayer` (player.hpp:1969); `Player::forgeHistory()` (player.cpp:12794-12796). */
    fun forgeHistory(): PlayerForgeHistory = features.forgeHistory

    /**
     * `PlayerStorage m_storage` (player.hpp:1968); `Player::storage()`
     * (player.hpp:1541-1542). The same instance the storage reserved ranges,
     * the `player:getStorageValue`/`setStorageValue` bindings and
     * `PlayerAttachedEffects` mutate.
     */
    fun storage(): PlayerStorage = features.storage

    /**
     * `Player::outfits` (player.hpp:1686) through `Player::getOutfits()`
     * (player.hpp:1564-1566): the composed `PlayerOutfitState` view the
     * `player:addOutfit` family and the storage reserved range share.
     */
    fun getOutfits(): List<OutfitEntry> = features.outfits.getOutfits()

    /** `Player::getBankBalance()` (player.cpp:8299-8301): the composed `Player::bankBalance` field. */
    fun getBankBalance(): Long = features.vitals.getBankBalance()

    // forge dust ledger (player.cpp:11825-11866)

    /** `Player::setForgeDusts` (player.cpp:11825-11830); the client balance refresh is protocol scope. */
    fun setForgeDusts(amount: Long) {
        features.forge.setForgeDusts(amount)
    }

    /** `Player::addForgeDusts` (player.cpp:11832-11837). */
    fun addForgeDusts(amount: Long) {
        features.forge.addForgeDusts(amount)
    }

    /** `Player::removeForgeDusts` (player.cpp:11839-11844). */
    fun removeForgeDusts(amount: Long) {
        features.forge.removeForgeDusts(amount)
    }

    /** `Player::getForgeDusts` (player.cpp:11846-11848). */
    fun getForgeDusts(): Long = features.forge.getForgeDusts()

    /** `Player::addForgeDustLevel` (player.cpp:11850-11855). */
    fun addForgeDustLevel(amount: Long) {
        features.forge.addForgeDustLevel(amount)
    }

    /** `Player::removeForgeDustLevel` (player.cpp:11857-11862). */
    fun removeForgeDustLevel(amount: Long) {
        features.forge.removeForgeDustLevel(amount)
    }

    /** `Player::getForgeDustLevel` (player.cpp:11864-11866). */
    fun getForgeDustLevel(): Long = features.forge.getForgeDustLevel()

    // prey system (player.cpp:10392-10491, 12641-12667)

    /**
     * `Player::initializePrey` (player.cpp:12641-12659). The slot construction
     * lives in [PlayerFeatures.preyEngine]; the port's split state receives the
     * live player level and premium status before the engine runs (PARITY GAP:
     * the legacy base-data packet is protocol scope).
     */
    fun initializePrey() {
        features.prey.setLevel(level)
        features.prey.isPremium = isPremium()
        features.preyEngine.initializePrey(features.prey)
    }

    /** `Player::removePreySlotById` (player.cpp:12661-12667). */
    fun removePreySlotById(slotId: Int) {
        features.prey.removePreySlotById(slotId)
    }

    /** `Player::reloadPreySlot` (player.cpp:10408-10413); the client send is a recorded no-op. */
    fun reloadPreySlot(slotId: Int) {
        features.prey.reloadPreySlot(slotId)
    }

    /** `Player::getPreySlotById` (player.cpp:10415-10424). */
    fun getPreySlotById(slotId: Int): PreySlot? = features.prey.getPreySlotById(slotId)

    /** `Player::setPreySlotClass` (player.cpp:10426-10433). */
    fun setPreySlotClass(slot: PreySlot): Boolean = features.prey.setPreySlotClass(slot)

    /** `Player::usePreyCards` (player.cpp:10435-10445). */
    fun usePreyCards(amount: Int): Boolean = features.prey.usePreyCards(amount)

    /** `Player::addPreyCards` (player.cpp:10447-10452). */
    fun addPreyCards(amount: Long) {
        features.prey.addPreyCards(amount)
    }

    /** `Player::getPreyCards` (player.cpp:10454-10456). */
    fun getPreyCards(): Long = features.prey.getPreyCards()

    /** `Player::getPreyRerollPrice` (player.cpp:10458-10460); the live level feeds the split state first. */
    fun getPreyRerollPrice(): Long {
        features.prey.setLevel(level)
        return features.prey.getPreyRerollPrice()
    }

    /** `Player::getPreyBlackList` (player.cpp:10462-10476). */
    fun getPreyBlackList(): List<Int> = features.prey.getPreyBlackList()

    /** `Player::getPreyWithMonster` (player.cpp:10478-10491). */
    fun getPreyWithMonster(raceId: Int): PreySlot? = features.prey.getPreyWithMonster(raceId)

    // task hunting system (player.cpp:10495-10681)

    /**
     * `Player::initializeTaskHunting` (player.cpp:10495-10525). The slot
     * construction lives in [PlayerFeatures.taskHuntingEngine]; the port's
     * split state receives the live player level and premium status first
     * (PARITY GAP: the legacy base-data packet is protocol scope).
     */
    fun initializeTaskHunting() {
        features.taskHunting.setLevel(level)
        features.taskHunting.isPremium = isPremium()
        features.taskHuntingEngine.initializeTaskHunting(features.taskHunting)
    }

    /** `Player::isCreatureUnlockedOnTaskHunting` (player.cpp:10527-10533). */
    fun isCreatureUnlockedOnTaskHunting(mtype: TaskMonsterInfo?): Boolean =
        features.taskHunting.isCreatureUnlockedOnTaskHunting(mtype)

    /** `Player::setTaskHuntingSlotClass` (player.cpp:10535-10542). */
    fun setTaskHuntingSlotClass(slot: TaskHuntingSlot): Boolean = features.taskHunting.setTaskHuntingSlotClass(slot)

    /** `Player::reloadTaskSlot` (player.cpp:10596-10601); the client send is a recorded no-op. */
    fun reloadTaskSlot(slotId: Int) {
        features.taskHunting.reloadTaskSlot(slotId)
    }

    /** `Player::getTaskHuntingSlotById` (player.cpp:10603-10612). */
    fun getTaskHuntingSlotById(slotId: Int): TaskHuntingSlot? = features.taskHunting.getTaskHuntingSlotById(slotId)

    /** `Player::getTaskHuntingBlackList` (player.cpp:10614-10628). */
    fun getTaskHuntingBlackList(): List<Int> = features.taskHunting.getTaskHuntingBlackList()

    /** `Player::addTaskHuntingPoints` (player.cpp:10641-10646). */
    fun addTaskHuntingPoints(amount: Long) {
        features.taskHunting.addTaskHuntingPoints(amount)
    }

    /** `Player::useTaskHuntingPoints` (player.cpp:10648-10658). */
    fun useTaskHuntingPoints(amount: Long): Boolean = features.taskHunting.useTaskHuntingPoints(amount)

    /** `Player::getTaskHuntingPoints` (player.cpp:10660-10662). */
    fun getTaskHuntingPoints(): Long = features.taskHunting.getTaskHuntingPoints()

    /** `Player::getTaskHuntingRerollPrice` (player.cpp:10664-10666); the live level feeds the split state first. */
    fun getTaskHuntingRerollPrice(): Long {
        features.taskHunting.setLevel(level)
        return features.taskHunting.getTaskHuntingRerollPrice()
    }

    /** `Player::getTaskHuntingWithCreature` (player.cpp:10668-10681). */
    fun getTaskHuntingWithCreature(raceId: Int): TaskHuntingSlot? = features.taskHunting.getTaskHuntingWithCreature(raceId)

    // cyclopedia monster tracker (player.cpp:941-996) and async ongoing tasks (8510-8520)

    /**
     * `Player::client` view used by the tracker updates (player.cpp:951-990); a
     * null client mirrors Canary's `if (!client) return;` guard.
     */
    var cyclopediaMonsterTrackerClient: CyclopediaMonsterTrackerClient? = null

    /**
     * `Player::getCyclopediaMonsterTrackerSet` (player.cpp:941-943) over the
     * composed [PlayerFeatures.cyclopediaTracker]. The C++ set may hold a null
     * `shared_ptr`; the host projection drops it because the host entry points
     * only insert live [MonsterType]s.
     */
    fun getCyclopediaMonsterTrackerSet(isBoss: Boolean): Set<MonsterType> =
        features.cyclopediaTracker.getCyclopediaMonsterTrackerSet(isBoss).filterNotNull().toSet()

    /**
     * `Player::addMonsterToCyclopediaTrackerList` (player.cpp:946-964). The
     * `if (!client) return;` gate lives on the host entry point because the
     * composed holder resolves the host client per call; without a live client
     * the tracker set stays untouched exactly like Canary.
     */
    fun addMonsterToCyclopediaTrackerList(mtype: MonsterType, isBoss: Boolean, reloadClient: Boolean = false) {
        if (cyclopediaMonsterTrackerClient == null) {
            return
        }
        features.cyclopediaTracker.addMonsterToCyclopediaTrackerList(mtype, isBoss, reloadClient)
    }

    /** `Player::removeMonsterFromCyclopediaTrackerList` (player.cpp:966-990). */
    fun removeMonsterFromCyclopediaTrackerList(mtype: MonsterType, isBoss: Boolean, reloadClient: Boolean = false) {
        if (cyclopediaMonsterTrackerClient == null) {
            return
        }
        features.cyclopediaTracker.removeMonsterFromCyclopediaTrackerList(mtype, isBoss, reloadClient)
    }

    /** `Player::isBossOnBosstiaryTracker` (player.cpp:998-1000). */
    fun isBossOnBosstiaryTracker(monsterType: MonsterType?): Boolean =
        features.cyclopediaTracker.isBossOnBosstiaryTracker(monsterType)

    /** `Player::isMonsterOnBestiaryTracker` (player.cpp:1002-1004). */
    fun isMonsterOnBestiaryTracker(monsterType: MonsterType?): Boolean =
        features.cyclopediaTracker.isMonsterOnBestiaryTracker(monsterType)

    /** `Player::refreshCyclopediaMonsterTracker` (player.hpp:390-392). */
    fun refreshCyclopediaMonsterTracker(isBoss: Boolean = false) {
        refreshCyclopediaMonsterTracker(getCyclopediaMonsterTrackerSet(isBoss), isBoss)
    }

    /** `Player::refreshCyclopediaMonsterTracker` (player.cpp:992-996). */
    fun refreshCyclopediaMonsterTracker(trackerList: Set<MonsterType>, isBoss: Boolean) {
        cyclopediaMonsterTrackerClient?.refreshCyclopediaMonsterTracker(trackerList, isBoss)
    }

    /** `Player::sendCyclopediaCharacterRecentDeaths` (player.cpp:8426-8430) protocol seam. */
    var sendCyclopediaCharacterRecentDeaths: (page: Int, pages: Int, entries: List<RecentDeathEntry>) -> Unit =
        { _, _, _ -> }

    /** `Player::sendCyclopediaCharacterRecentPvPKills` (player.cpp:8432-8436) protocol seam. */
    var sendCyclopediaCharacterRecentPvPKills: (page: Int, pages: Int, entries: List<RecentPvPKillEntry>) -> Unit =
        { _, _, _ -> }

    /** `Player::asyncOngoingTasks` (player.hpp:1752), a `uint64_t` bitmask. */
    private var asyncOngoingTasks: Long = 0

    /** `Player::addAsyncOngoingTask` (player.cpp:8510-8512). */
    fun addAsyncOngoingTask(task: PlayerAsyncTask) {
        asyncOngoingTasks = asyncOngoingTasks or task.value.toLong()
    }

    /** `Player::hasAsyncOngoingTask` (player.cpp:8514-8516). */
    fun hasAsyncOngoingTask(task: PlayerAsyncTask): Boolean =
        (asyncOngoingTasks and task.value.toLong()) != 0L

    /** `Player::resetAsyncOngoingTask` (player.cpp:8518-8520). */
    fun resetAsyncOngoingTask(task: PlayerAsyncTask) {
        asyncOngoingTasks = asyncOngoingTasks and task.value.toLong().inv()
    }

    // --- wave-51 host state bindings (player.cpp:344-350, 7606-7687, 12673-12724, 13361-13371) ---

    /** `Player::isItemAbilityEnabled` (player.cpp:344-346) over [PlayerFeatures.itemAbilities]. */
    fun isItemAbilityEnabled(slot: Slots_t): Boolean = features.itemAbilities.isItemAbilityEnabled(slot)

    /** `Player::setItemAbility` (player.cpp:348-350). */
    fun setItemAbility(slot: Slots_t, enabled: Boolean) {
        features.itemAbilities.setItemAbility(slot, enabled)
    }

    /** `Player::setBossPoints` (player.cpp:12673-12675). */
    fun setBossPoints(amount: Long) {
        features.bossPoints.setBossPoints(amount)
    }

    /** `Player::addBossPoints` (player.cpp:12677-12679). */
    fun addBossPoints(amount: Long) {
        features.bossPoints.addBossPoints(amount)
    }

    /** `Player::removeBossPoints` (player.cpp:12681-12683). */
    fun removeBossPoints(amount: Long) {
        features.bossPoints.removeBossPoints(amount)
    }

    /** `Player::getBossPoints` (player.cpp:12685-12687). */
    fun getBossPoints(): Long = features.bossPoints.getBossPoints()

    /** `Player::setSlotBossId` (player.cpp:12695-12704); `parseSendBosstiarySlots` is the client send. */
    fun setSlotBossId(slotId: Int, bossId: Long, parseSendBosstiarySlots: () -> Unit = {}) {
        features.bossPoints.setSlotBossId(slotId, bossId, parseSendBosstiarySlots)
    }

    /** `Player::getSlotBossId` (player.cpp:12706-12712). */
    fun getSlotBossId(slotId: Int): Long = features.bossPoints.getSlotBossId(slotId)

    /** `Player::addRemoveTime` (player.cpp:12714-12716). */
    fun addRemoveTime() {
        features.bossPoints.addRemoveTime()
    }

    /** `Player::setRemoveBossTime` (player.cpp:12718-12720). */
    fun setRemoveBossTime(newRemoveTimes: Int) {
        features.bossPoints.setRemoveBossTime(newRemoveTimes)
    }

    /** `Player::getRemoveTimes` (player.cpp:12722-12724). */
    fun getRemoveTimes(): Int = features.bossPoints.getRemoveTimes()

    /** `Player::updateAimAtTargetSpells` (player.cpp:13361-13367) over [PlayerFeatures.aimAtTarget]. */
    fun updateAimAtTargetSpells(spellId: Int, state: Int) {
        features.aimAtTarget.updateAimAtTargetSpells(spellId, state)
    }

    /** `Player::getAimAtTargetSpells` (player.cpp:13369-13371). */
    fun getAimAtTargetSpells(): Set<Int> = features.aimAtTarget.getAimAtTargetSpells()

    /** `Player::getSpecializedMagicLevel` (player.cpp:7606-7629) over [PlayerFeatures.magicAffinity]. */
    fun getSpecializedMagicLevel(combat: CombatType, useCharges: Boolean): Int =
        features.magicAffinity.getSpecializedMagicLevel(combat, useCharges)

    /** `Player::setSpecializedMagicLevel` (player.cpp:7631-7633). */
    fun setSpecializedMagicLevel(combat: CombatType, value: Int) {
        features.magicAffinity.setSpecializedMagicLevel(combat, value)
    }

    /** `Player::getMagicShieldCapacityFlat` (player.cpp:7635-7656). */
    fun getMagicShieldCapacityFlat(useCharges: Boolean): Int =
        features.magicAffinity.getMagicShieldCapacityFlat(useCharges)

    /** `Player::setMagicShieldCapacityFlat` (player.cpp:7658-7660). */
    fun setMagicShieldCapacityFlat(value: Int) {
        features.magicAffinity.setMagicShieldCapacityFlat(value)
    }

    /** `Player::getMagicShieldCapacityPercent` (player.cpp:7662-7683). */
    fun getMagicShieldCapacityPercent(useCharges: Boolean): Int =
        features.magicAffinity.getMagicShieldCapacityPercent(useCharges)

    /** `Player::setMagicShieldCapacityPercent` (player.cpp:7685-7687). */
    fun setMagicShieldCapacityPercent(value: Int) {
        features.magicAffinity.setMagicShieldCapacityPercent(value)
    }

    // --- death/walk state wiring (player.cpp:2942-3070, 4035-4284, 6161-6171) -----

    /**
     * `Player::manaSpent` (player.hpp:1722), owned by [deathState] like Canary
     * owns the accumulator on `Player` (player.cpp:4078-4113).
     */
    var manaSpent: Long
        get() = deathState.manaSpent
        set(value) {
            deathState.manaSpent = value
        }

    /** `Player::getSpentMana` (player.cpp:7849-7851). */
    fun getSpentMana(): Long = manaSpent

    /** `Player::lastLogout` (player.hpp:1717); seconds. */
    private var lastLogout: Long = 0

    /** `Player::getLastLogout` (player.hpp:630-632). */
    fun getLastLogout(): Long = lastLogout

    /** `Player::magLevelPercent` (player.hpp:1851). */
    private var magLevelPercent: Double = 0.0

    /** `Player::getMagicLevelPercent` (player.hpp:588-590). */
    fun getMagicLevelPercent(): Double = magLevelPercent

    /** `g_game().sendSingleSoundEffect(pos, source)` (game.cpp:7833) seam used by [death]. */
    var gameSendSingleSoundEffect: (Position, SoundEffect, Creature) -> Unit = { _, _, _ -> }

    /** `g_game().internalCreatureChangeOutfit(creature, outfit)` (game.cpp) seam used by [death]. */
    var internalCreatureChangeOutfit: (Player, Outfit) -> Unit = { _, _ -> }

    /** `g_game().getPlayerByID(id)` (game.cpp) seam used by [death]. */
    var playerByIdProvider: (Long) -> Player? = { null }

    /** `g_iobestiary().getBestiaryCharm(charm)` (iobestiary.cpp:231-248) seam used by [death]. */
    var bestiaryCharmProvider: (CharmRune) -> Charm? = { null }

    /** `g_monsters().getMonsterType(name)` (`src/creatures/monsters/monsters.hpp`) seam used by [death]. */
    var monsterTypeProvider: (String) -> MonsterType? = { null }

    /** `g_events().eventPlayerOnLoseExperience(player, expLoss)` (player.cpp:4118) seam. */
    var eventPlayerOnLoseExperience: (Player, Long) -> Unit = { _, _ -> }

    /** `g_callbacks().executeCallback(playerOnLoseExperience, player, expLoss)` (player.cpp:4119) seam. */
    var executePlayerOnLoseExperienceCallback: (Player, Long) -> Unit = { _, _ -> }


    /** `Player::sendReLoginWindow(unfairFightReduction)` (player.cpp:2384-2388) protocol seam. */
    var sendReLoginWindow: (Int) -> Unit = {}

    /**
     * `Player::removeItemOfType(id, amount, subType)` (player.cpp:5292) seam.
     * PARITY GAP: the inventory scan is not ported; the composition binds the
     * item subsystem's implementation.
     */
    var removeItemOfType: (Int, Int, Int) -> Unit = { _, _, _ -> }

    /** `Player::despawn()` (player.cpp:4310-4344) seam; the world removal/logout is Game-owned. */
    var despawn: () -> Unit = {}

    /** `g_game().internalTeleport(player, pos, effect)` (game.cpp:3540) seam used by the no-loss death. */
    var internalTeleport: (Player, Position, Boolean) -> Unit = { _, _, _ -> }


    /** `g_game().addPlayerMana(player)` (game.cpp) protocol seam used by the no-loss death. */
    var addPlayerMana: (Player) -> Unit = {}

    /** `Player::town`/`Player::loginPosition` (player.hpp:1713, 1771) state. */
    private val townState: PlayerTownState = PlayerTownState()

    /** `Player::getTown` (player.cpp:8307-8309). */
    fun getTown(): Town? = townState.getTown()

    /** `Player::setTown` (player.cpp:8310-8312). */
    fun setTown(newTown: Town?) {
        townState.setTown(newTown)
    }

    /** `Player::getTown()->getTemplePosition()` (player.cpp:8319-8326). */
    fun getTemplePosition(): Position = townState.getTemplePosition()

    /** `Player::getLoginPosition` (player.hpp:635-637). */
    fun getLoginPosition(): Position = townState.getLoginPosition()

    /**
     * `Player::getTemplePosition()` seam the death state reads; defaults to the
     * composed [townState] and stays overridable for tests/composition.
     */
    var templePositionProvider: () -> Position = { townState.getTemplePosition() }

    /**
     * `Player::loginPosition = ...` (player.cpp:4040, 12333-12336) seam;
     * defaults to the composed [townState].
     */
    var setLoginPosition: (Position) -> Unit = { townState.loginPosition = it }

    /** `g_configManager().getBoolean(TOGGLE_MOUNT_IN_PZ)` (configmanager.cpp:373, default false). */
    var toggleMountInPz: Boolean = false

    /** `g_logger().debug(...)` seam used by the death/walk states. */
    var logDebug: (String) -> Unit = {}

    /** `g_dispatcher().scheduleEvent` (dispatcher.hpp:144-145) seam; 0 means rejected. */
    var scheduleEvent: (DispatcherLane, Long, String, () -> Unit) -> Long = { _, _, _, _ -> 0L }

    /** `g_dispatcher().stopEvent` (player.cpp:2984, 2998) seam; overrides `Creature::stopEvent`. */
    override var stopEvent: (Long) -> Unit = {}

    /** `Creature::addEventWalk` (creature.cpp:342-383) scheduler admission seam. */
    var addEventWalk: (WalkStartPolicy) -> Unit = {}

    /** `Player::sendCancelWalk()` (player.cpp:2127-2131) protocol seam. */
    var sendCancelWalk: () -> Unit = {}

    /** `g_callbacks().executeCallback(playerOnWalk, player, dir)` (player.cpp:2960) seam. */
    var playerOnWalkCallback: (Direction) -> Unit = {}

    /** `Player::inEventMovePush` (player.hpp). */
    var inEventMovePush: Boolean = false

    /** `g_configManager().getBoolean(PUSH_WHEN_ATTACKING)` (configmanager.cpp:125, default false). */
    var pushWhenAttacking: Boolean = false

    /** `Player::cancelPush()` (player.cpp:7230) seam. */
    var cancelPush: () -> Unit = {}

    /** `g_configManager().getNumber(PROTECTION_LEVEL)` (configmanager.cpp:276, default 1). */
    var protectionLevel: Int = 1

    /** `g_game().getWorldType()` (game.cpp:926-928) seam. */
    var worldTypeProvider: () -> WorldType = { WorldType.PVP }

    /** `ExpertPvp::isEnabled()` reading the configured `WORLD_TYPE` (expert_pvp.cpp:361-363). */
    var expertPvpEnabled: Boolean = false

    /** `Monster::isFamiliar()` (monster.cpp:299) seam. */
    var isFamiliarProvider: (Monster) -> Boolean = { false }

    /** `g_game().map.getTile(pos)` (`src/game/game.cpp`) seam for the feared-field check. */
    var mapTileProvider: (Position) -> Tile? = { null }

    /** `Creature::getWalkDelay()` (creature.cpp:109) seam; the movement subsystem owns the delay. */
    var walkDelayProvider: () -> Int = { 0 }

    /** `Creature::getStepDuration(dir)` (creature.cpp:1607) seam; the movement subsystem owns the step speed. */
    var stepDurationProvider: (Direction) -> Int = { 0 }

    /** `Creature::goToFollowCreature()` (creature.cpp:1090-1120) seam for the follow subsystem. */
    var creatureGoToFollowCreature: () -> Unit = {}

    /** `Player::setNextAction` (player.cpp:3429-3433) seam; `PlayerActionTimers` owns the deadline. */
    var setNextAction: (Long) -> Unit = {}

    /** `Player::resetIdleTime` (player.hpp:564-566) seam; the idle-logout clock is not modelled (PARITY GAP). */
    var resetIdleTime: () -> Unit = {}

    /**
     * `g_dispatcher().scheduleEvent` wrapper: a scheduled action is dropped when
     * the player left the world before the event ran (AGENTS.md deferred
     * callback lifetime safety).
     */
    private fun schedulePlayerEvent(
        lane: DispatcherLane,
        delayMs: Long,
        context: String,
        action: () -> Unit,
    ): Long = scheduleEvent(lane, delayMs, context) { if (!isRemoved()) action() }

    /** `Creature::onWalk` super call seam for [walkState]. */
    private fun creatureOnWalkSuper(dir: Direction) {
        super.onWalk(dir)
    }

    /**
     * [PlayerDeathState] host instance: `Player::death` (player.cpp:4035-4284)
     * over the host accessors above. Constructed lazily so the host config
     * values ([toggleMountInPz]) are read after composition; the world lane
     * confinement makes `NONE` thread safety the correct mode (AGENTS.md 1.2).
     */
    private val deathState: PlayerDeathState by lazy(LazyThreadSafetyMode.NONE) {
        PlayerDeathState(
            player = this,
            now = { now() },
            toggleMountInPz = toggleMountInPz,
            adventurersBlessingLevel = { adventurersBlessingLevel },
            getSkillLoss = { skillLoss },
            setSkillLoss = { setSkillLoss(it) },
            skills = skills.toList(),
            setLevelPercent = { levelPercent = it },
            setMagLevelPercent = { magLevelPercent = it },
            setLastLogout = { lastLogout = it },
            getDamageMap = { getDamageMap() },
            getPlayerById = { playerByIdProvider(it) },
            getBestiaryCharm = { bestiaryCharmProvider(it) },
            getMonsterType = { monsterTypeProvider(it) },
            internalCreatureChangeOutfit = { p, outfit -> internalCreatureChangeOutfit(p, outfit) },
            sendSingleSoundEffect = { pos, sound, source -> gameSendSingleSoundEffect(pos, sound, source) },
            eventPlayerOnLoseExperience = { p, exp -> eventPlayerOnLoseExperience(p, exp) },
            executePlayerOnLoseExperienceCallback = { p, exp -> executePlayerOnLoseExperienceCallback(p, exp) },
            sendStats = { sendStats() },
            sendReLoginWindow = { sendReLoginWindow(it) },
            removeItemOfType = { id, amount, subType -> removeItemOfType(id, amount, subType) },
            despawn = { despawn() },
            internalTeleport = { p, pos, effect -> internalTeleport(p, pos, effect) },
            addCreatureHealth = { addCreatureHealth(it) },
            addPlayerMana = { addPlayerMana(it) },
            getTemplePosition = { templePositionProvider() },
            setLoginPosition = { setLoginPosition(it) },
            logDebug = { logDebug(it) },
        )
    }

    /**
     * [PlayerWalkState] host instance: the walk queue, `Player::onWalk`
     * (player.cpp:2942-2961), `goToFollowCreature` (player.cpp:6161-6171),
     * `canWalkthrough` (player.cpp:1424-1536) and the action/push/potion
     * scheduler variants (player.cpp:2983-3070).
     */
    private val walkState: PlayerWalkState = PlayerWalkState(
        now = { now() },
        getPosition = { position },
        hasParalyze = { hasCondition(ConditionType.CONDITION_PARALYZE) },
        hasFearedCondition = { hasCondition(ConditionType.CONDITION_FEARED) },
        getWalkDelay = { walkDelayProvider() },
        sendCancelWalk = { sendCancelWalk() },
        scheduleEvent = ::schedulePlayerEvent,
        stopEvent = { stopEvent(it) },
        resetIdleTime = { resetIdleTime() },
        sendCancelMessage = { sendCancelMessage(it) },
        executeFearedCondition = {
            getCondition(ConditionType.CONDITION_FEARED)?.executeCondition(conditionTarget, 0)
        },
        logDebug = { logDebug(it) },
        addEventWalk = { addEventWalk(it) },
        getPlayer = { this },
        hasRootedCondition = { hasCondition(ConditionType.CONDITION_ROOTED) },
        getFollowCreature = { getFollowCreature() },
        hasFollowPath = { hasFollowPath },
        creatureGoToFollowCreature = { creatureGoToFollowCreature() },
        getStepDuration = { stepDurationProvider(it) },
        setNextAction = { setNextAction(it) },
        getTile = { mapTileProvider(it) },
        creatureOnWalk = { creatureOnWalkSuper(it) },
        playerOnWalkCallback = { playerOnWalkCallback(it) },
        inEventMovePush = { inEventMovePush },
        pushWhenAttacking = { pushWhenAttacking },
        cancelPush = { cancelPush() },
        protectionLevel = { protectionLevel },
        getWorldType = { worldTypeProvider() },
        expertPvpEnabled = { expertPvpEnabled },
        isInGhostMode = { it.isInGhostMode() },
        isFamiliar = { isFamiliarProvider(it) },
    )

    /** `Player::death` (player.cpp:4035-4284). */
    override fun death(lastHitCreature: Creature?) {
        deathState.death(lastHitCreature)
    }

    /** `Player::onWalk` (player.cpp:2942-2961). */
    override fun onWalk(dir: Direction) {
        walkState.onWalk(dir)
    }

    /** `Player::goToFollowCreature` (player.cpp:6161-6171). */
    override fun goToFollowCreature() {
        walkState.goToFollowCreature()
    }

    /**
     * `Creature::resetMovementState` (creature.cpp:303-313) player body: the
     * next-walk latch and the walk event reset, the queue is cleared through
     * [PlayerWalkState.startAutoWalk] with an empty list (the port's only
     * queue-owner mutation) and the cancel-walk send fires. The
     * `Player::sendUpdateTile` call (creature.cpp:310-312) is a protocol side
     * effect owned by the movement subsystem (PARITY GAP).
     */
    override fun resetMovementState() {
        cancelNextWalk = false
        eventWalk = 0
        walkState.startAutoWalk(emptyList())
        sendCancelWalk()
    }

    /** `Player::canWalkthrough` (player.cpp:1424-1489). */
    fun canWalkthrough(creature: Creature): Boolean = walkState.canWalkthrough(creature)

    /** `Player::canWalkthroughEx` (player.cpp:1492-1536). */
    fun canWalkthroughEx(creature: Creature): Boolean = walkState.canWalkthroughEx(creature)

    /** `Player::setNextWalkActionTask` (player.cpp:2983-2994). */
    fun setNextWalkActionTask(task: PlayerTask?) {
        walkState.setNextWalkActionTask(task)
    }

    /** `Player::setNextWalkTask` (player.cpp:2996-3012). */
    fun setNextWalkTask(task: PlayerTask?) {
        walkState.setNextWalkTask(task)
    }

    /** `Player::setNextActionTask` (player.cpp:3014-3036). */
    fun setNextActionTask(task: PlayerTask?, resetIdleTime: Boolean = true) {
        walkState.setNextActionTask(task, resetIdleTime)
    }

    /** `Player::setNextActionPushTask` (player.cpp:3038-3052). */
    fun setNextActionPushTask(task: PlayerTask?) {
        walkState.setNextActionPushTask(task)
    }

    /** `Player::setNextPotionActionTask` (player.cpp:3054-3070). */
    fun setNextPotionActionTask(task: PlayerTask?) {
        walkState.setNextPotionActionTask(task)
    }

    /** `Player::onWalkAborted` (player.cpp:6242-6245). */
    fun onWalkAborted() {
        walkState.onWalkAborted()
    }

    /** `Player::onWalkComplete` (player.cpp:6247-6271). */
    fun onWalkComplete() {
        walkState.onWalkComplete()
    }

    /**
     * `Player::walkExhausted` (player.cpp:3176-3182), `setWalkExhaust`
     * (player.cpp:3184-3186) and `updateParalyzeWalkExhaust`
     * (player.cpp:3188-3196).
     */
    fun walkExhausted(): Boolean = walkState.walkExhausted()

    /** `Player::setWalkExhaust` (player.cpp:3184-3186). */
    fun setWalkExhaust(value: Long) {
        walkState.setWalkExhaust(value)
    }

    /** `Player::updateParalyzeWalkExhaust` (player.cpp:3188-3196). */
    fun updateParalyzeWalkExhaust() {
        walkState.updateParalyzeWalkExhaust()
    }

    /** `Creature::startAutoWalk` (creature.cpp:326-340). */
    fun startAutoWalk(
        listDir: List<Direction>,
        ignoreConditions: Boolean = false,
        startPolicy: WalkStartPolicy = WalkStartPolicy.RespectDelay,
    ) {
        walkState.startAutoWalk(listDir, ignoreConditions, startPolicy)
    }

    /** `Creature::getNextStep` (creature.cpp:315-324): the walk event's queue consumer. */
    fun getNextStep(): Direction? = walkState.getNextStep()

    /** `Creature::listWalkDir` (creature.hpp:872) read view for the walk event owner. */
    val listWalkDir: List<Direction> get() = walkState.listWalkDir

    /** `Player::createPlayerTask` (player.cpp:212-214). */
    fun createPlayerTask(delay: Long, f: () -> Unit, context: String): PlayerTask =
        walkState.createPlayerTask(delay, f, context)

    companion object {
        /** `PLAYER_MAX_STAFF_SPEED` (`src/creatures/players/player.hpp:126`). */
        const val PLAYER_MAX_STAFF_SPEED = 65535

        /** `PLAYER_MIN_SPEED` (`src/creatures/players/player.hpp:127`). */
        const val PLAYER_MIN_SPEED = 10

        /** `std::numeric_limits<uint16_t>::max()` (player.cpp:13406). */
        const val UINT16_MAX = 65535

        /** `std::numeric_limits<uint8_t>::max()` (player.cpp:2423). */
        const val UINT8_MAX = 255

        /** `maxBlessing` (player.cpp:7352) of `Player::getLostPercent`. */
        const val MAX_BLESSING = 8

        /** `MAX_CONTAINER_DEPTH` (configmanager.cpp:309) used by `Container::iterator`. */
        const val MAX_CONTAINER_DEPTH = 200

        /** `Player` harmony bar maximum (`player.cpp:13293`). */
        private const val MAX_HARMONY = 5

        /**
         * Matches Canary / OpenTibia formula:
         * (50 * n^3 - 300 * n^2 + 850 * n - 600) / 3
         */
        fun getExpForLevel(n: Int): Long {
            if (n <= 1) return 0L
            val l = n.toLong()
            return (50L * l * l * l - 300L * l * l + 850L * l - 600L) / 3L
        }

        /** `Player::getPercentLevel` (`player.cpp:3831-3840`). */
        fun getPercentLevel(count: Long, nextLevelCount: Long): Double {
            if (nextLevelCount == 0L) {
                return 0.0
            }
            val result = Math.round((count * 100.0 / nextLevelCount) * 100.0) / 100.0
            return if (result > 100) 0.0 else result
        }
    }
}

/**
 * `PlayerCyclopedia(Player&)` (`player_cyclopedia.hpp:26`) binding: the
 * component's [CyclopediaPlayer] view over the [Player] it belongs to. The
 * adapter keeps the "live player" lookup semantics of the C++ reference
 * (`kv()` follows later `Player::kv` replacements).
 */
private class PlayerCyclopediaAdapter(private val player: Player) : CyclopediaPlayer {
    override fun getGUID(): Long = player.getGUID()

    override fun getID(): Int = player.id.toInt()

    override fun getName(): String = player.name

    override fun kv(): KV = player.kv

    override fun addAsyncOngoingTask(task: PlayerAsyncTask) {
        player.addAsyncOngoingTask(task)
    }

    override fun resetAsyncOngoingTask(task: PlayerAsyncTask) {
        player.resetAsyncOngoingTask(task)
    }

    override fun sendCyclopediaCharacterRecentDeaths(page: Int, pages: Int, entries: List<RecentDeathEntry>) {
        player.sendCyclopediaCharacterRecentDeaths(page, pages, entries)
    }

    override fun sendCyclopediaCharacterRecentPvPKills(page: Int, pages: Int, entries: List<RecentPvPKillEntry>) {
        player.sendCyclopediaCharacterRecentPvPKills(page, pages, entries)
    }
}
