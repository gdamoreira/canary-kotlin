package org.opentibia.canary.creatures.players

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.opentibia.canary.combat.CombatType
import org.opentibia.canary.combat.condition.ConditionGeneric
import org.opentibia.canary.combat.condition.ConditionId
import org.opentibia.canary.combat.condition.ConditionType
import org.opentibia.canary.creatures.FindPathParams
import org.opentibia.canary.creatures.Player
import org.opentibia.canary.creatures.PlayerFlags_t
import org.opentibia.canary.creatures.Slots_t
import org.opentibia.canary.creatures.WeaponType_t
import org.opentibia.canary.creatures.grouping.Party
import org.opentibia.canary.game.zones.TileFlags
import org.opentibia.canary.items.Ammo_t
import org.opentibia.canary.items.Item
import org.opentibia.canary.items.ItemType
import org.opentibia.canary.map.Position
import org.opentibia.canary.map.Tile
import org.opentibia.canary.map.house.AccessHouseLevel
import org.opentibia.canary.map.house.House
import org.opentibia.canary.social.guild.Guild
import org.opentibia.canary.spells.SoundEffect

/**
 * Transcribes the remaining Player misc accessors
 * (src/creatures/players/player.cpp:1379-1381, 1981-1996, 6056-6064,
 * 6175-6178, 7331-7339, 8335-8358, 12100-12189, 12897-12921, 13020-13028):
 *
 * - `Player::canOpenCorpse` (player.cpp:1379-1381)
 * - `Player::isNearDepotBox` (player.cpp:1981-1996)
 * - `Player::updateState` (player.cpp:6056-6064)
 * - `Player::getPathSearchParams` (player.cpp:6175-6178)
 * - `Player::updateBaseSpeed` (player.cpp:7331-7339)
 * - `Player::getHelpers` (player.cpp:8335-8358)
 * - `Player::getAttackSoundEffect` (player.cpp:12100-12142)
 * - `Player::getHitSoundEffect` (player.cpp:12144-12189)
 * - `Player::hasPermittedConditionInPZ` (player.cpp:12897-12921)
 * - `Player::canSpeakWithHireling` (player.cpp:13020-13028)
 *
 * The map tile lookup, the guild/party member lists, the house access level,
 * the `g_game().*` senders and the C++ out-param `Creature::getPathSearchParams`
 * base call are call-site seams per AGENTS.md 1.1.
 */
class PlayerMiscStateTest {

    private fun newPlayer(): Player =
        Player(id = 31, name = "Scout", maxHealth = 150, health = 150, position = Position(100, 100, 7))

    // --- Player::canOpenCorpse (player.cpp:1379-1381) -----------------------------

    @Test
    fun `canOpenCorpse accepts the owner and the party of the owner`() {
        val player = newPlayer()

        assertThat(player.canOpenCorpse(31L)).isTrue()
        assertThat(player.canOpenCorpse(32L)).isFalse()
    }

    @Test
    fun `canOpenCorpse asks the party when one is joined`() {
        val player = newPlayer()
        val leader = newPlayer()
        player.setParty(Party(leader))
        val asked = mutableListOf<Long>()

        val allowed = player.canOpenCorpse(77L) { ownerId ->
            asked.add(ownerId)
            true
        }

        assertThat(allowed).isTrue()
        assertThat(asked).containsExactly(77L)
        assertThat(player.canOpenCorpse(78L) { false }).isFalse()
    }

    // --- Player::isNearDepotBox (player.cpp:1981-1996) ---------------------------

    @Test
    fun `isNearDepotBox scans the 3x3 tile block for the depot flag`() {
        val player = newPlayer()
        val depot = Tile(Position(101, 101, 7)).apply { flags = TileFlags.TILESTATE_DEPOT.value }

        assertThat(player.isNearDepotBox { pos -> if (pos == Position(101, 101, 7)) depot else null }).isTrue()
        assertThat(player.isNearDepotBox { null }).isFalse()
    }

    @Test
    fun `isNearDepotBox ignores tiles outside the block and wrong floors`() {
        val player = newPlayer()
        val outside = Tile(Position(105, 105, 7)).apply { flags = TileFlags.TILESTATE_DEPOT.value }
        val wrongFloor = Tile(Position(100, 100, 6)).apply { flags = TileFlags.TILESTATE_DEPOT.value }

        assertThat(player.isNearDepotBox { pos -> if (pos == outside.position) outside else null }).isFalse()
        assertThat(player.isNearDepotBox { pos -> if (pos == wrongFloor.position) wrongFloor else null }).isFalse()
    }

    // --- Player::hasPermittedConditionInPZ (player.cpp:12897-12921) --------------

    @Test
    fun `hasPermittedConditionInPZ only accepts the element conditions`() {
        val player = newPlayer()
        assertThat(player.hasPermittedConditionInPZ()).isFalse()

        player.addCondition(
            ConditionGeneric(
                id = ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_INFIGHT,
                ticks = 60_000,
            ),
        )
        assertThat(player.hasPermittedConditionInPZ()).isFalse()

        player.addCondition(
            ConditionGeneric(
                id = ConditionId.CONDITIONID_DEFAULT,
                type = ConditionType.CONDITION_POISON,
                ticks = 10_000,
            ),
        )
        assertThat(player.hasPermittedConditionInPZ()).isTrue()
    }

    // --- Player::canSpeakWithHireling (player.cpp:13020-13028) -------------------

    @Test
    fun `canSpeakWithHireling only gates the hireling bubble on house access`() {
        val player = newPlayer()
        val house = House(id = 9)
        val tile = Tile(Position(100, 100, 7)).apply { this.house = house }

        assertThat(player.canSpeakWithHireling(SPEECHBUBBLE_NORMAL, { tile }, { AccessHouseLevel.HOUSE_NOT_INVITED })).isTrue()
        assertThat(player.canSpeakWithHireling(SPEECHBUBBLE_HIRELING, { null }, { AccessHouseLevel.HOUSE_GUEST })).isFalse()
        assertThat(player.canSpeakWithHireling(SPEECHBUBBLE_HIRELING, { tile }, { AccessHouseLevel.HOUSE_NOT_INVITED })).isFalse()
        assertThat(player.canSpeakWithHireling(SPEECHBUBBLE_HIRELING, { tile }, { AccessHouseLevel.HOUSE_GUEST })).isTrue()
    }

    // --- Player::getHelpers (player.cpp:8335-8358) -------------------------------

    @Test
    fun `getHelpers without guild or party is zero`() {
        assertThat(newPlayer().getHelpers()).isZero()
    }

    @Test
    fun `getHelpers with only a guild reports the online member count`() {
        val player = newPlayer()
        player.setGuild(Guild(id = 1, name = "Guild"))

        assertThat(player.getHelpers(guildMemberCountOnline = { 12 })).isEqualTo(12)
    }

    @Test
    fun `getHelpers with only a party adds members invitations and the leader`() {
        val player = newPlayer()
        val leader = newPlayer()
        val member = newPlayer()
        val other = newPlayer()
        val party = Party(leader)
        party.invitePlayer(member)
        party.joinParty(member)
        party.invitePlayer(other)
        player.setParty(party)

        assertThat(player.getHelpers()).isEqualTo(party.getMemberCount() + party.getInvitationCount() + 1)
    }

    @Test
    fun `getHelpers with guild and party deduplicates by identity`() {
        val player = newPlayer()
        val shared = newPlayer()
        val leader = newPlayer()
        player.setGuild(Guild(id = 1, name = "Guild"))
        val party = Party(leader)
        party.invitePlayer(shared)
        party.joinParty(shared)
        player.setParty(party)

        val helpers = player.getHelpers(guildMembersOnline = { listOf(shared, leader) })

        assertThat(helpers).isEqualTo(2)
    }

    // --- Player::getPathSearchParams (player.cpp:6175-6178) ----------------------

    @Test
    fun `getPathSearchParams forces a full path search after the base call`() {
        val params = FindPathParams().apply { fullPathSearch = false }
        val baseCalls = mutableListOf<Unit>()

        newPlayer().getPathSearchParams(params) { baseCalls.add(Unit) }

        assertThat(baseCalls).hasSize(1)
        assertThat(params.fullPathSearch).isTrue()
    }

    // --- Player::updateBaseSpeed (player.cpp:7331-7339) --------------------------

    @Test
    fun `updateBaseSpeed derives the speed from the vocation base and level`() {
        val player = newPlayer()
        player.level = 100

        player.updateBaseSpeed { 300 }

        assertThat(player.getBaseSpeed()).isEqualTo(399)
    }

    @Test
    fun `updateBaseSpeed pins staff speed when the max speed flag is set`() {
        val player = newPlayer()
        player.level = 100
        player.setFlag(PlayerFlags_t.SetMaxSpeed)

        player.updateBaseSpeed { 300 }

        assertThat(player.getBaseSpeed()).isEqualTo(Player.PLAYER_MAX_STAFF_SPEED)
    }

    // --- Player::updateState (player.cpp:6056-6064) ------------------------------

    @Test
    fun `updateState refreshes weight light ids and stats in order`() {
        val player = newPlayer()
        val calls = mutableListOf<String>()

        player.updateState(
            updateInventoryWeight = { calls.add("weight") },
            updateItemsLight = { calls.add("light") },
            sendInventoryIds = { calls.add("ids") },
            sendStats = { calls.add("stats") },
            updateSaleShopList = { calls.add("sale") },
        )

        assertThat(calls).containsExactly("weight", "light", "ids", "stats")
    }

    @Test
    fun `updateState schedules the sale list only with a shop owner`() {
        val player = newPlayer()
        val calls = mutableListOf<String>()

        player.updateState(updateSaleShopList = { calls.add("sale") }, hasShopOwner = { true })

        assertThat(calls).containsExactly("sale")
    }

    // --- Player::getAttackSoundEffect (player.cpp:12100-12142) -------------------

    private fun Player.equipWeapon(item: Item, type: WeaponType_t) {
        weaponTypeProvider = { id -> if (id == item.id) type else WeaponType_t.WEAPON_NONE }
        addItem(item, Slots_t.CONST_SLOT_LEFT)
    }

    @Test
    fun `getAttackSoundEffect defaults to fists without a usable weapon`() {
        assertThat(newPlayer().getAttackSoundEffect()).isEqualTo(SoundEffect.HUMAN_CLOSE_ATK_FIST)

        val player = newPlayer()
        val shield = Item(1, count = 1, type = ItemType(id = 1, name = "shield"))
        player.equipWeapon(shield, WeaponType_t.WEAPON_SHIELD)
        assertThat(player.getAttackSoundEffect()).isEqualTo(SoundEffect.HUMAN_CLOSE_ATK_FIST)
    }

    @Test
    fun `getAttackSoundEffect maps every melee family`() {
        val cases = mapOf(
            WeaponType_t.WEAPON_SWORD to SoundEffect.MELEE_ATK_SWORD,
            WeaponType_t.WEAPON_AXE to SoundEffect.MELEE_ATK_AXE,
            WeaponType_t.WEAPON_CLUB to SoundEffect.MELEE_ATK_CLUB,
            WeaponType_t.WEAPON_WAND to SoundEffect.MAGICAL_RANGE_ATK,
        )

        for ((type, expected) in cases) {
            val player = newPlayer()
            player.equipWeapon(Item(2, count = 1, type = ItemType(id = 2, name = "weapon")), type)
            assertThat(player.getAttackSoundEffect()).isEqualTo(expected)
        }
    }

    @Test
    fun `getAttackSoundEffect maps the ammo subtype for distance weapons`() {
        val bolt = Ammo_t.AMMO_BOLT.value
        val bow = Ammo_t.AMMO_ARROW.value

        val crossbow = newPlayer()
        crossbow.equipWeapon(Item(3, count = 1, type = ItemType(id = 3, name = "crossbow", ammoType = bolt)), WeaponType_t.WEAPON_DISTANCE)
        assertThat(crossbow.getAttackSoundEffect()).isEqualTo(SoundEffect.DIST_ATK_CROSSBOW)

        val bowUser = newPlayer()
        bowUser.equipWeapon(Item(4, count = 1, type = ItemType(id = 4, name = "bow", ammoType = bow)), WeaponType_t.WEAPON_DISTANCE)
        assertThat(bowUser.getAttackSoundEffect()).isEqualTo(SoundEffect.DIST_ATK_BOW)

        val thrower = newPlayer()
        thrower.equipWeapon(Item(5, count = 1, type = ItemType(id = 5, name = "stone")), WeaponType_t.WEAPON_DISTANCE)
        assertThat(thrower.getAttackSoundEffect()).isEqualTo(SoundEffect.DIST_ATK_THROW)
    }

    // --- Player::getHitSoundEffect (player.cpp:12144-12189) ----------------------

    @Test
    fun `getHitSoundEffect is silent without a weapon`() {
        assertThat(newPlayer().getHitSoundEffect()).isEqualTo(SoundEffect.SILENCE)
    }

    @Test
    fun `getHitSoundEffect maps the equipped distance weapon ammo subtype`() {
        // `Player::getWeapon` filters WEAPON_AMMO (player.cpp:414-421), so the
        // WEAPON_AMMO switch branch is unreachable through the normal weapon
        // lookup; the equipped bow/crossbow itself carries the ammo type.
        val crossbow = newPlayer()
        crossbow.equipWeapon(
            Item(6, count = 1, type = ItemType(id = 6, name = "crossbow", ammoType = Ammo_t.AMMO_BOLT.value)),
            WeaponType_t.WEAPON_DISTANCE,
        )
        assertThat(crossbow.getHitSoundEffect()).isEqualTo(SoundEffect.DIST_ATK_CROSSBOW_SHOT)

        val bow = newPlayer()
        bow.equipWeapon(
            Item(7, count = 1, type = ItemType(id = 7, name = "bow", ammoType = Ammo_t.AMMO_ARROW.value)),
            WeaponType_t.WEAPON_DISTANCE,
        )
        assertThat(bow.getHitSoundEffect()).isEqualTo(SoundEffect.DIST_ATK_BOW_SHOT)

        val plain = newPlayer()
        plain.equipWeapon(Item(8, count = 1, type = ItemType(id = 8, name = "sling")), WeaponType_t.WEAPON_DISTANCE)
        assertThat(plain.getHitSoundEffect()).isEqualTo(SoundEffect.DIST_ATK_THROW_SHOT)
    }

    @Test
    fun `getHitSoundEffect maps distance and wand weapons`() {
        val thrower = newPlayer()
        thrower.equipWeapon(Item(10, count = 1, type = ItemType(id = 10, name = "stone")), WeaponType_t.WEAPON_DISTANCE)
        assertThat(thrower.getHitSoundEffect()).isEqualTo(SoundEffect.DIST_ATK_THROW_SHOT)

        val bow = newPlayer()
        bow.equipWeapon(
            Item(11, count = 1, type = ItemType(id = 11, name = "bow", ammoType = Ammo_t.AMMO_ARROW.value)),
            WeaponType_t.WEAPON_DISTANCE,
        )
        assertThat(bow.getHitSoundEffect()).isEqualTo(SoundEffect.DIST_ATK_BOW_SHOT)

        val wand = newPlayer()
        wand.equipWeapon(Item(12, count = 1, type = ItemType(id = 12, name = "wand")), WeaponType_t.WEAPON_WAND)
        assertThat(wand.getHitSoundEffect()).isEqualTo(SoundEffect.DIST_ATK_WAND_SHOT)
    }

    companion object {
        /** `SPEECHBUBBLE_NORMAL` (`src/creatures/creatures_definitions.hpp:335`). */
        private const val SPEECHBUBBLE_NORMAL = 1

        /** `SPEECHBUBBLE_HIRELING` (`src/creatures/creatures_definitions.hpp:341`). */
        private const val SPEECHBUBBLE_HIRELING = 7

    }

    // --- client tracker seams (player.cpp:1743-1800) -----------------------------

    @Test
    fun `updatePartyTrackerAnalyzer requires a client and a party`() {
        // player.cpp:1743-1747: `if (client && m_party)`.
        val player = newPlayer()
        var forwarded = 0

        player.updatePartyTrackerAnalyzer(
            force = true,
            hasClient = { false },
            party = { Party(player) },
            clientUpdatePartyTrackerAnalyzer = { _, _ -> forwarded++ },
        )
        player.updatePartyTrackerAnalyzer(
            force = true,
            hasClient = { true },
            party = { null },
            clientUpdatePartyTrackerAnalyzer = { _, _ -> forwarded++ },
        )

        assertThat(forwarded).isZero()
    }

    @Test
    fun `updatePartyTrackerAnalyzer forwards the party and force flag`() {
        // player.cpp:1745: client->updatePartyTrackerAnalyzer(m_party, force).
        val player = newPlayer()
        val party = Party(player)
        var forwardedParty: Party? = null
        var forwardedForce: Boolean? = null

        player.updatePartyTrackerAnalyzer(
            force = true,
            hasClient = { true },
            party = { party },
            clientUpdatePartyTrackerAnalyzer = { sentParty, force ->
                forwardedParty = sentParty
                forwardedForce = force
            },
        )

        assertThat(forwardedParty).isSameAs(party)
        assertThat(forwardedForce).isTrue()
    }

    @Test
    fun `updateImpactTracker forwards to the client`() {
        // player.cpp:1790-1794.
        val player = newPlayer()
        var forwarded: Pair<CombatType, Int>? = null

        player.updateImpactTracker(
            type = CombatType.FIREDAMAGE,
            amount = 42,
            hasClient = { true },
            clientSendUpdateImpactTracker = { type, amount -> forwarded = type to amount },
        )

        assertThat(forwarded).isEqualTo(CombatType.FIREDAMAGE to 42)
    }

    @Test
    fun `updateInputAnalyzer forwards the target`() {
        // player.cpp:1796-1800.
        val player = newPlayer()
        var forwarded: Triple<CombatType, Int, String>? = null

        player.updateInputAnalyzer(
            type = CombatType.ENERGYDAMAGE,
            amount = 7,
            target = "Rat",
            hasClient = { true },
            clientSendUpdateInputAnalyzer = { type, amount, target -> forwarded = Triple(type, amount, target) },
        )

        assertThat(forwarded).isEqualTo(Triple(CombatType.ENERGYDAMAGE, 7, "Rat"))
    }
}
