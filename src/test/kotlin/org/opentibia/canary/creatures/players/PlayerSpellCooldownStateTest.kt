package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionGeneric
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionSpellCooldown
import org.opentibia.canary.combat.condition.ConditionSpellGroupCooldown
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.SpellGroup
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.InstantSpell
import org.opentibia.canary.spells.Spell

/**
 * Transcribes the Player spell-cooldown clearing and the Momentum trigger
 * (src/creatures/players/player.cpp:11095-11176):
 *
 * - `Player::triggerMomentum` (player.cpp:11095-11137)
 * - `Player::clearCooldowns` (player.cpp:11139-11176)
 *
 * `Player::sendSpellCooldown`/`sendSpellGroupCooldown`/`sendSpellCooldowns`
 * already live on the host (`creatures/Creature.kt:2316-2358`) and are covered
 * by their own tests, so they are only used as seams here. `g_spells()` is the
 * injected [org.opentibia.canary.spells.Spells] lookup seam, `OTSYS_TIME()` is
 * the `now` clock, the wheel bonus an injected provider and the protocol sends
 * are callbacks (AGENTS.md 1.1).
 */
class PlayerSpellCooldownStateTest {

    /** Rounded to an even second so the Momentum gate sees an even second. */
    private var clock = (System.currentTimeMillis() / 2000) * 2000
    private val sentSpellCooldowns = mutableListOf<Pair<Int, Int>>()
    private val sentGroupCooldowns = mutableListOf<Pair<SpellGroup, Int>>()
    private val loggedErrors = mutableListOf<String>()
    private val magicEffects = mutableListOf<Unit>()
    private val attentionMessages = mutableListOf<String>()

    private fun newPlayer(): Player =
        Player(id = 7, name = "Mage", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun spellCooldown(spellId: Int, ticks: Int): Condition = ConditionSpellCooldown(
        id = ConditionId.CONDITIONID_DEFAULT,
        type = ConditionType.CONDITION_SPELLCOOLDOWN,
        ticks = ticks,
        subId = spellId,
        now = { clock },
    )

    private fun groupCooldown(group: SpellGroup, ticks: Int): Condition = ConditionSpellGroupCooldown(
        id = ConditionId.CONDITIONID_DEFAULT,
        type = ConditionType.CONDITION_SPELLGROUPCOOLDOWN,
        ticks = ticks,
        subId = group.value,
        now = { clock },
    )

    private fun fleeting(ticks: Int): Condition = ConditionGeneric(
        id = ConditionId.CONDITIONID_DEFAULT,
        type = ConditionType.CONDITION_HASTE,
        ticks = ticks,
        now = { clock },
    )

    private fun Player.clear(
        spenders: Boolean = false,
        builders: Boolean = false,
        ticks: Int = 0,
        spellsById: Map<Int, Spell> = emptyMap(),
        spendersById: Set<Int> = emptySet(),
        buildersById: Set<Int> = emptySet(),
    ) {
        clearCooldowns(
            spenders = spenders,
            builders = builders,
            ticks = ticks,
            instantSpell = { spellId -> spellsById[spellId] },
            isSpender = { spell -> spell.spellId in spendersById },
            isBuilder = { spell -> spell.spellId in buildersById },
            sendSpellCooldown = { spellId, time -> sentSpellCooldowns.add(spellId to time) },
            sendSpellGroupCooldown = { group, time -> sentGroupCooldowns.add(group to time) },
            logError = { message -> loggedErrors.add(message) },
        )
    }

    private fun momentumPlayer(
        head: Item? = null,
        boots: Item? = null,
        zone: ZoneType = ZoneType.ZONE_NOPVP,
        infight: Boolean = true,
        momentumBonus: Double = 0.0,
        random: Double = 0.0,
    ): Player {
        val player = newPlayer()
        head?.let { player.addItem(it, Slots_t.CONST_SLOT_HEAD) }
        boots?.let { player.addItem(it, Slots_t.CONST_SLOT_FEET) }
        if (infight) {
            player.addCondition(
                ConditionGeneric(
                    id = ConditionId.CONDITIONID_DEFAULT,
                    type = ConditionType.CONDITION_INFIGHT,
                    ticks = 60_000,
                ),
            )
        }
        player.triggerMomentum(
            now = { clock },
            zoneType = { zone },
            momentumBonus = { momentumBonus },
            uniformRandom = { _, _ -> random },
            addMagicEffect = { magicEffects.add(Unit) },
            sendTextMessage = { message -> attentionMessages.add(message) },
        )
        return player
    }

    private fun tiered(id: Int): Item =
        Item(id, count = 1, type = ItemType(id = id, name = "tiered")).apply {
            setAttribute(ItemAttribute.TIER, 1L)
        }

    // --- Player::clearCooldowns (player.cpp:11139-11176) -------------------------

    @Test
    fun `clearCooldowns without flags clears every cooldown condition and sends the zeroed windows`() {
        val player = newPlayer()
        val spell = spellCooldown(10, 3_000)
        val group = groupCooldown(SpellGroup.SPELLGROUP_ATTACK, 2_000)
        val haste = fleeting(5_000)
        player.addCondition(spell)
        player.addCondition(group)
        player.addCondition(haste)

        player.clear(spellsById = mapOf(10 to InstantSpell().apply { spellId = 10 }))

        assertThat(spell.ticks).isZero()
        assertThat(group.ticks).isZero()
        assertThat(haste.ticks).isEqualTo(5_000)
        assertThat(sentSpellCooldowns).containsExactly(10 to 0)
        assertThat(sentGroupCooldowns).containsExactly(SpellGroup.SPELLGROUP_ATTACK to 0)
        assertThat(loggedErrors).isEmpty()
    }

    @Test
    fun `clearCooldowns with ticks only subtracts the given amount and clamps at zero`() {
        val player = newPlayer()
        val long = spellCooldown(10, 3_000)
        val short = spellCooldown(11, 500)
        player.addCondition(long)
        player.addCondition(short)

        player.clear(
            ticks = 1_000,
            spellsById = mapOf(
                10 to InstantSpell().apply { spellId = 10 },
                11 to InstantSpell().apply { spellId = 11 },
            ),
        )

        assertThat(long.ticks).isEqualTo(2_000)
        assertThat(short.ticks).isZero()
        assertThat(sentSpellCooldowns).containsExactly(10 to 2_000, 11 to 0)
    }

    @Test
    fun `clearCooldowns spenders only touches spells flagged as spenders`() {
        val player = newPlayer()
        val spender = spellCooldown(10, 3_000)
        val other = spellCooldown(11, 3_000)
        player.addCondition(spender)
        player.addCondition(other)
        val spell10 = InstantSpell().apply { spellId = 10 }
        val spell11 = InstantSpell().apply { spellId = 11 }

        player.clear(
            spenders = true,
            spellsById = mapOf(10 to spell10, 11 to spell11),
            spendersById = setOf(10),
        )

        assertThat(spender.ticks).isZero()
        assertThat(other.ticks).isEqualTo(3_000)
        assertThat(sentSpellCooldowns).containsExactly(10 to 0)
    }

    @Test
    fun `clearCooldowns builders and spenders combine as an or`() {
        val player = newPlayer()
        val spender = spellCooldown(10, 3_000)
        val builder = spellCooldown(11, 3_000)
        val plain = spellCooldown(12, 3_000)
        player.addCondition(spender)
        player.addCondition(builder)
        player.addCondition(plain)

        player.clear(
            spenders = true,
            builders = true,
            spellsById = mapOf(
                10 to InstantSpell().apply { spellId = 10 },
                11 to InstantSpell().apply { spellId = 11 },
                12 to InstantSpell().apply { spellId = 12 },
            ),
            spendersById = setOf(10),
            buildersById = setOf(11),
        )

        assertThat(spender.ticks).isZero()
        assertThat(builder.ticks).isZero()
        assertThat(plain.ticks).isEqualTo(3_000)
        assertThat(sentSpellCooldowns).containsExactly(10 to 0, 11 to 0)
    }

    @Test
    fun `clearCooldowns logs and keeps an unknown spell cooldown`() {
        val player = newPlayer()
        val unknown = spellCooldown(777, 3_000)
        player.addCondition(unknown)

        player.clear()

        assertThat(unknown.ticks).isEqualTo(3_000)
        assertThat(sentSpellCooldowns).isEmpty()
        assertThat(loggedErrors).hasSize(1)
        assertThat(loggedErrors.single()).contains("777")
    }

    @Test
    fun `clearCooldowns clears an unknown group cooldown even with the flag filters`() {
        val player = newPlayer()
        val group = groupCooldown(SpellGroup.SPELLGROUP_HEALING, 3_000)
        player.addCondition(group)

        player.clear(spenders = true, builders = true)

        assertThat(group.ticks).isZero()
        assertThat(sentGroupCooldowns).containsExactly(SpellGroup.SPELLGROUP_HEALING to 0)
        assertThat(loggedErrors).isEmpty()
    }

    @Test
    fun `clearCooldowns truncates a sub id beyond uint16 to zero`() {
        val player = newPlayer()
        val overflow = spellCooldown(0x1_0000, 3_000)
        player.addCondition(overflow)

        player.clear(spellsById = mapOf(0 to InstantSpell().apply { spellId = 0 }))

        assertThat(sentSpellCooldowns).containsExactly(0 to 0)
    }

    @Test
    fun `clearCooldowns ignores non cooldown conditions`() {
        val player = newPlayer()
        val haste = fleeting(5_000)
        player.addCondition(haste)

        player.clear()

        assertThat(haste.ticks).isEqualTo(5_000)
        assertThat(sentSpellCooldowns).isEmpty()
        assertThat(sentGroupCooldowns).isEmpty()
        assertThat(loggedErrors).isEmpty()
    }

    // --- Player::triggerMomentum (player.cpp:11095-11137) ------------------------

    @Test
    fun `triggerMomentum requires a tiered head item`() {
        val player = momentumPlayer()

        assertThat(magicEffects).isEmpty()
        assertThat(attentionMessages).isEmpty()
        assertThat(player).isNotNull()
    }

    @Test
    fun `triggerMomentum ignores an untiered head item`() {
        momentumPlayer(head = Item(1, count = 1, type = ItemType(id = 1, name = "helm")))

        assertThat(magicEffects).isEmpty()
        assertThat(attentionMessages).isEmpty()
    }

    @Test
    fun `triggerMomentum does not fire in protection zones`() {
        momentumPlayer(head = tiered(2), zone = ZoneType.ZONE_PROTECTION, random = 0.0)

        assertThat(magicEffects).isEmpty()
        assertThat(attentionMessages).isEmpty()
    }

    @Test
    fun `triggerMomentum does not fire out of fight or on odd seconds`() {
        momentumPlayer(head = tiered(2), infight = false)
        assertThat(magicEffects).isEmpty()

        clock = 4_001_000L
        momentumPlayer(head = tiered(2))
        assertThat(magicEffects).isEmpty()
        assertThat(attentionMessages).isEmpty()
    }

    @Test
    fun `triggerMomentum needs a positive chance the roll can beat`() {
        val player = newPlayer()
        player.addItem(tiered(2), Slots_t.CONST_SLOT_HEAD)
        player.addCondition(
            ConditionGeneric(
                id = ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_INFIGHT,
                ticks = 60_000,
            ),
        )
        val chance = player.getInventoryItem(Slots_t.CONST_SLOT_HEAD)!!.getMomentumChance()

        player.triggerMomentum(
            now = { clock },
            zoneType = { ZoneType.ZONE_NOPVP },
            momentumBonus = { -chance },
            uniformRandom = { _, _ -> 0.0 },
            addMagicEffect = { magicEffects.add(Unit) },
            sendTextMessage = { attentionMessages.add(it) },
        )

        assertThat(magicEffects).isEmpty()

        player.triggerMomentum(
            now = { clock },
            zoneType = { ZoneType.ZONE_NOPVP },
            momentumBonus = { 0.0 },
            uniformRandom = { _, _ -> 10_000.0 },
            addMagicEffect = { magicEffects.add(Unit) },
            sendTextMessage = { attentionMessages.add(it) },
        )

        assertThat(magicEffects).isEmpty()
    }

    @Test
    fun `triggerMomentum shortens cooldowns by 2000 ticks and reports the trigger`() {
        val player = newPlayer()
        player.addItem(tiered(2), Slots_t.CONST_SLOT_HEAD)
        player.addCondition(
            ConditionGeneric(
                id = ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_INFIGHT,
                ticks = 60_000,
            ),
        )
        val spell = spellCooldown(10, 5_000)
        val group = groupCooldown(SpellGroup.SPELLGROUP_SPECIAL, 1_000)
        player.addCondition(spell)
        player.addCondition(group)

        player.triggerMomentum(
            now = { clock },
            zoneType = { ZoneType.ZONE_NOPVP },
            momentumBonus = { 0.0 },
            uniformRandom = { _, _ -> 0.0 },
            addMagicEffect = { magicEffects.add(Unit) },
            sendTextMessage = { attentionMessages.add(it) },
            sendSpellCooldown = { spellId, time -> sentSpellCooldowns.add(spellId to time) },
            sendSpellGroupCooldown = { groupId, time -> sentGroupCooldowns.add(groupId to time) },
        )

        assertThat(spell.ticks).isEqualTo(3_000)
        assertThat(group.ticks).isZero()
        assertThat(magicEffects).hasSize(1)
        assertThat(attentionMessages).containsExactly("Momentum was triggered.")
        assertThat(sentSpellCooldowns).containsExactly(10 to 3_000)
        assertThat(sentGroupCooldowns).containsExactly(SpellGroup.SPELLGROUP_SPECIAL to 0)
    }

    @Test
    fun `triggerMomentum skips groups at or below SPELLGROUP_SUPPORT but still reports`() {
        val player = newPlayer()
        player.addItem(tiered(2), Slots_t.CONST_SLOT_HEAD)
        player.addCondition(
            ConditionGeneric(
                id = ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_INFIGHT,
                ticks = 60_000,
            ),
        )
        val attack = groupCooldown(SpellGroup.SPELLGROUP_ATTACK, 5_000)
        player.addCondition(attack)

        player.triggerMomentum(
            now = { clock },
            zoneType = { ZoneType.ZONE_NOPVP },
            momentumBonus = { 0.0 },
            uniformRandom = { _, _ -> 0.0 },
            addMagicEffect = { magicEffects.add(Unit) },
            sendTextMessage = { attentionMessages.add(it) },
            sendSpellCooldown = { spellId, time -> sentSpellCooldowns.add(spellId to time) },
            sendSpellGroupCooldown = { groupId, time -> sentGroupCooldowns.add(groupId to time) },
        )

        assertThat(attack.ticks).isEqualTo(5_000)
        assertThat(sentGroupCooldowns).isEmpty()
        assertThat(magicEffects).hasSize(1)
        assertThat(attentionMessages).containsExactly("Momentum was triggered.")
    }

    @Test
    fun `triggerMomentum applies the boots amplification to the chance`() {
        val player = newPlayer()
        val head = tiered(2)
        val boots = tiered(3)
        player.addItem(head, Slots_t.CONST_SLOT_HEAD)
        player.addItem(boots, Slots_t.CONST_SLOT_FEET)
        player.addCondition(
            ConditionGeneric(
                id = ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_INFIGHT,
                ticks = 60_000,
            ),
        )

        val baseChance = head.getMomentumChance()
        val amplified = baseChance * (1 + boots.getAmplificationChance() / 100)
        val roll = (baseChance + amplified) / 2

        player.triggerMomentum(
            now = { clock },
            zoneType = { ZoneType.ZONE_NOPVP },
            momentumBonus = { 0.0 },
            uniformRandom = { _, _ -> roll * 100 },
            addMagicEffect = { magicEffects.add(Unit) },
            sendTextMessage = { attentionMessages.add(it) },
        )

        assertThat(magicEffects).hasSize(1)
        assertThat(attentionMessages).containsExactly("Momentum was triggered.")
    }
}
