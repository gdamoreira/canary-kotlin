package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionAttributes
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionRegeneration
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.WorldType
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcribes the condition-facing Player hooks
 * (src/creatures/players/player.cpp:3146-3158, 6436-6466):
 *
 * - `Player::updateRegeneration` (player.cpp:3146-3158)
 * - `Player::onCombatRemoveCondition` (player.cpp:6436-6466)
 *
 * `g_game().getWorldType()`, `g_game().internalRemoveItem` and the C++
 * `uniform_random` are call-site seams per AGENTS.md 1.1.
 */
class PlayerCombatConditionHooksTest {

    private fun newPlayer(vocations: Vocations? = null, vocationId: Int = 1): Player {
        val player = Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))
        if (vocations != null) {
            player.vocations = vocations
            player.vocationId = vocationId
        }
        return player
    }

    private fun regenVocations(): Vocations {
        val xml = """
            <vocations>
              <vocation id="1" name="Knight" clientid="1" gaincap="25" gainhp="15" gainmana="5"
                        gainhpticks="3" gainhpamount="12" gainmanaticks="4" gainmanaamount="7">
                <formula armor="1" defense="1"/>
              </vocation>
            </vocations>
        """.trimIndent()
        val vocations = Vocations()
        vocations.loadFromXml(xml)
        return vocations
    }

    private fun newRegenerationCondition(): ConditionRegeneration =
        ConditionRegeneration(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_REGENERATION,
            ticks = 100_000,
        )

    @Test
    fun `updateRegeneration rewrites the condition params from the vocation`() {
        // Player::updateRegeneration (player.cpp:3146-3158).
        val player = newPlayer(regenVocations())
        val condition = newRegenerationCondition()
        assertThat(player.addCondition(condition)).isTrue()

        player.updateRegeneration()

        assertThat(condition.healthGain).isEqualTo(12)
        assertThat(condition.healthTicks).isEqualTo(3)
        assertThat(condition.manaGain).isEqualTo(7)
        assertThat(condition.manaTicks).isEqualTo(4)
    }

    @Test
    fun `updateRegeneration without a vocation leaves the condition untouched`() {
        // `if (!vocation) return;` (player.cpp:3147-3149).
        val player = newPlayer()
        val condition = newRegenerationCondition()
        player.addCondition(condition)

        player.updateRegeneration()

        assertThat(condition.healthGain).isZero()
        assertThat(condition.healthTicks).isEqualTo(1000)
    }

    @Test
    fun `updateRegeneration without a regeneration condition is a no-op`() {
        // `getCondition(CONDITION_REGENERATION, CONDITIONID_DEFAULT)` (3146-3150).
        val player = newPlayer(regenVocations())
        player.updateRegeneration()
        assertThat(player.getCondition(ConditionType.CONDITION_REGENERATION, ConditionId.CONDITIONID_DEFAULT)).isNull()
    }

    private fun newItemCondition(): ConditionAttributes =
        ConditionAttributes(
            ConditionId.CONDITIONID_ARMOR,
            ConditionType.CONDITION_HASTE,
            ticks = 5_000,
        )

    @Test
    fun `onCombatRemoveCondition with a null condition is a no-op`() {
        // `if (!condition) return;` (player.cpp:6437-6439).
        val player = newPlayer()
        player.onCombatRemoveCondition(null)
    }

    @Test
    fun `an armor-condition item is destroyed with a twenty-five percent roll`() {
        // player.cpp:6441-6454: id > 0 -> slot item, PVP-enforced world,
        // `25 >= uniform_random(1, 100)`.
        val player = newPlayer()
        val armor = Item(1, 1, ItemType(id = 1, name = "Armor"))
        assertThat(player.addItem(armor, Slots_t.CONST_SLOT_ARMOR)).isEqualTo(ReturnValue.NOERROR)
        val removed = mutableListOf<Item>()

        player.onCombatRemoveCondition(
            newItemCondition(),
            worldType = { WorldType.PVP_ENFORCED },
            removeItem = { removed.add(it) },
            uniformRandom = { _, _ -> 25 },
        )

        assertThat(removed).containsExactly(armor)
    }

    @Test
    fun `an armor-condition item survives a roll above twenty-five`() {
        // The `25 >= uniform_random(1, 100)` boundary (player.cpp:6449).
        val player = newPlayer()
        val armor = Item(1, 1, ItemType(id = 1, name = "Armor"))
        player.addItem(armor, Slots_t.CONST_SLOT_ARMOR)
        val removed = mutableListOf<Item>()

        player.onCombatRemoveCondition(
            newItemCondition(),
            worldType = { WorldType.PVP_ENFORCED },
            removeItem = { removed.add(it) },
            uniformRandom = { _, _ -> 26 },
        )

        assertThat(removed).isEmpty()
    }

    @Test
    fun `an armor-condition item survives a non-enforced world`() {
        // `g_game().getWorldType() == WORLD_TYPE_PVP_ENFORCED` (player.cpp:6443).
        val player = newPlayer()
        val armor = Item(1, 1, ItemType(id = 1, name = "Armor"))
        player.addItem(armor, Slots_t.CONST_SLOT_ARMOR)
        val removed = mutableListOf<Item>()

        player.onCombatRemoveCondition(
            newItemCondition(),
            worldType = { WorldType.PVP },
            removeItem = { removed.add(it) },
        )

        assertThat(removed).isEmpty()
    }

    @Test
    fun `a non-item condition is re-armed to the next action tick`() {
        // player.cpp:6455-6465: `ticks = delay - (delay % EVENT_CREATURE_THINK_INTERVAL)`.
        val player = newPlayer()
        player.now = { 10_000L }
        player.setNextAction(15_000L)
        val condition = ConditionAttributes(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_HASTE,
            ticks = 100,
        )
        player.addCondition(condition)

        player.onCombatRemoveCondition(condition)

        assertThat(condition.ticks).isEqualTo(5_000)
        assertThat(player.getCondition(ConditionType.CONDITION_HASTE, ConditionId.CONDITIONID_COMBAT)).isSameAs(condition)
    }

    @Test
    fun `a paralyzed non-item condition is removed even when re-armed`() {
        // player.cpp:6461-6463: `condition->getType() == CONDITION_PARALYZE`.
        val player = newPlayer()
        player.now = { 10_000L }
        player.setNextAction(15_000L)
        val condition = ConditionAttributes(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_PARALYZE,
            ticks = 5_000,
        )
        player.addCondition(condition)

        player.onCombatRemoveCondition(condition)

        assertThat(player.getConditionsByType(ConditionType.CONDITION_PARALYZE)).isEmpty()
    }

    @Test
    fun `a non-item condition is removed when the player can act`() {
        // player.cpp:6464-6465: `else removeCondition(condition);`.
        val player = newPlayer()
        val condition = ConditionAttributes(
            ConditionId.CONDITIONID_COMBAT,
            ConditionType.CONDITION_HASTE,
            ticks = 5_000,
        )
        player.addCondition(condition)
        assertThat(player.canDoAction()).isTrue()

        player.onCombatRemoveCondition(condition)

        assertThat(player.getConditionsByType(ConditionType.CONDITION_HASTE)).isEmpty()
    }
}
