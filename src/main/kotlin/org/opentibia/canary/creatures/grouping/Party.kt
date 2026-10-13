package org.opentibia.canary.creatures.grouping

import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.map.Position
import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor

/**
 * `SharedExpStatus_t` (`src/creatures/players/grouping/party.hpp:16-22`).
 */
enum class SharedExpStatus_t {
    SHAREDEXP_OK,
    SHAREDEXP_TOOFARAWAY,
    SHAREDEXP_LEVELDIFFTOOLARGE,
    SHAREDEXP_MEMBERINACTIVE,
    SHAREDEXP_EMPTYPARTY,
}

/**
 * `PartyShields_t` (`src/utils/utils_definitions.hpp:517-530`).
 *
 * The values cross the wire protocol (`ProtocolGame::sendPartyCreatureShield`,
 * protocolgame.cpp:8351-8363) so they are transcribed 1:1.
 */
enum class PartyShields_t(val value: Int) {
    SHIELD_NONE(0),
    SHIELD_WHITEYELLOW(1),
    SHIELD_WHITEBLUE(2),
    SHIELD_BLUE(3),
    SHIELD_YELLOW(4),
    SHIELD_BLUE_SHAREDEXP(5),
    SHIELD_YELLOW_SHAREDEXP(6),
    SHIELD_BLUE_NOSHAREDEXP_BLINK(7),
    SHIELD_YELLOW_NOSHAREDEXP_BLINK(8),
    SHIELD_BLUE_NOSHAREDEXP(9),
    SHIELD_YELLOW_NOSHAREDEXP(10),
    SHIELD_GRAY(11),
}

/**
 * `Party` (`src/creatures/players/grouping/party.hpp`, `party.cpp`).
 *
 * Constructor-injected port of the Canary party: the leader is attached through
 * `Player::setParty` exactly like `Party::create` (party.cpp:22-33), and the
 * `PARTY_AUTO_SHARE_EXPERIENCE` / `PARTY_SHARE_RANGE_MULTIPLIER` config values
 * (configmanager.cpp:121, 356) plus the `OTSYS_TIME()` clock are injected
 * instead of read from the globals (AGENTS.md 1.1).
 *
 * PARITY GAPS: the Lua event/callback vetoes (`g_events()`, `g_callbacks()`),
 * every protocol side effect (`sendTextMessage`, `sendClosePrivate`,
 * `sendCreatureShield`, `updatePlayerShield`, `updatePlayerHelpers`,
 * `updateAllPartyIcons`, `showPlayerStatus`, party analyzer/tracker, `update
 * tracker` calls), the monk Guiding Presence/mantra holder, and the
 * `canOpenCorpse` player lookup are not ported.
 */
class Party(
    leader: Player? = null,
    autoShareExperience: Boolean = PARTY_AUTO_SHARE_EXPERIENCE,
    private val shareRangeMultiplier: Float = PARTY_SHARE_RANGE_MULTIPLIER,
    private val now: () -> Long = { System.currentTimeMillis() },
) {
    private var mLeader: Player? = leader

    private val memberList = mutableListOf<Player>()
    private val inviteList = mutableListOf<Player>()

    /** `Party::ticksMap` (party.hpp:127) — player id to last activity time. */
    private val ticksMap = mutableMapOf<Long, Long>()

    private var sharedExpActive = false
    private var sharedExpEnabled = false

    init {
        if (leader != null) {
            leader.setParty(this)
            if (autoShareExperience) {
                setSharedExperience(leader, true)
            }
        }
    }

    /** `Party::getLeader` (party.cpp:39-41). */
    fun getLeader(): Player? = mLeader

    /** `Party::getPlayers` (party.cpp:47-59) — members first, leader last. */
    fun getPlayers(): List<Player> {
        val players = ArrayList<Player>(memberList.size + 1)
        players.addAll(memberList)
        mLeader?.let { players.add(it) }
        return players
    }

    /** `Party::getMembers` (party.cpp:61-63). */
    fun getMembers(): List<Player> = memberList.toList()

    /** `Party::getInvitees` (party.cpp:65-67). */
    fun getInvitees(): List<Player> = inviteList.toList()

    /** `Party::getMemberCount` (party.cpp:69-71) — the leader is not a member. */
    fun getMemberCount(): Int = memberList.size

    /** `Party::getInvitationCount` (party.cpp:73-75). */
    fun getInvitationCount(): Int = inviteList.size

    /**
     * `Party::disband` (party.cpp:96-167): the leader and every member lose the
     * party and every invitation is dropped.
     */
    fun disband() {
        // g_events().eventPartyOnDisband / g_callbacks() vetoes are not ported (PARITY GAP).
        val currentLeader = getLeader()
        mLeader = null

        if (currentLeader != null) {
            currentLeader.setParty(null)
        }

        for (invitee in getInvitees()) {
            invitee.removePartyInvitation(this)
        }
        inviteList.clear()

        for (member in getMembers()) {
            member.setParty(null)
        }
        memberList.clear()
    }

    /**
     * `Party::invitePlayer` (party.cpp:456-494): duplicate invitations are
     * rejected, otherwise the player joins the invite list and gets the party
     * invitation recorded on the player.
     */
    fun invitePlayer(player: Player): Boolean {
        getLeader() ?: return false

        if (isPlayerInvited(player)) {
            return false
        }

        inviteList.add(player)
        player.addPartyInvitation(this)
        return true
    }

    /**
     * `Party::joinParty` (party.cpp:353-407): only an invited player can join;
     * the invitation is consumed, the player party is set and the member is
     * appended to the member list.
     */
    fun joinParty(player: Player): Boolean {
        getLeader() ?: return false
        // g_events().eventPartyOnJoin / g_callbacks() vetoes are not ported (PARITY GAP).

        if (!inviteList.remove(player)) {
            return false
        }

        player.setParty(this)
        memberList.add(player)
        player.removePartyInvitation(this)
        updateSharedExperience()
        return true
    }

    /**
     * `Party::removeInvite` (party.cpp:409-437): erases the invitee and, when
     * asked, the player-side invitation; a party left with nothing disbands.
     */
    fun removeInvite(player: Player, removeFromPlayer: Boolean = true): Boolean {
        getLeader() ?: return false

        if (!inviteList.remove(player)) {
            return false
        }

        if (removeFromPlayer) {
            player.removePartyInvitation(this)
        }

        if (empty()) {
            disband()
        }

        return true
    }

    /** `Party::revokeInvitation` (party.cpp:439-454). */
    fun revokeInvitation(player: Player) {
        getLeader() ?: return
        removeInvite(player)
    }

    /**
     * `Party::passPartyLeadership` (party.cpp:263-306): the target must already
     * be a member; it becomes the leader and the old leader becomes a member.
     */
    fun passPartyLeadership(player: Player?): Boolean {
        val leader = getLeader()
        if (leader == null || player == null || leader === player || player.getParty() !== this) {
            return false
        }

        if (!memberList.remove(player)) {
            return false
        }

        mLeader = player
        memberList.add(0, leader)
        updateSharedExperience()
        return true
    }

    /**
     * `Party::leaveParty` (party.cpp:169-261): a member (or the leader) leaves.
     * The leader hands leadership to `memberList.front()` unless the party is
     * about to be empty; a party without members or invitees disbands.
     */
    fun leaveParty(player: Player?, forceRemove: Boolean = false): Boolean {
        if (player == null) {
            return false
        }

        val leader = getLeader() ?: return false
        if (player.getParty() !== this && leader !== player) {
            return false
        }

        // `forceRemove` only overrides the g_events()/g_callbacks() veto, which
        // is not ported (PARITY GAP), so it has no observable effect here.

        var missingLeader = false
        if (leader === player) {
            if (memberList.isNotEmpty()) {
                if (memberList.size == 1 && inviteList.isEmpty()) {
                    missingLeader = true
                } else {
                    passPartyLeadership(memberList.first())
                }
            } else {
                missingLeader = true
            }
        }

        // Leadership already moved; erase the leaving player from the member list.
        val removedMember = memberList.remove(player)
        if (!removedMember && leader !== player) {
            return false
        }

        player.setParty(null)
        updateSharedExperience()
        clearPlayerPoints(player)

        if (missingLeader || empty()) {
            disband()
        }

        return true
    }

    /** `Party::isPlayerInvited` (party.cpp:496-498). */
    fun isPlayerInvited(player: Player): Boolean = inviteList.contains(player)

    /** `Party::empty` (party.cpp:536-538) — a leader-only party is empty. */
    fun empty(): Boolean = memberList.isEmpty() && inviteList.isEmpty()

    /**
     * `Party::shareExperience` (party.cpp:603-619): every member and the leader
     * receive `experience * getStaminaXpBoost() / 100`; the Lua callback that may
     * mutate the amount is not ported (PARITY GAP).
     */
    fun shareExperience(experience: Long, target: Creature? = null) {
        val leader = getLeader() ?: return

        val shareExperience = experience
        for (member in getMembers()) {
            val memberStaminaBoost = member.staminaXpBoost.toFloat() / 100
            member.onGainSharedExperience((shareExperience.toFloat() * memberStaminaBoost).toLong(), target)
        }
        val leaderStaminaBoost = leader.staminaXpBoost.toFloat() / 100
        leader.onGainSharedExperience((shareExperience.toFloat() * leaderStaminaBoost).toLong(), target)
    }

    /**
     * `Party::setSharedExperience` (party.cpp:567-593): only the leader can
     * toggle it; enabling immediately evaluates the whole party.
     */
    fun setSharedExperience(player: Player?, newSharedExpActive: Boolean, silent: Boolean = false): Boolean {
        val leader = getLeader()
        if (player == null || leader !== player) {
            return false
        }

        if (sharedExpActive == newSharedExpActive) {
            return true
        }

        sharedExpActive = newSharedExpActive

        if (newSharedExpActive) {
            val sharedExpStatus = getSharedExperienceStatus()
            sharedExpEnabled = sharedExpStatus == SharedExpStatus_t.SHAREDEXP_OK
        }

        // The leader text messages (`silent`, getSharedExpReturnMessage) and the
        // updateAllPartyIcons() broadcast are not ported (PARITY GAP).
        return true
    }

    /** `Party::isSharedExperienceActive` (party.cpp:595-597). */
    fun isSharedExperienceActive(): Boolean = sharedExpActive

    /** `Party::isSharedExperienceEnabled` (party.cpp:599-601). */
    fun isSharedExperienceEnabled(): Boolean = sharedExpEnabled

    /** `Party::canUseSharedExperience` (party.cpp:621-623). */
    fun canUseSharedExperience(player: Player): Boolean =
        getMemberSharedExperienceStatus(player) == SharedExpStatus_t.SHAREDEXP_OK

    /**
     * `Party::getMemberSharedExperienceStatus` (party.cpp:625-649): empty party,
     * level spread, `Position::areInRange<30, 30, 1>` and activity (skipped for
     * `NotGainInFight` players).
     */
    fun getMemberSharedExperienceStatus(player: Player): SharedExpStatus_t {
        val leader = getLeader() ?: return SharedExpStatus_t.SHAREDEXP_EMPTYPARTY
        if (memberList.isEmpty()) {
            return SharedExpStatus_t.SHAREDEXP_EMPTYPARTY
        }

        if (player.level < getMinLevel()) {
            return SharedExpStatus_t.SHAREDEXP_LEVELDIFFTOOLARGE
        }

        if (!areInRange(leader.position, player.position)) {
            return SharedExpStatus_t.SHAREDEXP_TOOFARAWAY
        }

        if (!player.hasFlag(PlayerFlags_t.NotGainInFight)) {
            if (!isPlayerActive(player)) {
                return SharedExpStatus_t.SHAREDEXP_MEMBERINACTIVE
            }
        }
        return SharedExpStatus_t.SHAREDEXP_OK
    }

    /**
     * `Party::updateSharedExperience` (party.cpp:540-548): while active, the
     * enabled flag follows the party-wide status. The `updateAllPartyIcons()`
     * broadcast is not ported (PARITY GAP).
     */
    fun updateSharedExperience() {
        if (sharedExpActive) {
            val result = getSharedExperienceStatus() == SharedExpStatus_t.SHAREDEXP_OK
            if (result != sharedExpEnabled) {
                sharedExpEnabled = result
            }
        }
    }

    /** `Party::updatePlayerTicks` (party.cpp:720-725). */
    fun updatePlayerTicks(player: Player, points: Int) {
        if (points != 0 && !player.hasFlag(PlayerFlags_t.NotGainInFight)) {
            ticksMap[player.id] = now()
            updateSharedExperience()
        }
    }

    /** `Party::clearPlayerPoints` (party.cpp:727-733). */
    fun clearPlayerPoints(player: Player) {
        if (ticksMap.remove(player.id) != null) {
            updateSharedExperience()
        }
    }

    /** `Party::getHighestLevel` (party.cpp:655-668) — private in Canary. */
    internal fun getHighestLevel(): Int {
        val leader = getLeader() ?: return 0

        var highestLevel = leader.level
        for (member in getMembers()) {
            if (member.level > highestLevel) {
                highestLevel = member.level
            }
        }
        return highestLevel
    }

    /** `Party::getLowestLevel` (party.cpp:674-686) — private in Canary. */
    internal fun getLowestLevel(): Int {
        val leader = getLeader() ?: return 0

        var lowestLevel = leader.level
        for (member in getMembers()) {
            if (member.level < lowestLevel) {
                lowestLevel = member.level
            }
        }
        return lowestLevel
    }

    /** `Party::getMinLevel` (party.cpp:670-672) — private in Canary. */
    internal fun getMinLevel(): Int = ceil(getHighestLevel().toFloat() / shareRangeMultiplier).toInt()

    /** `Party::getMaxLevel` (party.cpp:688-690) — private in Canary. */
    internal fun getMaxLevel(): Int = floor(getLowestLevel().toFloat() * shareRangeMultiplier).toInt()

    /**
     * `Party::getSharedExperienceStatus` (party.cpp:701-718) — private in
     * Canary: the leader first, then every member must be `SHAREDEXP_OK`.
     */
    private fun getSharedExperienceStatus(): SharedExpStatus_t {
        val leader = getLeader() ?: return SharedExpStatus_t.SHAREDEXP_EMPTYPARTY
        val leaderStatus = getMemberSharedExperienceStatus(leader)
        if (leaderStatus != SharedExpStatus_t.SHAREDEXP_OK) {
            return leaderStatus
        }

        for (member in getMembers()) {
            val memberStatus = getMemberSharedExperienceStatus(member)
            if (memberStatus != SharedExpStatus_t.SHAREDEXP_OK) {
                return memberStatus
            }
        }
        return SharedExpStatus_t.SHAREDEXP_OK
    }

    /** `Party::isPlayerActive` (party.cpp:692-699) — private in Canary. */
    private fun isPlayerActive(player: Player): Boolean {
        val lastTick = ticksMap[player.id] ?: return false
        val timeDiff = now() - lastTick
        return timeDiff <= 2 * 60 * 1000
    }

    /** `Position::areInRange<30, 30, 1>` (position.hpp:38-41) as used at party.cpp:639. */
    private fun areInRange(first: Position, second: Position): Boolean =
        abs(first.x - second.x) <= 30 && abs(first.y - second.y) <= 30 && abs(first.z - second.z) <= 1

    companion object {
        /** `PARTY_AUTO_SHARE_EXPERIENCE` default (configmanager.cpp:121). */
        const val PARTY_AUTO_SHARE_EXPERIENCE = true

        /** `PARTY_SHARE_RANGE_MULTIPLIER` default (configmanager.cpp:356). */
        const val PARTY_SHARE_RANGE_MULTIPLIER = 1.5f
    }
}
