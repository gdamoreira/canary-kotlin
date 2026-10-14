package org.opentibia.canary.network.protocol.outfit

import io.netty.buffer.ByteBuf
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.appearance.effects.AttachedEffect
import org.opentibia.canary.creatures.appearance.outfit.Outfit as OutfitDefinition
import org.opentibia.canary.creatures.appearance.outfit.ProtocolOutfit
import org.opentibia.canary.network.message.writeTibiaString
import org.opentibia.canary.network.protocol.GameWindowWriter
import org.opentibia.canary.systems.mount.MountProtocol

/**
 * Outbound outfit window serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendOutfitWindow`          (0xC8, protocolgame.cpp:9027-9258)
 *  - `ProtocolGame::sendOutfitWindowCustomOTCR`(no opcode, protocolgame.cpp:11880-11956)
 *
 * The `!player` guards stay with the caller. The current outfit (with its
 * mount, wing, aura, effect and shader values already resolved by the caller)
 * and the mount/support flags arrive through [OutfitWindowFrame]; the mount
 * rows reuse [MountProtocol.OutfitWindowMountEntry]. [buildOutfitEntries]
 * transcribes the C++ registry loop (support outfits, owned addons, the
 * 1210/1211 and 1456/1457 store branches) and [buildLegacyOutfitEntries] the
 * old-protocol `ProtocolOutfit` loop with its 150-entry cap. The OTCR custom
 * block is written by [writeOutfitWindowCustomOTCR], gated on `isOTCR` exactly
 * like the C++ method. Every multi-byte field is Little-Endian.
 */
object OutfitWindowWriter {

    const val PACKET_OUTFIT_WINDOW: Int = 0xC8

    /** `sendOutfitWindow` support outfit look types (protocolgame.cpp:9129-9145). */
    private const val SUPPORT_LOOK_TYPE_GAMEMASTER = 75
    private const val SUPPORT_LOOK_TYPE_CUSTOMER_SUPPORT = 266
    private const val SUPPORT_LOOK_TYPE_COMMUNITY_MANAGER = 302

    /** `sendOutfitWindow` store addon exception range (protocolgame.cpp:9177). */
    private const val STORE_LOOK_TYPE_RANGE_START = 962
    private const val STORE_LOOK_TYPE_RANGE_END = 975

    /** `ProtocolOutfit` cap for old-protocol clients (protocolgame.cpp:9082). */
    private const val LEGACY_OUTFIT_LIMIT = 150

    /** One resolved outfit row (`sendOutfitWindow` current profile). */
    data class OutfitWindowOutfitEntry(
        val lookType: Int,
        val name: String,
        val addons: Int,
        val type: Int,
        val storePrice: Long? = null,
    )

    /** One resolved familiar row of the outfit window. */
    data class OutfitWindowFamiliarEntry(
        val lookType: Int,
        val name: String,
    )

    /** One old-protocol mount row (`sendOutfitWindow` `mounts` list). */
    data class ProtocolOutfitMount(
        val clientId: Int,
        val name: String,
    )

    /** The `sendOutfitWindowCustomOTCR` wings/auras/effects/shaders lists. */
    data class OutfitWindowCustomOtcrFrame(
        val wings: List<AttachedEffect> = emptyList(),
        val auras: List<AttachedEffect> = emptyList(),
        val effects: List<AttachedEffect> = emptyList(),
        val shaders: List<AttachedEffect> = emptyList(),
    )

    /** The 0xC8 current-profile outfit window payload. */
    data class OutfitWindowFrame(
        val currentOutfit: Outfit,
        val supportOutfitOrNoMount: Boolean,
        val mounted: Boolean,
        val randomMounted: Boolean,
        val outfits: List<OutfitWindowOutfitEntry>,
        val mounts: List<MountProtocol.OutfitWindowMountEntry>,
        val familiars: List<OutfitWindowFamiliarEntry>,
        val isOTCR: Boolean = false,
        val customOtcr: OutfitWindowCustomOtcrFrame? = null,
    )

    /**
     * The live player/registry reads of the `sendOutfitWindow` outfit loop:
     * [outfits] is `Outfits::getInstance().getOutfits(sex)`, [getOutfitAddons]
     * is `Player::getOutfitAddons` (`null` when the outfit is not owned),
     * [canWear] is `Player::canWear`, and the two flags are
     * `Player::isAccessPlayer()` plus the `ENABLE_SUPPORT_OUTFIT` config.
     */
    data class OutfitWindowSource(
        val outfits: List<OutfitDefinition>,
        val getOutfitAddons: (OutfitDefinition) -> Int?,
        val canWear: (Int, Int) -> Boolean,
        val isAccessPlayer: Boolean,
        val enableSupportOutfit: Boolean,
    )

    /** `sendOutfitWindow` current-profile layout (protocolgame.cpp:9114-9257). */
    fun writeOutfitWindow(out: ByteBuf, frame: OutfitWindowFrame) {
        out.writeByte(PACKET_OUTFIT_WINDOW)

        val currentOutfit = frame.currentOutfit
        GameWindowWriter.writeOutfit(out, currentOutfit, addMount = true)

        if (currentOutfit.lookMount == 0) {
            out.writeByte(if (frame.supportOutfitOrNoMount) 0 else currentOutfit.lookMountHead)
            out.writeByte(if (frame.supportOutfitOrNoMount) 0 else currentOutfit.lookMountBody)
            out.writeByte(if (frame.supportOutfitOrNoMount) 0 else currentOutfit.lookMountLegs)
            out.writeByte(if (frame.supportOutfitOrNoMount) 0 else currentOutfit.lookMountFeet)
        }
        out.writeShortLE(currentOutfit.lookFamiliarsType)

        out.writeShortLE(frame.outfits.size)
        for (outfit in frame.outfits) {
            out.writeShortLE(outfit.lookType)
            out.writeTibiaString(outfit.name)
            out.writeByte(outfit.addons)
            out.writeByte(outfit.type)
            outfit.storePrice?.let { out.writeIntLE(it.toInt()) }
        }

        out.writeShortLE(frame.mounts.size)
        for (mount in frame.mounts) {
            out.writeShortLE(mount.clientId)
            out.writeTibiaString(mount.name)
            out.writeByte(mount.flag)
            mount.price?.let { out.writeIntLE(it.toInt()) }
        }

        out.writeShortLE(frame.familiars.size)
        for (familiar in frame.familiars) {
            out.writeShortLE(familiar.lookType)
            out.writeTibiaString(familiar.name)
            out.writeByte(0x00)
        }

        out.writeByte(0x00) // Try outfit
        out.writeByte(if (frame.mounted) 0x01 else 0x00)
        out.writeByte(if (frame.supportOutfitOrNoMount) 0x00 else if (frame.randomMounted) 0x01 else 0x00)

        if (frame.isOTCR) {
            writeCustomOtcrBlock(out, frame.customOtcr)
        }
    }

    /** `sendOutfitWindow` old-protocol layout (protocolgame.cpp:9069-9112). */
    fun writeOutfitWindowLegacy(
        out: ByteBuf,
        currentOutfit: Outfit,
        protocolOutfits: List<ProtocolOutfit>,
        mounts: List<ProtocolOutfitMount>,
        isOTCR: Boolean,
        customOtcr: OutfitWindowCustomOtcrFrame?,
    ) {
        out.writeByte(PACKET_OUTFIT_WINDOW)
        GameWindowWriter.writeOutfit(out, currentOutfit, addMount = true)

        out.writeByte(protocolOutfits.size)
        for (outfit in protocolOutfits) {
            out.writeShortLE(outfit.lookType)
            out.writeTibiaString(outfit.name)
            out.writeByte(outfit.addons)
        }

        out.writeByte(mounts.size)
        for (mount in mounts) {
            out.writeShortLE(mount.clientId)
            out.writeTibiaString(mount.name)
        }

        if (isOTCR) {
            writeCustomOtcrBlock(out, customOtcr)
        }
    }

    /** `sendOutfitWindowCustomOTCR` (protocolgame.cpp:11880-11956). */
    fun writeOutfitWindowCustomOTCR(out: ByteBuf, isOTCR: Boolean, frame: OutfitWindowCustomOtcrFrame?) {
        if (!isOTCR) {
            return
        }

        writeCustomOtcrBlock(out, frame)
    }

    /** The `sendOutfitWindow` outfit loop (protocolgame.cpp:9128-9186). */
    fun buildOutfitEntries(source: OutfitWindowSource): List<OutfitWindowOutfitEntry> {
        val entries = mutableListOf<OutfitWindowOutfitEntry>()

        if (source.isAccessPlayer && source.enableSupportOutfit) {
            entries.add(OutfitWindowOutfitEntry(SUPPORT_LOOK_TYPE_GAMEMASTER, "Gamemaster", 0, 0x00))
            entries.add(OutfitWindowOutfitEntry(SUPPORT_LOOK_TYPE_CUSTOMER_SUPPORT, "Customer Support", 0, 0x00))
            entries.add(OutfitWindowOutfitEntry(SUPPORT_LOOK_TYPE_COMMUNITY_MANAGER, "Community Manager", 0, 0x00))
        }

        val limitOutfits = 0xFFFF
        for (outfit in source.outfits) {
            val addons = source.getOutfitAddons(outfit)
            when {
                addons != null ->
                    entries.add(OutfitWindowOutfitEntry(outfit.lookType, outfit.name, addons, 0x00))

                outfit.lookType == 1210 || outfit.lookType == 1211 -> {
                    if (source.canWear(1210, 0) || source.canWear(1211, 0)) {
                        entries.add(OutfitWindowOutfitEntry(outfit.lookType, outfit.name, 3, 0x02))
                    }
                }

                outfit.lookType == 1456 || outfit.lookType == 1457 -> {
                    if (source.canWear(1456, 0) || source.canWear(1457, 0)) {
                        entries.add(OutfitWindowOutfitEntry(outfit.lookType, outfit.name, 3, 0x03))
                    }
                }

                outfit.from == "store" ->
                    entries.add(
                        OutfitWindowOutfitEntry(
                            lookType = outfit.lookType,
                            name = outfit.name,
                            addons = if (outfit.lookType in STORE_LOOK_TYPE_RANGE_START..STORE_LOOK_TYPE_RANGE_END) 0 else 3,
                            type = 0x01,
                            storePrice = 0L,
                        ),
                    )
            }

            if (entries.size == limitOutfits) {
                break
            }
        }

        return entries
    }

    /** The `sendOutfitWindow` old-protocol `ProtocolOutfit` loop (protocolgame.cpp:9070-9085). */
    fun buildLegacyOutfitEntries(source: OutfitWindowSource): List<ProtocolOutfit> {
        val entries = mutableListOf<ProtocolOutfit>()

        for (outfit in source.outfits) {
            val addons = source.getOutfitAddons(outfit) ?: continue
            entries.add(ProtocolOutfit(name = outfit.name, lookType = outfit.lookType, addons = addons))
            if (entries.size == LEGACY_OUTFIT_LIMIT) {
                break
            }
        }

        return entries
    }

    private fun writeCustomOtcrBlock(out: ByteBuf, frame: OutfitWindowCustomOtcrFrame?) {
        if (frame == null) {
            return
        }

        writeAttachedEffectList(out, frame.wings)
        writeAttachedEffectList(out, frame.auras)
        writeAttachedEffectList(out, frame.effects)

        out.writeByte(frame.shaders.size)
        for (shader in frame.shaders) {
            out.writeShortLE(shader.id)
            out.writeTibiaString(shader.name)
        }
    }

    private fun writeAttachedEffectList(out: ByteBuf, effects: List<AttachedEffect>) {
        out.writeByte(effects.size)
        for (effect in effects) {
            out.writeShortLE(effect.id)
            out.writeTibiaString(effect.name)
        }
    }
}
