package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.WorldType

/**
 * Transcript tests for the production `MagicField` model:
 *
 *  - production instance creation (`Item::CreateItem`'s `isMagicField` branch,
 *    src/items/item.cpp:411-472) through `Item::create`;
 *  - the owner stamp (`Item::setOwner(creature)`, item.cpp:1541-1548) applied
 *    at the field-creation root (`Combat::combatTileEffects`,
 *    src/creatures/combat/combat.cpp:1233-1247);
 *  - `MagicField::onStepInField` (combat.cpp:2590-2641): the
 *    `ItemType::conditionDamage` clone application, the owner-resolution gates
 *    (combat.cpp:2614-2636) and the `CONDITION_PARAM_OWNER` stamp.
 *
 * The world owner catalogs (`ownerCreatureProvider` and the gate seams) are
 * bound by `Game::internalAddItem` when the field enters the world, mirroring
 * the C++ `g_game()` lookups.
 */
class MagicFieldModelTest {

    private val fieldId = 1497
    private val center = Position(1000, 1000, 7)

    private fun fieldType(damage: Int, replaceable: Boolean = true): ItemType =
        ItemType(
            id = fieldId,
            name = "fire field",
            type = ItemTypes_t.ITEM_TYPE_MAGICFIELD,
            combatType = CombatType.FIREDAMAGE,
            replaceable = replaceable,
        ).apply {
            if (damage != 0) {
                conditionDamage = ConditionDamage(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_FIRE,
                ).apply {
                    setParam(ConditionParam.CONDITION_PARAM_FIELD, 1)
                    addDamage(1, 1000, -damage)
                }
            }
        }

    private fun registry(damage: Int, replaceable: Boolean = true): ItemTypeRegistry =
        ItemTypeRegistry().apply { register(fieldType(damage, replaceable)) }

    private fun newGame(): Game = Game(GameMap(), GameDispatcher())

    private fun Game.ground(): Unit {
        map.getOrCreateTile(center).ground = Item(id = 100)
    }

    private fun placeVictim(game: Game, position: Position = center): Player {
        val victim = Player(id = 1, name = "Victim", maxHealth = 100, health = 100, position = position)
        game.addPlayer(victim)
        assertThat(game.internalPlaceCreature(victim, position)).isTrue()
        return victim
    }

    @Test
    fun `Item create builds a MagicField that reads damage from the item type`() {
        val item = Item.create(fieldId, registry = registry(damage = 20))

        assertThat(item).isInstanceOf(MagicField::class.java)
        val field = item as MagicField
        assertThat(field.getMagicField()).isSameAs(field)
        assertThat(field.getDamage()).isEqualTo(20)
        assertThat(field.getCombatType()).isEqualTo(CombatType.FIREDAMAGE)
        assertThat(field.isReplaceable()).isTrue()
    }

    @Test
    fun `MagicField replaceable reads the item type flag`() {
        val pluggable = Item.create(fieldId, registry = registry(damage = 20, replaceable = true)) as MagicField
        val permanent = Item.create(fieldId, registry = registry(damage = 20, replaceable = false)) as MagicField

        assertThat(pluggable.isReplaceable()).isTrue()
        assertThat(permanent.isReplaceable()).isFalse()
    }

    @Test
    fun `field creation stamps the creating creature as owner`() {
        val owner = Player(id = 7, name = "Owner", maxHealth = 100, health = 100, position = Position(999, 1000, 7))
        val field = Item.create(fieldId, registry = registry(damage = 20)) as MagicField

        field.setOwner(owner)

        assertThat(field.getOwnerId()).isEqualTo(7L)
        assertThat(field.hasOwner()).isTrue()
        assertThat(field.isOwner(owner)).isTrue()
    }

    @Test
    fun `step-in applies the item type condition damage and credits the owner`() {
        val game = newGame()
        game.ground()
        val owner = Player(id = 7, name = "Owner", maxHealth = 100, health = 100, position = Position(999, 1000, 7))
        game.addPlayer(owner)
        val victim = placeVictim(game)

        val field = Item.create(fieldId, registry = registry(damage = 20)) as MagicField
        field.setOwner(owner)
        game.internalAddItem(victim.getTile(), field, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)

        field.onStepInField(victim)

        // `field && player && attackerPlayer` halves the player-to-player hit
        // (condition.cpp:2011-2013).
        assertThat(victim.health).isEqualTo(90)
        assertThat(victim.getDamageMap()[7L]?.total).isEqualTo(10)
        assertThat(victim.lastHitCreatureId).isEqualTo(7L)
    }

    @Test
    fun `fresh owner-owned field keeps the owner stamp inside the five-second window`() {
        var clock = 0L
        val game = newGame()
        game.ground()
        val owner = Player(id = 7, name = "Owner", maxHealth = 100, health = 100, position = Position(999, 1000, 7))
        game.addPlayer(owner)
        val victim = placeVictim(game)

        val field = MagicField(fieldId, type = fieldType(damage = 20), now = { clock })
        field.setOwner(owner)
        game.internalAddItem(victim.getTile(), field, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
        clock = 4_000

        field.onStepInField(victim)

        assertThat(victim.health).isEqualTo(90)
        assertThat(victim.getDamageMap()[7L]?.total).isEqualTo(10)
    }

    @Test
    fun `stale harmful field in a pvp world stays unattributed`() {
        var clock = 0L
        val game = newGame()
        game.ground()
        val owner = Player(id = 7, name = "Owner", maxHealth = 100, health = 100, position = Position(999, 1000, 7))
        game.addPlayer(owner)
        val victim = placeVictim(game)
        // A non-`VOCATION_NONE` vocation id on both sides clears the
        // `Combat::isProtected` vocation clause, so the field stays harmful.
        owner.vocationId = 1
        victim.vocationId = 1

        val field = MagicField(fieldId, type = fieldType(damage = 20), now = { clock })
        field.setOwner(owner)
        game.internalAddItem(victim.getTile(), field, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
        clock = 6_000

        field.onStepInField(victim)

        // Harmful + stale + target never attacked the owner -> no owner stamp
        // (combat.cpp:2638) -> no attacker -> no halving and no credit.
        assertThat(victim.health).isEqualTo(80)
        assertThat(victim.getDamageMap()).isEmpty()
    }

    @Test
    fun `stale owner-owned field in a no-pvp world keeps the owner stamp`() {
        var clock = 0L
        val game = newGame()
        game.ground()
        game.setWorldType(WorldType.NO_PVP)
        val owner = Player(id = 7, name = "Owner", maxHealth = 100, health = 100, position = Position(999, 1000, 7))
        game.addPlayer(owner)
        val victim = placeVictim(game)

        val field = MagicField(fieldId, type = fieldType(damage = 20), now = { clock })
        field.setOwner(owner)
        game.internalAddItem(victim.getTile(), field, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
        clock = 6_000

        field.onStepInField(victim)

        // `!harmfulField` stamps the owner (combat.cpp:2638): a player-owned
        // field in a no-PvP world is not a harmful PvP field.
        assertThat(victim.health).isEqualTo(90)
        assertThat(victim.getDamageMap()[7L]?.total).isEqualTo(10)
    }

    @Test
    fun `stale field still attributes against a target the owner attacked`() {
        var clock = 0L
        val game = newGame()
        game.ground()
        val owner = Player(id = 7, name = "Owner", maxHealth = 100, health = 100, position = Position(999, 1000, 7))
        game.addPlayer(owner)
        val victim = placeVictim(game)
        owner.vocationId = 1
        victim.vocationId = 1
        victim.addDamagePoints(owner, 1)

        val field = MagicField(fieldId, type = fieldType(damage = 20), now = { clock })
        field.setOwner(owner)
        game.internalAddItem(victim.getTile(), field, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)
        clock = 6_000

        field.onStepInField(victim)

        // `creature->hasBeenAttacked(ownerId)` keeps the stamp
        // (combat.cpp:2638): the hit lands halved and credited on top of the
        // pre-recorded point.
        assertThat(victim.health).isEqualTo(90)
        assertThat(victim.getDamageMap()[7L]?.total).isEqualTo(11)
    }

    @Test
    fun `ownerless field applies the full condition damage`() {
        val game = newGame()
        game.ground()
        val victim = placeVictim(game)

        val field = Item.create(fieldId, registry = registry(damage = 20)) as MagicField
        game.internalAddItem(victim.getTile(), field, INDEX_WHEREEVER, CylinderFlags.FLAG_NOLIMIT)

        field.onStepInField(victim)

        assertThat(victim.health).isEqualTo(80)
        assertThat(victim.getDamageMap()).isEmpty()
    }
}
