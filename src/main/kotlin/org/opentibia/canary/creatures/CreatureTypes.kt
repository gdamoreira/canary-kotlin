package org.opentibia.canary.creatures

/**
 * Value mirrors of the Canary enums the `Creature`/`Player` state uses.
 *
 * They are 1:1 transcriptions of the C++ definitions (exact constant names and
 * numeric values) because those values cross the creature state, the wire
 * protocol and the Lua bridge.
 */

/**
 * `Skulls_t` (`src/utils/utils_definitions.hpp:507-515`).
 *
 * `creature.hpp:925` (`Skulls_t skull = SKULL_NONE`) and the protocol writer
 * (`Player::getSkullClient`, player.cpp:7116-7121) rely on these numbers.
 */
enum class Skulls_t(val value: Int) {
    SKULL_NONE(0),
    SKULL_YELLOW(1),
    SKULL_GREEN(2),
    SKULL_WHITE(3),
    SKULL_RED(4),
    SKULL_BLACK(5),
    SKULL_ORANGE(6),
}

/**
 * `WeaponType_t` (`src/utils/utils_definitions.hpp:470-481`).
 *
 * `Player::getWeapon(slot, ignoreAmmo)` filters `WEAPON_NONE`/`WEAPON_SHIELD`/
 * `WEAPON_AMMO` (player.cpp:411-414).
 */
enum class WeaponType_t(val value: Int) {
    WEAPON_NONE(0),
    WEAPON_SWORD(1),
    WEAPON_CLUB(2),
    WEAPON_AXE(3),
    WEAPON_SHIELD(4),
    WEAPON_DISTANCE(5),
    WEAPON_WAND(6),
    WEAPON_AMMO(7),
    WEAPON_MISSILE(8),
    WEAPON_FIST(9),
}

/**
 * `PlayerFlags_t` (`src/utils/utils_definitions.hpp:718-762`).
 *
 * Canary keeps the bit array on the `Group` (`groups.hpp:16`); the port holds
 * the set on [Player] until the group flag array is modelled (see
 * `Player::hasFlag`, player.cpp:7853-7855).
 */
enum class PlayerFlags_t(val value: Int) {
    CannotUseCombat(0),
    CannotAttackPlayer(1),
    CannotAttackMonster(2),
    CannotBeAttacked(3),
    CanConvinceAll(4),
    CanSummonAll(5),
    CanIllusionAll(6),
    CanSenseInvisibility(7),
    IgnoredByMonsters(8),
    NotGainInFight(9),
    HasInfiniteMana(10),
    HasInfiniteSoul(11),
    HasNoExhaustion(12),
    CannotUseSpells(13),
    CannotPickupItem(14),
    CanAlwaysLogin(15),
    CanBroadcast(16),
    CanEditHouses(17),
    CannotBeBanned(18),
    CannotBePushed(19),
    HasInfiniteCapacity(20),
    CanPushAllCreatures(21),
    CanTalkRedPrivate(22),
    CanTalkRedChannel(23),
    TalkOrangeHelpChannel(24),
    NotGainExperience(25),
    NotGainMana(26),
    NotGainHealth(27),
    NotGainSkill(28),
    SetMaxSpeed(29),
    SpecialVIP(30),
    NotGenerateLoot(31),
    CanTalkRedChannelAnonymous(32),
    IgnoreProtectionZone(33),
    IgnoreSpellCheck(34),
    IgnoreWeaponCheck(35),
    CannotBeMuted(36),
    IsAlwaysPremium(37),
    CanMapClickTeleport(38),
    IgnoredByNpcs(39),

    /** Must always be the last (`utils_definitions.hpp:761`). */
    FlagLast(40),
}
