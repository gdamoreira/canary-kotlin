package org.opentibia.canary.lua.bindings.library

import org.luaj.vm2.LuaValue
import org.luaj.vm2.Varargs
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.bindings.core.luaToPosition
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.Position
import kotlin.math.pow
import org.opentibia.canary.lua.bindings.core.pushPosition

/**
 * Port of Canary's `NetworkMessage` buffer
 * (`src/server/network/message/networkmessage.hpp:20-215`) covering the surface the
 * Lua class exposes: byte/u16/u32/u64/string/position reads, byte/u16/u32/u64/8/16/32/64/
 * string/position/double writes, [reset], [skipBytes] and [getUnreadBytes].
 *
 * The read and write cursors are the same `info.position` as the C++ class, the buffer
 * is staged behind the 8-byte header of `INITIAL_BUFFER_POSITION`, and
 * [getUnreadBytes] follows the C++ `readEndPosition != 0 ? ... : 8 + length` fallback.
 * `setPayloadLength` (used by the protocol decoder, not by Lua) is modeled internally.
 */
class NetworkMessage {
    private val buffer = ByteArray(NETWORKMESSAGE_MAXSIZE)
    private var position: Int = INITIAL_BUFFER_POSITION
    private var length: Int = 0
    private var readEndPosition: Int = 0
    private var overrun: Boolean = false

    /** `NetworkMessage::reset` (`networkmessage.hpp:32-34`). */
    fun reset() {
        position = INITIAL_BUFFER_POSITION
        length = 0
        readEndPosition = 0
        overrun = false
    }

    /** `NetworkMessage::getUnreadBytes` (`networkmessage.cpp:253-256`). */
    fun getUnreadBytes(): Long {
        val readEnd = if (readEndPosition != 0) readEndPosition else INITIAL_BUFFER_POSITION + length
        return if (readEnd > position) (readEnd - position).toLong() else 0L
    }

    /** `NetworkMessage::setPayloadLength` (`networkmessage.cpp:258-266`). */
    internal fun setPayloadLength(payloadLength: Int) {
        if (position > buffer.size || payloadLength > buffer.size - position) {
            overrun = true
            return
        }
        readEndPosition = position + payloadLength
    }

    /** `NetworkMessage::getByte` (`networkmessage.cpp:48-71`). */
    fun getByte(): Int = if (!canRead(1)) 0 else buffer[position++].toInt() and 0xFF

    /** `NetworkMessage::get<uint16_t>`. */
    fun getU16(): Int = readLittleEndian(2).toInt()

    /** `NetworkMessage::get<uint32_t>`. */
    fun getU32(): Long = readLittleEndian(4)

    /** `NetworkMessage::get<uint64_t>`. */
    fun getU64(): Long = readLittleEndian(8)

    /** `NetworkMessage::getString` (`networkmessage.cpp:91-112`). */
    fun getString(): String {
        if (!canRead(2)) {
            return ""
        }
        val stringLen = getU16()
        if (!canRead(stringLen)) {
            return ""
        }
        val result = String(buffer, position, stringLen, Charsets.ISO_8859_1)
        position += stringLen
        return result
    }

    /** `NetworkMessage::getPosition` (`networkmessage.cpp:114-120`). */
    fun getPosition(): Position = Position(getU16(), getU16(), getByte())

    /** `NetworkMessage::skipBytes` (`networkmessage.cpp:123-125`). */
    fun skipBytes(count: Int) {
        position += count
    }

    /** `NetworkMessage::addByte` (`networkmessage.cpp:190-197`). */
    fun addByte(value: Int): Boolean {
        if (!canAdd(1)) {
            return false
        }
        buffer[position++] = value.toByte()
        length++
        return true
    }

    /** `NetworkMessage::add<uint16_t>`. */
    fun addU16(value: Int) = writeLittleEndian(value.toLong(), 2)

    /** `NetworkMessage::add<uint32_t>`. */
    fun addU32(value: Long) = writeLittleEndian(value, 4)

    /** `NetworkMessage::add<uint64_t>`. */
    fun addU64(value: Long) = writeLittleEndian(value, 8)

    /** `NetworkMessage::add<int8_t>`. */
    fun add8(value: Int) = writeLittleEndian(value.toLong(), 1)

    /** `NetworkMessage::add<int16_t>`. */
    fun add16(value: Int) = writeLittleEndian(value.toLong(), 2)

    /** `NetworkMessage::add<int32_t>`. */
    fun add32(value: Int) = writeLittleEndian(value.toLong(), 4)

    /** `NetworkMessage::add<int64_t>`. */
    fun add64(value: Long) = writeLittleEndian(value, 8)

    /** `NetworkMessage::addString` (`networkmessage.cpp:127-172`). */
    fun addString(value: String): Boolean {
        val bytes = value.toByteArray(Charsets.ISO_8859_1)
        if (bytes.isEmpty()) {
            addU16(0)
            return true
        }
        if (!canAdd(bytes.size + 2)) {
            return false
        }
        addU16(bytes.size)
        bytes.copyInto(buffer, position)
        position += bytes.size
        length += bytes.size
        return true
    }

    /** `NetworkMessage::addPosition` (`networkmessage.cpp:174`). */
    fun addPosition(position: Position): Boolean {
        addU16(position.x)
        addU16(position.y)
        return addByte(position.z)
    }

    /** `NetworkMessage::addDouble` (`networkmessage.cpp:174-177`). */
    fun addDouble(value: Double, precision: Int = 4): Boolean {
        addByte(precision)
        val scaled = value * 10.0.pow(precision) + Int.MAX_VALUE.toDouble()
        return addU32(scaled.toLong())
    }

    /** `NetworkMessage::canAdd` (`networkmessage.cpp:298-300`). */
    private fun canAdd(size: Int): Boolean = size + position < NETWORKMESSAGE_MAXSIZE

    /** `NetworkMessage::canRead` through `getUnreadBytes` (`networkmessage.cpp:302-304`). */
    private fun canRead(size: Int): Boolean = size >= 0 && size.toLong() <= getUnreadBytes()

    private fun readLittleEndian(size: Int): Long {
        if (!canRead(size)) {
            return 0L
        }
        var value = 0L
        for (index in 0 until size) {
            value = value or ((buffer[position + index].toLong() and 0xFF) shl (8 * index))
        }
        position += size
        return value
    }

    private fun writeLittleEndian(value: Long, size: Int): Boolean {
        if (!canAdd(size)) {
            return false
        }
        for (index in 0 until size) {
            buffer[position + index] = (value ushr (8 * index)).toByte()
        }
        position += size
        length += size
        return true
    }

    companion object {
        /** `NetworkMessage::INITIAL_BUFFER_POSITION` (`networkmessage.hpp:28`). */
        const val INITIAL_BUFFER_POSITION: Int = 8

        /** `NETWORKMESSAGE_MAXSIZE` (`src/utils/const.hpp:16`). */
        const val NETWORKMESSAGE_MAXSIZE: Int = 65_500
    }
}

/**
 * Engine seam behind the `NetworkMessage` userdata class. Mirrors the two
 * player-touching methods of `NetworkMessageFunctions`
 * (src/lua/functions/core/network/network_message_functions.cpp:287-355):
 * `addItem` (`ProtocolGame::AddItem`) and `sendToPlayer`
 * (`Player::sendNetworkMessage`).
 */
interface NetworkMessageOperations {
    fun addItem(message: NetworkMessage, item: Item, player: Player): Boolean

    fun sendToPlayer(message: NetworkMessage, player: Player): Boolean

    fun reportError(message: String) {}
}

/** Documented no-op used when no client/protocol sink is wired. */
object UnwiredNetworkMessageOperations : NetworkMessageOperations {
    override fun addItem(message: NetworkMessage, item: Item, player: Player): Boolean = false

    override fun sendToPlayer(message: NetworkMessage, player: Player): Boolean = false
}

/**
 * Registers the `NetworkMessage` userdata class with every method of
 * `NetworkMessageFunctions::init` (network_message_functions.cpp:21-54):
 *
 *   NetworkMessage()
 *   networkMessage:getByte() / getU16() / getU32() / getU64() / getString() /
 *                    getPosition() / getUnreadBytes()
 *   networkMessage:addByte / addU16 / addU32 / addU64 / add8 / add16 / add32 / add64 /
 *                    addString(string[, function]) / addPosition(position) / addDouble(number) /
 *                    addItem(item, player)
 *   networkMessage:reset() / skipBytes(number) / sendToPlayer(player) / delete()
 *
 * Every missing-userdata branch reproduces the C++ `lua_pushnil`.
 */
object NetworkMessageFunctions {
    const val NETWORK_MESSAGE_TYPE = "NetworkMessage"

    fun install(env: LuaEnvironment, operations: NetworkMessageOperations = UnwiredNetworkMessageOperations) {
        env.userdata.registerClass(NETWORK_MESSAGE_TYPE, "", libraryFunction {
            env.userdata.wrap(NETWORK_MESSAGE_TYPE, NetworkMessage())
        })

        env.userdata.registerMetaMethod(NETWORK_MESSAGE_TYPE, "__eq", libraryFunction { args ->
            LuaValue.valueOf(args.arg(1) == args.arg(2))
        })

        env.userdata.registerMethod(NETWORK_MESSAGE_TYPE, "delete", libraryFunction { LuaValue.NIL })

        registerGetter(env, "getByte") { LuaValue.valueOf(it.getByte()) }
        registerGetter(env, "getU16") { LuaValue.valueOf(it.getU16()) }
        registerGetter(env, "getU32") { LuaValue.valueOf(it.getU32().toDouble()) }
        registerGetter(env, "getU64") { LuaValue.valueOf(it.getU64().toDouble()) }
        registerGetter(env, "getString") { LuaValue.valueOf(it.getString()) }
        registerGetter(env, "getUnreadBytes") { LuaValue.valueOf(it.getUnreadBytes().toDouble()) }
        registerGetter(env, "getPosition") { pushPosition(env, it.getPosition()) }

        registerWriter(env, "addByte") { message, args -> message.addByte(luaArgInt(args, 2)) }
        registerWriter(env, "addU16") { message, args -> message.addU16(luaArgInt(args, 2)) }
        registerWriter(env, "addU32") { message, args -> message.addU32(luaArgLong(args, 2)) }
        registerWriter(env, "addU64") { message, args -> message.addU64(luaArgLong(args, 2)) }
        registerWriter(env, "add8") { message, args -> message.add8(luaArgInt(args, 2)) }
        registerWriter(env, "add16") { message, args -> message.add16(luaArgInt(args, 2)) }
        registerWriter(env, "add32") { message, args -> message.add32(luaArgInt(args, 2)) }
        registerWriter(env, "add64") { message, args -> message.add64(luaArgLong(args, 2)) }
        registerWriter(env, "addString") { message, args -> message.addString(luaArgString(args, 2)) }
        registerWriter(env, "addPosition") { message, args ->
            val position = luaToPosition(args.arg(2)) ?: Position(0, 0, 0)
            message.addPosition(position)
        }
        registerWriter(env, "addDouble") { message, args -> message.addDouble(luaArgDouble(args, 2)) }

        env.userdata.registerMethod(NETWORK_MESSAGE_TYPE, "reset", libraryFunction { args ->
            val message = args.arg(1).touserdata() as? NetworkMessage ?: return@libraryFunction LuaValue.NIL
            message.reset()
            LuaValue.TRUE
        })

        env.userdata.registerMethod(NETWORK_MESSAGE_TYPE, "skipBytes", libraryFunction { args ->
            val message = args.arg(1).touserdata() as? NetworkMessage ?: return@libraryFunction LuaValue.NIL
            message.skipBytes(luaArgInt(args, 2))
            LuaValue.TRUE
        })

        env.userdata.registerMethod(NETWORK_MESSAGE_TYPE, "addItem", libraryFunction { args ->
            val item = args.arg(2).touserdata() as? Item
            if (item == null) {
                operations.reportError("Item not found")
                return@libraryFunction LuaValue.NIL
            }
            val player = args.arg(3).touserdata() as? Player
            if (player == null) {
                operations.reportError("Player not found")
                return@libraryFunction LuaValue.NIL
            }
            val message = args.arg(1).touserdata() as? NetworkMessage
                ?: return@libraryFunction LuaValue.NIL
            if (operations.addItem(message, item, player)) LuaValue.TRUE else LuaValue.NIL
        })

        env.userdata.registerMethod(NETWORK_MESSAGE_TYPE, "sendToPlayer", libraryFunction { args ->
            val message = args.arg(1).touserdata() as? NetworkMessage
                ?: return@libraryFunction LuaValue.NIL
            val player = args.arg(2).touserdata() as? Player
            if (player == null) {
                operations.reportError("Player not found")
                return@libraryFunction args.arg(1)
            }
            LuaValue.valueOf(operations.sendToPlayer(message, player))
        })
    }

    private fun registerGetter(env: LuaEnvironment, name: String, getter: (NetworkMessage) -> LuaValue) {
        env.userdata.registerMethod(NETWORK_MESSAGE_TYPE, name, libraryFunction { args ->
            val message = args.arg(1).touserdata() as? NetworkMessage ?: return@libraryFunction LuaValue.NIL
            getter(message)
        })
    }

    private fun registerWriter(
        env: LuaEnvironment,
        name: String,
        writer: (NetworkMessage, Varargs) -> Boolean,
    ) {
        env.userdata.registerMethod(NETWORK_MESSAGE_TYPE, name, libraryFunction { args ->
            val message = args.arg(1).touserdata() as? NetworkMessage ?: return@libraryFunction LuaValue.NIL
            writer(message, args)
            LuaValue.TRUE
        })
    }
}
