package org.opentibia.canary.systems.mount

/**
 * Mirror of Canary's `struct Mount` (src/creatures/appearance/mounts/mounts.hpp).
 *
 * Field parity (C++ -> Kotlin):
 *  - `uint8_t id`        -> [id] (validated 0..255 on load)
 *  - `uint16_t clientId` -> [clientId] (0..65535 on load)
 *  - `std::string name`  -> [name]
 *  - `int32_t speed`     -> [speed]
 *  - `bool premium`      -> [premium]
 *  - `std::string type`  -> [type]
 */
data class Mount(
    val id: Int,
    val clientId: Int,
    val name: String,
    val speed: Int,
    val premium: Boolean,
    val type: String
)
