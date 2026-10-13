package org.opentibia.canary.lua.bindings.library

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.bank.BankService
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position

/**
 * Transcript tests for the `Bank` table, mirroring `BankFunctions`
 * (src/lua/functions/core/game/bank_functions.cpp) over the ported `BankService`.
 */
class BankFunctionsLibraryTest {

    private fun environment(banks: BankService = BankService()): LuaEnvironment =
        LuaEnvironment().apply { initState() }.also { BankFunctions.install(it, WorldBankOperations(banks)) }

    private fun player(id: Long, name: String): Player =
        Player(id, name, 100, 100, position = Position(100, 100, 7))

    @Test
    fun `credit debit and balance mutate the player bank account`() {
        val banks = BankService()
        val environment = environment(banks)
        environment.globals.set("alice", environment.userdata.wrap("Player", player(1, "Alice")))

        environment.loadChunk(
            """
            credited = Bank.credit(alice, 500)
            afterCredit = Bank.balance(alice)
            hasEnough = Bank.hasBalance(alice, 400)
            debited = Bank.debit(alice, 200)
            afterDebit = Bank.balance(alice)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("credited").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterCredit").todouble()).isEqualTo(500.0)
        assertThat(environment.getGlobal("hasEnough").toboolean()).isTrue()
        assertThat(environment.getGlobal("debited").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterDebit").todouble()).isEqualTo(300.0)
    }

    @Test
    fun `debit refuses amounts above the balance`() {
        val environment = environment()
        environment.globals.set("alice", environment.userdata.wrap("Player", player(1, "Alice")))

        environment.loadChunk("result = Bank.debit(alice, 1)")

        assertThat(environment.getGlobal("result").toboolean()).isFalse()
    }

    @Test
    fun `the two-argument balance overload sets the account balance`() {
        val environment = environment()
        environment.globals.set("alice", environment.userdata.wrap("Player", player(1, "Alice")))

        environment.loadChunk(
            """
            set = Bank.balance(alice, 750)
            value = Bank.balance(alice)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("set").toboolean()).isTrue()
        assertThat(environment.getGlobal("value").todouble()).isEqualTo(750.0)
    }

    @Test
    fun `transfer moves gold between two player accounts`() {
        val banks = BankService()
        val environment = environment(banks)
        environment.globals.set("alice", environment.userdata.wrap("Player", player(1, "Alice")))
        environment.globals.set("bob", environment.userdata.wrap("Player", player(2, "Bob")))

        environment.loadChunk(
            """
            Bank.credit(alice, 100)
            moved = Bank.transfer(alice, bob, 40)
            aliceBalance = Bank.balance(alice)
            bobBalance = Bank.balance(bob)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("moved").toboolean()).isTrue()
        assertThat(environment.getGlobal("aliceBalance").todouble()).isEqualTo(60.0)
        assertThat(environment.getGlobal("bobBalance").todouble()).isEqualTo(40.0)
    }

    @Test
    fun `withdraw and deposit mutate the account balances`() {
        val banks = BankService()
        val environment = environment(banks)
        environment.globals.set("alice", environment.userdata.wrap("Player", player(1, "Alice")))
        environment.globals.set("bob", environment.userdata.wrap("Player", player(2, "Bob")))

        environment.loadChunk(
            """
            Bank.credit(alice, 100)
            withdrawn = Bank.withdraw(alice, 25)
            afterWithdraw = Bank.balance(alice)
            deposited = Bank.deposit(alice, 50, bob)
            bobBalance = Bank.balance(bob)
            """.trimIndent(),
        )

        assertThat(environment.getGlobal("withdrawn").toboolean()).isTrue()
        assertThat(environment.getGlobal("afterWithdraw").todouble()).isEqualTo(75.0)
        assertThat(environment.getGlobal("deposited").toboolean()).isTrue()
        assertThat(environment.getGlobal("bobBalance").todouble()).isEqualTo(50.0)
    }
}
