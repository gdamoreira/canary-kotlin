package org.opentibia.canary.io.iologindata

import org.opentibia.canary.creatures.Player
import org.opentibia.canary.database.JdbcPlayerRepository
import org.opentibia.canary.persistence.PlayerStorage

/**
 * Root composition of the `IOLoginData::savePlayer` path
 * (src/io/iologindata.cpp:195-211) over one shared [PlayerSaveTransaction]
 * (`DBTransaction::executeWithinTransaction`, src/database/database.hpp:233-256),
 * exposed as the [PlayerStorage] facade `SaveManager::doSavePlayer`
 * (src/game/scheduling/save_manager.cpp:150-171) drives.
 *
 * Canary owns one `g_database()` handle, so every query of one save joins the
 * same transaction; [transaction] is the port's shared handle and [loginData]
 * is constructed over it, so [save] commits or rolls back the whole
 * `savePlayerGuard` as a unit. A failed guard throws so `SaveManager` reports
 * the save as failed exactly like the C++ `false` return. [loadByName] is the
 * row-level `Player::load` read of the login repository.
 */
class PlayerSavePath(
    /** `DBTransaction::executeWithinTransaction` scope shared by the guard. */
    val transaction: PlayerSaveTransaction,
    /** `IOLoginData` whose repositories are all constructed over [transaction]. */
    val loginData: IOLoginData,
    private val players: JdbcPlayerRepository,
) : PlayerStorage {

    override fun save(player: Player) {
        if (!loginData.savePlayer(player)) {
            throw IllegalStateException("Failed to save player ${player.name}")
        }
    }

    override fun loadByName(name: String): Player? = players.loadByName(name)
}
