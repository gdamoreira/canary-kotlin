package org.opentibia.canary.kv

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.database.DatabaseConnectionFactory

/**
 * H2 (Flyway `V16__kv_store.sql`) round-trip tests for [KVSQL]
 * (`src/kv/kv_sql.cpp`): `load`, `loadPrefix`, `save`, `saveAll` and the inherited
 * `KVStore` write-behind cache.
 */
class KvSqlTest {

    private lateinit var dataSource: HikariDataSource

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_kv_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        // Flyway baselines at V1 and then executes V2..V16 on a fresh database.
        assertThat(applied).isGreaterThanOrEqualTo(15)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    private fun store(maxSize: Int = KVStore.MAX_SIZE): KVSQL = KVSQL(dataSource, maxSize)

    private fun countRows(key: String): Int =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT COUNT(*) FROM kv_store WHERE key_name = ?").use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { result ->
                    result.next()
                    result.getInt(1)
                }
            }
        }

    private fun storedValue(key: String): ByteArray =
        dataSource.connection.use { connection ->
            connection.prepareStatement("SELECT \"value\" FROM kv_store WHERE key_name = ?").use { statement ->
                statement.setString(1, key)
                statement.executeQuery().use { result ->
                    assertThat(result.next()).isTrue()
                    result.getBytes("value")
                }
            }
        }

    @Test
    fun `saveAll persists cached entries and a fresh store loads them`() {
        val writer = store()
        writer.set("player.1.level", ValueWrapper(10))

        assertThat(writer.saveAll()).isTrue()

        val loaded = store().get("player.1.level")
        assertThat(loaded).isNotNull()
        assertThat(loaded!!.getInt()).isEqualTo(10)
    }

    @Test
    fun `flush writes and clears the local cache`() {
        val writer = store()
        writer.set("k", ValueWrapper("v"))

        writer.flush()

        assertThat(store().get("k")!!.getString()).isEqualTo("v")
        assertThat(writer.get("k")!!.getString()).isEqualTo("v")
    }

    @Test
    fun `timestamp survives the database round-trip`() {
        val writer = store()
        writer.set("k", ValueWrapper(1, 1234567890123L))

        assertThat(writer.saveAll()).isTrue()

        assertThat(store().get("k")!!.timestamp).isEqualTo(1234567890123L)
    }

    @Test
    fun `remove persists as a delete on flush`() {
        val writer = store()
        writer.set("k", ValueWrapper(1))
        writer.flush()

        writer.remove("k")
        writer.flush()

        assertThat(countRows("k")).isZero()
        assertThat(store().get("k")).isNull()
    }

    @Test
    fun `removing a key without a stored row still succeeds`() {
        val writer = store()
        writer.remove("missing")

        assertThat(writer.saveAll()).isTrue()
        assertThat(countRows("missing")).isZero()
    }

    @Test
    fun `keys reports persisted keys with the prefix stripped`() {
        val writer = store()
        writer.set("player.1.level", ValueWrapper(10))
        writer.set("player.1.name", ValueWrapper("Bob"))
        writer.set("guild.9", ValueWrapper(true))
        writer.flush()

        assertThat(store().keys("player.1.")).containsExactlyInAnyOrder("level", "name")
        assertThat(store().keys()).containsExactlyInAnyOrder("player.1.level", "player.1.name", "guild.9")
    }

    @Test
    fun `keys strips every occurrence of the prefix like replaceString`() {
        val writer = store()
        writer.set("player.player.1", ValueWrapper(1))
        writer.flush()

        assertThat(store().keys("player.")).containsExactly("1")
    }

    @Test
    fun `keys merges cached entries with persisted ones`() {
        val writer = store()
        writer.set("stored", ValueWrapper(1))
        writer.flush()

        val cached = store()
        cached.set("cached", ValueWrapper(2))

        assertThat(cached.keys()).containsExactlyInAnyOrder("stored", "cached")
    }

    @Test
    fun `keys skips cached tombstones but still reports the unflushed row`() {
        val writer = store()
        writer.set("k", ValueWrapper(1))
        writer.flush()

        writer.remove("k")

        assertThat(writer.keys()).contains("k")

        writer.flush()

        assertThat(writer.keys()).doesNotContain("k")
    }

    @Test
    fun `forceLoad reads through the cache`() {
        val first = store()
        first.set("k", ValueWrapper(1))

        val second = store()
        second.set("k", ValueWrapper(2))
        second.saveAll()

        assertThat(first.get("k")!!.getInt()).isEqualTo(1)
        assertThat(first.get("k", forceLoad = true)!!.getInt()).isEqualTo(2)
        assertThat(first.get("k")!!.getInt()).isEqualTo(2)
    }

    @Test
    fun `malformed value blobs are ignored`() {
        dataSource.connection.use { connection ->
            connection.prepareStatement("INSERT INTO kv_store (key_name, \"timestamp\", \"value\") VALUES (?, ?, ?)")
                .use { statement ->
                    statement.setString(1, "bad")
                    statement.setLong(2, 1L)
                    statement.setBytes(3, byteArrayOf(0x80.toByte()))
                    statement.executeUpdate()
                }
        }

        assertThat(store().get("bad")).isNull()
    }

    @Test
    fun `every value kind round-trips through the bytea column`() {
        val cases = linkedMapOf(
            "string" to ValueWrapper("héllo-日本語"),
            "bool" to ValueWrapper(true),
            "int" to ValueWrapper(-42),
            "double" to ValueWrapper(3.5),
            "array" to ValueWrapper(listOf(ValueWrapper(1), ValueWrapper("two"), ValueWrapper(false))),
            "map" to ValueWrapper(
                mapOf(
                    "answer" to ValueWrapper(42),
                    "nested" to ValueWrapper(mapOf("flag" to ValueWrapper(true))),
                ),
            ),
        )
        val writer = store()
        cases.forEach { (key, value) -> writer.set(key, value) }

        assertThat(writer.saveAll()).isTrue()

        val reader = store()
        cases.forEach { (key, value) ->
            assertThat(reader.get(key)!!.getVariant()).isEqualTo(value.getVariant())
        }
    }

    @Test
    fun `the stored blob is the canonical protobuf encoding`() {
        val writer = store()
        writer.set("k", ValueWrapper("hello"))
        writer.saveAll()

        assertThat(storedValue("k"))
            .isEqualTo(ProtoSerializable.toProto(ValueWrapper("hello")).serializeToByteArray())
    }

    @Test
    fun `scoped keys persist under the scope prefix`() {
        val scope = store().scoped("player").scoped("1")
        scope.set("level", ValueWrapper(10))
        assertThat(scope.saveAll()).isTrue()

        val reader = store().scoped("player").scoped("1")
        assertThat(reader.get("level")!!.getInt()).isEqualTo(10)
        assertThat(countRows("player.1.level")).isEqualTo(1)
        assertThat(reader.keys()).containsExactly("level")
    }

    @Test
    fun `get on a missing key returns null`() {
        assertThat(store().get("missing")).isNull()
    }

    @Test
    fun `saveAll on an empty store succeeds`() {
        assertThat(store().saveAll()).isTrue()
    }

    @Test
    fun `evicted entries are persisted immediately`() {
        val writer = store(maxSize = 2)
        writer.set("a", ValueWrapper(1))
        writer.set("b", ValueWrapper(2))
        writer.set("c", ValueWrapper(3))

        assertThat(store().get("a")!!.getInt()).isEqualTo(1)
    }

    @Test
    fun `zero timestamp defaults to the current time when persisted`() {
        val before = System.currentTimeMillis()
        val writer = store()
        writer.set("k", ValueWrapper(1))
        writer.saveAll()

        assertThat(store().get("k")!!.timestamp).isGreaterThanOrEqualTo(before)
    }
}
