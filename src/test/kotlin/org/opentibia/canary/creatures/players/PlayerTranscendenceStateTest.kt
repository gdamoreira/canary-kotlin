package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionOutfit
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.systems.wheel.WheelOnThink
import org.opentibia.canary.game.zones.ZoneType

/**
 * Transcribes the Monk transcendence/aim-at-target family
 * (src/creatures/players/player.cpp:11178-11243, 13361-13371):
 *
 * - `Player::triggerTranscendence` (player.cpp:11178-11243)
 * - `Player::updateAimAtTargetSpells` (player.cpp:13361-13367)
 * - `Player::getAimAtTargetSpells` (player.cpp:13369-13371)
 *
 * The wheel on-think timer, inventory, vocation avatar look type and the
 * condition store are real port state; the config duration, the aggressive
 * action window, the RNG, `g_game().addMagicEffect`,
 * `g_game().reloadCreature`, `PlayerWheel::sendGiftOfLifeCooldown`, the
 * post-transcendence refresh task and the protocol sends are call-site seams
 * per AGENTS.md 1.1.
 */
class PlayerTranscendenceStateTest {

    private fun newPlayer(): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun tieredItem(id: Int, name: String, tier: Int): Item =
        Item(id, 1, ItemType(id = id, name = name, upgradeClassification = 1)).apply { setTier(tier) }

    // --- Player::updateAimAtTargetSpells / getAimAtTargetSpells -----------------

    @Test
    fun `updateAimAtTargetSpells adds and removes spell ids`() {
        // player.cpp:13361-13367.
        val state = PlayerAimAtTargetState()
        assertThat(state.getAimAtTargetSpells()).isEmpty()

        state.updateAimAtTargetSpells(42, 1)
        state.updateAimAtTargetSpells(7, 1)
        assertThat(state.getAimAtTargetSpells()).containsExactlyInAnyOrder(42, 7)

        state.updateAimAtTargetSpells(42, 0)
        assertThat(state.getAimAtTargetSpells()).containsExactly(7)

        state.updateAimAtTargetSpells(99, 0)
        assertThat(state.getAimAtTargetSpells()).containsExactly(7)
    }

    // --- Player::triggerTranscendence -------------------------------------------

    private class Recorder {
        var skills = 0
        var stats = 0
        var basicData = 0
        var giftOfLife = 0
        var reloads = 0
        val effects = mutableListOf<Pair<Position, Int>>()
        val messages = mutableListOf<Pair<MessageClass, String>>()
        var scheduledDelay: Long? = null
        var scheduledAction: (() -> Unit)? = null
    }

    private fun Player.triggerRecorded(
        recorder: Recorder,
        now: Long = 10_000L,
        randomChance: Double = 0.0,
        duration: Int = 7000,
        zoneType: () -> ZoneType = { ZoneType.ZONE_NORMAL },
        aggressive: Boolean = true,
    ) {
        triggerTranscendence(
            now = now,
            randomChance = randomChance,
            duration = duration,
            zoneType = zoneType,
            checkLastAggressiveActionWithin = { aggressive },
            sendSkills = { recorder.skills++ },
            sendStats = { recorder.stats++ },
            sendBasicData = { recorder.basicData++ },
            sendTextMessage = { type, text -> recorder.messages.add(type to text) },
            addMagicEffect = { position, effect -> recorder.effects.add(position to effect) },
            scheduleRefresh = { delay, action ->
                recorder.scheduledDelay = delay
                recorder.scheduledAction = action
            },
            sendGiftOfLifeCooldown = { recorder.giftOfLife++ },
            reloadCreature = { recorder.reloads++ },
        )
    }

    @Test
    fun `triggerTranscendence is gated by the wheel avatar-forge timer`() {
        // `wheel().getOnThinkTimer(WheelOnThink_t::AVATAR_FORGE) > OTSYS_TIME()` (11179-11181).
        val player = newPlayer()
        player.addItem(tieredItem(1, "Legs", 1), Slots_t.CONST_SLOT_LEGS)
        player.wheel().setOnThinkTimer(WheelOnThink.AVATAR_FORGE, 10_001L)
        val recorder = Recorder()

        player.triggerRecorded(recorder)

        assertThat(player.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)).isNull()
        assertThat(recorder.skills).isZero()
    }

    @Test
    fun `triggerTranscendence requires tiered legs equipment`() {
        // player.cpp:11183-11191: missing item or tier 0 both return.
        val noItem = newPlayer()
        noItem.triggerRecorded(Recorder())
        assertThat(noItem.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)).isNull()

        val noTier = newPlayer()
        noTier.addItem(Item(1, 1, ItemType(id = 1, name = "Legs", upgradeClassification = 1)), Slots_t.CONST_SLOT_LEGS)
        noTier.triggerRecorded(Recorder())
        assertThat(noTier.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)).isNull()
    }

    @Test
    fun `triggerTranscendence only fires on even seconds`() {
        // `((OTSYS_TIME() / 1000) % 2) == 0` (11201).
        val player = newPlayer()
        player.addItem(tieredItem(1, "Legs", 1), Slots_t.CONST_SLOT_LEGS)
        val recorder = Recorder()

        player.triggerRecorded(recorder, now = 11_000L)

        assertThat(player.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)).isNull()
        assertThat(recorder.skills).isZero()
    }

    @Test
    fun `triggerTranscendence is blocked in a protection zone and without recent combat`() {
        // player.cpp:11201: `getZoneType() != ZONE_PROTECTION && checkLastAggressiveActionWithin(2000)`.
        val zoneBlocked = newPlayer()
        zoneBlocked.addItem(tieredItem(1, "Legs", 1), Slots_t.CONST_SLOT_LEGS)
        zoneBlocked.triggerRecorded(Recorder(), zoneType = { ZoneType.ZONE_PROTECTION })
        assertThat(zoneBlocked.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)).isNull()

        val idle = newPlayer()
        idle.addItem(tieredItem(1, "Legs", 1), Slots_t.CONST_SLOT_LEGS)
        idle.triggerRecorded(Recorder(), aggressive = false)
        assertThat(idle.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)).isNull()
    }

    @Test
    fun `triggerTranscendence requires the random roll under the item chance`() {
        // `chance > 0 && randomChance < chance` (11201).
        val player = newPlayer()
        val legs = tieredItem(1, "Legs", 1)
        player.addItem(legs, Slots_t.CONST_SLOT_LEGS)
        val recorder = Recorder()

        player.triggerRecorded(recorder, randomChance = legs.getTranscendenceChance())

        assertThat(player.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)).isNull()
    }

    @Test
    fun `triggerTranscendence applies the avatar outfit and refreshes the wheel timer`() {
        // player.cpp:11202-11213. The avatar look type comes from the monk
        // vocation (`vocation->getAvatarLookType()`).
        val player = newPlayer()
        val xml = """
            <vocations>
              <vocation id="1" name="Monk" clientid="5" gaincap="25" gainhp="15" gainmana="5" avatarlooktype="42">
                <formula armor="1" defense="1"/>
              </vocation>
            </vocations>
        """.trimIndent()
        val vocations = Vocations()
        vocations.loadFromXml(xml)
        player.vocations = vocations
        player.vocationId = 1
        player.addItem(tieredItem(1, "Legs", 1), Slots_t.CONST_SLOT_LEGS)
        val recorder = Recorder()

        player.triggerRecorded(recorder, duration = 7000)

        val condition = player.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)
        assertThat(condition).isInstanceOf(ConditionOutfit::class.java)
        assertThat((condition as ConditionOutfit).outfit.lookType).isEqualTo(42)
        assertThat(player.wheel().getOnThinkTimer(WheelOnThink.AVATAR_FORGE)).isEqualTo(17_000L)
        assertThat(recorder.effects).containsExactly(Position(100, 100, 7) to 244)
        assertThat(recorder.skills).isEqualTo(1)
        assertThat(recorder.stats).isEqualTo(1)
        assertThat(recorder.basicData).isEqualTo(1)
        assertThat(recorder.messages).containsExactly(MessageClass.MESSAGE_ATTENTION to "Transcendence was triggered.")
        assertThat(recorder.giftOfLife).isEqualTo(1)
        assertThat(recorder.reloads).isEqualTo(1)
    }

    @Test
    fun `triggerTranscendence schedules the refresh with the avatar duration`() {
        // player.cpp:11218-11240: delay = max(SCHEDULER_MINTICKS, duration).
        val player = newPlayer()
        player.addItem(tieredItem(1, "Legs", 1), Slots_t.CONST_SLOT_LEGS)
        val recorder = Recorder()

        player.triggerRecorded(recorder, duration = 7000)
        assertThat(recorder.scheduledDelay).isEqualTo(7000L)

        recorder.scheduledAction?.invoke()
        assertThat(recorder.skills).isEqualTo(2)
        assertThat(recorder.stats).isEqualTo(2)
        assertThat(recorder.basicData).isEqualTo(2)
    }

    @Test
    fun `triggerTranscendence floors the refresh delay at the scheduler minimum`() {
        // `std::max<uint32_t>(SCHEDULER_MINTICKS, duration)` (11219).
        val player = newPlayer()
        player.addItem(tieredItem(1, "Legs", 1), Slots_t.CONST_SLOT_LEGS)
        val recorder = Recorder()

        player.triggerRecorded(recorder, duration = 10)

        assertThat(recorder.scheduledDelay).isEqualTo(50L)
    }

    @Test
    fun `tiered boots amplify the transcendence chance`() {
        // player.cpp:11193-11197: `chance *= 1 + boots.getAmplificationChance() / 100`.
        val legs = tieredItem(1, "Legs", 1)
        val boots = tieredItem(2, "Boots", 1)
        val baseChance = legs.getTranscendenceChance()
        val amplified = baseChance * (1 + boots.getAmplificationChance() / 100.0)
        val roll = (baseChance + amplified) / 2.0
        assertThat(roll).isGreaterThan(baseChance).isLessThan(amplified)

        val withoutBoots = newPlayer()
        withoutBoots.addItem(legs, Slots_t.CONST_SLOT_LEGS)
        withoutBoots.triggerRecorded(Recorder(), randomChance = roll)
        assertThat(withoutBoots.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)).isNull()

        val withBoots = newPlayer()
        withBoots.addItem(tieredItem(1, "Legs", 1), Slots_t.CONST_SLOT_LEGS)
        withBoots.addItem(boots, Slots_t.CONST_SLOT_FEET)
        withBoots.triggerRecorded(Recorder(), randomChance = roll)
        assertThat(withBoots.getCondition(ConditionType.CONDITION_OUTFIT, ConditionId.CONDITIONID_COMBAT)).isNotNull()
    }

    @Test
    fun `triggerTranscendence with a null roll leaves the wheel timer untouched`() {
        // The `chance > 0` gate short-circuits before any state change (11201).
        val player = newPlayer()
        player.addItem(tieredItem(1, "Legs", 1), Slots_t.CONST_SLOT_LEGS)
        player.triggerRecorded(Recorder(), randomChance = Double.MAX_VALUE)

        assertThat(player.wheel().getOnThinkTimer(WheelOnThink.AVATAR_FORGE)).isZero()
    }
}
