package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.AreaCombat
import org.opentibia.canary.combat.Combat
import org.opentibia.canary.combat.CombatActor
import org.opentibia.canary.combat.CombatCallbackOperands
import org.opentibia.canary.combat.CombatDamage
import org.opentibia.canary.combat.CombatEffects
import org.opentibia.canary.combat.CombatFunction
import org.opentibia.canary.combat.CombatMap
import org.opentibia.canary.combat.CombatMonster
import org.opentibia.canary.combat.CombatOrigin
import org.opentibia.canary.combat.CombatParams
import org.opentibia.canary.combat.CombatPlayer
import org.opentibia.canary.combat.CombatTarget
import org.opentibia.canary.combat.CombatTargetCallback
import org.opentibia.canary.combat.CombatTargetState
import org.opentibia.canary.combat.CombatTile
import org.opentibia.canary.combat.CombatTileCallback
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.CombatValueCallback
import org.opentibia.canary.combat.CreatureCombatTarget
import org.opentibia.canary.combat.FormulaType
import org.opentibia.canary.combat.GlobalFunctions as CombatGlobalFunctions
import org.opentibia.canary.combat.PlayerConditionTarget
import org.opentibia.canary.combat.normalRandom
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionPlayer
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.creatures.players.hasPermittedConditionInPZ
import org.opentibia.canary.creatures.players.onCombatRemoveCondition
import org.opentibia.canary.creatures.vocations.Vocation
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.items.ItemProperty
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.LuaVariant
import org.opentibia.canary.lua.runtime.LuaVariantType
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.ReturnValue
import java.util.IdentityHashMap
import java.util.Random

/**
 * Per-execution [CombatActor]/[CombatPlayer]/[CombatMonster]/[CombatTarget]/
 * [CombatTile] adapter factory over the live Kotlin domain objects.
 *
 * The C++ combat entry points hand the raw `std::shared_ptr<Creature>`/
 * `Tile` around; the port replaces them with the wave-64 seams
 * ([CombatActor] and friends), so this world builds the production views the
 * Lua `combat:execute` and `doTargetCombat*` entry points drive. Every view is
 * identity-stable inside one world instance so the core's `caster !== target`/
 * `caster.getTile() == tile` reference checks behave like the C++ pointers
 * (AGENTS.md deferred-callback safety: the maps die with the world).
 */
class LuaCombatWorld(
    /** Game-owned combat state seam ([CreatureCombatTarget]). */
    val state: CombatTargetState = CombatTargetState.Inert,
    /** The `g_game()` effect fanout seam of the area dispatch. */
    val effects: CombatEffects = LuaCombatNoEffects,
    /**
     * `Creature::getFaction()` projection: `Player::getFaction()` (player.cpp:6234)
     * for players, `Faction_t::Default` elsewhere. PARITY GAP: the monster
     * type's faction is owned by the unported `Monsters` registry here, so the
     * default mirrors `MonsterPipelinesHost.creatureFacts`.
     */
    val factionOf: (Creature) -> Faction = { if (it is Player) Faction.Player else Faction.Default },
) {
    private val actors = IdentityHashMap<Creature, LuaCombatActor>()
    private val players = IdentityHashMap<Player, LuaCombatPlayer>()
    private val monsters = IdentityHashMap<Monster, LuaCombatMonster>()
    private val targets = IdentityHashMap<Creature, CombatTarget>()
    private val tiles = IdentityHashMap<Tile, LuaCombatTile>()

    fun actorOf(creature: Creature): LuaCombatActor = actors.getOrPut(creature) { LuaCombatActor(creature, this) }

    fun playerOf(player: Player): LuaCombatPlayer = players.getOrPut(player) { LuaCombatPlayer(player, this) }

    fun monsterOf(monster: Monster): LuaCombatMonster = monsters.getOrPut(monster) { LuaCombatMonster(monster, state) }

    fun targetOf(creature: Creature): CombatTarget =
        targets.getOrPut(creature) { CreatureCombatTarget(creature, state) }

    fun tileOf(tile: Tile): LuaCombatTile = tiles.getOrPut(tile) { LuaCombatTile(tile, this) }

    fun combatMapOf(map: GameMap): CombatMap = LuaCombatMap(map, this)
}

/** Inert `g_game()` effect fanout used when a host injects no effects seam. */
object LuaCombatNoEffects : CombatEffects

/**
 * Production [CombatActor] view of a live [Creature].
 *
 * PARITY GAPS: `Creature::isAttackable` and `Creature::getFaction` resolve
 * through the injected [CombatTargetState]/[LuaCombatWorld.factionOf] defaults
 * because the port's `Creature` does not model them; `Monster::isEnemyFaction`
 * and `Monster::onAttackedByPlayer` route through [LuaCombatMonster].
 */
class LuaCombatActor(
    val creature: Creature,
    private val world: LuaCombatWorld,
) : CombatActor {
    /** `Creature::getPlayer()`. */
    override fun getPlayer(): CombatPlayer? = (creature as? Player)?.let(world::playerOf)

    /** `Creature::getMonster()`. */
    override fun getMonster(): CombatMonster? = (creature as? Monster)?.let(world::monsterOf)

    /** `Creature::getNpc()` (combat.cpp:547). */
    override fun getNpc(): Boolean = creature is Npc

    /** `Creature::getMaster()`. */
    override fun getMaster(): CombatActor? = creature.getMaster()?.let(world::actorOf)

    /** `Creature::isSummon()`. */
    override fun isSummon(): Boolean = creature.isSummon()

    /** `Creature::isAttackable()` (combat.cpp:348). */
    override fun isAttackable(): Boolean = world.state.isAttackable(creature)

    /** `Creature::getZoneType()`. */
    override fun getZoneType(): ZoneType = creature.getZoneType()

    /** `Creature::getFaction()` (combat.cpp:498-541). */
    override fun getFaction(): Faction = world.factionOf(creature)

    /** `Creature::getPosition()`. */
    override fun getPosition(): Position = creature.position

    /** `Creature::getTile()`. */
    override fun getTile(): CombatTile? = creature.getTile()?.let(world::tileOf)
}

/**
 * Production [CombatPlayer] view of a live [Player] (`Creature::getPlayer()`).
 *
 * PARITY GAPS: `Player::sendMagicEffect` is not wired here (the no-PvP
 * first-on-stack rejection effect stays with the protocol layer);
 * `Player::getVocation()->canCombat()` reads the injected `Vocations` registry
 * and answers false without one, exactly like `Vocation::canCombat`'s default.
 */
class LuaCombatPlayer(
    val player: Player,
    private val world: LuaCombatWorld,
) : CombatPlayer {
    override fun getPlayer(): CombatPlayer = this

    override fun getMonster(): CombatMonster? = null

    override fun getNpc(): Boolean = false

    override fun getMaster(): CombatActor? = player.getMaster()?.let(world::actorOf)

    override fun isSummon(): Boolean = player.isSummon()

    override fun isAttackable(): Boolean = world.state.isAttackable(player)

    override fun getZoneType(): ZoneType = player.getZoneType()

    override fun getFaction(): Faction = world.factionOf(player)

    override fun getPosition(): Position = player.position

    override fun getTile(): CombatTile? = player.getTile()?.let(world::tileOf)

    /** `Player::hasFlag(flag)`. */
    override fun hasFlag(flag: PlayerFlags_t): Boolean = player.hasFlag(flag)

    /** `Player::getLevel()`. */
    override fun getLevel(): Int = player.level

    /** `Player::getSkull()`. */
    override fun getSkull(): Skulls_t = player.getSkull()

    /** `Player::getSkullClient(creature)` (player.cpp:7127-7151). */
    override fun getSkullClient(creature: CombatActor): Skulls_t {
        val target = (creature as? LuaCombatActor)?.creature ?: return Skulls_t.SKULL_NONE
        return player.pvpPlayer.getSkullClient(target)
    }

    /** `Player::hasSecureMode()`. */
    override fun hasSecureMode(): Boolean = player.secureMode

    /** `Player::hasPermittedConditionInPZ()` (player.cpp:12897-12921). */
    override fun hasPermittedConditionInPZ(): Boolean = player.hasPermittedConditionInPZ()

    /** `Player::getVocation()->canCombat()` (combat.cpp:438). */
    override fun getVocationCanCombat(): Boolean =
        player.vocations?.getVocation(player.vocationId)?.canCombat() ?: false

    /** `Player::getVocationId()` (combat.cpp:438). */
    override fun getVocationId(): Int = player.getVocationId()

    /** `Player::isFirstOnStack()` (player.cpp:13157-13172). */
    override fun isFirstOnStack(): Boolean {
        val tile = player.getTile() ?: return false
        val bottomCreature = tile.getBottomCreature() ?: return false
        val bottomPlayer = bottomCreature as? Player ?: return false

        if (bottomPlayer.isInGhostMode()) {
            return true
        }

        if (player.hasCondition(ConditionType.CONDITION_SPELLCOOLDOWN)) {
            return false
        }

        return player === bottomPlayer
    }

    /** `Player::sendCancelMessage(ret)` (combat.cpp:1527). */
    override fun sendCancelMessage(ret: ReturnValue) {
        player.sendCancelMessage.invoke(ret)
    }

    /** `Player::addInFightTicks()` (combat.cpp:1221). */
    override fun addInFightTicks() {
        player.addInFightTicks()
    }
}

/**
 * Production [CombatMonster] view of a live [Monster], routing
 * `Monster::isEnemyFaction`/`Monster::onAttackedByPlayer` through the injected
 * [CombatTargetState].
 */
class LuaCombatMonster(
    val monster: Monster,
    private val state: CombatTargetState,
) : CombatMonster {
    override fun isEnemyFaction(faction: Faction): Boolean = state.isEnemyFaction(monster, faction)

    override fun onAttackedByPlayer(attackerPlayer: ConditionPlayer) {
        val attacker = (attackerPlayer as? PlayerConditionTarget)?.player ?: return
        state.onAttackedByPlayer(monster, attacker)
    }
}

/** Production [CombatTile] view of a live [Tile] (`Tile::hasFlag` etc.). */
class LuaCombatTile(
    val tile: Tile,
    private val world: LuaCombatWorld,
) : CombatTile {
    override fun hasFlag(flag: TileFlags): Boolean = tile.hasFlag(flag.value)

    /** `Tile::hasProperty(ItemProperty)` (tile.cpp:124-153). */
    override fun hasProperty(property: Int): Boolean {
        val itemProperty = ItemProperty.entries.firstOrNull { it.value == property } ?: return false
        return tile.hasProperty(itemProperty)
    }

    override fun getPosition(): Position = tile.position

    /** `Tile::getTeleportItem()` (combat.cpp:402). */
    override fun getTeleportItem(): Boolean = tile.getTeleportItem() != null

    /** The `Tile::getItemList()` item ids (combat.cpp:384-391, magic wall check). */
    override fun getItemIds(): List<Int> = tile.items.map { it.id }

    /** `Tile::getCreatures()` (combat.cpp:1536). */
    override fun getCreatures(): List<CombatActor> = tile.getCreatures().map(world::actorOf)

    /** `Tile::getTopCreature()` (combat.cpp:1537). */
    override fun getTopCreature(): CombatActor? = tile.getTopCreature()?.let(world::actorOf)
}

/** Production [CombatMap] view of a live [GameMap] (map.cpp:816-998). */
class LuaCombatMap(
    private val map: GameMap,
    private val world: LuaCombatWorld,
) : CombatMap {
    override fun getTile(position: Position): CombatTile? = map.getTile(position)?.let(world::tileOf)

    override fun getOrCreateTile(position: Position): CombatTile = world.tileOf(map.getOrCreateTile(position))

    override fun isSightClear(fromPos: Position, toPos: Position, checkFloor: Boolean): Boolean =
        map.isSightClear(fromPos, toPos, checkFloor)
}

/**
 * `g_luaEnvironment().createAreaObject`/`getAreaObject`/`clearAreaObjects`
 * (src/lua/scripts/lua_environment.cpp:97-124): the per-environment area
 * registry behind `createCombatArea(area)` and the `area` argument of
 * `doAreaCombat*`/`combat:setArea`.
 */
class LuaCombatAreaRegistry {
    private val areas = linkedMapOf<Int, AreaCombat>()
    private var lastAreaId = 0

    /** `LuaEnvironment::createAreaObject`: registers a fresh area and returns its id (ids start at 1). */
    fun createAreaObject(): Int {
        val id = ++lastAreaId
        areas[id] = AreaCombat()
        return id
    }

    /** `LuaEnvironment::getAreaObject`; null for a never-created id. */
    fun getAreaObject(id: Int): AreaCombat? = areas[id]
}

/**
 * Lua-side bridge of `Combat::setCallback` (combat.cpp:672-711) over the
 * registered `LuaCombat.callbacks` (AGENTS.md 1.4: the callback scripts stay
 * Lua). Builds the wave-64 [CombatValueCallback]/[CombatTileCallback]/
 * [CombatTargetCallback] seams for one `combat:execute` call.
 */
class LuaCombatCallbackBridge(private val env: LuaEnvironment) {

    /**
     * `ValueCallback::getMinMaxValues` (combat.cpp:1982-2068): pushes the
     * casting player and the formula operands and reads back the two numbers.
     * The `COMBAT_FORMULA_SKILL` weapon resolution stays with the wave-64
     * `CombatSkillFormula` seam (PARITY GAP here).
     *
     * PARITY GAP: the C++ last-`setCallback`-wins overwrite is reduced to a
     * fixed precedence (SKILL over LEVELMAGIC) when a script registers both.
     */
    fun valueCallback(combat: LuaCombat, caster: Creature?): CombatValueCallback? {
        val player = caster as? Player ?: return null
        val skillFunction = combat.callbacks[CALLBACK_PARAM_SKILLVALUE]?.takeIf { it.isfunction() }
        val levelMagicFunction = combat.callbacks[CALLBACK_PARAM_LEVELMAGICVALUE]?.takeIf { it.isfunction() }
        val function = skillFunction ?: levelMagicFunction ?: return null
        val formulaType = if (skillFunction != null) {
            FormulaType.SKILL
        } else {
            FormulaType.LEVELMAGIC
        }

        return object : CombatValueCallback {
            override val type: FormulaType = formulaType

            override fun getMinMaxValues(
                useCharges: Boolean,
                operands: CombatCallbackOperands,
            ): Pair<Int, Int>? {
                val playerValue = env.userdata.wrap("Player", player)
                val args = when (operands) {
                    is CombatCallbackOperands.LevelMagic -> arrayOf(
                        playerValue,
                        LuaValue.valueOf(operands.level),
                        LuaValue.valueOf(operands.magicLevelSkill),
                    )

                    is CombatCallbackOperands.Skill -> arrayOf(
                        playerValue,
                        LuaValue.valueOf(operands.attackSkill),
                        LuaValue.valueOf(operands.attackValue),
                        LuaValue.valueOf(operands.attackFactor.toDouble()),
                    )
                }
                val results = function.invoke(LuaValue.varargsOf(args))
                return results.arg(1).toint() to results.arg(2).toint()
            }
        }
    }

    /** `TileCallback::onTileCombat` (combat.cpp:2077-2103): `(creature, pos)`. */
    fun tileCallback(combat: LuaCombat): CombatTileCallback? {
        val function = combat.callbacks[CALLBACK_PARAM_TARGETTILE]?.takeIf { it.isfunction() } ?: return null
        return CombatTileCallback { caster, tile ->
            function.invoke(pushCreature(caster), env.userdata.wrap("Position", tile.getPosition()))
        }
    }

    /** `TargetCallback::onTargetCombat` (combat.cpp:2106-2132): `(creature, target)`. */
    fun targetCallback(combat: LuaCombat): CombatTargetCallback? {
        val function = combat.callbacks[CALLBACK_PARAM_TARGETCREATURE]?.takeIf { it.isfunction() } ?: return null
        return CombatTargetCallback { caster, target ->
            function.invoke(pushCreature(caster), pushCreature(target))
        }
    }

    /** Mirrors `Lua::setCreatureMetatable` for the pushed userdata. */
    private fun pushCreature(actor: CombatActor?): LuaValue {
        val creature = (actor as? LuaCombatActor)?.creature ?: return LuaValue.NIL
        val meta = when (creature) {
            is Player -> "Player"
            is Monster -> "Monster"
            is Npc -> "Npc"
            else -> "Creature"
        }
        return env.userdata.wrap(meta, creature)
    }

    private companion object {
        /** `CALLBACK_PARAM_LEVELMAGICVALUE` (creatures_definitions.hpp:324). */
        const val CALLBACK_PARAM_LEVELMAGICVALUE = 0

        /** `CALLBACK_PARAM_SKILLVALUE`. */
        const val CALLBACK_PARAM_SKILLVALUE = 1

        /** `CALLBACK_PARAM_TARGETTILE`. */
        const val CALLBACK_PARAM_TARGETTILE = 2

        /** `CALLBACK_PARAM_TARGETCREATURE`. */
        const val CALLBACK_PARAM_TARGETCREATURE = 3
    }
}

/**
 * Lua-facing combat entry-point execution over the wave-64 combat core.
 *
 * Transcribes the entry points the Lua spell scripts reach:
 *
 *  - `CombatFunctions::luaCombatExecute` (combat_functions.cpp:165-250) ->
 *    `Combat::doCombat` target/position overloads (combat.cpp:1461-1505);
 *  - `GlobalFunctions::luaDoTargetCombatHealth/Mana/Condition/Dispel`
 *    (global_functions.cpp:348-560) -> `Combat::doCombatHealth`/`doCombatMana`/
 *    `doCombatCondition`/`doCombatDispel` (combat.cpp:1645-1830);
 *  - `GlobalFunctions::luaDoAreaCombatHealth/Mana/Condition/Dispel`
 *    (global_functions.cpp:316-530) -> the `Combat::CombatFunc` area dispatch
 *    (combat.cpp:1507-1643).
 *
 * Every world access is an injected seam (AGENTS.md 1.1); the defaults keep an
 * unwired host inert. The Lua callback scripts of `combat:setCallback` stay
 * Lua: [callbackBridge] resolves them (AGENTS.md 1.4).
 */
class LuaCombatExecutor(
    /** `Creature::blockHit`'s `uniform_random` (creature.cpp:968) source. */
    var random: Random = Random(),
) {
    /** The game-owned [CreatureCombatTarget] state seam. */
    var targetState: CombatTargetState = CombatTargetState.Inert

    /** The `g_game()` effect fanout seam of the area dispatch. */
    var effects: CombatEffects = LuaCombatNoEffects

    /** `g_game().getCreatureByID(id)` (game.cpp:1296). */
    var creatureById: (Long) -> Creature? = { null }

    /** `g_game().getPlayerByNameWildcard(name)` (spells.cpp:1193). */
    var playerByName: (String) -> Player? = { null }

    /**
     * `Creature::getCombatValues(min, max)` (combat.cpp:186-190). PARITY GAP:
     * the monster behavior state owns the values in the port, so the default
     * resolves none and the formula/callback matrix runs instead.
     */
    var combatValuesOf: (Creature) -> Pair<Int, Int>? = { null }

    /** `Creature::getFaction()` projection; see [LuaCombatWorld.factionOf]. */
    var factionOf: (Creature) -> Faction = { if (it is Player) Faction.Player else Faction.Default }

    /** `g_game().getWorldType() == WORLD_TYPE_NO_PVP` (combat.cpp:1525). */
    var noPvpWorld: () -> Boolean = { true }

    /** The map behind `g_game().map` (combat.cpp:2310-2317); null = area entry points are inert. */
    var gameMap: GameMap? = null

    /** The Lua callback bridge of `combat:setCallback`; null before an environment is attached. */
    var callbackBridge: LuaCombatCallbackBridge? = null

    private fun newWorld(): LuaCombatWorld = LuaCombatWorld(targetState, effects, factionOf)

    // --- `CombatFunctions::luaCombatExecute` -----------------------------------

    /**
     * `CombatFunctions::luaCombatExecute` (combat_functions.cpp:165-250):
     * dispatches the Lua variant against the resolved target/position.
     */
    fun execute(combat: LuaCombat, creature: Creature?, variant: LuaVariant): Boolean {
        combat.instantSpellName = variant.instantName
        combat.runeSpellName = variant.runeName
        prepareCallbacks(combat, creature)

        return when (variant.type) {
            LuaVariantType.NUMBER -> {
                val target = creatureById(variant.number) ?: return false
                if (combat.area != null) {
                    dispatchPosition(combat, creature, target.position)
                } else {
                    dispatchTarget(combat, creature, target)
                }
                true
            }

            LuaVariantType.POSITION -> {
                dispatchPosition(combat, creature, variant.pos ?: Position(0, 0, 0))
                true
            }

            LuaVariantType.TARGETPOSITION -> {
                val position = variant.pos ?: Position(0, 0, 0)
                if (combat.area != null) {
                    dispatchPosition(combat, creature, position)
                } else {
                    val world = newWorld()
                    val casterActor = creature?.let(world::actorOf)
                    // combat_functions.cpp:216-218: `postCombatEffects` plus the
                    // `CONST_ME_POFF` magic effect at the target position.
                    Combat.postCombatEffects(
                        casterActor,
                        creature?.position ?: Position(0, 0, 0),
                        position,
                        combat.params,
                        effects,
                    )
                    effects.addMagicEffect(casterActor, position, Combat.CONST_ME_POFF)
                }
                true
            }

            LuaVariantType.STRING -> {
                val target = playerByName(variant.text) ?: return false
                dispatchTarget(combat, creature, target)
                true
            }

            LuaVariantType.NONE -> false
        }
    }

    /**
     * `Combat::doCombat(caster, target)` (combat.cpp:1461-1484) with the
     * wave-64 `Combat.getCombatDamage` resolution kept explicit.
     *
     * PARITY GAP: the `params.combatType == COMBAT_NONE` branch's
     * `Combat::combatTileEffects` field placement stays with the reduced
     * `Combat.doCombatDefault` (its KDoc records the same gap).
     */
    private fun dispatchTarget(combat: LuaCombat, caster: Creature?, target: Creature) {
        val world = newWorld()
        val casterActor = caster?.let(world::actorOf)
        val targetActor = world.actorOf(target)
        val hasCombatType = combat.params.combatType != CombatType.NONE
        val damage = if (hasCombatType) resolveDamage(combat, caster) else CombatDamage()

        sendImpactEffect(combat.params, casterActor, target.position)
        if (hasCombatType) {
            if (damage.primaryType != CombatType.MANADRAIN) {
                Combat.doCombatHealth(
                    caster?.let(world::targetOf),
                    world.targetOf(target),
                    damage,
                    combat.params,
                    random,
                )
            } else {
                Combat.doCombatMana(
                    caster?.let(world::targetOf),
                    world.targetOf(target),
                    damage,
                    combat.params,
                    random,
                )
            }
        } else {
            // `Combat::doCombatDefault` -> `CombatNullFunc` (combat.cpp:1836, 1100-1103).
            Combat.doCombatDefault(caster?.let(world::targetOf), world.targetOf(target), combat.params)
            removeCombatCondition(target, combat.params.dispelType)
        }
        combat.params.targetCallback?.onTargetCombat(casterActor, targetActor)
        postTargetEffects(combat.params, casterActor, target.position)
    }

    /**
     * `Combat::doCombat(caster, position)` (combat.cpp:1486-1505) over the
     * `Combat::CombatFunc` area dispatch.
     */
    private fun dispatchPosition(combat: LuaCombat, caster: Creature?, position: Position) {
        val map = gameMap ?: return
        val world = newWorld()
        val casterActor = caster?.let(world::actorOf)
        val damage = if (combat.params.combatType != CombatType.NONE) resolveDamage(combat, caster) else null
        val func = when {
            damage == null -> nullFunc(world, caster)
            damage.primaryType != CombatType.MANADRAIN -> healthFunc(world, caster)
            else -> manaFunc(world, caster)
        }

        Combat.combatFunc(
            caster = casterActor,
            origin = caster?.position ?: Position(0, 0, 0),
            toPos = position,
            area = combat.area,
            params = combat.params,
            map = world.combatMapOf(map),
            effects = effects,
            damage = damage,
            func = func,
            noPvpWorld = noPvpWorld(),
        )
    }

    /** `Combat::CombatHealthFunc` (combat.cpp:844-947) reduced to the ported dispatch. */
    private fun healthFunc(world: LuaCombatWorld, caster: Creature?): CombatFunction =
        CombatFunction { _, target, params, damage ->
            val creatureTarget = (target as? LuaCombatActor)?.creature ?: return@CombatFunction
            Combat.doCombatHealth(caster?.let(world::targetOf), world.targetOf(creatureTarget), damage, params, random)
        }

    /** `Combat::CombatManaFunc` (combat.cpp:949-1001) reduced to the ported dispatch. */
    private fun manaFunc(world: LuaCombatWorld, caster: Creature?): CombatFunction =
        CombatFunction { _, target, params, damage ->
            val creatureTarget = (target as? LuaCombatActor)?.creature ?: return@CombatFunction
            Combat.doCombatMana(caster?.let(world::targetOf), world.targetOf(creatureTarget), damage, params, random)
        }

    /** `Combat::CombatConditionFunc` (combat.cpp:1031-1092) per target. */
    private fun conditionFunc(world: LuaCombatWorld, caster: Creature?): CombatFunction =
        CombatFunction { _, target, params, _ ->
            val creatureTarget = (target as? LuaCombatActor)?.creature ?: return@CombatFunction
            Combat.doCombatDefault(caster?.let(world::targetOf), world.targetOf(creatureTarget), params)
        }

    /** `Combat::CombatDispelFunc` (combat.cpp:1094-1098) per target. */
    private fun dispelFunc(): CombatFunction =
        CombatFunction { _, target, params, _ ->
            val creatureTarget = (target as? LuaCombatActor)?.creature ?: return@CombatFunction
            removeCombatCondition(creatureTarget, params.dispelType)
        }

    /** `Combat::CombatNullFunc` (combat.cpp:1100-1103): conditions + dispel per target. */
    private fun nullFunc(world: LuaCombatWorld, caster: Creature?): CombatFunction =
        CombatFunction { _, target, params, _ ->
            val creatureTarget = (target as? LuaCombatActor)?.creature ?: return@CombatFunction
            Combat.doCombatDefault(caster?.let(world::targetOf), world.targetOf(creatureTarget), params)
            removeCombatCondition(creatureTarget, params.dispelType)
        }

    /**
     * `Combat::getCombatDamage(caster, target)` (combat.cpp:154-231) resolved
     * from the Lua `Combat` state. PARITY GAPS: the Monk elemental-bond
     * override, the Wheel of Destiny combat data and the weapon-skill formula
     * (`COMBAT_FORMULA_SKILL` with a weapon) stay with their owning systems.
     */
    private fun resolveDamage(combat: LuaCombat, caster: Creature?): CombatDamage {
        val casterPlayer = caster as? Player
        return Combat.getCombatDamage(
            formulaType = combat.formulaType,
            random = random,
            mina = combat.mina,
            minb = combat.minb,
            maxa = combat.maxa,
            maxb = combat.maxb,
            combatType = combat.params.combatType,
            origin = combat.params.origin,
            levelFormula = casterPlayer?.let { Combat.getLevelFormula(it.level, it.getMagicLevel(), 0) } ?: 0,
            combatValues = caster?.let { combatValuesOf(it) },
            casterVocation = casterPlayer?.getVocationId() ?: Vocation.VOCATION_NONE,
            valueCallback = combat.params.valueCallback,
            useCharges = combat.params.useCharges,
            level = casterPlayer?.level ?: 0,
            magicLevelSkill = casterPlayer?.getMagicLevel() ?: 0,
            instantSpellName = combat.instantSpellName,
            runeSpellName = combat.runeSpellName,
        )
    }

    // --- `GlobalFunctions::luaDoTargetCombat*` ---------------------------------

    /** `luaDoTargetCombatHealth` (global_functions.cpp:348-397). */
    fun doTargetCombatHealth(
        caster: Creature?,
        target: Creature,
        combatType: Int,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean {
        val world = newWorld()
        val casterActor = caster?.let(world::actorOf)
        val params = CombatParams(impactEffect = effect)
        sendImpactEffect(params, casterActor, target.position)
        // The wave-64 `combat.GlobalFunctions` port builds the
        // `normal_random(min, max)` damage and the healing/manadrain
        // aggressiveness rule exactly like the C++ binding.
        CombatGlobalFunctions.doTargetCombatHealth(
            caster = caster?.let(world::targetOf),
            target = world.targetOf(target),
            combatType = combatTypeOf(combatType),
            min = min,
            max = max,
            params = params,
            random = random,
            origin = combatOriginOf(origin),
            instantSpellName = instantSpellName,
            runeSpellName = runeSpellName,
        )
        postTargetEffects(params, casterActor, target.position)
        return true
    }

    /** `luaDoTargetCombatMana` (global_functions.cpp:429-464). */
    fun doTargetCombatMana(
        caster: Creature?,
        target: Creature,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean {
        val world = newWorld()
        val casterActor = caster?.let(world::actorOf)
        val params = CombatParams(impactEffect = effect)
        sendImpactEffect(params, casterActor, target.position)
        CombatGlobalFunctions.doTargetCombatMana(
            caster = caster?.let(world::targetOf),
            target = world.targetOf(target),
            min = min,
            max = max,
            params = params,
            random = random,
            origin = combatOriginOf(origin),
            instantSpellName = instantSpellName,
            runeSpellName = runeSpellName,
        )
        postTargetEffects(params, casterActor, target.position)
        return true
    }

    /** `luaDoTargetCombatCondition` (global_functions.cpp:486-513). */
    fun doTargetCombatCondition(
        caster: Creature?,
        target: Creature,
        condition: Condition,
        effect: Int,
    ): Boolean {
        val world = newWorld()
        val casterActor = caster?.let(world::actorOf)
        val params = CombatParams(impactEffect = effect)
        params.conditionList += condition.clone()
        sendImpactEffect(params, casterActor, target.position)
        Combat.doCombatDefault(caster?.let(world::targetOf), world.targetOf(target), params)
        postTargetEffects(params, casterActor, target.position)
        return true
    }

    /** `luaDoTargetCombatDispel` (global_functions.cpp:533-560). */
    fun doTargetCombatDispel(
        caster: Creature?,
        target: Creature,
        dispelType: Int,
        effect: Int,
    ): Boolean {
        val world = newWorld()
        val casterActor = caster?.let(world::actorOf)
        val params = CombatParams(impactEffect = effect, dispelType = conditionTypeOf(dispelType))
        sendImpactEffect(params, casterActor, target.position)
        removeCombatCondition(target, params.dispelType)
        postTargetEffects(params, casterActor, target.position)
        return true
    }

    // --- `GlobalFunctions::luaDoAreaCombat*` -----------------------------------

    /** `luaDoAreaCombatHealth` (global_functions.cpp:316-346). */
    fun doAreaCombatHealth(
        caster: Creature?,
        position: Position,
        area: AreaCombat?,
        combatType: Int,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean {
        val type = combatTypeOf(combatType)
        val damage = CombatDamage(
            primaryType = type,
            // C++ reads `normal_random(getNumber(6), getNumber(5))`.
            primaryValue = normalRandom(max, min, random),
            origin = combatOriginOf(origin),
            instantSpellName = instantSpellName,
            runeSpellName = runeSpellName,
        )
        val params = CombatParams(combatType = type, impactEffect = effect)
        return areaDispatch(caster, position, area, damage, params, ::healthFunc)
    }

    /** `luaDoAreaCombatMana` (global_functions.cpp:399-427). */
    fun doAreaCombatMana(
        caster: Creature?,
        position: Position,
        area: AreaCombat?,
        min: Int,
        max: Int,
        effect: Int,
        origin: Int,
        instantSpellName: String,
        runeSpellName: String,
    ): Boolean {
        val damage = CombatDamage(
            primaryType = CombatType.MANADRAIN,
            primaryValue = normalRandom(min, max, random),
            origin = combatOriginOf(origin),
            instantSpellName = instantSpellName,
            runeSpellName = runeSpellName,
        )
        val params = CombatParams(impactEffect = effect)
        return areaDispatch(caster, position, area, damage, params, ::manaFunc)
    }

    /** `luaDoAreaCombatCondition` (global_functions.cpp:470-499). */
    fun doAreaCombatCondition(
        caster: Creature?,
        position: Position,
        area: AreaCombat?,
        condition: Condition,
        effect: Int,
    ): Boolean {
        val params = CombatParams(impactEffect = effect)
        params.conditionList += condition
        return areaDispatch(caster, position, area, null, params, ::conditionFunc)
    }

    /** `luaDoAreaCombatDispel` (global_functions.cpp:515-531). */
    fun doAreaCombatDispel(
        caster: Creature?,
        position: Position,
        area: AreaCombat?,
        dispelType: Int,
        effect: Int,
    ): Boolean {
        val params = CombatParams(impactEffect = effect, dispelType = conditionTypeOf(dispelType))
        return areaDispatch(caster, position, area, null, params) { _, _ -> dispelFunc() }
    }

    /**
     * The shared `Combat::CombatFunc` call of the `doAreaCombat*` entry points;
     * without a wired map the entry points stay inert (PARITY GAP).
     */
    private fun areaDispatch(
        caster: Creature?,
        position: Position,
        area: AreaCombat?,
        damage: CombatDamage?,
        params: CombatParams,
        funcFactory: (LuaCombatWorld, Creature?) -> CombatFunction,
    ): Boolean {
        val map = gameMap ?: return false
        val world = newWorld()
        val casterActor = caster?.let(world::actorOf)
        val func = funcFactory(world, caster)

        Combat.combatFunc(
            caster = casterActor,
            origin = caster?.position ?: Position(0, 0, 0),
            toPos = position,
            area = area,
            params = params,
            map = world.combatMapOf(map),
            effects = effects,
            damage = damage,
            func = func,
            noPvpWorld = noPvpWorld(),
        )
        return true
    }

    // --- shared helpers --------------------------------------------------------

    /** `Combat::setCallback` result wiring for one `combat:execute` call. */
    private fun prepareCallbacks(combat: LuaCombat, caster: Creature?) {
        val bridge = callbackBridge ?: return
        combat.params.valueCallback = bridge.valueCallback(combat, caster)
        combat.params.tileCallback = bridge.tileCallback(combat)
        combat.params.targetCallback = bridge.targetCallback(combat)
    }

    /** `Combat::sendCombatEffect`'s impact effect (combat.cpp:1655-1658). */
    private fun sendImpactEffect(params: CombatParams, casterActor: CombatActor?, position: Position) {
        if (casterActor == null || params.impactEffect == Combat.CONST_ME_NONE) {
            return
        }
        effects.addMagicEffect(casterActor, position, params.impactEffect)
    }

    /** The target overload's distance effect + sound fanout (combat.cpp:1690-1706). */
    private fun postTargetEffects(params: CombatParams, casterActor: CombatActor?, position: Position) {
        if (casterActor != null && params.distanceEffect != Combat.CONST_ANI_NONE) {
            effects.addDistanceEffect(casterActor, casterActor.getPosition(), position, params.distanceEffect)
        }

        if (params.soundImpactEffect != Combat.SOUND_SILENCE) {
            effects.sendDoubleSoundEffect(position, params.soundCastEffect, params.soundImpactEffect, casterActor)
        } else if (params.soundCastEffect != Combat.SOUND_SILENCE) {
            effects.sendSingleSoundEffect(position, params.soundCastEffect, casterActor)
        }
    }

    /**
     * `Creature::removeCombatCondition(type)` (creature.cpp:1469-1484): every
     * tracked condition of the type routes through `onCombatRemoveCondition`.
     */
    private fun removeCombatCondition(target: Creature, type: ConditionType) {
        for (condition in target.getConditionsByType(type)) {
            if (target is Player) {
                target.onCombatRemoveCondition(condition)
            } else {
                target.removeCondition(condition)
            }
        }
    }

    private fun combatTypeOf(value: Int): CombatType =
        CombatType.entries.firstOrNull { it.value == value } ?: CombatType.NONE

    private fun combatOriginOf(value: Int): CombatOrigin =
        CombatOrigin.entries.firstOrNull { it.value == value } ?: CombatOrigin.SPELL

    private fun conditionTypeOf(value: Int): ConditionType =
        ConditionType.entries.firstOrNull { it.value == value } ?: ConditionType.CONDITION_NONE
}
