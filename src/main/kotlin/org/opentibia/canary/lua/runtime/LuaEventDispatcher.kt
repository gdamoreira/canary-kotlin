package org.opentibia.canary.lua.runtime

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.registry.EventCallbackType
import org.opentibia.canary.lua.registry.LuaMoveEvent
import org.opentibia.canary.map.Position

/**
 * Mirrors Canary's `LuaVariantType_t` (src/enums/lua_variant_type.hpp) ordinal order.
 */
enum class LuaVariantType {
    NONE,
    NUMBER,
    POSITION,
    TARGETPOSITION,
    STRING,
}

/**
 * Kotlin counterpart of Canary's `LuaVariant` struct (src/lua/global/lua_variant.hpp): the
 * `variant` argument passed to `onCastSpell` and the weapon callbacks.
 */
data class LuaVariant(
    val type: LuaVariantType = LuaVariantType.NONE,
    val number: Long = 0,
    val text: String = "",
    val instantName: String = "",
    val runeName: String = "",
    val pos: Position? = null,
)

/**
 * Invokes the Lua callbacks registered through the revscriptsys event constructors
 * (`Action`, `TalkAction`, `Spell`, `CreatureEvent`, `GlobalEvent`, `MoveEvent`) with the
 * exact argument order Canary pushes in C++ (src/lua/creature/actions.cpp,
 * talkaction.cpp, movement.cpp, creatureevent.cpp and src/creatures/combat/spells.cpp,
 * src/lua/global/globalevent.cpp).
 *
 * Every registered Lua value is resolved from the [LuaEnvironment] registries and domain
 * objects are wrapped as Canary-named userdata through [LuaEnvironment.userdata] (`Player`,
 * `Monster`, `Npc`, `Item`, `Container`, `Position`). Each method returns `true` when a
 * matching callback was found and invoked, `false` when nothing is registered, mirroring the
 * `handled` contract of the packet handlers.
 *
 * This is a pure dispatch layer: it never mutates world state and never encodes gameplay
 * decisions (AGENTS.md 1.2/1.4). Callers are expected to run it on the `WorldCommit` lane.
 */
object LuaEventDispatcher {

    // --- Actions (Action::executeUse) ------------------------------------------------

    fun actionOnUse(
        env: LuaEnvironment,
        actionId: Int,
        player: Player,
        item: Item,
        fromPosition: Position,
        target: Any?,
        toPosition: Position,
        isHotkey: Boolean,
    ): Boolean {
        val action = env.actions.get(actionId) ?: return false
        return invoke(
            action.onUse,
            arrayOf(
                wrapCreature(env, player),
                pushThing(env, item),
                wrapPosition(env, fromPosition),
                pushThing(env, target),
                wrapPosition(env, toPosition),
                LuaValue.valueOf(isHotkey),
            ),
        )
    }

    // --- TalkActions (TalkAction::executeSay) ----------------------------------------

    fun talkActionOnSay(
        env: LuaEnvironment,
        words: String,
        player: Player,
        param: String,
    ): Boolean {
        val talkAction = env.talkActions.get(words) ?: return false
        return invoke(
            talkAction.onSay,
            arrayOf(
                wrapCreature(env, player),
                LuaValue.valueOf(words),
                LuaValue.valueOf(param),
            ),
        )
    }

    // --- Spells (CombatSpell/InstantSpell/RuneSpell::executeCastSpell) ---------------

    fun spellOnCastSpell(
        env: LuaEnvironment,
        words: String,
        creature: Creature,
        variant: LuaVariant,
    ): Boolean {
        val spell = env.spells.getInstant(words) ?: return false
        return invoke(
            spell.onCastSpell,
            arrayOf(wrapCreature(env, creature), pushVariant(env, variant)),
        )
    }

    fun spellOnCastRune(
        env: LuaEnvironment,
        runeId: Int,
        creature: Creature,
        variant: LuaVariant,
        isHotkey: Boolean,
    ): Boolean {
        val spell = env.spells.getRune(runeId) ?: return false
        return invoke(
            spell.onCastSpell,
            arrayOf(
                wrapCreature(env, creature),
                pushVariant(env, variant),
                LuaValue.valueOf(isHotkey),
            ),
        )
    }

    // --- CreatureEvents (CreatureEvent::execute*) ------------------------------------

    fun creatureEventOnLogin(env: LuaEnvironment, name: String, player: Player): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(event.callback, arrayOf(wrapCreature(env, player)))
    }

    fun creatureEventOnLogout(env: LuaEnvironment, name: String, player: Player): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(event.callback, arrayOf(wrapCreature(env, player)))
    }

    fun creatureEventOnThink(
        env: LuaEnvironment,
        name: String,
        creature: Creature,
        interval: Long,
    ): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(
            event.callback,
            arrayOf(wrapCreature(env, creature), LuaValue.valueOf(interval.toDouble())),
        )
    }

    fun creatureEventOnPrepareDeath(
        env: LuaEnvironment,
        name: String,
        creature: Creature,
        killer: Creature?,
        realDamage: Int,
    ): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(
            event.callback,
            arrayOf(
                wrapCreature(env, creature),
                killer?.let { wrapCreature(env, it) } ?: LuaValue.NIL,
                LuaValue.valueOf(realDamage),
            ),
        )
    }

    fun creatureEventOnDeath(
        env: LuaEnvironment,
        name: String,
        creature: Creature,
        corpse: Item?,
        killer: Creature?,
        mostDamageKiller: Creature?,
        lastHitUnjustified: Boolean,
        mostDamageUnjustified: Boolean,
    ): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(
            event.callback,
            arrayOf(
                wrapCreature(env, creature),
                pushThing(env, corpse),
                killer?.let { wrapCreature(env, it) } ?: LuaValue.NIL,
                mostDamageKiller?.let { wrapCreature(env, it) } ?: LuaValue.NIL,
                LuaValue.valueOf(lastHitUnjustified),
                LuaValue.valueOf(mostDamageUnjustified),
            ),
        )
    }

    fun creatureEventOnKill(
        env: LuaEnvironment,
        name: String,
        creature: Creature,
        target: Creature,
        lastHit: Boolean,
    ): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(
            event.callback,
            arrayOf(
                wrapCreature(env, creature),
                wrapCreature(env, target),
                LuaValue.valueOf(lastHit),
            ),
        )
    }

    fun creatureEventOnAdvance(
        env: LuaEnvironment,
        name: String,
        player: Player,
        skill: Int,
        oldLevel: Int,
        newLevel: Int,
    ): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(
            event.callback,
            arrayOf(
                wrapCreature(env, player),
                LuaValue.valueOf(skill),
                LuaValue.valueOf(oldLevel),
                LuaValue.valueOf(newLevel),
            ),
        )
    }

    fun creatureEventOnModalWindow(
        env: LuaEnvironment,
        name: String,
        player: Player,
        modalWindowId: Int,
        buttonId: Int,
        choiceId: Int,
    ): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(
            event.callback,
            arrayOf(
                wrapCreature(env, player),
                LuaValue.valueOf(modalWindowId),
                LuaValue.valueOf(buttonId),
                LuaValue.valueOf(choiceId),
            ),
        )
    }

    fun creatureEventOnTextEdit(
        env: LuaEnvironment,
        name: String,
        player: Player,
        item: Item?,
        text: String,
    ): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(
            event.callback,
            arrayOf(
                wrapCreature(env, player),
                pushThing(env, item),
                LuaValue.valueOf(text),
            ),
        )
    }

    fun creatureEventOnExtendedOpcode(
        env: LuaEnvironment,
        name: String,
        player: Player,
        opcode: Int,
        buffer: String,
    ): Boolean {
        val event = env.creatureEvents.get(name) ?: return false
        return invoke(
            event.callback,
            arrayOf(
                wrapCreature(env, player),
                LuaValue.valueOf(opcode),
                LuaValue.valueOf(buffer),
            ),
        )
    }

    // --- GlobalEvents (GlobalEvent::executeEvent/executeRecord) ----------------------

    fun globalEventOnStartup(env: LuaEnvironment, name: String): Boolean =
        globalEventExecuteEvent(env, name)

    fun globalEventOnThink(env: LuaEnvironment, name: String): Boolean =
        globalEventExecuteEvent(env, name)

    fun globalEventOnTime(env: LuaEnvironment, name: String): Boolean =
        globalEventExecuteEvent(env, name)

    /** `GlobalEvent::executeEvent(GLOBALEVENT_SAVE)` (globalevent.cpp:198-208): callback with no args. */
    fun globalEventOnSave(env: LuaEnvironment, name: String): Boolean =
        globalEventExecuteEvent(env, name)

    /** `GlobalEvent::executeEvent(GLOBALEVENT_SHUTDOWN)`: callback with no args. */
    fun globalEventOnShutdown(env: LuaEnvironment, name: String): Boolean =
        globalEventExecuteEvent(env, name)

    fun globalEventOnRecord(env: LuaEnvironment, name: String, current: Long, old: Long): Boolean {
        val event = env.globalEvents.get(name) ?: return false
        return invoke(
            event.callback,
            arrayOf(LuaValue.valueOf(current.toDouble()), LuaValue.valueOf(old.toDouble())),
        )
    }

    private fun globalEventExecuteEvent(env: LuaEnvironment, name: String): Boolean {
        val event = env.globalEvents.get(name) ?: return false
        val args: Array<LuaValue> = if (event.eventType.isEmpty() || event.eventType == TIMER_EVENT_TYPE) {
            arrayOf(LuaValue.valueOf(event.interval))
        } else {
            emptyArray()
        }
        return invoke(event.callback, args)
    }

    // --- MoveEvents (MoveEvent::executeStep/executeEquip/executeAddRemItem) ----------

    fun moveEventOnStepIn(
        env: LuaEnvironment,
        itemId: Int,
        creature: Creature,
        item: Item?,
        pos: Position,
        fromPosition: Position,
    ): Boolean = moveEventStep(env, itemId, STEP_IN_EVENT_TYPE, creature, item, pos, fromPosition)

    fun moveEventOnStepOut(
        env: LuaEnvironment,
        itemId: Int,
        creature: Creature,
        item: Item?,
        pos: Position,
        fromPosition: Position,
    ): Boolean = moveEventStep(env, itemId, STEP_OUT_EVENT_TYPE, creature, item, pos, fromPosition)

    fun moveEventOnEquip(
        env: LuaEnvironment,
        itemId: Int,
        player: Player,
        item: Item,
        slot: Int,
        isCheck: Boolean,
    ): Boolean = moveEventEquip(env, itemId, EQUIP_EVENT_TYPE, player, item, slot, isCheck)

    fun moveEventOnDeEquip(
        env: LuaEnvironment,
        itemId: Int,
        player: Player,
        item: Item,
        slot: Int,
        isCheck: Boolean,
    ): Boolean = moveEventEquip(env, itemId, DE_EQUIP_EVENT_TYPE, player, item, slot, isCheck)

    fun moveEventOnAddItem(
        env: LuaEnvironment,
        itemId: Int,
        moveItem: Item,
        tileItem: Item?,
        pos: Position,
    ): Boolean = moveEventAddRemoveItem(env, itemId, ADD_ITEM_EVENT_TYPE, moveItem, tileItem, pos)

    fun moveEventOnRemoveItem(
        env: LuaEnvironment,
        itemId: Int,
        moveItem: Item,
        tileItem: Item?,
        pos: Position,
    ): Boolean = moveEventAddRemoveItem(env, itemId, REMOVE_ITEM_EVENT_TYPE, moveItem, tileItem, pos)

    /**
     * `MoveEvent::executeStep` (movement.cpp:738-793) for a registry event the
     * caller already resolved (`MoveEvents::onCreatureMove`, movement.cpp:290-322):
     * `onStepIn(creature, item, position, fromPosition)` /
     * `onStepOut(creature, item, position, fromPosition)`. Returns the Lua
     * result through the `LuaScriptInterface::callFunction` default-true
     * boolean (luascript.cpp:793-810), so a script returning `false` stops the
     * caller's item scan like the C++ `step == 0` break.
     */
    fun moveEventExecuteStep(
        env: LuaEnvironment,
        event: LuaMoveEvent,
        creature: Creature,
        item: Item?,
        pos: Position,
        fromPosition: Position,
    ): Boolean {
        val args = arrayOf(
            wrapCreature(env, creature),
            pushThing(env, item),
            wrapPosition(env, pos),
            wrapPosition(env, fromPosition),
        )
        return invokeBoolean(event.callback, args)
    }

    /**
     * `MoveEvent::executeAddRemItem` (movement.cpp:838-856) for a registry
     * event the caller already resolved (`MoveEvents::onItemMove`,
     * movement.cpp:344-390): `onAddItem`/`onRemoveItem(moveItem, tileItem,
     * position)`. Returns the Lua result through the
     * `LuaScriptInterface::callFunction` default-true boolean
     * (luascript.cpp:793-810).
     */
    fun moveEventExecuteAddRemItem(
        env: LuaEnvironment,
        event: LuaMoveEvent,
        moveItem: Item,
        tileItem: Item?,
        pos: Position,
    ): Boolean {
        val args = arrayOf(
            pushThing(env, moveItem),
            pushThing(env, tileItem),
            wrapPosition(env, pos),
        )
        return invokeBoolean(event.callback, args)
    }

    private fun moveEventStep(
        env: LuaEnvironment,
        itemId: Int,
        eventType: String,
        creature: Creature,
        item: Item?,
        pos: Position,
        fromPosition: Position,
    ): Boolean {
        val events = env.moveEvents.getByItemId(itemId).filter { it.eventType == eventType }
        if (events.isEmpty()) {
            return false
        }
        val args = arrayOf(
            wrapCreature(env, creature),
            pushThing(env, item),
            wrapPosition(env, pos),
            wrapPosition(env, fromPosition),
        )
        events.forEach { invoke(it.callback, args) }
        return true
    }

    private fun moveEventEquip(
        env: LuaEnvironment,
        itemId: Int,
        eventType: String,
        player: Player,
        item: Item,
        slot: Int,
        isCheck: Boolean,
    ): Boolean {
        val events = env.moveEvents.getByItemId(itemId).filter { it.eventType == eventType }
        if (events.isEmpty()) {
            return false
        }
        val args = arrayOf(
            wrapCreature(env, player),
            pushThing(env, item),
            LuaValue.valueOf(slot),
            LuaValue.valueOf(isCheck),
        )
        events.forEach { invoke(it.callback, args) }
        return true
    }

    private fun moveEventAddRemoveItem(
        env: LuaEnvironment,
        itemId: Int,
        eventType: String,
        moveItem: Item,
        tileItem: Item?,
        pos: Position,
    ): Boolean {
        val events = env.moveEvents.getByItemId(itemId).filter { it.eventType == eventType }
        if (events.isEmpty()) {
            return false
        }
        val args = arrayOf(
            pushThing(env, moveItem),
            pushThing(env, tileItem),
            wrapPosition(env, pos),
        )
        events.forEach { invoke(it.callback, args) }
        return true
    }

    // --- EventCallbacks (EventsCallbacks::executeCallback) ---------------------------

    /**
     * `g_callbacks().executeCallback(EventCallback_t::playerOnRequestQuestLog, player)`
     * (src/game/game.cpp:7397): invokes every registered callback in
     * registration order. Returns whether at least one callback was registered.
     */
    fun eventCallbackPlayerOnRequestQuestLog(env: LuaEnvironment, player: Player): Boolean =
        executeEventCallbacks(env, EventCallbackType.playerOnRequestQuestLog, arrayOf(wrapCreature(env, player)))

    /**
     * `g_callbacks().executeCallback(EventCallback_t::playerOnRequestQuestLine, player, questId)`
     * (src/game/game.cpp:7407).
     */
    fun eventCallbackPlayerOnRequestQuestLine(env: LuaEnvironment, player: Player, questId: Int): Boolean =
        executeEventCallbacks(
            env,
            EventCallbackType.playerOnRequestQuestLine,
            arrayOf(wrapCreature(env, player), LuaValue.valueOf(questId)),
        )

    /**
     * `EventsCallbacks::executeCallback` (src/lua/callbacks/events_callbacks.hpp:88):
     * runs every registered callback of [type] in registration order. PARITY GAP:
     * Canary's per-callback `canExecute` script-id/enabled checks have no
     * counterpart in the ported registry.
     */
    private fun executeEventCallbacks(env: LuaEnvironment, type: EventCallbackType, args: Array<LuaValue>): Boolean {
        val entries = env.eventCallbacks.get(type)
        if (entries.isEmpty()) {
            return false
        }

        entries.forEach { invoke(it.callback, args) }
        return true
    }

    // --- Wrapping helpers ------------------------------------------------------------

    private fun invoke(callback: LuaValue, args: Array<LuaValue>): Boolean {
        callback.invoke(LuaValue.varargsOf(args))
        return true
    }

    /**
     * `LuaScriptInterface::callFunction`'s return conversion
     * (luascript.cpp:793-810): `Lua::getBoolean(L, -1, true)`, so a nil result
     * defaults to `true` exactly like the C++ helper.
     */
    private fun invokeBoolean(callback: LuaValue, args: Array<LuaValue>): Boolean {
        val result = callback.invoke(LuaValue.varargsOf(args)).arg1()
        return if (result.isnil()) true else result.toboolean()
    }

    /**
     * Mirrors Canary's `Lua::setCreatureMetatable`: chooses the `Player`, `Monster` or
     * `Npc` metatable based on the runtime creature, falling back to the shared `Creature`.
     */
    private fun wrapCreature(env: LuaEnvironment, creature: Creature): LuaValue =
        env.userdata.wrap(
            when (creature) {
                is Player -> "Player"
                is Monster -> "Monster"
                is Npc -> "Npc"
                else -> "Creature"
            },
            creature,
        )

    /** Mirrors Canary's `Lua::setItemMetatable`: containers get the `Container` metatable. */
    private fun wrapItem(env: LuaEnvironment, item: Item): LuaValue =
        env.userdata.wrap(if (item is Container) "Container" else "Item", item)

    private fun wrapPosition(env: LuaEnvironment, position: Position): LuaValue =
        env.userdata.wrap("Position", position)

    /**
     * Mirrors Canary's `Lua::pushThing`: a null thing becomes the sentinel table
     * `{uid=0, itemid=0, actionid=0, type=0}`, items and creatures become their userdata.
     */
    private fun pushThing(env: LuaEnvironment, thing: Any?): LuaValue = when (thing) {
        null -> LuaTable().apply {
            set("uid", LuaValue.ZERO)
            set("itemid", LuaValue.ZERO)
            set("actionid", LuaValue.ZERO)
            set("type", LuaValue.ZERO)
        }
        is Item -> wrapItem(env, thing)
        is Creature -> wrapCreature(env, thing)
        else -> LuaValue.NIL
    }

    /**
     * Mirrors Canary's `Lua::pushVariant`: builds a table carrying `type`, the type-specific
     * value, `instantName`/`runeName`, and sets the `Variant` metatable.
     */
    private fun pushVariant(env: LuaEnvironment, variant: LuaVariant): LuaValue {
        val table = LuaTable()
        table.set("type", LuaValue.valueOf(variant.type.ordinal))
        when (variant.type) {
            LuaVariantType.NUMBER -> table.set("number", LuaValue.valueOf(variant.number.toDouble()))
            LuaVariantType.STRING -> table.set("string", LuaValue.valueOf(variant.text))
            LuaVariantType.POSITION, LuaVariantType.TARGETPOSITION ->
                table.set("pos", wrapPosition(env, variant.pos ?: Position(0, 0, 0)))
            LuaVariantType.NONE -> Unit
        }
        table.set("instantName", LuaValue.valueOf(variant.instantName))
        table.set("runeName", LuaValue.valueOf(variant.runeName))
        table.setmetatable(env.userdata.metatable(VARIANT_TYPE))
        return table
    }

    private const val VARIANT_TYPE = "Variant"
    private const val TIMER_EVENT_TYPE = "timer"
    private const val STEP_IN_EVENT_TYPE = "stepin"
    private const val STEP_OUT_EVENT_TYPE = "stepout"
    private const val EQUIP_EVENT_TYPE = "equip"
    private const val DE_EQUIP_EVENT_TYPE = "deequip"
    private const val ADD_ITEM_EVENT_TYPE = "additem"
    private const val REMOVE_ITEM_EVENT_TYPE = "removeitem"
}
