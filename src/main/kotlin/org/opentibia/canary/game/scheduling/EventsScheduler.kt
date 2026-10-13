package org.opentibia.canary.game.scheduling

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import java.nio.file.Files
import java.nio.file.Path
import java.util.GregorianCalendar

private val logger = KotlinLogging.logger {}

private val OBJECT_MAPPER = ObjectMapper()

/** `struct EventScheduler` (src/game/scheduling/events_scheduler.hpp:19-23). */
data class EventScheduler(
    val name: String,
    val startTime: Long,
    val endTime: Long,
)

/** `struct EventRates` (src/game/scheduling/events_scheduler.hpp:25-36). */
data class EventRates(
    val exprate: Int = 100,
    val lootrate: Int = 100,
    val bosslootrate: Int = 100,
    val spawnrate: Int = 100,
    val skillrate: Int = 100,
    val forgeChance: Int = 100,
    val bosscooldown: Int = 100,
    val doubleBestiary: Boolean = false,
    val doubleBossTiary: Boolean = false,
    val fastExercise: Boolean = false,
)

/**
 * `EventsScheduler` (src/game/scheduling/events_scheduler.hpp:38-103,
 * events_scheduler.cpp:163-395): calendar events loaded from
 * `json/eventscheduler/events.json` and the rate multipliers they apply.
 *
 * PARITY GAPS: the C++ `g_scripts().loadEventSchedulerScripts` Lua hook and the
 * `g_kv()` scoped persistence are reduced to a [settings] map (no Lua per
 * AGENTS.md 1.4); parse errors return `false` instead of `consoleHandlerExit()`.
 */
class EventsScheduler(
    private val coreDirectory: String = DEFAULT_CORE_DIRECTORY,
    private val now: () -> Long = { System.currentTimeMillis() / 1000 },
    private val settings: MutableMap<String, Any> = linkedMapOf(),
) {
    private var expSchedule = 100
    private var lootSchedule = 100
    private var bossLootSchedule = 100
    private var skillSchedule = 100
    private var spawnMonsterSchedule = 100
    private val eventScheduler = mutableListOf<EventScheduler>()

    fun loadScheduleEventFromJson(): Boolean {
        reset()
        for (key in EVENT_SCHEDULER_SETTINGS_KEYS) {
            settings.remove(key)
        }

        val folder = Path.of(coreDirectory, JSON_EVENT_SCHEDULER_DIRECTORY, "events.json")
        if (!Files.isRegularFile(folder)) {
            logger.error { "loadScheduleEventFromJson - Unable to open file '$folder'" }
            return false
        }

        val eventsJson = try {
            OBJECT_MAPPER.readTree(Files.readString(folder))
        } catch (e: Exception) {
            logger.error(e) { "loadScheduleEventFromJson - JSON parsing error in file '$folder'" }
            return false
        }

        val events = eventsJson.get("events")
        if (events == null || !events.isArray) {
            logger.error { "loadScheduleEventFromJson - Missing or invalid 'events' array in '$folder'" }
            return false
        }

        val currentTime = now()
        val eventsOnSameDay = linkedMapOf<String, EventRates>()

        for (event in events) {
            val eventName = event.path("name").asText("")

            if (!event.has("startdate") || !event.has("enddate")) {
                logger.warn { "loadScheduleEventFromJson - Missing 'startdate' or 'enddate' for event '$eventName'" }
                continue
            }

            val defaultHour = if (event.has("hour") && !event.get("hour").isNull) event.get("hour").asText("") else ""
            val startHour = if (event.has("starthour") && !event.get("starthour").isNull) {
                event.get("starthour").asText("")
            } else if (defaultHour.isNotEmpty()) {
                defaultHour
            } else {
                "00:00"
            }
            val endHour = if (event.has("endhour") && !event.get("endhour").isNull) {
                event.get("endhour").asText("")
            } else if (defaultHour.isNotEmpty()) {
                defaultHour
            } else {
                "23:59:59"
            }

            val startTime = parseDateTime(event.get("startdate").asText(""), startHour)
            val endTime = parseDateTime(event.get("enddate").asText(""), endHour)
            if (startTime == null || endTime == null) {
                logger.warn { "loadScheduleEventFromJson - Invalid date or hour format for event '$eventName'" }
                continue
            }
            if (endTime < startTime) {
                logger.warn { "loadScheduleEventFromJson - Event '$eventName' end time is before start time" }
                continue
            }
            if (currentTime < startTime || currentTime > endTime) {
                continue
            }

            val currentEventRates = readEventRates(event.path("ingame"))
            var applyExp = currentEventRates.exprate != 100
            var applyLoot = currentEventRates.lootrate != 100
            var applyBossLoot = currentEventRates.bosslootrate != 100
            var applySpawn = currentEventRates.spawnrate != 100
            var applySkill = currentEventRates.skillrate != 100
            var applyForge = currentEventRates.forgeChance != 100
            var applyDoubleBestiary = currentEventRates.doubleBestiary
            var applyDoubleBosstiary = currentEventRates.doubleBossTiary
            var applyFastExercise = currentEventRates.fastExercise
            var applyBossCooldown = currentEventRates.bosscooldown != 100
            val duplicatedRates = linkedMapOf<String, MutableList<String>>()

            for ((existingEventName, rates) in eventsOnSameDay) {
                if (applyExp && rates.exprate != 100 && rates.exprate == currentEventRates.exprate) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("exprate")
                    applyExp = false
                }
                if (applyLoot && rates.lootrate != 100 && rates.lootrate == currentEventRates.lootrate) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("lootrate")
                    applyLoot = false
                }
                if (applyBossLoot && rates.bosslootrate != 100 && rates.bosslootrate == currentEventRates.bosslootrate) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("bosslootrate")
                    applyBossLoot = false
                }
                if (applySpawn && rates.spawnrate != 100 && rates.spawnrate == currentEventRates.spawnrate) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("spawnrate")
                    applySpawn = false
                }
                if (applySkill && rates.skillrate != 100 && rates.skillrate == currentEventRates.skillrate) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("skillrate")
                    applySkill = false
                }
                if (applyForge && rates.forgeChance != 100 && rates.forgeChance == currentEventRates.forgeChance) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("forge-chance")
                    applyForge = false
                }
                if (applyDoubleBestiary && rates.doubleBestiary == currentEventRates.doubleBestiary) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("double-bestiary")
                    applyDoubleBestiary = false
                }
                if (applyDoubleBosstiary && rates.doubleBossTiary == currentEventRates.doubleBossTiary) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("double-bosstiary")
                    applyDoubleBosstiary = false
                }
                if (applyFastExercise && rates.fastExercise == currentEventRates.fastExercise) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("fast-exercise")
                    applyFastExercise = false
                }
                if (applyBossCooldown && rates.bosscooldown != 100 && rates.bosscooldown == currentEventRates.bosscooldown) {
                    duplicatedRates.getOrPut(existingEventName) { mutableListOf() }.add("bosscooldown")
                    applyBossCooldown = false
                }
            }

            if (applyExp) {
                setExpSchedule(currentEventRates.exprate)
            }
            if (applyLoot) {
                setLootSchedule(currentEventRates.lootrate)
            }
            if (applyBossLoot) {
                setBossLootSchedule(currentEventRates.bosslootrate)
            }
            if (applySpawn) {
                setSpawnMonsterSchedule(currentEventRates.spawnrate)
            }
            if (applySkill) {
                setSkillSchedule(currentEventRates.skillrate)
            }
            if (applyForge) {
                settings["forge-chance"] = currentEventRates.forgeChance
            }
            if (applyDoubleBestiary) {
                settings["double-bestiary"] = true
            }
            if (applyDoubleBosstiary) {
                settings["double-bosstiary"] = true
            }
            if (applyFastExercise) {
                settings["fast-exercise"] = true
            }
            if (applyBossCooldown) {
                settings["boss-cooldown"] = currentEventRates.bosscooldown
            }

            for ((duplicateEventName, rates) in duplicatedRates) {
                if (rates.isNotEmpty()) {
                    logger.warn {
                        "loadScheduleEventFromJson - Events '$eventName' and '$duplicateEventName' " +
                            "have the same rates [${rates.joinToString(", ")}] on the same day."
                    }
                }
            }

            eventsOnSameDay[eventName] = currentEventRates
            eventScheduler.add(EventScheduler(eventName, startTime, endTime))
        }

        for (event in eventScheduler) {
            if (currentTime >= event.startTime && currentTime <= event.endTime) {
                logger.info { "Active EventScheduler: ${event.name}" }
            }
        }
        return true
    }

    fun getExpSchedule(): Int = expSchedule

    fun setExpSchedule(exprate: Int) {
        expSchedule = expSchedule * exprate / 100
    }

    fun getLootSchedule(): Int = lootSchedule

    fun setLootSchedule(lootrate: Int) {
        lootSchedule = lootSchedule * lootrate / 100
    }

    fun getBossLootSchedule(): Int = bossLootSchedule

    fun setBossLootSchedule(bosslootrate: Int) {
        bossLootSchedule = bossLootSchedule * bosslootrate / 100
    }

    fun getSpawnMonsterSchedule(): Int = spawnMonsterSchedule

    fun setSpawnMonsterSchedule(spawnrate: Int) {
        spawnMonsterSchedule = spawnMonsterSchedule * spawnrate / 100
    }

    fun getSkillSchedule(): Int = skillSchedule

    fun setSkillSchedule(skillrate: Int) {
        skillSchedule = skillSchedule * skillrate / 100
    }

    fun reset() {
        expSchedule = 100
        lootSchedule = 100
        bossLootSchedule = 100
        skillSchedule = 100
        spawnMonsterSchedule = 100
        eventScheduler.clear()
    }

    fun getActiveEvents(): List<String> {
        val currentTime = now()
        return eventScheduler
            .filter { currentTime >= it.startTime && currentTime <= it.endTime }
            .map { it.name }
    }

    private fun readEventRates(ingame: JsonNode): EventRates = EventRates(
        exprate = ingame.path("exprate").asInt(100),
        lootrate = ingame.path("lootrate").asInt(100),
        bosslootrate = ingame.path("bosslootrate").asInt(100),
        spawnrate = ingame.path("spawnrate").asInt(100),
        skillrate = ingame.path("skillrate").asInt(100),
        forgeChance = when {
            ingame.has("forgechance") -> ingame.get("forgechance").asInt(100)
            ingame.has("forge-chance") -> ingame.get("forge-chance").asInt(100)
            else -> 100
        },
        bosscooldown = ingame.path("bosscooldown").asInt(100),
        doubleBestiary = ingame.path("doublebestiary").asBoolean(false),
        doubleBossTiary = ingame.path("doublebosstiary").asBoolean(false),
        fastExercise = when {
            ingame.has("fastexercise") -> ingame.get("fastexercise").asBoolean(false)
            ingame.has("doubleexercise") -> ingame.get("doubleexercise").asBoolean(false)
            else -> false
        },
    )

    private fun parseDateTime(dateStr: String, timeStr: String): Long? {
        val dateParts = dateStr.split('/')
        if (dateParts.size != 3) {
            return null
        }
        val month = dateParts[0].trim().toIntOrNull() ?: return null
        val day = dateParts[1].trim().toIntOrNull() ?: return null
        val year = dateParts[2].trim().toIntOrNull() ?: return null

        var hour = 0
        var minute = 0
        var second = 0
        if (timeStr.isNotEmpty()) {
            val timeParts = timeStr.split(':')
            if (timeParts.size < 2) {
                return null
            }
            hour = timeParts[0].trim().toIntOrNull() ?: return null
            minute = timeParts[1].trim().toIntOrNull() ?: return null
            second = if (timeParts.size >= 3) timeParts[2].trim().toIntOrNull() ?: return null else 0
        }
        if (hour !in 0..23 || minute !in 0..59 || second !in 0..59) {
            return null
        }

        val calendar = GregorianCalendar()
        calendar.isLenient = true
        calendar.clear()
        calendar.set(year, month - 1, day, hour, minute, second)
        return calendar.timeInMillis / 1000
    }

    companion object {
        /** Canary `CORE_DIRECTORY` default (`config.lua.dist`). */
        const val DEFAULT_CORE_DIRECTORY: String = "data"

        /** `jsonEventSchedulerScriptsDir` (src/game/scheduling/events_scheduler.cpp:58). */
        private const val JSON_EVENT_SCHEDULER_DIRECTORY = "json/eventscheduler"

        private val EVENT_SCHEDULER_SETTINGS_KEYS = listOf(
            "forge-chance",
            "double-bestiary",
            "double-bosstiary",
            "fast-exercise",
            "boss-cooldown",
        )
    }
}
