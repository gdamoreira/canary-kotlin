package org.opentibia.canary.io

import org.opentibia.canary.database.CharmAssignmentState
import org.opentibia.canary.database.FeatureStateRepository
import org.opentibia.canary.database.JdbcFeatureStateRepository
import org.opentibia.canary.database.PlayerFeatureState
import org.opentibia.canary.systems.bestiary.CharmRune
import org.opentibia.canary.systems.bestiary.PlayerBestiaryState
import javax.sql.DataSource

/**
 * Persistence half of Canary's bestiary state, split from the engine
 * ([org.opentibia.canary.systems.bestiary.IOBestiary]) the way Canary splits it
 * between `IOLoginDataLoad`/`IOLoginDataSave` and the engine:
 *
 *  - `IOLoginDataLoad::loadPlayerBestiaryCharms`
 *    (src/io/functions/iologindata_load_player.cpp:472-525): the
 *    `player_charms` row (charm points, minor echoes, maxima, expansion, rune
 *    bits, the `charms` blob of raceId/tier per rune) and the `tracker list`
 *    blob; Canary INSERTs a default row when the player has none.
 *  - `IOLoginDataSave::savePlayerBestiarySystem`
 *    (src/io/functions/iologindata_save_player.cpp:478-532): the same columns
 *    plus the tracker list.
 *  - Kill counters, stored by Canary under
 *    `STORAGEVALUE_BESTIARYKILLCOUNT` (`Player::addBestiaryKillCount`,
 *    src/creatures/players/player.cpp:12969-12979). The Kotlin port maps them to
 *    `player_bestiary_kill_counts` and [JdbcFeatureStateRepository] owns that
 *    table; the methods here are the storage-key-shaped entry point that
 *    delegates to it so there is a single writer.
 *  - Animus mastery, serialized into `players.animus_mastery`
 *    (`AnimusMastery::serialize`/`unserialize`,
 *    src/creatures/players/animus_mastery/animus_mastery.cpp:62-73) as an
 *    insertion-ordered list of lowercased monster names; persisted in the
 *    Canary single column (V21). The wave-36 `player_animus_mastery` relational
 *    table (V13) is superseded and no longer read or written.
 *
 * PARITY GAPS: the C++ load resolves tracker race ids through
 * `g_monsters().getMonsterTypeByRaceId` and drops unknown ids; the port returns
 * the persisted ids and leaves resolution to the caller. The slot-bounded tracker
 * set (`Player::addMonsterToCyclopediaTrackerList`, UINT8_MAX entries) is not
 * enforced at the persistence layer.
 */
class IOBestiaryPersistence(
    private val dataSource: DataSource,
    private val featureStateRepository: FeatureStateRepository = JdbcFeatureStateRepository(dataSource),
) {

    /**
     * `IOLoginDataLoad::loadPlayerBestiaryCharms`
     * (iologindata_load_player.cpp:472-525): applies the persisted charm state to
     * [player] and returns the persisted bestiary tracker race ids. When the
     * player has no stored state yet, Canary inserts the `player_charms` default
     * row; this port creates the default `player_feature_state` row instead.
     */
    fun loadPlayerBestiaryCharms(playerId: Long, player: PlayerBestiaryState): List<Int> {
        val state = featureStateRepository.load(playerId) ?: PlayerFeatureState(playerId).also {
            featureStateRepository.save(it)
        }

        player.setCharmPoints(state.charmPoints)
        player.setMinorCharmEchoes(state.minorCharmEchoes)
        player.setMaxCharmPoints(state.maxCharmPoints)
        player.setMaxMinorCharmEchoes(state.maxMinorCharmEchoes)
        player.hasCharmExpansion = state.charmExpansion
        player.setUsedRunesBit(state.usedRunesBit)
        player.setUnlockedRunesBit(state.unlockedRunesBit)

        for ((charmId, assignment) in state.charmAssignments) {
            val rune = CharmRune.fromValue(charmId) ?: continue
            if (rune == CharmRune.CHARM_NONE) {
                continue
            }
            player.parseRacebyCharm(rune, set = true, newRaceid = assignment.raceId)
            player.setCharmTier(rune, assignment.tier)
        }

        return loadBestiaryTracker(playerId)
    }

    /**
     * `IOLoginDataSave::savePlayerBestiarySystem`
     * (iologindata_save_player.cpp:478-532): persists the charm state and tracker.
     * The other advanced-feature fields of the same row (wheel, forge, mounts,
     * familiars, proficiencies) are preserved through a load-modify-save.
     */
    fun savePlayerBestiarySystem(playerId: Long, player: PlayerBestiaryState, tracker: List<Int> = emptyList()) {
        val existing = featureStateRepository.load(playerId) ?: PlayerFeatureState(playerId)
        featureStateRepository.save(
            existing.copy(
                bestiaryKillCounts = player.getBestiaryKillCounts(),
                charmPoints = player.getCharmPoints(),
                minorCharmEchoes = player.getMinorCharmEchoes(),
                maxCharmPoints = player.getMaxCharmPoints(),
                maxMinorCharmEchoes = player.getMaxMinorCharmEchoes(),
                charmExpansion = player.hasCharmExpansion,
                usedRunesBit = player.getUsedRunesBit(),
                unlockedRunesBit = player.getUnlockedRunesBit(),
                charmAssignments = charmAssignments(player),
            ),
        )
        saveBestiaryTracker(playerId, tracker)
    }

    /**
     * The `STORAGEVALUE_BESTIARYKILLCOUNT` storage-key read
     * (`Player::getBestiaryKillCount`, player.cpp:12975-12979) as persisted by
     * [JdbcFeatureStateRepository].
     */
    fun loadBestiaryKillCounts(playerId: Long): Map<Int, Int> =
        featureStateRepository.load(playerId)?.bestiaryKillCounts ?: emptyMap()

    /**
     * The `STORAGEVALUE_BESTIARYKILLCOUNT` storage-key write
     * (`Player::addBestiaryKillCount`, player.cpp:12969-12973); replaces the
     * stored counters like the storage save does.
     */
    fun saveBestiaryKillCounts(playerId: Long, killCounts: Map<Int, Int>) {
        val existing = featureStateRepository.load(playerId) ?: PlayerFeatureState(playerId)
        featureStateRepository.save(existing.copy(bestiaryKillCounts = killCounts))
    }

    /**
     * `player_charms`.`tracker list` (iologindata_load_player.cpp:508-519):
     * insertion-ordered `uint16_t` race ids.
     */
    fun loadBestiaryTracker(playerId: Long): List<Int> = dataSource.connection.use { conn ->
        val tracker = mutableListOf<Int>()
        conn.prepareStatement(
            "SELECT raceid FROM player_bestiary_tracker WHERE player_id = ? ORDER BY position",
        ).use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                while (rs.next()) {
                    tracker.add(rs.getInt("raceid"))
                }
            }
        }
        tracker
    }

    /** `player_charms`.`tracker list` replace-write (iologindata_save_player.cpp:497-502). */
    fun saveBestiaryTracker(playerId: Long, tracker: List<Int>) {
        dataSource.connection.use { conn ->
            conn.autoCommit = false
            try {
                conn.prepareStatement("DELETE FROM player_bestiary_tracker WHERE player_id = ?").use { stmt ->
                    stmt.setLong(1, playerId)
                    stmt.executeUpdate()
                }
                if (tracker.isNotEmpty()) {
                    conn.prepareStatement(
                        "INSERT INTO player_bestiary_tracker (player_id, position, raceid) VALUES (?, ?, ?)",
                    ).use { stmt ->
                        tracker.forEachIndexed { position, raceId ->
                            stmt.setLong(1, playerId)
                            stmt.setInt(2, position)
                            stmt.setInt(3, raceId)
                            stmt.addBatch()
                        }
                        stmt.executeBatch()
                    }
                }
                conn.commit()
            } catch (ex: Exception) {
                conn.rollback()
                throw ex
            }
        }
    }

    /**
     * `players`.`animus_mastery` unserialize (`AnimusMastery::unserialize`,
     * src/creatures/players/animus_mastery/animus_mastery.cpp:68-73; reached by
     * `IOLoginDataLoad::loadPlayerAnimusMastery`,
     * iologindata_load_player.cpp:277-289): the column blob is an
     * insertion-ordered sequence of `PropWriteStream::writeString` records; the
     * decode loop stops at the first failed read exactly like the C++ `while
     * (propStream.readString(monsterName))`.
     */
    fun loadAnimusMastery(playerId: Long): List<String> {
        val blob = loadAnimusMasteryBlob(playerId) ?: return emptyList()
        val stream = PropStream(blob)
        val masteries = mutableListOf<String>()
        while (true) {
            val monsterName = stream.readString() ?: break
            masteries.add(monsterName)
        }
        return masteries
    }

    /**
     * `players`.`animus_mastery` serialize (`AnimusMastery::serialize`,
     * animus_mastery.cpp:62-66; inline block of
     * `IOLoginDataSave::savePlayerFirst`,
     * iologindata_save_player.cpp:293-299): every mastery is written as one
     * length-prefixed UTF-8 record into the Canary single column. The wave-36
     * `player_animus_mastery` rows are left untouched, exactly like Canary,
     * which never had that table.
     */
    fun saveAnimusMastery(playerId: Long, masteries: List<String>) {
        val stream = PropWriteStream()
        for (monsterName in masteries) {
            stream.writeString(monsterName)
        }

        dataSource.connection.use { conn ->
            conn.prepareStatement("UPDATE players SET animus_mastery = ? WHERE id = ?").use { stmt ->
                stmt.setBytes(1, stream.getStream())
                stmt.setLong(2, playerId)
                stmt.executeUpdate()
            }
        }
    }

    /** The raw `players`.`animus_mastery` column read; `null` when the row is missing. */
    private fun loadAnimusMasteryBlob(playerId: Long): ByteArray? = dataSource.connection.use { conn ->
        conn.prepareStatement("SELECT animus_mastery FROM players WHERE id = ?").use { stmt ->
            stmt.setLong(1, playerId)
            stmt.executeQuery().use { rs ->
                if (rs.next()) rs.getBytes("animus_mastery") else null
            }
        }
    }

    /**
     * The `charms` blob loop of `IOLoginDataSave::savePlayerBestiarySystem`
     * (iologindata_save_player.cpp:487-497): every charm rune writes its
     * raceId/tier pair.
     */
    private fun charmAssignments(player: PlayerBestiaryState): Map<Int, CharmAssignmentState> {
        val assignments = linkedMapOf<Int, CharmAssignmentState>()
        for (rune in CharmRune.entries) {
            if (rune == CharmRune.CHARM_NONE) {
                continue
            }
            assignments[rune.value] = CharmAssignmentState(
                raceId = player.parseRacebyCharm(rune),
                tier = player.getCharmTier(rune),
            )
        }
        return assignments
    }
}
