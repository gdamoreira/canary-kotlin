package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaValue
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.condition.CombatType
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.CreatureIconCategory_t
import org.opentibia.canary.creatures.CreatureIconModifications_t
import org.opentibia.canary.creatures.CreatureIconQuests_t
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.creatures.monsters.BosstiaryRarity_t
import org.opentibia.canary.creatures.monsters.MonstersEvent
import org.opentibia.canary.game.reload.Reload_t
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.Fluids_t
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.spells.SoundAmbientEffect
import org.opentibia.canary.spells.SoundEffect
import org.opentibia.canary.spells.SoundMusicEffect
import org.opentibia.canary.spells.SpellType
import org.opentibia.canary.spells.WorldType
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Enum-name subsets registered by `LuaEnums` (src/lua/functions/core/game/lua_enums.cpp).
 *
 * The `registerEnum` lists are not always exhaustive over their C++ enums, so the
 * exact registered subsets are transcribed rather than iterating every Kotlin entry.
 */
private val ITEM_TYPE_ENUM_NAMES = listOf(
    "ITEM_TYPE_DEPOT", "ITEM_TYPE_REWARDCHEST", "ITEM_TYPE_MAILBOX", "ITEM_TYPE_TRASHHOLDER",
    "ITEM_TYPE_DOOR", "ITEM_TYPE_MAGICFIELD", "ITEM_TYPE_TELEPORT", "ITEM_TYPE_BED",
    "ITEM_TYPE_KEY", "ITEM_TYPE_SUPPLY", "ITEM_TYPE_ARMOR", "ITEM_TYPE_AMULET",
    "ITEM_TYPE_BOOTS", "ITEM_TYPE_DECORATION", "ITEM_TYPE_CONTAINER", "ITEM_TYPE_FOOD",
    "ITEM_TYPE_HELMET", "ITEM_TYPE_LEGS", "ITEM_TYPE_OTHER", "ITEM_TYPE_POTION",
    "ITEM_TYPE_RING", "ITEM_TYPE_RUNE", "ITEM_TYPE_SHIELD", "ITEM_TYPE_TOOLS",
    "ITEM_TYPE_VALUABLE", "ITEM_TYPE_AMMO", "ITEM_TYPE_AXE", "ITEM_TYPE_CLUB",
    "ITEM_TYPE_DISTANCE", "ITEM_TYPE_SWORD", "ITEM_TYPE_WAND", "ITEM_TYPE_CREATUREPRODUCT",
    "ITEM_TYPE_RETRIEVE", "ITEM_TYPE_GOLD", "ITEM_TYPE_UNASSIGNED",
)

private val TILE_STATE_ENUM_NAMES = listOf(
    "TILESTATE_NONE", "TILESTATE_PROTECTIONZONE", "TILESTATE_NOPVPZONE", "TILESTATE_NOLOGOUT",
    "TILESTATE_PVPZONE", "TILESTATE_FLOORCHANGE", "TILESTATE_FLOORCHANGE_DOWN",
    "TILESTATE_FLOORCHANGE_NORTH", "TILESTATE_FLOORCHANGE_SOUTH", "TILESTATE_FLOORCHANGE_EAST",
    "TILESTATE_FLOORCHANGE_WEST", "TILESTATE_TELEPORT", "TILESTATE_MAGICFIELD",
    "TILESTATE_MAILBOX", "TILESTATE_TRASHHOLDER", "TILESTATE_BED", "TILESTATE_DEPOT",
    "TILESTATE_BLOCKSOLID", "TILESTATE_BLOCKPATH", "TILESTATE_IMMOVABLEBLOCKSOLID",
    "TILESTATE_IMMOVABLEBLOCKPATH", "TILESTATE_IMMOVABLENOFIELDBLOCKPATH",
    "TILESTATE_NOFIELDBLOCKPATH", "TILESTATE_FLOORCHANGE_SOUTH_ALT",
    "TILESTATE_FLOORCHANGE_EAST_ALT", "TILESTATE_SUPPORTS_HANGABLE",
)

private val RETURN_VALUE_ENUM_NAMES = listOf(
    "NOERROR", "NOTBOUGHTINSTORE", "ITEMCANNOTBEMOVEDTHERE", "ITEMCANNOTBEMOVEDPOUCH",
    "NOTPOSSIBLE", "NOTENOUGHROOM", "PLAYERISPZLOCKED", "PLAYERISNOTINVITED", "CANNOTTHROW",
    "THEREISNOWAY", "DESTINATIONOUTOFREACH", "CREATUREBLOCK", "NOTMOVABLE", "DROPTWOHANDEDITEM",
    "BOTHHANDSNEEDTOBEFREE", "CANONLYUSEONEWEAPON", "NEEDEXCHANGE", "CANNOTBEDRESSED",
    "PUTTHISOBJECTINYOURHAND", "PUTTHISOBJECTINBOTHHANDS", "TOOFARAWAY", "FIRSTGODOWNSTAIRS",
    "FIRSTGOUPSTAIRS", "CONTAINERNOTENOUGHROOM", "NOTENOUGHCAPACITY", "CANNOTPICKUP",
    "THISISIMPOSSIBLE", "DEPOTISFULL", "CREATUREDOESNOTEXIST", "CANNOTUSETHISOBJECT",
    "PLAYERWITHTHISNAMEISNOTONLINE", "NOTREQUIREDLEVELTOUSERUNE", "YOUAREALREADYTRADING",
    "THISPLAYERISALREADYTRADING", "YOUMAYNOTLOGOUTDURINGAFIGHT", "DIRECTPLAYERSHOOT",
    "NOTENOUGHLEVEL", "NOTENOUGHMAGICLEVEL", "NOTENOUGHMANA", "NOTENOUGHSOUL", "YOUAREEXHAUSTED",
    "YOUCANNOTUSEOBJECTSTHATFAST", "PLAYERISNOTREACHABLE", "CANONLYUSETHISRUNEONCREATURES",
    "ACTIONNOTPERMITTEDINPROTECTIONZONE", "YOUMAYNOTATTACKTHISPLAYER",
    "YOUMAYNOTATTACKAPERSONINPROTECTIONZONE", "YOUMAYNOTATTACKAPERSONWHILEINPROTECTIONZONE",
    "YOUMAYNOTATTACKTHISCREATURE", "YOUCANONLYUSEITONCREATURES", "CREATUREISNOTREACHABLE",
    "TURNSECUREMODETOATTACKUNMARKEDPLAYERS", "YOUNEEDPREMIUMACCOUNT", "YOUNEEDTOLEARNTHISSPELL",
    "YOURVOCATIONCANNOTUSETHISSPELL", "YOUNEEDAWEAPONTOUSETHISSPELL",
    "PLAYERISPZLOCKEDLEAVEPVPZONE", "PLAYERISPZLOCKEDENTERPVPZONE",
    "ACTIONNOTPERMITTEDINANOPVPZONE", "YOUCANNOTLOGOUTHERE", "YOUNEEDAMAGICITEMTOCASTSPELL",
    "CANNOTCONJUREITEMHERE", "YOUNEEDTOSPLITYOURSPEARS", "NAMEISTOOAMBIGUOUS",
    "CANONLYUSEONESHIELD", "NOPARTYMEMBERSINRANGE", "YOUARENOTTHEOWNER", "TRADEPLAYERFARAWAY",
    "YOUDONTOWNTHISHOUSE", "TRADEPLAYERALREADYOWNSAHOUSE", "TRADEPLAYERHIGHESTBIDDER",
    "YOUCANNOTTRADETHISHOUSE", "YOUDONTHAVEREQUIREDPROFESSION", "NOTENOUGHFISTLEVEL",
    "NOTENOUGHCLUBLEVEL", "NOTENOUGHSWORDLEVEL", "NOTENOUGHAXELEVEL", "NOTENOUGHDISTANCELEVEL",
    "NOTENOUGHSHIELDLEVEL", "NOTENOUGHFISHLEVEL", "REWARDCHESTISEMPTY", "CONTACTADMINISTRATOR",
    "ITEMISNOTYOURS", "ITEMUNTRADEABLE",
)

private val COMBAT_ORIGIN_ENUM_NAMES = listOf(
    "ORIGIN_NONE" to CombatOrigin.NONE,
    "ORIGIN_CONDITION" to CombatOrigin.CONDITION,
    "ORIGIN_SPELL" to CombatOrigin.SPELL,
    "ORIGIN_MELEE" to CombatOrigin.MELEE,
    "ORIGIN_FIST" to CombatOrigin.FIST,
    "ORIGIN_RANGED" to CombatOrigin.RANGED,
)

private val FIGHT_MODE_ENUM_NAMES = listOf(
    "FIGHTMODE_ATTACK" to 1,
    "FIGHTMODE_BALANCED" to 2,
    "FIGHTMODE_DEFENSE" to 3,
)

private val SPELL_TYPE_ENUM_NAMES = listOf(
    "SPELL_INSTANT" to SpellType.SPELL_INSTANT.value,
    "SPELL_RUNE" to SpellType.SPELL_RUNE.value,
)

private val GAME_STATE_ENUM_NAMES = listOf(
    "GAME_STATE_STARTUP", "GAME_STATE_INIT", "GAME_STATE_NORMAL", "GAME_STATE_CLOSED",
    "GAME_STATE_SHUTDOWN", "GAME_STATE_CLOSING", "GAME_STATE_MAINTAIN",
)

private val CREATURE_EVENT_ENUM_NAMES = listOf(
    "CREATURE_EVENT_NONE", "CREATURE_EVENT_LOGIN", "CREATURE_EVENT_LOGOUT", "CREATURE_EVENT_THINK",
    "CREATURE_EVENT_PREPAREDEATH", "CREATURE_EVENT_DEATH", "CREATURE_EVENT_KILL",
    "CREATURE_EVENT_ADVANCE", "CREATURE_EVENT_MODALWINDOW", "CREATURE_EVENT_TEXTEDIT",
    "CREATURE_EVENT_HEALTHCHANGE", "CREATURE_EVENT_MANACHANGE", "CREATURE_EVENT_EXTENDED_OPCODE",
)

private val DIRECTION_ENUM_NAMES = listOf(
    "DIRECTION_NORTH", "DIRECTION_EAST", "DIRECTION_SOUTH", "DIRECTION_WEST",
    "DIRECTION_SOUTHWEST", "DIRECTION_SOUTHEAST", "DIRECTION_NORTHWEST", "DIRECTION_NORTHEAST",
)

/**
 * Enum-name subsets registered by the remaining `LuaEnums::init` group functions
 * (src/lua/functions/core/game/lua_enums.cpp). Values are transcribed from the
 * C++ enums each `registerEnum` reads; the registered name lists are exact.
 */
private val ACCOUNT_GROUP_ENUM_NAMES = listOf(
    "ACCOUNT_TYPE_NORMAL" to 1,
    "ACCOUNT_TYPE_TUTOR" to 2,
    "ACCOUNT_TYPE_SENIORTUTOR" to 3,
    "ACCOUNT_TYPE_GAMEMASTER" to 4,
    "ACCOUNT_TYPE_GOD" to 6,
    "GROUP_TYPE_NORMAL" to 1,
    "GROUP_TYPE_TUTOR" to 2,
    "GROUP_TYPE_SENIORTUTOR" to 3,
    "GROUP_TYPE_GAMEMASTER" to 4,
    "GROUP_TYPE_COMMUNITYMANAGER" to 5,
    "GROUP_TYPE_GOD" to 6,
)

private val DAILY_REWARD_ENUM_NAMES = listOf(
    "DAILY_REWARD_COLLECTED" to 0,
    "DAILY_REWARD_NOTCOLLECTED" to 1,
    "DAILY_REWARD_NOTAVAILABLE" to 2,
)

private val BUG_CATEGORY_ENUM_NAMES = listOf(
    "BUG_CATEGORY_MAP" to 0,
    "BUG_CATEGORY_TYPO" to 1,
    "BUG_CATEGORY_TECHNICAL" to 2,
    "BUG_CATEGORY_OTHER" to 3,
)

private val REPORT_TYPE_ENUM_NAMES = listOf(
    "REPORT_TYPE_NAME" to 0,
    "REPORT_TYPE_STATEMENT" to 1,
    "REPORT_TYPE_BOT" to 2,
)

private val REPORT_REASON_ENUM_NAMES = listOf(
    "REPORT_REASON_NAMEINAPPROPRIATE" to 0,
    "REPORT_REASON_NAMEPOORFORMATTED" to 1,
    "REPORT_REASON_NAMEADVERTISING" to 2,
    "REPORT_REASON_NAMEUNFITTING" to 3,
    "REPORT_REASON_NAMERULEVIOLATION" to 4,
    "REPORT_REASON_INSULTINGSTATEMENT" to 5,
    "REPORT_REASON_SPAMMING" to 6,
    "REPORT_REASON_ADVERTISINGSTATEMENT" to 7,
    "REPORT_REASON_UNFITTINGSTATEMENT" to 8,
    "REPORT_REASON_LANGUAGESTATEMENT" to 9,
    "REPORT_REASON_DISCLOSURE" to 10,
    "REPORT_REASON_RULEVIOLATION" to 11,
    "REPORT_REASON_STATEMENT_BUGABUSE" to 12,
    "REPORT_REASON_UNOFFICIALSOFTWARE" to 13,
    "REPORT_REASON_PRETENDING" to 14,
    "REPORT_REASON_HARASSINGOWNERS" to 15,
    "REPORT_REASON_FALSEINFO" to 16,
    "REPORT_REASON_ACCOUNTSHARING" to 17,
    "REPORT_REASON_STEALINGDATA" to 18,
    "REPORT_REASON_SERVICEATTACKING" to 19,
    "REPORT_REASON_SERVICEAGREEMENT" to 20,
)

private val CALLBACK_PARAM_ENUM_NAMES = listOf(
    "CALLBACK_PARAM_LEVELMAGICVALUE" to 0,
    "CALLBACK_PARAM_SKILLVALUE" to 1,
    "CALLBACK_PARAM_TARGETTILE" to 2,
    "CALLBACK_PARAM_TARGETCREATURE" to 3,
    "CALLBACK_PARAM_CHAINVALUE" to 4,
    "CALLBACK_PARAM_CHAINPICKER" to 5,
)

private val COMBAT_PARAM_ENUM_NAMES = listOf(
    "COMBAT_PARAM_TYPE" to 0,
    "COMBAT_PARAM_EFFECT" to 1,
    "COMBAT_PARAM_DISTANCEEFFECT" to 2,
    "COMBAT_PARAM_BLOCKSHIELD" to 3,
    "COMBAT_PARAM_BLOCKARMOR" to 4,
    "COMBAT_PARAM_TARGETCASTERORTOPMOST" to 5,
    "COMBAT_PARAM_CREATEITEM" to 6,
    "COMBAT_PARAM_AGGRESSIVE" to 7,
    "COMBAT_PARAM_DISPEL" to 8,
    "COMBAT_PARAM_USECHARGES" to 9,
    "COMBAT_PARAM_CASTSOUND" to 10,
    "COMBAT_PARAM_IMPACTSOUND" to 11,
    "COMBAT_PARAM_CHAIN_EFFECT" to 12,
)

private val COMBAT_FORMULA_ENUM_NAMES = listOf(
    "COMBAT_FORMULA_UNDEFINED" to 0,
    "COMBAT_FORMULA_LEVELMAGIC" to 1,
    "COMBAT_FORMULA_SKILL" to 2,
    "COMBAT_FORMULA_DAMAGE" to 3,
)

private val VIRTUE_ENUM_NAMES = listOf(
    "Virtue_None" to 0,
    "Virtue_Harmony" to 1,
    "Virtue_Justice" to 2,
    "Virtue_Sustain" to 3,
)

private val MONK_SPELL_ENUM_NAMES = listOf(
    "MonkSpell_None" to 0,
    "MonkSpell_Builder" to 1,
    "MonkSpell_Spender" to 2,
)

private val FACTION_ENUM_NAMES = listOf(
    "FACTION_DEFAULT" to 0,
    "FACTION_PLAYER" to 1,
    "FACTION_LION" to 2,
    "FACTION_LIONUSURPERS" to 3,
    "FACTION_MARID" to 4,
    "FACTION_EFREET" to 5,
    "FACTION_DEEPLING" to 6,
    "FACTION_DEATHLING" to 7,
    "FACTION_ANUMA" to 8,
    "FACTION_FAFNAR" to 9,
    "FACTION_LAST" to 9,
)

private val CONST_ME_ENUM_NAMES = listOf(
    "CONST_ME_NONE" to 0,
    "CONST_ME_DRAWBLOOD" to 1,
    "CONST_ME_LOSEENERGY" to 2,
    "CONST_ME_POFF" to 3,
    "CONST_ME_BLOCKHIT" to 4,
    "CONST_ME_EXPLOSIONAREA" to 5,
    "CONST_ME_EXPLOSIONHIT" to 6,
    "CONST_ME_FIREAREA" to 7,
    "CONST_ME_YELLOW_RINGS" to 8,
    "CONST_ME_GREEN_RINGS" to 9,
    "CONST_ME_HITAREA" to 10,
    "CONST_ME_TELEPORT" to 11,
    "CONST_ME_ENERGYHIT" to 12,
    "CONST_ME_MAGIC_BLUE" to 13,
    "CONST_ME_MAGIC_RED" to 14,
    "CONST_ME_MAGIC_GREEN" to 15,
    "CONST_ME_HITBYFIRE" to 16,
    "CONST_ME_HITBYPOISON" to 17,
    "CONST_ME_MORTAREA" to 18,
    "CONST_ME_SOUND_GREEN" to 19,
    "CONST_ME_SOUND_RED" to 20,
    "CONST_ME_POISONAREA" to 21,
    "CONST_ME_SOUND_YELLOW" to 22,
    "CONST_ME_SOUND_PURPLE" to 23,
    "CONST_ME_SOUND_BLUE" to 24,
    "CONST_ME_SOUND_WHITE" to 25,
    "CONST_ME_BUBBLES" to 26,
    "CONST_ME_CRAPS" to 27,
    "CONST_ME_GIFT_WRAPS" to 28,
    "CONST_ME_FIREWORK_YELLOW" to 29,
    "CONST_ME_FIREWORK_RED" to 30,
    "CONST_ME_FIREWORK_BLUE" to 31,
    "CONST_ME_STUN" to 32,
    "CONST_ME_SLEEP" to 33,
    "CONST_ME_WATERCREATURE" to 34,
    "CONST_ME_GROUNDSHAKER" to 35,
    "CONST_ME_HEARTS" to 36,
    "CONST_ME_FIREATTACK" to 37,
    "CONST_ME_ENERGYAREA" to 38,
    "CONST_ME_SMALLCLOUDS" to 39,
    "CONST_ME_HOLYDAMAGE" to 40,
    "CONST_ME_BIGCLOUDS" to 41,
    "CONST_ME_ICEAREA" to 42,
    "CONST_ME_ICETORNADO" to 43,
    "CONST_ME_ICEATTACK" to 44,
    "CONST_ME_STONES" to 45,
    "CONST_ME_SMALLPLANTS" to 46,
    "CONST_ME_CARNIPHILA" to 47,
    "CONST_ME_PURPLEENERGY" to 48,
    "CONST_ME_YELLOWENERGY" to 49,
    "CONST_ME_HOLYAREA" to 50,
    "CONST_ME_BIGPLANTS" to 51,
    "CONST_ME_CAKE" to 52,
    "CONST_ME_GIANTICE" to 53,
    "CONST_ME_WATERSPLASH" to 54,
    "CONST_ME_PLANTATTACK" to 55,
    "CONST_ME_TUTORIALARROW" to 56,
    "CONST_ME_TUTORIALSQUARE" to 57,
    "CONST_ME_MIRRORHORIZONTAL" to 58,
    "CONST_ME_MIRRORVERTICAL" to 59,
    "CONST_ME_SKULLHORIZONTAL" to 60,
    "CONST_ME_SKULLVERTICAL" to 61,
    "CONST_ME_ASSASSIN" to 62,
    "CONST_ME_STEPSHORIZONTAL" to 63,
    "CONST_ME_BLOODYSTEPS" to 64,
    "CONST_ME_STEPSVERTICAL" to 65,
    "CONST_ME_YALAHARIGHOST" to 66,
    "CONST_ME_BATS" to 67,
    "CONST_ME_SMOKE" to 68,
    "CONST_ME_INSECTS" to 69,
    "CONST_ME_DRAGONHEAD" to 70,
    "CONST_ME_ORCSHAMAN" to 71,
    "CONST_ME_ORCSHAMAN_FIRE" to 72,
    "CONST_ME_THUNDER" to 73,
    "CONST_ME_FERUMBRAS" to 74,
    "CONST_ME_CONFETTI_HORIZONTAL" to 75,
    "CONST_ME_CONFETTI_VERTICAL" to 76,
    "CONST_ME_BLACKSMOKE" to 158,
    "CONST_ME_REDSMOKE" to 167,
    "CONST_ME_YELLOWSMOKE" to 168,
    "CONST_ME_GREENSMOKE" to 169,
    "CONST_ME_PURPLESMOKE" to 170,
    "CONST_ME_EARLY_THUNDER" to 171,
    "CONST_ME_RAGIAZ_BONECAPSULE" to 172,
    "CONST_ME_CRITICAL_DAMAGE" to 173,
    "CONST_ME_PLUNGING_FISH" to 175,
    "CONST_ME_BLUE_ENERGY_SPARK" to 176,
    "CONST_ME_ORANGE_ENERGY_SPARK" to 177,
    "CONST_ME_GREEN_ENERGY_SPARK" to 178,
    "CONST_ME_PINK_ENERGY_SPARK" to 179,
    "CONST_ME_WHITE_ENERGY_SPARK" to 180,
    "CONST_ME_YELLOW_ENERGY_SPARK" to 181,
    "CONST_ME_MAGIC_POWDER" to 182,
    "CONST_ME_PIXIE_EXPLOSION" to 184,
    "CONST_ME_PIXIE_COMING" to 185,
    "CONST_ME_PIXIE_GOING" to 186,
    "CONST_ME_STORM" to 188,
    "CONST_ME_STONE_STORM" to 189,
    "CONST_ME_BLUE_GHOST" to 191,
    "CONST_ME_PINK_VORTEX" to 193,
    "CONST_ME_TREASURE_MAP" to 194,
    "CONST_ME_PINK_BEAM" to 195,
    "CONST_ME_GREEN_FIREWORKS" to 196,
    "CONST_ME_ORANGE_FIREWORKS" to 197,
    "CONST_ME_PINK_FIREWORKS" to 198,
    "CONST_ME_BLUE_FIREWORKS" to 199,
    "CONST_ME_SUPREME_CUBE" to 201,
    "CONST_ME_BLACK_BLOOD" to 202,
    "CONST_ME_PRISMATIC_SPARK" to 203,
    "CONST_ME_THAIAN" to 204,
    "CONST_ME_THAIAN_GHOST" to 205,
    "CONST_ME_GHOST_SMOKE" to 206,
    "CONST_ME_WATER_BLOCK_FLOATING" to 208,
    "CONST_ME_WATER_BLOCK" to 209,
    "CONST_ME_ROOTS" to 210,
    "CONST_ME_GHOSTLY_SCRATCH" to 213,
    "CONST_ME_GHOSTLY_BITE" to 214,
    "CONST_ME_BIG_SCRATCH" to 215,
    "CONST_ME_SLASH" to 216,
    "CONST_ME_BITE" to 217,
    "CONST_ME_CHIVALRIOUS_CHALLENGE" to 219,
    "CONST_ME_DIVINE_DAZZLE" to 220,
    "CONST_ME_ELECTRICALSPARK" to 221,
    "CONST_ME_PURPLETELEPORT" to 222,
    "CONST_ME_REDTELEPORT" to 223,
    "CONST_ME_ORANGETELEPORT" to 224,
    "CONST_ME_GREYTELEPORT" to 225,
    "CONST_ME_LIGHTBLUETELEPORT" to 226,
    "CONST_ME_FATAL" to 230,
    "CONST_ME_DODGE" to 231,
    "CONST_ME_HOURGLASS" to 232,
    "CONST_ME_DAZZLING" to 233,
    "CONST_ME_SPARKLING" to 234,
    "CONST_ME_FERUMBRAS_1" to 235,
    "CONST_ME_GAZHARAGOTH" to 236,
    "CONST_ME_MAD_MAGE" to 237,
    "CONST_ME_HORESTIS" to 238,
    "CONST_ME_DEVOVORGA" to 239,
    "CONST_ME_FERUMBRAS_2" to 240,
    "CONST_ME_WHITE_SMOKE" to 241,
    "CONST_ME_WHITE_SMOKES" to 242,
    "CONST_ME_WATER_DROP" to 243,
    "CONST_ME_AVATAR_APPEAR" to 244,
    "CONST_ME_DIVINE_GRENADE" to 245,
    "CONST_ME_DIVINE_EMPOWERMENT" to 246,
    "CONST_ME_WATER_FLOATING_THRASH" to 247,
    "CONST_ME_AGONY" to 249,
    "CONST_ME_LOOT_HIGHLIGHT" to 252,
    "CONST_ME_MELTING_CREAM" to 263,
    "CONST_ME_REAPER" to 264,
    "CONST_ME_POWERFUL_HEARTS" to 265,
    "CONST_ME_CREAM" to 266,
    "CONST_ME_GENTLE_BUBBLE" to 267,
    "CONST_ME_STARBURST" to 268,
    "CONST_ME_SIRUP" to 269,
    "CONST_ME_CACAO" to 270,
    "CONST_ME_CANDY_FLOSS" to 271,
    "CONST_ME_HITAREA_GREEN" to 272,
    "CONST_ME_HITAREA_RED" to 273,
    "CONST_ME_HITAREA_BLUE" to 274,
    "CONST_ME_HITAREA_ORANGE" to 275,
    "CONST_ME_WHIRLWIND_BLOW_WHITE" to 276,
    "CONST_ME_WHIRLWIND_BLOW_GREEN" to 277,
    "CONST_ME_WHIRLWIND_BLOW_PINK" to 278,
    "CONST_ME_PULSE_WHITE" to 279,
    "CONST_ME_PULSE_GREEN" to 280,
    "CONST_ME_PULSE_PINK" to 281,
    "CONST_ME_CLAW_WHITE" to 282,
    "CONST_ME_CLAW_GREEN" to 283,
    "CONST_ME_CLAW_PINK" to 284,
    "CONST_ME_BLOW_WHITE" to 285,
    "CONST_ME_BLOW_GREEN" to 286,
    "CONST_ME_BLOW_BLUE" to 287,
    "CONST_ME_BLOW_PINK" to 288,
    "CONST_ME_OUTBURST_WHITE" to 289,
    "CONST_ME_OUTBURST_GREEN" to 290,
    "CONST_ME_OUTBURST_YELLOW" to 291,
    "CONST_ME_INK_EXPLOSION" to 292,
    "CONST_ME_PAPER_PLANE" to 293,
    "CONST_ME_WOODEN_STAKES" to 294,
    "CONST_ME_FIRE_SPARKLES" to 295,
    "CONST_ME_OPENING_MAGIC_BOOK" to 296,
    "CONST_ME_GRAY_ELECTRIC_SPARK" to 301,
    "CONST_ME_GREEN_ELECTRIC_SPARK" to 302,
    "CONST_ME_PURPLE_ELECTRIC_SPARK" to 303,
    "CONST_ME_LAST" to 304,
)

private val CONST_ANI_ENUM_NAMES = listOf(
    "CONST_ANI_NONE" to 0,
    "CONST_ANI_SPEAR" to 1,
    "CONST_ANI_BOLT" to 2,
    "CONST_ANI_ARROW" to 3,
    "CONST_ANI_FIRE" to 4,
    "CONST_ANI_ENERGY" to 5,
    "CONST_ANI_POISONARROW" to 6,
    "CONST_ANI_BURSTARROW" to 7,
    "CONST_ANI_THROWINGSTAR" to 8,
    "CONST_ANI_THROWINGKNIFE" to 9,
    "CONST_ANI_SMALLSTONE" to 10,
    "CONST_ANI_DEATH" to 11,
    "CONST_ANI_LARGEROCK" to 12,
    "CONST_ANI_SNOWBALL" to 13,
    "CONST_ANI_POWERBOLT" to 14,
    "CONST_ANI_POISON" to 15,
    "CONST_ANI_INFERNALBOLT" to 16,
    "CONST_ANI_HUNTINGSPEAR" to 17,
    "CONST_ANI_ENCHANTEDSPEAR" to 18,
    "CONST_ANI_REDSTAR" to 19,
    "CONST_ANI_GREENSTAR" to 20,
    "CONST_ANI_ROYALSPEAR" to 21,
    "CONST_ANI_SNIPERARROW" to 22,
    "CONST_ANI_ONYXARROW" to 23,
    "CONST_ANI_PIERCINGBOLT" to 24,
    "CONST_ANI_WHIRLWINDSWORD" to 25,
    "CONST_ANI_WHIRLWINDAXE" to 26,
    "CONST_ANI_WHIRLWINDCLUB" to 27,
    "CONST_ANI_ETHEREALSPEAR" to 28,
    "CONST_ANI_ICE" to 29,
    "CONST_ANI_EARTH" to 30,
    "CONST_ANI_HOLY" to 31,
    "CONST_ANI_SUDDENDEATH" to 32,
    "CONST_ANI_FLASHARROW" to 33,
    "CONST_ANI_FLAMMINGARROW" to 34,
    "CONST_ANI_SHIVERARROW" to 35,
    "CONST_ANI_ENERGYBALL" to 36,
    "CONST_ANI_SMALLICE" to 37,
    "CONST_ANI_SMALLHOLY" to 38,
    "CONST_ANI_SMALLEARTH" to 39,
    "CONST_ANI_EARTHARROW" to 40,
    "CONST_ANI_EXPLOSION" to 41,
    "CONST_ANI_CAKE" to 42,
    "CONST_ANI_TARSALARROW" to 44,
    "CONST_ANI_VORTEXBOLT" to 45,
    "CONST_ANI_PRISMATICBOLT" to 48,
    "CONST_ANI_CRYSTALLINEARROW" to 49,
    "CONST_ANI_DRILLBOLT" to 50,
    "CONST_ANI_ENVENOMEDARROW" to 51,
    "CONST_ANI_GLOOTHSPEAR" to 53,
    "CONST_ANI_SIMPLEARROW" to 54,
    "CONST_ANI_LEAFSTAR" to 56,
    "CONST_ANI_DIAMONDARROW" to 57,
    "CONST_ANI_SPECTRALBOLT" to 58,
    "CONST_ANI_ROYALSTAR" to 59,
    "CONST_ANI_CANDYCANE" to 61,
    "CONST_ANI_CHERRYBOMB" to 62,
    "CONST_ANI_WEAPONTYPE" to 254,
)

private val CONST_PROP_ENUM_NAMES = listOf(
    "CONST_PROP_BLOCKSOLID" to 0,
    "CONST_PROP_HASHEIGHT" to 1,
    "CONST_PROP_BLOCKPROJECTILE" to 2,
    "CONST_PROP_BLOCKPATH" to 3,
    "CONST_PROP_ISVERTICAL" to 4,
    "CONST_PROP_ISHORIZONTAL" to 5,
    "CONST_PROP_MOVABLE" to 6,
    "CONST_PROP_IMMOVABLEBLOCKSOLID" to 7,
    "CONST_PROP_IMMOVABLEBLOCKPATH" to 8,
    "CONST_PROP_IMMOVABLENOFIELDBLOCKPATH" to 9,
    "CONST_PROP_NOFIELDBLOCKPATH" to 10,
    "CONST_PROP_SUPPORTHANGABLE" to 11,
)

private val CONST_SLOT_ENUM_NAMES = listOf(
    "CONST_SLOT_FIRST" to 1,
    "CONST_SLOT_HEAD" to 1,
    "CONST_SLOT_NECKLACE" to 2,
    "CONST_SLOT_BACKPACK" to 3,
    "CONST_SLOT_ARMOR" to 4,
    "CONST_SLOT_RIGHT" to 5,
    "CONST_SLOT_LEFT" to 6,
    "CONST_SLOT_LEGS" to 7,
    "CONST_SLOT_FEET" to 8,
    "CONST_SLOT_RING" to 9,
    "CONST_SLOT_AMMO" to 10,
    "CONST_SLOT_STORE_INBOX" to 11,
    "CONST_SLOT_LAST" to 11,
)

private val SLOTP_ENUM_NAMES = listOf(
    "SLOTP_WHEREEVER" to 0xFFFFFFFFL,
    "SLOTP_HEAD" to (1L shl 0),
    "SLOTP_NECKLACE" to (1L shl 1),
    "SLOTP_BACKPACK" to (1L shl 2),
    "SLOTP_ARMOR" to (1L shl 3),
    "SLOTP_RIGHT" to (1L shl 4),
    "SLOTP_LEFT" to (1L shl 5),
    "SLOTP_LEGS" to (1L shl 6),
    "SLOTP_FEET" to (1L shl 7),
    "SLOTP_RING" to (1L shl 8),
    "SLOTP_AMMO" to (1L shl 9),
    "SLOTP_DEPOT" to (1L shl 10),
    "SLOTP_TWO_HAND" to (1L shl 11),
)

private val CYLINDER_FLAG_ENUM_NAMES = listOf(
    "FLAG_NOLIMIT" to (1 shl 0),
    "FLAG_IGNOREBLOCKITEM" to (1 shl 1),
    "FLAG_IGNOREBLOCKCREATURE" to (1 shl 2),
    "FLAG_CHILDISOWNER" to (1 shl 3),
    "FLAG_PATHFINDING" to (1 shl 4),
    "FLAG_IGNOREFIELDDAMAGE" to (1 shl 5),
    "FLAG_IGNORENOTMOVABLE" to (1 shl 6),
    "FLAG_IGNOREAUTOSTACK" to (1 shl 7),
    "FLAG_LOOTPOUCH" to (1 shl 9),
)

private val CHARM_ENUM_NAMES = listOf(
    "CHARM_OFFENSIVE" to 1,
    "CHARM_DEFENSIVE" to 2,
    "CHARM_PASSIVE" to 3,
    "CHARM_MAJOR" to 1,
    "CHARM_MINOR" to 2,
    "CHARM_GUT" to 14,
    "CHARM_SCAVENGE" to 13,
)

private val ACCESS_LIST_ENUM_NAMES = listOf(
    "GUEST_LIST" to 0x100,
    "SUBOWNER_LIST" to 0x101,
)

private val PLAYER_SEX_ENUM_NAMES = listOf(
    "PLAYERSEX_FEMALE" to 0,
    "PLAYERSEX_MALE" to 1,
)

private val PLAYER_PRONOUN_ENUM_NAMES = listOf(
    "PLAYERPRONOUN_UNSET" to 0,
    "PLAYERPRONOUN_THEY" to 1,
    "PLAYERPRONOUN_SHE" to 2,
    "PLAYERPRONOUN_HE" to 3,
    "PLAYERPRONOUN_ZE" to 4,
    "PLAYERPRONOUN_NAME" to 5,
)

private val RESPAWN_PERIOD_ENUM_NAMES = listOf(
    "RESPAWNPERIOD_ALL" to 0,
    "RESPAWNPERIOD_DAY" to 1,
    "RESPAWNPERIOD_NIGHT" to 2,
)

private val LIGHT_STATE_ENUM_NAMES = listOf(
    "LIGHT_STATE_DAY" to 0,
    "LIGHT_STATE_NIGHT" to 1,
    "LIGHT_STATE_SUNSET" to 2,
    "LIGHT_STATE_SUNRISE" to 3,
)

private val WORLD_CONSTANT_ENUM_NAMES = listOf(
    "STORAGEVALUE_EMOTE" to 30008,
    "IMMOVABLE_ACTION_ID" to 100,
    "MAX_LOOTCHANCE" to 100000,
    "VOCATION_NONE" to 0,
)

private val CLIENT_OS_ENUM_NAMES = listOf(
    "CLIENTOS_NONE" to 0,
    "CLIENTOS_LINUX" to 1,
    "CLIENTOS_WINDOWS" to 2,
    "CLIENTOS_FLASH" to 3,
    "CLIENTOS_NEW_LINUX" to 4,
    "CLIENTOS_NEW_WINDOWS" to 5,
    "CLIENTOS_NEW_MAC" to 6,
    "CLIENTOS_OTCLIENT_LINUX" to 10,
    "CLIENTOS_OTCLIENT_WINDOWS" to 11,
    "CLIENTOS_OTCLIENT_MAC" to 12,
    "CLIENTOS_OTCLIENTV8_LINUX" to 20,
    "CLIENTOS_OTCLIENTV8_WINDOWS" to 21,
    "CLIENTOS_OTCLIENTV8_MAC" to 22,
    "CLIENTOS_OTCLIENTV8_ANDROID" to 23,
    "CLIENTOS_OTCLIENTV8_IOS" to 24,
    "CLIENTOS_OTCLIENTV8_WEB" to 25,
)

private val WEBHOOK_COLOR_ENUM_NAMES = listOf(
    "WEBHOOK_COLOR_GREEN" to 0x00FF00,
    "WEBHOOK_COLOR_RED" to 0xFF0000,
    "WEBHOOK_COLOR_YELLOW" to 0xFFFF00,
    "WEBHOOK_COLOR_BLUE" to 0x0000FF,
)

private val SCREENSHOT_TYPE_ENUM_NAMES = listOf(
    "SCREENSHOT_TYPE_NONE" to 0,
    "SCREENSHOT_TYPE_ACHIEVEMENT" to 1,
    "SCREENSHOT_TYPE_BESTIARYENTRYCOMPLETED" to 2,
    "SCREENSHOT_TYPE_BESTIARYENTRYUNLOCKED" to 3,
    "SCREENSHOT_TYPE_BOSSDEFEATED" to 4,
    "SCREENSHOT_TYPE_DEATHPVE" to 5,
    "SCREENSHOT_TYPE_DEATHPVP" to 6,
    "SCREENSHOT_TYPE_LEVELUP" to 7,
    "SCREENSHOT_TYPE_PLAYERKILLASSIST" to 8,
    "SCREENSHOT_TYPE_PLAYERKILL" to 9,
    "SCREENSHOT_TYPE_PLAYERATTACKING" to 10,
    "SCREENSHOT_TYPE_TREASUREFOUND" to 11,
    "SCREENSHOT_TYPE_SKILLUP" to 12,
    "SCREENSHOT_TYPE_GIFTOFLIFE" to 13,
)

private val TEXT_COLOR_ENUM_NAMES = listOf(
    "TEXTCOLOR_BLUE" to 5,
    "TEXTCOLOR_LIGHTGREEN" to 30,
    "TEXTCOLOR_LIGHTBLUE" to 35,
    "TEXTCOLOR_DARKBROWN" to 78,
    "TEXTCOLOR_MAYABLUE" to 95,
    "TEXTCOLOR_DARKRED" to 108,
    "TEXTCOLOR_LIGHTGREY" to 129,
    "TEXTCOLOR_SKYBLUE" to 143,
    "TEXTCOLOR_PURPLE" to 154,
    "TEXTCOLOR_ELECTRICPURPLE" to 155,
    "TEXTCOLOR_RED" to 180,
    "TEXTCOLOR_PASTELRED" to 194,
    "TEXTCOLOR_ORANGE" to 198,
    "TEXTCOLOR_YELLOW" to 210,
    "TEXTCOLOR_WHITE_EXP" to 215,
    "TEXTCOLOR_NONE" to 255,
)

private val SPEECH_BUBBLE_ENUM_NAMES = listOf(
    "SPEECHBUBBLE_NONE" to 0,
    "SPEECHBUBBLE_NORMAL" to 1,
    "SPEECHBUBBLE_TRADE" to 2,
    "SPEECHBUBBLE_QUEST" to 3,
    "SPEECHBUBBLE_QUESTTRADER" to 4,
    "SPEECHBUBBLE_SAILOR" to 5,
    "SPEECHBUBBLE_BANKER" to 2,
    "SPEECHBUBBLE_HIRELING" to 7,
)

private val MAP_MARK_ENUM_NAMES = listOf(
    "MAPMARK_TICK" to 0,
    "MAPMARK_QUESTION" to 1,
    "MAPMARK_EXCLAMATION" to 2,
    "MAPMARK_STAR" to 3,
    "MAPMARK_CROSS" to 4,
    "MAPMARK_TEMPLE" to 5,
    "MAPMARK_KISS" to 6,
    "MAPMARK_SHOVEL" to 7,
    "MAPMARK_SWORD" to 8,
    "MAPMARK_FLAG" to 9,
    "MAPMARK_LOCK" to 10,
    "MAPMARK_BAG" to 11,
    "MAPMARK_SKULL" to 12,
    "MAPMARK_DOLLAR" to 13,
    "MAPMARK_REDNORTH" to 14,
    "MAPMARK_REDSOUTH" to 15,
    "MAPMARK_REDEAST" to 16,
    "MAPMARK_REDWEST" to 17,
    "MAPMARK_GREENNORTH" to 18,
    "MAPMARK_GREENSOUTH" to 19,
)

private val ITEM_ID_ENUM_NAMES = listOf(
    "ITEM_BAG" to 2853,
    "ITEM_SHOPPING_BAG" to 21411,
    "ITEM_GOLD_COIN" to 3031,
    "ITEM_PLATINUM_COIN" to 3035,
    "ITEM_CRYSTAL_COIN" to 3043,
    "ITEM_STORE_COIN" to 22118,
    "ITEM_REWARD_CHEST" to 19250,
    "ITEM_REWARD_CONTAINER" to 19202,
    "ITEM_AMULETOFLOSS" to 3057,
    "ITEM_PARCEL" to 3503,
    "ITEM_PARCEL_STAMPED" to 3504,
    "ITEM_LABEL" to 3507,
    "ITEM_FIREFIELD_PVP_FULL" to 2118,
    "ITEM_FIREFIELD_PVP_MEDIUM" to 2119,
    "ITEM_FIREFIELD_PVP_SMALL" to 2120,
    "ITEM_FIREFIELD_PERSISTENT_FULL" to 2123,
    "ITEM_FIREFIELD_PERSISTENT_MEDIUM" to 2124,
    "ITEM_FIREFIELD_PERSISTENT_SMALL" to 2125,
    "ITEM_FIREFIELD_NOPVP" to 21465,
    "ITEM_POISONFIELD_PVP" to 105,
    "ITEM_POISONFIELD_PERSISTENT" to 2121,
    "ITEM_POISONFIELD_NOPVP" to 2134,
    "ITEM_ENERGYFIELD_PVP" to 2122,
    "ITEM_ENERGYFIELD_PERSISTENT" to 2126,
    "ITEM_ENERGYFIELD_NOPVP" to 2135,
    "ITEM_MAGICWALL" to 2128,
    "ITEM_MAGICWALL_PERSISTENT" to 2129,
    "ITEM_MAGICWALL_SAFE" to 10181,
    "ITEM_WILDGROWTH" to 2130,
    "ITEM_WILDGROWTH_PERSISTENT" to 3635,
    "ITEM_WILDGROWTH_SAFE" to 10182,
    "ITEM_EXERCISE_START" to 28552,
    "ITEM_EXERCISE_END" to 28557,
    "ITEM_HEALTH_CASK_START" to 25879,
    "ITEM_HEALTH_CASK_END" to 25883,
    "ITEM_MANA_CASK_START" to 25889,
    "ITEM_MANA_CASK_END" to 25893,
    "ITEM_SPIRIT_CASK_START" to 25899,
    "ITEM_SPIRIT_CASK_END" to 25902,
    "ITEM_KEG_START" to 25903,
    "ITEM_KEG_END" to 25914,
    "ITEM_STONE_SKIN_AMULET" to 3081,
    "ITEM_OLD_DIAMOND_ARROW" to 25757,
    "ITEM_DIAMOND_ARROW" to 35901,
    "ITEM_GOLD_POUCH" to 23721,
    "ITEM_STORE_INBOX" to 23396,
    "ITEM_DIVINE_EMPOWERMENT" to 40450,
    "ITEM_FORGE_SLIVER" to 37109,
    "ITEM_FORGE_CORE" to 37110,
    "ITEM_PRIMAL_POD" to 39176,
    "ITEM_DECORATION_KIT" to 23398,
    "HIRELING_LAMP" to 29432,
)

private val NPCS_EVENT_ENUM_NAMES = listOf(
    "NPCS_EVENT_THINK" to 1,
    "NPCS_EVENT_APPEAR" to 2,
    "NPCS_EVENT_DISAPPEAR" to 3,
    "NPCS_EVENT_MOVE" to 4,
    "NPCS_EVENT_SAY" to 5,
    "NPCS_EVENT_PLAYER_BUY" to 6,
    "NPCS_EVENT_PLAYER_SELL" to 7,
    "NPCS_EVENT_PLAYER_CHECK_ITEM" to 8,
    "NPCS_EVENT_PLAYER_CLOSE_CHANNEL" to 9,
)

private val FORGE_CLASSIFICATION_ENUM_NAMES = listOf(
    "FORGE_NORMAL_MONSTER" to 0,
    "FORGE_INFLUENCED_MONSTER" to 1,
    "FORGE_FIENDISH_MONSTER" to 2,
)

private val ATTR_SUB_ID_ENUM_NAMES = listOf(
    "AttrSubId_None" to 0,
    "AttrSubId_TrainParty" to 1,
    "AttrSubId_ProtectParty" to 2,
    "AttrSubId_EnchantParty" to 3,
    "AttrSubId_JeanPierreMagic" to 4,
    "AttrSubId_JeanPierreMelee" to 5,
    "AttrSubId_JeanPierreDistance" to 6,
    "AttrSubId_JeanPierreDefense" to 7,
    "AttrSubId_JeanPierreFishing" to 8,
    "AttrSubId_BloodRageProtector" to 9,
    "AttrSubId_Sharpshooter" to 10,
    "AttrSubId_MentorOther" to 11,
    "AttrSubId_Sanctuary" to 12,
    "AttrSubId_VirtueOfJustice" to 13,
)

private val CONCOCTION_ENUM_NAMES = listOf(
    "Concoction_KooldownAid" to 36723,
    "Concoction_StaminaExtension" to 36725,
    "Concoction_StrikeEnhancement" to 36724,
    "Concoction_CharmUpgrade" to 36726,
    "Concoction_WealthDuplex" to 36727,
    "Concoction_BestiaryBetterment" to 36728,
    "Concoction_FireResilience" to 36729,
    "Concoction_IceResilience" to 36730,
    "Concoction_EarthResilience" to 36731,
    "Concoction_EnergyResilience" to 36732,
    "Concoction_HolyResilience" to 36733,
    "Concoction_DeathResilience" to 36734,
    "Concoction_PhysicalResilience" to 36735,
    "Concoction_FireAmplification" to 36736,
    "Concoction_IceAmplification" to 36737,
    "Concoction_EarthAmplification" to 36738,
    "Concoction_EnergyAmplification" to 36739,
    "Concoction_HolyAmplification" to 36740,
    "Concoction_DeathAmplification" to 36741,
    "Concoction_PhysicalAmplification" to 36742,
)

private val WHEEL_ENUM_NAMES = listOf(
    "WHEEL_INSTANT_BATTLE_INSTINCT" to 0,
    "WHEEL_INSTANT_BATTLE_HEALING" to 1,
    "WHEEL_INSTANT_POSITIONAL_TACTICS" to 2,
    "WHEEL_INSTANT_BALLISTIC_MASTERY" to 3,
    "WHEEL_INSTANT_HEALING_LINK" to 4,
    "WHEEL_INSTANT_RUNIC_MASTERY" to 5,
    "WHEEL_INSTANT_FOCUS_MASTERY" to 6,
    "WHEEL_INSTANT_SANCTUARY" to 7,
    "WHEEL_INSTANT_GUIDING_PRESENCE" to 8,
    "WHEEL_INSTANT_INSTANT_COUNT" to 9,
    "WHEEL_STAGE_GIFT_OF_LIFE" to 0,
    "WHEEL_STAGE_COMBAT_MASTERY" to 1,
    "WHEEL_STAGE_BLESSING_OF_THE_GROVE" to 2,
    "WHEEL_STAGE_DRAIN_BODY" to 3,
    "WHEEL_STAGE_BEAM_MASTERY" to 4,
    "WHEEL_STAGE_DIVINE_EMPOWERMENT" to 5,
    "WHEEL_STAGE_TWIN_BURST" to 6,
    "WHEEL_STAGE_EXECUTIONERS_THROW" to 7,
    "WHEEL_STAGE_AVATAR_OF_LIGHT" to 8,
    "WHEEL_STAGE_AVATAR_OF_NATURE" to 9,
    "WHEEL_STAGE_AVATAR_OF_STEEL" to 10,
    "WHEEL_STAGE_AVATAR_OF_STORM" to 11,
    "WHEEL_STAGE_AVATAR_OF_BALANCE" to 12,
    "WHEEL_STAGE_DIVINE_GRENADE" to 13,
    "WHEEL_STAGE_SPIRITUAL_OUTBURST" to 14,
    "WHEEL_STAGE_ASCETIC" to 15,
    "WHEEL_STAGE_STAGE_COUNT" to 16,
    "WHEEL_GRADE_NONE" to 0,
    "WHEEL_GRADE_REGULAR" to 1,
    "WHEEL_GRADE_UPGRADED" to 2,
    "WHEEL_GRADE_MAX" to 3,
    "WHEEL_AVATAR_SKILL_NONE" to 0,
    "WHEEL_AVATAR_SKILL_DAMAGE_REDUCTION" to 1,
    "WHEEL_AVATAR_SKILL_CRITICAL_CHANCE" to 2,
    "WHEEL_AVATAR_SKILL_CRITICAL_DAMAGE" to 3,
    "WHEEL_STAT_HEALTH" to 0,
    "WHEEL_STAT_MANA" to 1,
    "WHEEL_STAT_CAPACITY" to 2,
    "WHEEL_STAT_MITIGATION" to 3,
    "WHEEL_STAT_FIST" to 4,
    "WHEEL_STAT_MELEE" to 5,
    "WHEEL_STAT_DISTANCE" to 6,
    "WHEEL_STAT_MAGIC" to 7,
    "WHEEL_STAT_LIFE_LEECH" to 8,
    "WHEEL_STAT_MANA_LEECH" to 9,
    "WHEEL_STAT_HEALING" to 10,
    "WHEEL_STAT_DAMAGE" to 11,
    "WHEEL_STAT_LIFE_LEECH_CHANCE" to 12,
    "WHEEL_STAT_MANA_LEECH_CHANCE" to 13,
    "WHEEL_STAT_DODGE" to 14,
    "WHEEL_STAT_CRITICAL_DAMAGE" to 15,
    "WHEEL_STAT_CRITICAL_CHANCE" to 16,
    "WHEEL_STAT_TOTAL_COUNT" to 17,
    "WHEEL_BOOST_MANA" to 0,
    "WHEEL_BOOST_COOLDOWN" to 1,
    "WHEEL_BOOST_GROUP_COOLDOWN" to 2,
    "WHEEL_BOOST_SECONDARY_GROUP_COOLDOWN" to 3,
    "WHEEL_BOOST_MANA_LEECH" to 4,
    "WHEEL_BOOST_MANA_LEECH_CHANCE" to 5,
    "WHEEL_BOOST_LIFE_LEECH" to 6,
    "WHEEL_BOOST_LIFE_LEECH_CHANCE" to 7,
    "WHEEL_BOOST_DAMAGE" to 8,
    "WHEEL_BOOST_DAMAGE_REDUCTION" to 9,
    "WHEEL_BOOST_HEAL" to 10,
    "WHEEL_BOOST_CRITICAL_DAMAGE" to 11,
    "WHEEL_BOOST_CRITICAL_CHANCE" to 12,
    "WHEEL_BOOST_TOTAL_COUNT" to 13,
)

/**
 * Port of `LuaEnums::init` (src/lua/functions/core/game/lua_enums.cpp:64-121): the
 * global enumerator names Lua scripts rely on, sourced from the port's enum mirrors
 * (each one a faithful transcription of its C++ enum).
 *
 * Registered families: `WORLD_TYPE_*`, `GAME_STATE_*`, `CREATURE_EVENT_*`, `DIRECTION_*`,
 * `AMMO_*`, `ORIGIN_*`, `SPELL_*`, `ZONE_*`, `WEAPON_*`, `COMBAT_*`, `CONDITIONID_*`,
 * `CONDITION_PARAM_*`, `CONDITION_*`, `SKILL_*`, `SKULL_*`, `TALKTYPE_*`, `FIGHTMODE_*`,
 * `ITEM_ATTRIBUTE_*`, `ITEM_TYPE_*`, `FLUID_*`, `TILESTATE_*`, `RETURNVALUE_*`,
 * `RELOAD_TYPE_*`, `MONSTERS_EVENT_*`, `MESSAGE_*`, `PlayerFlags_t` names,
 * `RARITY_*`/`BOSS_INVALID`, `CreatureIconCategory_*`/`CreatureIconModifications_*`/
 * `CreatureIconQuests_*` and the complete `SOUND_EFFECT_TYPE_*` namespace
 * (`SoundEffect_t`, `SoundAmbientEffect_t`, `SoundMusicEffect_t`), plus the
 * remaining Canary groups: `ACCOUNT_TYPE_*`/`GROUP_TYPE_*`, `DAILY_REWARD_*`,
 * `BUG_CATEGORY_*`, `REPORT_TYPE_*`, `CALLBACK_PARAM_*`, `COMBAT_PARAM_*`,
 * `COMBAT_FORMULA_*`, `Virtue_*`/`MonkSpell_*`, `FACTION_*`, `CONST_ME_*`,
 * `CONST_ANI_*`, `CONST_PROP_*`, `CONST_SLOT_*`/`SLOTP_*`, `FLAG_*`, `CHARM_*`,
 * `GUEST_LIST`/`SUBOWNER_LIST`, `PLAYERSEX_*`/`PLAYERPRONOUN_*`, `RESPAWNPERIOD_*`,
 * `LIGHT_STATE_*`, `SCREENSHOT_TYPE_*`, `TEXTCOLOR_*`, `SPEECHBUBBLE_*`, `MAPMARK_*`,
 * `ITEM_*` ids, `NPCS_EVENT_*`, `FORGE_*`, `AttrSubId_*`, `Concoction_*` and the
 * `WHEEL_*` namespaces.
 */
object LuaEnums {
    fun install(env: LuaEnvironment) {
        WorldType.entries.forEach { env.registerEnum("WORLD_TYPE_${it.name}", it.value) }
        GAME_STATE_ENUM_NAMES.forEachIndexed { index, name -> env.registerEnum(name, index) }
        CREATURE_EVENT_ENUM_NAMES.forEachIndexed { index, name -> env.registerEnum(name, index) }
        DIRECTION_ENUM_NAMES.forEachIndexed { index, name -> env.registerEnum(name, index) }
        Ammo_t.entries.forEach { env.registerEnum(it.name, it.value) }
        COMBAT_ORIGIN_ENUM_NAMES.forEach { (name, origin) -> env.registerEnum(name, origin.value) }
        SPELL_TYPE_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value) }
        ZoneType.entries.forEach { env.registerEnum(it.name, it.value) }
        WeaponType_t.entries.forEach { env.registerEnum(it.name, it.value) }
        CombatType.entries.forEach { env.registerEnum(it.name, it.value) }
        ConditionId.entries.forEach { env.registerEnum(it.name, it.value) }
        ConditionParam.entries.forEach { env.registerEnum(it.name, it.value) }
        ConditionType.entries.forEach { env.registerEnum(it.name, it.value) }
        skills_t.entries.forEach { env.registerEnum(it.name, it.value) }
        Skulls_t.entries.forEach { env.registerEnum(it.name, it.value) }
        SpeakClasses.entries.forEach { env.registerEnum(it.name, it.value) }
        FIGHT_MODE_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value) }
        ItemAttribute.entries.forEach { env.registerEnum("ITEM_ATTRIBUTE_${it.name}", it.value) }
        ITEM_TYPE_ENUM_NAMES.forEach { name ->
            ItemTypes_t.valueOf(name).let { env.registerEnum(name, it.value) }
        }
        Fluids_t.entries.forEach { env.registerEnum(it.name, it.value) }
        TILE_STATE_ENUM_NAMES.forEach { name ->
            TileFlags.valueOf(name).let { env.registerEnum(name, it.value) }
        }
        RETURN_VALUE_ENUM_NAMES.forEach { name ->
            env.registerEnum("RETURNVALUE_$name", ReturnValue.valueOf(name).ordinal)
        }
        Reload_t.entries.forEach { env.registerEnum(it.name, it.value) }
        MonstersEvent.entries.forEach { env.registerEnum(it.name, it.value) }
        MessageClass.entries.forEach { env.registerEnum(it.name, it.value) }
        PlayerFlags_t.entries.forEach { env.registerEnum(it.name, it.value) }
        BosstiaryRarity_t.entries.forEach { env.registerEnum(it.name, it.value) }
        CreatureIconCategory_t.entries.forEach { env.registerEnum("CreatureIconCategory_${it.name}", it.value) }
        CreatureIconModifications_t.entries.forEach {
            env.registerEnum("CreatureIconModifications_${it.name}", it.value)
        }
        CreatureIconQuests_t.entries.forEach { env.registerEnum("CreatureIconQuests_${it.name}", it.value) }
        SoundEffect.entries.forEach { env.registerEnum("SOUND_EFFECT_TYPE_${it.name}", it.value) }
        SoundAmbientEffect.entries.forEach { env.registerEnum("SOUND_EFFECT_TYPE_${it.name}", it.value) }
        SoundMusicEffect.entries.forEach { env.registerEnum("SOUND_EFFECT_TYPE_${it.name}", it.value) }
        ACCOUNT_GROUP_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        DAILY_REWARD_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        BUG_CATEGORY_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        REPORT_TYPE_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        REPORT_REASON_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        CALLBACK_PARAM_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        COMBAT_PARAM_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        COMBAT_FORMULA_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        VIRTUE_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        MONK_SPELL_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        FACTION_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        CONST_ME_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        CONST_ANI_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        CONST_PROP_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        CONST_SLOT_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        SLOTP_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        CYLINDER_FLAG_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        CHARM_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        ACCESS_LIST_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        PLAYER_SEX_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        PLAYER_PRONOUN_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        RESPAWN_PERIOD_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        LIGHT_STATE_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        WORLD_CONSTANT_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        CLIENT_OS_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        WEBHOOK_COLOR_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        SCREENSHOT_TYPE_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        TEXT_COLOR_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        SPEECH_BUBBLE_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        MAP_MARK_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        ITEM_ID_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        NPCS_EVENT_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        FORGE_CLASSIFICATION_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        ATTR_SUB_ID_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        CONCOCTION_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
        WHEEL_ENUM_NAMES.forEach { (name, value) -> env.registerEnum(name, value.toDouble()) }
    }

    private fun LuaEnvironment.registerEnum(name: String, value: Number) {
        globals.set(name, LuaValue.valueOf(value.toDouble()))
    }
}
