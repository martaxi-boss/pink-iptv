package com.pinkiptv.app

import com.pinkiptv.app.vpn.VpnIdentityError
import com.pinkiptv.app.vpn.VpnIdentityResult
import com.pinkiptv.app.vpn.VpnIdentityStore
import com.pinkiptv.app.vpn.VpnPermissionCheck
import com.pinkiptv.app.vpn.VpnPermissionGateway
import com.pinkiptv.app.vpn.VpnPreparationController
import com.pinkiptv.app.vpn.VpnPreparationPhase
import com.pinkiptv.app.vpn.VpnPrepareAction
import com.pinkiptv.app.vpn.VpnPublicIdentity
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VpnPreparationControllerTest {
    @Test
    fun permissionRequiredWaitsAndDenialDoesNotCreateIdentity() = runTest {
        val store = FakeIdentityStore()
        val controller = VpnPreparationController(
            identityStore = store,
            permissionGateway = FakePermissionGateway(VpnPermissionCheck.SystemPermissionRequired),
            scope = this,
        )
        advanceUntilIdle()

        assertEquals(VpnPrepareAction.LaunchSystemPermission, controller.prepare())
        assertEquals(VpnPreparationPhase.AWAITING_SYSTEM_PERMISSION, controller.state.value.phase)
        assertEquals(0, store.ensureCount)

        controller.onPermissionResult(false)
        advanceUntilIdle()

        assertEquals(VpnPreparationPhase.PERMISSION_DENIED, controller.state.value.phase)
        assertEquals(0, store.ensureCount)
        assertFalse(controller.state.value.identityAvailable)
    }

    @Test
    fun alreadyAuthorizedCreatesOnceThenReusesExistingIdentity() = runTest {
        val store = FakeIdentityStore()
        val controller = VpnPreparationController(
            identityStore = store,
            permissionGateway = FakePermissionGateway(VpnPermissionCheck.AlreadyAuthorized),
            scope = this,
        )
        advanceUntilIdle()

        assertEquals(VpnPrepareAction.None, controller.prepare())
        advanceUntilIdle()

        assertEquals(VpnPreparationPhase.READY_FOR_TUNNEL_STAGE, controller.state.value.phase)
        assertTrue(controller.state.value.identityAvailable)
        assertEquals(1, store.ensureCount)

        controller.prepare()
        advanceUntilIdle()

        assertEquals(VpnPreparationPhase.READY_FOR_TUNNEL_STAGE, controller.state.value.phase)
        assertEquals(2, store.ensureCount)
        assertEquals(1, store.creationCount)
    }

    @Test
    fun resultOkEnsuresIdentityAndLogoutPreservesIt() = runTest {
        val store = FakeIdentityStore()
        val controller = VpnPreparationController(
            identityStore = store,
            permissionGateway = FakePermissionGateway(VpnPermissionCheck.SystemPermissionRequired),
            scope = this,
        )
        advanceUntilIdle()

        controller.prepare()
        controller.onPermissionResult(true)
        advanceUntilIdle()

        val identityBeforeLogout = store.identity
        assertEquals(VpnPreparationPhase.READY_FOR_TUNNEL_STAGE, controller.state.value.phase)

        controller.onIptvLogout()
        advanceUntilIdle()

        assertEquals(identityBeforeLogout, store.identity)
        assertEquals(VpnPreparationPhase.LOCAL_IDENTITY_READY, controller.state.value.phase)
        assertTrue(controller.state.value.identityAvailable)
    }

    @Test
    fun corruptionAndGatewayFailureBecomeErrorWithoutSilentRotation() = runTest {
        val store = FakeIdentityStore(
            loadResult = VpnIdentityResult.Failure(VpnIdentityError.Decrypt),
        )
        val controller = VpnPreparationController(
            identityStore = store,
            permissionGateway = FakePermissionGateway(VpnPermissionCheck.AlreadyAuthorized),
            scope = this,
        )
        advanceUntilIdle()

        assertEquals(VpnPreparationPhase.ERROR, controller.state.value.phase)
        controller.prepare()
        advanceUntilIdle()

        assertEquals(VpnPreparationPhase.ERROR, controller.state.value.phase)
        assertEquals(1, store.ensureCount)
        assertEquals(0, store.creationCount)

        val gatewayFailure = VpnPreparationController(
            identityStore = FakeIdentityStore(),
            permissionGateway = FakePermissionGateway(VpnPermissionCheck.Failure),
            scope = this,
        )
        advanceUntilIdle()
        gatewayFailure.prepare()
        assertEquals(VpnPreparationPhase.ERROR, gatewayFailure.state.value.phase)
    }

    private class FakePermissionGateway(
        private val result: VpnPermissionCheck,
    ) : VpnPermissionGateway {
        override fun prepare(): VpnPermissionCheck = result
    }

    private class FakeIdentityStore(
        private var loadResult: VpnIdentityResult = VpnIdentityResult.Absent,
    ) : VpnIdentityStore {
        var ensureCount = 0
        var creationCount = 0
        var identity: VpnPublicIdentity? = null

        override suspend fun loadIdentity(): VpnIdentityResult {
            identity?.let { return VpnIdentityResult.Available(it) }
            return loadResult
        }

        override suspend fun ensureIdentity(): VpnIdentityResult {
            ensureCount += 1
            if (loadResult is VpnIdentityResult.Failure) return loadResult
            val current = identity
            if (current != null) return VpnIdentityResult.Available(current)

            creationCount += 1
            val created = VpnPublicIdentity(
                identityAvailable = true,
                publicKey = "synthetic-public-key",
            )
            identity = created
            loadResult = VpnIdentityResult.Available(created)
            return VpnIdentityResult.Available(created)
        }
    }
}
