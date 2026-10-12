package org.opentibia.canary.database

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import org.opentibia.canary.persistence.PlayerStorage
import javax.sql.DataSource

class JdbcPlayerRepository(private val dataSource: DataSource) : PlayerStorage {

    override fun save(player: Player) {
        val sql = """
            UPDATE players SET 
                health = ?,
                mana = ?,
                level = ?,
                experience = ?,
                posx = ?,
                posy = ?,
                posz = ?
            WHERE LOWER(name) = LOWER(?)
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setInt(1, player.health)
                stmt.setInt(2, player.mana)
                stmt.setInt(3, player.level)
                stmt.setLong(4, player.experience)
                stmt.setInt(5, player.position.x)
                stmt.setInt(6, player.position.y)
                stmt.setInt(7, player.position.z)
                stmt.setString(8, player.name)
                stmt.executeUpdate()
            }
        }
    }

    override fun loadByName(name: String): Player? {
        val sql = """
            SELECT id, name, health, healthmax, mana, manamax, cap, speed, level, experience, posx, posy, posz
            FROM players 
            WHERE LOWER(name) = LOWER(?) AND deletion = 0
        """.trimIndent()

        dataSource.connection.use { conn ->
            conn.prepareStatement(sql).use { stmt ->
                stmt.setString(1, name)
                val rs = stmt.executeQuery()
                if (rs.next()) {
                    return Player(
                        id = rs.getLong("id"),
                        name = rs.getString("name"),
                        maxHealth = rs.getInt("healthmax"),
                        health = rs.getInt("health"),
                        maxMana = rs.getInt("manamax"),
                        mana = rs.getInt("mana"),
                        capacity = rs.getDouble("cap"),
                        speed = rs.getInt("speed"),
                        position = Position(rs.getInt("posx"), rs.getInt("posy"), rs.getInt("posz")),
                        experience = rs.getLong("experience"),
                        level = rs.getInt("level")
                    )
                }
            }
        }
        return null
    }
}
