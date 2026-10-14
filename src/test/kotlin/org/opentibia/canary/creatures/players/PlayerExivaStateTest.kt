package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.map.Position
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.spells.WorldType

/**
 * Transcript tests for the exiva (Find Person) restrictions.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/player.hpp:182-195 (`ExivaRestrictions`)
 *  - src/creatures/players/player.cpp:7000-7048 (`canExiva`)
 *  - src/creatures/players/player.cpp:13440-13446 (`getExivaRestrictions`)
 */
class PlayerExivaStateTest {

    private fun newPlayer(id: Long, name: String = "Player$id") =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = Position(100, 100, 7))

    @Test
    fun `ExivaRestrictions defaults match the C++ initializers`() {
        // player.hpp:183-191.
        val restrictions = ExivaRestrictions()

        assertThat(restrictions.allowAll).isFalse()
        assertThat(restrictions.allowOwnGuild).isTrue()
        assertThat(restrictions.allowOwnParty).isTrue()
        assertThat(restrictions.allowVipList).isTrue()
        assertThat(restrictions.allowPlayerWhitelist).isTrue()
        assertThat(restrictions.allowGuildWhitelist).isTrue()
        assertThat(restrictions.playerWhitelist).isEmpty()
        assertThat(restrictions.guildWhitelist).isEmpty()
    }

    @Test
    fun `getExivaRestrictions returns the mutable player-owned state`() {
        // player.cpp:13440-13446: both overloads return the member reference.
        val player = newPlayer(1)

        player.getExivaRestrictions().allowAll = true
        player.getExivaRestrictions().playerWhitelist.add(42L)

        assertThat(player.exivaRestrictions.allowAll).isTrue()
        assertThat(player.exivaRestrictions.playerWhitelist).containsExactly(42L)
    }

    @Test
    fun `canExiva always allows outside no-pvp worlds`() {
        // player.cpp:7001-7003.
        val source = newPlayer(1)

        assertThat(source.canExiva("Ghost", worldType = { WorldType.PVP })).isTrue()
        assertThat(source.canExiva("Ghost", worldType = { WorldType.PVP_ENFORCED })).isTrue()
    }

    @Test
    fun `canExiva refuses an unknown target on a no-pvp world`() {
        // player.cpp:7005-7008.
        val source = newPlayer(1)

        assertThat(source.canExiva("Missing", worldType = { WorldType.NO_PVP })).isFalse()
    }

    @Test
    fun `allowAll reveals the target`() {
        // player.cpp:7012-7014.
        val source = newPlayer(1)
        val target = newPlayer(2)
        target.exivaRestrictions.allowAll = true

        assertThat(source.canExiva("Target", worldType = { WorldType.NO_PVP }, targetPlayerByName = { target })).isTrue()
    }

    @Test
    fun `allowOwnGuild matches only the same guild`() {
        // player.cpp:7016-7022.
        val source = newPlayer(1)
        val guild = Guild(id = 10, name = "G")
        source.setGuild(guild)

        val sameGuild = newPlayer(2)
        sameGuild.setGuild(guild)
        assertThat(
            source.canExiva("Same", worldType = { WorldType.NO_PVP }, targetPlayerByName = { sameGuild }),
        ).isTrue()

        val otherGuild = newPlayer(3)
        otherGuild.setGuild(Guild(id = 11, name = "Other"))
        assertThat(
            source.canExiva("Other", worldType = { WorldType.NO_PVP }, targetPlayerByName = { otherGuild }),
        ).isFalse()
    }

    @Test
    fun `allowOwnGuild is skipped when disabled`() {
        // player.cpp:7016 guard.
        val source = newPlayer(1)
        val guild = Guild(id = 10, name = "G")
        source.setGuild(guild)
        val target = newPlayer(2)
        target.setGuild(guild)
        target.exivaRestrictions.allowOwnGuild = false

        assertThat(source.canExiva("Target", worldType = { WorldType.NO_PVP }, targetPlayerByName = { target })).isFalse()
    }

    @Test
    fun `allowOwnParty matches only the same party instance`() {
        // player.cpp:7024-7030.
        val source = newPlayer(1)
        val party = Party()
        source.setParty(party)

        val sameParty = newPlayer(2)
        sameParty.setParty(party)
        assertThat(
            source.canExiva("Same", worldType = { WorldType.NO_PVP }, targetPlayerByName = { sameParty }),
        ).isTrue()

        val otherParty = newPlayer(3)
        otherParty.setParty(Party())
        assertThat(
            source.canExiva("Other", worldType = { WorldType.NO_PVP }, targetPlayerByName = { otherParty }),
        ).isFalse()
    }

    @Test
    fun `allowVipList checks the target's vip list for the source guid`() {
        // player.cpp:7032-7034.
        val source = newPlayer(1)
        val target = newPlayer(2)
        val vipGuids = mutableListOf<Long>()
        target.exivaRestrictions.allowOwnGuild = false
        target.exivaRestrictions.allowOwnParty = false

        vipGuids.add(source.getGUID())
        assertThat(
            source.canExiva(
                "Target",
                worldType = { WorldType.NO_PVP },
                targetPlayerByName = { target },
                targetVipExists = { t, guid -> t === target && guid in vipGuids },
            ),
        ).isTrue()

        assertThat(
            source.canExiva(
                "Target",
                worldType = { WorldType.NO_PVP },
                targetPlayerByName = { target },
                targetVipExists = { _, _ -> false },
            ),
        ).isFalse()
    }

    @Test
    fun `allowGuildWhitelist matches the source guild id`() {
        // player.cpp:7036-7042.
        val source = newPlayer(1)
        val guild = Guild(id = 10, name = "G")
        source.setGuild(guild)
        val target = newPlayer(2)
        target.exivaRestrictions.allowOwnGuild = false
        target.exivaRestrictions.allowOwnParty = false
        target.exivaRestrictions.allowVipList = false
        target.exivaRestrictions.guildWhitelist.add(10L)

        assertThat(source.canExiva("Target", worldType = { WorldType.NO_PVP }, targetPlayerByName = { target })).isTrue()

        target.exivaRestrictions.allowGuildWhitelist = false
        assertThat(source.canExiva("Target", worldType = { WorldType.NO_PVP }, targetPlayerByName = { target })).isFalse()
    }

    @Test
    fun `allowPlayerWhitelist matches the source guid`() {
        // player.cpp:7044-7048.
        val source = newPlayer(1)
        val target = newPlayer(2)
        target.exivaRestrictions.allowOwnGuild = false
        target.exivaRestrictions.allowOwnParty = false
        target.exivaRestrictions.allowVipList = false
        target.exivaRestrictions.allowGuildWhitelist = false
        target.exivaRestrictions.playerWhitelist.add(1L)

        assertThat(source.canExiva("Target", worldType = { WorldType.NO_PVP }, targetPlayerByName = { target })).isTrue()

        target.exivaRestrictions.allowPlayerWhitelist = false
        assertThat(source.canExiva("Target", worldType = { WorldType.NO_PVP }, targetPlayerByName = { target })).isFalse()
    }
}
