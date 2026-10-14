package org.opentibia.canary.chat

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.map.Position
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.social.guild.GuildRank
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcripts of the Canary chat behavior:
 * - `Game::internalCreatureSay`/`playerWhisper`/`playerYell`/`playerSay`
 *   (src/game/game.cpp:7411-7657)
 * - `Player::isMuted` (src/creatures/players/player.cpp:3494-3506)
 * - `ChatChannel`/`PrivateChatChannel`/`Chat` (src/creatures/interactions/chat.cpp:20-729)
 */
class ChatManagerTest {

    // -- speech scopes (Game::internalCreatureSay, game.cpp:7618-7657) --

    @Test
    fun `say reaches eight by six tiles on the same floor`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        val inRange = fixture.player(2, "Bob", x = 108, y = 106)
        val tooFarX = fixture.player(3, "Carol", x = 109)
        val tooFarY = fixture.player(4, "Dave", y = 107)
        val otherFloor = fixture.player(5, "Erin", z = 6)

        val recipients = fixture.chat.broadcastSpeech(
            speaker = speaker,
            type = SpeakClasses.TALKTYPE_SAY,
            text = "Hello",
            potentialListeners = listOf(speaker, inRange, tooFarX, tooFarY, otherFloor),
        )

        assertThat(recipients).containsExactlyInAnyOrder(speaker, inRange)
    }

    @Test
    fun `yell reaches eighteen by fourteen tiles across floors`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        val edge = fixture.player(2, "Bob", x = 118, y = 114)
        val tooFarX = fixture.player(3, "Carol", x = 119)
        val tooFarY = fixture.player(4, "Dave", y = 115)
        val otherFloor = fixture.player(5, "Erin", z = 6)

        val recipients = fixture.chat.broadcastSpeech(
            speaker = speaker,
            type = SpeakClasses.TALKTYPE_YELL,
            text = "HELLO",
            potentialListeners = listOf(speaker, edge, tooFarX, tooFarY, otherFloor),
        )

        assertThat(recipients).containsExactlyInAnyOrder(speaker, edge, otherFloor)
    }

    @Test
    fun `whisper reaches only one by one tiles on the same floor`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        val near = fixture.player(2, "Bob", x = 101, y = 100)
        val far = fixture.player(3, "Carol", x = 103)
        val otherFloor = fixture.player(4, "Dave", z = 6)

        val recipients = fixture.chat.broadcastSpeech(
            speaker = speaker,
            type = SpeakClasses.TALKTYPE_WHISPER,
            text = "psst",
            potentialListeners = listOf(speaker, near, far, otherFloor),
        )

        assertThat(recipients).containsExactlyInAnyOrder(speaker, near)
    }

    @Test
    fun `empty speech is never delivered`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        val listener = fixture.player(2, "Bob")

        val recipients = fixture.chat.broadcastSpeech(
            speaker = speaker,
            type = SpeakClasses.TALKTYPE_SAY,
            text = "",
            potentialListeners = listOf(speaker, listener),
        )

        assertThat(recipients).isEmpty()
    }

    // -- muted checks (Player::isMuted, player.cpp:3494-3506; Game::playerSay, game.cpp:7424-7430) --

    @Test
    fun `isMuted returns the longest muted condition in seconds`() {
        val fixture = Fixture()
        val player = fixture.player(1, "Alice")
        player.addCondition(fixture.condition(ConditionType.CONDITION_MUTED, ticks = 3_000, subId = 0))
        player.addCondition(fixture.condition(ConditionType.CONDITION_MUTED, ticks = 5_000, subId = 1))

        assertThat(player.isMuted()).isEqualTo(5)
    }

    @Test
    fun `isMuted returns zero for CannotBeMuted players`() {
        val fixture = Fixture()
        val player = fixture.player(1, "Alice")
        player.setFlag(PlayerFlags_t.CannotBeMuted)
        player.addCondition(fixture.condition(ConditionType.CONDITION_MUTED, ticks = 5_000))

        assertThat(player.isMuted()).isEqualTo(0)
    }

    @Test
    fun `playerSay rejects muted players with the remaining seconds`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        val listener = fixture.player(2, "Bob")
        speaker.addCondition(fixture.condition(ConditionType.CONDITION_MUTED, ticks = 7_000))

        val delivered = fixture.chat.playerSay(
            player = speaker,
            channelId = 0,
            type = SpeakClasses.TALKTYPE_SAY,
            text = "Hello",
            potentialListeners = listOf(listener),
        )

        assertThat(delivered).isFalse()
        assertThat(fixture.client.creatureSays).isEmpty()
        assertThat(fixture.client.textMessages).containsExactly(
            FakeChatClient.TextMessage(speaker, "You are still muted for 7 seconds."),
        )
    }

    @Test
    fun `playerSay suppresses access player commands`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        speaker.group = Group(access = true)
        val listener = fixture.player(2, "Bob")

        val delivered = fixture.chat.playerSay(
            player = speaker,
            channelId = 0,
            type = SpeakClasses.TALKTYPE_SAY,
            text = "/i sword",
            potentialListeners = listOf(listener),
        )

        assertThat(delivered).isFalse()
        assertThat(fixture.client.creatureSays).isEmpty()
    }

    @Test
    fun `playerSay delivers say to listeners within scope`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        val inRange = fixture.player(2, "Bob", x = 107, y = 105)
        val tooFar = fixture.player(3, "Carol", x = 109)

        val delivered = fixture.chat.playerSay(
            player = speaker,
            channelId = 0,
            type = SpeakClasses.TALKTYPE_SAY,
            text = "Hello",
            potentialListeners = listOf(inRange, tooFar),
        )

        assertThat(delivered).isTrue()
        assertThat(fixture.client.creatureSays).containsExactly(
            FakeChatClient.CreatureSay(inRange, speaker, SpeakClasses.TALKTYPE_SAY, "Hello"),
        )
    }

    @Test
    fun `playerSay feeds the message buffer anti-spam on the say path`() {
        // Game::playerSay -> Player::removeMessageBuffer (game.cpp:7437,
        // player.cpp:3514-3543) with MAX_MESSAGEBUFFER = 4.
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        speaker.maxMessageBuffer = 4
        val listener = fixture.player(2, "Bob", x = 101)

        repeat(4) {
            fixture.chat.playerSay(speaker, 0, SpeakClasses.TALKTYPE_SAY, "spam", listOf(listener))
        }

        assertThat(speaker.features.messageBuffer.messageBufferCount).isEqualTo(4)

        fixture.chat.playerSay(speaker, 0, SpeakClasses.TALKTYPE_SAY, "spam", listOf(listener))

        assertThat(speaker.features.messageBuffer.messageBufferCount).isEqualTo(5)
        assertThat(speaker.hasCondition(ConditionType.CONDITION_MUTED)).isTrue()
    }

    @Test
    fun `playerSay leaves the message buffer untouched for private npc messages`() {
        // Game::playerSay skips removeMessageBuffer for TALKTYPE_PRIVATE_PN
        // (game.cpp:7436-7438).
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        speaker.maxMessageBuffer = 4

        fixture.chat.playerSay(speaker, 0, SpeakClasses.TALKTYPE_PRIVATE_PN, "hi", emptyList())

        assertThat(speaker.features.messageBuffer.messageBufferCount).isZero()
    }

    // -- yell rules (Game::playerYell, game.cpp:7517-7535) --

    @Test
    fun `playerYell rejects level one players`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice", level = 1)

        val yelled = fixture.chat.playerYell(speaker, "Hello", emptyList())

        assertThat(yelled).isFalse()
        assertThat(speaker.hasCondition(ConditionType.CONDITION_YELLTICKS)).isFalse()
        assertThat(fixture.client.textMessages).containsExactly(
            FakeChatClient.TextMessage(speaker, "You may not yell as long as you are on level 1."),
        )
    }

    @Test
    fun `playerYell adds a thirty second yell condition for normal players`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")

        val yelled = fixture.chat.playerYell(speaker, "Hello", emptyList())

        assertThat(yelled).isTrue()
        val condition = speaker.getCondition(ConditionType.CONDITION_YELLTICKS)
        assertThat(condition).isNotNull
        assertThat(condition?.ticks).isEqualTo(30_000)
        assertThat(condition?.endTime).isEqualTo(fixture.clock + 30_000)
    }

    @Test
    fun `playerYell rejects players under the yell exhaust condition`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        speaker.addCondition(fixture.condition(ConditionType.CONDITION_YELLTICKS, ticks = 30_000))

        val yelled = fixture.chat.playerYell(speaker, "Hello", emptyList())

        assertThat(yelled).isFalse()
        assertThat(fixture.client.cancelMessages).containsExactly(
            FakeChatClient.CancelMessage(speaker, ReturnValue.YOUAREEXHAUSTED),
        )
    }

    @Test
    fun `playerYell does not add the condition for gamemasters`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        speaker.account = Account(accountType = AccountType.ACCOUNT_TYPE_GAMEMASTER)

        val yelled = fixture.chat.playerYell(speaker, "Hello", emptyList())

        assertThat(yelled).isTrue()
        assertThat(speaker.hasCondition(ConditionType.CONDITION_YELLTICKS)).isFalse()
    }

    @Test
    fun `playerYell uppercases the text and broadcasts across the wide scope`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        val inRange = fixture.player(2, "Bob", x = 118, y = 114)
        val otherFloor = fixture.player(3, "Carol", z = 6)
        val tooFar = fixture.player(4, "Dave", x = 119)

        val yelled = fixture.chat.playerYell(speaker, "hello", listOf(inRange, otherFloor, tooFar))

        assertThat(yelled).isTrue()
        assertThat(fixture.client.creatureSays).containsExactlyInAnyOrder(
            FakeChatClient.CreatureSay(inRange, speaker, SpeakClasses.TALKTYPE_YELL, "HELLO"),
            FakeChatClient.CreatureSay(otherFloor, speaker, SpeakClasses.TALKTYPE_YELL, "HELLO"),
        )
    }

    @Test
    fun `playerWhisper sends pspsps to viewport players beyond one tile`() {
        val fixture = Fixture()
        val speaker = fixture.player(1, "Alice")
        val near = fixture.player(2, "Bob", x = 101)
        val distant = fixture.player(3, "Carol", x = 107, y = 105)
        val outsideViewport = fixture.player(4, "Dave", x = 109)

        fixture.chat.playerSay(
            player = speaker,
            channelId = 0,
            type = SpeakClasses.TALKTYPE_WHISPER,
            text = "psst",
            potentialListeners = listOf(near, distant, outsideViewport),
        )

        assertThat(fixture.client.creatureSays).containsExactlyInAnyOrder(
            FakeChatClient.CreatureSay(near, speaker, SpeakClasses.TALKTYPE_WHISPER, "psst"),
            FakeChatClient.CreatureSay(distant, speaker, SpeakClasses.TALKTYPE_WHISPER, "pspsps"),
        )
    }

    // -- normal channel registry (Chat::load, chat.cpp:349-410) --

    @Test
    fun `load registers normal channels with their public flag`() {
        val fixture = Fixture()

        val loaded = fixture.chat.load(
            """
            <channels>
                <channel id="2" name="Tutor"/>
                <channel id="3" name="World Chat" public="1"/>
            </channels>
            """.trimIndent(),
        )

        assertThat(loaded).isTrue()
        val tutor = fixture.chat.getChannelById(2)
        assertThat(tutor).isNotNull
        assertThat(tutor?.getName()).isEqualTo("Tutor")
        assertThat(tutor?.isPublicChannel()).isFalse()
        val world = fixture.chat.getChannelById(3)
        assertThat(world?.getName()).isEqualTo("World Chat")
        assertThat(world?.isPublicChannel()).isTrue()
        assertThat(fixture.chat.getChannelById(99)).isNull()
    }

    @Test
    fun `load rejects malformed xml`() {
        val fixture = Fixture()

        assertThat(fixture.chat.load("<channels><channel")).isFalse()
    }

    @Test
    fun `load updates an existing channel and re-adds its users`() {
        val fixture = Fixture()
        val player = fixture.player(1, "Alice")
        fixture.chat.load("""<channels><channel id="3" name="World Chat" public="1"/></channels>""")
        val channel = fixture.chat.getChannelById(3)!!
        fixture.chat.addUserToChannel(player, 3)

        val loaded = fixture.chat.load("""<channels><channel id="3" name="World Chat 2" public="0"/></channels>""")

        assertThat(loaded).isTrue()
        val reloaded = fixture.chat.getChannelById(3)
        assertThat(reloaded).isSameAs(channel)
        assertThat(reloaded?.getName()).isEqualTo("World Chat 2")
        assertThat(reloaded?.isPublicChannel()).isFalse()
        assertThat(reloaded?.hasUser(player)).isTrue()
    }

    @Test
    fun `addUserToChannel joins once and rejects a duplicate join`() {
        val fixture = Fixture()
        fixture.chat.load("""<channels><channel id="2" name="Tutor"/></channels>""")
        val player = fixture.player(1, "Alice")

        assertThat(fixture.chat.addUserToChannel(player, 2)).isNotNull
        assertThat(fixture.chat.addUserToChannel(player, 2)).isNull()
        assertThat(fixture.chat.getChannelById(2)?.hasUser(player)).isTrue()
        assertThat(fixture.chat.addUserToChannel(player, 99)).isNull()
    }

    @Test
    fun `removeUserFromChannel leaves and reports missing membership`() {
        val fixture = Fixture()
        fixture.chat.load("""<channels><channel id="2" name="Tutor"/></channels>""")
        val player = fixture.player(1, "Alice")
        fixture.chat.addUserToChannel(player, 2)

        assertThat(fixture.chat.removeUserFromChannel(player, 2)).isTrue()
        assertThat(fixture.chat.removeUserFromChannel(player, 2)).isFalse()
        assertThat(fixture.chat.removeUserFromChannel(player, 99)).isFalse()
    }

    @Test
    fun `talkToChannel delivers to every user and rejects non members`() {
        val fixture = Fixture()
        fixture.chat.load("""<channels><channel id="2" name="Tutor"/></channels>""")
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        val stranger = fixture.player(3, "Carol")
        fixture.chat.addUserToChannel(alice, 2)
        fixture.chat.addUserToChannel(bob, 2)

        val talked = fixture.chat.talkToChannel(alice, SpeakClasses.TALKTYPE_CHANNEL_Y, "hi", 2)

        assertThat(talked).isTrue()
        assertThat(fixture.client.toChannel).containsExactlyInAnyOrder(
            FakeChatClient.ToChannel(alice, alice, SpeakClasses.TALKTYPE_CHANNEL_Y, "hi", 2),
            FakeChatClient.ToChannel(bob, alice, SpeakClasses.TALKTYPE_CHANNEL_Y, "hi", 2),
        )
        assertThat(fixture.chat.talkToChannel(stranger, SpeakClasses.TALKTYPE_CHANNEL_Y, "hi", 2)).isFalse()
        assertThat(fixture.chat.talkToChannel(alice, SpeakClasses.TALKTYPE_CHANNEL_Y, "hi", 99)).isFalse()
    }

    // -- private channels (PrivateChatChannel, chat.cpp:20-99) --

    @Test
    fun `createChannel gives a premium player a private channel starting at one hundred`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        alice.premium = true

        val channel = fixture.chat.createChannel(alice, CHANNEL_PRIVATE)

        assertThat(channel).isNotNull
        assertThat(channel?.getId()).isEqualTo(100)
        assertThat(channel?.getName()).isEqualTo("Alice's Channel")
        assertThat(channel?.getOwner()).isEqualTo(alice.getGUID())
        assertThat(fixture.chat.getPrivateChannel(alice)).isSameAs(channel)
        assertThat(fixture.chat.createChannel(alice, CHANNEL_PRIVATE)).isNull()
    }

    @Test
    fun `createChannel rejects private channels for non premium players`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")

        assertThat(fixture.chat.createChannel(alice, CHANNEL_PRIVATE)).isNull()
        assertThat(fixture.chat.getPrivateChannel(alice)).isNull()
    }

    @Test
    fun `private channel ids are allocated per owner`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        alice.premium = true
        bob.premium = true

        assertThat(fixture.chat.createChannel(alice, CHANNEL_PRIVATE)?.getId()).isEqualTo(100)
        assertThat(fixture.chat.createChannel(bob, CHANNEL_PRIVATE)?.getId()).isEqualTo(101)
    }

    @Test
    fun `invitePlayer invites once and notifies the inviter, invitee and users`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        alice.premium = true
        fixture.chat.createChannel(alice, CHANNEL_PRIVATE)
        val channel = fixture.chat.getPrivateChannel(alice)!!
        fixture.chat.addUserToChannel(alice, 100)

        channel.invitePlayer(alice, bob)

        assertThat(channel.isInvited(bob.getGUID())).isTrue()
        assertThat(channel.isInvited(alice.getGUID())).isTrue()
        assertThat(channel.getInvitedUsers()).containsEntry(bob.id, bob)
        assertThat(fixture.client.textMessages).containsExactlyInAnyOrder(
            FakeChatClient.TextMessage(bob, "Alice invites you to her private chat channel."),
            FakeChatClient.TextMessage(alice, "Bob has been invited."),
        )
        assertThat(fixture.client.channelEvents).containsExactly(
            FakeChatClient.ChannelEventCall(
                alice,
                100,
                "Bob",
                ChannelEvent.CHANNELEVENT_INVITE,
            ),
        )

        val messagesBefore = fixture.client.textMessages.size
        val eventsBefore = fixture.client.channelEvents.size
        channel.invitePlayer(alice, bob)
        assertThat(fixture.client.textMessages).hasSize(messagesBefore)
        assertThat(fixture.client.channelEvents).hasSize(eventsBefore)
    }

    @Test
    fun `removeInvite reports whether the invite existed`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        alice.premium = true
        fixture.chat.createChannel(alice, CHANNEL_PRIVATE)
        val channel = fixture.chat.getPrivateChannel(alice)!!
        channel.invitePlayer(alice, bob)

        assertThat(channel.removeInvite(bob.getGUID())).isTrue()
        assertThat(channel.removeInvite(bob.getGUID())).isFalse()
        assertThat(channel.isInvited(bob.getGUID())).isFalse()
    }

    @Test
    fun `getChannel exposes a private channel only to invited players`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        val stranger = fixture.player(3, "Carol")
        alice.premium = true
        fixture.chat.createChannel(alice, CHANNEL_PRIVATE)
        val channel = fixture.chat.getPrivateChannel(alice)!!
        channel.invitePlayer(alice, bob)

        assertThat(fixture.chat.getChannel(alice, 100)).isSameAs(channel)
        assertThat(fixture.chat.getChannel(bob, 100)).isSameAs(channel)
        assertThat(fixture.chat.getChannel(stranger, 100)).isNull()
    }

    @Test
    fun `excludePlayer removes invite and user, closes it and notifies the users`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        alice.premium = true
        fixture.chat.createChannel(alice, CHANNEL_PRIVATE)
        val channel = fixture.chat.getPrivateChannel(alice)!!
        fixture.chat.addUserToChannel(alice, 100)
        channel.invitePlayer(alice, bob)
        fixture.chat.addUserToChannel(bob, 100)

        channel.excludePlayer(alice, bob)

        assertThat(channel.isInvited(bob.getGUID())).isFalse()
        assertThat(channel.hasUser(bob)).isFalse()
        assertThat(fixture.client.textMessages).contains(
            FakeChatClient.TextMessage(alice, "Bob has been excluded."),
        )
        assertThat(fixture.client.closePrivate).containsExactly(
            FakeChatClient.ClosePrivate(bob, 100),
        )
        assertThat(fixture.client.channelEvents).contains(
            FakeChatClient.ChannelEventCall(
                alice,
                100,
                "Bob",
                ChannelEvent.CHANNELEVENT_EXCLUDE,
            ),
        )
    }

    @Test
    fun `excludePlayer ignores players that were never invited`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        alice.premium = true
        fixture.chat.createChannel(alice, CHANNEL_PRIVATE)
        val channel = fixture.chat.getPrivateChannel(alice)!!

        channel.excludePlayer(alice, bob)

        assertThat(fixture.client.textMessages).isEmpty()
        assertThat(fixture.client.closePrivate).isEmpty()
    }

    @Test
    fun `deleteChannel closes the private channel for every user`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        alice.premium = true
        fixture.chat.createChannel(alice, CHANNEL_PRIVATE)
        val channel = fixture.chat.getPrivateChannel(alice)!!
        fixture.chat.addUserToChannel(alice, 100)
        channel.invitePlayer(alice, bob)
        fixture.chat.addUserToChannel(bob, 100)

        val deleted = fixture.chat.deleteChannel(alice, 100)

        assertThat(deleted).isTrue()
        assertThat(fixture.chat.getPrivateChannel(alice)).isNull()
        assertThat(fixture.client.closePrivate).containsExactlyInAnyOrder(
            FakeChatClient.ClosePrivate(alice, 100),
            FakeChatClient.ClosePrivate(bob, 100),
        )
    }

    @Test
    fun `removeUserFromChannel deletes the private channel when the owner leaves`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        alice.premium = true
        fixture.chat.createChannel(alice, CHANNEL_PRIVATE)
        val channel = fixture.chat.getPrivateChannel(alice)!!
        fixture.chat.addUserToChannel(alice, 100)
        channel.invitePlayer(alice, bob)
        fixture.chat.addUserToChannel(bob, 100)

        val removed = fixture.chat.removeUserFromChannel(alice, 100)

        assertThat(removed).isTrue()
        assertThat(fixture.chat.getPrivateChannel(alice)).isNull()
        assertThat(fixture.client.closePrivate).containsExactly(
            FakeChatClient.ClosePrivate(bob, 100),
        )
    }

    // -- guild channels (Chat::createChannel/deleteChannel, chat.cpp:412-509) --

    @Test
    fun `createChannel creates a guild channel keyed by the guild id`() {
        val fixture = Fixture()
        val guild = Guild(42, "Red Rose")
        val alice = fixture.player(1, "Alice")
        alice.setGuild(guild)

        val channel = fixture.chat.createChannel(alice, CHANNEL_GUILD)

        assertThat(channel).isNotNull
        assertThat(channel?.getName()).isEqualTo("Red Rose")
        assertThat(fixture.chat.getGuildChannelById(42)).isSameAs(channel)
        assertThat(fixture.chat.getChannel(alice, CHANNEL_GUILD)).isSameAs(channel)
        assertThat(fixture.chat.createChannel(alice, CHANNEL_GUILD)).isNull()

        val bob = fixture.player(2, "Bob")
        bob.setGuild(guild)
        assertThat(fixture.chat.getChannel(bob, CHANNEL_GUILD)).isSameAs(channel)

        assertThat(fixture.chat.deleteChannel(alice, CHANNEL_GUILD)).isTrue()
        assertThat(fixture.chat.getGuildChannelById(42)).isNull()
        assertThat(fixture.chat.deleteChannel(alice, CHANNEL_GUILD)).isFalse()
    }

    @Test
    fun `createChannel rejects guild channels without a guild`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")

        assertThat(fixture.chat.createChannel(alice, CHANNEL_GUILD)).isNull()
    }

    @Test
    fun `talkToChannel gives guild officers the orange type and forces members to yellow`() {
        val fixture = Fixture()
        val guild = Guild(42, "Red Rose")
        val leader = fixture.player(1, "Alice")
        val member = fixture.player(2, "Bob")
        leader.setGuild(guild)
        leader.setGuildRank(GuildRank.Leader)
        member.setGuild(guild)
        val channel = fixture.chat.createChannel(leader, CHANNEL_GUILD)!!
        fixture.chat.addUserToChannel(leader, CHANNEL_GUILD)
        fixture.chat.addUserToChannel(member, CHANNEL_GUILD)

        fixture.chat.talkToChannel(member, SpeakClasses.TALKTYPE_CHANNEL_O, "oops", CHANNEL_GUILD)
        fixture.chat.talkToChannel(leader, SpeakClasses.TALKTYPE_CHANNEL_Y, "hi", CHANNEL_GUILD)

        val memberSends = fixture.client.toChannel.filter { it.speaker === member }
        assertThat(memberSends).allMatch { it.type == SpeakClasses.TALKTYPE_CHANNEL_Y }
        val leaderSends = fixture.client.toChannel.filter { it.speaker === leader }
        assertThat(leaderSends).isNotEmpty.allMatch { it.type == SpeakClasses.TALKTYPE_CHANNEL_O }
        assertThat(channel.hasUser(member)).isTrue()
    }

    // -- party channels (Chat::createChannel, chat.cpp:429-438) --

    @Test
    fun `party members share one party channel keyed by the party`() {
        val fixture = Fixture()
        val leader = fixture.player(1, "Leader")
        val member = fixture.player(2, "Member")
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        val channel = fixture.chat.createChannel(leader, CHANNEL_PARTY)

        assertThat(channel).isNotNull
        assertThat(channel?.getName()).isEqualTo("Party")
        assertThat(fixture.chat.getChannel(member, CHANNEL_PARTY)).isSameAs(channel)
        assertThat(fixture.chat.createChannel(member, CHANNEL_PARTY)).isNull()
        fixture.chat.addUserToChannel(leader, CHANNEL_PARTY)
        fixture.chat.addUserToChannel(member, CHANNEL_PARTY)

        fixture.chat.talkToChannel(member, SpeakClasses.TALKTYPE_SAY, "hi", CHANNEL_PARTY)

        assertThat(fixture.client.toChannel).containsExactlyInAnyOrder(
            FakeChatClient.ToChannel(leader, member, SpeakClasses.TALKTYPE_CHANNEL_Y, "hi", CHANNEL_PARTY),
            FakeChatClient.ToChannel(member, member, SpeakClasses.TALKTYPE_CHANNEL_Y, "hi", CHANNEL_PARTY),
        )

        assertThat(fixture.chat.deleteChannel(leader, CHANNEL_PARTY)).isTrue()
        assertThat(fixture.chat.getChannel(leader, CHANNEL_PARTY)).isNull()
    }

    @Test
    fun `parties without members still resolve no party channel`() {
        val fixture = Fixture()
        val player = fixture.player(1, "Solo")

        assertThat(fixture.chat.createChannel(player, CHANNEL_PARTY)).isNull()
        assertThat(fixture.chat.getChannel(player, CHANNEL_PARTY)).isNull()
    }

    // -- channel list (Chat::getChannelList, chat.cpp:594-653) --

    @Test
    fun `getChannelList creates guild and party channels and lists normal channels`() {
        val fixture = Fixture()
        fixture.chat.load(
            """
            <channels>
                <channel id="2" name="Tutor"/>
                <channel id="3" name="World Chat" public="1"/>
            </channels>
            """.trimIndent(),
        )
        val guild = Guild(42, "Red Rose")
        val leader = fixture.player(1, "Leader")
        val member = fixture.player(2, "Member")
        leader.setGuild(guild)
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)

        val list = fixture.chat.getChannelList(leader)

        assertThat(list.map { it.getId() }).contains(2, 3, CHANNEL_GUILD, CHANNEL_PARTY)
        assertThat(fixture.chat.getGuildChannelById(42)).isNotNull
        assertThat(fixture.chat.getChannel(leader, CHANNEL_PARTY)).isNotNull
    }

    @Test
    fun `getChannelList offers the dummy private channel to premium players without one`() {
        val fixture = Fixture()
        val premium = fixture.player(1, "Alice")
        premium.premium = true
        val free = fixture.player(2, "Bob")

        val premiumList = fixture.chat.getChannelList(premium)
        assertThat(premiumList.map { it.getId() }).contains(CHANNEL_PRIVATE)

        val freeList = fixture.chat.getChannelList(free)
        assertThat(freeList.map { it.getId() }).doesNotContain(CHANNEL_PRIVATE)

        val own = fixture.chat.createChannel(premium, CHANNEL_PRIVATE)!!
        val withOwnList = fixture.chat.getChannelList(premium)
        assertThat(withOwnList.map { it.getId() }).contains(own.getId())
        assertThat(withOwnList.map { it.getId() }).doesNotContain(CHANNEL_PRIVATE)
    }

    @Test
    fun `getChannelList lists private channels the player is invited to`() {
        val fixture = Fixture()
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        alice.premium = true
        fixture.chat.createChannel(alice, CHANNEL_PRIVATE)
        val channel = fixture.chat.getPrivateChannel(alice)!!
        channel.invitePlayer(alice, bob)

        assertThat(fixture.chat.getChannelList(bob).map { it.getId() }).contains(100)
    }

    // -- removeUserFromAllChannels (Chat::removeUserFromAllChannels, chat.cpp:531-568) --

    @Test
    fun `removeUserFromAllChannels drops memberships, invites and owned private channels`() {
        val fixture = Fixture()
        fixture.chat.load("""<channels><channel id="2" name="Tutor"/></channels>""")
        val alice = fixture.player(1, "Alice")
        val bob = fixture.player(2, "Bob")
        alice.premium = true
        alice.setGuild(Guild(42, "Red Rose"))
        val party = Party(alice)
        party.invitePlayer(bob)
        party.joinParty(bob)
        fixture.chat.addUserToChannel(alice, 2)
        val guildChannel = fixture.chat.createChannel(alice, CHANNEL_GUILD)!!
        fixture.chat.addUserToChannel(alice, CHANNEL_GUILD)
        val partyChannel = fixture.chat.createChannel(alice, CHANNEL_PARTY)!!
        fixture.chat.addUserToChannel(alice, CHANNEL_PARTY)
        fixture.chat.createChannel(alice, CHANNEL_PRIVATE)
        val privateChannel = fixture.chat.getPrivateChannel(alice)!!
        fixture.chat.addUserToChannel(alice, 100)
        privateChannel.invitePlayer(alice, bob)
        fixture.chat.addUserToChannel(bob, 100)

        fixture.chat.removeUserFromAllChannels(alice)

        assertThat(fixture.chat.getChannelById(2)?.hasUser(alice)).isFalse()
        assertThat(guildChannel.hasUser(alice)).isFalse()
        assertThat(partyChannel.hasUser(alice)).isFalse()
        assertThat(fixture.chat.getPrivateChannel(alice)).isNull()
        assertThat(fixture.chat.getChannel(bob, 100)).isNull()
        assertThat(fixture.client.closePrivate).contains(FakeChatClient.ClosePrivate(bob, 100))
    }

    /**
     * Fixture wiring the injected clock (AGENTS.md 1.1) so the 30s yell
     * condition and the mute durations are deterministic.
     */
    private class Fixture {
        var clock: Long = 1_000_000L

        val client = FakeChatClient()
        val chat = ChatManager(client = client, now = { clock })

        fun player(
            id: Long,
            name: String,
            x: Int = 100,
            y: Int = 100,
            z: Int = 7,
            level: Int = 10,
        ): Player {
            val player = Player(id, name, 100, 100, position = Position(x, y, z), level = level)
            player.now = { clock }
            return player
        }

        fun condition(type: ConditionType, ticks: Int, subId: Int = 0): Condition =
            Condition.createCondition(
                id = ConditionId.CONDITIONID_DEFAULT,
                type = type,
                ticks = ticks,
                subId = subId,
                now = { clock },
            )!!
    }
}
