package com.pinkiptv.app.vpn

import android.content.Context
import android.content.Intent
import android.net.VpnService

enum class VpnPermissionCheck {
    AlreadyAuthorized,
    SystemPermissionRequired,
    Failure,
}

interface VpnPermissionGateway {
    fun prepare(): VpnPermissionCheck
}

class AndroidVpnPermissionGateway(
    context: Context,
) : VpnPermissionGateway {
    private val appContext = context.applicationContext
    private var pendingIntent: Intent? = null

    override fun prepare(): VpnPermissionCheck {
        return try {
            val intent = VpnService.prepare(appContext)
            pendingIntent = intent
            if (intent == null) {
                VpnPermissionCheck.AlreadyAuthorized
            } else {
                VpnPermissionCheck.SystemPermissionRequired
            }
        } catch (_: Exception) {
            pendingIntent = null
            VpnPermissionCheck.Failure
        }
    }

    fun takePendingIntent(): Intent? =
        pendingIntent.also { pendingIntent = null }
}
