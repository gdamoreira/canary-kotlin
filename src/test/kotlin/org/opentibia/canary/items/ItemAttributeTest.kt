package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcripts Canary's item attribute behaviour.
 *
 * Reference:
 * - src/enums/item_attribute.hpp (ItemAttribute_t values, ItemDecayState_t)
 * - src/items/functions/item/attribute.hpp/.cpp (integer/string classification, set/remove)
 * - src/items/item.hpp/.cpp (getDuration, setDuration, setDecaying, getDecaying)
 * - src/lua/functions/items/item_functions.cpp (getActionId/setActionId/getUniqueId)
 */
class ItemAttributeTest {

    @Test
    fun `enum mirrors canary ItemAttribute_t values`() {
        assertThat(ItemAttribute.NONE.value).isEqualTo(0L)
        assertThat(ItemAttribute.ACTIONID.value).isEqualTo(1L)
        assertThat(ItemAttribute.UNIQUEID.value).isEqualTo(2L)
        assertThat(ItemAttribute.DURATION.value).isEqualTo(18L)
        assertThat(ItemAttribute.DECAYSTATE.value).isEqualTo(19L)
        assertThat(ItemAttribute.CORPSEOWNER.value).isEqualTo(20L)
        assertThat(ItemAttribute.CHARGES.value).isEqualTo(21L)
        assertThat(ItemAttribute.DURATION_TIMESTAMP.value).isEqualTo(28L)
        assertThat(ItemAttribute.CUSTOM.value).isEqualTo(32L)
        assertThat(ItemAttribute.MANTRA.value).isEqualTo(37L)
    }

    @Test
    fun `decay state mirrors canary ItemDecayState_t values`() {
        assertThat(ItemDecayState.DECAYING_FALSE.value).isEqualTo(0)
        assertThat(ItemDecayState.DECAYING_TRUE.value).isEqualTo(1)
        assertThat(ItemDecayState.DECAYING_PENDING.value).isEqualTo(2)
        assertThat(ItemDecayState.DECAYING_STOPPING.value).isEqualTo(3)
    }

    @Test
    fun `setAttribute stores integer and string values by classification`() {
        val item = Item(2160)

        item.setAttribute(ItemAttribute.ACTIONID, 1000L)
        item.setAttribute(ItemAttribute.DESCRIPTION, "a crystal coin")

        assertThat(item.getAttribute(ItemAttribute.ACTIONID)).isEqualTo(1000L)
        assertThat(item.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo("a crystal coin")
    }

    @Test
    fun `getAttribute defaults mirror canary missing attribute reads`() {
        val item = Item(2160)

        assertThat(item.getAttribute(ItemAttribute.ACTIONID)).isEqualTo(0L)
        assertThat(item.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo("")
        assertThat(item.getAttribute(ItemAttribute.NONE)).isNull()
        assertThat(item.getAttribute(ItemAttribute.CUSTOM)).isNull()
    }

    @Test
    fun `setAttribute ignores values of the wrong classification`() {
        val item = Item(2160)

        item.setAttribute(ItemAttribute.ACTIONID, "not a number")
        item.setAttribute(ItemAttribute.DESCRIPTION, 42L)

        assertThat(item.hasAttribute(ItemAttribute.ACTIONID)).isFalse()
        assertThat(item.hasAttribute(ItemAttribute.DESCRIPTION)).isFalse()
    }

    @Test
    fun `setAttribute ignores empty strings`() {
        val item = Item(2160)

        item.setAttribute(ItemAttribute.DESCRIPTION, "")

        assertThat(item.hasAttribute(ItemAttribute.DESCRIPTION)).isFalse()
    }

    @Test
    fun `setting integer zero still records the attribute`() {
        val item = Item(2160)

        item.setAttribute(ItemAttribute.ACTIONID, 0L)

        assertThat(item.hasAttribute(ItemAttribute.ACTIONID)).isTrue()
        assertThat(item.getAttribute(ItemAttribute.ACTIONID)).isEqualTo(0L)
    }

    @Test
    fun `removeAttribute reports whether the attribute existed`() {
        val item = Item(2160)
        item.setAttribute(ItemAttribute.ACTIONID, 7L)

        assertThat(item.removeAttribute(ItemAttribute.ACTIONID)).isTrue()
        assertThat(item.hasAttribute(ItemAttribute.ACTIONID)).isFalse()
        assertThat(item.removeAttribute(ItemAttribute.ACTIONID)).isFalse()
        assertThat(item.getAttribute(ItemAttribute.ACTIONID)).isEqualTo(0L)
    }

    @Test
    fun `getActionId and setActionId clamp to uint16 like lua binding`() {
        val item = Item(2160)

        assertThat(item.getActionId()).isEqualTo(0)
        item.setActionId(1000)
        assertThat(item.getActionId()).isEqualTo(1000)

        item.setActionId(70000)
        assertThat(item.getActionId()).isEqualTo(70000 and 0xFFFF)
    }

    @Test
    fun `getUniqueId and setUniqueId store the unique attribute`() {
        val item = Item(2160)

        assertThat(item.getUniqueId()).isEqualTo(0L)
        item.setUniqueId(1234L)
        assertThat(item.getUniqueId()).isEqualTo(1234L)
        assertThat(item.getAttribute(ItemAttribute.UNIQUEID)).isEqualTo(1234L)
    }

    @Test
    fun `setDuration clamps negative durations to zero`() {
        val item = Item(2160)

        item.setDuration(-5)
        assertThat(item.getDuration()).isEqualTo(0)

        item.setDuration(30_000)
        assertThat(item.getDuration()).isEqualTo(30_000)
    }

    @Test
    fun `getDuration returns remaining time while decaying`() {
        val item = Item(2160)
        item.setDuration(60_000)
        item.setAttribute(ItemAttribute.DURATION_TIMESTAMP, System.currentTimeMillis() + 60_000)
        item.setDecaying(ItemDecayState.DECAYING_TRUE)

        assertThat(item.getDuration()).isBetween(59_000, 60_000)

        item.setDecaying(ItemDecayState.DECAYING_FALSE)
        assertThat(item.hasAttribute(ItemAttribute.DURATION_TIMESTAMP)).isFalse()
        assertThat(item.getDuration()).isEqualTo(60_000)
    }

    @Test
    fun `container shares the item attribute bag`() {
        val container = Container(1988, capacity = 4)

        container.setAttribute(ItemAttribute.ACTIONID, 500L)

        assertThat(container.getActionId()).isEqualTo(500)
    }
}
