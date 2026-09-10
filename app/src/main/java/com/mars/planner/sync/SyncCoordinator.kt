package com.mars.planner.sync

import android.content.Context
import com.mars.planner.data.PlannerRepository
import com.mars.planner.data.prefs.AppSettings
import com.mars.planner.data.prefs.SettingsRepository
import com.mars.planner.reminder.ReminderScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first

/** Результат шага синхронизации в виде, пригодном для показа на экране. */
data class SyncStepResult(
    val ok: Boolean,
    val message: String,
    val conflicts: Int = 0
)

/** Состояние незавершённой заявки на сопряжение. */
data class PairingProgress(
    val active: Boolean = false,
    val host: String = "",
    val port: Int = 0
)

/**
 * Связывает экран синхронизации с примитивами протокола «рубеж-синхронизация» v1:
 * сопряжение по QR, сборка и отправка пакетов, приём и применение входящих,
 * пересчёт напоминаний после изменений с ПК.
 */
class SyncCoordinator(
    private val context: Context,
    private val planner: PlannerRepository,
    private val packages: PackageEngine,
    private val client: RubezhSyncClient,
    private val tokenStore: SecureTokenStore,
    private val settingsRepo: SettingsRepository,
    private val deviceNameProvider: () -> String,
    private val onEndpointChanged: (AppSettings) -> Unit
) {
    private val pairingState = MutableStateFlow(PairingProgress())
    val pairing: StateFlow<PairingProgress> = pairingState.asStateFlow()

    /** Данные QR живут только в памяти: одноразовый токен не сохраняется. */
    @Volatile
    private var pendingQr: PairingQr? = null

    @Volatile
    private var pendingRequestId: String = ""

    /** Номер попытки отправки: после `package_rejected` нужен новый package_id. */
    @Volatile
    private var pushAttempt: Int = 0

    private suspend fun settings(): AppSettings = settingsRepo.settings.first()

    val isPaired: Boolean get() = tokenStore.isPaired()

    /** Проверка доступности ПК. Требует уже выполненного сопряжения. */
    suspend fun checkServer(): SyncStepResult {
        if (!tokenStore.isPaired()) {
            return SyncStepResult(false, SyncErrorCodes.messageRu(SyncErrorCodes.NOT_PAIRED))
        }
        return when (val result = client.health()) {
            is SyncCallResult.Ok -> SyncStepResult(true, "ПК на связи, протокол v${result.value.protocolVersion}")
            is SyncCallResult.Failure -> {
                rememberError(result.code)
                SyncStepResult(false, result.messageRu)
            }
        }
    }

    /** Разбирает QR-код «Рубежа» и отправляет заявку на сопряжение. */
    suspend fun startPairing(qrRaw: String): SyncStepResult {
        val parsed = PairingQr.parse(qrRaw)
        if (parsed is SyncCallResult.Failure) {
            rememberError(parsed.code)
            return SyncStepResult(false, parsed.messageRu)
        }
        val qr = (parsed as SyncCallResult.Ok).value
        val endpoint = qr.endpoint()
        return when (val response = client.pairRequest(endpoint, qr.token, deviceNameProvider())) {
            is SyncCallResult.Failure -> {
                rememberError(response.code)
                SyncStepResult(false, response.messageRu)
            }
            is SyncCallResult.Ok -> {
                pendingQr = qr
                pendingRequestId = response.value.requestId
                pairingState.value = PairingProgress(active = true, host = qr.host, port = qr.port)
                settingsRepo.update { it.copy(lastSyncError = "") }
                SyncStepResult(true, "Заявка отправлена. Подтвердите её в «Рубеже» на ПК.")
            }
        }
    }

    /** Опрашивает ПК: подтвердил ли пользователь заявку. */
    suspend fun pollPairStatus(): SyncStepResult {
        val qr = pendingQr
        if (qr == null || pendingRequestId.isBlank()) {
            return SyncStepResult(false, "Нет активной заявки — отсканируйте QR-код заново")
        }
        return when (val response = client.pairStatus(qr.endpoint(), pendingRequestId)) {
            is SyncCallResult.Failure -> {
                if (response.code == SyncErrorCodes.PAIRING_DENIED ||
                    response.code == SyncErrorCodes.PAIRING_TOKEN_EXPIRED ||
                    response.code == SyncErrorCodes.PAIRING_TOKEN_USED ||
                    response.code == SyncErrorCodes.BAD_PAIRING_TOKEN
                ) {
                    clearPending()
                }
                rememberError(response.code)
                SyncStepResult(false, response.messageRu)
            }
            is SyncCallResult.Ok -> {
                val info = response.value
                when {
                    info.isApproved -> {
                        tokenStore.savePairing(info.deviceId!!, info.deviceToken!!)
                        val next = settings().copy(
                            syncPaired = true,
                            pairedHost = qr.host,
                            pairedPort = qr.port,
                            certSha256 = qr.certSha256,
                            pairedDeviceName = "ПК ${qr.host}",
                            deviceId = info.deviceId,
                            lastSyncError = ""
                        )
                        settingsRepo.update { next }
                        onEndpointChanged(next)
                        clearPending()
                        SyncStepResult(true, "Сопряжение выполнено. Можно отправлять изменения.")
                    }
                    info.status == "denied" || info.status == "rejected" -> {
                        clearPending()
                        rememberError(SyncErrorCodes.PAIRING_DENIED)
                        SyncStepResult(false, SyncErrorCodes.messageRu(SyncErrorCodes.PAIRING_DENIED))
                    }
                    else -> SyncStepResult(true, "ПК ещё не подтвердил заявку")
                }
            }
        }
    }

    fun cancelPairing() {
        clearPending()
    }

    /** Разрыв сопряжения: токен удаляется, локальные данные остаются. */
    suspend fun unpair() {
        tokenStore.clearPairing()
        clearPending()
        val next = settings().copy(
            syncPaired = false,
            pairedHost = "",
            certSha256 = "",
            pairedDeviceName = "",
            lastSyncError = ""
        )
        settingsRepo.update { next }
        onEndpointChanged(next)
    }

    /** Отправляет пакет из неподтверждённых локальных операций. */
    suspend fun pushChanges(): SyncStepResult {
        if (!tokenStore.isPaired()) {
            return SyncStepResult(false, SyncErrorCodes.messageRu(SyncErrorCodes.NOT_PAIRED))
        }
        deltaBlockedMessage()?.let { return it }
        val pack = try {
            packages.buildOutgoingPackage(attempt = pushAttempt)
        } catch (e: SyncEngineException) {
            rememberError(e.code)
            return SyncStepResult(false, SyncErrorCodes.messageRu(e.code))
        } ?: return SyncStepResult(true, "Все изменения уже отправлены")

        return when (val response = client.pushPackage(pack)) {
            is SyncCallResult.Ok -> {
                packages.onPushAccepted(pack)
                pushAttempt = 0
                settingsRepo.update {
                    it.copy(lastPushAt = System.currentTimeMillis(), lastSyncError = "")
                }
                SyncStepResult(true, "Отправлено изменений: ${pack.operations.size}")
            }
            is SyncCallResult.Failure -> {
                // Отклонённый пакет нельзя повторять с тем же package_id.
                if (response.code == SyncErrorCodes.PACKAGE_REJECTED) pushAttempt += 1
                rememberError(response.code)
                SyncStepResult(false, response.messageRu)
            }
        }
    }

    /** Забирает пакеты с ПК и применяет их, спорные записи уходят в расхождения. */
    suspend fun pullChanges(): SyncStepResult {
        if (!tokenStore.isPaired()) {
            return SyncStepResult(false, SyncErrorCodes.messageRu(SyncErrorCodes.NOT_PAIRED))
        }
        deltaBlockedMessage()?.let { return it }
        val response = client.pullPackages()
        if (response is SyncCallResult.Failure) {
            rememberError(response.code)
            return SyncStepResult(false, response.messageRu)
        }
        val incoming = (response as SyncCallResult.Ok).value
        if (incoming.isEmpty()) {
            settingsRepo.update { it.copy(lastPullAt = System.currentTimeMillis(), lastSyncError = "") }
            return SyncStepResult(true, "На ПК нет новых изменений")
        }

        var added = 0
        var updated = 0
        var deleted = 0
        var conflicts = 0
        var rejected: String? = null

        for (pack in incoming) {
            when (val outcome = packages.applyIncomingPackage(pack)) {
                is ApplyOutcome.Applied -> {
                    added += outcome.stats.added
                    updated += outcome.stats.updated
                    deleted += outcome.stats.deleted
                    conflicts += outcome.stats.conflicts
                    client.ackPackage(pack.packageId)
                }
                ApplyOutcome.AlreadyApplied -> client.ackPackage(pack.packageId)
                is ApplyOutcome.Rejected -> rejected = outcome.code
            }
        }

        rescheduleReminders()
        settingsRepo.update {
            it.copy(
                lastPullAt = System.currentTimeMillis(),
                lastSyncError = when {
                    rejected != null -> rejected
                    conflicts > 0 -> SyncErrorCodes.CONFLICT_STATE
                    else -> ""
                }
            )
        }

        if (rejected != null && added + updated + deleted + conflicts == 0) {
            return SyncStepResult(false, SyncErrorCodes.messageRu(rejected))
        }
        val message = buildString {
            append("Получено с ПК: новых $added, обновлено $updated, удалено $deleted")
            if (conflicts > 0) append(". Расхождений: $conflicts")
        }
        return SyncStepResult(true, message, conflicts)
    }

    private suspend fun deltaBlockedMessage(): SyncStepResult? {
        if (!settings().requiresPcPrimarySnapshot) return null
        rememberError(SyncErrorCodes.SNAPSHOT_REQUIRED)
        return SyncStepResult(false, SyncErrorCodes.messageRu(SyncErrorCodes.SNAPSHOT_REQUIRED))
    }

    suspend fun resolveConflict(conflictId: Long, keepLocal: Boolean) {
        if (keepLocal) {
            planner.resolveConflictKeepLocal(conflictId)
        } else {
            planner.resolveConflictAcceptRemote(conflictId)
            rescheduleReminders()
        }
    }

    enum class SnapshotStage {
        INFO, DOWNLOAD, VALIDATE, BACKUP, APPLY, DONE
    }

    data class SnapshotPreview(
        val localProjects: Int,
        val localTasks: Int,
        val remoteProjects: Int,
        val remoteTasks: Int,
        val info: SnapshotInfo
    )

    /** Сведения о снимке для диалога подтверждения (база телефона ещё не меняется). */
    suspend fun preparePcPrimarySnapshot(): SyncCallResult<SnapshotPreview> {
        if (!tokenStore.isPaired()) {
            return SyncCallResult.Failure(SyncErrorCodes.NOT_PAIRED)
        }
        when (val health = client.health()) {
            is SyncCallResult.Failure -> return health
            is SyncCallResult.Ok -> {
                if (!health.value.supportsFullSnapshot) {
                    return SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_UNSUPPORTED)
                }
            }
        }
        return when (val info = client.snapshotInfo()) {
            is SyncCallResult.Failure -> {
                val code = if (info.code == SyncErrorCodes.NOT_FOUND) {
                    SyncErrorCodes.SNAPSHOT_UNSUPPORTED
                } else {
                    info.code
                }
                SyncCallResult.Failure(code, info.httpStatus, info.detail)
            }
            is SyncCallResult.Ok -> SyncCallResult.Ok(
                SnapshotPreview(
                    localProjects = planner.countUserProjects(),
                    localTasks = planner.countUserTasks(),
                    remoteProjects = info.value.projectCount,
                    remoteTasks = info.value.taskCount,
                    info = info.value
                )
            )
        }
    }

    /**
     * Полный снимок «ПК — основной»: загрузка → проверка → бэкап → атомарная замена.
     * При ошибке до APPLY локальная база не меняется.
     * После APPLY `snapshot_id` уже в БД (`snapshot_pending_ack`) — ACK можно повторить.
     */
    suspend fun applyPcPrimarySnapshot(
        preview: SnapshotPreview,
        onStage: (SnapshotStage) -> Unit = {}
    ): SyncStepResult {
        if (!tokenStore.isPaired()) {
            return SyncStepResult(false, SyncErrorCodes.messageRu(SyncErrorCodes.NOT_PAIRED))
        }
        var appliedSnapshotId: String? = null
        return try {
            FullSnapshotValidator.validateInfo(preview.info)

            onStage(SnapshotStage.DOWNLOAD)
            val chunks = ArrayList<FullSnapshot>(preview.info.chunkCount)
            for (i in 0 until preview.info.chunkCount) {
                when (val part = client.snapshotChunk(preview.info.snapshotId, i)) {
                    is SyncCallResult.Failure -> {
                        rememberError(part.code)
                        return SyncStepResult(false, part.messageRu)
                    }
                    is SyncCallResult.Ok -> {
                        FullSnapshotValidator.validateChunkMeta(preview.info, part.value, i)
                        chunks += part.value.payload
                    }
                }
            }
            if (chunks.size != preview.info.chunkCount) {
                throw SyncEngineException(SyncErrorCodes.SNAPSHOT_CORRUPT, "Пропущены части снимка")
            }
            onStage(SnapshotStage.VALIDATE)
            val merged = FullSnapshotCodec.mergeChunks(chunks)
            FullSnapshotValidator.validateMergedAgainstInfo(merged, preview.info)

            onStage(SnapshotStage.BACKUP)
            PhoneSnapshotBackup(context).createBackup(planner)

            onStage(SnapshotStage.APPLY)
            planner.applyPcPrimarySnapshot(merged, preview.info.snapshotId)
            appliedSnapshotId = preview.info.snapshotId
            // Сразу фиксируем pending ACK в DataStore (БД уже содержит snapshot_pending_ack).
            settingsRepo.update {
                it.copy(
                    lastPullAt = System.currentTimeMillis(),
                    pendingSnapshotAckId = preview.info.snapshotId,
                    lastSyncError = SyncErrorCodes.SNAPSHOT_ACK_PENDING
                )
            }

            var reminderNote = ""
            try {
                rescheduleReminders()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (e: Exception) {
                reminderNote = " Напоминания не обновлены: ${e.message ?: "ошибка"}."
            }

            val ackResult = finishSnapshotAck(
                snapshotId = preview.info.snapshotId,
                projects = merged.projects.size,
                tasks = merged.tasks.size,
                onStage = onStage
            )
            if (reminderNote.isNotEmpty()) {
                ackResult.copy(message = ackResult.message + reminderNote)
            } else {
                ackResult
            }
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            // База уже заменена — pending ACK остаётся в sync_applied_packages.
            throw e
        } catch (e: SyncEngineException) {
            if (appliedSnapshotId == null) {
                rememberError(e.code)
            }
            SyncStepResult(false, SyncErrorCodes.messageRu(e.code))
        } catch (e: Exception) {
            if (appliedSnapshotId == null) {
                rememberError(SyncErrorCodes.INTERNAL_ERROR)
            }
            SyncStepResult(false, e.message ?: SyncErrorCodes.messageRu(SyncErrorCodes.INTERNAL_ERROR))
        }
    }

    /**
     * Явное состояние локальных маркеров подтверждения полного снимка.
     * Не использует nullable как «успех / нет pending».
     */
    sealed class PendingSnapshotAckState {
        /** Pending ACK нет — подтверждение не требуется. */
        data object None : PendingSnapshotAckState()

        /** Есть ровно один согласованный pending ID — можно отправить ACK. */
        data class Ready(val snapshotId: String) : PendingSnapshotAckState()

        /** Уже включён режим «нужен полный снимок» (после restore / corrupt). */
        data object RequiresFullSnapshot : PendingSnapshotAckState()

        /**
         * Повреждённые маркеры: расхождение Room/DataStore или несколько pending.
         * Безопасный режим уже включён; HTTP ACK отправлять нельзя.
         */
        data class Corrupt(val detail: String) : PendingSnapshotAckState()
    }

    /**
     * Разрешает локальные маркеры pending ACK.
     * При повреждении включает `requiresPcPrimarySnapshot` и очищает DataStore ID.
     */
    suspend fun resolvePendingSnapshotAckState(): PendingSnapshotAckState {
        val current = settings()
        if (current.requiresPcPrimarySnapshot) {
            if (current.pendingSnapshotAckId.isNotBlank()) {
                settingsRepo.update {
                    it.copy(
                        pendingSnapshotAckId = "",
                        lastSyncError = SyncErrorCodes.SNAPSHOT_REQUIRED
                    )
                }
            }
            return PendingSnapshotAckState.RequiresFullSnapshot
        }

        val pendingIds = planner.listPendingSnapshotAckIds()
        val fromSettings = current.pendingSnapshotAckId

        if (pendingIds.size > 1) {
            enterCorruptPendingAckSafeMode()
            return PendingSnapshotAckState.Corrupt(
                "Несколько незакрытых подтверждений снимка"
            )
        }

        val fromDb = pendingIds.firstOrNull()
        if (fromDb != null && fromSettings.isNotBlank() && fromDb != fromSettings) {
            enterCorruptPendingAckSafeMode()
            return PendingSnapshotAckState.Corrupt(
                "Идентификаторы подтверждения снимка в базе и настройках не совпадают"
            )
        }

        if (fromDb != null) {
            if (fromSettings != fromDb) {
                settingsRepo.update {
                    it.copy(
                        pendingSnapshotAckId = fromDb,
                        lastSyncError = SyncErrorCodes.SNAPSHOT_ACK_PENDING
                    )
                }
            }
            return PendingSnapshotAckState.Ready(fromDb)
        }

        if (fromSettings.isNotBlank()) {
            // Только DataStore без Room — нельзя ACK по устаревшему ID.
            enterCorruptPendingAckSafeMode()
            return PendingSnapshotAckState.Corrupt(
                "Подтверждение снимка сохранено неполно — нужна полная копия с ПК"
            )
        }

        return PendingSnapshotAckState.None
    }

    private suspend fun enterCorruptPendingAckSafeMode() {
        planner.closePendingSnapshotAcksAfterRestore()
        settingsRepo.update {
            it.copy(
                pendingSnapshotAckId = "",
                requiresPcPrimarySnapshot = true,
                lastSyncError = SyncErrorCodes.SNAPSHOT_STATE_CORRUPT
            )
        }
        rememberError(SyncErrorCodes.SNAPSHOT_STATE_CORRUPT)
    }

    /**
     * Совместимость: возвращает ID только для согласованного Ready;
     * при повреждении включает safe-mode и возвращает null без маскировки успеха в retry.
     */
    suspend fun hydratePendingSnapshotAck(): String? =
        when (val state = resolvePendingSnapshotAckState()) {
            is PendingSnapshotAckState.Ready -> state.snapshotId
            else -> null
        }

    /** Повторная отправка ACK без повторной замены базы (после сбоя сети или перезапуска). */
    suspend fun retryPendingSnapshotAck(): SyncStepResult {
        return when (val state = resolvePendingSnapshotAckState()) {
            is PendingSnapshotAckState.None ->
                SyncStepResult(true, "Подтверждение снимка не требуется")
            is PendingSnapshotAckState.Ready ->
                finishSnapshotAck(snapshotId = state.snapshotId, projects = -1, tasks = -1)
            is PendingSnapshotAckState.RequiresFullSnapshot ->
                SyncStepResult(false, SyncErrorCodes.messageRu(SyncErrorCodes.SNAPSHOT_REQUIRED))
            is PendingSnapshotAckState.Corrupt ->
                SyncStepResult(
                    false,
                    SyncErrorCodes.messageRu(SyncErrorCodes.SNAPSHOT_STATE_CORRUPT)
                )
        }
    }

    private suspend fun finishSnapshotAck(
        snapshotId: String,
        projects: Int,
        tasks: Int,
        onStage: (SnapshotStage) -> Unit = {}
    ): SyncStepResult {
        return when (val ack = client.snapshotAck(snapshotId)) {
            is SyncCallResult.Failure -> {
                settingsRepo.update {
                    it.copy(
                        pendingSnapshotAckId = snapshotId,
                        lastSyncError = if (ack.code == SyncErrorCodes.SNAPSHOT_CORRUPT) {
                            SyncErrorCodes.SNAPSHOT_CORRUPT
                        } else {
                            SyncErrorCodes.SNAPSHOT_ACK_PENDING
                        }
                    )
                }
                SyncStepResult(
                    ok = false,
                    message = if (projects >= 0) {
                        "Данные применены (проектов $projects, задач $tasks), но подтверждение на ПК " +
                            "не завершено: ${ack.messageRu}. Повторите подтверждение."
                    } else {
                        "Подтверждение снимка на ПК не завершено: ${ack.messageRu}"
                    }
                )
            }
            is SyncCallResult.Ok -> {
                planner.markSnapshotAckCompleted(snapshotId)
                settingsRepo.update {
                    it.copy(
                        pendingSnapshotAckId = "",
                        requiresPcPrimarySnapshot = false,
                        lastSyncError = "",
                        lastPullAt = System.currentTimeMillis()
                    )
                }
                onStage(SnapshotStage.DONE)
                SyncStepResult(
                    true,
                    if (projects >= 0) {
                        "Получено с ПК: проектов $projects, задач $tasks. " +
                            "Резервная копия телефона сохранена."
                    } else {
                        "Подтверждение снимка на ПК завершено."
                    }
                )
            }
        }
    }

    suspend fun restorePhoneBackup(): SyncStepResult {
        return try {
            val file = PhoneSnapshotBackup(context).restoreLatest(planner)
            // База уже заменена: закрываем pending ACK и требуем новый полный снимок.
            planner.closePendingSnapshotAcksAfterRestore()
            settingsRepo.update {
                it.copy(
                    pendingSnapshotAckId = "",
                    requiresPcPrimarySnapshot = true,
                    lastSyncError = SyncErrorCodes.SNAPSHOT_REQUIRED
                )
            }
            try {
                rescheduleReminders()
            } catch (e: kotlin.coroutines.cancellation.CancellationException) {
                throw e
            } catch (_: Exception) {
                // Напоминания не должны откатывать согласованное состояние восстановления.
            }
            SyncStepResult(
                true,
                "Резервная копия телефона восстановлена. Для продолжения синхронизации снова получите полную копию с ПК"
            )
        } catch (e: kotlin.coroutines.cancellation.CancellationException) {
            throw e
        } catch (e: SyncEngineException) {
            SyncStepResult(false, SyncErrorCodes.messageRu(e.code))
        } catch (e: Exception) {
            SyncStepResult(false, e.message ?: "Не удалось восстановить копию")
        }
    }

    /** Перепланирует напоминания по срокам открытых задач. */
    suspend fun rescheduleReminders() {
        val snooze = settings().defaultSnoozeMinutes
        val (tasks, _) = planner.exportSnapshot()
        ReminderScheduler.syncTaskReminders(context, tasks, snooze)
    }

    private fun clearPending() {
        pendingQr = null
        pendingRequestId = ""
        pairingState.value = PairingProgress()
    }

    private suspend fun rememberError(code: String) {
        settingsRepo.update { it.copy(lastSyncError = code) }
    }
}
