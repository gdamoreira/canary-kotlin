package org.opentibia.canary.game.bank

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.map.Position

class BankSystemTest {

    @Test
    fun `calculates currency conversion across gold, platinum, and crystal coins`() {
        // 1 Crystal (2160) = 10,000 gp
        // 1 Platinum (2152) = 100 gp
        // 1 Gold (2148) = 1 gp
        assertThat(Currency.toGold(crystal = 2, platinum = 5, gold = 30)).isEqualTo(20530L)

        val breakdown = Currency.fromGold(25432L)
        assertThat(breakdown.crystal).isEqualTo(2)
        assertThat(breakdown.platinum).isEqualTo(54)
        assertThat(breakdown.gold).isEqualTo(32)
    }

    @Test
    fun `deposits and withdraws money from player bank account`() {
        val bankService = BankService()
        val player = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))

        // Initial balance
        assertThat(bankService.getBalance(player)).isEqualTo(0L)

        // Deposit 15,000 gp
        assertThat(bankService.deposit(player, 15000L)).isTrue()
        assertThat(bankService.getBalance(player)).isEqualTo(15000L)

        // Withdraw 5,000 gp
        assertThat(bankService.withdraw(player, 5000L)).isTrue()
        assertThat(bankService.getBalance(player)).isEqualTo(10000L)

        // Withdraw more than balance fails
        assertThat(bankService.withdraw(player, 20000L)).isFalse()
        assertThat(bankService.getBalance(player)).isEqualTo(10000L)
    }

    @Test
    fun `transfers funds between player bank accounts`() {
        val bankService = BankService()
        val sender = Player(1, "Alice", 100, 100, position = Position(100, 100, 7))
        val recipient = Player(2, "Bob", 100, 100, position = Position(100, 100, 7))

        bankService.deposit(sender, 10000L)

        val transferred = bankService.transfer(sender, recipient, 4000L)
        assertThat(transferred).isTrue()
        assertThat(bankService.getBalance(sender)).isEqualTo(6000L)
        assertThat(bankService.getBalance(recipient)).isEqualTo(4000L)
    }
}
