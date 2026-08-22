from pathlib import Path
import re

p = Path(r"F:\1\Cursor\Mars\Mars\app\src\main\java\com\mars\planner\screens\Screens.kt")
text = p.read_text(encoding="utf-8")
idx = text.find("\nprivate val fieldColors")
if idx < 0:
    raise SystemExit("anchor not found")
body = text[idx + 1 :]
body = body.replace(
    """modifier = if (reduce) Modifier else Modifier.animateItem(
                            fadeInSpec = tween(220),
                            fadeOutSpec = tween(160),
                            placementSpec = tween(220)
                        )""",
    "modifier = Modifier",
)
# also remove unused reduce in TasksScreen if present
body = body.replace("    val reduce = LocalReduceAnimations.current\n", "")

header = r'''package com.mars.planner.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController
import com.mars.planner.domain.model.EnhancementIdea
import com.mars.planner.domain.model.EnhancementStatus
import com.mars.planner.domain.model.MarsMood
import com.mars.planner.domain.model.MotivatorMode
import com.mars.planner.domain.model.TaskItem
import com.mars.planner.domain.model.TaskPriority
import com.mars.planner.domain.model.TaskStatus
import com.mars.planner.export.BackupCodec
import com.mars.planner.motivator.MarsMotivator
import com.mars.planner.reminder.nextReminderMillis
import com.mars.planner.sync.SyncResult
import com.mars.planner.ui.components.MarsEmptyState
import com.mars.planner.ui.components.MarsPrimaryButton
import com.mars.planner.ui.components.MarsReactionBanner
import com.mars.planner.ui.components.MarsSecondaryButton
import com.mars.planner.ui.components.NewTaskCtaBar
import com.mars.planner.ui.components.StatusDot
import com.mars.planner.ui.components.TaskCard
import com.mars.planner.ui.components.redactSyncSecrets
import com.mars.planner.ui.theme.MarsCardDark
import com.mars.planner.ui.theme.MarsMuted
import com.mars.planner.ui.theme.MarsOrange
import com.mars.planner.ui.theme.MarsPeach
import com.mars.planner.ui.theme.MarsWhite
import com.mars.planner.ui.theme.StatusDone
import com.mars.planner.ui.theme.StatusNotDone
import com.mars.planner.voice.VoiceInputHelper
import com.mars.planner.voice.VoiceResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

'''
# strip OptIn ExperimentalFoundationApi from TasksScreen
body = body.replace("@OptIn(ExperimentalFoundationApi::class)\n", "")
p.write_text(header + body, encoding="utf-8")
print("Screens.kt OK")

p2 = Path(r"F:\1\Cursor\Mars\Mars\app\src\main\java\com\mars\planner\screens\MarsApp.kt")
t2 = p2.read_text(encoding="utf-8")
t2 = t2.replace("import androidx.compose.foundation.lazy.animateItem\n", "")
t2 = t2.replace("import androidx.compose.animation.core.tween\n", "")
t2 = t2.replace("import androidx.compose.foundation.ExperimentalFoundationApi\n", "")
t2 = re.sub(
    r"modifier = if \(reduce\) Modifier else Modifier\.animateItem\(\s*fadeInSpec = tween\(220\),\s*fadeOutSpec = tween\(160\),\s*placementSpec = tween\(220\)\s*\)",
    "modifier = Modifier",
    t2,
)
t2 = t2.replace("                val reduce = LocalReduceAnimations.current\n", "")
t2 = t2.replace("@OptIn(ExperimentalFoundationApi::class)\n", "")
# keep LocalReduceAnimations import only if used - may be unused now
if "LocalReduceAnimations" not in t2.replace("import com.mars.planner.ui.components.LocalReduceAnimations\n", ""):
    t2 = t2.replace("import com.mars.planner.ui.components.LocalReduceAnimations\n", "")
p2.write_text(t2, encoding="utf-8")
print("MarsApp.kt OK")
