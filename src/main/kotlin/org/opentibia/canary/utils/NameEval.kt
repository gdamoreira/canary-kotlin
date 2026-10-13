package org.opentibia.canary.utils

/** `enum NameEval_t` (src/utils/utils_definitions.hpp:541-548). */
enum class NameEval(val value: Int) {
    VALID(0),
    INVALID(1),
    INVALID_LENGTH(2),
    INVALID_TOKEN_LENGTH(3),
    INVALID_FORBIDDEN(4),
    INVALID_CHARACTER(5),
}
