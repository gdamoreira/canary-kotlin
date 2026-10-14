package org.opentibia.canary.systems.hireling

/**
 * Engine-side hireling registry mirroring Canary's `Game` hireling maps.
 *
 * Source of truth: ../.projects/canary
 *  - src/game/game.cpp (Game::m_hirelingSkills / Game::m_hirelingOutfits)
 *  - src/game/game.hpp (Game::getHirelingSkills / Game::getHirelingOutfits)
 */
object HirelingRegistry {

    private val hirelingSkills: Map<Int, String> = mapOf(
        1001 to "banker",
        1002 to "cooker",
        1003 to "steward",
        1004 to "trader"
    )

    private val hirelingOutfits: Map<Int, String> = mapOf(
        2001 to "banker",
        2002 to "cooker",
        2003 to "steward",
        2004 to "trader",
        2005 to "servant",
        2006 to "hydra",
        2007 to "ferumbras",
        2008 to "bonelord",
        2009 to "dragon"
    )

    /** Mirrors `Game::getHirelingSkills()` (`m_hirelingSkills`). */
    fun getHirelingSkills(): Map<Int, String> = hirelingSkills

    /** Mirrors `Game::getHirelingOutfits()` (`m_hirelingOutfits`). */
    fun getHirelingOutfits(): Map<Int, String> = hirelingOutfits
}
