package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.items.Abilities
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.utils.Tools

/**
 * Host composition tests for the wave-51 player state holders: the item
 * ability, boss point, cyclopedia tracker, aim-at-target and magic affinity
 * states the host [Player] now delegates to.
 *
 * Canary sources:
 *  - `Player::isItemAbilityEnabled`/`setItemAbility` (player.cpp:344-350)
 *  - `Player::getCyclopediaMonsterTrackerSet`/`addMonsterToCyclopediaTrackerList`/
 *    `removeMonsterFromCyclopediaTrackerList`/`isBossOnBosstiaryTracker`/
 *    `isMonsterOnBestiaryTracker` (player.cpp:941-1004)
 *  - `Player::getSpecializedMagicLevel`/`setSpecializedMagicLevel`/
 *    `getMagicShieldCapacityFlat`/`setMagicShieldCapacityFlat`/
 *    `getMagicShieldCapacityPercent`/`setMagicShieldCapacityPercent`
 *    (player.cpp:7606-7687)
 *  - `Player::setBossPoints`...`getRemoveTimes` (player.cpp:12673-12724)
 *  - `Player::updateAimAtTargetSpells`/`getAimAtTargetSpells`
 *    (player.cpp:13361-13371)
 */
class PlayerStateCompositionTest {

    private fun newPlayer() = Player(
        id = 7,
        name = "Host Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    // --- item abilities (player.cpp:344-350) ------------------------------------

    @Test
    fun `item ability state is composed into the player`() {
        val player = newPlayer()

        assertThat(player.isItemAbilityEnabled(Slots_t.CONST_SLOT_HEAD)).isFalse()

        player.setItemAbility(Slots_t.CONST_SLOT_HEAD, true)

        assertThat(player.isItemAbilityEnabled(Slots_t.CONST_SLOT_HEAD)).isTrue()
        assertThat(player.features.itemAbilities.isItemAbilityEnabled(Slots_t.CONST_SLOT_HEAD)).isTrue()
    }

    // --- boss points (player.cpp:12673-12724) -----------------------------------

    @Test
    fun `boss point state is composed into the player`() {
        val player = newPlayer()

        player.setBossPoints(100)
        player.addBossPoints(25)
        assertThat(player.getBossPoints()).isEqualTo(125)

        player.removeBossPoints(25)
        assertThat(player.getBossPoints()).isEqualTo(100)

        player.setSlotBossId(slotId = 1, bossId = 42)
        player.setSlotBossId(slotId = 2, bossId = 43)
        assertThat(player.getSlotBossId(1)).isEqualTo(42)
        assertThat(player.getSlotBossId(2)).isEqualTo(43)

        player.addRemoveTime()
        assertThat(player.getRemoveTimes()).isEqualTo(1)

        player.setRemoveBossTime(9)
        assertThat(player.getRemoveTimes()).isEqualTo(9)

        assertThat(player.features.bossPoints.getBossPoints()).isEqualTo(100)
    }

    // --- cyclopedia tracker (player.cpp:941-1004) -------------------------------

    @Test
    fun `cyclopedia tracker state is composed into the player`() {
        val player = newPlayer()
        val rat = MonsterType("Rat").apply { info.raceid = 5 }
        val dragon = MonsterType("Dragon").apply { info.raceid = 6 }
        val client = RecordingTrackerClient()
        player.cyclopediaMonsterTrackerClient = client

        player.addMonsterToCyclopediaTrackerList(rat, isBoss = false)
        player.addMonsterToCyclopediaTrackerList(rat, isBoss = false)
        player.addMonsterToCyclopediaTrackerList(dragon, isBoss = true)

        assertThat(player.getCyclopediaMonsterTrackerSet(false)).containsExactly(rat)
        assertThat(player.getCyclopediaMonsterTrackerSet(true)).containsExactly(dragon)
        assertThat(player.isMonsterOnBestiaryTracker(rat)).isTrue()
        assertThat(player.isMonsterOnBestiaryTracker(dragon)).isFalse()
        assertThat(player.isBossOnBosstiaryTracker(dragon)).isTrue()
        assertThat(player.isBossOnBosstiaryTracker(rat)).isFalse()

        player.removeMonsterFromCyclopediaTrackerList(rat, isBoss = false)

        assertThat(player.getCyclopediaMonsterTrackerSet(false)).isEmpty()
        assertThat(player.features.cyclopediaTracker.getCyclopediaMonsterTrackerSet(false)).isEmpty()
        assertThat(client.refreshes).isEqualTo(3)
    }

    // --- aim at target (player.cpp:13361-13371) ---------------------------------

    @Test
    fun `aim at target state is composed into the player`() {
        val player = newPlayer()

        player.updateAimAtTargetSpells(0x1234, 1)
        player.updateAimAtTargetSpells(0x5678, 0)

        assertThat(player.getAimAtTargetSpells()).containsExactly(0x1234)

        player.updateAimAtTargetSpells(0x1234, 0)

        assertThat(player.getAimAtTargetSpells()).isEmpty()
        assertThat(player.features.aimAtTarget.getAimAtTargetSpells()).isEmpty()
    }

    // --- magic affinity (player.cpp:7606-7687) ----------------------------------

    @Test
    fun `magic affinity state is composed into the player`() {
        val player = newPlayer()
        val type = ItemType(id = 9, name = "Affinity Ring")
        type.abilities = Abilities().apply {
            specializedMagicLevel[Tools.combatTypeToIndex(CombatType.FIREDAMAGE)] = 4
        }
        player.addItem(Item(9, 1, type), Slots_t.CONST_SLOT_RING)

        player.setSpecializedMagicLevel(CombatType.FIREDAMAGE, 2)
        player.setMagicShieldCapacityFlat(3)
        player.setMagicShieldCapacityPercent(5)

        assertThat(player.getSpecializedMagicLevel(CombatType.FIREDAMAGE, useCharges = false)).isEqualTo(6)
        assertThat(player.getMagicShieldCapacityFlat(useCharges = false)).isEqualTo(3)
        assertThat(player.getMagicShieldCapacityPercent(useCharges = false)).isEqualTo(5)
    }

    private class RecordingTrackerClient : CyclopediaMonsterTrackerClient {
        var refreshes = 0

        override fun parseSendBosstiary() = Unit

        override fun sendBestiaryEntryChanged(raceId: Int) = Unit

        override fun refreshCyclopediaMonsterTracker(tracker: Set<MonsterType>, isBoss: Boolean) {
            refreshes++
        }
    }
}
