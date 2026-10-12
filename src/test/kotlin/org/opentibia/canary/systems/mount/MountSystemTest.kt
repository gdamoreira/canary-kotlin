package org.opentibia.canary.systems.mount

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.nio.file.Files
import java.nio.file.Path

class MountSystemTest {

    private fun realMountsXml(): String {
        val candidates = listOf(
            Path.of("data/XML/mounts.xml"),
            Path.of("../data/XML/mounts.xml"),
            Path.of("../../data/XML/mounts.xml")
        )
        val path = candidates.firstOrNull { Files.exists(it) }
            ?: error("data/XML/mounts.xml not found from ${System.getProperty("user.dir")}")
        return Files.readString(path)
    }

    private fun loadedMounts(): Mounts = Mounts().apply {
        assertThat(loadFromXml(realMountsXml())).isTrue()
    }

    @Test
    fun `loads every mount from the real data XML with exact speed`() {
        val mounts = loadedMounts()
        assertThat(mounts.getMounts()).hasSize(236)

        val widowQueen = mounts.getMountByID(1)
        assertThat(widowQueen).isNotNull
        assertThat(widowQueen!!.name).isEqualTo("Widow Queen")
        assertThat(widowQueen.clientId).isEqualTo(368)
        assertThat(widowQueen.speed).isEqualTo(10)
        assertThat(widowQueen.premium).isTrue()
        assertThat(widowQueen.type).isEqualTo("quest")

        val rentedHorse = mounts.getMountByID(22)
        assertThat(rentedHorse!!.name).isEqualTo("Rented Horse")
        assertThat(rentedHorse.premium).isFalse()

        val gloomMaw = mounts.getMountByID(236)
        assertThat(gloomMaw!!.name).isEqualTo("Gloom Maw")
        assertThat(gloomMaw.clientId).isEqualTo(1866)
    }

    @Test
    fun `resolves mounts by id name and client id`() {
        val mounts = loadedMounts()

        assertThat(mounts.getMountByName("widow queen")?.id).isEqualTo(1)
        assertThat(mounts.getMountByName("WIDOW QUEEN")?.id).isEqualTo(1)
        assertThat(mounts.getMountByClientID(368)?.id).isEqualTo(1)
        assertThat(mounts.getMountByID(9999)).isNull()
        assertThat(mounts.getMountByName("No Such Mount")).isNull()
        assertThat(mounts.getMountByClientID(65535)).isNull()
    }

    @Test
    fun `reload clears the previous registry before loading`() {
        val mounts = Mounts()
        assertThat(mounts.loadFromXml("<mounts><mount id=\"1\" clientid=\"1\" name=\"Solo\" speed=\"5\" premium=\"no\" type=\"store\"/></mounts>")).isTrue()
        assertThat(mounts.getMounts()).hasSize(1)

        assertThat(mounts.reload(realMountsXml())).isTrue()
        assertThat(mounts.getMounts()).hasSize(236)
        assertThat(mounts.getMountByID(1)?.name).isEqualTo("Widow Queen")
    }

    @Test
    fun `tame and untame maintain the mount bit storage`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts)
        val widowQueen = mounts.getMountByID(1)!!

        assertThat(state.hasMount(widowQueen)).isFalse()
        assertThat(state.hasAnyMount()).isFalse()

        assertThat(state.tameMount(1)).isTrue()
        assertThat(state.hasMount(widowQueen)).isTrue()
        assertThat(state.hasAnyMount()).isTrue()

        assertThat(state.untameMount(1)).isTrue()
        assertThat(state.hasMount(widowQueen)).isFalse()
        assertThat(state.hasAnyMount()).isFalse()

        assertThat(state.tameMount(9999)).isFalse()
        assertThat(state.untameMount(9999)).isFalse()
    }

    @Test
    fun `premium mounts require premium while access players own everything`() {
        val mounts = loadedMounts()
        val premiumMount = mounts.getMountByID(1)!! // premium="yes"
        val freeMount = mounts.getMountByID(22)!! // premium="no"

        val freePlayer = PlayerMountState(mounts, isPremium = { false })
        assertThat(freePlayer.tameMount(1)).isTrue()
        assertThat(freePlayer.tameMount(22)).isTrue()
        assertThat(freePlayer.hasMount(premiumMount)).isFalse()
        assertThat(freePlayer.hasMount(freeMount)).isTrue()

        val accessPlayer = PlayerMountState(mounts, isAccessPlayer = { true })
        assertThat(accessPlayer.hasMount(premiumMount)).isTrue()
    }

    @Test
    fun `toggle applies the mount speed and dismount restores base speed`() {
        val mounts = loadedMounts()
        val deltas = mutableListOf<Int>()
        var fakeNow = 10_000L
        val state = PlayerMountState(mounts, changeSpeed = { deltas += it }, now = { fakeNow })

        state.tameMount(1)
        state.setCurrentMount(1)
        assertThat(state.getCurrentMount()).isEqualTo(1)

        assertThat(state.toggleMount(true)).isTrue()
        assertThat(state.isMounted()).isTrue()
        assertThat(state.getCurrentMount()).isEqualTo(1)
        assertThat(deltas).containsExactly(10)

        fakeNow += 3001
        assertThat(state.toggleMount(false)).isTrue()
        assertThat(state.isMounted()).isFalse()
        assertThat(deltas).containsExactly(10, -10)
    }

    @Test
    fun `toggle is exhausted inside the three second window`() {
        val mounts = loadedMounts()
        var fakeNow = 10_000L
        val state = PlayerMountState(mounts, now = { fakeNow })

        state.tameMount(1)
        state.setCurrentMount(1)

        assertThat(state.toggleMount(true)).isTrue()
        assertThat(state.toggleMount(false)).isFalse()
        assertThat(state.isMounted()).isTrue()
    }

    @Test
    fun `toggle refuses an unowned current mount and clears it`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, now = { 10_000L })

        state.setCurrentMount(1) // never tamed
        assertThat(state.toggleMount(true)).isFalse()
        assertThat(state.isMounted()).isFalse()
        assertThat(state.getCurrentMount()).isEqualTo(0)
    }

    @Test
    fun `toggle refuses when no mount was ever selected`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, now = { 10_000L })
        assertThat(state.toggleMount(true)).isFalse()
        assertThat(state.isMounted()).isFalse()
    }

    @Test
    fun `untaming the active mount dismounts and clears selection`() {
        val mounts = loadedMounts()
        val deltas = mutableListOf<Int>()
        var fakeNow = 10_000L
        val state = PlayerMountState(mounts, changeSpeed = { deltas += it }, now = { fakeNow })

        state.tameMount(1)
        state.setCurrentMount(1)
        state.toggleMount(true)
        assertThat(state.isMounted()).isTrue()

        fakeNow += 3001
        assertThat(state.untameMount(1)).isTrue()
        assertThat(state.isMounted()).isFalse()
        assertThat(state.getCurrentMount()).isEqualTo(0)
        assertThat(deltas).containsExactly(10, -10)
    }

    @Test
    fun `random mount id is zero when nothing is owned`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts)
        assertThat(state.getRandomMountId()).isEqualTo(0)
    }

    @Test
    fun `random mount id picks from owned mounts`() {
        val mounts = loadedMounts()
        val state = PlayerMountState(mounts, uniformRandom = { min, _ -> min })

        state.tameMount(1)
        state.tameMount(2)
        state.tameMount(3)

        assertThat(state.getRandomMountId()).isEqualTo(1)
        assertThat(state.hasAnyMount()).isTrue()
    }
}
