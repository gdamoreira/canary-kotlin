package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the description primitives of `Item`
 * (src/items/item.cpp:3357-3426, 2441-2495): `getNameDescription`,
 * `getWeightDescription`, `parseShowDurationSpeed` and `parseShowDuration`.
 *
 * The composite look renderer (`Item::getDescription`) is covered by
 * [ItemGetDescriptionTest]; `Item::getDescriptions` (the plural inspection
 * variant) remains unported and is listed in the wave report as a parity gap.
 */
class ItemDescriptionTest {

    @Test
    fun `getNameDescription prefixes the article`() {
        val sword = ItemType(id = 2400, name = "sword", article = "a")

        assertThat(Item(2400, type = sword).getNameDescription()).isEqualTo("a sword")
    }

    @Test
    fun `getNameDescription shows the count and plural name for stacks`() {
        val coins = ItemType(id = 2148, name = "gold coin", isStackable = true)
        val item = Item(2148, count = 5, type = coins)

        assertThat(item.getNameDescription()).isEqualTo("5 gold coins")
    }

    @Test
    fun `getNameDescription omits the count when showCount is off`() {
        val coins = ItemType(id = 2148, name = "gold coin", isStackable = true, showCount = false)

        assertThat(Item(2148, count = 5, type = coins).getNameDescription()).isEqualTo("gold coin")
    }

    @Test
    fun `getNameDescription prefers the plural and name attributes`() {
        val coins = ItemType(id = 2148, name = "gold coin", isStackable = true)
        val item = Item(2148, count = 2, type = coins).apply {
            setAttribute(ItemAttribute.PLURALNAME, "shiny coins")
        }
        assertThat(item.getNameDescription()).isEqualTo("2 shiny coins")

        val renamed = Item(2400, type = ItemType(2400, "sword", article = "a")).apply {
            setAttribute(ItemAttribute.NAME, "Excalibur")
        }
        assertThat(renamed.getNameDescription()).isEqualTo("a Excalibur")
    }

    @Test
    fun `getNameDescription falls back to the type id when the name is empty`() {
        assertThat(Item(9999).getNameDescription()).isEqualTo("an item of type 9999")
    }

    @Test
    fun `getNameDescription can omit the article`() {
        val sword = ItemType(id = 2400, name = "sword", article = "a")
        val item = Item(2400, type = sword)

        assertThat(Item.getNameDescription(sword, item, addArticle = false)).isEqualTo("sword")
    }

    @Test
    fun `getWeightDescription formats hundredths of an ounce`() {
        assertThat(Item(1, type = ItemType(1, "x", weight = 0.05)).getWeightDescription())
            .isEqualTo("It weighs 0.05 oz.")
        assertThat(Item(1, type = ItemType(1, "x", weight = 1.0)).getWeightDescription())
            .isEqualTo("It weighs 1.00 oz.")
        assertThat(Item.getWeightDescription(ItemType(1, "x"), 50, 1)).isEqualTo("It weighs 0.50 oz.")
        assertThat(Item.getWeightDescription(ItemType(1, "x"), 1234, 1)).isEqualTo("It weighs 12.34 oz.")
    }

    @Test
    fun `getWeightDescription uses plural when a stack shows its count`() {
        val coins = ItemType(id = 2148, name = "gold coin", isStackable = true, weight = 1.0)
        val item = Item(2148, count = 2, type = coins)

        assertThat(item.getWeightDescription()).isEqualTo("They weigh 2.00 oz.")
        assertThat(Item.getWeightDescription(coins, 200, 2)).isEqualTo("They weigh 2.00 oz.")
    }

    @Test
    fun `getWeightDescription is empty for weightless items`() {
        assertThat(Item(1, type = ItemType(1, "x")).getWeightDescription()).isEmpty()
    }

    @Test
    fun `parseShowDuration renders brand-new without a duration attribute`() {
        assertThat(Item.parseShowDuration(Item(2050))).isEqualTo(" that is brand-new")
    }

    @Test
    fun `parseShowDuration renders seconds minutes hours and days`() {
        assertThat(durationText(1_000)).isEqualTo(" that will expire in 1 second")
        assertThat(durationText(2_000)).isEqualTo(" that will expire in 2 seconds")
        assertThat(durationText(90_000)).isEqualTo(" that will expire in 1 minute and 30 seconds")
        assertThat(durationText(3_600_000)).isEqualTo(" that will expire in 1 hour")
        assertThat(durationText(3_661_000)).isEqualTo(" that will expire in 1 hour and 1 minute")
        assertThat(durationText(86_400_000)).isEqualTo(" that will expire in 1 day")
        assertThat(durationText(183_600_000)).isEqualTo(" that will expire in 2 days and 3 hours")
    }

    @Test
    fun `parseShowDuration is empty for a null item`() {
        assertThat(Item.parseShowDuration(null)).isEmpty()
    }

    @Test
    fun `parseShowDurationSpeed opens or continues the attribute list`() {
        assertThat(Item.parseShowDurationSpeed(20, true)).isEqualTo(" (speed +20" to false)
        assertThat(Item.parseShowDurationSpeed(20, false)).isEqualTo(", speed +20" to false)
    }

    private fun durationText(millis: Int): String {
        val item = Item(2050).apply { setDuration(millis) }
        return Item.parseShowDuration(item)
    }
}
