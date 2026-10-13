package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcripts of `Cylinder`/`VirtualCylinder` (src/items/cylinder.hpp:18-217,
 * src/items/cylinder.cpp:14-48).
 */
class CylinderTest {

    @Test
    fun `index wherever is minus one`() {
        assertThat(INDEX_WHEREEVER).isEqualTo(-1)
    }

    @Test
    fun `cylinder link values follow the canary declaration order`() {
        assertThat(CylinderLink.entries.map { it.ordinal }).containsExactly(0, 1, 2, 3)
        assertThat(CylinderLink.LINK_OWNER).isEqualTo(CylinderLink.entries[0])
        assertThat(CylinderLink.LINK_PARENT).isEqualTo(CylinderLink.entries[1])
        assertThat(CylinderLink.LINK_TOPPARENT).isEqualTo(CylinderLink.entries[2])
        assertThat(CylinderLink.LINK_NEAR).isEqualTo(CylinderLink.entries[3])
    }

    @Test
    fun `item property values follow the canary declaration order`() {
        assertThat(ItemProperty.entries.map { it.ordinal }).containsExactly(0, 1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11)
        assertThat(ItemProperty.CONST_PROP_BLOCKSOLID.ordinal).isZero()
        assertThat(ItemProperty.CONST_PROP_SUPPORTHANGABLE.ordinal).isEqualTo(11)
    }

    @Test
    fun `virtual cylinder rejects add and remove queries`() {
        val cylinder = VirtualCylinder.virtualCylinder

        assertThat(cylinder.queryAdd(INDEX_WHEREEVER, null, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(cylinder.queryMaxCount(INDEX_WHEREEVER, null, 1, 1, 0))
            .isEqualTo(QueryMaxCountResult(ReturnValue.NOTPOSSIBLE, 1))
        assertThat(cylinder.queryRemove(null, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `virtual cylinder query destination returns null and keeps the inputs`() {
        val item = Item(100)
        val result = VirtualCylinder.virtualCylinder.queryDestination(3, item, item, 0)

        assertThat(result.cylinder).isNull()
        assertThat(result.index).isEqualTo(3)
        assertThat(result.destItem).isSameAs(item)
        assertThat(result.flags).isZero()
    }

    @Test
    fun `virtual cylinder mutation hooks are no-ops`() {
        val cylinder = VirtualCylinder.virtualCylinder
        val item = Item(100)

        cylinder.addThing(item)
        cylinder.addThing(0, item)
        cylinder.updateThing(item, 101, 2)
        cylinder.replaceThing(0, item)
        cylinder.removeThing(item, 1)
        cylinder.internalAddThing(item)
        cylinder.internalAddThing(0, item)
        cylinder.startDecaying()
    }

    @Test
    fun `virtual cylinder thing metadata matches canary`() {
        val cylinder = VirtualCylinder.virtualCylinder

        assertThat(cylinder.getDescription(0)).isEmpty()
        assertThat(cylinder.getThrowRange()).isEqualTo(1)
        assertThat(cylinder.isPushable()).isFalse()
        assertThat(cylinder.isRemoved()).isFalse()
        assertThat(cylinder.isBrowsefield()).isFalse()
    }

    @Test
    fun `virtual cylinder uses the empty cylinder index defaults`() {
        val cylinder = VirtualCylinder.virtualCylinder

        assertThat(cylinder.getThingIndex(null)).isEqualTo(-1)
        assertThat(cylinder.getThingIndex(Item(100))).isEqualTo(-1)
        assertThat(cylinder.getFirstIndex()).isZero()
        assertThat(cylinder.getLastIndex()).isZero()
        assertThat(cylinder.getThing(0)).isNull()
        assertThat(cylinder.getItemTypeCount(100)).isZero()
        assertThat(cylinder.getItemTypeCount(100, 5)).isZero()
    }

    @Test
    fun `getAllItemTypeCount returns the map unchanged`() {
        val cylinder = VirtualCylinder.virtualCylinder
        val countMap = mutableMapOf(100 to 2)

        assertThat(cylinder.getAllItemTypeCount(countMap)).isSameAs(countMap)
        assertThat(countMap).containsExactlyEntriesOf(mapOf(100 to 2))
    }
}
