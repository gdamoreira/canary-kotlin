package org.opentibia.canary.systems.hireling

import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.message.writeTibiaString

/**
 * Transcript tests for the Canary engine core of the House Hirelings subsystem.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/game/game.cpp (m_hirelingSkills / m_hirelingOutfits + Game::getHirelingSkills/getHirelingOutfits)
 *  - src/creatures/players/player.cpp (Player::canSpeakWithHireling)
 *  - src/creatures/creatures_definitions.hpp (SPEECHBUBBLE_HIRELING)
 *  - src/map/map_definitions.hpp (AccessHouseLevel_t)
 *  - src/server/network/protocol/protocolgame.cpp (ProtocolGame::parseSetHirelingName, opcode 0xEC)
 *  - src/utils/utils_definitions.hpp (ItemID_t::HIRELING_LAMP)
 */
class HirelingTest {

    @Test
    fun `hireling skills mirror Canary Game hirelingSkills map`() {
        val skills = HirelingRegistry.getHirelingSkills()

        assertThat(skills).hasSize(4)
        assertThat(skills).containsEntry(1001, "banker")
        assertThat(skills).containsEntry(1002, "cooker")
        assertThat(skills).containsEntry(1003, "steward")
        assertThat(skills).containsEntry(1004, "trader")
    }

    @Test
    fun `hireling outfits mirror Canary Game hirelingOutfits map`() {
        val outfits = HirelingRegistry.getHirelingOutfits()

        assertThat(outfits).hasSize(9)
        assertThat(outfits).containsEntry(2001, "banker")
        assertThat(outfits).containsEntry(2002, "cooker")
        assertThat(outfits).containsEntry(2003, "steward")
        assertThat(outfits).containsEntry(2004, "trader")
        assertThat(outfits).containsEntry(2005, "servant")
        assertThat(outfits).containsEntry(2006, "hydra")
        assertThat(outfits).containsEntry(2007, "ferumbras")
        assertThat(outfits).containsEntry(2008, "bonelord")
        assertThat(outfits).containsEntry(2009, "dragon")
    }

    @Test
    fun `hireling registry accessors expose the same maps`() {
        assertThat(HirelingRegistry.getHirelingSkills()).isSameAs(HirelingRegistry.getHirelingSkills())
        assertThat(HirelingRegistry.getHirelingOutfits()).isSameAs(HirelingRegistry.getHirelingOutfits())
    }

    @Test
    fun `house access levels mirror Canary AccessHouseLevel_t`() {
        assertThat(AccessHouseLevel.HOUSE_NOT_INVITED.value).isEqualTo(0)
        assertThat(AccessHouseLevel.HOUSE_GUEST.value).isEqualTo(1)
        assertThat(AccessHouseLevel.HOUSE_SUBOWNER.value).isEqualTo(2)
        assertThat(AccessHouseLevel.HOUSE_OWNER.value).isEqualTo(3)
    }

    @Test
    fun `hireling lamp item id mirrors Canary ItemID_t HIRELING_LAMP`() {
        assertThat(Hireling.HIRELING_LAMP).isEqualTo(29432)
    }

    @Test
    fun `canSpeakWithHireling rejects hireling bubble without a house`() {
        val result = Hireling.canSpeakWithHireling(
            speechBubble = Hireling.SPEECHBUBBLE_HIRELING,
            houseAccessLevel = null
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `canSpeakWithHireling rejects hireling bubble when not invited`() {
        val result = Hireling.canSpeakWithHireling(
            speechBubble = Hireling.SPEECHBUBBLE_HIRELING,
            houseAccessLevel = AccessHouseLevel.HOUSE_NOT_INVITED
        )

        assertThat(result).isFalse()
    }

    @Test
    fun `canSpeakWithHireling allows invited house members`() {
        assertThat(
            Hireling.canSpeakWithHireling(Hireling.SPEECHBUBBLE_HIRELING, AccessHouseLevel.HOUSE_GUEST)
        ).isTrue()
        assertThat(
            Hireling.canSpeakWithHireling(Hireling.SPEECHBUBBLE_HIRELING, AccessHouseLevel.HOUSE_SUBOWNER)
        ).isTrue()
        assertThat(
            Hireling.canSpeakWithHireling(Hireling.SPEECHBUBBLE_HIRELING, AccessHouseLevel.HOUSE_OWNER)
        ).isTrue()
    }

    @Test
    fun `canSpeakWithHireling ignores house gating for non hireling bubbles`() {
        assertThat(Hireling.canSpeakWithHireling(1, null)).isTrue()
        assertThat(Hireling.canSpeakWithHireling(2, AccessHouseLevel.HOUSE_NOT_INVITED)).isTrue()
    }

    @Test
    fun `parseSetHirelingName reads name and two uint32 like opcode 0xEC`() {
        val buffer = Unpooled.buffer()
        buffer.writeTibiaString("Sir Hireling")
        buffer.writeIntLE(0x11223344)
        buffer.writeIntLE(0x55667788)

        val parsed = Hireling.parseSetHirelingName(buffer)

        assertThat(parsed.name).isEqualTo("Sir Hireling")
        assertThat(parsed.value1).isEqualTo(0x11223344L)
        assertThat(parsed.value2).isEqualTo(0x55667788L)
        assertThat(buffer.readableBytes()).isEqualTo(0)
    }
}
