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

    fun creatureEventOnTextEdit(name: String, player: Player, item: Item?, text: String): Boolean =
        submitCreature(name) { LuaEventDispatcher.creatureEventOnTextEdit(environment, name, player, item, text) }

    fun creatureEventOnExtendedOpcode(name: String, player: Player, opcode: Int, buffer: String): Boolean =
        submitCreature(name) {
            LuaEventDispatcher.creatureEventOnExtendedOpcode(environment, name, player, opcode, buffer)
        }

    // --- GlobalEvents (GlobalEvent::executeEvent/executeRecord) ----------------------

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

    private companion object {
        const val STEP_IN_EVENT_TYPE = "stepin"
        const val STEP_OUT_EVENT_TYPE = "stepout"
        const val EQUIP_EVENT_TYPE = "equip"
        const val DE_EQUIP_EVENT_TYPE = "deequip"
        const val ADD_ITEM_EVENT_TYPE = "additem"
        const val REMOVE_ITEM_EVENT_TYPE = "removeitem"
    }
}
