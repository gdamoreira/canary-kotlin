package org.opentibia.canary.lua.runtime

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

/**
 * Runtime entry point for the Lua event families that are not driven by an inbound packet
 * handler: talkaction, spell, creatureevent, globalevent and moveevent.
 *
 * Every method reports whether a callback was registered and, when one was, posts the
 * matching [LuaEventDispatcher] invocation to the single-threaded [DispatcherLane.WorldCommit]
 * lane so Lua callbacks never run on a Netty I/O thread (AGENTS.md 1.2). It mirrors the
 * `handled` contract of the packet handlers (see `ItemPacketHandler.handleUse`).
 *
 * This is a pure dispatch/confinement layer: it delegates argument ordering and userdata
 * wrapping to [LuaEventDispatcher] and never mutates world state or encodes gameplay
 * decisions (AGENTS.md 1.2/1.4). Plugin this class only where the runtime owns a
 * [LuaEnvironment] and a running [GameDispatcher].
 */
class ScriptingDispatch(
    private val environment: LuaEnvironment,
    private val dispatcher: GameDispatcher,
    /**
     * `Creature::getCreatureEvents` (src/creatures/creature.hpp:952,
     * creature.cpp:1701-1715): the named creature events attached to [Creature]
     * through `Creature::registerCreatureEvent`
     * (src/creatures/creature.cpp:1674-1693), in attachment order; `null` when
     * the port has no per-creature attachment registry for that creature
     * (players and NPCs until their `registerEvent` bindings are wired).
     *
     * The composition root injects the world's per-creature sets (the
     * `MonsterPipelinesHost` entry of each live monster); the permissive `null`
     * default keeps the wave-63 global walk for every registered event of the
     * type (PARITY GAP: it fires events the creature never attached).
     */
    private val creatureEventsOf: (Creature) -> Collection<String>? = { null },
) {

    // --- TalkActions (TalkAction::executeSay) ----------------------------------------

    fun talkActionOnSay(words: String, player: Player, param: String): Boolean =
        submit(environment.talkActions.get(words) != null) {
            LuaEventDispatcher.talkActionOnSay(environment, words, player, param)
        }

    // --- Spells (CombatSpell/InstantSpell/RuneSpell::executeCastSpell) ---------------

    fun spellOnCastSpell(words: String, creature: Creature, variant: LuaVariant): Boolean =
        submit(environment.spells.getInstant(words) != null) {
            LuaEventDispatcher.spellOnCastSpell(environment, words, creature, variant)
        }

    fun spellOnCastRune(runeId: Int, creature: Creature, variant: LuaVariant, isHotkey: Boolean): Boolean =
        submit(environment.spells.getRune(runeId) != null) {
            LuaEventDispatcher.spellOnCastRune(environment, runeId, creature, variant, isHotkey)
        }

    // --- CreatureEvents (CreatureEvent::execute*) ------------------------------------

    fun creatureEventOnLogin(name: String, player: Player): Boolean =
        submitCreature(name) { LuaEventDispatcher.creatureEventOnLogin(environment, name, player) }

    /**
     * Mirrors `CreatureEvents::playerLogin` (src/lua/creature/creatureevent.cpp:63): submits
     * every registered creature event whose type is `login`, in registration order, to the
     * WorldCommit lane so the `onLogin` callbacks run confined. Returns whether at least one
     * login event was registered.
     *
     * Like the C++ registry walk (`for (const auto &it : creatureEvents)`), this is a global
     * dispatch: every registered `login` event fires, not only the ones attached to the
     * player (`creatureEventsOf` is deliberately not consulted).
     */
    fun playerLogin(player: Player): Boolean =
        submitRegisteredCreatureEvents(LOGIN_EVENT_TYPE) { name ->
            LuaEventDispatcher.creatureEventOnLogin(environment, name, player)
        }

    /**
     * Mirrors the `onThink` loop of `Creature::onThink` (src/creatures/creature.cpp:157-163):
     * submits the `think` events attached to [creature] through
     * `Creature::registerCreatureEvent` (`Creature::getCreatureEvents(CREATURE_EVENT_THINK)`),
     * in attachment order, to the WorldCommit lane so the `onThink` callbacks run confined.
     * Returns whether at least one attached think event was dispatched; a creature whose
     * attachment source is absent keeps the permissive global walk (PARITY GAP).
     */
    fun creatureOnThink(creature: Creature, interval: Long): Boolean =
        submitCreatureEvents(creature, THINK_EVENT_TYPE) { name ->
            LuaEventDispatcher.creatureEventOnThink(environment, name, creature, interval)
        }

    /**
     * Mirrors the `onDeath` loop of `Creature::dropCorpse` (src/creatures/creature.cpp:820-824):
     * submits the `death` events attached to [creature] through
     * `Creature::registerCreatureEvent`
     * (`Creature::getCreatureEvents(CREATURE_EVENT_DEATH)`), in attachment order, to the
     * WorldCommit lane so the `onDeath` callbacks run confined. Returns whether at least
     * one attached death event was dispatched; a creature whose attachment source is
     * absent keeps the permissive global walk (PARITY GAP).
     */
    fun creatureOnDeath(
        creature: Creature,
        corpse: Item?,
        killer: Creature?,
        mostDamageKiller: Creature?,
        lastHitUnjustified: Boolean,
        mostDamageUnjustified: Boolean,
    ): Boolean =
        submitCreatureEvents(creature, DEATH_EVENT_TYPE) { name ->
            LuaEventDispatcher.creatureEventOnDeath(
                environment, name, creature, corpse, killer, mostDamageKiller,
                lastHitUnjustified, mostDamageUnjustified,
            )
        }

    fun creatureEventOnLogout(name: String, player: Player): Boolean =
        submitCreature(name) { LuaEventDispatcher.creatureEventOnLogout(environment, name, player) }

    fun creatureEventOnThink(name: String, creature: Creature, interval: Long): Boolean =
        submitCreature(name) { LuaEventDispatcher.creatureEventOnThink(environment, name, creature, interval) }

    fun creatureEventOnPrepareDeath(
        name: String,
        creature: Creature,
        killer: Creature?,
        realDamage: Int,
    ): Boolean =
        submitCreature(name) {
            LuaEventDispatcher.creatureEventOnPrepareDeath(environment, name, creature, killer, realDamage)
        }

    fun creatureEventOnDeath(
        name: String,
        creature: Creature,
        corpse: Item?,
        killer: Creature?,
        mostDamageKiller: Creature?,
        lastHitUnjustified: Boolean,
        mostDamageUnjustified: Boolean,
    ): Boolean =
        submitCreature(name) {
            LuaEventDispatcher.creatureEventOnDeath(
                environment, name, creature, corpse, killer, mostDamageKiller,
                lastHitUnjustified, mostDamageUnjustified,
            )
        }

    fun creatureEventOnKill(
        name: String,
        creature: Creature,
        target: Creature,
        lastHit: Boolean,
    ): Boolean =
        submitCreature(name) {
            LuaEventDispatcher.creatureEventOnKill(environment, name, creature, target, lastHit)
        }

    fun creatureEventOnAdvance(
        name: String,
        player: Player,
        skill: Int,
        oldLevel: Int,
        newLevel: Int,
    ): Boolean =
        submitCreature(name) {
            LuaEventDispatcher.creatureEventOnAdvance(environment, name, player, skill, oldLevel, newLevel)
        }

    fun creatureEventOnModalWindow(
        name: String,
        player: Player,
        modalWindowId: Int,
        buttonId: Int,
        choiceId: Int,
    ): Boolean =
        submitCreature(name) {
            LuaEventDispatcher.creatureEventOnModalWindow(
                environment, name, player, modalWindowId, buttonId, choiceId,
            )
        }

    /**
     * Mirrors the `CREATURE_EVENT_MODALWINDOW` loop of `Game::playerAnswerModalWindow`
     * (src/game/game.cpp:11171-11175): submits the `modalwindow` events attached to
     * [player] through `Creature::registerCreatureEvent`
     * (`Player::getCreatureEvents(CREATURE_EVENT_MODALWINDOW)`), in attachment order,
     * to the WorldCommit lane so the callbacks run confined. Returns whether at least
     * one attached modal-window event was dispatched; a player whose attachment source
     * is absent keeps the permissive global walk (PARITY GAP). The `uint32_t` window id
     * is narrowed to the Lua `Int` argument of
     * [LuaEventDispatcher.creatureEventOnModalWindow]; the hardcoded offline-training id
     * never reaches this path (game.cpp:11157).
     */
    fun playerAnswerModalWindow(
        player: Player,
        modalWindowId: Long,
        button: Int,
        choice: Int,
    ): Boolean =
        submitCreatureEvents(player, MODALWINDOW_EVENT_TYPE) { name ->
            LuaEventDispatcher.creatureEventOnModalWindow(
                environment, name, player, modalWindowId.toInt(), button, choice,
            )
        }

    fun creatureEventOnTextEdit(name: String, player: Player, item: Item?, text: String): Boolean =
        submitCreature(name) { LuaEventDispatcher.creatureEventOnTextEdit(environment, name, player, item, text) }

    fun creatureEventOnExtendedOpcode(name: String, player: Player, opcode: Int, buffer: String): Boolean =
        submitCreature(name) {
            LuaEventDispatcher.creatureEventOnExtendedOpcode(environment, name, player, opcode, buffer)
        }

    // --- GlobalEvents (GlobalEvent::executeEvent/executeRecord) ----------------------

    /**
     * Mirrors `GlobalEvents::startup` (src/lua/global/globalevent.cpp:112-114) and its
     * `execute(GLOBALEVENT_STARTUP)` loop (globalevent.cpp:210-216): submits every registered
     * global event whose type is `startup`, in registration order, to the WorldCommit lane
     * so the `onStartup` callbacks run confined. Returns whether at least one startup event
     * was registered.
     */
    fun globalStartup(): Boolean =
        submitGlobalEvents(STARTUP_EVENT_TYPE) { name ->
            LuaEventDispatcher.globalEventOnStartup(environment, name)
        }

    /**
     * Mirrors `Game::checkPlayersRecord` (src/game/game.cpp:9625-9636): submits every
     * registered global event whose type is `record`, in registration order, to the
     * WorldCommit lane so the `onRecord` callbacks receive `current`/`old` confined.
     * Returns whether at least one record event was registered.
     */
    fun globalRecord(current: Long, old: Long): Boolean =
        submitGlobalEvents(RECORD_EVENT_TYPE) { name ->
            LuaEventDispatcher.globalEventOnRecord(environment, name, current, old)
        }

    fun globalEventOnThink(name: String): Boolean =
        submitGlobal(name) { LuaEventDispatcher.globalEventOnThink(environment, name) }

    fun globalEventOnTime(name: String): Boolean =
        submitGlobal(name) { LuaEventDispatcher.globalEventOnTime(environment, name) }

    fun globalEventOnRecord(name: String, current: Long, old: Long): Boolean =
        submitGlobal(name) { LuaEventDispatcher.globalEventOnRecord(environment, name, current, old) }

    // --- MoveEvents (MoveEvent::executeStep/executeEquip/executeAddRemItem) ----------

    fun moveEventOnStepIn(
        itemId: Int,
        creature: Creature,
        item: Item?,
        pos: Position,
        fromPosition: Position,
    ): Boolean = submitMove(itemId, STEP_IN_EVENT_TYPE) {
        LuaEventDispatcher.moveEventOnStepIn(environment, itemId, creature, item, pos, fromPosition)
    }

    fun moveEventOnStepOut(
        itemId: Int,
        creature: Creature,
        item: Item?,
        pos: Position,
        fromPosition: Position,
    ): Boolean = submitMove(itemId, STEP_OUT_EVENT_TYPE) {
        LuaEventDispatcher.moveEventOnStepOut(environment, itemId, creature, item, pos, fromPosition)
    }

    fun moveEventOnEquip(
        itemId: Int,
        player: Player,
        item: Item,
        slot: Int,
        isCheck: Boolean,
    ): Boolean = submitMove(itemId, EQUIP_EVENT_TYPE) {
        LuaEventDispatcher.moveEventOnEquip(environment, itemId, player, item, slot, isCheck)
    }

    fun moveEventOnDeEquip(
        itemId: Int,
        player: Player,
        item: Item,
        slot: Int,
        isCheck: Boolean,
    ): Boolean = submitMove(itemId, DE_EQUIP_EVENT_TYPE) {
        LuaEventDispatcher.moveEventOnDeEquip(environment, itemId, player, item, slot, isCheck)
    }

    fun moveEventOnAddItem(
        itemId: Int,
        moveItem: Item,
        tileItem: Item?,
        pos: Position,
    ): Boolean = submitMove(itemId, ADD_ITEM_EVENT_TYPE) {
        LuaEventDispatcher.moveEventOnAddItem(environment, itemId, moveItem, tileItem, pos)
    }

    fun moveEventOnRemoveItem(
        itemId: Int,
        moveItem: Item,
        tileItem: Item?,
        pos: Position,
    ): Boolean = submitMove(itemId, REMOVE_ITEM_EVENT_TYPE) {
        LuaEventDispatcher.moveEventOnRemoveItem(environment, itemId, moveItem, tileItem, pos)
    }

    // --- Confinement helpers ---------------------------------------------------------

    private fun submit(registered: Boolean, invoke: () -> Boolean): Boolean {
        if (!registered) {
            return false
        }
        dispatcher.dispatch(DispatcherLane.WorldCommit) { invoke() }
        return true
    }

    private fun submitCreature(name: String, invoke: () -> Boolean): Boolean =
        submit(environment.creatureEvents.get(name) != null, invoke)

    private fun submitGlobal(name: String, invoke: () -> Boolean): Boolean =
        submit(environment.globalEvents.get(name) != null, invoke)

    private fun submitMove(itemId: Int, eventType: String, invoke: () -> Boolean): Boolean =
        submit(environment.moveEvents.getByItemId(itemId).any { it.eventType == eventType }, invoke)

    /**
     * `Creature::getCreatureEvents(type)` walk: resolves the attached names for
     * [creature] through [creatureEventsOf] and keeps only those the registry
     * knows under [eventType] (an attached name the registry never registered is
     * dropped, mirroring the typed `CreatureEventList`). When the seam has no
     * attachment source for the creature (`null`), the walk falls back to every
     * registered event of the type (wave-63 behavior, PARITY GAP).
     */
    private fun submitCreatureEvents(
        creature: Creature,
        eventType: String,
        invoke: (String) -> Boolean,
    ): Boolean {
        val attached = creatureEventsOf(creature)
        val candidates = attached ?: environment.creatureEvents.names()
        val names = candidates.filter { environment.creatureEvents.get(it)?.eventType == eventType }
        return submit(names.isNotEmpty()) {
            names.forEach { invoke(it) }
            true
        }
    }

    /** The global `CreatureEvents::playerLogin`-style registry walk filtered by [eventType]. */
    private fun submitRegisteredCreatureEvents(eventType: String, invoke: (String) -> Boolean): Boolean {
        val names = environment.creatureEvents.names()
            .filter { environment.creatureEvents.get(it)?.eventType == eventType }
        return submit(names.isNotEmpty()) {
            names.forEach { invoke(it) }
            true
        }
    }

    private fun submitGlobalEvents(eventType: String, invoke: (String) -> Boolean): Boolean {
        val names = environment.globalEvents.names()
            .filter { environment.globalEvents.get(it)?.eventType == eventType }
        return submit(names.isNotEmpty()) {
            names.forEach { invoke(it) }
            true
        }
    }

    private companion object {
        const val LOGIN_EVENT_TYPE = "login"
        const val THINK_EVENT_TYPE = "think"
        const val DEATH_EVENT_TYPE = "death"
        const val STARTUP_EVENT_TYPE = "startup"
        const val RECORD_EVENT_TYPE = "record"
        const val STEP_IN_EVENT_TYPE = "stepin"
        const val STEP_OUT_EVENT_TYPE = "stepout"
        const val MODALWINDOW_EVENT_TYPE = "modalwindow"
        const val EQUIP_EVENT_TYPE = "equip"
        const val DE_EQUIP_EVENT_TYPE = "deequip"
        const val ADD_ITEM_EVENT_TYPE = "additem"
        const val REMOVE_ITEM_EVENT_TYPE = "removeitem"
    }
}
