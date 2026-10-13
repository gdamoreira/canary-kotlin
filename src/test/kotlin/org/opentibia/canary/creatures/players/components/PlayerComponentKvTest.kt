package org.opentibia.canary.creatures.players.components

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Transcript tests for the minimal `KV`/`ScopedKV` seam the player components
 * use, mirroring Canary `src/kv/kv.hpp` and `src/kv/kv.cpp`:
 *  - `ScopedKV::set/get` compose `"{prefix}.{key}"` (kv.hpp:148-154)
 *  - `ScopedKV::keys` strips the scope prefix from the stored keys
 *    (kv.hpp:174-176, kv.cpp:100-118)
 *  - `KV::remove` drops the entry (kv.cpp:120-122)
 */
class PlayerComponentKvTest {

    @Test
    fun `scoped set and get compose the key path`() {
        val kv = InMemoryKV()

        kv.scoped("titles").scoped("unlocked").set("Gold Hoarder", 1234L)

        assertThat(kv.scoped("titles").scoped("unlocked").get("Gold Hoarder")).isEqualTo(1234L)
        assertThat(kv.scoped("titles").get("unlocked")).isNull()
        assertThat(kv.get("titles")).isNull()
    }

    @Test
    fun `keys returns only the names stored under the scope`() {
        val kv = InMemoryKV()
        kv.scoped("titles").scoped("unlocked").set("Gold Hoarder", 1L)
        kv.scoped("titles").scoped("unlocked").set("Beaststrider (Grade 1)", 2L)
        kv.scoped("badges").scoped("unlocked").set("Veteran Hero", 3L)

        assertThat(kv.scoped("titles").scoped("unlocked").keys())
            .containsExactlyInAnyOrder("Gold Hoarder", "Beaststrider (Grade 1)")
        assertThat(kv.scoped("badges").scoped("unlocked").keys()).containsExactly("Veteran Hero")
    }

    @Test
    fun `remove drops the key from its scope`() {
        val kv = InMemoryKV()
        val scoped = kv.scoped("titles").scoped("unlocked")
        scoped.set("Gold Hoarder", 1L)

        scoped.remove("Gold Hoarder")

        assertThat(scoped.get("Gold Hoarder")).isNull()
        assertThat(scoped.keys()).isEmpty()
    }

    @Test
    fun `forceLoad reads the stored value like Canary's cached lookup`() {
        val kv = InMemoryKV()
        kv.scoped("achievements").set("points", 12L)

        assertThat(kv.scoped("achievements").get("points", forceLoad = true)).isEqualTo(12L)
    }
}
