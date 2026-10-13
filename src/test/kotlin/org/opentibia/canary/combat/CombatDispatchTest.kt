package org.opentibia.canary.combat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import java.util.Random

/**
 * Transcript tests for the combat target-dispatch layer.
 *
 * Every expectation is derived from the exact C++ control flow in the cited
 * source lines:
 *  - `Combat::doCombat`, `Combat::doCombatHealth`, `Combat::doCombatMana`
 *    (src/creatures/combat/combat.cpp:1461-1755)
 *  - `Combat::CombatHealthFunc` / `Combat::CombatManaFunc` (combat.cpp:794-1030)
 *  - `Game::combatBlockHit` (src/game/game.cpp:7874-8057)
 *  - `Game::combatChangeHealth` (src/game/game.cpp:8344-8770)
 *  - `Game::combatChangeMana` (src/game/game.cpp:9063)
 *  - `Creature::blockHit` / `Creature::mitigateDamage` (src/creatures/creature.cpp:910-1009)
 *  - `GlobalFunctions::luaDoTargetCombatHealth/Mana`
 *    (src/lua/functions/core/game/global_functions.cpp:348-464)
 *
 * Canary sign convention is preserved: negative values damage/drain, positive
 * values heal/restore.
 */
class CombatDispatchTest {

    @Test
    fun `CombatParams mirrors the C++ struct defaults`() {
        // src/creatures/combat/combat.hpp:82-110
        val params = CombatParams()
        assertThat(params.combatType).isEqualTo(CombatType.NONE)
        assertThat(params.origin).isEqualTo(CombatOrigin.SPELL)
        assertThat(params.aggressive).isTrue()
        assertThat(params.blockedByArmor).isFalse()
        assertThat(params.blockedByShield).isFalse()
        assertThat(params.targetCasterOrTopMost).isFalse()
        assertThat(params.useCharges).isFalse()
        assertThat(params.itemId).isEqualTo(0)
        assertThat(params.dispelType).isEqualTo(ConditionType.CONDITION_NONE)
        assertThat(params.conditionList).isEmpty()
        assertThat(params.impactEffect).isEqualTo(0)
        assertThat(params.distanceEffect).isEqualTo(0)
        assertThat(params.chainEffect).isEqualTo(0)
    }

    @Test
    fun `doCombat routes manadrain to the mana dispatch and other types to health`() {
        // src/creatures/combat/combat.cpp:1469-1483
        val drained = FakeCombatTarget(health = 1000, mana = 100)
        Combat.doCombat(
            caster = null,
            target = drained,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = -40),
            params = CombatParams(combatType = CombatType.MANADRAIN),
            random = Random(1),
        )
        assertThat(drained.getMana()).isEqualTo(60)
        assertThat(drained.getHealth()).isEqualTo(1000)

        val hurt = FakeCombatTarget(health = 1000, mana = 100)
        Combat.doCombat(
            caster = null,
            target = hurt,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -50),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )
        assertThat(hurt.getHealth()).isEqualTo(950)
        assertThat(hurt.getMana()).isEqualTo(100)
    }

    @Test
    fun `doCombat records the affected target count on the damage`() {
        // src/creatures/combat/combat.cpp:1473 `damage.affected = affected`
        val target = FakeCombatTarget()
        val damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -10)
        Combat.doCombat(
            caster = null,
            target = target,
            damage = damage,
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
            affected = 3,
        )
        assertThat(damage.affected).isEqualTo(3)
    }

    @Test
    fun `doCombat leaves everything untouched when params have no combat type`() {
        // src/creatures/combat/combat.cpp:1480 `doCombatDefault` runs for the
        // condition-only branch and the call still reports true.
        val target = FakeCombatTarget(health = 1000, mana = 100)
        val applied = Combat.doCombat(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100),
            params = CombatParams(),
            random = Random(1),
        )
        assertThat(applied).isTrue()
        assertThat(target.getHealth()).isEqualTo(1000)
        assertThat(target.getMana()).isEqualTo(100)
    }

    @Test
    fun `doCombatHealth applies damage only through the blockedByArmor gate`() {
        // combat.cpp:1691-1697 -> game.cpp:7908-7939 passes
        // params.blockedByShield/blockedByArmor to Game::combatBlockHit.
        val ungated = FakeCombatTarget(health = 1000)
        ungated.armorValue = 2000
        Combat.doCombatHealth(
            caster = null,
            target = ungated,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )
        assertThat(ungated.getHealth()).isEqualTo(900)

        val gated = FakeCombatTarget(health = 1000)
        gated.armorValue = 20
        Combat.doCombatHealth(
            caster = null,
            target = gated,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE, blockedByArmor = true),
            random = Random(1),
        )
        // armor 20 -> uniform_random(10, 19) reduction -> 81..90 damage
        assertThat(gated.getHealth()).isBetween(910, 919)
    }

    @Test
    fun `doCombatHealth applies defense only through the blockedByShield gate`() {
        val ungated = FakeCombatTarget(health = 1000)
        ungated.defenseValue = 2000
        Combat.doCombatHealth(
            caster = null,
            target = ungated,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )
        assertThat(ungated.getHealth()).isEqualTo(900)

        val gated = FakeCombatTarget(health = 1000)
        gated.defenseValue = 50
        Combat.doCombatHealth(
            caster = null,
            target = gated,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE, blockedByShield = true),
            random = Random(1),
        )
        // defense 50 -> uniform_random(25, 50) reduction -> 50..75 damage
        assertThat(gated.getHealth()).isBetween(925, 950)
    }

    @Test
    fun `doCombatHealth fully blocks when defense absorbs the hit`() {
        // src/creatures/creature.cpp:966-974 -> BLOCK_DEFENSE, armor skipped
        val target = FakeCombatTarget(health = 1000)
        target.defenseValue = 1000
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -50),
            params = CombatParams(
                combatType = CombatType.PHYSICALDAMAGE,
                blockedByShield = true,
                blockedByArmor = true,
            ),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(1000)
        assertThat(target.drainedHealth).isEmpty()
    }

    @Test
    fun `doCombatHealth fully blocks when small armor reduces the hit to zero`() {
        // src/creatures/creature.cpp:984-988 -> BLOCK_ARMOR
        val target = FakeCombatTarget(health = 1000)
        target.armorValue = 3
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -1),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE, blockedByArmor = true),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(1000)
        assertThat(target.drainedHealth).isEmpty()
    }

    @Test
    fun `doCombatHealth zeroes the damage against an immune target`() {
        // src/creatures/creature.cpp:955-957 -> BLOCK_IMMUNITY
        val target = FakeCombatTarget(health = 1000)
        target.immuneTypes.add(CombatType.PHYSICALDAMAGE)
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE, blockedByArmor = true, blockedByShield = true),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(1000)
        assertThat(target.drainedHealth).isEmpty()
    }

    @Test
    fun `doCombatHealth applies mitigation after blocking`() {
        // src/creatures/creature.cpp:995-997 `if (damage != 0) mitigateDamage`
        val target = FakeCombatTarget(health = 1000)
        target.mitigationValue = 20.0
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(920)
        assertThat(target.drainedHealth).containsExactly(80)
    }

    @Test
    fun `doCombatHealth lets the secondary element bypass shield and armor`() {
        // game.cpp:8009 `blockHit(..., false, false, field)` for the secondary
        val target = FakeCombatTarget(health = 1000)
        target.armorValue = 50
        target.defenseValue = 1000
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(secondaryType = CombatType.FIREDAMAGE, secondaryValue = -100),
            params = CombatParams(
                combatType = CombatType.FIREDAMAGE,
                blockedByShield = true,
                blockedByArmor = true,
            ),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(900)
    }

    @Test
    fun `doCombatHealth lets agony damage bypass block and mitigation`() {
        // game.cpp:7916-7918 `primary.value > 0 || type == COMBAT_AGONYDAMAGE`
        // returns before Game::combatBlockHit
        val target = FakeCombatTarget(health = 1000)
        target.armorValue = 50
        target.mitigationValue = 50.0
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.AGONYDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.AGONYDAMAGE, blockedByArmor = true),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(900)
        assertThat(target.drainedHealth).containsExactly(100)
    }

    @Test
    fun `doCombatHealth skips damage against an unattackable target`() {
        // game.cpp:8387-8395
        val target = FakeCombatTarget(health = 1000)
        target.attackable = false
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(1000)
        assertThat(target.drainedHealth).isEmpty()
    }

    @Test
    fun `doCombatHealth caps the drained health at the target's current health`() {
        // game.cpp:8722 `realDamage = min(targetHealth, primary + secondary)`
        val target = FakeCombatTarget(health = 30, maxHealth = 1000)
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.PHYSICALDAMAGE, primaryValue = -100),
            params = CombatParams(combatType = CombatType.PHYSICALDAMAGE),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(0)
        assertThat(target.drainedHealth).containsExactly(30)
    }

    @Test
    fun `doCombatHealth heals a living target up to its maximum health`() {
        // game.cpp:8347-8350, 8412 `target->gainHealth`
        val target = FakeCombatTarget(health = 900, maxHealth = 1000)
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.HEALING, primaryValue = 200),
            params = CombatParams(combatType = CombatType.HEALING, blockedByArmor = true),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(1000)
        assertThat(target.gainedHealth).containsExactly(200)
        assertThat(target.drainedHealth).isEmpty()
    }

    @Test
    fun `doCombatHealth refuses to heal a dead target`() {
        // game.cpp:8348-8350 `if (target->getHealth() <= 0) return false`
        val target = FakeCombatTarget(health = 0, maxHealth = 1000)
        Combat.doCombatHealth(
            caster = null,
            target = target,
            damage = CombatDamage(primaryType = CombatType.HEALING, primaryValue = 50),
            params = CombatParams(combatType = CombatType.HEALING),
            random = Random(1),
        )
        assertThat(target.getHealth()).isEqualTo(0)
        assertThat(target.gainedHealth).isEmpty()
    }

    @Test
    fun `doCombatMana drains and restores mana with the changeMana clamp`() {
        // src/game/game.cpp:9063-9072 -> Creature::changeMana (creature.cpp:869-875)
        val drained = FakeCombatTarget(mana = 100)
        Combat.doCombatMana(
            caster = null,
            target = drained,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = -40),
            params = CombatParams(combatType = CombatType.MANADRAIN),
            random = Random(1),
        )
        assertThat(drained.getMana()).isEqualTo(60)

        val restored = FakeCombatTarget(mana = 100)
        Combat.doCombatMana(
            caster = null,
            target = restored,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = 40),
            params = CombatParams(combatType = CombatType.MANADRAIN, aggressive = false),
            random = Random(1),
        )
        assertThat(restored.getMana()).isEqualTo(140)

        val empty = FakeCombatTarget(mana = 10)
        Combat.doCombatMana(
            caster = null,
            target = empty,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = -50),
            params = CombatParams(combatType = CombatType.MANADRAIN),
            random = Random(1),
        )
        assertThat(empty.getMana()).isEqualTo(0)

        val full = FakeCombatTarget(mana = 490, maxMana = 500)
        Combat.doCombatMana(
            caster = null,
            target = full,
            damage = CombatDamage(primaryType = CombatType.MANADRAIN, primaryValue = 50),
            params = CombatParams(combatType = CombatType.MANADRAIN, aggressive = false),
            random = Random(1),
        )
        assertThat(full.getMana()).isEqualTo(500)
    }

    @Test
    fun `doTargetCombatMana ports the lua wrapper drain restore and aggressiveness`() {
        // global_functions.cpp:429-464
        val drained = FakeCombatTarget(mana = 100)
        val drainParams = CombatParams()
        GlobalFunctions.doTargetCombatMana(
            caster = null,
            target = drained,
            min = -30,
            max = -30,
            params = drainParams,
            random = Random(1),
        )
        assertThat(drainParams.aggressive).isTrue()
        assertThat(drained.getMana()).isEqualTo(70)

        val restored = FakeCombatTarget(mana = 100)
        val restoreParams = CombatParams()
        GlobalFunctions.doTargetCombatMana(
            caster = null,
            target = restored,
            min = 25,
            max = 25,
            params = restoreParams,
            random = Random(1),
        )
        assertThat(restoreParams.aggressive).isFalse()
        assertThat(restored.getMana()).isEqualTo(125)
    }

    @Test
    fun `doTargetCombatMana blocks self-targeted aggressive drains`() {
        // combat.cpp:1723-1724 `canCombat = !aggressive || caster != target`
        val self = FakeCombatTarget(mana = 100)
        GlobalFunctions.doTargetCombatMana(
            caster = self,
            target = self,
            min = -30,
            max = -30,
            params = CombatParams(),
            random = Random(1),
        )
        assertThat(self.getMana()).isEqualTo(100)
    }

    @Test
    fun `doTargetCombatHealth ports the lua wrapper damage build and healing tag`() {
        // global_functions.cpp:348-397
        val hurt = FakeCombatTarget(health = 1000)
        val hurtParams = CombatParams()
        GlobalFunctions.doTargetCombatHealth(
            caster = null,
            target = hurt,
            combatType = CombatType.PHYSICALDAMAGE,
            min = -60,
            max = -60,
            params = hurtParams,
            random = Random(1),
        )
        assertThat(hurtParams.combatType).isEqualTo(CombatType.PHYSICALDAMAGE)
        assertThat(hurt.getHealth()).isEqualTo(940)

        // Negative manadrain routed through the *health* wrapper still drains
        // health: the Lua binding always calls Combat::doCombatHealth.
        val drained = FakeCombatTarget(health = 1000, mana = 100)
        GlobalFunctions.doTargetCombatHealth(
            caster = null,
            target = drained,
            combatType = CombatType.MANADRAIN,
            min = -40,
            max = -40,
            params = CombatParams(),
            random = Random(1),
        )
        assertThat(drained.getHealth()).isEqualTo(960)
        assertThat(drained.getMana()).isEqualTo(100)

        // Positive manadrain becomes non-aggressive and heals health.
        val drainHeal = FakeCombatTarget(health = 1000, maxHealth = 1100, mana = 100)
        val drainHealParams = CombatParams()
        GlobalFunctions.doTargetCombatHealth(
            caster = null,
            target = drainHeal,
            combatType = CombatType.MANADRAIN,
            min = 40,
            max = 40,
            params = drainHealParams,
            random = Random(1),
        )
        assertThat(drainHealParams.aggressive).isFalse()
        assertThat(drainHeal.getHealth()).isEqualTo(1040)
        assertThat(drainHeal.getMana()).isEqualTo(100)

        // COMBAT_HEALING becomes non-aggressive.
        val healer = FakeCombatTarget(health = 500)
        val healParams = CombatParams()
        GlobalFunctions.doTargetCombatHealth(
            caster = null,
            target = healer,
            combatType = CombatType.HEALING,
            min = 30,
            max = 30,
            params = healParams,
            random = Random(1),
        )
        assertThat(healParams.aggressive).isFalse()
        assertThat(healer.getHealth()).isEqualTo(530)
    }
}
