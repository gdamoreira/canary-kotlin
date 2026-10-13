package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Port of the `spellBlock_t` fields exposed by
 * `MonsterTypeFunctions::luaMonsterTypeGetAttackList`
 * (src/lua/functions/creatures/monster/monster_type_functions.cpp:974). `spell` is the
 * optional `CombatSpell` userdata; the port does not model combat spells yet.
 */
data class MonsterAttackInfo(
    val chance: Int = 0,
    val isCombatSpell: Boolean = false,
    val isMelee: Boolean = false,
    val minCombatValue: Int = 0,
    val maxCombatValue: Int = 0,
    val range: Int = 0,
    val speed: Int = 0,
    val spell: Any? = null,
)

/**
 * Engine seam for the `MonsterType` metatable. Mirrors the primitives in
 * src/lua/functions/creatures/monster/monster_type_functions.cpp.
 *
 * Getters return `null` when the receiver is not a type; setters return `null` for the
 * same case and `true`/`false` otherwise, matching Canary's `lua_pushnil` branches.
 */
interface MonsterTypeOperations {
    /** Mirrors `MonsterType(name or raceid)` resolving `g_monsters()`. */
    fun create(nameOrRaceId: Any): Any?
    fun getName(monsterType: Any): String?
    fun setName(monsterType: Any, name: String): Boolean?
    fun getHealth(monsterType: Any): Int?
    fun setHealth(monsterType: Any, health: Int): Boolean?
    fun getMaxHealth(monsterType: Any): Int?
    fun setMaxHealth(monsterType: Any, maxHealth: Int): Boolean?
    fun getExperience(monsterType: Any): Long?
    fun setExperience(monsterType: Any, experience: Long): Boolean?
    fun getBaseSpeed(monsterType: Any): Int?
    fun setBaseSpeed(monsterType: Any, speed: Int): Boolean?
    fun getRace(monsterType: Any): Int?
    fun setRace(monsterType: Any, race: Int): Boolean?
    fun getBestiaryClass(monsterType: Any): String?
    fun setBestiaryClass(monsterType: Any, bestiaryClass: String): Boolean?
    fun getCorpseId(monsterType: Any): Int?
    fun getTypeName(monsterType: Any): String?
    fun isAttackable(monsterType: Any): Boolean?
    fun setAttackable(monsterType: Any, value: Boolean): Boolean?
    fun isSummonable(monsterType: Any): Boolean?
    fun setSummonable(monsterType: Any, value: Boolean): Boolean?
    fun isConvinceable(monsterType: Any): Boolean?
    fun setConvinceable(monsterType: Any, value: Boolean): Boolean?
    fun isHostile(monsterType: Any): Boolean?
    fun setHostile(monsterType: Any, value: Boolean): Boolean?
    fun isBlockable(monsterType: Any): Boolean?
    fun setBlockable(monsterType: Any, value: Boolean): Boolean?
    fun canSpawn(monsterType: Any, position: Position): Boolean?
    fun getLoot(monsterType: Any): List<LootData>?
    fun addLoot(monsterType: Any, loot: LootData): Boolean?
    fun getAttackList(monsterType: Any): List<MonsterAttackInfo>?
}

/**
 * Registers the Canary `MonsterType` methods, delegating every attribute to
 * [MonsterTypeOperations]:
 *
 *   MonsterType(name or raceid)
 *   monsterType:name([name])
 *   monsterType:health([health])
 *   monsterType:maxHealth([maxHealth])
 *   monsterType:experience([experience])
 *   monsterType:baseSpeed([baseSpeed])
 *   monsterType:race([race])
 *   monsterType:Bestiaryclass([class])
 *   monsterType:getCorpseId()
 *   monsterType:getTypeName()
 *   monsterType:isAttackable([bool])
 *   monsterType:isSummonable([bool])
 *   monsterType:isConvinceable([bool])
 *   monsterType:isHostile([bool])
 *   monsterType:isBlockable([bool])
 *   monsterType:canSpawn(position)
 *   monsterType:getLoot()
 *   monsterType:addLoot(loot)
 *   monsterType:getAttackList()
 *
 * `getLoot`/`getAttackList` render the same Lua tables as Canary's
 * `createMonsterTypeLootLuaTable`/`luaMonsterTypeGetAttackList`, including the
 * `maxCount`/`minCount`/`childLoot` loot shape.
 */
object MonsterTypeFunctions {
    const val MONSTER_TYPE_TYPE = "MonsterType"
    const val COMBAT_SPELL_TYPE = "CombatSpell"

    private const val RACE_VENOM = 1
    private const val RACE_BLOOD = 2
    private const val RACE_UNDEAD = 3
    private const val RACE_FIRE = 4
    private const val RACE_ENERGY = 5
    private const val RACE_INK = 6
    private const val RACE_CHOCOLATE = 7
    private const val RACE_CANDY = 8

    fun install(env: LuaEnvironment, operations: MonsterTypeOperations = UnwiredMonsterTypeOperations) {
        env.globals.set(MONSTER_TYPE_TYPE, luaFunction { args ->
            val argument = args.arg(1)
            val monsterType = when {
                argument.isnumber() -> operations.create(argument.toint())
                argument.isstring() -> operations.create(argument.tojstring())
                else -> null
            }
            monsterType?.let { env.userdata.wrap(MONSTER_TYPE_TYPE, it) } ?: LuaValue.NIL
        })

        registerStringAttribute(env, "name", operations::getName, operations::setName)
        registerIntAttribute(env, "health", operations::getHealth, operations::setHealth)
        registerIntAttribute(env, "maxHealth", operations::getMaxHealth, operations::setMaxHealth)
        registerLongAttribute(env, "experience", operations::getExperience, operations::setExperience)
        registerIntAttribute(env, "baseSpeed", operations::getBaseSpeed, operations::setBaseSpeed)
        registerStringAttribute(env, "Bestiaryclass", operations::getBestiaryClass, operations::setBestiaryClass)

        registerBooleanAttribute(env, "isAttackable", operations::isAttackable, operations::setAttackable)
        registerBooleanAttribute(env, "isSummonable", operations::isSummonable, operations::setSummonable)
        registerBooleanAttribute(env, "isConvinceable", operations::isConvinceable, operations::setConvinceable)
        registerBooleanAttribute(env, "isHostile", operations::isHostile, operations::setHostile)
        registerBooleanAttribute(env, "isBlockable", operations::isBlockable, operations::setBlockable)

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "race", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                operations.getRace(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                val race = raceIdByName(args.arg(2).tojstring()) ?: return@luaFunction LuaValue.NIL
                operations.setRace(monsterType, race)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getCorpseId", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getCorpseId(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getTypeName", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.getTypeName(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "canSpawn", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val position = luaToPosition(args.arg(2)) ?: return@luaFunction LuaValue.NIL
            operations.canSpawn(monsterType, position)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getLoot", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val loot = operations.getLoot(monsterType) ?: return@luaFunction LuaValue.NIL
            lootTable(loot)
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "addLoot", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val loot = retrieveUserdata(args, 2) as? LootData ?: return@luaFunction LuaValue.NIL
            operations.addLoot(monsterType, loot)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        env.userdata.registerMethod(MONSTER_TYPE_TYPE, "getAttackList", luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val attacks = operations.getAttackList(monsterType) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                attacks.forEachIndexed { index, attack -> table.set(index + 1, attackRow(env, attack)) }
            }
        })
    }

    /** Mirrors `MonsterTypeFunctions::luaMonsterTypeRace`'s string -> `RaceType_t` map. */
    private fun raceIdByName(race: String): Int? = when (race) {
        "venom" -> RACE_VENOM
        "blood" -> RACE_BLOOD
        "undead" -> RACE_UNDEAD
        "fire" -> RACE_FIRE
        "energy" -> RACE_ENERGY
        "ink" -> RACE_INK
        "chocolate" -> RACE_CHOCOLATE
        "candy" -> RACE_CANDY
        else -> null
    }

    private fun registerIntAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Int?,
        setter: (Any, Int) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(monsterType, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    private fun registerLongAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Long?,
        setter: (Any, Long) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(monsterType)?.let { LuaValue.valueOf(it.toDouble()) } ?: LuaValue.NIL
            } else {
                setter(monsterType, args.arg(2).tolong())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    private fun registerStringAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> String?,
        setter: (Any, String) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(monsterType, args.arg(2).tojstring())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    private fun registerBooleanAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Boolean?,
        setter: (Any, Boolean) -> Boolean?,
    ) {
        env.userdata.registerMethod(MONSTER_TYPE_TYPE, name, luaFunction { args ->
            val monsterType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(monsterType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(monsterType, args.arg(2).toboolean())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    /** Mirrors `MonsterTypeFunctions::createMonsterTypeLootLuaTable`. */
    private fun lootTable(loot: List<LootData>): LuaTable =
        LuaTable().also { table ->
            loot.forEachIndexed { index, block -> table.set(index + 1, lootRow(block)) }
        }

    private fun lootRow(block: LootData): LuaTable =
        LuaTable().also { row ->
            row.set("itemId", LuaValue.valueOf(block.id))
            row.set("chance", LuaValue.valueOf(block.chance))
            row.set("subType", LuaValue.valueOf(block.subType))
            row.set("maxCount", LuaValue.valueOf(block.countMax))
            row.set("minCount", LuaValue.valueOf(block.countMin))
            row.set("actionId", LuaValue.valueOf(block.actionId))
            row.set("text", LuaValue.valueOf(block.text))
            row.set("unique", LuaValue.valueOf(block.unique))
            row.set("childLoot", lootTable(block.childLoot))
        }

    /** Mirrors `MonsterTypeFunctions::luaMonsterTypeGetAttackList`. */
    private fun attackRow(env: LuaEnvironment, attack: MonsterAttackInfo): LuaTable =
        LuaTable().also { row ->
            row.set("chance", LuaValue.valueOf(attack.chance))
            row.set("isCombatSpell", LuaValue.valueOf(if (attack.isCombatSpell) 1 else 0))
            row.set("isMelee", LuaValue.valueOf(if (attack.isMelee) 1 else 0))
            row.set("minCombatValue", LuaValue.valueOf(attack.minCombatValue))
            row.set("maxCombatValue", LuaValue.valueOf(attack.maxCombatValue))
            row.set("range", LuaValue.valueOf(attack.range))
            row.set("speed", LuaValue.valueOf(attack.speed))
            val spell = attack.spell
            row.set("spell", if (spell != null) env.userdata.wrap(COMBAT_SPELL_TYPE, spell) else LuaValue.NIL)
        }
}

internal object UnwiredMonsterTypeOperations : MonsterTypeOperations {
    override fun create(nameOrRaceId: Any): Any? =
        throw NotImplementedError("MonsterTypeFunctions.create seam is not wired")

    override fun getName(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getName seam is not wired")

    override fun setName(monsterType: Any, name: String): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setName seam is not wired")

    override fun getHealth(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getHealth seam is not wired")

    override fun setHealth(monsterType: Any, health: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setHealth seam is not wired")

    override fun getMaxHealth(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getMaxHealth seam is not wired")

    override fun setMaxHealth(monsterType: Any, maxHealth: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setMaxHealth seam is not wired")

    override fun getExperience(monsterType: Any): Long? =
        throw NotImplementedError("MonsterTypeFunctions.getExperience seam is not wired")

    override fun setExperience(monsterType: Any, experience: Long): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setExperience seam is not wired")

    override fun getBaseSpeed(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getBaseSpeed seam is not wired")

    override fun setBaseSpeed(monsterType: Any, speed: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBaseSpeed seam is not wired")

    override fun getRace(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getRace seam is not wired")

    override fun setRace(monsterType: Any, race: Int): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setRace seam is not wired")

    override fun getBestiaryClass(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getBestiaryClass seam is not wired")

    override fun setBestiaryClass(monsterType: Any, bestiaryClass: String): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBestiaryClass seam is not wired")

    override fun getCorpseId(monsterType: Any): Int? =
        throw NotImplementedError("MonsterTypeFunctions.getCorpseId seam is not wired")

    override fun getTypeName(monsterType: Any): String? =
        throw NotImplementedError("MonsterTypeFunctions.getTypeName seam is not wired")

    override fun isAttackable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isAttackable seam is not wired")

    override fun setAttackable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setAttackable seam is not wired")

    override fun isSummonable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isSummonable seam is not wired")

    override fun setSummonable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setSummonable seam is not wired")

    override fun isConvinceable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isConvinceable seam is not wired")

    override fun setConvinceable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setConvinceable seam is not wired")

    override fun isHostile(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isHostile seam is not wired")

    override fun setHostile(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setHostile seam is not wired")

    override fun isBlockable(monsterType: Any): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.isBlockable seam is not wired")

    override fun setBlockable(monsterType: Any, value: Boolean): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.setBlockable seam is not wired")

    override fun canSpawn(monsterType: Any, position: Position): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.canSpawn seam is not wired")

    override fun getLoot(monsterType: Any): List<LootData>? =
        throw NotImplementedError("MonsterTypeFunctions.getLoot seam is not wired")

    override fun addLoot(monsterType: Any, loot: LootData): Boolean? =
        throw NotImplementedError("MonsterTypeFunctions.addLoot seam is not wired")

    override fun getAttackList(monsterType: Any): List<MonsterAttackInfo>? =
        throw NotImplementedError("MonsterTypeFunctions.getAttackList seam is not wired")
}
