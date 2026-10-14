package org.opentibia.canary.network.protocol

import io.netty.buffer.ByteBuf
import io.netty.buffer.ByteBufAllocator
import io.netty.channel.Channel
import kotlin.math.floor
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.Creature
import org.opentibia.canary.creatures.FightMode
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.creatures.Npc
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.skills_t
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.lua.bindings.core.returnMessage
import org.opentibia.canary.map.GameMap
import org.opentibia.canary.map.Position
import org.opentibia.canary.network.protocol.container.ContainerItemWriter
import org.opentibia.canary.network.protocol.creature.CreaturePacketWriter
import org.opentibia.canary.network.protocol.player.PlayerWindowWriter
import org.opentibia.canary.systems.rewardchest.ReturnValue

/**
 * Per-player outbound `ProtocolGame` surface used by world state changes.
 *
 * The sink is the port's replacement for `Player::client` (`ProtocolGame*`,
 * player.hpp:1955): state owners (`Game`, `Player`, `Creature`, `Container`)
 * call the same senders Canary calls, and the sink serializes them through the
 * already-ported writers. A `null` [Player.protocolSink] models
 * `Player::client == nullptr`, exactly like the seam properties it feeds.
 *
 * Implementations must be fire-and-forget: `NettyProtocolSink` queues the frame
 * on the channel's event loop, so sink writes never block the WorldCommit lane
 * (AGENTS.md 1.2) and never retain mutable world state.
 */
interface ProtocolSink {

    /** `ProtocolGame::sendTextMessage(TextMessage)` (0xB4, protocolgame.cpp:6070). */
    fun sendTextMessage(type: MessageClass, text: String)

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
}

/**
 * Shared frame serialization for every [ProtocolSink] transport: the senders
 * resolve live entity state into the ported frame types, allocate one `ByteBuf`
 * per frame from [frameAllocator] and hand it to [writeFrame], which owns the
 * buffer from that point on.
 */
abstract class ProtocolFrameSink(
    protected val player: Player,
    protected val map: GameMap,
) : ProtocolSink {

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
        val tile = creature.getTile() ?: return
        val stackpos = tile.getClientIndexOfCreature(player, creature)
        if (stackpos < 0 || stackpos >= MAX_STACKPOS) {
            // `Player::sendCreatureAppear` falls back to `sendUpdateTile`
            // (player.cpp:8793); the tile-update sender is not ported.
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

    /**
     * Resolved `ProtocolGame::AddCreature` input for the observer. `known` /
     * `removedKnown` are the unported session known-set outputs, and the
     * health-hidden, guild-emblem, speech-bubble, creature-mark and
     * walkthrough fields that have no Kotlin state use the Canary neutral
     * values (PARITY GAPS).
     */
    protected fun appearanceOf(creature: Creature): CreatureAppearanceFrame {
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

    private companion object {
        /** `MAX_STACKPOS` (`src/server/network/protocol/protocolgame.hpp`). */
        const val MAX_STACKPOS = 10
    }
}

/**
 * [ProtocolSink] over a live Netty channel. Frames are allocated from the
 * channel's allocator and queued with `writeAndFlush`, so the caller never
 * blocks and the channel's pipeline (XTEA/checksum encoder) owns encryption.
 * A closed channel releases the frame and drops the write.
 */
class NettyProtocolSink(
    private val channel: Channel,
    player: Player,
    map: GameMap,
) : ProtocolFrameSink(player, map) {

    override fun frameAllocator(): ByteBufAllocator = channel.alloc()

    override fun writeFrame(frame: ByteBuf) {
        if (!channel.isActive) {
            frame.release()
            return
        }
        channel.writeAndFlush(frame)
    }
}
