package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.monsters.MonsterType

/**
 * Transcribes the cyclopedia monster-tracker set
 * (src/creatures/players/player.cpp:941-1004):
 *
 * - `Player::getCyclopediaMonsterTrackerSet` (player.cpp:941-943)
 * - `Player::addMonsterToCyclopediaTrackerList` (player.cpp:945-963)
 * - `Player::removeMonsterFromCyclopediaTrackerList` (player.cpp:965-984)
 * - `Player::isBossOnBosstiaryTracker` (player.cpp:998-1000)
 * - `Player::isMonsterOnBestiaryTracker` (player.cpp:1002-1004)
 *
 * Every mutating method starts with `if (!client) return;` in Canary, so the
 * `client` is a constructor seam (AGENTS.md 1.1). The tracker set stores the
 * `shared_ptr<MonsterType>` identity, including a possible `nullptr` entry from
 * `tracker.emplace(mtype)`.
 */
class PlayerCyclopediaTrackerStateTest {

    private class RecordingClient : CyclopediaTrackerClient {
        var bosstiarySends = 0
        val entryChanges = mutableListOf<Int>()
        val refreshes = mutableListOf<Pair<Set<MonsterType?>, Boolean>>()

        override fun parseSendBosstiary() {
            bosstiarySends++
        }

        override fun sendBestiaryEntryChanged(raceId: Int) {
            entryChanges.add(raceId)
        }

        override fun refreshCyclopediaMonsterTracker(trackerList: Set<MonsterType?>, isBoss: Boolean) {
            refreshes.add(trackerList to isBoss)
        }
    }

    private fun monster(name: String, raceId: Int): MonsterType = MonsterType(name).apply {
        info.raceid = raceId
    }

    @Test
    fun `without a client every mutation is a no-op`() {
        // `if (!client) return;` (player.cpp:946-948, 966-968).
        val state = PlayerCyclopediaTrackerState()
        val monster = monster("Rat", 21)

        state.addMonsterToCyclopediaTrackerList(monster, isBoss = false)

        assertThat(state.getCyclopediaMonsterTrackerSet(false)).isEmpty()
        assertThat(state.isMonsterOnBestiaryTracker(monster)).isFalse()
    }

    @Test
    fun `addMonsterToCyclopediaTrackerList stores the identity and refreshes the client`() {
        // player.cpp:950-962: `tracker.emplace(mtype).second` then refresh.
        val client = RecordingClient()
        val state = PlayerCyclopediaTrackerState(client)
        val rat = monster("Rat", 21)
        val dragon = monster("Dragon", 34)

        state.addMonsterToCyclopediaTrackerList(rat, isBoss = false)
        state.addMonsterToCyclopediaTrackerList(dragon, isBoss = true)

        assertThat(state.getCyclopediaMonsterTrackerSet(false)).containsExactly(rat)
        assertThat(state.getCyclopediaMonsterTrackerSet(true)).containsExactly(dragon)
        assertThat(state.isMonsterOnBestiaryTracker(rat)).isTrue()
        assertThat(state.isBossOnBosstiaryTracker(dragon)).isTrue()
        assertThat(client.refreshes.map { it.second }).containsExactly(false, true)
        assertThat(client.refreshes.first().first).containsExactly(rat)
    }

    @Test
    fun `adding the same monster twice does not refresh twice`() {
        // `tracker.emplace(mtype).second` is false on a duplicate (player.cpp:952).
        val client = RecordingClient()
        val state = PlayerCyclopediaTrackerState(client)
        val rat = monster("Rat", 21)

        state.addMonsterToCyclopediaTrackerList(rat, isBoss = false)
        state.addMonsterToCyclopediaTrackerList(rat, isBoss = false)

        assertThat(state.getCyclopediaMonsterTrackerSet(false)).containsExactly(rat)
        assertThat(client.refreshes).hasSize(1)
    }

    @Test
    fun `reloadClient reports bestiary and bosstiary entry changes`() {
        // player.cpp:953-961: bestiary -> sendBestiaryEntryChanged(raceId),
        // boss -> parseSendBosstiary, both only when `reloadClient` and raceId != 0.
        val client = RecordingClient()
        val state = PlayerCyclopediaTrackerState(client)

        state.addMonsterToCyclopediaTrackerList(monster("Rat", 21), isBoss = false, reloadClient = true)
        state.addMonsterToCyclopediaTrackerList(monster("Orshabaal", 110), isBoss = true, reloadClient = true)

        assertThat(client.entryChanges).containsExactly(21)
        assertThat(client.bosstiarySends).isEqualTo(1)
    }

    @Test
    fun `reloadClient without a race id skips the entry change`() {
        // `raceId != 0` (player.cpp:956).
        val client = RecordingClient()
        val state = PlayerCyclopediaTrackerState(client)

        state.addMonsterToCyclopediaTrackerList(monster("Rat", 0), isBoss = false, reloadClient = true)

        assertThat(client.entryChanges).isEmpty()
        assertThat(client.refreshes).hasSize(1)
    }

    @Test
    fun `a null monster type is stored like the C++ shared_ptr overload`() {
        // `tracker.emplace(mtype)` adds a nullptr; the query null-check keeps
        // `isMonsterOnBestiaryTracker(null)` false (player.cpp:1003).
        val client = RecordingClient()
        val state = PlayerCyclopediaTrackerState(client)

        state.addMonsterToCyclopediaTrackerList(null, isBoss = false)

        assertThat(state.getCyclopediaMonsterTrackerSet(false)).containsExactly(null as MonsterType?)
        assertThat(state.isMonsterOnBestiaryTracker(null)).isFalse()
    }

    @Test
    fun `the tracker caps at two hundred fifty-five entries`() {
        // `tracker.size() < std::numeric_limits<uint8_t>::max()` (player.cpp:952).
        val state = PlayerCyclopediaTrackerState(RecordingClient())
        repeat(255) { index -> state.addMonsterToCyclopediaTrackerList(monster("M$index", index + 1), isBoss = false) }
        val overflow = monster("Overflow", 999)

        state.addMonsterToCyclopediaTrackerList(overflow, isBoss = false)

        assertThat(state.getCyclopediaMonsterTrackerSet(false)).hasSize(255)
        assertThat(state.isMonsterOnBestiaryTracker(overflow)).isFalse()
    }

    @Test
    fun `removeMonsterFromCyclopediaTrackerList erases and refreshes only when present`() {
        // player.cpp:965-984: `tracker.erase(mtype) > 0`.
        val client = RecordingClient()
        val state = PlayerCyclopediaTrackerState(client)
        val rat = monster("Rat", 21)
        val dragon = monster("Dragon", 34)
        state.addMonsterToCyclopediaTrackerList(rat, isBoss = false)

        state.removeMonsterFromCyclopediaTrackerList(rat, isBoss = false)
        assertThat(state.isMonsterOnBestiaryTracker(rat)).isFalse()
        assertThat(client.refreshes).hasSize(2)

        state.removeMonsterFromCyclopediaTrackerList(dragon, isBoss = false)
        assertThat(client.refreshes).hasSize(2)
    }
}
