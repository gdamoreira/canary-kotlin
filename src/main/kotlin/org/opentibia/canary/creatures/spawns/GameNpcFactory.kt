package org.opentibia.canary.creatures.spawns

import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.npcs.Npcs
import org.opentibia.canary.game.Game
import org.opentibia.canary.map.Position
import java.util.concurrent.atomic.AtomicLong

/** `Npc::npcAutoID` seed (src/creatures/npcs/npc.cpp:26). */
const val NPC_AUTO_ID_START: Long = 0x80000000L

/**
 * Production [NpcFactory] over the composed world: the Kotlin counterpart of
 * `std::make_shared<Npc>(npcType)`
 * (src/creatures/npcs/spawns/spawn_npc.cpp:194) plus the world-entry tail the
 * C++ engine keeps around it (`g_game().internalPlaceCreature` on startup,
 * `g_game().placeCreature` on a respawn, spawn_npc.cpp:195-205) and the
 * `Npc::addList` registration inside `Game::internalPlaceCreature`
 * (src/game/game.cpp:1513, `Npc::addList` -> `Game::addNpc`, npc.cpp:1225).
 *
 * The npc stats come from the registered `creatures.npcs.NpcType`
 * (`Npc::Npc`, src/creatures/npcs/npc.cpp:420-439: health, base speed, outfit
 * and light); the `RATE_NPC_HEALTH` multiplier and the creature-event
 * registration of the C++ constructor are PARITY GAPS (Npc.kt). An npc that
 * cannot enter the world resolves to `null`, mirroring the C++ `return false`.
 *
 * `Npc::npcAutoID` is a C++ static; the port replaces the global with this
 * factory-owned sequence (AGENTS.md 1.1). The constructor-assigned id is a
 * PARITY GAP against the C++ `Npc::setID()` after placement
 * (`Game::internalPlaceCreature`, game.cpp:1512): the id is assigned up front
 * and a rejected placement leaves a gap in the sequence, exactly like
 * [GameMonsterFactory].
 */
class GameNpcFactory(
    private val game: Game,
    private val npcTypes: Npcs,
) : NpcFactory {
    private val npcAutoId = AtomicLong(NPC_AUTO_ID_START)

    override fun create(npcType: NpcType, position: Position, startup: Boolean): Npc? {
        val registeredType = npcTypes.getNpcType(npcType.name) ?: return null
        val npc = Npc(
            id = npcAutoId.getAndIncrement(),
            name = registeredType.name,
            position = position,
            npcType = registeredType,
        )

        val placed = if (startup) {
            game.internalPlaceCreature(npc, position, extendedPos = true, forced = false)
        } else {
            game.placeCreature(npc, position, extendedPos = false, forced = true)
        }
        if (!placed) {
            return null
        }

        game.addNpc(npc)
        // `SpawnNpc::spawnNpc`'s `npc->setMasterPos(pos)` (spawn_npc.cpp:211).
        npc.setMasterPos(position)
        return npc
    }
}
