package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.ConditionTargetState
import org.opentibia.canary.combat.CreatureConditionTarget
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionParam
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

class WorldObjectsTest {

    private fun fieldType(damage: Int): ItemType =
        ItemType(
            id = 1487,
            name = "fire field",
            type = ItemTypes_t.ITEM_TYPE_MAGICFIELD,
            combatType = CombatType.FIREDAMAGE,
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

    private fun player(id: Long, name: String, position: Position): Player =
        Player(id = id, name = name, maxHealth = 100, health = 100, position = position)
            .also { it.conditionTarget = CreatureConditionTarget(it) }

    @Test
    fun `stepping on magic field applies the item type condition damage`() {
        val fireField = MagicField(id = 1487, type = fieldType(20))
        val victim = player(1, "Adventurer", Position(100, 100, 7))

        fireField.onStepInField(victim)
        assertThat(victim.health).isEqualTo(80)
    }

    @Test
    fun `stepping on an owned magic field credits the field owner`() {
        // `MagicField::onStepInField` stamps the owner on the cloned condition
        // (src/creatures/combat/combat.cpp:2612-2638); the condition's
        // owner-resolved drain credits the field owner
        // (condition.cpp:2000-2033, creature.cpp:893-899).
        val fieldOwner = player(7, "Owner", Position(99, 100, 7))
        val fireField = MagicField(id = 1487, type = fieldType(20))
        fireField.setOwner(7)
        fireField.ownerPlayerByGuidProvider = { ownerId: Long -> if (ownerId == 7L) fieldOwner else null }
        fireField.ownerCreatureProvider = { ownerId: Long -> if (ownerId == 7L) fieldOwner else null }
        fireField.ownerPlayerByIdProvider = { ownerId: Long -> if (ownerId == 7L) fieldOwner else null }

        val victim = player(1, "Adventurer", Position(100, 100, 7))
        victim.conditionTarget = CreatureConditionTarget(
            victim,
            object : ConditionTargetState {
                override fun getCreatureFromOwner(owner: Int): Creature? = if (owner == 7) fieldOwner else null
            },
        )
        fireField.onStepInField(victim)

        // `field && player && attackerPlayer` halves the hit
        // (condition.cpp:2011-2013).
        assertThat(victim.health).isEqualTo(90)
        assertThat(victim.getDamageMap()[7L]?.total).isEqualTo(10)
        assertThat(victim.lastHitCreatureId).isEqualTo(7L)
    }

    @Test
    fun `stepping on a magic field with an unresolved owner stays unattributed`() {
        val fireField = MagicField(id = 1487, type = fieldType(20))
        fireField.setOwner(7)

        val victim = player(1, "Adventurer", Position(100, 100, 7))
        fireField.onStepInField(victim)

        assertThat(victim.health).isEqualTo(80)
        assertThat(victim.getDamageMap()).isEmpty()
    }

    @Test
    fun `stepping on teleporter moves player to destination coordinates`() {
        val destination = Position(32000, 32000, 7)
        val teleporter = TeleportItem(id = 1387, destination = destination)
        val victim = player(1, "Adventurer", Position(100, 100, 7))

        teleporter.onStepIn(victim)
        assertThat(victim.position).isEqualTo(destination)
    }

    @Test
    fun `using closed door toggles state to open door`() {
        val door = DoorItem(closedId = 1210, openId = 1211, isOpen = false)
        assertThat(door.currentId).isEqualTo(1210)

        door.toggle()
        assertThat(door.isOpen).isTrue()
        assertThat(door.currentId).isEqualTo(1211)

        door.toggle()
        assertThat(door.isOpen).isFalse()
        assertThat(door.currentId).isEqualTo(1210)
    }
}
