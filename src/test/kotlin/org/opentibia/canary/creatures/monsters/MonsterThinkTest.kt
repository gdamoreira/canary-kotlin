package org.opentibia.canary.creatures.monsters

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.creatures.Monster
import org.opentibia.canary.map.Position
import org.opentibia.canary.spells.SoundEffect

/**
 * Transcribes the Canary monster loot tail, summon and respawn/think surface:
 * - `Monster::getLostExperience` (src/creatures/monsters/monster.cpp:873-876)
 * - `Monster::canDropLoot` (src/creatures/monsters/monster.cpp:3945-3947)
 * - `Creature::setMaster`/`removeMaster`/`isSummon`/`hasBeenSummoned`
 *   (src/creatures/creature.cpp:1339-1373, src/creatures/creature.hpp:460-481)
 * - `Creature::checkSummonMove` (src/creatures/creature.cpp:454-500)
 * - `MonsterType::canSpawn` (src/creatures/monsters/monsters.cpp:35-45)
 * - `Monster::onThink` (src/creatures/monsters/monster.cpp:1581-1632)
 * - `Monster::onThink_async` (src/creatures/monsters/monster.cpp:1708-1752)
 * - `Monster::onThinkYell` (src/creatures/monsters/monster.cpp:2274-2294)
 * - `Monster::onThinkSound` (src/creatures/monsters/monster.cpp:2296-2310)
 */
class MonsterThinkTest {

    private val pos = Position(100, 100, 7)

    private fun monster(id: Long = 1, position: Position = pos) = Monster(id, "Rat", 100, 100, position = position)

    // --- Monster::getLostExperience ---

    @Test
    fun `getLostExperience returns zero without skill loss`() {
        assertThat(MonsterThink.getLostExperience(forgeStack = 0, skillLoss = false, experience = 100)).isZero()
    }

    @Test
    fun `getLostExperience scales by the forge stack tier`() {
        assertThat(MonsterThink.getLostExperience(forgeStack = 0, skillLoss = true, experience = 100)).isEqualTo(100)
        assertThat(MonsterThink.getLostExperience(forgeStack = 10, skillLoss = true, experience = 100)).isEqualTo(200)
        assertThat(MonsterThink.getLostExperience(forgeStack = 15, skillLoss = true, experience = 100)).isEqualTo(200)
        assertThat(MonsterThink.getLostExperience(forgeStack = 16, skillLoss = true, experience = 100)).isEqualTo(2800)
    }

    // --- Monster::canDropLoot ---

    @Test
    fun `canDropLoot requires a non empty loot table`() {
        assertThat(MonsterLoot.canDropLoot(emptyList())).isFalse()
        assertThat(MonsterLoot.canDropLoot(listOf(LootBlock(id = 1)))).isTrue()
    }

    // --- Monster::isTargetNearby ---

    @Test
    fun `isTargetNearby reads the step duration`() {
        val state = MonsterBehaviorState()

        assertThat(MonsterWalk.isTargetNearby(state)).isFalse()
        state.stepDuration = 1
        assertThat(MonsterWalk.isTargetNearby(state)).isTrue()
    }

    // --- Creature::setMaster / removeMaster ---

    @Test
    fun `setMaster records the summon on the new master and detaches the old one`() {
        val state = SummonMasterState(creatureId = 7)
        val added = mutableListOf<Pair<Long, Long>>()
        val removed = mutableListOf<Pair<Long, Long>>()

        val first = SummonController.setMaster(
            state = state,
            newMasterId = 3,
            addSummon = { masterId, summonId -> added.add(masterId to summonId) },
            removeSummon = { masterId, summonId -> removed.add(masterId to summonId) },
        )

        assertThat(first).isTrue()
        assertThat(state.masterId).isEqualTo(3)
        assertThat(state.summoned).isTrue()
        assertThat(added).containsExactly(3L to 7L)
        assertThat(removed).isEmpty()

        SummonController.setMaster(
            state = state,
            newMasterId = 4,
            addSummon = { masterId, summonId -> added.add(masterId to summonId) },
            removeSummon = { masterId, summonId -> removed.add(masterId to summonId) },
        )

        assertThat(state.masterId).isEqualTo(4)
        assertThat(added).containsExactly(3L to 7L, 4L to 7L)
        assertThat(removed).containsExactly(3L to 7L)
    }

    @Test
    fun `setMaster marks the summoning latches even without a master`() {
        val state = SummonMasterState(creatureId = 7)

        val result = SummonController.setMaster(state = state, newMasterId = null)

        assertThat(result).isFalse()
        assertThat(state.summoned).isTrue()
        assertThat(SummonController.hasBeenSummoned(state)).isTrue()
    }

    @Test
    fun `setMaster reload asks the caller to reset follow loot and skills`() {
        val state = SummonMasterState(creatureId = 7)
        var reloads = 0

        SummonController.setMaster(
            state = state,
            newMasterId = 3,
            reloadCreature = true,
            onReloadCreature = { reloads++ },
        )

        assertThat(reloads).isEqualTo(1)
    }

    @Test
    fun `removeMaster drops the pointer without touching the old master list`() {
        val state = SummonMasterState(creatureId = 7, masterId = 3)

        SummonController.removeMaster(state)

        assertThat(state.masterId).isNull()
        assertThat(SummonController.isSummon(state)).isFalse()

        SummonController.removeMaster(state)
        assertThat(state.masterId).isNull()
    }

    // --- Creature::checkSummonMove ---

    private fun summonFacts(
        id: Long,
        x: Int = 100,
        y: Int = 100,
        z: Int = 7,
        masterId: Long? = 1,
        masterPosition: Position? = pos,
        isSummon: Boolean = true,
        isFamiliar: Boolean = false,
        masterTileHasProtectionZone: Boolean = false,
        masterTileHasTeleport: Boolean = false,
    ) = SummonMoveFacts(
        id = id,
        position = Position(x, y, z),
        masterId = masterId,
        masterPosition = masterPosition,
        isSummon = isSummon,
        isFamiliar = isFamiliar,
        masterTileHasProtectionZone = masterTileHasProtectionZone,
        masterTileHasTeleport = masterTileHasTeleport,
    )

    @Test
    fun `checkSummonMove teleports a distant familiar home`() {
        val teleports = mutableListOf<Pair<Long, Position>>()

        SummonController.checkSummonMove(
            newPos = pos,
            teleportSummon = false,
            summons = listOf(summonFacts(id = 9, x = 120, isFamiliar = true)),
            onTeleport = { id, target -> teleports.add(id to target) },
        )

        assertThat(teleports).containsExactly(9L to pos)
    }

    @Test
    fun `checkSummonMove leaves a nearby familiar alone`() {
        var teleports = 0

        SummonController.checkSummonMove(
            newPos = pos,
            teleportSummon = false,
            summons = listOf(summonFacts(id = 9, x = 114, isFamiliar = true)),
            onTeleport = { _, _ -> teleports++ },
        )

        assertThat(teleports).isZero()
    }

    @Test
    fun `checkSummonMove teleports a configured summon unless the master stands on a protection zone`() {
        val teleports = mutableListOf<Long>()

        SummonController.checkSummonMove(
            newPos = pos,
            teleportSummon = true,
            summons = listOf(summonFacts(id = 9, x = 120)),
            onTeleport = { id, _ -> teleports.add(id) },
        )
        assertThat(teleports).containsExactly(9L)

        SummonController.checkSummonMove(
            newPos = pos,
            teleportSummon = true,
            summons = listOf(summonFacts(id = 9, x = 120, masterTileHasProtectionZone = true)),
            onTeleport = { id, _ -> teleports.add(id) },
        )
        assertThat(teleports).containsExactly(9L)
    }

    @Test
    fun `checkSummonMove removes non familiar summons beyond thirty tiles or two floors`() {
        val removed = mutableListOf<Long>()

        SummonController.checkSummonMove(
            newPos = pos,
            teleportSummon = false,
            summons = listOf(
                summonFacts(id = 8, x = 131),
                summonFacts(id = 9, x = 130),
                summonFacts(id = 10, z = 10),
                summonFacts(id = 11, z = 9),
            ),
            onRemove = { removed.add(it) },
        )

        assertThat(removed).containsExactly(8L, 10L)
    }

    @Test
    fun `checkSummonMove never removes a familiar even when far away`() {
        var removed = 0

        SummonController.checkSummonMove(
            newPos = pos,
            teleportSummon = false,
            summons = listOf(summonFacts(id = 9, x = 200, isFamiliar = true, masterTileHasTeleport = true)),
            onRemove = { removed++ },
        )

        assertThat(removed).isZero()
    }

    @Test
    fun `checkSummonMove warns instead of teleporting when the master tile is a teleport`() {
        val warned = mutableListOf<Long>()
        var teleports = 0

        SummonController.checkSummonMove(
            newPos = pos,
            teleportSummon = false,
            summons = listOf(summonFacts(id = 9, x = 120, isFamiliar = true, masterTileHasTeleport = true)),
            onTeleport = { _, _ -> teleports++ },
            onTeleportWarn = { id -> warned.add(id) },
        )

        assertThat(teleports).isZero()
        assertThat(warned).containsExactly(9L)
    }

    @Test
    fun `checkSummonMove skips a summon without a master`() {
        var teleports = 0

        SummonController.checkSummonMove(
            newPos = pos,
            teleportSummon = false,
            summons = listOf(summonFacts(id = 9, x = 120, isFamiliar = true, masterId = null)),
            onTeleport = { _, _ -> teleports++ },
        )

        assertThat(teleports).isZero()
    }

    // --- MonsterType::canSpawn ---

    @Test
    fun `canSpawn always allows the all period`() {
        val type = MonsterType("Rat")
        type.info.respawnType.period = RespawnPeriod.RESPAWNPERIOD_ALL

        assertThat(type.canSpawn(Position(100, 100, 7), isDay = true)).isTrue()
        assertThat(type.canSpawn(Position(100, 100, 7), isDay = false)).isTrue()
    }

    @Test
    fun `canSpawn gates day and night periods outside underground spawns`() {
        val day = MonsterType("Rat").apply { info.respawnType.period = RespawnPeriod.RESPAWNPERIOD_DAY }
        assertThat(day.canSpawn(Position(100, 100, 7), isDay = true)).isTrue()
        assertThat(day.canSpawn(Position(100, 100, 7), isDay = false)).isFalse()
        assertThat(day.canSpawn(Position(100, 100, 8), isDay = false)).isFalse()

        val dayUnderground = MonsterType("Rat").apply {
            info.respawnType.period = RespawnPeriod.RESPAWNPERIOD_DAY
            info.respawnType.underground = true
        }
        assertThat(dayUnderground.canSpawn(Position(100, 100, 8), isDay = false)).isTrue()
        assertThat(dayUnderground.canSpawn(Position(100, 100, 7), isDay = false)).isFalse()

        val night = MonsterType("Rat").apply { info.respawnType.period = RespawnPeriod.RESPAWNPERIOD_NIGHT }
        assertThat(night.canSpawn(Position(100, 100, 7), isDay = false)).isTrue()
        assertThat(night.canSpawn(Position(100, 100, 7), isDay = true)).isFalse()
    }

    // --- Monster::onThink ---

    private fun thinkRequest(
        state: MonsterBehaviorState = MonsterBehaviorState(),
        interval: Int = 100,
        thinkEvent: Int = -1,
        runThinkEvent: () -> Boolean = { false },
        canSpawn: (Position) -> Boolean = { true },
        isInSpawnRange: (Position) -> Boolean = { true },
        masterPos: Position = Position(50, 50, 7),
        events: MutableList<String> = mutableListOf(),
    ) = MonsterThinkRequest(
        monster = monster(),
        state = state,
        interval = interval,
        thinkEvent = thinkEvent,
        infoTargetDistance = 1,
        runThinkEvent = runThinkEvent,
        canSpawn = canSpawn,
        isInSpawnRange = isInSpawnRange,
        masterPos = masterPos,
        onUpdateCreatureIcon = { events.add("icon") },
        removeCreature = { events.add("remove") },
        internalTeleport = { events.add("teleport:$it") },
        setIdle = { events.add("setIdle:$it") },
        updateIdleStatus = { events.add("updateIdle") },
        setAsyncTaskFlag = { events.add("asyncFlag") },
    )

    @Test
    fun `onThink returns after a handled lua think event`() {
        val events = mutableListOf<String>()

        MonsterThink.onThink(
            thinkRequest(
                state = MonsterBehaviorState().apply { challengeMeleeDuration = 500 },
                thinkEvent = 7,
                runThinkEvent = { true },
                events = events,
            )
        )

        assertThat(events).isEmpty()
    }

    @Test
    fun `onThink ticks the melee challenge and restores the target distance`() {
        val state = MonsterBehaviorState().apply {
            challengeMeleeDuration = 150
            targetDistance = 5
        }
        val events = mutableListOf<String>()

        MonsterThink.onThink(thinkRequest(state = state, interval = 100, events = events))
        assertThat(state.challengeMeleeDuration).isEqualTo(50)
        assertThat(state.targetDistance).isEqualTo(5)
        assertThat(events).doesNotContain("icon")

        MonsterThink.onThink(thinkRequest(state = state, interval = 100, events = events))
        assertThat(state.challengeMeleeDuration).isZero()
        assertThat(state.targetDistance).isEqualTo(1)
        assertThat(events).contains("icon")
    }

    @Test
    fun `onThink removes a monster that can no longer spawn but keeps thinking`() {
        val events = mutableListOf<String>()

        MonsterThink.onThink(thinkRequest(canSpawn = { false }, events = events))

        assertThat(events).contains("remove", "updateIdle", "asyncFlag")
    }

    @Test
    fun `onThink teleports a monster outside the spawn range home and idles it`() {
        val events = mutableListOf<String>()
        val masterPos = Position(50, 50, 7)

        MonsterThink.onThink(
            thinkRequest(
                isInSpawnRange = { false },
                masterPos = masterPos,
                events = events,
            )
        )

        assertThat(events).containsExactly("teleport:$masterPos", "setIdle:true")
    }

    @Test
    fun `onThink updates the idle status and flags the async task on a normal tick`() {
        val events = mutableListOf<String>()

        MonsterThink.onThink(thinkRequest(events = events))

        assertThat(events).containsExactly("updateIdle", "asyncFlag")
    }

    // --- Monster::onThink_async ---

    private fun asyncThinkRequest(
        state: MonsterBehaviorState = MonsterBehaviorState(),
        isIdle: Boolean = false,
        isSummon: Boolean = false,
        targetListEmpty: Boolean = false,
        targetListPopulatedByRefresh: Boolean = false,
        friendlyFire: Boolean = false,
        attackedCreatureId: Long? = null,
        followCreatureId: Long? = null,
        hasFollowPath: Boolean = false,
        targetDistance: Int = 1,
        canUseAttack: Boolean = true,
        isFleeing: Boolean = false,
        events: MutableList<String> = mutableListOf(),
    ) = MonsterThinkAsyncRequest(
        state = state,
        isIdle = isIdle,
        isSummon = isSummon,
        targetListEmpty = targetListEmpty,
        monsterPerfTestFriendlyFire = friendlyFire,
        attackedCreatureId = attackedCreatureId,
        followCreatureId = followCreatureId,
        hasFollowPath = hasFollowPath,
        targetDistance = targetDistance,
        targetListPopulatedByRefresh = targetListPopulatedByRefresh,
        canUseAttack = { canUseAttack },
        isFleeing = isFleeing,
        addEventWalk = { events.add("addEventWalk") },
        updateSummonTarget = { events.add("updateSummonTarget") },
        updateTargetList = { events.add("updateTargetList") },
        searchTarget = { events.add("search:$it") },
        onThinkTarget = { events.add("onThinkTarget") },
        onThinkYell = { events.add("onThinkYell") },
        onThinkDefense = { events.add("onThinkDefense") },
        onThinkSound = { events.add("onThinkSound") },
        queuePostThinkAfterAsync = { events.add("queuePostThink") },
    )

    @Test
    fun `onThink_async stops an idle monster`() {
        val events = mutableListOf<String>()

        val result = MonsterThink.onThinkAsync(asyncThinkRequest(isIdle = true, events = events))

        assertThat(result).isFalse()
        assertThat(events).isEmpty()
    }

    @Test
    fun `onThink_async runs the summon target update without a search`() {
        val events = mutableListOf<String>()

        val result = MonsterThink.onThinkAsync(asyncThinkRequest(isSummon = true, events = events))

        assertThat(result).isTrue()
        assertThat(events).contains("addEventWalk", "updateSummonTarget", "onThinkTarget", "onThinkYell", "onThinkDefense", "onThinkSound", "queuePostThink")
        assertThat(events).noneMatch { it.startsWith("search:") }
    }

    @Test
    fun `onThink_async refreshes the target list under friendly fire before searching`() {
        val events = mutableListOf<String>()

        MonsterThink.onThinkAsync(
            asyncThinkRequest(
                targetListEmpty = true,
                targetListPopulatedByRefresh = true,
                friendlyFire = true,
                events = events,
            )
        )

        assertThat(events).contains("updateTargetList", "search:Random")
    }

    @Test
    fun `onThink_async searches the nearest target when the attack target is missing`() {
        val events = mutableListOf<String>()

        MonsterThink.onThinkAsync(asyncThinkRequest(events = events))

        assertThat(events).contains("search:Nearest")
    }

    @Test
    fun `onThink_async re-searches an unattackable target`() {
        val events = mutableListOf<String>()

        MonsterThink.onThinkAsync(
            asyncThinkRequest(attackedCreatureId = 9, canUseAttack = false, events = events)
        )

        assertThat(events).contains("search:Nearest")
    }

    @Test
    fun `onThink_async re-searches an unreachable melee target`() {
        val events = mutableListOf<String>()

        MonsterThink.onThinkAsync(
            asyncThinkRequest(attackedCreatureId = 9, followCreatureId = 9, hasFollowPath = false, events = events)
        )

        assertThat(events).contains("search:Nearest")
    }

    @Test
    fun `onThink_async keeps a reachable attack target`() {
        val events = mutableListOf<String>()

        MonsterThink.onThinkAsync(
            asyncThinkRequest(attackedCreatureId = 9, followCreatureId = 9, hasFollowPath = true, events = events)
        )

        assertThat(events).noneMatch { it.startsWith("search:") }
    }

    @Test
    fun `onThink_async flees with a default search when the target slips out of range`() {
        val events = mutableListOf<String>()

        MonsterThink.onThinkAsync(
            asyncThinkRequest(
                attackedCreatureId = 9,
                followCreatureId = 9,
                hasFollowPath = true,
                canUseAttack = false,
                isFleeing = true,
                events = events,
            )
        )

        assertThat(events).contains("search:Default")
    }

    // --- Monster::onThinkYell ---

    @Test
    fun `onThinkYell does nothing without a yell speed`() {
        val state = MonsterBehaviorState()
        var says = 0

        MonsterThink.onThinkYell(
            MonsterYellRequest(
                state = state,
                interval = 100,
                yellSpeedTicks = 0,
                yellChance = 100,
                voiceVector = listOf(VoiceBlock("hi", false)),
                say = { _, _ -> says++ },
            )
        )

        assertThat(state.yellTicks).isZero()
        assertThat(says).isZero()
    }

    @Test
    fun `onThinkYell accumulates ticks and stays silent below the speed`() {
        val state = MonsterBehaviorState()
        var says = 0

        MonsterThink.onThinkYell(
            MonsterYellRequest(
                state = state,
                interval = 100,
                yellSpeedTicks = 1000,
                yellChance = 100,
                voiceVector = listOf(VoiceBlock("hi", false)),
                say = { _, _ -> says++ },
            )
        )

        assertThat(state.yellTicks).isEqualTo(100)
        assertThat(says).isZero()
    }

    @Test
    fun `onThinkYell speaks the picked voice line once the window elapses`() {
        val state = MonsterBehaviorState().apply { yellTicks = 950 }
        val said = mutableListOf<Pair<String, Boolean>>()

        MonsterThink.onThinkYell(
            MonsterYellRequest(
                state = state,
                interval = 100,
                yellSpeedTicks = 1000,
                yellChance = 100,
                voiceVector = listOf(VoiceBlock("hello", false), VoiceBlock("run", true)),
                nextIndex = { 1 },
                say = { text, yell -> said.add(text to yell) },
            )
        )

        assertThat(state.yellTicks).isZero()
        assertThat(said).containsExactly("run" to true)
    }

    @Test
    fun `onThinkYell respects the chance and the empty voice vector`() {
        val state = MonsterBehaviorState().apply { yellTicks = 950 }
        var says = 0
        val say: (String, Boolean) -> Unit = { _, _ -> says++ }

        MonsterThink.onThinkYell(
            MonsterYellRequest(
                state = state,
                interval = 100,
                yellSpeedTicks = 1000,
                yellChance = 30,
                voiceVector = listOf(VoiceBlock("hello", false)),
                nextRandom = { _, _ -> 50 },
                say = say,
            )
        )
        assertThat(says).isZero()

        state.yellTicks = 950
        MonsterThink.onThinkYell(
            MonsterYellRequest(
                state = state,
                interval = 100,
                yellSpeedTicks = 1000,
                yellChance = 100,
                voiceVector = emptyList(),
                say = say,
            )
        )
        assertThat(says).isZero()
        assertThat(state.yellTicks).isZero()
    }

    // --- Monster::onThinkSound ---

    @Test
    fun `onThinkSound emits the picked sound once the window elapses`() {
        val state = MonsterBehaviorState().apply { soundTicks = 950 }
        val sounds = mutableListOf<Pair<Position, SoundEffect>>()
        val monster = monster()

        MonsterThink.onThinkSound(
            MonsterSoundRequest(
                monster = monster,
                state = state,
                interval = 100,
                soundSpeedTicks = 1000,
                soundChance = 100,
                soundVector = listOf(SoundEffect.MONSTER_SPELL_SUMMON),
                nextIndex = { 0 },
                sendSoundEffect = { position, sound, source -> sounds.add(position to sound); assertThat(source).isSameAs(monster) },
            )
        )

        assertThat(state.soundTicks).isZero()
        assertThat(sounds).containsExactly(pos to SoundEffect.MONSTER_SPELL_SUMMON)
    }

    @Test
    fun `onThinkSound gates on speed chance and an empty vector`() {
        val state = MonsterBehaviorState()
        var sounds = 0

        MonsterThink.onThinkSound(
            MonsterSoundRequest(
                monster = monster(),
                state = state,
                interval = 100,
                soundSpeedTicks = 0,
                soundChance = 100,
                soundVector = listOf(SoundEffect.MONSTER_SPELL_SUMMON),
                sendSoundEffect = { _, _, _ -> sounds++ },
            )
        )
        assertThat(sounds).isZero()

        state.soundTicks = 950
        MonsterThink.onThinkSound(
            MonsterSoundRequest(
                monster = monster(),
                state = state,
                interval = 100,
                soundSpeedTicks = 1000,
                soundChance = 30,
                soundVector = listOf(SoundEffect.MONSTER_SPELL_SUMMON),
                nextRandom = { _, _ -> 50 },
                sendSoundEffect = { _, _, _ -> sounds++ },
            )
        )
        assertThat(sounds).isZero()
        assertThat(state.soundTicks).isZero()

        state.soundTicks = 950
        MonsterThink.onThinkSound(
            MonsterSoundRequest(
                monster = monster(),
                state = state,
                interval = 100,
                soundSpeedTicks = 1000,
                soundChance = 100,
                soundVector = emptyList(),
                sendSoundEffect = { _, _, _ -> sounds++ },
            )
        )
        assertThat(sounds).isZero()
    }
}
