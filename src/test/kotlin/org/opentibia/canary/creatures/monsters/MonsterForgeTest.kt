package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.CreatureIcon
import org.opentibia.canary.creatures.CreatureIconModifications_t
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.map.Position

/**
 * Transcribes the Canary forge/hazard/soul-pit monster surface:
 * - `Monster::applyStacks` (src/creatures/monsters/monster.cpp:3857-3864)
 * - `Monster::configureForgeSystem` (src/creatures/monsters/monster.cpp:3866-3889)
 * - `Monster::canBeForgeMonster` (src/creatures/monsters/monster.cpp:3891-3893)
 * - `Monster::isForgeCreature`/`Monster::setForgeMonster` (monster.cpp:3895-3901)
 * - `Monster::getForgeStack`/`Monster::setForgeStack` (monster.cpp:3903-3909)
 * - `Monster::getMonsterForgeClassification`/`setMonsterForgeClassification`
 *   (monster.cpp:3911-3918)
 * - `Monster::setTimeToChangeFiendish`/`getTimeToChangeFiendish` (monster.cpp:3920-3926)
 * - `Monster::clearFiendishStatus` (monster.cpp:3932-3943)
 * - `Monster::canDropLoot`/`Monster::getRaceId` (monster.cpp:3945-3947, 3144-3146)
 * - `Monster::setSoulPitStack` (monster.cpp:3201-3208) and the hazard accessors
 *   (monster.cpp:3148-3198)
 *
 * The icon/event/spectator side effects that Canary reaches through `g_game()`
 * and the Lua event registry are injected seams, so this suite asserts the
 * state mutations plus the observed seam calls.
 */
class MonsterForgeTest {

    private val pos = Position(100, 100, 7)

    private fun monster(id: Long = 1, maxHealth: Int = 100, health: Int = maxHealth) =
        Monster(id, "Demon", maxHealth, health, position = pos)

    private fun type(
        raceId: Int = 1,
        forgeCreature: Boolean = true,
        rewardBoss: Boolean = false,
        loot: Boolean = true,
    ) = MonsterType("Demon").apply {
        info.raceid = raceId
        info.isForgeCreature = forgeCreature
        info.isRewardBoss = rewardBoss
        if (loot) {
            info.lootItems += LootBlock(id = 1, chance = 1)
        }
    }

    // --- Monster::canBeForgeMonster ---

    @Test
    fun `canBeForgeMonster requires a fresh stack, non-summon, loot, forge creature and race id`() {
        val state = MonsterTypeState(type())

        assertThat(MonsterForge.canBeForgeMonster(state, isSummon = false)).isTrue()
        assertThat(MonsterForge.canBeForgeMonster(state, isSummon = true)).isFalse()

        state.forgeStack = 1
        assertThat(MonsterForge.canBeForgeMonster(state, isSummon = false)).isFalse()

        assertThat(MonsterForge.canBeForgeMonster(MonsterTypeState(type(loot = false)), isSummon = false)).isFalse()
        assertThat(MonsterForge.canBeForgeMonster(MonsterTypeState(type(forgeCreature = false)), isSummon = false)).isFalse()
        assertThat(MonsterForge.canBeForgeMonster(MonsterTypeState(type(rewardBoss = true)), isSummon = false)).isFalse()
        assertThat(MonsterForge.canBeForgeMonster(MonsterTypeState(type(raceId = 0)), isSummon = false)).isFalse()
        assertThat(MonsterForge.canBeForgeMonster(MonsterTypeState(null), isSummon = false)).isFalse()
    }

    // --- Monster::getForgeStack / setForgeStack / applyStacks ---

    @Test
    fun `setForgeStack applies the stack health multiplier to health and max health`() {
        val state = MonsterTypeState(type())

        fun applyTo(stack: Int, maxHealth: Int = 100): Monster {
            val unit = monster(maxHealth = maxHealth)
            MonsterForge.setForgeStack(state, unit, stack)
            return unit
        }

        val base = applyTo(0)
        assertThat(base.health).isEqualTo(135)
        assertThat(base.maxHealth).isEqualTo(135)
        assertThat(applyTo(1).maxHealth).isEqualTo(150)
        assertThat(applyTo(2).maxHealth).isEqualTo(165)
        assertThat(applyTo(3).maxHealth).isEqualTo(180)
        assertThat(applyTo(15).maxHealth).isEqualTo(360)

        assertThat(applyTo(0, maxHealth = 97).health).isEqualTo(131)
        assertThat(applyTo(1, maxHealth = 1000).health).isEqualTo(1500)
        assertThat(applyTo(15, maxHealth = 1000).health).isEqualTo(3600)
        assertThat(applyTo(0, maxHealth = 1).health).isEqualTo(2)

        assertThat(MonsterForge.getForgeStack(state)).isEqualTo(0)
    }

    // --- Monster::configureForgeSystem ---

    @Test
    fun `configureForgeSystem no ops when the monster cannot be forged`() {
        val state = MonsterTypeState(type(rewardBoss = true))
        MonsterForge.setMonsterForgeClassification(state, ForgeClassification.Fiendish)
        val unit = monster()
        val trace = mutableListOf<String>()

        MonsterForge.configureForgeSystem(
            state = state,
            monster = unit,
            stack = 0,
            isSummon = false,
            updateCreatureIcon = { trace += "icon" },
            registerCreatureEvent = { trace += "event:$it" },
            sendUpdateCreature = { trace += "update" },
        )

        assertThat(state.forgeStack).isZero()
        assertThat(unit.maxHealth).isEqualTo(100)
        assertThat(unit.getIcons()).isEmpty()
        assertThat(trace).isEmpty()
    }

    @Test
    fun `configureForgeSystem fiendish sets fifteen stacks, the icon and the dust event`() {
        val state = MonsterTypeState(type())
        MonsterForge.setMonsterForgeClassification(state, ForgeClassification.Fiendish)
        val unit = monster()
        val trace = mutableListOf<String>()

        MonsterForge.configureForgeSystem(
            state = state,
            monster = unit,
            stack = 0,
            isSummon = false,
            updateCreatureIcon = { trace += "icon" },
            registerCreatureEvent = { trace += "event:$it" },
            sendUpdateCreature = { trace += "update" },
        )

        assertThat(state.forgeStack).isEqualTo(15)
        assertThat(unit.health).isEqualTo(360)
        assertThat(unit.maxHealth).isEqualTo(360)
        assertThat(unit.getIcon(MonsterForge.ICON_KEY_FORGE))
            .isEqualTo(CreatureIcon(CreatureIconModifications_t.Fiendish, 0))
        assertThat(trace).containsExactly("icon", "event:ForgeSystemMonster", "update")
    }

    @Test
    fun `configureForgeSystem influenced draws a stack when none was given`() {
        val state = MonsterTypeState(type())
        MonsterForge.setMonsterForgeClassification(state, ForgeClassification.Influenced)
        val unit = monster()
        val draws = mutableListOf<Pair<Int, Int>>()

        MonsterForge.configureForgeSystem(
            state = state,
            monster = unit,
            stack = 0,
            isSummon = false,
            normalRandom = { min, max ->
                draws += min to max
                3
            },
        )

        assertThat(draws).containsExactly(1 to 5)
        assertThat(state.forgeStack).isEqualTo(3)
        assertThat(unit.health).isEqualTo(180)
        assertThat(unit.getIcon(MonsterForge.ICON_KEY_FORGE))
            .isEqualTo(CreatureIcon(CreatureIconModifications_t.Influenced, 3))
    }

    @Test
    fun `configureForgeSystem influenced keeps an explicit stack`() {
        val state = MonsterTypeState(type())
        MonsterForge.setMonsterForgeClassification(state, ForgeClassification.Influenced)
        val unit = monster()

        MonsterForge.configureForgeSystem(
            state = state,
            monster = unit,
            stack = 5,
            isSummon = false,
            normalRandom = { _, _ -> error("an explicit stack must not draw") },
        )

        assertThat(state.forgeStack).isEqualTo(5)
        assertThat(unit.health).isEqualTo(210)
        assertThat(unit.getIcon(MonsterForge.ICON_KEY_FORGE))
            .isEqualTo(CreatureIcon(CreatureIconModifications_t.Influenced, 5))
    }

    @Test
    fun `configureForgeSystem for a normal classification only registers the event`() {
        val state = MonsterTypeState(type())
        val unit = monster()
        val trace = mutableListOf<String>()

        MonsterForge.configureForgeSystem(
            state = state,
            monster = unit,
            stack = 0,
            isSummon = false,
            updateCreatureIcon = { trace += "icon" },
            registerCreatureEvent = { trace += "event:$it" },
            sendUpdateCreature = { trace += "update" },
        )

        assertThat(state.forgeStack).isZero()
        assertThat(unit.health).isEqualTo(100)
        assertThat(unit.getIcons()).isEmpty()
        assertThat(trace).containsExactly("event:ForgeSystemMonster", "update")
    }

    // --- Monster::setTimeToChangeFiendish / getMonsterForgeClassification ---

    @Test
    fun `fiendish change time and forge classification round trip`() {
        val state = MonsterTypeState(type())

        assertThat(MonsterForge.getTimeToChangeFiendish(state)).isZero()
        assertThat(MonsterForge.getMonsterForgeClassification(state)).isEqualTo(ForgeClassification.Normal)

        MonsterForge.setTimeToChangeFiendish(state, 1_700_000_000L)
        MonsterForge.setMonsterForgeClassification(state, ForgeClassification.Fiendish)

        assertThat(MonsterForge.getTimeToChangeFiendish(state)).isEqualTo(1_700_000_000L)
        assertThat(MonsterForge.getMonsterForgeClassification(state)).isEqualTo(ForgeClassification.Fiendish)
    }

    // --- Monster::clearFiendishStatus ---

    @Test
    fun `clearFiendishStatus restores the type health and drops the forge latch`() {
        val state = MonsterTypeState(type())
        val unit = monster()
        MonsterForge.setMonsterForgeClassification(state, ForgeClassification.Fiendish)
        MonsterForge.setForgeStack(state, unit, 15)
        MonsterForge.setTimeToChangeFiendish(state, 1_700_000_000L)
        unit.setIcon(MonsterForge.ICON_KEY_FORGE, CreatureIcon(CreatureIconModifications_t.Fiendish, 0))
        val trace = mutableListOf<String>()

        MonsterForge.clearFiendishStatus(
            state = state,
            monster = unit,
            healthMultiplier = 2.0,
            updateCreatureIcon = { trace += "icon" },
            sendUpdateCreature = { trace += "update" },
        )

        assertThat(MonsterForge.getTimeToChangeFiendish(state)).isZero()
        assertThat(MonsterForge.getForgeStack(state)).isZero()
        assertThat(MonsterForge.getMonsterForgeClassification(state)).isEqualTo(ForgeClassification.Normal)
        assertThat(unit.health).isEqualTo(200)
        assertThat(unit.maxHealth).isEqualTo(200)
        assertThat(unit.hasIcon(MonsterForge.ICON_KEY_FORGE)).isFalse()
        assertThat(trace).containsExactly("icon", "update")

        MonsterForge.clearFiendishStatus(state, unit)
        assertThat(unit.health).isEqualTo(100)
        assertThat(unit.maxHealth).isEqualTo(100)
    }

    // --- Monster::isForgeCreature / setForgeMonster / canDropLoot / getRaceId ---

    @Test
    fun `forge creature flag, loot table and race id read the monster type`() {
        val state = MonsterTypeState(type())

        assertThat(MonsterForge.isForgeCreature(state)).isTrue()
        assertThat(MonsterForge.canDropLoot(state)).isTrue()
        assertThat(MonsterForge.getRaceId(state)).isEqualTo(1)

        MonsterForge.setForgeMonster(state, false)
        assertThat(state.monsterType?.info?.isForgeCreature).isFalse()
        assertThat(MonsterForge.isForgeCreature(state)).isFalse()
    }

    @Test
    fun `a monster without a type reports no forge support`() {
        val state = MonsterTypeState(null)

        assertThat(MonsterForge.isForgeCreature(state)).isFalse()
        assertThat(MonsterForge.canDropLoot(state)).isFalse()
        assertThat(MonsterForge.getRaceId(state)).isZero()
    }

    // --- Monster::setSoulPitStack ---

    @Test
    fun `setSoulPitStack applies the reduced-health icon and drops loot for a non boss stack`() {
        val state = MonsterTypeState(type())
        val unit = monster()
        val dropLoot = mutableListOf<Boolean>()
        val skillLoss = mutableListOf<Boolean>()

        MonsterForge.setSoulPitStack(
            state = state,
            monster = unit,
            stack = 3,
            isSummon = false,
            setDropLoot = { dropLoot += it },
            setSkillLoss = { skillLoss += it },
        )

        assertThat(state.soulPit).isTrue()
        assertThat(state.forgeStack).isEqualTo(3)
        assertThat(unit.health).isEqualTo(180)
        assertThat(unit.maxHealth).isEqualTo(180)
        assertThat(unit.getIcon(MonsterForge.ICON_KEY_SOUL_PIT))
            .isEqualTo(CreatureIcon(CreatureIconModifications_t.ReducedHealth, 3))
        assertThat(dropLoot).containsExactly(false)
        assertThat(skillLoss).containsExactly(false)
    }

    @Test
    fun `setSoulPitStack for the boss stack uses the exclamation icon without skill loss for summons`() {
        val state = MonsterTypeState(type())
        val unit = monster(maxHealth = 1000)
        val skillLoss = mutableListOf<Boolean>()

        MonsterForge.setSoulPitStack(
            state = state,
            monster = unit,
            stack = 40,
            isSummon = true,
            setSkillLoss = { skillLoss += it },
        )

        assertThat(state.forgeStack).isEqualTo(40)
        assertThat(unit.getIcon(MonsterForge.ICON_KEY_SOUL_PIT))
            .isEqualTo(CreatureIcon(CreatureIconModifications_t.ReducedHealthExclamation, 0))
        assertThat(skillLoss).containsExactly(false)
    }

    @Test
    fun `setSoulPitStack for the boss stack keeps skill loss for the owner`() {
        val state = MonsterTypeState(type())
        val unit = monster()
        val skillLoss = mutableListOf<Boolean>()

        MonsterForge.setSoulPitStack(
            state = state,
            monster = unit,
            stack = MonsterForge.SOUL_PIT_BOSS_STACK,
            isSummon = false,
            setSkillLoss = { skillLoss += it },
        )

        assertThat(skillLoss).containsExactly(true)
    }

    // --- hazard accessors ---

    @Test
    fun `hazard latches round trip through the monster state`() {
        val state = MonsterTypeState(type())

        assertThat(MonsterForge.getHazard(state)).isFalse()
        assertThat(MonsterForge.getHazardSystemCrit(state)).isFalse()
        assertThat(MonsterForge.getHazardSystemDodge(state)).isFalse()
        assertThat(MonsterForge.getHazardSystemDamageBoost(state)).isFalse()
        assertThat(MonsterForge.getHazardSystemDefenseBoost(state)).isFalse()
        assertThat(MonsterForge.getSoulPit(state)).isFalse()

        MonsterForge.setHazard(state, true)
        MonsterForge.setHazardSystemCrit(state, true)
        MonsterForge.setHazardSystemDodge(state, true)
        MonsterForge.setHazardSystemDamageBoost(state, true)
        MonsterForge.setHazardSystemDefenseBoost(state, true)
        MonsterForge.setSoulPit(state, true)

        assertThat(MonsterForge.getHazard(state)).isTrue()
        assertThat(MonsterForge.getHazardSystemCrit(state)).isTrue()
        assertThat(MonsterForge.getHazardSystemDodge(state)).isTrue()
        assertThat(MonsterForge.getHazardSystemDamageBoost(state)).isTrue()
        assertThat(MonsterForge.getHazardSystemDefenseBoost(state)).isTrue()
        assertThat(MonsterForge.getSoulPit(state)).isTrue()
    }
}
