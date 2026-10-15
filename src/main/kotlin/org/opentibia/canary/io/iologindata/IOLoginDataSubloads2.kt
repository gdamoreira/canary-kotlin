package org.opentibia.canary.io.iologindata

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.monsters.MonsterType
import org.opentibia.canary.creatures.monsters.Monsters
import org.opentibia.canary.io.IOBestiaryPersistence
import org.opentibia.canary.items.Container
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemTypeRegistry
import org.opentibia.canary.systems.imbuement.Imbuements
import org.opentibia.canary.systems.prey.IOPreyPersistence
import org.opentibia.canary.systems.rewardchest.Reward

/**
 * Wave-56 part 2 of the `IOLoginData::loadPlayer` sub-loads
 * (src/io/iologindata.cpp:95-185 over
 * src/io/functions/iologindata_load_player.cpp), split from [IOLoginData] so the
 * part-1 sibling can edit the main class without file overlap:
 *
 *  - [loadPlayerBestiaryCharms]: `IOLoginDataLoad::loadPlayerBestiaryCharms`
 *    (iologindata_load_player.cpp:472-525) through the wave-36
 *    [IOBestiaryPersistence] and `Player::addMonsterToCyclopediaTrackerList`
 *    (player.cpp:945-963).
 *  - [loadPlayerStoreInbox]: `IOLoginDataLoad::loadPlayerStoreInbox`
 *    (iologindata_load_player.cpp:634-643).
 *  - [loadRewardItems]: `IOLoginDataLoad::loadRewardItems` plus
 *    `IOLoginDataLoad::bindRewardBag`/`insertItemsIntoRewardBag`
 *    (iologindata_load_player.cpp:645-661, 953-990) over the wave-36
 *    `PlayerRewardsRepository` reached through [IOLoginData.loadRewardItems].
 *  - [loadPlayerPreyClass]/[loadPlayerTaskHuntingClass]:
 *    `IOLoginDataLoad::loadPlayerPreyClass`/`loadPlayerTaskHuntingClass`
 *    (iologindata_load_player.cpp:808-903) through the wave-36
 *    [IOPreyPersistence].
 *
 * All dependencies are constructor-injected (AGENTS.md 1.1): the
 * `g_monsters()` race-id lookup becomes [monsterTypeByRaceId], the
 * `Item::CreateItem(ITEM_STORE_INBOX)` global registry becomes [itemTypes] and
 * a `null` seam keeps the corresponding sub-load inert, mirroring the C++
 * `if (!client)`/`if (!result)` guards' observable behavior.
 *
 * PARITY GAPS: Canary applies the bestiary/kill-count columns of the same
 * `player_charms` row through `Player::storage()` and the wheel/bosstiary
 * columns through `loadOnlyDataForOnlinePlayer`; the Kotlin port keeps those
 * with their owning loaders. The reward proxy is a real [Container]
 * ([Reward]), so [insertItemsIntoRewardBag] reaches it through
 * `Container::internalAddThing` and every loaded child gets its parent link;
 * the chest itself is still not a `Cylinder` (RewardChest.kt parity gap), so
 * the rebind only re-keys the proxy to the chest id, like the C++ `itemPair`
 * replacement.
 */
class IOLoginDataSubloads2(
    /**
     * The owning loader: [loadRewardItems] reuses its public [IOLoginData.loadItems]
     * materialization and `PlayerRewardsRepository` row read, so the reward
     * rows take the same attribute codec/registry path as every other item
     * tree.
     */
    private val loginData: IOLoginData,
    /** Wave-36 `player_charms` sink; `null` keeps the bestiary sub-load inert. */
    private val bestiaryPersistence: IOBestiaryPersistence? = null,
    /** Wave-36 `player_prey`/`player_taskhunt` sink; `null` keeps both sub-loads inert. */
    private val preyPersistence: IOPreyPersistence? = null,
    /** `Item::items` registry of `Item::CreateItem(ITEM_STORE_INBOX)` (item.cpp:393-472). */
    private val itemTypes: ItemTypeRegistry? = null,
    /** `g_imbuements()` seam of the created store inbox item. */
    private val imbuements: Imbuements? = null,
    /**
     * `g_monsters()` registry whose `getMonsterTypeByRaceId`
     * (monsters.cpp:375-388) resolves the persisted tracker race ids. The
     * composition root passes the booted registry; when only the lambda seam is
     * supplied the registry stays unused.
     */
    private val monsters: Monsters? = null,
    /**
     * `g_monsters().getMonsterTypeByRaceId` (monsters.cpp:375-388) resolution of
     * the persisted tracker race ids; an unresolved id is dropped exactly like
     * the C++ `if (monsterType)` guard. Defaults to the injected [monsters]
     * registry lookup.
     */
    private val monsterTypeByRaceId: (raceId: Int, isBoss: Boolean) -> MonsterType? = { raceId, isBoss ->
        monsters?.getMonsterTypeByRaceId(raceId, isBoss)
    },
) {

    /**
     * `IOLoginDataLoad::loadPlayerBestiaryCharms`
     * (iologindata_load_player.cpp:472-525): the persisted charm state is
     * applied through [IOBestiaryPersistence.loadPlayerBestiaryCharms] and the
     * returned `tracker list` race ids are re-added to the bestiary tracker,
     * dropping ids [monsterTypeByRaceId] cannot resolve.
     */
    fun loadPlayerBestiaryCharms(player: Player) {
        val persistence = bestiaryPersistence ?: return
        val tracker = persistence.loadPlayerBestiaryCharms(player.id, player.features.bestiary)
        for (raceId in tracker) {
            val monsterType = monsterTypeByRaceId(raceId, false) ?: continue
            player.addMonsterToCyclopediaTrackerList(monsterType, isBoss = false, reloadClient = false)
        }
    }

    /**
     * `IOLoginDataLoad::loadPlayerAnimusMastery`
     * (iologindata_load_player.cpp:277-289): the `players.animus_mastery`
     * blob's lowercased monster names are re-added through
     * `Player::animusMastery()` (player.cpp:12812), which lowercases again and
     * de-duplicates exactly like `AnimusMastery::add`
     * (animus_mastery.cpp:27-32). An unwired sink keeps the sub-load inert like
     * the other unwired seams.
     */
    fun loadPlayerAnimusMastery(player: Player) {
        val persistence = bestiaryPersistence ?: return
        for (monsterName in persistence.loadAnimusMastery(player.id)) {
            player.features.animusMastery().add(monsterName)
        }
    }

    /**
     * `IOLoginDataLoad::loadPlayerStoreInbox`
     * (iologindata_load_player.cpp:634-643): `CONST_SLOT_STORE_INBOX` receives
     * a fresh `ITEM_STORE_INBOX` only while the slot is empty.
     */
    fun loadPlayerStoreInbox(player: Player) {
        if (player.getInventoryItem(Slots_t.CONST_SLOT_STORE_INBOX) != null) {
            return
        }

        val storeInbox = Item.create(Container.ITEM_STORE_INBOX, registry = itemTypes, imbuements = imbuements)
        player.internalAddThing(Slots_t.CONST_SLOT_STORE_INBOX.value, storeInbox)
    }

    /**
     * `IOLoginDataLoad::loadRewardItems`
     * (iologindata_load_player.cpp:645-661): the `player_rewards` rows are
     * materialized with the shared [IOLoginData.loadItems], rebound to the live
     * reward chest through [bindRewardBag] and attached through
     * [insertItemsIntoRewardBag].
     */
    fun loadRewardItems(player: Player) {
        val rewardItems = sortedMapOf<Int, Pair<Item, Int>>()
        loginData.loadItems(rewardItems, loginData.loadRewardItems(player.id), player)
        bindRewardBag(player, rewardItems)
        insertItemsIntoRewardBag(rewardItems)
    }

    /**
     * `IOLoginDataLoad::loadPlayerPreyClass`
     * (iologindata_load_player.cpp:808-853): `Player::isPremium()` (player.cpp:7532)
     * feeds the `PreySlot_Two` locked-slot unlock before
     * [IOPreyPersistence.loadPlayerPreyClass].
     */
    fun loadPlayerPreyClass(player: Player) {
        val persistence = preyPersistence ?: return
        player.features.prey.isPremium = player.isPremium()
        persistence.loadPlayerPreyClass(player.features.prey, player.id)
    }

    /**
     * `IOLoginDataLoad::loadPlayerTaskHuntingClass`
     * (iologindata_load_player.cpp:855-903): `Player::isPremium()` (player.cpp:7532)
     * feeds the `PreySlot_Two` locked-slot unlock before
     * [IOPreyPersistence.loadPlayerTaskHuntingClass].
     */
    fun loadPlayerTaskHuntingClass(player: Player) {
        val persistence = preyPersistence ?: return
        player.features.taskHunting.isPremium = player.isPremium()
        persistence.loadPlayerTaskHuntingClass(player.features.taskHunting, player.id)
    }

    /**
     * `IOLoginDataLoad::bindRewardBag`
     * (iologindata_load_player.cpp:953-970): every direct reward row (`pid == 0`)
     * is rebound from its materialized `ITEM_REWARD_CONTAINER` to the live
     * reward proxy created by `Player::getReward`, keyed by the reward id held
     * in the `DATE` attribute, and re-parented to the reward chest id. The
     * ascending `sid` map order is the C++ `std::map` order, so the `pid != 0`
     * break mirrors the C++ early exit.
     */
    private fun bindRewardBag(player: Player, rewardItemsMap: ItemsMap) {
        val reboundRewards = mutableListOf<Pair<Int, Pair<Item, Int>>>()

        for ((sid, pair) in rewardItemsMap) {
            val (item, pid) = pair
            if (pid != 0) {
                break
            }

            val rewardId = (item.getAttribute(ItemAttribute.DATE) as? Number)?.toLong() ?: 0L
            val rewardChest = player.getRewardChest()
            val reward = rewardChest.getReward(rewardId, autoCreate = true) ?: continue
            reboundRewards.add(sid to (reward to rewardChest.id))
        }

        for ((sid, replacement) in reboundRewards) {
            rewardItemsMap[sid] = replacement
        }
    }

    /**
     * `IOLoginDataLoad::insertItemsIntoRewardBag`
     * (iologindata_load_player.cpp:972-990): descending `sid` order, every
     * non-direct item is attached to the container of the row `pid` resolved
     * to. The [Reward] proxy is a [Container], so `getContainer()` resolves it
     * exactly like the C++ `rewardIt->second.first->getContainer()` and
     * `Container::internalAddThing` prepends the child (restoring the ascending
     * serialized order) and sets its parent link.
     */
    private fun insertItemsIntoRewardBag(rewardItemsMap: ItemsMap) {
        for ((_, pair) in rewardItemsMap.entries.reversed()) {
            val (item, pid) = pair
            if (pid == 0) {
                break
            }

            val parentItem = rewardItemsMap[pid]?.first ?: continue
            parentItem.getContainer()?.internalAddThing(item)
        }
    }
}
