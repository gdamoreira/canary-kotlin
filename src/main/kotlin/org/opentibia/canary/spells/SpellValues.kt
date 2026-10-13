package org.opentibia.canary.spells

import org.opentibia.canary.map.Position

/** `SpellType_t` (`src/creatures/creatures_definitions.hpp:498-502`). */
enum class SpellType(val value: Int) {
    SPELL_UNDEFINED(0),
    SPELL_INSTANT(1),
    SPELL_RUNE(2),
}

/** `LuaVariantType_t` (`src/enums/lua_variant_type.hpp`). */
enum class LuaVariantType {
    VARIANT_NONE,
    VARIANT_NUMBER,
    VARIANT_POSITION,
    VARIANT_TARGETPOSITION,
    VARIANT_STRING,
}

/**
 * `LuaVariant` (`src/lua/global/lua_variant.hpp:14-21`).
 *
 * The engine hands this value to the Lua `onCastSpell` bridge; the fields keep
 * the C++ names because the Lua variant table reads them verbatim.
 */
data class LuaVariant(
    var type: LuaVariantType = LuaVariantType.VARIANT_NONE,
    var text: String = "",
    var instantName: String = "",
    var runeName: String = "",
    var pos: Position = Position(0, 0, 0),
    var number: Long = 0,
)
