package com.mars.planner.sync

import org.json.JSONObject
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Чистая UI-логика синхронизации: категории ошибок, пользовательские формулировки
 * и сравнение версий расхождения без Compose/Android.
 *
 * Секреты, сырой JSON, UUID, hash и токены на экран не выводятся.
 */
object SyncUiPresentation {

    private val ru = Locale("ru")
    private val dueFormatter: DateTimeFormatter =
        DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", ru)

    enum class ErrorCategory {
        NETWORK,
        SECURE_CONNECTION,
        AUTHORIZATION,
        PAIRING,
        COMPATIBILITY_DATA,
        INTERNAL
    }

    enum class ConnectionKind {
        NOT_LINKED,
        AWAITING_PC_CONFIRMATION,
        LINKED,
        NEEDS_ATTENTION
    }

    data class ErrorPresentation(
        val category: ErrorCategory,
        val categoryLabel: String,
        val title: String,
        val explanation: String,
        val action: String
    )

    data class ConnectionStatus(
        val kind: ConnectionKind,
        val title: String,
        val deviceName: String?,
        val technicalEndpoint: String?
    )

    data class ExchangeStatus(
        val pendingLine: String,
        val conflictsLine: String,
        val lastPushLine: String,
        val lastPullLine: String,
        val busyLine: String?,
        val lastActionLine: String?
    )

    data class PayloadView(
        val deleted: Boolean,
        val opLabel: String,
        val fields: Map<String, String>,
        val unreadable: Boolean = false,
        val empty: Boolean = false
    )

    data class DiffRow(
        val label: String,
        val phoneValue: String,
        val pcValue: String,
        val differs: Boolean
    )

    data class ConflictCompare(
        val entityTypeLabel: String,
        val recordTitle: String,
        val phoneDeleted: Boolean,
        val pcDeleted: Boolean,
        val scenarioNote: String?,
        /** Только различающиеся поля; при удалённой версии список может быть пуст. */
        val differingRows: List<DiffRow>,
        val phoneOpLabel: String,
        val pcOpLabel: String,
        val phoneUnreadable: Boolean,
        val pcUnreadable: Boolean
    )

    fun categorize(code: String): ErrorCategory = when (normalize(code)) {
        SyncErrorCodes.NETWORK_UNAVAILABLE, SyncErrorCodes.TIMEOUT -> ErrorCategory.NETWORK
        SyncErrorCodes.TLS_PIN_MISMATCH, SyncErrorCodes.TLS_ERROR -> ErrorCategory.SECURE_CONNECTION
        SyncErrorCodes.UNAUTHORIZED, SyncErrorCodes.FORBIDDEN, SyncErrorCodes.DEVICE_REVOKED ->
            ErrorCategory.AUTHORIZATION
        SyncErrorCodes.BAD_PAIRING_TOKEN,
        SyncErrorCodes.PAIRING_TOKEN_EXPIRED,
        SyncErrorCodes.PAIRING_TOKEN_USED,
        SyncErrorCodes.PAIRING_PENDING,
        SyncErrorCodes.PAIRING_DENIED,
        SyncErrorCodes.BAD_QR,
        SyncErrorCodes.NOT_PAIRED,
        SyncErrorCodes.NOT_CONFIGURED -> ErrorCategory.PAIRING
        SyncErrorCodes.UNSUPPORTED_PROTOCOL,
        SyncErrorCodes.INVALID_PAYLOAD,
        SyncErrorCodes.UNRESOLVED_DEPENDENCY,
        SyncErrorCodes.PACKAGE_REJECTED,
        SyncErrorCodes.SENDER_MISMATCH,
        SyncErrorCodes.REQUEST_TOO_LARGE,
        SyncErrorCodes.RATE_LIMITED,
        SyncErrorCodes.DEMO_NOT_ALLOWED,
        SyncErrorCodes.SYNC_DISABLED,
        SyncErrorCodes.NOT_FOUND,
        SyncErrorCodes.CONFLICT_STATE,
        SyncErrorCodes.SNAPSHOT_UNSUPPORTED,
        SyncErrorCodes.SNAPSHOT_CORRUPT,
        SyncErrorCodes.SNAPSHOT_ACK_PENDING,
        SyncErrorCodes.SNAPSHOT_REQUIRED,
        SyncErrorCodes.SNAPSHOT_STATE_CORRUPT,
        SyncErrorCodes.RESPONSE_TOO_LARGE -> ErrorCategory.COMPATIBILITY_DATA
        SyncErrorCodes.INTERNAL_ERROR -> ErrorCategory.INTERNAL
        else -> ErrorCategory.INTERNAL
    }

    fun categoryLabel(category: ErrorCategory): String = when (category) {
        ErrorCategory.NETWORK -> "Сеть"
        ErrorCategory.SECURE_CONNECTION -> "Защищённое соединение"
        ErrorCategory.AUTHORIZATION -> "Авторизация"
        ErrorCategory.PAIRING -> "Сопряжение"
        ErrorCategory.COMPATIBILITY_DATA -> "Совместимость и данные"
        ErrorCategory.INTERNAL -> "Внутренняя ошибка"
    }

    fun presentError(code: String): ErrorPresentation {
        val normalized = normalize(code)
        val category = categorize(normalized)
        val (title, explanation, action) = when (normalized) {
            SyncErrorCodes.NETWORK_UNAVAILABLE -> Triple(
                "ПК недоступен",
                "Телефон не смог связаться с «Рубежем» в общей сети.",
                "Проверьте общую Wi‑Fi-сеть и что «Рубеж» на ПК запущен, затем повторите попытку."
            )
            SyncErrorCodes.TIMEOUT -> Triple(
                "ПК не ответил вовремя",
                "Запрос к «Рубежу» занял слишком много времени.",
                "Убедитесь, что ПК не ушёл в сон и «Рубеж» открыт, затем повторите попытку."
            )
            SyncErrorCodes.TLS_PIN_MISMATCH -> Triple(
                "Сертификат ПК изменился",
                "Защищённое соединение прервано: подпись ПК не совпала с сохранённой.",
                "Выполните новое сопряжение по актуальному QR-коду из «Рубежа»."
            )
            SyncErrorCodes.TLS_ERROR -> Triple(
                "Нет защищённого соединения",
                "Не удалось установить безопасный канал с ПК.",
                "Проверьте сеть и «Рубеж», затем повторите попытку. Если ошибка повторяется — новое сопряжение."
            )
            SyncErrorCodes.UNAUTHORIZED -> Triple(
                "ПК не принял доступ телефона",
                "Сохранённый ключ доступа не принят «Рубежем».",
                "Разорвите сопряжение на телефоне и выполните новое по QR-коду."
            )
            SyncErrorCodes.FORBIDDEN -> Triple(
                "Доступ запрещён",
                "ПК отклонил запрос этого телефона.",
                "Проверьте настройки синхронизации в «Рубеже» или выполните новое сопряжение."
            )
            SyncErrorCodes.DEVICE_REVOKED -> Triple(
                "Устройство отозвано на ПК",
                "Этот телефон больше не разрешён в «Рубеже».",
                "Разрешите устройство заново или выполните новое сопряжение по QR."
            )
            SyncErrorCodes.BAD_PAIRING_TOKEN -> Triple(
                "Неверный код сопряжения",
                "Код из QR не подходит для связи с этим ПК.",
                "Покажите QR в «Рубеже» заново и отсканируйте его ещё раз."
            )
            SyncErrorCodes.PAIRING_TOKEN_EXPIRED -> Triple(
                "Код сопряжения истёк",
                "Время действия QR на ПК закончилось.",
                "Покажите новый QR в «Рубеже» и повторите сканирование."
            )
            SyncErrorCodes.PAIRING_TOKEN_USED -> Triple(
                "Код уже использован",
                "Этот QR уже был применён ранее.",
                "Покажите новый QR в «Рубеже» и выполните сопряжение заново."
            )
            SyncErrorCodes.PAIRING_PENDING -> Triple(
                "Нужно подтверждение на ПК",
                "Заявка отправлена, но в «Рубеже» ещё не нажали «Разрешить».",
                "Откройте «Рубеж» на ПК и подтвердите этот телефон."
            )
            SyncErrorCodes.PAIRING_DENIED -> Triple(
                "Сопряжение отклонено",
                "На ПК заявку отклонили.",
                "При необходимости покажите QR снова и подтвердите телефон в «Рубеже»."
            )
            SyncErrorCodes.BAD_QR -> Triple(
                "QR не подходит",
                "Код не похож на QR синхронизации «Рубеж».",
                "Откройте QR именно в разделе синхронизации «Рубежа» и отсканируйте его."
            )
            SyncErrorCodes.NOT_PAIRED -> Triple(
                "Телефон не связан с ПК",
                "Обмен возможен только после сопряжения.",
                "Отсканируйте QR-код из «Рубежа»."
            )
            SyncErrorCodes.NOT_CONFIGURED -> Triple(
                "Нет данных подключения",
                "Адрес ПК ещё не сохранён.",
                "Отсканируйте QR-код из «Рубежа»."
            )
            SyncErrorCodes.UNSUPPORTED_PROTOCOL -> Triple(
                "Версии не совпадают",
                "Телефон и «Рубеж» говорят на разных версиях протокола.",
                "Обновите Mars или «Рубеж» до совместимых версий."
            )
            SyncErrorCodes.INVALID_PAYLOAD -> Triple(
                "Данные не приняты",
                "ПК не смог разобрать переданные изменения.",
                "Повторите обмен. Если ошибка останется — получите полную копию с ПК."
            )
            SyncErrorCodes.UNRESOLVED_DEPENDENCY -> Triple(
                "Нет связанного проекта",
                "В пакете есть ссылка на проект, которого нет на принимающей стороне.",
                "Получите полную копию с ПК или сначала синхронизируйте проекты."
            )
            SyncErrorCodes.PACKAGE_REJECTED -> Triple(
                "Пакет отклонён",
                "ПК не принял набор изменений и ожидает новый.",
                "Повторите отправку изменений."
            )
            SyncErrorCodes.SENDER_MISMATCH -> Triple(
                "Пакет от другого устройства",
                "Ответ относится к другому телефону.",
                "Проверьте сопряжение и повторите обмен с этим устройством."
            )
            SyncErrorCodes.REQUEST_TOO_LARGE -> Triple(
                "Слишком большой пакет",
                "Объём данных превысил допустимый размер обмена.",
                "Повторите попытку позже. При необходимости получите полную копию с ПК."
            )
            SyncErrorCodes.RATE_LIMITED -> Triple(
                "Слишком много попыток",
                "ПК временно ограничил частоту запросов.",
                "Подождите около минуты и повторите попытку."
            )
            SyncErrorCodes.DEMO_NOT_ALLOWED -> Triple(
                "На ПК открыта демо-база",
                "Демо-данные нельзя обменивать с телефоном.",
                "В «Рубеже» откройте рабочую базу и повторите обмен."
            )
            SyncErrorCodes.SYNC_DISABLED -> Triple(
                "Синхронизация выключена на ПК",
                "«Рубеж» сейчас не принимает обмен.",
                "Включите синхронизацию в «Рубеже» и повторите попытку."
            )
            SyncErrorCodes.NOT_FOUND -> Triple(
                "Ресурс не найден",
                "ПК не нашёл запрошенные данные обмена.",
                "Повторите попытку. Если нужно — получите полную копию с ПК."
            )
            SyncErrorCodes.CONFLICT_STATE -> Triple(
                "Состояние на ПК изменилось",
                "Пока шёл обмен, данные на ПК уже обновились.",
                "Получите изменения с ПК и повторите действие."
            )
            SyncErrorCodes.SNAPSHOT_UNSUPPORTED -> Triple(
                "ПК не умеет отдавать полный снимок",
                "Эта версия «Рубежа» не поддерживает полную копию.",
                "Обновите «Рубеж» на ПК и повторите получение полной копии."
            )
            SyncErrorCodes.SNAPSHOT_CORRUPT -> Triple(
                "Снимок ПК повреждён",
                "Полная копия пришла неполной или повреждённой.",
                "Повторно получите полную копию с ПК."
            )
            SyncErrorCodes.SNAPSHOT_ACK_PENDING -> Triple(
                "Нужно подтвердить снимок на ПК",
                "Данные уже на телефоне, но подтверждение на ПК не завершено.",
                "Нажмите «Повторить подтверждение снимка на ПК»."
            )
            SyncErrorCodes.SNAPSHOT_REQUIRED -> Triple(
                "Нужна полная копия с ПК",
                "После восстановления резервной копии обычный обмен заблокирован.",
                "Снова получите полную копию с ПК."
            )
            SyncErrorCodes.SNAPSHOT_STATE_CORRUPT -> Triple(
                "Состояние снимка повреждено",
                "Локальные отметки подтверждения снимка повреждены.",
                "Получите полную копию с ПК заново."
            )
            SyncErrorCodes.RESPONSE_TOO_LARGE -> Triple(
                "Ответ ПК слишком большой",
                "Телефон не принял слишком объёмный ответ.",
                "Повторите попытку. При необходимости обновите Mars или «Рубеж»."
            )
            SyncErrorCodes.INTERNAL_ERROR -> Triple(
                "Внутренняя ошибка",
                "Клиент синхронизации столкнулся с неожиданной ошибкой.",
                "Повторите попытку. Если ошибка повторяется — перезапустите приложение и «Рубеж»."
            )
            else -> Triple(
                "Ошибка синхронизации",
                "Произошла неизвестная ошибка обмена с ПК.",
                "Повторите попытку. Если не поможет — выполните новое сопряжение или получите полную копию с ПК."
            )
        }
        return ErrorPresentation(
            category = category,
            categoryLabel = categoryLabel(category),
            title = title,
            explanation = explanation,
            action = action
        )
    }

    fun connectionStatus(
        syncPaired: Boolean,
        pairingActive: Boolean,
        pairedDeviceName: String,
        pairedHost: String,
        pairedPort: Int,
        lastSyncError: String,
        requiresPcPrimarySnapshot: Boolean,
        pendingSnapshotAck: Boolean
    ): ConnectionStatus {
        if (pairingActive) {
            return ConnectionStatus(
                kind = ConnectionKind.AWAITING_PC_CONFIRMATION,
                title = "Ожидается подтверждение в «Рубеже»",
                deviceName = pairedHost.takeIf { it.isNotBlank() },
                technicalEndpoint = endpointLine(pairedHost, pairedPort)
            )
        }
        if (!syncPaired) {
            return ConnectionStatus(
                kind = ConnectionKind.NOT_LINKED,
                title = "Телефон не связан с ПК",
                deviceName = null,
                technicalEndpoint = null
            )
        }
        val needsAttention = lastSyncError.isNotBlank() ||
            requiresPcPrimarySnapshot ||
            pendingSnapshotAck
        val name = pairedDeviceName.ifBlank { "ПК" }
        return ConnectionStatus(
            kind = if (needsAttention) ConnectionKind.NEEDS_ATTENTION else ConnectionKind.LINKED,
            title = if (needsAttention) "Требуется внимание" else "Связь с ПК настроена",
            deviceName = name,
            technicalEndpoint = endpointLine(pairedHost, pairedPort)
        )
    }

    fun exchangeStatus(
        pendingCount: Int,
        conflictCount: Int,
        lastPushAt: Long,
        lastPullAt: Long,
        busy: Boolean,
        busyDetail: String?,
        lastActionOk: Boolean?,
        lastActionMessage: String?,
        zone: ZoneId = ZoneId.systemDefault()
    ): ExchangeStatus {
        val pendingLine = when {
            pendingCount <= 0 -> "На телефоне нет изменений для отправки"
            pendingCount == 1 -> "На телефоне есть 1 изменение для отправки"
            pendingCount in 2..4 -> "На телефоне есть $pendingCount изменения для отправки"
            else -> "На телефоне есть $pendingCount изменений для отправки"
        }
        val conflictsLine = when {
            conflictCount <= 0 -> "Расхождений нет"
            conflictCount == 1 -> "Есть 1 неразобранное расхождение"
            conflictCount in 2..4 -> "Есть $conflictCount неразобранных расхождения"
            else -> "Есть $conflictCount неразобранных расхождений"
        }
        val push = formatTimestamp(lastPushAt, zone)
        val pull = formatTimestamp(lastPullAt, zone)
        val busyLine = when {
            !busy -> null
            !busyDetail.isNullOrBlank() -> busyDetail
            else -> "Сейчас выполняется обмен с ПК…"
        }
        val lastActionLine = lastActionMessage?.takeIf { it.isNotBlank() }?.let { msg ->
            when (lastActionOk) {
                true -> "Последнее действие: $msg"
                false -> "Последнее действие не выполнено: $msg"
                null -> msg
            }
        }
        return ExchangeStatus(
            pendingLine = pendingLine,
            conflictsLine = conflictsLine,
            lastPushLine = "Последняя отправка: $push",
            lastPullLine = "Последнее получение: $pull",
            busyLine = busyLine,
            lastActionLine = lastActionLine
        )
    }

    fun pushButtonLabel(pendingCount: Int): String =
        if (pendingCount > 0) "Отправить изменения ($pendingCount)" else "Отправить изменения"

    fun parsePayload(payloadJson: String?, op: String): PayloadView {
        val opLabel = opLabel(op)
        if (payloadJson.isNullOrBlank()) {
            return PayloadView(
                deleted = op == "delete",
                opLabel = opLabel,
                fields = emptyMap(),
                empty = true
            )
        }
        val json = runCatching { JSONObject(payloadJson) }.getOrNull()
            ?: return PayloadView(
                deleted = false,
                opLabel = opLabel,
                fields = emptyMap(),
                unreadable = true
            )
        if (json.optBoolean("deleted", false) || op == "delete") {
            return PayloadView(deleted = true, opLabel = "удалено", fields = emptyMap())
        }
        val fields = linkedMapOf<String, String>()
        json.optString("title").takeIf { it.isNotBlank() }?.let { fields["Заголовок"] = it }
        json.optString("name").takeIf { it.isNotBlank() }?.let { fields["Название"] = it }
        json.optString("description").takeIf { it.isNotBlank() }?.let { fields["Описание"] = it }
        json.optString("status").takeIf { it.isNotBlank() }?.let {
            fields["Статус"] = if (it == "done") "Выполнено" else "Открыта"
        }
        json.optString("priority").takeIf { it.isNotBlank() }?.let {
            fields["Приоритет"] = when (it) {
                "low" -> "Низкий"
                "high" -> "Высокий"
                else -> "Обычный"
            }
        }
        if (!json.isNull("due_at")) {
            val raw = json.optString("due_at")
            if (raw.isNotBlank()) fields["Срок"] = formatDueAt(raw)
        }
        if (json.optBoolean("archived", false)) fields["Архив"] = "да"
        return PayloadView(deleted = false, opLabel = opLabel, fields = fields)
    }

    fun recordTitle(localPayloadJson: String?, remotePayloadJson: String?): String {
        for (payload in listOf(localPayloadJson, remotePayloadJson)) {
            if (payload.isNullOrBlank()) continue
            val json = runCatching { JSONObject(payload) }.getOrNull() ?: continue
            val title = json.optString("title").ifBlank { json.optString("name") }
            if (title.isNotBlank()) return title
        }
        return "Без названия"
    }

    fun compareConflict(
        entityTypeTask: Boolean,
        localPayloadJson: String?,
        remotePayloadJson: String?,
        localOp: String,
        remoteOp: String
    ): ConflictCompare {
        val phone = parsePayload(localPayloadJson, localOp)
        val pc = parsePayload(remotePayloadJson, remoteOp)
        val title = recordTitle(localPayloadJson, remotePayloadJson)
        val scenarioNote = when {
            phone.deleted && !pc.deleted ->
                "На телефоне запись удалена, на ПК — изменена или сохранена."
            !phone.deleted && pc.deleted ->
                "На ПК запись удалена, на телефоне — изменена или сохранена."
            phone.deleted && pc.deleted ->
                "Обе стороны отмечают удаление."
            phone.unreadable || pc.unreadable ->
                "Одну из версий не удалось прочитать. Выберите сторону или получите полную копию с ПК."
            else -> null
        }
        val labels = (phone.fields.keys + pc.fields.keys).toList().distinct()
        val rows = labels.mapNotNull { label ->
            val a = phone.fields[label] ?: "—"
            val b = pc.fields[label] ?: "—"
            if (a == b) null
            else DiffRow(label = label, phoneValue = a, pcValue = b, differs = true)
        }
        return ConflictCompare(
            entityTypeLabel = if (entityTypeTask) "Задача" else "Проект",
            recordTitle = title,
            phoneDeleted = phone.deleted,
            pcDeleted = pc.deleted,
            scenarioNote = scenarioNote,
            differingRows = rows,
            phoneOpLabel = phone.opLabel,
            pcOpLabel = pc.opLabel,
            phoneUnreadable = phone.unreadable,
            pcUnreadable = pc.unreadable
        )
    }

    fun containsForbiddenLeak(text: String): Boolean {
        val t = text.lowercase(Locale.ROOT)
        if ("uuid" in t) return true
        if ("token" in t) return true
        if ("sha256" in t || "version_hash" in t || "cert_sha" in t) return true
        if (t.contains('{') && t.contains('}')) return true
        if (UUID_REGEX.containsMatchIn(text)) return true
        if (HEX64_REGEX.containsMatchIn(t)) return true
        return false
    }

    fun presentationLeaksSecrets(presentation: ErrorPresentation): Boolean =
        listOf(
            presentation.title,
            presentation.explanation,
            presentation.action,
            presentation.categoryLabel
        ).any { containsForbiddenLeak(it) }

    private fun normalize(code: String): String = code.trim()

    private fun endpointLine(host: String, port: Int): String? {
        if (host.isBlank()) return null
        return if (port in 1..65535) "$host:$port" else host
    }

    private fun formatTimestamp(millis: Long, zone: ZoneId): String {
        if (millis <= 0L) return "ещё не было"
        val formatter = DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm", ru)
        return java.time.Instant.ofEpochMilli(millis).atZone(zone).format(formatter)
    }

    private fun formatDueAt(raw: String): String {
        val parsed = runCatching {
            OffsetDateTime.parse(raw.replace(' ', 'T'))
                .atZoneSameInstant(ZoneId.systemDefault())
                .format(dueFormatter)
        }.getOrNull()
        return parsed ?: "срок указан"
    }

    private fun opLabel(op: String): String = when (op) {
        "create" -> "создано"
        "delete" -> "удалено"
        else -> "изменено"
    }

    private val UUID_REGEX =
        Regex("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")
    private val HEX64_REGEX = Regex("\\b[0-9a-f]{64}\\b")
}
