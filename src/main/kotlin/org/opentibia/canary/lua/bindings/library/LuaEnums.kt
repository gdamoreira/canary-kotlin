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
import org.opentibia.canary.spells.SoundEffect
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

/**
 * Port of `LuaEnums::init` (src/lua/functions/core/game/lua_enums.cpp:64-121): the
 * global enumerator names Lua scripts rely on, sourced from the port's enum mirrors
 * (each one a faithful transcription of its C++ enum).
 *
 * Registered families: `WORLD_TYPE_*`, `AMMO_*`, `ORIGIN_*`, `SPELL_*`, `ZONE_*`,
 * `WEAPON_*`, `COMBAT_*`, `CONDITIONID_*`, `CONDITION_PARAM_*`, `CONDITION_*`,
 * `SKILL_*`, `SKULL_*`, `TALKTYPE_*`, `FIGHTMODE_*`, `ITEM_ATTRIBUTE_*`,
 * `ITEM_TYPE_*`, `FLUID_*`, `TILESTATE_*`, `RETURNVALUE_*`, `RELOAD_TYPE_*`,
 * `MONSTERS_EVENT_*`, `MESSAGE_*`, `PlayerFlags_t` names, `RARITY_*`/`BOSS_INVALID`,
 * `CreatureIconCategory_*`/`CreatureIconModifications_*`/`CreatureIconQuests_*` and
 * the complete `SOUND_EFFECT_TYPE_*` namespace.
 *
 * PARITY GAPS (documented): the C++ `CLIENTOS_*`, `TEXTCOLOR_*`, `SPEECHBUBBLE_*`,
 * `MAPMARK_*`, `CONST_ME_*`, `CONST_ANI_*`, `CONST_PROP_*`, `CONST_SLOT_*`,
 * `CHARM_*`, `PLAYERSEX_*`, `ITEM_*` id, `NPCS_EVENT_*`, `Forge*`, `Virtue_*`,
 * `MonkSpell_*`, `AttrSubId_*`, `Concoction_*` families are not registered yet
 * because the port has no faithful mirror for every one of them.
 */
object LuaEnums {
    fun install(env: LuaEnvironment) {
        WorldType.entries.forEach { env.registerEnum("WORLD_TYPE_${it.name}", it.value) }
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
    }

    private fun LuaEnvironment.registerEnum(name: String, value: Number) {
        globals.set(name, LuaValue.valueOf(value.toDouble()))
    }
}
