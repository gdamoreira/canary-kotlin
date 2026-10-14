package org.opentibia.canary.creatures.players

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators

private val logger = KotlinLogging.logger {}

/** `STORAGEVALUE_EMOTE` (`src/utils/const.hpp:29`). */
private const val STORAGEVALUE_EMOTE = 30008

/** `MAP_MAX_CLIENT_VIEW_PORT_X` (`src/map/map_const.hpp:12`). */
private const val MAP_MAX_CLIENT_VIEW_PORT_X = 8

/** `MAP_MAX_CLIENT_VIEW_PORT_Y` (`src/map/map_const.hpp:13`). */
private const val MAP_MAX_CLIENT_VIEW_PORT_Y = 6

/** Inert map access for the default empty local spectator snapshot. */
private object NullSpectatorMapAccess : SpectatorMapAccess {
    override fun getMapSector(x: Int, y: Int): SpectatorSector? = null
}

/**
 * `Player::saySpell` (`src/creatures/players/player.cpp:11026-11080`).
 *
 * Spectators receive the emote/monster-say variant when `EMOTE_SPELLS` is
 * enabled and their emote storage value is 1, otherwise the spell-use type.
 * Ghost mode restricts the send to spectators that can see the caster, and the
 * Lua `onCreatureSay` event fires for every player spectator afterwards.
 *
 * The Canary globals (`g_configManager`, the `Spectators` map query and the
 * protocol sinks) are injected seams per AGENTS.md 1.1; the default local
 * search is empty because the port's `Spectators` needs the map access.
 */
fun Player.saySpell(
    /** `type` (player.cpp:11026). */
    type: SpeakClasses,
    /** `text` (player.cpp:11026). */
    text: String,
    /** `isGhostMode` (player.cpp:11026). */
    isGhostMode: Boolean,
    /** `spectatorsPtr` (player.cpp:11026); an empty snapshot falls back to the local search. */
    spectatorsPtr: Spectators? = null,
    /** `pos` (player.cpp:11026); defaults to `getPosition()`. */
    pos: Position? = null,
    /**
     * `Spectators().find<Creature>(...)` (player.cpp:11041-11047): `multifloor`
     * is true and the window doubled for yells.
     */
    findSpectators: (center: Position, multifloor: Boolean, minRangeX: Int, maxRangeX: Int, minRangeY: Int, maxRangeY: Int) -> Spectators =
        { _, _, _, _, _, _ -> Spectators(NullSpectatorMapAccess) },
    /** `g_configManager().getBoolean(EMOTE_SPELLS)` (configmanager.cpp:98, default false). */
    emoteSpells: Boolean = false,
    /** `tmpPlayer->getStorageValue(STORAGEVALUE_EMOTE)` (player.cpp:11058). */
    getStorageValue: (Player, Int) -> Int = { _, _ -> 0 },
    /** `tmpPlayer->sendCreatureSay(speaker, type, text, pos)` (player.cpp:11062, 11064). */
    sendCreatureSay: (recipient: Player, speaker: Player, type: SpeakClasses, text: String, pos: Position) -> Unit =
        { _, _, _, _, _ -> },
    /** `tmpPlayer->onCreatureSay(speaker, type, text)` (player.cpp:11076) Lua event. */
    onCreatureSay: ((Player, SpeakClasses, String) -> Unit)? = null,
): Boolean {
    if (text.isEmpty()) {
        logger.debug { "[Player::saySpell] Spell text is empty for player $name" }
        return false
    }

    val speakerPos = pos ?: position
    val spectators = when {
        spectatorsPtr != null && !spectatorsPtr.empty() -> spectatorsPtr
        type != SpeakClasses.TALKTYPE_YELL && type != SpeakClasses.TALKTYPE_MONSTER_YELL ->
            findSpectators(
                speakerPos,
                false,
                MAP_MAX_CLIENT_VIEW_PORT_X,
                MAP_MAX_CLIENT_VIEW_PORT_X,
                MAP_MAX_CLIENT_VIEW_PORT_Y,
                MAP_MAX_CLIENT_VIEW_PORT_Y,
            )

        else -> findSpectators(
            speakerPos,
            true,
            (MAP_MAX_CLIENT_VIEW_PORT_X + 1) * 2,
            (MAP_MAX_CLIENT_VIEW_PORT_X + 1) * 2,
            (MAP_MAX_CLIENT_VIEW_PORT_Y + 1) * 2,
            (MAP_MAX_CLIENT_VIEW_PORT_Y + 1) * 2,
        )
    }

    // Send to client
    for (spectator in spectators) {
        val tmpPlayer = spectator as? Player ?: continue
        val valueEmote = if (emoteSpells) getStorageValue(tmpPlayer, STORAGEVALUE_EMOTE) else 0
        if (!isGhostMode || tmpPlayer.canSeeCreature(this)) {
            if (valueEmote == 1) {
                sendCreatureSay(tmpPlayer, this, SpeakClasses.TALKTYPE_MONSTER_SAY, text, speakerPos)
            } else {
                sendCreatureSay(tmpPlayer, this, SpeakClasses.TALKTYPE_SPELL_USE, text, speakerPos)
            }
        }
    }

    // Execute lua event method
    val creatureSayEvent = onCreatureSay ?: { p: Player, t: SpeakClasses, x: String ->
        p.onCreatureSay(this, t, x)
    }
    for (spectator in spectators) {
        val tmpPlayer = spectator as? Player ?: continue
        creatureSayEvent(tmpPlayer, type, text)
    }

    return true
}
