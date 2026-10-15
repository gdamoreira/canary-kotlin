package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.FakeConditionTarget
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.Random

/**
 * Transcript tests for the player-identity hooks of the combat damage dispatch:
 *  - `Combat::CombatHealthFunc` monster `onPlayerAttack` fanout
 *    (src/creatures/combat/combat.cpp:895-899,
 *    src/creatures/monsters/monster.cpp:600-625)
 *  - `CombatManaFunc` black-skull halving (combat.cpp:988-996)
 *  - `Combat::canDoCombat(caster, tile, aggressive)` (combat.cpp:376-426)
 *  - `ValueCallback::getMinMaxValues` resolution (combat.cpp:191-192, 1982-2068)
 */
class CombatSpellIdentityTest {

    private fun playerCaster(id: Int = 1): FakeCombatTarget =
        FakeCombatTarget(id = id).apply { playerValue = FakeConditionTarget() }

    private fun monsterTarget(
        monster: FakeCombatMonster,
        id: Int = 2,
        health: Int = 1000,
        mana: Int = 100,
    ): FakeCombatTarget = FakeCombatTarget(id = id, health = health, mana = mana).apply {
        monsterValue = monster
    }

    // `Combat::CombatHealthFunc` (combat.cpp:832-902) reaches
    // `targetMonster->onAttackedByPlayer(attackerPlayer)` for every player hit.

    @Test
    fun `player spell damage on a monster fires the player attack hook`() {
        val attackerPlayer = FakeConditionTarget()
        val caster = FakeCombatTarget(id = 1).apply { playerValue = attackerPlayer }
        val monster = FakeCombatMonster()
        val target = monsterTarget(monster)

        Combat.doCombatHealth(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -50),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )

        assertThat(monster.attackedByPlayers).containsExactly(attackerPlayer)
        assertThat(target.getHealth()).isEqualTo(950)
    }

    @Test
    fun `fully blocked spell damage still fires the player attack hook`() {
        // combat.cpp:895-901: the hook runs before `Game::combatBlockHit`.
        val caster = playerCaster()
        val monster = FakeCombatMonster()
        val target = monsterTarget(monster).apply { armorValue = 1000 }

        Combat.doCombatHealth(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -50),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE, blockedByArmor = true),
            random = Random(1),
        )

        assertThat(monster.attackedByPlayers).hasSize(1)
        assertThat(target.getHealth()).isEqualTo(1000)
    }

    @Test
    fun `positive non healing damage on a monster still fires the player attack hook`() {
        // combat.cpp:895-901: the hook runs before `Game::combatBlockHit`'s
        // positive-damage early return.
        val caster = playerCaster()
        val monster = FakeCombatMonster()
        val target = monsterTarget(monster)

        Combat.doCombatHealth(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = 50),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )

        assertThat(monster.attackedByPlayers).hasSize(1)
        assertThat(target.getHealth()).isEqualTo(1000)
    }

    @Test
    fun `monster casters do not fire the player attack hook`() {
        val caster = FakeCombatTarget(id = 1)
        val monster = FakeCombatMonster()
        val target = monsterTarget(monster)

        Combat.doCombatHealth(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -50),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE, aggressive = false),
            random = Random(1),
        )

        assertThat(monster.attackedByPlayers).isEmpty()
    }

    @Test
    fun `player healing a monster does not fire the player attack hook`() {
        // combat.cpp:1660-1685: the player-heals-monster branch returns before
        // `CombatHealthFunc`.
        val caster = playerCaster()
        val monster = FakeCombatMonster()
        val target = monsterTarget(monster, health = 100)

        Combat.doCombatHealth(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.HEALING, primaryValue = 50),
            params = CombatParams(combatType = CombatType.HEALING, aggressive = false),
            random = Random(1),
        )

        assertThat(target.getHealth()).isEqualTo(150)
        assertThat(monster.attackedByPlayers).isEmpty()
    }

    @Test
    fun `mana drain does not fire the player attack hook`() {
        // `CombatManaFunc` (combat.cpp:967-1010) never calls onAttackedByPlayer.
        val caster = playerCaster()
        val monster = FakeCombatMonster()
        val target = monsterTarget(monster)

        Combat.doCombatMana(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = -40),
            params = CombatParams(combatType = CombatType.MANADRAIN),
            random = Random(1),
        )

        assertThat(monster.attackedByPlayers).isEmpty()
    }

    // `CombatManaFunc` black-skull halving (combat.cpp:988-996).

    @Test
    fun `player mana drain is halved against a non black skull player`() {
        val caster = playerCaster()
        val target = FakeCombatTarget(id = 2, mana = 100).apply {
            playerValue = FakeConditionTarget()
            skullValue = Skulls_t.SKULL_NONE
        }

        Combat.doCombatMana(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = -40),
            params = CombatParams(combatType = CombatType.MANADRAIN),
            random = Random(1),
        )

        assertThat(target.getMana()).isEqualTo(80)
    }

    @Test
    fun `black skull players take the full player mana drain`() {
        val caster = playerCaster()
        val target = FakeCombatTarget(id = 2, mana = 100).apply {
            playerValue = FakeConditionTarget()
            skullValue = Skulls_t.SKULL_BLACK
        }

        Combat.doCombatMana(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = -41),
            params = CombatParams(combatType = CombatType.MANADRAIN),
            random = Random(1),
        )

        assertThat(target.getMana()).isEqualTo(59)
    }

    @Test
    fun `monster mana drains are never halved`() {
        val caster = playerCaster()
        val target = FakeCombatTarget(id = 2, mana = 100)

        Combat.doCombatMana(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = -40),
            params = CombatParams(combatType = CombatType.MANADRAIN),
            random = Random(1),
        )

        assertThat(target.getMana()).isEqualTo(60)
    }

    @Test
    fun `non player casters never halve mana drains`() {
        val caster = FakeCombatTarget(id = 1)
        val target = FakeCombatTarget(id = 2, mana = 100).apply { playerValue = FakeConditionTarget() }

        Combat.doCombatMana(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = -40),
            params = CombatParams(combatType = CombatType.MANADRAIN),
            random = Random(1),
        )

        assertThat(target.getMana()).isEqualTo(60)
    }

    @Test
    fun `mana restoration is never halved`() {
        val caster = playerCaster()
        val target = FakeCombatTarget(id = 2, mana = 100).apply { playerValue = FakeConditionTarget() }

        Combat.doCombatMana(
            caster = caster,
            target = target,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = 40),
            params = CombatParams(combatType = CombatType.MANADRAIN, aggressive = false),
            random = Random(1),
        )

        assertThat(target.getMana()).isEqualTo(140)
    }

    // `params.valueCallback->getMinMaxValues` (combat.cpp:191-192, 1982-2068).

    /** `ValueCallback(COMBAT_FORMULA_LEVELMAGIC)` (`Combat::setCallback`, combat.cpp:674-676). */
    private fun levelMagicCallback(
        get: (useCharges: Boolean, operands: CombatCallbackOperands.LevelMagic) -> Pair<Int, Int>?,
    ): CombatValueCallback = object : CombatValueCallback {
        override val type: FormulaType = FormulaType.LEVELMAGIC

        override fun getMinMaxValues(useCharges: Boolean, operands: CombatCallbackOperands): Pair<Int, Int>? =
            when (operands) {
                is CombatCallbackOperands.LevelMagic -> get(useCharges, operands)
                else -> null
            }
    }

    @Test
    fun `value callback min max numbers win over the level magic formula`() {
        val callback = levelMagicCallback { useCharges, _ -> if (useCharges) 30 to 30 else 10 to 20 }

        val damage = Combat.getCombatDamage(
            formulaType = FormulaType.LEVELMAGIC,
            mina = 1.0,
            maxa = 2.0,
            levelFormula = 100,
            valueCallback = callback,
            useCharges = true,
            random = Random(1),
        )

        assertThat(damage.primaryValue).isEqualTo(30)
        assertThat(damage.secondaryType).isEqualTo(CombatType.NONE)
        assertThat(damage.secondaryValue).isZero()
    }

    @Test
    fun `value callback forwards the use charges flag`() {
        var seenUseCharges: Boolean? = null
        val callback = levelMagicCallback { useCharges, _ ->
            seenUseCharges = useCharges
            5 to 5
        }

        val damage = Combat.getCombatDamage(
            formulaType = FormulaType.LEVELMAGIC,
            mina = 1.0,
            maxa = 2.0,
            levelFormula = 100,
            valueCallback = callback,
            useCharges = true,
            random = Random(1),
        )

        assertThat(seenUseCharges).isTrue()
        assertThat(damage.primaryValue).isEqualTo(5)
    }

    @Test
    fun `the level magic callback receives the raw level and magic level skill operands`() {
        // combat.cpp:2001-2006: `onGetPlayerMinMaxValues(player, level, maglevel)`.
        var seenOperands: CombatCallbackOperands.LevelMagic? = null
        val callback = levelMagicCallback { _, operands ->
            seenOperands = operands
            5 to 5
        }

        Combat.getCombatDamage(
            formulaType = FormulaType.LEVELMAGIC,
            levelFormula = 100,
            valueCallback = callback,
            level = 120,
            magicLevelSkill = 44,
            random = Random(1),
        )

        assertThat(seenOperands).isEqualTo(CombatCallbackOperands.LevelMagic(level = 120, magicLevelSkill = 44))
    }

    @Test
    fun `an unresolved value callback leaves the damage at zero`() {
        // combat.cpp:2037-2040: the C++ early return leaves the callback damage at 0.
        val callback = levelMagicCallback { _, _ -> null }

        val damage = Combat.getCombatDamage(
            formulaType = FormulaType.LEVELMAGIC,
            mina = 1.0,
            maxa = 2.0,
            levelFormula = 100,
            valueCallback = callback,
            random = Random(1),
        )

        assertThat(damage.primaryValue).isZero()
    }

    @Test
    fun `monster combat values still win over the value callback`() {
        // combat.cpp:186-192: `getCombatValues` is checked before the callback.
        val callback = levelMagicCallback { _, _ -> 30 to 30 }

        val damage = Combat.getCombatDamage(
            formulaType = FormulaType.LEVELMAGIC,
            levelFormula = 100,
            valueCallback = callback,
            combatValues = 7 to 7,
            random = Random(1),
        )

        assertThat(damage.primaryValue).isEqualTo(7)
    }

    // `Combat::canDoCombat(caster, tile, aggressive)` (combat.cpp:376-426).

    @Test
    fun `tile combat allows non aggressive casts everywhere`() {
        val tile = FakeCombatTile()
        tile.flags.add(TileFlags.TILESTATE_PROTECTIONZONE)

        assertThat(Combat.canDoCombat(null, tile, aggressive = false)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `tile combat denies aggressive casts into a protection zone`() {
        val tile = FakeCombatTile()
        tile.flags.add(TileFlags.TILESTATE_PROTECTIONZONE)

        assertThat(Combat.canDoCombat(null, tile, aggressive = true))
            .isEqualTo(ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE)
    }

    @Test
    fun `tile combat refuses block projectile tiles without a magic wall`() {
        val tile = FakeCombatTile()
        tile.properties.add(Combat.CONST_PROP_BLOCKPROJECTILE)

        assertThat(Combat.canDoCombat(null, tile, aggressive = true)).isEqualTo(ReturnValue.CANNOTTHROW)

        tile.itemIdList.add(Combat.ITEM_MAGICWALL)
        assertThat(Combat.canDoCombat(null, tile, aggressive = true)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `tile combat refuses teleport tiles`() {
        val tile = FakeCombatTile()
        tile.teleport = true

        assertThat(Combat.canDoCombat(null, tile, aggressive = true)).isEqualTo(ReturnValue.CANNOTTHROW)
    }

    @Test
    fun `tile combat guards the stair direction`() {
        val down = FakeCombatActor().apply { positionValue = Position(100, 100, 6) }
        val up = FakeCombatActor().apply { positionValue = Position(100, 100, 8) }
        val tile = FakeCombatTile(Position(100, 100, 7))

        assertThat(Combat.canDoCombat(down, tile, aggressive = true)).isEqualTo(ReturnValue.FIRSTGODOWNSTAIRS)
        assertThat(Combat.canDoCombat(up, tile, aggressive = true)).isEqualTo(ReturnValue.FIRSTGOUPSTAIRS)
    }
}
