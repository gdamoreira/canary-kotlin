package org.opentibia.canary.systems.raid

import io.github.oshai.kotlinlogging.KotlinLogging
import org.w3c.dom.Document
import org.w3c.dom.Element
import org.w3c.dom.Node
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

private val logger = KotlinLogging.logger {}

/** Mirrors `MAXIMUM_TRIES_PER_MONSTER` (src/lua/creature/raids.hpp:28). */
const val MAXIMUM_TRIES_PER_MONSTER: Int = 10

/** Mirrors `CHECK_RAIDS_INTERVAL` (src/lua/creature/raids.hpp:29). */
const val CHECK_RAIDS_INTERVAL: Int = 60

/** Mirrors `RAID_MINTICKS` (src/lua/creature/raids.hpp:30). */
const val RAID_MINTICKS: Int = 1000

/** Mirrors `MAX_RAND_RANGE` (src/lua/creature/raids.cpp:96). */
const val MAX_RAND_RANGE: Int = 10000000

/** Mirrors `TILESTATE_PROTECTIONZONE = 1 << 7` (src/items/items_definitions.hpp:453). */
const val TILESTATE_PROTECTIONZONE: Int = 1 shl 7

/**
 * Mirrors `class Raids` (src/lua/creature/raids.hpp:35), the legacy XML raid
 * container and scheduler.
 *
 * Startup/reload map to `Game::raids` (src/game/game.cpp:979-980, game_reload.cpp:235).
 * The `DISABLE_LEGACY_RAIDS` config gate is modelled by [disableLegacyRaids].
 */
class Raids(
	private val environment: RaidEnvironment,
	private val disableLegacyRaids: Boolean = false,
) : RaidLifecycle {
	internal val raidList: MutableList<Raid> = mutableListOf()

	private var running: Raid? = null
	private var lastRaidEnd: Long = 0
	private var checkRaidsEvent: Long = 0
	private var loaded: Boolean = false
	private var started: Boolean = false

	/** Mirrors `Raids::loadFromXml` (src/lua/creature/raids.cpp:23). */
	fun loadFromXml(): Boolean {
		if (disableLegacyRaids || isLoaded()) {
			return true
		}

		val folder = "${environment.dataDirectory}/raids/raids.xml"
		val doc = parseXmlFile(folder)
		if (doc == null) {
			logger.error { "loadFromXml - Failed to parse $folder" }
			return false
		}

		for (raidNode in elementChildren(doc.documentElement)) {
			if (!raidNode.hasAttribute("name")) {
				logger.error { "loadFromXml - Name tag missing for raid" }
				continue
			}
			val name = raidNode.getAttribute("name")

			// Canary's default is "raids/<name>.xml" and the loader prefixes
			// DATA_DIRECTORY + "/raids/", so the default resolves under a nested
			// raids directory. Replicated verbatim for parity.
			val file = if (raidNode.hasAttribute("file")) {
				raidNode.getAttribute("file")
			} else {
				logger.warn { "loadFromXml - 'file' tag missing for raid: $name using default: raids/$name.xml" }
				"raids/$name.xml"
			}

			val interval = xmlUInt(raidNode, "interval2") * 60
			if (interval == 0) {
				logger.error { "loadFromXml - 'interval2' tag missing or zero (would divide by 0) for raid: $name" }
				continue
			}

			val margin = if (raidNode.hasAttribute("margin")) {
				xmlUInt(raidNode, "margin").toLong() * 60 * 1000
			} else {
				logger.warn { "loadFromXml - 'margin' tag missing for raid: $name" }
				0L
			}

			val repeat = if (raidNode.hasAttribute("repeat")) {
				booleanString(raidNode.getAttribute("repeat"))
			} else {
				false
			}

			val newRaid = Raid(name, interval, margin, repeat, environment, this)
			val raidFile = "${environment.dataDirectory}/raids/$file"
			if (newRaid.loadFromXml(raidFile)) {
				raidList.add(newRaid)
			} else {
				logger.error { "loadFromXml - Failed to load raid: $name" }
			}
		}

		loaded = true
		return true
	}

	/** Mirrors `Raids::startup` (src/lua/creature/raids.cpp:98). */
	fun startup(): Boolean {
		if (!isLoaded() || isStarted() || disableLegacyRaids) {
			return false
		}

		setLastRaidEnd(environment.clock.otsysTime())

		checkRaidsEvent = environment.dispatcher.scheduleEvent(
			CHECK_RAIDS_INTERVAL * 1000L,
			"Raids::checkRaids",
		) { checkRaids() }

		if (checkRaidsEvent == 0L) {
			logger.warn { "[Raids::startup] Failed to schedule raid checks" }
			return false
		}

		started = true
		return started
	}

	/** Mirrors `Raids::checkRaids` (src/lua/creature/raids.cpp:117). */
	fun checkRaids() {
		checkRaidsEvent = 0
		if (disableLegacyRaids) {
			return
		}

		if (getRunning() == null) {
			val now = environment.clock.otsysTime()

			val iterator = raidList.iterator()
			while (iterator.hasNext()) {
				val raid = iterator.next()
				if (now >= getLastRaidEnd() + raid.getMargin()) {
					// Canary computes this in uint32_t, which wraps for large
					// interval values; UInt multiplication reproduces it.
					val roll = environment.random.uniformRandom(0, MAX_RAND_RANGE).toUInt()
					val required = (MAX_RAND_RANGE.toUInt() * raid.getInterval().toUInt()) /
						CHECK_RAIDS_INTERVAL.toUInt()
					if (required >= roll) {
						setRunning(raid)
						raid.startRaid()

						if (!raid.canBeRepeated()) {
							iterator.remove()
						}
						break
					}
				}
			}
		}

		checkRaidsEvent = environment.dispatcher.scheduleEvent(
			CHECK_RAIDS_INTERVAL * 1000L,
			"Raids::checkRaids",
		) { checkRaids() }

		if (checkRaidsEvent == 0L) {
			logger.warn { "[Raids::checkRaids] Failed to reschedule raid checks" }
		}
	}

	/** Mirrors `Raids::clear` (src/lua/creature/raids.cpp:152). */
	fun clear() {
		environment.dispatcher.stopEvent(checkRaidsEvent)
		checkRaidsEvent = 0

		for (raid in raidList) {
			raid.stopEvents()
		}
		raidList.clear()

		loaded = false
		started = false
		running = null
		lastRaidEnd = 0

		environment.scriptRunner?.reInitState()
	}

	/** Mirrors `Raids::reload` (src/lua/creature/raids.cpp:169). */
	fun reload(): Boolean {
		clear()
		return loadFromXml()
	}

	/** Mirrors `Raids::getRaidByName` (strcasecmp) (src/lua/creature/raids.cpp:174). */
	fun getRaidByName(name: String): Raid? {
		return raidList.firstOrNull { it.getName().equals(name, ignoreCase = true) }
	}

	fun isLoaded(): Boolean = loaded

	fun isStarted(): Boolean = started

	fun getRunning(): Raid? = running

	override fun setRunning(raid: Raid?) {
		running = raid
	}

	fun getLastRaidEnd(): Long = lastRaidEnd

	override fun setLastRaidEnd(time: Long) {
		lastRaidEnd = time
	}
}

/** Mirrors `booleanString` (src/utils/tools.cpp:1125). */
internal fun booleanString(value: String): Boolean {
	if (value.isEmpty()) {
		return false
	}
	val ch = value.first().lowercaseChar()
	return ch != 'f' && ch != 'n' && ch != '0'
}

/** Parses an XML file, returning null when the file is missing or invalid. */
internal fun parseXmlFile(path: String): Document? {
	val file = File(path)
	if (!file.isFile) {
		return null
	}
	return try {
		val factory = DocumentBuilderFactory.newInstance()
		factory.isNamespaceAware = false
		factory.newDocumentBuilder().parse(file)
	} catch (e: Exception) {
		logger.error(e) { "Failed to parse XML file: $path" }
		null
	}
}

/** Element children of [node], skipping text/comment nodes. */
internal fun elementChildren(node: Node): List<Element> {
	val children = node.childNodes
	val result = mutableListOf<Element>()
	for (i in 0 until children.length) {
		val child = children.item(i)
		if (child.nodeType == Node.ELEMENT_NODE) {
			result.add(child as Element)
		}
	}
	return result
}

/** Mirrors `pugi::cast<uint32_t>`: missing/invalid attribute yields 0. */
internal fun xmlUInt(element: Element, attribute: String): Int {
	if (!element.hasAttribute(attribute)) {
		return 0
	}
	return element.getAttribute(attribute).toLongOrNull()?.toInt() ?: 0
}

/** Mirrors `pugi::cast<uint16_t>`: missing attribute yields null, values are masked to 16 bits. */
internal fun xmlU16(element: Element, attribute: String): Int? {
	if (!element.hasAttribute(attribute)) {
		return null
	}
	return (element.getAttribute(attribute).toLongOrNull()?.toInt() ?: 0) and 0xFFFF
}
