package org.opentibia.canary.systems.bestiary

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.protocol.bestiary.BestiaryMonsterDataCommand
import org.opentibia.canary.network.protocol.bestiary.BestiarySendRacesCommand
import org.opentibia.canary.network.protocol.bestiary.BuyCharmRuneCommand
import org.opentibia.canary.network.protocol.bestiary.SearchRaceIdsCommand
import org.opentibia.canary.network.protocol.bestiary.SearchRaceByNameCommand

/**
 * Application-service parity tests for the bestiary/charm client commands.
 *
 * Transcribed from:
 *  - `ProtocolGame::parseBestiarySendRaces` (src/server/network/protocol/protocolgame.cpp:3472-3502)
 *  - `ProtocolGame::parseBestiarySendCreatures` (protocolgame.cpp:4164-4239)
 *  - `ProtocolGame::parseBestiarysendMonsterData` (protocolgame.cpp:3515-3632)
 *  - `IOBestiary::sendBuyCharmRune` (src/io/iobestiary.cpp:459-574)
 *
 * Monster fixtures reproduce `data-otservbr-global/monster` values (rat.lua,
 * wolf.lua, dragon.lua, demon.lua, troll_guard.lua). Charm point tables
 * reproduce `data/scripts/systems/bestiary_charms.lua` (which stays Lua); only
 * the already-decoded numbers are transcribed here.
 */
class BestiaryApplicationTest {

    private val rat = MonsterBestiaryInfo(
        raceid = 21,
        name = "Rat",
        bestiaryFirstUnlock = 10,
        bestiarySecondUnlock = 100,
        bestiaryToUnlock = 250,
        bestiaryCharmsPoints = 5,
        bestiaryStars = 1,
        bestiaryClass = "Mammal",
        bestiaryRace = BestiaryRace.BESTY_RACE_MAMMAL,
        bestiaryOccurrence = 0,
    )

    private val wolf = MonsterBestiaryInfo(
        raceid = 27,
        name = "Wolf",
        bestiaryFirstUnlock = 10,
        bestiarySecondUnlock = 100,
        bestiaryToUnlock = 250,
        bestiaryCharmsPoints = 5,
        bestiaryStars = 1,
        bestiaryClass = "Mammal",
        bestiaryRace = BestiaryRace.BESTY_RACE_MAMMAL,
        bestiaryOccurrence = 0,
    )

    private val dragon = MonsterBestiaryInfo(
        raceid = 34,
        name = "Dragon",
        bestiaryFirstUnlock = 50,
        bestiarySecondUnlock = 500,
        bestiaryToUnlock = 1000,
        bestiaryCharmsPoints = 25,
        bestiaryStars = 3,
        bestiaryClass = "Dragon",
        bestiaryRace = BestiaryRace.BESTY_RACE_DRAGON,
        bestiaryOccurrence = 0,
    )

    private val demon = MonsterBestiaryInfo(
        raceid = 35,
        name = "Demon",
        bestiaryFirstUnlock = 100,
        bestiarySecondUnlock = 1000,
        bestiaryToUnlock = 2500,
        bestiaryCharmsPoints = 50,
        bestiaryStars = 4,
        bestiaryClass = "Demon",
        bestiaryRace = BestiaryRace.BESTY_RACE_DEMON,
        bestiaryOccurrence = 0,
    )

    private val trollGuard = MonsterBestiaryInfo(
        raceid = 745,
        name = "Troll Guard",
        bestiaryFirstUnlock = 1,
        bestiarySecondUnlock = 3,
        bestiaryToUnlock = 5,
        bestiaryCharmsPoints = 30,
        bestiaryStars = 2,
        bestiaryClass = "Humanoid",
        bestiaryRace = BestiaryRace.BESTY_RACE_HUMANOID,
        bestiaryOccurrence = 3,
    )

    private val wound = Charm(
        name = "Wound",
        id = CharmRune.CHARM_WOUND,
        category = CharmCategory.CHARM_MAJOR,
        type = CharmType.CHARM_OFFENSIVE,
        points = listOf(240, 360, 1200),
        binary = 1 shl CharmRune.CHARM_WOUND.value,
    )

    private val lowBlow = Charm(
        name = "Low Blow",
        id = CharmRune.CHARM_LOW,
        category = CharmCategory.CHARM_MAJOR,
        type = CharmType.CHARM_OFFENSIVE,
        points = listOf(800, 1200, 4000),
        binary = 1 shl CharmRune.CHARM_LOW.value,
    )

    private val cripple = Charm(
        name = "Cripple",
        id = CharmRune.CHARM_CRIPPLE,
        category = CharmCategory.CHARM_MINOR,
        type = CharmType.CHARM_OFFENSIVE,
        points = listOf(100, 150, 225),
        binary = 1 shl CharmRune.CHARM_CRIPPLE.value,
    )

    private val numb = Charm(
        name = "Numb",
        id = CharmRune.CHARM_NUMB,
        category = CharmCategory.CHARM_MINOR,
        type = CharmType.CHARM_DEFENSIVE,
        points = listOf(100, 150, 225),
        binary = 1 shl CharmRune.CHARM_NUMB.value,
    )

    private val registry = BestiaryRegistry(listOf(rat, wolf, dragon, demon, trollGuard))

    private val application = BestiaryApplication(
        registry = registry,
        bestiary = IOBestiary(mutableListOf(wound, lowBlow, cripple, numb)),
    )

    // ---------------------------------------------------------------- races

    @Test
    fun `send races mirrors the 21 race loop over class, count and unlocked count`() {
        val state = PlayerBestiaryState()
        state.addBestiaryKillCount(21, 12)

        val races = application.parseBestiarySendRaces(state)

        assertThat(races).isNotNull
        assertThat(races!!).hasSize(BestiaryRace.BESTY_RACE_INKBORN.value)
        val mammal = races.single { it.race == BestiaryRace.BESTY_RACE_MAMMAL }
        assertThat(mammal.bestiaryClass).isEqualTo("Mammal")
        assertThat(mammal.monsterCount).isEqualTo(2)
        assertThat(mammal.unlockedCount).isEqualTo(1)
        val inkborn = races.single { it.race == BestiaryRace.BESTY_RACE_INKBORN }
        assertThat(inkborn.bestiaryClass).isEmpty()
        assertThat(inkborn.monsterCount).isEqualTo(0)
        assertThat(inkborn.unlockedCount).isEqualTo(0)
    }

    @Test
    fun `send races aborts when a listed monster cannot be resolved`() {
        val broken = BestiaryRegistry(listOf(rat))
        broken.addBestiaryList(999, "Ghost")
        val application = BestiaryApplication(broken, IOBestiary())

        assertThat(application.parseBestiarySendRaces(PlayerBestiaryState())).isNull()
    }

    // ------------------------------------------------------------ creatures

    @Test
    fun `send creatures by ids keeps only killed races and resolves progress and occurrence`() {
        val state = PlayerBestiaryState()
        state.addBestiaryKillCount(21, 12)
        state.addBestiaryKillCount(745, 3)
        state.addBestiaryKillCount(35, 0)
        state.addBestiaryKillCount(999, 5)

        val creatures = application.parseBestiarySendCreatures(
            state,
            SearchRaceIdsCommand(listOf(34, 745, 21, 21, 35, 999)),
        )

        assertThat(creatures).isNotNull
        assertThat(creatures!!.searchText).isEmpty()
        assertThat(creatures.creatures).containsExactly(
            BestiaryCreature(21, "Rat", 12, 2, 0),
            BestiaryCreature(745, "Troll Guard", 3, 3, 3),
        )
    }

    @Test
    fun `send creatures by name resolves the class and leaves unkilled monsters at level zero`() {
        val state = PlayerBestiaryState()
        state.addBestiaryKillCount(21, 12)

        val creatures = application.parseBestiarySendCreatures(state, SearchRaceByNameCommand("Mammal"))

        assertThat(creatures).isNotNull
        assertThat(creatures!!.searchText).isEqualTo("Mammal")
        assertThat(creatures.creatures).containsExactly(
            BestiaryCreature(21, "Rat", 12, 2, 0),
            BestiaryCreature(27, "Wolf", 0, 0, 0),
        )
    }

    @Test
    fun `send creatures by an unknown class name is dropped`() {
        assertThat(
            application.parseBestiarySendCreatures(PlayerBestiaryState(), SearchRaceByNameCommand("NoSuchClass")),
        ).isNull()
    }

    @Test
    fun `send creatures aborts when a killed race name cannot be resolved`() {
        val broken = BestiaryRegistry(listOf(rat))
        broken.addBestiaryList(999, "Ghost")
        val application = BestiaryApplication(broken, IOBestiary())
        val state = PlayerBestiaryState()
        state.addBestiaryKillCount(999, 1)

        assertThat(application.parseBestiarySendCreatures(state, SearchRaceIdsCommand(listOf(999)))).isNull()
    }

    // ---------------------------------------------------------- monster data

    @Test
    fun `send monster data resolves the class and current kill status`() {
        val state = PlayerBestiaryState()
        state.addBestiaryKillCount(34, 500)

        val data = application.parseBestiarysendMonsterData(state, BestiaryMonsterDataCommand(34))

        assertThat(data).isNotNull
        assertThat(data!!.raceId).isEqualTo(34)
        assertThat(data.monster).isSameAs(dragon)
        assertThat(data.bestiaryClass).isEqualTo("Dragon")
        assertThat(data.killCount).isEqualTo(500)
        assertThat(data.currentLevel).isEqualTo(3)
    }

    @Test
    fun `send monster data for an unknown race is dropped`() {
        assertThat(
            application.parseBestiarysendMonsterData(PlayerBestiaryState(), BestiaryMonsterDataCommand(65000)),
        ).isNull()
    }

    // ---------------------------------------------------------- buy charm 0

    @Test
    fun `buy charm major tier zero costs 240 points and grants 50 minor echoes`() {
        val state = PlayerBestiaryState()
        state.setCharmPoints(240)
        state.setMaxCharmPoints(240)

        application.sendBuyCharmRune(state, 0, CharmRune.CHARM_WOUND.value, 0)

        assertThat(state.getCharmPoints()).isEqualTo(0)
        assertThat(state.getMaxCharmPoints()).isEqualTo(240)
        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(1)
        assertThat(state.getMinorCharmEchoes()).isEqualTo(50)
        assertThat(state.getMaxMinorCharmEchoes()).isEqualTo(50)
        assertThat(state.getUnlockedRunesBit()).isEqualTo(1 shl CharmRune.CHARM_WOUND.value)
        assertThat(state.fyiBoxMessages).isEmpty()
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    @Test
    fun `buy charm major upgrades use the next tier cost and echo formula`() {
        val state = PlayerBestiaryState()
        state.setCharmTier(CharmRune.CHARM_WOUND, 1)
        state.setCharmPoints(360)

        application.sendBuyCharmRune(state, 0, CharmRune.CHARM_WOUND.value, 0)

        assertThat(state.getCharmPoints()).isEqualTo(0)
        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(2)
        assertThat(state.getMinorCharmEchoes()).isEqualTo(100)

        state.setCharmPoints(1200)
        application.sendBuyCharmRune(state, 0, CharmRune.CHARM_WOUND.value, 0)

        assertThat(state.getCharmPoints()).isEqualTo(0)
        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(3)
        assertThat(state.getMinorCharmEchoes()).isEqualTo(300)
    }

    @Test
    fun `buy charm rejects a max level rune`() {
        val state = PlayerBestiaryState()
        state.setCharmTier(CharmRune.CHARM_WOUND, 3)
        state.setCharmPoints(9999)

        application.sendBuyCharmRune(state, 0, CharmRune.CHARM_WOUND.value, 0)

        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(3)
        assertThat(state.getCharmPoints()).isEqualTo(9999)
        assertThat(state.fyiBoxMessages).containsExactly("Charm at max level.")
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    @Test
    fun `buy charm rejects when charm points are insufficient`() {
        val state = PlayerBestiaryState()
        state.setCharmPoints(239)

        application.sendBuyCharmRune(state, 0, CharmRune.CHARM_WOUND.value, 0)

        assertThat(state.getCharmPoints()).isEqualTo(239)
        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(0)
        assertThat(state.fyiBoxMessages).containsExactly("You don't have enough charm points to unlock this rune.")
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    @Test
    fun `buy charm minor consumes echoes and does not award any`() {
        val state = PlayerBestiaryState()
        state.setMinorCharmEchoes(100)
        state.setMaxMinorCharmEchoes(100)

        application.sendBuyCharmRune(state, 0, CharmRune.CHARM_CRIPPLE.value, 0)

        assertThat(state.getMinorCharmEchoes()).isEqualTo(0)
        assertThat(state.getMaxMinorCharmEchoes()).isEqualTo(100)
        assertThat(state.getCharmTier(CharmRune.CHARM_CRIPPLE)).isEqualTo(1)
        assertThat(state.getUnlockedRunesBit()).isEqualTo(1 shl CharmRune.CHARM_CRIPPLE.value)
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    @Test
    fun `buy charm minor rejects when minor echoes are insufficient`() {
        val state = PlayerBestiaryState()
        state.setMinorCharmEchoes(99)

        application.sendBuyCharmRune(state, 0, CharmRune.CHARM_CRIPPLE.value, 0)

        assertThat(state.getMinorCharmEchoes()).isEqualTo(99)
        assertThat(state.getCharmTier(CharmRune.CHARM_CRIPPLE)).isEqualTo(0)
        assertThat(state.fyiBoxMessages)
            .containsExactly("You don't have enough minor charm echoes to unlock this rune.")
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    @Test
    fun `buy charm with an unknown rune id is ignored`() {
        val state = PlayerBestiaryState()
        state.setCharmPoints(10000)

        application.sendBuyCharmRune(state, 0, 99, 0)
        application.sendBuyCharmRune(state, 0, CharmRune.CHARM_NONE.value, 0)

        assertThat(state.getCharmPoints()).isEqualTo(10000)
        assertThat(state.fyiBoxMessages).isEmpty()
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    // ---------------------------------------------------------- buy charm 1

    @Test
    fun `buy charm set assigns a finished major rune and warns free players`() {
        val state = PlayerBestiaryState()
        state.addBestiaryKillCount(34, 1000)

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(34)
        assertThat(state.getUsedRunesBit()).isEqualTo(1 shl CharmRune.CHARM_WOUND.value)
        assertThat(state.fyiBoxMessages).containsExactly(
            "Creature has been set! You are a Free player, so you benefit from up to 2 runes! " +
                "Premium players benefit from 6 and Charm Expansion allow you to set creatures to all runes at once!.",
        )
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    @Test
    fun `buy charm set refuses a major rune before the monster is finished`() {
        val state = PlayerBestiaryState()
        state.addBestiaryKillCount(34, 999)

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(0)
        assertThat(state.getUsedRunesBit()).isEqualTo(0)
        assertThat(state.fyiBoxMessages).isEmpty()
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    @Test
    fun `buy charm set on an unknown race skips the kill check and assigns`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.hasCharmExpansion = true

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 65000)

        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(65000)
        assertThat(state.getUsedRunesBit()).isEqualTo(1 shl CharmRune.CHARM_WOUND.value)
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    @Test
    fun `buy charm set reports the premium slot allowance`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.addBestiaryKillCount(34, 1000)

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.fyiBoxMessages).containsExactly(
            "Creature has been set! You are a Premium player, so you benefit from up to 6 runes! " +
                "Charm Expansion allow you to set creatures to all runes at once!",
        )
    }

    @Test
    fun `buy charm set is silent for premium players with the charm expansion`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.hasCharmExpansion = true
        state.addBestiaryKillCount(34, 1000)

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.fyiBoxMessages).isEmpty()
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    @Test
    fun `buy charm set enforces the free two slot limit`() {
        val state = PlayerBestiaryState()
        state.setUsedRunesBit(0b11)

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.fyiBoxMessages).containsExactly("You don't have any charm slots available.")
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(0)
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    @Test
    fun `buy charm set enforces the premium six slot limit`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.setUsedRunesBit(0b111111)

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.fyiBoxMessages).containsExactly("You don't have any charm slots available.")
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    @Test
    fun `buy charm set enforces the charm expansion twenty five slot limit`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.hasCharmExpansion = true
        state.setUsedRunesBit((1 shl 25) - 1)

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.fyiBoxMessages).containsExactly("You don't have any charm slots available.")
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    @Test
    fun `buy charm set refuses a creature already assigned to another major rune`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.hasCharmExpansion = true
        state.addBestiaryKillCount(34, 1000)
        state.addBestiaryKillCount(35, 2500)

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)
        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_LOW.value, 35)
        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_LOW.value, 34)

        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(34)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_LOW)).isEqualTo(35)
        assertThat(state.fyiBoxMessages)
            .containsExactly("You already have this monster set on another Major Charm!")
        assertThat(state.bestiaryCharmsSent).isEqualTo(2)
    }

    @Test
    fun `buy charm set refuses a creature already assigned to another minor rune`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.hasCharmExpansion = true

        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_CRIPPLE.value, 21)
        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_NUMB.value, 21)

        assertThat(state.parseRacebyCharm(CharmRune.CHARM_CRIPPLE)).isEqualTo(21)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_NUMB)).isEqualTo(0)
        assertThat(state.fyiBoxMessages)
            .containsExactly("You already have this monster set on another Minor Charm!")
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    // ---------------------------------------------------------- buy charm 2

    @Test
    fun `buy charm remove charges level times one hundred and clears the rune`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.setLevel(50)
        state.setBankBalance(5000)
        state.addBestiaryKillCount(34, 1000)
        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)
        state.fyiBoxMessages.clear()

        application.sendBuyCharmRune(state, 2, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.getBankBalance()).isEqualTo(0)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(0)
        assertThat(state.getUsedRunesBit()).isEqualTo(0)
        assertThat(state.fyiBoxMessages).isEmpty()
        assertThat(state.bestiaryCharmsSent).isEqualTo(2)
    }

    @Test
    fun `buy charm remove applies the charm expansion discount`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.hasCharmExpansion = true
        state.setLevel(50)
        state.setBankBalance(3750)
        state.addBestiaryKillCount(34, 1000)
        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)

        application.sendBuyCharmRune(state, 2, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.getBankBalance()).isEqualTo(0)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(0)
    }

    @Test
    fun `buy charm remove refuses without enough gold and keeps the rune`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.hasCharmExpansion = true
        state.setLevel(50)
        state.setBankBalance(499)
        state.addBestiaryKillCount(34, 1000)
        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)
        state.fyiBoxMessages.clear()

        application.sendBuyCharmRune(state, 2, CharmRune.CHARM_WOUND.value, 34)

        assertThat(state.getBankBalance()).isEqualTo(499)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(34)
        assertThat(state.fyiBoxMessages).containsExactly("You don't have enough gold.")
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    @Test
    fun `buy charm remove with an unknown rune id is ignored`() {
        val state = PlayerBestiaryState()
        state.setBankBalance(100000)

        application.sendBuyCharmRune(state, 2, 99, 34)

        assertThat(state.getBankBalance()).isEqualTo(100000)
        assertThat(state.fyiBoxMessages).isEmpty()
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    // ---------------------------------------------------------- buy charm 3

    @Test
    fun `buy charm reset all costs 100000 up to level 100 and restores points and echoes`() {
        val state = PlayerBestiaryState()
        state.isPremium = true
        state.isPromoted = true
        state.setLevel(100)
        state.setBankBalance(100000)
        state.setCharmPoints(30)
        state.setMaxCharmPoints(750)
        state.setMinorCharmEchoes(20)
        state.setMaxMinorCharmEchoes(20)
        state.setUnlockedRunesBit(0b111)
        state.addBestiaryKillCount(34, 1000)
        state.addBestiaryKillCount(35, 2500)
        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_WOUND.value, 34)
        application.sendBuyCharmRune(state, 1, CharmRune.CHARM_LOW.value, 35)
        state.setCharmTier(CharmRune.CHARM_WOUND, 2)
        state.setCharmTier(CharmRune.CHARM_LOW, 1)
        state.fyiBoxMessages.clear()

        application.sendBuyCharmRune(state, 3, CharmRune.CHARM_NONE.value, 0)

        assertThat(state.getBankBalance()).isEqualTo(0)
        assertThat(state.getCharmPoints()).isEqualTo(750)
        assertThat(state.getMinorCharmEchoes()).isEqualTo(100)
        assertThat(state.getMaxMinorCharmEchoes()).isEqualTo(100)
        assertThat(state.getUsedRunesBit()).isEqualTo(0)
        assertThat(state.getUnlockedRunesBit()).isEqualTo(0)
        assertThat(state.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(0)
        assertThat(state.getCharmTier(CharmRune.CHARM_LOW)).isEqualTo(0)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(0)
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_LOW)).isEqualTo(0)
        assertThat(state.fyiBoxMessages).isEmpty()
        assertThat(state.bestiaryCharmsSent).isEqualTo(3)
    }

    @Test
    fun `buy charm reset all adds 11000 per level above 100`() {
        val state = PlayerBestiaryState()
        state.setLevel(101)
        state.setBankBalance(1_211_000)

        application.sendBuyCharmRune(state, 3, CharmRune.CHARM_NONE.value, 0)

        assertThat(state.getBankBalance()).isEqualTo(0)
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    @Test
    fun `buy charm reset all applies the charm expansion discount`() {
        val state = PlayerBestiaryState()
        state.hasCharmExpansion = true
        state.setLevel(101)
        state.setBankBalance(908_250)

        application.sendBuyCharmRune(state, 3, CharmRune.CHARM_NONE.value, 0)

        assertThat(state.getBankBalance()).isEqualTo(0)
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    @Test
    fun `buy charm reset all clears minor echoes to zero without promotion`() {
        val state = PlayerBestiaryState()
        state.setLevel(1)
        state.setBankBalance(100000)
        state.setMinorCharmEchoes(20)
        state.setMaxMinorCharmEchoes(20)

        application.sendBuyCharmRune(state, 3, CharmRune.CHARM_NONE.value, 0)

        assertThat(state.getMinorCharmEchoes()).isEqualTo(0)
        assertThat(state.getMaxMinorCharmEchoes()).isEqualTo(0)
    }

    @Test
    fun `buy charm reset all refuses without enough gold`() {
        val state = PlayerBestiaryState()
        state.setLevel(100)
        state.setBankBalance(99_999)
        state.setUnlockedRunesBit(0b111)

        application.sendBuyCharmRune(state, 3, CharmRune.CHARM_NONE.value, 0)

        assertThat(state.getBankBalance()).isEqualTo(99_999)
        assertThat(state.getUnlockedRunesBit()).isEqualTo(0b111)
        assertThat(state.fyiBoxMessages).containsExactly("You don't have enough gold.")
        assertThat(state.bestiaryCharmsSent).isEqualTo(0)
    }

    @Test
    fun `buy charm reset all runs with an invalid rune id`() {
        val state = PlayerBestiaryState()
        state.setLevel(1)
        state.setBankBalance(100000)

        application.sendBuyCharmRune(state, 3, 99, 0)

        assertThat(state.getBankBalance()).isEqualTo(0)
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    @Test
    fun `buy charm with an unknown action and a valid rune falls through to the charm panel`() {
        val state = PlayerBestiaryState()

        application.sendBuyCharmRune(state, 4, CharmRune.CHARM_WOUND.value, 0)

        assertThat(state.fyiBoxMessages).isEmpty()
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }

    // -------------------------------------------------------------- dispatch

    @Test
    fun `apply dispatches every parsed command to its handler`() {
        val state = PlayerBestiaryState()
        state.addBestiaryKillCount(34, 1000)

        val races = application.apply(state, BestiarySendRacesCommand)
        assertThat(races).isInstanceOf(BestiaryApplyResult.Races::class.java)
        assertThat((races as BestiaryApplyResult.Races).races).isNotNull

        val creatures = application.apply(state, SearchRaceIdsCommand(listOf(34)))
        assertThat(creatures).isEqualTo(
            BestiaryApplyResult.Creatures(
                BestiaryCreatures("", listOf(BestiaryCreature(34, "Dragon", 1000, 4, 0))),
            ),
        )

        val data = application.apply(state, BestiaryMonsterDataCommand(34))
        assertThat(data).isEqualTo(
            BestiaryApplyResult.MonsterData(
                BestiaryMonsterData(34, "Dragon", 1000, 4, dragon),
            ),
        )

        val charm = application.apply(state, BuyCharmRuneCommand(1, CharmRune.CHARM_WOUND.value, 34))
        assertThat(charm).isEqualTo(BestiaryApplyResult.CharmRune(1, CharmRune.CHARM_WOUND.value, 34))
        assertThat(state.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(34)
        assertThat(state.bestiaryCharmsSent).isEqualTo(1)
    }
}
