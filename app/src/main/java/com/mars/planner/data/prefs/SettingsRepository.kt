package com.mars.planner.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mars.planner.domain.model.MotivatorMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore("mars_settings")

/**
 * Настройки приложения. Секреты сопряжения (`device_token`) здесь **не хранятся** —
 * только в `SecureTokenStore` под ключом Android Keystore.
 */
data class AppSettings(
    val motivatorMode: MotivatorMode = MotivatorMode.ADAPTIVE,
    val morningReminderEnabled: Boolean = true,
    val morningReminderHour: Int = 9,
    val morningReminderMinute: Int = 0,
    val eveningReminderEnabled: Boolean = true,
    val eveningReminderHour: Int = 21,
    val eveningReminderMinute: Int = 0,
    val defaultSnoozeMinutes: Int = 30,
    val lastSyncAt: Long = 0L,
    val userName: String = "",
    val demoLoaded: Boolean = false,
    /** Декоративные анимации UI: при true — мгновенная смена состояний. */
    val reduceAnimations: Boolean = false,

    /** Идентификатор темы «Рубежа»: orbit, nebula, white-station, ash-amber, polar-night, light-concrete, shelter-terminal. */
    val themeId: String = "orbit",
    /** Интенсивность декоративных эффектов, 0f..1f. */
    val effectIntensity: Float = 1f,

    /** `device_id`, выданный ПК при сопряжении (не секрет). */
    val deviceId: String = "",
    val pairedDeviceName: String = "",
    val pairedHost: String = "",
    val pairedPort: Int = 8765,
    /** SHA-256 DER сертификата ПК из QR — для пиннинга TLS. */
    val certSha256: String = "",
    val syncPaired: Boolean = false,
    val lastPushAt: Long = 0L,
    val lastPullAt: Long = 0L,
    /** Код последней ошибки синхронизации; пустая строка — ошибок нет. */
    val lastSyncError: String = "",
    /** Идентификатор снимка, применённого локально, но ещё не подтверждённого ACK на ПК. */
    val pendingSnapshotAckId: String = "",
    /**
     * После восстановления резервной копии телефона дельта запрещена,
     * пока снова не получен и не подтверждён полный снимок с ПК.
     */
    val requiresPcPrimarySnapshot: Boolean = false,
    /** Отчёт о миграции данных Mars 1.x ещё не показан пользователю. */
    val migrationReportPending: Boolean = false,

    /**
     * Локальный UI списка «Задачи»: пользователь уже менял раскрытие групп.
     * Пока false — применяется стартовая композиция (Без проекта + первая проектная).
     * Не входит в sync и в SettingsDto экспорта.
     */
    val tasksGroupsUserConfigured: Boolean = false,
    /**
     * Ключи раскрытых групп (UUID проекта или `__no_project__`), по одному на строку.
     * Имеет смысл при [tasksGroupsUserConfigured] == true.
     */
    val tasksGroupExpandedKeys: String = "",
    /** Локально: группа «Без проекта» закреплена сверху списка. */
    val tasksNoProjectPinnedTop: Boolean = false
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
        val lastSync = longPreferencesKey("last_sync_at")
        val userName = stringPreferencesKey("user_name")
        val demoLoaded = booleanPreferencesKey("demo_loaded")
        val reduceAnimations = booleanPreferencesKey("reduce_animations")

        val themeId = stringPreferencesKey("theme_id")
        val effectIntensity = floatPreferencesKey("effect_intensity")

        val deviceId = stringPreferencesKey("device_id")
        val pairedDeviceName = stringPreferencesKey("paired_device_name")
        val pairedHost = stringPreferencesKey("paired_host")
        val pairedPort = intPreferencesKey("paired_port")
        val certSha256 = stringPreferencesKey("cert_sha256")
        val syncPaired = booleanPreferencesKey("sync_paired")
        val lastPush = longPreferencesKey("last_push_at")
        val lastPull = longPreferencesKey("last_pull_at")
        val lastSyncError = stringPreferencesKey("last_sync_error")
        val pendingSnapshotAckId = stringPreferencesKey("pending_snapshot_ack_id")
        val requiresPcPrimarySnapshot = booleanPreferencesKey("requires_pc_primary_snapshot")
        val migrationReportPending = booleanPreferencesKey("migration_report_pending")
        val tasksGroupsUserConfigured = booleanPreferencesKey("tasks_groups_user_configured")
        val tasksGroupExpandedKeys = stringPreferencesKey("tasks_group_expanded_keys")
        val tasksNoProjectPinnedTop = booleanPreferencesKey("tasks_no_project_pinned_top")
    }

    val settings: Flow<AppSettings> = context.dataStore.data.map { it.toAppSettings() }

    suspend fun update(transform: (AppSettings) -> AppSettings) {
        context.dataStore.edit { prefs ->
            prefs.write(transform(prefs.toAppSettings()))
        }
    }

    /**
     * Однократная очистка plaintext-секрета Mars 1.x из DataStore при обновлении
     * до Rubezh-синхронизации. Идемпотентно: повторный вызов безопасен.
     */
    suspend fun purgeLegacyPlaintextSecrets(): Boolean {
        var removed = false
        context.dataStore.edit { prefs ->
            removed = LegacyDataStoreCleanup.purge(prefs)
        }
        return removed
    }

    /** Для диагностики: есть ли ещё plaintext `sync_key` в DataStore. */
    suspend fun hasLegacyPlaintextSyncKey(): Boolean =
        context.dataStore.data.map { prefs -> prefs.contains(LegacyDataStoreCleanup.syncKey) }.first()

    private fun Preferences.toAppSettings(): AppSettings = AppSettings(
        motivatorMode = MotivatorMode.fromKey(this[Keys.motivator] ?: MotivatorMode.ADAPTIVE.key),
        morningReminderEnabled = this[Keys.morningEnabled] ?: true,
        morningReminderHour = this[Keys.morningHour] ?: 9,
        morningReminderMinute = this[Keys.morningMinute] ?: 0,
        eveningReminderEnabled = this[Keys.eveningEnabled] ?: true,
        eveningReminderHour = this[Keys.eveningHour] ?: 21,
        eveningReminderMinute = this[Keys.eveningMinute] ?: 0,
        defaultSnoozeMinutes = this[Keys.snooze] ?: 30,
        lastSyncAt = this[Keys.lastSync] ?: 0L,
        userName = this[Keys.userName] ?: "",
        demoLoaded = this[Keys.demoLoaded] ?: false,
        reduceAnimations = this[Keys.reduceAnimations] ?: false,
        themeId = this[Keys.themeId] ?: "orbit",
        effectIntensity = (this[Keys.effectIntensity] ?: 1f).coerceIn(0f, 1f),
        deviceId = this[Keys.deviceId] ?: "",
        pairedDeviceName = this[Keys.pairedDeviceName] ?: "",
        pairedHost = this[Keys.pairedHost] ?: "",
        pairedPort = this[Keys.pairedPort] ?: 8765,
        certSha256 = this[Keys.certSha256] ?: "",
        syncPaired = this[Keys.syncPaired] ?: false,
        lastPushAt = this[Keys.lastPush] ?: 0L,
        lastPullAt = this[Keys.lastPull] ?: 0L,
        lastSyncError = this[Keys.lastSyncError] ?: "",
        pendingSnapshotAckId = this[Keys.pendingSnapshotAckId] ?: "",
        requiresPcPrimarySnapshot = this[Keys.requiresPcPrimarySnapshot] ?: false,
        migrationReportPending = this[Keys.migrationReportPending] ?: false,
        tasksGroupsUserConfigured = this[Keys.tasksGroupsUserConfigured] ?: false,
        tasksGroupExpandedKeys = this[Keys.tasksGroupExpandedKeys] ?: "",
        tasksNoProjectPinnedTop = this[Keys.tasksNoProjectPinnedTop] ?: false
    )

    private fun MutablePreferences.write(next: AppSettings) {
        this[Keys.motivator] = next.motivatorMode.key
        this[Keys.morningEnabled] = next.morningReminderEnabled
        this[Keys.morningHour] = next.morningReminderHour
        this[Keys.morningMinute] = next.morningReminderMinute
        this[Keys.eveningEnabled] = next.eveningReminderEnabled
        this[Keys.eveningHour] = next.eveningReminderHour
        this[Keys.eveningMinute] = next.eveningReminderMinute
        this[Keys.snooze] = next.defaultSnoozeMinutes
        this[Keys.lastSync] = next.lastSyncAt
        this[Keys.userName] = next.userName
        this[Keys.demoLoaded] = next.demoLoaded
        this[Keys.reduceAnimations] = next.reduceAnimations
        this[Keys.themeId] = next.themeId
        this[Keys.effectIntensity] = next.effectIntensity.coerceIn(0f, 1f)
        this[Keys.deviceId] = next.deviceId
        this[Keys.pairedDeviceName] = next.pairedDeviceName
        this[Keys.pairedHost] = next.pairedHost
        this[Keys.pairedPort] = next.pairedPort
        this[Keys.certSha256] = next.certSha256
        this[Keys.syncPaired] = next.syncPaired
        this[Keys.lastPush] = next.lastPushAt
        this[Keys.lastPull] = next.lastPullAt
        this[Keys.lastSyncError] = next.lastSyncError
        this[Keys.pendingSnapshotAckId] = next.pendingSnapshotAckId
        this[Keys.requiresPcPrimarySnapshot] = next.requiresPcPrimarySnapshot
        this[Keys.migrationReportPending] = next.migrationReportPending
        this[Keys.tasksGroupsUserConfigured] = next.tasksGroupsUserConfigured
        this[Keys.tasksGroupExpandedKeys] = next.tasksGroupExpandedKeys
        this[Keys.tasksNoProjectPinnedTop] = next.tasksNoProjectPinnedTop
    }
}
