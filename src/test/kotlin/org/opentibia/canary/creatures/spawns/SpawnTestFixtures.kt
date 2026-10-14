package org.opentibia.canary.creatures.spawns

import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.map.Position

class FakeSpawnScheduler(private val now: () -> Long) : SpawnScheduler {
    private class PendingTask(val dueAt: Long, val task: () -> Unit) {
        var cancelled = false
    }

    private val tasks = mutableListOf<PendingTask>()

    val pendingCount: Int get() = tasks.count { !it.cancelled }

    fun runDue() {
        val due = tasks.filter { !it.cancelled && it.dueAt <= now() }
        for (pending in due) {
            tasks.remove(pending)
            pending.task()
        }
    }

    override fun schedule(delayMs: Long, task: () -> Unit): SpawnTask {
        val pending = PendingTask(now() + delayMs, task)
        tasks.add(pending)
        return SpawnTask { pending.cancelled = true }
    }
}

class FakeMonsterFactory : MonsterFactory {
    private var nextId = 1L
    val created = mutableListOf<Monster>()

    override fun create(monsterType: MonsterType, position: Position, startup: Boolean): Monster {
        val monster = Monster(nextId++, monsterType.name, maxHealth = 100, health = 100, position = position)
        created.add(monster)
        return monster
    }
}

fun monsterTypeLookup(vararg types: MonsterType): MonsterTypeLookup {
    val byName = types.associateBy { it.name }
    return MonsterTypeLookup { byName[it] }
}

class FakeNpcFactory : NpcFactory {
    private var nextId = 1L
    val created = mutableListOf<Npc>()

    override fun create(npcType: NpcType, position: Position): Npc {
        val npc = Npc(nextId++, npcType.name, position = position)
        created.add(npc)
        return npc
    }
}

fun npcTypeLookup(vararg types: NpcType): NpcTypeLookup {
    val byName = types.associateBy { it.name }
    return NpcTypeLookup { byName[it] }
}
