package org.opentibia.canary.systems.raid

import org.opentibia.canary.combat.uniformRandom
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.network.webhook.Webhook
import java.util.Random

/**
 * `g_dispatcher()` adapter for the raid scheduler: every raid check and event
 * is scheduled on `DispatcherLane::Maintenance` (src/lua/creature/raids.cpp:105,
 * 144, 231), the serial lane shared with the spawn engine's checks, and
 * cancellation re-resolves the event by id
 * (`Dispatcher::stopEvent`, dispatcher.cpp:1171).
 *
 * `Dispatcher::scheduleEvent` returns `0` when the event cannot be scheduled
 * (the engine's `checkRaidsEvent == 0` failure path, raids.cpp:108-111); this
 * adapter maps the not-running dispatcher to `0`.
 *
 * PARITY GAP: a lane-admission rejection is not observable through the port's
 * `ScheduledTask`, so a rejected reservation is indistinguishable from an
 * accepted one here (the spawn path's `SpawnScheduler` adapter shares the
 * limitation).
 */
class WorldRaidDispatcher(private val dispatcher: GameDispatcher) : RaidDispatcher {
	override fun scheduleEvent(delayMs: Long, taskName: String, callback: () -> Unit): Long {
		if (!dispatcher.isRunning) {
			return 0
		}
		return dispatcher.schedule(delayMs, DispatcherLane.Maintenance) { callback() }.eventId
	}

	override fun stopEvent(eventId: Long) {
		dispatcher.stopEvent(eventId)
	}
}

/**
 * `AnnounceEvent::executeEvent`'s sinks (src/lua/creature/raids.cpp:345-347)
 * over the composed world: [broadcastMessage] is `g_game().broadcastMessage`
 * (game.cpp:9526) with the `MessageClasses` value the XML `type` resolved to,
 * and [sendWebhook] is `g_webhook().sendMessage`; without a composed
 * `g_webhook()` the webhook half degrades to a no-op exactly like an
 * unconfigured `DISCORD_WEBHOOK_URL`.
 */
class WorldRaidAnnouncer(
	private val game: Game,
	private val webhook: Webhook? = null,
) : RaidAnnouncer {
	override fun broadcastMessage(message: String, messageType: MessageType) {
		game.broadcastMessage(message, MessageClass.fromValue(messageType.value))
	}

	override fun sendWebhook(message: String) {
		webhook?.sendMessage(message)
	}
}

/**
 * The production [RaidEnvironment]. Canary reaches these services through the
 * `g_configManager()`/`g_dispatcher()`/`g_game()`/`g_webhook()` globals; per
 * AGENTS.md 1.1 the composition root injects them here:
 *
 *  - [dispatcher] schedules the checks/events on `DispatcherLane::Maintenance`
 *    (raids.cpp:105, 144, 231);
 *  - [announcer] fans the announce events out through `Game::broadcastMessage`
 *    and `g_webhook().sendMessage` (raids.cpp:346-347);
 *  - [spawner] is the wave-70 [GameRaidSpawner] over the booted world;
 *  - [random] is the ported `uniform_random` (src/utils/tools.cpp:455-464);
 *  - [clock] is `OTSYS_TIME()` (src/utils/tools.hpp:181);
 *  - [scriptRunner] loads `{DATA_DIRECTORY}/raids/scripts/<script>` and calls
 *    its `onRaid` function (raids.cpp:583-627), keeping the script Lua.
 */
class WorldRaidEnvironment(
	override val dataDirectory: String,
	gameDispatcher: GameDispatcher,
	game: Game,
	luaEnvironment: LuaEnvironment,
	override val spawner: RaidSpawner,
	webhook: Webhook? = null,
	private val randomSource: Random = Random(),
	private val otsysTime: () -> Long = System::currentTimeMillis,
) : RaidEnvironment {
	override val dispatcher: RaidDispatcher = WorldRaidDispatcher(gameDispatcher)

	override val announcer: RaidAnnouncer = WorldRaidAnnouncer(game, webhook)

	override val random: RaidRandom = RaidRandom { minNumber, maxNumber ->
		uniformRandom(minNumber, maxNumber, randomSource)
	}

	override val clock: RaidClock = RaidClock { otsysTime() }

	override val scriptRunner: RaidScriptRunner = WorldRaidScriptRunner(luaEnvironment)
}
