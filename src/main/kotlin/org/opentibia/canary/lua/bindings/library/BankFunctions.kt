package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.bank.BankService
import org.opentibia.canary.lua.runtime.LuaEnvironment

/**
 * Engine seam behind the global `Bank` table. Mirrors the `Bank` operations of
 * `BankFunctions::luaBank*` (src/lua/functions/core/game/bank_functions.cpp:29-167).
 *
 * Every method receives the resolved `playerOrGuild` argument and returns `null` for
 * the C++ "Bank is nullptr" branch, which [BankFunctions] answers by reporting the
 * error and returning the first argument (the C++ `return 1` without a push).
 */
interface BankOperations {
    fun credit(bank: Any?, amount: Long): Boolean?

    fun debit(bank: Any?, amount: Long): Boolean?

    /** `Bank.balance(playerOrGuild)` for the one-argument getter. */
    fun balance(bank: Any?): Long?

    /** `Bank.balance(playerOrGuild, amount)` for the setter overload. */
    fun setBalance(bank: Any?, amount: Long): Boolean?

    fun hasBalance(bank: Any?, amount: Long): Boolean?

    fun transfer(source: Any?, destination: Any?, amount: Long): Boolean?

    /** `Bank.withdraw(player, amount[, source])`; [source] is null for the two-argument form. */
    fun withdraw(player: Any, amount: Long, source: Any?): Boolean?

    /** `Bank.deposit(player, amount[, destination])`; a null [amount] is the C++ `nil` -> `getMoney()`. */
    fun deposit(player: Any, amount: Long?, destination: Any?): Boolean?

    fun reportError(message: String) {}
}

/** Documented no-op used when no bank service is wired. */
object UnwiredBankOperations : BankOperations {
    override fun credit(bank: Any?, amount: Long): Boolean? = null
    override fun debit(bank: Any?, amount: Long): Boolean? = null
    override fun balance(bank: Any?): Long? = null
    override fun setBalance(bank: Any?, amount: Long): Boolean? = null
    override fun hasBalance(bank: Any?, amount: Long): Boolean? = null
    override fun transfer(source: Any?, destination: Any?, amount: Long): Boolean? = null
    override fun withdraw(player: Any, amount: Long, source: Any?): Boolean? = null
    override fun deposit(player: Any, amount: Long?, destination: Any?): Boolean? = null
}

/**
 * [BankOperations] over the ported [BankService] for player banks.
 *
 * Parity gaps:
 *  - Guild banks (`getBank(..., isGuild)`) are not modeled: guild arguments resolve to
 *    `null` and take the C++ error branch.
 *  - `Bank.withdraw`/`Bank.deposit` inventory delivery (`Game::addMoney`/
 *    `Game::removeMoney`) and the town/denied-name transfer gates are not modeled;
 *    the balance mutation is applied directly.
 *  - `Bank::transferTo` metrics counters are not emitted.
 */
class WorldBankOperations(
    private val banks: BankService = BankService(),
) : BankOperations {

    override fun credit(bank: Any?, amount: Long): Boolean? {
        val player = resolvePlayer(bank) ?: return null
        if (amount <= 0) {
            return true
        }
        return banks.deposit(player, amount)
    }

    override fun debit(bank: Any?, amount: Long): Boolean? {
        val player = resolvePlayer(bank) ?: return null
        if (banks.getBalance(player) < amount) {
            return false
        }
        if (amount == 0L) {
            return true
        }
        return banks.withdraw(player, amount)
    }

    override fun balance(bank: Any?): Long? = resolvePlayer(bank)?.let(banks::getBalance)

    override fun setBalance(bank: Any?, amount: Long): Boolean? {
        val player = resolvePlayer(bank) ?: return null
        val current = banks.getBalance(player)
        if (amount == current) {
            return true
        }
        if (amount < 0) {
            return false
        }
        return if (amount > current) {
            banks.deposit(player, amount - current)
        } else {
            banks.withdraw(player, current - amount)
        }
    }

    override fun hasBalance(bank: Any?, amount: Long): Boolean? {
        val player = resolvePlayer(bank) ?: return null
        return banks.getBalance(player) >= amount
    }

    override fun transfer(source: Any?, destination: Any?, amount: Long): Boolean? {
        val from = resolvePlayer(source) ?: return null
        val to = resolvePlayer(destination) ?: return null
        return banks.transfer(from, to, amount)
    }

    override fun withdraw(player: Any, amount: Long, source: Any?): Boolean? {
        val target = resolvePlayer(player) ?: return null
        val bank = if (source == null) target else resolvePlayer(source) ?: return null
        if (amount <= 0) {
            return amount == 0L
        }
        return banks.withdraw(bank, amount)
    }

    override fun deposit(player: Any, amount: Long?, destination: Any?): Boolean? {
        val target = resolvePlayer(player) ?: return null
        val bank = if (destination == null) target else resolvePlayer(destination) ?: return null
        val value = amount ?: 0L
        if (value <= 0) {
            return value == 0L
        }
        return banks.deposit(bank, value)
    }

    private fun resolvePlayer(value: Any?): Player? = value as? Player
}

/**
 * Registers the global `Bank` table with every method of `BankFunctions::init`
 * (bank_functions.cpp:17-27):
 *
 *   Bank.credit(playerOrGuild, amount)
 *   Bank.debit(playerOrGuild, amount)
 *   Bank.balance(playerOrGuild[, amount])
 *   Bank.hasBalance(playerOrGuild, amount)
 *   Bank.transfer(fromPlayerOrGuild, toPlayerOrGuild, amount)
 *   Bank.transferToGuild(fromPlayerOrGuild, toGuild, amount)
 *   Bank.withdraw(player, amount[, source])
 *   Bank.deposit(player, amount[, destination])
 *
 * The `balance` overload is selected by `lua_gettop(L) == 1`, exactly like the C++
 * branch, and every null-bank branch reports the C++ error text before returning the
 * first argument (`return 1` without a push).
 */
object BankFunctions {
    const val BANK_GLOBAL = "Bank"

    fun install(env: LuaEnvironment, operations: BankOperations = UnwiredBankOperations) {
        val bank = (env.globals.get(BANK_GLOBAL) as? LuaTable)
            ?: LuaTable().also { env.globals.set(BANK_GLOBAL, it) }

        bank.set("credit", libraryFunction { args ->
            val result = operations.credit(args.arg(1).touserdata(), luaArgLong(args, 2))
            if (result == null) bankNullError(operations, args.arg(1)) else LuaValue.valueOf(result)
        })

        bank.set("debit", libraryFunction { args ->
            val result = operations.debit(args.arg(1).touserdata(), luaArgLong(args, 2))
            if (result == null) bankNullError(operations, args.arg(1)) else LuaValue.valueOf(result)
        })

        bank.set("balance", libraryFunction { args ->
            val receiver = args.arg(1).touserdata()
            if (args.narg() == 1) {
                val result = operations.balance(receiver)
                if (result == null) bankNullError(operations, args.arg(1)) else LuaValue.valueOf(result.toDouble())
            } else {
                val result = operations.setBalance(receiver, luaArgLong(args, 2))
                if (result == null) bankNullError(operations, args.arg(1)) else LuaValue.valueOf(result)
            }
        })

        bank.set("hasBalance", libraryFunction { args ->
            val result = operations.hasBalance(args.arg(1).touserdata(), luaArgLong(args, 2))
            if (result == null) bankNullError(operations, args.arg(1)) else LuaValue.valueOf(result)
        })

        bank.set("transfer", libraryFunction { args ->
            val result = operations.transfer(
                args.arg(1).touserdata(),
                args.arg(2).touserdata(),
                luaArgLong(args, 3),
            )
            if (result == null) bankNullError(operations, args.arg(1)) else LuaValue.valueOf(result)
        })

        bank.set("transferToGuild", libraryFunction { args ->
            val result = operations.transfer(
                args.arg(1).touserdata(),
                args.arg(2).touserdata(),
                luaArgLong(args, 3),
            )
            if (result == null) bankNullError(operations, args.arg(1)) else LuaValue.valueOf(result)
        })

        bank.set("withdraw", libraryFunction { args ->
            val player = args.arg(1).touserdata()
            if (player == null) {
                operations.reportError("Player not found")
                return@libraryFunction args.arg(1)
            }
            val source = if (args.narg() > 2) args.arg(3).touserdata() else null
            val result = operations.withdraw(player, luaArgLong(args, 2), source)
            if (result == null) bankNullError(operations, args.arg(1)) else LuaValue.valueOf(result)
        })

        bank.set("deposit", libraryFunction { args ->
            val player = args.arg(1).touserdata()
            if (player == null) {
                operations.reportError("Player not found")
                return@libraryFunction args.arg(1)
            }
            val amount = if (args.narg() >= 2 && !args.arg(2).isnil()) luaArgLong(args, 2) else null
            val destination = if (args.narg() > 2) args.arg(3).touserdata() else null
            val result = operations.deposit(player, amount, destination)
            if (result == null) bankNullError(operations, args.arg(1)) else LuaValue.valueOf(result)
        })
    }

    /** The C++ `Lua::reportErrorFunc("Bank is nullptr")` + `return 1` first-arg branch. */
    private fun bankNullError(operations: BankOperations, firstArgument: LuaValue): LuaValue {
        operations.reportError("Bank is nullptr")
        return firstArgument
    }
}
