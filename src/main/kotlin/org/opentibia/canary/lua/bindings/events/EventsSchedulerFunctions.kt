package org.opentibia.canary.lua.bindings.events

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.luaj.vm2.lib.VarArgFunction
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Registers the global `EventsScheduler` table, mirroring
 * `src/lua/functions/events/events_scheduler_functions.cpp`: five rate getters backed by
 * the domain [org.opentibia.canary.game.scheduling.EventsScheduler].
 */
object EventsSchedulerFunctions {
    fun install(env: LuaEnvironment) {
        val scheduler = env.eventsScheduler

        val table = LuaTable()
        table.set("getEventSLoot", getter { LuaValue.valueOf(scheduler.getLootSchedule()) })
        table.set("getEventSBossLoot", getter { LuaValue.valueOf(scheduler.getBossLootSchedule()) })
        table.set("getEventSSkill", getter { LuaValue.valueOf(scheduler.getSkillSchedule()) })
        table.set("getEventSExp", getter { LuaValue.valueOf(scheduler.getExpSchedule()) })
        table.set("getSpawnMonsterSchedule", getter { LuaValue.valueOf(scheduler.getSpawnMonsterSchedule()) })
        env.globals.set("EventsScheduler", table)
    }

    private fun getter(value: () -> LuaValue): VarArgFunction = object : VarArgFunction() {
        override fun invoke(args: Varargs): Varargs = value()
    }
}
