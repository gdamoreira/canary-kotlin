package org.opentibia.canary.lua.runtime

/**
 * Port of `enum class LuaData_t` (src/lua/lua_definitions.hpp:20-58). The ordinal is
 * written into every class metatable's `t` field by [LuaClassRegistry.registerClass]
 * and read back by `Lua::getUserdataType` / `GlobalFunctions::luaIsType`.
 */
enum class LuaDataType {
    Unknown,
    Item,
    Container,
    Teleport,
    Player,
    Monster,
    Npc,
    MonsterType,
    NpcType,
    Tile,
    Variant,
    Position,
    NetworkMessage,
    ModalWindow,
    Guild,
    Group,
    Vocation,
    Town,
    House,
    ItemType,
    Combat,
    Condition,
    Charm,
    Loot,
    MonsterSpell,
    Spell,
    Party,
    Action,
    TalkAction,
    CreatureEvent,
    MoveEvent,
    GlobalEvent,
    Weapon,
    Imbuement,
    Mount,
    ItemClassification,
    ;

    companion object {
        /**
         * Mirrors `magic_enum::enum_cast<LuaData_t>(className)` in
         * `Lua::registerClass` (lua_functions_loader.cpp:805-810): a class name with
         * no matching `LuaData_t` enumerator maps to [Unknown].
         */
        fun fromClassName(className: String): LuaDataType =
            entries.firstOrNull { it.name == className } ?: Unknown
    }
}
