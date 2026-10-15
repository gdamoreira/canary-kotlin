package org.opentibia.canary.map

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountType
import org.opentibia.canary.chat.SpeakClasses
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionDamage
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.players.pvp.ExpertPvp
import org.opentibia.canary.creatures.players.pvp.PvpMode_t
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.INDEX_WHEREEVER
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.MagicField
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Transcripts of the `Tile::queryAdd` depth tails
 * (src/items/tile.cpp:691-964): the monster push/field rules, the player
 * account/PZ-lock step, the `queryExpertPvpFieldStep` helper
 * (src/items/tile.cpp:34-84) and the `Creature::isMoveLocked` gate
 * (src/creatures/creature.hpp:189-194). The remaining `g_game()`/protocol
 * side effects are injected seams (AGENTS.md 1.1), bound here like the
 * composition root would.
 */
class TileQueryAddTest {

    private val types = ItemTypeRegistry().apply {
        register(ItemType(100, "ground", isGroundTile = true))
        register(
            ItemType(
                106,
                "fire field",
                type = ItemTypes_t.ITEM_TYPE_MAGICFIELD,
                combatType = CombatType.FIREDAMAGE,
            ).apply {
                conditionDamage = ConditionDamage(
                    ConditionId.CONDITIONID_COMBAT,
                    ConditionType.CONDITION_FIRE,
                ).apply { addDamage(1, 1000, -20) }
            },
        )
        register(ItemType(120, "wall", blockSolid = true))
        register(ItemType(121, "movable solid", blockSolid = true, isMovable = true))
        register(
            ItemType(
                ExpertPvp.ITEM_MAGICWALL,
                "magic wall",
                type = ItemTypes_t.ITEM_TYPE_MAGICFIELD,
                blockSolid = true,
            ),
        )
        register(
            ItemType(
                ExpertPvp.ITEM_MAGICWALL_SAFE,
                "safe magic wall",
                type = ItemTypes_t.ITEM_TYPE_MAGICFIELD,
                blockSolid = true,
            ),
        )
    }

    private val pos = Position(100, 100, 7)

    private fun item(id: Int, count: Int = 1): Item = Item(id, count = count, type = types.get(id))

    private fun tileWithGround(position: Position = pos): Tile {
        val tile = Tile(position)
        tile.internalAddThing(item(100))
        return tile
    }

    private fun player(id: Long, name: String = "P$id", position: Position = pos): Player =
        Player(id = id, name = name, maxHealth = 100, health = 100, position = position)

    private fun monster(id: Long, name: String = "M$id", position: Position = pos): Monster =
        Monster(id = id, name = name, maxHealth = 100, health = 100, position = position)

    private fun field(): MagicField =
        MagicField(106, type = types.get(106))

    // --- monster push/field rules (tile.cpp:716-782) -------------------------

    @Test
    fun `queryAdd blocks a monster on a harmful field unless immune or ignoring it`() {
        val tile = tileWithGround()
        tile.internalAddThing(field())
        val rat = monster(1)
        val ratType = MonsterType("Rat").apply { info.canWalkOnFire = false }
        tile.monsterTypeProvider = { if (it === rat) ratType else null }

        assertThat(tile.queryAdd(INDEX_WHEREEVER, rat, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(tile.queryAdd(INDEX_WHEREEVER, rat, 1, CylinderFlags.FLAG_IGNOREFIELDDAMAGE))
            .isEqualTo(ReturnValue.NOTPOSSIBLE)

        tile.ignoreFieldDamageProvider = { it === rat }
        assertThat(tile.queryAdd(INDEX_WHEREEVER, rat, 1, CylinderFlags.FLAG_IGNOREFIELDDAMAGE))
            .isEqualTo(ReturnValue.NOERROR)

        tile.ignoreFieldDamageProvider = { false }
        tile.monsterIsImmuneProvider = { m, combatType -> m === rat && combatType == CombatType.FIREDAMAGE }
        assertThat(tile.queryAdd(INDEX_WHEREEVER, rat, 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd monster push rules follow the pushed tile occupants`() {
        val tile = tileWithGround()
        val pusher = monster(1)
        val pusherType = MonsterType("Pusher").apply { info.canPushCreatures = true }
        tile.monsterTypeProvider = { if (it === pusher) pusherType else null }

        assertThat(tile.queryAdd(INDEX_WHEREEVER, pusher, 1, 0)).isEqualTo(ReturnValue.NOERROR)

        tile.addThing(monster(2, "Occupant"))
        assertThat(tile.queryAdd(INDEX_WHEREEVER, pusher, 1, 0)).isEqualTo(ReturnValue.NOERROR)

        tile.addThing(player(3, "Ghost").apply { switchGhostMode() })
        assertThat(tile.queryAdd(INDEX_WHEREEVER, pusher, 1, 0)).isEqualTo(ReturnValue.NOERROR)

        tile.addThing(player(4, "Solid"))
        assertThat(tile.queryAdd(INDEX_WHEREEVER, pusher, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    @Test
    fun `queryAdd rejects a player summon occupant and a non pushing monster`() {
        val summonTile = tileWithGround()
        val pusher = monster(1)
        val pusherType = MonsterType("Pusher").apply { info.canPushCreatures = true }
        summonTile.monsterTypeProvider = { if (it === pusher) pusherType else null }
        val summon = monster(2, "Summon")
        summon.setMaster(player(3, "Owner"))
        summonTile.addThing(summon)

        assertThat(summonTile.queryAdd(INDEX_WHEREEVER, pusher, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)

        val plainTile = tileWithGround()
        plainTile.addThing(monster(4, "Blocker"))
        assertThat(plainTile.queryAdd(INDEX_WHEREEVER, pusher, 1, 0)).isEqualTo(ReturnValue.NOTENOUGHROOM)

        val ghostTile = tileWithGround()
        ghostTile.addThing(player(5, "Ghost").apply { switchGhostMode() })
        assertThat(ghostTile.queryAdd(INDEX_WHEREEVER, pusher, 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd monster needs push items on a solid tile`() {
        val tile = tileWithGround()
        tile.internalAddThing(item(121))
        val rat = monster(1)
        val ratType = MonsterType("Rat")
        tile.monsterTypeProvider = { if (it === rat) ratType else null }

        assertThat(tile.queryAdd(INDEX_WHEREEVER, rat, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)

        ratType.info.canPushItems = true
        assertThat(tile.queryAdd(INDEX_WHEREEVER, rat, 1, 0)).isEqualTo(ReturnValue.NOERROR)

        ratType.info.canPushItems = false
        assertThat(tile.queryAdd(INDEX_WHEREEVER, rat, 1, CylinderFlags.FLAG_IGNOREBLOCKITEM))
            .isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd blocks a familiar in a protection zone only while its master is attacking`() {
        val tile = tileWithGround()
        tile.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        val familiar = monster(1, "Familiar")
        val familiarType = MonsterType("Familiar").apply { info.isFamiliar = true }
        tile.monsterTypeProvider = { if (it === familiar) familiarType else null }
        // `Player::canSee` (player.cpp:1402) is inert without a client, so the
        // attacking master models a connected player.
        val master = player(2, "Master").apply { hasClient = { true } }
        familiar.setMaster(master)

        assertThat(tile.queryAdd(INDEX_WHEREEVER, familiar, 1, 0)).isEqualTo(ReturnValue.NOERROR)

        master.setAttackedCreature(player(3, "Target"))
        assertThat(tile.queryAdd(INDEX_WHEREEVER, familiar, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    // --- creature move lock (creature.hpp:189-194) ---------------------------

    @Test
    fun `queryAdd refuses a move locked creature`() {
        val tile = tileWithGround()
        val walker = player(1)
        walker.setMoveLocked(true)

        assertThat(tile.queryAdd(INDEX_WHEREEVER, walker, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)

        walker.setMoveLocked(false)
        assertThat(tile.queryAdd(INDEX_WHEREEVER, walker, 1, 0)).isEqualTo(ReturnValue.NOERROR)

        val lockedMonster = monster(2).apply { setMoveLocked(true) }
        assertThat(tile.queryAdd(INDEX_WHEREEVER, lockedMonster, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
    }

    // --- player PZ-lock and account rules (tile.cpp:805-847) -----------------

    @Test
    fun `queryAdd denies a pz locked player crossing zones`() {
        val source = tileWithGround()
        val destination = tileWithGround(Position(pos.x + 1, pos.y, pos.z))
        val walker = player(1).apply { pzLocked = true }
        source.addThing(walker)

        destination.setFlag(TileFlags.TILESTATE_PVPZONE.value)
        assertThat(destination.queryAdd(INDEX_WHEREEVER, walker, 1, 0))
            .isEqualTo(ReturnValue.PLAYERISPZLOCKEDENTERPVPZONE)

        source.setFlag(TileFlags.TILESTATE_PVPZONE.value)
        destination.resetFlag(TileFlags.TILESTATE_PVPZONE.value)
        assertThat(destination.queryAdd(INDEX_WHEREEVER, walker, 1, 0))
            .isEqualTo(ReturnValue.PLAYERISPZLOCKEDLEAVEPVPZONE)

        source.resetFlag(TileFlags.TILESTATE_PVPZONE.value)
        destination.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        assertThat(destination.queryAdd(INDEX_WHEREEVER, walker, 1, 0)).isEqualTo(ReturnValue.PLAYERISPZLOCKED)
    }

    @Test
    fun `queryAdd limits account characters outside the protection zone`() {
        val account = Account(id = 1, name = "acc")
        val source = tileWithGround()
        source.setFlag(TileFlags.TILESTATE_PROTECTIONZONE.value)
        val destination = tileWithGround(Position(pos.x + 1, pos.y, pos.z))
        val walker = player(1, "One").apply { this.account = account }
        source.addThing(walker)
        val sibling = player(2, "Two").apply { this.account = account }
        tileWithGround(Position(pos.x, pos.y + 1, pos.z)).addThing(sibling)

        destination.maxPlayersPerAccount = 2
        destination.maxPlayersOutsidePzPerAccount = 1
        destination.playersByAccountProvider = { acc -> if (acc === account) listOf(walker, sibling) else emptyList() }
        val messages = mutableListOf<Pair<SpeakClasses, String>>()
        destination.sendCreatureSay = { _, _, type, text, _ -> messages.add(type to text) }

        assertThat(destination.queryAdd(INDEX_WHEREEVER, walker, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)
        assertThat(messages).containsExactly(
            SpeakClasses.TALKTYPE_MONSTER_SAY to
                "You can only have one character from your account outside of a protection zone.",
        )

        account.accountType = AccountType.ACCOUNT_TYPE_GAMEMASTER
        assertThat(destination.queryAdd(INDEX_WHEREEVER, walker, 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    @Test
    fun `queryAdd denies a login on a no logout tile`() {
        val tile = tileWithGround()
        tile.setFlag(TileFlags.TILESTATE_NOLOGOUT.value)
        val unplaced = player(1)

        assertThat(tile.queryAdd(INDEX_WHEREEVER, unplaced, 1, 0)).isEqualTo(ReturnValue.NOTPOSSIBLE)

        tile.resetFlag(TileFlags.TILESTATE_NOLOGOUT.value)
        assertThat(tile.queryAdd(INDEX_WHEREEVER, unplaced, 1, 0)).isEqualTo(ReturnValue.NOERROR)
    }

    // --- expert-PvP field step (tile.cpp:34-84, 794-803, 856-878) ------------

    private fun expertMagicWall(ownerMode: PvpMode_t): MagicField =
        MagicField(ExpertPvp.ITEM_MAGICWALL, type = types.get(ExpertPvp.ITEM_MAGICWALL)).apply {
            setCustomAttribute("expertPvpOwnerGuid", 99L)
            setCustomAttribute("expertPvpOwnerMode", ownerMode.value.toLong())
            setCustomAttribute("expertPvpCanonicalItemId", ExpertPvp.ITEM_MAGICWALL.toLong())
            setCustomAttribute("expertPvpSafeVisualItemId", ExpertPvp.ITEM_MAGICWALL_SAFE.toLong())
            setCustomAttribute("expertPvpBlockingVisualItemId", ExpertPvp.ITEM_MAGICWALL.toLong())
            setCustomAttribute("expertPvpOwnerWasPlayerOrSummon", true)
        }

    @Test
    fun `queryAdd expert pvp field step returns the decision value`() {
        val tile = tileWithGround()
        val wall = expertMagicWall(PvpMode_t.PVP_MODE_DOVE)
        tile.internalAddThing(wall)
        tile.expertPvpEnabled = true
        val walker = player(1)

        assertThat(tile.queryAdd(INDEX_WHEREEVER, walker, 1, 0)).isEqualTo(ReturnValue.NOERROR)

        wall.setCustomAttribute("expertPvpOwnerMode", PvpMode_t.PVP_MODE_RED_FIST.value.toLong())
        assertThat(tile.queryAdd(INDEX_WHEREEVER, walker, 1, 0)).isEqualTo(ReturnValue.NOTENOUGHROOM)

        tile.expertPvpEnabled = false
        assertThat(tile.queryAdd(INDEX_WHEREEVER, walker, 1, 0)).isEqualTo(ReturnValue.NOTENOUGHROOM)
    }

    @Test
    fun `queryAdd expert pvp field step handles the pathfinding probe`() {
        val plain = tileWithGround()
        plain.setFlag(TileFlags.TILESTATE_BLOCKPATH.value)
        val walker = player(1)

        assertThat(plain.queryAdd(INDEX_WHEREEVER, walker, 1, CylinderFlags.FLAG_PATHFINDING))
            .isEqualTo(ReturnValue.NOTPOSSIBLE)

        val tile = tileWithGround()
        val wall = expertMagicWall(PvpMode_t.PVP_MODE_DOVE)
        tile.internalAddThing(wall)
        tile.setFlag(TileFlags.TILESTATE_BLOCKPATH.value)
        tile.expertPvpEnabled = true

        assertThat(tile.queryAdd(INDEX_WHEREEVER, walker, 1, CylinderFlags.FLAG_PATHFINDING))
            .isEqualTo(ReturnValue.NOERROR)

        wall.setCustomAttribute("expertPvpOwnerMode", PvpMode_t.PVP_MODE_RED_FIST.value.toLong())
        assertThat(tile.queryAdd(INDEX_WHEREEVER, walker, 1, CylinderFlags.FLAG_PATHFINDING))
            .isEqualTo(ReturnValue.NOTENOUGHROOM)
    }

    @Test
    fun `queryAdd removes a safe magic wall and returns noerror`() {
        val tile = tileWithGround()
        val safeWall = MagicField(ExpertPvp.ITEM_MAGICWALL_SAFE, type = types.get(ExpertPvp.ITEM_MAGICWALL_SAFE))
        tile.internalAddThing(safeWall)
        val removed = mutableListOf<Pair<Item, Int>>()
        tile.internalRemoveItem = { findfield, count -> removed.add(findfield to count) }

        assertThat(tile.queryAdd(INDEX_WHEREEVER, player(1), 1, 0)).isEqualTo(ReturnValue.NOERROR)
        assertThat(removed).containsExactly(safeWall to 1)

        val ghost = player(2, "Ghost").apply { switchGhostMode() }
        assertThat(tile.queryAdd(INDEX_WHEREEVER, ghost, 1, 0)).isEqualTo(ReturnValue.NOERROR)
        assertThat(removed).hasSize(1)
    }
}
