package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.assertj.core.api.Assertions.entry
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Augment_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcribes the equipped-augment queries
 * (src/creatures/players/player.cpp:5892-5947):
 *
 * - `Player::getEquippedAugmentItemsByType` (player.cpp:5892-5905)
 * - `Player::getEquippedAugmentItems` (player.cpp:5907-5919)
 * - `Player::getEquippedAugments` (player.cpp:5921-5947)
 *
 * `g_spells().getInstantSpellByName(augment->spellName)` is the injected
 * [getEquippedAugments] lookup; it returns 0 (or nothing) for unknown names.
 * The map key is the C++ `std::pair<uint16_t, uint8_t>` `(spellId, type)`.
 */
class PlayerAugmentStateTest {

    private fun newPlayer(): Player =
        Player(id = 1, name = "Hero", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun equip(player: Player, item: Item, slot: Slots_t) {
        assertThat(player.addItem(item, slot)).isEqualTo(ReturnValue.NOERROR)
    }

    private fun item(id: Int, name: String, vararg augments: Triple<String, Augment_t, Int>): Item {
        val type = ItemType(id = id, name = name)
        for ((spellName, augmentType, value) in augments) {
            type.addAugment(spellName, augmentType, value)
        }
        return Item(id, 1, type)
    }

    @Test
    fun `getEquippedAugmentItems keeps only augmented equipment in slot order`() {
        // Player::getEquippedAugmentItems (player.cpp:5907-5919).
        val player = newPlayer()
        val helmet = item(1, "Helmet", Triple("exura", Augment_t.BaseHealing, 100))
        val backpack = item(2, "Backpack")
        val armor = item(3, "Armor", Triple("exori", Augment_t.BaseDamage, 50))
        equip(player, helmet, Slots_t.CONST_SLOT_HEAD)
        equip(player, backpack, Slots_t.CONST_SLOT_BACKPACK)
        equip(player, armor, Slots_t.CONST_SLOT_ARMOR)

        assertThat(player.getEquippedAugmentItems()).containsExactly(helmet, armor)
    }

    @Test
    fun `getEquippedAugmentItemsByType filters on the augment type`() {
        // Player::getEquippedAugmentItemsByType (player.cpp:5892-5905).
        val player = newPlayer()
        val cooldownHelmet = item(1, "Helmet", Triple("exura", Augment_t.Cooldown, -3000))
        val damageArmor = item(2, "Armor", Triple("exori", Augment_t.BaseDamage, 50))
        equip(player, cooldownHelmet, Slots_t.CONST_SLOT_HEAD)
        equip(player, damageArmor, Slots_t.CONST_SLOT_ARMOR)

        assertThat(player.getEquippedAugmentItemsByType(Augment_t.Cooldown)).containsExactly(cooldownHelmet)
        assertThat(player.getEquippedAugmentItemsByType(Augment_t.BaseDamage)).containsExactly(damageArmor)
        assertThat(player.getEquippedAugmentItemsByType(Augment_t.ManaCost)).isEmpty()
    }

    @Test
    fun `getEquippedAugments accumulates the value of each spell and type`() {
        // Player::getEquippedAugments (player.cpp:5921-5947): cooldown uses a
        // -1000 divisor, every other type 100; unknown spells (id 0) are skipped.
        val player = newPlayer()
        val helmet = item(
            1,
            "Helmet",
            Triple("exura", Augment_t.Cooldown, -3000),
            Triple("exori", Augment_t.BaseDamage, 200),
        )
        val armor = item(
            2,
            "Armor",
            Triple("exura", Augment_t.Cooldown, -1000),
            Triple("unknown spell", Augment_t.BaseDamage, 900),
            Triple("exori", Augment_t.None, 777),
        )
        equip(player, helmet, Slots_t.CONST_SLOT_HEAD)
        equip(player, armor, Slots_t.CONST_SLOT_ARMOR)

        val spells = mapOf("exura" to 5, "exori" to 7)
        val augments = player.getEquippedAugments { spells[it] }

        assertThat(augments).containsOnly(
            entry(AugmentKey(5, Augment_t.Cooldown), 4.0),
            entry(AugmentKey(7, Augment_t.BaseDamage), 2.0),
        )
    }

    @Test
    fun `getEquippedAugments without a spell registry yields an empty map`() {
        // The default lookup models the unwired g_spells() registry.
        val player = newPlayer()
        val helmet = item(1, "Helmet", Triple("exura", Augment_t.BaseHealing, 100))
        equip(player, helmet, Slots_t.CONST_SLOT_HEAD)

        assertThat(player.getEquippedAugments()).isEmpty()
    }
}
