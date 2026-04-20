package com.ayagmar.pimobile.di

import android.content.Context
import androidx.core.content.edit
import com.ayagmar.pimobile.coresessions.FileSessionIndexCache
import com.ayagmar.pimobile.coresessions.SessionIndexRepository
import com.ayagmar.pimobile.hosts.ConnectionDiagnostics
import com.ayagmar.pimobile.hosts.HostProfileStore
import com.ayagmar.pimobile.hosts.HostTokenStore
import com.ayagmar.pimobile.hosts.KeystoreHostTokenStore
import com.ayagmar.pimobile.hosts.SharedPreferencesHostProfileStore
import com.ayagmar.pimobile.sessions.BridgeSessionIndexRemoteDataSource
import com.ayagmar.pimobile.sessions.RpcSessionController
import com.ayagmar.pimobile.sessions.SessionController
import com.ayagmar.pimobile.sessions.SessionCwdPreferenceStore
import com.ayagmar.pimobile.sessions.SharedPreferencesSessionCwdPreferenceStore
import java.util.UUID

class AppGraph(
    context: Context,
) {
    private val appContext = context.applicationContext

    val sessionController: SessionController by lazy {
        RpcSessionController(clientIdProvider = ::loadOrCreateClientId)
    }

    // Persist the bridge clientId across process restarts so the bridge can resume our
    // control locks after the reconnect grace period instead of treating the app as a new
    // client on every launch (which caused control_lock_denied errors on the prior clientId's
    // still-held locks).
    private fun loadOrCreateClientId(): String {
        val prefs = appContext.getSharedPreferences(CLIENT_ID_PREFS, Context.MODE_PRIVATE)
        prefs.getString(CLIENT_ID_KEY, null)?.let { return it }
        val fresh = UUID.randomUUID().toString()
        prefs.edit { putString(CLIENT_ID_KEY, fresh) }
        return fresh
    }

    val sessionCwdPreferenceStore: SessionCwdPreferenceStore by lazy {
        SharedPreferencesSessionCwdPreferenceStore(appContext)
    }

    val hostProfileStore: HostProfileStore by lazy {
        SharedPreferencesHostProfileStore(appContext)
    }

    val hostTokenStore: HostTokenStore by lazy {
        KeystoreHostTokenStore(appContext)
    }

    val sessionIndexRepository: SessionIndexRepository by lazy {
        SessionIndexRepository(
            remoteDataSource = BridgeSessionIndexRemoteDataSource(hostProfileStore, hostTokenStore),
            cache = FileSessionIndexCache(appContext.cacheDir.toPath().resolve("session-index-cache")),
        )
    }

    val connectionDiagnostics: ConnectionDiagnostics by lazy { ConnectionDiagnostics() }

    private companion object {
        const val CLIENT_ID_PREFS = "pi-mobile-client-id"
        const val CLIENT_ID_KEY = "bridge-client-id"
    }
}
