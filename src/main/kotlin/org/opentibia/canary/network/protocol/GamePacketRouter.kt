package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.config.FeatureFlags
import org.opentibia.canary.game.Game
import org.opentibia.canary.game.QuickLootFilter_t
import org.opentibia.canary.game.scheduling.DispatcherLane
import org.opentibia.canary.game.scheduling.GameDispatcher
import org.opentibia.canary.io.IOMarket
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.lua.runtime.LuaEnvironment
import org.opentibia.canary.lua.runtime.LuaEventDispatcher
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.network.protocol.offline.StartOfflineTrainingPacketParser
import org.opentibia.canary.network.protocol.rewardchest.RewardChestCollectPacketParser
import org.opentibia.canary.network.protocol.bestiary.BestiaryPacketParser
import org.opentibia.canary.network.protocol.forge.ForgePacketParser
import org.opentibia.canary.network.protocol.guild.GuildPacketParser
import org.opentibia.canary.network.protocol.market.MarketAcceptOfferCommand
import org.opentibia.canary.network.protocol.market.MarketBrowseCommand
import org.opentibia.canary.network.protocol.market.MarketBrowseRequest
import org.opentibia.canary.network.protocol.market.MarketCancelOfferCommand
import org.opentibia.canary.network.protocol.market.MarketCreateOfferCommand
import org.opentibia.canary.network.protocol.market.MarketLeaveCommand
import org.opentibia.canary.network.protocol.market.MarketOfferApplication
import org.opentibia.canary.network.protocol.market.MarketOfferContext
import org.opentibia.canary.network.protocol.market.MarketPacketParser
import org.opentibia.canary.network.protocol.npcshop.NpcShopPacketHandler
import org.opentibia.canary.network.protocol.wheel.OpenWheelCommand
import org.opentibia.canary.network.protocol.wheel.SaveWheelCommand
import org.opentibia.canary.network.protocol.wheel.WheelGemActionCommand
import org.opentibia.canary.network.protocol.wheel.WheelPacketParser
import org.opentibia.canary.systems.forge.ForgeApplication
import org.opentibia.canary.systems.forge.PlayerForgeAdapter
import org.opentibia.canary.systems.prey.PreyAction
import org.opentibia.canary.systems.prey.PreyOption
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
    val itemTypes: ItemTypeRegistry? = null,
    /**
     * `g_game()` seam for the handlers whose application lives in a
     * `Game::player*` member (trade, containers, follow, quick loot, depot
     * search, market, imbuements). Optional so callers without a composed
     * world keep the decode-only confinement boundary.
     */
    val game: Game? = null,
    /**
     * `IOMarket::getInstance()` (game.cpp:10297, 4078) seam for the market
     * offer application (`Game::playerCreateMarketOffer` etc., game.cpp:10717).
     * Optional so callers without the market persistence keep the decode-only
     * boundary.
     */
    val ioMarket: IOMarket? = null
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
                            when (command) {
                                // Canary's parseContainerAction reads and discards
                                // its operands (protocolgame.cpp:3258-3268); no
                                // Game call is made.
                                is ContainerActionCommand -> Unit

                                // Game::playerCloseContainer (game.cpp:5028) and
                                // Game::playerMoveUpContainer (game.cpp:5038)
                                // apply to the Player open-container registry.
                                is CloseContainerCommand -> context.game?.playerCloseContainer(
                                    player.id,
                                    command.containerId,
                                )

                                is UpArrowContainerCommand -> context.game?.playerMoveUpContainer(
                                    player.id,
                                    command.containerId,
                                )

                                // Game::playerRequestOpenContainerFromDepotSearch
                                // (game.cpp:6975) gates on the open depot search
                                // and resolves the searched item's parent
                                // container, then dispatches the container action
                                // through the Lua action layer (Player::openContainerFromDepotSearch,
                                // player.cpp:10901).
                                is OpenParentContainerCommand ->
                                    TradeAndContainerPacketHandler.applyOpenParentContainer(
                                        player,
                                        command,
                                        context.luaEnvironment,
                                    )

                                else -> Unit
                            }
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
                            // Game::playerRequestTrade (game.cpp:5797),
                            // Game::playerLookInTrade (game.cpp:6119),
                            // Game::playerAcceptTrade (game.cpp:5986) and
                            // Game::playerCloseTrade (game.cpp:6178) drive the
                            // trade session state machine on the Player/facade.
                            when (command) {
                                is RequestTradeCommand -> context.game?.playerRequestTrade(
                                    player.id,
                                    command.position,
                                    command.stackPosition,
                                    command.partnerPlayerId,
                                    command.itemId,
                                )

                                is LookInTradeCommand -> context.game?.playerLookInTrade(
                                    player.id,
                                    command.counterOffer,
                                    command.index,
                                )

                                is AcceptTradeCommand -> context.game?.playerAcceptTrade(player.id)

                                is CloseTradeCommand -> context.game?.playerCloseTrade(player.id)

                                else -> Unit
                            }
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
                            when (command) {
                                // Game::playerRequestCloseDepotSearch (game.cpp:6933)
                                // resets Player::depotSearchOnItem.
                                is CloseDepotSearchCommand -> context.game?.playerRequestCloseDepotSearch(player.id)

                                // Game::playerRequestDepotItems / playerRequestDepotSearchItem
                                // (game.cpp:6920, 6945) set Player::depotSearchOnItem.
                                OpenDepotSearchCommand,
                                is DepotSearchItemRequestCommand -> DepotAndStashPacketHandler.apply(player, command)

                                // ProtocolGame::parseStashWithdraw stash actions
                                // (protocolgame.cpp:10874) with the
                                // Game::playerStowItem / playerStashWithdraw state
                                // transitions (game.cpp:5665, 5706).
                                is StowItemCommand,
                                is StashWithdrawCommand -> DepotAndStashPacketHandler.applyStashAction(
                                    player,
                                    command,
                                    context.map,
                                    context.itemTypes,
                                )

                                // Game::playerRequestDepotSearchRetrieve
                                // (game.cpp:6960) moves the searched depot/inbox
                                // items to the player and refreshes the search.
                                is RetrieveDepotSearchCommand -> DepotAndStashPacketHandler.applyRetrieveDepotSearch(
                                    player,
                                    command,
                                    context.itemTypes,
                                )

                                else -> Unit
                            }
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
                            when (command) {
                                // Game::playerQuickLoot / playerLootNearby
                                // (game.cpp:6428, 6766): the corpse resolution
                                // and the managed-container loot collection.
                                is QuickLootCorpseCommand,
                                QuickLootNearbyCommand,
                                -> QuickLootPacketHandler.applyQuickLoot(player, command, context.map)

                                is QuickLootUnsupportedVariantCommand -> Unit

                                // Game::playerSetManagedContainer /
                                // playerClearManagedContainer / playerOpenManagedContainer
                                // (game.cpp:6822-6897) register/clear/re-send the
                                // Player managed containers.
                                is SetManagedContainerCommand,
                                is ClearManagedContainerCommand,
                                is OpenManagedContainerCommand,
                                -> QuickLootPacketHandler.apply(player, command)

                                is UnknownLootContainerActionCommand -> Unit

                                // Game::playerSetQuickLootFallback (game.cpp:6898)
                                // and Game::playerQuickLootBlackWhitelist (game.cpp:6907)
                                // apply to the Player quick-loot state.
                                is SetQuickLootFallbackCommand -> context.game?.playerSetQuickLootFallback(
                                    player.id,
                                    command.useMainAsFallback,
                                )

                                is QuickLootBlackWhitelistCommand -> context.game?.playerQuickLootBlackWhitelist(
                                    player.id,
                                    QuickLootFilter_t.fromValue(command.filter),
                                    command.itemIds,
                                )
                            }
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
                            // Game::playerFollowCreature (game.cpp:7033) clears the
                            // attack target and applies Player::setFollowCreature
                            // (player.cpp:6127) over Creature::setFollowCreature
                            // (creature.cpp:1132).
                            context.game?.playerFollowCreature(player.id, command.creatureId)
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
                                // Game::playerChangeOutfit (game.cpp:7300): the
                                // outfit/mount core is applied; the Outfits/mount
                                // registries and the attached-effects block are
                                // injected seams / PARITY GAPs (see the handler).
                                is SetOutfitCommand -> OutfitPacketHandler.apply(player, command)
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
                            when (command) {
                                // Game::playerCloseImbuementWindow (game.cpp:7178)
                                // clears Player::imbuingItem.
                                is CloseImbuementWindowCommand -> context.game?.playerCloseImbuementWindow(player.id)

                                // 0xB2 PickItem resolves the tile item and stores it
                                // through Player::setImbuingItem (game.cpp:1170,
                                // protocolgame.cpp:10193).
                                is ImbuementActionCommand -> ImbuementPacketHandler.apply(player, command, context.map)

                                // Game::playerApplyImbuement / playerClearImbuement
                                // (game.cpp:7116, 7153) drive ImbuementEngine over the
                                // `ImbuementItem` seam. ProtocolGame::parseApplyImbuement
                                // drops an id above uint16 max before the Game call
                                // (protocolgame.cpp:2543).
                                is ApplyImbuementCommand -> {
                                    if (command.imbuementId <= 0xFFFF) {
                                        context.game?.playerApplyImbuement(
                                            player.id,
                                            command.imbuementId.toInt(),
                                            command.slot,
                                        )
                                    }
                                }

                                is ClearImbuementCommand -> context.game?.playerClearImbuement(
                                    player.id,
                                    command.slot,
                                )
                            }
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
                            // ProtocolGame::parseEditGuildMessage reads the string and
                            // discards it (protocolgame.cpp:3303-3305); Canary makes no
                            // Game call, so there is no state to apply.
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
                            // Game::playerShowQuestLog / playerShowQuestLine
                            // (game.cpp:7391, 7401) fire the registered
                            // playerOnRequestQuestLog / playerOnRequestQuestLine
                            // event callbacks; the quest data stays in the Lua
                            // scripts (data/scripts/eventcallbacks/player/).
                            when (command) {
                                ShowQuestLogCommand -> LuaEventDispatcher.eventCallbackPlayerOnRequestQuestLog(
                                    context.luaEnvironment,
                                    player,
                                )

                                is ShowQuestLineCommand -> LuaEventDispatcher.eventCallbackPlayerOnRequestQuestLine(
                                    context.luaEnvironment,
                                    player,
                                    command.questId,
                                )
                            }
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
                            when (command) {
                                // Game::playerLeaveMarket (game.cpp:10273) clears
                                // Player::inMarket.
                                MarketLeaveCommand -> context.game?.playerLeaveMarket(player.id)

                                // Game::playerBrowseMarket /
                                // playerBrowseMarketOwnOffers / playerBrowseMarketOwnHistory
                                // (game.cpp:10282-10337) read the IOMarket offers.
                                is MarketBrowseCommand -> when (val request = command.request) {
                                    MarketBrowseRequest.OwnOffers ->
                                        context.game?.playerBrowseMarketOwnOffers(player.id)

                                    MarketBrowseRequest.OwnHistory ->
                                        context.game?.playerBrowseMarketOwnHistory(player.id)

                                    is MarketBrowseRequest.Item ->
                                        context.game?.playerBrowseMarket(player.id, request.itemId, request.tier)
                                }

                                // Game::playerCreateMarketOffer /
                                // playerCancelMarketOffer / playerAcceptMarketOffer
                                // (game.cpp:10717, 10819, 10884) run the IOMarket
                                // transaction engine, item escrow and bank balance
                                // through the handler-layer MarketOfferApplication.
                                is MarketCreateOfferCommand ->
                                    marketOfferContext(context, player)?.let {
                                        MarketOfferApplication.applyCreateOffer(command, it)
                                    } ?: context.game?.playerCreateMarketOffer(
                                        player.id,
                                        command.action,
                                        command.itemId,
                                        command.amount,
                                        command.price,
                                        command.tier,
                                        command.anonymous,
                                    )

                                is MarketCancelOfferCommand ->
                                    marketOfferContext(context, player)?.let {
                                        MarketOfferApplication.applyCancelOffer(command, it)
                                    } ?: context.game?.playerCancelMarketOffer(
                                        player.id,
                                        command.timestamp,
                                        command.counter,
                                    )

                                is MarketAcceptOfferCommand ->
                                    marketOfferContext(context, player)?.let {
                                        MarketOfferApplication.applyAcceptOffer(command, it)
                                    } ?: context.game?.playerAcceptMarketOffer(
                                        player.id,
                                        command.timestamp,
                                        command.counter,
                                        command.amount,
                                    )
                            }
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

                // Wave 35 batch B1
                0x1D -> {
                    KeepaliveHandler.handlePingBack()
                }
                CyclopediaTrackerPacketHandler.OPCODE_CYCLOPEDIA_MONSTER_TRACKER -> {
                    val command = CyclopediaTrackerPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::parseCyclopediaMonsterTracker resolves the race id
                            // through the bosstiary/bestiary monster registries, which this
                            // context does not provide; the resolver seams default to no
                            // monster (PARITY GAP).
                            CyclopediaTrackerPacketHandler.apply(player, command)
                        }
                        true
                    }
                }
                PartyAnalyzerPacketHandler.OPCODE_PARTY_ANALYZER_ACTION -> {
                    val command = PartyAnalyzerPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Party::resetAnalyzer/switchAnalyzerPriceType/reloadPrices and
                            // Player::setItemCustomPrice are not ported, so the decoded
                            // command (including the item prices) is only the confinement
                            // boundary (PARITY GAP).
                        }
                        true
                    }
                }
                TeamFinderPacketHandler.OPCODE_LEADER_FINDER_WINDOW,
                TeamFinderPacketHandler.OPCODE_MEMBER_FINDER_WINDOW -> {
                    val command = TeamFinderPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // The shared assembler map cannot resolve online players by
                            // guid from this context, so member status changes and join
                            // requests against online members stay inert (PARITY GAP).
                            TeamFinderPacketHandler.apply(player, command)
                        }
                        true
                    }
                }
                ClientOptionsPacketHandler.OPCODE_SET_CLIENT_OPTIONS,
                ClientOptionsPacketHandler.OPCODE_CLIENT_CHECK -> {
                    val command = ClientOptionsPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Both Canary parsers only consume and discard their payload
                            // (protocolgame.cpp:3221-3248); no state to apply.
                        }
                        true
                    }
                }
                ExtendedOpcodePacketHandler.OPCODE_EXTENDED_OPCODE -> {
                    ExtendedOpcodePacketHandler.handle(player, buffer, context.dispatcher, context.luaEnvironment)
                }
                PlayerTypingPacketHandler.OPCODE_PLAYER_TYPING -> {
                    val command = PlayerTypingPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerSetTyping broadcasts ProtocolGame::sendPlayerTyping
                            // to the spectators; that sender is not ported (PARITY GAP).
                        }
                        true
                    }
                }
                InventoryImbuementsPacketHandler.OPCODE_INVENTORY_IMBUEMENTS -> {
                    val command = InventoryImbuementsPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerRequestInventoryImbuements needs the unported
                            // Player::imbuementTrackerWindowOpen flag and the
                            // sendInventoryImbuements sender (PARITY GAP).
                        }
                        true
                    }
                }
                CharacterTradeConfigurationPacketHandler.OPCODE_CHARACTER_TRADE_CONFIGURATION -> {
                    val command = CharacterTradeConfigurationPacketHandler.parse(
                        buffer,
                        itemTypeHasSubtype = { itemId ->
                            // ItemType::hasSubType also covers fluid containers, splashes
                            // and charged items, which the port's ItemType does not model
                            // (PARITY GAP); isStackable is the subset it carries.
                            context.itemTypes?.get(itemId)?.isStackable == true
                        },
                    )
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Canary only reads and discards this configuration
                            // (protocolgame.cpp:3270-3297); no state to apply.
                        }
                        true
                    }
                }
                HotkeyEquipPacketHandler.OPCODE_HOTKEY_EQUIP -> {
                    val command = HotkeyEquipPacketHandler.parse(
                        buffer,
                        upgradeClassification = { itemId ->
                            context.itemTypes?.get(itemId)?.upgradeClassification ?: 0
                        },
                    )
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerEquipItem (game.cpp:4159) is not ported; the
                            // decoded command only marks the confinement boundary
                            // (PARITY GAP).
                        }
                        true
                    }
                }
                NpcShopPacketHandler.OPCODE_LOOK_IN_SHOP -> {
                    val command = NpcShopPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerLookInShop resolves the interacted merchant; the
                            // shop window and dialogue stay Lua (AGENTS.md 1.4) and the
                            // merchant interaction is not ported (PARITY GAP).
                        }
                        true
                    }
                }
                FriendSystemPacketHandler.OPCODE_FRIEND_SYSTEM_ACTION -> {
                    val command = FriendSystemPacketHandler.parse(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerFriendSystemAction's title branch needs
                            // Player::title() and the cyclopedia senders; neither is
                            // reachable from this context (PARITY GAP).
                        }
                        true
                    }
                }

                // Wave 35 batch B2
                // Item interaction: 0x84 parseUseWithCreature / 0x85 parseRotateItem /
                // 0x86 parseConfigureShowOffSocket / 0x8B parseWrapableItem
                ItemInteractionPacketHandler.OPCODE_USE_WITH_CREATURE,
                ItemInteractionPacketHandler.OPCODE_ROTATE_ITEM,
                ItemInteractionPacketHandler.OPCODE_CONFIGURE_SHOW_OFF_SOCKET,
                ItemInteractionPacketHandler.OPCODE_WRAPABLE_ITEM -> {
                    val command = ItemInteractionPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerUseWithCreature (game.cpp:4853),
                            // Game::playerRotateItem/playerRotatePodium (game.cpp:5104,
                            // 11395), Game::playerConfigureShowOffSocket (game.cpp:5161)
                            // and Game::playerWrapableItem (game.cpp:5361) resolve the
                            // target thing and mutate the world; the item-interaction
                            // engine is not ported, and the item's `isPodium` /
                            // `isWrapable` type state is not modeled, so the decoded
                            // command only marks the confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                // Monster podium: 0x9F parseSetMonsterPodium
                ItemInteractionPacketHandler.OPCODE_SET_MONSTER_PODIUM -> {
                    val command = ItemInteractionPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerSetMonsterPodium (game.cpp:11283) writes the
                            // podium custom attributes and re-sends the tile to
                            // spectators; the podium engine is not ported (PARITY GAP).
                        }
                        true
                    }
                }

                // Text windows: 0x89 parseTextWindow / 0x8A parseHouseWindow
                TextWindowPacketHandler.OPCODE_TEXT_WINDOW,
                TextWindowPacketHandler.OPCODE_HOUSE_WINDOW -> {
                    val command = TextWindowPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerWriteItem (game.cpp:5535) and
                            // Game::playerUpdateHouseWindow (game.cpp:5780) mutate the
                            // written item text and the house access list; neither engine
                            // is ported, and the CREATURE_EVENT_TEXTEDIT callback stays
                            // Lua (AGENTS.md 1.4), so the decoded command only marks the
                            // confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                // Channels: 0x97 playerRequestChannels / 0x98 parseOpenChannel /
                // 0x99 parseCloseChannel / 0x9A parseOpenPrivateChannel /
                // 0x9E playerCloseNpcChannel
                ChannelPacketHandler.OPCODE_REQUEST_CHANNELS,
                ChannelPacketHandler.OPCODE_OPEN_CHANNEL,
                ChannelPacketHandler.OPCODE_CLOSE_CHANNEL,
                ChannelPacketHandler.OPCODE_OPEN_PRIVATE_CHANNEL,
                ChannelPacketHandler.OPCODE_CLOSE_NPC_CHANNEL -> {
                    val command = ChannelPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Channel membership lives in ChatManager, which this context
                            // does not carry (PARITY GAP), and the NPC close-channel
                            // dispatch to Npc::onPlayerCloseChannel (game.cpp:4443) needs
                            // a creature registry the context does not provide (PARITY
                            // GAP); the decoded command only marks the confinement
                            // boundary.
                        }
                        true
                    }
                }

                // Read-and-discard queries: 0x8E parseJoinAggression /
                // 0x9D parseGetTextForReport
                AggressionAndReportPacketHandler.OPCODE_JOIN_AGGRESSION,
                AggressionAndReportPacketHandler.OPCODE_GET_TEXT_FOR_REPORT -> {
                    val command = AggressionAndReportPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Canary reads and discards both payloads with no Game call
                            // (protocolgame.cpp:3299, 3307); the decoded command only
                            // marks the confinement boundary.
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

                // Wave 35 batch B4
                AimAtTargetPacketHandler.OPCODE_AIM_AT_TARGET -> {
                    val command = AimAtTargetPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // ProtocolGame::parseAimAtTarget applies each pair through
                            // Player::updateAimAtTargetSpells (player.cpp:13361); the
                            // aimAtTargetSpellIds set is not ported, so the decoded
                            // command only marks the confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                TransactionDetailsPacketHandler.OPCODE_GET_TRANSACTION_DETAILS -> {
                    val command = TransactionDetailsPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Canary reads and discards the id (protocolgame.cpp:4337)
                            // and the transaction-details sender is not ported; the
                            // decoded command only marks the confinement boundary.
                        }
                        true
                    }
                }

                in ContainerBrowsePacketHandler.OPCODE_LIVESTREAM_VIEWER_CONTAINER..ContainerBrowsePacketHandler.OPCODE_SEEK_IN_CONTAINER -> {
                    val command = ContainerBrowsePacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerBrowseField needs the Game::browseFields
                            // registry, Game::playerSeekInContainer needs
                            // Container::hasPagination/isStoreInbox and the livestream
                            // resend needs the open-container sends; none are ported, so
                            // the decoded command only marks the confinement boundary
                            // (PARITY GAP).
                        }
                        true
                    }
                }

                in InspectionPacketHandler.OPCODE_INSPECTION_OBJECT..InspectionPacketHandler.OPCODE_INSPECT_PLAYER -> {
                    val command = InspectionPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerInspectItem and Player::sendItemInspection
                            // (game.cpp:1635,1652) and sendWeaponProficiencyWindow are
                            // not ported; the decoded command only marks the
                            // confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                BlessingWindowPacketHandler.OPCODE_BLESSING_WINDOW -> {
                    val command = BlessingWindowPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // ProtocolGame::sendBlessingWindow (protocolgame.cpp:5899)
                            // serializes the player blessing state through the protocol
                            // output, which this context does not provide; the decoded
                            // command only marks the confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                OutfitRequestPacketHandler.OPCODE_PLAYER_REQUEST_OUTFIT -> {
                    val command = OutfitRequestPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerRequestOutfit (game.cpp:7206) gates on
                            // ALLOW_CHANGEOUTFIT and calls Player::sendOutfitWindow,
                            // which is not ported; the decoded command only marks the
                            // confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                CyclopediaPacketHandler.OPCODE_CYCLOPEDIA_MAP_ACTION -> {
                    val command = CyclopediaPacketHandler.parseMapAction(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // ProtocolGame::parseCyclopediaMapAction (protocolgame.cpp:3332)
                            // reads and discards its fields; the decoded command only
                            // marks the confinement boundary.
                        }
                        true
                    }
                }

                in VipPacketHandler.OPCODE_ADD_VIP..VipPacketHandler.OPCODE_VIP_GROUP_ACTIONS -> {
                    val command = VipPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerRequestAddVip/RemoveVip/EditVip and
                            // PlayerVIP::addGroup/editGroup/removeGroup mutate
                            // Player::vip(); the ported PlayerVip component
                            // (creatures/players/vip/PlayerVip.kt) is not composed into
                            // Player, so the decoded command only marks the confinement
                            // boundary (PARITY GAP).
                        }
                        true
                    }
                }

                CyclopediaPacketHandler.OPCODE_CYCLOPEDIA_CHARACTER_INFO -> {
                    val command = CyclopediaPacketHandler.parseCharacterInfo(buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerCyclopediaCharacterInfo (game.cpp:9823-9882):
                            // protocolgame.cpp:3040-3042 resolves a zero character id to
                            // the requesting player, another character yields
                            // sendCyclopediaCharacterNoData, the recent deaths / PvP
                            // kills types load the ported PlayerCyclopedia history, and
                            // every other type only has unported senders (PARITY GAP).
                            val characterId = if (command.characterId == 0L) {
                                player.id.toInt().toLong()
                            } else {
                                command.characterId
                            }
                            if (characterId == player.id.toInt().toLong()) {
                                when (command.infoType) {
                                    CyclopediaPacketHandler.CYCLOPEDIA_CHARACTERINFO_RECENT_DEATHS ->
                                        player.features.cyclopedia.loadDeathHistory(
                                            command.page,
                                            command.entriesPerPage,
                                        )

                                    CyclopediaPacketHandler.CYCLOPEDIA_CHARACTERINFO_RECENTPVPKILLS ->
                                        player.features.cyclopedia.loadRecentKills(
                                            command.page,
                                            command.entriesPerPage,
                                        )

                                    else -> Unit
                                }
                            }
                        }
                        true
                    }
                }

                BugReportPacketHandler.OPCODE_BUG_REPORT -> {
                    val command = BugReportPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerReportBug (game.cpp:10193) dispatches the
                            // playerOnReportBug Lua event; the Lua event bridge for
                            // report-bug is not ported, so the decoded command only
                            // marks the confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                OfferDescriptionPacketHandler.OPCODE_OFFER_DESCRIPTION -> {
                    val command = OfferDescriptionPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Canary only logs the offer id (protocolgame.cpp:4258-4261);
                            // the decoded command only marks the confinement boundary.
                        }
                        true
                    }
                }

                PreyPacketHandler.OPCODE_PREY_ACTION -> {
                    val command = PreyPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerPreyAction -> IOPrey::parsePreyAction
                            // (ioprey.cpp:299) over the ported PlayerPreyState engine.
                            // Canary's PREY_ENABLED config gate is not reachable from
                            // the router, and an unknown action value is a no-op like
                            // the C++ default branch.
                            val action = PreyAction.entries.firstOrNull { it.value == command.action }
                            if (action != null) {
                                player.features.preyEngine.parsePreyAction(
                                    player = player.features.prey,
                                    slotId = command.slot,
                                    action = action,
                                    option = PreyOption.entries.firstOrNull { it.value == command.option }
                                        ?: PreyOption.None,
                                    index = command.index,
                                    raceId = command.raceId,
                                )
                            }
                        }
                        true
                    }
                }

                HirelingNamePacketHandler.OPCODE_SET_HIRELING_NAME -> {
                    val command = HirelingNamePacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // ProtocolGame::parseSetHirelingName (protocolgame.cpp:3350)
                            // reads and discards every field; no hireling rename engine
                            // is ported, so the decoded command only marks the
                            // confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                ResourceBalancePacketHandler.OPCODE_SEND_RESOURCE_BALANCE -> {
                    val command = ResourceBalancePacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // ProtocolGame::parseSendResourceBalance (protocolgame.cpp:4305)
                            // answers through the unported balance senders; the decoded
                            // command only marks the confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                NpcGreetPacketHandler.OPCODE_GREET -> {
                    val command = NpcGreetPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // Game::playerNpcGreet (game.cpp:10236) resolves the NPC and
                            // drives its Lua onGreet callback; neither the NPC registry
                            // nor the greet bridge is ported, so the decoded command
                            // only marks the confinement boundary (PARITY GAP).
                        }
                        true
                    }
                }

                ObjectInfoPacketHandler.OPCODE_GET_OBJECT_INFO -> {
                    val command = ObjectInfoPacketHandler.parse(opcode, buffer)
                    if (command == null) {
                        false
                    } else {
                        context.dispatcher.dispatch(DispatcherLane.WorldCommit) {
                            // ProtocolGame::parseGetObjectInfo (protocolgame.cpp:4342)
                            // answers with the unported 0xF4 object-info sender; the
                            // decoded command only marks the confinement boundary
                            // (PARITY GAP).
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

    /**
     * Builds the [MarketOfferContext] over the `Game` seams the market offer
     * handlers (`Game::playerCreateMarketOffer` etc., game.cpp:10717-11096)
     * read; `null` when the context carries no [IOMarket] or [Game].
     */
    private fun marketOfferContext(context: GamePacketContext, player: Player): MarketOfferContext? {
        val game = context.game ?: return null
        val ioMarket = context.ioMarket ?: return null
        return MarketOfferContext(
            player = player,
            ioMarket = ioMarket,
            itemTypes = context.itemTypes,
            resolvePlayer = { playerId -> game.getPlayerByGUID(playerId, true) },
            getBankBalance = { target -> game.getBankBalance(target) },
            setBankBalance = { target, value -> game.setBankBalance(target, value) },
            removeMoney = { target, amount -> game.removeMoney(target, amount, 0, true) },
            isUIExhausted = { target -> game.isUIExhausted(target) },
            updateUIExhausted = { target -> game.updateUIExhausted(target) },
        )
    }
}

data class GameContext(val context: GamePacketContext)
