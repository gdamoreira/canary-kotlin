package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.containers.depot.DepotChest
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.systems.rewardchest.Reward
import org.opentibia.canary.systems.rewardchest.RewardChest

/**
 * Transcribes the Player container/reward UI helpers
 * (src/creatures/players/player.cpp:1329-1344, 9032-9054, 9186-9240,
 * 9586-9629, 12023-12060, 12491-12516, 12518-12541, 12578-12587):
 *
 * - `Player::closeAllExternalContainers` (player.cpp:9032-9054)
 * - `Player::closeContainersOutOfRange` (player.cpp:9186-9209)
 * - `Player::shouldCloseContainer` (player.cpp:9211-9240)
 * - `Player::getAllContainers` (player.cpp:9586-9629)
 * - `Player::openPlayerContainers` (player.cpp:12023-12060)
 * - `Player::onCloseContainer` (player.cpp:12491-12501)
 * - `Player::onSendContainer` (player.cpp:12503-12516)
 * - `Player::autoCloseContainers` (player.cpp:12518-12541)
 * - `Player::getFreeBackpackSlots` (player.cpp:12578-12587)
 * - `Player::removeEmptyRewards` (player.cpp:1329-1338)
 * - `Player::hasOtherRewardContainerOpen` (player.cpp:1340-1344)
 *
 * The open-container registry is already on the host (`Player::addContainer`,
 * `getOpenContainers`, `closeContainer`, ...), so the methods are extension
 * functions over it. `g_game().map.getTile` depot/tile state, the
 * `Container::getHoldingPlayer` owning player, `Player::checkLootContainers`
 * and every `client->send*` call are call-site seams (AGENTS.md 1.1).
 */
class PlayerContainerUiStateTest {

    private val sentCloseContainers = mutableListOf<Int>()
    private val sentContainers = mutableListOf<List<Any?>>()
    private val lootChecks = mutableListOf<Container?>()

    private fun newPlayer(): Player =
        Player(id = 11, name = "Pack Mule", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    private fun container(id: Int, capacity: Int = 10): Container = Container(id, capacity)

    private fun onTile(item: Item, position: Position): Tile {
        val tile = Tile(position)
        tile.addThing(item)
        item.setParent(tile)
        return tile
    }

    private val send: (Int, Container, Boolean, Int) -> Unit = { cid, container, hasParent, index ->
        sentContainers.add(listOf(cid, container, hasParent, index))
    }

    // --- Player::shouldCloseContainer (player.cpp:9211-9240) ---------------------

    @Test
    fun `shouldCloseContainer closes removed and unparented containers`() {
        val player = newPlayer()

        assertThat(player.shouldCloseContainer(null)).isTrue()
        assertThat(player.shouldCloseContainer(container(1))).isTrue()
    }

    @Test
    fun `shouldCloseContainer keeps a container on a near tile and closes a far one`() {
        val player = newPlayer()
        val near = container(1).also { onTile(it, Position(101, 100, 7)) }
        val far = container(2).also { onTile(it, Position(140, 100, 7)) }

        assertThat(player.shouldCloseContainer(near)).isFalse()
        assertThat(player.shouldCloseContainer(far)).isTrue()
    }

    @Test
    fun `shouldCloseContainer treats a same-different-z tile as out of range`() {
        val player = newPlayer()
        val otherFloor = container(1).also { onTile(it, Position(100, 100, 6)) }

        assertThat(player.shouldCloseContainer(otherFloor)).isTrue()
    }

    @Test
    fun `shouldCloseContainer asks the depot box proximity for a depot chest`() {
        val player = newPlayer()
        val depotChest = DepotChest(ITEM_DEPOT).also { onTile(it, Position(140, 100, 7)) }
        val depotChests = { listOf<Container>(depotChest) }

        val farFromBox = player.shouldCloseContainer(depotChest, depotChests = depotChests, isNearDepotBox = { false })
        val nearBox = player.shouldCloseContainer(depotChest, depotChests = depotChests, isNearDepotBox = { true })

        assertThat(farFromBox).isTrue()
        assertThat(nearBox).isFalse()
    }

    // --- Player::closeContainersOutOfRange (player.cpp:9186-9209) ----------------

    @Test
    fun `closeContainersOutOfRange closes only out of range containers`() {
        val player = newPlayer()
        val near = container(1).also { onTile(it, Position(101, 100, 7)) }
        val far = container(2).also { onTile(it, Position(140, 100, 7)) }
        player.addContainer(0, near)
        player.addContainer(1, far)

        player.closeContainersOutOfRange(
            hasClient = { true },
            checkLootContainers = { lootChecks.add(it) },
            sendCloseContainer = { sentCloseContainers.add(it) },
        )

        assertThat(player.getContainerByID(0)).isSameAs(near)
        assertThat(player.getContainerByID(1)).isNull()
        assertThat(sentCloseContainers).containsExactly(1)
        assertThat(lootChecks).containsExactly(far)
    }

    @Test
    fun `closeContainersOutOfRange is inert without a client`() {
        val player = newPlayer()
        val far = container(2).also { onTile(it, Position(140, 100, 7)) }
        player.addContainer(0, far)

        player.closeContainersOutOfRange(hasClient = { false }, sendCloseContainer = { sentCloseContainers.add(it) })

        assertThat(player.getContainerByID(0)).isSameAs(far)
        assertThat(sentCloseContainers).isEmpty()
    }

    // --- Player::closeAllExternalContainers (player.cpp:9032-9054) ---------------

    @Test
    fun `closeAllExternalContainers closes containers held by another player`() {
        val player = newPlayer()
        val other = newPlayer()
        val mine = container(1).also { onTile(it, Position(101, 100, 7)) }
        val theirs = container(2).also { onTile(it, Position(102, 100, 7)) }
        player.addContainer(0, mine)
        player.addContainer(1, theirs)

        player.closeAllExternalContainers(
            hasClient = { true },
            getHoldingPlayer = { container -> if (container === mine) player else other },
            sendCloseContainer = { sentCloseContainers.add(it) },
        )

        assertThat(player.getContainerByID(0)).isSameAs(mine)
        assertThat(player.getContainerByID(1)).isNull()
        assertThat(sentCloseContainers).containsExactly(1)
    }

    // --- Player::autoCloseContainers (player.cpp:12518-12541) --------------------

    @Test
    fun `autoCloseContainers closes the container and every nested open container`() {
        val player = newPlayer()
        val target = container(1).also { onTile(it, Position(101, 100, 7)) }
        val nested = container(2).also { it.setParent(target) }
        val independent = container(3).also { onTile(it, Position(102, 100, 7)) }
        player.addContainer(0, target)
        player.addContainer(1, nested)
        player.addContainer(2, independent)

        player.autoCloseContainers(target, hasClient = { true }, sendCloseContainer = { sentCloseContainers.add(it) })

        assertThat(player.getContainerByID(0)).isNull()
        assertThat(player.getContainerByID(1)).isNull()
        assertThat(player.getContainerByID(2)).isSameAs(independent)
        assertThat(sentCloseContainers).containsExactly(0, 1)
    }

    @Test
    fun `autoCloseContainers also drops containers already marked removed`() {
        val player = newPlayer()
        val target = container(1).also { onTile(it, Position(101, 100, 7)) }
        val removed = container(2)
        player.addContainer(0, target)
        player.addContainer(1, removed)

        player.autoCloseContainers(target)

        assertThat(player.getContainerByID(1)).isNull()
    }

    // --- Player::onCloseContainer / onSendContainer (player.cpp:12491-12516) -----

    @Test
    fun `onCloseContainer sends the id of the matching open container`() {
        val player = newPlayer()
        val first = container(1)
        val second = container(2)
        player.addContainer(3, first)
        player.addContainer(4, second)

        player.onCloseContainer(second, hasClient = { true }, sendCloseContainer = { sentCloseContainers.add(it) })

        assertThat(sentCloseContainers).containsExactly(4)
    }

    @Test
    fun `onSendContainer forwards parent flag and index for each matching id`() {
        val player = newPlayer()
        val parent = container(1)
        val child = container(2).also { it.setParent(parent) }
        player.addContainer(2, parent)
        player.addContainer(3, child)
        player.setContainerIndex(3, 5)

        player.onSendContainer(child, hasClient = { true }, sendContainer = send)

        assertThat(sentContainers).hasSize(1)
        assertThat(sentContainers.single()[0]).isEqualTo(3)
        assertThat(sentContainers.single()[1]).isSameAs(child)
        assertThat(sentContainers.single()[2]).isEqualTo(true)
        assertThat(sentContainers.single()[3]).isEqualTo(5)
    }

    // --- Player::getAllContainers (player.cpp:9586-9629) -------------------------

    @Test
    fun `getAllContainers walks inventory slots and nested containers breadth first`() {
        val player = newPlayer()
        val backpack = container(1, capacity = 20)
        val nested = container(2)
        backpack.addItem(nested)
        val leftHand = container(3)
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)
        player.addItem(leftHand, Slots_t.CONST_SLOT_LEFT)

        assertThat(player.getAllContainers()).containsExactly(backpack, leftHand, nested)
        assertThat(player.getAllContainers(onlyFromMainBackpack = true)).containsExactly(backpack, nested)
    }

    @Test
    fun `getAllContainers ignores plain items`() {
        val player = newPlayer()
        player.addItem(Item(100), Slots_t.CONST_SLOT_LEFT)

        assertThat(player.getAllContainers()).isEmpty()
        assertThat(player.getAllContainers(onlyFromMainBackpack = true)).isEmpty()
    }

    // --- Player::openPlayerContainers (player.cpp:12023-12060) -------------------

    @Test
    fun `openPlayerContainers restores the containers marked with the open attribute`() {
        val player = newPlayer()
        val backpack = container(1, capacity = 20)
        val nested = container(2)
        backpack.addItem(nested)
        nested.setAttribute(ItemAttribute.OPENCONTAINER, 5L)
        backpack.setAttribute(ItemAttribute.OPENCONTAINER, 4L)
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)

        player.openPlayerContainers(hasClient = { true }, sendContainer = send)

        assertThat(player.getContainerByID(3)).isSameAs(backpack)
        assertThat(player.getContainerByID(4)).isSameAs(nested)
        assertThat(sentContainers.map { it[0] }).containsExactly(3, 4)
    }

    @Test
    fun `openPlayerContainers keeps containers without the open attribute closed`() {
        val player = newPlayer()
        val unopened = container(1)
        player.addItem(unopened, Slots_t.CONST_SLOT_BACKPACK)

        player.openPlayerContainers()

        assertThat(player.getOpenContainers()).isEmpty()
    }

    // --- Player::getFreeBackpackSlots (player.cpp:12578-12587) -------------------

    @Test
    fun `getFreeBackpackSlots reports the backpack free slots and zero without backpack`() {
        val player = newPlayer()
        assertThat(player.getFreeBackpackSlots()).isZero()

        val backpack = container(1, capacity = 10)
        backpack.addItem(Item(100))
        backpack.addItem(Item(101))
        player.addItem(backpack, Slots_t.CONST_SLOT_BACKPACK)

        assertThat(player.getFreeBackpackSlots()).isEqualTo(backpack.getFreeSlots())
    }

    // --- Player::removeEmptyRewards / hasOtherRewardContainerOpen ---------------

    @Test
    fun `removeEmptyRewards drops empty reward proxies and keeps filled ones`() {
        val player = newPlayer()
        val chest = RewardChest()
        chest.getReward(1L, autoCreate = true)
        val filled = chest.getReward(2L, autoCreate = true)!!
        filled.addItem(Item(100))

        player.removeEmptyRewards(chest)

        assertThat(chest.getRewardList()).containsExactly(2L)
        assertThat(chest.items).containsExactly(filled)
    }

    @Test
    fun `hasOtherRewardContainerOpen only counts a different reward container`() {
        val player = newPlayer()
        val reward = container(RewardChest.ITEM_REWARD_CHEST)
        val otherReward = container(Reward.ITEM_REWARD_CONTAINER)
        player.addContainer(0, reward)

        assertThat(player.hasOtherRewardContainerOpen(reward)).isFalse()

        player.addContainer(1, otherReward)
        assertThat(player.hasOtherRewardContainerOpen(reward)).isTrue()
    }

    companion object {
        /** `ITEM_DEPOT` (src/utils/utils_definitions.hpp). */
        private const val ITEM_DEPOT = 2594
    }
}
