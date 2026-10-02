package com.pinkiptv.app.vpn

import android.content.Context
import com.wireguard.android.backend.Backend
import com.wireguard.android.backend.GoBackend
import com.wireguard.android.backend.Tunnel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class PinkTunnelState {
    DOWN,
    UP,
}

sealed interface PinkBackendResult<out T> {
    data class Success<T>(val value: T) : PinkBackendResult<T>
    data object Failure : PinkBackendResult<Nothing>
}

class PinkWireGuardTunnel : Tunnel {
    private val mutableState = MutableStateFlow(PinkTunnelState.DOWN)
    val state: StateFlow<PinkTunnelState> = mutableState.asStateFlow()

    override fun getName(): String = TUNNEL_NAME

    override fun onStateChange(newState: Tunnel.State) {
        when (newState) {
            Tunnel.State.DOWN -> mutableState.value = PinkTunnelState.DOWN
            Tunnel.State.UP -> mutableState.value = PinkTunnelState.UP
            Tunnel.State.TOGGLE -> Unit
        }
    }

    companion object {
        const val TUNNEL_NAME = "pink"
    }
}

class PinkWireGuardBackend(
    private val backend: Backend,
    val tunnel: PinkWireGuardTunnel = PinkWireGuardTunnel(),
) {
    val state: StateFlow<PinkTunnelState> = tunnel.state

    fun backendVersion(): PinkBackendResult<String> =
        try {
            val version = backend.version
            if (version.isBlank()) PinkBackendResult.Failure
            else PinkBackendResult.Success(version)
        } catch (_: Exception) {
            PinkBackendResult.Failure
        }

    fun currentState(): PinkBackendResult<PinkTunnelState> =
        try {
            when (backend.getState(tunnel)) {
                Tunnel.State.DOWN -> PinkBackendResult.Success(PinkTunnelState.DOWN)
                Tunnel.State.UP -> PinkBackendResult.Success(PinkTunnelState.UP)
                Tunnel.State.TOGGLE -> PinkBackendResult.Success(tunnel.state.value)
            }
        } catch (_: Exception) {
            PinkBackendResult.Failure
        }
}

fun createPinkWireGuardBackend(context: Context): PinkWireGuardBackend =
    PinkWireGuardBackend(GoBackend(context.applicationContext))
