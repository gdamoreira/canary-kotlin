package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.SkillState
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.creatures.vocations.Vocations
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.systems.bestiary.Charm
import org.opentibia.canary.systems.bestiary.CharmRune
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transcribes `Player::death` (src/creatures/players/player.cpp:4035-4284) and
 * the state it reads/writes (player.hpp:1711, 1722, 1849-1851; creature.hpp:932).
 *
 * The out-of-scope `Player` (creatures/Creature.kt) holds the public state
 * (level, experience, magLevel, health/mana overrides, blessings, skull) and
 * every private member (skills array, levelPercent, magLevelPercent, lastLogout,
 * damageMap, skillLoss latch) is a documented constructor seam, per the wave
 * convention for state owned by out-of-scope types.
 *
 * The Knight expectations use the shipped `data/XML/vocations.xml` values
 * (`gainhp=15`, `gainmana=5`, `gaincap=25`, `manamultiplier=3.0`), loaded through
 * the public `Vocations::loadFromXml` like [org.opentibia.canary.creatures.vocations.VocationTest].
 */
class PlayerDeathStateTest {

    private var now = 1_000_000L
    private var timeNow = 1_700_000_000L

    private val vocations: Vocations = Vocations().apply {
        assertThat(loadFromXml(realXml())).isTrue()
    }

    private val knight: Vocation get() = vocations.getVocation(4)!!

    private class Sink {
        var skillLoss = true
        var skills: List<SkillState> = List(13) { SkillState() }
        var levelPercent: Int? = null
        var magLevelPercent: Double? = null
        var lastLogout: Long? = null
        var damageMap: Map<Long, CountBlock> = emptyMap()
        var playersById: Map<Long, Player> = emptyMap()
        var templePosition = Position(50, 60, 7)
        var loginPosition: Position? = null
        var isMounted = false
        var setSkillLossCalls = mutableListOf<Boolean>()
        val messages = mutableListOf<Pair<MessageClass, String>>()
        val statsSends = mutableListOf<Unit>()
        var skillsSends = 0
        var blessingStatusSends = 0
        val reLoginWindows = mutableListOf<Int>()
        val soundEffects = mutableListOf<Triple<Position, SoundEffect, Creature>>()
        val removedItemTypes = mutableListOf<Triple<Int, Int, Int>>()
        var despawns = 0
        val teleports = mutableListOf<Triple<Player, Position, Boolean>>()
        var creatureHealth = 0
        var playerMana = 0
        val outfitChanges = mutableListOf<Pair<Player, Outfit>>()
        var dismounts = 0
        val logs = mutableListOf<String>()
        var charm: Charm? = null
        var monsterType: MonsterType? = null
        val loseExperienceEvents = mutableListOf<Long>()
        val loseExperienceCallbacks = mutableListOf<Long>()
    }

    private fun newPlayer(
        level: Int = 30,
        experience: Long = 400_000,
        magLevel: Int = 3,
        vocationId: Int = 4,
    ): Player = Player(
        id = 1,
        name = "Hero",
        maxHealth = 500,
        health = 120,
        maxMana = 300,
        mana = 10,
        // Hundredths, like the C++ `Player::capacity` (`JdbcPlayerRepository`
        // persists `capacity / 100`), so the vocation `getCapGain()` (=2500 for
        // the shipped Knight) is subtracted without a scale mismatch.
        capacity = 40_000.0,
        position = Position(100, 100, 7),
    ).apply {
        this.level = level
        this.experience = experience
        this.magLevel = magLevel
        this.vocationId = vocationId
        vocations = this@PlayerDeathStateTest.vocations
        sendTextMessageTyped = { messageClass, text -> sink.messages.add(messageClass to text) }
        sendBlessStatus = { sink.blessingStatusSends++ }
        sendSkills = { sink.skillsSends++ }
    }

    private val sink = Sink()

    private fun newState(
        player: Player,
        sink: Sink = this.sink,
        manaSpent: Long = 1000,
        toggleMountInPz: Boolean = false,
    ): PlayerDeathState = PlayerDeathState(
        player = player,
        now = { now },
        getTimeNow = { timeNow },
        toggleMountInPz = toggleMountInPz,
        adventurersBlessingLevel = { 21 },
        getSkillLoss = { sink.skillLoss },
        setSkillLoss = { sink.setSkillLossCalls.add(it) },
        skills = sink.skills,
        setLevelPercent = { sink.levelPercent = it },
        setMagLevelPercent = { sink.magLevelPercent = it },
        setLastLogout = { sink.lastLogout = it },
        getDamageMap = { sink.damageMap },
        getPlayerById = { sink.playersById[it] },
        getVocation = { knight },
        getBestiaryCharm = { sink.charm },
        getMonsterType = { sink.monsterType },
        eventPlayerOnLoseExperience = { _, exp -> sink.loseExperienceEvents.add(exp) },
        executePlayerOnLoseExperienceCallback = { _, exp -> sink.loseExperienceCallbacks.add(exp) },
        sendStats = { sink.statsSends.add(Unit) },
        sendReLoginWindow = { sink.reLoginWindows.add(it) },
        sendSingleSoundEffect = { position, sound, source -> sink.soundEffects.add(Triple(position, sound, source)) },
        removeItemOfType = { id, amount, subType -> sink.removedItemTypes.add(Triple(id, amount, subType)) },
        despawn = { sink.despawns++ },
        internalTeleport = { p, position, teleportEffect -> sink.teleports.add(Triple(p, position, teleportEffect)) },
        addCreatureHealth = { sink.creatureHealth++ },
        addPlayerMana = { sink.playerMana++ },
        internalCreatureChangeOutfit = { p, outfit -> sink.outfitChanges.add(p to outfit) },
        getTemplePosition = { sink.templePosition },
        setLoginPosition = { sink.loginPosition = it },
        isMounted = { sink.isMounted },
        dismount = { sink.dismounts++ },
        logDebug = { sink.logs.add(it) },
    ).also { it.manaSpent = manaSpent }

    // --- skill-loss death (player.cpp:4043-4252) -----------------------------------

    @Test
    fun `death applies the level formula to experience and magic level`() {
        // player.cpp:4078-4113: getLostPercent() then the magic-level loop.
        // Knight, level 30, 400000 exp: loss = 7.58 -> 0.0758, expLoss =
        // ceil(400000 * 0.0758) = 30321, mana 1600+4800+14400+1000 = 21800,
        // lostMana = 1652 -> magLevel 3 -> 2, manaSpent 13748, percent 95.47.
        sink.skills = List(13) { SkillState() }.toMutableList().also {
            it[2] = SkillState(tries = 30, level = 12, percent = 0.0)
        }
        val player = newPlayer()
        val state = newState(player)

        state.death(null)

        assertThat(player.experience).isEqualTo(369_679)
        assertThat(player.level).isEqualTo(30)
        assertThat(player.magLevel).isEqualTo(2)
        assertThat(state.manaSpent).isEqualTo(13_748)
        assertThat(state.getSpentMana()).isEqualTo(13_748)
        assertThat(sink.magLevelPercent).isEqualTo(95.47)
        assertThat(sink.levelPercent).isEqualTo(3)
        assertThat(sink.lastLogout).isEqualTo(timeNow)
    }

    @Test
    fun `death applies the skill loop with the vocation requirement table`() {
        // player.cpp:4121-4139: sumSkillTries = req(11..level) + tries.
        // Sword (50 base, 1.1 mult): req(11)=50, req(12)=55 -> (105+30) * 0.0758
        // = 10 lost tries; 30 -> 20 tries, percent = 20/55 -> 36.36.
        val skills = List(13) { SkillState() }.toMutableList().also {
            it[2] = SkillState(tries = 30, level = 12, percent = 0.0)
        }
        sink.skills = skills
        val player = newPlayer()
        val state = newState(player)

        state.death(null)

        assertThat(skills[2].tries).isEqualTo(20)
        assertThat(skills[2].level).isEqualTo(12)
        assertThat(skills[2].percent).isEqualTo(36.36)
        assertThat(skills[0].tries).isZero()
        assertThat(skills[0].level).isEqualTo(10)
        assertThat(skills[0].percent).isEqualTo(0.0)
    }

    @Test
    fun `death floors a skill at level 10 when the loss exceeds its tries`() {
        // player.cpp:4126-4132: the loop breaks at level 10 with 0 tries once the
        // remaining loss exceeds the level-11 requirement. Only a configured
        // DEATH_LOSE_PERCENT above 100 can drive the loop that far; the player
        // keeps zero experience so the level block is skipped.
        val skills = List(13) { SkillState() }.toMutableList().also {
            it[2] = SkillState(tries = 0, level = 11, percent = 0.0)
        }
        sink.skills = skills
        val player = newPlayer(level = 11, experience = 0)
        player.deathLosePercent = 200
        val state = newState(player, manaSpent = 0)

        state.death(null)

        assertThat(skills[2].level).isEqualTo(10)
        assertThat(skills[2].tries).isZero()
        assertThat(skills[2].percent).isEqualTo(0.0)
    }

    @Test
    fun `death sends the exact battle messages`() {
        // player.cpp:4119, 4139, 4173-4178, 4200-4220.
        val player = newPlayer()
        val state = newState(player)

        state.death(null)

        assertThat(sink.messages.map { it.second }).containsExactly(
            "You are dead.",
            "You lost 30321 experience.",
            "You died during PvE.",
            "You weren't protected with any blessings.",
        )
        assertThat(sink.messages.map { it.first }).containsOnly(MessageClass.MESSAGE_EVENT_ADVANCE)
        assertThat(sink.loseExperienceEvents).containsExactly(30_321L)
        assertThat(sink.loseExperienceCallbacks).containsExactly(30_321L)
        assertThat(sink.logs).containsExactly("[death] - experience lost 30321")
    }

    @Test
    fun `death restores health and mana to the maxima`() {
        // player.cpp:4229-4234.
        val player = newPlayer()
        val state = newState(player, manaSpent = 0)

        state.death(null)

        assertThat(player.health).isEqualTo(player.maxHealth)
        assertThat(player.mana).isEqualTo(player.maxMana)
        assertThat(sink.statsSends).hasSize(1)
        assertThat(sink.blessingStatusSends).isEqualTo(1)
        assertThat(sink.skillsSends).isEqualTo(1)
    }

    @Test
    fun `black skull death leaves 40 health and no mana`() {
        // player.cpp:4229-4233: SKULL_BLACK keeps health at 40 and mana at 0.
        val player = newPlayer()
        player.setSkull(org.opentibia.canary.creatures.Skulls_t.SKULL_BLACK)
        val state = newState(player, manaSpent = 0)

        state.death(null)

        assertThat(player.health).isEqualTo(40)
        assertThat(player.mana).isZero()
    }

    @Test
    fun `death applies the unfair fight reduction from the damage map`() {
        // player.cpp:4044-4076: two level-100 dealers (200 total levels) against
        // a level-30 player -> floor(0.15 * 100 + 0.5) = 15, clamped to 20, so
        // deathLossPercent = 0.0758 * 0.2 and expLoss = ceil(400000 * that) = 6065.
        val player = newPlayer()
        val dealerA = newPlayer(level = 100, experience = 0)
        val dealerB = newPlayer(level = 100, experience = 0)
        sink.damageMap = mapOf(
            2L to CountBlock(total = 100, ticks = now),
            3L to CountBlock(total = 100, ticks = now),
        )
        sink.playersById = mapOf(2L to dealerA, 3L to dealerB)
        val state = newState(player, manaSpent = 0)

        state.death(dealerA)

        assertThat(sink.reLoginWindows).containsExactly(20)
        assertThat(player.experience).isEqualTo(393_935)
        assertThat(sink.messages.map { it.second }).contains("You died during PvP.")
    }

    @Test
    fun `death ignores damage entries older than the five minute window`() {
        // player.cpp:4049-4051: (OTSYS_TIME() - ticks) <= 5 * 60 * 1000.
        val player = newPlayer()
        val dealer = newPlayer(level = 100, experience = 0)
        sink.damageMap = mapOf(
            2L to CountBlock(total = 500, ticks = now - 300_001),
            3L to CountBlock(total = 5, ticks = now - 300_000),
        )
        sink.playersById = mapOf(2L to dealer, 3L to dealer)
        val state = newState(player, manaSpent = 0)

        state.death(null)

        // Only the fresh entry counts; dealer 3 is a player, so the fight is PvP
        // with a single level-100 dealer -> floor(30/100*100+0.5) = 30.
        assertThat(sink.reLoginWindows).containsExactly(30)
        assertThat(sink.messages.map { it.second }).contains("You died during PvP.")
    }

    @Test
    fun `damage from a non-player id counts as others damage`() {
        // player.cpp:4053-4058: getPlayerByID miss -> othersDmg.
        val player = newPlayer()
        sink.damageMap = mapOf(
            99L to CountBlock(total = 400_000, ticks = now),
        )
        val state = newState(player, manaSpent = 0)

        state.death(null)

        // othersDmg only, no last-hit player: playerDmg 0 -> ratio 0 -> PvE.
        assertThat(sink.messages.map { it.second }).contains("You died during PvE.")
        assertThat(sink.reLoginWindows).containsExactly(100)
    }

    @Test
    fun `a player-damage share below five percent stays PvE`() {
        // player.cpp:4061-4063: playerDmg / (playerDmg + othersDmg) >= 0.05.
        val player = newPlayer()
        val dealer = newPlayer(level = 100, experience = 0)
        sink.damageMap = mapOf(
            2L to CountBlock(total = 1, ticks = now),
            3L to CountBlock(total = 100, ticks = now),
        )
        sink.playersById = mapOf(2L to dealer)
        val state = newState(player, manaSpent = 0)

        state.death(null)

        assertThat(sink.messages.map { it.second }).contains("You died during PvE.")
    }

    @Test
    fun `the charm bless halves the loss for the matching monster race`() {
        // player.cpp:4084-4096: percentReduction = chance[tier] / 100.
        val player = newPlayer()
        val rat = Monster(id = 7, name = "Rat", maxHealth = 20, health = 20, position = Position(101, 100, 7))
        val monsterType = MonsterType("Rat").apply { info.raceid = 44 }
        player.features.bestiary.parseRacebyCharm(CharmRune.CHARM_BLESS, true, 44)
        sink.charm = Charm(chance = listOf(50.0))
        sink.monsterType = monsterType
        val state = newState(player, manaSpent = 0)

        state.death(rat)

        // 0.0758 - 0.0758 * 0.5 = 0.0379; ceil(400000 * 0.0379...) = 15161.
        assertThat(player.experience).isEqualTo(384_839)
    }

    @Test
    fun `the charm bless does not apply for a different race`() {
        // player.cpp:4089-4090: mType->info.raceid == charmBlessRaceId.
        val player = newPlayer()
        val rat = Monster(id = 7, name = "Rat", maxHealth = 20, health = 20, position = Position(101, 100, 7))
        val monsterType = MonsterType("Cave Rat").apply { info.raceid = 45 }
        player.features.bestiary.parseRacebyCharm(CharmRune.CHARM_BLESS, true, 44)
        sink.charm = Charm(chance = listOf(50.0))
        sink.monsterType = monsterType
        val state = newState(player, manaSpent = 0)

        state.death(rat)

        assertThat(player.experience).isEqualTo(369_679)
    }

    @Test
    fun `death removes persistent conditions and despawns`() {
        // player.cpp:4236-4250: isPersistent() && isRemovableOnDeath().
        val player = newPlayer()
        val inFight = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_INFIGHT,
            60_000,
            now = { now },
        )!!
        val cooldown = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_SPELLCOOLDOWN,
            60_000,
            now = { now },
        )!!
        val nonPersistent = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_DRUNK,
            -1,
            now = { now },
        )!!
        player.addCondition(inFight)
        player.addCondition(cooldown)
        player.addCondition(nonPersistent)
        val state = newState(player, manaSpent = 0)

        state.death(null)

        assertThat(player.conditions).containsExactly(cooldown, nonPersistent)
        assertThat(sink.despawns).isEqualTo(1)
    }

    @Test
    fun `death removes only twist of fate for an unskulled pvp death`() {
        // player.cpp:4209-4213: !hasSkull && pvpDeath && hasBlessing(1) removes TOF.
        val player = newPlayer()
        player.addBlessing(1, 1)
        player.addBlessing(2, 1)
        val dealer = newPlayer(level = 100, experience = 0)
        sink.damageMap = mapOf(2L to CountBlock(total = 500, ticks = now))
        sink.playersById = mapOf(2L to dealer)
        val state = newState(player, manaSpent = 0)

        state.death(dealer)

        assertThat(player.hasBlessing(1)).isFalse()
        assertThat(player.hasBlessing(2)).isTrue()
        assertThat(sink.removedItemTypes).isEmpty()
    }

    @Test
    fun `death removes blessings 2 to 8 for a skulled pvp death`() {
        // player.cpp:4210-4216: a skull diverts to the else branch.
        val player = newPlayer()
        player.setSkull(org.opentibia.canary.creatures.Skulls_t.SKULL_RED)
        player.addBlessing(1, 1)
        player.addBlessing(2, 1)
        val dealer = newPlayer(level = 100, experience = 0)
        sink.damageMap = mapOf(2L to CountBlock(total = 500, ticks = now))
        sink.playersById = mapOf(2L to dealer)
        val state = newState(player, manaSpent = 0)

        state.death(dealer)

        assertThat(player.hasBlessing(1)).isTrue()
        assertThat(player.hasBlessing(2)).isFalse()
    }

    @Test
    fun `death removes blessings 2 to 8 and the amulet of loss otherwise`() {
        // player.cpp:4210-4218: the else branch clears 2..8 and the AOL.
        val player = newPlayer()
        player.addBlessing(1, 1)
        player.addBlessing(3, 1)
        player.addItem(Item(3057), Slots_t.CONST_SLOT_NECKLACE)
        val state = newState(player, manaSpent = 0)

        state.death(null)

        assertThat(player.hasBlessing(1)).isTrue()
        assertThat(player.hasBlessing(3)).isFalse()
        assertThat(sink.removedItemTypes).containsExactly(Triple(3057, 1, -1))
        // `getBlessingsName` runs before the removal, so both held blessings show.
        assertThat(sink.messages.map { it.second })
            .contains("You were blessed with Twist Of Fate, and The Spark Of The Phoenix.")
    }

    @Test
    fun `death keeps the adventurer's blessing message below the config level`() {
        // player.cpp:4180-4185: willNotLoseBless short-circuits the removal block.
        val player = newPlayer(level = 20)
        player.addBlessing(1, 1)
        val state = newState(player, manaSpent = 0)

        state.death(null)

        assertThat(player.hasBlessing(1)).isTrue()
        assertThat(sink.messages.map { it.second })
            .contains("You still have adventurer's blessings for being level lower than 21!")
    }

    @Test
    fun `death reports the downgrade and adjusts the gain values`() {
        // player.cpp:4144-4168: the level-down loop subtracts the gains.
        val player = newPlayer(level = 8, experience = 4_500)
        val state = newState(player, manaSpent = 0)

        state.death(null)

        // level < 24 -> loss 10%; expLoss = ceil(4500 * 0.1) = 450 -> 4050,
        // below getExpForLevel(8)=4200 -> level 7, gains subtracted once.
        assertThat(player.level).isEqualTo(7)
        assertThat(player.experience).isEqualTo(4_050)
        assertThat(player.maxHealth).isEqualTo(485)
        assertThat(player.maxMana).isEqualTo(295)
        assertThat(player.capacity).isEqualTo(37_500.0)
        assertThat(sink.levelPercent).isEqualTo(90)
        assertThat(sink.messages.map { it.second }).contains("You were downgraded from Level 8 to Level 7.")
    }

    @Test
    fun `death advances the login position and plays the sexed death sound`() {
        // player.cpp:4036-4042.
        val player = newPlayer()
        player.setSex(PlayerSex_t.MALE)
        val state = newState(player, manaSpent = 0)

        state.death(null)

        assertThat(sink.loginPosition).isEqualTo(Position(50, 60, 7))
        assertThat(sink.soundEffects.single().first).isEqualTo(Position(100, 100, 7))
        assertThat(sink.soundEffects.single().second).isEqualTo(SoundEffect.HUMAN_MALE_DEATH)
        assertThat(sink.soundEffects.single().third).isSameAs(player)
    }

    @Test
    fun `death dismounts unless the mount is allowed in protection zones`() {
        // player.cpp:4036-4039.
        sink.isMounted = true
        val mounted = newPlayer()
        newState(mounted, manaSpent = 0).death(null)

        assertThat(sink.dismounts).isEqualTo(1)
        assertThat(sink.outfitChanges).hasSize(1)

        sink.dismounts = 0
        sink.outfitChanges.clear()
        val protectedPlayer = newPlayer()
        newState(protectedPlayer, manaSpent = 0, toggleMountInPz = true).death(null)

        assertThat(sink.dismounts).isZero()
        assertThat(sink.outfitChanges).isEmpty()
    }

    @Test
    fun `monk death empties the harmony charges`() {
        // player.cpp:4282-4284.
        val player = newPlayer(vocationId = 9)
        player.setHarmony(4)
        val state = newState(player, manaSpent = 0)

        state.death(null)

        assertThat(player.getHarmony()).isZero()
    }

    // --- no-skill-loss death (player.cpp:4253-4281) --------------------------------

    @Test
    fun `death without skill loss respawns at the temple and keeps the loot state`() {
        // player.cpp:4253-4279: conditions, health, teleport, onThink/onIdleStatus.
        sink.skillLoss = false
        val player = newPlayer()
        val inFight = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_INFIGHT,
            60_000,
            now = { now },
        )!!
        val nonPersistent = Condition.createCondition(
            ConditionId.CONDITIONID_DEFAULT,
            ConditionType.CONDITION_DRUNK,
            -1,
            now = { now },
        )!!
        player.addCondition(inFight)
        player.addCondition(nonPersistent)
        val state = newState(player)

        state.death(null)

        assertThat(sink.setSkillLossCalls).containsExactly(true)
        assertThat(player.conditions).containsExactly(nonPersistent)
        assertThat(player.health).isEqualTo(player.maxHealth)
        assertThat(sink.teleports).containsExactly(Triple(player, Position(50, 60, 7), true))
        assertThat(sink.creatureHealth).isEqualTo(1)
        assertThat(sink.playerMana).isEqualTo(1)
        assertThat(sink.statsSends).hasSize(1)
        assertThat(sink.despawns).isZero()
        assertThat(player.experience).isEqualTo(400_000)
        assertThat(sink.messages).isEmpty()
    }

    // --- Player::getSpentMana (player.cpp:7849-7851) --------------------------------

    @Test
    fun `getSpentMana reads the mana spent accumulator`() {
        val player = newPlayer()
        val state = newState(player, manaSpent = 4_242)

        assertThat(state.getSpentMana()).isEqualTo(4_242)
    }

    private fun realXml(): String {
        val candidates = listOf(
            Path.of("data/XML/vocations.xml"),
            Path.of("../data/XML/vocations.xml"),
            Path.of("../../data/XML/vocations.xml"),
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("data/XML/vocations.xml not found from ${System.getProperty("user.dir")}")
        return Files.readString(path)
    }
}
