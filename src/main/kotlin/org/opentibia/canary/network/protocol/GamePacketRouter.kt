package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.protocol.offline.StartOfflineTrainingPacketParser
import org.opentibia.canary.network.protocol.rewardchest.RewardChestCollectPacketParser
import org.opentibia.canary.network.protocol.wheel.WheelPacketParser

data class GamePacketContext(
    val map: GameMap,
    val dispatcher: GameDispatcher,
    val luaEnvironment: LuaEnvironment,
    val tradeManager: TradeManager = TradeManager(),
    val modalManager: ModalWindowManager = ModalWindowManager()
)

object GamePacketRouter {

    fun route(player: Player, buffer: ByteBuf, context: GamePacketContext): Int {
        var packetsHandled = 0

        while (buffer.isReadable) {
            val opcode = buffer.readUnsignedByte().toInt()

            val handled = when (opcode) {
                // Keepalive
                0x1E -> {
                    KeepaliveHandler.handlePing(player)
                    true
                }
                0x14 -> {
                    KeepaliveHandler.canLogout(player)
                }

                // Movement & Turns
                in 0x64..0x72 -> {
                    MovePacketHandler.handle(player, opcode, buffer, context.map)
                }

                // Items & Containers
                0x78 -> {
                    ItemPacketHandler.handleThrow(player, buffer, context.map)
                }
                0x82 -> {
                    ItemPacketHandler.handleUse(player, buffer, context.map, context.dispatcher, context.luaEnvironment)
                }

                // Look At
                0x8C, 0x8D -> {
                    LookPacketHandler.handleLook(player, buffer, context.map) != null
                }

                // Combat
                0xA0 -> {
                    CombatPacketHandler.handleFightModes(player, buffer)
                }
                0xA1 -> {
                    CombatPacketHandler.handleAttack(player, buffer, emptyList())
                }

                // Wheel of Destiny
                // 0x61 parseOpenWheel / 0x62 parseSaveWheel / 0xE7 parseWheelGemAction
                WheelPacketParser.OPCODE_OPEN_WHEEL,
                WheelPacketParser.OPCODE_SAVE_WHEEL,
                WheelPacketParser.OPCODE_WHEEL_GEM_ACTION -> {
                    val command = WheelPacketParser.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // PlayerWheel/gem state is not yet attached to Player, so
                            // this is the confinement boundary for the Canary mutations
                            // (playerOpenWheel / playerSaveWheel / playerWheelGemAction).
                            // Parsing alone is no-op-safe until that engine state is wired.
                        }
                        true
                    }
                }

                // Offline training: 0x74 parseStartOfflineTraining
                0x74 -> {
                    val command = StartOfflineTrainingPacketParser.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // OfflineTraining engine is not yet attached to Player, so
                            // this is the confinement boundary for parseStartOfflineTraining.
                            // Parsing alone is no-op-safe until that engine state is wired.
                        }
                        true
                    }
                }

                // Reward chest: 0xFF parseRewardChestCollect
                0xFF -> {
                    val command = RewardChestCollectPacketParser.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerRewardChestCollect needs the player's reward
                            // container, which is not yet wired; this is the confinement
                            // boundary. Parsing alone is no-op-safe until then.
                        }
                        true
                    }
                }

                else -> false
            }

            if (handled) {
                packetsHandled++
            } else {
                // Stop parsing frame if opcode is unhandled or malformed
                buffer.skipBytes(buffer.readableBytes())
                break
            }
        }

        return packetsHandled
    }
}

data class GameContext(val context: GamePacketContext)
