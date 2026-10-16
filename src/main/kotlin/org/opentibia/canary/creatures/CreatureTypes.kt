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

/** `CreatureIconCategory_t` (`src/creatures/creatures_definitions.hpp:1500-1503`). */
enum class CreatureIconCategory_t(val value: Int) {
    Quests(0),
    Modifications(1),
}

/** `CreatureIconModifications_t` (`src/creatures/creatures_definitions.hpp:1505-1516`). */
enum class CreatureIconModifications_t(val value: Int) {
    None(0),
    HigherDamageReceived(1),
    LowerDamageDealt(2),
    TurnedMelee(3),
    Influenced(4),
    Fiendish(5),
    ReducedHealth(6),
    ReducedHealthExclamation(7),
    WeeklyTaskMonster(8),
    BountyTaskMonster(9),
}

/** `CreatureIconQuests_t` (`src/creatures/creatures_definitions.hpp:1518-1544`). */
enum class CreatureIconQuests_t(val value: Int) {
    None(0),
    WhiteCross(1),
    RedCross(2),
    RedBall(3),
    GreenBall(4),
    RedGreenBall(5),
    GreenShield(6),
    YellowShield(7),
    BlueShield(8),
    PurpleShield(9),
    RedShield(10),
    Dove(11),
    Energy(12),
    Earth(13),
    Water(14),
    Fire(15),
    Ice(16),
    ArrowUp(17),
    ArrowDown(18),
    ExclamationMark(19),
    QuestionMark(20),
    CancelMark(21),
    Hazard(22),
    BrownSkull(23),
    BloodDrop(24),
}

/**
 * `CreatureIcon` (`src/creatures/creatures_definitions.hpp:1546-1578`).
 *
 * The C++ aggregate default-initializes `category` to `Quests` (value 0); the
 * modification and quest constructors override it to their category.
 */
data class CreatureIcon(
    val category: CreatureIconCategory_t = CreatureIconCategory_t.Quests,
    val modification: CreatureIconModifications_t = CreatureIconModifications_t.None,
    val quest: CreatureIconQuests_t = CreatureIconQuests_t.None,
    val count: Int = 0,
) {
    /** `CreatureIcon(modification, count)` (`creatures_definitions.hpp:1549-1550`). */
    constructor(modification: CreatureIconModifications_t, count: Int = 0) : this(
        CreatureIconCategory_t.Modifications,
        modification,
        CreatureIconQuests_t.None,
        count,
    )

    /** `CreatureIcon(quest, count)` (`creatures_definitions.hpp:1552-1553`). */
    constructor(quest: CreatureIconQuests_t, count: Int = 0) : this(
        CreatureIconCategory_t.Quests,
        CreatureIconModifications_t.None,
        quest,
        count,
    )

    /** `CreatureIcon::isNone` (`creatures_definitions.hpp:1562-1564`). */
    fun isNone(): Boolean =
        modification == CreatureIconModifications_t.None && quest == CreatureIconQuests_t.None

    /** `CreatureIcon::isSet` (`creatures_definitions.hpp:1566-1568`). */
    fun isSet(): Boolean = !isNone()

    /** `CreatureIcon::serialize` (`creatures_definitions.hpp:1570-1577`). */
    fun serialize(): Int = when (category) {
        CreatureIconCategory_t.Modifications -> modification.value
        CreatureIconCategory_t.Quests -> quest.value
    }
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

/**
 * `PlayerPronoun_t` (`src/creatures/creatures_definitions.hpp:455-464`).
 *
 * Used by `Player::getPossessivePronoun` (player.cpp:7096-7098), which the
 * private chat channel invite message reads (chat.cpp:49).
 */
enum class PlayerPronoun_t(val value: Int) {
    PLAYERPRONOUN_UNSET(0),
    PLAYERPRONOUN_THEY(1),
    PLAYERPRONOUN_SHE(2),
    PLAYERPRONOUN_HE(3),
    PLAYERPRONOUN_ZE(4),
    PLAYERPRONOUN_NAME(5),
}

/**
 * `CreatureType_t` (`src/creatures/creatures_definitions.hpp:489-496`).
 *
 * Registered as the `CREATURETYPE_*` Lua globals
 * (`registerEnum`, src/lua/functions/core/game/lua_enums.cpp:582-584); the
 * datapack `compat.lua` derives the `THING_TYPE_*` constants from them.
 */
enum class CreatureType_t(val value: Int) {
    CREATURETYPE_PLAYER(0),
    CREATURETYPE_MONSTER(1),
    CREATURETYPE_NPC(2),
    CREATURETYPE_SUMMON_PLAYER(3),
    CREATURETYPE_SUMMON_OTHERS(4),
    CREATURETYPE_HIDDEN(5),
}
