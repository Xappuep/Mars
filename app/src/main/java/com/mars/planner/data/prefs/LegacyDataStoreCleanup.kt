package com.mars.planner.data.prefs

import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey

/**
 * Физическое удаление plaintext-ключей Mars 1.x из DataStore.
 * Вызывается при обновлении приложения до Rubezh-синхронизации.
 */
object LegacyDataStoreCleanup {
    val syncHost = stringPreferencesKey("sync_host")
    val syncPort = intPreferencesKey("sync_port")
    val syncKey = stringPreferencesKey("sync_key")
    val legacySecretsPurged = booleanPreferencesKey("legacy_sync_secrets_purged_v2")

    /**
     * @return true, если plaintext `sync_key` реально присутствовал и был удалён.
     */
    fun purge(prefs: MutablePreferences): Boolean {
        var removedSyncKey = false
        if (prefs.contains(syncKey)) {
            prefs.remove(syncKey)
            removedSyncKey = true
        }
        if (prefs.contains(syncHost)) prefs.remove(syncHost)
        if (prefs.contains(syncPort)) prefs.remove(syncPort)
        prefs[legacySecretsPurged] = true
        return removedSyncKey
    }
}
