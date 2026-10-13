package org.opentibia.canary.database

/**
 * Mirror of Canary `enum TransactionStates_t`
 * (src/database/database_definitions.hpp:13-17); enumerator names and implicit
 * values are preserved (STATE_NO_START=0, STATE_START=1, STATE_COMMIT=2).
 */
enum class TransactionStates_t(val value: Int) {
    STATE_NO_START(0),
    STATE_START(1),
    STATE_COMMIT(2),
}
