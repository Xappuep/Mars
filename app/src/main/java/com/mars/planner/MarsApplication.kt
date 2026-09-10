package com.mars.planner

import android.app.Application
import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.db.MarsDatabase
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.data.prefs.SettingsRepository
import com.mars.planner.reminder.ReminderChannels
import com.mars.planner.reminder.ReminderScheduler
import com.mars.planner.sync.PackageEngine
import com.mars.planner.sync.RubezhSyncClient
import com.mars.planner.sync.SecureTokenStore
import com.mars.planner.sync.SyncCoordinator
import com.mars.planner.sync.SyncEndpoint
import com.mars.planner.sync.TlsFingerprint
import com.mars.planner.sync.createSecureTokenStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class AppContainer(app: Application) {

    private val db = MarsDatabase.get(app)

    val settings = SettingsRepository(app)

    /** `device_token` под ключом Android Keystore; в DataStore не попадает. */
    val tokenStore: SecureTokenStore = createSecureTokenStore(app)

    val planner = PlannerRepository(db) { tokenStore.effectiveDeviceId() }

    val packageEngine = PackageEngine(
        repo = planner,
        deviceIdProvider = { tokenStore.effectiveDeviceId() }
    )

    @Volatile
    private var endpoint: SyncEndpoint? = null

    val syncClient = RubezhSyncClient(
        tokenStore = tokenStore,
        endpointProvider = { endpoint }
    )

    /** Фасад синхронизации для экранов: сопряжение, отправка, приём, расхождения. */
    val sync = SyncCoordinator(
        context = app,
        planner = planner,
        packages = packageEngine,
        client = syncClient,
        tokenStore = tokenStore,
        settingsRepo = settings,
        deviceNameProvider = { deviceDisplayName() },
        onEndpointChanged = { applySyncSettings(it) }
    )

    /** Адрес и отпечаток сертификата ПК берутся из сохранённых данных сопряжения. */
    fun applySyncSettings(settings: AppSettings) {
        endpoint = if (
            settings.syncPaired &&
            settings.pairedHost.isNotBlank() &&
            TlsFingerprint.isValidFingerprint(settings.certSha256)
        ) {
            SyncEndpoint(
                host = settings.pairedHost,
                port = settings.pairedPort,
                certSha256 = TlsFingerprint.normalizeHex(settings.certSha256)
            )
        } else {
            null
        }
    }

    val currentEndpoint: SyncEndpoint? get() = endpoint
}

/** Имя телефона, которое ПК показывает в списке сопряжённых устройств. */
fun deviceDisplayName(): String {
    val manufacturer = android.os.Build.MANUFACTURER.orEmpty().trim()
    val model = android.os.Build.MODEL.orEmpty().trim()
    val name = if (model.startsWith(manufacturer, ignoreCase = true)) {
        model
    } else {
        "$manufacturer $model".trim()
    }
    return name.ifBlank { "Телефон Марса" }
}

class MarsApplication : Application() {

    lateinit var container: AppContainer
        private set

    val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        ReminderChannels.ensure(this)
        ReminderScheduler.scheduleDigestWorkers(this)

        applicationScope.launch {
            // Физически удаляем plaintext sync_key Mars 1.x из DataStore при обновлении.
            container.settings.purgeLegacyPlaintextSecrets()
            val current = container.settings.settings.first()
            container.applySyncSettings(current)
            val deviceId = container.tokenStore.effectiveDeviceId()
            if (current.deviceId != deviceId) {
                container.settings.update { it.copy(deviceId = deviceId) }
            }
            container.settings.settings.collect { container.applySyncSettings(it) }
        }
    }
}
