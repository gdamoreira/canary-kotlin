package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the `Combat::CombatFunc` consumption tail
 * (src/creatures/combat/combat.cpp:1507-1643) and its effect helpers
 * `Combat::combatTileEffects` (combat.cpp:1161-1264, 1242-1262) and
 * `Combat::postCombatEffects` (combat.cpp:1265-1275).
 *
 * The tile list is enumerated twice: the first pass runs the no-PvP
 * first-on-stack gate and collects the affected targets; the second pass
 * invokes the per-target function, the target callback and, per tile, the tile
 * effects.
 */
class CombatAreaDispatchTest {

    private val casterPos = Position(100, 100, 7)
    private val targetPos = Position(101, 100, 7)

    /**
     * `AreaCombat::setupArea(list, rows)` of a `[1,3,1]` row rotated East
     * (target is east of the caster), so the three marked cells land on
     * `(101, 99)`, `(101, 100)` and `(101, 101)` (targetPos is the centre).
     */
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

    @Test
    fun `the area dispatch consumes every accepted target across the tiles`() {
        // combat.cpp:1536-1546, 1608-1626: both passes walk the tile creature
        // lists under the same filters.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val centreActor = actor(Position(101, 100, 7))
        val southActor = actor(Position(101, 101, 7))
        tileFor(map, Position(101, 100, 7)).creatureList.add(centreActor)
        tileFor(map, Position(101, 101, 7)).creatureList.add(southActor)
        tileFor(map, Position(101, 99, 7))
        val recorder = Recorder()
        val damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -50)

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = eastArea(),
            params = CombatParams(combatType = CombatType.FIREDAMAGE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = damage,
            func = recorder.function(),
        )

        assertThat(recorder.applied.map { it.first }).containsExactly(centreActor, southActor)
        assertThat(recorder.applied.map { it.second.affected }).containsExactly(2, 2)
        // The caller's damage is never mutated; each target got a copy.
        assertThat(damage.affected).isEqualTo(1)
        assertThat(recorder.applied[0].second).isNotSameAs(recorder.applied[1].second)
    }

    @Test
    fun `a tile rejected by the combat gate is skipped in both passes`() {
        // combat.cpp:1547-1549, 1606-1608: `canDoCombat(caster, tile, aggressive)`.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val blockedTile = tileFor(map, Position(101, 99, 7))
        blockedTile.properties.add(Combat.CONST_PROP_BLOCKPROJECTILE)
        val blockedActor = actor(Position(101, 99, 7))
        blockedTile.creatureList.add(blockedActor)
        val openActor = actor(Position(101, 100, 7))
        tileFor(map, Position(101, 100, 7)).creatureList.add(openActor)
        tileFor(map, Position(101, 101, 7))
        val recorder = Recorder()
        val effects = FakeCombatEffects()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = eastArea(),
            params = CombatParams(combatType = CombatType.FIREDAMAGE, aggressive = true, impactEffect = 5),
            map = map,
            effects = effects,
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -50),
            func = recorder.function(),
        )

        assertThat(recorder.applied.map { it.first }).containsExactly(openActor)
        // Tile effects fire per non-rejected tile (combat.cpp:1634).
        assertThat(effects.magicEffects).hasSize(2)
    }

    @Test
    fun `target caster or top most keeps only the top creature of a foreign tile`() {
        // combat.cpp:1538-1544, 1612-1618: a tile the caster does not stand on
        // only dispatches its top creature.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val tile = tileFor(map, Position(101, 100, 7))
        val top = actor(Position(101, 100, 7))
        val bottom = actor(Position(101, 100, 7))
        tile.creatureList.add(top)
        tile.creatureList.add(bottom)
        tileFor(map, Position(101, 99, 7))
        tileFor(map, Position(101, 101, 7))
        val recorder = Recorder()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = eastArea(),
            params = CombatParams(
                combatType = CombatType.FIREDAMAGE,
                aggressive = true,
                targetCasterOrTopMost = true,
            ),
            map = map,
            effects = FakeCombatEffects(),
            damage = CombatDamage(primaryType = CombatType.FIREDAMAGE, primaryValue = -50),
            func = recorder.function(),
        )

        assertThat(recorder.applied.map { it.first }).containsExactly(top)
    }

    @Test
    fun `target caster or top most keeps only the caster on its own tile`() {
        // combat.cpp:1539-1541: when the caster stands on the tile the filter
        // keeps the caster (the aggressive gate still excludes it).
        val map = FakeCombatMap()
        val casterTile = tileFor(map, casterPos)
        val caster = FakeCombatPlayer().apply {
            positionValue = casterPos
            tileValue = casterTile
        }
        val other = actor(casterPos)
        casterTile.creatureList.add(other)
        casterTile.creatureList.add(caster)
        tileFor(map, Position(101, 99, 7))
        tileFor(map, Position(101, 100, 7))
        tileFor(map, Position(101, 101, 7))
        val recorder = Recorder()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = casterPos,
            area = eastArea(),
            params = CombatParams(
                combatType = CombatType.NONE,
                aggressive = false,
                targetCasterOrTopMost = true,
            ),
            map = map,
            effects = FakeCombatEffects(),
            damage = null,
            func = recorder.function(),
        )

        assertThat(recorder.applied.map { it.first }).containsExactly(caster)
    }

    @Test
    fun `an aggressive cast never dispatches on the caster itself`() {
        // combat.cpp:1546: `!params.aggressive || (caster != creature && ...)`.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val tile = tileFor(map, Position(101, 100, 7))
        tile.creatureList.add(caster)
        val monster = actor(Position(101, 100, 7))
        tile.creatureList.add(monster)
        tileFor(map, Position(101, 99, 7))
        tileFor(map, Position(101, 101, 7))
        val recorder = Recorder()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = eastArea(),
            params = CombatParams(combatType = CombatType.NONE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = null,
            func = recorder.function(),
        )

        assertThat(recorder.applied.map { it.first }).containsExactly(monster)
    }

    @Test
    fun `the no pvp gate cancels a non first on stack caster`() {
        // combat.cpp:1524-1530: `WORLD_TYPE_NO_PVP` + caster tile == origin +
        // `!isFirstOnStack()` -> cancel message + POFF effect + return.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply {
            positionValue = casterPos
            firstOnStackValue = false
        }
        val recorder = Recorder()
        val effects = FakeCombatEffects()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = casterPos,
            area = null,
            params = CombatParams(combatType = CombatType.NONE, aggressive = true),
            map = map,
            effects = effects,
            damage = null,
            func = recorder.function(),
            noPvpWorld = true,
        )

        assertThat(recorder.applied).isEmpty()
        assertThat(caster.cancelMessages).containsExactly(ReturnValue.NOTPOSSIBLE)
        assertThat(caster.magicEffects).containsExactly(casterPos to Combat.CONST_ME_POFF)
        assertThat(effects.magicEffects).isEmpty()
    }

    @Test
    fun `post combat effects dispatch the distance effect and sounds`() {
        // combat.cpp:1265-1275.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        tileFor(map, targetPos)
        val effects = FakeCombatEffects()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = null,
            params = CombatParams(
                combatType = CombatType.NONE,
                aggressive = true,
                distanceEffect = 31,
                soundCastEffect = 2,
                soundImpactEffect = 3,
            ),
            map = map,
            effects = effects,
            damage = null,
            func = null,
        )

        assertThat(effects.distanceEffects).hasSize(1)
        assertThat(effects.distanceEffects[0].caster).isSameAs(caster)
        assertThat(effects.distanceEffects[0].fromPos).isEqualTo(casterPos)
        assertThat(effects.distanceEffects[0].toPos).isEqualTo(targetPos)
        assertThat(effects.distanceEffects[0].effect).isEqualTo(31)
        // `combatTileEffects` (combat.cpp:1260) and `postCombatEffects`
        // (combat.cpp:1291) both dispatch the sound, exactly like Canary.
        assertThat(effects.doubleSounds).hasSize(2)
        assertThat(effects.doubleSounds[0].castEffect).isEqualTo(2)
        assertThat(effects.doubleSounds[0].impactEffect).isEqualTo(3)
        assertThat(effects.singleSounds).isEmpty()
    }

    @Test
    fun `tile effects fire the tile callback impact effect and single sound`() {
        // combat.cpp:1242-1262.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val tile = tileFor(map, targetPos)
        val effects = FakeCombatEffects()
        val tiles: MutableList<CombatTile> = mutableListOf()
        val params = CombatParams(
            combatType = CombatType.NONE,
            aggressive = true,
            impactEffect = 17,
            soundCastEffect = 4,
        )
        params.tileCallback = CombatTileCallback { _, seenTile -> tiles.add(seenTile) }

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = null,
            params = params,
            map = map,
            effects = effects,
            damage = null,
            func = null,
        )

        assertThat(tiles).containsExactly(tile)
        assertThat(effects.magicEffects).hasSize(1)
        assertThat(effects.magicEffects[0].effect).isEqualTo(17)
        // Duplicated by the `postCombatEffects` tail, exactly like Canary
        // (combat.cpp:1262 + combat.cpp:1293).
        assertThat(effects.singleSounds).hasSize(2)
        assertThat(effects.singleSounds[0].castEffect).isEqualTo(4)
    }

    @Test
    fun `the persistent fire field becomes the pvp field and arms fight ticks`() {
        // combat.cpp:1165-1175, 1216-1222.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        tileFor(map, targetPos)
        val effects = FakeCombatEffects()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = null,
            params = CombatParams(combatType = CombatType.NONE, aggressive = true, itemId = Combat.ITEM_FIREFIELD_PERSISTENT_FULL),
            map = map,
            effects = effects,
            damage = null,
            func = null,
        )

        assertThat(effects.fieldItems.single().itemId).isEqualTo(Combat.ITEM_FIREFIELD_PVP_FULL)
        assertThat(caster.fightTicks).isEqualTo(1)
    }

    @Test
    fun `a no pvp zone tile downgrades the pvp field`() {
        // combat.cpp:1210-1215: `WORLD_TYPE_NO_PVP || TILESTATE_NOPVPZONE`.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val tile = tileFor(map, targetPos)
        tile.flags.add(TileFlags.TILESTATE_NOPVPZONE)
        val effects = FakeCombatEffects()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = null,
            params = CombatParams(combatType = CombatType.NONE, aggressive = true, itemId = Combat.ITEM_POISONFIELD_PERSISTENT),
            map = map,
            effects = effects,
            damage = null,
            func = null,
        )

        assertThat(effects.fieldItems.single().itemId).isEqualTo(Combat.ITEM_POISONFIELD_NOPVP)
        assertThat(caster.fightTicks).isZero()
    }

    @Test
    fun `the target callback fires for every dispatched creature`() {
        // combat.cpp:1630-1632.
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val first = actor(Position(101, 100, 7))
        val second = actor(Position(101, 101, 7))
        tileFor(map, Position(101, 100, 7)).creatureList.add(first)
        tileFor(map, Position(101, 101, 7)).creatureList.add(second)
        tileFor(map, Position(101, 99, 7))
        val seen: MutableList<CombatActor> = mutableListOf()
        val params = CombatParams(combatType = CombatType.NONE, aggressive = true)
        params.targetCallback = CombatTargetCallback { _, target -> seen.add(target) }

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = eastArea(),
            params = params,
            map = map,
            effects = FakeCombatEffects(),
            damage = null,
            func = null,
        )

        assertThat(seen).containsExactly(first, second)
    }

    @Test
    fun `a null area dispatches the target tile creatures`() {
        // combat.cpp:1512-1516 with `area == nullptr`: `getCombatArea(toPos, toPos)`. 
        val map = FakeCombatMap()
        val caster = FakeCombatPlayer().apply { positionValue = casterPos }
        val onTarget = actor(targetPos)
        tileFor(map, targetPos).creatureList.add(onTarget)
        val recorder = Recorder()

        Combat.combatFunc(
            caster = caster,
            origin = casterPos,
            toPos = targetPos,
            area = null,
            params = CombatParams(combatType = CombatType.NONE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = null,
            func = recorder.function(),
        )

        assertThat(recorder.applied.map { it.first }).containsExactly(onTarget)
    }
}
