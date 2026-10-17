package org.opentibia.canary.network.protocol.outfit

import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.appearance.effects.AttachedEffects
import org.opentibia.canary.creatures.appearance.outfit.Outfits
import org.opentibia.canary.creatures.appearance.outfit.ProtocolOutfit
import org.opentibia.canary.systems.familiar.Familiars
import org.opentibia.canary.systems.mount.MountProtocol
import org.opentibia.canary.systems.mount.Mounts

/**
 * Composes the `ProtocolGame::sendOutfitWindow` payload (protocolgame.cpp:
 * 9027-9258) from the live player and the booted registries.
 *
 * The method's registry reads (`Outfits`, `Mounts`, `Familiars`,
 * `AttachedEffects`, `Game::outfitSupportsMount`) and the `g_configManager()`
 * `ENABLE_SUPPORT_OUTFIT` flag are injected through [Deps] per AGENTS.md 1.1;
 * the player reads (default outfit, support outfit, owned outfits/mounts/
 * familiars, attached effects, sex, vocation) stay on the player.
 */
object OutfitWindowComposer {

    /** The `sendOutfitWindow` registry/config reads the C++ method resolves through `g_game()`. */
    data class Deps(
        /** `Outfits::getInstance()` behind `getOutfits(player->getSex())`. */
        val outfits: Outfits,
        /** `g_game().mounts` behind `getMountByID`/`getMounts`. */
        val mounts: Mounts,
        /** `Familiars::getInstance()` behind `getFamiliars(vocationId)`. */
        val familiars: Familiars,
        /** `g_game().getAttachedEffects()` behind the wing/aura/effect/shader look-ups. */
        val attachedEffects: AttachedEffects,
        /** `Game::outfitSupportsMount` (game.cpp:10071). */
        val outfitSupportsMount: (lookType: Int) -> Boolean = { true },
        /** `g_configManager().getBoolean(ENABLE_SUPPORT_OUTFIT)` (protocolgame.cpp:9128). */
        val enableSupportOutfit: Boolean = true,
    )

    /** The composed `0xC8` current-profile payload. */
    data class CurrentPayload(
        val frame: OutfitWindowWriter.OutfitWindowFrame,
    ) : OutfitWindowPayload

    /** The composed old-protocol payload (protocolgame.cpp:9069-9112). */
    data class LegacyPayload(
        val currentOutfit: Outfit,
        val outfits: List<ProtocolOutfit>,
        val mounts: List<OutfitWindowWriter.ProtocolOutfitMount>,
        val isOTCR: Boolean = false,
        val customOtcr: OutfitWindowWriter.OutfitWindowCustomOtcrFrame? = null,
    ) : OutfitWindowPayload

    /** `sendOutfitWindow` (protocolgame.cpp:9027-9258); [legacy] selects the old-protocol layout. */
    fun compose(player: Player, deps: Deps, legacy: Boolean = false): OutfitWindowPayload {
        val currentOutfit = player.getDefaultOutfit()
        val isSupportOutfit = player.isWearingSupportOutfit()
        val supportsMount = deps.outfitSupportsMount(currentOutfit.lookType)
        var mounted = false

        if (!isSupportOutfit && supportsMount) {
            val currentMount = deps.mounts.getMountByID(player.features.mounts.getLastMount())
            if (currentMount != null) {
                mounted = currentOutfit.lookMount == currentMount.clientId
                currentOutfit.lookMount = currentMount.clientId
            }
        } else {
            currentOutfit.lookMount = 0
        }

        val attached = player.features.attachedEffects
        deps.attachedEffects.getWingByID(attached.getCurrentWing())?.let { currentOutfit.lookWing = it.id }
        deps.attachedEffects.getAuraByID(attached.getCurrentAura())?.let { currentOutfit.lookAura = it.id }
        deps.attachedEffects.getEffectByID(attached.getCurrentEffect())?.let { currentOutfit.lookEffect = it.id }
        deps.attachedEffects.getShaderByID(attached.getCurrentShader())?.let { currentOutfit.lookShader = it.id }

        val source = OutfitWindowWriter.OutfitWindowSource(
            outfits = deps.outfits.getOutfits(player.getSex()),
            getOutfitAddons = { player.features.outfits.getOutfitAddons(it) },
            canWear = { lookType, addons -> player.features.outfits.canWear(lookType, addons) },
            isAccessPlayer = player.isAccessPlayer(),
            enableSupportOutfit = deps.enableSupportOutfit,
        )

        if (legacy) {
            val mounts = mutableListOf<OutfitWindowWriter.ProtocolOutfitMount>()
            for (mount in deps.mounts.getMounts()) {
                if (player.features.mounts.hasMount(mount)) {
                    mounts.add(OutfitWindowWriter.ProtocolOutfitMount(clientId = mount.clientId, name = mount.name))
                }
            }
            return LegacyPayload(
                currentOutfit = currentOutfit,
                outfits = OutfitWindowWriter.buildLegacyOutfitEntries(source),
                mounts = mounts,
            )
        }

        return CurrentPayload(
            frame = OutfitWindowWriter.OutfitWindowFrame(
                currentOutfit = currentOutfit,
                supportOutfitOrNoMount = isSupportOutfit || !supportsMount,
                mounted = mounted,
                randomMounted = player.features.mounts.isRandomMounted(),
                outfits = OutfitWindowWriter.buildOutfitEntries(source),
                mounts = MountProtocol.buildOutfitWindowMountList(deps.mounts) { mount ->
                    player.features.mounts.hasMount(mount)
                },
                familiars = OutfitWindowWriter.buildFamiliarEntries(
                    familiars = deps.familiars.getFamiliars(player.getVocationId()),
                    getFamiliar = { familiar -> player.features.familiars.getFamiliar(familiar) },
                ),
            ),
        )
    }
}

/** The composed outfit-window payload union. */
sealed interface OutfitWindowPayload
