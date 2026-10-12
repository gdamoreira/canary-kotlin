package org.opentibia.canary.database

import org.opentibia.canary.account.Account
import org.opentibia.canary.account.AccountRepository
import org.opentibia.canary.account.CharacterEntry
import javax.sql.DataSource

class JdbcAccountRepository(private val dataSource: DataSource) : AccountRepository {

    override fun findByAccountName(name: String): Account? {
        val sqlAccount = "SELECT id, name, password, premdays FROM accounts WHERE LOWER(name) = LOWER(?)"
        dataSource.connection.use { conn ->
            conn.prepareStatement(sqlAccount).use { stmt ->
                stmt.setString(1, name)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    val accountId = rs.getLong("id")
                    val accName = rs.getString("name")
                    val password = rs.getString("password")
                    val premdays = rs.getInt("premdays")

                    val characters = loadCharactersForAccount(conn, accountId)

                    return Account(
                        id = accountId,
                        name = accName,
                        passwordHash = password,
                        premiumDays = premdays,
                        characters = characters
                    )
                }
            }
        }
        return null
    }

    override fun authenticate(name: String, password: String): Account? {
        val account = findByAccountName(name) ?: return null
        return if (account.passwordHash == password) account else null
    }

    private fun loadCharactersForAccount(conn: java.sql.Connection, accountId: Long): List<CharacterEntry> {
        val sqlPlayers = "SELECT name FROM players WHERE account_id = ? AND deletion = 0"
        val list = mutableListOf<CharacterEntry>()
        conn.prepareStatement(sqlPlayers).use { stmt ->
            stmt.setLong(1, accountId)
            val rs = stmt.executeQuery()
            while (rs.next()) {
                list.add(CharacterEntry(name = rs.getString("name")))
            }
        }
        return list
    }
}
