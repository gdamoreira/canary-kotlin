package org.opentibia.canary.creatures

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.familiar.Familiars
import org.opentibia.canary.systems.wheel.IOWheel

class PlayerVocationTest {

    private fun newPlayer() = Player(
        id = 7,
        name = "Vocation Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `player defaults to no vocation`() {
        val player = newPlayer()

        assertThat(player.vocationId).isEqualTo(0)
        assertThat(player.getPlayerVocationEnum()).isEqualTo(0)
    }

    @Test
    fun `vocation flows into the built wheel`() {
        val player = newPlayer()
        player.vocationId = IOWheel.VOCATION_KNIGHT_CIP

        assertThat(player.getPlayerVocationEnum()).isEqualTo(IOWheel.VOCATION_KNIGHT_CIP)
        assertThat(player.features.wheel.vocationCipId).isEqualTo(IOWheel.VOCATION_KNIGHT_CIP)
    }

    @Test
    fun `premium defaults to false`() {
        val player = newPlayer()

        assertThat(player.premium).isFalse()
        assertThat(player.isPremium()).isFalse()
    }

    @Test
    fun `premium flag is reflected by isPremium`() {
        val player = newPlayer()
        player.premium = true

        assertThat(player.isPremium()).isTrue()
    }

    @Test
    fun `features gate premium familiars on the premium provider`() {
        val definitions = Familiars()
        assertThat(
            definitions.loadFromXml(
                """
                <familiars>
                    <familiar name="Premium" vocation="1" lookType="777" premium="1" unlocked="1" type="0"/>
                </familiars>
                """.trimIndent(),
            ),
        ).isTrue

        val nonPremium = PlayerFeatures(playerId = 1, vocationIdProvider = { 1 }, familiarDefinitions = definitions)
        assertThat(nonPremium.familiars.canFamiliar(777)).isFalse()

        val premium = PlayerFeatures(
            playerId = 1,
            vocationIdProvider = { 1 },
            isPremiumProvider = { true },
            familiarDefinitions = definitions,
        )
        assertThat(premium.familiars.canFamiliar(777)).isTrue()
    }
}
