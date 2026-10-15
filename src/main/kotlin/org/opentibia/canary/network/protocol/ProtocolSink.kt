package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import kotlin.math.abs
import kotlin.math.floor
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.players.ExivaRestrictions
import org.opentibia.canary.creatures.players.PlayerFrameSink
import org.opentibia.canary.creatures.players.PlayerSendSink
import org.opentibia.canary.creatures.players.vip.VipClient
import org.opentibia.canary.creatures.players.vip.VipStatus
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.game.modal.ModalWindow
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.bindings.SpellBindings
import org.opentibia.canary.lua.bindings.core.returnMessage
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.container.ContainerItemWriter
import org.opentibia.canary.network.protocol.creature.CreaturePacketWriter
import org.opentibia.canary.network.protocol.map.TilePacketWriter
import org.opentibia.canary.network.protocol.misc.MiscDataWriter
import org.opentibia.canary.network.protocol.npcshop.NpcShopWindowSerializer
import org.opentibia.canary.network.protocol.player.PlayerWindowWriter
import org.opentibia.canary.network.protocol.proficiency.WeaponProficiencyPayload
import org.opentibia.canary.network.protocol.vip.VipWindowWriter
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Per-player outbound `ProtocolGame` surface used by world state changes.
 *
 * The sink is the port's replacement for `Player::client` (`ProtocolGame*`,
 * player.hpp:1955): state owners (`Game`, `Player`, `Creature`, `Container`,
 * `PlayerVip`) call the same senders Canary calls, and the sink serializes them
 * through the already-ported writers. A `null` [Player.protocolSink] models
 * `Player::client == nullptr`, exactly like the seam properties it feeds.
 *
 * [VipClient] is part of the surface because `PlayerVIP` calls
 * `m_player.client->sendVIP`/`sendVIPGroups`/`sendUpdatedVIPStatus` directly
 * (player_vip.cpp:50, 92, 181, 205, 224) rather than through a `Player::send*`
 * member, so the attached sink is the component's live client.
 *
 * Implementations must be fire-and-forget: `NettyProtocolSink` queues the frame
 * on the channel's event loop, so sink writes never block the WorldCommit lane
 * (AGENTS.md 1.2) and never retain mutable world state.
 */
interface ProtocolSink : VipClient {

    /** `ProtocolGame::sendTextMessage(TextMessage)` (0xB4, protocolgame.cpp:6070). */
    fun sendTextMessage(type: MessageClass, text: String)

    /**
     * `ProtocolGame::sendTextMessage(TextMessage)` (0xB4, protocolgame.cpp:6070)
     * with the full frame: the position/primary/secondary fields the plain
     * `(type, text)` variant leaves at their defaults, e.g. the
     * `MESSAGE_EXPERIENCE_OTHERS` gain notice (creature.cpp:1324-1326).
     */
    fun sendTextMessage(frame: TextMessageFrame)

    /** `Player::sendCancelMessage(ReturnValue)` (player.cpp:2117). */
    fun sendCancelMessage(returnValue: ReturnValue)

    /** `Player::sendCancelMessage(const std::string&)` (player.cpp:2111). */
    fun sendCancelMessage(message: String)

    /** `ProtocolGame::sendCloseContainer(cid)` (0x6F, protocolgame.cpp:7981). */
    fun sendCloseContainer(cid: Int)

    /** `Player::sendCreatureAppear(creature, pos, isLogin)` (player.cpp:8785). */
    fun sendCreatureAppear(creature: Creature, position: Position, isLogin: Boolean)

    /** `Player::sendRemoveTileThing(pos, stackpos)` (player.cpp:7076). */
    fun sendRemoveTileThing(position: Position, stackpos: Int)

    /** `Player::sendCreatureMove(creature, newPos, newStackPos, oldPos, oldStackPos, teleport)` (player.cpp:8803). */
    fun sendCreatureMove(
        creature: Creature,
        newPos: Position,
        newStackPos: Int,
        oldPos: Position,
        oldStackPos: Int,
        teleport: Boolean,
    )

    /** `Player::sendCreatureHealth(creature)` (player.cpp:2139). */
    fun sendCreatureHealth(creature: Creature)

    /** `Player::sendCreatureChangeOutfit(creature, outfit)` (player.cpp:8861). */
    fun sendCreatureOutfit(creature: Creature, outfit: Outfit)

    /** `Player::sendChangeSpeed(creature, stepSpeed)` (player.hpp:1097). */
    fun sendChangeSpeed(creature: Creature, stepSpeed: Int)

    /** `Player::sendStats()` (player.cpp:2347). */
    fun sendStats(player: Player)

    /** `Player::sendSkills()` (player.cpp:2366). */
    fun sendSkills(player: Player)

    /** `Player::sendInventoryItem(slot, item)` (player.cpp:9281). */
    fun sendInventoryItem(slot: Slots_t, item: Item?)

    /** `Player::sendContainer(cid, container, hasParent, firstIndex)` (player.cpp:9134). */
    fun sendContainer(cid: Int, container: Container, hasParent: Boolean, firstIndex: Int)

    /** `Player::sendAddContainerItem(container, item)` (player.cpp:9056). */
    fun sendAddContainerItem(cid: Int, slot: Int, item: Item)

    /** `Player::sendUpdateContainerItem(container, slot, newItem)` (player.cpp:9087). */
    fun sendUpdateContainerItem(cid: Int, slot: Int, item: Item)

    /** `Player::sendRemoveContainerItem(container, slot)` (player.cpp:9110). */
    fun sendRemoveContainerItem(cid: Int, slot: Int, lastItem: Item?)

    /**
     * `Player::sendModalWindow(const ModalWindow&)` (player.cpp:9021-9028) ->
     * `ProtocolGame::sendModalWindow` (0xFA, protocolgame.cpp:9630-9659).
     */
    fun sendModalWindow(modalWindow: ModalWindow)

    /**
     * `Player::sendSaleItemList(inventoryMap)` (player.cpp:2408-2412) ->
     * `ProtocolGame::sendSaleItemList` (0xEE balances + 0x7B list,
     * protocolgame.cpp:6669-6736). The `client && shopOwner` gate and the
     * owner's shop-vector resolution stay with the sink implementation.
     */
    fun sendSaleItemList(inventoryMap: Map<Int, Int>)

    /**
     * `ProtocolGame::sendWeaponProficiencyWindow(weaponId)`
     * (0xC4, protocolgame.cpp:12329-12359). `weaponId == 0` resolves
     * `Player::getWeaponId(true)` like Canary; [sendDetailList] is the
     * `shouldSendWeaponProficiencyDetailList` trailing-list gate the caller
     * resolves from the client build string.
     */
    fun sendWeaponProficiencyWindow(weaponId: Int, sendDetailList: Boolean = false)

    /**
     * `ProtocolGame::sendWeaponProficiency(weaponId)` (0x5C,
     * protocolgame.cpp:12313-12327). `weaponId == 0` resolves
     * `Player::getWeaponId(true)`; the trailing frame always accompanies the
     * `0xB3` window response.
     */
    fun sendWeaponProficiency(weaponId: Int)

    /**
     * `ProtocolGame::sendCreatureHelpers(creatureId, helpers)` (0x94,
     * protocolgame.cpp:10928-10938). The `oldProtocol` early return lives in the
     * session transport ([NettyProtocolSink]); the `Player::client != nullptr`
     * gate is the sink itself (player.cpp:1820-1823). Transports without the
     * negotiated profile stay silent.
     */
    fun sendCreatureHelpers(creatureId: Long, helpers: Int) {
    }

    /**
     * `ProtocolGame::sendExivaRestrictions(isLogin, added..., removed...)`
     * (0xCA, protocolgame.cpp:12252-12312). Login mode resolves the stored
     * player/guild whitelists into names; update mode echoes the added/removed
     * name lists a `parseExivaRestrictions` apply produced. The transports
     * without a session whitelist resolver keep the empty default, and a
     * `ProtocolGame::oldProtocol`/`player == nullptr` gate stays with the caller.
     */
    fun sendExivaRestrictions(
        isLogin: Boolean = false,
        addedPlayerNames: List<String> = emptyList(),
        removedPlayerNames: List<String> = emptyList(),
        addedGuildNames: List<String> = emptyList(),
        removedGuildNames: List<String> = emptyList(),
    ) {
    }
}

/**
 * `ProtocolGame::sendAddCreature` self-branch inputs
 * (src/server/network/protocol/protocolgame.cpp:8641-8682) resolved from the
 * session protocol state: the negotiated client `version` behind the
 * `>= 980`/`>= 981`/`>= 1054`/`>= 1058`/`>= 1080` gates, the
 * `ProtocolFeature::LoginSpeedFormula` speed-formula doubles, the expert-PvP
 * controls gate (`ExpertPvp::isEnabled()` plus `ExpertPvpModeByte`), the
 * `g_configManager()` STORE_IMAGES_URL/STORE_COIN_PACKET pair, the
 * `ProtocolFeature::OldProtocolCompat` arm that skips the store/exiva tail and
 * the `Game::getWorldType() == WORLD_TYPE_NO_PVP` exiva-button value.
 *
 * The defaults describe the port's `Current` profile with the Canary config
 * defaults (`coinImagesURL = ""`, `coinPacketSize = 25`; configmanager.cpp:359,
 * 410) and no exiva, exactly like a directly constructed transport.
 */
data class SelfAppearTail(
    val clientVersion: Int = CLIENT_VERSION,
    val loginSpeedFormula: Boolean = true,
    val expertPvpControlsEnabled: Boolean = false,
    val storeImagesUrl: String = "",
    val storeCoinPacket: Int = STORE_COIN_PACKET_DEFAULT,
    val oldProtocol: Boolean = false,
    val exivaEnabled: Boolean = false,
    /**
     * `hasInlineBugReportFlag` (protocolgame.cpp:8653-8656):
     * `oldProtocol && (version >= 1054 || ProtocolFeature::InlineLoginBugReportFlag)`.
     */
    val inlineBugReportFlag: Boolean = false,
    /** `Player::getAccountType() >= ACCOUNT_TYPE_NORMAL` byte value (protocolgame.cpp:8654-8658). */
    val bugReportEnabled: Boolean = true,
)

/** `STORE_COIN_PACKET` config default (`loadIntConfig(..., 25)`, configmanager.cpp:359). */
const val STORE_COIN_PACKET_DEFAULT: Int = 25

/**
 * Shared frame serialization for every [ProtocolSink] transport: the senders
 * resolve live entity state into the ported frame types, allocate one `ByteBuf`
 * per frame from [frameAllocator] and hand it to [writeFrame], which owns the
 * buffer from that point on.
 */
abstract class ProtocolFrameSink(
    protected val player: Player,
    protected val map: GameMap,
    /** `g_game().getCreatureByID` behind the known-set eviction (`protocolgame.cpp:2346`). */
    creatureById: (Long) -> Creature? = { null },
    /**
     * `Player::getShopOwner` (player.cpp:6123-6125) behind
     * `Player::sendSaleItemList`'s `client && shopOwner` gate (player.cpp:2408).
     * `null` models a player without an open shop window.
     */
    private val shopOwner: (Player) -> Npc? = { null },
    /**
     * `WeaponProficiency::isUpgradeAvailable(weaponId)` resolver for the 0x5C
     * frame (weapon_proficiency.cpp:823-864); the transports without the
     * `proficiencies.json` registry report `false` like the C++ missing set.
     */
    private val weaponProficiencyUpgradeAvailable: (Int) -> Boolean = { false },
    /** `IOLoginData::getNameByGuid` behind the login-mode 0xCA whitelist (protocolgame.cpp:12273). */
    private val playerNameByGuid: (Long) -> String = { "" },
    /** `g_game().getGuild(id, true)->getName()` behind the login-mode 0xCA whitelist (protocolgame.cpp:12292). */
    private val guildNameById: (Long) -> String = { "" },
) : ProtocolSink {

    /**
     * `ProtocolGame::knownCreatureSet` (`protocolgame.hpp:620`) with the
     * `checkCreatureAsKnown` and `contains` semantics; the eviction visibility
     * test is [canSee].
     */
    protected val knownCreatureSet = KnownCreatureSet(player, creatureById) { canSee(it) }

    /** Allocator for one outgoing frame; the Netty sink returns the channel allocator. */
    protected abstract fun frameAllocator(): ByteBufAllocator

    /** Consumes (and releases) one serialized frame. Fire-and-forget. */
    protected abstract fun writeFrame(frame: ByteBuf)

    private inline fun emit(build: ByteBuf.() -> Unit) {
        val out = frameAllocator().buffer()
        out.build()
        writeFrame(out)
    }

    override fun sendTextMessage(type: MessageClass, text: String) {
        emit { GameWindowWriter.writeTextMessage(this, TextMessageFrame(type, text)) }
    }

    override fun sendTextMessage(frame: TextMessageFrame) {
        emit { GameWindowWriter.writeTextMessage(this, frame) }
    }

    override fun sendCancelMessage(returnValue: ReturnValue) {
        sendCancelMessage(returnMessage(returnValue.ordinal))
    }

    override fun sendCancelMessage(message: String) {
        emit { PlayerWindowWriter.writeCancelMessage(this, message) }
    }

    override fun sendCloseContainer(cid: Int) {
        emit { SocialWindowWriter.writeCloseContainer(this, cid) }
    }

    override fun sendCreatureAppear(creature: Creature, position: Position, isLogin: Boolean) {
        if (!canSee(position)) {
            return
        }

        val tile = creature.getTile() ?: return
        val stackpos = tile.getClientIndexOfCreature(player, creature)
        if (stackpos < 0 || stackpos >= PROTOCOL_MAX_STACKPOS) {
            // `Player::sendCreatureAppear` falls back to `sendUpdateTile`
            // (player.cpp:8793). The transports without the extended
            // `PlayerSendSink.sendUpdateTile` sender refresh the viewport
            // through the same ported tile-update frame instead of dropping the
            // appear silently.
            emit { TilePacketWriter.writeUpdateTile(this, player, tile, position) { appearanceOf(it) } }
            return
        }

        if (creature === player) {
            // `ProtocolGame::sendAddCreature`'s `creature == player` branch
            // (protocolgame.cpp:8639-8682): the self appear frame instead of
            // the 0x6A add-creature frame, so the logging-in player's own
            // placement fanout emits the login frame exactly once. The
            // delegated session transports override [sendCreatureAppear] to
            // resolve their negotiated [SelfAppearTail]; this base transport
            // keeps the current-profile defaults.
            emit { GameWindowWriter.writeSelfAddCreature(this, player.id) }
            return
        }

        emit { GameWindowWriter.writeAddCreature(this, position, stackpos, appearanceOf(creature)) }
    }

    override fun sendRemoveTileThing(position: Position, stackpos: Int) {
        emit { GameWindowWriter.writeRemoveTileThing(this, position, stackpos) }
    }

    override fun sendCreatureMove(
        creature: Creature,
        newPos: Position,
        newStackPos: Int,
        oldPos: Position,
        oldStackPos: Int,
        teleport: Boolean,
    ) {
        emit {
            CreaturePacketWriter.writeMoveCreature(
                this,
                map,
                player,
                creature,
                newPos,
                newStackPos,
                oldPos,
                oldStackPos,
                teleport,
            ) { appearanceOf(it) }
        }
    }

    override fun sendCreatureHealth(creature: Creature) {
        emit {
            CreatureWindowWriter.writeCreatureHealth(
                this,
                creature.id,
                creature.health,
                creature.maxHealth,
            )
        }
    }

    override fun sendCreatureOutfit(creature: Creature, outfit: Outfit) {
        emit {
            CreatureWindowWriter.writeCreatureOutfit(
                this,
                creature.id,
                outfit,
                creature.isWearingSupportOutfit(),
            )
        }
    }

    override fun sendChangeSpeed(creature: Creature, stepSpeed: Int) {
        emit { CreatureWindowWriter.writeChangeSpeed(this, creature.id, creature.getBaseSpeed(), stepSpeed) }
    }

    override fun sendStats(player: Player) {
        emit { GameWindowWriter.writePlayerStats(this, statsFrame(player)) }
    }

    override fun sendSkills(player: Player) {
        emit { GameWindowWriter.writePlayerSkills(this, skillsFrame(player)) }
    }

    override fun sendInventoryItem(slot: Slots_t, item: Item?) {
        emit { GameWindowWriter.writeInventoryItem(this, slot, item) }
    }

    override fun sendContainer(cid: Int, container: Container, hasParent: Boolean, firstIndex: Int) {
        emit {
            GameWindowWriter.writeContainer(
                this,
                container,
                ContainerWindowFrame(cid, container.getName(), hasParent, firstIndex),
            )
        }
    }

    override fun sendAddContainerItem(cid: Int, slot: Int, item: Item) {
        emit { ContainerItemWriter.writeAddContainerItem(this, cid, slot, item) }
    }

    override fun sendUpdateContainerItem(cid: Int, slot: Int, item: Item) {
        emit { ContainerItemWriter.writeUpdateContainerItem(this, cid, slot, item) }
    }

    override fun sendRemoveContainerItem(cid: Int, slot: Int, lastItem: Item?) {
        emit { ContainerItemWriter.writeRemoveContainerItem(this, cid, slot, lastItem) }
    }

    override fun sendModalWindow(modalWindow: ModalWindow) {
        emit {
            SocialWindowWriter.writeModalWindow(
                this,
                SocialWindowWriter.ModalWindowFrame(
                    id = modalWindow.id,
                    title = modalWindow.title,
                    message = modalWindow.message,
                    buttons = modalWindow.buttons.map { (label, id) ->
                        SocialWindowWriter.ModalWindowButton(id = id, label = label)
                    },
                    choices = modalWindow.choices.map { (label, id) ->
                        SocialWindowWriter.ModalWindowButton(id = id, label = label)
                    },
                    defaultEscapeButton = modalWindow.defaultEscapeButton,
                    defaultEnterButton = modalWindow.defaultEnterButton,
                    priority = modalWindow.priority,
                ),
            )
        }
    }

    // --- shop / proficiency ------------------------------------------------------

    /**
     * `Player::sendSaleItemList(inventoryMap)` (player.cpp:2408-2412):
     * `if (client && shopOwner)` then
     * `client->sendSaleItemList(shopOwner->getShopItemVector(getGUID()), inventoryMap)`.
     */
    override fun sendSaleItemList(inventoryMap: Map<Int, Int>) {
        emitSaleItemList(player, shopOwner(player), inventoryMap) { build -> emit(build) }
    }

    /**
     * `ProtocolGame::sendWeaponProficiencyWindow` (protocolgame.cpp:12329-12359):
     * `weaponId == 0` resolves the equipped weapon and the experience/perks come
     * from the player's per-weapon proficiency state
     * (`Player::weaponProficiency().getExperience/getSelectedPerks`).
     */
    override fun sendWeaponProficiencyWindow(weaponId: Int, sendDetailList: Boolean) {
        val resolvedWeaponId = if (weaponId > 0) weaponId else player.getWeaponId(true)
        val proficiency = player.features.weaponProficiencies[resolvedWeaponId]

        emit {
            WeaponProficiencyPayload.writeWeaponProficiencyWindow(
                this,
                weaponId = resolvedWeaponId,
                experience = proficiency?.experience ?: 0,
                selectedPerks = proficiency?.perks ?: emptyList(),
                sendDetailList = sendDetailList,
            )
        }
    }

    /**
     * `ProtocolGame::sendWeaponProficiency` (protocolgame.cpp:12313-12327):
     * `weaponId == 0` resolves the equipped weapon and the experience comes from
     * the player's per-weapon proficiency state. The `oldProtocol` early return
     * (protocolgame.cpp:12314) stays with the session caller, whose `0xB3` parse
     * gate already suppresses the frame.
     */
    override fun sendWeaponProficiency(weaponId: Int) {
        val resolvedWeaponId = if (weaponId > 0) weaponId else player.getWeaponId(true)
        val experience = player.features.weaponProficiencies[resolvedWeaponId]?.experience ?: 0
        val upgradeAvailable = resolvedWeaponId > 0 && weaponProficiencyUpgradeAvailable(resolvedWeaponId)

        emit {
            WeaponProficiencyPayload.writeWeaponProficiency(
                this,
                weaponId = resolvedWeaponId,
                experience = experience,
                upgradeAvailable = upgradeAvailable,
            )
        }
    }

    /**
     * `ProtocolGame::sendExivaRestrictions` (protocolgame.cpp:12252-12312):
     * login mode resolves the stored guid/id whitelists through the injected
     * `IOLoginData::getNameByGuid` / `Game::getGuild(id, true)` providers, update
     * mode echoes the added/removed name lists the apply produced.
     */
    override fun sendExivaRestrictions(
        isLogin: Boolean,
        addedPlayerNames: List<String>,
        removedPlayerNames: List<String>,
        addedGuildNames: List<String>,
        removedGuildNames: List<String>,
    ) {
        val restrictions = player.getExivaRestrictions()
        val flags = exivaFlags(restrictions)

        if (isLogin) {
            emit {
                MiscDataWriter.writeExivaRestrictions(
                    this,
                    flags,
                    isLogin = true,
                    playerWhitelistNames = restrictions.playerWhitelist.map(playerNameByGuid),
                    guildWhitelistNames = restrictions.guildWhitelist.map(guildNameById),
                )
            }
            return
        }

        emit {
            MiscDataWriter.writeExivaRestrictions(
                this,
                flags,
                isLogin = false,
                addedPlayerNames = addedPlayerNames,
                removedPlayerNames = removedPlayerNames,
                addedGuildNames = addedGuildNames,
                removedGuildNames = removedGuildNames,
            )
        }
    }

    // --- VIP sends ---------------------------------------------------------------

    /**
     * `ProtocolGame::sendVIP` (protocolgame.cpp:9374-9405) current-profile
     * branch: the group ids come from the attached player's `PlayerVIP`
     * (`player->vip().getGroupsIdGuidBelongs(guid)`).
     */
    override fun sendVip(guid: Long, name: String, description: String, icon: Int, notify: Boolean, status: VipStatus) {
        val groups = player.features.vip.getGroupsIdGuidBelongs(guid)
        emit {
            SocialWindowWriter.writeVip(
                this,
                SocialWindowWriter.VipEntry(
                    guid = guid,
                    name = name,
                    description = description,
                    icon = icon.toLong(),
                    notify = notify,
                    status = status,
                ),
                groups,
            )
        }
    }

    /** `ProtocolGame::sendVIPGroups` (protocolgame.cpp:9407-9425) current-profile branch. */
    override fun sendVipGroups() {
        val vip = player.features.vip
        val groups = vip.getGroups().map {
            SocialWindowWriter.VipGroup(id = it.id, name = it.name, customizable = it.customizable)
        }
        val maxGroupEntries = vip.getMaxGroupEntries()
        emit { SocialWindowWriter.writeVipGroups(this, groups, maxGroupEntries) }
    }

    /** `ProtocolGame::sendUpdatedVIPStatus` (protocolgame.cpp:9355-9372). */
    override fun sendUpdatedVipStatus(guid: Long, newStatus: VipStatus) {
        emit { VipWindowWriter.writeUpdatedVipStatus(this, guid, newStatus) }
    }

    /**
     * `ProtocolGame::canSee(const std::shared_ptr<Creature>&)`
     * (protocolgame.cpp:2375-2390): removed creatures, creatures the player
     * cannot see and out-of-viewport positions are not visible. The
     * `Player::canSeeCreature` ghost-mode branch is a Player-side parity gap
     * (the port's `Player` inherits the base `Creature::canSeeCreature`).
     */
    protected fun canSee(creature: Creature?): Boolean {
        if (creature == null || creature.isRemoved()) {
            return false
        }

        if (!player.canSeeCreature(creature)) {
            return false
        }

        return canSee(creature.position)
    }

    /** `ProtocolGame::canSee(const Position&)` (protocolgame.cpp:2392-2394). */
    protected fun canSee(pos: Position): Boolean = canSee(pos.x, pos.y, pos.z)

    /** `ProtocolGame::canSee(x, y, z)` (protocolgame.cpp:2396-2411). */
    protected fun canSee(x: Int, y: Int, z: Int): Boolean = protocolCanSeePosition(player, x, y, z)

    /**
     * Resolved `ProtocolGame::AddCreature` input for the observer with the
     * session known-set outputs of `ProtocolGame::checkCreatureAsKnown`
     * (protocolgame.cpp:2334): the first frame for a creature carries
     * `known = false` plus the evicted `removedKnown`, every later frame
     * `known = true`.
     */
    protected fun appearanceOf(creature: Creature): CreatureAppearanceFrame {
        val known = knownCreatureSet.checkCreatureAsKnown(creature.id)
        return appearanceOf(creature, known.known, known.removedKnown)
    }

    /**
     * Resolved `ProtocolGame::AddCreature` input with explicit `known` /
     * `removedKnown` out-parameter values for call sites that pass them without
     * touching the set (`ProtocolGame::sendCreatureEmblem`'s
     * `AddCreature(creature, false, creature->getID())` and
     * `ProtocolGame::reloadCreature`'s known branch). The health-hidden,
     * guild-emblem, speech-bubble, creature-mark and walkthrough fields that
     * have no Kotlin state use the Canary neutral values (PARITY GAPS).
     */
    protected fun appearanceOf(
        creature: Creature,
        known: Boolean,
        removedKnown: Long,
    ): CreatureAppearanceFrame {
        val master = creature.getMaster()
        return CreatureAppearanceFrame(
            id = creature.id,
            name = creature.name,
            health = creature.health,
            maxHealth = creature.maxHealth,
            direction = creature.direction,
            outfit = creature.getCurrentOutfit(),
            light = creature.getCreatureLight(),
            stepSpeed = creature.getStepSpeed(),
            creatureType = when (creature) {
                is Player -> CreatureType.PLAYER
                is Monster -> CreatureType.MONSTER
                is Npc -> CreatureType.NPC
                else -> CreatureType.MONSTER
            },
            known = known,
            removedKnown = removedKnown,
            masterId = master?.id,
            masterIsPlayer = master is Player,
            vocationClientId = (creature as? Player)?.getPlayerVocationEnum() ?: 0,
            skull = player.pvpPlayer.getSkullClient(creature).value,
            partyShield = player.getPartyShield(creature as? Player).value,
            accessPlayer = player.isAccessPlayer(),
            isInvisibleOrGhost = creature.isInvisible(),
            icons = creature.getIcons().map { CreatureIcon(it.serialize(), it.category.value, it.count) },
        )
    }

    /** `ProtocolGame::AddPlayerStats` input (protocolgame.cpp:9782-9877). */
    protected fun statsFrame(player: Player): PlayerStatsFrame = PlayerStatsFrame(
        health = player.health,
        maxHealth = player.maxHealth,
        freeCapacity = player.getFreeCapacity().toInt(),
        experience = player.experience,
        level = player.level,
        levelPercent = player.getLevelPercent().toDouble(),
        baseXpGain = 0,
        displayGrindingXpBoost = 0,
        displayXpBoostPercent = 0,
        staminaXpBoost = player.staminaXpBoost,
        mana = player.mana,
        maxMana = player.maxMana,
        soul = 0,
        staminaMinutes = 0,
        baseSpeed = player.getBaseSpeed(),
    )

    /**
     * `ProtocolGame::AddPlayerSkills` input (protocolgame.cpp:9879-10050). The
     * current profile writes the seven `SKILL_FIRST..SKILL_FISHING` slots; the
     * weapon block is the `weapon == nullptr` fist branch and every equipment,
     * imbuement, forge or Wheel modifier the port does not model stays at its
     * neutral value (PARITY GAPS).
     */
    protected fun skillsFrame(player: Player): PlayerSkillsFrame {
        val entries = (skills_t.SKILL_FIST.value..skills_t.SKILL_FISHING.value).map { value ->
            val skill = skills_t.fromValue(value)!!
            SkillEntry(
                level = player.getSkillLevel(skill),
                base = player.getBaseSkill(skill),
                loyalty = player.getLoyaltySkill(skill),
                percent = player.getSkillPercent(skill),
            )
        }

        return PlayerSkillsFrame(
            magicLevel = player.getMagicLevel(),
            baseMagicLevel = player.getBaseMagicLevel(),
            loyaltyMagicLevel = player.getLoyaltyMagicLevel(),
            magicLevelPercent = 0.0,
            skills = entries,
            capacity = player.capacity.toInt(),
            baseCapacity = player.capacity.toInt(),
            flatBonus = 0,
            weaponAttack = fistAttackTotal(player),
            lifeLeechSkill = 0,
            manaLeechSkill = 0,
            baseCriticalChance = 0.0,
            baseCriticalDamage = 0.0,
            criticalHitChanceSkill = 0,
            criticalHitDamageSkill = 0,
            onslaught = 0.0,
            defense = 0,
            armor = 0,
            partyMantra = 0,
            mitigation = 0.0,
            dodge = 0.0,
            reflectFlat = 0,
            absorbs = emptyList(),
            momentum = 0.0,
            transcendence = 0.0,
            amplification = 0.0,
        )
    }

    /** `Player::attackTotal(flatBonus, 7, fistSkill)` for the no-weapon branch (player.cpp:622). */
    private fun fistAttackTotal(player: Player): WeaponAttack {
        val flatBonus = 0
        val equipment = 7
        val fightFactor = floor(
            when (player.fightMode) {
                FightMode.Attack -> 1.2 * equipment
                FightMode.Balanced -> 1.0 * equipment
                FightMode.Defense -> 0.6 * equipment
            },
        )
        val skillFactor = (player.getSkillLevel(skills_t.SKILL_FIST) + 4) / 28.0
        return WeaponAttack.Fist((flatBonus + fightFactor * skillFactor).toInt())
    }
}

/** `MAX_STACKPOS` (`src/server/network/protocol/protocolgame.hpp`). */
internal const val PROTOCOL_MAX_STACKPOS: Int = 10

/** `ExivaRestrictions` flag bytes (`player.hpp`, protocolgame.cpp:12261-12267). */
internal fun exivaFlags(restrictions: ExivaRestrictions): MiscDataWriter.ExivaRestrictionFlags =
    MiscDataWriter.ExivaRestrictionFlags(
        allowAll = if (restrictions.allowAll) 1 else 0,
        allowOwnGuild = if (restrictions.allowOwnGuild) 1 else 0,
        allowOwnParty = if (restrictions.allowOwnParty) 1 else 0,
        allowVipList = if (restrictions.allowVipList) 1 else 0,
        allowPlayerWhitelist = if (restrictions.allowPlayerWhitelist) 1 else 0,
        allowGuildWhitelist = if (restrictions.allowGuildWhitelist) 1 else 0,
    )

/** `ProtocolGame::canSee(x, y, z)` (protocolgame.cpp:2396-2411). */
internal fun protocolCanSeePosition(player: Player, x: Int, y: Int, z: Int): Boolean {
    val myPos = player.position
    if (myPos.z <= GameMap.MAP_INIT_SURFACE_LAYER) {
        // We are on ground level or above (7 -> 0): the view is 7 -> 0.
        if (z > GameMap.MAP_INIT_SURFACE_LAYER) {
            return false
        }
    } else if (myPos.z >= GameMap.MAP_INIT_SURFACE_LAYER + 1) {
        // We are underground (8 -> 15): the view is +/- 2 floors.
        if (abs(myPos.z - z) > GameMap.MAP_LAYER_VIEW_LIMIT) {
            return false
        }
    }

    // A negative offset means the action takes place on a lower floor.
    val offsetz = myPos.z - z
    return x >= myPos.x - GameMap.MAP_MAX_CLIENT_VIEW_PORT_X + offsetz &&
        x <= myPos.x + (GameMap.MAP_MAX_CLIENT_VIEW_PORT_X + 1) + offsetz &&
        y >= myPos.y - GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + offsetz &&
        y <= myPos.y + (GameMap.MAP_MAX_CLIENT_VIEW_PORT_Y + 1) + offsetz
}

/**
 * `Player::sendSaleItemList(inventoryMap)` emission shared by the sink
 * transports: the `client && shopOwner` gate (player.cpp:2408) plus the
 * owner's shop-vector/currency resolution into
 * [NpcShopWindowSerializer.writeSaleItemListFrames].
 */
internal fun emitSaleItemList(
    player: Player,
    shopOwner: Npc?,
    inventoryMap: Map<Int, Int>,
    emit: (ByteBuf.() -> Unit) -> Unit,
) {
    if (shopOwner == null) {
        return
    }

    NpcShopWindowSerializer.writeSaleItemListFrames(
        emit = emit,
        shopBlocks = shopOwner.getShopItemVector(player.getGUID()),
        inventoryMap = inventoryMap,
        bankBalance = player.getBankBalance(),
        currency = shopOwner.getCurrency(),
    )
}

/**
 * [PlayerSendSink] over a live Netty channel: the extended `Player::client`
 * surface `GameHandler` installs on login. The player senders delegate to a
 * [PlayerFrameSink] sharing this channel's allocator and writer, so both the
 * base and the extended frames are serialized by the ported writers and queued
 * with `writeAndFlush`; the caller never blocks and the channel's pipeline
 * (XTEA/checksum encoder) owns encryption. A closed channel releases the frame
 * and drops the write.
 *
 * The `sendBasicData` spell list resolves through the session's attached
 * `g_spells()` registry ([basicDataSpellsOf]), exactly like the spell-registry
 * consumers of the login/window senders.
 *
 * [sendSaleItemList] and the self `sendCreatureAppear` branch are overridden
 * instead of delegated because the shop-owner and negotiated [SelfAppearTail]
 * providers cannot reach the delegated [PlayerFrameSink]'s `ProtocolFrameSink`
 * constructor (that surface lives outside this module's ownership); the frame
 * sequences themselves stay in [emitSaleItemList] and
 * [GameWindowWriter.writeSelfAddCreature].
 */
class NettyProtocolSink private constructor(
    private val channel: Channel,
    private val player: Player,
    private val delegate: PlayerFrameSink,
    /** `Player::getShopOwner` (player.cpp:6123-6125) behind `sendSaleItemList`. */
    private val shopOwner: (Player) -> Npc?,
    /** `ProtocolGame` session state behind the self appear frame (protocolgame.cpp:8641-8682). */
    private val selfAppearTail: SelfAppearTail,
    /** `WeaponProficiency::isUpgradeAvailable(weaponId)` for the 0x5C frame. */
    private val weaponProficiencyUpgradeAvailable: (Int) -> Boolean,
    /** `IOLoginData::getNameByGuid` behind the login-mode 0xCA whitelist (protocolgame.cpp:12273). */
    private val playerNameByGuid: (Long) -> String,
    /** `g_game().getGuild(id, true)->getName()` behind the login-mode 0xCA whitelist (protocolgame.cpp:12292). */
    private val guildNameById: (Long) -> String,
) : PlayerSendSink by delegate {

    constructor(
        channel: Channel,
        player: Player,
        map: GameMap,
        /** `g_game().getCreatureByID` behind the known-set eviction (protocolgame.cpp:2346). */
        creatureById: (Long) -> Creature? = { null },
        /** `Player::getShopOwner` (player.cpp:6123-6125) behind `sendSaleItemList`. */
        shopOwner: (Player) -> Npc? = { null },
        /** The negotiated session tail of the self appear frame. */
        selfAppearTail: SelfAppearTail = SelfAppearTail(),
        /** `WeaponProficiency::isUpgradeAvailable(weaponId)` for the 0x5C frame. */
        weaponProficiencyUpgradeAvailable: (Int) -> Boolean = { false },
        /** `IOLoginData::getNameByGuid` behind the login-mode 0xCA whitelist. */
        playerNameByGuid: (Long) -> String = { "" },
        /** `g_game().getGuild(id, true)->getName()` behind the login-mode 0xCA whitelist. */
        guildNameById: (Long) -> String = { "" },
    ) : this(
        channel,
        player,
        PlayerFrameSink(
            player,
            map,
            frameAllocatorProvider = { channel.alloc() },
            frameWriter = { frame ->
                if (channel.isActive) {
                    channel.writeAndFlush(frame)
                } else {
                    frame.release()
                }
            },
            basicDataSpells = { basicDataSpellsOf(player) },
            creatureById = creatureById,
        ),
        shopOwner,
        selfAppearTail,
        weaponProficiencyUpgradeAvailable,
        playerNameByGuid,
        guildNameById,
    )

    /** Queues one frame on the session channel (fire-and-forget; closed channel releases). */
    private inline fun emitSessionFrame(build: ByteBuf.() -> Unit) {
        val frame = channel.alloc().buffer()
        frame.build()
        if (channel.isActive) {
            channel.writeAndFlush(frame)
        } else {
            frame.release()
        }
    }

    /**
     * `ProtocolGame::sendAddCreature`'s `creature == player` branch resolved
     * with this session's negotiated [SelfAppearTail]. Every other appear path
     * delegates to the shared [PlayerFrameSink] so the known-creature
     * bookkeeping stays single-sourced; a self appear that fails the
     * viewport/tile guard also delegates, which emits the same
     * `sendUpdateTile` fallback Canary uses (player.cpp:8791-8794).
     */
    override fun sendCreatureAppear(creature: Creature, position: Position, isLogin: Boolean) {
        if (creature === player && protocolCanSeePosition(player, position.x, position.y, position.z)) {
            val tile = creature.getTile()
            val stackpos = tile?.getClientIndexOfCreature(player, creature) ?: -1
            if (stackpos in 0 until PROTOCOL_MAX_STACKPOS) {
                val frame = channel.alloc().buffer()
                GameWindowWriter.writeSelfAddCreature(frame, player.id, selfAppearTail)
                if (channel.isActive) {
                    channel.writeAndFlush(frame)
                } else {
                    frame.release()
                }
                return
            }
        }
        delegate.sendCreatureAppear(creature, position, isLogin)
    }

    override fun sendSaleItemList(inventoryMap: Map<Int, Int>) {
        emitSaleItemList(player, shopOwner(player), inventoryMap) { build -> emitSessionFrame(build) }
    }

    /**
     * `ProtocolGame::sendCreatureHelpers` (protocolgame.cpp:10928-10938): the
     * `oldProtocol` early return is the session's negotiated profile.
     */
    override fun sendCreatureHelpers(creatureId: Long, helpers: Int) {
        if (!selfAppearTail.oldProtocol) {
            return
        }

        emitSessionFrame { CreaturePacketWriter.writeCreatureHelpers(this, creatureId, helpers) }
    }

    /**
     * `ProtocolGame::sendWeaponProficiency` (protocolgame.cpp:12313-12327) over
     * this session's `isUpgradeAvailable` resolver.
     */
    override fun sendWeaponProficiency(weaponId: Int) {
        val resolvedWeaponId = if (weaponId > 0) weaponId else player.getWeaponId(true)
        val experience = player.features.weaponProficiencies[resolvedWeaponId]?.experience ?: 0
        val upgradeAvailable = resolvedWeaponId > 0 && weaponProficiencyUpgradeAvailable(resolvedWeaponId)

        emitSessionFrame {
            WeaponProficiencyPayload.writeWeaponProficiency(
                this,
                weaponId = resolvedWeaponId,
                experience = experience,
                upgradeAvailable = upgradeAvailable,
            )
        }
    }

    /**
     * `ProtocolGame::sendExivaRestrictions` (protocolgame.cpp:12252-12312) with
     * this session's `IOLoginData::getNameByGuid` / `Game::getGuild` resolvers.
     */
    override fun sendExivaRestrictions(
        isLogin: Boolean,
        addedPlayerNames: List<String>,
        removedPlayerNames: List<String>,
        addedGuildNames: List<String>,
        removedGuildNames: List<String>,
    ) {
        val restrictions = player.getExivaRestrictions()
        val flags = exivaFlags(restrictions)

        if (isLogin) {
            emitSessionFrame {
                MiscDataWriter.writeExivaRestrictions(
                    this,
                    flags,
                    isLogin = true,
                    playerWhitelistNames = restrictions.playerWhitelist.map(playerNameByGuid),
                    guildWhitelistNames = restrictions.guildWhitelist.map(guildNameById),
                )
            }
            return
        }

        emitSessionFrame {
            MiscDataWriter.writeExivaRestrictions(
                this,
                flags,
                isLogin = false,
                addedPlayerNames = addedPlayerNames,
                removedPlayerNames = removedPlayerNames,
                addedGuildNames = addedGuildNames,
                removedGuildNames = removedGuildNames,
            )
        }
    }
}

/**
 * `ProtocolGame::sendBasicData`'s valid spell list (protocolgame.cpp:5851-5883):
 * `g_spells().getSpellsByVocation(vocationId)` re-resolved through
 * `getInstantSpellById` and filtered to `spellId > 0`. The registry resolves
 * through the player's attached `Player::g_spells()` seam when present,
 * otherwise the booted engine registry ([SpellBindings.spellRegistry]) the
 * datapack spell scripts registered into.
 */
internal fun basicDataSpellsOf(player: Player): List<MiscDataWriter.BasicDataSpell> {
    val registry = player.spells ?: SpellBindings.spellRegistry()
    return registry.getSpellsByVocation(player.getVocationId())
        .mapNotNull { registry.getInstantSpellById(it) }
        .filter { it.spellId > 0 }
        .map { spell ->
            MiscDataWriter.BasicDataSpell(
                spellId = spell.spellId,
                learnable = spell.isLearnable(),
                learned = player.hasLearnedInstantSpell(spell.name),
                wheelGrade = player.features.wheel.getSpellUpgrade(spell.name).value,
            )
        }
}
