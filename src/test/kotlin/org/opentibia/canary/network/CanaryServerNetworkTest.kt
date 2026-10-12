package org.opentibia.canary.network

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import org.opentibia.canary.account.InMemoryAccountRepository
import java.net.Socket

class CanaryServerNetworkTest {

    private var server: CanaryServer? = null

    @AfterEach
    fun tearDown() {
        server?.stop()
    }

    @Test
    fun `starts server binding both login port 7171 and game port 7172`() {
        val s = CanaryServer(
            loginPort = 7181,
            gamePort = 7182,
            accountRepository = InMemoryAccountRepository()
        )
        server = s
        s.start()

        // Verify TCP connectivity to login port
        Socket("127.0.0.1", 7181).use { loginSocket ->
            assertThat(loginSocket.isConnected).isTrue()
        }

        // Verify TCP connectivity to game port
        Socket("127.0.0.1", 7182).use { gameSocket ->
            assertThat(gameSocket.isConnected).isTrue()
        }
    }
}
