package org.opentibia.canary.systems.raid

import org.opentibia.canary.map.Position
import org.w3c.dom.Element

/**
 * Kotlin port of Canary's legacy XML raid events
 * (`src/lua/creature/raids.hpp`, `src/lua/creature/raids.cpp`).
 *
 * The C++ events reach the rest of the engine through globals
 * (`g_game()`, `g_dispatcher()`, `Monster::createMonster`, `g_webhook()`).
 * Per AGENTS.md 1.1 those are injected here as small interfaces
 * ([RaidAnnouncer], [RaidSpawner], [RaidRandom], [RaidScriptRunner]).
 *
 * Lua stays Lua: [ScriptEvent] only carries the script name and delegates
 * loading/execution to [RaidScriptRunner]; it never reimplements the script.
 */

/** Mirrors the `MessageClasses` values used by `AnnounceEvent` (src/utils/utils_definitions.hpp:373-381). */
enum class MessageType(val value: Int) {
	MESSAGE_GAMEMASTER_CONSOLE(13),
	MESSAGE_EVENT_ADVANCE(19),
	MESSAGE_GAME_HIGHLIGHT(20),
	MESSAGE_FAILURE(21),
	MESSAGE_LOOK(22),
}

/** Mirrors Canary `struct MonsterSpawn` (src/lua/creature/raids.hpp:18). */
data class MonsterSpawn(val name: String, val minAmount: Int, val maxAmount: Int)

/**
 * The subset of Canary `Monster` used by the raid engine
 * (`Monster::createMonster`, `Monster::setForgeMonster`, `Monster::onSpawn`).
 * The production bridge ([GameRaidMonster]) overrides the hooks over the real
 * placed [org.opentibia.canary.creatures.Monster]; the open defaults keep the
 * engine unit-testable with inert views.
 */
open class RaidMonster(val name: String) {
	open fun setForgeMonster(value: Boolean) { }

	open fun onSpawn(position: Position) { }
}

/**
 * Read-only tile view used by [AreaSpawnEvent]. Mirrors Canary `Tile` accessors
 * (src/items/tile.hpp).
 */
interface RaidTile {
	val position: Position

	fun isMovableBlocking(): Boolean

	fun hasFlag(flag: Int): Boolean

	/** `tile->getTopCreature() != nullptr` (raids.cpp:568): any creature blocks the tile. */
	val hasTopCreature: Boolean
}

/**
 * Mirrors `Game::broadcastMessage` and `Webhook::sendMessage`, both called by
 * `AnnounceEvent::executeEvent` (src/lua/creature/raids.cpp:346-347).
 */
interface RaidAnnouncer {
	fun broadcastMessage(message: String, messageType: MessageType)

	fun sendWebhook(message: String)
}

/**
 * Mirrors the spawning primitives used by the spawn events:
 * `Monster::createMonster` (src/creatures/monsters/monster.cpp),
 * `Game::placeCreature` (src/game/game.cpp:1524) and
 * `GameMap::getTile` (src/map/map.cpp).
 */
interface RaidSpawner {
	fun createMonster(name: String): RaidMonster?

	fun placeCreature(monster: RaidMonster, position: Position, extendedPos: Boolean, force: Boolean): Boolean

	fun getTile(x: Int, y: Int, z: Int): RaidTile?
}

/** Mirrors `uniform_random` (src/utils/tools.hpp:90). */
fun interface RaidRandom {
	fun uniformRandom(minNumber: Int, maxNumber: Int): Int
}

/** Mirrors `OTSYS_TIME` (src/utils/utils_definitions.hpp). */
fun interface RaidClock {
	fun otsysTime(): Long
}

/**
 * Boundary for the Lua-backed `ScriptEvent`. Canary loads
 * `data/raids/scripts/<script>` and calls its `onRaid` function
 * (src/lua/creature/raids.cpp:583-627); that script must stay Lua.
 */
interface RaidScriptRunner {
	fun loadScript(file: String, scriptName: String): Boolean

	fun executeOnRaid(scriptName: String): Boolean

	/**
	 * `ScriptInterface::reInitState` reached by `Raids::clear`
	 * (src/lua/creature/raids.cpp:166): drops every script reference the
	 * runner loaded, so a cleared container cannot execute a stale script.
	 */
	fun reInitState() {}
}

/**
 * Mirrors `class RaidEvent` (src/lua/creature/raids.hpp:142). `delay` is private
 * in Canary with a public `getDelay()` accessor; callers configure it through
 * [configureRaidEvent].
 */
abstract class RaidEvent {
	private var delay: Int = 0

	fun getDelay(): Int = delay

	/** Mirrors `RaidEvent::configureRaidEvent` (src/lua/creature/raids.cpp:289). */
	open fun configureRaidEvent(eventNode: Element): Boolean {
		if (!eventNode.hasAttribute("delay")) {
			return false
		}
		delay = maxOf(RAID_MINTICKS, xmlUInt(eventNode, "delay"))
		return true
	}

	abstract fun executeEvent(): Boolean
}

/** Mirrors `class AnnounceEvent` (src/lua/creature/raids.hpp:157). */
class AnnounceEvent(private val announcer: RaidAnnouncer) : RaidEvent() {
	var message: String = ""
		private set

	var messageType: MessageType = MessageType.MESSAGE_EVENT_ADVANCE
		private set

	/** Mirrors `AnnounceEvent::configureRaidEvent` (src/lua/creature/raids.cpp:300). */
	override fun configureRaidEvent(eventNode: Element): Boolean {
		if (!super.configureRaidEvent(eventNode)) {
			return false
		}

		if (!eventNode.hasAttribute("message")) {
			return false
		}
		message = eventNode.getAttribute("message")

		if (eventNode.hasAttribute("type")) {
			when (eventNode.getAttribute("type").lowercase()) {
				"warning" -> messageType = MessageType.MESSAGE_GAME_HIGHLIGHT
				"event" -> messageType = MessageType.MESSAGE_EVENT_ADVANCE
				"default" -> messageType = MessageType.MESSAGE_EVENT_ADVANCE
				"description" -> messageType = MessageType.MESSAGE_LOOK
				"smallstatus" -> messageType = MessageType.MESSAGE_FAILURE
				"redconsole" -> messageType = MessageType.MESSAGE_GAMEMASTER_CONSOLE
				// Unknown type keeps the current default, matching Canary's warn-and-fallback.
			}
		} else {
			messageType = MessageType.MESSAGE_EVENT_ADVANCE
		}
		return true
	}

	/** Mirrors `AnnounceEvent::executeEvent` (src/lua/creature/raids.cpp:345). */
	override fun executeEvent(): Boolean {
		announcer.broadcastMessage(message, messageType)
		announcer.sendWebhook(":space_invader: $message")
		return true
	}
}

/** Mirrors `class SingleSpawnEvent` (src/lua/creature/raids.hpp:170). */
class SingleSpawnEvent(private val spawner: RaidSpawner) : RaidEvent() {
	var monsterName: String = ""
		private set

	var position: Position = Position(0, 0, 0)
		private set

	/** Mirrors `SingleSpawnEvent::configureRaidEvent` (src/lua/creature/raids.cpp:351). */
	override fun configureRaidEvent(eventNode: Element): Boolean {
		if (!super.configureRaidEvent(eventNode)) {
			return false
		}

		if (!eventNode.hasAttribute("name")) {
			return false
		}
		monsterName = eventNode.getAttribute("name")

		val x = xmlU16(eventNode, "x") ?: return false
		val y = xmlU16(eventNode, "y") ?: return false
		val z = xmlU16(eventNode, "z") ?: return false
		position = Position(x, y, z)
		return true
	}

	/** Mirrors `SingleSpawnEvent::executeEvent` (src/lua/creature/raids.cpp:395). */
	override fun executeEvent(): Boolean {
		val monster = spawner.createMonster(monsterName) ?: return false
		if (!spawner.placeCreature(monster, position, false, true)) {
			return false
		}
		monster.setForgeMonster(false)
		monster.onSpawn(position)
		return true
	}
}

/** Mirrors `class AreaSpawnEvent` (src/lua/creature/raids.hpp:181). */
class AreaSpawnEvent(
	private val spawner: RaidSpawner,
	private val random: RaidRandom,
) : RaidEvent() {
	val spawnMonsterList: MutableList<MonsterSpawn> = mutableListOf()

	var fromPos: Position = Position(0, 0, 0)
		private set

	var toPos: Position = Position(0, 0, 0)
		private set

	/** Mirrors `AreaSpawnEvent::configureRaidEvent` (src/lua/creature/raids.cpp:412). */
	override fun configureRaidEvent(eventNode: Element): Boolean {
		if (!super.configureRaidEvent(eventNode)) {
			return false
		}

		if (eventNode.hasAttribute("radius")) {
			val radius = xmlUInt(eventNode, "radius")
			val centerX = xmlU16(eventNode, "centerx") ?: return false
			val centerY = xmlU16(eventNode, "centery") ?: return false
			val centerZ = xmlU16(eventNode, "centerz") ?: return false

			fromPos = Position(maxOf(0, centerX - radius), maxOf(0, centerY - radius), centerZ)
			toPos = Position(minOf(0xFFFF, centerX + radius), minOf(0xFFFF, centerY + radius), centerZ)
		} else {
			val fromX = xmlU16(eventNode, "fromx") ?: return false
			val fromY = xmlU16(eventNode, "fromy") ?: return false
			val fromZ = xmlU16(eventNode, "fromz") ?: return false
			val toX = xmlU16(eventNode, "tox") ?: return false
			val toY = xmlU16(eventNode, "toy") ?: return false
			val toZ = xmlU16(eventNode, "toz") ?: return false

			fromPos = Position(fromX, fromY, fromZ)
			toPos = Position(toX, toY, toZ)
		}

		for (monsterNode in elementChildren(eventNode)) {
			if (!monsterNode.hasAttribute("name")) {
				return false
			}
			val name = monsterNode.getAttribute("name")

			var minAmount = xmlUInt(monsterNode, "minamount")
			var maxAmount = xmlUInt(monsterNode, "maxamount")

			if (maxAmount == 0 && minAmount == 0) {
				if (!monsterNode.hasAttribute("amount")) {
					return false
				}
				minAmount = xmlUInt(monsterNode, "amount")
				maxAmount = minAmount
			}

			spawnMonsterList.add(MonsterSpawn(name, minAmount, maxAmount))
		}
		return true
	}

	/**
	 * Mirrors `AreaSpawnEvent::executeEvent` (src/lua/creature/raids.cpp:556),
	 * including the up-to-`MAXIMUM_TRIES_PER_MONSTER` tile search.
	 */
	override fun executeEvent(): Boolean {
		for (spawn in spawnMonsterList) {
			val amount = random.uniformRandom(spawn.minAmount, spawn.maxAmount)
			for (i in 0 until amount) {
				val monster = spawner.createMonster(spawn.name) ?: return false

				for (tries in 0 until MAXIMUM_TRIES_PER_MONSTER) {
					val x = random.uniformRandom(fromPos.x, toPos.x)
					val y = random.uniformRandom(fromPos.y, toPos.y)
					val z = random.uniformRandom(fromPos.z, toPos.z)
					val tile = spawner.getTile(x, y, z) ?: continue

					if (!tile.isMovableBlocking() &&
						!tile.hasFlag(TILESTATE_PROTECTIONZONE) &&
						!tile.hasTopCreature &&
						spawner.placeCreature(monster, tile.position, false, true)
					) {
						monster.setForgeMonster(false)
						break
					}
				}
			}
		}
		return true
	}
}

/**
 * Mirrors `class ScriptEvent` (src/lua/creature/raids.hpp:192). The script itself
 * remains Lua; [RaidScriptRunner] is the bridge port.
 */
class ScriptEvent(
	private val scriptRunner: RaidScriptRunner?,
	private val dataDirectory: String,
) : RaidEvent() {
	var scriptName: String = ""
		private set

	/** Mirrors `ScriptEvent::configureRaidEvent` (src/lua/creature/raids.cpp:583). */
	override fun configureRaidEvent(eventNode: Element): Boolean {
		if (!super.configureRaidEvent(eventNode)) {
			return false
		}

		if (!eventNode.hasAttribute("script")) {
			return false
		}
		val name = eventNode.getAttribute("script")
		val runner = scriptRunner ?: return false
		if (!runner.loadScript("$dataDirectory/raids/scripts/$name", name)) {
			return false
		}
		scriptName = name
		return true
	}

	/** Mirrors `ScriptEvent::executeEvent` (src/lua/creature/raids.cpp:612). */
	override fun executeEvent(): Boolean = scriptRunner?.executeOnRaid(scriptName) ?: false
}
