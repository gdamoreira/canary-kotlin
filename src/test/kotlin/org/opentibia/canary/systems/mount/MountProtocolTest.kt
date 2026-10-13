package org.opentibia.canary.systems.mount

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import java.nio.file.Files
import java.nio.file.Path

/**
 * Transcript of the mount branches of `Game::resolveRandomMountClientId`
 * (src/game/game.cpp:10041-10044), `Game::playerChangeOutfit`
 * (src/game/game.cpp:7237-7312 override) and the mount list/current-mount
 * computation of `ProtocolGame::sendOutfitWindow`
 * (src/server/network/protocol/protocolgame.cpp:9027-9258).
 *
 * The Canary C++ regression test `tests/unit/game/random_mount_test.cpp` is
 * transcribed here.
 */
class MountProtocolTest {

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

    // `Game::resolveRandomMountClientId` (game.cpp:10041-10044).

    @Test
    fun `invalid random mount id resolves to no mount`() {
        val mounts = loadedMounts()

        assertThat(MountProtocol.resolveRandomMountClientId(mounts, 9999)).isEqualTo(0)
        assertThat(MountProtocol.resolveRandomMountClientId(mounts, 0)).isEqualTo(0)
    }

    @Test
    fun `valid random mount id resolves to the client id`() {
        val mounts = loadedMounts()

        assertThat(MountProtocol.resolveRandomMountClientId(mounts, 1)).isEqualTo(368)
        assertThat(MountProtocol.resolveRandomMountClientId(mounts, 236)).isEqualTo(1866)
    }

    // `Game::playerChangeOutfit` mount branch (game.cpp:7237-7302).

    @Test
    fun `player change outfit without mount clears mounted and random state`() {
        val mounts = loadedMounts()
        val deltas = mutableListOf<Int>()
        val state = PlayerMountState(mounts, changeSpeed = { deltas += it }, now = { 10_000L })

        state.tameMount(1)
        state.setCurrentMount(1)
        state.toggleMount(true)
        state.setRandomMount(1)
        assertThat(state.isMounted()).isTrue()

        val runtime = MountOutfitRuntime(mounts, state, changeSpeed = { deltas += it })
        val change = runtime.playerChangeOutfit(
            outfit = Outfit(lookType = 128, lookMount = 368),
            setMount = false,
            isMountRandomized = 1,
        )

        assertThat(change.lookMount).isEqualTo(0)
        assertThat(change.isMountRandomized).isEqualTo(0)
        assertThat(state.isMounted()).isFalse()
        assertThat(state.isRandomMounted()).isFalse()
        assertThat(deltas).containsExactly(10, -10)
    }

    @Test
    fun `random mounted outfit changes resolve a client id from the owned mounts`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, uniformRandom = { min, _ -> min }, now = { 10_000L })
        state.tameMount(1)
        state.tameMount(2)
        state.setRandomMount(1)

        val runtime = MountOutfitRuntime(mounts, state)
        val change = runtime.playerChangeOutfit(
            outfit = Outfit(lookType = 128, lookMount = 1),
            setMount = true,
            isMountRandomized = 1,
        )

        // The uniform draw is pinned to the first owned mount for determinism.
        assertThat(change.lookMount).isEqualTo(368)
        assertThat(change.isMountRandomized).isEqualTo(1)
        assertThat(state.getCurrentMount()).isEqualTo(1)
        assertThat(state.isRandomMounted()).isTrue()
    }

    @Test
    fun `random resolution to an unknown mount clears the random state`() {
        val owned = Mounts().apply {
            loadFromXml("<mounts><mount id=\"1\" clientid=\"368\" name=\"Widow Queen\" speed=\"10\" premium=\"no\" type=\"quest\"/></mounts>")
        }
        val runtimeMounts = Mounts().apply {
            loadFromXml("<mounts><mount id=\"9\" clientid=\"999\" name=\"Other\" speed=\"10\" premium=\"no\" type=\"quest\"/></mounts>")
        }
        val state = PlayerMountState(owned, now = { 10_000L })
        state.tameMount(1)
        state.setRandomMount(1)

        val runtime = MountOutfitRuntime(runtimeMounts, state)
        val change = runtime.playerChangeOutfit(
            outfit = Outfit(lookType = 128, lookMount = 1),
            setMount = true,
            isMountRandomized = 1,
        )

        assertThat(change.lookMount).isEqualTo(0)
        assertThat(change.isMountRandomized).isEqualTo(0)
        assertThat(state.isRandomMounted()).isFalse()
    }

    @Test
    fun `changing to a different owned mount applies the speed delta`() {
        val mounts = Mounts().apply {
            loadFromXml(
                """
                <mounts>
                  <mount id="1" clientid="368" name="Slow" speed="10" premium="no" type="quest"/>
                  <mount id="2" clientid="369" name="Fast" speed="30" premium="no" type="quest"/>
                </mounts>
                """.trimIndent(),
            )
        }
        val deltas = mutableListOf<Int>()
        val state = PlayerMountState(mounts, changeSpeed = { deltas += it }, now = { 10_000L })
        state.tameMount(1)
        state.tameMount(2)
        state.setCurrentMount(1)
        state.toggleMount(true)
        assertThat(state.isMounted()).isTrue()

        val runtime = MountOutfitRuntime(mounts, state, changeSpeed = { deltas += it })
        val change = runtime.playerChangeOutfit(
            outfit = Outfit(lookType = 128, lookMount = 369),
            setMount = true,
            isMountRandomized = 0,
        )

        assertThat(change.lookMount).isEqualTo(369)
        assertThat(deltas).containsExactly(10, 20)
        assertThat(state.getCurrentMount()).isEqualTo(2)
        assertThat(state.isMounted()).isTrue()
    }

    @Test
    fun `unowned client ids abort the outfit change`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, now = { 10_000L })

        val runtime = MountOutfitRuntime(mounts, state)
        val change = runtime.playerChangeOutfit(
            outfit = Outfit(lookType = 128, lookMount = 368),
            setMount = true,
            isMountRandomized = 0,
        )

        assertThat(change.lookMount).isNull()
        assertThat(state.getCurrentMount()).isEqualTo(0)
    }

    @Test
    fun `protection zone blocking clears the mount without aborting the change`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, now = { 10_000L })
        state.tameMount(1)
        state.tameMount(2)
        state.setCurrentMount(1)
        state.toggleMount(true)

        val runtime = MountOutfitRuntime(
            mounts,
            state,
            isInProtectionZone = { true },
            toggleMountInPz = false,
        )
        val change = runtime.playerChangeOutfit(
            outfit = Outfit(lookType = 128, lookMount = 369),
            setMount = true,
            isMountRandomized = 0,
        )

        assertThat(change.lookMount).isEqualTo(0)
        assertThat(state.getCurrentMount()).isEqualTo(2)
        assertThat(state.isMounted()).isTrue()
    }

    @Test
    fun `outfits that do not support mounting clear the mount`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, now = { 10_000L })

        val runtime = MountOutfitRuntime(mounts, state, outfitSupportsMount = { false })
        val change = runtime.playerChangeOutfit(
            outfit = Outfit(lookType = 128, lookMount = 368),
            setMount = true,
            isMountRandomized = 1,
        )

        assertThat(change.lookMount).isEqualTo(0)
        assertThat(change.isMountRandomized).isEqualTo(0)
        assertThat(state.isRandomMounted()).isFalse()
        assertThat(state.getCurrentMount()).isEqualTo(0)
    }

    @Test
    fun `support outfits clear the mount and the random state`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, now = { 10_000L })
        state.tameMount(1)
        state.setCurrentMount(1)
        state.toggleMount(true)

        val runtime = MountOutfitRuntime(mounts, state, isWearingSupportOutfit = { true })
        val change = runtime.playerChangeOutfit(
            outfit = Outfit(lookType = 75, lookMount = 368),
            setMount = true,
            isMountRandomized = 1,
        )

        assertThat(change.lookMount).isEqualTo(0)
        assertThat(change.isMountRandomized).isEqualTo(0)
        assertThat(state.isMounted()).isFalse()
    }

    // Mount list of `ProtocolGame::sendOutfitWindow` (protocolgame.cpp:9193-9221).

    @Test
    fun `outfit window lists owned mounts as free entries`() {
        val mounts = loadedMounts()

        val entries = MountProtocol.buildOutfitWindowMountList(mounts) { it.id == 1 }

        val owned = entries.filter { it.flag == 0x00 }
        assertThat(owned).hasSize(1)
        assertThat(owned.single().clientId).isEqualTo(368)
        assertThat(owned.single().name).isEqualTo("Widow Queen")
        assertThat(owned.single().price).isNull()
    }

    @Test
    fun `outfit window lists unowned store mounts as purchasable entries`() {
        val mounts = loadedMounts()
        val storeCount = mounts.getMounts().count { it.type == "store" }

        val entries = MountProtocol.buildOutfitWindowMountList(mounts) { false }

        assertThat(entries).hasSize(storeCount)
        assertThat(entries).allSatisfy { entry ->
            assertThat(entry.flag).isEqualTo(0x01)
            assertThat(entry.price).isEqualTo(0)
        }
    }

    @Test
    fun `outfit window owns a store mount once and hides unowned quest mounts`() {
        val mounts = loadedMounts()
        val storeCount = mounts.getMounts().count { it.type == "store" }

        val entries = MountProtocol.buildOutfitWindowMountList(mounts) { it.id == 23 }

        assertThat(entries).hasSize(storeCount)
        val armoured = entries.single { it.clientId == 426 }
        assertThat(armoured.flag).isEqualTo(0x00)
        assertThat(armoured.price).isNull()
    }

    // Current-mount computation of `ProtocolGame::sendOutfitWindow` (protocolgame.cpp:9031-9044).

    @Test
    fun `outfit window marks the last mount as mounted when the outfit supports it`() {
        val mounts = loadedMounts()

        val windowMount = MountProtocol.resolveOutfitWindowMount(
            mounts = mounts,
            lastMount = 1,
            currentLookMount = 368,
            supportOutfit = false,
            outfitSupportsMount = true,
        )

        assertThat(windowMount.lookMount).isEqualTo(368)
        assertThat(windowMount.mounted).isTrue()
    }

    @Test
    fun `outfit window shows the last mount unmounted when the outfit is bare`() {
        val mounts = loadedMounts()

        val windowMount = MountProtocol.resolveOutfitWindowMount(
            mounts = mounts,
            lastMount = 1,
            currentLookMount = 0,
            supportOutfit = false,
            outfitSupportsMount = true,
        )

        assertThat(windowMount.lookMount).isEqualTo(368)
        assertThat(windowMount.mounted).isFalse()
    }

    @Test
    fun `outfit window hides mounts for support outfits and unsupported outfits`() {
        val mounts = loadedMounts()

        assertThat(
            MountProtocol.resolveOutfitWindowMount(mounts, 1, 368, supportOutfit = true, outfitSupportsMount = true),
        ).isEqualTo(MountProtocol.OutfitWindowMount(lookMount = 0, mounted = false))

        assertThat(
            MountProtocol.resolveOutfitWindowMount(mounts, 1, 368, supportOutfit = false, outfitSupportsMount = false),
        ).isEqualTo(MountProtocol.OutfitWindowMount(lookMount = 0, mounted = false))
    }

    @Test
    fun `outfit window keeps the requested mount when the last mount vanished`() {
        val mounts = loadedMounts()

        val windowMount = MountProtocol.resolveOutfitWindowMount(
            mounts = mounts,
            lastMount = 0,
            currentLookMount = 55,
            supportOutfit = false,
            outfitSupportsMount = true,
        )

        assertThat(windowMount.lookMount).isEqualTo(55)
        assertThat(windowMount.mounted).isFalse()
    }
}
