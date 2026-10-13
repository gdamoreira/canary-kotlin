package org.opentibia.canary.kv

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcription of Canary's `tests/unit/kv/kv_test.cpp` (the `KVTest` suite) over the
 * ported engine (`src/kv/kv.cpp`, `src/kv/kv.hpp`) plus the protobuf-value edge cases
 * from `src/kv/value_wrapper.cpp`.
 *
 * The C++ fixture injects `KVMemory` (`tests/fixture/kv/in_memory_kv.hpp`) for
 * `KVStore`; [KVMemory] is the port of that fixture.
 */
class KvStoreTest {

    private fun kv(): KVMemory = KVMemory()

    @Test
    fun `set and get integer value`() {
        val kv = kv()
        kv.set("keyInt", ValueWrapper(42))

        assertThat(kv.get("keyInt")).isNotNull()
        assertThat(kv.get("keyInt")!!.getInt()).isEqualTo(42)
    }

    @Test
    fun `set and get boolean value`() {
        val kv = kv()
        kv.set("keyBool", ValueWrapper(true))

        assertThat(kv.get("keyBool")).isNotNull()
        assertThat(kv.get("keyBool")!!.getBoolean()).isTrue()
    }

    @Test
    fun `set and get string value`() {
        val kv = kv()
        kv.set("keyString", ValueWrapper("hello"))

        assertThat(kv.get("keyString")).isNotNull()
        assertThat(kv.get("keyString")!!.getString()).isEqualTo("hello")
    }

    @Test
    fun `set and get double value`() {
        val kv = kv()
        kv.set("keyDouble", ValueWrapper(3.14))

        assertThat(kv.get("keyDouble")).isNotNull()
        assertThat(kv.get("keyDouble")!!.getDouble()).isEqualTo(3.14)
    }

    @Test
    fun `overwrite existing key`() {
        val kv = kv()
        kv.set("keyInt", ValueWrapper(42))
        kv.set("keyInt", ValueWrapper(43))

        assertThat(kv.get("keyInt")).isNotNull()
        assertThat(kv.get("keyInt")!!.getInt()).isEqualTo(43)
    }

    @Test
    fun `multiple key value pairs`() {
        val kv = kv()
        kv.set("key1", ValueWrapper(1))
        kv.set("key2", ValueWrapper(2))
        kv.set("key3", ValueWrapper(3))

        assertThat(kv.get("key1")!!.getInt()).isEqualTo(1)
        assertThat(kv.get("key2")!!.getInt()).isEqualTo(2)
        assertThat(kv.get("key3")!!.getInt()).isEqualTo(3)
    }

    @Test
    fun `non existent key`() {
        val kv = kv()

        assertThat(kv.get("non-existant")).isNull()
        assertThat(kv.get("non-existant2")).isNull()
    }

    @Test
    fun `set and get map type value`() {
        val kv = kv()
        val mapValue = ValueWrapper(
            mapOf("key1" to ValueWrapper(1), "key2" to ValueWrapper(2)),
        )
        kv.set("keyMap", mapValue)

        assertThat(kv.get("keyMap")).isNotNull()
        assertThat(kv.get("keyMap")!!.getVariant()).isEqualTo(mapValue.getVariant())
    }

    @Test
    fun `set and get array type value`() {
        val kv = kv()
        val arrayValue = ValueWrapper(listOf(ValueWrapper(1), ValueWrapper(2), ValueWrapper(3)))
        kv.set("keyArray", arrayValue)

        assertThat(kv.get("keyArray")).isNotNull()
        assertThat(kv.get("keyArray")!!.getVariant()).isEqualTo(arrayValue.getVariant())
    }

    @Test
    fun `mixed types in map type`() {
        val kv = kv()
        val mixedMap = ValueWrapper(
            mapOf(
                "keyInt" to ValueWrapper(1),
                "keyString" to ValueWrapper("hello"),
                "keyDouble" to ValueWrapper(3.14),
            ),
        )
        kv.set("keyMixedMap", mixedMap)

        assertThat(kv.get("keyMixedMap")).isNotNull()
        assertThat(kv.get("keyMixedMap")!!.getVariant()).isEqualTo(mixedMap.getVariant())
    }

    @Test
    fun `nested map type and array type`() {
        val kv = kv()
        val nestedMap = ValueWrapper(
            mapOf(
                "keyArray" to ValueWrapper(listOf(ValueWrapper(1), ValueWrapper(2))),
                "keyInnerMap" to ValueWrapper(mapOf("key3" to ValueWrapper(3))),
            ),
        )
        kv.set("keyNested", nestedMap)

        assertThat(kv.get("keyNested")).isNotNull()
        assertThat(kv.get("keyNested")!!.getVariant()).isEqualTo(nestedMap.getVariant())
    }

    @Test
    fun `scoped kv`() {
        val kv = kv()
        val scoped = kv.scoped("scope-name")
        scoped.set("key1", ValueWrapper(1))

        assertThat(scoped.get("key1")).isNotNull()
        assertThat(kv.get("scope-name.key1")).isNotNull()
        assertThat(scoped.get("key1")!!.getInt()).isEqualTo(1)
        assertThat(kv.get("scope-name.key1")!!.getInt()).isEqualTo(1)
    }

    @Test
    fun `nested scoped kv`() {
        val kv = kv()
        val scoped = kv.scoped("scope-name")
        val nestedScoped = scoped.scoped("nested-scope-name")
        val nestedScoped2 = nestedScoped.scoped("nested-scope-name2")
        nestedScoped2.set("key1", ValueWrapper(1))

        assertThat(nestedScoped2.get("key1")).isNotNull()
        assertThat(nestedScoped.get("nested-scope-name2.key1")).isNotNull()
        assertThat(scoped.get("nested-scope-name.nested-scope-name2.key1")).isNotNull()
        assertThat(kv.get("scope-name.nested-scope-name.nested-scope-name2.key1")).isNotNull()
        assertThat(nestedScoped2.get("key1")!!.getInt()).isEqualTo(1)
        assertThat(nestedScoped.get("nested-scope-name2.key1")!!.getInt()).isEqualTo(1)
        assertThat(scoped.get("nested-scope-name.nested-scope-name2.key1")!!.getInt()).isEqualTo(1)
        assertThat(kv.get("scope-name.nested-scope-name.nested-scope-name2.key1")!!.getInt()).isEqualTo(1)
    }

    @Test
    fun `removing an element`() {
        val kv = kv()
        kv.set("key1", ValueWrapper(1))
        kv.set("key2", ValueWrapper(2))
        assertThat(kv.get("key1")).isNotNull()
        assertThat(kv.get("key2")).isNotNull()

        kv.set("key1", ValueWrapper.deleted())

        assertThat(kv.get("key1")).isNull()
        assertThat(kv.get("key2")).isNotNull()
        assertThat(kv.get("key2")!!.getInt()).isEqualTo(2)

        kv.remove("key2")

        assertThat(kv.get("key2")).isNull()
    }

    @Test
    fun `keys skip deleted entries`() {
        val kv = kv()
        kv.set("key1", ValueWrapper(1))
        kv.set("key2", ValueWrapper(2))
        kv.remove("key1")

        val keys = kv.keys()

        assertThat(keys).doesNotContain("key1")
        assertThat(keys).contains("key2")
    }

    @Test
    fun `keys only report entries under the requested prefix`() {
        val kv = kv()
        kv.set("alpha", ValueWrapper(1))
        kv.set("alpine", ValueWrapper(2))
        kv.set("beta", ValueWrapper(3))

        assertThat(kv.keys("al")).containsExactlyInAnyOrder("pha", "pine")
        assertThat(kv.keys()).containsExactlyInAnyOrder("alpha", "alpine", "beta")
    }

    @Test
    fun `deleted wrapper carries the deletion flag and the default variant`() {
        val deleted = ValueWrapper.deleted()

        assertThat(deleted.isDeleted).isTrue()
        assertThat(deleted.getVariant()).isEqualTo(ValueVariant.StringValue(""))
    }

    @Test
    fun `timestamp defaults to the current time and is preserved when provided`() {
        val before = System.currentTimeMillis()
        val now = ValueWrapper("value")
        val after = System.currentTimeMillis()
        val fixed = ValueWrapper("value", 1234L)

        assertThat(now.timestamp).isBetween(before, after)
        assertThat(fixed.timestamp).isEqualTo(1234L)
    }

    @Test
    fun `typed getters fall back to the variant default on type mismatch`() {
        val wrapper = ValueWrapper(7)

        assertThat(wrapper.getInt()).isEqualTo(7)
        assertThat(wrapper.getString()).isEmpty()
        assertThat(wrapper.getBoolean()).isFalse()
        assertThat(wrapper.getDouble()).isEqualTo(0.0)
        assertThat(wrapper.getArray()).isEmpty()
        assertThat(wrapper.getMap()).isEmpty()
    }

    @Test
    fun `getNumber reads int and double variants`() {
        assertThat(ValueWrapper(2).getNumber()).isEqualTo(2.0)
        assertThat(ValueWrapper(2.5).getNumber()).isEqualTo(2.5)
        assertThat(ValueWrapper("text").getNumber()).isEqualTo(0.0)
    }

    @Test
    fun `map and array accessors return nested values or null`() {
        val wrapper = ValueWrapper(
            mapOf(
                "array" to ValueWrapper(listOf(ValueWrapper(1), ValueWrapper(2))),
                "scalar" to ValueWrapper("x"),
            ),
        )

        assertThat(wrapper.get("scalar")!!.getString()).isEqualTo("x")
        assertThat(wrapper.get("array")!!.get(1)!!.getInt()).isEqualTo(2)
        assertThat(wrapper.get("array")!!.get(9)).isNull()
        assertThat(wrapper.get("missing")).isNull()
        assertThat(ValueWrapper(1).get("scalar")).isNull()
    }

    @Test
    fun `forceLoad bypasses the cached value and overwrites it`() {
        val store = RecordingStore()
        store.set("k", ValueWrapper(1))
        store.persisted = ValueWrapper(2)

        assertThat(store.get("k")!!.getInt()).isEqualTo(1)
        assertThat(store.get("k", forceLoad = true)!!.getInt()).isEqualTo(2)
        assertThat(store.get("k")!!.getInt()).isEqualTo(2)
    }

    @Test
    fun `keys merge cached entries with the persisted prefix`() {
        val store = RecordingStore()
        store.set("alpha", ValueWrapper(1))
        store.set("gone", ValueWrapper(2))
        store.remove("gone")
        store.persistedPrefix = mutableListOf("pine", "pine2")

        assertThat(store.keys("al")).containsExactlyInAnyOrder("pha", "pine", "pine2")
    }

    @Test
    fun `flush saves every cached entry and clears the cache`() {
        val store = RecordingStore()
        store.set("k", ValueWrapper(1))
        store.remove("gone")

        store.flush()

        assertThat(store.saved.map { it.first }).containsExactlyInAnyOrder("k", "gone")
        assertThat(store.keys()).isEmpty()
    }

    @Test
    fun `max size evicts the least recently used entry and persists it`() {
        val store = RecordingStore(maxSize = 2)
        store.set("a", ValueWrapper(1))
        store.set("b", ValueWrapper(2))
        store.get("a")
        store.set("c", ValueWrapper(3))

        assertThat(store.get("a")!!.getInt()).isEqualTo(1)
        assertThat(store.get("b")).isNull()
        assertThat(store.get("c")!!.getInt()).isEqualTo(3)
        assertThat(store.saved.map { it.first }).contains("b")
    }

    @Test
    fun `default saveAll succeeds for the in-memory store`() {
        assertThat(kv().saveAll()).isTrue()
    }

    @Test
    fun `generate uuid is a 20 digit millisecond stamp and a 12 digit counter`() {
        val first = KV.generateUUID()
        val second = KV.generateUUID()

        assertThat(first).matches("\\d{20}-\\d{12}")
        assertThat(second).matches("\\d{20}-\\d{12}")
        assertThat(first).isNotEqualTo(second)
    }

    /** Port of `KVMemory` (`tests/fixture/kv/in_memory_kv.hpp`) with recording hooks. */
    private class RecordingStore(maxSize: Int = KVStore.MAX_SIZE) : KVStore(maxSize) {
        var persisted: ValueWrapper? = null
        var persistedPrefix: MutableList<String> = mutableListOf()
        val saved = mutableListOf<Pair<String, ValueWrapper>>()

        override fun load(key: String): ValueWrapper? = persisted

        override fun save(key: String, value: ValueWrapper): Boolean {
            saved += key to value
            return true
        }

        override fun loadPrefix(prefix: String): List<String> = persistedPrefix
    }
}
