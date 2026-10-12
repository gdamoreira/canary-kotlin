package org.opentibia.canary.game.bank

import org.opentibia.canary.creatures.Player
import java.util.concurrent.ConcurrentHashMap

data class CurrencyBreakdown(
    val crystal: Int,
    val platinum: Int,
    val gold: Int
)

object Currency {
    const val CRYSTAL_COIN_VALUE = 10000L
    const val PLATINUM_COIN_VALUE = 100L
    const val GOLD_COIN_VALUE = 1L

    fun toGold(crystal: Int = 0, platinum: Int = 0, gold: Int = 0): Long {
        return (crystal * CRYSTAL_COIN_VALUE) + (platinum * PLATINUM_COIN_VALUE) + (gold * GOLD_COIN_VALUE)
    }

    fun fromGold(totalGold: Long): CurrencyBreakdown {
        var remaining = totalGold
        val crystal = (remaining / CRYSTAL_COIN_VALUE).toInt()
        remaining %= CRYSTAL_COIN_VALUE

        val platinum = (remaining / PLATINUM_COIN_VALUE).toInt()
        remaining %= PLATINUM_COIN_VALUE

        val gold = remaining.toInt()

        return CurrencyBreakdown(crystal, platinum, gold)
    }
}

class BankService {
    // Key: playerId -> bank balance in gold coins
    private val balances = ConcurrentHashMap<Long, Long>()

    fun getBalance(player: Player): Long {
        return balances.getOrDefault(player.id, 0L)
    }

    fun deposit(player: Player, amount: Long): Boolean {
        if (amount <= 0) return false
        balances.merge(player.id, amount) { current, added -> current + added }
        return true
    }

    fun withdraw(player: Player, amount: Long): Boolean {
        if (amount <= 0) return false
        val current = getBalance(player)
        if (current < amount) return false

        balances[player.id] = current - amount
        return true
    }

    fun transfer(from: Player, to: Player, amount: Long): Boolean {
        if (amount <= 0) return false
        val fromBalance = getBalance(from)
        if (fromBalance < amount) return false

        balances[from.id] = fromBalance - amount
        deposit(to, amount)
        return true
    }
}
