package org.opentibia.canary.lua.bindings

import org.luaj.vm2.LuaTable
import org.luaj.vm2.LuaValue
import org.opentibia.canary.combat.condition.LightInfo
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.monsters.RespawnPeriod
import org.opentibia.canary.creatures.monsters.VoiceBlock
import org.opentibia.canary.creatures.npcs.NpcType
import org.opentibia.canary.creatures.npcs.NpcsEvent
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.lua.bindings.core.luaFunction
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.bindings.core.retrieveUserdata
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.npcshop.ShopBlock
import org.opentibia.canary.spells.SoundEffect

/**
 * Engine seam for the `NpcType` metatable. Mirrors the primitives of
 * src/lua/functions/creatures/npc/npc_type_functions.cpp.
 *
 * Every method returns `null` when the receiver is not a type, matching Canary's
 * `Lua::getUserdataShared<NpcType>(L, 1, "NpcType")` nil results, except the methods
 * whose C++ null branch pushes `false` (`speechBubble`, `currency`, `soundChance`,
 * `soundSpeedTicks`, `addSound`).
 */
interface NpcTypeOperations {
    /** Mirrors `NpcType(name)` resolving `g_npcs().getNpcType(name, true)`. */
    fun create(name: String): Any? = null

    fun isPushable(npcType: Any): Boolean? = null
    fun setPushable(npcType: Any, value: Boolean): Boolean? = null
    fun floorChange(npcType: Any): Boolean? = null
    fun setFloorChange(npcType: Any, value: Boolean): Boolean? = null
    fun canSpawn(npcType: Any, position: Position): Boolean? = null
    fun canPushItems(npcType: Any): Boolean? = null
    fun setCanPushItems(npcType: Any, value: Boolean): Boolean? = null
    fun canPushCreatures(npcType: Any): Boolean? = null
    fun setCanPushCreatures(npcType: Any, value: Boolean): Boolean? = null
    fun name(npcType: Any): String? = null
    fun setName(npcType: Any, value: String): Boolean? = null
    fun nameDescription(npcType: Any): String? = null
    fun setNameDescription(npcType: Any, value: String): Boolean? = null
    fun health(npcType: Any): Int? = null
    fun setHealth(npcType: Any, value: Int): Boolean? = null
    fun maxHealth(npcType: Any): Int? = null
    fun setMaxHealth(npcType: Any, value: Int): Boolean? = null
    fun getVoices(npcType: Any): List<MonsterVoiceInfo>? = null
    fun addVoice(npcType: Any, text: String, interval: Int, chance: Int, yell: Boolean): Boolean? = null
    fun getCreatureEvents(npcType: Any): List<String>? = null
    fun registerEvent(npcType: Any, name: String): Boolean? = null
    fun setEventType(npcType: Any, event: Int): Boolean? = null

    /** Mirrors `NpcType::loadCallback(&g_scripts().getScriptInterface())`. */
    fun loadCallback(npcType: Any): Boolean? = null

    fun outfit(npcType: Any): Outfit? = null

    /** Returns `null` when the look type is rejected like Canary's unsafe-script guard. */
    fun setOutfit(npcType: Any, outfit: Outfit): Boolean? = null

    fun baseSpeed(npcType: Any): Int? = null
    fun setBaseSpeed(npcType: Any, value: Int): Boolean? = null
    fun walkInterval(npcType: Any): Int? = null
    fun setWalkInterval(npcType: Any, value: Int): Boolean? = null
    fun walkRadius(npcType: Any): Int? = null
    fun setWalkRadius(npcType: Any, value: Int): Boolean? = null
    fun light(npcType: Any): LightInfo? = null
    fun setLight(npcType: Any, color: Int, level: Int): Boolean? = null
    fun yellChance(npcType: Any): Int? = null
    fun setYellChance(npcType: Any, value: Int): Boolean? = null
    fun yellSpeedTicks(npcType: Any): Int? = null
    fun setYellSpeedTicks(npcType: Any, value: Int): Boolean? = null
    fun respawnTypePeriod(npcType: Any): Int? = null
    fun setRespawnTypePeriod(npcType: Any, period: Int): Boolean? = null

    /** `npcType:respawnTypeIsUnderground()` returns the bool field as a number. */
    fun respawnTypeIsUnderground(npcType: Any): Int? = null
    fun setRespawnTypeIsUnderground(npcType: Any, value: Boolean): Boolean? = null
    fun speechBubble(npcType: Any): Int? = null
    fun setSpeechBubble(npcType: Any, value: Int): Boolean? = null
    fun currency(npcType: Any): Int? = null
    fun setCurrency(npcType: Any, value: Int): Boolean? = null
    fun addShopItem(npcType: Any, shop: ShopData): Boolean? = null
    fun soundChance(npcType: Any): Int? = null
    fun setSoundChance(npcType: Any, value: Int): Boolean? = null
    fun soundSpeedTicks(npcType: Any): Int? = null
    fun setSoundSpeedTicks(npcType: Any, value: Int): Boolean? = null
    fun addSound(npcType: Any, soundId: Int): Boolean? = null
    fun getSounds(npcType: Any): List<Int>? = null
}

/**
 * Registers the Canary `NpcType` methods from
 * src/lua/functions/creatures/npc/npc_type_functions.cpp:
 *
 *   NpcType(name)
 *   npcType:isPushable([bool])
 *   npcType:floorChange([bool])
 *   npcType:canSpawn(position)
 *   npcType:canPushItems([bool])
 *   npcType:canPushCreatures([bool])
 *   npcType:name([string])
 *   npcType:nameDescription([string])
 *   npcType:health([health])
 *   npcType:maxHealth([health])
 *   npcType:getVoices()
 *   npcType:addVoice(sentence, interval, chance, yell)
 *   npcType:getCreatureEvents()
 *   npcType:registerEvent(name)
 *   npcType:eventType(event)
 *   npcType:onThink/onAppear/onDisappear/onMove/onSay/onCloseChannel/onBuyItem/onSellItem/onCheckItem(callback)
 *   npcType:outfit([outfit])
 *   npcType:baseSpeed([speed])
 *   npcType:walkInterval([interval])
 *   npcType:walkRadius([radius])
 *   npcType:light([color, level]) -> getter returns level, color
 *   npcType:yellChance([chance])
 *   npcType:yellSpeedTicks([ticks])
 *   npcType:respawnTypePeriod([period])
 *   npcType:respawnTypeIsUnderground([underground])
 *   npcType:speechBubble([bubble])
 *   npcType:currency([currency])
 *   npcType:addShopItem(shop)
 *   npcType:soundChance([chance])
 *   npcType:soundSpeedTicks([ticks])
 *   npcType:addSound(soundId)
 *   npcType:getSounds()
 *
 * Fidelity quirks kept from the C++ source: `respawnTypeIsUnderground` answers a
 * number; `getSounds` reproduces the C++ stack behavior where each iteration
 * allocates a fresh table, so only the last entry is returned; `speechBubble`,
 * `currency`, `soundChance`, `soundSpeedTicks` and `addSound` answer `false` for a
 * missing receiver.
 */
object NpcTypeFunctions {
    const val NPC_TYPE_TYPE = "NpcType"

    fun install(env: LuaEnvironment, operations: NpcTypeOperations = WorldNpcTypeOperations()) {
        val registry = env.userdata

        registry.registerClass(NPC_TYPE_TYPE, "", luaFunction { args ->
            operations.create(args.arg(1).tojstring())?.let { env.userdata.wrap(NPC_TYPE_TYPE, it) } ?: LuaValue.NIL
        })

        registerBooleanAttribute(env, "isPushable", operations::isPushable, operations::setPushable)
        registerBooleanAttribute(env, "floorChange", operations::floorChange, operations::setFloorChange)
        registerBooleanAttribute(env, "canPushItems", operations::canPushItems, operations::setCanPushItems)
        registerBooleanAttribute(env, "canPushCreatures", operations::canPushCreatures, operations::setCanPushCreatures)

        registry.registerMethod(NPC_TYPE_TYPE, "canSpawn", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val position = luaToPosition(args.arg(2)) ?: Position(0, 0, 0)
            operations.canSpawn(npcType, position)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registerStringAttribute(env, "name", operations::name, operations::setName)
        registerStringAttribute(env, "nameDescription", operations::nameDescription, operations::setNameDescription)
        registerIntAttribute(env, "health", operations::health, operations::setHealth)
        registerIntAttribute(env, "maxHealth", operations::maxHealth, operations::setMaxHealth)

        registry.registerMethod(NPC_TYPE_TYPE, "getVoices", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val voices = operations.getVoices(npcType) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                voices.forEachIndexed { index, voice ->
                    table.set(
                        index + 1,
                        LuaTable().also { row ->
                            row.set("text", LuaValue.valueOf(voice.text))
                            row.set("yellText", LuaValue.valueOf(voice.yellText))
                        },
                    )
                }
            }
        })

        registry.registerMethod(NPC_TYPE_TYPE, "addVoice", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.addVoice(
                npcType,
                args.arg(2).tojstring(),
                args.arg(3).toint(),
                args.arg(4).toint(),
                args.arg(5).toboolean(),
            )?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE_TYPE, "getCreatureEvents", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val events = operations.getCreatureEvents(npcType) ?: return@luaFunction LuaValue.NIL
            LuaTable().also { table ->
                events.forEachIndexed { index, name -> table.set(index + 1, LuaValue.valueOf(name)) }
            }
        })

        registry.registerMethod(NPC_TYPE_TYPE, "registerEvent", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.registerEvent(npcType, args.arg(2).tojstring())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registry.registerMethod(NPC_TYPE_TYPE, "eventType", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.setEventType(npcType, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registerCallback(env, "onThink", operations)
        registerCallback(env, "onAppear", operations)
        registerCallback(env, "onDisappear", operations)
        registerCallback(env, "onMove", operations)
        registerCallback(env, "onSay", operations)
        registerCallback(env, "onCloseChannel", operations)
        registerCallback(env, "onBuyItem", operations)
        registerCallback(env, "onSellItem", operations)
        registerCallback(env, "onCheckItem", operations)

        // luaNpcTypeOutfit (npc_type_functions.cpp:411) reports nil when the
        // unsafe-script guard blocks the look type; the seam owns that decision.
        registry.registerMethod(NPC_TYPE_TYPE, "outfit", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                operations.outfit(npcType)?.let { pushOutfit(it) } ?: LuaValue.NIL
            } else {
                val outfit = luaToOutfit(args.arg(2)) ?: return@luaFunction LuaValue.NIL
                operations.setOutfit(npcType, outfit)?.let { if (it) LuaValue.TRUE else LuaValue.NIL } ?: LuaValue.NIL
            }
        })

        registerIntAttribute(env, "baseSpeed", operations::baseSpeed, operations::setBaseSpeed)
        registerIntAttribute(env, "walkInterval", operations::walkInterval, operations::setWalkInterval)
        registerIntAttribute(env, "walkRadius", operations::walkRadius, operations::setWalkRadius)

        // luaNpcTypeLight (npc_type_functions.cpp:481) returns level then color.
        registry.registerMethod(NPC_TYPE_TYPE, "light", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                val light = operations.light(npcType) ?: return@luaFunction LuaValue.NIL
                LuaValue.varargsOf(arrayOf(LuaValue.valueOf(light.level), LuaValue.valueOf(light.color)))
            } else {
                val color = args.arg(2).toint()
                val level = args.arg(3).toint()
                operations.setLight(npcType, color, level)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })

        registerIntAttribute(env, "yellChance", operations::yellChance, operations::setYellChance)
        registerIntAttribute(env, "yellSpeedTicks", operations::yellSpeedTicks, operations::setYellSpeedTicks)
        registerIntAttribute(env, "respawnTypePeriod", operations::respawnTypePeriod, operations::setRespawnTypePeriod)

        registry.registerMethod(NPC_TYPE_TYPE, "respawnTypeIsUnderground", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                operations.respawnTypeIsUnderground(npcType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                operations.setRespawnTypeIsUnderground(npcType, args.arg(2).toint() != 0)?.let { LuaValue.valueOf(it) }
                    ?: LuaValue.NIL
            }
        })

        registerFalseDefaultedInt(env, "speechBubble", operations::speechBubble, operations::setSpeechBubble)
        registerFalseDefaultedInt(env, "currency", operations::currency, operations::setCurrency)

        // luaNpcTypeAddShopItem (npc_type_functions.cpp:294) forwards the Shop block to
        // NpcType::loadShop; a missing shop userdata leaves nil on the stack.
        registry.registerMethod(NPC_TYPE_TYPE, "addShopItem", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val shop = retrieveUserdata(args, 2) as? ShopData ?: return@luaFunction LuaValue.NIL
            operations.addShopItem(npcType, shop)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })

        registerFalseDefaultedInt(env, "soundChance", operations::soundChance, operations::setSoundChance)
        registerFalseDefaultedInt(env, "soundSpeedTicks", operations::soundSpeedTicks, operations::setSoundSpeedTicks)

        // luaNpcTypeAddSound (npc_type_functions.cpp:653) reports false for a missing type.
        registry.registerMethod(NPC_TYPE_TYPE, "addSound", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            operations.addSound(npcType, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
        })

        // Mirrors the C++ stack behavior of luaNpcTypeGetSounds
        // (npc_type_functions.cpp:667-684): every iteration allocates a new inner table,
        // so only the last one is returned; with no sounds the empty outer table is
        // returned.
        registry.registerMethod(NPC_TYPE_TYPE, "getSounds", luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            val sounds = operations.getSounds(npcType) ?: return@luaFunction LuaValue.NIL
            var returned: LuaTable? = null
            sounds.forEachIndexed { index, sound ->
                returned = LuaTable().also { it.set(index + 1, LuaValue.valueOf(sound)) }
            }
            returned ?: LuaTable()
        })
    }

    private fun registerCallback(env: LuaEnvironment, name: String, operations: NpcTypeOperations) {
        env.userdata.registerMethod(NPC_TYPE_TYPE, name, luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            operations.loadCallback(npcType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
        })
    }

    private fun registerIntAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Int?,
        setter: (Any, Int) -> Boolean?,
    ) {
        env.userdata.registerMethod(NPC_TYPE_TYPE, name, luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(npcType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(npcType, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    private fun registerStringAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> String?,
        setter: (Any, String) -> Boolean?,
    ) {
        env.userdata.registerMethod(NPC_TYPE_TYPE, name, luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(npcType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(npcType, args.arg(2).tojstring())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    private fun registerBooleanAttribute(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Boolean?,
        setter: (Any, Boolean) -> Boolean?,
    ) {
        env.userdata.registerMethod(NPC_TYPE_TYPE, name, luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.NIL
            if (args.narg() == 1) {
                getter(npcType)?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            } else {
                setter(npcType, args.arg(2).toboolean())?.let { LuaValue.valueOf(it) } ?: LuaValue.NIL
            }
        })
    }

    /** Mirrors the integer attributes that report `false` (not `nil`) without a receiver. */
    private fun registerFalseDefaultedInt(
        env: LuaEnvironment,
        name: String,
        getter: (Any) -> Int?,
        setter: (Any, Int) -> Boolean?,
    ) {
        env.userdata.registerMethod(NPC_TYPE_TYPE, name, luaFunction { args ->
            val npcType = retrieveUserdata(args, 1) ?: return@luaFunction LuaValue.FALSE
            if (args.narg() == 1) {
                getter(npcType)?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
            } else {
                setter(npcType, args.arg(2).toint())?.let { LuaValue.valueOf(it) } ?: LuaValue.FALSE
            }
        })
    }

    /** Mirrors `Lua::pushOutfit` (src/lua/functions/lua_functions_loader.cpp:722). */
    private fun pushOutfit(outfit: Outfit): LuaTable =
        LuaTable().also { table ->
            table.set("lookType", LuaValue.valueOf(outfit.lookType))
            table.set("lookTypeEx", LuaValue.valueOf(outfit.lookTypeEx))
            table.set("lookHead", LuaValue.valueOf(outfit.lookHead))
            table.set("lookBody", LuaValue.valueOf(outfit.lookBody))
            table.set("lookLegs", LuaValue.valueOf(outfit.lookLegs))
            table.set("lookFeet", LuaValue.valueOf(outfit.lookFeet))
            table.set("lookAddons", LuaValue.valueOf(outfit.lookAddons))
            table.set("lookMount", LuaValue.valueOf(outfit.lookMount))
            table.set("lookMountHead", LuaValue.valueOf(outfit.lookMountHead))
            table.set("lookMountBody", LuaValue.valueOf(outfit.lookMountBody))
            table.set("lookMountLegs", LuaValue.valueOf(outfit.lookMountLegs))
            table.set("lookMountFeet", LuaValue.valueOf(outfit.lookMountFeet))
            table.set("lookFamiliarsType", LuaValue.valueOf(outfit.lookFamiliarsType))
            // The C++ keys carry trailing spaces (lua_functions_loader.cpp:735-737).
            table.set("lookWing ", LuaValue.valueOf(outfit.lookWing))
            table.set("lookAura", LuaValue.valueOf(outfit.lookAura))
            table.set("lookEffect ", LuaValue.valueOf(outfit.lookEffect))
            table.set("lookShader ", LuaValue.valueOf(outfit.lookShader))
        }

    /** Mirrors `Lua::getOutfit` (src/lua/functions/lua_functions_loader.cpp:439). */
    private fun luaToOutfit(value: LuaValue): Outfit? {
        if (!value.istable()) {
            return null
        }
        return Outfit(
            lookMountFeet = value.get("lookMountFeet").toint(),
            lookMountLegs = value.get("lookMountLegs").toint(),
            lookMountBody = value.get("lookMountBody").toint(),
            lookMountHead = value.get("lookMountHead").toint(),
            lookFamiliarsType = value.get("lookFamiliarsType").toint(),
            lookMount = value.get("lookMount").toint(),
            lookAddons = value.get("lookAddons").toint(),
            lookFeet = value.get("lookFeet").toint(),
            lookLegs = value.get("lookLegs").toint(),
            lookBody = value.get("lookBody").toint(),
            lookHead = value.get("lookHead").toint(),
            lookTypeEx = value.get("lookTypeEx").toint(),
            lookType = value.get("lookType").toint(),
            // The C++ reads lookShader into lookWing (lua_functions_loader.cpp:457).
            lookWing = value.get("lookShader").toint(),
            lookAura = value.get("lookAura").toint(),
            lookEffect = value.get("lookEffect").toint(),
            lookShader = value.get("lookShader").toint(),
        )
    }
}

/**
 * [NpcTypeOperations] seam backed by the Kotlin [NpcType] domain object plus the
 * optional `name -> NpcType` catalog for `NpcType(name)`.
 *
 * Parity gaps, all documented approximations:
 * - `create` does not register a missing type the way
 *   `g_npcs().getNpcType(name, true)` does; only the supplied catalog resolves;
 * - `setEventType` clamps an unknown numeric event to `NPCS_EVENT_NONE` because the
 *   Kotlin enum cannot hold Canary's unchecked `static_cast`;
 * - `setRespawnTypePeriod` clamps an unknown period to `RESPAWNPERIOD_ALL` for the
 *   same reason;
 * - `setOutfit` skips Canary's `WARN_UNSAFE_SCRIPTS`/`isLookTypeRegistered` gate
 *   (no look-type registry is wired);
 * - `loadCallback` inherits the not-found branch: the Lua callback registration stays
 *   with the event dispatcher (AGENTS.md 1.4);
 * - `addSound` maps an unknown `SoundEffect_t` to `SILENCE`.
 */
class WorldNpcTypeOperations(
    private val npcTypesByName: Map<String, Any> = emptyMap(),
    private val itemTypes: ItemTypeRegistry? = null,
) : NpcTypeOperations {

    override fun create(name: String): Any? = npcTypesByName[name]

    override fun isPushable(npcType: Any): Boolean? = (npcType as? NpcType)?.info?.pushable

    override fun setPushable(npcType: Any, value: Boolean): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.pushable = value
        return true
    }

    override fun floorChange(npcType: Any): Boolean? = (npcType as? NpcType)?.info?.floorChange

    override fun setFloorChange(npcType: Any, value: Boolean): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.floorChange = value
        return true
    }

    override fun canSpawn(npcType: Any, position: Position): Boolean? = (npcType as? NpcType)?.canSpawn(position)

    override fun canPushItems(npcType: Any): Boolean? = (npcType as? NpcType)?.info?.canPushItems

    override fun setCanPushItems(npcType: Any, value: Boolean): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.canPushItems = value
        return true
    }

    override fun canPushCreatures(npcType: Any): Boolean? = (npcType as? NpcType)?.info?.canPushCreatures

    override fun setCanPushCreatures(npcType: Any, value: Boolean): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.canPushCreatures = value
        return true
    }

    override fun name(npcType: Any): String? = (npcType as? NpcType)?.name

    override fun setName(npcType: Any, value: String): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.name = value
        return true
    }

    override fun nameDescription(npcType: Any): String? = (npcType as? NpcType)?.nameDescription

    override fun setNameDescription(npcType: Any, value: String): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.nameDescription = value
        return true
    }

    override fun health(npcType: Any): Int? = (npcType as? NpcType)?.info?.health

    override fun setHealth(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.health = value
        return true
    }

    override fun maxHealth(npcType: Any): Int? = (npcType as? NpcType)?.info?.healthMax

    override fun setMaxHealth(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.healthMax = value
        return true
    }

    override fun getVoices(npcType: Any): List<MonsterVoiceInfo>? {
        val target = npcType as? NpcType ?: return null
        return target.info.voiceVector.map { MonsterVoiceInfo(text = it.text, yellText = it.yellText) }
    }

    override fun addVoice(npcType: Any, text: String, interval: Int, chance: Int, yell: Boolean): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.yellSpeedTicks = interval
        target.info.yellChance = chance
        target.info.voiceVector += VoiceBlock(text = text, yellText = yell)
        return true
    }

    override fun getCreatureEvents(npcType: Any): List<String>? = (npcType as? NpcType)?.info?.scripts?.toList()

    override fun registerEvent(npcType: Any, name: String): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.scripts += name
        return true
    }

    override fun setEventType(npcType: Any, event: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.eventType = NpcsEvent.entries.firstOrNull { it.value == event } ?: NpcsEvent.NPCS_EVENT_NONE
        return true
    }

    override fun outfit(npcType: Any): Outfit? = (npcType as? NpcType)?.info?.outfit?.copy()

    override fun setOutfit(npcType: Any, outfit: Outfit): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.outfit = outfit.copy()
        return true
    }

    override fun baseSpeed(npcType: Any): Int? = (npcType as? NpcType)?.info?.baseSpeed

    override fun setBaseSpeed(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.baseSpeed = value
        return true
    }

    override fun walkInterval(npcType: Any): Int? = (npcType as? NpcType)?.info?.walkInterval

    override fun setWalkInterval(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.walkInterval = value
        return true
    }

    override fun walkRadius(npcType: Any): Int? = (npcType as? NpcType)?.info?.walkRadius

    override fun setWalkRadius(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.walkRadius = value
        return true
    }

    override fun light(npcType: Any): LightInfo? = (npcType as? NpcType)?.info?.light?.copy()

    override fun setLight(npcType: Any, color: Int, level: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.light.color = color
        target.info.light.level = level
        return true
    }

    override fun yellChance(npcType: Any): Int? = (npcType as? NpcType)?.info?.yellChance

    override fun setYellChance(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.yellChance = value
        return true
    }

    override fun yellSpeedTicks(npcType: Any): Int? = (npcType as? NpcType)?.info?.yellSpeedTicks

    override fun setYellSpeedTicks(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.yellSpeedTicks = value
        return true
    }

    override fun respawnTypePeriod(npcType: Any): Int? = (npcType as? NpcType)?.info?.respawnType?.period?.value

    override fun setRespawnTypePeriod(npcType: Any, period: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.respawnType.period = RespawnPeriod.entries.firstOrNull { it.value == period }
            ?: RespawnPeriod.RESPAWNPERIOD_ALL
        return true
    }

    override fun respawnTypeIsUnderground(npcType: Any): Int? {
        val target = npcType as? NpcType ?: return null
        return if (target.info.respawnType.underground) 1 else 0
    }

    override fun setRespawnTypeIsUnderground(npcType: Any, value: Boolean): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.respawnType.underground = value
        return true
    }

    override fun speechBubble(npcType: Any): Int? = (npcType as? NpcType)?.info?.speechBubble

    override fun setSpeechBubble(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.speechBubble = value
        return true
    }

    override fun currency(npcType: Any): Int? = (npcType as? NpcType)?.info?.currencyId

    override fun setCurrency(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.currencyId = value
        return true
    }

    override fun addShopItem(npcType: Any, shop: ShopData): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.loadShop(
            target,
            ShopBlock(
                itemId = shop.itemId,
                itemName = shop.itemName.ifEmpty { itemTypes?.get(shop.itemId)?.name ?: "" },
                itemSubType = shop.itemSubType,
                itemBuyPrice = shop.itemBuyPrice,
                itemSellPrice = shop.itemSellPrice,
                itemStorageKey = shop.itemStorageKey,
                itemStorageValue = shop.itemStorageValue,
            ),
        )
        return true
    }

    override fun soundChance(npcType: Any): Int? = (npcType as? NpcType)?.info?.soundChance

    override fun setSoundChance(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.soundChance = value
        return true
    }

    override fun soundSpeedTicks(npcType: Any): Int? = (npcType as? NpcType)?.info?.soundSpeedTicks

    override fun setSoundSpeedTicks(npcType: Any, value: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.soundSpeedTicks = value
        return true
    }

    override fun addSound(npcType: Any, soundId: Int): Boolean? {
        val target = npcType as? NpcType ?: return null
        target.info.soundVector += SoundEffect.entries.firstOrNull { it.value == soundId } ?: SoundEffect.SILENCE
        return true
    }

    override fun getSounds(npcType: Any): List<Int>? = (npcType as? NpcType)?.info?.soundVector?.map { it.value }
}
