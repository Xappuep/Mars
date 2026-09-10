package com.mars.planner.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.mars.planner.domain.model.SyncConflictItem
import com.mars.planner.domain.model.SyncEntityType
import com.mars.planner.sync.SyncCallResult
import com.mars.planner.sync.SyncCoordinator
import com.mars.planner.sync.SyncErrorCodes
import com.mars.planner.sync.runWithBusyFlag
import com.mars.planner.ui.components.MarsDangerOutlineButton
import com.mars.planner.ui.components.MarsPrimaryButton
import com.mars.planner.ui.components.MarsSecondaryButton
import com.mars.planner.ui.theme.LocalMarsPalette
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusOpen
import com.mars.planner.ui.theme.StatusOverdue
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.json.JSONObject

/** Экран синхронизации с ПК-узлом «Рубеж». Секретов на экране нет. */
@Composable
internal fun SyncScreen(vm: AppViewModel, nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val settings by vm.settings.collectAsState()
    val pending by vm.pendingSyncCount.collectAsState()
    val conflicts by vm.conflicts.collectAsState()
    val pairing by vm.pairing.collectAsState()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf(true) }
    var showManualQr by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    var snapshotPreview by remember { mutableStateOf<SyncCoordinator.SnapshotPreview?>(null) }
    var snapshotStage by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }

    fun step(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            runWithBusyFlag({ busy = it }, block)
        }
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) {
            message = "Сканирование отменено"
            messageOk = false
            return@rememberLauncherForActivityResult
        }
        step {
            val step = vm.startPairing(raw)
            message = step.message
            messageOk = step.ok
        }
    }
    val cameraPermission = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            scanLauncher.launch(
                ScanOptions()
                    .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                    .setPrompt("Наведите камеру на QR-код в «Рубеже» на ПК")
                    .setBeepEnabled(false)
                    .setOrientationLocked(false)
            )
        } else {
            message = "Без доступа к камере QR не отсканировать. Можно вставить код вручную."
            messageOk = false
        }
    }

    // Пока заявка не подтверждена — тихо опрашиваем ПК.
    LaunchedEffect(pairing.active) {
        while (pairing.active) {
            delay(3_000)
            if (!pairing.active) break
            val step = vm.pollPairStatus()
            if (!step.ok || step.message.contains("Сопряжение выполнено")) {
                message = step.message
                messageOk = step.ok
            }
        }
    }

    // После перезапуска: незавершённый ACK или повреждённые маркеры.
    LaunchedEffect(settings.syncPaired) {
        if (settings.syncPaired) {
            when (val state = vm.resolvePendingSnapshotAckState()) {
                is SyncCoordinator.PendingSnapshotAckState.Ready -> {
                    message = SyncErrorCodes.messageRu(SyncErrorCodes.SNAPSHOT_ACK_PENDING)
                    messageOk = false
                }
                is SyncCoordinator.PendingSnapshotAckState.Corrupt -> {
                    message = SyncErrorCodes.messageRu(SyncErrorCodes.SNAPSHOT_STATE_CORRUPT)
                    messageOk = false
                }
                is SyncCoordinator.PendingSnapshotAckState.RequiresFullSnapshot -> {
                    message = SyncErrorCodes.messageRu(SyncErrorCodes.SNAPSHOT_REQUIRED)
                    messageOk = false
                }
                SyncCoordinator.PendingSnapshotAckState.None -> Unit
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitleRow("Синхронизация с ПК", nav)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.card)
                .padding(16.dp)
        ) {
            StatusLine(
                label = "Сопряжение",
                value = if (settings.syncPaired) {
                    settings.pairedDeviceName.ifBlank { "ПК ${settings.pairedHost}" }
                } else {
                    "не выполнено"
                },
                accent = if (settings.syncPaired) StatusDone else StatusOverdue
            )
            if (settings.syncPaired) {
                StatusLine("Адрес ПК", "${settings.pairedHost}:${settings.pairedPort}")
            }
            StatusLine(
                label = "К отправке",
                value = if (pending == 0) "нет изменений" else "$pending изменений",
                accent = if (pending == 0) StatusDone else StatusOpen
            )
            StatusLine(
                label = "Расхождения",
                value = if (conflicts.isEmpty()) "нет" else "${conflicts.size}",
                accent = if (conflicts.isEmpty()) StatusDone else StatusOverdue
            )
            StatusLine("Отправка", timestampLabel(settings.lastPushAt))
            StatusLine("Приём", timestampLabel(settings.lastPullAt))
            if (settings.lastSyncError.isNotBlank()) {
                StatusLine(
                    label = "Ошибка",
                    value = SyncErrorCodes.messageRu(settings.lastSyncError),
                    accent = StatusOverdue
                )
            }
        }

        if (pairing.active) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.accent.copy(alpha = 0.16f))
                    .padding(16.dp)
            ) {
                Text("Ждём подтверждения на ПК", color = palette.text, fontWeight = FontWeight.SemiBold)
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Откройте «Рубеж» на ${pairing.host} и нажмите «Разрешить» для этого телефона. " +
                        "Проверка идёт автоматически.",
                    color = palette.textMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarsSecondaryButton(
                        text = "Проверить сейчас",
                        onClick = {
                            step {
                                val step = vm.pollPairStatus()
                                message = step.message
                                messageOk = step.ok
                            }
                        }
                    )
                    MarsSecondaryButton("Отменить заявку", onClick = { vm.cancelPairing() })
                }
            }
        }

        if (!settings.syncPaired) {
            MarsPrimaryButton(
                text = "Сканировать QR с ПК",
                onClick = { cameraPermission.launch(Manifest.permission.CAMERA) },
                modifier = Modifier.fillMaxWidth()
            )
            MarsSecondaryButton(
                text = "Вставить код вручную",
                onClick = { showManualQr = true },
                modifier = Modifier.fillMaxWidth()
            )
        } else {
            val deltaLocked = settings.requiresPcPrimarySnapshot
            if (deltaLocked) {
                Text(
                    SyncErrorCodes.messageRu(SyncErrorCodes.SNAPSHOT_REQUIRED),
                    color = StatusOverdue,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(8.dp))
            }
            MarsPrimaryButton(
                text = if (pending > 0) "Отправить ($pending)" else "Отправить",
                onClick = {
                    step {
                        val step = vm.pushChanges()
                        message = step.message
                        messageOk = step.ok
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && !deltaLocked
            )
            MarsSecondaryButton(
                text = "Получить с ПК",
                onClick = {
                    step {
                        val step = vm.pullChanges()
                        message = step.message
                        messageOk = step.ok
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && !deltaLocked
            )
            MarsPrimaryButton(
                text = "Получить полную копию с ПК — данные ПК будут основными",
                onClick = {
                    step {
                        snapshotStage = "Получение сведений…"
                        when (val prep = vm.preparePcPrimarySnapshot()) {
                            is SyncCallResult.Failure -> {
                                message = prep.messageRu
                                messageOk = false
                                snapshotStage = null
                            }
                            is SyncCallResult.Ok -> {
                                snapshotPreview = prep.value
                                snapshotStage = null
                            }
                        }
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )
            MarsSecondaryButton(
                text = "Восстановить резервную копию телефона",
                onClick = { confirmRestore = true },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )
            if (settings.pendingSnapshotAckId.isNotBlank() && !settings.requiresPcPrimarySnapshot) {
                MarsPrimaryButton(
                    text = "Повторить подтверждение снимка на ПК",
                    onClick = {
                        step {
                            val step = vm.retryPendingSnapshotAck()
                            message = step.message
                            messageOk = step.ok
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                )
            }
            MarsSecondaryButton(
                text = "Проверить связь",
                onClick = {
                    step {
                        val step = vm.checkSyncServer()
                        message = step.message
                        messageOk = step.ok
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )
        }

        if (conflicts.isNotEmpty()) {
            MarsSecondaryButton(
                text = "Разобрать расхождения (${conflicts.size})",
                onClick = { nav.navigate(Routes.Conflicts) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        MarsSecondaryButton(
            text = "Инструкция",
            onClick = { nav.navigate(Routes.SyncGuide) },
            modifier = Modifier.fillMaxWidth()
        )

        if (settings.syncPaired) {
            MarsDangerOutlineButton(
                text = "Разорвать сопряжение",
                onClick = { confirmUnpair = true },
                modifier = Modifier.fillMaxWidth()
            )
        }

        if (busy) Text(snapshotStage ?: "Идёт обмен с ПК…", color = palette.textMuted, fontSize = 13.sp)
        message?.let {
            Text(it, color = if (messageOk) palette.highlight else StatusOverdue, fontSize = 13.sp)
        }
        Text(
            "Обмен идёт напрямую по домашней Wi-Fi-сети и только с сопряжённым ПК. " +
                "Ключ доступа хранится в защищённом хранилище телефона и на экране не показывается.",
            color = palette.textMuted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    if (showManualQr) {
        ManualQrDialog(
            onDismiss = { showManualQr = false },
            onSubmit = { raw ->
                showManualQr = false
                step {
                    val step = vm.startPairing(raw)
                    message = step.message
                    messageOk = step.ok
                }
            }
        )
    }

    if (confirmUnpair) {
        AlertDialog(
            onDismissRequest = { confirmUnpair = false },
            containerColor = palette.backgroundElevated,
            title = { Text("Разорвать сопряжение?") },
            text = {
                Text(
                    "Задачи и проекты на телефоне останутся. Для обмена с ПК понадобится " +
                        "снова отсканировать QR-код."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmUnpair = false
                    step {
                        vm.unpair()
                        message = "Сопряжение разорвано"
                        messageOk = true
                    }
                }) { Text("Разорвать") }
            },
            dismissButton = {
                TextButton(onClick = { confirmUnpair = false }) { Text("Отмена") }
            }
        )
    }

    snapshotPreview?.let { preview ->
        AlertDialog(
            onDismissRequest = { if (!busy) snapshotPreview = null },
            containerColor = palette.backgroundElevated,
            title = { Text("ПК будет основным источником") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Данные ПК заменят проекты и задачи на телефоне. " +
                            "Настройки темы, доступности и сопряжение сохранятся."
                    )
                    Text("На телефоне сейчас: проектов ${preview.localProjects}, задач ${preview.localTasks}.")
                    Text("В снимке ПК: проектов ${preview.remoteProjects}, задач ${preview.remoteTasks}.")
                    Text("Перед заменой будет создана восстанавливаемая резервная копия.")
                }
            },
            confirmButton = {
                TextButton(
                    enabled = !busy,
                    onClick = {
                        val p = preview
                        snapshotPreview = null
                        step {
                            val result = vm.applyPcPrimarySnapshot(p) { stage ->
                                snapshotStage = when (stage) {
                                    SyncCoordinator.SnapshotStage.INFO -> "Получение сведений…"
                                    SyncCoordinator.SnapshotStage.DOWNLOAD -> "Загрузка снимка…"
                                    SyncCoordinator.SnapshotStage.VALIDATE -> "Проверка…"
                                    SyncCoordinator.SnapshotStage.BACKUP -> "Резервная копия…"
                                    SyncCoordinator.SnapshotStage.APPLY -> "Применение…"
                                    SyncCoordinator.SnapshotStage.DONE -> "Готово"
                                }
                            }
                            message = result.message
                            messageOk = result.ok
                            snapshotStage = null
                        }
                    }
                ) { Text("Заменить данными ПК") }
            },
            dismissButton = {
                TextButton(enabled = !busy, onClick = { snapshotPreview = null }) {
                    Text("Отмена")
                }
            }
        )
    }

    if (confirmRestore) {
        AlertDialog(
            onDismissRequest = { confirmRestore = false },
            containerColor = palette.backgroundElevated,
            title = { Text("Восстановить копию телефона?") },
            text = {
                Text(
                    "Будет восстановлена последняя копия, созданная перед полной " +
                        "заменой данными ПК. Сопряжение и тема не изменятся."
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    confirmRestore = false
                    step {
                        val step = vm.restorePhoneBackup()
                        message = step.message
                        messageOk = step.ok
                    }
                }) { Text("Восстановить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmRestore = false }) { Text("Отмена") }
            }
        )
    }
}

@Composable
private fun StatusLine(label: String, value: String, accent: Color? = null) {
    Row(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
        val palette = LocalMarsPalette.current
        Text(label, color = palette.textMuted, fontSize = 13.sp, modifier = Modifier.width(110.dp))
        Text(value, color = accent ?: palette.text, fontSize = 13.sp)
    }
}

@Composable
private fun ManualQrDialog(onDismiss: () -> Unit, onSubmit: (String) -> Unit) {
    val palette = LocalMarsPalette.current
    var raw by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = palette.backgroundElevated,
        title = { Text("Код сопряжения") },
        text = {
            Column {
                Text(
                    "Диагностический ввод: скопируйте текст под QR-кодом в «Рубеже» и вставьте здесь.",
                    color = palette.textMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                OutlinedTextField(
                    value = raw,
                    onValueChange = { raw = it },
                    label = { Text("Содержимое QR") },
                    colors = marsFieldColors,
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(enabled = raw.isNotBlank(), onClick = { onSubmit(raw.trim()) }) {
                Text("Связать")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Отмена") } }
    )
}

/** Пошаговая инструкция: как связать телефон и ПК. */
@Composable
internal fun SyncGuideScreen(nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val steps = listOf(
        "Подключите телефон и ПК к одной домашней Wi-Fi-сети. Через мобильный интернет обмен не работает.",
        "На ПК откройте «Рубеж» → «Синхронизация» → «Показать QR-код».",
        "На телефоне нажмите «Сканировать QR с ПК» и наведите камеру на код.",
        "На ПК подтвердите заявку: в списке появится название этого телефона, нажмите «Разрешить».",
        "Если на ПК уже есть полная база, а на телефоне ещё нет: нажмите «Получить полную копию с ПК». " +
            "Данные ПК станут основными. Перед заменой телефон покажет счётчики и создаст резервную копию.",
        "Кнопки «Отправить» и «Получить с ПК» — только для последующих изменений (дельта), " +
            "они не заменяют первоначальный полный снимок.",
        "Если нужно вернуть задачи телефона после полной копии — «Восстановить резервную копию телефона».",
        "Если задача менялась и там, и там, откройте «Расхождения» и выберите, какую версию оставить."
    )
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        ScreenTitleRow("Как связать телефон и ПК", nav)
        steps.forEachIndexed { index, step ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(18.dp))
                    .background(palette.card)
                    .padding(14.dp)
            ) {
                Text(
                    text = "${index + 1}",
                    color = palette.accent,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    modifier = Modifier.width(28.dp)
                )
                Text(step, color = palette.text, fontSize = 14.sp, lineHeight = 20.sp)
            }
        }
        Text("Если что-то не получается", color = palette.text, fontWeight = FontWeight.SemiBold)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(18.dp))
                .background(palette.card)
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            HintLine("«ПК недоступен» — проверьте, что «Рубеж» запущен и обе сети одинаковые.")
            HintLine("«ПК не принял токен» — сопряжение устарело или устройство отозвано; отсканируйте QR заново.")
            HintLine("«Код сопряжения истёк» — покажите QR-код на ПК заново.")
            HintLine("«Сертификат ПК изменился» — обмен прерван для безопасности, повторите сопряжение.")
            HintLine("«ПК не поддерживает полный снимок» — обновите «Рубеж» на ПК до версии с corr4.")
            HintLine("Демо-задачи на ПК не входят в полный снимок и не отправляются в дельте.")
        }
        MarsSecondaryButton(
            text = "Назад к синхронизации",
            onClick = { nav.popBackStack() },
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun HintLine(text: String) {
    val palette = LocalMarsPalette.current
    Text("• $text", color = palette.textMuted, fontSize = 13.sp, lineHeight = 18.sp)
}

/** Разбор расхождений: показываем обе версии и даём выбрать одну. */
@Composable
internal fun ConflictsScreen(vm: AppViewModel, nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val conflicts by vm.conflicts.collectAsState()
    val scope = rememberCoroutineScope()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        ScreenTitleRow("Расхождения с ПК", nav)
        if (conflicts.isEmpty()) {
            Text(
                "Расхождений нет. Они появляются, если одна и та же задача изменилась " +
                    "и на телефоне, и на ПК.",
                color = palette.textMuted,
                fontSize = 13.sp
            )
        } else {
            Text(
                "Выберите, какую версию оставить. Вторая будет отброшена.",
                color = palette.textMuted,
                fontSize = 13.sp
            )
        }
        conflicts.forEach { conflict ->
            ConflictCard(
                conflict = conflict,
                onKeepPhone = { scope.launch { vm.resolveConflict(conflict.id, keepLocal = true) } },
                onAcceptPc = { scope.launch { vm.resolveConflict(conflict.id, keepLocal = false) } }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflictItem,
    onKeepPhone: () -> Unit,
    onAcceptPc: () -> Unit
) {
    val palette = LocalMarsPalette.current
    val typeLabel = when (conflict.entityType) {
        SyncEntityType.PROJECT -> "Проект"
        SyncEntityType.TASK -> "Задача"
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.card)
            .padding(16.dp)
    ) {
        Text(typeLabel, color = StatusOverdue, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = conflictTitle(conflict),
            color = palette.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text("Обнаружено ${timestampLabel(conflict.createdAt)}", color = palette.textMuted, fontSize = 12.sp)
        Spacer(modifier = Modifier.height(12.dp))
        ConflictVersion(
            title = "Версия телефона",
            accent = StatusOpen,
            lines = payloadLines(conflict.localPayloadJson, conflict.localOp)
        )
        Spacer(modifier = Modifier.height(10.dp))
        ConflictVersion(
            title = "Версия ПК",
            accent = palette.highlight,
            lines = payloadLines(conflict.remotePayloadJson, conflict.remoteOp)
        )
        Spacer(modifier = Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            MarsPrimaryButton("Оставить телефон", onClick = onKeepPhone)
            MarsSecondaryButton("Принять ПК", onClick = onAcceptPc)
        }
    }
}

@Composable
private fun ConflictVersion(title: String, accent: Color, lines: List<Pair<String, String>>) {
    val palette = LocalMarsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(accent.copy(alpha = 0.10f))
            .padding(12.dp)
    ) {
        Text(title, color = accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold)
        Spacer(modifier = Modifier.height(6.dp))
        if (lines.isEmpty()) {
            Text("Нет данных", color = palette.textMuted, fontSize = 13.sp)
        }
        lines.forEach { (label, value) ->
            Row(modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp)) {
                Text(label, color = palette.textMuted, fontSize = 12.sp, modifier = Modifier.width(96.dp))
                Text(value, color = palette.text, fontSize = 12.sp)
            }
        }
    }
}

private fun conflictTitle(conflict: SyncConflictItem): String {
    val payload = conflict.localPayloadJson ?: conflict.remotePayloadJson ?: return "Без названия"
    val json = runCatching { JSONObject(payload) }.getOrNull() ?: return "Без названия"
    val title = json.optString("title").ifBlank { json.optString("name") }
    return title.ifBlank { "Без названия" }
}

/** Человекочитаемое описание версии из canonical-JSON операции. */
private fun payloadLines(payloadJson: String?, op: String): List<Pair<String, String>> {
    if (payloadJson.isNullOrBlank()) {
        return listOf("Действие" to opLabel(op))
    }
    val json = runCatching { JSONObject(payloadJson) }.getOrNull()
        ?: return listOf("Действие" to opLabel(op))
    if (json.optBoolean("deleted", false)) {
        return listOf("Действие" to "удалено")
    }
    val lines = mutableListOf<Pair<String, String>>()
    lines += "Действие" to opLabel(op)
    json.optString("title").takeIf { it.isNotBlank() }?.let { lines += "Заголовок" to it }
    json.optString("name").takeIf { it.isNotBlank() }?.let { lines += "Название" to it }
    json.optString("description").takeIf { it.isNotBlank() }?.let { lines += "Описание" to it }
    json.optString("status").takeIf { it.isNotBlank() }?.let {
        lines += "Статус" to if (it == "done") "Выполнено" else "Открыта"
    }
    json.optString("priority").takeIf { it.isNotBlank() }?.let {
        lines += "Приоритет" to when (it) {
            "low" -> "Низкий"
            "high" -> "Высокий"
            else -> "Обычный"
        }
    }
    if (!json.isNull("due_at")) {
        json.optString("due_at").takeIf { it.isNotBlank() }?.let { lines += "Срок" to it }
    }
    if (json.optBoolean("archived", false)) lines += "Архив" to "да"
    return lines
}

private fun opLabel(op: String): String = when (op) {
    "create" -> "создано"
    "delete" -> "удалено"
    else -> "изменено"
}