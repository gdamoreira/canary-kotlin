package org.opentibia.canary.creatures

import org.opentibia.canary.account.Account
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.Condition
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.combat.condition.Outfit
import org.opentibia.canary.creatures.appearance.effects.AttachedEffects
import org.opentibia.canary.creatures.appearance.outfit.Outfits
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.players.CyclopediaTrackerClient
import org.opentibia.canary.creatures.players.PlayerActiveFoods
import org.opentibia.canary.creatures.players.PlayerAimAtTargetState
import org.opentibia.canary.creatures.players.PlayerBossPointsState
import org.opentibia.canary.creatures.players.PlayerCombatBonusState
import org.opentibia.canary.creatures.players.PlayerCyclopediaTrackerState
import org.opentibia.canary.creatures.players.PlayerItemAbilityState
import org.opentibia.canary.creatures.players.PlayerLoyaltyState
import org.opentibia.canary.creatures.players.PlayerMagicAffinityState
import org.opentibia.canary.creatures.players.PlayerMessageBuffer
import org.opentibia.canary.creatures.players.animus_mastery.AnimusMastery
import org.opentibia.canary.creatures.players.components.Achievement
import org.opentibia.canary.creatures.players.components.AchievementPlayer
import org.opentibia.canary.creatures.players.components.AchievementRegistry
import org.opentibia.canary.creatures.players.components.AttachedEffectsPlayer
import org.opentibia.canary.creatures.players.components.BadgePlayer
import org.opentibia.canary.creatures.players.components.CyclopediaPlayer
import org.opentibia.canary.creatures.players.components.InMemoryAchievementRegistry
import org.opentibia.canary.creatures.players.components.InMemoryKV
import org.opentibia.canary.creatures.players.components.KV
import org.opentibia.canary.creatures.players.components.MutableBadgeRegistry
import org.opentibia.canary.creatures.players.components.MutableTitleRegistry
import org.opentibia.canary.creatures.players.components.PlayerAchievement
import org.opentibia.canary.creatures.players.components.PlayerAsyncTask
import org.opentibia.canary.creatures.players.components.PlayerAttachedEffects
import org.opentibia.canary.creatures.players.components.PlayerBadge
import org.opentibia.canary.creatures.players.components.PlayerCyclopedia
import org.opentibia.canary.creatures.players.components.PlayerSex_t
import org.opentibia.canary.creatures.players.components.PlayerTitle
import org.opentibia.canary.creatures.players.components.RecentDeathEntry
import org.opentibia.canary.creatures.players.components.RecentPvPKillEntry
import org.opentibia.canary.creatures.players.components.TitlePlayer
import org.opentibia.canary.creatures.players.getEquippedItems
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.network.protocol.MessageClass
import org.opentibia.canary.social.guild.GuildRank
import org.opentibia.canary.systems.bestiary.PlayerBestiaryState
import org.opentibia.canary.systems.concoction.PlayerConcoctions
import org.opentibia.canary.systems.dailyreward.PlayerDailyReward
import org.opentibia.canary.systems.familiar.Familiars
import org.opentibia.canary.systems.familiar.PlayerFamiliars
import org.opentibia.canary.systems.forge.Forge
import org.opentibia.canary.systems.forge.ForgeConfig
import org.opentibia.canary.systems.forge.PlayerForgeHistory
import org.opentibia.canary.systems.hazard.PlayerHazard
import org.opentibia.canary.systems.mount.Mount
import org.opentibia.canary.systems.mount.Mounts
import org.opentibia.canary.systems.mount.PlayerMountState
import org.opentibia.canary.systems.offlinetraining.OfflineTraining
import org.opentibia.canary.systems.offlinetraining.Skill
import org.opentibia.canary.systems.offlinetraining.VocationRequirements
import org.opentibia.canary.systems.prey.PlayerPreyState
import org.opentibia.canary.systems.prey.PreyEngine
import org.opentibia.canary.systems.proficiency.WeaponProficiencyData
import org.opentibia.canary.systems.proficiency.WeaponProficiencyPerfectShotBonus
import org.opentibia.canary.systems.rewardchest.ReturnValue
import org.opentibia.canary.systems.rewardchest.RewardChest
import org.opentibia.canary.systems.taskhunting.PlayerTaskHuntingState
import org.opentibia.canary.systems.taskhunting.TaskHuntingEngine
import org.opentibia.canary.systems.wheel.PlayerWheel
import org.opentibia.canary.creatures.vocations.VocationRequirementsAdapter
import org.opentibia.canary.creatures.vocations.Vocations

/**
 * Composition holder for the per-player advanced-feature engine cores.
 *
 * Canary spreads these over the `Player` class and its component members:
 * `PlayerWheel m_wheelPlayer`, `PlayerForgeHistory m_forgeHistoryPlayer`
 * (src/creatures/players/player.hpp:1959,1969) plus the directly embedded
 * offline-training, concoction, daily-reward, mount, familiar, bestiary and
 * hazard state. This holder owns one instance of each engine core and adds no
 * gameplay logic of its own; it exists so routed opcodes and Lua bindings can
 * reach the already-ported engines through a single seam.
 *
 * Features whose construction depends on player state (level, vocation,
 * premium/access) are created lazily the first time they are accessed, so the
 * data only has to be resolvable by then. `LazyThreadSafetyMode.NONE` keeps the
 * world-confinement contract of AGENTS.md 1.2 (no locking across player state).
 *
 * Every dependency Canary resolves through a global (`Mounts::getInstance`,
 * `Familiars::getInstance`, `Vocations`, the account group) is injected here
 * with a default, per AGENTS.md 1.1.
 *
 * The remaining standalone `Player` state holders are composed here so routed
 * opcodes/Lua bindings share the same instance as the `Player`:
 * [combatBonuses] (`Player::cleavePercent`/`perfectShot` and the reflect
 * queries), [activeFoods] (`Player::m_activeFoods`), [messageBuffer]
 * (`Player::MessageBufferCount`), [itemAbilities]
 * (`Player::inventoryAbilities`), [bossPoints] (`Player::bossPoints`),
 * [cyclopediaTracker] (`Player::m_bestiaryMonsterTracker`/
 * `m_bosstiaryMonsterTracker`), [aimAtTarget]
 * (`Player::aimAtTargetSpellIds`), [magicAffinity]
 * (`Player::specializedMagicLevel`/`magicShieldCapacity*`), [achievement]
 * (`Player::achiev()`, player.cpp:12758) and [animusMastery]
 * (`Player::animusMastery()`, player.cpp:12812). Their
 * `Player`-backed seams ([getEquippedItems], [cannotBeMuted], [addCondition],
 * [sendTextMessage], [achievementKv]) default to the injected [wheelPlayer]
 * view, which the `Player` always supplies as itself.
 *
 * COMPOSITION (`CanaryServer` composition root):
 *  - the `Player` feeds [maxMessageBuffer] from its own `maxMessageBuffer`
 *    property; `Game::addPlayer` copies it from
 *    `g_configManager().getNumber(MAX_MESSAGEBUFFER)` (configmanager.cpp:315)
 *    when the world composes a `ConfigManager`. The `0` default keeps the mute
 *    pipeline inert for a directly constructed player.
 *  - [muteCountMap] and [transformItemCharges] are injected through the
 *    `Player` constructor; `CanaryServer` owns one map and one lambda per world
 *    (Canary's `Player::muteCountMap` static, player.hpp:281, and
 *    `Game::transformItem(item, item->getID(), charges - 1)`, player.cpp:1065,
 *    7588, 7697, 7718) and shares them with every session, so the mute
 *    escalation survives relogs.
 *  - the weapon-proficiency engine must still bind [perfectShotBonus] to
 *    `WeaponProficiency::getPerfectShotBonus` (weapon_proficiency.cpp:1201);
 *    the base reflect arrays stay `{ 0 }` until the unported `Creature` arrays
 *    exist. [transformItemCharges] also backs [magicAffinity]'s charged-item
 *    reads.
 *  - [wheelSpecializedMagic] and [proficiencySpecializedMagic] stay `{ 0 }`
 *    until `PlayerWheel::getSpecializedMagic` (player_wheel.cpp:405) and
 *    `WeaponProficiency::getSpecializedMagic` (weapon_proficiency.hpp:65) are
 *    ported.
 */
class PlayerFeatures(
    /** `Player::getGUID()`, used to scope forge-history persistence. */
    val playerId: Long = 0,
    /** `Player::getLevel()`; Canary builds the wheel from the live level. */
    private val levelProvider: () -> Int = { 1 },
    /** `Player::vocation->getID()`; not yet ported on the Kotlin `Player`. */
    private val vocationIdProvider: () -> Int = { 0 },
    /** `Player::isPremium()`. */
    private val isPremiumProvider: () -> Boolean = { false },
    /** `Player::isAccessPlayer()` (`group->access`). */
    private val hasAccessProvider: () -> Boolean = { false },
    /**
     * `Player&` owner stored by Canary's `PlayerWheel(Player &initPlayer)`
     * constructor (player_wheel.hpp:79,494); injected per AGENTS.md 1.1 so the
     * wheel's `check<Spell>ByTarget` owner checks work without a global.
     */
    private val wheelPlayer: Player? = null,
    /** `Vocations::getInstance()` registry; when present it satisfies the offline-training requirement seam. */
    private val vocations: Vocations? = null,
    /** `Vocation::getReqSkillTries` / `Vocation::getReqMana` seam. */
    private val vocationRequirementsProvider: () -> VocationRequirements = {
        vocations?.getVocation(vocationIdProvider())
            ?.let { VocationRequirementsAdapter(it) }
            ?: UnwiredVocationRequirements
    },
    /** `Mounts::getInstance()` definition registry. */
    private val mountDefinitions: Mounts = Mounts(),
    /** `Familiars::getInstance()` definition registry. */
    private val familiarDefinitions: Familiars = Familiars(),
    forgeConfig: ForgeConfig = ForgeConfig(),
    /**
     * `Game::m_badges` (game.cpp:570-597, 12536-12564) catalog of [badge]. The
     * Kotlin `Player` cannot receive the composition root's registry by
     * injection yet, so the world operations adopt its entries into this
     * catalog on first touch (PARITY GAP, see `WorldPlayerExtraOperations`).
     */
    val badgeRegistry: MutableBadgeRegistry = MutableBadgeRegistry(),
    /**
     * `Game::m_titles` (game.cpp:599-703, 12566-12594) catalog of [title]; see
     * [badgeRegistry] for the adoption seam.
     */
    val titleRegistry: MutableTitleRegistry = MutableTitleRegistry(),
    /**
     * `g_game().getAttachedEffects()` (player_attached_effects.cpp:56,63,110)
     * definition registry of [attachedEffects]; the composition root injects
     * the instance loaded from `data/XML/attachedeffects.xml`.
     */
    val attachedEffectDefinitions: AttachedEffects = AttachedEffects(),
    /**
     * `Outfits::getInstance()` (player_attached_effects.cpp:95,284,472,629)
     * outfit registry of [attachedEffects], injected per AGENTS.md 1.1.
     */
    val attachedOutfits: Outfits = Outfits(),
    /**
     * `g_ioprey()` prey engine core (`src/io/ioprey.cpp`); Canary reaches it
     * through the global singleton and `Player::initializePrey` uses it, so it
     * is injected here per AGENTS.md 1.1.
     */
    val preyEngine: PreyEngine = PreyEngine(),
    /** `g_ioprey()` task-hunting engine core (`src/io/ioprey.cpp`); see [preyEngine]. */
    val taskHuntingEngine: TaskHuntingEngine = TaskHuntingEngine(),
    /**
     * `Player&` view backing [cyclopedia]
     * (`src/creatures/players/components/player_cyclopedia.hpp:26`); absent
     * when the holder is constructed without a player (tooling/tests).
     */
    private val cyclopediaPlayer: CyclopediaPlayer? = null,
    /**
     * `Player::client` cyclopedia tracker sink (`ProtocolGame::parseSendBosstiary`/
     * `sendBestiaryEntryChanged`/`refreshCyclopediaMonsterTracker`,
     * player.cpp:951-990) resolved per call by [cyclopediaTracker]; a `null`
     * result mirrors `Player::client == nullptr`. The `Player` supplies its own
     * [CyclopediaMonsterTrackerClient] property and keeps the `if (!client)
     * return;` gate on the host entry points (player.cpp:947, 967).
     */
    private val cyclopediaTrackerClient: () -> CyclopediaMonsterTrackerClient? = { null },
    /**
     * `Player::getEquippedItems()` (player.cpp:5949) seam of [combatBonuses];
     * defaults to the injected [wheelPlayer] inventory.
     */
    private val getEquippedItems: () -> List<Item> = { wheelPlayer?.getEquippedItems() ?: emptyList() },
    /**
     * `Item::getCharges()` (src/items/item.hpp:107) seam of [combatBonuses];
     * defaults to the port's `ItemAttribute.CHARGES` backing store.
     */
    private val getCharges: (Item) -> Int = { item ->
        (item.getAttribute(ItemAttribute.CHARGES) as? Number)?.toInt() ?: 0
    },
    /**
     * `g_game().transformItem(item, item->getID(), charges - 1)` seam of
     * [combatBonuses] and [magicAffinity]. Public read view: the composition
     * root binds one world lambda (`Game::transformItem`, game.cpp) and shares
     * it across every `Player`.
     */
    val transformItemCharges: (Item) -> Unit = {},
    /** `PlayerWheel::getSpecializedMagic(combat)` (player_wheel.cpp:405) seam of [magicAffinity]. */
    private val wheelSpecializedMagic: (CombatType) -> Int = { 0 },
    /** `WeaponProficiency::getSpecializedMagic(combat)` (weapon_proficiency.hpp:65) seam of [magicAffinity]. */
    private val proficiencySpecializedMagic: (CombatType) -> Int = { 0 },
    /** `WeaponProficiency::getPerfectShotBonus()` (weapon_proficiency.cpp:1201) seam of [combatBonuses]. */
    private val perfectShotBonus: () -> WeaponProficiencyPerfectShotBonus? = { null },
    /** `Creature::reflectPercent[combatTypeToIndex(combat)]` (creature.hpp:911) seam of [combatBonuses]. */
    private val baseReflectPercent: (Int) -> Double = { 0.0 },
    /** `Creature::reflectFlat[combatTypeToIndex(combat)]` (creature.hpp:912) seam of [combatBonuses]. */
    private val baseReflectFlat: (Int) -> Int = { 0 },
    /** `g_configManager().getNumber(MAX_MESSAGEBUFFER)` (configmanager.cpp:315) seam of [messageBuffer]. */
    private val maxMessageBuffer: () -> Int = { 0 },
    /** `Player::hasFlag(PlayerFlags_t::CannotBeMuted)` seam of [messageBuffer]. */
    private val cannotBeMuted: () -> Boolean = { wheelPlayer?.hasFlag(PlayerFlags_t.CannotBeMuted) ?: false },
    /** `Player::addCondition(condition)` (player.cpp:3537) seam of [messageBuffer]. */
    private val addCondition: (Condition) -> Unit = { condition -> wheelPlayer?.addCondition(condition) },
    /** `Player::sendTextMessage(MESSAGE_FAILURE, ...)` (player.cpp:3541) seam of [messageBuffer]. */
    private val sendTextMessage: (MessageClass, String) -> Unit = { type, text ->
        wheelPlayer?.sendTextMessageTyped(type, text)
    },
    /**
     * `Player::muteCountMap` (player.hpp:281) shared per world; see the
     * call-site note. Public read view so the composition root and transcript
     * tests can observe that every `Player` gets the same instance.
     */
    val muteCountMap: MutableMap<Long, Int> = mutableMapOf(),
    /**
     * `AnimusMastery m_animusMastery` (player.hpp:1966); accessor
     * `Player::animusMastery()` (player.cpp:12812-12818). The default carries
     * Canary's `ANIMUS_MASTERY_*` defaults (configmanager.cpp:222-224, 390);
     * the composition root injects an instance built from the loaded config.
     */
    val animusMastery: AnimusMastery = AnimusMastery(),
    /**
     * `Player&` view of [achievement] (`player_achievement.hpp:31`); when
     * absent the holder adapts the injected [wheelPlayer] host.
     */
    private val achievementPlayer: AchievementPlayer? = null,
    /**
     * `m_player.kv()` root of [achievement] (player_achievement.cpp:81, 87, 92,
     * 136); defaults to the host's `kv` (player.cpp:1398-1400).
     */
    private val achievementKv: KV? = null,
    /** `g_game()` achievement registry seam of [achievement] (game.cpp:12481-12503). */
    val achievementRegistry: AchievementRegistry = InMemoryAchievementRegistry(),
    /**
     * `Player::sendCyclopediaCharacterAchievements(secretsUnlocked, achievements)`
     * (player.cpp:8438-8441) seam of [achievement]. The Kotlin `Player` has no
     * such protocol method yet, so the default is inert (PARITY GAP).
     */
    private val sendCyclopediaCharacterAchievements: (Int, List<Pair<Achievement, Long>>) -> Unit = { _, _ -> },
) {
    /** `PlayerWheel m_wheelPlayer` (player.hpp:1959); accessor `wheel()`. */
    val wheel: PlayerWheel by lazy(LazyThreadSafetyMode.NONE) {
        PlayerWheel(vocationCipId = vocationIdProvider(), level = levelProvider(), player = wheelPlayer)
    }

    /** Offline-training engine over `Player::offlineTrainingSkill/Time` (player.hpp:1807-1808). */
    val offlineTraining: OfflineTraining by lazy(LazyThreadSafetyMode.NONE) {
        OfflineTraining(vocationRequirementsProvider())
    }

    /** `Player::activeConcoctions` (player.hpp:1898). */
    val concoctions: PlayerConcoctions = PlayerConcoctions()

    /** `Player::isDailyReward` (player.hpp:1797). */
    val dailyReward: PlayerDailyReward = PlayerDailyReward()

    /** `Player` mount state (`getCurrentMount`/`toggleMount`/...). */
    val mounts: PlayerMountState = PlayerMountState(mountDefinitions)

    /** `Player::familiars` (player.cpp:6891-6958). */
    val familiars: PlayerFamiliars by lazy(LazyThreadSafetyMode.NONE) {
        PlayerFamiliars(
            definitions = familiarDefinitions,
            vocationId = vocationIdProvider(),
            isPremium = isPremiumProvider(),
            hasAccess = hasAccessProvider(),
        )
    }

    /** `Player` bestiary/charm state (player.hpp:1836-1841). */
    val bestiary: PlayerBestiaryState = PlayerBestiaryState()

    /**
     * `Player::preys` / `Player::preyCards` (player.hpp:1689, 1728) through the
     * prey engine state holder; the `Player` accessors delegate to it.
     */
    val prey: PlayerPreyState = PlayerPreyState()

    /**
     * `Player::taskHunting` / `Player::taskHuntingPoints` (player.hpp:1690, 1729).
     * `Player::isCreatureUnlockedOnTaskHunting` reads the bestiary kill count
     * (player.cpp:10527-10533), wired to the owned [bestiary] state.
     */
    val taskHunting: PlayerTaskHuntingState = PlayerTaskHuntingState(
        bestiaryKillCount = { raceId -> bestiary.getBestiaryKillCount(raceId) },
    )

    /** Exaltation Forge dust ledger and fusion trivance core. */
    val forge: Forge = Forge(forgeConfig)

    /** `PlayerForgeHistory m_forgeHistoryPlayer` (player.hpp:1969). */
    val forgeHistory: PlayerForgeHistory = PlayerForgeHistory(playerId)

    /** `Player` Gnomprona Hazard state (player.cpp:5513-5647). */
    val hazard: PlayerHazard = PlayerHazard()

    /**
     * `Player::getRewardChest()` (player.cpp:2058) over the `rewardChest` member
     * (player.hpp:1773). Created lazily as `RewardChest(ITEM_REWARD_CHEST)` the
     * first time it is accessed, exactly like Canary's `std::make_shared`.
     */
    val rewardChest: RewardChest by lazy(LazyThreadSafetyMode.NONE) {
        RewardChest(RewardChest.ITEM_REWARD_CHEST)
    }

    /**
     * `PlayerCyclopedia m_playerCyclopedia` (player.hpp:1963); the component is
     * constructed over the injected [CyclopediaPlayer] view and is stateless
     * beyond that reference (`player_cyclopedia.hpp:41-43`).
     */
    val cyclopedia: PlayerCyclopedia by lazy(LazyThreadSafetyMode.NONE) {
        PlayerCyclopedia(cyclopediaPlayer ?: NullCyclopediaPlayer)
    }

    /** `Player::inventoryAbilities[CONST_SLOT_LAST + 1]` (player.hpp:1874). */
    val itemAbilities: PlayerItemAbilityState = PlayerItemAbilityState()

    /**
     * `Player::bossPoints`/`bossIdSlotOne`/`bossIdSlotTwo`/`bossRemoveTimes`
     * (player.hpp:1731) over the bosstiary counters of player.cpp:12673-12724.
     */
    val bossPoints: PlayerBossPointsState = PlayerBossPointsState()

    /**
     * `Player::m_bestiaryMonsterTracker`/`m_bosstiaryMonsterTracker`
     * (player.hpp:1700-1701) over the client-resolving
     * [cyclopediaTrackerClient] seam.
     */
    val cyclopediaTracker: PlayerCyclopediaTrackerState =
        PlayerCyclopediaTrackerState(HostCyclopediaTrackerClient(cyclopediaTrackerClient))

    /**
     * `Player::aimAtTargetSpellIds` (player.hpp:1705) over the aim-at-target
     * update hooks of player.cpp:13361-13371.
     */
    val aimAtTarget: PlayerAimAtTargetState = PlayerAimAtTargetState()

    /**
     * `Player::specializedMagicLevel`/`magicShieldCapacityFlat`/
     * `magicShieldCapacityPercent` (player.hpp:1821-1824) over the equipped
     * item seams (player.cpp:7606-7687).
     */
    val magicAffinity: PlayerMagicAffinityState = PlayerMagicAffinityState(
        getEquippedItems = getEquippedItems,
        wheelSpecializedMagic = wheelSpecializedMagic,
        proficiencySpecializedMagic = proficiencySpecializedMagic,
        getCharges = getCharges,
        transformItemCharges = transformItemCharges,
    )

    /**
     * Per-weapon proficiency state (`WeaponProficiency proficiency`,
     * weapon_proficiency.hpp:135; `Player::m_weaponProficiency`, player.hpp:1970),
     * keyed by weapon id. Added as the persistence sink for the
     * `weapon-proficiency` state; the combat-application engine is not yet wired
     * into this holder (see PARITY GAPS).
     */
    val weaponProficiencies: MutableMap<Int, WeaponProficiencyData> = linkedMapOf()

    /**
     * `Player::cleavePercent`/`perfectShot` and the equipped-item reflect
     * queries (player.cpp:1051-1069, 7560-7600, 7683-7723) over [getEquippedItems].
     */
    val combatBonuses: PlayerCombatBonusState = PlayerCombatBonusState(
        getEquippedItems = getEquippedItems,
        getCharges = getCharges,
        transformItemCharges = transformItemCharges,
        perfectShotBonus = perfectShotBonus,
        baseReflectPercent = baseReflectPercent,
        baseReflectFlat = baseReflectFlat,
    )

    /** `Player::m_activeFoods` (player.hpp:1677) food-duration state. */
    val activeFoods: PlayerActiveFoods = PlayerActiveFoods()

    /**
     * `Player::MessageBufferCount` chat anti-spam state (player.hpp:1804,
     * player.cpp:3508-3543).
     */
    val messageBuffer: PlayerMessageBuffer = PlayerMessageBuffer(
        maxMessageBuffer = maxMessageBuffer,
        cannotBeMuted = cannotBeMuted,
        guid = { playerId },
        addCondition = addCondition,
        sendTextMessage = sendTextMessage,
        muteCountMap = muteCountMap,
    )

    /**
     * `PlayerAchievement m_playerAchievement` (player.hpp:1961); accessor
     * `Player::achiev()` (player.cpp:12758-12764). Created lazily so the host's
     * `kv` (player.cpp:1398-1400) is only resolved after the `Player` finished
     * constructing; [wheelPlayer] is the live host the C++ member embeds.
     */
    val achievement: PlayerAchievement by lazy(LazyThreadSafetyMode.NONE) {
        PlayerAchievement(
            player = achievementPlayer ?: HostAchievementPlayer(
                wheelPlayer,
                sendTextMessage,
                sendCyclopediaCharacterAchievements,
            ),
            kv = achievementKv ?: wheelPlayer?.kv ?: InMemoryKV(),
            registry = achievementRegistry,
        )
    }

    /** `Player::achiev()` (player.cpp:12758): the composed [achievement] component. */
    fun achiev(): PlayerAchievement = achievement

    /** `Player::animusMastery()` (player.cpp:12812): the composed [animusMastery] component. */
    fun animusMastery(): AnimusMastery = animusMastery

    /**
     * `Player::loyaltyPoints`/`loyaltyBonusPercent`/`loyaltyTitle`
     * (player.hpp:1709,1796,1850) through the ported [PlayerLoyaltyState]; the
     * `loyalty` Lua bindings and [HostBadgePlayer] read this same instance.
     * Absent when the holder is constructed without a player (tooling/tests).
     */
    val loyalty: PlayerLoyaltyState? by lazy(LazyThreadSafetyMode.NONE) {
        wheelPlayer?.let { PlayerLoyaltyState(it) }
    }

    /**
     * `PlayerBadge m_badge` (player.hpp:1960); accessor `Player::badge()`
     * (player.cpp:12767-12774). Backed by [badgeRegistry] and the host `kv`
     * (player_badge.cpp:104), created lazily like the C++ member.
     */
    val badge: PlayerBadge by lazy(LazyThreadSafetyMode.NONE) {
        PlayerBadge(
            player = HostBadgePlayer(wheelPlayer) { loyalty?.getLoyaltyPoints() ?: 0L },
            kv = wheelPlayer?.kv ?: InMemoryKV(),
            registry = badgeRegistry,
        )
    }

    /**
     * `PlayerTitle m_title` (player.hpp:1962); accessor `Player::title()`
     * (player.cpp:12776-12783). Backed by [titleRegistry] and the host `kv`
     * (player_title.cpp:89), created lazily like the C++ member.
     */
    val title: PlayerTitle by lazy(LazyThreadSafetyMode.NONE) {
        PlayerTitle(
            player = HostTitlePlayer(wheelPlayer, this),
            kv = wheelPlayer?.kv ?: InMemoryKV(),
            registry = titleRegistry,
            getMounts = { mountDefinitions.getMounts() },
        )
    }

    /**
     * `PlayerAttachedEffects m_attachedEffects` (player.hpp:1964); accessor
     * `Player::attachedEffects()` (player.cpp:12821-12828). Backed by
     * [attachedEffectDefinitions] and [attachedOutfits], created lazily like
     * the C++ member.
     */
    val attachedEffects: PlayerAttachedEffects by lazy(LazyThreadSafetyMode.NONE) {
        PlayerAttachedEffects(
            player = HostAttachedEffectsPlayer(wheelPlayer),
            attachedEffects = attachedEffectDefinitions,
            outfits = attachedOutfits,
        )
    }
}

/**
 * Default [VocationRequirements] used until the Kotlin `Player` carries a
 * vocation. It reports zero requirements so the offline-training engine is
 * constructible and inert; the real `Vocation::getReqSkillTries` /
 * `Vocation::getReqMana` formulas are still an unported parity gap.
 */
internal object UnwiredVocationRequirements : VocationRequirements {
    override fun getReqSkillTries(skill: Skill, level: Int): Long = 0L
    override fun getReqMana(magLevel: Int): Long = 0L
}

/**
 * Null [CyclopediaPlayer] used when a [PlayerFeatures] is constructed without a
 * live player view (tooling and unit tests). The `Player` always supplies an
 * adapter over itself, so the summary reads and sends only reach this object in
 * the unwired configuration, where they are inert.
 */
internal object NullCyclopediaPlayer : CyclopediaPlayer {
    private val kv: KV = InMemoryKV()

    override fun getGUID(): Long = 0

    override fun getID(): Int = 0

    override fun getName(): String = ""

    override fun kv(): KV = kv

    override fun addAsyncOngoingTask(task: PlayerAsyncTask) = Unit

    override fun resetAsyncOngoingTask(task: PlayerAsyncTask) = Unit

    override fun sendCyclopediaCharacterRecentDeaths(page: Int, pages: Int, entries: List<RecentDeathEntry>) = Unit

    override fun sendCyclopediaCharacterRecentPvPKills(page: Int, pages: Int, entries: List<RecentPvPKillEntry>) = Unit
}

/**
 * [CyclopediaTrackerClient] adapter of [PlayerFeatures.cyclopediaTracker] over
 * the host [CyclopediaMonsterTrackerClient] seam. The state holder keeps
 * Canary's null-tolerant `shared_ptr` set (`player.hpp:1700-1701`); the host
 * client takes the non-null projection because the host entry points only
 * insert live [MonsterType]s. The client is resolved per call, so rebinding
 * `Player::cyclopediaMonsterTrackerClient` is observed immediately.
 */
private class HostCyclopediaTrackerClient(
    private val client: () -> CyclopediaMonsterTrackerClient?,
) : CyclopediaTrackerClient {

    override fun parseSendBosstiary() {
        client()?.parseSendBosstiary()
    }

    override fun sendBestiaryEntryChanged(raceId: Int) {
        client()?.sendBestiaryEntryChanged(raceId)
    }

    override fun refreshCyclopediaMonsterTracker(trackerList: Set<MonsterType?>, isBoss: Boolean) {
        client()?.refreshCyclopediaMonsterTracker(trackerList.filterNotNull().toSet(), isBoss)
    }
}

/**
 * [AchievementPlayer] adapter of [PlayerFeatures.achievement] over the live
 * [wheelPlayer] host. The unlock message is sent as
 * `MESSAGE_EVENT_ADVANCE` (player_achievement.cpp:30) through the injected
 * typed-text seam, and the cyclopedia send through
 * [PlayerFeatures.sendCyclopediaCharacterAchievements]. Reads resolve per
 * call, so rebinding `Player.sendTextMessageTyped` is observed immediately; a
 * `null` host (tooling/tests) yields the inert projection.
 */
private class HostAchievementPlayer(
    private val player: Player?,
    private val sendTypedTextMessage: (MessageClass, String) -> Unit,
    private val sendAchievements: (Int, List<Pair<Achievement, Long>>) -> Unit,
) : AchievementPlayer {

    override fun getName(): String = player?.name ?: ""

    override fun sendTextMessage(message: String) {
        sendTypedTextMessage(MessageClass.MESSAGE_EVENT_ADVANCE, message)
    }

    override fun sendCyclopediaCharacterAchievements(
        secretsUnlocked: Int,
        achievementsUnlocked: List<Pair<Achievement, Long>>,
    ) {
        sendAchievements(secretsUnlocked, achievementsUnlocked)
    }
}

/**
 * [BadgePlayer] adapter of [PlayerFeatures.badge] over the live [wheelPlayer]
 * host. `Player::getName()` reads `Creature::name`, `Player::getAccount()`
 * reads [Player.account] and `Player::getLoyaltyPoints()` reads the composed
 * [PlayerFeatures.loyalty] state (player.cpp:10683). A `null` host
 * (tooling/tests) yields the inert projection.
 */
private class HostBadgePlayer(
    private val player: Player?,
    private val loyaltyPoints: () -> Long,
) : BadgePlayer {

    override fun getName(): String = player?.name ?: ""

    override fun getLoyaltyPoints(): Int = loyaltyPoints().toInt()

    override fun getAccount(): Account? = player?.account
}

/**
 * [TitlePlayer] adapter of [PlayerFeatures.title] over the live [wheelPlayer]
 * host. Reads resolve per call so the `Player` rebinding of level, bestiary,
 * task-hunting, boss-points and mount state is observed immediately; a `null`
 * host (tooling/tests) yields the inert projection.
 *
 * PARITY GAPS: `Player::getBankBalance` (`PlayerVitalsState.bankBalance`),
 * `Player::outfits` (`PlayerOutfitState`) and `Player::canWear` are state
 * holders this port does not compose on `Player` yet, so `checkGold`,
 * `checkOutfit` and the outfit-based `checkOther` titles read their zero/false
 * defaults until the host wiring lands.
 */
private class HostTitlePlayer(
    private val player: Player?,
    private val features: PlayerFeatures,
) : TitlePlayer {

    override fun getName(): String = player?.name ?: ""

    override fun getSex(): PlayerSex_t = player?.getSex() ?: PlayerSex_t.MALE

    override fun getBankBalance(): Long = 0L

    override fun hasMount(mount: Mount): Boolean = features.mounts.hasMount(mount)

    override fun getOutfitsCount(): Int = 0

    override fun getLevel(): Int = player?.level ?: 1

    override fun getGUID(): Long = player?.getGUID() ?: 0L

    override fun getBestiaryKillCount(raceid: Int): Int = features.bestiary.getBestiaryKillCount(raceid)

    override fun getTaskHuntingPoints(): Long = features.taskHunting.getTaskHuntingPoints()

    override fun getCharmPoints(): Int = features.bestiary.getCharmPoints()

    override fun getBossPoints(): Int = features.bossPoints.getBossPoints().toInt()

    override fun canWear(lookType: Int, addons: Int): Boolean = false

    override fun getGuildRank(): GuildRank? = player?.getGuildRank()
}

/**
 * [AttachedEffectsPlayer] adapter of [PlayerFeatures.attachedEffects] over the
 * live [wheelPlayer] host. Outfit reads/writes, storage, `kv`, access checks
 * and the cancel/outfit-change protocol seams resolve per call; a `null` host
 * (tooling/tests) yields the inert projection.
 *
 * PARITY NOTES:
 *  - `Player::m_storage` (player_storage.hpp:168) is not composed on the
 *    Kotlin `Player` yet, so [getStorageValue]/[addStorageValue] keep the
 *    per-feature storage in this adapter. `getStorageValue` returns -1 for a
 *    missing key like `PlayerStorage::get` (player_storage.cpp:95-104) so the
 *    wing/aura/effect range arithmetic of player_attached_effects.cpp:145-195,
 *    265-308, 440-483, 615-657 behaves exactly like Canary.
 *  - `Player::isRandomMounted()` and `Player::sendOutfitWindow()` have no
 *    Kotlin `Player` seam yet (PARITY GAP); they default to `false`/no-op.
 */
private class HostAttachedEffectsPlayer(
    private val player: Player?,
) : AttachedEffectsPlayer {

    private val storage = mutableMapOf<Int, Int>()

    private val fallbackKv: KV = InMemoryKV()

    override fun getSex(): PlayerSex_t = player?.getSex() ?: PlayerSex_t.MALE

    override fun getDefaultOutfit(): Outfit = player?.getDefaultOutfit() ?: Outfit()

    override fun setDefaultOutfit(outfit: Outfit) {
        player?.setDefaultOutfit(outfit)
    }

    override fun getStorageValue(key: Int): Int = storage[key] ?: -1

    override fun addStorageValue(key: Int, value: Int) {
        if (value == -1) {
            storage.remove(key)
        } else {
            storage[key] = value
        }
    }

    override fun kv(): KV = player?.kv ?: fallbackKv

    override fun isAccessPlayer(): Boolean = player?.isAccessPlayer() ?: false

    override fun isRandomMounted(): Boolean = false

    override fun hasCondition(conditionType: ConditionType): Boolean =
        player?.hasCondition(conditionType) ?: false

    override fun sendCancelMessage(returnValue: ReturnValue) {
        player?.sendCancelMessage?.invoke(returnValue)
    }

    override fun sendOutfitWindow() = Unit

    override fun internalCreatureChangeOutfit(outfit: Outfit) {
        player?.let { it.internalCreatureChangeOutfit(it, outfit) }
    }
}
