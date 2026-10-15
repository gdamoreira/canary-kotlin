package org.opentibia.canary.systems.wheel

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.Combat
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatFunction
import org.opentibia.canary.combat.CombatParams
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.FakeCombatActor
import org.opentibia.canary.combat.FakeCombatEffects
import org.opentibia.canary.combat.FakeCombatMap
import org.opentibia.canary.combat.FakeCombatPlayer
import org.opentibia.canary.combat.FakeCombatTile
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSpellCooldown
import org.opentibia.canary.combat.condition.ConditionSpellGroupCooldown
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.SpellCooldownClient
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.InstantSpell
import org.opentibia.canary.spells.Spells

/**
 * Transcript tests for the wheel beam-mastery host adapter:
 *
 *  - `PlayerWheel::checkBeamMasteryDamage` (player_wheel.cpp:3243-3255);
 *  - `PlayerWheel::getBeamAffectedTotal` (player_wheel.cpp:4037-4043);
 *  - `PlayerWheel::updateBeamMasteryDamage` (player_wheel.cpp:4045-4052) with
 *    the `reduceAllSpellsCooldownTimer(1000)` call (player_wheel.cpp:3423-3450);
 *  - the `m_beamMasterySpells` registration of `registerPlayerBonusData`
 *    (player_wheel.cpp:2262-2267);
 *  - the [WheelBeamCombatPlayer] host adapter binding the
 *    `CombatPlayer.getBeamAffectedTotal`/`updateBeamMasteryDamage` seam
 *    (combat.cpp:1579-1580, 1614-1617).
 */
class PlayerWheelBeamMasteryTest {

    private val center = Position(100, 100, 7)
    private val targetPos = Position(101, 100, 7)

    private class RecordingCooldownClient : SpellCooldownClient {
        val spellCooldowns = mutableListOf<Pair<Int, Int>>()
        val groupCooldowns = mutableListOf<Pair<SpellGroup, Int>>()

        override fun sendSpellCooldown(spellId: Int, time: Int) {
            spellCooldowns.add(spellId to time)
        }

        override fun sendSpellGroupCooldown(groupId: SpellGroup, time: Int) {
            groupCooldowns.add(groupId to time)
        }
    }

    private var clock = 2_000_000L

    private fun newPlayer() = Player(
        id = 1,
        name = "Beam Hero",
        maxHealth = 150,
        health = 150,
        position = center,
    ).apply { now = { clock } }

    /** `g_spells()` test registry with one beam spell carrying a secondary group. */
    private fun beamRegistry(): Spells = Spells().apply {
        registerInstantLuaEvent(
            InstantSpell().apply {
                spellId = 1300
                name = "Energy Beam"
                words = "exevo vis lux"
                group = SpellGroup.SPELLGROUP_ATTACK
                secondaryGroup = SpellGroup.SPELLGROUP_GREAT_BEAMS
            },
        )
    }

    private fun newWheel(
        player: Player = newPlayer(),
        spells: Spells = beamRegistry(),
    ): PlayerWheel = PlayerWheel(
        vocationCipId = IOWheel.VOCATION_SORCERER_CIP,
        level = 200,
        player = player,
        spells = spells,
        otsysTimeMs = { clock },
    )

    private fun tileFor(map: FakeCombatMap, position: Position): FakeCombatTile =
        map.getOrCreateTile(position) as FakeCombatTile

    private fun beamDamage(name: String = "Energy Beam") =
        CombatDamage(
            primaryType = CombatType.ENERGYDAMAGE,
            primaryValue = -100,
            instantSpellName = name,
            extension = true,
        )

    // --- getBeamAffectedTotal -------------------------------------------------

    @Test
    fun `getBeamAffectedTotal caps at three for every beam mastery spell`() {
        val wheel = newWheel()
        wheel.setStage(WheelStage.BEAM_MASTERY, 3)

        assertThat(wheel.getBeamAffectedTotal(beamDamage("Energy Beam"))).isEqualTo(3)
        assertThat(wheel.getBeamAffectedTotal(beamDamage("Great Death Beam"))).isEqualTo(3)
        assertThat(wheel.getBeamAffectedTotal(beamDamage("Great Energy Beam"))).isEqualTo(3)
    }

    @Test
    fun `getBeamAffectedTotal is zero for other spells and without the stage`() {
        val wheel = newWheel()
        wheel.setStage(WheelStage.BEAM_MASTERY, 3)
        assertThat(wheel.getBeamAffectedTotal(beamDamage("Fire Ball"))).isZero()

        val plain = newWheel()
        assertThat(plain.getBeamAffectedTotal(beamDamage("Energy Beam"))).isZero()
    }

    @Test
    fun `resetUpgradedSpells clears the beam mastery registration`() {
        val wheel = newWheel()
        wheel.setStage(WheelStage.BEAM_MASTERY, 3)
        assertThat(wheel.getBeamAffectedTotal(beamDamage())).isEqualTo(3)

        wheel.resetUpgradedSpells()

        assertThat(wheel.getBeamAffectedTotal(beamDamage())).isZero()
    }

    // --- checkBeamMasteryDamage / updateBeamMasteryDamage ---------------------

    @Test
    fun `checkBeamMasteryDamage scales with the stage`() {
        val wheel = newWheel()

        assertThat(wheel.checkBeamMasteryDamage()).isZero()
        wheel.setStage(WheelStage.BEAM_MASTERY, 1)
        assertThat(wheel.checkBeamMasteryDamage()).isEqualTo(10)
        wheel.setStage(WheelStage.BEAM_MASTERY, 2)
        assertThat(wheel.checkBeamMasteryDamage()).isEqualTo(12)
        wheel.setStage(WheelStage.BEAM_MASTERY, 3)
        assertThat(wheel.checkBeamMasteryDamage()).isEqualTo(14)
    }

    @Test
    fun `updateBeamMasteryDamage adds the stage multiplier while the counter lasts`() {
        val wheel = newWheel()
        wheel.setStage(WheelStage.BEAM_MASTERY, 2)

        val damage = beamDamage()
        val update = wheel.updateBeamMasteryDamage(damage, beamAffectedTotal = 3, beamAffectedCurrent = 0)

        assertThat(damage.damageMultiplier).isEqualTo(12)
        assertThat(update).isEqualTo(2 to 1)
    }

    @Test
    fun `an exhausted counter leaves the damage untouched`() {
        val wheel = newWheel()
        wheel.setStage(WheelStage.BEAM_MASTERY, 3)

        val damage = beamDamage()
        val update = wheel.updateBeamMasteryDamage(damage, beamAffectedTotal = 0, beamAffectedCurrent = 3)

        assertThat(damage.damageMultiplier).isZero()
        assertThat(update).isEqualTo(0 to 3)
    }

    // --- reduceAllSpellsCooldownTimer -----------------------------------------

    @Test
    fun `updateBeamMasteryDamage reduces the spell and group cooldowns by one second`() {
        val player = newPlayer()
        val client = RecordingCooldownClient()
        player.spellCooldownClient = client
        player.addCondition(
            ConditionSpellCooldown(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_SPELLCOOLDOWN,
                3_000,
                subId = 1300,
                now = { clock },
            ),
        )
        player.addCondition(
            ConditionSpellGroupCooldown(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
                3_000,
                subId = SpellGroup.SPELLGROUP_GREAT_BEAMS.value,
                now = { clock },
            ),
        )
        val wheel = newWheel(player = player)
        wheel.setStage(WheelStage.BEAM_MASTERY, 1)

        wheel.updateBeamMasteryDamage(beamDamage(), beamAffectedTotal = 3, beamAffectedCurrent = 0)

        assertThat(client.groupCooldowns).containsExactly(SpellGroup.SPELLGROUP_GREAT_BEAMS to 2_000)
        assertThat(client.spellCooldowns).containsExactly(1300 to 2_000)
        assertThat(
            player.getCondition(ConditionType.CONDITION_SPELLCOOLDOWN, ConditionId.CONDITIONID_DEFAULT, 1300)?.ticks,
        ).isEqualTo(2_000)
        assertThat(
            player.getCondition(
                ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
                ConditionId.CONDITIONID_DEFAULT,
                SpellGroup.SPELLGROUP_GREAT_BEAMS.value,
            )?.ticks,
        ).isEqualTo(2_000)
    }

    @Test
    fun `a cooldown covered by the reduction is sent as zero`() {
        val player = newPlayer()
        val client = RecordingCooldownClient()
        player.spellCooldownClient = client
        player.addCondition(
            ConditionSpellCooldown(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_SPELLCOOLDOWN,
                1_000,
                subId = 1300,
                now = { clock },
            ),
        )
        player.addCondition(
            ConditionSpellGroupCooldown(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
                1_000,
                subId = SpellGroup.SPELLGROUP_GREAT_BEAMS.value,
                now = { clock },
            ),
        )
        val wheel = newWheel(player = player)
        wheel.setStage(WheelStage.BEAM_MASTERY, 1)

        wheel.updateBeamMasteryDamage(beamDamage(), beamAffectedTotal = 1, beamAffectedCurrent = 0)

        assertThat(client.groupCooldowns).containsExactly(SpellGroup.SPELLGROUP_GREAT_BEAMS to 0)
        assertThat(client.spellCooldowns).containsExactly(1300 to 0)
    }

    @Test
    fun `an unknown spell id keeps its cooldown untouched`() {
        val player = newPlayer()
        val client = RecordingCooldownClient()
        player.spellCooldownClient = client
        player.addCondition(
            ConditionSpellCooldown(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_SPELLCOOLDOWN,
                3_000,
                subId = 99,
                now = { clock },
            ),
        )
        val wheel = newWheel(player = player)
        wheel.setStage(WheelStage.BEAM_MASTERY, 1)

        wheel.reduceAllSpellsCooldownTimer(1_000)

        assertThat(client.spellCooldowns).isEmpty()
        assertThat(client.groupCooldowns).isEmpty()
        assertThat(
            player.getCondition(ConditionType.CONDITION_SPELLCOOLDOWN, ConditionId.CONDITIONID_DEFAULT, 99)?.ticks,
        ).isEqualTo(3_000)
    }

    @Test
    fun `an unwired wheel owner makes the cooldown reduction inert`() {
        val wheel = PlayerWheel(
            vocationCipId = IOWheel.VOCATION_SORCERER_CIP,
            level = 200,
            spells = beamRegistry(),
            otsysTimeMs = { clock },
        )

        wheel.setStage(WheelStage.BEAM_MASTERY, 1)
        wheel.reduceAllSpellsCooldownTimer(1_000)
    }

    // --- host adapter binding -------------------------------------------------

    @Test
    fun `the wheel adapter applies beam mastery through the combat func`() {
        val player = newPlayer()
        val wheel = newWheel(player = player)
        wheel.setStage(WheelStage.BEAM_MASTERY, 2)
        val caster = FakeCombatActor().apply {
            positionValue = center
            playerValue = WheelBeamCombatPlayer(FakeCombatPlayer(), { wheel })
        }
        val map = FakeCombatMap()
        tileFor(map, targetPos).creatureList.add(
            FakeCombatActor().apply { positionValue = targetPos },
        )
        val applied = mutableListOf<CombatDamage>()

        Combat.combatFunc(
            caster = caster,
            origin = center,
            toPos = targetPos,
            area = null,
            params = CombatParams(combatType = CombatType.ENERGYDAMAGE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = beamDamage(),
            func = CombatFunction { _, _, _, damage -> applied.add(damage) },
        )

        assertThat(applied.single().damageMultiplier).isEqualTo(12)
    }

    @Test
    fun `the adapter without a resolved wheel keeps the damage untouched`() {
        val caster = FakeCombatActor().apply {
            positionValue = center
            playerValue = WheelBeamCombatPlayer(FakeCombatPlayer(), { null })
        }
        val map = FakeCombatMap()
        tileFor(map, targetPos).creatureList.add(
            FakeCombatActor().apply { positionValue = targetPos },
        )
        val applied = mutableListOf<CombatDamage>()

        Combat.combatFunc(
            caster = caster,
            origin = center,
            toPos = targetPos,
            area = null,
            params = CombatParams(combatType = CombatType.ENERGYDAMAGE, aggressive = true),
            map = map,
            effects = FakeCombatEffects(),
            damage = beamDamage(),
            func = CombatFunction { _, _, _, damage -> applied.add(damage) },
        )

        assertThat(applied.single().damageMultiplier).isZero()
    }

    @Test
    fun `the adapter routes the beam counter through the wheel`() {
        val wheel = newWheel()
        wheel.setStage(WheelStage.BEAM_MASTERY, 3)
        val adapter = WheelBeamCombatPlayer(FakeCombatPlayer(), { wheel })

        assertThat(adapter.getBeamAffectedTotal(beamDamage())).isEqualTo(3)
        val damage = beamDamage()
        assertThat(adapter.updateBeamMasteryDamage(damage, 3, 0)).isEqualTo(2 to 1)
        assertThat(damage.damageMultiplier).isEqualTo(14)
    }
}
