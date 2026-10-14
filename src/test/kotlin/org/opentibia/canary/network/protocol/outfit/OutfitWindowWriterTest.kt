package org.opentibia.canary.network.protocol.outfit

import io.netty.buffer.ByteBuf
import io.netty.buffer.Unpooled
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.appearance.effects.Aura
import org.opentibia.canary.creatures.appearance.effects.Effect
import org.opentibia.canary.creatures.appearance.effects.Shader
import org.opentibia.canary.creatures.appearance.effects.Wing
import org.opentibia.canary.creatures.appearance.outfit.Outfit as OutfitDefinition
import org.opentibia.canary.network.protocol.outfit.OutfitWindowWriter.OutfitWindowCustomOtcrFrame
import org.opentibia.canary.network.protocol.outfit.OutfitWindowWriter.OutfitWindowFamiliarEntry
import org.opentibia.canary.network.protocol.outfit.OutfitWindowWriter.OutfitWindowFrame
import org.opentibia.canary.network.protocol.outfit.OutfitWindowWriter.OutfitWindowOutfitEntry
import org.opentibia.canary.network.protocol.outfit.OutfitWindowWriter.OutfitWindowSource
import org.opentibia.canary.systems.mount.MountProtocol

class OutfitWindowWriterTest {

    private fun ByteBuf.toByteArray(): ByteArray {
        val bytes = ByteArray(readableBytes())
        readBytes(bytes)
        return bytes
    }

    private fun bytes(vararg values: Int): ByteArray = ByteArray(values.size) { values[it].toByte() }

    private fun currentOutfit(lookMount: Int = 0) = Outfit(
        lookType = 128,
        lookHead = 1,
        lookBody = 2,
        lookLegs = 3,
        lookFeet = 4,
        lookAddons = 5,
        lookMount = lookMount,
        lookFamiliarsType = 0,
    )

    @Test
    fun `writes the outfit window 0xC8 with the current outfit and lists`() {
        val out = Unpooled.buffer()

        OutfitWindowWriter.writeOutfitWindow(
            out,
            OutfitWindowFrame(
                currentOutfit = currentOutfit(),
                supportOutfitOrNoMount = false,
                mounted = false,
                randomMounted = false,
                outfits = listOf(OutfitWindowOutfitEntry(lookType = 128, name = "Citizen", addons = 3, type = 0x00)),
                mounts = emptyList(),
                familiars = emptyList(),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC8,
                0x80, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, // current outfit
                0x00, 0x00, // no mount
                0x00, 0x00, 0x00, 0x00, // no mount look
                0x00, 0x00, // familiar type
                0x01, 0x00, // outfit count
                0x80, 0x00, 0x07, 0x00, 0x43, 0x69, 0x74, 0x69, 0x7A, 0x65, 0x6E, 0x03, 0x00,
                0x00, 0x00, // no mounts
                0x00, 0x00, // no familiars
                0x00, // try outfit
                0x00, // not mounted
                0x00, // random mount flag
            )
        )
    }

    @Test
    fun `writes the mount colors and the store mount price`() {
        val out = Unpooled.buffer()

        OutfitWindowWriter.writeOutfitWindow(
            out,
            OutfitWindowFrame(
                currentOutfit = currentOutfit(lookMount = 10).copy(
                    lookMountHead = 1,
                    lookMountBody = 2,
                    lookMountLegs = 3,
                    lookMountFeet = 4,
                ),
                supportOutfitOrNoMount = false,
                mounted = true,
                randomMounted = true,
                outfits = emptyList(),
                mounts = listOf(MountProtocol.OutfitWindowMountEntry(clientId = 10, name = "Horse", flag = 0x01, price = 0L)),
                familiars = listOf(OutfitWindowFamiliarEntry(lookType = 99, name = "Bear")),
            ),
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC8,
                0x80, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05, // current outfit
                0x0A, 0x00, 0x01, 0x02, 0x03, 0x04, // mount
                0x00, 0x00, // familiar type
                0x00, 0x00, // no outfits
                0x01, 0x00, // mount count
                0x0A, 0x00, 0x05, 0x00, 0x48, 0x6F, 0x72, 0x73, 0x65, 0x01, 0x00, 0x00, 0x00, 0x00,
                0x01, 0x00, // familiar count
                0x63, 0x00, 0x04, 0x00, 0x42, 0x65, 0x61, 0x72, 0x00,
                0x00,
                0x01, // mounted
                0x01, // random mount
            )
        )
    }

    @Test
    fun `appends the custom OTCR block to the outfit window when enabled`() {
        val out = Unpooled.buffer()

        OutfitWindowWriter.writeOutfitWindow(
            out,
            OutfitWindowFrame(
                currentOutfit = currentOutfit(),
                supportOutfitOrNoMount = false,
                mounted = false,
                randomMounted = false,
                outfits = emptyList(),
                mounts = emptyList(),
                familiars = emptyList(),
                isOTCR = true,
                customOtcr = OutfitWindowCustomOtcrFrame(
                    wings = listOf(Wing(id = 1, name = "W")),
                    auras = listOf(Aura(id = 2, name = "A")),
                    effects = emptyList(),
                    shaders = listOf(Shader(id = 3, name = "S")),
                ),
            ),
        )

        assertThat(out.toByteArray()).endsWith(
            *bytes(
                0x01, 0x01, 0x00, 0x01, 0x00, 0x57,
                0x01, 0x02, 0x00, 0x01, 0x00, 0x41,
                0x00,
                0x01, 0x03, 0x00, 0x01, 0x00, 0x53,
            )
        )
    }

    @Test
    fun `writeOutfitWindowCustomOTCR is suppressed without the OTCR flag`() {
        val out = Unpooled.buffer()

        OutfitWindowWriter.writeOutfitWindowCustomOTCR(
            out,
            isOTCR = false,
            frame = OutfitWindowCustomOtcrFrame(wings = listOf(Wing(id = 1, name = "W"))),
        )

        assertThat(out.toByteArray()).isEmpty()
    }

    @Test
    fun `builds the outfit rows from the registry owned store and support branches`() {
        val entries = OutfitWindowWriter.buildOutfitEntries(
            OutfitWindowSource(
                outfits = listOf(
                    OutfitDefinition(name = "Citizen", lookType = 128, premium = false, unlocked = true, from = "quest"),
                    OutfitDefinition(name = "Mage", lookType = 1210, premium = true, unlocked = false, from = "quest"),
                    OutfitDefinition(name = "Hunter", lookType = 1456, premium = true, unlocked = false, from = "quest"),
                    OutfitDefinition(name = "Store 962", lookType = 962, premium = false, unlocked = false, from = "store"),
                    OutfitDefinition(name = "Store 1000", lookType = 1000, premium = false, unlocked = false, from = "store"),
                    OutfitDefinition(name = "Locked", lookType = 200, premium = false, unlocked = false, from = "quest"),
                ),
                getOutfitAddons = { if (it.lookType == 128) 3 else null },
                canWear = { lookType, _ -> lookType == 1210 || lookType == 1456 },
                isAccessPlayer = true,
                enableSupportOutfit = true,
            ),
        )

        assertThat(entries).containsExactly(
            OutfitWindowOutfitEntry(lookType = 75, name = "Gamemaster", addons = 0, type = 0x00),
            OutfitWindowOutfitEntry(lookType = 266, name = "Customer Support", addons = 0, type = 0x00),
            OutfitWindowOutfitEntry(lookType = 302, name = "Community Manager", addons = 0, type = 0x00),
            OutfitWindowOutfitEntry(lookType = 128, name = "Citizen", addons = 3, type = 0x00),
            OutfitWindowOutfitEntry(lookType = 1210, name = "Mage", addons = 3, type = 0x02),
            OutfitWindowOutfitEntry(lookType = 1456, name = "Hunter", addons = 3, type = 0x03),
            OutfitWindowOutfitEntry(lookType = 962, name = "Store 962", addons = 0, type = 0x01, storePrice = 0L),
            OutfitWindowOutfitEntry(lookType = 1000, name = "Store 1000", addons = 3, type = 0x01, storePrice = 0L),
        )
    }

    @Test
    fun `writes the legacy outfit window with the byte-count lists`() {
        val out = Unpooled.buffer()

        OutfitWindowWriter.writeOutfitWindowLegacy(
            out,
            currentOutfit = currentOutfit(),
            protocolOutfits = listOf(
                org.opentibia.canary.creatures.appearance.outfit.ProtocolOutfit(name = "Citizen", lookType = 128, addons = 3),
            ),
            mounts = listOf(OutfitWindowWriter.ProtocolOutfitMount(clientId = 10, name = "Horse")),
            isOTCR = false,
            customOtcr = null,
        )

        assertThat(out.toByteArray()).containsExactly(
            *bytes(
                0xC8,
                0x80, 0x00, 0x01, 0x02, 0x03, 0x04, 0x05,
                0x00, 0x00, // no mount
                0x01, // outfit count byte
                0x80, 0x00, 0x07, 0x00, 0x43, 0x69, 0x74, 0x69, 0x7A, 0x65, 0x6E, 0x03,
                0x01, // mount count byte
                0x0A, 0x00, 0x05, 0x00, 0x48, 0x6F, 0x72, 0x73, 0x65,
            )
        )
    }

    @Test
    fun `builds the legacy outfit rows from the owned registry branch`() {
        val entries = OutfitWindowWriter.buildLegacyOutfitEntries(
            OutfitWindowSource(
                outfits = listOf(
                    OutfitDefinition(name = "Citizen", lookType = 128, premium = false, unlocked = true, from = "quest"),
                    OutfitDefinition(name = "Locked", lookType = 200, premium = false, unlocked = false, from = "quest"),
                ),
                getOutfitAddons = { if (it.lookType == 128) 3 else null },
                canWear = { _, _ -> false },
                isAccessPlayer = false,
                enableSupportOutfit = false,
            ),
        )

        assertThat(entries).containsExactly(
            org.opentibia.canary.creatures.appearance.outfit.ProtocolOutfit(name = "Citizen", lookType = 128, addons = 3),
        )
    }

    @Test
    fun `writes the store outfit price after the type byte`() {
        val out = Unpooled.buffer()

        OutfitWindowWriter.writeOutfitWindow(
            out,
            OutfitWindowFrame(
                currentOutfit = currentOutfit(),
                supportOutfitOrNoMount = false,
                mounted = false,
                randomMounted = false,
                outfits = listOf(
                    OutfitWindowOutfitEntry(lookType = 1000, name = "Store", addons = 3, type = 0x01, storePrice = 0L),
                ),
                mounts = emptyList(),
                familiars = emptyList(),
            ),
        )

        assertThat(out.toByteArray()).containsSubsequence(
            *bytes(0xE8, 0x03, 0x05, 0x00, 0x53, 0x74, 0x6F, 0x72, 0x65, 0x03, 0x01, 0x00, 0x00, 0x00, 0x00)
        )
    }
}
