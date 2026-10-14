package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.map.Position

/**
 * Wave 57 A4: the `HostTitlePlayer`/`HostBadgePlayer` adapters of
 * [org.opentibia.canary.creatures.PlayerFeatures] must read the composed
 * player state instead of the zero/false defaults the wave-56 report recorded.
 *
 * Canary source of truth: `../.projects/canary`
 *  - `PlayerTitle::checkGold` (player_title.cpp:183-185)
 *  - `PlayerTitle::checkOutfit` (player_title.cpp:197-199)
 *  - `PlayerTitle::checkOther` (player_title.cpp:276-328),
 *    `Player::canWear` (player.cpp:6783-6806)
 *  - `PlayerBadge::loyalty`/`accountAge` (player_badge.cpp:111-117),
 *    `Player::getLoyaltyPoints` (player.cpp:10683-10685)
 */
class PlayerTitleHostReadsTest {

    private fun newPlayer() = Player(
        id = 42,
        name = "Title Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
    )

    @Test
    fun `checkGold reads the composed bank balance`() {
        val player = newPlayer()
        player.features.vitals.setBankBalance(1_000_000)

        assertThat(player.features.title.checkGold(1_000_000)).isTrue()
        assertThat(player.features.title.checkGold(1_000_001)).isFalse()
    }

    @Test
    fun `checkOutfit reads the composed outfit count`() {
        val player = newPlayer()
        assertThat(player.features.title.checkOutfit(1)).isFalse()

        player.features.storage.add(
            PlayerStorage.PSTRG_OUTFITS_RANGE_START + 1,
            (50 shl 16) or 3,
            true,
        )

        assertThat(player.features.title.checkOutfit(1)).isTrue()
        assertThat(player.features.title.checkOutfit(2)).isFalse()
    }

    @Test
    fun `checkOther canWear reads the composed outfit state and outfit registry`() {
        val player = newPlayer()
        player.setSex(PlayerSex_t.MALE)
        player.features.attachedOutfits.loadFromXml(
            """
            <outfits>
                <outfit type="1" looktype="1211" name="Golden Outfit"/>
                <outfit type="1" looktype="1210" name="Golden Outfit Addon"/>
            </outfits>
            """.trimIndent(),
        )
        player.features.outfits.addOutfit(1211, 3)
        player.features.outfits.addOutfit(1210, 3)

        assertThat(player.features.title.checkOther("Big Spender")).isTrue()
    }

    @Test
    fun `checkOther canWear grants access players any outfit`() {
        val player = newPlayer()
        player.group = Group(access = true)

        assertThat(player.features.title.checkOther("Big Spender")).isTrue()
        assertThat(player.features.title.checkOther("Admirer of the Crown")).isTrue()
    }

    @Test
    fun `checkOther canWear stays false without the outfits`() {
        val player = newPlayer()

        assertThat(player.features.title.checkOther("Big Spender")).isFalse()
    }

    @Test
    fun `the badge host reads the composed loyalty state`() {
        val player = newPlayer()

        assertThat(player.features.loyalty).isNotNull
        assertThat(player.features.badge.loyalty(0)).isTrue()
        assertThat(player.features.badge.loyalty(1)).isFalse()
        assertThat(player.features.badge.accountAge(1)).isFalse()
    }
}
