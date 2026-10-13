package org.opentibia.canary.map.house

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.CylinderFlags
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.map.Position

/**
 * Transcript tests for `House::getRent`, `House::getPrice`,
 * `House::updateDoorDescription` and `Houses::payHouses`
 * (src/map/house/house.cpp:156-181, 953-1073).
 */
class HouseRentTest {

    private val position = Position(100, 100, 7)

    private fun player(id: Long, name: String, position: Position = this.position): Player =
        Player(id, name, 100, 100, position = position)

    private fun newHouse(world: FakeHouseWorld, rentPeriod: String = "never"): House =
        House(
            id = 1,
            name = "Central Flat 1",
            owner = 10,
            townId = 1,
            world = world,
            ownerInfoLookup = { HouseOwnerInfo("Player $it", it * 10) },
            rentPeriod = { rentPeriod },
        )

    @Test
    fun `getRent applies the HOUSE_RENT_RATE configuration`() {
        val world = FakeHouseWorld().apply { houseRentRate = 2.5f }
        val house = newHouse(world).apply { rent = 400 }

        assertThat(house.getRent()).isEqualTo(1000L)
    }

    @Test
    fun `getPrice is the square meter price plus the configured rent price`() {
        val world = FakeHouseWorld().apply {
            housePricePerSqm = 10
            housePriceRentMultiplier = 1.5f
            houseRentRate = 2.0f
        }
        val house = newHouse(world).apply {
            size = 17
            rent = 100
        }

        assertThat(house.getRent()).isEqualTo(200L)
        assertThat(house.getPrice()).isEqualTo(470L)
    }

    @Test
    fun `updateDoorDescription writes the owned house sentence with price and rent`() {
        val world = FakeHouseWorld().apply {
            cyclopediaHouseAuction = false
            housePurchasedShowPrice = true
        }
        val house = newHouse(world, rentPeriod = "weekly").apply {
            size = 17
            rent = 100
            ownerName = "Alice"
        }
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)

        house.updateDoorDescription()

        assertThat(door.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo(
            "It belongs to house 'Central Flat 1'. Alice owns this house. It is 17 square meters. " +
                "It costs 17,100 gold coins. The rent cost is 100 gold coins and it is billed weekly.",
        )
        assertThat(house.getPrice()).isEqualTo(17100L)
    }

    @Test
    fun `updateDoorDescription writes the unowned house sentence`() {
        val world = FakeHouseWorld().apply { cyclopediaHouseAuction = false }
        val house = newHouse(world).apply {
            owner = 0
            ownerName = ""
            size = 17
            rent = 100
        }
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)

        house.updateDoorDescription()

        assertThat(door.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo(
            "It belongs to house 'Central Flat 1'. Nobody owns this house. It is 17 square meters. " +
                "It costs 17,100 gold coins.",
        )
    }

    @Test
    fun `updateDoorDescription hides the price of an owned house unless configured to show it`() {
        val world = FakeHouseWorld().apply {
            cyclopediaHouseAuction = false
            housePurchasedShowPrice = false
        }
        val house = newHouse(world, rentPeriod = "monthly").apply {
            size = 10
            rent = 50
            ownerName = "Alice"
        }
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)

        house.updateDoorDescription()

        assertThat(door.getAttribute(ItemAttribute.DESCRIPTION)).isEqualTo(
            "It belongs to house 'Central Flat 1'. Alice owns this house. It is 10 square meters. " +
                "The rent cost is 50 gold coins and it is billed monthly.",
        )
    }

    @Test
    fun `updateDoorDescription omits the size and price when the auction system is enabled`() {
        val world = FakeHouseWorld().apply { cyclopediaHouseAuction = true }
        val house = newHouse(world).apply {
            size = 10
            rent = 50
            ownerName = "Alice"
        }
        val door = Door(1210)
        door.setDoorId(7)
        house.addDoor(door)

        house.updateDoorDescription()

        assertThat(door.getAttribute(ItemAttribute.DESCRIPTION))
            .isEqualTo("It belongs to house 'Central Flat 1'. Alice owns this house.")
    }

    @Test
    fun `payHouses does nothing for RENTPERIOD_NEVER`() {
        val world = FakeHouseWorld().apply { towns.add(1) }
        val houses = Houses(world = world)
        houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 100
        }
        world.onlinePlayers[10] = player(10, "Owner")

        houses.payHouses(RentPeriod_t.RENTPERIOD_NEVER)

        assertThat(world.savedPlayers).isEmpty()
        assertThat(world.ownerRows).isEmpty()
        assertThat(world.moneyRemoved).isEmpty()
    }

    @Test
    fun `payHouses skips ownerless houses and houses without a town`() {
        val world = FakeHouseWorld().apply { towns.add(1) }
        val houses = Houses(world = world)
        houses.addHouse(1).apply { rent = 100 }
        houses.addHouse(2).apply {
            owner = 10
            townId = 99
            rent = 100
        }
        world.onlinePlayers[10] = player(10, "Owner")

        houses.payHouses(RentPeriod_t.RENTPERIOD_MONTHLY)

        assertThat(world.ownerRows).isEmpty()
        assertThat(world.savedPlayers).isEmpty()
    }

    @Test
    fun `payHouses resets the ownership when the owner cannot be loaded`() {
        val world = FakeHouseWorld().apply { towns.add(1) }
        val houses = Houses(world = world)
        val house = houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 100
        }

        houses.payHouses(RentPeriod_t.RENTPERIOD_MONTHLY)

        assertThat(world.ownerRows).isEmpty()
        assertThat(house.owner).isEqualTo(10L)
        assertThat(world.savedPlayers).isEmpty()
    }

    @Test
    fun `payHouses resets the house of an inactive owner and saves the player`() {
        val world = FakeHouseWorld().apply {
            towns.add(1)
            houseLoseAfterInactivity = 30
            now = 1_700_000_000L
        }
        val houses = Houses(world = world, ownerInfoLookup = { HouseOwnerInfo("Owner", 100) })
        val house = houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 100
            paidUntil = 0
        }
        val owner = player(10, "Owner")
        world.onlinePlayers[10] = owner
        world.lastLoginSaved[10] = world.now - 31 * 86_400
        world.bankBalances[10] = 1000

        houses.payHouses(RentPeriod_t.RENTPERIOD_MONTHLY)

        assertThat(house.owner).isEqualTo(0L)
        assertThat(world.ownerRows).containsEntry(1, 0L)
        assertThat(world.moneyRemoved).isEmpty()
        assertThat(world.savedPlayers).containsExactly(owner)
    }

    @Test
    fun `payHouses keeps the house of an inactive VIP but still charges the rent`() {
        val world = FakeHouseWorld().apply {
            towns.add(1)
            houseLoseAfterInactivity = 30
            vipKeepHouse = true
            now = 1_700_000_000L
        }
        val houses = Houses(world = world)
        val house = houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 100
            paidUntil = 0
        }
        val owner = player(10, "Owner")
        world.onlinePlayers[10] = owner
        world.vipPlayers.add(10)
        world.lastLoginSaved[10] = world.now - 31 * 86_400
        world.bankBalances[10] = 1000

        houses.payHouses(RentPeriod_t.RENTPERIOD_WEEKLY)

        assertThat(house.owner).isEqualTo(10L)
        assertThat(world.moneyRemoved).containsExactly(owner to 100L)
        assertThat(house.paidUntil).isEqualTo(world.now + 7 * 86_400)
        assertThat(house.getPayRentWarnings()).isEqualTo(0L)
        assertThat(world.savedPlayers).containsExactly(owner)
    }

    @Test
    fun `payHouses charges the rent and extends paidUntil by the period`() {
        val world = FakeHouseWorld().apply {
            towns.add(1)
            now = 1_700_000_000L
        }
        val houses = Houses(world = world)
        val house = houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 100
            paidUntil = 0
        }
        val owner = player(10, "Owner")
        world.onlinePlayers[10] = owner
        world.bankBalances[10] = 500

        houses.payHouses(RentPeriod_t.RENTPERIOD_MONTHLY)

        assertThat(world.moneyRemoved).containsExactly(owner to 100L)
        assertThat(house.paidUntil).isEqualTo(world.now + 30 * 86_400)
        assertThat(world.savedPlayers).containsExactly(owner)
    }

    @Test
    fun `payHouses skips the charge while paidUntil is in the future`() {
        val world = FakeHouseWorld().apply {
            towns.add(1)
            now = 1_700_000_000L
        }
        val houses = Houses(world = world)
        val house = houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 100
            paidUntil = world.now + 1000
        }
        world.onlinePlayers[10] = player(10, "Owner")
        world.bankBalances[10] = 500

        houses.payHouses(RentPeriod_t.RENTPERIOD_DAILY)

        assertThat(world.moneyRemoved).isEmpty()
        assertThat(world.savedPlayers).isEmpty()
        assertThat(house.paidUntil).isEqualTo(world.now + 1000)
    }

    @Test
    fun `payHouses skips houses with a zero configured rent`() {
        val world = FakeHouseWorld().apply { towns.add(1) }
        val houses = Houses(world = world)
        houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 0
        }
        world.onlinePlayers[10] = player(10, "Owner")

        houses.payHouses(RentPeriod_t.RENTPERIOD_DAILY)

        assertThat(world.savedPlayers).isEmpty()
    }

    @Test
    fun `payHouses mails a rent warning letter and increments the warning counter`() {
        val world = FakeHouseWorld().apply { towns.add(1) }
        val houses = Houses(world = world)
        val house = houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 100
            name = "Central Flat 1"
            paidUntil = 0
        }
        val owner = player(10, "Owner")
        world.onlinePlayers[10] = owner

        houses.payHouses(RentPeriod_t.RENTPERIOD_MONTHLY)

        val letter = world.addedItems.single()
        assertThat(letter.to).isSameAs(world.inbox)
        assertThat(letter.index).isEqualTo(-1)
        assertThat(letter.flags and CylinderFlags.FLAG_NOLIMIT).isEqualTo(CylinderFlags.FLAG_NOLIMIT)
        assertThat(letter.item?.id).isEqualTo(ITEM_LETTER_STAMPED)
        assertThat(letter.item?.getAttribute(ItemAttribute.TEXT)).isEqualTo(
            "Warning! \nThe monthly rent of 100 gold for your house \"Central Flat 1\" is payable. " +
                "Have it within 7 days or you will lose this house.",
        )
        assertThat(house.getPayRentWarnings()).isEqualTo(1L)
        assertThat(world.moneyRemoved).isEmpty()
        assertThat(world.savedPlayers).containsExactly(owner)
    }

    @Test
    fun `payHouses words the yearly warning as annual`() {
        val world = FakeHouseWorld().apply { towns.add(1) }
        val houses = Houses(world = world)
        houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 250
            name = "Central Flat 1"
        }
        world.onlinePlayers[10] = player(10, "Owner")

        houses.payHouses(RentPeriod_t.RENTPERIOD_YEARLY)

        assertThat(world.addedItems.single().item?.getAttribute(ItemAttribute.TEXT)).isEqualTo(
            "Warning! \nThe annual rent of 250 gold for your house \"Central Flat 1\" is payable. " +
                "Have it within 7 days or you will lose this house.",
        )
    }

    @Test
    fun `payHouses evicts the owner after seven warnings`() {
        val world = FakeHouseWorld().apply { towns.add(1) }
        val houses = Houses(world = world)
        val house = houses.addHouse(1).apply {
            owner = 10
            townId = 1
            rent = 100
            rentWarnings = 7
        }
        val owner = player(10, "Owner")
        world.onlinePlayers[10] = owner

        houses.payHouses(RentPeriod_t.RENTPERIOD_MONTHLY)

        assertThat(world.addedItems).isEmpty()
        assertThat(world.ownerRows).containsEntry(1, 0L)
        assertThat(house.owner).isEqualTo(0L)
        assertThat(world.savedPlayers).containsExactly(owner)
    }
}
