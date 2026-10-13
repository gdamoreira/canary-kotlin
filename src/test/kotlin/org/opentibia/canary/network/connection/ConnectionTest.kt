package org.opentibia.canary.network.connection

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.network.crypto.Adler32Checksum
import org.opentibia.canary.network.message.OutputMessage
import org.opentibia.canary.network.protocol.CHECKSUM_LENGTH
import org.opentibia.canary.network.protocol.TransportCodecs
import org.opentibia.canary.network.protocol.TransportProfileId

/**
 * Transcript of Canary `tests/unit/server/network/connection/connection_write_diagnostics_test.cpp`
 * plus the connection state machine, packet-rate, size-limit and close-reason
 * coverage from `src/server/network/connection/connection.cpp`.
 *
 * C++ sources: src/server/network/connection/connection.hpp/.cpp,
 * src/server/server_definitions.hpp, src/server/network/protocol/transport_codec.cpp
 */
class ConnectionTest {

    private class RecordingSocket(var open: Boolean = true) : ConnectionSocket {
        var cancelCalls = 0
        var shutdownCalls = 0
        var closeCalls = 0

        override val isOpen: Boolean get() = open

        override fun cancel() {
            cancelCalls++
        }

        override fun shutdown() {
            shutdownCalls++
        }

        override fun close() {
            closeCalls++
            open = false
        }
    }

    private class RecordingProtocol : ConnectionProtocol {
        var accepted = 0
        var releaseCount = 0
        var preparedMessages = 0
        var closeOnPreparation = 0
        var skipReadingNextPacket = false
        var connection: Connection? = null
        val firstMessages = mutableListOf<ByteArray>()
        val receivedMessages = mutableListOf<ByteArray>()

        override fun onConnectionAccepted() {
            accepted++
        }

        override fun onRecvFirstMessage(message: ByteArray) {
            firstMessages += message
        }

        override fun onRecvMessage(message: ByteArray): Boolean {
            receivedMessages += message
            return skipReadingNextPacket
        }

        override fun onSendMessage(outputMessage: OutputMessage) {
            preparedMessages++
            if (preparedMessages == closeOnPreparation) {
                connection?.close(force = true)
            }
        }

        override fun release() {
            releaseCount++
        }
    }

    private fun outputMessage(): OutputMessage = OutputMessage(byteArrayOf(0x31, 0x72, 0xA5.toByte()))

    private fun checksumPrefix(payload: ByteArray): ByteArray {
        val checksum = Adler32Checksum.compute(payload, 0, payload.size)
        return byteArrayOf(
            (checksum and 0xFF).toByte(),
            ((checksum shr 8) and 0xFF).toByte(),
            ((checksum shr 16) and 0xFF).toByte(),
            ((checksum shr 24) and 0xFF).toByte(),
        )
    }

    private fun connection(
        socket: RecordingSocket = RecordingSocket(),
        servicePort: ConnectionServicePort? = null,
        serverName: String = "Canary",
        maxPacketsPerSecond: Int = 25,
        clock: () -> Long = { 0L },
        manager: ConnectionManager? = null,
        postWorker: (() -> Unit) -> Unit = { it() },
        scheduleProtocolRelease: (ConnectionProtocol) -> Unit = { it.release() },
    ): Connection = Connection(
        socket = socket,
        servicePort = servicePort,
        serverName = serverName,
        maxPacketsPerSecond = maxPacketsPerSecond,
        clock = clock,
        manager = manager,
        postWorker = postWorker,
        scheduleProtocolRelease = scheduleProtocolRelease,
    )

    @Test
    fun `initial state is open and idle`() {
        val c = connection()

        assertThat(c.connectionState).isEqualTo(ConnectionState.Open)
        assertThat(c.receivedFirst).isFalse()
        assertThat(c.forcedClose).isFalse()
        assertThat(c.firstReadError).isNull()
        assertThat(c.getInitialTransportState()).isEqualTo(InitialTransportState.Pending)
        assertThat(c.getTransportCodec().getProfile().id).isEqualTo(TransportProfileId.RawClientFirst)
        assertThat(c.messageQueue).isEmpty()
    }

    @Test
    fun `connection ids are unique`() {
        val manager = ConnectionManager { RecordingSocket() }
        val first = manager.createConnection()
        val second = manager.createConnection()

        assertThat(first.connectionId).isNotEqualTo(second.connectionId)
    }

    @Test
    fun `accept moves to identifying and notifies the protocol`() {
        val c = connection()
        val protocol = RecordingProtocol()

        c.accept(protocol)

        assertThat(c.connectionState).isEqualTo(ConnectionState.Identifying)
        assertThat(c.protocol).isSameAs(protocol)
        assertThat(protocol.accepted).isEqualTo(1)
    }

    @Test
    fun `accept stops when the protocol closes during accept`() {
        val c = connection()
        val protocol = object : ConnectionProtocol {
            override fun onConnectionAccepted() {
                c.close(force = true)
            }

            override fun onRecvFirstMessage(message: ByteArray) = Unit

            override fun onRecvMessage(message: ByteArray): Boolean = false

            override fun onSendMessage(outputMessage: OutputMessage) = Unit

            override fun release() = Unit
        }

        c.accept(protocol)

        assertThat(c.connectionState).isEqualTo(ConnectionState.Closed)
        assertThat(c.forcedClose).isTrue()
    }

    @Test
    fun `non proxy header opens the connection and parses it as a length header`() {
        val c = connection(serverName = "Canary")
        c.accept(RecordingProtocol())

        val bodySize = c.parseProxyIdentification(null, byteArrayOf(10, 0))

        assertThat(c.connectionState).isEqualTo(ConnectionState.Open)
        assertThat(bodySize).isEqualTo(10)
    }

    @Test
    fun `proxy prefix transitions to readings until the server name completes`() {
        val c = connection(serverName = "Canary")
        c.accept(RecordingProtocol())

        val pending = c.parseProxyIdentification(null, byteArrayOf('C'.code.toByte(), 'a'.code.toByte()))
        assertThat(c.connectionState).isEqualTo(ConnectionState.Readings)
        assertThat(pending).isNull()

        c.parseProxyIdentification(null, "nary\n".toByteArray())
        assertThat(c.connectionState).isEqualTo(ConnectionState.Open)
    }

    @Test
    fun `proxy remainder mismatch force closes the connection`() {
        val c = connection(serverName = "Canary")
        c.accept(RecordingProtocol())
        c.parseProxyIdentification(null, byteArrayOf('C'.code.toByte(), 'a'.code.toByte()))

        c.parseProxyIdentification(null, "XXXX\n".toByteArray())

        assertThat(c.connectionState).isEqualTo(ConnectionState.Closed)
        assertThat(c.forcedClose).isTrue()
    }

    @Test
    fun `proxy header with an empty remainder opens immediately`() {
        val c = connection(serverName = "A")
        c.accept(RecordingProtocol())

        c.parseProxyIdentification(null, byteArrayOf('A'.code.toByte(), '\n'.code.toByte()))

        assertThat(c.connectionState).isEqualTo(ConnectionState.Open)
    }

    @Test
    fun `proxy read error survives the cancellation callback`() {
        val c = connection()
        c.accept(RecordingProtocol())

        c.parseProxyIdentification(ConnectionError.EndOfFile, byteArrayOf(0, 0))
        c.parseProxyIdentification(ConnectionError.OperationAborted, byteArrayOf(0, 0))

        assertThat(c.firstReadError).isEqualTo(ConnectionError.EndOfFile)
        assertThat(c.connectionState).isEqualTo(ConnectionState.Closed)
    }

    @Test
    fun `header decodes the body size for the active transport`() {
        val c = connection()

        assertThat(c.parseHeader(null, 100)).isEqualTo(100)

        c.setTransportCodec(TransportCodecs.currentModern())
        assertThat(c.parseHeader(null, 1)).isEqualTo(XTEA_MULTIPLE + CHECKSUM_LENGTH)
    }

    @Test
    fun `zero body length force closes`() {
        val c = connection()

        assertThat(c.parseHeader(null, 0)).isNull()
        assertThat(c.connectionState).isEqualTo(ConnectionState.Closed)
        assertThat(c.forcedClose).isTrue()
    }

    @Test
    fun `body length above the input message maximum force closes`() {
        val c = connection()

        assertThat(c.parseHeader(null, INPUTMESSAGE_MAXSIZE + 1)).isNull()
        assertThat(c.connectionState).isEqualTo(ConnectionState.Closed)
        assertThat(c.forcedClose).isTrue()
    }

    @Test
    fun `body length at the input message maximum is accepted`() {
        val c = connection()

        assertThat(c.parseHeader(null, INPUTMESSAGE_MAXSIZE)).isEqualTo(INPUTMESSAGE_MAXSIZE)
        assertThat(c.connectionState).isEqualTo(ConnectionState.Open)
    }

    @Test
    fun `header read error force closes and retains the first error`() {
        val c = connection()

        c.parseHeader(ConnectionError.Failure("boom"), 10)
        c.parseHeader(ConnectionError.OperationAborted, 10)

        assertThat(c.firstReadError).isEqualTo(ConnectionError.Failure("boom"))
        assertThat(c.forcedClose).isTrue()
    }

    @Test
    fun `packet rate above the configured maximum triggers a graceful close`() {
        val socket = RecordingSocket()
        val c = connection(socket = socket, maxPacketsPerSecond = 1)

        assertThat(c.parseHeader(null, 10)).isEqualTo(10)
        assertThat(c.parseHeader(null, 10)).isNull()

        assertThat(c.connectionState).isEqualTo(ConnectionState.Closed)
        assertThat(c.forcedClose).isFalse()
        assertThat(socket.isOpen).isFalse()
    }

    @Test
    fun `packet rate counters reset after a quiet window`() {
        var now = 0L
        val c = connection(maxPacketsPerSecond = 1, clock = { now })

        assertThat(c.parseHeader(null, 10)).isEqualTo(10)
        now = 3
        assertThat(c.parseHeader(null, 10)).isEqualTo(10)
        assertThat(c.parseHeader(null, 10)).isEqualTo(10)
        assertThat(c.connectionState).isEqualTo(ConnectionState.Open)
    }

    @Test
    fun `first packet validates the checksum and creates the protocol`() {
        var checksummed: Boolean? = null
        var received: ByteArray? = null
        val port = ConnectionServicePort { flag, message, _ ->
            checksummed = flag
            received = message
            RecordingProtocol()
        }
        val c = connection(servicePort = port)
        val payload = byteArrayOf(1, 2, 3)
        val body = checksumPrefix(payload) + payload

        val skipReadingNextPacket = c.parsePacket(null, body)

        assertThat(skipReadingNextPacket).isFalse()
        assertThat(c.receivedFirst).isTrue()
        assertThat(checksummed).isTrue()
        assertThat(received).isEqualTo(payload)
        assertThat(c.protocol).isNotNull()
    }

    @Test
    fun `checksum mismatch hands the raw body to the protocol`() {
        var checksummed: Boolean? = null
        var received: ByteArray? = null
        val port = ConnectionServicePort { flag, message, _ ->
            checksummed = flag
            received = message
            RecordingProtocol()
        }
        val c = connection(servicePort = port)
        val body = byteArrayOf(9, 9, 9, 9, 1, 2, 3)

        c.parsePacket(null, body)

        assertThat(checksummed).isFalse()
        assertThat(received).isEqualTo(body)
    }

    @Test
    fun `missing protocol factory force closes the first packet`() {
        val c = connection(servicePort = null)

        val skipReadingNextPacket = c.parsePacket(null, byteArrayOf(0, 0, 0, 0))

        assertThat(skipReadingNextPacket).isFalse()
        assertThat(c.connectionState).isEqualTo(ConnectionState.Closed)
        assertThat(c.forcedClose).isTrue()
    }

    @Test
    fun `later packets are delivered to the protocol and honor the skip flag`() {
        val protocol = RecordingProtocol()
        val port = ConnectionServicePort { _, _, _ -> protocol }
        val c = connection(servicePort = port)
        c.parsePacket(null, byteArrayOf(0, 0, 0, 0))

        protocol.skipReadingNextPacket = true
        val skipReadingNextPacket = c.parsePacket(null, byteArrayOf(7))

        assertThat(skipReadingNextPacket).isTrue()
        assertThat(protocol.receivedMessages.single()).isEqualTo(byteArrayOf(7))
    }

    @Test
    fun `first message skips the server first packet header bytes`() {
        val protocol = RecordingProtocol()
        val c = connection()
        c.protocol = protocol
        c.setTransportCodec(TransportCodecs.legacyClassic())

        c.parsePacket(null, byteArrayOf(1, 2, 3, 4, 5, 6, 7))

        assertThat(protocol.firstMessages.single()).isEqualTo(byteArrayOf(6, 7))
    }

    @Test
    fun `packet read error retains the first error and force closes`() {
        val c = connection()

        c.parsePacket(ConnectionError.ConnectionReset, byteArrayOf())
        c.parsePacket(ConnectionError.OperationAborted, byteArrayOf())

        assertThat(c.firstReadError).isEqualTo(ConnectionError.ConnectionReset)
        assertThat(c.forcedClose).isTrue()
    }

    @Test
    fun `successful write drains the queue and keeps the socket open`() {
        val socket = RecordingSocket()
        val c = connection(socket = socket)
        val message = outputMessage()
        c.messageQueue.addLast(message)

        c.internalSend(message)
        c.onWriteOperation(null, 3, 3, true)

        assertThat(c.messageQueue).isEmpty()
        assertThat(socket.isOpen).isTrue()
    }

    @Test
    fun `graceful close drains the queued write before closing the socket`() {
        val socket = RecordingSocket()
        val c = connection(socket = socket)
        val message = outputMessage()
        c.messageQueue.addLast(message)

        c.close()

        assertThat(c.connectionState).isEqualTo(ConnectionState.Closed)
        assertThat(c.forcedClose).isFalse()
        assertThat(socket.isOpen).isTrue()

        c.internalSend(message)
        c.onWriteOperation(null, 3, 3, true)

        assertThat(c.messageQueue).isEmpty()
        assertThat(socket.isOpen).isFalse()
    }

    @Test
    fun `write failure clears the queue and force closes`() {
        val socket = RecordingSocket()
        val c = connection(socket = socket)
        c.messageQueue.addLast(outputMessage())
        c.close(force = true)
        assertThat(socket.isOpen).isFalse()

        c.onWriteOperation(ConnectionError.Failure("bad descriptor"), 0, 3, true)

        assertThat(c.messageQueue).isEmpty()
        assertThat(c.forcedClose).isTrue()
    }

    @Test
    fun `aborted write clears the queue and closes`() {
        val socket = RecordingSocket()
        val c = connection(socket = socket)
        c.messageQueue.addLast(outputMessage())

        c.onWriteOperation(ConnectionError.OperationAborted, 1, 3, true)

        assertThat(c.messageQueue).isEmpty()
        assertThat(socket.isOpen).isFalse()
    }

    @Test
    fun `queued send cancelled before the worker does not prepare or write`() {
        val socket = RecordingSocket()
        val protocol = RecordingProtocol()
        val workerTasks = mutableListOf<() -> Unit>()
        val c = connection(socket = socket, postWorker = { workerTasks += it })
        c.protocol = protocol

        c.send(outputMessage())
        c.close(force = true)
        workerTasks.single().invoke()

        assertThat(protocol.preparedMessages).isEqualTo(0)
        assertThat(c.messageQueue).isEmpty()
        assertThat(socket.isOpen).isFalse()
    }

    @Test
    fun `close during preparation does not start the write`() {
        val socket = RecordingSocket()
        val protocol = RecordingProtocol()
        val c = connection(socket = socket)
        protocol.connection = c
        protocol.closeOnPreparation = 1
        c.protocol = protocol

        c.send(outputMessage())

        assertThat(protocol.preparedMessages).isEqualTo(1)
        assertThat(socket.isOpen).isFalse()
        assertThat(c.messageQueue).isEmpty()
    }

    @Test
    fun `close during the next preparation stops the second write`() {
        val socket = RecordingSocket()
        val protocol = RecordingProtocol()
        val c = connection(socket = socket)
        protocol.connection = c
        protocol.closeOnPreparation = 2
        c.protocol = protocol

        c.send(outputMessage())
        c.send(outputMessage())
        assertThat(protocol.preparedMessages).isEqualTo(1)

        c.onWriteOperation(null, 3, 3, true)

        assertThat(protocol.preparedMessages).isEqualTo(2)
        assertThat(socket.isOpen).isFalse()
        assertThat(c.messageQueue).isEmpty()
    }

    @Test
    fun `successful completion after force close does not prepare the next message`() {
        val socket = RecordingSocket()
        val protocol = RecordingProtocol()
        val c = connection(socket = socket)
        c.protocol = protocol
        c.messageQueue.addLast(outputMessage())
        c.messageQueue.addLast(outputMessage())
        c.close(force = true)

        c.onWriteOperation(null, 3, 3, true)

        assertThat(protocol.preparedMessages).isEqualTo(0)
        assertThat(c.messageQueue).isEmpty()
        assertThat(socket.isOpen).isFalse()
    }

    @Test
    fun `graceful close can escalate to forced close and releases the protocol once`() {
        val socket = RecordingSocket()
        val protocol = RecordingProtocol()
        val releaseTasks = mutableListOf<ConnectionProtocol>()
        val c = connection(socket = socket, scheduleProtocolRelease = { releaseTasks += it })
        c.protocol = protocol

        c.send(outputMessage())
        c.close()

        assertThat(socket.isOpen).isTrue()
        assertThat(releaseTasks).hasSize(1)

        c.close(force = true)

        assertThat(socket.isOpen).isFalse()
        assertThat(c.forcedClose).isTrue()
        assertThat(releaseTasks).hasSize(1)

        releaseTasks.forEach { it.release() }
        assertThat(protocol.releaseCount).isEqualTo(1)
    }

    @Test
    fun `send on a closed socket force closes the connection`() {
        val socket = RecordingSocket(open = false)
        val c = connection(socket = socket)

        c.send(outputMessage())

        assertThat(c.connectionState).isEqualTo(ConnectionState.Closed)
        assertThat(c.forcedClose).isTrue()
    }

    @Test
    fun `transport codec and initial state are injectable`() {
        val c = connection()

        c.setTransportCodec(TransportCodecs.legacyClassic(), InitialTransportState.ResolvedFromHint)

        assertThat(c.getTransportCodec().getProfile().id).isEqualTo(TransportProfileId.LegacyClassic)
        assertThat(c.getInitialTransportState()).isEqualTo(InitialTransportState.ResolvedFromHint)

        c.setInitialTransportState(InitialTransportState.ResolvedFromPrelude)
        assertThat(c.getInitialTransportState()).isEqualTo(InitialTransportState.ResolvedFromPrelude)
    }

    @Test
    fun `manager tracks created connections and releases them on close`() {
        val manager = ConnectionManager { RecordingSocket() }
        val c = manager.createConnection()
        assertThat(manager.size).isEqualTo(1)

        c.close()

        assertThat(manager.size).isEqualTo(0)
    }

    @Test
    fun `closeAll shuts down open sockets and clears the registry`() {
        val first = RecordingSocket()
        val second = RecordingSocket()
        val manager = ConnectionManager { RecordingSocket() }
        manager.createConnection(socket = first)
        manager.createConnection(socket = second)

        manager.closeAll()

        assertThat(first.shutdownCalls).isEqualTo(1)
        assertThat(second.shutdownCalls).isEqualTo(1)
        assertThat(manager.size).isEqualTo(0)
    }

    @Test
    fun `session end information ordinals match canary`() {
        assertThat(SessionEndInformations.Logout.ordinal).isEqualTo(0)
        assertThat(SessionEndInformations.Unk2.ordinal).isEqualTo(1)
        assertThat(SessionEndInformations.ForceClose.ordinal).isEqualTo(2)
        assertThat(SessionEndInformations.Unk3.ordinal).isEqualTo(3)
    }

    @Test
    fun `connection size limits match the server definitions`() {
        assertThat(HEADER_LENGTH).isEqualTo(2)
        assertThat(CHECKSUM_LENGTH).isEqualTo(4)
        assertThat(XTEA_MULTIPLE).isEqualTo(8)
        assertThat(NETWORKMESSAGE_MAXSIZE).isEqualTo(65500)
        assertThat(INPUTMESSAGE_MAXSIZE).isEqualTo(4096)
        assertThat(MAX_BODY_LENGTH).isEqualTo(65486)
        assertThat(MAX_PROTOCOL_BODY_LENGTH).isEqualTo(65476)
    }
}
