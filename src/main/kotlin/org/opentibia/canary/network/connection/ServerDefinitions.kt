package org.opentibia.canary.network.connection

import org.opentibia.canary.network.protocol.CHECKSUM_LENGTH

/** `HEADER_LENGTH` (src/server/server_definitions.hpp:16). */
const val HEADER_LENGTH = 2

/** `XTEA_MULTIPLE` (src/server/server_definitions.hpp:18). */
const val XTEA_MULTIPLE = 8

/** `NETWORKMESSAGE_MAXSIZE` (src/utils/const.hpp:16). */
const val NETWORKMESSAGE_MAXSIZE = 65500

/** `INPUTMESSAGE_MAXSIZE` (src/utils/const.hpp:18). */
const val INPUTMESSAGE_MAXSIZE = 4096

/** `MAX_BODY_LENGTH` (src/server/server_definitions.hpp:19). */
const val MAX_BODY_LENGTH = NETWORKMESSAGE_MAXSIZE - HEADER_LENGTH - CHECKSUM_LENGTH - XTEA_MULTIPLE

/** `MAX_PROTOCOL_BODY_LENGTH` (src/server/server_definitions.hpp:20). */
const val MAX_PROTOCOL_BODY_LENGTH = MAX_BODY_LENGTH - 10

/** `ConnectionState_t` (src/server/server_definitions.hpp:23-28). */
enum class ConnectionState {
    Open,
    Identifying,
    Readings,
    Closed,
}

/**
 * `SessionEndInformations` (src/server/server_definitions.hpp:48-55): the reason
 * sent to the client through `ProtocolGame::sendSessionEndInformation`.
 */
enum class SessionEndInformations {
    Logout,
    Unk2,
    ForceClose,
    Unk3,
}
