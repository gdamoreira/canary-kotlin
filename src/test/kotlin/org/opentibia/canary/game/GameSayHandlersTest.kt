package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.spells.PlayerSaySpellResult
import org.opentibia.canary.spells.TalkActionResult
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcript tests for the `Game::playerSay*` family:
 *
 *  - `Game::playerSay` (game.cpp:7411)
 *  - `Game::playerSaySpell` (game.cpp:7477)
 *  - `Game::playerWhisper` (game.cpp:7497)
 *  - `Game::playerYell` (game.cpp:7517)
 *  - `Game::playerSpeakTo` (game.cpp:7537)
 *  - `Game::playerSpeakToNpc` (game.cpp:7563)
 */
class GameSayHandlersTest {

    private val center = Position(1000, 1000, 7)

    private class SayHarness(
        val game: Game,
        val speaker: Player,
        val said: MutableList<Triple<Creature, SpeakClasses, String>>,
        val texts: MutableList<Pair<MessageClass, String>>,
        val cancels: MutableList<ReturnValue>,
        val privateSends: MutableList<Triple<Player, SpeakClasses, String>>,
    )

    private fun newGame(): SayHarness {
        SpectatorsClear()
        val game = Game(GameMap(), GameDispatcher())
        val speaker = Player(id = 1, name = "Speaker", maxHealth = 150, health = 150, position = center)
        val said = mutableListOf<Triple<Creature, SpeakClasses, String>>()
        val texts = mutableListOf<Pair<MessageClass, String>>()
        val cancels = mutableListOf<ReturnValue>()
        val privateSends = mutableListOf<Triple<Player, SpeakClasses, String>>()
        speaker.sendTextMessageTyped = { type, text -> texts.add(type to text) }
        game.internalCreatureSay = { creature, type, text, _, _ -> said.add(Triple(creature, type, text)) }
        game.sendTextMessage = { _, type, text -> texts.add(type to text) }
        game.sendCancelMessage = { _, value -> cancels.add(value) }
        game.sendCancelMessageText = { _, text -> texts.add(MessageClass.MESSAGE_FAILURE to text) }
        game.sendPrivateMessage = { target, _, type, text -> privateSends.add(Triple(target, type, text)) }
        game.addPlayer(speaker)
        return SayHarness(game, speaker, said, texts, cancels, privateSends)
    }

    private fun SpectatorsClear() = org.opentibia.canary.map.Spectators.clearCache()

    private fun groundTile(game: Game, position: Position = center) {
        game.map.getOrCreateTile(position).ground = Item(id = 100)
    }

    // ----- playerSay -----

    @Test
    fun `playerSay resets the timers and broadcasts TALKTYPE_SAY`() {
        val harness = newGame()
        var loginResets = 0
        var idleResets = 0
        harness.game.resetLoginProtection = { loginResets++ }
        harness.game.resetIdleTime = { idleResets++ }

        harness.game.playerSay(1, 0, SpeakClasses.TALKTYPE_SAY, "", "hello")

        assertThat(loginResets).isEqualTo(1)
        assertThat(idleResets).isEqualTo(1)
        assertThat(harness.said).containsExactly(Triple(harness.speaker, SpeakClasses.TALKTYPE_SAY, "hello"))
    }

    @Test
    fun `playerSay stops on a talk action break`() {
        val harness = newGame()
        harness.game.checkPlayerCanSayTalkAction = { _, _, _ -> TalkActionResult.TALKACTION_BREAK }

        harness.game.playerSay(1, 0, SpeakClasses.TALKTYPE_SAY, "", "!command")

        assertThat(harness.said).isEmpty()
    }

    @Test
    fun `playerSay spell break cancels the push unless push-when-attacking`() {
        val harness = newGame()
        var pushCancels = 0
        harness.game.cancelPush = { pushCancels++ }
        harness.game.spellsPlayerSaySpell = { _, words -> PlayerSaySpellResult(TalkActionResult.TALKACTION_BREAK, words) }
        harness.game.playerSaySpellWords = { _, _, _, _ -> true }

        harness.game.playerSay(1, 0, SpeakClasses.TALKTYPE_SAY, "", "exura")

        assertThat(pushCancels).isEqualTo(1)
        assertThat(harness.said).isEmpty()

        harness.game.pushWhenAttacking = true
        harness.game.playerSay(1, 0, SpeakClasses.TALKTYPE_SAY, "", "exura")
        assertThat(pushCancels).isEqualTo(1)
    }

    @Test
    fun `playerSay spell failure consumes the input`() {
        val harness = newGame()
        var saySpellCalls = 0
        harness.game.spellsPlayerSaySpell = { _, words -> PlayerSaySpellResult(TalkActionResult.TALKACTION_FAILED, words) }
        harness.game.playerSaySpellWords = { _, _, _, _ -> saySpellCalls++; true }

        harness.game.playerSay(1, 0, SpeakClasses.TALKTYPE_SAY, "", "bad spell")

        assertThat(saySpellCalls).isZero()
        assertThat(harness.said).isEmpty()
    }

    @Test
    fun `playerSay rejects a muted speaker`() {
        val harness = newGame()
        harness.speaker.addCondition(
            org.opentibia.canary.combat.condition.Condition.createCondition(
                id = org.opentibia.canary.combat.condition.ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_MUTED,
                ticks = 10_000,
            ),
        )

        harness.game.playerSay(1, 0, SpeakClasses.TALKTYPE_SAY, "", "hello")

        assertThat(harness.texts).anyMatch {
            it.second == "You are still muted for 10 seconds."
        }
        assertThat(harness.said).isEmpty()
    }

    @Test
    fun `playerSay routes channel types through the talkToChannel seam`() {
        val harness = newGame()
        val channels = mutableListOf<Triple<SpeakClasses, String, Int>>()
        harness.game.talkToChannel = { _, type, text, channelId -> channels.add(Triple(type, text, channelId)) }

        harness.game.playerSay(1, 5, SpeakClasses.TALKTYPE_CHANNEL_Y, "", "hi channel")

        assertThat(channels).containsExactly(Triple(SpeakClasses.TALKTYPE_CHANNEL_Y, "hi channel", 5))
    }

    @Test
    fun `playerSay routes broadcast through playerBroadcastMessage and unknowns nowhere`() {
        val harness = newGame()
        val target = Player(id = 2, name = "Target", maxHealth = 150, health = 150, position = center)
        harness.game.addPlayer(target)
        harness.speaker.setFlag(org.opentibia.canary.creatures.PlayerFlags_t.CanBroadcast)

        harness.game.playerSay(1, 0, SpeakClasses.TALKTYPE_BROADCAST, "", "news")
        harness.game.playerSay(1, 0, SpeakClasses.TALKTYPE_SPELL_USE, "", "ignored")

        assertThat(harness.privateSends).containsExactly(
            Triple(harness.speaker, SpeakClasses.TALKTYPE_BROADCAST, "news"),
            Triple(target, SpeakClasses.TALKTYPE_BROADCAST, "news"),
        )
        assertThat(harness.said).isEmpty()
    }

    // ----- playerYell -----

    @Test
    fun `playerYell rejects level one and yells uppercased otherwise`() {
        val harness = newGame()
        harness.speaker.level = 1

        harness.game.playerYell(harness.speaker, "help")

        assertThat(harness.texts).anyMatch { it.second == "You may not yell as long as you are on level 1." }
        assertThat(harness.said).isEmpty()

        harness.speaker.level = 20
        val result = harness.game.playerYell(harness.speaker, "help")

        assertThat(result).isTrue()
        assertThat(harness.said).containsExactly(Triple(harness.speaker, SpeakClasses.TALKTYPE_YELL, "HELP"))
        assertThat(harness.speaker.hasCondition(ConditionType.CONDITION_YELLTICKS)).isTrue()
    }

    @Test
    fun `playerYell rejects an active yell exhaust`() {
        val harness = newGame()
        harness.speaker.level = 20
        harness.speaker.addCondition(
            org.opentibia.canary.combat.condition.Condition.createCondition(
                id = org.opentibia.canary.combat.condition.ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_YELLTICKS,
                ticks = 30_000,
            ),
        )

        val result = harness.game.playerYell(harness.speaker, "help")

        assertThat(result).isFalse()
        assertThat(harness.cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
    }

    // ----- playerSpeakTo -----

    @Test
    fun `playerSpeakTo rejects an unknown receiver`() {
        val harness = newGame()

        val result = harness.game.playerSpeakTo(harness.speaker, SpeakClasses.TALKTYPE_PRIVATE_TO, "Nobody", "hi")

        assertThat(result).isFalse()
        assertThat(harness.texts).anyMatch { it.second == "A player with this name is not online." }
    }

    @Test
    fun `playerSpeakTo delivers the private message and reports the target`() {
        val harness = newGame()
        val target = Player(id = 2, name = "Target", maxHealth = 150, health = 150, position = Position(1001, 1000, 7))
        harness.game.addPlayer(target)

        val result = harness.game.playerSpeakTo(harness.speaker, SpeakClasses.TALKTYPE_PRIVATE_TO, "Target", "hi")

        assertThat(result).isTrue()
        assertThat(harness.privateSends).containsExactly(Triple(target, SpeakClasses.TALKTYPE_PRIVATE_FROM, "hi"))
        assertThat(harness.texts).anyMatch { it.second == "Message sent to Target." }
    }

    @Test
    fun `playerSpeakTo hides a ghost receiver from non-access speakers`() {
        val harness = newGame()
        val ghost = Player(id = 2, name = "Ghost", maxHealth = 150, health = 150, position = Position(1001, 1000, 7))
        harness.game.addPlayer(ghost)
        val game = Game(
            harness.game.map,
            harness.game.dispatcher,
            isInGhostMode = { it === ghost },
        )
        game.internalCreatureSay = harness.game.internalCreatureSay
        game.sendTextMessage = { _, type, text -> harness.texts.add(type to text) }
        game.sendPrivateMessage = { target, _, type, text -> harness.privateSends.add(Triple(target, type, text)) }
        game.addPlayer(harness.speaker)
        game.addPlayer(ghost)

        val result = game.playerSpeakTo(harness.speaker, SpeakClasses.TALKTYPE_PRIVATE_TO, "Ghost", "hi")

        assertThat(result).isTrue()
        assertThat(harness.texts).anyMatch { it.second == "A player with this name is not online." }
    }

    // ----- playerSpeakToNpc -----

    @Test
    fun `playerSpeakToNpc forwards to the nearby npc and refreshes the ui timer`() {
        val harness = newGame()
        groundTile(harness.game)
        val npc = Npc(id = 5, name = "Bob", position = center)
        val npcSays = mutableListOf<Triple<Long, Int, String>>()
        npc.registerOnCreatureSayHandler { _, creature, type, text ->
            npcSays.add(Triple((creature as? Player)?.id ?: 0L, type, text))
        }
        assertThat(harness.game.internalPlaceCreature(npc, center)).isTrue()
        var uiRefreshes = 0
        harness.game.isUIExhausted = { false }
        harness.game.updateUIExhausted = { uiRefreshes++ }

        harness.game.playerSpeakToNpc(harness.speaker, "hello bob")

        assertThat(npcSays).containsExactly(Triple(1L, SpeakClasses.TALKTYPE_PRIVATE_PN.value, "hello bob"))
        assertThat(uiRefreshes).isEqualTo(1)
    }

    @Test
    fun `playerSpeakToNpc rejects an exhausted speaker`() {
        val harness = newGame()
        harness.game.isUIExhausted = { true }

        harness.game.playerSpeakToNpc(harness.speaker, "hello")

        assertThat(harness.cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
    }
}
