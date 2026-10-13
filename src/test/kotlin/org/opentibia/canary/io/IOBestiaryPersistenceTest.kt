package org.opentibia.canary.io

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.database.JdbcFeatureStateRepository
import org.opentibia.canary.database.PlayerFeatureState
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.PlayerBestiaryState

/**
 * H2 (Flyway V1-V13) round-trip tests for [IOBestiaryPersistence], transcribed
 * from Canary's bestiary persistence:
 *
 *  - `IOLoginDataLoad::loadPlayerBestiaryCharms`
 *    (src/io/functions/iologindata_load_player.cpp:472-525): charm points,
 *    echoes, rune bits, expansion, the `charms` blob (raceId + tier per rune)
 *    and the `tracker list` blob, with the default-row INSERT when the player
 *    has no row yet.
 *  - `IOLoginDataSave::savePlayerBestiarySystem`
 *    (src/io/functions/iologindata_save_player.cpp:478-532): the same columns
 *    plus the tracker list.
 *  - Kill counters, which Canary stores through
 *    `STORAGEVALUE_BESTIARYKILLCOUNT` (`Player::addBestiaryKillCount`,
 *    src/creatures/players/player.cpp:12969-12979); the Kotlin port keeps them
 *    in `player_bestiary_kill_counts` (V3) via [JdbcFeatureStateRepository].
 *  - Animus mastery, serialized into `players.animus_mastery`
 *    (`AnimusMastery::serialize`,
 *    src/creatures/players/animus_mastery/animus_mastery.cpp:62-72).
 */
class IOBestiaryPersistenceTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var persistence: IOBestiaryPersistence

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_io_bestiary_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        persistence = IOBestiaryPersistence(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips bestiary kill counts`() {
        persistence.saveBestiaryKillCounts(1, mapOf(56 to 10000, 698 to 25, 2346 to 1))

        assertThat(persistence.loadBestiaryKillCounts(1))
            .containsExactlyInAnyOrderEntriesOf(mapOf(56 to 10000, 698 to 25, 2346 to 1))
    }

    @Test
    fun `loadBestiaryKillCounts returns an empty map without stored counters`() {
        assertThat(persistence.loadBestiaryKillCounts(2)).isEmpty()
    }

    @Test
    fun `saveBestiaryKillCounts replaces the stored counters`() {
        persistence.saveBestiaryKillCounts(1, mapOf(56 to 10000, 698 to 25))

        persistence.saveBestiaryKillCounts(1, mapOf(1003 to 5))

        assertThat(persistence.loadBestiaryKillCounts(1)).containsExactlyInAnyOrderEntriesOf(mapOf(1003 to 5))
    }

    @Test
    fun `savePlayerBestiarySystem persists the kill counters already in the player`() {
        val player = PlayerBestiaryState()
        player.addBestiaryKillCount(56, 42)
        player.addBestiaryKillCount(2346, 1)

        persistence.savePlayerBestiarySystem(1, player)

        assertThat(persistence.loadBestiaryKillCounts(1))
            .containsExactlyInAnyOrderEntriesOf(mapOf(56 to 42, 2346 to 1))
    }

    @Test
    fun `savePlayerBestiarySystem preserves unrelated feature state`() {
        val repository = JdbcFeatureStateRepository(dataSource)
        repository.save(PlayerFeatureState(playerId = 1, wheelPoints = mapOf(1 to 25), currentMount = 7))

        persistence.savePlayerBestiarySystem(1, PlayerBestiaryState(), tracker = listOf(56))

        val state = repository.load(1)
        assertThat(state).isNotNull
        assertThat(state?.wheelPoints).containsExactlyEntriesOf(mapOf(1 to 25))
        assertThat(state?.currentMount).isEqualTo(7)
        assertThat(state?.bestiaryKillCounts).isEmpty()
        assertThat(persistence.loadBestiaryTracker(1)).containsExactly(56)
    }

    @Test
    fun `round-trips the charm scalar state and rune assignments`() {
        val player = PlayerBestiaryState()
        player.setCharmPoints(120)
        player.setMinorCharmEchoes(30)
        player.setMaxCharmPoints(400)
        player.setMaxMinorCharmEchoes(100)
        player.hasCharmExpansion = true
        player.setUsedRunesBit(0b101)
        player.setUnlockedRunesBit(0b111)
        player.parseRacebyCharm(CharmRune.CHARM_WOUND, set = true, newRaceid = 56)
        player.setCharmTier(CharmRune.CHARM_WOUND, 2)
        player.parseRacebyCharm(CharmRune.CHARM_ZAP, set = true, newRaceid = 698)
        player.setCharmTier(CharmRune.CHARM_ZAP, 1)

        persistence.savePlayerBestiarySystem(1, player)

        val loaded = PlayerBestiaryState()
        persistence.loadPlayerBestiaryCharms(1, loaded)

        assertThat(loaded.getCharmPoints()).isEqualTo(120)
        assertThat(loaded.getMinorCharmEchoes()).isEqualTo(30)
        assertThat(loaded.getMaxCharmPoints()).isEqualTo(400)
        assertThat(loaded.getMaxMinorCharmEchoes()).isEqualTo(100)
        assertThat(loaded.hasCharmExpansion).isTrue()
        assertThat(loaded.getUsedRunesBit()).isEqualTo(0b101)
        assertThat(loaded.getUnlockedRunesBit()).isEqualTo(0b111)
        assertThat(loaded.parseRacebyCharm(CharmRune.CHARM_WOUND)).isEqualTo(56)
        assertThat(loaded.getCharmTier(CharmRune.CHARM_WOUND)).isEqualTo(2)
        assertThat(loaded.parseRacebyCharm(CharmRune.CHARM_ZAP)).isEqualTo(698)
        assertThat(loaded.getCharmTier(CharmRune.CHARM_ZAP)).isEqualTo(1)
        assertThat(loaded.parseRacebyCharm(CharmRune.CHARM_FREEZE)).isZero()
        assertThat(loaded.getCharmTier(CharmRune.CHARM_FREEZE)).isZero()
    }

    @Test
    fun `loadPlayerBestiaryCharms creates the canary default row when missing`() {
        val repository = JdbcFeatureStateRepository(dataSource)
        assertThat(repository.load(2)).isNull()

        val tracker = persistence.loadPlayerBestiaryCharms(2, PlayerBestiaryState())

        assertThat(tracker).isEmpty()
        val state = repository.load(2)
        assertThat(state).isNotNull
        assertThat(state?.charmPoints).isZero()
        assertThat(state?.bestiaryKillCounts).isEmpty()
    }

    @Test
    fun `round-trips the bestiary tracker list in order`() {
        persistence.saveBestiaryTracker(1, listOf(1003, 698, 56))

        assertThat(persistence.loadBestiaryTracker(1)).containsExactly(1003, 698, 56)
    }

    @Test
    fun `savePlayerBestiarySystem persists the tracker list`() {
        persistence.savePlayerBestiarySystem(1, PlayerBestiaryState(), tracker = listOf(56, 1003))

        assertThat(persistence.loadBestiaryTracker(1)).containsExactly(56, 1003)
    }

    @Test
    fun `saveBestiaryTracker replaces the stored tracker rows`() {
        persistence.saveBestiaryTracker(1, listOf(1003, 698))

        persistence.saveBestiaryTracker(1, listOf(56))

        assertThat(persistence.loadBestiaryTracker(1)).containsExactly(56)
    }

    @Test
    fun `round-trips the animus mastery list in order`() {
        persistence.saveAnimusMastery(1, listOf("demon", "dragon lord", "rat"))

        assertThat(persistence.loadAnimusMastery(1)).containsExactly("demon", "dragon lord", "rat")
    }

    @Test
    fun `loadAnimusMastery returns an empty list without stored masteries`() {
        assertThat(persistence.loadAnimusMastery(2)).isEmpty()
    }

    @Test
    fun `saveAnimusMastery replaces the stored masteries`() {
        persistence.saveAnimusMastery(1, listOf("demon", "dragon lord"))

        persistence.saveAnimusMastery(1, listOf("rat"))

        assertThat(persistence.loadAnimusMastery(1)).containsExactly("rat")
    }
}
