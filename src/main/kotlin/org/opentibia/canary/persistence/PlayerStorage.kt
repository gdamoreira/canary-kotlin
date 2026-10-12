package org.opentibia.canary.persistence

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.map.Position

data class PlayerData(
    val id: Long,
    val name: String,
    val maxHealth: Int,
    val health: Int,
    val maxMana: Int,
    val mana: Int,
    val capacity: Double,
    val speed: Int,
    val posX: Int,
    val posY: Int,
    val posZ: Int,
    val experience: Long,
    val level: Int
)

interface PlayerStorage {
    fun save(player: Player)
    fun loadByName(name: String): Player?
}

class InMemoryPlayerStorage : PlayerStorage {
    private val records = mutableMapOf<String, PlayerData>()

    override fun save(player: Player) {
        records[player.name.lowercase()] = PlayerData(
            id = player.id,
            name = player.name,
            maxHealth = player.maxHealth,
            health = player.health,
            maxMana = player.maxMana,
            mana = player.mana,
            capacity = player.capacity,
            speed = player.speed,
            posX = player.position.x,
            posY = player.position.y,
            posZ = player.position.z,
            experience = player.experience,
            level = player.level
        )
    }

    override fun loadByName(name: String): Player? {
        val data = records[name.lowercase()] ?: return null
        return Player(
            id = data.id,
            name = data.name,
            maxHealth = data.maxHealth,
            health = data.health,
            maxMana = data.maxMana,
            mana = data.mana,
            capacity = data.capacity,
            speed = data.speed,
            position = Position(data.posX, data.posY, data.posZ),
            experience = data.experience,
            level = data.level
        )
    }
}
