package org.opentibia.canary.lua.bindings

import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.lua.registry.LuaSpell
import org.opentibia.canary.lua.registry.Spells
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Canonical owner of the `Spell` global and its metatable in the Kotlin port.
 * The binding surface itself is the faithful [SpellFunctions] port of
 * src/lua/functions/creatures/combat/spell_functions.cpp; this entry point keeps
 * the registry identity contract used by `LuaEnvironment.spells` and the
 * `LuaEventDispatcher` (both need the same [Spells] instance the constructor
 * registers into).
 *
 * It also holds the registry reference the `g_spells()` look-ups of the engine
 * resolve through, e.g. `Monsters::deserializeSpell` (monsters.cpp:77); Canary
 * reaches the process-wide singleton, the port keeps the [install]-created
 * instance.
 */
object SpellBindings {
    @Volatile
    private var registry: Spells? = null

    fun install(env: LuaEnvironment, operations: SpellOperations = StandaloneSpellOperations): Spells {
        val spells = Spells()
        SpellFunctions.install(env, spells, operations)
        registry = spells
        return spells
    }

    /**
     * `Spells::getSpellByName` (`src/creatures/combat/spells.cpp:255-261`): the
     * rune spell wins over an instant spell registered with the same name.
     * Returns `null` before [install] has run or when the name is not
     * registered.
     */
    fun getSpellByName(name: String): LuaSpell? =
        registry?.let { it.getRuneByName(name) ?: it.getInstantByName(name) }
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
