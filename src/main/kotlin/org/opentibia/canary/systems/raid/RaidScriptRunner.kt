package org.opentibia.canary.systems.raid

import io.github.oshai.kotlinlogging.KotlinLogging
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.runtime.LuaEnvironment
import java.nio.file.Files
import java.nio.file.Path

private val logger = KotlinLogging.logger {}

/**
 * Lua-backed [RaidScriptRunner] over the booted [LuaEnvironment]. Lua stays
 * Lua: this loader only runs the datapack file and captures the `onRaid`
 * function, mirroring
 *
 *  - `Event::loadScript` (src/lua/global/baseevents.cpp:118-141): load the file
 *    through the script interface and resolve `getScriptEventName()`, which
 *    `ScriptEvent::getScriptEventName` (src/lua/creature/raids.cpp:608-610)
 *    reports as `onRaid`;
 *  - `LuaScriptInterface::getEvent` (luascript.cpp:598-624): the resolved
 *    function is kept per script and the global slot is reset to nil, so a
 *    later script load cannot retarget an already loaded event;
 *  - `ScriptEvent::executeEvent` (raids.cpp:612-627): call the event with no
 *    arguments;
 *  - `ScriptInterface::reInitState` reached by `Raids::clear`
 *    (raids.cpp:166): drop every loaded function.
 *
 * A missing file, a file that defines no `onRaid`, or a failing chunk leaves
 * the event unconfigured and logs, exactly like the C++ `false` path.
 */
class WorldRaidScriptRunner(private val environment: LuaEnvironment) : RaidScriptRunner {
	private val scripts = mutableMapOf<String, LuaValue>()

	override fun loadScript(file: String, scriptName: String): Boolean {
		if (environment.getLuaState() == null) {
			return false
		}

		val path = Path.of(file)
		if (!Files.isRegularFile(path)) {
			logger.warn { "[Event::loadScript] - Can not load script: $file" }
			return false
		}

		return try {
			environment.loadFile(path)
			val onRaid = environment.getGlobal(SCRIPT_EVENT_NAME)
			if (!onRaid.isfunction()) {
				logger.warn { "[Event::loadScript] - Event $SCRIPT_EVENT_NAME not found $file" }
				false
			} else {
				scripts[scriptName] = onRaid
				// `LuaScriptInterface::getEvent` moves the function into the
				// event table and resets the global (luascript.cpp:613-620).
				environment.globals.set(SCRIPT_EVENT_NAME, LuaValue.NIL)
				true
			}
		} catch (e: Exception) {
			logger.error(e) { "[Event::loadScript] - Can not load script: $file" }
			false
		}
	}

	override fun executeOnRaid(scriptName: String): Boolean {
		val onRaid = scripts[scriptName] ?: return false
		return try {
			onRaid.call()
			true
		} catch (e: Exception) {
			logger.error(e) { "[ScriptEvent::executeEvent] - Failed to execute raid script: $scriptName" }
			false
		}
	}

	override fun reInitState() {
		scripts.clear()
	}

	private companion object {
		/** `ScriptEvent::getScriptEventName()` (src/lua/creature/raids.cpp:608-610). */
		const val SCRIPT_EVENT_NAME = "onRaid"
	}
}
