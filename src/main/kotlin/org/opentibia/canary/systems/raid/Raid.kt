package org.opentibia.canary.systems.raid

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

/** Mirrors `enum RaidState_t` (src/lua/lua_definitions.hpp:91). */
enum class RaidState {
	RAIDSTATE_IDLE,
	RAIDSTATE_EXECUTING,
}

/**
 * Mirrors `g_dispatcher().scheduleEvent` / `stopEvent` (src/game/scheduling/dispatcher.hpp).
 * `scheduleEvent` returns an event id, or 0 when the event could not be scheduled.
 */
interface RaidDispatcher {
	fun scheduleEvent(delayMs: Long, taskName: String, callback: () -> Unit): Long

	fun stopEvent(eventId: Long)
}

/**
 * Back-reference to the owning [Raids] container, mirroring the
 * `g_game().raids` access used by `Raid::resetRaid`
 * (src/lua/creature/raids.cpp:267-272).
 */
interface RaidLifecycle {
	fun setRunning(raid: Raid?)

	fun setLastRaidEnd(time: Long)
}

/**
 * The engine services injected into [Raids]/[Raid]. In Canary these are reached
 * through global singletons (`g_configManager()`, `g_dispatcher()`, `g_game()`,
 * `g_webhook()`); per AGENTS.md 1.1 they are constructor-injected here.
 */
interface RaidEnvironment {
	/** `DATA_DIRECTORY` from `g_configManager()`. */
	val dataDirectory: String

	val dispatcher: RaidDispatcher

	val announcer: RaidAnnouncer

	val spawner: RaidSpawner

	val random: RaidRandom

	val clock: RaidClock

	/** Null disables `ScriptEvent` loading, matching a missing Lua script interface. */
	val scriptRunner: RaidScriptRunner?
}

/**
 * Mirrors `class Raid` (src/lua/creature/raids.hpp:90).
 *
 * Fields and methods map one-to-one to the C++ class: [loadFromXml], [startRaid],
 * [executeRaidEvent], [resetRaid], [getNextRaidEvent], [setState], [getName],
 * [isLoaded], [getMargin], [getInterval], [canBeRepeated], [stopEvents].
 */
class Raid(
	private val name: String,
	private val interval: Int,
	private val margin: Long,
	private val repeat: Boolean,
	private val environment: RaidEnvironment,
	private val raids: RaidLifecycle,
) {
	internal val raidEvents: MutableList<RaidEvent> = mutableListOf()

	internal var state: RaidState = RaidState.RAIDSTATE_IDLE

	private var nextEvent: Int = 0
	private var nextEventEvent: Long = 0
	private var loaded: Boolean = false

	/** Mirrors `Raid::loadFromXml` (src/lua/creature/raids.cpp:183). */
	fun loadFromXml(filename: String): Boolean {
		if (isLoaded()) {
			return true
		}

		val doc = parseXmlFile(filename)
		if (doc == null) {
			logger.error { "Raid::loadFromXml - Failed to parse $filename" }
			return false
		}

		val raidNode = doc.documentElement
		for (eventNode in elementChildren(raidNode)) {
			val event: RaidEvent = when (eventNode.tagName.lowercase()) {
				"announce" -> AnnounceEvent(environment.announcer)
				"singlespawn" -> SingleSpawnEvent(environment.spawner)
				"areaspawn" -> AreaSpawnEvent(environment.spawner, environment.random)
				"script" -> ScriptEvent(environment.scriptRunner, environment.dataDirectory)
				else -> continue
			}

			if (event.configureRaidEvent(eventNode)) {
				raidEvents.add(event)
			} else {
				logger.error { "Raid::loadFromXml - Invalid ${eventNode.tagName} event in $filename" }
			}
		}

		// Sort by delay time; Kotlin's sort is stable, so equal delays keep file order.
		raidEvents.sortBy { it.getDelay() }

		loaded = true
		return true
	}

	/** Mirrors `Raid::startRaid` (src/lua/creature/raids.cpp:227). */
	fun startRaid() {
		val raidEvent = getNextRaidEvent()
		if (raidEvent != null) {
			state = RaidState.RAIDSTATE_EXECUTING
			nextEventEvent = environment.dispatcher.scheduleEvent(
				raidEvent.getDelay().toLong(),
				"Raid::executeRaidEvent",
			) { executeRaidEvent(raidEvent) }

			if (nextEventEvent == 0L) {
				logger.warn { "[Raid::startRaid] Failed to schedule the first event for raid $name" }
				resetRaid()
			}
		} else {
			logger.warn { "[raids] Raid $name has no events" }
			resetRaid()
		}
	}

	/** Mirrors `Raid::executeRaidEvent` (src/lua/creature/raids.cpp:244). */
	fun executeRaidEvent(raidEvent: RaidEvent) {
		nextEventEvent = 0
		if (raidEvent.executeEvent()) {
			nextEvent++
			val newRaidEvent = getNextRaidEvent()

			if (newRaidEvent != null) {
				val ticks = maxOf(RAID_MINTICKS, newRaidEvent.getDelay() - raidEvent.getDelay())
				nextEventEvent = environment.dispatcher.scheduleEvent(
					ticks.toLong(),
					"Raid::executeRaidEvent",
				) { executeRaidEvent(newRaidEvent) }

				if (nextEventEvent == 0L) {
					logger.warn { "[Raid::executeRaidEvent] Failed to schedule the next event for raid $name" }
					resetRaid()
				}
			} else {
				resetRaid()
			}
		} else {
			resetRaid()
		}
	}

	/** Mirrors `Raid::resetRaid` (src/lua/creature/raids.cpp:267). */
	fun resetRaid() {
		nextEvent = 0
		state = RaidState.RAIDSTATE_IDLE
		raids.setRunning(null)
		raids.setLastRaidEnd(environment.clock.otsysTime())
	}

	/** Mirrors `Raid::stopEvents` (src/lua/creature/raids.cpp:274). */
	fun stopEvents() {
		if (nextEventEvent != 0L) {
			environment.dispatcher.stopEvent(nextEventEvent)
			nextEventEvent = 0
		}
	}

	/** Mirrors `Raid::getNextRaidEvent` (src/lua/creature/raids.cpp:281). */
	fun getNextRaidEvent(): RaidEvent? {
		return if (nextEvent < raidEvents.size) raidEvents[nextEvent] else null
	}

	fun setState(newState: RaidState) {
		state = newState
	}

	fun getName(): String = name

	fun isLoaded(): Boolean = loaded

	fun getMargin(): Long = margin

	fun getInterval(): Int = interval

	fun canBeRepeated(): Boolean = repeat
}
