package org.opentibia.canary.database

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

/**
 * Enum value parity with Canary `src/database/database_definitions.hpp`
 * (`TransactionStates_t`). The values are consumed by `DBTransaction`
 * (src/database/database.hpp:282-353) to guard begin/commit/rollback.
 */
class DatabaseDefinitionsTest {

    @Test
    fun `transaction state values mirror TransactionStates_t`() {
        // database_definitions.hpp:13-17.
        assertThat(TransactionStates_t.STATE_NO_START.value).isEqualTo(0)
        assertThat(TransactionStates_t.STATE_START.value).isEqualTo(1)
        assertThat(TransactionStates_t.STATE_COMMIT.value).isEqualTo(2)
    }

    @Test
    fun `transaction state enumeration matches declaration order`() {
        assertThat(TransactionStates_t.entries.map { it.value }).containsExactly(0, 1, 2)
        assertThat(TransactionStates_t.entries.map { it.name })
            .containsExactly("STATE_NO_START", "STATE_START", "STATE_COMMIT")
    }
}
