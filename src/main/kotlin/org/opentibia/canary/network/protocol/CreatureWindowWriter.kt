package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import kotlin.math.ceil
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.map.Position

/**
 * `SourceEffect_t` (`src/creatures/creatures_definitions.hpp:1493-1498`), the
 * source byte the 15.x client reads after a graphical effect.
 */
enum class SourceEffect(val value: Int) {
    GLOBAL(0),
    OWN(1),
    OTHERS(2),
    CREATURES(3),
}

/** `PlayerIcon` (`src/enums/player_icons.hpp:16-50`); values are the bitset indexes. */
enum class PlayerIcon(val value: Int) {
    Poison(0),
    Burn(1),
    Energy(2),
    Drunk(3),
    ManaShield(4),
    Paralyze(5),
    Haste(6),
    Swords(7),
    Drowning(8),
    Freezing(9),
    Dazzled(10),
    Cursed(11),
    PartyBuff(12),
    RedSwords(13),
    Pigeon(14),
    Bleeding(15),
    LesserHex(16),
    IntenseHex(17),
    GreaterHex(18),
    Rooted(19),
    Feared(20),
    GoshnarTaint1(21),
    GoshnarTaint2(22),
    GoshnarTaint3(23),
    GoshnarTaint4(24),
    GoshnarTaint5(25),
    NewManaShield(26),
    Agony(27),
    Powerless(28),
    MentorOther(29),
}

/** `IconBakragore` (`src/enums/player_icons.hpp:52-63`). */
enum class IconBakragore(val value: Int) {
    None(0),
    Taint1(1),
    Taint2(2),
    Taint3(3),
    Taint4(4),
    Taint5(5),
    Taint6(6),
    Taint7(7),
    Taint8(8),
    Taint9(9),
}

/**
 * Outbound creature-state and graphical-effect frames for the Canary
 * `ProtocolProfileId::Current` profile (`protocol_new` / 15.x: `oldProtocol ==
 * false`), ported from src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::AddWorldLight`            (0x82, protocolgame.cpp:10311-10315)
 *  - `ProtocolGame::sendMagicEffect`          (0x83, protocolgame.cpp:8264-8294)
 *  - `ProtocolGame::sendDistanceShoot`        (0x83, protocolgame.cpp:8185-8217)
 *  - `ProtocolGame::sendCreatureIcon`         (0x8B, protocolgame.cpp:4609-4621)
 *  - `ProtocolGame::sendCreatureHealth`       (0x8C, protocolgame.cpp:8316-8331)
 *  - `ProtocolGame::AddCreatureLight`         (0x8D, protocolgame.cpp:10380-10387)
 *  - `ProtocolGame::sendCreatureOutfit`       (0x8E, protocolgame.cpp:4547-4564)
 *  - `ProtocolGame::sendChangeSpeed`          (0x8F, protocolgame.cpp:8137-8150)
 *  - `ProtocolGame::sendCreatureSkull`        (0x90, protocolgame.cpp:4687-4701)
 *  - `ProtocolGame::sendCreatureShield`       (0x91, protocolgame.cpp:4653-4663)
 *  - `ProtocolGame::sendCreatureWalkthrough`  (0x92, protocolgame.cpp:4641-4651)
 *  - `ProtocolGame::sendCreatureSquare`       (0x93, protocolgame.cpp:4723-4740)
 *  - `ProtocolGame::sendCreatureMark`         (0x93, protocolgame.cpp:4742-4753)
 *  - `ProtocolGame::sendCreatureType`         (0x95, protocolgame.cpp:4703-4721)
 *  - `ProtocolGame::sendIcons`                (0xA2, protocolgame.cpp:6257-6294)
 *  - `ProtocolGame::sendTibiaTime`            (0xEF, protocolgame.cpp:4629-4639)
 *
 * Every multi-byte field is Little-Endian. Frames whose C++ sender starts with
 * a state gate (`canSee`, `isHealthHidden`, world type, old protocol) leave
 * that gate to the caller, matching how `GameWindowWriter.writeAddCreature`
 * documents its `canSee` contract.
 */
object CreatureWindowWriter {

    const val PACKET_WORLD_LIGHT: Int = 0x82
    const val PACKET_MAGIC_EFFECTS: Int = 0x83
    const val PACKET_CREATURE_ICON: Int = 0x8B
    const val PACKET_CREATURE_HEALTH: Int = 0x8C
    const val PACKET_CREATURE_LIGHT: Int = 0x8D
    const val PACKET_CREATURE_OUTFIT: Int = 0x8E
    const val PACKET_CHANGE_SPEED: Int = 0x8F
    const val PACKET_CREATURE_SKULL: Int = 0x90
    const val PACKET_CREATURE_SHIELD: Int = 0x91
    const val PACKET_CREATURE_WALKTHROUGH: Int = 0x92
    const val PACKET_CREATURE_SQUARE: Int = 0x93
    const val PACKET_CREATURE_TYPE: Int = 0x95
    const val PACKET_PLAYER_ICONS: Int = 0xA2
    const val PACKET_TIBIA_TIME: Int = 0xEF

    /** `MAGIC_EFFECTS_*` (`src/server/server_definitions.hpp:100-108`). */
    const val MAGIC_EFFECTS_END_LOOP: Int = 0
    const val MAGIC_EFFECTS_CREATE_EFFECT: Int = 3
    const val MAGIC_EFFECTS_CREATE_DISTANCEEFFECT: Int = 4

    /** `sendCreatureIcon` writes the generic creature-update packet type 14 (protocolgame.cpp:4618). */
    private const val CREATURE_UPDATE_ICON_TYPE: Int = 14

    /** `CREATURE_MARK_TIMED_SQUARE_TYPE`/`PERSISTENT` (protocolgame.cpp:82-83). */
    private const val CREATURE_MARK_TIMED_SQUARE_TYPE: Int = 0x01
    private const val CREATURE_MARK_PERSISTENT_SQUARE_TYPE: Int = 0x02

    /** `CREATURETYPE_SUMMON_OTHERS` (`src/creatures/creatures_definitions.hpp:489-495`). */
    private const val CREATURETYPE_SUMMON_OTHERS = 4
    private const val ACCESS_PLAYER_LIGHT = 0xFF
    private const val MAX_CLIENT_ICONS = 3
    private const val MAX_HEALTH_PERCENT = 100

    /** `AddWorldLight` (protocolgame.cpp:10311-10315). */
    fun writeWorldLight(out: ByteBuf, light: LightInfo, accessPlayer: Boolean = false) {
        out.writeByte(PACKET_WORLD_LIGHT)
        out.writeByte(if (accessPlayer) ACCESS_PLAYER_LIGHT else light.level)
        out.writeByte(light.color)
    }

    /**
     * `sendMagicEffect` (protocolgame.cpp:8264-8294). The current profile always
     * carries the `GraphicalEffectSourceByte` feature (protocol_profile.cpp:202),
     * so [source] is always written.
     */
    fun writeMagicEffect(
        out: ByteBuf,
        pos: Position,
        type: Int,
        source: SourceEffect = SourceEffect.OWN,
    ) {
        out.writeByte(PACKET_MAGIC_EFFECTS)
        out.writePosition(pos)
        out.writeByte(MAGIC_EFFECTS_CREATE_EFFECT)
        out.writeShortLE(type)
        out.writeByte(source.value)
        out.writeByte(MAGIC_EFFECTS_END_LOOP)
    }

    /**
     * `sendDistanceShoot` (protocolgame.cpp:8185-8217). The deltas are written as
     * `int8_t` (protocolgame.cpp:8205-8206), so the low byte is equivalent.
     */
    fun writeDistanceShoot(
        out: ByteBuf,
        from: Position,
        to: Position,
        type: Int,
        source: SourceEffect = SourceEffect.OWN,
    ) {
        out.writeByte(PACKET_MAGIC_EFFECTS)
        out.writePosition(from)
        out.writeByte(MAGIC_EFFECTS_CREATE_DISTANCEEFFECT)
        out.writeShortLE(type)
        out.writeByte(to.x - from.x)
        out.writeByte(to.y - from.y)
        out.writeByte(source.value)
        out.writeByte(MAGIC_EFFECTS_END_LOOP)
    }

    /**
     * `sendCreatureIcon` (protocolgame.cpp:4609-4621) followed by
     * `addCreatureIcon` (protocolgame.cpp:4576-4607). The taskboard overlay
     * substitution Canary applies for unsummoned monsters is resolved by the
     * caller before the icon list reaches this frame.
     */
    fun writeCreatureIcon(out: ByteBuf, id: Long, icons: List<CreatureIcon>) {
        out.writeByte(PACKET_CREATURE_ICON)
        out.writeIntLE(id.toInt())
        out.writeByte(CREATURE_UPDATE_ICON_TYPE)
        writeCreatureIcons(out, icons)
    }

    /**
     * `sendCreatureHealth` (protocolgame.cpp:8316-8331). Hidden health is
     * suppressed entirely; otherwise the percentage is
     * `min(100, ceil(health / max(maxHealth, 1) * 100))`.
     */
    fun writeCreatureHealth(out: ByteBuf, id: Long, health: Int, maxHealth: Int, healthHidden: Boolean = false) {
        if (healthHidden) {
            return
        }

        out.writeByte(PACKET_CREATURE_HEALTH)
        out.writeIntLE(id.toInt())
        val healthPercent = minOf(
            MAX_HEALTH_PERCENT,
            ceil((health.toDouble() / maxOf(maxHealth, 1)) * 100).toInt(),
        )
        out.writeByte(healthPercent)
    }

    /** `sendCreatureLight` + `AddCreatureLight` (protocolgame.cpp:4566-4574, 10380-10387). */
    fun writeCreatureLight(out: ByteBuf, id: Long, light: LightInfo, accessPlayer: Boolean = false) {
        out.writeByte(PACKET_CREATURE_LIGHT)
        out.writeIntLE(id.toInt())
        out.writeByte(if (accessPlayer) ACCESS_PLAYER_LIGHT else light.level)
        out.writeByte(light.color)
    }

    /**
     * `sendCreatureOutfit` + `AddOutfit` (protocolgame.cpp:4547-4564, 10052-10076).
     *
     * When [supportOutfit] is true Canary also resets the player's current mount
     * (protocolgame.cpp:4553-4556); that player-state mutation stays with the
     * caller, this frame only zeroes the serialized mount.
     */
    fun writeCreatureOutfit(out: ByteBuf, id: Long, outfit: Outfit, supportOutfit: Boolean = false) {
        out.writeByte(PACKET_CREATURE_OUTFIT)
        out.writeIntLE(id.toInt())
        GameWindowWriter.writeOutfit(out, if (supportOutfit) outfit.copy(lookMount = 0) else outfit)
    }

    /**
     * `sendChangeSpeed` (protocolgame.cpp:8137-8150). The current profile writes
     * the base speed first and the effective speed undoubled.
     */
    fun writeChangeSpeed(out: ByteBuf, id: Long, baseSpeed: Int, speed: Int) {
        out.writeByte(PACKET_CHANGE_SPEED)
        out.writeIntLE(id.toInt())
        out.writeShortLE(baseSpeed)
        out.writeShortLE(speed)
    }

    /** `sendCreatureWalkthrough` (protocolgame.cpp:4641-4651): the wire byte is inverted. */
    fun writeCreatureWalkthrough(out: ByteBuf, id: Long, walkthrough: Boolean) {
        out.writeByte(PACKET_CREATURE_WALKTHROUGH)
        out.writeIntLE(id.toInt())
        out.writeByte(if (walkthrough) 0x00 else 0x01)
    }

    /** `sendCreatureShield` (protocolgame.cpp:4653-4663). */
    fun writeCreatureShield(out: ByteBuf, id: Long, partyShield: Int) {
        out.writeByte(PACKET_CREATURE_SHIELD)
        out.writeIntLE(id.toInt())
        out.writeByte(partyShield)
    }

    /** `sendCreatureSkull` (protocolgame.cpp:4687-4701); the PvP-world gate stays with the caller. */
    fun writeCreatureSkull(out: ByteBuf, id: Long, skull: Int) {
        out.writeByte(PACKET_CREATURE_SKULL)
        out.writeIntLE(id.toInt())
        out.writeByte(skull)
    }

    /**
     * `sendCreatureSquare` current-profile branch (protocolgame.cpp:4733-4738).
     * The `isCipsoft860Profile` 0x86 branch (protocolgame.cpp:4730-4732) is
     * intentionally out of scope: the port serializes the current protocol
     * profile only (wave 63 A4).
     */
    fun writeCreatureSquare(out: ByteBuf, id: Long, color: Int) {
        out.writeByte(PACKET_CREATURE_SQUARE)
        out.writeIntLE(id.toInt())
        out.writeByte(CREATURE_MARK_TIMED_SQUARE_TYPE)
        out.writeByte(color)
    }

    /** `sendCreatureMark` (protocolgame.cpp:4742-4753), current-profile only in Canary. */
    fun writeCreatureMark(out: ByteBuf, id: Long, mark: Int) {
        out.writeByte(PACKET_CREATURE_SQUARE)
        out.writeIntLE(id.toInt())
        out.writeByte(CREATURE_MARK_PERSISTENT_SQUARE_TYPE)
        out.writeByte(mark)
    }

    /**
     * `sendCreatureType` (protocolgame.cpp:4703-4721). `CREATURETYPE_SUMMON_OTHERS`
     * is promoted to `CREATURETYPE_SUMMON_PLAYER` and only that type carries the
     * master id on the current profile.
     */
    fun writeCreatureType(out: ByteBuf, id: Long, creatureType: CreatureType, masterId: Long = 0L) {
        val resolvedType = if (creatureType.value == CREATURETYPE_SUMMON_OTHERS) {
            CreatureType.SUMMON_PLAYER
        } else {
            creatureType
        }

        out.writeByte(PACKET_CREATURE_TYPE)
        out.writeIntLE(id.toInt())
        out.writeByte(resolvedType.value)
        if (resolvedType == CreatureType.SUMMON_PLAYER) {
            out.writeIntLE(masterId.toInt())
        }
    }

    /**
     * `sendIcons` current-profile branch (protocolgame.cpp:6284-6291): a 64-bit
     * icon bitset plus the Bakragore icon byte.
     */
    fun writePlayerIcons(
        out: ByteBuf,
        icons: Set<PlayerIcon>,
        iconBakragore: IconBakragore = IconBakragore.None,
    ) {
        var iconBits = 0L
        for (icon in icons) {
            iconBits = iconBits or (1L shl icon.value)
        }

        out.writeByte(PACKET_PLAYER_ICONS)
        out.writeLongLE(iconBits)
        out.writeByte(iconBakragore.value)
    }

    /** `sendTibiaTime` (protocolgame.cpp:4629-4639). */
    fun writeTibiaTime(out: ByteBuf, time: Int) {
        out.writeByte(PACKET_TIBIA_TIME)
        out.writeByte(time / 60)
        out.writeByte(time % 60)
    }

    private fun writeCreatureIcons(out: ByteBuf, icons: List<CreatureIcon>) {
        val count = minOf(MAX_CLIENT_ICONS, icons.size)
        out.writeByte(count)
        for (index in 0 until count) {
            val icon = icons[index]
            out.writeByte(icon.serialize)
            out.writeByte(icon.category)
            out.writeShortLE(icon.count)
        }
    }

    private fun ByteBuf.writePosition(pos: Position) {
        writeShortLE(pos.x)
        writeShortLE(pos.y)
        writeByte(pos.z)
    }
}
