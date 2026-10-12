package org.opentibia.canary.combat

import java.util.Random

/**
 * Faithful port of Canary `uniform_random` (src/utils/tools.cpp:455-464).
 *
 * Inclusive on both ends, swaps inverted bounds, and short-circuits equal bounds.
 * The RNG is injected instead of Canary's global `std::mt19937` so callers can
 * seed deterministic tests (AGENTS.md 1.1: no global singletons).
 */
fun uniformRandom(minNumber: Int, maxNumber: Int, random: Random): Int {
    if (minNumber == maxNumber) {
        return minNumber
    }

    var min = minNumber
    var max = maxNumber
    if (min > max) {
        val swap = min
        min = max
        max = swap
    }
    return min + random.nextInt(max - min + 1)
}

/**
 * Faithful port of Canary `normal_random` (src/utils/tools.cpp:466-475):
 * `std::normal_distribution<float>(0.5f, 0.25f)` rejected outside `[0, 1]`,
 * then `min + std::lround(v * (max - min))`.
 */
fun normalRandom(minNumber: Int, maxNumber: Int, random: Random): Int {
    var v: Float
    do {
        v = 0.5f + random.nextGaussian().toFloat() * 0.25f
    } while (v < 0.0f || v > 1.0f)

    val a = minOf(minNumber, maxNumber)
    val b = maxOf(minNumber, maxNumber)
    return a + Math.round(v * (b - a))
}
