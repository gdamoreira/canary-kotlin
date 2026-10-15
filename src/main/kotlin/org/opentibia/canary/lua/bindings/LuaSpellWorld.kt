package org.opentibia.canary.lua.bindings

import org.opentibia.canary.account.Account
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.spells.SpellCaster
import org.opentibia.canary.spells.SpellTarget
import org.opentibia.canary.spells.SpellWorld
import org.opentibia.canary.spells.WorldType

/**
 * Production [SpellWorld] over the optional [GameMap], built for the
 * `InstantSpell::canThrowSpell` gate of the monster spell entry points
 * (spells.cpp:1299-1310).
 *
 * Only `Game::canThrowObjectTo` (game.cpp:7591) is live; the config values
 * keep the C++ field defaults and every other world query stays inert because
 * the call sites that read them are not part of this entry point
 * (AGENTS.md 1.1: injected seams, documented gaps).
 */
class LuaSpellWorld(private val gameMap: GameMap?) : SpellWorld {

    /** `g_configManager().getBoolean(LEARN_SPELLS)`; no Lua call site here. */
    override val learnSpells: Boolean = false

    /** `g_configManager().getBoolean(REMOVE_RUNE_CHARGES)`; no Lua call site here. */
    override val removeRuneCharges: Boolean = false

    /** `g_game().getWorldType()`; no Lua call site here. */
    override val worldType: WorldType = WorldType.NO_PVP

    /** `g_configManager().getFloat(RATE_SPELL_COOLDOWN)`; no Lua call site here. */
    override val rateSpellCooldown: Double = 1.0

    /** `g_configManager().getNumber(MAX_PLAYERS_PER_ACCOUNT)`; no Lua call site here. */
    override val maxPlayersPerAccount: Int = 1

    /** `g_configManager().getNumber(MAX_PLAYERS_OUTSIDE_PZ_PER_ACCOUNT)`; no Lua call site here. */
    override val maxPlayersOutsidePzPerAccount: Int = 1

    /**
     * `Game::canThrowObjectTo` (game.cpp:7591): the `-1` range folds the
     * range-less overload's viewport bounds (`spells.cpp:1306`), otherwise the
     * ranged overload bounds both axes by [range]. Without a wired map the
     * historical permissive result is kept (PARITY GAP).
     */
    override fun canThrowObjectTo(from: Position, to: Position, checkSightLine: Boolean, range: Int): Boolean {
        val map = gameMap ?: return true
        val sightLine = if (checkSightLine) {
            GameMap.SightLine_CheckSightLineAndFloor
        } else {
            GameMap.SightLine_NoCheck
        }
        return if (range == -1) {
            map.canThrowObjectTo(from, to, sightLine)
        } else {
            map.canThrowObjectTo(from, to, sightLine, range, range)
        }
    }

    /** `g_game().sendDoubleSoundEffect`; the gate never casts. */
    override fun sendDoubleSoundEffect(
        position: Position,
        castEffect: SoundEffect,
        impactEffect: SoundEffect,
        spectator: SpellCaster,
    ) = Unit

    /** `Tile::hasFlag(TILESTATE_BLOCKSOLID)`; the gate never checks tiles. */
    override fun isTileBlockingSolid(position: Position): Boolean = false

    /** `Tile::getBottomVisibleCreature`; the gate never checks tiles. */
    override fun getBottomVisibleCreatureId(position: Position): Long? = null

    /** `Game::getPlayerByNameWildcard`; the gate never resolves players. */
    override fun getPlayerByNameWildcard(name: String): SpellTarget? = null

    /** `Game::getPlayersByAccount`; the gate never resolves accounts. */
    override fun getPlayersByAccount(account: Account?): List<SpellCaster> = emptyList()
}
