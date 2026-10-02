package com.pinkiptv.app.vpn

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class VpnPreparationPhase {
    NOT_PREPARED,
    LOCAL_IDENTITY_READY,
    AWAITING_SYSTEM_PERMISSION,
    READY_FOR_TUNNEL_STAGE,
    PERMISSION_DENIED,
    ERROR,
}

data class VpnPreparationState(
    val phase: VpnPreparationPhase = VpnPreparationPhase.NOT_PREPARED,
    val identityAvailable: Boolean = false,
)

enum class VpnPrepareAction {
    None,
    LaunchSystemPermission,
}

class VpnPreparationController(
    private val identityStore: VpnIdentityStore,
    private val permissionGateway: VpnPermissionGateway,
    private val scope: CoroutineScope,
) {
    private val mutableState = MutableStateFlow(VpnPreparationState())
    val state: StateFlow<VpnPreparationState> = mutableState.asStateFlow()

    init {
        scope.launch { refreshLocalState() }
    }

    fun prepare(): VpnPrepareAction {
        if (mutableState.value.phase == VpnPreparationPhase.AWAITING_SYSTEM_PERMISSION) {
            return VpnPrepareAction.None
        }

        return when (permissionGateway.prepare()) {
            VpnPermissionCheck.SystemPermissionRequired -> {
                mutableState.value = mutableState.value.copy(
                    phase = VpnPreparationPhase.AWAITING_SYSTEM_PERMISSION,
                )
                VpnPrepareAction.LaunchSystemPermission
            }
            VpnPermissionCheck.AlreadyAuthorized -> {
                scope.launch { ensureIdentityAndReady() }
                VpnPrepareAction.None
            }
            VpnPermissionCheck.Failure -> {
                mutableState.value = mutableState.value.copy(
                    phase = VpnPreparationPhase.ERROR,
                )
                VpnPrepareAction.None
            }
        }
    }

    fun onPermissionResult(granted: Boolean) {
        if (!granted) {
            mutableState.value = mutableState.value.copy(
                phase = VpnPreparationPhase.PERMISSION_DENIED,
            )
            return
        }
        scope.launch { ensureIdentityAndReady() }
    }

    fun onPermissionLaunchFailed() {
        mutableState.value = mutableState.value.copy(
            phase = VpnPreparationPhase.ERROR,
        )
    }

    fun onIptvLogout() {
        scope.launch { refreshLocalState() }
    }

    private suspend fun refreshLocalState() {
        mutableState.value = when (val result = identityStore.loadIdentity()) {
            VpnIdentityResult.Absent -> VpnPreparationState(
                phase = VpnPreparationPhase.NOT_PREPARED,
                identityAvailable = false,
            )
            is VpnIdentityResult.Available -> VpnPreparationState(
                phase = VpnPreparationPhase.LOCAL_IDENTITY_READY,
                identityAvailable = result.identity.identityAvailable,
            )
            is VpnIdentityResult.Failure -> VpnPreparationState(
                phase = VpnPreparationPhase.ERROR,
                identityAvailable = false,
            )
        }
    }

    private suspend fun ensureIdentityAndReady() {
        mutableState.value = when (val result = identityStore.ensureIdentity()) {
            VpnIdentityResult.Absent -> VpnPreparationState(
                phase = VpnPreparationPhase.ERROR,
                identityAvailable = false,
            )
            is VpnIdentityResult.Available -> VpnPreparationState(
                phase = VpnPreparationPhase.READY_FOR_TUNNEL_STAGE,
                identityAvailable = result.identity.identityAvailable,
            )
            is VpnIdentityResult.Failure -> VpnPreparationState(
                phase = VpnPreparationPhase.ERROR,
                identityAvailable = false,
            )
        }
    }
}
