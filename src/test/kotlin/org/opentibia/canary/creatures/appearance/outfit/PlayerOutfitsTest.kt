package org.opentibia.canary.creatures.appearance.outfit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit as OutfitValue
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import java.io.File

class PlayerOutfitsTest {

    private fun registry(): Outfits = Outfits().apply {
        loadFromXml(File("data/XML/outfits.xml").readText())
    }

    private fun playerOutfits(
        isPremium: () -> Boolean = { false },
        hasAccess: () -> Boolean = { false },
        warnUnsafeScripts: Boolean = true,
        isLookTypeRegistered: (Int) -> Boolean = { true },
    ): PlayerOutfits = PlayerOutfits(
        outfits = registry(),
        player = OutfitPlayer { PlayerSex_t.FEMALE },
        isPremium = isPremium,
        hasAccess = hasAccess,
        warnUnsafeScripts = warnUnsafeScripts,
        isLookTypeRegistered = isLookTypeRegistered,
    )

    @Test
    fun `accepts an unlocked outfit without addons`() {
        val player = playerOutfits()

        assertThat(player.canWear(136, 0)).isTrue()
        assertThat(player.canWear(136, 1)).isFalse()
    }

    @Test
    fun `requires the exact owned addon bits`() {
        val player = playerOutfits()

        assertThat(player.canWear(136, 3)).isFalse()

        player.addOutfit(136, 1)
        assertThat(player.canWear(136, 1)).isTrue()
        assertThat(player.canWear(136, 2)).isFalse()
        assertThat(player.canWear(136, 3)).isFalse()

        player.addOutfit(136, 2)
        assertThat(player.canWear(136, 3)).isTrue()

        assertThat(player.removeOutfitAddon(136, 1)).isTrue()
        assertThat(player.canWear(136, 1)).isFalse()
        assertThat(player.canWear(136, 2)).isTrue()
        assertThat(player.canWear(136, 3)).isFalse()
    }

    @Test
    fun `premium outfits require premium`() {
        val free = playerOutfits(isPremium = { false })
        free.addOutfit(155, 0)
        assertThat(free.canWear(155, 0)).isFalse()

        val premium = playerOutfits(isPremium = { true })
        premium.addOutfit(155, 0)
        assertThat(premium.canWear(155, 0)).isTrue()
    }

    @Test
    fun `access players can wear any registered outfit`() {
        val access = playerOutfits(hasAccess = { true })

        assertThat(access.canWear(155, 3)).isTrue()
        assertThat(access.canWear(9999, 0)).isTrue()
    }

    @Test
    fun `unsafe unregistered look types are blocked before the access shortcut`() {
        val access = playerOutfits(
            hasAccess = { true },
            warnUnsafeScripts = true,
            isLookTypeRegistered = { it == 136 },
        )

        assertThat(access.canWear(9999, 0)).isFalse()
        assertThat(access.canWear(136, 0)).isTrue()
        assertThat(access.canWear(0, 0)).isTrue()
    }

    @Test
    fun `add outfit unions bits and remove outfit drops the entry`() {
        val player = playerOutfits()

        player.addOutfit(136, 1)
        player.addOutfit(136, 2)
        assertThat(player.outfitEntries).containsExactly(OutfitEntry(lookType = 136, addons = 3))

        assertThat(player.removeOutfit(136)).isTrue()
        assertThat(player.outfitEntries).isEmpty()
        assertThat(player.removeOutfit(136)).isFalse()
        assertThat(player.removeOutfitAddon(136, 1)).isFalse()
    }

    @Test
    fun `get outfit addons mirrors access premium stored and unlocked states`() {
        val outfits = registry()
        val pirate = outfits.getOutfitByName(PlayerSex_t.FEMALE, "Pirate")!!
        val citizen = outfits.getOutfitByName(PlayerSex_t.FEMALE, "Citizen")!!

        val access = playerOutfits(hasAccess = { true })
        assertThat(access.getOutfitAddons(pirate)).isEqualTo(3)

        val free = playerOutfits(isPremium = { false })
        assertThat(free.getOutfitAddons(pirate)).isNull()
        assertThat(free.getOutfitAddons(citizen)).isEqualTo(0)

        val premium = playerOutfits(isPremium = { true })
        assertThat(premium.getOutfitAddons(pirate)).isNull()

        premium.addOutfit(155, 2)
        assertThat(premium.getOutfitAddons(pirate)).isEqualTo(2)
    }

    @Test
    fun `addon checks read the request addons while the outfit colors stay untouched`() {
        val request = OutfitValue(
            lookType = 136,
            lookHead = 10,
            lookBody = 20,
            lookLegs = 30,
            lookFeet = 40,
        )
        val player = playerOutfits()

        assertThat(player.canWear(request.lookType, request.lookAddons)).isTrue()

        request.lookAddons = 1
        assertThat(player.canWear(request.lookType, request.lookAddons)).isFalse()

        assertThat(request.lookHead).isEqualTo(10)
        assertThat(request.lookBody).isEqualTo(20)
        assertThat(request.lookLegs).isEqualTo(30)
        assertThat(request.lookFeet).isEqualTo(40)
    }
}
