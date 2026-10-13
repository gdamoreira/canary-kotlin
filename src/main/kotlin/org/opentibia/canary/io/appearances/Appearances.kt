package org.opentibia.canary.io.appearances

import java.io.File

/** Mirrors `PLAYER_ACTION` (src/protobuf/appearances.proto:13). */
enum class PlayerAction(val value: Int) {
    NONE(0),
    LOOK(1),
    USE(2),
    OPEN(3),
    AUTOWALK_HIGHLIGHT(4);

    companion object {
        fun fromValue(value: Int): PlayerAction = entries.firstOrNull { it.value == value } ?: NONE
    }
}

/** Mirrors `ITEM_CATEGORY` (src/protobuf/appearances.proto:21). */
enum class ItemCategory(val value: Int) {
    ARMORS(1),
    AMULETS(2),
    BOOTS(3),
    CONTAINERS(4),
    DECORATION(5),
    FOOD(6),
    HELMETS_HATS(7),
    LEGS(8),
    OTHERS(9),
    POTIONS(10),
    RINGS(11),
    RUNES(12),
    SHIELDS(13),
    TOOLS(14),
    VALUABLES(15),
    AMMUNITION(16),
    AXES(17),
    CLUBS(18),
    DISTANCE_WEAPONS(19),
    SWORDS(20),
    WANDS_RODS(21),
    PREMIUM_SCROLLS(22),
    TIBIA_COINS(23),
    CREATURE_PRODUCTS(24),
    QUIVER(25),
    TWOHANDWEAPON(26),
    FIST_WEAPONS(27),
    BACKPACK(28),
    ONEHANDWEAPON(29),
    ARROW(30),
    SOULCORES(31);

    companion object {
        fun fromValue(value: Int): ItemCategory? = entries.firstOrNull { it.value == value }
    }
}

/** Mirrors `PLAYER_PROFESSION` (src/protobuf/appearances.proto:55). */
enum class PlayerProfession(val value: Int) {
    ANY(-1),
    NONE(0),
    KNIGHT(1),
    PALADIN(2),
    SORCERER(3),
    DRUID(4),
    MONK(5),
    PROMOTED(10);

    companion object {
        fun fromValue(value: Int): PlayerProfession? = entries.firstOrNull { it.value == value }
    }
}

/** Mirrors `ANIMATION_LOOP_TYPE` (src/protobuf/appearances.proto:66). */
enum class AnimationLoopType(val value: Int) {
    PINGPONG(-1),
    INFINITE(0),
    COUNTED(1);

    companion object {
        fun fromValue(value: Int): AnimationLoopType = entries.firstOrNull { it.value == value } ?: INFINITE
    }
}

/** Mirrors `HOOK_TYPE` (src/protobuf/appearances.proto:72). */
enum class HookType(val value: Int) {
    SOUTH(1),
    EAST(2);

    companion object {
        fun fromValue(value: Int): HookType? = entries.firstOrNull { it.value == value }
    }
}

/** Mirrors `FIXED_FRAME_GROUP` (src/protobuf/appearances.proto:289). */
enum class FixedFrameGroup(val value: Int) {
    OUTFIT_IDLE(0),
    OUTFIT_MOVING(1),
    OBJECT_INITIAL(2);

    companion object {
        fun fromValue(value: Int): FixedFrameGroup = entries.firstOrNull { it.value == value } ?: OUTFIT_IDLE
    }
}

/** Mirrors `SpritePhase` (src/protobuf/appearances.proto:85). */
data class SpritePhase(
    val durationMin: Int = 0,
    val durationMax: Int = 0
)

/** Mirrors `SpriteAnimation` (src/protobuf/appearances.proto:90). */
data class SpriteAnimation(
    val defaultStartPhase: Int = 0,
    val synchronized: Boolean = false,
    val randomStartPhase: Boolean = false,
    val loopType: AnimationLoopType = AnimationLoopType.INFINITE,
    val loopCount: Int = 0,
    val spritePhases: List<SpritePhase> = emptyList()
)

/** Mirrors `Box` (src/protobuf/appearances.proto:99). */
data class Box(
    val x: Int = 0,
    val y: Int = 0,
    val width: Int = 0,
    val height: Int = 0
)

/** Mirrors `SpriteInfo` (src/protobuf/appearances.proto:106). */
data class SpriteInfo(
    val patternWidth: Int = 0,
    val patternHeight: Int = 0,
    val patternDepth: Int = 0,
    val layers: Int = 0,
    val spriteIds: List<Int> = emptyList(),
    val animation: SpriteAnimation? = null,
    val boundingSquare: Int = 0,
    val isOpaque: Boolean = false,
    val boundingBoxPerDirection: List<Box> = emptyList()
)

/** Mirrors `FrameGroup` (src/protobuf/appearances.proto:118). */
data class FrameGroup(
    val fixedFrameGroup: FixedFrameGroup = FixedFrameGroup.OUTFIT_IDLE,
    val id: Int = 0,
    val spriteInfo: SpriteInfo? = null
)

/** Mirrors `AppearanceFlagBank` (src/protobuf/appearances.proto:199). */
data class AppearanceFlagBank(val waypoints: Int = 0)

/** Mirrors `AppearanceFlagWrite` (src/protobuf/appearances.proto:203). */
data class AppearanceFlagWrite(val maxTextLength: Int = 0)

/** Mirrors `AppearanceFlagWriteOnce` (src/protobuf/appearances.proto:207). */
data class AppearanceFlagWriteOnce(val maxTextLengthOnce: Int = 0)

/** Mirrors `AppearanceFlagLight` (src/protobuf/appearances.proto:211). */
data class AppearanceFlagLight(val brightness: Int = 0, val color: Int = 0)

/** Mirrors `AppearanceFlagHeight` (src/protobuf/appearances.proto:216). */
data class AppearanceFlagHeight(val elevation: Int = 0)

/** Mirrors `AppearanceFlagShift` (src/protobuf/appearances.proto:220). */
data class AppearanceFlagShift(val x: Int = 0, val y: Int = 0)

/** Mirrors `AppearanceFlagClothes` (src/protobuf/appearances.proto:225). */
data class AppearanceFlagClothes(val slot: Int = 0)

/** Mirrors `AppearanceFlagDefaultAction` (src/protobuf/appearances.proto:229). */
data class AppearanceFlagDefaultAction(val action: PlayerAction = PlayerAction.NONE)

/** Mirrors `AppearanceFlagMarket` (src/protobuf/appearances.proto:233). */
data class AppearanceFlagMarket(
    val category: ItemCategory? = null,
    val tradeAsObjectId: Int = 0,
    val showAsObjectId: Int = 0,
    val restrictToProfession: List<PlayerProfession> = emptyList(),
    val minimumLevel: Int = 0
)

/** Mirrors `AppearanceFlagNPC` (src/protobuf/appearances.proto:241). */
data class AppearanceFlagNpc(
    val name: String = "",
    val location: String = "",
    val salePrice: Int = 0,
    val buyPrice: Int = 0,
    val currencyObjectTypeId: Int = 0,
    val currencyQuestFlagDisplayName: String = ""
)

/** Mirrors `AppearanceFlagAutomap` (src/protobuf/appearances.proto:250). */
data class AppearanceFlagAutomap(val color: Int = 0)

/** Mirrors `AppearanceFlagHook` (src/protobuf/appearances.proto:254). */
data class AppearanceFlagHook(val direction: HookType? = null)

/** Mirrors `AppearanceFlagLenshelp` (src/protobuf/appearances.proto:258). */
data class AppearanceFlagLenshelp(val id: Int = 0)

/** Mirrors `AppearanceFlagChangedToExpire` (src/protobuf/appearances.proto:262). */
data class AppearanceFlagChangedToExpire(val formerObjectTypeid: Int = 0)

/** Mirrors `AppearanceFlagCyclopedia` (src/protobuf/appearances.proto:266). */
data class AppearanceFlagCyclopedia(val cyclopediaType: Int = 0)

/** Mirrors `AppearanceFlagUpgradeClassification` (src/protobuf/appearances.proto:195). */
data class AppearanceFlagUpgradeClassification(val upgradeClassification: Int = 0)

/** Mirrors `AppearanceFlagSkillWheelGem` (src/protobuf/appearances.proto:270). */
data class AppearanceFlagSkillWheelGem(val gemQualityId: Int = 0, val vocationId: Int = 0)

/** Mirrors `AppearanceFlagProficiency` (src/protobuf/appearances.proto:275). */
data class AppearanceFlagProficiency(val proficiencyId: Int = 0)

/** Mirrors `AppearanceFlags` (src/protobuf/appearances.proto:132). */
data class AppearanceFlags(
    val bank: AppearanceFlagBank? = null,
    val clip: Boolean = false,
    val bottom: Boolean = false,
    val top: Boolean = false,
    val container: Boolean = false,
    val cumulative: Boolean = false,
    val usable: Boolean = false,
    val forceuse: Boolean = false,
    val multiuse: Boolean = false,
    val write: AppearanceFlagWrite? = null,
    val writeOnce: AppearanceFlagWriteOnce? = null,
    val liquidpool: Boolean = false,
    val unpass: Boolean = false,
    val unmove: Boolean = false,
    val unsight: Boolean = false,
    val avoid: Boolean = false,
    val noMovementAnimation: Boolean = false,
    val take: Boolean = false,
    val liquidcontainer: Boolean = false,
    val hang: Boolean = false,
    val hook: AppearanceFlagHook? = null,
    val rotate: Boolean = false,
    val light: AppearanceFlagLight? = null,
    val dontHide: Boolean = false,
    val translucent: Boolean = false,
    val shift: AppearanceFlagShift? = null,
    val height: AppearanceFlagHeight? = null,
    val lyingObject: Boolean = false,
    val animateAlways: Boolean = false,
    val automap: AppearanceFlagAutomap? = null,
    val lenshelp: AppearanceFlagLenshelp? = null,
    val fullbank: Boolean = false,
    val ignoreLook: Boolean = false,
    val clothes: AppearanceFlagClothes? = null,
    val defaultAction: AppearanceFlagDefaultAction? = null,
    val market: AppearanceFlagMarket? = null,
    val wrap: Boolean = false,
    val unwrap: Boolean = false,
    val topeffect: Boolean = false,
    val npcsaledata: List<AppearanceFlagNpc> = emptyList(),
    val changedtoexpire: AppearanceFlagChangedToExpire? = null,
    val corpse: Boolean = false,
    val playerCorpse: Boolean = false,
    val cyclopediaitem: AppearanceFlagCyclopedia? = null,
    val ammo: Boolean = false,
    val showOffSocket: Boolean = false,
    val reportable: Boolean = false,
    val upgradeclassification: AppearanceFlagUpgradeClassification? = null,
    val reverseAddonsEast: Boolean = false,
    val reverseAddonsWest: Boolean = false,
    val reverseAddonsSouth: Boolean = false,
    val reverseAddonsNorth: Boolean = false,
    val wearout: Boolean = false,
    val clockexpire: Boolean = false,
    val expire: Boolean = false,
    val expirestop: Boolean = false,
    val wrapkit: Boolean = false,
    val skillwheelGem: AppearanceFlagSkillWheelGem? = null,
    val dualWielding: Boolean = false,
    val proficiency: AppearanceFlagProficiency? = null
)

/** Mirrors `Appearance` (src/protobuf/appearances.proto:124). */
data class Appearance(
    val id: Int = 0,
    val frameGroups: List<FrameGroup> = emptyList(),
    val flags: AppearanceFlags? = null,
    val name: String = "",
    val description: String = ""
)

/** Mirrors `SpecialMeaningAppearanceIds` (src/protobuf/appearances.proto:279). */
data class SpecialMeaningAppearanceIds(
    val goldCoinId: Int = 0,
    val platinumCoinId: Int = 0,
    val crystalCoinId: Int = 0,
    val tibiaCoinId: Int = 0,
    val stampedLetterId: Int = 0,
    val supplyStashId: Int = 0,
    val rewardChestId: Int = 0
)

/**
 * Appearance tables parsed from `{CORE_DIRECTORY}/items/appearances.dat`
 * (Canary `src/protobuf/appearances.proto`, loaded by `Game::loadAppearanceProtobuf`
 * at src/game/game.cpp:1657 and merged by `Items::loadFromProtobuf` at
 * src/items/items.cpp:153).
 *
 * The canonical asset stores objects (items), outfits, effects and missiles in separate
 * lists. Mount appearances share the outfit list: the client renders a mount through the
 * outfit appearance whose id equals `Mount::clientId`. Mount *identity* stays owned by
 * `Mounts` (`data/XML/mounts.xml`); these tables only provide the appearance data.
 */
class Appearances private constructor(
    val objects: Map<Int, Appearance>,
    val outfits: Map<Int, Appearance>,
    val effects: Map<Int, Appearance>,
    val missiles: Map<Int, Appearance>,
    val specialMeaningAppearanceIds: SpecialMeaningAppearanceIds?,
    /** Mirrors `Game::outfitMountSupportedLookTypes` (src/game/game.hpp:884). */
    val outfitMountSupportedLookTypes: Set<Int>
) {

    /** Canary `m_appearancesPtr->object()` look-up by appearance id. */
    fun getObject(id: Int): Appearance? = objects[id]

    /** Canary `m_appearancesPtr->outfit()` look-up by look type. */
    fun getOutfit(id: Int): Appearance? = outfits[id]

    /**
     * Resolves a mount appearance by its client id (`Mount::clientId`). Mount render data is
     * part of the shared outfit list, so this is Canary's `m_appearancesPtr->outfit()` lookup
     * keyed by the mount client id.
     */
    fun getMount(id: Int): Appearance? = outfits[id]

    /** Canary `m_appearancesPtr->effect()` look-up by magic effect id. */
    fun getEffect(id: Int): Appearance? = effects[id]

    /** Canary `m_appearancesPtr->missile()` look-up by distance effect id. */
    fun getMissile(id: Int): Appearance? = missiles[id]

    /** Mirrors `Game::outfitSupportsMount` (src/game/game.cpp:10071). */
    fun outfitSupportsMount(lookType: Int): Boolean = outfitMountSupportedLookTypes.contains(lookType)

    companion object {
        fun load(file: File): Appearances = load(file.readBytes())

        /**
         * Parses the wire-format `Appearances` message. Truncated or otherwise invalid input is
         * tolerated: every message decoded before the corruption is kept and no exception escapes.
         */
        fun load(bytes: ByteArray): Appearances {
            val objects = LinkedHashMap<Int, Appearance>()
            val outfits = LinkedHashMap<Int, Appearance>()
            val effects = LinkedHashMap<Int, Appearance>()
            val missiles = LinkedHashMap<Int, Appearance>()
            var specialMeaningAppearanceIds: SpecialMeaningAppearanceIds? = null

            val reader = ProtobufWireReader(bytes)
            try {
                while (reader.hasRemaining()) {
                    val tag = reader.readTag()
                    val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
                    val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
                    if (wireType == ProtobufWireReader.WIRE_TYPE_LENGTH_DELIMITED) {
                        when (field) {
                            1 -> {
                                val appearance = parseAppearance(reader.readLengthDelimitedReader())
                                objects[appearance.id] = appearance
                            }

                            2 -> {
                                val appearance = parseAppearance(reader.readLengthDelimitedReader())
                                outfits[appearance.id] = appearance
                            }

                            3 -> {
                                val appearance = parseAppearance(reader.readLengthDelimitedReader())
                                effects[appearance.id] = appearance
                            }

                            4 -> {
                                val appearance = parseAppearance(reader.readLengthDelimitedReader())
                                missiles[appearance.id] = appearance
                            }

                            5 -> specialMeaningAppearanceIds =
                                parseSpecialMeaningAppearanceIds(reader.readLengthDelimitedReader())

                            else -> reader.skip(wireType)
                        }
                    } else {
                        reader.skip(wireType)
                    }
                }
            } catch (_: WireFormatException) {
                // Keep the partial result; damaged assets must not take the loader down.
            }

            val mountSupported = outfits.values
                .filter { outfitSupportsMount(it) }
                .mapTo(LinkedHashSet()) { it.id }

            return Appearances(
                objects = objects,
                outfits = outfits,
                effects = effects,
                missiles = missiles,
                specialMeaningAppearanceIds = specialMeaningAppearanceIds,
                outfitMountSupportedLookTypes = mountSupported
            )
        }

        /**
         * Mirrors `Game::outfitAppearanceSupportsMount` (src/game/game.cpp:10046): an outfit
         * supports mounting only when both the idle and moving frame groups carry addon/mount
         * layers (`pattern_depth > 1`).
         */
        private fun outfitSupportsMount(appearance: Appearance): Boolean {
            var hasIdleFrameGroup = false
            var hasMovingFrameGroup = false
            for (frameGroup in appearance.frameGroups) {
                val fixed = frameGroup.fixedFrameGroup
                if (fixed != FixedFrameGroup.OUTFIT_IDLE && fixed != FixedFrameGroup.OUTFIT_MOVING) {
                    continue
                }
                if (fixed == FixedFrameGroup.OUTFIT_IDLE) {
                    hasIdleFrameGroup = true
                } else {
                    hasMovingFrameGroup = true
                }
                val spriteInfo = frameGroup.spriteInfo ?: return false
                if (spriteInfo.patternDepth <= 1) {
                    return false
                }
            }
            return hasIdleFrameGroup && hasMovingFrameGroup
        }
    }
}

private fun parseAppearance(reader: ProtobufWireReader): Appearance {
    var id = 0
    val frameGroups = ArrayList<FrameGroup>()
    var flags: AppearanceFlags? = null
    var name = ""
    var description = ""

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> id = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else if (wireType == ProtobufWireReader.WIRE_TYPE_LENGTH_DELIMITED) {
            when (field) {
                2 -> frameGroups += parseFrameGroup(reader.readLengthDelimitedReader())
                3 -> flags = parseAppearanceFlags(reader.readLengthDelimitedReader())
                4 -> name = reader.readString()
                5 -> description = reader.readString()
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }

    return Appearance(id = id, frameGroups = frameGroups, flags = flags, name = name, description = description)
}

private fun parseFrameGroup(reader: ProtobufWireReader): FrameGroup {
    var fixedFrameGroup = FixedFrameGroup.OUTFIT_IDLE
    var id = 0
    var spriteInfo: SpriteInfo? = null

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> fixedFrameGroup = FixedFrameGroup.fromValue(reader.readVarint().toInt())
                2 -> id = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else if (wireType == ProtobufWireReader.WIRE_TYPE_LENGTH_DELIMITED) {
            when (field) {
                3 -> spriteInfo = parseSpriteInfo(reader.readLengthDelimitedReader())
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }

    return FrameGroup(fixedFrameGroup = fixedFrameGroup, id = id, spriteInfo = spriteInfo)
}

private fun parseSpriteInfo(reader: ProtobufWireReader): SpriteInfo {
    var patternWidth = 0
    var patternHeight = 0
    var patternDepth = 0
    var layers = 0
    val spriteIds = ArrayList<Int>()
    var animation: SpriteAnimation? = null
    var boundingSquare = 0
    var isOpaque = false
    val boundingBoxPerDirection = ArrayList<Box>()

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> patternWidth = reader.readVarint().toInt()
                2 -> patternHeight = reader.readVarint().toInt()
                3 -> patternDepth = reader.readVarint().toInt()
                4 -> layers = reader.readVarint().toInt()
                5 -> spriteIds += reader.readVarint().toInt()
                7 -> boundingSquare = reader.readVarint().toInt()
                8 -> isOpaque = reader.readVarint() != 0L
                else -> reader.skip(wireType)
            }
        } else if (wireType == ProtobufWireReader.WIRE_TYPE_LENGTH_DELIMITED) {
            when (field) {
                5 -> readPackedVarints(reader).forEach { spriteIds += it.toInt() }
                6 -> animation = parseSpriteAnimation(reader.readLengthDelimitedReader())
                9 -> boundingBoxPerDirection += parseBox(reader.readLengthDelimitedReader())
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }

    return SpriteInfo(
        patternWidth = patternWidth,
        patternHeight = patternHeight,
        patternDepth = patternDepth,
        layers = layers,
        spriteIds = spriteIds,
        animation = animation,
        boundingSquare = boundingSquare,
        isOpaque = isOpaque,
        boundingBoxPerDirection = boundingBoxPerDirection
    )
}

private fun parseSpriteAnimation(reader: ProtobufWireReader): SpriteAnimation {
    var defaultStartPhase = 0
    var synchronized = false
    var randomStartPhase = false
    var loopType = AnimationLoopType.INFINITE
    var loopCount = 0
    val spritePhases = ArrayList<SpritePhase>()

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> defaultStartPhase = reader.readVarint().toInt()
                2 -> synchronized = reader.readVarint() != 0L
                3 -> randomStartPhase = reader.readVarint() != 0L
                4 -> loopType = AnimationLoopType.fromValue(reader.readVarint().toInt())
                5 -> loopCount = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else if (wireType == ProtobufWireReader.WIRE_TYPE_LENGTH_DELIMITED) {
            when (field) {
                6 -> spritePhases += parseSpritePhase(reader.readLengthDelimitedReader())
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }

    return SpriteAnimation(
        defaultStartPhase = defaultStartPhase,
        synchronized = synchronized,
        randomStartPhase = randomStartPhase,
        loopType = loopType,
        loopCount = loopCount,
        spritePhases = spritePhases
    )
}

private fun parseSpritePhase(reader: ProtobufWireReader): SpritePhase {
    var durationMin = 0
    var durationMax = 0

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> durationMin = reader.readVarint().toInt()
                2 -> durationMax = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }

    return SpritePhase(durationMin = durationMin, durationMax = durationMax)
}

private fun parseBox(reader: ProtobufWireReader): Box {
    var x = 0
    var y = 0
    var width = 0
    var height = 0

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> x = reader.readVarint().toInt()
                2 -> y = reader.readVarint().toInt()
                3 -> width = reader.readVarint().toInt()
                4 -> height = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }

    return Box(x = x, y = y, width = width, height = height)
}

private fun parseAppearanceFlags(reader: ProtobufWireReader): AppearanceFlags {
    var bank: AppearanceFlagBank? = null
    var clip = false
    var bottom = false
    var top = false
    var container = false
    var cumulative = false
    var usable = false
    var forceuse = false
    var multiuse = false
    var write: AppearanceFlagWrite? = null
    var writeOnce: AppearanceFlagWriteOnce? = null
    var liquidpool = false
    var unpass = false
    var unmove = false
    var unsight = false
    var avoid = false
    var noMovementAnimation = false
    var take = false
    var liquidcontainer = false
    var hang = false
    var hook: AppearanceFlagHook? = null
    var rotate = false
    var light: AppearanceFlagLight? = null
    var dontHide = false
    var translucent = false
    var shift: AppearanceFlagShift? = null
    var height: AppearanceFlagHeight? = null
    var lyingObject = false
    var animateAlways = false
    var automap: AppearanceFlagAutomap? = null
    var lenshelp: AppearanceFlagLenshelp? = null
    var fullbank = false
    var ignoreLook = false
    var clothes: AppearanceFlagClothes? = null
    var defaultAction: AppearanceFlagDefaultAction? = null
    var market: AppearanceFlagMarket? = null
    var wrap = false
    var unwrap = false
    var topeffect = false
    val npcsaledata = ArrayList<AppearanceFlagNpc>()
    var changedtoexpire: AppearanceFlagChangedToExpire? = null
    var corpse = false
    var playerCorpse = false
    var cyclopediaitem: AppearanceFlagCyclopedia? = null
    var ammo = false
    var showOffSocket = false
    var reportable = false
    var upgradeclassification: AppearanceFlagUpgradeClassification? = null
    var reverseAddonsEast = false
    var reverseAddonsWest = false
    var reverseAddonsSouth = false
    var reverseAddonsNorth = false
    var wearout = false
    var clockexpire = false
    var expire = false
    var expirestop = false
    var wrapkit = false
    var skillwheelGem: AppearanceFlagSkillWheelGem? = null
    var dualWielding = false
    var proficiency: AppearanceFlagProficiency? = null

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                2 -> clip = reader.readVarint() != 0L
                3 -> bottom = reader.readVarint() != 0L
                4 -> top = reader.readVarint() != 0L
                5 -> container = reader.readVarint() != 0L
                6 -> cumulative = reader.readVarint() != 0L
                7 -> usable = reader.readVarint() != 0L
                8 -> forceuse = reader.readVarint() != 0L
                9 -> multiuse = reader.readVarint() != 0L
                12 -> liquidpool = reader.readVarint() != 0L
                13 -> unpass = reader.readVarint() != 0L
                14 -> unmove = reader.readVarint() != 0L
                15 -> unsight = reader.readVarint() != 0L
                16 -> avoid = reader.readVarint() != 0L
                17 -> noMovementAnimation = reader.readVarint() != 0L
                18 -> take = reader.readVarint() != 0L
                19 -> liquidcontainer = reader.readVarint() != 0L
                20 -> hang = reader.readVarint() != 0L
                22 -> rotate = reader.readVarint() != 0L
                24 -> dontHide = reader.readVarint() != 0L
                25 -> translucent = reader.readVarint() != 0L
                28 -> lyingObject = reader.readVarint() != 0L
                29 -> animateAlways = reader.readVarint() != 0L
                32 -> fullbank = reader.readVarint() != 0L
                33 -> ignoreLook = reader.readVarint() != 0L
                37 -> wrap = reader.readVarint() != 0L
                38 -> unwrap = reader.readVarint() != 0L
                39 -> topeffect = reader.readVarint() != 0L
                42 -> corpse = reader.readVarint() != 0L
                43 -> playerCorpse = reader.readVarint() != 0L
                45 -> ammo = reader.readVarint() != 0L
                46 -> showOffSocket = reader.readVarint() != 0L
                47 -> reportable = reader.readVarint() != 0L
                49 -> reverseAddonsEast = reader.readVarint() != 0L
                50 -> reverseAddonsWest = reader.readVarint() != 0L
                51 -> reverseAddonsSouth = reader.readVarint() != 0L
                52 -> reverseAddonsNorth = reader.readVarint() != 0L
                53 -> wearout = reader.readVarint() != 0L
                54 -> clockexpire = reader.readVarint() != 0L
                55 -> expire = reader.readVarint() != 0L
                56 -> expirestop = reader.readVarint() != 0L
                57 -> wrapkit = reader.readVarint() != 0L
                59 -> dualWielding = reader.readVarint() != 0L
                else -> reader.skip(wireType)
            }
        } else if (wireType == ProtobufWireReader.WIRE_TYPE_LENGTH_DELIMITED) {
            when (field) {
                1 -> bank = parseFlagBank(reader.readLengthDelimitedReader())
                10 -> write = parseFlagWrite(reader.readLengthDelimitedReader())
                11 -> writeOnce = parseFlagWriteOnce(reader.readLengthDelimitedReader())
                21 -> hook = parseFlagHook(reader.readLengthDelimitedReader())
                23 -> light = parseFlagLight(reader.readLengthDelimitedReader())
                26 -> shift = parseFlagShift(reader.readLengthDelimitedReader())
                27 -> height = parseFlagHeight(reader.readLengthDelimitedReader())
                30 -> automap = parseFlagAutomap(reader.readLengthDelimitedReader())
                31 -> lenshelp = parseFlagLenshelp(reader.readLengthDelimitedReader())
                34 -> clothes = parseFlagClothes(reader.readLengthDelimitedReader())
                35 -> defaultAction = parseFlagDefaultAction(reader.readLengthDelimitedReader())
                36 -> market = parseFlagMarket(reader.readLengthDelimitedReader())
                40 -> npcsaledata += parseFlagNpc(reader.readLengthDelimitedReader())
                41 -> changedtoexpire = parseFlagChangedToExpire(reader.readLengthDelimitedReader())
                44 -> cyclopediaitem = parseFlagCyclopedia(reader.readLengthDelimitedReader())
                48 -> upgradeclassification = parseFlagUpgradeClassification(reader.readLengthDelimitedReader())
                58 -> skillwheelGem = parseFlagSkillWheelGem(reader.readLengthDelimitedReader())
                61 -> proficiency = parseFlagProficiency(reader.readLengthDelimitedReader())
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }

    return AppearanceFlags(
        bank = bank,
        clip = clip,
        bottom = bottom,
        top = top,
        container = container,
        cumulative = cumulative,
        usable = usable,
        forceuse = forceuse,
        multiuse = multiuse,
        write = write,
        writeOnce = writeOnce,
        liquidpool = liquidpool,
        unpass = unpass,
        unmove = unmove,
        unsight = unsight,
        avoid = avoid,
        noMovementAnimation = noMovementAnimation,
        take = take,
        liquidcontainer = liquidcontainer,
        hang = hang,
        hook = hook,
        rotate = rotate,
        light = light,
        dontHide = dontHide,
        translucent = translucent,
        shift = shift,
        height = height,
        lyingObject = lyingObject,
        animateAlways = animateAlways,
        automap = automap,
        lenshelp = lenshelp,
        fullbank = fullbank,
        ignoreLook = ignoreLook,
        clothes = clothes,
        defaultAction = defaultAction,
        market = market,
        wrap = wrap,
        unwrap = unwrap,
        topeffect = topeffect,
        npcsaledata = npcsaledata,
        changedtoexpire = changedtoexpire,
        corpse = corpse,
        playerCorpse = playerCorpse,
        cyclopediaitem = cyclopediaitem,
        ammo = ammo,
        showOffSocket = showOffSocket,
        reportable = reportable,
        upgradeclassification = upgradeclassification,
        reverseAddonsEast = reverseAddonsEast,
        reverseAddonsWest = reverseAddonsWest,
        reverseAddonsSouth = reverseAddonsSouth,
        reverseAddonsNorth = reverseAddonsNorth,
        wearout = wearout,
        clockexpire = clockexpire,
        expire = expire,
        expirestop = expirestop,
        wrapkit = wrapkit,
        skillwheelGem = skillwheelGem,
        dualWielding = dualWielding,
        proficiency = proficiency
    )
}

private fun parseFlagBank(reader: ProtobufWireReader): AppearanceFlagBank {
    var waypoints = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            waypoints = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagBank(waypoints = waypoints)
}

private fun parseFlagWrite(reader: ProtobufWireReader): AppearanceFlagWrite {
    var maxTextLength = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            maxTextLength = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagWrite(maxTextLength = maxTextLength)
}

private fun parseFlagWriteOnce(reader: ProtobufWireReader): AppearanceFlagWriteOnce {
    var maxTextLengthOnce = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            maxTextLengthOnce = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagWriteOnce(maxTextLengthOnce = maxTextLengthOnce)
}

private fun parseFlagLight(reader: ProtobufWireReader): AppearanceFlagLight {
    var brightness = 0
    var color = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> brightness = reader.readVarint().toInt()
                2 -> color = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagLight(brightness = brightness, color = color)
}

private fun parseFlagHeight(reader: ProtobufWireReader): AppearanceFlagHeight {
    var elevation = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            elevation = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagHeight(elevation = elevation)
}

private fun parseFlagShift(reader: ProtobufWireReader): AppearanceFlagShift {
    var x = 0
    var y = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> x = reader.readVarint().toInt()
                2 -> y = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagShift(x = x, y = y)
}

private fun parseFlagClothes(reader: ProtobufWireReader): AppearanceFlagClothes {
    var slot = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            slot = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagClothes(slot = slot)
}

private fun parseFlagDefaultAction(reader: ProtobufWireReader): AppearanceFlagDefaultAction {
    var action = PlayerAction.NONE
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            action = PlayerAction.fromValue(reader.readVarint().toInt())
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagDefaultAction(action = action)
}

private fun parseFlagMarket(reader: ProtobufWireReader): AppearanceFlagMarket {
    var category: ItemCategory? = null
    var tradeAsObjectId = 0
    var showAsObjectId = 0
    val restrictToProfession = ArrayList<PlayerProfession>()
    var minimumLevel = 0

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> category = ItemCategory.fromValue(reader.readVarint().toInt())
                2 -> tradeAsObjectId = reader.readVarint().toInt()
                3 -> showAsObjectId = reader.readVarint().toInt()
                5 -> PlayerProfession.fromValue(reader.readVarint().toInt())?.let { restrictToProfession += it }
                6 -> minimumLevel = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else if (wireType == ProtobufWireReader.WIRE_TYPE_LENGTH_DELIMITED && field == 5) {
            readPackedVarints(reader).forEach { value ->
                PlayerProfession.fromValue(value.toInt())?.let { restrictToProfession += it }
            }
        } else {
            reader.skip(wireType)
        }
    }

    return AppearanceFlagMarket(
        category = category,
        tradeAsObjectId = tradeAsObjectId,
        showAsObjectId = showAsObjectId,
        restrictToProfession = restrictToProfession,
        minimumLevel = minimumLevel
    )
}

private fun parseFlagNpc(reader: ProtobufWireReader): AppearanceFlagNpc {
    var name = ""
    var location = ""
    var salePrice = 0
    var buyPrice = 0
    var currencyObjectTypeId = 0
    var currencyQuestFlagDisplayName = ""

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                3 -> salePrice = reader.readVarint().toInt()
                4 -> buyPrice = reader.readVarint().toInt()
                5 -> currencyObjectTypeId = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else if (wireType == ProtobufWireReader.WIRE_TYPE_LENGTH_DELIMITED) {
            when (field) {
                1 -> name = reader.readString()
                2 -> location = reader.readString()
                6 -> currencyQuestFlagDisplayName = reader.readString()
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }

    return AppearanceFlagNpc(
        name = name,
        location = location,
        salePrice = salePrice,
        buyPrice = buyPrice,
        currencyObjectTypeId = currencyObjectTypeId,
        currencyQuestFlagDisplayName = currencyQuestFlagDisplayName
    )
}

private fun parseFlagAutomap(reader: ProtobufWireReader): AppearanceFlagAutomap {
    var color = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            color = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagAutomap(color = color)
}

private fun parseFlagHook(reader: ProtobufWireReader): AppearanceFlagHook {
    var direction: HookType? = null
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            direction = HookType.fromValue(reader.readVarint().toInt())
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagHook(direction = direction)
}

private fun parseFlagLenshelp(reader: ProtobufWireReader): AppearanceFlagLenshelp {
    var id = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            id = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagLenshelp(id = id)
}

private fun parseFlagChangedToExpire(reader: ProtobufWireReader): AppearanceFlagChangedToExpire {
    var formerObjectTypeid = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            formerObjectTypeid = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagChangedToExpire(formerObjectTypeid = formerObjectTypeid)
}

private fun parseFlagCyclopedia(reader: ProtobufWireReader): AppearanceFlagCyclopedia {
    var cyclopediaType = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            cyclopediaType = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagCyclopedia(cyclopediaType = cyclopediaType)
}

private fun parseFlagUpgradeClassification(reader: ProtobufWireReader): AppearanceFlagUpgradeClassification {
    var upgradeClassification = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            upgradeClassification = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagUpgradeClassification(upgradeClassification = upgradeClassification)
}

private fun parseFlagSkillWheelGem(reader: ProtobufWireReader): AppearanceFlagSkillWheelGem {
    var gemQualityId = 0
    var vocationId = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> gemQualityId = reader.readVarint().toInt()
                2 -> vocationId = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagSkillWheelGem(gemQualityId = gemQualityId, vocationId = vocationId)
}

private fun parseFlagProficiency(reader: ProtobufWireReader): AppearanceFlagProficiency {
    var proficiencyId = 0
    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (field == 1 && wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            proficiencyId = reader.readVarint().toInt()
        } else {
            reader.skip(wireType)
        }
    }
    return AppearanceFlagProficiency(proficiencyId = proficiencyId)
}

private fun parseSpecialMeaningAppearanceIds(reader: ProtobufWireReader): SpecialMeaningAppearanceIds {
    var goldCoinId = 0
    var platinumCoinId = 0
    var crystalCoinId = 0
    var tibiaCoinId = 0
    var stampedLetterId = 0
    var supplyStashId = 0
    var rewardChestId = 0

    while (reader.hasRemaining()) {
        val tag = reader.readTag()
        val field = tag ushr ProtobufWireReader.FIELD_NUMBER_SHIFT
        val wireType = tag and ProtobufWireReader.WIRE_TYPE_MASK
        if (wireType == ProtobufWireReader.WIRE_TYPE_VARINT) {
            when (field) {
                1 -> goldCoinId = reader.readVarint().toInt()
                2 -> platinumCoinId = reader.readVarint().toInt()
                3 -> crystalCoinId = reader.readVarint().toInt()
                4 -> tibiaCoinId = reader.readVarint().toInt()
                5 -> stampedLetterId = reader.readVarint().toInt()
                6 -> supplyStashId = reader.readVarint().toInt()
                7 -> rewardChestId = reader.readVarint().toInt()
                else -> reader.skip(wireType)
            }
        } else {
            reader.skip(wireType)
        }
    }

    return SpecialMeaningAppearanceIds(
        goldCoinId = goldCoinId,
        platinumCoinId = platinumCoinId,
        crystalCoinId = crystalCoinId,
        tibiaCoinId = tibiaCoinId,
        stampedLetterId = stampedLetterId,
        supplyStashId = supplyStashId,
        rewardChestId = rewardChestId
    )
}

private fun readPackedVarints(reader: ProtobufWireReader): List<Long> {
    val packed = reader.readLengthDelimitedReader()
    val values = ArrayList<Long>()
    while (packed.hasRemaining()) {
        values += packed.readVarint()
    }
    return values
}
