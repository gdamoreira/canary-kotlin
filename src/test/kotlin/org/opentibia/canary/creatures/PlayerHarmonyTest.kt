package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Buff
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.FakeConditionTarget
import org.opentibia.canary.combat.condition.MonkData
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.map.Position

/**
 * Transcribes the Monk harmony/virtue/serenity state of Canary's `Player`:
 *
 * - `Player::harmony` / `Player::virtue` (player.hpp:1703-1704) and
 *   `getHarmony`/`setHarmony`/`emptyHarmony`/`fillHarmony`/`buildHarmony`/
 *   `spendHarmony`/`getHarmonyBonus`/`getHarmonyDamage` (player.cpp:13282-13359)
 * - `Player::healFromHarmony` target selection (player.cpp:13373-13403)
 * - `Player::setVirtue` (player.cpp:13248-13259), `Player::setSerene`
 *   (player.cpp:13261-13280) and `Player::sendMonkData` (player.cpp:9249-9253)
 * - `Player::updateSerenityState` and its file-local helpers (player.cpp:8556-8616)
 *   driven from `Player::onThink` (player.cpp:8629)
 * - `Creature::varBuffs` (creature.hpp:909) through `getBuff`/`setBuff`
 *   (creature.hpp:309-323)
 */
class PlayerHarmonyTest {

    private fun newPlayer(
        health: Int = 150,
        position: Position = Position(100, 100, 7),
        vocationId: Int = Vocation.VOCATION_MONK_CIP,
    ) = Player(
        id = 31,
        name = "Harmony Hero",
        maxHealth = 150,
        health = health,
        position = position,
    ).apply {
        this.vocationId = vocationId
    }

    private fun recorder(player: Player): MutableList<Pair<Player, Int>> {
        val heals = mutableListOf<Pair<Player, Int>>()
        player.harmonyHeal = { target, charges -> heals.add(target to charges) }
        return heals
    }

    @Test
    fun `new player holds an empty harmony bar and no virtue`() {
        // player.hpp:1703-1704 defaults.
        val player = newPlayer()

        assertThat(player.getHarmony()).isEqualTo(0)
        assertThat(player.getVirtue()).isEqualTo(Virtue_t.None)
    }

    @Test
    fun `setHarmony stores the charge count`() {
        // Player::setHarmony (player.cpp:13325-13327).
        val player = newPlayer()

        player.setHarmony(3)

        assertThat(player.getHarmony()).isEqualTo(3)
    }

    @Test
    fun `emptyHarmony zeroes the bar and notifies the client`() {
        // Player::emptyHarmony (player.cpp:13282-13285).
        val player = newPlayer()
        player.setHarmony(3)
        val monkData = mutableListOf<Pair<MonkData, Int>>()
        player.sendMonkData = { type, value -> monkData.add(type to value) }

        player.emptyHarmony()

        assertThat(player.getHarmony()).isEqualTo(0)
        assertThat(monkData).containsExactly(MonkData.Harmony to 0)
    }

    @Test
    fun `buildHarmony heals and adds charges`() {
        // Player::buildHarmony (player.cpp:13292-13307).
        val player = newPlayer()
        val heals = recorder(player)
        val monkData = mutableListOf<Pair<MonkData, Int>>()
        player.sendMonkData = { type, value -> monkData.add(type to value) }

        player.buildHarmony(2)

        assertThat(player.getHarmony()).isEqualTo(2)
        assertThat(heals).containsExactly(player to 2)
        assertThat(monkData).containsExactly(MonkData.Harmony to 2)
    }

    @Test
    fun `buildHarmony clamps the charges to the remaining bar`() {
        // player.cpp:13297: `charges = std::min<uint8_t>(charges, 5 - harmony)`.
        val player = newPlayer()
        player.setHarmony(4)
        val heals = recorder(player)

        player.buildHarmony(5)

        assertThat(player.getHarmony()).isEqualTo(5)
        assertThat(heals).containsExactly(player to 1)
    }

    @Test
    fun `buildHarmony is a no-op on a full bar`() {
        // player.cpp:13293-13295.
        val player = newPlayer()
        player.setHarmony(5)
        val heals = recorder(player)
        var monkDataSent = 0
        player.sendMonkData = { _, _ -> monkDataSent++ }

        player.buildHarmony()

        assertThat(heals).isEmpty()
        assertThat(monkDataSent).isEqualTo(0)
    }

    @Test
    fun `fillHarmony fills the bar and notifies twice`() {
        // Player::fillHarmony (player.cpp:13287-13290): buildHarmony(5) sends, then sends again.
        val player = newPlayer()
        val heals = recorder(player)
        val monkData = mutableListOf<Pair<MonkData, Int>>()
        player.sendMonkData = { type, value -> monkData.add(type to value) }

        player.fillHarmony()

        assertThat(player.getHarmony()).isEqualTo(5)
        assertThat(heals).containsExactly(player to 5)
        assertThat(monkData).containsExactly(
            MonkData.Harmony to 5,
            MonkData.Harmony to 5,
        )
    }

    @Test
    fun `spendHarmony heals with the spent charges and empties the bar`() {
        // Player::spendHarmony (player.cpp:13309-13319).
        val player = newPlayer()
        player.setHarmony(3)
        val heals = recorder(player)
        val monkData = mutableListOf<Pair<MonkData, Int>>()
        player.sendMonkData = { type, value -> monkData.add(type to value) }

        player.spendHarmony()

        assertThat(player.getHarmony()).isEqualTo(0)
        assertThat(heals).containsExactly(player to 3)
        assertThat(monkData).containsExactly(MonkData.Harmony to 0)
    }

    @Test
    fun `spendHarmony rebuilds a charge under the virtue of harmony`() {
        // player.cpp:13314-13316: `virtue == Harmony` triggers `buildHarmony()`.
        val player = newPlayer()
        player.setVirtue(Virtue_t.Harmony, notifyClient = false)
        player.setHarmony(3)
        val heals = recorder(player)
        val monkData = mutableListOf<Pair<MonkData, Int>>()
        player.sendMonkData = { type, value -> monkData.add(type to value) }

        player.spendHarmony()

        assertThat(player.getHarmony()).isEqualTo(1)
        assertThat(heals).containsExactly(player to 3, player to 1)
        assertThat(monkData).containsExactly(
            MonkData.Harmony to 1,
            MonkData.Harmony to 1,
        )
    }

    @Test
    fun `getHarmonyDamage returns the base values with no charges`() {
        // player.cpp:13330-13332.
        val player = newPlayer()

        assertThat(player.getHarmonyDamage(1_000_000.0, 2_000_000.0))
            .isEqualTo(1_000_000L to 2_000_000L)
    }

    @Test
    fun `getHarmonyDamage applies eight percent per charge`() {
        // player.cpp:13334, 13352-13353: base 8 * 2^(harmony-1) percent.
        val player = newPlayer()
        player.setHarmony(1)

        assertThat(player.getHarmonyDamage(1_000_000.0, 2_000_000.0))
            .isEqualTo(1_080_000L to 2_160_000L)

        player.setHarmony(5)

        assertThat(player.getHarmonyDamage(1_000_000.0, 2_000_000.0))
            .isEqualTo(2_280_000L to 4_560_000L)
    }

    @Test
    fun `getHarmonyDamage stacks the virtue of harmony bonus`() {
        // player.cpp:13336-13338: +4% without serenity, +8% with it.
        val player = newPlayer()
        player.setVirtue(Virtue_t.Harmony, notifyClient = false)
        player.setHarmony(1)

        assertThat(player.getHarmonyDamage(1_000_000.0, 2_000_000.0))
            .isEqualTo(1_120_000L to 2_240_000L)

        player.setSerene(true)

        assertThat(player.getHarmonyDamage(1_000_000.0, 2_000_000.0))
            .isEqualTo(1_160_000L to 2_320_000L)
    }

    @Test
    fun `getHarmonyDamage counts the harmony buff over the base hundred`() {
        // player.cpp:13342-13346: BUFF_HARMONYBONUS shifts the base by raw - 100.
        val player = newPlayer()
        player.setHarmony(1)
        player.setBuff(Buff.BUFF_HARMONYBONUS, 2)

        assertThat(player.getHarmonyDamage(1_000_000.0, 2_000_000.0))
            .isEqualTo(1_100_000L to 2_200_000L)
    }

    @Test
    fun `getHarmonyDamage ignores an empty harmony buff`() {
        // player.cpp:13343: `if (rawHarmonyBuff != 0)`.
        val player = newPlayer()
        player.setHarmony(1)
        player.setBuff(Buff.BUFF_HARMONYBONUS, -100)

        assertThat(player.getHarmonyDamage(1_000_000.0, 2_000_000.0))
            .isEqualTo(1_080_000L to 2_160_000L)
    }

    @Test
    fun `getHarmonyDamage clamps a non positive bonus`() {
        // player.cpp:13348-13350: a base bonus <= 0 returns the unmodified multiplier.
        val player = newPlayer()
        player.setHarmony(1)
        player.setBuff(Buff.BUFF_HARMONYBONUS, -105)

        assertThat(player.getHarmonyDamage(1_000_000.0, 2_000_000.0))
            .isEqualTo(1_000_000L to 2_000_000L)
    }

    @Test
    fun `getHarmonyDamage adds the ascetic wheel stage`() {
        // player.cpp:13340: `harmonyBaseBonus += m_wheelPlayer.getStage(WheelStage_t::ASCETIC)`.
        val player = newPlayer()
        player.setHarmony(1)
        player.wheelStageAsceticProvider = { 5 }

        assertThat(player.getHarmonyDamage(1_000_000.0, 2_000_000.0))
            .isEqualTo(1_130_000L to 2_260_000L)
    }

    @Test
    fun `buildHarmony heals the most injured visible party member`() {
        // player.cpp:13377-13399: the most injured visible member replaces self.
        val player = newPlayer(health = 150)
        val party = Party(player)
        val member = newPlayer(health = 50, position = Position(105, 103, 7))
        party.invitePlayer(member)
        party.joinParty(member)
        val heals = recorder(player)

        player.buildHarmony(1)

        assertThat(heals).containsExactly(member to 1)
    }

    @Test
    fun `buildHarmony skips downed and removed party members`() {
        // player.cpp:13380-13382: `isRemoved()`/`isDead()` members are skipped.
        val player = newPlayer(health = 150)
        val party = Party(player)
        val downed = newPlayer(health = 0, position = Position(101, 100, 7))
        party.invitePlayer(downed)
        party.joinParty(downed)
        val heals = recorder(player)

        player.buildHarmony(1)

        assertThat(heals).containsExactly(player to 1)
    }

    @Test
    fun `buildHarmony skips members outside the client view`() {
        // player.cpp:13390: `areInRange<MAP_MAX_CLIENT_VIEW_PORT_X, MAP_MAX_CLIENT_VIEW_PORT_Y, 0>`.
        val player = newPlayer(health = 150)
        val party = Party(player)
        val farAway = newPlayer(health = 1, position = Position(109, 100, 7))
        party.invitePlayer(farAway)
        party.joinParty(farAway)
        val heals = recorder(player)

        player.buildHarmony(1)

        assertThat(heals).containsExactly(player to 1)
    }

    @Test
    fun `setVirtue stores the virtue and notifies skills and monk data`() {
        // Player::setVirtue (player.cpp:13248-13259).
        val player = newPlayer()
        var skillsSent = 0
        val monkData = mutableListOf<Pair<MonkData, Int>>()
        player.sendSkills = { skillsSent++ }
        player.sendMonkData = { type, value -> monkData.add(type to value) }

        player.setVirtue(Virtue_t.Harmony)

        assertThat(player.getVirtue()).isEqualTo(Virtue_t.Harmony)
        assertThat(skillsSent).isEqualTo(1)
        assertThat(monkData).containsExactly(MonkData.Virtue to Virtue_t.Harmony.value)
    }

    @Test
    fun `setVirtue is a no-op for the current virtue`() {
        // player.cpp:13249-13251.
        val player = newPlayer()
        var notifications = 0
        player.sendSkills = { notifications++ }
        player.sendMonkData = { _, _ -> notifications++ }

        player.setVirtue(Virtue_t.None)

        assertThat(notifications).isEqualTo(0)
    }

    @Test
    fun `setVirtue can stay silent`() {
        // player.cpp:13255-13258: `notifyClient` gates the sends.
        val player = newPlayer()
        var notifications = 0
        player.sendSkills = { notifications++ }
        player.sendMonkData = { _, _ -> notifications++ }

        player.setVirtue(Virtue_t.Sustain, notifyClient = false)

        assertThat(player.getVirtue()).isEqualTo(Virtue_t.Sustain)
        assertThat(notifications).isEqualTo(0)
    }

    @Test
    fun `setSerene adds and removes the serene condition`() {
        // Player::setSerene (player.cpp:13261-13280).
        val player = newPlayer()

        player.setSerene(true)

        assertThat(player.hasCondition(ConditionType.CONDITION_SERENE)).isTrue()

        player.setSerene(false)

        assertThat(player.hasCondition(ConditionType.CONDITION_SERENE)).isFalse()
    }

    @Test
    fun `setSerene cannot override an enforced window`() {
        // player.cpp:13263-13266: a condition with remaining ticks refuses the new value.
        val player = newPlayer()

        player.setSerene(true, ticks = 7000)
        player.setSerene(false)
        player.setSerene(true, ticks = -1)

        val serene = player.getCondition(ConditionType.CONDITION_SERENE)
        assertThat(serene).isNotNull()
        assertThat(serene!!.ticks).isEqualTo(7000)
    }

    @Test
    fun `setSerene under the virtue of justice notifies the skills`() {
        // player.cpp:13277-13279.
        val player = newPlayer()
        player.setVirtue(Virtue_t.Justice, notifyClient = false)
        var skillsSent = 0
        player.sendSkills = { skillsSent++ }

        player.setSerene(true)

        assertThat(skillsSent).isEqualTo(1)
    }

    @Test
    fun `serene condition toggles the client serenity flag through the condition target`() {
        // ConditionSerene::startCondition/endCondition (condition.cpp:667-682).
        val player = newPlayer()
        val target = FakeConditionTarget()
        player.conditionTarget = target

        player.setSerene(true)
        player.setSerene(false)

        assertThat(target.monkData).containsExactly(
            MonkData.Serenity to 1,
            MonkData.Serenity to 0,
        )
    }

    @Test
    fun `onThink makes a monk serene without company`() {
        // Player::onThink -> updateSerenityState (player.cpp:8603-8616, 8629).
        val player = newPlayer()

        player.onThink(1000)

        assertThat(player.hasCondition(ConditionType.CONDITION_SERENE)).isTrue()
    }

    @Test
    fun `onThink drops serenity with a visible party member and too many creatures`() {
        // player.cpp:8608-8614: checkSerenity is false when both conditions hold.
        val player = newPlayer()
        player.onThink(1000)
        val party = Party(player)
        val member = newPlayer(health = 150, position = Position(105, 103, 7))
        party.invitePlayer(member)
        party.joinParty(member)
        player.nearbyCreaturesProvider = { position ->
            List(6) { Monster(id = 100L + it, name = "Rat", maxHealth = 10, health = 10, position = position) }
        }

        player.onThink(1000)

        assertThat(player.hasCondition(ConditionType.CONDITION_SERENE)).isFalse()
    }

    @Test
    fun `onThink keeps serenity with creatures but no visible party member`() {
        // player.cpp:8609-8611: a missing visible member short-circuits tooManyCreaturesNearby.
        val player = newPlayer()
        player.nearbyCreaturesProvider = { position ->
            List(6) { Monster(id = 100L + it, name = "Rat", maxHealth = 10, health = 10, position = position) }
        }

        player.onThink(1000)

        assertThat(player.hasCondition(ConditionType.CONDITION_SERENE)).isTrue()
    }

    @Test
    fun `onThink does not touch serenity for non monk vocations`() {
        // player.cpp:8604-8606: only VOCATION_MONK_CIP is processed.
        val player = newPlayer(vocationId = Vocation.VOCATION_KNIGHT)

        player.onThink(1000)

        assertThat(player.hasCondition(ConditionType.CONDITION_SERENE)).isFalse()
    }
}
