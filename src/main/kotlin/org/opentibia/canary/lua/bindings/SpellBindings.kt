package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.SpellType
import org.opentibia.canary.lua.registry.Spells
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.spells.LuaVariant
import org.opentibia.canary.spells.LuaVariantType
import org.opentibia.canary.spells.MonsterRegisteredSpellRunner
import org.opentibia.canary.spells.SpellRegistryBoot
import org.opentibia.canary.spells.Spells as EngineSpells

/**
 * Canonical owner of the `Spell` global and its metatable in the Kotlin port.
 * The binding surface itself is the faithful [SpellFunctions] port of
 * src/lua/functions/creatures/combat/spell_functions.cpp; this entry point keeps
 * the registry identity contract used by `LuaEnvironment.spells` and the
 * `LuaEventDispatcher` (both need the same [Spells] instance the constructor
 * registers into).
 *
 * It also owns the composed engine `g_spells()` registry ([EngineSpells],
 * spells.hpp:28-79): the datapack spell scripts register through the Lua path
 * and [recordRegisteredSpells] records their compiled `InstantSpell`/`RuneSpell`
 * actors (spells.cpp:181-222) so the engine look-ups resolve, e.g.
 * `Monsters::deserializeSpell` (monsters.cpp:77). Canary reaches the
 * process-wide singleton, the port keeps the [install]-created instance. The
 * same applies to the registry-backed monster spell `onCastSpell` bridge
 * ([monsterRegisteredSpellRunner]), which needs the environment the bindings
 * were installed into.
 */
object SpellBindings {
    @Volatile
    private var registry: Spells? = null

    /** The compiled engine registry the `g_spells()` look-ups resolve through. */
    @Volatile
    private var engineRegistry: EngineSpells = EngineSpells()

    @Volatile
    private var monsterSpellRunner: MonsterRegisteredSpellRunner =
        MonsterRegisteredSpellRunner { _, _, _, _ -> false }

    fun install(env: LuaEnvironment, operations: SpellOperations = StandaloneSpellOperations): Spells {
        val spells = Spells()
        SpellFunctions.install(env, spells, operations)
        registry = spells
        engineRegistry = EngineSpells()
        monsterSpellRunner = LuaMonsterRegisteredSpellRunner(env)
        return spells
    }

    /**
     * The composed engine `g_spells()` registry ([EngineSpells]). Canary
     * resolves the process-wide singleton; per AGENTS.md 1.1 the port hands the
     * [install]-created instance to the engine consumers instead (the wheel
     * tables, the spell list and the rune/vocation look text).
     */
    fun spellRegistry(): EngineSpells = engineRegistry

    /**
     * Records every spell the Lua registration path registered into the engine
     * registry ([SpellRegistryBoot.recordRegisteredSpells]) and returns the
     * number of newly recorded actors. Idempotent, so the boot calls it after
     * the datapack scripts load and [getSpellByName] keeps the registry current
     * for later registrations (e.g. a script reload).
     */
    fun recordRegisteredSpells(): Int =
        registry?.let { SpellRegistryBoot.recordRegisteredSpells(it, engineRegistry) } ?: 0

    /**
     * `Spells::getSpellByName` (`src/creatures/combat/spells.cpp:255-261`): the
     * rune spell wins over an instant spell registered with the same name. The
     * look-up resolves through the compiled engine registry — the monster
     * `sb.spell` hit of `Monsters::deserializeSpell` (monsters.cpp:77-80) — and
     * returns the registered [LuaSpell] script state the monster actor drives
     * `onCastSpell` through. Returns `null` before [install] has run or when the
     * name is not registered.
     */
    fun getSpellByName(name: String): LuaSpell? {
        val spells = registry ?: return null
        recordRegisteredSpells()
        if (engineRegistry.getSpellByName(name) == null) {
            return null
        }
        return spells.getRuneByName(name) ?: spells.getInstantByName(name)
    }

    /**
     * The `onCastSpell` bridge for a registry-backed monster spell
     * (`InstantSpell::executeCastSpell`, spells.cpp:1351-1373;
     * `RuneSpell::executeCastSpell`, spells.cpp:1587-1611). Before [install] it
     * is inert, exactly like a Canary spell whose `m_spellScriptId` was never
     * loaded.
     */
    fun monsterRegisteredSpellRunner(): MonsterRegisteredSpellRunner = monsterSpellRunner
}

/**
 * `LuaEnvironment`-backed [MonsterRegisteredSpellRunner]: pushes the creature
 * userdata and the variant table exactly like `InstantSpell::executeCastSpell` /
 * `RuneSpell::executeCastSpell` (spells.cpp:1351-1373, 1587-1611) and invokes
 * the registered `onCastSpell` callback with the same argument order.
 */
internal class LuaMonsterRegisteredSpellRunner(
    private val env: LuaEnvironment,
) : MonsterRegisteredSpellRunner {

    override fun executeCastSpell(
        creature: Creature,
        spell: LuaSpell,
        variant: LuaVariant,
        isHotkey: Boolean,
    ): Boolean {
        val callback = spell.onCastSpell
        if (!callback.isfunction()) {
            return false
        }

        // `RuneSpell::executeCastSpell` pushes `onCastSpell(creature, var, isHotkey)`
        // (spells.cpp:1606-1610); the instant path pushes two arguments.
        val args = if (spell.spellType == SpellType.RUNE) {
            arrayOf(wrapCreature(creature), pushVariant(variant), LuaValue.valueOf(isHotkey))
        } else {
            arrayOf(wrapCreature(creature), pushVariant(variant))
        }
        // `callFunction(2|3)` returns the Lua boolean (spells.cpp:1372, 1610).
        return callback.invoke(LuaValue.varargsOf(args)).arg1().toboolean()
    }

    /**
     * Mirrors `Lua::setCreatureMetatable`: chooses the `Player`, `Monster` or
     * `Npc` metatable based on the runtime creature, falling back to the shared
     * `Creature`.
     */
    private fun wrapCreature(creature: Creature): LuaValue =
        env.userdata.wrap(
            when (creature) {
                is Player -> "Player"
                is Monster -> "Monster"
                is Npc -> "Npc"
                else -> "Creature"
            },
            creature,
        )

    /**
     * Mirrors `Lua::pushVariant` (`src/lua/functions/lua_functions_loader.cpp:162-186`):
     * a table carrying `type`, the type-specific value and `instantName`/`runeName`,
     * with the `Variant` metatable.
     */
    private fun pushVariant(variant: LuaVariant): LuaValue {
        val table = LuaTable()
        table.set("type", LuaValue.valueOf(variant.type.ordinal))
        when (variant.type) {
            LuaVariantType.VARIANT_NUMBER -> table.set("number", LuaValue.valueOf(variant.number.toDouble()))
            LuaVariantType.VARIANT_STRING -> table.set("string", LuaValue.valueOf(variant.text))
            LuaVariantType.VARIANT_POSITION, LuaVariantType.VARIANT_TARGETPOSITION ->
                table.set("pos", env.userdata.wrap("Position", variant.pos))
            LuaVariantType.VARIANT_NONE -> Unit
        }
        table.set("instantName", LuaValue.valueOf(variant.instantName))
        table.set("runeName", LuaValue.valueOf(variant.runeName))
        table.setmetatable(env.userdata.metatable(VARIANT_TYPE))
        return table
    }

    private companion object {
        const val VARIANT_TYPE = "Variant"
    }
}

/**
 * Standalone `SpellOperations` fallback used when no world seam is wired:
 * `Item::items.getItemType` degrades to a local item type (the rune register
 * info is not persisted) and every vocation resolves to `VOCATION_NONE` (0),
 * which is exactly what `g_vocations().getVocationId` returns for an unknown
 * name.
 */
internal object StandaloneSpellOperations : SpellOperations {
    private val itemTypes = linkedMapOf<Int, ItemType>()

    override fun getItemType(id: Int): ItemType = itemTypes.getOrPut(id) { ItemType(id, "") }

    override fun vocationIdByName(name: String): Int = Vocation.VOCATION_NONE

    override fun vocationName(vocationId: Int): String? = null
}
