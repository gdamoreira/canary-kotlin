package org.opentibia.canary.creatures.players.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.systems.familiar.FamiliarEntry

/**
 * Transcript tests for Canary `PlayerStorage`
 * (`src/creatures/players/components/player_storage.hpp/.cpp`) and the unit
 * suite `tests/unit/players/components/player_storage_test.cpp`.
 *
 * The C++ tests construct a real `Player`; the port injects the `Player` view
 * with [FakeStoragePlayer], which owns the `outfits`/`familiars` lists the
 * component mutates directly, exactly like `Player::outfits`/`Player::familiars`
 * (player.hpp:1686-1687).
 */
class PlayerStorageTest {

    private class FakeStoragePlayer(
        private val playerName: String = "Test Player",
        val outfitEntries: MutableList<OutfitEntry> = mutableListOf(),
        val familiarEntries: MutableList<FamiliarEntry> = mutableListOf(),
    ) : StoragePlayer {
        override fun getName(): String = playerName

        override fun getOutfits(): MutableList<OutfitEntry> = outfitEntries

        override fun getFamiliars(): MutableList<FamiliarEntry> = familiarEntries
    }

    @Test
    fun `add and get storage value tracks an upsert`() {
        val player = FakeStoragePlayer()
        val storage = PlayerStorage(player)
        val key = 50000

        storage.ingest(emptyList())
        storage.add(key, 42, true)

        assertThat(storage.has(key)).isTrue()
        assertThat(storage.get(key)).isEqualTo(42)
        assertThat(storage.getStorageMap()).hasSize(1)
        val delta = storage.delta()
        assertThat(delta.upserts).containsExactlyEntriesOf(mapOf(key to 42))
        storage.clearDirty()
    }

    @Test
    fun `remove storage value tracks a deletion`() {
        val player = FakeStoragePlayer()
        val storage = PlayerStorage(player)
        val key = 50001

        storage.ingest(emptyList())
        storage.add(key, 7, true)
        assertThat(storage.has(key)).isTrue()

        assertThat(storage.remove(key)).isTrue()
        assertThat(storage.has(key)).isFalse()
        val delta = storage.delta()
        assertThat(delta.deletions).containsExactly(key)
        storage.clearDirty()
    }

    @Test
    fun `passthrough mounts range is persisted`() {
        val player = FakeStoragePlayer()
        val storage = PlayerStorage(player)
        val key = PlayerStorage.PSTRG_MOUNTS_RANGE_START + 1

        storage.ingest(emptyList())
        storage.add(key, 123, true)

        assertThat(storage.has(key)).isTrue()
        assertThat(storage.get(key)).isEqualTo(123)
        assertThat(storage.getModifiedKeys()).containsExactly(key)
    }

    @Test
    fun `passthrough wing effect aura and shader ranges are persisted`() {
        val player = FakeStoragePlayer()
        val storage = PlayerStorage(player)

        storage.ingest(emptyList())
        val keys = listOf(
            PlayerStorage.PSTRG_WING_RANGE_START + 1,
            PlayerStorage.PSTRG_EFFECT_RANGE_START + 1,
            PlayerStorage.PSTRG_AURA_RANGE_START + 1,
            PlayerStorage.PSTRG_SHADER_RANGE_START + 1,
        )

        var value = 10
        for (key in keys) {
            storage.add(key, value, true)
            assertThat(storage.has(key)).isTrue()
            assertThat(storage.get(key)).isEqualTo(value)
            assertThat(storage.getModifiedKeys()).contains(key)
            value++
        }
    }

    @Test
    fun `outfits reserved key appends the outfit entry without persisting`() {
        val player = FakeStoragePlayer()
        val storage = PlayerStorage(player)

        storage.ingest(emptyList())
        val beforeOutfits = player.getOutfits().size
        val key = PlayerStorage.PSTRG_OUTFITS_RANGE_START + 1
        val lookType = 50
        val addons = 3

        storage.add(key, (lookType shl 16) or addons, true)

        assertThat(player.getOutfits()).hasSize(beforeOutfits + 1)
        assertThat(player.getOutfits().first().lookType).isEqualTo(lookType)
        assertThat(player.getOutfits().first().addons).isEqualTo(addons)
        assertThat(storage.has(key)).isFalse()
        assertThat(storage.getStorageMap()).isEmpty()
    }

    @Test
    fun `familiars reserved key appends the familiar entry without persisting`() {
        val player = FakeStoragePlayer()
        val storage = PlayerStorage(player)

        storage.ingest(emptyList())
        val before = player.getFamiliars().size
        val key = PlayerStorage.PSTRG_FAMILIARS_RANGE_START + 1
        val lookType = 77

        storage.add(key, lookType shl 16, true)

        assertThat(player.getFamiliars()).hasSize(before + 1)
        assertThat(player.getFamiliars().first().lookType).isEqualTo(lookType)
        assertThat(storage.getStorageMap()).isEmpty()
    }

    @Test
    fun `unknown reserved key is persisted and warns`() {
        val player = FakeStoragePlayer()
        val warnings = mutableListOf<String>()
        val storage = PlayerStorage(player, logWarn = warnings::add)
        val key = PlayerStorage.PSTRG_RESERVED_RANGE_START + 900000

        storage.ingest(emptyList())
        storage.add(key, 321, true)

        assertThat(storage.has(key)).isTrue()
        assertThat(storage.get(key)).isEqualTo(321)
        assertThat(warnings).containsExactly("Unknown reserved key: $key for player: Test Player")
    }

    @Test
    fun `get returns minus one for a missing key`() {
        val player = FakeStoragePlayer()
        val storage = PlayerStorage(player)

        storage.ingest(emptyList())
        assertThat(storage.get(999999)).isEqualTo(-1)
        assertThat(storage.has(999999)).isFalse()
    }

    @Test
    fun `ingest clears the map and dirty state and loads rows untracked`() {
        val player = FakeStoragePlayer()
        val storage = PlayerStorage(player)
        storage.add(1, 10, true)
        storage.add(2, -1)

        storage.ingest(listOf(PlayerStorageRow(100, 5), PlayerStorageRow(200, 6)))

        assertThat(storage.getStorageMap())
            .containsExactlyEntriesOf(mapOf(100 to 5, 200 to 6))
        assertThat(storage.getModifiedKeys()).isEmpty()
        assertThat(storage.getRemovedKeys()).isEmpty()
        assertThat(storage.delta().upserts).isEmpty()
        assertThat(storage.delta().deletions).isEmpty()
    }

    @Test
    fun `remove returns false for a missing key without enqueuing a deletion`() {
        val storage = PlayerStorage(FakeStoragePlayer())

        assertThat(storage.remove(1234)).isFalse()
        assertThat(storage.getRemovedKeys()).isEmpty()
        assertThat(storage.delta().deletions).isEmpty()
    }

    @Test
    fun `add minus one removes an existing key and enqueues its deletion`() {
        val storage = PlayerStorage(FakeStoragePlayer())
        val key = 50002

        storage.add(key, 99, true)
        storage.add(key, -1)

        assertThat(storage.has(key)).isFalse()
        assertThat(storage.getModifiedKeys()).isEmpty()
        assertThat(storage.getRemovedKeys()).containsExactly(key)
        assertThat(storage.delta().upserts).isEmpty()
        assertThat(storage.delta().deletions).containsExactly(key)
    }

    @Test
    fun `add minus one on a missing key still enqueues a deletion`() {
        val storage = PlayerStorage(FakeStoragePlayer())

        storage.add(4321, -1)

        assertThat(storage.has(4321)).isFalse()
        assertThat(storage.getRemovedKeys()).containsExactly(4321)
    }

    @Test
    fun `add fires the storage update events unless shouldStorageUpdate is set`() {
        val player = FakeStoragePlayer()
        val events = mutableListOf<String>()
        val storage = PlayerStorage(
            player,
            currentFrameTime = { 777L },
            eventOnStorageUpdate = { owner, key, value, oldValue, frameTime ->
                events += "event:${owner.getName()}:$key:$value:$oldValue:$frameTime"
            },
            playerOnStorageUpdateCallback = { owner, key, value, oldValue, frameTime ->
                events += "callback:${owner.getName()}:$key:$value:$oldValue:$frameTime"
            },
        )

        storage.add(1, 5)
        storage.add(1, 9)
        storage.add(2, 4, shouldStorageUpdate = true)

        assertThat(events).containsExactly(
            "event:Test Player:1:5:-1:777",
            "callback:Test Player:1:5:-1:777",
            "event:Test Player:1:9:5:777",
            "callback:Test Player:1:9:5:777",
        )
    }

    @Test
    fun `add marks the key modified even when the value is unchanged`() {
        val storage = PlayerStorage(FakeStoragePlayer())

        storage.add(1, 5, true)
        storage.clearDirty()
        storage.add(1, 5, true)

        assertThat(storage.getModifiedKeys()).containsExactly(1)
        assertThat(storage.delta().upserts).containsExactlyEntriesOf(mapOf(1 to 5))
    }

    @Test
    fun `clearDirty clears modified and removed keys`() {
        val storage = PlayerStorage(FakeStoragePlayer())

        storage.add(1, 5, true)
        storage.add(2, 6, true)
        storage.remove(2)

        storage.clearDirty()

        assertThat(storage.getModifiedKeys()).isEmpty()
        assertThat(storage.getRemovedKeys()).isEmpty()
        assertThat(storage.delta().upserts).isEmpty()
        assertThat(storage.delta().deletions).isEmpty()
        assertThat(storage.get(1)).isEqualTo(5)
    }

    @Test
    fun `re-adding a removed key emits an upsert and keeps the deletion`() {
        val storage = PlayerStorage(FakeStoragePlayer())

        storage.add(1, 10, true)
        storage.add(1, -1)
        storage.add(1, 55, true)

        assertThat(storage.has(1)).isTrue()
        assertThat(storage.get(1)).isEqualTo(55)
        assertThat(storage.delta().upserts).containsExactlyEntriesOf(mapOf(1 to 55))
        assertThat(storage.delta().deletions).containsExactly(1)
    }

    @Test
    fun `prepareForPersist serializes outfits and familiars reserved ranges`() {
        val player = FakeStoragePlayer(
            outfitEntries = mutableListOf(OutfitEntry(lookType = 50, addons = 3), OutfitEntry(lookType = 51, addons = 1)),
            familiarEntries = mutableListOf(FamiliarEntry(77), FamiliarEntry(78)),
        )
        val storage = PlayerStorage(player)

        storage.prepareForPersist()

        val outfitKey = PlayerStorage.PSTRG_OUTFITS_RANGE_START
        val familiarKey = PlayerStorage.PSTRG_FAMILIARS_RANGE_START
        assertThat(storage.getStorageMap()).containsExactlyEntriesOf(
            mapOf(
                (outfitKey + 1) to ((50 shl 16) or 3),
                (outfitKey + 2) to ((51 shl 16) or 1),
                (familiarKey + 1) to (77 shl 16),
                (familiarKey + 2) to (78 shl 16),
            ),
        )
        assertThat(storage.getModifiedKeys()).containsExactlyInAnyOrder(
            outfitKey + 1,
            outfitKey + 2,
            familiarKey + 1,
            familiarKey + 2,
        )
    }

    @Test
    fun `prepareForPersist marks reserved keys modified only when values change`() {
        val outfit = OutfitEntry(lookType = 50, addons = 3)
        val player = FakeStoragePlayer(outfitEntries = mutableListOf(outfit))
        val storage = PlayerStorage(player)
        val key = PlayerStorage.PSTRG_OUTFITS_RANGE_START + 1

        storage.prepareForPersist()
        storage.clearDirty()
        storage.prepareForPersist()

        assertThat(storage.getModifiedKeys()).isEmpty()

        outfit.addons = 1
        storage.prepareForPersist()

        assertThat(storage.getModifiedKeys()).containsExactly(key)
        assertThat(storage.get(key)).isEqualTo((50 shl 16) or 1)

        player.getOutfits().clear()
        storage.clearDirty()
        storage.prepareForPersist()

        assertThat(storage.getStorageMap()).containsKey(key)
        assertThat(storage.getModifiedKeys()).isEmpty()
    }

    @Test
    fun `reserved range membership includes the start offset by the range size`() {
        val player = FakeStoragePlayer()
        val warnings = mutableListOf<String>()
        val storage = PlayerStorage(player, logWarn = warnings::add)
        val outfitStart = PlayerStorage.PSTRG_OUTFITS_RANGE_START
        val inRange = outfitStart + PlayerStorage.PSTRG_OUTFITS_RANGE_SIZE
        val outOfRange = inRange + 1

        storage.add(inRange, (50 shl 16) or 3, true)
        storage.add(outOfRange, 123, true)

        assertThat(player.getOutfits()).hasSize(1)
        assertThat(storage.has(inRange)).isFalse()
        assertThat(storage.get(outOfRange)).isEqualTo(123)
        assertThat(warnings).containsExactly("Unknown reserved key: $outOfRange for player: Test Player")
    }
}
