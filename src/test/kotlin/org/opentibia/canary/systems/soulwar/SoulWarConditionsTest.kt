package org.opentibia.canary.systems.soulwar

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the Canary engine core of the Soul War / Goshnar's Taint
 * subsystem. Values are transcribed from the Canary C++ sources:
 *
 * - `src/creatures/creatures_definitions.hpp` (condition types / params)
 * - `src/enums/player_icons.hpp` (taint / fear icons)
 * - `src/creatures/combat/condition.cpp` (Condition base, ConditionGeneric, ConditionFeared)
 * - `src/creatures/combat/combat.cpp` (Combat::checkFearConditionAffected)
 * - `src/creatures/players/player.cpp` (setImmuneFear / isImmuneFear / cleanse)
 * - `src/creatures/monsters/monsters.cpp` (fear spell default duration)
 */
class SoulWarConditionsTest {

    private var clock = 1_000_000L

    private fun state(id: Long = 1): PlayerConditionState =
        PlayerConditionState(id = id, now = { clock })

    // --- Goshnar's Taint condition (ConditionGeneric) -------------------------

    @Test
    fun `taint condition maps each subId 1 to 5 to its amplification icon`() {
        // ConditionGeneric::getIcons (condition.cpp:624-643).
        val expected = mapOf(
            1 to PlayerIcon.GoshnarTaint1,
            2 to PlayerIcon.GoshnarTaint2,
            3 to PlayerIcon.GoshnarTaint3,
            4 to PlayerIcon.GoshnarTaint4,
            5 to PlayerIcon.GoshnarTaint5,
        )
        for ((level, icon) in expected) {
            val taint = Condition.createCondition(
                id = ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_GOSHNARTAINT,
                ticks = 14 * 24 * 60 * 60 * 1000,
                subId = level,
            )
            assertThat(taint).isNotNull()
            assertThat(taint!!.getIcons()).containsExactly(icon)
        }
    }

    @Test
    fun `taint condition without a known subId contributes no taint icon`() {
        val taint = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_GOSHNARTAINT,
            ticks = 1000,
            subId = 0,
        )
        assertThat(taint!!.getIcons()).isEmpty()
    }

    @Test
    fun `taint is not removable on death`() {
        // Condition::isRemovableOnDeath (condition.cpp:491-512) lists CONDITION_GOSHNARTAINT.
        val taint = Condition.createCondition(
            id = ConditionId.CONDITIONID_DEFAULT,
            type = ConditionType.CONDITION_GOSHNARTAINT,
            ticks = 1000,
            subId = 1,
        )
        assertThat(taint!!.isRemovableOnDeath()).isFalse()
    }

    @Test
    fun `re-adding a longer taint extends the existing stacking duration`() {
        // Creature::addCondition finds the same (type,id,subId) and calls
        // ConditionGeneric::addCondition (condition.cpp:578-586), which updates
        // when Condition::updateCondition (condition.cpp:543-557) returns true.
        val holder = state()
        val first = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_GOSHNARTAINT, 10_000, subId = 1, now = { clock },
        )!!
        holder.addCondition(first)

        val longer = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_GOSHNARTAINT, 20_000, subId = 1, now = { clock },
        )!!
        holder.addCondition(longer)

        val stored = holder.getCondition(ConditionType.CONDITION_GOSHNARTAINT, ConditionId.CONDITIONID_DEFAULT, 1)!!
        assertThat(stored.ticks).isEqualTo(20_000)
        assertThat(stored).isSameAs(first)
    }

    @Test
    fun `re-adding a shorter taint never shortens the existing stacking duration`() {
        val holder = state()
        val first = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_GOSHNARTAINT, 20_000, subId = 1, now = { clock },
        )!!
        holder.addCondition(first)

        val shorter = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_GOSHNARTAINT, 5_000, subId = 1, now = { clock },
        )!!
        holder.addCondition(shorter)

        assertThat(holder.getCondition(ConditionType.CONDITION_GOSHNARTAINT, ConditionId.CONDITIONID_DEFAULT, 1)!!.ticks)
            .isEqualTo(20_000)
    }

    @Test
    fun `taint conditions of different subIds are independent`() {
        // Creature::getCondition keys on (type, id, subId) (creature.cpp:1513-1525).
        val holder = state()
        holder.addCondition(
            Condition.createCondition(
                ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_GOSHNARTAINT, 1000, subId = 1, now = { clock },
            )!!,
        )
        holder.addCondition(
            Condition.createCondition(
                ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_GOSHNARTAINT, 1000, subId = 2, now = { clock },
            )!!,
        )

        assertThat(holder.getCondition(ConditionType.CONDITION_GOSHNARTAINT, ConditionId.CONDITIONID_DEFAULT, 1)).isNotNull()
        assertThat(holder.getCondition(ConditionType.CONDITION_GOSHNARTAINT, ConditionId.CONDITIONID_DEFAULT, 2)).isNotNull()
        assertThat(holder.hasCondition(ConditionType.CONDITION_GOSHNARTAINT, 1)).isTrue()
        assertThat(holder.hasCondition(ConditionType.CONDITION_GOSHNARTAINT, 3)).isFalse()
    }

    // --- ConditionFeared application / duration / immunity --------------------

    @Test
    fun `fear spell condition defaults to 6000ms combat condition`() {
        // Monsters::deserializeSpell "soulwars fear"/"fear" (monsters.cpp:199-207).
        val fear = Condition.createCondition(
            id = ConditionId.CONDITIONID_COMBAT,
            type = ConditionType.CONDITION_FEARED,
            ticks = 6000,
        )
        assertThat(fear).isInstanceOf(ConditionFeared::class.java)
        assertThat(fear!!.type).isEqualTo(ConditionType.CONDITION_FEARED)
        assertThat(fear.id).isEqualTo(ConditionId.CONDITIONID_COMBAT)
        assertThat(fear.ticks).isEqualTo(6000)
    }

    @Test
    fun `condition feared exposes the feared player icon`() {
        // ConditionFeared::getIcons (condition.cpp:2446-2451).
        val fear = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FEARED, 6000,
        )!!
        assertThat(fear.getIcons()).contains(PlayerIcon.Feared)
    }

    @Test
    fun `ending fear grants the default 10 second immunity`() {
        // ConditionFeared::endCondition -> Player::setImmuneFear() (condition.cpp:2429-2438,
        // player.cpp:1930-1938, default immuneTime 10000).
        val holder = state()
        val fear = Condition.createCondition(
            ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FEARED, 6000, now = { clock },
        )!!
        holder.addCondition(fear)
        assertThat(holder.isImmuneFear()).isFalse()

        holder.removeCondition(fear)
        assertThat(holder.isImmuneFear()).isTrue()

        clock += 10_000
        assertThat(holder.isImmuneFear()).isTrue()

        clock += 1
        assertThat(holder.isImmuneFear()).isFalse()
    }

    @Test
    fun `cleanse immunity for fear uses 11 seconds`() {
        // Player::setImmuneCleanse (player.cpp:1897-1911) maps CONDITION_FEARED to
        // setImmuneFear(11000).
        val holder = state()
        holder.setImmuneCleanse(ConditionType.CONDITION_FEARED)
        assertThat(holder.isImmuneFear()).isTrue()

        clock += 11_000
        assertThat(holder.isImmuneFear()).isTrue()

        clock += 1
        assertThat(holder.isImmuneFear()).isFalse()
    }

    @Test
    fun `isImmuneCleanse for fear delegates to the fear immunity`() {
        // Player::isImmuneCleanse (player.cpp:1913-1928).
        val holder = state()
        holder.setImmuneFear(5000)
        assertThat(holder.isImmuneCleanse(ConditionType.CONDITION_FEARED)).isTrue()
        clock += 5001
        assertThat(holder.isImmuneCleanse(ConditionType.CONDITION_FEARED)).isFalse()
    }

    // --- Combat::checkFearConditionAffected -----------------------------------

    @Test
    fun `checkFearConditionAffected returns false while the player is immune`() {
        // combat.cpp:1003-1006.
        val player = state()
        player.setImmuneFear()
        assertThat(Combat.checkFearConditionAffected(player)).isFalse()
    }

    @Test
    fun `checkFearConditionAffected returns false when already feared`() {
        // combat.cpp:1008-1010.
        val player = state()
        player.addCondition(
            Condition.createCondition(
                ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FEARED, 6000, now = { clock },
            )!!,
        )
        assertThat(Combat.checkFearConditionAffected(player)).isFalse()
    }

    @Test
    fun `checkFearConditionAffected returns true for a solo eligible player`() {
        // combat.cpp:1012-1028 (no party).
        val player = state()
        assertThat(Combat.checkFearConditionAffected(player)).isTrue()
    }

    @Test
    fun `checkFearConditionAffected limits feared members to party member count over five`() {
        // combat.cpp:1012-1026:
        //   affectedCount = (getMemberCount() + 5) / 5
        // With 5 members affectedCount = 2; two feared members exhaust the budget.
        val leader = state(id = 10)
        val feared = state(id = 11)

        feared.addCondition(
            Condition.createCondition(
                ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FEARED, 6000, now = { clock },
            )!!,
        )

        val party = SoulWarParty(
            members = mutableListOf(feared, state(id = 12), state(id = 13), state(id = 14), state(id = 15)),
        )
        leader.party = party

        // memberCount = 5 -> affectedCount = 2; feared = 1 -> 1 -> allowed.
        assertThat(Combat.checkFearConditionAffected(leader)).isTrue()
    }

    @Test
    fun `checkFearConditionAffected denies a fire when the party fear budget is exhausted`() {
        // combat.cpp:1012-1026: with 5 members the budget is 2; two feared members -> 0.
        val leader = state(id = 20)
        val fearedOne = state(id = 21)
        val fearedTwo = state(id = 22)

        listOf(fearedOne, fearedTwo).forEach { m ->
            m.addCondition(
                Condition.createCondition(
                    ConditionId.CONDITIONID_COMBAT, ConditionType.CONDITION_FEARED, 6000, now = { clock },
                )!!,
            )
        }

        val party = SoulWarParty(members = mutableListOf(fearedOne, fearedTwo, state(23), state(24), state(25)))
        leader.party = party

        assertThat(Combat.checkFearConditionAffected(leader)).isFalse()
    }

    // --- ConditionFeared::getFleeDirection region state machine ----------------

    @Test
    fun `getFleeDirection selects the correct region start index`() {
        val caster = Position(100, 100, 7)

        assertThat(fleeIndex(caster, Position(105, 100, 7))).isEqualTo(2) // SW, offy == 0 -> East
        assertThat(fleeIndex(caster, Position(105, 98, 7))).isEqualTo(0)  // SW, offy != 0 -> North
        assertThat(fleeIndex(caster, Position(100, 105, 7))).isEqualTo(4) // NW, offx == 0 -> South
        assertThat(fleeIndex(caster, Position(102, 105, 7))).isEqualTo(2) // NW, offx != 0 -> East
        assertThat(fleeIndex(caster, Position(98, 100, 7))).isEqualTo(6)  // NE, offy == 0 -> West
        assertThat(fleeIndex(caster, Position(98, 105, 7))).isEqualTo(4)  // NE, offy != 0 -> South
        assertThat(fleeIndex(caster, Position(100, 98, 7))).isEqualTo(0)  // SE, offx == 0 -> North
        assertThat(fleeIndex(caster, Position(98, 98, 7))).isEqualTo(6)   // SE, offx != 0 -> West
    }

    @Test
    fun `getFleeDirection falls back to random walk on the caster tile`() {
        // Same SQM -> getRandomDirection; with no walkable tile seam every
        // direction is blocked, so it reports false and keeps fleeIndx 99.
        val caster = Position(100, 100, 7)
        val fear = ConditionFeared(
            id = ConditionId.CONDITIONID_COMBAT,
            ticks = 6000,
            now = { clock },
        )
        fear.setPositionParam(ConditionParam.CONDITION_PARAM_CASTER_POSITION, caster)

        assertThat(fear.getFleeDirection(caster)).isFalse()
        assertThat(fear.fleeIndx).isEqualTo(99)
    }

    @Test
    fun `setPositionParam only accepts the caster position param`() {
        // ConditionFeared::setPositionParam (condition.cpp:2385-2391).
        val fear = ConditionFeared(
            id = ConditionId.CONDITIONID_COMBAT,
            ticks = 6000,
            now = { clock },
        )
        val caster = Position(123, 456, 7)
        assertThat(fear.setPositionParam(ConditionParam.CONDITION_PARAM_CASTER_POSITION, caster)).isTrue()
        assertThat(fear.fleeingFromPos).isEqualTo(caster)
        assertThat(fear.setPositionParam(ConditionParam.CONDITION_PARAM_TICKS, caster)).isFalse()
    }

    private fun fleeIndex(caster: Position, creaturePos: Position): Int {
        val fear = ConditionFeared(
            id = ConditionId.CONDITIONID_COMBAT,
            ticks = 6000,
            now = { clock },
        )
        fear.setPositionParam(ConditionParam.CONDITION_PARAM_CASTER_POSITION, caster)
        fear.startCondition(creaturePos)
        return fear.fleeIndx
    }
}
