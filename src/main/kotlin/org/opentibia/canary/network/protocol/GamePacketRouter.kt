package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.config.FeatureFlags
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.protocol.offline.StartOfflineTrainingPacketParser
import org.opentibia.canary.network.protocol.rewardchest.RewardChestCollectPacketParser
import org.opentibia.canary.network.protocol.bestiary.BestiaryPacketParser
import org.opentibia.canary.network.protocol.forge.ForgePacketParser
import org.opentibia.canary.network.protocol.guild.GuildPacketParser
import org.opentibia.canary.network.protocol.market.MarketPacketParser
import org.opentibia.canary.network.protocol.npcshop.NpcShopPacketHandler
import org.opentibia.canary.network.protocol.wheel.OpenWheelCommand
import org.opentibia.canary.network.protocol.wheel.SaveWheelCommand
import org.opentibia.canary.network.protocol.wheel.WheelGemActionCommand
import org.opentibia.canary.network.protocol.wheel.WheelPacketParser
import org.opentibia.canary.systems.forge.ForgeApplication
import org.opentibia.canary.systems.forge.PlayerForgeAdapter
import org.opentibia.canary.systems.offlinetraining.OfflineTrainingApplication
import org.opentibia.canary.systems.rewardchest.PlayerRewardChestOwner
import org.opentibia.canary.systems.rewardchest.RewardChestApplication
import org.opentibia.canary.systems.wheel.WheelApplication

data class GamePacketContext(
    val map: GameMap,
    val dispatcher: GameDispatcher,
    val luaEnvironment: LuaEnvironment,
    val featureFlags: FeatureFlags = FeatureFlags(),
    val tradeManager: TradeManager = TradeManager(),
    val modalManager: ModalWindowManager = ModalWindowManager(),
    /**
     * `ItemType::upgradeClassification` source (`Item::getClassification`,
     * src/items/item.hpp:753). Optional so callers without a loaded registry
     * keep the classification-0 default.
     */
    val itemTypes: ItemTypeRegistry? = null
)

object GamePacketRouter {

    private val wheelApplication = WheelApplication()
    private val bestiaryApplication = org.opentibia.canary.systems.bestiary.BestiaryApplication()
    private val forgeApplication = ForgeApplication()

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
                    MovePacketHandler.handle(
                        player, opcode, buffer, context.map,
                        context.luaEnvironment, context.dispatcher,
                    )
                }

                // Items & Containers
                0x78 -> {
                    ItemPacketHandler.handleThrow(player, buffer, context.map)
                }

                // Containers: 0x75 parseContainerAction / 0x87 parseCloseContainer /
                // 0x88 parseUpArrowContainer / 0x95 parseOpenParentContainer
                TradeAndContainerPacketHandler.OPCODE_CONTAINER_ACTION,
                TradeAndContainerPacketHandler.OPCODE_CLOSE_CONTAINER,
                TradeAndContainerPacketHandler.OPCODE_UP_ARROW_CONTAINER,
                TradeAndContainerPacketHandler.OPCODE_OPEN_PARENT_CONTAINER -> {
                    val command = TradeAndContainerPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Open-container state belongs to Player::closeContainer,
                            // Player::moveUpContainer and Game::playerRequestOpenContainerFromDepotSearch;
                            // none are ported, and 0x75 reads and discards its operands.
                        }
                        true
                    }
                }

                // NPC shop: 0x7A parsePlayerBuyOnShop / 0x7B parsePlayerSellOnShop /
                // 0x7C playerCloseShop
                in NpcShopPacketHandler.OPCODE_PLAYER_BUY_ON_SHOP..NpcShopPacketHandler.OPCODE_PLAYER_CLOSE_SHOP -> {
                    val command = NpcShopPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // NPC trade buy/sell/close; the shop window and dialogue stay Lua
                            // (AGENTS.md 1.4), so the decoded command only marks the confinement boundary.
                        }
                        true
                    }
                }

                // Trade: 0x7D parseRequestTrade / 0x7E parseLookInTrade /
                // 0x7F playerAcceptTrade / 0x80 playerCloseTrade
                in TradeAndContainerPacketHandler.OPCODE_REQUEST_TRADE..TradeAndContainerPacketHandler.OPCODE_CLOSE_TRADE -> {
                    val command = TradeAndContainerPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // The trade session state machine (Game::playerRequestTrade,
                            // playerAcceptTrade, playerCloseTrade, playerLookInTrade) is
                            // not yet ported, so the decoded command only marks the
                            // confinement boundary.
                        }
                        true
                    }
                }

                // Stash & depot search: 0x28 parseStashWithdraw / 0x29 parseRetrieveDepotSearch /
                // 0x92 parseOpenDepotSearch / 0x93 parseCloseDepotSearch /
                // 0x94 parseDepotSearchItemRequest (0x95 parseOpenParentContainer is decoded
                // with the container opcodes above)
                DepotAndStashPacketHandler.OPCODE_STASH_WITHDRAW,
                DepotAndStashPacketHandler.OPCODE_RETRIEVE_DEPOT_SEARCH,
                in DepotAndStashPacketHandler.OPCODE_OPEN_DEPOT_SEARCH..DepotAndStashPacketHandler.OPCODE_DEPOT_SEARCH_ITEM_REQUEST -> {
                    val command = DepotAndStashPacketHandler.parse(
                        opcode,
                        buffer,
                        upgradeClassification = { itemId ->
                            context.itemTypes?.get(itemId)?.upgradeClassification ?: 0
                        },
                    )
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Player stash and depot-search state (Game::playerStowItem,
                            // playerStashWithdraw, playerRequestDepotItems,
                            // playerRequestDepotSearchItem, playerRequestDepotSearchRetrieve)
                            // is not ported, so the decoded command only marks the
                            // confinement boundary.
                        }
                        true
                    }
                }

                0x82 -> {
                    ItemPacketHandler.handleUse(player, buffer, context.map, context.dispatcher, context.luaEnvironment)
                }

                // Use item ex: 0x83 parseUseItemEx
                ItemPacketHandler.OPCODE_USE_ITEM_EX -> {
                    ItemPacketHandler.handleUseItemEx(buffer, context.dispatcher)
                }

                // Look At
                0x8C, 0x8D -> {
                    LookPacketHandler.handleLook(player, buffer, context.map) != null
                }

                // Quick loot: 0x8F parseQuickLoot / 0x90 parseLootContainer /
                // 0x91 parseQuickLootBlackWhitelist
                in QuickLootPacketHandler.OPCODE_QUICK_LOOT..QuickLootPacketHandler.OPCODE_QUICK_LOOT_BLACK_WHITELIST -> {
                    val command = QuickLootPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Player quick-loot state (Game::playerQuickLoot, playerLootNearby,
                            // playerSetManagedContainer, playerClearManagedContainer,
                            // playerOpenManagedContainer, playerSetQuickLootFallback,
                            // playerQuickLootBlackWhitelist) is not ported, so the decoded
                            // command only marks the confinement boundary.
                        }
                        true
                    }
                }

                // Say / talkactions: 0x96 parseSay
                TalkPacketHandler.OPCODE_SAY -> {
                    TalkPacketHandler.handleSay(player, buffer, context.dispatcher, context.luaEnvironment)
                }

                // Combat
                0xA0 -> {
                    CombatPacketHandler.handleFightModes(player, buffer)
                }
                0xA1 -> {
                    CombatPacketHandler.handleAttack(player, buffer, emptyList())
                }

                // Follow: 0xA2 parseFollow
                FollowPacketHandler.OPCODE_FOLLOW -> {
                    val command = FollowPacketHandler.parseFollow(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerFollowCreature -> Player::setFollowCreature; the
                            // follow state machine is not yet ported, so the decoded
                            // command only marks the confinement boundary.
                        }
                        true
                    }
                }

                // Party: 0xA3 parseInviteToParty / 0xA4 parseJoinParty /
                // 0xA5 parseRevokePartyInvite / 0xA6 parsePassPartyLeadership /
                // 0xA7 playerLeaveParty / 0xA8 parseEnableSharedPartyExperience
                in PartyPacketHandler.OPCODE_INVITE_TO_PARTY..PartyPacketHandler.OPCODE_ENABLE_SHARED_PARTY_EXPERIENCE -> {
                    val command = PartyPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerInviteToParty/playerJoinParty/playerRevokePartyInvitation/
                            // playerPassPartyLeadership/playerLeaveParty/playerEnableSharedPartyExperience
                            // resolve the target player by id through the global game registry,
                            // which this context does not provide; the party state machine stays
                            // unapplied (PARITY GAP).
                        }
                        true
                    }
                }

                // Wheel of Destiny
                // 0x61 parseOpenWheel / 0x62 parseSaveWheel / 0xE7 parseWheelGemAction
                WheelPacketParser.OPCODE_OPEN_WHEEL,
                WheelPacketParser.OPCODE_SAVE_WHEEL,
                WheelPacketParser.OPCODE_WHEEL_GEM_ACTION -> {
                    val command = WheelPacketParser.parse(opcode, buffer)
                    if (command == null || !context.featureFlags.toggleWheelSystem) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            when (command) {
                                is SaveWheelCommand -> wheelApplication.applySave(player.features.wheel, command)
                                is WheelGemActionCommand -> wheelApplication.applyGemAction(player.features.wheel, command)
                                is OpenWheelCommand -> Unit // opening the window mutates no state
                            }
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
                            OfflineTrainingApplication.apply(player.features.offlineTraining, command)
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
                            val owner = PlayerRewardChestOwner(
                                capacityProvider = { player.capacity },
                                cancelMessageSink = { },
                                textMessageSink = { },
                                collectManagedItem = { item -> player.addItem(item) },
                            )
                            RewardChestApplication.apply(
                                chest = player.features.rewardChest,
                                owner = owner,
                                command = command,
                                collectEnabled = context.featureFlags.rewardChestCollectEnabled,
                                maxCollectItems = context.featureFlags.rewardChestMaxCollectItems,
                            )
                        }
                        true
                    }
                }

                // Outfit & mount: 0xD3 parseSetOutfit / 0xD4 parseToggleMount
                in OutfitPacketHandler.OPCODE_SET_OUTFIT..OutfitPacketHandler.OPCODE_TOGGLE_MOUNT -> {
                    val command = OutfitPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            when (command) {
                                // Player::toggleMount (player.cpp) is ported as
                                // PlayerMountState; its speed/outfit-window side effects
                                // are not wired yet.
                                is ToggleMountCommand -> player.features.mounts.toggleMount(command.mount)
                                // Player::changeOutfit / playerSetShowOffSocket are not ported.
                                is SetOutfitCommand -> Unit
                            }
                        }
                        true
                    }
                }

                // Imbuements: 0xB2 parseImbuementAction / 0xD5 parseApplyImbuement /
                // 0xD6 parseClearImbuement / 0xD7 parseCloseImbuementWindow
                ImbuementPacketHandler.OPCODE_IMBUEMENT_ACTION,
                in ImbuementPacketHandler.OPCODE_APPLY_IMBUEMENT..ImbuementPacketHandler.OPCODE_CLOSE_IMBUEMENT_WINDOW -> {
                    val command = ImbuementPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerApplyImbuement, playerClearImbuement and
                            // playerCloseImbuementWindow, and the imbuement window payload
                            // itself, are not ported; the decoded command only marks the
                            // confinement boundary.
                        }
                        true
                    }
                }

                // Bestiary & charms: 0xE1 send races / 0xE2 send creatures /
                // 0xE3 monster data / 0xE4 buy charm rune
                in BestiaryPacketParser.OPCODE_SEND_RACES..BestiaryPacketParser.OPCODE_BUY_CHARM_RUNE -> {
                    val command = BestiaryPacketParser.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            bestiaryApplication.apply(player.features.bestiary, command)
                        }
                        true
                    }
                }

                // Exaltation Forge: 0xBF parseForgeEnter
                ForgePacketParser.OPCODE_FORGE_ENTER -> {
                    val command = ForgePacketParser.parseForgeEnter(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Canary's forge handlers read `g_game().getItemsClassifications()`
                            // (player.cpp:11297,11589), the store the Lua price table
                            // (data/scripts/systems/item_tiers.lua) populates through
                            // `Game.createItemClassification`, and each item's
                            // `ItemType::upgradeClassification` (`Item::getClassification`,
                            // src/items/item.hpp:753).
                            val adapter = PlayerForgeAdapter(
                                player,
                                itemClassifications = context.luaEnvironment.itemClassifications,
                                itemRegistry = context.itemTypes,
                            )
                            forgeApplication.apply(player.features.forge, adapter, command)
                            adapter.syncItemTiers()
                        }
                        true
                    }
                }

                // Guild: 0x9C parseEditGuildMessage
                GuildPacketParser.OPCODE_EDIT_GUILD_MESSAGE -> {
                    val command = GuildPacketParser.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Guild message edit; confinement boundary.
                        }
                        true
                    }
                }

                // Quest log: 0xF0 playerShowQuestLog / 0xF1 parseQuestLine
                QuestLogPacketHandler.OPCODE_SHOW_QUEST_LOG,
                QuestLogPacketHandler.OPCODE_QUEST_LINE -> {
                    val command = QuestLogPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerShowQuestLog / playerShowQuestLine read quest state
                            // from the Lua quest scripts (AGENTS.md 1.4); the quest window
                            // senders are not wired here.
                        }
                        true
                    }
                }

                // Market: 0xF4 leave / 0xF5 browse / 0xF6 create / 0xF7 cancel / 0xF8 accept
                in MarketPacketParser.OPCODE_MARKET_LEAVE..MarketPacketParser.OPCODE_MARKET_ACCEPT_OFFER -> {
                    val command = MarketPacketParser.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Market operations mutate the offer book; confinement boundary.
                        }
                        true
                    }
                }

                // Modal window answer: 0xF9 parseModalWindowAnswer
                ModalAnswerPacketHandler.OPCODE_MODAL_WINDOW_ANSWER -> {
                    val command = ModalAnswerPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            context.modalManager.handleResponse(
                                player,
                                command.windowId.toInt(),
                                command.button,
                                command.choice,
                            )
                        }
                        true
                    }
                }

                // Wave 35 batch B3
                // Private channels: 0xAA playerCreatePrivateChannel /
                // 0xAB parseChannelInvite / 0xAC parseChannelExclude
                ChannelPacketHandler.OPCODE_CREATE_PRIVATE_CHANNEL,
                ChannelPacketHandler.OPCODE_CHANNEL_INVITE,
                ChannelPacketHandler.OPCODE_CHANNEL_EXCLUDE -> {
                    val command = ChannelPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerCreatePrivateChannel/playerChannelInvite/
                            // playerChannelExclude (game.cpp:4324-4382) are ported in
                            // chat/ChatManager.kt, but no ChatManager instance is reachable
                            // from GamePacketContext, so the decoded command only marks the
                            // confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                // Cyclopedia house auction: 0xAD parseCyclopediaHouseAuction
                CyclopediaHouseAuctionPacketHandler.OPCODE_CYCLOPEDIA_HOUSE_AUCTION -> {
                    val command = CyclopediaHouseAuctionPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerCyclopediaHousesByTown/HouseBid/HouseMoveOut/
                            // HouseTransfer/HouseCancelMoveOut/HouseCancelTransfer/
                            // HouseAcceptTransfer/HouseRejectTransfer (game.cpp:12762-12925)
                            // are not ported (the House model carries no auction state), so
                            // the decoded command only marks the confinement boundary
                            // (PARITY GAP).
                        }
                        true
                    }
                }

                // Bosstiary: 0xAE parseSendBosstiary / 0xAF parseSendBosstiarySlots /
                // 0xB0 parseBosstiarySlot
                BosstiaryPacketHandler.OPCODE_SEND_BOSSTIARY,
                BosstiaryPacketHandler.OPCODE_SEND_BOSSTIARY_SLOTS,
                BosstiaryPacketHandler.OPCODE_BOSSTIARY_SLOT -> {
                    val command = BosstiaryPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // BosstiaryWindowWriter ports the 0xAE/0xAF replies and
                            // Game::playerBosstiarySlot (game.cpp:11256) mutates the player
                            // slot state, but the player-bosstiary store and the IO
                            // bosstiary map are not ported and no session sink is reachable
                            // here, so the decoded command only marks the confinement
                            // boundary (PARITY GAP).
                        }
                        true
                    }
                }

                // Highscores: 0xB1 parseHighscores
                HighscoresPacketHandler.OPCODE_HIGHSCORES -> {
                    val command = HighscoresPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerHighscores (game.cpp:10124) runs the async
                            // highscore query and HighscoresWindowSerializer ports the 0xB1
                            // reply, but the query repository and the async-task bookkeeping
                            // are not ported, so the decoded command only marks the
                            // confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                // Weapon proficiency: 0xB3 parseWeaponProficiency
                WeaponProficiencyPacketHandler.OPCODE_WEAPON_PROFICIENCY -> {
                    val command = WeaponProficiencyPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Player::weaponProficiency()'s getTrackedWeaponIds,
                            // clearSelectedPerks, setSelectedPerk, applyPerks and
                            // clearAllStats surface (weapon_proficiency.cpp:320-595, 1488)
                            // is not ported, so the decoded command only marks the
                            // confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                // Task hunting: 0xBA parseTaskHuntingAction
                TaskHuntingPacketHandler.OPCODE_TASK_HUNTING_ACTION -> {
                    val command = TaskHuntingPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerTaskHuntingAction -> IOPrey::parseTaskHuntingAction
                            // (game.cpp:10227, ioprey.cpp:405) is ported as
                            // TaskHuntingEngine.parseTaskHuntingAction. The parse-time
                            // TASK_HUNTING_ENABLED gate (protocolgame.cpp:3214) is not
                            // observable through the injected engine config (PARITY GAP).
                            val action = command.action
                            if (action != null) {
                                player.features.taskHuntingEngine.parseTaskHuntingAction(
                                    player = player.features.taskHunting,
                                    slotId = command.slot,
                                    action = action,
                                    upgrade = command.upgrade,
                                    raceId = command.raceId,
                                )
                            }
                        }
                        true
                    }
                }

                // Cancel attack and follow: 0xBE playerCancelAttackAndFollow
                CancelAttackAndFollowPacketHandler.OPCODE_CANCEL_ATTACK_AND_FOLLOW -> {
                    CancelAttackAndFollowPacketHandler.parse()
                    context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                        // Game::playerCancelAttackAndFollow calls playerSetAttackedCreature(id, 0),
                        // playerFollowCreature(id, 0) and Player::stopWalk (game.cpp:6990-6997,
                        // 7001-7031, 7033-7042). Only the attacked-creature clear is ported;
                        // Player::resetLoginProtection, the follow state machine, stopWalk and
                        // the cancel-target send are not ported (PARITY GAP).
                        player.setAttackedCreature(null)
                    }
                    true
                }

                // Forge browse history: 0xC0 parseForgeBrowseHistory
                ForgeHistoryPacketHandler.OPCODE_FORGE_BROWSE_HISTORY -> {
                    val command = ForgeHistoryPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerBrowseForgeHistory (game.cpp:11241) sends the
                            // stored history page through Player::forgeHistory;
                            // PlayerForgeHistory and ForgeHistorySerializer are ported, but
                            // the outbound session send is not reachable here, so the decoded
                            // command only marks the confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                // Client details: 0xC1 parseClientDetails (read and discarded)
                ClientDetailsPacketHandler.OPCODE_CLIENT_DETAILS -> {
                    ClientDetailsPacketHandler.parse(buffer) != null
                }

                // Boss difficulty selection: 0xC2 parseBossDifficultySelection
                // (read and discarded; protocolgame.cpp:3317-3323)
                BossDifficultyPacketHandler.OPCODE_BOSS_DIFFICULTY_SELECTION -> {
                    BossDifficultyPacketHandler.parse(buffer) != null
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
