package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerPronoun_t
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.map.Position
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRank

/**
 * Transcribes `Player::getDescription` (src/creatures/players/player.cpp:
 * 235-328) plus the premium helpers it composes with
 * (`Player::getPremiumDays`, `Player::getPremiumLastDay`, `Player::isVip`,
 * player.cpp:7541-7554).
 *
 * The title component reads (`title().getCurrentTitle[Name]`), the vocation
 * lookup, the loyalty title, the VIP check and `Player::guildNick` are
 * call-site seams per AGENTS.md 1.1; the sentence grammar is transcribed
 * verbatim.
 *
 * The tests route through the local [describe] shim because the host
 * `Creature.getDescription(Int)` member currently shadows the extension until
 * `creatures/Creature.kt` (owned by the host-binding track) delegates to it;
 * the shim's full argument list makes the member inapplicable, so the parity
 * extension is exercised.
 */
class PlayerDescriptionTest {

    private fun newPlayer(name: String = "Hero", level: Int = 10): Player =
        Player(id = 1, name = name, maxHealth = 150, health = 150, position = Position(100, 100, 7), level = level)

    // --- Player::getDescription (lookDistance == -1) ------------------------------

    @Test
    fun `getDescription describes the player to themself`() {
        // player.cpp:243-259.
        val player = newPlayer()
        player.setSex(PlayerSex_t.MALE)

        assertThat(player.describe(-1)).isEqualTo("yourself. You have no vocation.")
    }

    @Test
    fun `getDescription uses the group name for access players`() {
        // `group->access` (247-249).
        val player = newPlayer()
        player.group = Group(name = "Gamemaster", access = true)

        assertThat(player.describe(-1)).isEqualTo("yourself. You are Gamemaster.")
    }

    @Test
    fun `getDescription appends the current title`() {
        // lookDistance == -1 title (239, 244).
        val player = newPlayer()

        assertThat(
            player.describe(
                -1,
                getCurrentTitle = { 3 },
                getCurrentTitleName = { "Demonic" },
            ),
        ).isEqualTo("yourself, Demonic. You have no vocation.")
    }

    @Test
    fun `getDescription appends the loyalty title and vip`() {
        // 251-258.
        val player = newPlayer()

        assertThat(player.describe(-1, loyaltyTitle = { "Sage" }, isVip = { true }))
            .isEqualTo("yourself. You have no vocation. You are a Sage. You are VIP.")
    }

    @Test
    fun `getDescription uses the vocation description for self`() {
        // `vocation->getVocDescription()` (250-253).
        val player = newPlayer()
        val vocation = Vocation(4).also { it.description = "an elite knight" }

        assertThat(player.describe(-1, vocation = { vocation }))
            .isEqualTo("yourself. You are an elite knight.")
    }

    // --- Player::getDescription (lookDistance != -1) ------------------------------

    @Test
    fun `getDescription describes by level and third person`() {
        // 260-276: `name (Level N). She is ...` with the capitalized pronoun.
        val player = newPlayer()

        assertThat(player.describe(5)).isEqualTo("Hero (Level 10). She has no vocation.")
    }

    @Test
    fun `getDescription hides the level for access players`() {
        // `if (!group->access) s << " (Level ..." (263-265).
        val player = newPlayer()
        player.group = Group(name = "God", access = true)

        assertThat(player.describe(5)).isEqualTo("Hero. She is God.")
    }

    @Test
    fun `getDescription keeps a name pronoun capitalized`() {
        // `capitalizeWords(subjectPronoun)` (238).
        val player = newPlayer(name = "rani")
        player.setPronoun(PlayerPronoun_t.PLAYERPRONOUN_NAME)

        assertThat(player.describe(5)).isEqualTo("rani (Level 10). Rani has no vocation.")
    }

    @Test
    fun `getDescription picks the loyalty article by leading vowel`() {
        // 277-287: `an` before A/E/I/O/U, else `a`.
        val player = newPlayer()

        assertThat(player.describe(5, loyaltyTitle = { "Elder" }))
            .isEqualTo("Hero (Level 10). She has no vocation. She is an Elder.")
        assertThat(player.describe(5, loyaltyTitle = { "Sage" }))
            .isEqualTo("Hero (Level 10). She has no vocation. She is a Sage.")
    }

    @Test
    fun `getDescription appends the third person vip sentence`() {
        // 289-291.
        val player = newPlayer()

        assertThat(player.describe(5, isVip = { true }))
            .isEqualTo("Hero (Level 10). She has no vocation. She is VIP.")
    }

    // --- party block --------------------------------------------------------------

    @Test
    fun `getDescription counts the leader as a party member`() {
        // 293-311: `getMemberCount() + 1`, singular/plural invitation forms.
        val leader = newPlayer(name = "Leader")
        val party = Party(leader)
        leader.setParty(party)

        assertThat(leader.describe(-1))
            .isEqualTo("yourself. You have no vocation. Your party has 1 member and 0 pending invitations.")
    }

    @Test
    fun `getDescription describes an external party membership`() {
        // 296-301: `She is in a party with N members and M pending invitations.`
        val leader = newPlayer(name = "Leader")
        val member = newPlayer(name = "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        assertThat(leader.describe(5)).isEqualTo(
            "Leader (Level 10). She has no vocation. She is in a party with 2 members and 0 pending invitations.",
        )
    }

    @Test
    fun `getDescription uses the singular pending invitation form`() {
        // `invitationCount == 1` (306-310).
        val leader = newPlayer(name = "Leader")
        val invitee = newPlayer(name = "Invitee")
        val party = Party(leader)
        party.invitePlayer(invitee)
        leader.setParty(party)

        assertThat(leader.describe(-1)).isEqualTo(
            "yourself. You have no vocation. Your party has 1 member and 1 pending invitation.",
        )
    }

    // --- guild block --------------------------------------------------------------

    @Test
    fun `getDescription renders the guild rank and online count`() {
        // 313-327.
        val player = newPlayer(name = "Hero")
        val guild = Guild(id = 1, name = "Red Rose")
        guild.addRank(1, "Member", 1)
        guild.addRank(2, "Vice-Leader", 2)
        guild.setMemberCount(2)
        player.setGuild(guild)
        player.setGuildRank(GuildRank.ViceLeader)

        assertThat(player.describe(5, guildNick = { "Ross" })).isEqualTo(
            "Hero (Level 10). She has no vocation. She is Vice-Leader of the Red Rose (Ross), " +
                "which has 2 members, 1 of them online.",
        )
    }

    @Test
    fun `getDescription omits the guild block for thousand member guilds`() {
        // `memberCount >= 1000 -> return s.str()` (315-318).
        val player = newPlayer(name = "Hero")
        val guild = Guild(id = 1, name = "Mega")
        guild.setMemberCount(1000)
        player.setGuild(guild)
        player.setGuildRank(GuildRank.Member)

        assertThat(player.describe(5)).isEqualTo("Hero (Level 10). She has no vocation.")
    }

    @Test
    fun `getDescription uses the singular guild member form`() {
        // `memberCount == 1` (322-327).
        val player = newPlayer(name = "Hero")
        val guild = Guild(id = 1, name = "Solo")
        guild.setMemberCount(1)
        player.setGuild(guild)
        player.setGuildRank(GuildRank.Leader)

        assertThat(player.describe(-1)).isEqualTo(
            "yourself. You have no vocation. You are The Leader of the Solo, " +
                "which has 1 member, 1 of them online.",
        )
    }

    /**
     * Calls the `Player::getDescription` extension with every parameter
     * explicit; the full argument list makes the shadowing
     * `Creature.getDescription(Int)` member inapplicable.
     */
    private fun Player.describe(
        lookDistance: Int,
        getCurrentTitle: () -> Int = { 0 },
        getCurrentTitleName: () -> String = { "" },
        vocation: () -> Vocation? = { vocations?.getVocation(vocationId) },
        loyaltyTitle: () -> String = { "" },
        isVip: () -> Boolean = { false },
        guildNick: () -> String = { "" },
    ): String = getDescription(
        lookDistance,
        getCurrentTitle,
        getCurrentTitleName,
        vocation,
        loyaltyTitle,
        isVip,
        guildNick,
    )
}
