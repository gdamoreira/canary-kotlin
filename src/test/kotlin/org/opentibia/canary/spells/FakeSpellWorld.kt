package org.opentibia.canary.spells

import org.opentibia.canary.map.Position

/** Injected world/config seam replacement for `g_game()`/`g_configManager()`. */
class FakeSpellWorld(
    override var learnSpells: Boolean = true,
    override var removeRuneCharges: Boolean = true,
    override var worldType: WorldType = WorldType.PVP,
    override var rateSpellCooldown: Double = 1.0,
) : SpellWorld {

    var tileBlockingSolid: (Position) -> Boolean = { false }
    var bottomVisibleCreature: (Position) -> Long? = { null }
    var canThrow: (from: Position, to: Position, checkSightLine: Boolean, range: Int) -> Boolean =
        { _, _, _, _ -> true }
    var playerByName: (String) -> SpellTarget? = { null }

    /** Every sound effect dispatched through `g_game().sendDoubleSoundEffect`. */
    val doubleSounds: MutableList<Triple<Position, SoundEffect, SoundEffect>> = mutableListOf()

    override fun sendDoubleSoundEffect(
        position: Position,
        castEffect: SoundEffect,
        impactEffect: SoundEffect,
        spectator: SpellCaster,
    ) {
        doubleSounds.add(Triple(position, castEffect, impactEffect))
    }

    override fun isTileBlockingSolid(position: Position): Boolean = tileBlockingSolid(position)

    override fun getBottomVisibleCreatureId(position: Position): Long? = bottomVisibleCreature(position)

    override fun canThrowObjectTo(from: Position, to: Position, checkSightLine: Boolean, range: Int): Boolean =
        canThrow(from, to, checkSightLine, range)

    override fun getPlayerByNameWildcard(name: String): SpellTarget? = playerByName(name)
}

/** Test double for a creature targeted by the spell pipeline. */
class FakeSpellTarget(
    private val id: Long = 2,
    private val name: String = "Target",
    position: Position = Position(101, 100, 7),
    var healthValue: Int = 100,
    var removedValue: Boolean = false,
    var accessPlayerValue: Boolean = false,
) : SpellTarget {

    var currentPosition: Position = position

    override fun getID(): Long = id

    override fun getName(): String = name

    override fun getPosition(): Position = currentPosition

    override fun getHealth(): Int = healthValue

    override fun isRemoved(): Boolean = removedValue

    override fun isAccessPlayer(): Boolean = accessPlayerValue
}
