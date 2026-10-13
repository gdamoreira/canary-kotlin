package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf

/**
 * Outbound store/resource balance serialization, ported from Canary
 * src/server/network/protocol/protocolgame.cpp:
 *
 *  - `ProtocolGame::sendResourcesBalance`      (protocolgame.cpp:6610-6626)
 *  - `ProtocolGame::sendResourceBalance`       (0xEE, protocolgame.cpp:6628-6646)
 *  - `ProtocolGame::sendCharmResourcesBalance` (protocolgame.cpp:6648-6653)
 *  - `ProtocolGame::sendCharmResourceBalance`  (0xEE, protocolgame.cpp:6655-6667)
 *  - `ProtocolGame::sendCoinBalance`           (0xF2/0xDF, protocolgame.cpp:6803-6829)
 *  - `ProtocolGame::sendSpecialContainersAvailable` (0x2A, protocolgame.cpp:10317-10327)
 *
 * The `version < 1100`, `ProtocolFeature::ResourceBalancePackets` and
 * `m_isLivestreamViewer` guards stay with the caller. `Resource_t` and
 * `CharmResource_t` are transcribed from `src/server/server_definitions.hpp:57-82`;
 * the current payload switches the listed resource types to a `uint32_t`
 * (`resourceBalanceUsesU32`, protocolgame.cpp:150-164). Canary's store
 * offer/history inbound opcodes (0xFA-0xFE) are commented out
 * (protocolgame.cpp:2114-2118), so no store-offer sender exists to port.
 * Every multi-byte field is Little-Endian.
 */
object StoreWindowWriter {

    const val PACKET_RESOURCE_BALANCE: Int = 0xEE
    const val PACKET_COIN_BALANCE_UPDATE: Int = 0xF2
    const val PACKET_COIN_BALANCE: Int = 0xDF

    /** `SpecialContainersAvailable` opcode (protocolgame.cpp:10323). */
    const val PACKET_SPECIAL_CONTAINERS_AVAILABLE: Int = 0x2A

    /** `Resource_t` (`src/server/server_definitions.hpp:57-75`). */
    enum class Resource(val value: Int) {
        BANK(0x00),
        INVENTORY_MONEY(0x01),
        INVENTORY_CURRENCY_CUSTOM(0x02),
        PREY_CARDS(0x0A),
        TASK_HUNTING(0x32),
        NPC_TRADE_QUEST_FLAG_CURRENCY(0x3C),
        FORGE_DUST(0x46),
        FORGE_SLIVER(0x47),
        FORGE_CORES(0x48),
        UNSPENT_SKILL_POINTS(0x50),
        LESSER_GEMS(0x51),
        REGULAR_GEMS(0x52),
        GREATER_GEMS(0x53),
        LESSER_FRAGMENT(0x54),
        GREATER_FRAGMENT(0x55),
        BOUNTY_POINTS(0x56),
        SOULSEALS(0x57),
    }

    /** `CharmResource_t` (`src/server/server_definitions.hpp:77-82`). */
    enum class CharmResource(val value: Int) {
        CHARM(0x1E),
        MINOR_CHARM(0x1F),
        MAX_CHARM(0x20),
        MAX_MINOR_CHARM(0x21),
    }

    private const val UINT32_MAX_VALUE = 0xFFFFFFFFL

    /** `sendResourcesBalance` (protocolgame.cpp:6610-6626). */
    fun writeResourcesBalance(
        out: ByteBuf,
        money: Long,
        bank: Long,
        preyCards: Long,
        taskHunting: Long,
        forgeDust: Long,
        forgeSliver: Long,
        forgeCores: Long,
        currentPayload: Boolean = true,
    ) {
        writeResourceBalance(out, Resource.BANK, bank, currentPayload)
        writeResourceBalance(out, Resource.INVENTORY_MONEY, money, currentPayload)
        writeResourceBalance(out, Resource.PREY_CARDS, preyCards, currentPayload)
        writeResourceBalance(out, Resource.TASK_HUNTING, taskHunting, currentPayload)
        writeResourceBalance(out, Resource.FORGE_DUST, forgeDust, currentPayload)
        writeResourceBalance(out, Resource.FORGE_SLIVER, forgeSliver, currentPayload)
        writeResourceBalance(out, Resource.FORGE_CORES, forgeCores, currentPayload)
    }

    /**
     * `sendResourceBalance` (protocolgame.cpp:6628-6646). [oldProtocol] keeps
     * the `resourceType > RESOURCE_PREY_CARDS` early return; the current payload
     * clamps `uint32` resources to `UINT32_MAX`.
     */
    fun writeResourceBalance(
        out: ByteBuf,
        resource: Resource,
        value: Long,
        currentPayload: Boolean = true,
        oldProtocol: Boolean = false,
    ) {
        if (oldProtocol && resource.value > Resource.PREY_CARDS.value) {
            return
        }

        out.writeByte(PACKET_RESOURCE_BALANCE)
        out.writeByte(resource.value)

        if (currentPayload && resourceBalanceUsesU32(resource.value)) {
            out.writeIntLE(minOf(value, UINT32_MAX_VALUE).toInt())
        } else {
            out.writeLongLE(value)
        }
    }

    /** `sendCharmResourcesBalance` (protocolgame.cpp:6648-6653). */
    fun writeCharmResourcesBalance(
        out: ByteBuf,
        charm: Long,
        minorCharm: Long,
        maxCharm: Long,
        maxMinorCharm: Long,
        oldProtocol: Boolean = false,
    ) {
        writeCharmResourceBalance(out, CharmResource.CHARM, charm, oldProtocol)
        writeCharmResourceBalance(out, CharmResource.MINOR_CHARM, minorCharm, oldProtocol)
        writeCharmResourceBalance(out, CharmResource.MAX_CHARM, maxCharm, oldProtocol)
        writeCharmResourceBalance(out, CharmResource.MAX_MINOR_CHARM, maxMinorCharm, oldProtocol)
    }

    /** `sendCharmResourceBalance` (protocolgame.cpp:6655-6667). */
    fun writeCharmResourceBalance(
        out: ByteBuf,
        resource: CharmResource,
        value: Long,
        oldProtocol: Boolean = false,
    ) {
        if (oldProtocol) {
            return
        }

        out.writeByte(PACKET_RESOURCE_BALANCE)
        out.writeByte(resource.value)
        out.writeIntLE(value.toInt())
    }

    /**
     * `sendCoinBalance` (protocolgame.cpp:6803-6829): the `0xF2` "updating"
     * frame followed by the `0xDF` balances. The reserved auction balance is the
     * normal coin balance on the current payload.
     */
    fun writeCoinBalance(
        out: ByteBuf,
        coinBalance: Long,
        transferableBalance: Long,
        oldProtocol: Boolean = false,
    ) {
        out.writeByte(PACKET_COIN_BALANCE_UPDATE)
        out.writeByte(0x01)

        out.writeByte(PACKET_COIN_BALANCE)
        out.writeByte(0x01)
        out.writeIntLE(coinBalance.toInt())
        out.writeIntLE(transferableBalance.toInt())
        if (!oldProtocol) {
            out.writeIntLE(coinBalance.toInt())
        }
    }

    /**
     * `sendSpecialContainersAvailable` (protocolgame.cpp:10317-10327). The
     * `player->isStashMenuAvailable()` / `isMarketMenuAvailable()` reads and the
     * `oldProtocol`/`!player` guard stay with the caller; the depot-search flag
     * is not written by Canary.
     */
    fun writeSpecialContainersAvailable(out: ByteBuf, stashMenu: Boolean, marketMenu: Boolean) {
        out.writeByte(PACKET_SPECIAL_CONTAINERS_AVAILABLE)
        out.writeByte(if (stashMenu) 0x01 else 0x00)
        out.writeByte(if (marketMenu) 0x01 else 0x00)
    }

    /** `resourceBalanceUsesU32` (protocolgame.cpp:150-164). */
    private fun resourceBalanceUsesU32(resourceType: Int): Boolean = when (resourceType) {
        CharmResource.CHARM.value,
        CharmResource.MINOR_CHARM.value,
        CharmResource.MAX_CHARM.value,
        CharmResource.MAX_MINOR_CHARM.value,
        Resource.NPC_TRADE_QUEST_FLAG_CURRENCY.value,
        Resource.UNSPENT_SKILL_POINTS.value,
        Resource.BOUNTY_POINTS.value,
        Resource.SOULSEALS.value,
        -> true

        else -> false
    }
}
