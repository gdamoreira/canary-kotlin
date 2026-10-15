package org.opentibia.canary.combat

import org.opentibia.canary.combat.condition.ConditionPlayer
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Skulls_t
import org.opentibia.canary.creatures.monsters.Faction
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.game.zones.ZoneType
import org.opentibia.canary.map.Position
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Test double for the [CombatActor]/[CombatPlayer] validation seam.
 *
 * It carries the exact `Creature`/`Player` state the C++ target-validation
 * functions read, without a live world.
 */
open class FakeCombatActor : CombatActor {
    var playerValue: CombatPlayer? = null
    var monsterValue: CombatMonster? = null
    var npcValue: Boolean = false
    var masterValue: CombatActor? = null
    var summonValue: Boolean = false
    var attackableValue: Boolean = true
    var zoneTypeValue: ZoneType = ZoneType.ZONE_NORMAL
    var factionValue: Faction = Faction.Default
    var positionValue: Position = Position(100, 100, 7)
    var tileValue: CombatTile? = null

    /** `Creature::m_combatDamage` (`creature.hpp:1077`) backing [getCombatDamage]. */
    var combatDamageValue: CombatDamage = CombatDamage()

    override fun getPlayer(): CombatPlayer? = playerValue
    override fun getMonster(): CombatMonster? = monsterValue
    override fun getNpc(): Boolean = npcValue
    override fun getMaster(): CombatActor? = masterValue
    override fun isSummon(): Boolean = summonValue
    override fun isAttackable(): Boolean = attackableValue
    override fun getZoneType(): ZoneType = zoneTypeValue
    override fun getFaction(): Faction = factionValue
    override fun getPosition(): Position = positionValue
    override fun getTile(): CombatTile? = tileValue

    override fun getCombatDamage(): CombatDamage = combatDamageValue

    override fun setCombatDamage(damage: CombatDamage) {
        combatDamageValue = damage
    }
}

open class FakeCombatPlayer : FakeCombatActor(), CombatPlayer {
    var flags: MutableSet<PlayerFlags_t> = mutableSetOf()
    var levelValue: Int = 100
    var skullValue: Skulls_t = Skulls_t.SKULL_NONE
    var skullClientValue: Skulls_t = Skulls_t.SKULL_NONE
    var secureModeValue: Boolean = false
    var permittedConditionInPzValue: Boolean = false
    var vocationCanCombatValue: Boolean = true
    var vocationIdValue: Int = 1
    var firstOnStackValue: Boolean = true

    /** Last argument passed to [getSkullClient]. */
    var skullClientTarget: CombatActor? = null

    /** Every `Player::sendCancelMessage` return value in order. */
    val cancelMessages: MutableList<ReturnValue> = mutableListOf()

    /** Every `Player::sendMagicEffect` `(position, effect)` pair in order. */
    val magicEffects: MutableList<Pair<Position, Int>> = mutableListOf()

    /** `Player::addInFightTicks()` call count. */
    var fightTicks: Int = 0

    override fun getPlayer(): CombatPlayer = this

    override fun hasFlag(flag: PlayerFlags_t): Boolean = flag in flags

    override fun getLevel(): Int = levelValue

    override fun getSkull(): Skulls_t = skullValue

    override fun getSkullClient(creature: CombatActor): Skulls_t {
        skullClientTarget = creature
        return skullClientValue
    }

    override fun hasSecureMode(): Boolean = secureModeValue

    override fun hasPermittedConditionInPZ(): Boolean = permittedConditionInPzValue

    override fun getVocationCanCombat(): Boolean = vocationCanCombatValue

    override fun getVocationId(): Int = vocationIdValue

    override fun isFirstOnStack(): Boolean = firstOnStackValue

    override fun sendCancelMessage(ret: ReturnValue) {
        cancelMessages.add(ret)
    }

    override fun sendMagicEffect(position: Position, effect: Int) {
        magicEffects.add(position to effect)
    }

    override fun addInFightTicks() {
        fightTicks++
    }
}

class FakeCombatMonster(
    var enemyFactions: MutableSet<Faction> = mutableSetOf(),
) : CombatMonster {
    override fun isEnemyFaction(faction: Faction): Boolean = faction in enemyFactions

    /** Every `Monster::onAttackedByPlayer` argument in order. */
    val attackedByPlayers: MutableList<ConditionPlayer> = mutableListOf()

    override fun onAttackedByPlayer(attackerPlayer: ConditionPlayer) {
        attackedByPlayers.add(attackerPlayer)
    }
}

class FakeCombatTile(
    var positionValue: Position = Position(100, 100, 7),
) : CombatTile {
    var flags: MutableSet<TileFlags> = mutableSetOf()
    var properties: MutableSet<Int> = mutableSetOf()
    var teleport: Boolean = false
    var itemIdList: MutableList<Int> = mutableListOf()

    /** `Tile::getCreatures()`; the first entry is the top creature. */
    var creatureList: MutableList<CombatActor> = mutableListOf()

    override fun hasFlag(flag: TileFlags): Boolean = flag in flags
    override fun hasProperty(property: Int): Boolean = property in properties
    override fun getPosition(): Position = positionValue
    override fun getTeleportItem(): Boolean = teleport
    override fun getItemIds(): List<Int> = itemIdList
    override fun getCreatures(): List<CombatActor> = creatureList
    override fun getTopCreature(): CombatActor? = creatureList.firstOrNull()
}

/** Recording double for the `g_game()` effect/tile calls of the [Combat] tail. */
class FakeCombatEffects : CombatEffects {
    data class MagicEffect(val caster: CombatActor?, val position: Position, val effect: Int)

    data class Sound(val position: Position, val castEffect: Int, val impactEffect: Int, val caster: CombatActor?)

    data class DistanceEffect(val caster: CombatActor, val fromPos: Position, val toPos: Position, val effect: Int)

    data class FieldItem(val caster: CombatActor?, val tile: CombatTile, val itemId: Int)

    val magicEffects: MutableList<MagicEffect> = mutableListOf()
    val doubleSounds: MutableList<Sound> = mutableListOf()
    val singleSounds: MutableList<Sound> = mutableListOf()
    val distanceEffects: MutableList<DistanceEffect> = mutableListOf()
    val fieldItems: MutableList<FieldItem> = mutableListOf()

    override fun addMagicEffect(caster: CombatActor?, position: Position, effect: Int) {
        magicEffects.add(MagicEffect(caster, position, effect))
    }

    override fun sendDoubleSoundEffect(position: Position, castEffect: Int, impactEffect: Int, caster: CombatActor?) {
        doubleSounds.add(Sound(position, castEffect, impactEffect, caster))
    }

    override fun sendSingleSoundEffect(position: Position, effect: Int, caster: CombatActor?) {
        singleSounds.add(Sound(position, effect, 0, caster))
    }

    override fun addDistanceEffect(caster: CombatActor, fromPos: Position, toPos: Position, effect: Int) {
        distanceEffects.add(DistanceEffect(caster, fromPos, toPos, effect))
    }

    override fun createFieldItem(caster: CombatActor?, tile: CombatTile, itemId: Int) {
        fieldItems.add(FieldItem(caster, tile, itemId))
    }
}

class FakeCombatMap : CombatMap {
    val tiles: MutableMap<Position, CombatTile> = mutableMapOf()

    /** Positions handed out by [getOrCreateTile] in order. */
    val created: MutableList<Position> = mutableListOf()

    /** `(from, to)` pairs passed to [isSightClear] in order. */
    val sightChecks: MutableList<Pair<Position, Position>> = mutableListOf()

    var sightClear: Boolean = true

    override fun getTile(position: Position): CombatTile? = tiles[position]

    override fun getOrCreateTile(position: Position): CombatTile {
        tiles[position]?.let { return it }
        val tile = FakeCombatTile(position)
        tiles[position] = tile
        created.add(position)
        return tile
    }

    override fun isSightClear(fromPos: Position, toPos: Position, checkFloor: Boolean): Boolean {
        sightChecks.add(fromPos to toPos)
        return sightClear
    }
}
