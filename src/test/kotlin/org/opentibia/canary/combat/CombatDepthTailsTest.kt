package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import java.util.Random

/**
 * Transcript tests for the wave-67 combat depth tails of `Combat::CombatFunc`
 * (src/creatures/combat/combat.cpp:1507-1643) and `Combat::combatTileEffects`
 * (combat.cpp:1161-1264):
 *
 *  - the `ExpertPvp` field swaps and the `legacyBlockingWall` fight-tick branch
 *    (combat.cpp:1221-1231);
 *  - the `applyExtensions` copy semantics (`extensionsDamage` staged per target,
 *    single-target promotion, combat.cpp:1583-1586);
 *  - the per-creature `Creature::getCombatDamage` override and its reset
 *    (combat.cpp:1619-1627);
 *  - the Wheel of Destiny beam-mastery counter (combat.cpp:1577-1580, 1614-1617).
 */
class CombatDepthTailsTest {

    private val casterPos = Position(100, 100, 7)
    private val targetPos = Position(101, 100, 7)

    /** `AreaCombat::setupArea(list, rows)` of a `[1,3,1]` row rotated East. */
    private fun eastArea(): AreaCombat = AreaCombat().apply { setupArea(listOf(1, 3, 1), 1) }

    private class Recorder {
        val applied: MutableList<Pair<CombatActor, CombatDamage>> = mutableListOf()

        fun function(): CombatFunction = CombatFunction { _, target, _, damage ->
            applied.add(target to damage)
        }
    }

    private fun actor(position: Position): FakeCombatActor =
        FakeCombatActor().apply { positionValue = position }

    private fun tileFor(map: FakeCombatMap, position: Position): FakeCombatTile =
        map.getOrCreateTile(position) as FakeCombatTile

    /**
     * Player stub whose beam-mastery counter starts at [beamTotal] and applies
     * `PlayerWheel::checkBeamMasteryDamage`'s `damageMultiplier += 10` per
     * accepted target (player_wheel.cpp:4045-4052).
     */
    private class BeamCombatPlayer(private val beamTotal: Int) : FakeCombatPlayer() {
        override fun getBeamAffectedTotal(damage: CombatDamage): Int = beamTotal

        override fun updateBeamMasteryDamage(
            damage: CombatDamage,
            beamAffectedTotal: Int,
            beamAffectedCurrent: Int,
        ): Pair<Int, Int> {
            if (beamAffectedTotal <= 0) {
                return beamAffectedTotal to beamAffectedCurrent
            }
            damage.damageMultiplier += 10
            return (beamAffectedTotal - 1) to (beamAffectedCurrent + 1)
        }
    }

    private fun castField(
        map: FakeCombatMap,
        caster: FakeCombatPlayer,
        effects: FakeCombatEffects,
        itemId: Int,
        expertPvpEnabled: Boolean,
    ) {
        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = null,
            params = CombatParams(combatType = CombatType.NONE, aggressive = true, itemId = itemId),
            map = map,
            effects = effects,
            damage = null,
            func = null,
            expertPvpEnabled = expertPvpEnabled,
        )
    }

    // --- ExpertPvp field swaps and the fight-tick branch --------------------------

    @Test
    fun `expert pvp swaps the legacy walls for their safe variants without fight ticks`() {
        // combat.cpp:1221-1231: `expertPvpWall` swaps MAGICWALL/WILDGROWTH and
        // `legacyBlockingWall` is gated off, so no fight ticks are armed.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        tileFor(map, targetPos)
        val effects = FakeCombatEffects()

        castField(map, caster, effects, Combat.ITEM_MAGICWALL, expertPvpEnabled = true)

        assertThat(effects.fieldItems.single().itemId).isEqualTo(Combat.ITEM_MAGICWALL_SAFE)
        assertThat(caster.fightTicks).isZero()
    }

    @Test
    fun `expert pvp swaps the persistent wild growth through the swap table`() {
        // combat.cpp:1190-1192 then 1221-1225: WILDGROWTH_PERSISTENT -> WILDGROWTH -> SAFE.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        tileFor(map, targetPos)
        val effects = FakeCombatEffects()

        castField(map, caster, effects, Combat.ITEM_WILDGROWTH_PERSISTENT, expertPvpEnabled = true)

        assertThat(effects.fieldItems.single().itemId).isEqualTo(Combat.ITEM_WILDGROWTH_SAFE)
        assertThat(caster.fightTicks).isZero()
    }

    @Test
    fun `without expert pvp the legacy blocking wall arms the caster fight ticks`() {
        // combat.cpp:1228-1231: `legacyBlockingWall = !expertPvpWall && ...`.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        tileFor(map, targetPos)
        val effects = FakeCombatEffects()

        castField(map, caster, effects, Combat.ITEM_MAGICWALL, expertPvpEnabled = false)

        assertThat(effects.fieldItems.single().itemId).isEqualTo(Combat.ITEM_MAGICWALL)
        assertThat(caster.fightTicks).isEqualTo(1)
    }

    @Test
    fun `the combat none field path places the pvp field and arms fight ticks`() {
        // combat.cpp:1486-1505 -> CombatFunc(CombatNullFunc, nullptr) -> combatTileEffects:
        // the COMBAT_NONE field placement still runs the swap table and fight ticks.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        tileFor(map, targetPos)
        val effects = FakeCombatEffects()

        castField(map, caster, effects, Combat.ITEM_FIREFIELD_PERSISTENT_FULL, expertPvpEnabled = false)

        assertThat(effects.fieldItems.single().itemId).isEqualTo(Combat.ITEM_FIREFIELD_PVP_FULL)
        assertThat(caster.fightTicks).isEqualTo(1)
    }

    // --- applyExtensions copy semantics -------------------------------------------

    @Test
    fun `extensions promote the single target damage copy`() {
        // combat.cpp:1583-1586: `extensionsDamage = tmpDamage`, then
        // `tmpDamage = extensionsDamage` for a single affected target.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        tileFor(map, targetPos).creatureList.add(actor(targetPos))
        val recorder = Recorder()
        val damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -100)

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = null,
            params = CombatParams(combatType = CombatType.FIREDAMAGE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = damage,
            func = recorder.function(),
            extensionBaseChance = 10000,
            extensionBaseBonus = 5000,
            random = Random(1),
        )

        val applied = recorder.applied.single().second
        assertThat(applied.primaryValue).isEqualTo(-150)
        assertThat(applied.critical).isTrue()
        // The caller's damage object is never mutated (the port copies it).
        assertThat(damage.primaryValue).isEqualTo(-100)
        assertThat(damage.critical).isFalse()
    }

    @Test
    fun `extensions stage independent per target copies for area combat`() {
        // combat.cpp:2771-2775: multi-target extensions go through
        // `targetCreature->setCombatDamage(targetDamage)`.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val targets = mutableListOf<FakeCombatActor>()
        for (position in listOf(Position(101, 99, 7), Position(101, 100, 7), Position(101, 101, 7))) {
            val target = actor(position)
            targets.add(target)
            tileFor(map, position).creatureList.add(target)
        }
        val recorder = Recorder()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = eastArea(),
            params = CombatParams(combatType = CombatType.FIREDAMAGE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -100),
            func = recorder.function(),
            extensionBaseChance = 10000,
            extensionBaseBonus = 5000,
            random = Random(1),
        )

        assertThat(recorder.applied).hasSize(3)
        assertThat(recorder.applied.map { it.second.primaryValue }).containsExactly(-150, -150, -150)
        assertThat(recorder.applied[0].second).isNotSameAs(recorder.applied[1].second)
        // `Creature::getCombatDamage` is read and reset for every target
        // (combat.cpp:1619-1627), so every staged copy was consumed.
        assertThat(targets.map { it.combatDamageValue.isEmpty() }).containsExactly(true, true, true)
    }

    // --- per-creature getCombatDamage override ------------------------------------

    @Test
    fun `the per creature damage override wins over the cast damage and resets after use`() {
        // combat.cpp:1619-1627: a non-empty `creature->getCombatDamage()` is handed
        // to func and reset to a default `CombatDamage` afterwards.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val target = actor(targetPos).apply {
            combatDamageValue = CombatDamage(primaryType = CombatType.ICEDAMAGE, primaryValue = -42)
        }
        tileFor(map, targetPos).creatureList.add(target)
        val recorder = Recorder()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = null,
            params = CombatParams(combatType = CombatType.FIREDAMAGE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -100),
            func = recorder.function(),
        )

        val applied = recorder.applied.single().second
        assertThat(applied.primaryType).isEqualTo(CombatType.ICEDAMAGE)
        assertThat(applied.primaryValue).isEqualTo(-42)
        assertThat(target.combatDamageValue.isEmpty()).isTrue()
    }

    // --- Wheel of Destiny beam mastery --------------------------------------------

    @Test
    fun `beam mastery updates the damage for the capped affected targets`() {
        // player_wheel.cpp:4045-4052 via combat.cpp:1614-1617: three accepted
        // targets get the beam multiplier, the rest keep the plain damage. The
        // `extension` flag keeps `applyExtensions` from staging per-target
        // copies, exactly like the C++ early return (combat.cpp:2649-2651), so
        // every target consumes the beam-updated `tmpDamage`.
        val map = FakeCombatMap()
        val caster = BeamCombatPlayer(beamTotal = 3).apply { positionValue = casterPos }
        for (position in listOf(Position(101, 99, 7), Position(101, 100, 7), Position(101, 101, 7))) {
            tileFor(map, position).creatureList.add(actor(position))
        }
        val recorder = Recorder()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = eastArea(),
            params = CombatParams(combatType = CombatType.FIREDAMAGE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -100, extension = true),
            func = recorder.function(),
        )

        assertThat(recorder.applied.map { it.second.damageMultiplier }).containsExactly(10, 20, 30)
    }

    @Test
    fun `an exhausted beam counter stops updating after the cap`() {
        // player_wheel.cpp:4046: the update only runs while `beamAffectedTotal > 0`.
        val map = FakeCombatMap()
        val caster = BeamCombatPlayer(beamTotal = 1).apply { positionValue = casterPos }
        for (position in listOf(Position(101, 99, 7), Position(101, 100, 7), Position(101, 101, 7))) {
            tileFor(map, position).creatureList.add(actor(position))
        }
        val recorder = Recorder()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = eastArea(),
            params = CombatParams(combatType = CombatType.FIREDAMAGE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -100, extension = true),
            func = recorder.function(),
        )

        assertThat(recorder.applied.map { it.second.damageMultiplier }).containsExactly(10, 10, 10)
    }
}
