package org.opentibia.canary.systems.mount

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.appearance.outfit.OutfitEntry
import org.opentibia.canary.creatures.players.components.PlayerStorage
import org.opentibia.canary.creatures.players.components.StoragePlayer
import org.opentibia.canary.systems.familiar.FamiliarEntry
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transcript of the Player mount runtime (src/creatures/players/player.cpp:
 * 7918-8128) over the persistence seams it uses in Canary: the reserved
 * `PlayerStorage` mount range (`PSTRG_MOUNTS_RANGE_START`..,
 * src/utils/const.hpp:50-52) and the `last-mount` key of the player KV store
 * (`kv()->get("last-mount")`, player.cpp:7923).
 */
class PlayerMountStatePersistenceTest {

    private class FakeStoragePlayer : StoragePlayer {
        override fun getName(): String = "Tester"
        override fun getOutfits(): MutableList<OutfitEntry> = mutableListOf()
        override fun getFamiliars(): MutableList<FamiliarEntry> = mutableListOf()
    }

    private fun realMountsXml(): String {
        val candidates = listOf(
            Path.of("data/XML/mounts.xml"),
            Path.of("../data/XML/mounts.xml"),
            Path.of("../../data/XML/mounts.xml"),
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("data/XML/mounts.xml not found from ${System.getProperty("user.dir")}")
        return Files.readString(path)
    }

    private fun loadedMounts(): Mounts = Mounts().apply {
        assertThat(loadFromXml(realMountsXml())).isTrue()
    }

    @Test
    fun `tame mount packs the owned bit into the reserved storage range`() {
        val storage = PlayerStorage(FakeStoragePlayer())
        val state = PlayerMountState(loadedMounts(), storage = PlayerStorageMountStorage(storage))

        assertThat(state.tameMount(1)).isTrue()

        assertThat(storage.get(PlayerMountState.PSTRG_MOUNTS_RANGE_START)).isEqualTo(1)
        assertThat(state.getOwnedMounts()).containsExactly(1)
    }

    @Test
    fun `owned mounts survive a reload through the same storage`() {
        val storage = PlayerStorage(FakeStoragePlayer())
        val mounts = loadedMounts()
        val first = PlayerMountState(mounts, storage = PlayerStorageMountStorage(storage))
        first.tameMount(1)
        first.tameMount(2)
        first.setCurrentMount(1)

        val reloaded = PlayerMountState(mounts, storage = PlayerStorageMountStorage(storage))

        assertThat(reloaded.hasMount(mounts.getMountByID(1)!!)).isTrue()
        assertThat(reloaded.hasMount(mounts.getMountByID(2)!!)).isTrue()
        assertThat(reloaded.getOwnedMounts()).containsExactly(1, 2)
        assertThat(reloaded.getCurrentMount()).isEqualTo(1)
        assertThat(storage.get(PlayerMountState.PSTRG_MOUNTS_CURRENTMOUNT)).isEqualTo(1)
    }

    @Test
    fun `untame mount clears the bit and keeps the other owned mounts`() {
        val storage = PlayerStorage(FakeStoragePlayer())
        val state = PlayerMountState(loadedMounts(), storage = PlayerStorageMountStorage(storage))
        state.tameMount(1)
        state.tameMount(2)

        assertThat(state.untameMount(1)).isTrue()

        assertThat(state.getOwnedMounts()).containsExactly(2)
        assertThat(storage.get(PlayerMountState.PSTRG_MOUNTS_RANGE_START)).isEqualTo(1 shl 1)
    }

    @Test
    fun `toggling a mount writes the last mount into the player kv store`() {
        val kv = InMemoryMountKeyValueStore()
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, kv = kv, now = { 10_000L })
        state.tameMount(1)
        state.setCurrentMount(1)

        assertThat(state.toggleMount(true)).isTrue()

        assertThat(kv.get(PlayerMountState.LAST_MOUNT_KEY)).isEqualTo(1)
    }

    @Test
    fun `the last mount falls back to the kv store when no current mount is selected`() {
        val kv = InMemoryMountKeyValueStore()
        kv.set(PlayerMountState.LAST_MOUNT_KEY, 5)
        val state = PlayerMountState(loadedMounts(), kv = kv)

        assertThat(state.getLastMount()).isEqualTo(5)
    }

    @Test
    fun `the current mount storage value wins over the kv store`() {
        val kv = InMemoryMountKeyValueStore()
        kv.set(PlayerMountState.LAST_MOUNT_KEY, 5)
        val state = PlayerMountState(loadedMounts(), kv = kv)
        state.setCurrentMount(1)

        assertThat(state.getLastMount()).isEqualTo(1)
    }

    @Test
    fun `untaming the active mount clears the persisted last mount`() {
        val kv = InMemoryMountKeyValueStore()
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, kv = kv, now = { 10_000L })
        state.tameMount(1)
        state.setCurrentMount(1)
        state.toggleMount(true)
        assertThat(kv.get(PlayerMountState.LAST_MOUNT_KEY)).isEqualTo(1)

        assertThat(state.untameMount(1)).isTrue()

        assertThat(kv.get(PlayerMountState.LAST_MOUNT_KEY)).isEqualTo(0)
        assertThat(state.getCurrentMount()).isEqualTo(0)
    }

    // Protocol feedback of `Player::toggleMount` (player.cpp:7971-8052).

    @Test
    fun `toggle exhaustion inside the cooldown sends you are exhausted`() {
        val mounts = loadedMounts()
        var now = 10_000L
        val cancels = mutableListOf<ReturnValue>()
        val state = PlayerMountState(mounts, sendCancelMessage = cancels::add, now = { now })
        state.tameMount(1)
        state.setCurrentMount(1)
        state.toggleMount(true)

        assertThat(state.toggleMount(false)).isFalse()
        assertThat(cancels).containsExactly(ReturnValue.YOUAREEXHAUSTED)
    }

    @Test
    fun `toggle without a selected mount opens the outfit window`() {
        val windowRequests = mutableListOf<Unit>()
        val state = PlayerMountState(
            loadedMounts(),
            sendOutfitWindow = { windowRequests.add(Unit) },
            now = { 10_000L },
        )

        assertThat(state.toggleMount(true)).isFalse()
        assertThat(windowRequests).hasSize(1)
    }

    @Test
    fun `toggle of an unowned current mount clears it and opens the outfit window`() {
        val windowRequests = mutableListOf<Unit>()
        val kv = InMemoryMountKeyValueStore()
        val state = PlayerMountState(
            loadedMounts(),
            kv = kv,
            sendOutfitWindow = { windowRequests.add(Unit) },
            now = { 10_000L },
        )
        state.setCurrentMount(1)

        assertThat(state.toggleMount(true)).isFalse()
        assertThat(state.getCurrentMount()).isEqualTo(0)
        assertThat(kv.get(PlayerMountState.LAST_MOUNT_KEY)).isEqualTo(0)
        assertThat(windowRequests).hasSize(1)
    }

    @Test
    fun `toggle of a premium mount without premium sends the premium cancel`() {
        val cancels = mutableListOf<ReturnValue>()
        val mounts = loadedMounts()
        // An access player owns every mount (`hasMount` short-circuit), which is
        // the only way to reach the premium check of `Player::toggleMount`
        // (player.cpp:8024-8027).
        val state = PlayerMountState(
            mounts,
            sendCancelMessage = cancels::add,
            isPremium = { false },
            isAccessPlayer = { true },
            now = { 10_000L },
        )
        state.setCurrentMount(1)

        assertThat(state.toggleMount(true)).isFalse()
        assertThat(cancels).containsExactly(ReturnValue.YOUNEEDPREMIUMACCOUNT)
    }

    @Test
    fun `toggle while wearing an outfit condition sends not possible`() {
        val cancels = mutableListOf<ReturnValue>()
        val mounts = loadedMounts()
        val state = PlayerMountState(
            mounts,
            sendCancelMessage = cancels::add,
            hasOutfitCondition = { true },
            now = { 10_000L },
        )
        state.tameMount(1)
        state.setCurrentMount(1)

        assertThat(state.toggleMount(true)).isFalse()
        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `toggle of an unsupported outfit sends not possible`() {
        val cancels = mutableListOf<ReturnValue>()
        val mounts = loadedMounts()
        val state = PlayerMountState(
            mounts,
            sendCancelMessage = cancels::add,
            outfitSupportsMount = { false },
            now = { 10_000L },
        )

        assertThat(state.toggleMount(true)).isFalse()
        assertThat(cancels).containsExactly(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `toggle inside a protection zone is refused when the config forbids it`() {
        val cancels = mutableListOf<ReturnValue>()
        val mounts = loadedMounts()
        val state = PlayerMountState(
            mounts,
            sendCancelMessage = cancels::add,
            isInProtectionZone = { true },
            toggleMountInPz = false,
            isAccessPlayer = { false },
            now = { 10_000L },
        )
        state.tameMount(1)
        state.setCurrentMount(1)

        assertThat(state.toggleMount(true)).isFalse()
        assertThat(cancels).containsExactly(ReturnValue.ACTIONNOTPERMITTEDINPROTECTIONZONE)
    }

    @Test
    fun `successful toggles send no cancel message and open no window`() {
        val cancels = mutableListOf<ReturnValue>()
        val windowRequests = mutableListOf<Unit>()
        val mounts = loadedMounts()
        val state = PlayerMountState(
            mounts,
            sendCancelMessage = cancels::add,
            sendOutfitWindow = { windowRequests.add(Unit) },
            now = { 10_000L },
        )
        state.tameMount(1)
        state.setCurrentMount(1)

        assertThat(state.toggleMount(true)).isTrue()
        assertThat(cancels).isEmpty()
        assertThat(windowRequests).isEmpty()
    }

    @Test
    fun `random mount changes keep the mounted state until dismounted`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, now = { 10_000L })
        state.tameMount(1)
        state.setCurrentMount(1)
        state.toggleMount(true)

        val runtime = MountOutfitRuntime(mounts, state)
        runtime.playerChangeOutfit(
            outfit = Outfit(lookType = 128, lookMount = 0),
            setMount = false,
            isMountRandomized = 0,
        )

        assertThat(state.isMounted()).isFalse()
    }
}
