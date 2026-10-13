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
 * `TalkActionResult_t` (`src/lua/lua_definitions.hpp:96-100`).
 *
 * Returned by `Spells::playerSaySpell` to tell the talk pipeline whether to
 * keep processing (`CONTINUE`), stop and say the spell words (`BREAK`) or
 * consume the input as a failed spell cast (`FAILED`).
 */
enum class TalkActionResult {
    TALKACTION_CONTINUE,
    TALKACTION_BREAK,
    TALKACTION_FAILED,
}

/**
 * Result of [SpellEngine.playerSaySpell].
 *
 * Canary passes `std::string &words` by reference and mutates it into the
 * normalized spell words/parameter echo (spells.cpp:102-157); the port returns
 * the same value instead of mutating the caller's string.
 */
data class PlayerSaySpellResult(
    val action: TalkActionResult,
    val words: String,
)


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
