package com.mars.planner.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mars.planner.domain.model.MotivatorMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("mars_settings")

data class AppSettings(
    val motivatorMode: MotivatorMode = MotivatorMode.ADAPTIVE,
    val morningReminderEnabled: Boolean = true,
    val morningReminderHour: Int = 9,
    val morningReminderMinute: Int = 0,
    val eveningReminderEnabled: Boolean = true,
    val eveningReminderHour: Int = 21,
    val eveningReminderMinute: Int = 0,
    val defaultSnoozeMinutes: Int = 30,
    val syncHost: String = "",
    val syncPort: Int = 8765,
    val syncKey: String = "",
    val lastSyncAt: Long = 0L,
    val userName: String = "",
    val demoLoaded: Boolean = false
)

class SettingsRepository(private val context: Context) {
    private object Keys {
        val motivator = stringPreferencesKey("motivator_mode")
        val morningEnabled = booleanPreferencesKey("morning_enabled")
        val morningHour = intPreferencesKey("morning_hour")
        val morningMinute = intPreferencesKey("morning_minute")
        val eveningEnabled = booleanPreferencesKey("evening_enabled")
        val eveningHour = intPreferencesKey("evening_hour")
        val eveningMinute = intPreferencesKey("evening_minute")
        val snooze = intPreferencesKey("default_snooze")
        val syncHost = stringPreferencesKey("sync_host")
        val syncPort = intPreferencesKey("sync_port")
        val syncKey = stringPreferencesKey("sync_key")
        val lastSync = longPreferencesKey("last_sync_at")
        val userName = stringPreferencesKey("user_name")
        val demoLoaded = booleanPreferencesKey("demo_loaded")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { p ->
        AppSettings(
            motivatorMode = MotivatorMode.fromKey(p[Keys.motivator] ?: MotivatorMode.ADAPTIVE.key),
            morningReminderEnabled = p[Keys.morningEnabled] ?: true,
            morningReminderHour = p[Keys.morningHour] ?: 9,
            morningReminderMinute = p[Keys.morningMinute] ?: 0,
            eveningReminderEnabled = p[Keys.eveningEnabled] ?: true,
            eveningReminderHour = p[Keys.eveningHour] ?: 21,
            eveningReminderMinute = p[Keys.eveningMinute] ?: 0,
            defaultSnoozeMinutes = p[Keys.snooze] ?: 30,
            syncHost = p[Keys.syncHost] ?: "",
            syncPort = p[Keys.syncPort] ?: 8765,
            syncKey = p[Keys.syncKey] ?: "",
            lastSyncAt = p[Keys.lastSync] ?: 0L,
            userName = p[Keys.userName] ?: "",
            demoLoaded = p[Keys.demoLoaded] ?: false
        )
    }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            val current = AppSettings(
                motivatorMode = MotivatorMode.fromKey(prefs[Keys.motivator] ?: MotivatorMode.ADAPTIVE.key),
                morningReminderEnabled = prefs[Keys.morningEnabled] ?: true,
                morningReminderHour = prefs[Keys.morningHour] ?: 9,
                morningReminderMinute = prefs[Keys.morningMinute] ?: 0,
                eveningReminderEnabled = prefs[Keys.eveningEnabled] ?: true,
                eveningReminderHour = prefs[Keys.eveningHour] ?: 21,
                eveningReminderMinute = prefs[Keys.eveningMinute] ?: 0,
                defaultSnoozeMinutes = prefs[Keys.snooze] ?: 30,
                syncHost = prefs[Keys.syncHost] ?: "",
                syncPort = prefs[Keys.syncPort] ?: 8765,
                syncKey = prefs[Keys.syncKey] ?: "",
                lastSyncAt = prefs[Keys.lastSync] ?: 0L,
                userName = prefs[Keys.userName] ?: "",
                demoLoaded = prefs[Keys.demoLoaded] ?: false
            )
            val next = transform(current)
            prefs[Keys.motivator] = next.motivatorMode.key
            prefs[Keys.morningEnabled] = next.morningReminderEnabled
            prefs[Keys.morningHour] = next.morningReminderHour
            prefs[Keys.morningMinute] = next.morningReminderMinute
            prefs[Keys.eveningEnabled] = next.eveningReminderEnabled
            prefs[Keys.eveningHour] = next.eveningReminderHour
            prefs[Keys.eveningMinute] = next.eveningReminderMinute
            prefs[Keys.snooze] = next.defaultSnoozeMinutes
            prefs[Keys.syncHost] = next.syncHost
            prefs[Keys.syncPort] = next.syncPort
            prefs[Keys.syncKey] = next.syncKey
            prefs[Keys.lastSync] = next.lastSyncAt
            prefs[Keys.userName] = next.userName
            prefs[Keys.demoLoaded] = next.demoLoaded
        }
    }
}
