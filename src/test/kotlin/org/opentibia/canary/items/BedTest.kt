package org.opentibia.canary.items

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.movement.Direction
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.house.House
import org.opentibia.canary.map.house.Houses
import org.opentibia.canary.map.house.SUBOWNER_LIST

/**
 * Transcript tests for the `BedItem` lifecycle (src/items/bed.cpp:26-312):
 * `forceClearSleeper`, `getNextBedItem`, `canUse`, `trySleep`, `sleep`,
 * `wakeUp`, `regeneratePlayer` and `updateAppearance`. The C++
 * `g_game()`/`g_configManager()`/`g_saveManager()`/`IOLoginData` calls are the
 * [BedWorld] seam (AGENTS.md 1.1), while the deferred walk/logout callback is
 * re-resolved and identity-checked against the captured bed/player.
 */
class BedTest {

    private val bedPosition = Position(100, 100, 7)
    private val partnerPosition = Position(101, 100, 7)

    @Test
    fun `forceClearSleeper notifies the world and clears the sleeper state`() {
        val world = RecordingBedWorld()
        val bed = BedItem(1750, type = pillowType()).apply {
            setHouse(world.house)
            sleeperGUID = 42
            sleepStart = 123
        }
        world.positions[bed] = bedPosition

        bed.forceClearSleeper(world)

        assertThat(world.removedSleepers).containsExactly(42L)
        assertThat(bed.sleeperGUID).isEqualTo(0L)
        assertThat(bed.sleepStart).isEqualTo(0L)
        assertThat(bed.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo("Nobody is sleeping there.")
    }

    @Test
    fun `forceClearSleeper transforms the bed back to its free appearance`() {
        val world = RecordingBedWorld()
        val type = pillowType().copy(transformToFree = 30)
        world.itemTypes[30] = ItemType(id = 30, name = "free bed", isBed = true)
        val bed = BedItem(1750, type = type).apply { setHouse(world.house) }
        world.positions[bed] = bedPosition

        bed.forceClearSleeper(world)

        assertThat(world.transforms).containsExactly(bed to 30)
    }

    @Test
    fun `trySleep rejects a player without a house`() {
        val world = RecordingBedWorld()
        val bed = BedItem(1750, type = pillowType())
        val player = player(id = 1, name = "Alice")

        assertThat(bed.trySleep(player, world)).isFalse()
    }

    @Test
    fun `trySleep rejects a removed player`() {
        val world = RecordingBedWorld()
        val bed = BedItem(1750, type = pillowType()).apply { setHouse(world.house) }
        val player = player(id = 1, name = "Alice").apply { setRemoved() }

        assertThat(bed.trySleep(player, world)).isFalse()
    }

    @Test
    fun `trySleep accepts an empty bed`() {
        val world = RecordingBedWorld()
        val bed = BedItem(1750, type = pillowType()).apply { setHouse(world.house) }
        val player = player(id = 1, name = "Alice")

        assertThat(bed.trySleep(player, world)).isTrue()
    }

    @Test
    fun `trySleep wakes the sleeping house owner and emits the poff effect`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        world.house.owner = player.getGUID()
        world.house.setAccessList(SUBOWNER_LIST, "Alice")
        world.playersById[1] = player
        val bed = BedItem(1750, type = pillowType().copy(transformToFree = 30)).apply {
            setHouse(world.house)
            sleeperGUID = player.getGUID()
            sleepStart = world.time - 30
        }
        world.itemTypes[30] = ItemType(id = 30, name = "free bed", isBed = true)
        world.positions[bed] = bedPosition

        assertThat(bed.trySleep(player, world)).isFalse()

        assertThat(bed.sleeperGUID).isEqualTo(0L)
        assertThat(world.effects).containsExactly(player.position to CONST_ME_POFF)
    }

    @Test
    fun `trySleep rejects an occupied bed with the poff effect`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val bed = BedItem(1750, type = pillowType()).apply {
            setHouse(world.house)
            sleeperGUID = 99
        }
        world.positions[bed] = bedPosition

        assertThat(bed.trySleep(player, world)).isFalse()

        assertThat(world.effects).containsExactly(player.position to CONST_ME_POFF)
        assertThat(bed.sleeperGUID).isEqualTo(99L)
    }

    @Test
    fun `sleep marks both bed parts and the sleeper registry`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val (bed, partner) = completeBedPair(world, transformToOnUse = listOf(20, 21))
        world.playersById[player.id] = player

        assertThat(bed.sleep(player, world)).isTrue()

        assertThat(bed.sleeperGUID).isEqualTo(player.getGUID())
        assertThat(partner.sleeperGUID).isEqualTo(player.getGUID())
        assertThat(bed.sleepStart).isEqualTo(world.time)
        assertThat(bed.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo("Alice is sleeping there.")
        assertThat(world.bedSleepers[player.getGUID()]).isSameAs(bed)
        assertThat(world.effects).containsExactly(player.position to CONST_ME_SLEEP)
        assertThat(world.transforms).containsExactly(bed to 20, partner to 20)
    }

    @Test
    fun `sleep rejects an occupied bed`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val (bed, _) = completeBedPair(world)
        bed.sleeperGUID = 99

        assertThat(bed.sleep(player, world)).isFalse()
        assertThat(bed.sleeperGUID).isEqualTo(99L)
    }

    @Test
    fun `sleep rejects when the walk event is refused`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val (bed, _) = completeBedPair(world)
        world.walkEventAccepted = false

        assertThat(bed.sleep(player, world)).isFalse()

        assertThat(bed.sleeperGUID).isEqualTo(0L)
        assertThat(world.bedSleepers).isEmpty()
    }

    @Test
    fun `sleep walk event moves the player and schedules the logout`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val (bed, _) = completeBedPair(world)
        world.playersById[player.id] = player

        assertThat(bed.sleep(player, world)).isTrue()
        world.walkEvents.single().second()

        assertThat(world.moves).containsExactly(player to bed)
        assertThat(world.logoutRequests).containsExactly(player.getGUID())
        assertThat(world.logouts).isEmpty()
    }

    @Test
    fun `sleep walk event logs out immediately when the schedule is refused`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val (bed, _) = completeBedPair(world)
        world.playersById[player.id] = player
        world.logoutScheduled = false

        assertThat(bed.sleep(player, world)).isTrue()
        world.walkEvents.single().second()

        assertThat(world.logouts).containsExactly(player.getGUID())
    }

    @Test
    fun `sleep walk event is inert when the captured player was destroyed`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val (bed, _) = completeBedPair(world)
        world.playersById[player.id] = player

        assertThat(bed.sleep(player, world)).isTrue()
        world.playersById.clear()

        world.walkEvents.single().second()

        assertThat(world.moves).isEmpty()
        assertThat(world.logouts).isEmpty()
    }

    @Test
    fun `sleep walk event is inert when the captured player identity was reused`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val (bed, _) = completeBedPair(world)
        world.playersById[player.id] = player

        assertThat(bed.sleep(player, world)).isTrue()
        world.playersById[player.id] = player(id = 1, name = "Impostor")

        world.walkEvents.single().second()

        assertThat(world.moves).isEmpty()
        assertThat(world.logouts).isEmpty()
    }

    @Test
    fun `wakeUp regenerates the online player from the remaining ticks`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        player.addCondition(regenerationCondition(ticks = 300_000))
        val bed = sleepingBed(world, player, sleptSeconds = 60)

        bed.wakeUp(player, world)

        assertThat(player.health).isEqualTo(52)
        assertThat(world.manaChanges).containsExactly(player to 2)
        assertThat(world.soulChanges).containsExactly(player to 0)
        assertThat(player.getCondition(ConditionType.CONDITION_REGENERATION, ConditionId.CONDITIONID_DEFAULT)?.ticks)
            .isEqualTo(240_000)
        assertThat(world.healthBroadcasts).containsExactly(player)
        assertThat(world.removedSleepers).containsExactly(player.getGUID())
        assertThat(bed.sleeperGUID).isEqualTo(0L)
    }

    @Test
    fun `wakeUp removes the exhausted regeneration condition`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        player.addCondition(regenerationCondition(ticks = 60_000))
        val bed = sleepingBed(world, player, sleptSeconds = 60)

        bed.wakeUp(player, world)

        assertThat(player.getCondition(ConditionType.CONDITION_REGENERATION, ConditionId.CONDITIONID_DEFAULT)).isNull()
        assertThat(player.health).isEqualTo(52)
    }

    @Test
    fun `wakeUp regenerates an immortal condition from the slept time only`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        player.addCondition(regenerationCondition(ticks = -1))
        val bed = sleepingBed(world, player, sleptSeconds = 900)

        bed.wakeUp(player, world)

        assertThat(player.health).isEqualTo(80)
        assertThat(world.manaChanges).containsExactly(player to 30)
        assertThat(world.soulChanges).containsExactly(player to 1)
    }

    @Test
    fun `wakeUp loads and saves the offline sleeper`() {
        val world = RecordingBedWorld()
        val sleeper = player(id = 42, name = "Bob")
        sleeper.addCondition(regenerationCondition(ticks = 600_000))
        world.loadedPlayers[42L] = sleeper
        val bed = BedItem(1750, type = pillowType()).apply {
            setHouse(world.house)
            sleeperGUID = 42
            sleepStart = world.time - 60
        }
        world.positions[bed] = bedPosition

        bed.wakeUp(null, world)

        assertThat(sleeper.health).isEqualTo(52)
        assertThat(world.savedPlayers).containsExactly(sleeper)
        assertThat(world.removedSleepers).containsExactly(42L)
        assertThat(bed.sleeperGUID).isEqualTo(0L)
    }

    @Test
    fun `wakeUp clears both bed parts and restores the free appearance`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val (bed, partner) = completeBedPair(world, transformToFree = 30)
        world.playersById[player.id] = player
        bed.sleeperGUID = player.getGUID()
        partner.sleeperGUID = player.getGUID()

        bed.wakeUp(player, world)

        assertThat(bed.sleeperGUID).isEqualTo(0L)
        assertThat(partner.sleeperGUID).isEqualTo(0L)
        assertThat(bed.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo("Nobody is sleeping there.")
        assertThat(world.transforms).containsExactly(bed to 30, partner to 30)
    }

    @Test
    fun `wakeUp without a house does nothing`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val bed = BedItem(1750, type = pillowType()).apply { sleeperGUID = player.getGUID() }

        bed.wakeUp(player, world)

        assertThat(bed.sleeperGUID).isEqualTo(player.getGUID())
        assertThat(world.removedSleepers).isEmpty()
    }

    @Test
    fun `wakeUp on an empty bed does nothing`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice")
        val bed = BedItem(1750, type = pillowType()).apply { setHouse(world.house) }

        bed.wakeUp(player, world)

        assertThat(world.removedSleepers).isEmpty()
        assertThat(player.health).isEqualTo(50)
    }

    @Test
    fun `canUse requires premium`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice", premium = false)
        val (bed, _) = completeBedPair(world)

        assertThat(bed.canUse(player, world)).isFalse()
    }

    @Test
    fun `canUse requires a house`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice", premium = true)
        val bed = BedItem(1750, type = pillowType())

        assertThat(bed.canUse(player, world)).isFalse()
    }

    @Test
    fun `canUse requires the pillow part`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice", premium = true)
        val (bed, _) = completeBedPair(world)

        val blanket = BedItem(
            1751,
            type = pillowType(id = 1751).copy(bedPart = BedItemPart.BED_BLANKET_PART, bedPartOf = 1750),
        ).apply { setHouse(world.house) }

        assertThat(blanket.canUse(player, world)).isFalse()
        assertThat(bed.canUse(player, world)).isTrue()
    }

    @Test
    fun `canUse requires a complete movable bed`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice", premium = true)
        val (bed, partner) = completeBedPair(world)

        assertThat(bed.canUse(player, world)).isTrue()
        assertThat(partner.isBedComplete(bed)).isTrue()
    }

    @Test
    fun `canUse rejects a missing partner`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice", premium = true)
        val bed = BedItem(1750, type = pillowType()).apply { setHouse(world.house) }
        world.positions[bed] = bedPosition

        assertThat(bed.canUse(player, world)).isFalse()
    }

    @Test
    fun `canUse grants the house owner access to an occupied bed`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Alice", premium = true)
        world.house.owner = player.getGUID()
        val (bed, _) = completeBedPair(world)
        bed.sleeperGUID = 99

        assertThat(bed.canUse(player, world)).isTrue()
    }

    @Test
    fun `canUse rejects a player with a lower access level than the sleeper`() {
        val world = RecordingBedWorld()
        val player = player(id = 1, name = "Guest", premium = true)
        val sleeper = player(id = 99, name = "Owner")
        world.house.setAccessList(SUBOWNER_LIST, "Owner")
        world.loadedPlayers[99L] = sleeper
        val (bed, _) = completeBedPair(world)
        bed.sleeperGUID = 99

        assertThat(bed.canUse(player, world)).isFalse()
    }

    @Test
    fun `getNextBedItem follows the bed partner direction`() {
        val world = RecordingBedWorld()
        val bed = BedItem(1750, type = pillowType().copy(bedPartnerDir = Direction.East))
        val partner = BedItem(1751, type = pillowType(id = 1751))
        world.positions[bed] = bedPosition
        world.bedsByPosition[partnerPosition] = partner

        assertThat(bed.getNextBedItem(world)).isSameAs(partner)
    }

    @Test
    fun `getNextBedItem without a partner direction looks at the own tile`() {
        val world = RecordingBedWorld()
        val bed = BedItem(1750, type = pillowType().copy(bedPartnerDir = null))
        world.positions[bed] = bedPosition
        world.bedsByPosition[bedPosition] = bed

        assertThat(bed.getNextBedItem(world)).isSameAs(bed)
    }

    @Test
    fun `getNextBedItem without a position returns null`() {
        val world = RecordingBedWorld()

        assertThat(BedItem(1750, type = pillowType()).getNextBedItem(world)).isNull()
    }

    @Test
    fun `isBedComplete requires the matching partner id`() {
        val type = pillowType(bedPartOf = 1751)
        val bed = BedItem(1750, type = type)

        assertThat(bed.isBedComplete(null)).isFalse()
        assertThat(bed.isBedComplete(BedItem(1751))).isTrue()
        assertThat(bed.isBedComplete(BedItem(1752))).isFalse()
    }

    private fun pillowType(id: Int = 1750, bedPartOf: Int = 1751): ItemType = ItemType(
        id = id,
        name = "bed",
        isBed = true,
        isMovable = true,
        bedPartOf = bedPartOf,
        bedPart = BedItemPart.BED_PILLOW_PART,
        bedPartnerDir = Direction.East,
    )

    private fun player(id: Long, name: String, premium: Boolean = false): Player = Player(
        id = id,
        name = name,
        maxHealth = 100,
        health = 50,
        maxMana = 100,
        mana = 0,
        position = Position(100, 100, 7),
    ).apply { this.premium = premium }

    private fun regenerationCondition(ticks: Int): Condition =
        requireNotNull(
            Condition.createCondition(
                ConditionId.CONDITIONID_DEFAULT,
                ConditionType.CONDITION_REGENERATION,
                ticks = ticks,
                now = { 0L },
            ),
        )

    private fun completeBedPair(
        world: RecordingBedWorld,
        transformToOnUse: List<Int> = listOf(0, 0),
        transformToFree: Int = 0,
    ): Pair<BedItem, BedItem> {
        world.itemTypes[1750] = pillowType().copy(
            transformToOnUse = transformToOnUse,
            transformToFree = transformToFree,
        )
        world.itemTypes[1751] = pillowType(id = 1751, bedPartOf = 1750).copy(
            bedPartnerDir = Direction.West,
            transformToOnUse = transformToOnUse,
            transformToFree = transformToFree,
        )
        for (lookType in transformToOnUse.filter { it != 0 } + transformToFree) {
            world.itemTypes[lookType] = ItemType(id = lookType, name = "bed look", isBed = true)
        }
        val bed = BedItem(1750, type = world.itemTypes[1750]).apply { setHouse(world.house) }
        val partner = BedItem(1751, type = world.itemTypes[1751]).apply { setHouse(world.house) }
        world.positions[bed] = bedPosition
        world.positions[partner] = partnerPosition
        world.bedsByPosition[partnerPosition] = partner
        world.bedsByPosition[bedPosition] = bed
        return bed to partner
    }

    private fun sleepingBed(world: RecordingBedWorld, player: Player, sleptSeconds: Long): BedItem {
        val bed = BedItem(1750, type = pillowType()).apply {
            setHouse(world.house)
            sleeperGUID = player.getGUID()
            sleepStart = world.time - sleptSeconds
        }
        world.positions[bed] = bedPosition
        return bed
    }

    private class RecordingBedWorld : BedWorld {
        val house: House = Houses().addHouse(1)
        val playersById = mutableMapOf<Long, Player>()
        val bedsByPosition = mutableMapOf<Position, BedItem>()
        val positions = mutableMapOf<BedItem, Position>()
        val itemTypes = mutableMapOf<Int, ItemType>()
        var time: Long = 1_000
        val loadedPlayers = mutableMapOf<Long, Player>()
        val savedPlayers = mutableListOf<Player>()
        val bedSleepers = mutableMapOf<Long, BedItem>()
        val removedSleepers = mutableListOf<Long>()
        val effects = mutableListOf<Pair<Position, Int>>()
        var walkEventAccepted = true
        val walkEvents = mutableListOf<Pair<Long, () -> Unit>>()
        var logoutScheduled = true
        val logoutRequests = mutableListOf<Long>()
        val logouts = mutableListOf<Long>()
        val moves = mutableListOf<Pair<Player, BedItem>>()
        val manaChanges = mutableListOf<Pair<Player, Int>>()
        val soulChanges = mutableListOf<Pair<Player, Int>>()
        val healthBroadcasts = mutableListOf<Player>()
        val transforms = mutableListOf<Pair<BedItem, Int>>()

        override var rateHealthRegen: Double = 1.0
        override var rateManaRegen: Double = 1.0

        override fun getPlayerById(playerId: Long): Player? = playersById[playerId]

        override fun getBedItem(position: Position): BedItem? = bedsByPosition[position]

        override fun getPosition(bed: BedItem): Position? = positions[bed]

        override fun getItemType(id: Int): ItemType? = itemTypes[id]

        override fun timeNow(): Long = time

        override fun loadPlayerById(guid: Long): Player? = loadedPlayers[guid]

        override fun savePlayer(player: Player) {
            savedPlayers.add(player)
        }

        override fun setBedSleeper(bed: BedItem, guid: Long) {
            bedSleepers[guid] = bed
        }

        override fun removeBedSleeper(guid: Long) {
            removedSleepers.add(guid)
        }

        override fun addMagicEffect(position: Position, effect: Int) {
            effects.add(position to effect)
        }

        override fun addWalkEvent(playerId: Long, event: () -> Unit): Boolean {
            if (!walkEventAccepted) {
                return false
            }
            walkEvents.add(playerId to event)
            return true
        }

        override fun scheduleLogout(playerId: Long): Boolean {
            if (!logoutScheduled) {
                return false
            }
            logoutRequests.add(playerId)
            return true
        }

        override fun logoutPlayer(playerId: Long) {
            logouts.add(playerId)
        }

        override fun moveCreature(player: Player, bed: BedItem) {
            moves.add(player to bed)
        }

        override fun changeMana(player: Player, amount: Int) {
            manaChanges.add(player to amount)
        }

        override fun changeSoul(player: Player, amount: Int) {
            soulChanges.add(player to amount)
        }

        override fun addCreatureHealth(player: Player) {
            healthBroadcasts.add(player)
        }

        override fun transformItem(bed: BedItem, itemId: Int) {
            transforms.add(bed to itemId)
        }
    }
}
