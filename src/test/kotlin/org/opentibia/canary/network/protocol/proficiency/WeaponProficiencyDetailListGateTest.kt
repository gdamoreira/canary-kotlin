package org.opentibia.canary.network.protocol.proficiency

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.protocol.ProtocolFeature
import org.opentibia.canary.network.protocol.ProtocolProfile
import org.opentibia.canary.network.protocol.ProtocolProfileRegistry
import org.opentibia.canary.network.protocol.protocolFeatureMask

/**
 * Unit tests for the `shouldSendWeaponProficiencyDetailList` gate
 * (src/server/network/protocol/protocolgame.cpp:109-132): the profile feature
 * gates the 0xC4 payload family and the login build string gates the trailing
 * detail-list boundary inside it.
 */
class WeaponProficiencyDetailListGateTest {

    private val currentProfile = ProtocolProfileRegistry.getCurrentProfile()

    @Test
    fun `the known 15_25 builds support the detail list`() {
        assertThat(WeaponProficiencyPayload.supportsWeaponProficiencyDetailList("15.25.794c2e.1400")).isTrue()
        assertThat(WeaponProficiencyPayload.supportsWeaponProficiencyDetailList("15.25.d96c64.1")).isTrue()
    }

    @Test
    fun `unrecognized 15_25 builds and other builds keep the shorter shape`() {
        assertThat(WeaponProficiencyPayload.supportsWeaponProficiencyDetailList("15.25.abcdef.1")).isFalse()
        assertThat(WeaponProficiencyPayload.supportsWeaponProficiencyDetailList("15.20.794c2e.1")).isFalse()
        assertThat(WeaponProficiencyPayload.supportsWeaponProficiencyDetailList("")).isFalse()
    }

    @Test
    fun `the profile feature and the build string must both match`() {
        assertThat(WeaponProficiencyPayload.shouldSendWeaponProficiencyDetailList(currentProfile, "15.25.794c2e.1")).isTrue()
        assertThat(WeaponProficiencyPayload.shouldSendWeaponProficiencyDetailList(currentProfile, "15.25.zzz")).isFalse()

        val withoutFeature = ProtocolProfile(
            features = protocolFeatureMask(ProtocolFeature.CurrentPayload),
        )
        assertThat(WeaponProficiencyPayload.shouldSendWeaponProficiencyDetailList(withoutFeature, "15.25.794c2e.1")).isFalse()
        assertThat(WeaponProficiencyPayload.shouldSendWeaponProficiencyDetailList(null, "15.25.794c2e.1")).isFalse()
    }
}
