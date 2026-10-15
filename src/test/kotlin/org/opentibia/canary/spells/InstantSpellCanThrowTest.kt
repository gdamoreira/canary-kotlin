package org.opentibia.canary.spells

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for `InstantSpell::canThrowSpell`
 * (src/creatures/combat/spells.cpp:1299-1310) and the `Game::canThrowObjectTo`
 * seam fold it drives (`game.hpp:472`, `game.cpp:7591`).
 */
class InstantSpellCanThrowTest {

    private val world = FakeSpellWorld(learnSpells = false, rateSpellCooldown = 1.0)
    private val scripts = FakeSpellScriptRunner()
    private val engine = SpellEngine(Spells(), world, scripts)

    private fun caster(position: Position = Position(100, 100, 7)): FakeSpellCaster =
        FakeSpellCaster(levelValue = 20, manaValue = 100).apply { currentPosition = position }

    private fun spell(range: Int = -1, checkLineOfSight: Boolean = true): InstantSpell = InstantSpell().apply {
        spellType = SpellType.SPELL_INSTANT
        name = "test spell"
        words = "testspell"
        spellId = 7
        group = SpellGroup.SPELLGROUP_ATTACK
        aggressive = true
        level = 1
        mana = 0
        needTarget = true
        this.range = range
        this.checkLineOfSight = checkLineOfSight
    }

    @Test
    fun `the seam receives the caster and target positions, the sight flag and the range`() {
        val caster = caster()
        val target = FakeSpellTarget(position = Position(105, 100, 7))

        assertThat(engine.canThrowSpell(caster, spell(range = 8, checkLineOfSight = false), target)).isTrue()

        assertThat(world.canThrowCalls).containsExactly(
            FakeSpellWorld.ThrowCall(Position(100, 100, 7), Position(105, 100, 7), false, 8),
        )
    }

    @Test
    fun `the range less overload forwards the minus one sentinel`() {
        // spells.cpp:1306: `range == -1` takes the no-range overload; the seam
        // keeps the sentinel and the production binding applies the
        // MAP_MAX_CLIENT_VIEW_PORT defaults.
        val caster = caster()
        val target = FakeSpellTarget(position = Position(105, 100, 7))

        assertThat(engine.canThrowSpell(caster, spell(range = -1), target)).isTrue()

        assertThat(world.canThrowCalls).containsExactly(
            FakeSpellWorld.ThrowCall(Position(100, 100, 7), Position(105, 100, 7), true, -1),
        )
    }

    @Test
    fun `a different floor rejects without consulting the seam`() {
        // spells.cpp:1306: `fromPos.z != toPos.z` short-circuits.
        val caster = caster(Position(100, 100, 7))
        val target = FakeSpellTarget(position = Position(100, 100, 6))

        assertThat(engine.canThrowSpell(caster, spell(), target)).isFalse()
        assertThat(world.canThrowCalls).isEmpty()
    }

    @Test
    fun `an out of range target is rejected through the game seam`() {
        // `Game::canThrowObjectTo(from, to, line, range, range)` (game.cpp:7591).
        val game = Game(GameMap(), GameDispatcher())
        world.canThrow = { from, to, checkSightLine, range ->
            val line = if (checkSightLine) {
                GameMap.SightLine_CheckSightLineAndFloor
            } else {
                GameMap.SightLine_NoCheck
            }
            if (range == -1) {
                game.canThrowObjectTo(from, to, line)
            } else {
                game.canThrowObjectTo(from, to, line, range, range)
            }
        }
        val caster = caster(Position(100, 100, 7))

        assertThat(engine.canThrowSpell(caster, spell(range = 5), FakeSpellTarget(position = Position(105, 100, 7))))
            .isTrue()
        assertThat(engine.canThrowSpell(caster, spell(range = 5), FakeSpellTarget(position = Position(111, 100, 7))))
            .isFalse()
        // The no-range overload bounds by MAP_MAX_CLIENT_VIEW_PORT_X (8).
        assertThat(engine.canThrowSpell(caster, spell(range = -1), FakeSpellTarget(position = Position(109, 100, 7))))
            .isFalse()
        assertThat(engine.canThrowSpell(caster, spell(range = -1), FakeSpellTarget(position = Position(108, 100, 7))))
            .isTrue()
    }

    @Test
    fun `player cast instant cancels an out of range target without running the script`() {
        // spells.cpp:1229-1232: `canThrowSpell` false -> CREATUREISNOTREACHABLE.
        world.canThrow = { _, _, _, _ -> false }
        val caster = caster()
        caster.attackedTarget = FakeSpellTarget(position = Position(105, 100, 7))

        assertThat(engine.playerCastInstant(caster, spell(range = 5))).isFalse()

        assertThat(caster.cancelMessages).containsExactly(ReturnValue.CREATUREISNOTREACHABLE)
        assertThat(scripts.instantCalls).isEmpty()
    }

    @Test
    fun `player cast instant reaches the script when the target is reachable`() {
        val caster = caster()
        caster.attackedTarget = FakeSpellTarget(position = Position(105, 100, 7))

        assertThat(engine.playerCastInstant(caster, spell(range = 5))).isTrue()

        assertThat(caster.cancelMessages).isEmpty()
        assertThat(scripts.instantCalls).hasSize(1)
    }
}
