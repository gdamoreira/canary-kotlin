package org.opentibia.canary.creatures.players

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.utils.Tools

/**
 * `Player::getDescription` (src/creatures/players/player.cpp:235-328).
 *
 * The host already owns the pronoun/sex, level, group, party and guild state,
 * so the transcription is an extension function that keeps the exact `Player::`
 * name. The title reads (`title().getCurrentTitle[Name]`), the vocation lookup,
 * the loyalty title, the VIP flag (`Player::isVip`, PlayerSocialState.kt) and
 * the `Player::guildNick` state (`PlayerSocialState`) are call-site seams per
 * AGENTS.md 1.1, because the host `creatures/Creature.kt` is out of this wave's
 * ownership.
 */

/**
 * `Player::getDescription(lookDistance)` (player.cpp:235-328): the look
 * description with the pronoun/title/vocation/loyalty/VIP/party/guild grammar
 * transcribed verbatim.
 */
fun Player.getDescription(
    /** The `lookDistance` argument (player.cpp:235); `-1` is the self look. */
    lookDistance: Int,
    /** `title().getCurrentTitle()` (player.cpp:239). */
    getCurrentTitle: () -> Int = { 0 },
    /** `title().getCurrentTitleName()` (player.cpp:239). */
    getCurrentTitleName: () -> String = { "" },
    /** `Player::vocation` (player.hpp:1755); `null` reads as `VOCATION_NONE`. */
    vocation: () -> Vocation? = { vocations?.getVocation(vocationId) },
    /** `Player::loyaltyTitle` (player.hpp:1709). */
    loyaltyTitle: () -> String = { "" },
    /** `Player::isVip()` (player.cpp:7550). */
    isVip: () -> Boolean = { false },
    /** `Player::guildNick` (player.hpp:1852 neighbourhood). */
    guildNick: () -> String = { "" },
): String {
    val s = StringBuilder()
    val subjectPronoun = Tools.capitalizeWords(getSubjectPronoun())
    val playerTitle = if (getCurrentTitle() == 0) "" else ", " + getCurrentTitleName()

    val group = group
    val groupName = group?.name ?: ""
    val hasAccess = group?.access == true
    val currentVocation = vocation()
    val hasVocation = currentVocation != null && currentVocation.getId() != Vocation.VOCATION_NONE
    val vocationDescription = currentVocation?.getVocDescription() ?: ""
    val loyalty = loyaltyTitle()
    val vip = isVip()

    if (lookDistance == -1) {
        s.append("yourself").append(playerTitle).append('.')

        if (hasAccess) {
            s.append(" You are ").append(groupName).append('.')
        } else if (hasVocation) {
            s.append(" You are ").append(vocationDescription).append('.')
        } else {
            s.append(" You have no vocation.")
        }

        if (loyalty.isNotEmpty()) {
            s.append(" You are a ").append(loyalty).append('.')
        }

        if (vip) {
            s.append(" You are VIP.")
        }
    } else {
        s.append(name)
        if (!hasAccess) {
            s.append(" (Level ").append(level).append(')')
        }

        s.append(playerTitle).append(". ").append(subjectPronoun)

        if (hasAccess) {
            s.append(' ').append(getSubjectVerb()).append(' ').append(groupName).append('.')
        } else if (hasVocation) {
            s.append(' ').append(getSubjectVerb()).append(' ').append(vocationDescription).append('.')
        } else {
            s.append(" has no vocation.")
        }

        if (loyalty.isNotEmpty()) {
            val article = if (loyalty[0] == 'A' || loyalty[0] == 'E' || loyalty[0] == 'I' ||
                loyalty[0] == 'O' || loyalty[0] == 'U'
            ) {
                "an"
            } else {
                "a"
            }
            s.append(' ').append(subjectPronoun).append(' ').append(getSubjectVerb()).append(' ')
                .append(article).append(' ').append(loyalty).append('.')
        }

        if (vip) {
            s.append(' ').append(subjectPronoun).append(' ').append(getSubjectVerb()).append(" VIP.")
        }
    }

    val party = getParty()
    if (party != null) {
        if (lookDistance == -1) {
            s.append(" Your party has ")
        } else {
            s.append(' ').append(subjectPronoun).append(' ').append(getSubjectVerb()).append(" in a party with ")
        }

        val memberCount = party.getMemberCount() + 1
        if (memberCount == 1) {
            s.append("1 member and ")
        } else {
            s.append(memberCount).append(" members and ")
        }

        val invitationCount = party.getInvitationCount()
        if (invitationCount == 1) {
            s.append("1 pending invitation.")
        } else {
            s.append(invitationCount).append(" pending invitations.")
        }
    }

    val guild = getGuild()
    val guildRank = getGuildRank()
    if (guild != null && guildRank != null) {
        val memberCount = guild.getMemberCount()
        if (memberCount >= 1000) {
            return s.toString()
        }

        if (lookDistance == -1) {
            s.append(" You are ")
        } else {
            s.append(' ').append(subjectPronoun).append(' ').append(getSubjectVerb()).append(' ')
        }

        s.append(guildRank.name).append(" of the ").append(guild.name)
        val nick = guildNick()
        if (nick.isNotEmpty()) {
            s.append(" (").append(nick).append(')')
        }

        val onlineCount = guild.getMembersOnline().size
        if (memberCount == 1L) {
            s.append(", which has 1 member, ").append(onlineCount).append(" of them online.")
        } else {
            s.append(", which has ").append(memberCount).append(" members, ").append(onlineCount)
                .append(" of them online.")
        }
    }

    return s.toString()
}
