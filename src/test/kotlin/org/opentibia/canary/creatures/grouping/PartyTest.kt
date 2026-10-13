package org.opentibia.canary.creatures.grouping

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.map.Position

/**
 * Transcribes the Canary party behavior:
 *
 * - `Party` state and lifecycle (`src/creatures/players/grouping/party.cpp`,
 *   `party.hpp`): `create`, `invitePlayer`, `joinParty`, `leaveParty`,
 *   `passPartyLeadership`, `removeInvite`, `revokeInvitation`, `disband`,
 *   member/invitation counts, shared experience and party shields.
 * - `Player` party seams (`src/creatures/players/player.cpp`):
 *   `addPartyInvitation`/`removePartyInvitation` (7802-7814), `isInviting`
 *   (7783-7788), `getPartyShield` (7729-7781), `getStaminaXpBoost` (3248-3250),
 *   `onGainSharedExperience` (6724-6726).
 * - Canary's own party unit test
 *   (`tests/unit/players/party_test.cpp`) for the member/leader ordering.
 */
class PartyTest {

    private fun player(
        id: Long,
        name: String,
        level: Int = 1,
        position: Position = Position(100, 100, 7),
    ) = Player(id, name, maxHealth = 100, health = 100, position = position, level = level)

    @Test
    fun `party shield enum matches Canary values`() {
        // PartyShields_t (src/utils/utils_definitions.hpp:517-530).
        assertThat(PartyShields_t.SHIELD_NONE.value).isEqualTo(0)
        assertThat(PartyShields_t.SHIELD_WHITEYELLOW.value).isEqualTo(1)
        assertThat(PartyShields_t.SHIELD_WHITEBLUE.value).isEqualTo(2)
        assertThat(PartyShields_t.SHIELD_BLUE.value).isEqualTo(3)
        assertThat(PartyShields_t.SHIELD_YELLOW.value).isEqualTo(4)
        assertThat(PartyShields_t.SHIELD_BLUE_SHAREDEXP.value).isEqualTo(5)
        assertThat(PartyShields_t.SHIELD_YELLOW_SHAREDEXP.value).isEqualTo(6)
        assertThat(PartyShields_t.SHIELD_BLUE_NOSHAREDEXP_BLINK.value).isEqualTo(7)
        assertThat(PartyShields_t.SHIELD_YELLOW_NOSHAREDEXP_BLINK.value).isEqualTo(8)
        assertThat(PartyShields_t.SHIELD_BLUE_NOSHAREDEXP.value).isEqualTo(9)
        assertThat(PartyShields_t.SHIELD_YELLOW_NOSHAREDEXP.value).isEqualTo(10)
        assertThat(PartyShields_t.SHIELD_GRAY.value).isEqualTo(11)
    }

    @Test
    fun `constructor attaches the leader and enables auto shared experience`() {
        // Party::create (party.cpp:22-33): the leader is stored and
        // leader->setParty(party); the default PARTY_AUTO_SHARE_EXPERIENCE
        // config (configmanager.cpp:121) then activates shared experience.
        val leader = player(1, "Leader")

        val party = Party(leader)

        assertThat(leader.getParty()).isSameAs(party)
        assertThat(party.getLeader()).isSameAs(leader)
        assertThat(party.getMemberCount()).isZero()
        assertThat(party.isSharedExperienceActive()).isTrue()
        // Alone in the party the status is SHAREDEXP_EMPTYPARTY (party.cpp:630-632).
        assertThat(party.isSharedExperienceEnabled()).isFalse()
    }

    @Test
    fun `a leaderless party cannot invite or join`() {
        // party.cpp:457-458 (`invitePlayer`) and 353-355 (`joinParty`) bail out
        // when there is no leader.
        val party = Party()
        val candidate = player(1, "Candidate")

        assertThat(party.getLeader()).isNull()
        assertThat(party.invitePlayer(candidate)).isFalse()
        assertThat(party.joinParty(candidate)).isFalse()
    }

    @Test
    fun `invitePlayer stores the invitee and rejects duplicate invites`() {
        // Party::invitePlayer (party.cpp:456-494): duplicates return false; the
        // invitee is appended to inviteList and gets Player::addPartyInvitation.
        val leader = player(1, "Leader")
        val invitee = player(2, "Invitee")
        val party = Party(leader)

        assertThat(party.invitePlayer(invitee)).isTrue()
        assertThat(party.invitePlayer(invitee)).isFalse()
        assertThat(party.isPlayerInvited(invitee)).isTrue()
        assertThat(party.getInvitationCount()).isEqualTo(1)
        assertThat(invitee.getParty()).isNull()
        assertThat(invitee.invitePartyList).containsExactly(party)
    }

    @Test
    fun `joinParty requires an invitation and moves the invitee into the member list`() {
        // Party::joinParty (party.cpp:353-407): joining without an invite fails;
        // a successful join erases the invite, sets the player party and appends
        // to memberList.
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val party = Party(leader)

        assertThat(party.joinParty(member)).isFalse()

        party.invitePlayer(member)

        assertThat(party.joinParty(member)).isTrue()
        assertThat(party.getMemberCount()).isEqualTo(1)
        assertThat(party.getInvitationCount()).isZero()
        assertThat(party.isPlayerInvited(member)).isFalse()
        assertThat(member.getParty()).isSameAs(party)
        assertThat(member.invitePartyList).isEmpty()
        assertThat(party.getMembers()).containsExactly(member)
        assertThat(party.joinParty(member)).isFalse()
    }

    @Test
    fun `leaveParty removes a member while others remain`() {
        // Party::leaveParty (party.cpp:169-261): the member is erased from
        // memberList and loses the party; a second leave fails because
        // Player::getParty() no longer points at this party.
        val leader = player(1, "Leader")
        val first = player(2, "First")
        val second = player(3, "Second")
        val party = Party(leader)
        party.invitePlayer(first)
        party.joinParty(first)
        party.invitePlayer(second)
        party.joinParty(second)

        assertThat(party.leaveParty(first)).isTrue()

        assertThat(first.getParty()).isNull()
        assertThat(party.getMemberCount()).isEqualTo(1)
        assertThat(party.getMembers()).containsExactly(second)
        assertThat(party.getLeader()).isSameAs(leader)
        assertThat(leader.getParty()).isSameAs(party)
        assertThat(party.leaveParty(first)).isFalse()
    }

    @Test
    fun `leaveParty disbands the party when the last member leaves`() {
        // `empty()` (party.cpp:536-538) is true once memberList and inviteList
        // are empty, so leaveParty disbands (party.cpp:251-253, 96-167).
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        assertThat(party.leaveParty(member)).isTrue()

        assertThat(party.getLeader()).isNull()
        assertThat(party.getMemberCount()).isZero()
        assertThat(leader.getParty()).isNull()
        assertThat(member.getParty()).isNull()
    }

    @Test
    fun `leader leaving transfers leadership to the first member`() {
        // Party::leaveParty leader branch (party.cpp:192-214) hands leadership to
        // memberList.front() through passPartyLeadership (party.cpp:263-306); the
        // old leader is inserted at the front and then erased.
        val leader = player(1, "Leader")
        val first = player(2, "First")
        val second = player(3, "Second")
        val party = Party(leader)
        party.invitePlayer(first)
        party.joinParty(first)
        party.invitePlayer(second)
        party.joinParty(second)

        assertThat(party.leaveParty(leader)).isTrue()

        assertThat(party.getLeader()).isSameAs(first)
        assertThat(first.getParty()).isSameAs(party)
        assertThat(leader.getParty()).isNull()
        assertThat(party.getMemberCount()).isEqualTo(1)
        assertThat(party.getMembers()).containsExactly(second)
    }

    @Test
    fun `leader leaving with a single member and no invitees disbands the party`() {
        // memberList.size() == 1 && inviteList.empty() sets missingLeader
        // (party.cpp:195-196), so the party disbands instead of transferring.
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        assertThat(party.leaveParty(leader)).isTrue()

        assertThat(party.getLeader()).isNull()
        assertThat(party.getMemberCount()).isZero()
        assertThat(leader.getParty()).isNull()
        assertThat(member.getParty()).isNull()
    }

    @Test
    fun `leader leaving with an invitee transfers leadership instead of disbanding`() {
        // With an outstanding invitation the single-member shortcut does not
        // apply (party.cpp:195-198): leadership moves to the member and the
        // invitee stays invited.
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val invitee = player(3, "Invitee")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        party.invitePlayer(invitee)

        assertThat(party.leaveParty(leader)).isTrue()

        assertThat(party.getLeader()).isSameAs(member)
        assertThat(party.getMemberCount()).isZero()
        assertThat(party.isPlayerInvited(invitee)).isTrue()
        assertThat(leader.getParty()).isNull()
    }

    @Test
    fun `passPartyLeadership moves the leader and keeps the old leader as a member`() {
        // Party::passPartyLeadership (party.cpp:263-306): the new leader is
        // erased from memberList, becomes m_leader, and the old leader is
        // inserted at the front of memberList.
        val leader = player(1, "Leader")
        val first = player(2, "First")
        val second = player(3, "Second")
        val outsider = player(4, "Outsider")
        val party = Party(leader)
        party.invitePlayer(first)
        party.joinParty(first)
        party.invitePlayer(second)
        party.joinParty(second)

        assertThat(party.passPartyLeadership(second)).isTrue()

        assertThat(party.getLeader()).isSameAs(second)
        assertThat(second.getParty()).isSameAs(party)
        assertThat(party.getMembers()).containsExactly(leader, first)

        assertThat(party.passPartyLeadership(outsider)).isFalse()
        assertThat(party.passPartyLeadership(second)).isFalse()
    }

    @Test
    fun `revokeInvitation removes the invitation from both sides`() {
        // Party::revokeInvitation (party.cpp:439-454) delegates to removeInvite
        // (party.cpp:409-437), which erases the invitee from inviteList and the
        // party from Player::invitePartyList.
        val leader = player(1, "Leader")
        val invitee = player(2, "Invitee")
        val party = Party(leader)
        party.invitePlayer(invitee)

        party.revokeInvitation(invitee)

        assertThat(party.getInvitationCount()).isZero()
        assertThat(party.isPlayerInvited(invitee)).isFalse()
        assertThat(invitee.invitePartyList).isEmpty()
    }

    @Test
    fun `removeInvite disbands a leader-only party once the last invitee is removed`() {
        // removeInvite (party.cpp:426-427) disbands when `empty()` is true.
        val leader = player(1, "Leader")
        val invitee = player(2, "Invitee")
        val party = Party(leader)
        party.invitePlayer(invitee)

        assertThat(party.removeInvite(invitee)).isTrue()

        assertThat(party.getLeader()).isNull()
        assertThat(leader.getParty()).isNull()
        assertThat(invitee.invitePartyList).isEmpty()
    }

    @Test
    fun `member and invitation counts exclude the leader and preserve player order`() {
        // Party::getPlayers (party.cpp:47-59) lists members first and the leader
        // last (transcribed from tests/unit/players/party_test.cpp);
        // getMemberCount/getInvitationCount (party.cpp:69-75) ignore the leader.
        val leader = player(1, "Leader")
        val first = player(2, "First")
        val second = player(3, "Second")
        val invitee = player(4, "Invitee")
        val party = Party(leader)
        party.invitePlayer(first)
        party.joinParty(first)
        party.invitePlayer(second)
        party.joinParty(second)
        party.invitePlayer(invitee)

        assertThat(party.getMemberCount()).isEqualTo(2)
        assertThat(party.getInvitationCount()).isEqualTo(1)
        assertThat(party.getPlayers()).containsExactly(first, second, leader)
        assertThat(party.getMembers()).containsExactly(first, second)
        assertThat(party.getInvitees()).containsExactly(invitee)
    }

    @Test
    fun `shareExperience grants each participant the stamina-scaled experience`() {
        // Party::shareExperience (party.cpp:603-619) hands every member and the
        // leader experience * getStaminaXpBoost() / 100, which lands in
        // Player::onGainSharedExperience -> gainExperience (player.cpp:6724-6726).
        val leader = player(1, "Leader")
        val first = player(2, "First")
        val second = player(3, "Second")
        val party = Party(leader)
        party.invitePlayer(first)
        party.joinParty(first)
        party.invitePlayer(second)
        party.joinParty(second)

        party.shareExperience(1000)

        assertThat(leader.experience).isEqualTo(1000)
        assertThat(first.experience).isEqualTo(1000)
        assertThat(second.experience).isEqualTo(1000)
    }

    @Test
    fun `shareExperience scales each participant by their own stamina boost`() {
        // Each member's staminaXpBoost (player.hpp:1824, default 100) scales only
        // that member's share (party.cpp:613-618).
        val leader = player(1, "Leader")
        val half = player(2, "Half")
        val bonus = player(3, "Bonus")
        val party = Party(leader)
        party.invitePlayer(half)
        party.joinParty(half)
        party.invitePlayer(bonus)
        party.joinParty(bonus)
        half.staminaXpBoost = 50
        bonus.staminaXpBoost = 150

        party.shareExperience(1000)

        assertThat(leader.experience).isEqualTo(1000)
        assertThat(half.experience).isEqualTo(500)
        assertThat(bonus.experience).isEqualTo(1500)
    }

    @Test
    fun `updatePlayerTicks activates shared experience for active members`() {
        // Party::updatePlayerTicks (party.cpp:720-725) records OTSYS_TIME for
        // players that gained points and re-evaluates shared experience
        // (party.cpp:540-548, 701-718).
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        assertThat(party.isSharedExperienceActive()).isTrue()
        assertThat(party.isSharedExperienceEnabled()).isFalse()

        party.updatePlayerTicks(leader, 1)
        party.updatePlayerTicks(member, 1)

        assertThat(party.isSharedExperienceEnabled()).isTrue()
    }

    @Test
    fun `NotGainInFight members stay eligible without activity ticks`() {
        // getMemberSharedExperienceStatus skips isPlayerActive for members with
        // PlayerFlags_t::NotGainInFight (party.cpp:643-647), and updatePlayerTicks
        // ignores their points (party.cpp:721).
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        member.setFlag(PlayerFlags_t.NotGainInFight)

        party.updatePlayerTicks(leader, 1)
        party.updatePlayerTicks(member, 1)

        assertThat(party.isSharedExperienceEnabled()).isTrue()
    }

    @Test
    fun `shared experience expires after two minutes of inactivity`() {
        // Party::isPlayerActive (party.cpp:692-699): OTSYS_TIME - tick <= 2 * 60 * 1000.
        var clock = 1_000_000L
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val party = Party(leader, now = { clock })
        party.invitePlayer(member)
        party.joinParty(member)
        party.updatePlayerTicks(leader, 1)
        party.updatePlayerTicks(member, 1)
        assertThat(party.isSharedExperienceEnabled()).isTrue()

        clock += 2 * 60 * 1000
        party.updateSharedExperience()
        assertThat(party.isSharedExperienceEnabled()).isTrue()

        clock += 1
        party.updateSharedExperience()
        assertThat(party.isSharedExperienceEnabled()).isFalse()
    }

    @Test
    fun `getMemberSharedExperienceStatus reports an empty party and level spread`() {
        // Party::getMemberSharedExperienceStatus (party.cpp:625-649) returns
        // EMPTYPARTY without members and LEVELDIFFTOOLARGE below getMinLevel.
        val leader = player(1, "Leader", level = 100)
        val alone = Party(leader)

        assertThat(alone.getMemberSharedExperienceStatus(leader))
            .isEqualTo(SharedExpStatus_t.SHAREDEXP_EMPTYPARTY)

        val low = player(2, "Low", level = 1)
        alone.invitePlayer(low)
        alone.joinParty(low)

        assertThat(alone.getMemberSharedExperienceStatus(low))
            .isEqualTo(SharedExpStatus_t.SHAREDEXP_LEVELDIFFTOOLARGE)
        assertThat(alone.canUseSharedExperience(low)).isFalse()
    }

    @Test
    fun `getMemberSharedExperienceStatus checks the thirty by thirty by one range`() {
        // party.cpp:639 uses Position::areInRange<30, 30, 1>: 30 tiles on x/y and
        // one floor on z.
        val leader = player(1, "Leader", level = 10, position = Position(100, 100, 7))
        val far = player(2, "Far", level = 10, position = Position(140, 100, 7))
        val upperFloor = player(3, "Upper", level = 10, position = Position(100, 100, 8))
        val twoFloorsUp = player(4, "TwoFloorsUp", level = 10, position = Position(100, 100, 9))
        val party = Party(leader)
        for (candidate in listOf(far, upperFloor, twoFloorsUp)) {
            party.invitePlayer(candidate)
            party.joinParty(candidate)
        }

        assertThat(party.getMemberSharedExperienceStatus(far))
            .isEqualTo(SharedExpStatus_t.SHAREDEXP_TOOFARAWAY)
        // z + 1 is inside the range, so the member fails on activity instead.
        assertThat(party.getMemberSharedExperienceStatus(upperFloor))
            .isEqualTo(SharedExpStatus_t.SHAREDEXP_MEMBERINACTIVE)
        assertThat(party.getMemberSharedExperienceStatus(twoFloorsUp))
            .isEqualTo(SharedExpStatus_t.SHAREDEXP_TOOFARAWAY)
    }

    @Test
    fun `getMemberSharedExperienceStatus reports inactive members and accepts active ones`() {
        // party.cpp:643-647 requires an active player unless NotGainInFight.
        val leader = player(1, "Leader", level = 10)
        val member = player(2, "Member", level = 10)
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        assertThat(party.getMemberSharedExperienceStatus(member))
            .isEqualTo(SharedExpStatus_t.SHAREDEXP_MEMBERINACTIVE)

        party.updatePlayerTicks(member, 1)

        assertThat(party.getMemberSharedExperienceStatus(member))
            .isEqualTo(SharedExpStatus_t.SHAREDEXP_OK)
        assertThat(party.canUseSharedExperience(member)).isTrue()
    }

    @Test
    fun `share range derives from the highest and lowest levels`() {
        // getHighestLevel/getMinLevel/getLowestLevel/getMaxLevel
        // (party.cpp:655-690) with the default PARTY_SHARE_RANGE_MULTIPLIER 1.5
        // (configmanager.cpp:356): min = ceil(highest / 1.5), max = floor(lowest * 1.5).
        val leader = player(1, "Leader", level = 100)
        val member = player(2, "Member", level = 30)
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        assertThat(party.getHighestLevel()).isEqualTo(100)
        assertThat(party.getLowestLevel()).isEqualTo(30)
        assertThat(party.getMinLevel()).isEqualTo(67)
        assertThat(party.getMaxLevel()).isEqualTo(45)
    }

    @Test
    fun `setSharedExperience requires the leader and toggles the flag`() {
        // Party::setSharedExperience (party.cpp:567-593) only accepts the leader
        // and is idempotent when the flag already matches.
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        assertThat(party.setSharedExperience(member, false)).isFalse()
        assertThat(party.isSharedExperienceActive()).isTrue()

        assertThat(party.setSharedExperience(leader, false)).isTrue()
        assertThat(party.isSharedExperienceActive()).isFalse()
        assertThat(party.setSharedExperience(leader, false)).isTrue()

        assertThat(party.setSharedExperience(leader, true)).isTrue()
        assertThat(party.isSharedExperienceActive()).isTrue()
        assertThat(party.isSharedExperienceEnabled()).isFalse()
    }

    @Test
    fun `party shield reports shared experience states to leader and members`() {
        // Player::getPartyShield (player.cpp:7729-7781) maps the shared
        // experience state to SHIELD_YELLOW_* for the leader and SHIELD_BLUE_*
        // for members.
        val leader = player(1, "Leader")
        val member = player(2, "Member")
        val party = Party(leader, autoShareExperience = false)
        party.invitePlayer(member)
        party.joinParty(member)

        assertThat(leader.getPartyShield(leader)).isEqualTo(PartyShields_t.SHIELD_YELLOW)
        assertThat(member.getPartyShield(member)).isEqualTo(PartyShields_t.SHIELD_BLUE)

        party.setSharedExperience(leader, true)
        party.updatePlayerTicks(leader, 1)
        party.updatePlayerTicks(member, 1)
        assertThat(party.isSharedExperienceEnabled()).isTrue()

        // The viewer's shield is YELLOW for the leader target and BLUE for a
        // member target (player.cpp:7734-7765).
        assertThat(member.getPartyShield(leader)).isEqualTo(PartyShields_t.SHIELD_YELLOW_SHAREDEXP)
        assertThat(leader.getPartyShield(member)).isEqualTo(PartyShields_t.SHIELD_BLUE_SHAREDEXP)
    }

    @Test
    fun `party shield reports no-shared-experience states while a member cannot share`() {
        // When shared experience is active but not enabled,
        // canUseSharedExperience picks NOSHAREDEXP and otherwise the BLINK
        // variants (player.cpp:7736-7764).
        val leader = player(1, "Leader")
        val active = player(2, "Active")
        val inactive = player(3, "Inactive")
        val party = Party(leader)
        for (member in listOf(active, inactive)) {
            party.invitePlayer(member)
            party.joinParty(member)
        }
        party.updatePlayerTicks(leader, 1)
        party.updatePlayerTicks(active, 1)

        assertThat(party.isSharedExperienceActive()).isTrue()
        assertThat(party.isSharedExperienceEnabled()).isFalse()

        assertThat(active.getPartyShield(leader)).isEqualTo(PartyShields_t.SHIELD_YELLOW_NOSHAREDEXP)
        assertThat(active.getPartyShield(active)).isEqualTo(PartyShields_t.SHIELD_BLUE_NOSHAREDEXP)
        assertThat(leader.getPartyShield(inactive)).isEqualTo(PartyShields_t.SHIELD_BLUE_NOSHAREDEXP_BLINK)

        // An inactive leader cannot use shared experience, so the yellow blink state.
        val idleLeader = player(5, "IdleLeader")
        val follower = player(6, "Follower")
        val idleParty = Party(idleLeader)
        idleParty.invitePlayer(follower)
        idleParty.joinParty(follower)
        idleParty.updatePlayerTicks(follower, 1)
        assertThat(idleParty.isSharedExperienceEnabled()).isFalse()
        assertThat(follower.getPartyShield(idleLeader))
            .isEqualTo(PartyShields_t.SHIELD_YELLOW_NOSHAREDEXP_BLINK)
    }

    @Test
    fun `party shield reports invite and gray states`() {
        // Invitation shields (player.cpp:7767-7774) and SHIELD_GRAY for a target
        // that belongs to another party (player.cpp:7776-7778).
        val leader = player(1, "Leader")
        val invitee = player(2, "Invitee")
        val outsider = player(3, "Outsider")
        val otherLeader = player(4, "OtherLeader")
        val otherMember = player(5, "OtherMember")
        val party = Party(leader, autoShareExperience = false)
        val otherParty = Party(otherLeader, autoShareExperience = false)
        otherParty.invitePlayer(otherMember)
        otherParty.joinParty(otherMember)
        party.invitePlayer(invitee)

        assertThat(leader.getPartyShield(invitee)).isEqualTo(PartyShields_t.SHIELD_WHITEBLUE)
        assertThat(invitee.getPartyShield(leader)).isEqualTo(PartyShields_t.SHIELD_WHITEYELLOW)
        assertThat(outsider.getPartyShield(otherMember)).isEqualTo(PartyShields_t.SHIELD_GRAY)
        assertThat(outsider.getPartyShield(invitee)).isEqualTo(PartyShields_t.SHIELD_NONE)
    }
}
