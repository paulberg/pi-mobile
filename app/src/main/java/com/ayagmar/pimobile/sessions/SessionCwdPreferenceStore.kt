package com.ayagmar.pimobile.sessions

import android.content.Context
import android.content.SharedPreferences

interface SessionCwdPreferenceStore {
    fun getPreferredCwd(hostId: String): String?

    fun setPreferredCwd(
        hostId: String,
        cwd: String,
    )

    fun clearPreferredCwd(hostId: String)

    fun getRecentCwds(hostId: String): List<String>

    fun addRecentCwd(
        hostId: String,
        cwd: String,
    )
}

object NoOpSessionCwdPreferenceStore : SessionCwdPreferenceStore {
    override fun getPreferredCwd(hostId: String): String? = null

    override fun setPreferredCwd(
        hostId: String,
        cwd: String,
    ) = Unit

    override fun clearPreferredCwd(hostId: String) = Unit

    override fun getRecentCwds(hostId: String): List<String> = emptyList()

    override fun addRecentCwd(
        hostId: String,
        cwd: String,
    ) = Unit
}

class InMemorySessionCwdPreferenceStore : SessionCwdPreferenceStore {
    private val valuesByHostId = linkedMapOf<String, String>()
    private val recentByHostId = linkedMapOf<String, MutableList<String>>()

    override fun getPreferredCwd(hostId: String): String? = valuesByHostId[hostId]

    override fun setPreferredCwd(
        hostId: String,
        cwd: String,
    ) {
        valuesByHostId[hostId] = cwd
    }

    override fun clearPreferredCwd(hostId: String) {
        valuesByHostId.remove(hostId)
    }

    override fun getRecentCwds(hostId: String): List<String> {
        return recentByHostId[hostId]?.toList() ?: emptyList()
    }

    override fun addRecentCwd(
        hostId: String,
        cwd: String,
    ) {
        val list = recentByHostId.getOrPut(hostId) { mutableListOf() }
        list.remove(cwd)
        list.add(0, cwd)
        if (list.size > MAX_RECENT_CWDS) {
            list.removeAt(list.lastIndex)
        }
    }
}

class SharedPreferencesSessionCwdPreferenceStore(
    context: Context,
) : SessionCwdPreferenceStore {
    private val preferences: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun getPreferredCwd(hostId: String): String? {
        return preferences.getString(cwdKey(hostId), null)
    }

    override fun setPreferredCwd(
        hostId: String,
        cwd: String,
    ) {
        preferences.edit().putString(cwdKey(hostId), cwd).apply()
    }

    override fun clearPreferredCwd(hostId: String) {
        preferences.edit().remove(cwdKey(hostId)).apply()
    }

    override fun getRecentCwds(hostId: String): List<String> {
        val raw = preferences.getString(recentCwdsKey(hostId), null) ?: return emptyList()
        return raw.split('\n').filter { it.isNotBlank() }
    }

    override fun addRecentCwd(
        hostId: String,
        cwd: String,
    ) {
        val normalized = cwd.trim().takeIf { it.isNotBlank() } ?: return
        val current = getRecentCwds(hostId).toMutableList()
        current.remove(normalized)
        current.add(0, normalized)
        val capped = current.take(MAX_RECENT_CWDS)
        preferences.edit().putString(recentCwdsKey(hostId), capped.joinToString("\n")).apply()
    }

    private fun cwdKey(hostId: String): String = "cwd_$hostId"

    private fun recentCwdsKey(hostId: String): String = "recent_cwds_$hostId"

    companion object {
        private const val PREFS_NAME = "pi_mobile_session_cwd_preferences"
    }
}

private const val MAX_RECENT_CWDS = 10
