package org.opentibia.canary.database

import com.zaxxer.hikari.HikariDataSource
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.items.ItemAttribute
import org.opentibia.canary.items.ItemDecayState

/**
 * H2 (Flyway V1-V5) round-trip tests for [JdbcPlayerItemsRepository],
 * transcribed from Canary's inventory persistence:
 * `IOLoginDataSave::savePlayerItems` (src/io/functions/iologindata_save_player.cpp:530-564),
 * `IOLoginDataLoad::loadPlayerInventoryItems` / `loadItems`
 * (src/io/functions/iologindata_load_player.cpp:34-65, 543-561) and
 * `Item::serializeAttr` / `Item::unserializeAttr`
 * (src/items/item.cpp:912-1349, 1368-1539; wire codes from
 * src/items/items_definitions.hpp:202-252).
 */
class JdbcPlayerItemsRepositoryTest {

    private lateinit var dataSource: HikariDataSource
    private lateinit var repository: JdbcPlayerItemsRepository

    @BeforeEach
    fun setUp() {
        dataSource = DatabaseConnectionFactory.createInMemoryDataSource("canary_items_${System.nanoTime()}")
        val applied = DatabaseConnectionFactory.migrate(dataSource)
        assertThat(applied).isGreaterThanOrEqualTo(5)
        repository = JdbcPlayerItemsRepository(dataSource)
    }

    @AfterEach
    fun tearDown() {
        dataSource.close()
    }

    @Test
    fun `round-trips a stackable item count through the slot table`() {
        repository.save(1L, listOf(PlayerItemState(slot = 3, itemId = 2148, count = 100)))

        assertThat(repository.load(1L)).containsExactly(PlayerItemState(slot = 3, itemId = 2148, count = 100))
    }

    @Test
    fun `serializes a stackable subtype with Canary's ATTR_COUNT wire code`() {
        repository.save(1L, listOf(PlayerItemState(slot = 3, itemId = 2148, count = 100)))

        assertThat(rawAttributes(1L, 3)).containsExactly(15, 100)
    }

    @Test
    fun `serializes attributed items with Canary wire codes and little-endian payloads`() {
        repository.save(
            1L,
            listOf(
                PlayerItemState(
                    slot = 6,
                    itemId = 2376,
                    attributes = mapOf(
                        ItemAttribute.ACTIONID to 1234L,
                        ItemAttribute.TEXT to "Hi",
                        ItemAttribute.TIER to 3L,
                    ),
                ),
            ),
        )

        assertThat(rawAttributes(1L, 6)).containsExactly(
            4, 0xD2, 0x04,
            6, 2, 0x00, 'H'.code, 'i'.code,
            40, 3,
        )
    }

    @Test
    fun `round-trips integer and string item attributes`() {
        val attributes = mapOf(
            ItemAttribute.STORE to 1_700_000_000_000L,
            ItemAttribute.CHARGES to 9L,
            ItemAttribute.ACTIONID to 1234L,
            ItemAttribute.TEXT to "Excalibur",
            ItemAttribute.DATE to 1_700_000_000_001L,
            ItemAttribute.WRITER to "Merlin",
            ItemAttribute.DESCRIPTION to "A legendary blade.",
            ItemAttribute.DURATION to 3600L,
            ItemAttribute.NAME to "Excalibur",
            ItemAttribute.ARTICLE to "an",
            ItemAttribute.PLURALNAME to "Excaliburs",
            ItemAttribute.WEIGHT to 5000L,
            ItemAttribute.ATTACK to 42L,
            ItemAttribute.DEFENSE to 30L,
            ItemAttribute.EXTRADEFENSE to 5L,
            ItemAttribute.MANTRA to 2L,
            ItemAttribute.IMBUEMENT_SLOT to 3L,
            ItemAttribute.OPENCONTAINER to 1L,
            ItemAttribute.ARMOR to 7L,
            ItemAttribute.HITCHANCE to 4L,
            ItemAttribute.SHOOTRANGE to 6L,
            ItemAttribute.SPECIAL to "special",
            ItemAttribute.QUICKLOOTCONTAINER to 2L,
            ItemAttribute.TIER to 3L,
            ItemAttribute.AMOUNT to 5L,
            ItemAttribute.STORE_INBOX_CATEGORY to "Loot",
            ItemAttribute.OWNER to 1L,
            ItemAttribute.OBTAINCONTAINER to 2L,
        )
        val item = PlayerItemState(slot = 6, itemId = 2376, count = 1, attributes = attributes)

        repository.save(1L, listOf(item))

        assertThat(repository.load(1L)).containsExactly(item)
    }

    @Test
    fun `writes decaying duration and state in Canary's serializeAttr order`() {
        repository.save(
            1L,
            listOf(
                PlayerItemState(
                    slot = 6,
                    itemId = 2376,
                    attributes = mapOf(
                        ItemAttribute.DURATION to 3600L,
                        ItemAttribute.DECAYSTATE to ItemDecayState.DECAYING_TRUE.value.toLong(),
                    ),
                ),
            ),
        )

        assertThat(rawAttributes(1L, 6)).containsExactly(
            16, 0x10, 0x0E, 0x00, 0x00,
            17, 1,
        )
    }

    @Test
    fun `normalizes a persisted decaying state to pending like Canary's readAttr`() {
        repository.save(
            1L,
            listOf(
                PlayerItemState(
                    slot = 6,
                    itemId = 2376,
                    attributes = mapOf(
                        ItemAttribute.DURATION to 3600L,
                        ItemAttribute.DECAYSTATE to ItemDecayState.DECAYING_TRUE.value.toLong(),
                    ),
                ),
            ),
        )

        val loaded = repository.load(1L).single()

        assertThat(loaded.attributes[ItemAttribute.DECAYSTATE])
            .isEqualTo(ItemDecayState.DECAYING_PENDING.value.toLong())
        assertThat(loaded.attributes[ItemAttribute.DURATION]).isEqualTo(3600L)
    }

    @Test
    fun `loads inventory slots in ascending order`() {
        repository.save(1L, listOf(PlayerItemState(slot = 6, itemId = 2376), PlayerItemState(slot = 3, itemId = 2148, count = 5)))

        assertThat(repository.load(1L).map { it.slot }).containsExactly(3, 6)
    }

    @Test
    fun `replaces the persisted inventory on resave`() {
        repository.save(1L, listOf(PlayerItemState(slot = 3, itemId = 2148, count = 100), PlayerItemState(slot = 6, itemId = 2376)))
        repository.save(1L, listOf(PlayerItemState(slot = 3, itemId = 2148, count = 50)))

        assertThat(repository.load(1L)).containsExactly(PlayerItemState(slot = 3, itemId = 2148, count = 50))
    }

    @Test
    fun `returns an empty inventory for a player without persisted items`() {
        assertThat(repository.load(2L)).isEmpty()
    }

    @Test
    fun `skips persisted items whose blob carries an unsupported attribute code`() {
        insertRawItem(2L, slot = 5, itemId = 2376, count = 1, blob = byteArrayOf(99, 0))

        assertThat(repository.load(2L)).isEmpty()
    }

    private fun rawAttributes(playerId: Long, slot: Int): ByteArray {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "SELECT attributes FROM player_inventory_items WHERE player_id = ? AND slot = ?",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, slot)
                stmt.executeQuery().use { rs ->
                    assertThat(rs.next()).isTrue()
                    return rs.getBytes("attributes")
                }
            }
        }
    }

    private fun insertRawItem(playerId: Long, slot: Int, itemId: Int, count: Int, blob: ByteArray) {
        dataSource.connection.use { conn ->
            conn.prepareStatement(
                "INSERT INTO player_inventory_items (player_id, slot, item_id, count, attributes) VALUES (?, ?, ?, ?, ?)",
            ).use { stmt ->
                stmt.setLong(1, playerId)
                stmt.setInt(2, slot)
                stmt.setInt(3, itemId)
                stmt.setInt(4, count)
                stmt.setBytes(5, blob)
                stmt.executeUpdate()
            }
        }
    }
}
