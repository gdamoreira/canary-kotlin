package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass

/**
 * Transcript tests for the `Game` party handlers:
 * `Game::playerInviteToParty` (game.cpp:9657), `Game::playerJoinParty`
 * (game.cpp:9701), `Game::playerRevokePartyInvitation` (game.cpp:9725),
 * `Game::playerPassPartyLeadership` (game.cpp:9744), `Game::playerLeaveParty`
 * (game.cpp:9763) and `Game::playerEnableSharedPartyExperience` (game.cpp:9778).
 *
 * The handlers delegate to the existing `Party` engine
 * (`src/creatures/players/grouping/party.cpp`) exactly like Canary.
 */
class GamePartyTest {

    private val center = Position(1000, 1000, 7)

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    private fun Game.placePlayer(player: Player, position: Position = center, protectionZone: Boolean = false) {
        map.getOrCreateTile(position).also { tile ->
            if (tile.ground == null) {
                tile.ground = org.opentibia.canary.items.Item(id = 100)
            }
            if (protectionZone) {
                tile.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
            }
        }
        check(internalPlaceCreature(player, position)) { "player placement failed" }
    }

    private fun messagesOf(player: Player): MutableList<Pair<MessageClass, String>> {
        val messages = mutableListOf<Pair<MessageClass, String>>()
        player.sendTextMessageTyped = { type, text -> messages.add(type to text) }
        return messages
    }

    // ----- playerInviteToParty -----

    @Test
    fun `playerInviteToParty creates a party for the leader and records the invitation`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val invited = player(2, "Invited")
        game.addPlayer(leader)
        game.addPlayer(invited)

        game.playerInviteToParty(1, 2)

        val party = leader.getParty()
        assertThat(party).isNotNull()
        assertThat(party!!.getLeader()).isSameAs(leader)
        assertThat(party.isPlayerInvited(invited)).isTrue()
        assertThat(invited.invitePartyList).containsExactly(party)
    }

    @Test
    fun `playerInviteToParty ignores inviting yourself`() {
        val game = newGame()
        val leader = player(1, "Leader")
        game.addPlayer(leader)

        game.playerInviteToParty(1, 1)

        assertThat(leader.getParty()).isNull()
    }

    @Test
    fun `playerInviteToParty refuses an invited player already in a party`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val invited = player(2, "Invited")
        val otherLeader = player(3, "OtherLeader")
        game.addPlayer(leader)
        game.addPlayer(invited)
        game.addPlayer(otherLeader)
        game.playerInviteToParty(3, 2)
        game.playerJoinParty(2, 3)
        val messages = messagesOf(leader)

        game.playerInviteToParty(1, 2)

        assertThat(messages).containsExactly(MessageClass.MESSAGE_PARTY_MANAGEMENT to "Invited is already in a party.")
        assertThat(leader.getParty()).isNull()
    }

    @Test
    fun `playerInviteToParty ignores a non-leader party member`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val candidate = player(3, "Candidate")
        game.addPlayer(leader)
        game.addPlayer(member)
        game.addPlayer(candidate)
        game.playerInviteToParty(1, 2)
        game.playerJoinParty(2, 1)
        val party = leader.getParty()!!

        game.playerInviteToParty(2, 3)

        assertThat(party.isPlayerInvited(candidate)).isFalse()
    }

    // ----- playerJoinParty -----

    @Test
    fun `playerJoinParty lets an invited player join the party`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val invited = player(2, "Invited")
        game.addPlayer(leader)
        game.addPlayer(invited)
        game.playerInviteToParty(1, 2)
        val party = leader.getParty()!!

        game.playerJoinParty(2, 1)

        assertThat(invited.getParty()).isSameAs(party)
        assertThat(party.getMemberCount()).isEqualTo(1)
        assertThat(party.getMembers()).containsExactly(invited)
        assertThat(invited.invitePartyList).isEmpty()
    }

    @Test
    fun `playerJoinParty refuses a player who is not invited`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val candidate = player(2, "Candidate")
        game.addPlayer(leader)
        game.addPlayer(candidate)
        game.playerInviteToParty(1, 2)
        val party = leader.getParty()!!

        game.playerJoinParty(3, 1)

        assertThat(candidate.getParty()).isNull()
        assertThat(party.getMemberCount()).isZero()
    }

    @Test
    fun `playerJoinParty refuses a player already in a party`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val invited = player(2, "Invited")
        game.addPlayer(leader)
        game.addPlayer(invited)
        game.playerInviteToParty(1, 2)
        invited.setParty(player(3, "Unrelated").let { org.opentibia.canary.creatures.grouping.Party(it) })
        val messages = messagesOf(invited)

        game.playerJoinParty(2, 1)

        assertThat(messages).containsExactly(MessageClass.MESSAGE_PARTY_MANAGEMENT to "You are already in a party.")
    }

    @Test
    fun `playerJoinParty ignores a leader who is not the party leader`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val candidate = player(3, "Candidate")
        game.addPlayer(leader)
        game.addPlayer(member)
        game.addPlayer(candidate)
        game.playerInviteToParty(1, 2)
        game.playerJoinParty(2, 1)
        val party = leader.getParty()!!

        game.playerJoinParty(3, 2)

        assertThat(candidate.getParty()).isNull()
        assertThat(party.getMemberCount()).isEqualTo(1)
    }

    // ----- playerRevokePartyInvitation -----

    @Test
    fun `playerRevokePartyInvitation removes the invitation from the leader`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val invited = player(2, "Invited")
        game.addPlayer(leader)
        game.addPlayer(invited)
        game.playerInviteToParty(1, 2)
        val party = leader.getParty()!!

        game.playerRevokePartyInvitation(1, 2)

        assertThat(party.isPlayerInvited(invited)).isFalse()
        assertThat(invited.invitePartyList).isEmpty()
    }

    @Test
    fun `playerRevokePartyInvitation ignores a non-leader`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val candidate = player(3, "Candidate")
        game.addPlayer(leader)
        game.addPlayer(member)
        game.addPlayer(candidate)
        game.playerInviteToParty(1, 2)
        game.playerJoinParty(2, 1)
        game.playerInviteToParty(1, 3)
        val party = leader.getParty()!!

        game.playerRevokePartyInvitation(2, 3)

        assertThat(party.isPlayerInvited(candidate)).isTrue()
    }

    // ----- playerPassPartyLeadership -----

    @Test
    fun `playerPassPartyLeadership hands leadership to a party member`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        game.addPlayer(leader)
        game.addPlayer(member)
        game.playerInviteToParty(1, 2)
        game.playerJoinParty(2, 1)
        val party = leader.getParty()!!

        game.playerPassPartyLeadership(1, 2)

        assertThat(party.getLeader()).isSameAs(member)
        assertThat(party.getMembers()).containsExactly(leader)
    }

    @Test
    fun `playerPassPartyLeadership refuses a player who is not a partner`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val outsider = player(2, "Outsider")
        game.addPlayer(leader)
        game.addPlayer(outsider)

        game.playerInviteToParty(1, 2)
        val party = leader.getParty()!!

        game.playerPassPartyLeadership(1, 2)

        assertThat(party.getLeader()).isSameAs(leader)
    }

    @Test
    fun `playerPassPartyLeadership ignores a non-leader`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val secondMember = player(3, "SecondMember")
        game.addPlayer(leader)
        game.addPlayer(member)
        game.addPlayer(secondMember)
        game.playerInviteToParty(1, 2)
        game.playerJoinParty(2, 1)
        game.playerInviteToParty(1, 3)
        game.playerJoinParty(3, 1)
        val party = leader.getParty()!!

        game.playerPassPartyLeadership(2, 3)

        assertThat(party.getLeader()).isSameAs(leader)
    }

    // ----- playerLeaveParty -----

    @Test
    fun `playerLeaveParty sends the failure text when the player has no party`() {
        val game = newGame()
        val player = player(1, "Solo")
        game.addPlayer(player)
        val messages = messagesOf(player)

        game.playerLeaveParty(1)

        assertThat(messages).containsExactly(
            MessageClass.MESSAGE_FAILURE to "You cannot leave party, contact the administrator.",
        )
    }

    @Test
    fun `playerLeaveParty removes a party member`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        game.addPlayer(leader)
        game.addPlayer(member)
        game.playerInviteToParty(1, 2)
        game.playerJoinParty(2, 1)
        val party = leader.getParty()!!

        game.playerLeaveParty(2)

        assertThat(member.getParty()).isNull()
        assertThat(party.getMembers()).isEmpty()
    }

    @Test
    fun `playerLeaveParty is refused while in fight outside a protection zone`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        game.addPlayer(leader)
        game.addPlayer(member)
        game.placePlayer(member)
        game.playerInviteToParty(1, 2)
        game.playerJoinParty(2, 1)
        member.addInFightTicks()
        val messages = messagesOf(member)

        game.playerLeaveParty(2)

        assertThat(member.hasCondition(ConditionType.CONDITION_INFIGHT)).isTrue()
        assertThat(member.getParty()).isNotNull()
        assertThat(messages).containsExactly(
            MessageClass.MESSAGE_FAILURE to "You cannot leave party, contact the administrator.",
        )
    }

    @Test
    fun `playerLeaveParty is allowed while in fight inside a protection zone`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        game.addPlayer(leader)
        game.addPlayer(member)
        game.placePlayer(member, protectionZone = true)
        game.playerInviteToParty(1, 2)
        game.playerJoinParty(2, 1)
        member.addInFightTicks()

        game.playerLeaveParty(2)

        assertThat(member.getParty()).isNull()
    }

    // ----- playerEnableSharedPartyExperience -----

    @Test
    fun `playerEnableSharedPartyExperience toggles shared experience for the leader`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val invited = player(2, "Invited")
        game.addPlayer(leader)
        game.addPlayer(invited)
        game.playerInviteToParty(1, 2)
        val party = leader.getParty()!!

        game.playerEnableSharedPartyExperience(1, false)
        assertThat(party.isSharedExperienceActive()).isFalse()

        game.playerEnableSharedPartyExperience(1, true)
        assertThat(party.isSharedExperienceActive()).isTrue()
    }

    @Test
    fun `playerEnableSharedPartyExperience ignores a player without a party`() {
        val game = newGame()
        val player = player(1, "Solo")
        game.addPlayer(player)

        game.playerEnableSharedPartyExperience(1, true)

        assertThat(player.getParty()).isNull()
    }

    @Test
    fun `playerEnableSharedPartyExperience is refused while in fight outside a protection zone`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val invited = player(2, "Invited")
        game.addPlayer(leader)
        game.addPlayer(invited)
        game.placePlayer(leader)
        game.playerInviteToParty(1, 2)
        val party = leader.getParty()!!
        leader.addInFightTicks()

        game.playerEnableSharedPartyExperience(1, false)

        assertThat(party.isSharedExperienceActive()).isTrue()
    }

    @Test
    fun `playerEnableSharedPartyExperience is allowed while in fight inside a protection zone`() {
        val game = newGame()
        val leader = player(1, "Leader")
        val invited = player(2, "Invited")
        game.addPlayer(leader)
        game.addPlayer(invited)
        game.placePlayer(leader, protectionZone = true)
        game.playerInviteToParty(1, 2)
        val party = leader.getParty()!!
        leader.addInFightTicks()

        game.playerEnableSharedPartyExperience(1, false)

        assertThat(party.isSharedExperienceActive()).isFalse()
    }
}
