package com.mars.planner.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import com.mars.planner.sync.SyncUiPresentation
import com.mars.planner.sync.runWithBusyFlag
import com.mars.planner.ui.components.MarsDangerOutlineButton
import com.mars.planner.ui.components.MarsPrimaryButton
import com.mars.planner.ui.components.MarsSecondaryButton
import com.mars.planner.ui.theme.LocalMarsPalette
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusOpen
import com.mars.planner.ui.theme.StatusOverdue
import com.mars.planner.ui.theme.ThemeSceneScreen
import com.mars.planner.ui.theme.ThemeSceneShell
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Экран синхронизации с ПК-узлом «Рубеж». Секреты на экран не выводятся. */
@Composable
internal fun SyncScreen(vm: AppViewModel, nav: NavHostController) {
    val palette = LocalMarsPalette.current
    val settings by vm.settings.collectAsState()
    val pending by vm.pendingSyncCount.collectAsState()
    val conflicts by vm.conflicts.collectAsState()
    val pairing by vm.pairing.collectAsState()
    val scope = rememberCoroutineScope()

    var busy by remember { mutableStateOf(false) }
    var message by remember { mutableStateOf<String?>(null) }
    var messageOk by remember { mutableStateOf<Boolean?>(null) }
    var showManualQr by remember { mutableStateOf(false) }
    var confirmUnpair by remember { mutableStateOf(false) }
    var snapshotPreview by remember { mutableStateOf<SyncCoordinator.SnapshotPreview?>(null) }
    var snapshotStage by remember { mutableStateOf<String?>(null) }
    var confirmRestore by remember { mutableStateOf(false) }
    var showTechDetails by remember { mutableStateOf(false) }

    fun step(block: suspend () -> Unit) {
        if (busy) return
        scope.launch {
            runWithBusyFlag({ busy = it }, block)
        }
    }

    fun applyStepMessage(stepMessage: String, ok: Boolean) {
        message = stepMessage
        messageOk = ok
    }

    val scanLauncher = rememberLauncherForActivityResult(ScanContract()) { result ->
        val raw = result.contents
        if (raw.isNullOrBlank()) {
            applyStepMessage("Сканирование отменено", false)
            return@rememberLauncherForActivityResult
        }
        step {
            val step = vm.startPairing(raw)
            applyStepMessage(step.message, step.ok)
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
            applyStepMessage(
                "Без доступа к камере QR не отсканировать. Можно вставить код вручную.",
                false
            )
        }
    }

    LaunchedEffect(pairing.active) {
        while (pairing.active) {
            delay(3_000)
            if (!pairing.active) break
            val step = vm.pollPairStatus()
            if (!step.ok || step.message.contains("Сопряжение выполнено")) {
                applyStepMessage(step.message, step.ok)
            }
        }
    }

    LaunchedEffect(settings.syncPaired) {
        if (settings.syncPaired) {
            when (val state = vm.resolvePendingSnapshotAckState()) {
                is SyncCoordinator.PendingSnapshotAckState.Ready -> {
                    applyStepMessage(
                        SyncUiPresentation.presentError(SyncErrorCodes.SNAPSHOT_ACK_PENDING).title,
                        false
                    )
                }
                is SyncCoordinator.PendingSnapshotAckState.Corrupt -> {
                    applyStepMessage(
                        SyncUiPresentation.presentError(SyncErrorCodes.SNAPSHOT_STATE_CORRUPT).title,
                        false
                    )
                }
                is SyncCoordinator.PendingSnapshotAckState.RequiresFullSnapshot -> {
                    applyStepMessage(
                        SyncUiPresentation.presentError(SyncErrorCodes.SNAPSHOT_REQUIRED).title,
                        false
                    )
                }
                SyncCoordinator.PendingSnapshotAckState.None -> Unit
            }
        }
    }

    val connection = SyncUiPresentation.connectionStatus(
        syncPaired = settings.syncPaired,
        pairingActive = pairing.active,
        pairedDeviceName = settings.pairedDeviceName,
        pairedHost = if (pairing.active) pairing.host else settings.pairedHost,
        pairedPort = if (pairing.active) pairing.port else settings.pairedPort,
        lastSyncError = settings.lastSyncError,
        requiresPcPrimarySnapshot = settings.requiresPcPrimarySnapshot,
        pendingSnapshotAck = settings.pendingSnapshotAckId.isNotBlank()
    )
    val exchange = SyncUiPresentation.exchangeStatus(
        pendingCount = pending,
        conflictCount = conflicts.size,
        lastPushAt = settings.lastPushAt,
        lastPullAt = settings.lastPullAt,
        busy = busy,
        busyDetail = snapshotStage,
        lastActionOk = messageOk,
        lastActionMessage = message
    )
    val storedError = settings.lastSyncError.takeIf { it.isNotBlank() }?.let {
        SyncUiPresentation.presentError(it)
    }
    val connectionAccent = when (connection.kind) {
        SyncUiPresentation.ConnectionKind.LINKED -> StatusDone
        SyncUiPresentation.ConnectionKind.AWAITING_PC_CONFIRMATION -> StatusOpen
        SyncUiPresentation.ConnectionKind.NOT_LINKED,
        SyncUiPresentation.ConnectionKind.NEEDS_ATTENTION -> StatusOverdue
    }

    val syncBody: @Composable ColumnScope.() -> Unit = {
        ScreenTitleRow("Синхронизация с ПК", nav)

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Состояние связи", color = palette.textMuted, fontSize = 12.sp)
            Text(
                connection.title,
                color = connectionAccent,
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp
            )
            connection.deviceName?.let {
                Text(it, color = palette.text, fontSize = 14.sp)
            }
            if (connection.technicalEndpoint != null) {
                MarsSecondaryButton(
                    text = if (showTechDetails) "Скрыть технические сведения" else "Технические сведения",
                    onClick = { showTechDetails = !showTechDetails },
                    modifier = Modifier.fillMaxWidth()
                )
                if (showTechDetails) {
                    Text(
                        "Адрес ПК: ${connection.technicalEndpoint}",
                        color = palette.textMuted,
                        fontSize = 12.sp
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(22.dp))
                .background(palette.card)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text("Обмен данными", color = palette.textMuted, fontSize = 12.sp)
            Text(exchange.pendingLine, color = palette.text, fontSize = 14.sp)
            Text(
                exchange.conflictsLine,
                color = if (conflicts.isEmpty()) palette.text else StatusOverdue,
                fontSize = 14.sp
            )
            Text(exchange.lastPushLine, color = palette.textMuted, fontSize = 13.sp)
            Text(exchange.lastPullLine, color = palette.textMuted, fontSize = 13.sp)
            exchange.busyLine?.let {
                Text(it, color = StatusOpen, fontSize = 13.sp)
            }
            exchange.lastActionLine?.let { line ->
                Text(
                    line,
                    color = when (messageOk) {
                        true -> palette.highlight
                        false -> StatusOverdue
                        null -> palette.textMuted
                    },
                    fontSize = 13.sp
                )
            }
        }

        storedError?.let { err ->
            SyncErrorCard(err)
        }

        if (pairing.active) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(palette.accent.copy(alpha = 0.16f))
                    .padding(16.dp)
            ) {
                Text(
                    "Ждём подтверждения на ПК",
                    color = palette.text,
                    fontWeight = FontWeight.SemiBold
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Откройте «Рубеж» на ${pairing.host} и нажмите «Разрешить» для этого телефона. " +
                        "Проверка идёт автоматически.",
                    color = palette.textMuted,
                    fontSize = 13.sp
                )
                Spacer(modifier = Modifier.height(10.dp))
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    MarsSecondaryButton(
                        text = "Проверить сейчас",
                        onClick = {
                            step {
                                val step = vm.pollPairStatus()
                                applyStepMessage(step.message, step.ok)
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    )
                    MarsSecondaryButton(
                        text = "Отменить заявку",
                        onClick = { vm.cancelPairing() },
                        modifier = Modifier.fillMaxWidth()
                    )
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
                SyncErrorCard(SyncUiPresentation.presentError(SyncErrorCodes.SNAPSHOT_REQUIRED))
            }
            MarsPrimaryButton(
                text = SyncUiPresentation.pushButtonLabel(pending),
                onClick = {
                    step {
                        val step = vm.pushChanges()
                        applyStepMessage(step.message, step.ok)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && !deltaLocked
            )
            MarsSecondaryButton(
                text = "Получить изменения с ПК",
                onClick = {
                    step {
                        val step = vm.pullChanges()
                        applyStepMessage(step.message, step.ok)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy && !deltaLocked
            )
            MarsSecondaryButton(
                text = "Проверить связь",
                onClick = {
                    step {
                        val step = vm.checkSyncServer()
                        applyStepMessage(step.message, step.ok)
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                enabled = !busy
            )

            Spacer(modifier = Modifier.height(8.dp))
            SyncSectionHeading("Полная копия и восстановление")
            Text(
                "Замена данных телефона полной копией с ПК и возврат из резервной копии.",
                color = palette.textMuted,
                fontSize = 12.sp,
                lineHeight = 16.sp
            )
            Spacer(modifier = Modifier.height(4.dp))
            MarsPrimaryButton(
                text = "Получить полную копию с ПК",
                onClick = {
                    step {
                        snapshotStage = "Получение сведений…"
                        when (val prep = vm.preparePcPrimarySnapshot()) {
                            is SyncCallResult.Failure -> {
                                val err = SyncUiPresentation.presentError(prep.code)
                                applyStepMessage("${err.title}. ${err.action}", false)
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
                            applyStepMessage(step.message, step.ok)
                        }
                    },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = !busy
                )
            }
        }

        if (conflicts.isNotEmpty()) {
            MarsSecondaryButton(
                text = "Разобрать расхождения (${conflicts.size})",
                onClick = { nav.navigate(Routes.Conflicts) },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(8.dp))
        SyncSectionHeading("Помощь")
        MarsSecondaryButton(
            text = "Инструкция",
            onClick = { nav.navigate(Routes.SyncGuide) },
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(8.dp))
        SyncSectionHeading("Сопряжение")
        if (settings.syncPaired) {
            MarsDangerOutlineButton(
                text = "Разорвать сопряжение",
                onClick = { confirmUnpair = true },
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(modifier = Modifier.height(6.dp))
        }
        Text(
            "Обмен идёт напрямую по домашней Wi-Fi-сети и только с сопряжённым ПК. " +
                "Ключ доступа хранится в защищённом хранилище телефона и на экране не показывается.",
            color = palette.textMuted,
            fontSize = 12.sp,
            lineHeight = 16.sp
        )
        Spacer(modifier = Modifier.height(24.dp))
    }

    ThemeSceneShell(
        screen = ThemeSceneScreen.Sync,
        fallback = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = syncBody
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = syncBody
            )
        }
    )

    if (showManualQr) {
        ManualQrDialog(
            onDismiss = { showManualQr = false },
            onSubmit = { raw ->
                showManualQr = false
                step {
                    val step = vm.startPairing(raw)
                    applyStepMessage(step.message, step.ok)
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
                        applyStepMessage("Сопряжение разорвано", true)
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
                            applyStepMessage(result.message, result.ok)
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
                        applyStepMessage(step.message, step.ok)
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
private fun SyncSectionHeading(text: String) {
    val palette = LocalMarsPalette.current
    Text(
        text = text,
        color = palette.textMuted,
        fontSize = 12.sp,
        fontWeight = FontWeight.SemiBold
    )
}

@Composable
private fun SyncErrorCard(err: SyncUiPresentation.ErrorPresentation) {
    val palette = LocalMarsPalette.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(StatusOverdue.copy(alpha = 0.12f))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        Text(err.categoryLabel, color = StatusOverdue, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
        Text(err.title, color = StatusOverdue, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
        Text(err.explanation, color = palette.text, fontSize = 13.sp)
        Text(err.action, color = palette.textMuted, fontSize = 12.sp)
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
        "Кнопки «Отправить изменения» и «Получить изменения с ПК» — только для последующих изменений, " +
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
            HintLine("«ПК не принял доступ» — сопряжение устарело или устройство отозвано; отсканируйте QR заново.")
            HintLine("«Код сопряжения истёк» — покажите QR-код на ПК заново.")
            HintLine("«Сертификат ПК изменился» — обмен прерван для безопасности, повторите сопряжение.")
            HintLine("«ПК не умеет отдавать полный снимок» — обновите «Рубеж» на ПК.")
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
    val conflicts by vm.conflicts.collectAsState()
    val scope = rememberCoroutineScope()
    var pendingConfirm by remember { mutableStateOf<ConflictResolveConfirm?>(null) }

    val body: @Composable ColumnScope.() -> Unit = {
        val palette = LocalMarsPalette.current
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
                "Сравните версии и выберите, какую оставить. Вторая будет отброшена.",
                color = palette.textMuted,
                fontSize = 13.sp
            )
        }
        conflicts.forEach { conflict ->
            ConflictCard(
                conflict = conflict,
                onKeepPhone = {
                    pendingConfirm = ConflictResolveConfirm(
                        conflictId = conflict.id,
                        keepLocal = true,
                        title = SyncUiPresentation.recordTitle(
                            conflict.localPayloadJson,
                            conflict.remotePayloadJson
                        )
                    )
                },
                onAcceptPc = {
                    pendingConfirm = ConflictResolveConfirm(
                        conflictId = conflict.id,
                        keepLocal = false,
                        title = SyncUiPresentation.recordTitle(
                            conflict.localPayloadJson,
                            conflict.remotePayloadJson
                        )
                    )
                }
            )
        }
        Spacer(modifier = Modifier.height(24.dp))
    }

    ThemeSceneShell(
        screen = ThemeSceneScreen.Sync,
        fallback = {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = body
            )
        },
        content = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
                content = body
            )
        }
    )

    pendingConfirm?.let { confirm ->
        val palette = LocalMarsPalette.current
        AlertDialog(
            onDismissRequest = { pendingConfirm = null },
            containerColor = palette.backgroundElevated,
            title = {
                Text(
                    if (confirm.keepLocal) "Оставить версию телефона?"
                    else "Принять версию ПК?"
                )
            },
            text = {
                Text(
                    if (confirm.keepLocal) {
                        "Запись «${confirm.title}» останется в версии с телефона. Версия ПК будет отброшена."
                    } else {
                        "Запись «${confirm.title}» будет заменена версией с ПК. Версия телефона будет отброшена."
                    }
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val c = confirm
                    pendingConfirm = null
                    scope.launch { vm.resolveConflict(c.conflictId, keepLocal = c.keepLocal) }
                }) {
                    Text(if (confirm.keepLocal) "Оставить версию телефона" else "Принять версию ПК")
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingConfirm = null }) { Text("Отмена") }
            }
        )
    }
}

private data class ConflictResolveConfirm(
    val conflictId: Long,
    val keepLocal: Boolean,
    val title: String
)

@Composable
private fun ConflictCard(
    conflict: SyncConflictItem,
    onKeepPhone: () -> Unit,
    onAcceptPc: () -> Unit
) {
    val palette = LocalMarsPalette.current
    val compare = SyncUiPresentation.compareConflict(
        entityTypeTask = conflict.entityType == SyncEntityType.TASK,
        localPayloadJson = conflict.localPayloadJson,
        remotePayloadJson = conflict.remotePayloadJson,
        localOp = conflict.localOp,
        remoteOp = conflict.remoteOp
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(22.dp))
            .background(palette.card)
            .padding(16.dp)
    ) {
        Text(
            compare.entityTypeLabel,
            color = StatusOverdue,
            fontSize = 12.sp,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = compare.recordTitle,
            color = palette.text,
            fontWeight = FontWeight.SemiBold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            "Обнаружено ${timestampLabel(conflict.createdAt)}",
            color = palette.textMuted,
            fontSize = 12.sp
        )
        compare.scenarioNote?.let {
            Spacer(modifier = Modifier.height(8.dp))
            Text(it, color = StatusOpen, fontSize = 13.sp)
        }
        Spacer(modifier = Modifier.height(12.dp))
        ConflictVersionBlock(
            title = "Версия телефона",
            accent = StatusOpen,
            deleted = compare.phoneDeleted,
            opLabel = compare.phoneOpLabel,
            unreadable = compare.phoneUnreadable,
            rows = compare.differingRows.map { it.label to it.phoneValue }
        )
        Spacer(modifier = Modifier.height(10.dp))
        ConflictVersionBlock(
            title = "Версия ПК",
            accent = palette.highlight,
            deleted = compare.pcDeleted,
            opLabel = compare.pcOpLabel,
            unreadable = compare.pcUnreadable,
            rows = compare.differingRows.map { it.label to it.pcValue }
        )
        if (compare.differingRows.isEmpty() &&
            !compare.phoneDeleted &&
            !compare.pcDeleted &&
            !compare.phoneUnreadable &&
            !compare.pcUnreadable
        ) {
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "Различающихся полей не видно — выберите сторону по действию или получите полную копию с ПК.",
                color = palette.textMuted,
                fontSize = 12.sp
            )
        }
        Spacer(modifier = Modifier.height(14.dp))
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            MarsPrimaryButton(
                text = "Оставить версию телефона",
                onClick = onKeepPhone,
                modifier = Modifier.fillMaxWidth()
            )
            MarsSecondaryButton(
                text = "Принять версию ПК",
                onClick = onAcceptPc,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

@Composable
private fun ConflictVersionBlock(
    title: String,
    accent: Color,
    deleted: Boolean,
    opLabel: String,
    unreadable: Boolean,
    rows: List<Pair<String, String>>
) {
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
        when {
            deleted -> Text("Удалено", color = palette.text, fontSize = 13.sp, fontWeight = FontWeight.Medium)
            unreadable -> Text("Версию не удалось прочитать", color = StatusOverdue, fontSize = 13.sp)
            else -> {
                Text("Действие: $opLabel", color = palette.textMuted, fontSize = 12.sp)
                Spacer(modifier = Modifier.height(4.dp))
                if (rows.isEmpty()) {
                    Text("Совпадает по показанным полям", color = palette.textMuted, fontSize = 12.sp)
                } else {
                    rows.forEach { (label, value) ->
                        Column(modifier = Modifier.fillMaxWidth().padding(vertical = 3.dp)) {
                            Text(label, color = palette.textMuted, fontSize = 12.sp)
                            Text(
                                value,
                                color = palette.text,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }
        }
    }
}
