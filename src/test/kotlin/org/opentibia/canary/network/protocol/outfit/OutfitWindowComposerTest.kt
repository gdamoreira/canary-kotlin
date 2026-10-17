package org.opentibia.canary.network.protocol.outfit

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.appearance.effects.AttachedEffects
import org.opentibia.canary.creatures.appearance.outfit.Outfits
import org.opentibia.canary.creatures.grouping.Group
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.familiar.Familiars
import org.opentibia.canary.systems.mount.Mounts

/**
 * `ProtocolGame::sendOutfitWindow` composition (protocolgame.cpp:9027-9258)
 * over the booted registries: current outfit + mount/attached-effect look-ups,
 * the owned/premium/store outfit rows, the owned/store mount rows, the
 * vocation familiars and the old-protocol payload.
 */
class OutfitWindowComposerTest {

    private val outfitsXml = """
        <outfits>
            <outfit type="0" looktype="136" name="Citizen" premium="0" unlocked="1"/>
            <outfit type="0" looktype="137" name="Hunter" premium="0" unlocked="0"/>
            <outfit type="0" looktype="1000" name="Store Outfit" premium="0" unlocked="0" from="store"/>
        </outfits>
    """.trimIndent()

    private val mountsXml = """
        <mounts>
            <mount id="1" clientid="368" name="Widow Queen" speed="10" premium="0" type="store"/>
            <mount id="2" clientid="369" name="Racing Bird" speed="10" premium="0" type="quest"/>
        </mounts>
    """.trimIndent()

    private val familiarsXml = """
        <familiars>
            <familiar lookType="10" type="1" unlocked="0" premium="0" vocation="1" name="Wolf"/>
        </familiars>
    """.trimIndent()

    private fun deps(
        enableSupportOutfit: Boolean = true,
        outfitSupportsMount: (Int) -> Boolean = { true },
    ): OutfitWindowComposer.Deps {
        val outfits = Outfits().also { it.loadFromXml(outfitsXml) }
        val mounts = Mounts().also { it.loadFromXml(mountsXml) }
        val familiars = Familiars().also { it.loadFromXml(familiarsXml) }
        return OutfitWindowComposer.Deps(
            outfits = outfits,
            mounts = mounts,
            familiars = familiars,
            attachedEffects = AttachedEffects(),
            outfitSupportsMount = outfitSupportsMount,
            enableSupportOutfit = enableSupportOutfit,
        )
    }

    private fun hero(
        id: Long,
        deps: OutfitWindowComposer.Deps,
        access: Boolean = false,
    ): Player = Player(
        id = id,
        name = "Hero",
        maxHealth = 150,
        health = 150,
        position = Position(100, 100, 7),
        mountDefinitions = deps.mounts,
        attachedOutfits = deps.outfits,
        familiarDefinitions = deps.familiars,
    ).also { player ->
        if (access) {
            player.group = Group(id = 6, access = true)
        }
    }

    @Test
    fun `composes the current-profile frame with owned, locked and store outfits`() {
        val deps = deps()
        val player = hero(1, deps)
        player.features.outfits.addOutfit(136, 3)

        val payload = OutfitWindowComposer.compose(player, deps) as OutfitWindowComposer.CurrentPayload

        assertThat(payload.frame.currentOutfit.lookType).isEqualTo(0)
        assertThat(payload.frame.outfits.map { it.name })
            .containsExactly("Citizen", "Store Outfit")
        assertThat(payload.frame.outfits.first().addons).isEqualTo(3)
        assertThat(payload.frame.outfits.last().type).isEqualTo(0x01)
        assertThat(payload.frame.familiars).isEmpty()
        // The unowned store mount still lists with the store flag.
        assertThat(payload.frame.mounts.map { it.name }).containsExactly("Widow Queen")
        assertThat(payload.frame.mounts.single().flag).isEqualTo(0x01)
        assertThat(payload.frame.mounted).isFalse()
        assertThat(payload.frame.randomMounted).isFalse()
        assertThat(payload.frame.isOTCR).isFalse()
    }

    @Test
    fun `an access player receives the three support outfits first`() {
        val deps = deps()
        val player = hero(2, deps, access = true)

        val payload = OutfitWindowComposer.compose(player, deps) as OutfitWindowComposer.CurrentPayload

        assertThat(payload.frame.outfits.take(3).map { it.name })
            .containsExactly("Gamemaster", "Customer Support", "Community Manager")
        assertThat(payload.frame.outfits.take(3).map { it.lookType }).containsExactly(75, 266, 302)
    }

    @Test
    fun `a disabled support outfit config drops the support rows`() {
        val deps = deps(enableSupportOutfit = false)
        val player = hero(3, deps, access = true)

        val payload = OutfitWindowComposer.compose(player, deps) as OutfitWindowComposer.CurrentPayload

        assertThat(payload.frame.outfits).noneMatch { it.name == "Gamemaster" }
    }

    @Test
    fun `an owned mount resolves the current mount client id and the mounted flag`() {
        val deps = deps()
        val player = hero(4, deps)
        assertThat(player.features.mounts.tameMount(1)).isTrue()
        player.features.mounts.setCurrentMount(1)
        player.setDefaultOutfit(Outfit(lookMount = 368))

        val payload = OutfitWindowComposer.compose(player, deps) as OutfitWindowComposer.CurrentPayload

        assertThat(payload.frame.currentOutfit.lookMount).isEqualTo(368)
        assertThat(payload.frame.mounted).isTrue()
        // The owned mount lists; the unowned quest mount stays hidden.
        assertThat(payload.frame.mounts.map { it.clientId }).containsExactly(368)
        assertThat(payload.frame.mounts.single().flag).isEqualTo(0x00)
    }

    @Test
    fun `the stored familiar is listed when the vocation matches`() {
        val deps = deps()
        val player = hero(5, deps)
        player.vocationId = 1
        player.features.familiars.addFamiliar(10)

        val payload = OutfitWindowComposer.compose(player, deps) as OutfitWindowComposer.CurrentPayload

        assertThat(payload.frame.familiars).hasSize(1)
        assertThat(payload.frame.familiars.single().lookType).isEqualTo(10)
        assertThat(payload.frame.familiars.single().name).isEqualTo("Wolf")
    }

    @Test
    fun `an unsupported mount suppresses the mount variant`() {
        val deps = deps(outfitSupportsMount = { false })
        val player = hero(6, deps)

        val payload = OutfitWindowComposer.compose(player, deps) as OutfitWindowComposer.CurrentPayload

        assertThat(payload.frame.supportOutfitOrNoMount).isTrue()
        assertThat(payload.frame.currentOutfit.lookMount).isEqualTo(0)
    }

    @Test
    fun `the legacy payload carries the owned outfits and mounts only`() {
        val deps = deps()
        val player = hero(7, deps)
        player.features.outfits.addOutfit(136, 1)
        assertThat(player.features.mounts.tameMount(2)).isTrue()

        val payload = OutfitWindowComposer.compose(player, deps, legacy = true) as OutfitWindowComposer.LegacyPayload

        assertThat(payload.outfits.map { it.lookType }).containsExactly(136)
        assertThat(payload.outfits.single().addons).isEqualTo(1)
        assertThat(payload.mounts.map { it.clientId }).containsExactly(369)
        assertThat(payload.isOTCR).isFalse()
        assertThat(payload.customOtcr).isNull()
    }

    @Test
    fun `the attached effects decorate the current outfit`() {
        val deps = deps()
        val player = hero(8, deps)
        // The empty registry has no wing/aura/effect/shader entries, so the
        // current-outfit look fields stay untouched exactly like the C++ null
        // look-ups.
        val payload = OutfitWindowComposer.compose(player, deps) as OutfitWindowComposer.CurrentPayload

        assertThat(payload.frame.currentOutfit.lookWing).isEqualTo(0)
        assertThat(payload.frame.currentOutfit.lookAura).isEqualTo(0)
    }

}
