package org.opentibia.canary.creatures.npcs

import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.monsters.RespawnPeriod
import org.opentibia.canary.creatures.monsters.RespawnType
import org.opentibia.canary.creatures.monsters.VoiceBlock
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.npcshop.ShopBlock
import org.opentibia.canary.spells.SoundEffect
import java.util.TreeMap

/** `enum NpcsEvent_t` (`src/creatures/creatures_definitions.hpp:564-575`). */
enum class NpcsEvent(val value: Int) {
    NPCS_EVENT_NONE(0),
    NPCS_EVENT_THINK(1),
    NPCS_EVENT_APPEAR(2),
    NPCS_EVENT_DISAPPEAR(3),
    NPCS_EVENT_MOVE(4),
    NPCS_EVENT_SAY(5),
    NPCS_EVENT_PLAYER_BUY(6),
    NPCS_EVENT_PLAYER_SELL(7),
    NPCS_EVENT_PLAYER_CHECK_ITEM(8),
    NPCS_EVENT_PLAYER_CLOSE_CHANNEL(9),
}

/** `struct NpcInfo` (`src/creatures/npcs/npcs.hpp:29-76`). */
class NpcInfo {
    var scriptInterface: Any? = null
    var outfit: Outfit = Outfit()
    var respawnType: RespawnType = RespawnType()
    var light: LightInfo = LightInfo()
    var speechBubble: Int = SPEECHBUBBLE_NORMAL
    var currencyId: Int = ITEM_GOLD_COIN
    var yellChance: Int = 0
    var yellSpeedTicks: Int = 0
    var baseSpeed: Int = 55
    var walkInterval: Int = 2000
    var creatureAppearEvent: Int = -1
    var creatureDisappearEvent: Int = -1
    var creatureMoveEvent: Int = -1
    var creatureSayEvent: Int = -1
    var thinkEvent: Int = -1
    var playerCloseChannel: Int = -1
    var playerBuyEvent: Int = -1
    var playerSellEvent: Int = -1
    var playerLookEvent: Int = -1
    var health: Int = 100
    var healthMax: Int = 100
    var walkRadius: Int = 10
    var canPushItems: Boolean = false
    var canPushCreatures: Boolean = false
    var pushable: Boolean = false
    var floorChange: Boolean = false
    var soundChance: Int = 0
    var soundSpeedTicks: Int = 0
    var soundVector: MutableList<SoundEffect> = mutableListOf()
    var voiceVector: MutableList<VoiceBlock> = mutableListOf()
    var scripts: MutableSet<String> = sortedSetOf()
    var shopItemVector: MutableList<ShopBlock> = mutableListOf()
    var eventType: NpcsEvent = NpcsEvent.NPCS_EVENT_NONE

    companion object {
        /** `ITEM_GOLD_COIN` (`src/utils/utils_definitions.hpp:608`). */
        const val ITEM_GOLD_COIN: Int = 3031

        /** `SPEECHBUBBLE_NORMAL` (`src/creatures/creatures_definitions.hpp:335`). */
        const val SPEECHBUBBLE_NORMAL: Int = 1
    }
}

/**
 * `class NpcType` (`src/creatures/npcs/npcs.hpp:28-96`).
 *
 * `canSpawn` reads the day/night state Canary resolves through
 * `g_game().gameIsDay()`; per AGENTS.md 1.1 it is constructor-injected.
 */
class NpcType(
    initName: String,
    private val gameIsDay: () -> Boolean = { true },
) {
    var name: String = initName
    var m_lowerName: String = initName.lowercase()
    var typeName: String = initName
    var nameDescription: String = initName
    val info: NpcInfo = NpcInfo()

    /** `NpcType::getLowerName` (`src/creatures/npcs/npcs.hpp:67`). */
    fun getLowerName(): String = m_lowerName

    /** `NpcType::canSpawn` (`src/creatures/npcs/npcs.cpp:22-32`). */
    fun canSpawn(pos: Position): Boolean {
        var canSpawn = true
        val isDay = gameIsDay()

        if ((isDay && info.respawnType.period == RespawnPeriod.RESPAWNPERIOD_NIGHT) ||
            (!isDay && info.respawnType.period == RespawnPeriod.RESPAWNPERIOD_DAY)
        ) {
            // It will ignore day and night if underground
            canSpawn = pos.z > Creature.MAP_INIT_SURFACE_LAYER && info.respawnType.underground
        }

        return canSpawn
    }

    /**
     * `NpcType::loadCallback` (`src/creatures/npcs/npcs.cpp:34-75`). The
     * `LuaScriptInterface::getEvent()` read is hoisted into [eventId] because the
     * Lua registration itself stays Lua (AGENTS.md 1.4).
     */
    fun loadCallback(eventId: Int, scriptInterface: Any? = null): Boolean {
        if (eventId == -1) {
            return false
        }

        info.scriptInterface = scriptInterface
        when (info.eventType) {
            NpcsEvent.NPCS_EVENT_THINK -> info.thinkEvent = eventId
            NpcsEvent.NPCS_EVENT_APPEAR -> info.creatureAppearEvent = eventId
            NpcsEvent.NPCS_EVENT_DISAPPEAR -> info.creatureDisappearEvent = eventId
            NpcsEvent.NPCS_EVENT_MOVE -> info.creatureMoveEvent = eventId
            NpcsEvent.NPCS_EVENT_SAY -> info.creatureSayEvent = eventId
            NpcsEvent.NPCS_EVENT_PLAYER_BUY -> info.playerBuyEvent = eventId
            NpcsEvent.NPCS_EVENT_PLAYER_SELL -> info.playerSellEvent = eventId
            NpcsEvent.NPCS_EVENT_PLAYER_CHECK_ITEM -> info.playerLookEvent = eventId
            NpcsEvent.NPCS_EVENT_PLAYER_CLOSE_CHANNEL -> info.playerCloseChannel = eventId
            NpcsEvent.NPCS_EVENT_NONE -> Unit
        }

        return true
    }

    /**
     * `NpcType::loadShop` (`src/creatures/npcs/npcs.cpp:77-104`): deduplicates
     * against the target vector and appends. The global `Item::items` price
     * registration (`npcs.cpp:78-86`) and the container child-shop copy
     * (`npcs.cpp:95-102`) belong to the item registry and are PARITY GAPS.
     */
    fun loadShop(npcType: NpcType, shopBlock: ShopBlock) {
        if (npcType.info.shopItemVector.any { it == shopBlock }) {
            return
        }

        npcType.info.shopItemVector.add(shopBlock)
    }
}

/** `class Npcs` (`src/creatures/npcs/npcs.hpp:98-116`). */
class Npcs {
    private val npcs: MutableMap<String, NpcType> = TreeMap()

    /** `Npcs::getNpcType` (`src/creatures/npcs/npcs.cpp:138-147`). */
    fun getNpcType(name: String, create: Boolean = false): NpcType? {
        val key = name.lowercase()
        npcs[key]?.let { return it }

        return if (create) {
            NpcType(name).also { npcs[key] = it }
        } else {
            null
        }
    }
}
