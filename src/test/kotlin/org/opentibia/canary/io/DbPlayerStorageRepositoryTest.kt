package org.opentibia.canary.io

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.creatures.players.components.PlayerStorageRow
import org.opentibia.canary.creatures.players.components.StoragePlayer
import org.opentibia.canary.database.DatabaseConnectionFactory
import org.opentibia.canary.systems.familiar.FamiliarEntry

/**
 * H2 (Flyway V1-V14) round-trip tests for [DbPlayerStorageRepository],
 * transcribed from Canary's typed storage repository:
 * `tests/integration/player_storage/player_storage_repository_db_it.cpp:56-101`
 * (`Load`, `DeleteKeys`, `Upsert`) and the repository implementation
 * `src/io/player_storage_repository_db.cpp:25-57` over the `player_storage`
 * table (`schema.sql:803-811`).
 *
 * The delta/dirty round-trip tests transcribe Canary's persistence flow:
 * `IOLoginDataSave::savePlayerStorage`
 * (src/io/functions/iologindata_save_player.cpp:837-857) deletes the delta's
 * removals first and upserts the modifications second, then clears the dirty
 * tracking; `IOLoginDataLoad::loadPlayerStorageMap`
 * (src/io/functions/iologindata_load_player.cpp:759-767) loads every row and
 * ingests it into the component without marking it dirty.
 */
class DbPlayerStorageRepositoryTest {

    private class FakeStoragePlayer(private val playerName: String = "Storage Test") : StoragePlayer {
        override fun getName(): String = playerName

        override fun getOutfits(): MutableList<OutfitEntry> = mutableListOf()

        override fun getFamiliars(): MutableList<FamiliarEntry> = mutableListOf()
    }

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: DbPlayerStorageRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_storage_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(10)
        repository = DbPlayerStorageRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `load returns every stored key value pair for the player`() {
        insertRawStorage(1L, key = 100, value = 42)
        insertRawStorage(1L, key = 200, value = 55)

        assertThat(repository.load(1L)).containsExactlyInAnyOrder(
            PlayerStorageRow(key = 100, value = 42),
            PlayerStorageRow(key = 200, value = 55),
        )
    }

    @Test
    fun `load returns an empty list when the player has no stored keys`() {
        assertThat(repository.load(2L)).isEmpty()
    }

    @Test
    fun `load only returns rows of the given player`() {
        insertRawStorage(1L, key = 100, value = 42)
        insertRawStorage(2L, key = 100, value = 77)

        assertThat(repository.load(1L)).containsExactly(PlayerStorageRow(key = 100, value = 42))
        assertThat(repository.load(2L)).containsExactly(PlayerStorageRow(key = 100, value = 77))
    }

    @Test
    fun `deleteKeys removes only the requested keys and reports success`() {
        insertRawStorage(1L, key = 1, value = 10)
        insertRawStorage(1L, key = 2, value = 20)
        insertRawStorage(1L, key = 3, value = 30)

        assertThat(repository.deleteKeys(1L, listOf(1, 3))).isTrue()

        assertThat(repository.load(1L)).containsExactly(PlayerStorageRow(key = 2, value = 20))
    }

    @Test
    fun `deleteKeys with an empty key list succeeds and keeps every row`() {
        insertRawStorage(1L, key = 1, value = 10)

        assertThat(repository.deleteKeys(1L, emptyList())).isTrue()

        assertThat(repository.load(1L)).containsExactly(PlayerStorageRow(key = 1, value = 10))
    }

    @Test
    fun `deleteKeys only affects the given player`() {
        insertRawStorage(1L, key = 1, value = 10)
        insertRawStorage(2L, key = 1, value = 20)

        assertThat(repository.deleteKeys(1L, listOf(1))).isTrue()

        assertThat(repository.load(1L)).isEmpty()
        assertThat(repository.load(2L)).containsExactly(PlayerStorageRow(key = 1, value = 20))
    }

    @Test
    fun `upsert inserts new keys and reports success`() {
        assertThat(repository.upsert(1L, mapOf(1 to 10, 2 to 20))).isTrue()

        assertThat(repository.load(1L)).containsExactlyInAnyOrder(
            PlayerStorageRow(key = 1, value = 10),
            PlayerStorageRow(key = 2, value = 20),
        )
    }

    @Test
    fun `upsert overwrites existing values and leaves the other keys untouched`() {
        assertThat(repository.upsert(1L, mapOf(1 to 10, 2 to 20))).isTrue()

        assertThat(repository.upsert(1L, mapOf(1 to 100))).isTrue()

        assertThat(repository.load(1L)).containsExactlyInAnyOrder(
            PlayerStorageRow(key = 1, value = 100),
            PlayerStorageRow(key = 2, value = 20),
        )
    }

    @Test
    fun `upsert with an empty map succeeds and writes nothing`() {
        assertThat(repository.upsert(1L, emptyMap())).isTrue()

        assertThat(repository.load(1L)).isEmpty()
    }

    @Test
    fun `upsert round-trips negative int32 values`() {
        assertThat(repository.upsert(1L, mapOf(50000 to -7, 50001 to Int.MIN_VALUE))).isTrue()

        assertThat(repository.load(1L)).containsExactlyInAnyOrder(
            PlayerStorageRow(key = 50000, value = -7),
            PlayerStorageRow(key = 50001, value = Int.MIN_VALUE),
        )
    }

    @Test
    fun `upsert only affects the given player`() {
        insertRawStorage(2L, key = 1, value = 20)

        assertThat(repository.upsert(1L, mapOf(1 to 10))).isTrue()

        assertThat(repository.load(1L)).containsExactly(PlayerStorageRow(key = 1, value = 10))
        assertThat(repository.load(2L)).containsExactly(PlayerStorageRow(key = 1, value = 20))
    }

    @Test
    fun `savePlayerStorage deletes the delta removals before upserting modifications`() {
        val storage = PlayerStorage(FakeStoragePlayer())
        storage.add(100, 42, true)
        storage.add(200, 55, true)
        storage.remove(100)

        val delta = storage.delta()
        assertThat(delta.deletions).containsExactly(100)
        assertThat(delta.upserts).containsExactlyEntriesOf(mapOf(200 to 55))

        assertThat(repository.deleteKeys(1L, delta.deletions)).isTrue()
        assertThat(repository.upsert(1L, delta.upserts)).isTrue()
        storage.clearDirty()

        assertThat(storage.delta().upserts).isEmpty()
        assertThat(storage.delta().deletions).isEmpty()
        assertThat(repository.load(1L)).containsExactly(PlayerStorageRow(key = 200, value = 55))
    }

    @Test
    fun `re-adding a removed key deletes first so the upsert wins`() {
        val storage = PlayerStorage(FakeStoragePlayer())
        storage.add(1, 10, true)
        storage.add(1, -1)
        storage.add(1, 55, true)

        val delta = storage.delta()
        assertThat(delta.deletions).containsExactly(1)
        assertThat(delta.upserts).containsExactlyEntriesOf(mapOf(1 to 55))

        assertThat(repository.deleteKeys(1L, delta.deletions)).isTrue()
        assertThat(repository.upsert(1L, delta.upserts)).isTrue()

        assertThat(repository.load(1L)).containsExactly(PlayerStorageRow(key = 1, value = 55))
    }

    @Test
    fun `loadPlayerStorageMap ingests persisted rows without dirtying them`() {
        insertRawStorage(1L, key = 7, value = 70)
        val storage = PlayerStorage(FakeStoragePlayer())
        storage.add(999, 1)

        storage.ingest(repository.load(1L))

        assertThat(storage.get(7)).isEqualTo(70)
        assertThat(storage.get(999)).isEqualTo(-1)
        assertThat(storage.getModifiedKeys()).isEmpty()
        assertThat(storage.getRemovedKeys()).isEmpty()
        assertThat(storage.delta().upserts).isEmpty()
        assertThat(storage.delta().deletions).isEmpty()
    }

    private fun insertRawStorage(playerId: Long, key: Int, value: Int) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO player_storage (player_id, \"key\", \"value\") VALUES (?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, key)
                stmt.setInt(3, value)
                stmt.executeUpdate()
            }
        }
    }
}
