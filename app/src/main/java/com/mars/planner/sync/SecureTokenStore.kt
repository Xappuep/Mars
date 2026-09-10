package com.mars.planner.sync

import android.content.Context
import android.content.SharedPreferences
import android.util.Log
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import java.util.UUID

/**
 * Хранилище секретов сопряжения с ПК «Рубеж».
 *
 * `device_token` шифруется ключом Android Keystore (AES-256-GCM) и никогда
 * не попадает ни в DataStore-настройки, ни в резервные копии, ни в логи.
 */
interface SecureTokenStore {

    /** true — значения сохраняются между запусками приложения. */
    val isPersistent: Boolean

    /**
     * Идентификатор установки. Существует до сопряжения и используется как
     * `device_id` в локальном журнале операций, пока ПК не выдал постоянный.
     */
    fun installId(): String

    /** `device_id`, выданный ПК при сопряжении, либо null. */
    fun deviceId(): String?

    /** Постоянный `device_token` для заголовка Authorization, либо null. */
    fun deviceToken(): String?

    fun savePairing(deviceId: String, deviceToken: String)

    fun clearPairing()

    fun isPaired(): Boolean = !deviceId().isNullOrBlank() && !deviceToken().isNullOrBlank()

    /** `device_id` сопряжения, либо идентификатор установки. */
    fun effectiveDeviceId(): String = deviceId()?.takeIf { it.isNotBlank() } ?: installId()
}

/** Реализация в памяти — для unit-тестов и как аварийный резерв. */
class MemorySecureTokenStore(
    installId: String = UUID.randomUUID().toString()
) : SecureTokenStore {

    private var installIdValue: String = installId
    private var deviceIdValue: String? = null
    private var deviceTokenValue: String? = null

    override val isPersistent: Boolean = false

    override fun installId(): String = installIdValue

    override fun deviceId(): String? = deviceIdValue

    override fun deviceToken(): String? = deviceTokenValue

    override fun savePairing(deviceId: String, deviceToken: String) {
        require(deviceId.isNotBlank()) { "device_id пустой" }
        require(deviceToken.isNotBlank()) { "device_token пустой" }
        deviceIdValue = deviceId
        deviceTokenValue = deviceToken
    }

    override fun clearPairing() {
        deviceIdValue = null
        deviceTokenValue = null
    }
}

/**
 * Реализация на `EncryptedSharedPreferences`: значения шифруются мастер-ключом
 * из Android Keystore, файл настроек исключён из авто-бэкапа манифестом.
 */
class KeystoreSecureTokenStore internal constructor(
    private val prefs: SharedPreferences
) : SecureTokenStore {

    override val isPersistent: Boolean = true

    override fun installId(): String {
        prefs.getString(KEY_INSTALL_ID, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val generated = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_INSTALL_ID, generated).apply()
        return generated
    }

    override fun deviceId(): String? =
        prefs.getString(KEY_DEVICE_ID, null)?.takeIf { it.isNotBlank() }

    override fun deviceToken(): String? =
        prefs.getString(KEY_DEVICE_TOKEN, null)?.takeIf { it.isNotBlank() }

    override fun savePairing(deviceId: String, deviceToken: String) {
        require(deviceId.isNotBlank()) { "device_id пустой" }
        require(deviceToken.isNotBlank()) { "device_token пустой" }
        prefs.edit()
            .putString(KEY_DEVICE_ID, deviceId)
            .putString(KEY_DEVICE_TOKEN, deviceToken)
            .apply()
    }

    override fun clearPairing() {
        prefs.edit()
            .remove(KEY_DEVICE_ID)
            .remove(KEY_DEVICE_TOKEN)
            .apply()
    }

    internal companion object {
        const val FILE_NAME = "mars_sync_secure"
        const val KEY_INSTALL_ID = "install_id"
        const val KEY_DEVICE_ID = "device_id"
        const val KEY_DEVICE_TOKEN = "device_token"
    }
}

private const val TAG = "MarsSecureStore"

/**
 * Создаёт постоянное хранилище. Если Keystore недоступен или файл повреждён,
 * файл пересоздаётся; при повторной неудаче возвращается хранилище в памяти —
 * приложение продолжает работать, потребуется повторное сопряжение.
 */
fun createSecureTokenStore(context: Context): SecureTokenStore {
    val app = context.applicationContext
    openEncryptedPrefs(app)?.let { return KeystoreSecureTokenStore(it) }
    // Повреждённый ключ/файл: единственный безопасный вариант — начать заново.
    resetEncryptedPrefs(app)
    openEncryptedPrefs(app)?.let { return KeystoreSecureTokenStore(it) }
    Log.w(TAG, "Keystore недоступен: секреты сопряжения не сохранятся")
    return MemorySecureTokenStore()
}

private fun openEncryptedPrefs(app: Context): SharedPreferences? = try {
    val masterKey = MasterKey.Builder(app)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    EncryptedSharedPreferences.create(
        app,
        KeystoreSecureTokenStore.FILE_NAME,
        masterKey,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
} catch (e: Exception) {
    Log.w(TAG, "Не удалось открыть защищённое хранилище: ${e.javaClass.simpleName}")
    null
}

private fun resetEncryptedPrefs(app: Context) {
    runCatching {
        app.getSharedPreferences(KeystoreSecureTokenStore.FILE_NAME, Context.MODE_PRIVATE)
            .edit().clear().commit()
    }
    runCatching {
        val file = java.io.File(
            app.applicationInfo.dataDir,
            "shared_prefs/${KeystoreSecureTokenStore.FILE_NAME}.xml"
        )
        if (file.exists()) file.delete()
    }
}
