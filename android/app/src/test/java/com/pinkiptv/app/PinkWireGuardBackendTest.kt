package com.pinkiptv.app

import com.pinkiptv.app.vpn.PinkBackendResult
import com.pinkiptv.app.vpn.PinkTunnelState
import com.pinkiptv.app.vpn.PinkWireGuardBackend
import com.pinkiptv.app.vpn.PinkWireGuardTunnel
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.Statistics
import com.wireguard.android.backend.Tunnel
import com.wireguard.config.Config
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PinkWireGuardBackendTest {
    @Test
    fun backendVersionAndFreshStateAreReadOnlyQueries() {
        val backend = FakeBackend()
        val adapter = PinkWireGuardBackend(backend)

        assertEquals(
            PinkBackendResult.Success("synthetic-wg"),
            adapter.backendVersion(),
        )
        assertEquals(
            PinkBackendResult.Success(PinkTunnelState.DOWN),
            adapter.currentState(),
        )
        assertEquals(0, backend.setStateCalls)
        assertEquals("pink", adapter.tunnel.name)
    }

    @Test
    fun tunnelCallbackMapsUpDownAndIgnoresToggleAsPersistedState() {
        val tunnel = PinkWireGuardTunnel()
        assertEquals(PinkTunnelState.DOWN, tunnel.state.value)

        tunnel.onStateChange(Tunnel.State.UP)
        assertEquals(PinkTunnelState.UP, tunnel.state.value)

        tunnel.onStateChange(Tunnel.State.TOGGLE)
        assertEquals(PinkTunnelState.UP, tunnel.state.value)

        tunnel.onStateChange(Tunnel.State.DOWN)
        assertEquals(PinkTunnelState.DOWN, tunnel.state.value)
    }

    @Test
    fun backendFailuresDoNotExposeConfigOrInvokeSetState() {
        val backend = FakeBackend(throwQueries = true)
        val adapter = PinkWireGuardBackend(backend)

        assertEquals(PinkBackendResult.Failure, adapter.backendVersion())
        assertEquals(PinkBackendResult.Failure, adapter.currentState())
        assertEquals(0, backend.setStateCalls)

        val stateFields = com.pinkiptv.app.vpn.VpnPreparationState::class.java.declaredFields
            .map { it.name.lowercase() }
        assertTrue(
            stateFields.none {
                it.contains("private") ||
                    it.contains("psk") ||
                    it.contains("password") ||
                    it.contains("username") ||
                    it.contains("config") ||
                    it.contains("provider")
            },
        )
    }

    private class FakeBackend(
        private val throwQueries: Boolean = false,
    ) : Backend {
        var setStateCalls = 0

        override fun getRunningTunnelNames(): MutableSet<String> = mutableSetOf()

        override fun getState(tunnel: Tunnel): Tunnel.State {
            if (throwQueries) error("synthetic backend failure")
            return Tunnel.State.DOWN
        }

        override fun getStatistics(tunnel: Tunnel): Statistics = Statistics()

        override fun getVersion(): String {
            if (throwQueries) error("synthetic backend failure")
            return "synthetic-wg"
        }

        override fun isAlwaysOn(): Boolean = false

        override fun isLockdownEnabled(): Boolean = false

        override fun setState(
            tunnel: Tunnel,
            state: Tunnel.State,
            config: Config?,
        ): Tunnel.State {
            setStateCalls += 1
            return state
        }
    }
}
