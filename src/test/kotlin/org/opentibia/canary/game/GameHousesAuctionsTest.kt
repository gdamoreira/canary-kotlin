package org.opentibia.canary.game

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.Account
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.GuidNameEx
import org.opentibia.canary.database.PlayerLoginRepository
import org.opentibia.canary.database.PlayerRecord
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.BedItem
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.items.ItemTypes_t
import org.opentibia.canary.items.Items
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Town
import org.opentibia.canary.map.house.CyclopediaHouseState
import org.opentibia.canary.map.house.FakeHouseWorld
import org.opentibia.canary.map.house.GUEST_LIST
import org.opentibia.canary.map.house.House
import org.opentibia.canary.map.house.Houses
import org.opentibia.canary.map.house.SUBOWNER_LIST
import org.opentibia.canary.network.protocol.AcceptTransferErrorMessage
import org.opentibia.canary.network.protocol.BidErrorMessage
import org.opentibia.canary.network.protocol.BidSuccessMessage
import org.opentibia.canary.network.protocol.HouseAuctionType
import org.opentibia.canary.network.protocol.StoreWindowWriter
import org.opentibia.canary.network.protocol.TransferErrorMessage

/**
 * Transcript tests for the `Game` house-auction family:
 * `Game::playerCyclopediaHousesByTown` (game.cpp:12762),
 * `Game::playerCyclopediaHouseBid` (game.cpp:12802),
 * `Game::playerCyclopediaHouseMoveOut` (game.cpp:12867),
 * `Game::playerCyclopediaHouseCancelMoveOut` (game.cpp:12896),
 * `Game::playerCyclopediaHouseTransfer` (game.cpp:12925),
 * `Game::playerCyclopediaHouseCancelTransfer` (game.cpp:12964),
 * `Game::playerCyclopediaHouseAcceptTransfer` (game.cpp:13008),
 * `Game::playerCyclopediaHouseRejectTransfer` (game.cpp:13042),
 * `Game::processBankAuction` (game.cpp:13081),
 * `Game::playerUpdateHouseWindow` (game.cpp:5780),
 * `Game::wrapItem`/`Game::unwrapItem` (game.cpp:5472/5501),
 * `Game::setTransferPlayerHouseItems`/`Game::transferHouseItemsToDepot`
 * (game.cpp:12363/12399) and `Game::getPlayerByGUID` (game.cpp:1419).
 */
class GameHousesAuctionsTest {

    private data class AuctionMessage(
        val houseId: Long,
        val type: HouseAuctionType,
        val index: Int,
        val bidSuccess: Boolean
    )

    private class StubLoginRepository : PlayerLoginRepository {
        val balanceIncreases = mutableListOf<Pair<Long, Long>>()
        var guidByName: (String) -> Long = { 0 }

        override fun loadRecordById(id: Long): PlayerRecord? = null
        override fun loadRecordByName(name: String): PlayerRecord? = null
        override fun getNameByGuid(guid: Long): String = ""
        override fun getGuidByName(name: String): Long = guidByName(name)
        override fun getGuidByNameEx(name: String): GuidNameEx? = null
        override fun formatPlayerName(name: String): String? = null
        override fun increaseBankBalance(guid: Long, amount: Long): Boolean {
            balanceIncreases.add(guid to amount)
            return true
        }

        override fun savePlayerRecord(player: Player): Boolean = false
    }

    private val center = Position(1000, 1000, 7)
    private val itemRegistry = Items()
    private val world = FakeHouseWorld()
    private val loginRepository = StubLoginRepository()
    private val houses = Houses(
        ownerInfoLookup = { guid -> org.opentibia.canary.map.house.HouseOwnerInfo("Player $guid", guid * 10) },
        world = world
    )
    private val bankBalances = mutableMapOf<Long, Long>()
    private val auctionMessages = mutableListOf<AuctionMessage>()
    private val houseLists = mutableListOf<Pair<Player, Map<Int, House>>>()
    private val resourceBalances = mutableListOf<Pair<Player, Pair<StoreWindowWriter.Resource, Long>>>()
    private val cancelTexts = mutableListOf<Pair<Player, String>>()
    private val wokenBeds = mutableListOf<BedItem>()
    private val decayedItems = mutableListOf<Item>()
    private val loadedPlayers = mutableMapOf<Long, Player>()

    private fun newGame(): Game {
        val game = Game(
            GameMap(houses = houses),
            GameDispatcher(),
            loginRepository = loginRepository,
            itemTypes = itemRegistry,
            loadPlayerById = { guid -> loadedPlayers[guid] }
        )
        game.getBankBalance = { player -> bankBalances[player.getGUID()] ?: 0L }
        game.setBankBalance = { player, value -> bankBalances[player.getGUID()] = value }
        game.sendResourceBalance = { player, resource, value -> resourceBalances.add(player to (resource to value)) }
        game.sendHouseAuctionMessage = { _, houseId, type, index, bidSuccess ->
            auctionMessages.add(AuctionMessage(houseId, type, index, bidSuccess))
        }
        game.sendCyclopediaHouseList = { player, houses -> houseLists.add(player to houses) }
        game.sendCancelMessageText = { player, text -> cancelTexts.add(player to text) }
        game.wakeUpBed = { bed -> wokenBeds.add(bed) }
        game.startDecaying = { item -> decayedItems.add(item) }
        return game
    }

    private fun player(id: Long, name: String, position: Position = center): Player =
        Player(id = id, name = name, maxHealth = 150, health = 150, position = position)

    private fun premiumPlayer(id: Long, name: String): Player =
        player(id, name).also {
            it.premium = true
            it.vocationId = 1
        }

    private fun newHouse(
        id: Int = 1,
        clientId: Int = 100,
        townId: Int = 1,
        owner: Long = 0,
        rent: Long = 100
    ): House = houses.addHouse(id).also {
        it.name = "Central Flat $id"
        it.owner = owner
        it.rent = rent
        it.townId = townId
        it.clientId = clientId
        houses.addHouseClientId(clientId, it)
    }

    private fun Game.addTown(id: Int, name: String) {
        map.towns.addTown(id, Town(id).apply { setName(name) })
    }

    // ----- playerCyclopediaHousesByTown -----

    @Test
    fun `playerCyclopediaHousesByTown filters the registry by town name`() {
        val game = newGame()
        game.addTown(1, "Thais")
        game.addTown(2, "Carlin")
        val player = player(10, "Knight")
        game.addPlayer(player)
        (newHouse(id = 1, clientId = 100, townId = 1))
        (newHouse(id = 2, clientId = 200, townId = 2))

        game.playerCyclopediaHousesByTown(10, "Thais")

        assertThat(houseLists).hasSize(1)
        assertThat(houseLists.single().first).isSameAs(player)
        assertThat(houseLists.single().second.keys).containsExactly(100)
    }

    @Test
    fun `playerCyclopediaHousesByTown with an empty name sends owned and bidder houses`() {
        val game = newGame()
        val player = player(10, "Knight")
        game.addPlayer(player)
        (newHouse(id = 1, clientId = 100, owner = 10))
        (newHouse(id = 2, clientId = 200).apply { bidderName = "Knight" })

        game.playerCyclopediaHousesByTown(10, "")

        assertThat(houseLists.single().second.keys).containsExactly(100, 200)
    }

    // ----- playerCyclopediaHouseBid -----

    @Test
    fun `playerCyclopediaHouseBid is gated by the auction config`() {
        val game = newGame()
        game.cyclopediaHouseAuction = false
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        newHouse()

        game.playerCyclopediaHouseBid(10, 100, 500)

        assertThat(auctionMessages).isEmpty()
        assertThat(bankBalances).isEmpty()
    }

    @Test
    fun `playerCyclopediaHouseBid stores the first bid and pays rent plus bid`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        bankBalances[10] = 1000
        val house = newHouse()
        game.addTown(1, "Thais")
        world.now = 1_700_000_000L
        world.serverSaveTime = "06:00"

        game.playerCyclopediaHouseBid(10, 100, 500)

        assertThat(bankBalances[10]).isEqualTo(400)
        assertThat(house.highestBid).isZero()
        assertThat(house.internalBid).isEqualTo(500)
        assertThat(house.bidHolderLimit).isEqualTo(500)
        assertThat(house.bidderName).isEqualTo("Knight")
        assertThat(house.bidder).isEqualTo(10)
        assertThat(house.bidEndDate).isEqualTo(1_700_546_400L)
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.Bid, BidSuccessMessage.BidSuccess.value, true)
        )
        assertThat(resourceBalances).containsExactly(player to (StoreWindowWriter.Resource.BANK to 400L))
        assertThat(houseLists).hasSize(1)
    }

    @Test
    fun `playerCyclopediaHouseBid reports NotEnoughMoney twice when the bank is short`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        bankBalances[10] = 50
        val house = newHouse()

        game.playerCyclopediaHouseBid(10, 100, 500)

        assertThat(bankBalances[10]).isEqualTo(50)
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.Bid, BidErrorMessage.NotEnoughMoney.value, false),
            AuctionMessage(100, HouseAuctionType.Bid, BidErrorMessage.NotEnoughMoney.value, false)
        )
        assertThat(house.internalBid).isZero()
    }

    @Test
    fun `playerCyclopediaHouseBid from the current bidder debits only the difference`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        bankBalances[10] = 1000
        val house =         newHouse().apply {
            bidderName = "Knight"
            bidder = 10
            internalBid = 500
            bidHolderLimit = 500
        }
        game.addTown(1, "Thais")

        game.playerCyclopediaHouseBid(10, 100, 700)

        assertThat(bankBalances[10]).isEqualTo(800)
        assertThat(house.internalBid).isEqualTo(700)
        assertThat(house.bidHolderLimit).isEqualTo(700)
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.Bid, BidSuccessMessage.BidSuccess.value, true)
        )
    }

    @Test
    fun `playerCyclopediaHouseBid below the internal bid records the lower bid without moving money`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        bankBalances[10] = 1000
        val house =         newHouse().apply {
            bidderName = "Other"
            internalBid = 500
        }
        game.addTown(1, "Thais")

        game.playerCyclopediaHouseBid(10, 100, 400)

        assertThat(bankBalances[10]).isEqualTo(1000)
        assertThat(house.highestBid).isEqualTo(400)
        assertThat(house.internalBid).isEqualTo(500)
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.Bid, BidSuccessMessage.LowerBid.value, true)
        )
    }

    @Test
    fun `playerCyclopediaHouseBid outbids the current bidder and refunds him`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        val previous = player(99, "Other")
        game.addPlayer(player)
        game.addPlayer(previous)
        bankBalances[10] = 2000
        bankBalances[99] = 100
        val house =         newHouse().apply {
            bidderName = "Other"
            bidder = 99
            internalBid = 500
            bidHolderLimit = 500
        }
        game.addTown(1, "Thais")

        game.playerCyclopediaHouseBid(10, 100, 800)

        assertThat(bankBalances[10]).isEqualTo(1100)
        assertThat(bankBalances[99]).isEqualTo(700)
        assertThat(house.highestBid).isEqualTo(501)
        assertThat(house.internalBid).isEqualTo(800)
        assertThat(house.bidderName).isEqualTo("Knight")
        assertThat(house.bidder).isEqualTo(10)
    }

    // ----- processBankAuction -----

    @Test
    fun `processBankAuction rejects a non-replace bid without rent headroom`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        bankBalances[10] = 500
        val house = newHouse(rent = 100)

        val result = game.processBankAuction(player, house, 500)

        assertThat(result).isFalse()
        assertThat(bankBalances[10]).isEqualTo(500)
    }

    @Test
    fun `processBankAuction rejects a replace bid above the balance`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        bankBalances[10] = 400
        val house = newHouse(rent = 100).apply { internalBid = 500 }

        val result = game.processBankAuction(player, house, 700, replace = true)

        assertThat(result).isFalse()
        assertThat(bankBalances[10]).isEqualTo(400)
    }

    @Test
    fun `processBankAuction refunds an offline previous bidder through the login repository`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        bankBalances[10] = 2000
        loginRepository.guidByName = { name -> if (name == "Offline") 77L else 0L }
        val house = newHouse().apply {
            bidderName = "Offline"
            bidder = 77
            bidHolderLimit = 500
        }

        val result = game.processBankAuction(player, house, 800)

        assertThat(result).isTrue()
        assertThat(bankBalances[10]).isEqualTo(1100)
        assertThat(loginRepository.balanceIncreases).containsExactly(77L to 600L)
    }

    @Test
    fun `processBankAuction refunds the empty bidder slot through the repository`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        bankBalances[10] = 2000
        val house = newHouse()

        val result = game.processBankAuction(player, house, 500)

        assertThat(result).isTrue()
        assertThat(loginRepository.balanceIncreases).containsExactly(0L to 100L)
    }

    // ----- move out / cancel move out -----

    @Test
    fun `playerCyclopediaHouseMoveOut requires the rented state and the owner`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        val house = (newHouse(owner = 10).apply { state = CyclopediaHouseState.Rented })

        game.playerCyclopediaHouseMoveOut(10, 100, 1_700_000_000L)

        assertThat(house.state).isEqualTo(CyclopediaHouseState.MoveOut)
        assertThat(house.bidEndDate).isEqualTo(1_700_000_000L)
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.MoveOut, TransferErrorMessage.Success.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseMoveOut rejects a non-owner`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        val house = (newHouse(owner = 99).apply { state = CyclopediaHouseState.Rented })

        game.playerCyclopediaHouseMoveOut(10, 100, 1_700_000_000L)

        assertThat(house.state).isEqualTo(CyclopediaHouseState.Rented)
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.MoveOut, TransferErrorMessage.NotHouseOwner.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseMoveOut rejects a house that is not rented`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        (newHouse(owner = 10))

        game.playerCyclopediaHouseMoveOut(10, 100, 1_700_000_000L)

        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.MoveOut, TransferErrorMessage.Internal.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseCancelMoveOut resets the move-out state`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        val house =         newHouse(owner = 10).apply {
            state = CyclopediaHouseState.MoveOut
            bidEndDate = 1_700_000_000L
        }

        game.playerCyclopediaHouseCancelMoveOut(10, 100)

        assertThat(house.state).isEqualTo(CyclopediaHouseState.Rented)
        assertThat(house.bidEndDate).isZero()
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.CancelMoveOut, TransferErrorMessage.Success.value, false)
        )
    }

    // ----- transfer -----

    @Test
    fun `playerCyclopediaHouseTransfer schedules the transfer for an eligible player`() {
        val game = newGame()
        val owner = premiumPlayer(10, "Knight")
        val newOwner = premiumPlayer(20, "Druid")
        game.addPlayer(owner)
        game.addPlayer(newOwner)
        val house = (newHouse(owner = 10).apply { state = CyclopediaHouseState.Rented })

        game.playerCyclopediaHouseTransfer(10, 100, 1_700_000_000L, "Druid", 500)

        assertThat(house.state).isEqualTo(CyclopediaHouseState.Transfer)
        assertThat(house.bidderName).isEqualTo("Druid")
        assertThat(house.bidder).isEqualTo(20)
        assertThat(house.internalBid).isEqualTo(500)
        assertThat(house.bidEndDate).isEqualTo(1_700_000_000L)
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.Transfer, TransferErrorMessage.Success.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseTransfer reports a missing character`() {
        val game = newGame()
        val owner = premiumPlayer(10, "Knight")
        game.addPlayer(owner)
        val house = (newHouse(owner = 10).apply { state = CyclopediaHouseState.Rented })

        game.playerCyclopediaHouseTransfer(10, 100, 1_700_000_000L, "Missing", 500)

        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.Transfer, TransferErrorMessage.CharacterNotExist.value, false)
        )
        assertThat(house.state).isEqualTo(CyclopediaHouseState.Rented)
    }

    @Test
    fun `playerCyclopediaHouseTransfer refuses a non-owner`() {
        val game = newGame()
        val owner = premiumPlayer(10, "Knight")
        val newOwner = premiumPlayer(20, "Druid")
        game.addPlayer(owner)
        game.addPlayer(newOwner)
        (newHouse(owner = 99).apply { state = CyclopediaHouseState.Rented })

        game.playerCyclopediaHouseTransfer(10, 100, 1_700_000_000L, "Druid", 500)

        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.Transfer, TransferErrorMessage.NotHouseOwner.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseTransfer refuses a player already bidding on another house`() {
        val game = newGame()
        val owner = premiumPlayer(10, "Knight")
        val newOwner = premiumPlayer(20, "Druid")
        newOwner.account = Account(id = 1).apply { houseBidId = 5 }
        game.addPlayer(owner)
        game.addPlayer(newOwner)
        (newHouse(owner = 10).apply { state = CyclopediaHouseState.Rented })

        game.playerCyclopediaHouseTransfer(10, 100, 1_700_000_000L, "Druid", 500)

        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.Transfer, TransferErrorMessage.OnlyOneBid.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseCancelTransfer refunds the accepted transfer and resets the state`() {
        val game = newGame()
        val owner = premiumPlayer(10, "Knight")
        val newOwner = premiumPlayer(20, "Druid")
        game.addPlayer(owner)
        game.addPlayer(newOwner)
        bankBalances[20] = 100
        val house =         newHouse(owner = 10).apply {
            state = CyclopediaHouseState.Transfer
            bidder = 20
            bidderName = "Druid"
            internalBid = 500
            transferStatus = true
        }

        game.playerCyclopediaHouseCancelTransfer(10, 100)

        assertThat(bankBalances[20]).isEqualTo(700)
        assertThat(house.state).isEqualTo(CyclopediaHouseState.Rented)
        assertThat(house.bidder).isZero()
        assertThat(house.bidderName).isEmpty()
        assertThat(house.internalBid).isZero()
        assertThat(house.transferStatus).isFalse()
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.CancelTransfer, TransferErrorMessage.Success.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseCancelTransfer refunds an offline new owner through the repository`() {
        val game = newGame()
        val owner = premiumPlayer(10, "Knight")
        game.addPlayer(owner)
        (
            newHouse(owner = 10).apply {
                state = CyclopediaHouseState.Transfer
                bidder = 20
                internalBid = 500
                transferStatus = true
            }
        )

        game.playerCyclopediaHouseCancelTransfer(10, 100)

        assertThat(loginRepository.balanceIncreases).containsExactly(20L to 600L)
    }

    @Test
    fun `playerCyclopediaHouseCancelTransfer requires the transfer state`() {
        val game = newGame()
        val owner = premiumPlayer(10, "Knight")
        game.addPlayer(owner)
        (newHouse(owner = 10).apply { state = CyclopediaHouseState.Rented })

        game.playerCyclopediaHouseCancelTransfer(10, 100)

        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.CancelTransfer, TransferErrorMessage.Internal.value, false)
        )
    }

    // ----- accept / reject transfer -----

    @Test
    fun `playerCyclopediaHouseAcceptTransfer charges the new owner and flags the transfer`() {
        val game = newGame()
        val newOwner = premiumPlayer(20, "Druid")
        game.addPlayer(newOwner)
        bankBalances[20] = 1000
        val house =         newHouse(rent = 100).apply {
            state = CyclopediaHouseState.Transfer
            bidder = 20
            bidderName = "Druid"
            internalBid = 500
        }

        game.playerCyclopediaHouseAcceptTransfer(20, 100)

        assertThat(bankBalances[20]).isEqualTo(400)
        assertThat(house.transferStatus).isTrue()
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.AcceptTransfer, AcceptTransferErrorMessage.Success.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseAcceptTransfer reports Frozen when the bank is short`() {
        val game = newGame()
        val newOwner = premiumPlayer(20, "Druid")
        game.addPlayer(newOwner)
        bankBalances[20] = 100
        val house =         newHouse(rent = 100).apply {
            state = CyclopediaHouseState.Transfer
            bidder = 20
            internalBid = 500
        }

        game.playerCyclopediaHouseAcceptTransfer(20, 100)

        assertThat(house.transferStatus).isFalse()
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.AcceptTransfer, AcceptTransferErrorMessage.Frozen.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseAcceptTransfer refuses a player that is not the bidder`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        (
            newHouse().apply {
                state = CyclopediaHouseState.Transfer
                bidder = 20
                internalBid = 500
            }
        )

        game.playerCyclopediaHouseAcceptTransfer(10, 100)

        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.AcceptTransfer, AcceptTransferErrorMessage.NotNewOwner.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseRejectTransfer requires the bidder and the transfer state`() {
        val game = newGame()
        val player = premiumPlayer(10, "Knight")
        game.addPlayer(player)
        (
            newHouse().apply {
                state = CyclopediaHouseState.Transfer
                bidder = 20
                internalBid = 500
            }
        )

        game.playerCyclopediaHouseRejectTransfer(10, 100)

        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.Transfer, TransferErrorMessage.NotHouseOwner.value, false)
        )
    }

    @Test
    fun `playerCyclopediaHouseRejectTransfer refunds and resets the transfer`() {
        val game = newGame()
        val newOwner = premiumPlayer(20, "Druid")
        game.addPlayer(newOwner)
        bankBalances[20] = 100
        val house =         newHouse(owner = 10).apply {
            state = CyclopediaHouseState.Transfer
            bidder = 20
            internalBid = 500
            transferStatus = true
        }

        game.playerCyclopediaHouseRejectTransfer(20, 100)

        assertThat(bankBalances[20]).isEqualTo(700)
        assertThat(house.state).isEqualTo(CyclopediaHouseState.Rented)
        assertThat(house.transferStatus).isFalse()
        assertThat(auctionMessages).containsExactly(
            AuctionMessage(100, HouseAuctionType.RejectTransfer, TransferErrorMessage.Success.value, false)
        )
    }

    // ----- playerUpdateHouseWindow -----

    @Test
    fun `playerUpdateHouseWindow writes the guest list and clears the edit house`() {
        val game = newGame()
        val player = player(10, "Knight")
        game.addPlayer(player)
        val house = newHouse(owner = 10)

        game.setEditHouse(player, house, GUEST_LIST)
        game.playerUpdateHouseWindow(10, 0, 1, "Knight")

        assertThat(house.getAccessList(GUEST_LIST)).isEqualTo("Knight\n")
        assertThat(game.getEditHouse(player)).isNull()
    }

    @Test
    fun `playerUpdateHouseWindow drops a mismatched window text id`() {
        val game = newGame()
        val player = player(10, "Knight")
        game.addPlayer(player)
        val house = newHouse(owner = 10)

        game.setEditHouse(player, house, GUEST_LIST)
        game.playerUpdateHouseWindow(10, 0, 99, "Knight")

        assertThat(house.getAccessList(GUEST_LIST)).isEmpty()
        assertThat(game.getEditHouse(player)).isNull()
    }

    @Test
    fun `playerUpdateHouseWindow refuses a list the player cannot edit`() {
        val game = newGame()
        val subOwner = player(20, "Sub Owner")
        game.addPlayer(subOwner)
        val house = newHouse(owner = 10)
        house.setAccessList(SUBOWNER_LIST, "Sub Owner")

        game.setEditHouse(subOwner, house, SUBOWNER_LIST)
        game.playerUpdateHouseWindow(20, 0, 1, "Knight")

        assertThat(house.getAccessList(SUBOWNER_LIST)).isEqualTo("Sub Owner\n")
        assertThat(game.getEditHouse(subOwner)).isNull()
    }

    // ----- wrapItem / unwrapItem -----

    private fun item(id: Int, type: ItemType? = null, count: Int = 1): Item = Item(id = id, count = count, type = type)

    private val kitType = ItemType(
        id = 23398,
        name = "decoration kit",
        isContainer = true,
        type = ItemTypes_t.ITEM_TYPE_CONTAINER
    )

    private fun kitContainer(): Container = Container(23398, capacity = 8)

    @Test
    fun `wrapItem stores the original id, description, store attribute and amount`() {
        val game = newGame()
        itemRegistry.register(kitType)
        val container = kitContainer()
        val source = item(2400, ItemType(id = 2400, name = "magic sword", isWrappable = true))
        source.setAttribute(ItemAttribute.STORE, 42L)
        container.addThing(source)

        val wrapped = game.wrapItem(source, null)

        assertThat(wrapped).isNotNull()
        assertThat(wrapped!!.id).isEqualTo(23398)
        assertThat(wrapped.getAttribute(ItemAttribute.STORE)).isEqualTo(42L)
        assertThat(wrapped.getAttribute(ItemAttribute.DESCRIPTION))
            .isEqualTo("You bought this item in the Store.\nUnwrap it in your own house to create a <magic sword>.")
        assertThat(wrapped.getAttribute(ItemAttribute.AMOUNT)).isEqualTo(1L)
        assertThat(decayedItems).contains(wrapped)
    }

    @Test
    fun `wrapItem moves a cask charge count into the date attribute`() {
        val game = newGame()
        itemRegistry.register(kitType)
        val container = kitContainer()
        val cask = item(25879, ItemType(id = 25879, name = "cask", charges = 7, isWrappable = true, type = ItemTypes_t.ITEM_TYPE_OTHER), count = 7)
        container.addThing(cask)

        val wrapped = game.wrapItem(cask, null)

        assertThat(wrapped!!.getAttribute(ItemAttribute.DATE)).isEqualTo(7L)
    }

    @Test
    fun `wrapItem wakes and removes a bed from the house`() {
        val game = newGame()
        itemRegistry.register(kitType)
        itemRegistry.register(ItemType(id = 1714, name = "bed", isBed = true, type = ItemTypes_t.ITEM_TYPE_BED))
        val container = kitContainer()
        val bed = BedItem(1714, type = ItemType(id = 1714, name = "bed", isBed = true, type = ItemTypes_t.ITEM_TYPE_BED))
        container.addThing(bed)
        val house = newHouse()
        house.addBed(bed)

        game.wrapItem(bed, house)

        assertThat(wokenBeds).containsExactly(bed)
        assertThat(house.getBeds()).isEmpty()
    }

    @Test
    fun `unwrapItem restores the original item and clears the description`() {
        val game = newGame()
        itemRegistry.register(kitType)
        itemRegistry.register(ItemType(id = 2400, name = "magic sword", type = ItemTypes_t.ITEM_TYPE_SWORD))
        val container = kitContainer()
        val kit = item(23398, kitType)
        kit.setAttribute(ItemAttribute.DESCRIPTION, "wrapped")
        container.addThing(kit)
        val player = player(10, "Knight")

        game.unwrapItem(kit, 2400, null, player)

        assertThat(container.getItemList()).hasSize(1)
        val unwrapped = container.getItemList().single()
        assertThat(unwrapped.id).isEqualTo(2400)
        assertThat(unwrapped.hasAttribute(ItemAttribute.DESCRIPTION)).isFalse()
        assertThat(decayedItems).contains(unwrapped)
    }

    @Test
    fun `unwrapItem refuses an item owned by somebody else`() {
        val game = newGame()
        itemRegistry.register(kitType)
        itemRegistry.register(ItemType(id = 2400, name = "magic sword", type = ItemTypes_t.ITEM_TYPE_SWORD))
        val container = kitContainer()
        val kit = item(23398, kitType)
        kit.setOwner(99)
        container.addThing(kit)
        val player = player(10, "Knight")

        game.unwrapItem(kit, 2400, null, player)

        assertThat(container.getItemList().single().id).isEqualTo(23398)
        assertThat(decayedItems).isEmpty()
    }

    @Test
    fun `unwrapItem refuses a bed above the house bed limit`() {
        val game = newGame()
        itemRegistry.register(kitType)
        itemRegistry.register(ItemType(id = 1714, name = "bed", isBed = true, type = ItemTypes_t.ITEM_TYPE_BED))
        val container = kitContainer()
        val kit = item(23398, kitType)
        container.addThing(kit)
        val house = newHouse()
        house.maxBeds = 1
        house.addBed(BedItem(1714, type = ItemType(id = 1714, name = "bed", isBed = true, type = ItemTypes_t.ITEM_TYPE_BED)))
        house.addBed(BedItem(1714, type = ItemType(id = 1714, name = "bed", isBed = true, type = ItemTypes_t.ITEM_TYPE_BED)))
        val player = player(10, "Knight")

        game.unwrapItem(kit, 1714, house, player)

        assertThat(container.getItemList().single().id).isEqualTo(23398)
        assertThat(cancelTexts).containsExactly(player to "You reached the maximum beds in this house")
    }

    @Test
    fun `unwrapItem restores a cask charge count and registers the bed`() {
        val game = newGame()
        itemRegistry.register(kitType)
        itemRegistry.register(ItemType(id = 25879, name = "cask", charges = 7, isWrappable = true, type = ItemTypes_t.ITEM_TYPE_OTHER))
        itemRegistry.register(ItemType(id = 1714, name = "bed", isBed = true, type = ItemTypes_t.ITEM_TYPE_BED))
        val container = kitContainer()
        val kit = item(23398, kitType)
        kit.setAttribute(ItemAttribute.DATE, 7L)
        container.addThing(kit)
        val player = player(10, "Knight")

        game.unwrapItem(kit, 25879, null, player)

        val unwrapped = container.getItemList().single()
        assertThat(unwrapped.id).isEqualTo(25879)
        assertThat(unwrapped.getSubType()).isEqualTo(7)

        val bedKit = item(23398, kitType)
        val bedContainer = kitContainer()
        bedContainer.addThing(bedKit)
        val house = newHouse()

        game.unwrapItem(bedKit, 1714, house, player)

        assertThat(house.getBeds()).hasSize(1)
    }

    // ----- transfer items to depot -----

    @Test
    fun `setTransferPlayerHouseItems records the owner and transferHouseItemsToDepot drains it`() {
        val game = newGame()
        game.toggleHouseTransferOnServerRestart = true
        val offline = player(20, "Druid")
        loadedPlayers[20] = offline
        val house = game.map.houses.addHouse(1)
        house.owner = 20
        house.townId = 1
        world.toggleHouseTransferOnServerRestart = true
        val savedHouses = mutableListOf<Int>()
        game.map.saveHouseInfo = { savedHouses.add(1); true }
        game.map.saveHouseItems = { true }

        game.setTransferPlayerHouseItems(1, 20)
        game.transferHouseItemsToDepot()

        assertThat(world.ownerRows).isEmpty()
        assertThat(world.newOwnerRows).containsEntry(1, -1)
        assertThat(savedHouses).containsExactly(1)
    }

    @Test
    fun `transferHouseItemsToDepot is gated by the restart transfer config`() {
        val game = newGame()
        game.toggleHouseTransferOnServerRestart = false
        game.map.houses.addHouse(1)

        game.setTransferPlayerHouseItems(1, 20)
        game.transferHouseItemsToDepot()

        assertThat(world.ownerRows).isEmpty()
    }

    @Test
    fun `transferHouseItemsToDepot skips a player that cannot be loaded`() {
        val game = newGame()
        game.toggleHouseTransferOnServerRestart = true
        game.map.houses.addHouse(1)

        game.setTransferPlayerHouseItems(1, 20)
        game.transferHouseItemsToDepot()

        assertThat(world.ownerRows).isEmpty()
    }

    // ----- getPlayerByGUID -----

    @Test
    fun `getPlayerByGUID resolves online players, zero and offline fallback`() {
        val game = newGame()
        val online = player(7, "Knight")
        game.addPlayer(online)
        loadedPlayers[8] = player(8, "Druid")

        assertThat(game.getPlayerByGUID(7)).isSameAs(online)
        assertThat(game.getPlayerByGUID(0)).isNull()
        assertThat(game.getPlayerByGUID(8)).isNull()
        assertThat(game.getPlayerByGUID(8, allowOffline = true)!!.name).isEqualTo("Druid")
    }
}
