package org.opentibia.canary.game.scheduling

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.nio.file.Files
import java.nio.file.Path
import java.util.GregorianCalendar

class EventsSchedulerTest {

    @TempDir
    lateinit var tempDir: Path

    @Test
    fun `schedule setters compound rates like canary`() {
        val scheduler = EventsScheduler(now = { 0 })

        scheduler.setExpSchedule(200)
        assertThat(scheduler.getExpSchedule()).isEqualTo(200)
        scheduler.setExpSchedule(150)
        assertThat(scheduler.getExpSchedule()).isEqualTo(300)

        scheduler.setLootSchedule(250)
        assertThat(scheduler.getLootSchedule()).isEqualTo(250)
        scheduler.setBossLootSchedule(200)
        assertThat(scheduler.getBossLootSchedule()).isEqualTo(200)
        scheduler.setSpawnMonsterSchedule(50)
        assertThat(scheduler.getSpawnMonsterSchedule()).isEqualTo(50)
        scheduler.setSkillSchedule(120)
        assertThat(scheduler.getSkillSchedule()).isEqualTo(120)

        scheduler.reset()
        assertThat(scheduler.getExpSchedule()).isEqualTo(100)
        assertThat(scheduler.getLootSchedule()).isEqualTo(100)
        assertThat(scheduler.getBossLootSchedule()).isEqualTo(100)
        assertThat(scheduler.getSpawnMonsterSchedule()).isEqualTo(100)
        assertThat(scheduler.getSkillSchedule()).isEqualTo(100)
    }

    @Test
    fun `active events respect the injected clock boundaries`() {
        var now = epoch(2026, 1, 10, 8, 0)
        val scheduler = EventsScheduler(coreDirectory = tempDir.toString(), now = { now })
        writeEventsJson(
            """
            {
              "events": [
                {
                  "name": "Morning",
                  "startdate": "1/10/2026",
                  "starthour": "8:00",
                  "enddate": "1/10/2026",
                  "endhour": "10:00",
                  "ingame": { "exprate": 200 }
                }
              ]
            }
            """.trimIndent(),
        )

        assertThat(scheduler.loadScheduleEventFromJson()).isTrue()
        assertThat(scheduler.getActiveEvents()).containsExactly("Morning")

        now = epoch(2026, 1, 10, 8, 0) - 1
        assertThat(scheduler.getActiveEvents()).isEmpty()

        now = epoch(2026, 1, 10, 8, 0)
        assertThat(scheduler.getActiveEvents()).containsExactly("Morning")

        now = epoch(2026, 1, 10, 10, 0)
        assertThat(scheduler.getActiveEvents()).containsExactly("Morning")

        now = epoch(2026, 1, 10, 10, 0) + 1
        assertThat(scheduler.getActiveEvents()).isEmpty()
    }

    @Test
    fun `defaults to the whole day when no hour is configured`() {
        var now = epoch(2026, 1, 10, 12, 0)
        val scheduler = EventsScheduler(coreDirectory = tempDir.toString(), now = { now })
        writeEventsJson(
            """
            {
              "events": [
                {
                  "name": "Whole day",
                  "startdate": "1/10/2026",
                  "enddate": "1/10/2026",
                  "ingame": { "lootrate": 150 }
                }
              ]
            }
            """.trimIndent(),
        )

        assertThat(scheduler.loadScheduleEventFromJson()).isTrue()
        assertThat(scheduler.getActiveEvents()).containsExactly("Whole day")

        now = epoch(2026, 1, 11, 0, 0, 1)
        assertThat(scheduler.getActiveEvents()).isEmpty()
    }

    @Test
    fun `applies ingame rates and settings for active events`() {
        val now = epoch(2026, 1, 10, 12, 0)
        val settings = linkedMapOf<String, Any>()
        val scheduler = EventsScheduler(coreDirectory = tempDir.toString(), now = { now }, settings = settings)
        writeEventsJson(
            """
            {
              "events": [
                {
                  "name": "Rates",
                  "startdate": "1/10/2026",
                  "enddate": "1/10/2026",
                  "ingame": {
                    "exprate": 200,
                    "lootrate": 150,
                    "bosslootrate": 300,
                    "spawnrate": 50,
                    "skillrate": 110,
                    "forgechance": 120,
                    "bosscooldown": 90,
                    "doublebestiary": true,
                    "doublebosstiary": true,
                    "fastexercise": true
                  }
                }
              ]
            }
            """.trimIndent(),
        )

        assertThat(scheduler.loadScheduleEventFromJson()).isTrue()
        assertThat(scheduler.getExpSchedule()).isEqualTo(200)
        assertThat(scheduler.getLootSchedule()).isEqualTo(150)
        assertThat(scheduler.getBossLootSchedule()).isEqualTo(300)
        assertThat(scheduler.getSpawnMonsterSchedule()).isEqualTo(50)
        assertThat(scheduler.getSkillSchedule()).isEqualTo(110)
        assertThat(settings["forge-chance"]).isEqualTo(120)
        assertThat(settings["boss-cooldown"]).isEqualTo(90)
        assertThat(settings["double-bestiary"]).isEqualTo(true)
        assertThat(settings["double-bosstiary"]).isEqualTo(true)
        assertThat(settings["fast-exercise"]).isEqualTo(true)
    }

    @Test
    fun `does not double apply duplicate rates from events on the same day`() {
        val now = epoch(2026, 1, 10, 12, 0)
        val scheduler = EventsScheduler(coreDirectory = tempDir.toString(), now = { now })
        writeEventsJson(
            """
            {
              "events": [
                {
                  "name": "First",
                  "startdate": "1/10/2026",
                  "enddate": "1/10/2026",
                  "ingame": { "exprate": 200, "lootrate": 150 }
                },
                {
                  "name": "Second",
                  "startdate": "1/10/2026",
                  "enddate": "1/10/2026",
                  "ingame": { "exprate": 200, "lootrate": 150 }
                }
              ]
            }
            """.trimIndent(),
        )

        assertThat(scheduler.loadScheduleEventFromJson()).isTrue()
        assertThat(scheduler.getExpSchedule()).isEqualTo(200)
        assertThat(scheduler.getLootSchedule()).isEqualTo(150)
        assertThat(scheduler.getActiveEvents()).containsExactly("First", "Second")
    }

    @Test
    fun `ignores events outside the current window and invalid entries`() {
        val now = epoch(2026, 1, 10, 12, 0)
        val scheduler = EventsScheduler(coreDirectory = tempDir.toString(), now = { now })
        writeEventsJson(
            """
            {
              "events": [
                {
                  "name": "Inactive",
                  "startdate": "2/10/2026",
                  "enddate": "2/11/2026",
                  "ingame": { "exprate": 500 }
                },
                {
                  "name": "Reversed",
                  "startdate": "1/11/2026",
                  "starthour": "10:00",
                  "enddate": "1/11/2026",
                  "endhour": "08:00",
                  "ingame": { "exprate": 500 }
                },
                {
                  "name": "BadDate",
                  "startdate": "not-a-date",
                  "enddate": "1/11/2026",
                  "ingame": { "exprate": 500 }
                },
                {
                  "name": "MissingDates",
                  "ingame": { "exprate": 500 }
                }
              ]
            }
            """.trimIndent(),
        )

        assertThat(scheduler.loadScheduleEventFromJson()).isTrue()
        assertThat(scheduler.getActiveEvents()).isEmpty()
        assertThat(scheduler.getExpSchedule()).isEqualTo(100)
    }

    @Test
    fun `returns false for missing malformed or empty schedules`() {
        val scheduler = EventsScheduler(coreDirectory = tempDir.toString(), now = { 0 })

        assertThat(scheduler.loadScheduleEventFromJson()).isFalse()

        writeEventsJson("{ not json")
        assertThat(scheduler.loadScheduleEventFromJson()).isFalse()

        writeEventsJson("""{ "events": { } }""")
        assertThat(scheduler.loadScheduleEventFromJson()).isFalse()
    }

    private fun writeEventsJson(json: String) {
        val directory = tempDir.resolve("json/eventscheduler")
        Files.createDirectories(directory)
        Files.writeString(directory.resolve("events.json"), json)
    }

    private fun epoch(year: Int, month: Int, day: Int, hour: Int = 0, minute: Int = 0, second: Int = 0): Long {
        val calendar = GregorianCalendar()
        calendar.isLenient = true
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, second)
        return calendar.timeInMillis / 1000
    }
}
