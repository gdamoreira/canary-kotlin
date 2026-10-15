package org.opentibia.canary.systems.raid

import org.opentibia.canary.lua.bindings.core.GameOperations
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * `GameFunctions::luaGameStartRaid` (src/lua/functions/core/game/game_functions.cpp:752-772)
 * bound over the composed [Raids] container: the `Game` table the Lua
 * environment installed keeps its documented `RETURNVALUE_NOSUCHRAIDEXISTS`
 * default until the server root composes raids, and this decorator replaces it
 * so a loaded raid starts in production.
 *
 * The tail is transcribed verbatim: an unknown or unloaded raid resolves
 * `RETURNVALUE_NOSUCHRAIDEXISTS`, a raid already executing resolves
 * `RETURNVALUE_ANOTHERRAIDISALREADYEXECUTING` (checked after the name, like the
 * C++ order), otherwise the raid becomes the running raid and schedules its
 * first event (`Raids::setRunning` + `Raid::startRaid`, raids.cpp:227-242).
 * Every other `Game` member delegates to the booted world operations unchanged.
 */
class RaidGameOperations(
	private val delegate: GameOperations,
	private val raids: Raids,
) : GameOperations by delegate {
	override fun startRaid(name: String): Int {
		val raid = raids.getRaidByName(name)
		if (raid == null || !raid.isLoaded()) {
			return ReturnValue.NOSUCHRAIDEXISTS.ordinal
		}

		if (raids.getRunning() != null) {
			return ReturnValue.ANOTHERRAIDISALREADYEXECUTING.ordinal
		}

		raids.setRunning(raid)
		raid.startRaid()
		return ReturnValue.NOERROR.ordinal
	}
}
