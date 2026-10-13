package org.opentibia.canary.lua.bindings.events

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Covers `src/lua/functions/events/events_scheduler_functions.cpp`: the global
 * `EventsScheduler` table and its five rate getters backed by the domain
 * [org.opentibia.canary.game.scheduling.EventsScheduler].
 */
class EventsSchedulerFunctionsTest {
    private fun env(): LuaEnvironment = LuaEnvironment().apply { initState() }

    @Test
    fun `EventsScheduler getters expose the domain rates`() {
        val environment = env()
        environment.eventsScheduler.setExpSchedule(200)
        environment.eventsScheduler.setLootSchedule(150)
        environment.eventsScheduler.setBossLootSchedule(300)
        environment.eventsScheduler.setSkillSchedule(110)
        environment.eventsScheduler.setSpawnMonsterSchedule(50)

        environment.loadChunk(
            """
            exp = EventsScheduler.getEventSExp()
            loot = EventsScheduler.getEventSLoot()
            boss = EventsScheduler.getEventSBossLoot()
            skill = EventsScheduler.getEventSSkill()
            spawn = EventsScheduler.getSpawnMonsterSchedule()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("exp").toint()).isEqualTo(200)
        assertThat(environment.getGlobal("loot").toint()).isEqualTo(150)
        assertThat(environment.getGlobal("boss").toint()).isEqualTo(300)
        assertThat(environment.getGlobal("skill").toint()).isEqualTo(110)
        assertThat(environment.getGlobal("spawn").toint()).isEqualTo(50)
    }

    @Test
    fun `EventsScheduler rates default to one hundred`() {
        val environment = env()
        environment.loadChunk(
            """
            exp = EventsScheduler.getEventSExp()
            loot = EventsScheduler.getEventSLoot()
            boss = EventsScheduler.getEventSBossLoot()
            skill = EventsScheduler.getEventSSkill()
            spawn = EventsScheduler.getSpawnMonsterSchedule()
            """.trimIndent()
        )

        assertThat(environment.getGlobal("exp").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("loot").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("boss").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("skill").toint()).isEqualTo(100)
        assertThat(environment.getGlobal("spawn").toint()).isEqualTo(100)
    }
}
