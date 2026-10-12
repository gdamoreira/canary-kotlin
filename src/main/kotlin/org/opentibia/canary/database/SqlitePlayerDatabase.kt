package org.opentibia.canary.database

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position
import java.sql.DriverManager

class SqlitePlayerDatabase(private val jdbcUrl: String = "jdbc:sqlite::memory:") {

    init {
        try {
            Class.forName("org.sqlite.JDBC")
        } catch (_: Exception) {
            // driver might be loaded via service provider or already registered
        }
    }

    private val connection = DriverManager.getConnection(jdbcUrl)

    fun initSchema() {
        connection.createStatement().use { stmt ->
            stmt.execute(
                """
                CREATE TABLE IF NOT EXISTS players (
                    id INTEGER PRIMARY KEY,
                    name TEXT NOT NULL UNIQUE,
                    max_health INTEGER NOT NULL,
                    health INTEGER NOT NULL,
                    max_mana INTEGER NOT NULL,
                    mana INTEGER NOT NULL,
                    capacity REAL NOT NULL,
                    speed INTEGER NOT NULL,
                    pos_x INTEGER NOT NULL,
                    pos_y INTEGER NOT NULL,
                    pos_z INTEGER NOT NULL,
                    experience INTEGER NOT NULL,
                    level INTEGER NOT NULL
                );
                """.trimIndent()
            )
        }
    }

    fun savePlayer(player: Player) {
        val sql = """
            INSERT OR REPLACE INTO players 
            (id, name, max_health, health, max_mana, mana, capacity, speed, pos_x, pos_y, pos_z, experience, level)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?);
        """.trimIndent()

        connection.prepareStatement(sql).use { stmt ->
            stmt.setLong(1, player.id)
            stmt.setString(2, player.name)
            stmt.setInt(3, player.maxHealth)
            stmt.setInt(4, player.health)
            stmt.setInt(5, player.maxMana)
            stmt.setInt(6, player.mana)
            stmt.setDouble(7, player.capacity)
            stmt.setInt(8, player.speed)
            stmt.setInt(9, player.position.x)
            stmt.setInt(10, player.position.y)
            stmt.setInt(11, player.position.z)
            stmt.setLong(12, player.experience)
            stmt.setInt(13, player.level)
            stmt.executeUpdate()
        }
    }

    fun loadPlayerByName(name: String): Player? {
        val sql = "SELECT * FROM players WHERE LOWER(name) = LOWER(?);"
        connection.prepareStatement(sql).use { stmt ->
            stmt.setString(1, name)
            val rs = stmt.executeQuery()
            if (rs.next()) {
                return Player(
                    id = rs.getLong("id"),
                    name = rs.getString("name"),
                    maxHealth = rs.getInt("max_health"),
                    health = rs.getInt("health"),
                    maxMana = rs.getInt("max_mana"),
                    mana = rs.getInt("mana"),
                    capacity = rs.getDouble("capacity"),
                    speed = rs.getInt("speed"),
                    position = Position(rs.getInt("pos_x"), rs.getInt("pos_y"), rs.getInt("pos_z")),
                    experience = rs.getLong("experience"),
                    level = rs.getInt("level")
                )
            }
        }
        return null
    }
}
