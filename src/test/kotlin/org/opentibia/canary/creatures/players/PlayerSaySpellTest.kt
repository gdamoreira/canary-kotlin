package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.SpectatorMapAccess
import org.opentibia.canary.map.SpectatorSector
import org.opentibia.canary.map.Spectators

/**
 * Transcript tests for `Player::saySpell`.
 *
 * Source of truth: /Users/damoreira/.projects/canary
 *  - src/creatures/players/player.cpp:11026-11080 (`saySpell`)
 *  - src/utils/const.hpp:29 (`STORAGEVALUE_EMOTE = 30008`)
 *  - src/config/configmanager.cpp:98 (`EMOTE_SPELLS`, default false)
 */
class PlayerSaySpellTest {

    private object NoMapAccess : SpectatorMapAccess {
        override fun getMapSector(x: Int, y: Int): SpectatorSector? = null
    }

    private class Send(
        val recipient: Player,
        val speaker: Player,
        val type: SpeakClasses,
        val text: String,
        val pos: Position?,
    )

    private class World {
        val sends = mutableListOf<Send>()
        val sayEvents = mutableListOf<Triple<Player, SpeakClasses, String>>()
        val messages = mutableListOf<String>()
        var emoteSpells = false
        val storageValues = mutableMapOf<Long, Int>()

        fun player(id: Long, name: String = "Player$id") =
            Player(id = id, name = name, maxHealth = 150, health = 150, position = Position(100, 100, 7))
    }

    private fun findArgs(
        spectatorList: List<Creature>,
    ): (Position, Boolean, Int, Int, Int, Int) -> Spectators = { _, _, _, _, _, _ ->
        Spectators(NoMapAccess).also { spectators -> spectatorList.forEach { spectators.insert(it) } }
    }

    @Test
    fun `saySpell rejects empty text without sending`() {
        // player.cpp:11027-11030.
        val world = World()
        val caster = world.player(1)

        val result = caster.saySpell(
            type = SpeakClasses.TALKTYPE_SAY,
            text = "",
            isGhostMode = false,
            sendCreatureSay = { recipient, speaker, type, text, pos ->
                world.sends.add(Send(recipient, speaker, type, text, pos))
            },
        )

        assertThat(result).isFalse()
        assertThat(world.sends).isEmpty()
    }

    @Test
    fun `saySpell sends the spell-use type to player spectators with the caster position`() {
        // player.cpp:11055-11070.
        val world = World()
        val caster = world.player(1)
        val spectator = world.player(2)
        val monster = Monster(3, "Rat", maxHealth = 10, health = 10, position = Position(101, 100, 7))
        var requested: List<Int> = emptyList()
        var multifloor = false

        val result = caster.saySpell(
            type = SpeakClasses.TALKTYPE_SAY,
            text = "exura",
            isGhostMode = false,
            findSpectators = { _, multi, minX, maxX, minY, maxY ->
                multifloor = multi
                requested = listOf(minX, maxX, minY, maxY)
                Spectators(NoMapAccess).also {
                    it.insert(spectator)
                    it.insert(monster)
                }
            },
            sendCreatureSay = { recipient, speaker, type, text, pos ->
                world.sends.add(Send(recipient, speaker, type, text, pos))
            },
            onCreatureSay = { p, type, text -> world.sayEvents.add(Triple(p, type, text)) },
        )

        assertThat(result).isTrue()
        // player.cpp:11041-11043: non-yell uses the client view window.
        assertThat(multifloor).isFalse()
        assertThat(requested).containsExactly(8, 8, 6, 6)
        assertThat(world.sends).hasSize(1)
        assertThat(world.sends.single().recipient).isSameAs(spectator)
        assertThat(world.sends.single().speaker).isSameAs(caster)
        assertThat(world.sends.single().type).isEqualTo(SpeakClasses.TALKTYPE_SPELL_USE)
        assertThat(world.sends.single().text).isEqualTo("exura")
        assertThat(world.sends.single().pos).isEqualTo(caster.position)
        assertThat(world.sayEvents).containsExactly(Triple(spectator, SpeakClasses.TALKTYPE_SAY, "exura"))
    }

    @Test
    fun `saySpell widens the search for yells`() {
        // player.cpp:11044-11047.
        val world = World()
        val caster = world.player(1)
        var requested: List<Int> = emptyList()
        var multifloor = false

        caster.saySpell(
            type = SpeakClasses.TALKTYPE_YELL,
            text = "help!",
            isGhostMode = false,
            findSpectators = { _, multi, minX, maxX, minY, maxY ->
                multifloor = multi
                requested = listOf(minX, maxX, minY, maxY)
                Spectators(NoMapAccess)
            },
        )

        assertThat(multifloor).isTrue()
        assertThat(requested).containsExactly(18, 18, 14, 14)
    }

    @Test
    fun `emote spell storage value switches the send type per spectator`() {
        // player.cpp:11056-11066.
        val world = World()
        world.emoteSpells = true
        world.storageValues[3L] = 1
        val caster = world.player(1)
        val emote = world.player(3)
        val silent = world.player(4)

        caster.saySpell(
            type = SpeakClasses.TALKTYPE_SAY,
            text = "utevo lux",
            isGhostMode = false,
            findSpectators = findArgs(listOf(emote, silent)),
            emoteSpells = world.emoteSpells,
            getStorageValue = { p, key -> world.storageValues[p.id] ?: 0 },
            sendCreatureSay = { recipient, speaker, type, text, pos ->
                world.sends.add(Send(recipient, speaker, type, text, pos))
            },
        )

        assertThat(world.sends.map { it.recipient.id to it.type }).containsExactly(
            3L to SpeakClasses.TALKTYPE_MONSTER_SAY,
            4L to SpeakClasses.TALKTYPE_SPELL_USE,
        )
    }

    @Test
    fun `ghost mode skips spectators that cannot see the caster`() {
        // player.cpp:11063-11065.
        val world = World()
        val caster = world.player(1)
        val blind = world.player(2)
        val seer = world.player(3)
        seer.setFlag(org.opentibia.canary.creatures.PlayerFlags_t.CanSenseInvisibility)
        caster.addCondition(
            Condition.createCondition(ConditionId.CONDITIONID_DEFAULT, ConditionType.CONDITION_INVISIBLE, 0),
        )

        caster.saySpell(
            type = SpeakClasses.TALKTYPE_SAY,
            text = "ghost words",
            isGhostMode = true,
            findSpectators = findArgs(listOf(blind, seer)),
            sendCreatureSay = { recipient, speaker, type, text, pos ->
                world.sends.add(Send(recipient, speaker, type, text, pos))
            },
        )

        // Only the spectator that can see the invisible caster receives the words.
        assertThat(world.sends).hasSize(1)
        assertThat(world.sends.single().recipient).isSameAs(seer)
    }

    @Test
    fun `an explicit spectator snapshot replaces the local search`() {
        // player.cpp:11048-11050.
        val world = World()
        val caster = world.player(1)
        val spectator = world.player(2)
        var findCalls = 0
        val provided = Spectators(NoMapAccess).also { it.insert(spectator) }

        caster.saySpell(
            type = SpeakClasses.TALKTYPE_SAY,
            text = "cached",
            isGhostMode = false,
            spectatorsPtr = provided,
            findSpectators = { _, _, _, _, _, _ ->
                findCalls++
                Spectators(NoMapAccess)
            },
            sendCreatureSay = { recipient, speaker, type, text, pos ->
                world.sends.add(Send(recipient, speaker, type, text, pos))
            },
        )

        assertThat(findCalls).isZero()
        assertThat(world.sends).hasSize(1)
        assertThat(world.sends.single().recipient).isSameAs(spectator)
    }

    @Test
    fun `an explicit position overrides the caster position and suffix`() {
        // player.cpp:11032-11034, 11058.
        val world = World()
        val caster = world.player(1)
        val spectator = world.player(2)
        val pos = Position(120, 130, 7)

        caster.saySpell(
            type = SpeakClasses.TALKTYPE_SAY,
            text = "far away",
            isGhostMode = false,
            pos = pos,
            findSpectators = findArgs(listOf(spectator)),
            sendCreatureSay = { recipient, speaker, type, text, sendPos ->
                world.sends.add(Send(recipient, speaker, type, text, sendPos))
            },
        )

        assertThat(world.sends.single().pos).isEqualTo(pos)
    }
}
