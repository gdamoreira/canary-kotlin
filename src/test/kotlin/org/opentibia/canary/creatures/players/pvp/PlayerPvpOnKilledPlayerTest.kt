package org.opentibia.canary.creatures.players.pvp

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.spells.WorldType

/**
 * Transcript tests for `Player::onKilledPlayer`.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/player.cpp:6595-6604 (`onKilledPlayer`)
 *  - src/creatures/players/player.cpp:7263-7301 (`addUnjustifiedDead`)
 *  - src/config/configmanager.cpp:386 (`WHITE_SKULL_TIME`, default 15 min)
 */
class PlayerPvpOnKilledPlayerTest {

    private class World(
        val messages: MutableList<String> = mutableListOf(),
    ) {
        var clock: Long = 1_700_000_000L
        val components = mutableMapOf<Long, PlayerPvp>()

        fun player(id: Long, pvpZone: Boolean = false): Player {
            val player = Player(id = id, name = "Player$id", maxHealth = 150, health = 150, position = Position(100, 100, 7))
            player.now = { clock * 1000 }
            if (pvpZone) {
                player.setParent(Tile(Position(100, 100, 7)).apply { setFlag(TileFlags.TILESTATE_PVPZONE.value) })
            }
            return player
        }

        fun pvp(player: Player): PlayerPvp {
            val component = PlayerPvp(
                player = player,
                playerByGuid = { guid -> components[guid] },
                worldType = { WorldType.PVP },
                config = PvpConfig(),
                timeNow = { clock },
                otsysTime = { clock * 1000 },
                sendTextMessage = { messages.add(it) },
            )
            components[player.id] = component
            return component
        }
    }

    @Test
    fun `a pvp-zone victim drops loot and skill loss without an unjustified kill`() {
        // player.cpp:6597-6600.
        val world = World()
        val attacker = world.pvp(world.player(1))
        val target = world.player(2, pvpZone = true)
        target.experience = 1000
        val targetPvp = world.pvp(target)

        val unjustified = attacker.onKilledPlayer(targetPvp, lastHit = false)

        assertThat(unjustified).isFalse()
        assertThat(target.lootDrop).isFalse()
        assertThat(target.getLostExperience()).isZero()
        assertThat(attacker.getUnjustifiedKills()).isEmpty()
    }

    @Test
    fun `a partner kill is justified`() {
        // player.cpp:6601 (`!isPartner(target)`).
        val world = World()
        val attackerPlayer = world.player(1)
        val targetPlayer = world.player(2)
        attackerPlayer.setParty(org.opentibia.canary.creatures.grouping.Party())
        targetPlayer.setParty(attackerPlayer.getParty())
        val attacker = world.pvp(attackerPlayer)
        val target = world.pvp(targetPlayer)
        attacker.addAttacked(target)

        val unjustified = attacker.onKilledPlayer(target, lastHit = false)

        assertThat(unjustified).isFalse()
        assertThat(attacker.getUnjustifiedKills()).isEmpty()
    }

    @Test
    fun `NotGainInFight never records an unjustified kill`() {
        // player.cpp:6601.
        val world = World()
        val attackerPlayer = world.player(1)
        attackerPlayer.setFlag(PlayerFlags_t.NotGainInFight)
        val attacker = world.pvp(attackerPlayer)
        val target = world.pvp(world.player(2))
        attacker.addAttacked(target)

        val unjustified = attacker.onKilledPlayer(target, lastHit = false)

        assertThat(unjustified).isFalse()
        assertThat(attacker.getUnjustifiedKills()).isEmpty()
    }

    @Test
    fun `the right of retribution clears the target's unavenged kill`() {
        // player.cpp:6604-6612.
        val world = World()
        val attackerPlayer = world.player(1)
        val targetPlayer = world.player(2)
        val attacker = world.pvp(attackerPlayer)
        val target = world.pvp(targetPlayer)
        attacker.addAttacked(target)
        target.setUnjustifiedKills(listOf(Kill(target = 1L, time = world.clock, unavenged = true)))

        val unjustified = attacker.onKilledPlayer(target, lastHit = false)

        assertThat(unjustified).isFalse()
        assertThat(target.getUnjustifiedKills().single().unavenged).isFalse()
        assertThat(attacker.getAttackedPlayerGuids()).isEmpty()
    }

    @Test
    fun `an unprovoked kill is flagged unjustified and recorded`() {
        // player.cpp:6613-6614 (`addUnjustifiedDead`).
        val world = World()
        val attacker = world.pvp(world.player(1))
        val target = world.pvp(world.player(2))
        attacker.addAttacked(target)

        val unjustified = attacker.onKilledPlayer(target, lastHit = false)

        assertThat(unjustified).isTrue()
        assertThat(attacker.getUnjustifiedKills()).hasSize(1)
        assertThat(attacker.getUnjustifiedKills().single().target).isEqualTo(2L)
        assertThat(world.messages.single()).contains("was not justified")
    }

    @Test
    fun `a guild mate kill stays justified`() {
        // player.cpp:6613 (`!isGuildMate(target)`).
        val world = World()
        val attackerPlayer = world.player(1)
        val targetPlayer = world.player(2)
        val guild = org.opentibia.canary.social.guild.Guild(id = 10, name = "G")
        attackerPlayer.setGuild(guild)
        targetPlayer.setGuild(guild)
        val attacker = world.pvp(attackerPlayer)
        val target = world.pvp(targetPlayer)
        attacker.addAttacked(target)

        val unjustified = attacker.onKilledPlayer(target, lastHit = false)

        assertThat(unjustified).isFalse()
        assertThat(attacker.getUnjustifiedKills()).isEmpty()
    }

    @Test
    fun `an in-pvp-zone fight stays justified`() {
        // player.cpp:6613 (`!Combat::isInPvpZone(getPlayer(), target)`).
        val world = World()
        val attacker = world.pvp(world.player(1))
        val target = world.pvp(world.player(2))
        attacker.addAttacked(target)

        val unjustified = attacker.onKilledPlayer(target, lastHit = false, isInPvpZone = { _, _ -> true })

        assertThat(unjustified).isFalse()
        assertThat(attacker.getUnjustifiedKills()).isEmpty()
    }

    @Test
    fun `the last hit opens the white-skull pz lock`() {
        // player.cpp:6616-6628.
        val world = World()
        val attackerPlayer = world.player(1)
        attackerPlayer.addCondition(
            org.opentibia.canary.combat.condition.Condition.createCondition(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_INFIGHT,
                1000,
            ),
        )
        val attacker = world.pvp(attackerPlayer)
        val target = world.pvp(world.player(2))
        attacker.addAttacked(target)

        attacker.onKilledPlayer(target, lastHit = true)

        assertThat(attackerPlayer.pzLocked).isTrue()
        val condition = attackerPlayer.getCondition(ConditionType.CONDITION_INFIGHT)
        assertThat(condition).isNotNull()
        assertThat(condition!!.ticks).isEqualTo(15 * 60 * 1000)
        assertThat(target.player.getSkull()).isEqualTo(Skulls_t.SKULL_NONE)
    }

    @Test
    fun `a non-last hit leaves the pz lock closed`() {
        // player.cpp:6616 (`lastHit && hasCondition(CONDITION_INFIGHT)`).
        val world = World()
        val attackerPlayer = world.player(1)
        attackerPlayer.addCondition(
            org.opentibia.canary.combat.condition.Condition.createCondition(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_INFIGHT,
                1000,
            ),
        )
        val attacker = world.pvp(attackerPlayer)
        val target = world.pvp(world.player(2))
        attacker.addAttacked(target)

        attacker.onKilledPlayer(target, lastHit = false)

        assertThat(attackerPlayer.pzLocked).isFalse()
    }
}
