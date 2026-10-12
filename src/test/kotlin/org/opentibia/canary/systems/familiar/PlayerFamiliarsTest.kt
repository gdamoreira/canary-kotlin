package org.opentibia.canary.systems.familiar

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.io.File

class PlayerFamiliarsTest {

    private fun loadedFamiliars(): Familiars {
        val familiars = Familiars()
        familiars.loadFromXml(File("data/XML/familiars.xml").readText())
        return familiars
    }

    @Test
    fun `addFamiliar adds once and removeFamiliar reports whether an entry was removed`() {
        val player = PlayerFamiliars(loadedFamiliars(), vocationId = 1, isPremium = true)

        player.addFamiliar(994)
        player.addFamiliar(994)
        assertThat(player.familiars).hasSize(1)
        assertThat(player.familiars[0].lookType).isEqualTo(994)

        player.addFamiliar(1367)
        assertThat(player.familiars.map { it.lookType }).containsExactly(994, 1367)

        assertThat(player.removeFamiliar(994)).isTrue()
        assertThat(player.familiars.map { it.lookType }).containsExactly(1367)

        assertThat(player.removeFamiliar(994)).isFalse()
        assertThat(player.familiars).hasSize(1)
    }

    @Test
    fun `canFamiliar mirrors access premium unlocked and owned rules`() {
        val definitions = loadedFamiliars()

        val freePlayer = PlayerFamiliars(definitions, vocationId = 1, isPremium = false)
        assertThat(freePlayer.canFamiliar(994)).isFalse()

        val noAccess = PlayerFamiliars(definitions, vocationId = 1, isPremium = true)
        assertThat(noAccess.canFamiliar(994)).isFalse()
        assertThat(noAccess.canFamiliar(993)).isFalse()

        noAccess.addFamiliar(994)
        assertThat(noAccess.canFamiliar(994)).isTrue()

        val access = PlayerFamiliars(definitions, vocationId = 1, isPremium = true, hasAccess = true)
        assertThat(access.canFamiliar(994)).isTrue()
        assertThat(access.canFamiliar(9999)).isTrue()
    }

    @Test
    fun `canFamiliar returns true for unlocked definitions regardless of ownership`() {
        val xml = """
            <?xml version="1.0" encoding="UTF-8"?>
            <familiars>
                <familiar vocation="4" lookType="991" name="Skullfrost" premium="no" unlocked="yes" enabled="yes" />
            </familiars>
        """.trimIndent()
        val familiars = Familiars()
        familiars.loadFromXml(xml)

        val player = PlayerFamiliars(familiars, vocationId = 4, isPremium = false)
        assertThat(player.familiars).isEmpty()
        assertThat(player.canFamiliar(991)).isTrue()
    }

    @Test
    fun `getFamiliar mirrors access premium ownership and unlocked rules`() {
        val definitions = loadedFamiliars()
        val thundergiant = definitions.getFamiliarByLookType(1, 994)!!

        val access = PlayerFamiliars(definitions, vocationId = 1, isPremium = false, hasAccess = true)
        assertThat(access.getFamiliar(thundergiant)).isTrue()

        val noOwnership = PlayerFamiliars(definitions, vocationId = 1, isPremium = true)
        assertThat(noOwnership.getFamiliar(thundergiant)).isFalse()
        noOwnership.addFamiliar(994)
        assertThat(noOwnership.getFamiliar(thundergiant)).isTrue()

        val notPremium = PlayerFamiliars(definitions, vocationId = 1, isPremium = false)
        assertThat(notPremium.getFamiliar(thundergiant)).isFalse()
    }

    @Test
    fun `setFamiliarLooktype stores the current familiar looktype`() {
        val player = PlayerFamiliars(loadedFamiliars(), vocationId = 1, isPremium = true)
        assertThat(player.familiarLooktype).isEqualTo(0)

        player.setFamiliarLooktype(994)
        assertThat(player.familiarLooktype).isEqualTo(994)
    }

    @Test
    fun `assigns familiar storage range keys using lookType shifted high`() {
        val player = PlayerFamiliars(loadedFamiliars(), vocationId = 1, isPremium = true)
        val start = PlayerFamiliars.PSTRG_FAMILIARS_RANGE_START

        assertThat(PlayerFamiliars.isStorageKeyInRange(start, start, 500)).isTrue()
        assertThat(PlayerFamiliars.isStorageKeyInRange(start + 500, start, 500)).isTrue()
        assertThat(PlayerFamiliars.isStorageKeyInRange(start + 501, start, 500)).isFalse()
        assertThat(PlayerFamiliars.isStorageKeyInRange(start - 1, start, 500)).isFalse()

        assertThat(player.addStorage(start + 1, 994 shl 16)).isTrue()
        assertThat(player.addStorage(start + 2, 1367 shl 16)).isTrue()
        assertThat(player.addStorage(start - 1, 994 shl 16)).isFalse()
        assertThat(player.addStorage(12345, 994 shl 16)).isFalse()

        assertThat(player.familiars.map { it.lookType }).containsExactly(994, 1367)
    }

    @Test
    fun `getReservedRange serializes owned familiars into the familiar storage range`() {
        val player = PlayerFamiliars(loadedFamiliars(), vocationId = 1, isPremium = true)
        player.addFamiliar(994)
        player.addFamiliar(1367)

        val start = PlayerFamiliars.PSTRG_FAMILIARS_RANGE_START
        assertThat(player.getReservedRange()).containsExactly(
            java.util.Map.entry(start + 1, 994 shl 16),
            java.util.Map.entry(start + 2, 1367 shl 16)
        )
    }
}
