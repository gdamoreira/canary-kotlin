package org.opentibia.canary.network.connection

import io.github.oshai.kotlinlogging.KotlinLogging
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.message.OutputMessage
import org.opentibia.canary.network.protocol.CHECKSUM_LENGTH
import org.opentibia.canary.network.protocol.TransportCodec
import org.opentibia.canary.network.protocol.TransportCodecs
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/** `InitialTransportState` (src/server/network/connection/connection.hpp:41-47). */
enum class InitialTransportState {
    Pending,
    ResolvedModernDefault,
    ResolvedFromPrelude,
    ResolvedFromHint,
    Rejected,
}

/**
 * Kotlin stand-in for the `std::error_code` values delivered to Canary's asio
 * read/write completions. `OperationAborted`, `EndOfFile` and `ConnectionReset`
 * are the cancellation/peer-close cases the C++ logging path suppresses.
 */
sealed interface ConnectionError {
    data object OperationAborted : ConnectionError

    data object EndOfFile : ConnectionError

    data object ConnectionReset : ConnectionError

    data class Failure(val message: String) : ConnectionError
}

/** Socket seam replacing asio's `tcp::socket` so the state machine needs no live sockets. */
interface ConnectionSocket {
    val isOpen: Boolean

    fun cancel()

    fun shutdown()

    fun close()
}

/**
 * Connection-facing subset of Canary's `Protocol` (src/server/network/protocol/protocol.hpp)
 * used by `connection.cpp`: accept notification, first/next message delivery and release.
 */
interface ConnectionProtocol {
    fun onConnectionAccepted()

    fun onRecvFirstMessage(message: ByteArray)

    /** Returns whether the read loop must skip reading the next packet header. */
    fun onRecvMessage(message: ByteArray): Boolean

    fun onSendMessage(outputMessage: OutputMessage)

    fun release()
}

/** `ServicePort::make_protocol` seam (src/server/server.cpp:125-137). */
fun interface ConnectionServicePort {
    fun makeProtocol(checksummed: Boolean, message: ByteArray, connection: Connection): ConnectionProtocol?
}

/**
 * Kotlin port of Canary's `Connection` state machine
 * (src/server/network/connection/connection.hpp/.cpp): connection states, transport
 * size limits, packet-rate limiting and graceful/forced close reasons.
 *
 * The asio event loop is replaced by seams: [ConnectionSocket] for the socket,
 * [postWorker] for `asio::post(internalWorker)`, [scheduleProtocolRelease] for the
 * dispatcher-bound `protocol->release()` task and [clock] for wall-clock seconds.
 * `internalSend` starts a write whose completion is delivered by
 * [onWriteOperation]; the Netty pipeline owns the actual async write.
 */
class Connection(
    internal val socket: ConnectionSocket,
    private val servicePort: ConnectionServicePort? = null,
    private val serverName: String = "",
    private val maxPacketsPerSecond: Int = DEFAULT_MAX_PACKETS_PER_SECOND,
    private val clock: () -> Long = { System.currentTimeMillis() / 1000 },
    private val manager: ConnectionManager? = null,
    private val postWorker: (() -> Unit) -> Unit = { it() },
    private val scheduleProtocolRelease: (ConnectionProtocol) -> Unit = { it.release() },
) {
    /** `nextConnectionId` (connection.cpp:35) plus the `connectionId` member. */
    val connectionId: Long = nextConnectionId.getAndIncrement()

    var connectionState: ConnectionState = ConnectionState.Open
        private set

    var receivedFirst: Boolean = false
        private set

    var forcedClose: Boolean = false
        private set

    var closeSource: String = "unknown"
        private set

    var firstReadError: ConnectionError? = null
        private set

    var protocol: ConnectionProtocol? = null
        internal set

    private var codec: TransportCodec = TransportCodecs.rawClientFirst()

    private var transportState: InitialTransportState = InitialTransportState.Pending

    val messageQueue = ArrayDeque<OutputMessage>()

    private var timeConnected: Long = clock()
    private var packetsSent: Long = 0

    /** `Connection::close` (connection.cpp:110-135). */
    fun close(force: Boolean = false, closeSource: String = "unknown") {
        manager?.releaseConnection(this)

        if (connectionState == ConnectionState.Closed) {
            if (force && socket.isOpen) {
                this.closeSource = closeSource
                forcedClose = true
                closeSocket()
            }
            return
        }

        connectionState = ConnectionState.Closed
        this.closeSource = closeSource
        forcedClose = force

        protocol?.let(scheduleProtocolRelease)

        if (messageQueue.isEmpty() || force) {
            closeSocket()
        }
    }

    /** `Connection::accept` (connection.cpp:201-211); read arming stays in the pipeline. */
    fun accept(protocol: ConnectionProtocol) {
        connectionState = ConnectionState.Identifying
        this.protocol = protocol
        protocol.onConnectionAccepted()

        if (connectionState == ConnectionState.Closed) {
            return
        }
    }

    /**
     * `Connection::parseProxyIdentification` (connection.cpp:231-286). Returns the
     * decoded body size when the proxy header falls through to [parseHeader].
     */
    fun parseProxyIdentification(error: ConnectionError?, header: ByteArray): Int? {
        if (error != null || connectionState == ConnectionState.Closed) {
            if (error != null && firstReadError == null) {
                firstReadError = error
            }
            if (error != null && !error.isCancellation()) {
                logger.debug { "[Connection::parseProxyIdentification] - Read error: $error" }
            }
            close(force = true)
            return null
        }

        val expected = "$serverName\n"
        if (connectionState == ConnectionState.Identifying) {
            if (header[1].toInt() == 0x00 || !header.matchesIgnoreCase(0, expected, 0, 2)) {
                connectionState = ConnectionState.Open
                return parseHeader(null, littleEndianU16(header))
            }

            val remainder = expected.length - 2
            if (remainder > 0) {
                connectionState = ConnectionState.Readings
                return null
            }
            connectionState = ConnectionState.Open
        } else if (connectionState == ConnectionState.Readings) {
            val remainder = expected.length - 2
            if (!header.matchesIgnoreCase(0, expected, 2, remainder)) {
                logger.error { "[Connection::parseProxyIdentification] Invalid Client Login! Server Name mismatch!" }
                close(force = true)
                return null
            }
            connectionState = ConnectionState.Open
        }

        return null
    }

    /**
     * `Connection::parseHeader` (connection.cpp:288-336): packet-rate limit, body
     * size decoding and the `INPUTMESSAGE_MAXSIZE` guard. Returns the body size to
     * read, or `null` when the connection was closed.
     */
    fun parseHeader(error: ConnectionError?, lengthHeader: Int): Int? {
        if (error != null) {
            if (firstReadError == null) {
                firstReadError = error
            }
            if (!error.isCancellation()) {
                logger.debug { "[Connection::parseHeader] - Read error: $error" }
            }
            close(force = true)
            return null
        } else if (connectionState == ConnectionState.Closed) {
            return null
        }

        val now = clock()
        val timePassed = maxOf(1L, now - timeConnected + 1)
        packetsSent++
        if (packetsSent / timePassed > maxPacketsPerSecond.toLong()) {
            logger.warn { "[Connection::parseHeader] - disconnected for exceeding packet per second limit." }
            close()
            return null
        }

        if (timePassed > 2) {
            timeConnected = now
            packetsSent = 0
        }

        val size = codec.decodeBodySize(lengthHeader)
        if (size == null || size > INPUTMESSAGE_MAXSIZE) {
            close(force = true)
            return null
        }

        return size
    }

    /**
     * `Connection::parsePacket` (connection.cpp:338-402): first-message checksum
     * validation/protocol creation and delivery to the active protocol. Returns
     * whether the read loop must skip the next packet header.
     */
    fun parsePacket(error: ConnectionError?, body: ByteArray = ByteArray(0)): Boolean {
        if (error != null || connectionState == ConnectionState.Closed) {
            if (error != null) {
                if (firstReadError == null) {
                    firstReadError = error
                }
                logger.debug { "[Connection::parsePacket] - Read error: $error" }
            }
            close(force = true)
            return false
        }

        var skipReadingNextPacket = false
        if (!receivedFirst) {
            receivedFirst = true

            var offset = 0
            if (protocol == null) {
                val checksumLength = body.size - CHECKSUM_LENGTH
                val checksum = if (checksumLength > 0) {
                    Adler32Checksum.compute(body, CHECKSUM_LENGTH, checksumLength)
                } else {
                    0L
                }
                val receivedChecksum = readUInt32LE(body, 0)
                val checksumValid = receivedChecksum == checksum
                if (checksumValid) {
                    offset = CHECKSUM_LENGTH
                }

                val created = servicePort?.makeProtocol(
                    checksumValid,
                    body.copyOfRange(offset, body.size),
                    this,
                )
                if (created == null) {
                    close(force = true)
                    return false
                }
                protocol = created
            } else {
                offset = codec.getProfile().serverFirstPacketHeaderBytes
            }

            protocol?.onRecvFirstMessage(body.copyOfRange(offset.coerceAtMost(body.size), body.size))
        } else {
            skipReadingNextPacket = protocol?.onRecvMessage(body) ?: false
        }

        return skipReadingNextPacket
    }

    /** `Connection::send` (connection.cpp:416-438). */
    fun send(outputMessage: OutputMessage) {
        if (connectionState == ConnectionState.Closed) {
            return
        }

        val noPendingWrite = messageQueue.isEmpty()
        messageQueue.addLast(outputMessage)

        if (noPendingWrite) {
            if (socket.isOpen) {
                postWorker { internalWorker() }
            } else {
                logger.error { "[Connection::send] - Socket is not open for writing." }
                close(force = true)
            }
        }
    }

    /** `Connection::internalWorker` (connection.cpp:440-460). */
    internal fun internalWorker() {
        if (!socket.isOpen) {
            messageQueue.clear()
            return
        }

        if (messageQueue.isEmpty()) {
            if (connectionState == ConnectionState.Closed) {
                closeSocket()
            }
            return
        }

        val outputMessage = messageQueue.first()
        protocol?.onSendMessage(outputMessage)
        internalSend(outputMessage)
    }

    /** `Connection::internalSend` (connection.cpp:504-524); the write completes via [onWriteOperation]. */
    internal fun internalSend(outputMessage: OutputMessage) {
        if (!socket.isOpen) {
            messageQueue.clear()
            return
        }
    }

    /** `Connection::onWriteOperation` (connection.cpp:549-576). */
    internal fun onWriteOperation(
        error: ConnectionError?,
        bytesTransferred: Int,
        requestedBytes: Int,
        socketOpenAtStart: Boolean,
    ) {
        if (error != null) {
            logWriteError(error, bytesTransferred, requestedBytes, socketOpenAtStart)
            messageQueue.clear()
            close(force = true)
            return
        }

        if (!socket.isOpen) {
            messageQueue.clear()
            return
        }

        messageQueue.removeFirst()

        if (messageQueue.isNotEmpty()) {
            val outputMessage = messageQueue.first()
            protocol?.onSendMessage(outputMessage)
            internalSend(outputMessage)
        } else if (connectionState == ConnectionState.Closed) {
            closeSocket()
        }
    }

    fun setTransportCodec(codec: TransportCodec, state: InitialTransportState = InitialTransportState.ResolvedModernDefault) {
        this.codec = codec
        transportState = state
    }

    fun setInitialTransportState(state: InitialTransportState) {
        transportState = state
    }

    fun getTransportCodec(): TransportCodec = codec

    fun getInitialTransportState(): InitialTransportState = transportState

    /** `Connection::logWriteError` (connection.cpp:526-547); warning suppression stays in the dispatcher policy. */
    private fun logWriteError(error: ConnectionError, bytesTransferred: Int, requestedBytes: Int, socketOpenAtStart: Boolean) {
        val message = "[Connection::onWriteOperation] - Write error: $error; connection_id=$connectionId, " +
            "state=$connectionState, received_first=$receivedFirst, socket_open_at_write=$socketOpenAtStart, " +
            "socket_open_now=${socket.isOpen}, bytes_written=$bytesTransferred/$requestedBytes, " +
            "queued_messages=${messageQueue.size}, close_requested_by=$closeSource, forced_close=$forcedClose, " +
            "first_read_error=$firstReadError"
        if (error is ConnectionError.OperationAborted) {
            logger.debug { message }
        } else {
            logger.warn { message }
        }
    }

    /** `Connection::closeSocket` (connection.cpp:176-199). */
    private fun closeSocket() {
        if (!socket.isOpen) {
            return
        }

        try {
            socket.cancel()
            socket.shutdown()
            socket.close()
        } catch (e: Exception) {
            logger.error(e) { "[Connection::closeSocket] - error closeSocket" }
        }
    }

    companion object {
        /** `maxPacketsPerSecond` default (src/config/configmanager.cpp:316). */
        const val DEFAULT_MAX_PACKETS_PER_SECOND = 25

        private val nextConnectionId = AtomicLong(1)
    }
}

/**
 * Kotlin port of Canary's `ConnectionManager`
 * (src/server/network/connection/connection.hpp:49-61): owns the live connection
 * registry and the `closeAll` stop-parity path used during server shutdown.
 *
 * The C++ singleton `getInstance()` is replaced by constructor injection
 * (AGENTS.md §1.1); [socketFactory] supplies the per-connection socket seam.
 */
class ConnectionManager(
    private val socketFactory: () -> ConnectionSocket,
) {
    private val connections: MutableSet<Connection> = ConcurrentHashMap.newKeySet()

    val size: Int get() = connections.size

    fun createConnection(
        servicePort: ConnectionServicePort? = null,
        socket: ConnectionSocket = socketFactory(),
        serverName: String = "",
        maxPacketsPerSecond: Int = Connection.DEFAULT_MAX_PACKETS_PER_SECOND,
        clock: () -> Long = { System.currentTimeMillis() / 1000 },
        postWorker: (() -> Unit) -> Unit = { it() },
        scheduleProtocolRelease: (ConnectionProtocol) -> Unit = { it.release() },
    ): Connection {
        val connection = Connection(
            socket = socket,
            servicePort = servicePort,
            serverName = serverName,
            maxPacketsPerSecond = maxPacketsPerSecond,
            clock = clock,
            manager = this,
            postWorker = postWorker,
            scheduleProtocolRelease = scheduleProtocolRelease,
        )
        connections.add(connection)
        return connection
    }

    /** `ConnectionManager::releaseConnection` (connection.cpp:78-80). */
    fun releaseConnection(connection: Connection) {
        connections.remove(connection)
    }

    /** `ConnectionManager::closeAll` (connection.cpp:82-98): shutdown every open socket, then clear. */
    fun closeAll() {
        for (connection in connections) {
            if (connection.socket.isOpen) {
                try {
                    connection.socket.shutdown()
                } catch (e: Exception) {
                    logger.error(e) { "[ConnectionManager::closeAll] - Failed to close connection" }
                }
            }
        }
        connections.clear()
    }
}

private fun ConnectionError.isCancellation(): Boolean =
    this is ConnectionError.OperationAborted ||
        this is ConnectionError.EndOfFile ||
        this is ConnectionError.ConnectionReset

private fun ByteArray.matchesIgnoreCase(sourceOffset: Int, target: String, targetOffset: Int, length: Int): Boolean {
    if (sourceOffset + length > size || targetOffset + length > target.length) {
        return false
    }

    for (index in 0 until length) {
        val sourceChar = (this[sourceOffset + index].toInt() and 0xFF).toChar()
        val targetChar = target[targetOffset + index]
        if (sourceChar.lowercaseChar() != targetChar.lowercaseChar()) {
            return false
        }
    }
    return true
}

private fun littleEndianU16(bytes: ByteArray): Int =
    (bytes[0].toInt() and 0xFF) or ((bytes[1].toInt() and 0xFF) shl 8)

private fun readUInt32LE(bytes: ByteArray, offset: Int): Long {
    if (offset + 4 > bytes.size) {
        return 0L
    }
    return (bytes[offset].toLong() and 0xFF) or
        ((bytes[offset + 1].toLong() and 0xFF) shl 8) or
        ((bytes[offset + 2].toLong() and 0xFF) shl 16) or
        ((bytes[offset + 3].toLong() and 0xFF) shl 24)
}
