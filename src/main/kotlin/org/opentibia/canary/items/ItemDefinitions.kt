package org.opentibia.canary.items

/**
 * Value mirrors of the Canary item enums, bit constants and ids referenced by
 * `Item`/`ItemType` (`src/items/items_definitions.hpp`,
 * `src/utils/utils_definitions.hpp`, `src/utils/const.hpp`).
 *
 * Names and numeric values are 1:1 with the C++ definitions because they cross
 * the item attributes, the wire protocol and the Lua bridge.
 */

/** `ItemGroup_t` (`src/items/items_definitions.hpp:104-113`). */
enum class ItemGroup_t(val value: Int) {
    ITEM_GROUP_NONE(0),
    ITEM_GROUP_GROUND(1),
    ITEM_GROUP_CONTAINER(2),
    ITEM_GROUP_SPLASH(3),
    ITEM_GROUP_FLUID(4),
    ITEM_GROUP_LAST(5),
    ;

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun fromValue(value: Int): ItemGroup_t = byValue[value] ?: ITEM_GROUP_NONE
    }
}

/** `ItemTypes_t` (`src/items/items_definitions.hpp:115-172`). */
enum class ItemTypes_t(val value: Int) {
    ITEM_TYPE_NONE(0),
    ITEM_TYPE_ARMOR(1),
    ITEM_TYPE_AMULET(2),
    ITEM_TYPE_BOOTS(3),
    ITEM_TYPE_CONTAINER(4),
    ITEM_TYPE_DECORATION(5),
    ITEM_TYPE_FOOD(6),
    ITEM_TYPE_HELMET(7),
    ITEM_TYPE_LEGS(8),
    ITEM_TYPE_OTHER(9),
    ITEM_TYPE_POTION(10),
    ITEM_TYPE_RING(11),
    ITEM_TYPE_RUNE(12),
    ITEM_TYPE_SHIELD(13),
    ITEM_TYPE_TOOLS(14),
    ITEM_TYPE_VALUABLE(15),
    ITEM_TYPE_AMMO(16),
    ITEM_TYPE_AXE(17),
    ITEM_TYPE_CLUB(18),
    ITEM_TYPE_DISTANCE(19),
    ITEM_TYPE_SWORD(20),
    ITEM_TYPE_WAND(21),
    ITEM_TYPE_PREMIUMSCROLL(22),
    ITEM_TYPE_TIBIACOIN(23),
    ITEM_TYPE_CREATUREPRODUCT(24),
    ITEM_TYPE_QUIVER(25),
    ITEM_TYPE_SOULCORES(26),
    ITEM_TYPE_FIST(27),
    ITEM_TYPE_DEPOT(28),
    ITEM_TYPE_MAILBOX(29),
    ITEM_TYPE_TRASHHOLDER(30),
    ITEM_TYPE_DOOR(31),
    ITEM_TYPE_MAGICFIELD(32),
    ITEM_TYPE_TELEPORT(33),
    ITEM_TYPE_BED(34),
    ITEM_TYPE_KEY(35),
    ITEM_TYPE_SUPPLY(36),
    ITEM_TYPE_REWARDCHEST(37),
    ITEM_TYPE_CARPET(38),
    ITEM_TYPE_RETRIEVE(39),
    ITEM_TYPE_GOLD(40),
    ITEM_TYPE_UNASSIGNED(41),
    ITEM_TYPE_LADDER(42),
    ITEM_TYPE_DUMMY(43),
    ITEM_TYPE_LAST(44),
    ;

    companion object {
        private val byValue = entries.associateBy { it.value }

        fun fromValue(value: Int): ItemTypes_t = byValue[value] ?: ITEM_TYPE_NONE
    }
}

/** `Ammo_t` (`src/utils/utils_definitions.hpp:483-491`). */
enum class Ammo_t(val value: Int) {
    AMMO_NONE(0),
    AMMO_BOLT(1),
    AMMO_ARROW(2),
    AMMO_SPEAR(3),
    AMMO_THROWINGSTAR(4),
    AMMO_THROWINGKNIFE(5),
    AMMO_STONE(6),
    AMMO_SNOWBALL(7),
}

/** `ItemProperty` (`src/items/items_definitions.hpp:18-30`). */
enum class ItemProperty(val value: Int) {
    CONST_PROP_BLOCKSOLID(0),
    CONST_PROP_HASHEIGHT(1),
    CONST_PROP_BLOCKPROJECTILE(2),
    CONST_PROP_BLOCKPATH(3),
    CONST_PROP_ISVERTICAL(4),
    CONST_PROP_ISHORIZONTAL(5),
    CONST_PROP_MOVABLE(6),
    CONST_PROP_IMMOVABLEBLOCKSOLID(7),
    CONST_PROP_IMMOVABLEBLOCKPATH(8),
    CONST_PROP_IMMOVABLENOFIELDBLOCKPATH(9),
    CONST_PROP_NOFIELDBLOCKPATH(10),
    CONST_PROP_SUPPORTHANGABLE(11),
}

/** `Fluids_t` (`src/utils/utils_definitions.hpp:412-435`). */
enum class Fluids_t(val value: Int) {
    FLUID_NONE(0),
    FLUID_WATER(1),
    FLUID_WINE(2),
    FLUID_BEER(3),
    FLUID_MUD(4),
    FLUID_BLOOD(5),
    FLUID_SLIME(6),
    FLUID_OIL(7),
    FLUID_URINE(8),
    FLUID_MILK(9),
    FLUID_MANA(10),
    FLUID_LIFE(11),
    FLUID_LEMONADE(12),
    FLUID_RUM(13),
    FLUID_FRUITJUICE(14),
    FLUID_COCONUTMILK(15),
    FLUID_MEAD(16),
    FLUID_TEA(17),
    FLUID_INK(18),
    FLUID_CANDY(19),
    FLUID_CHOCOLATE(20),
}

/**
 * `magic_enum::enum_count<Fluids_t>()` (21 distinct values). The C++ item
 * constructor clamps to this value, not to `size - 1`, so 21 is accepted.
 */
const val FLUIDS_COUNT = 21

// Slot positions (`src/items/items_definitions.hpp:427-440`).
const val SLOTP_HEAD = 1 shl 0
const val SLOTP_NECKLACE = 1 shl 1
const val SLOTP_BACKPACK = 1 shl 2
const val SLOTP_ARMOR = 1 shl 3
const val SLOTP_RIGHT = 1 shl 4
const val SLOTP_LEFT = 1 shl 5
const val SLOTP_LEGS = 1 shl 6
const val SLOTP_FEET = 1 shl 7
const val SLOTP_RING = 1 shl 8
const val SLOTP_AMMO = 1 shl 9
const val SLOTP_DEPOT = 1 shl 10
const val SLOTP_TWO_HAND = 1 shl 11
const val SLOTP_HAND = SLOTP_LEFT or SLOTP_RIGHT

// Wield info bits (`src/utils/utils_definitions.hpp:13-18`).
const val WIELDINFO_NONE = 0
const val WIELDINFO_LEVEL = 1 shl 0
const val WIELDINFO_MAGLV = 1 shl 1
const val WIELDINFO_VOCREQ = 1 shl 2
const val WIELDINFO_PREMIUM = 1 shl 3

/** `IMMOVABLE_ACTION_ID` (`src/utils/const.hpp:73`). */
const val IMMOVABLE_ACTION_ID = 100

// Item ids used by the value/forge accessors.
const val ITEM_GOLD_COIN = 3031
const val ITEM_PLATINUM_COIN = 3035
const val ITEM_CRYSTAL_COIN = 3043
const val ITEM_FORGE_SLIVER = 37109
const val ITEM_FORGE_CORE = 37110

/**
 * `FORGE_MAX_ITEM_TIER` default (`src/config/configmanager.cpp:258-273`,
 * `ConfigKeys`), used by `Item::getTier`/`setTier`.
 */
const val FORGE_MAX_ITEM_TIER_DEFAULT = 10

// Tier chance formula defaults (`ConfigKeys`, configmanager.cpp:236-253,410-412).
const val RUSE_CHANCE_FORMULA_A = 0.0307576
const val RUSE_CHANCE_FORMULA_B = 0.440697
const val RUSE_CHANCE_FORMULA_C = 0.026
const val ONSLAUGHT_CHANCE_FORMULA_A = 0.05
const val ONSLAUGHT_CHANCE_FORMULA_B = 0.4
const val ONSLAUGHT_CHANCE_FORMULA_C = 0.05
const val MOMENTUM_CHANCE_FORMULA_A = 0.05
const val MOMENTUM_CHANCE_FORMULA_B = 1.9
const val MOMENTUM_CHANCE_FORMULA_C = 0.05
const val TRANSCENDENCE_CHANCE_FORMULA_A = 0.0127
const val TRANSCENDENCE_CHANCE_FORMULA_B = 0.1070
const val TRANSCENDENCE_CHANCE_FORMULA_C = 0.0073
const val AMPLIFICATION_CHANCE_FORMULA_A = 0.4
const val AMPLIFICATION_CHANCE_FORMULA_B = 1.7
const val AMPLIFICATION_CHANCE_FORMULA_C = 0.4
