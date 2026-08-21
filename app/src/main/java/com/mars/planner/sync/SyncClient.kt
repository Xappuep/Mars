package com.mars.planner.sync

import com.mars.planner.export.BackupPayload
import com.mars.planner.export.BackupCodec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.util.concurrent.TimeUnit

data class SyncServerInfo(
    val ok: Boolean,
    val message: String,
    val lastBackupAt: Long? = null,
    val backupCount: Int = 0
)

data class SyncConflictInfo(
    val phoneExportedAt: Long,
    val pcExportedAt: Long?,
    val hasPcBackup: Boolean
)

sealed class SyncResult {
    data class Success(val message: String) : SyncResult()
    data class Conflict(val info: SyncConflictInfo) : SyncResult()
    data class Error(val message: String) : SyncResult()
}

class SyncClient(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()
) {
    fun checkConnection(host: String, port: Int, key: String): SyncServerInfo {
        return try {
            val request = Request.Builder()
                .url(baseUrl(host, port) + "/health")
                .header("X-Sync-Key", key)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val body = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    return SyncServerInfo(false, humanHttpError(response.code, body, "проверки подключения"))
                }
                val json = JSONObject(body)
                SyncServerInfo(
                    ok = json.optBoolean("ok", true),
                    message = json.optString("message", "Подключение успешно"),
                    lastBackupAt = json.optLong("last_backup_at").takeIf { it > 0 },
                    backupCount = json.optInt("backup_count", 0)
                )
            }
        } catch (e: Exception) {
            SyncServerInfo(false, "Не удалось подключиться: ${e.message ?: "ошибка сети"}")
        }
    }

    fun uploadBackup(host: String, port: Int, key: String, json: String): SyncResult {
        return try {
            val body = json.toRequestBody("application/json; charset=utf-8".toMediaType())
            val request = Request.Builder()
                .url(baseUrl(host, port) + "/backup")
                .header("X-Sync-Key", key)
                .post(body)
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                if (!response.isSuccessful) {
                    SyncResult.Error(humanHttpError(response.code, text, "отправки"))
                } else {
                    SyncResult.Success("Резервная копия отправлена на ПК")
                }
            }
        } catch (e: Exception) {
            SyncResult.Error("Отправка не удалась: ${e.message ?: "ошибка"}")
        }
    }

    fun downloadBackup(host: String, port: Int, key: String): Pair<SyncResult, String?> {
        return try {
            val request = Request.Builder()
                .url(baseUrl(host, port) + "/backup/latest")
                .header("X-Sync-Key", key)
                .get()
                .build()
            client.newCall(request).execute().use { response ->
                val text = response.body?.string().orEmpty()
                when (response.code) {
                    404 -> SyncResult.Error("На ПК пока нет резервных копий") to null
                    in 200..299 -> SyncResult.Success("Копия получена с ПК") to text
                    else -> SyncResult.Error(humanHttpError(response.code, text, "загрузки")) to null
                }
            }
        } catch (e: Exception) {
            SyncResult.Error("Загрузка не удалась: ${e.message ?: "ошибка"}") to null
        }
    }

    fun detectConflict(phoneJson: String, pcJson: String?): SyncConflictInfo {
        val phone = BackupCodec.fromJson(phoneJson)
        val pc: BackupPayload? = pcJson?.let { runCatching { BackupCodec.fromJson(it) }.getOrNull() }
        return SyncConflictInfo(
            phoneExportedAt = phone.exportedAt,
            pcExportedAt = pc?.exportedAt,
            hasPcBackup = pc != null
        )
    }

    private fun baseUrl(host: String, port: Int): String {
        val clean = host.trim().removePrefix("http://").removePrefix("https://").trimEnd('/')
        return "http://$clean:$port"
    }

    private fun humanHttpError(code: Int, body: String, action: String): String {
        val detail = runCatching {
            val json = JSONObject(body)
            json.optString("detail").ifBlank { json.optString("message") }
        }.getOrNull().orEmpty().ifBlank {
            body.trim().take(160)
        }
        return when (code) {
            401 -> if (detail.isNotBlank()) detail else "Неверный ключ сопряжения"
            else -> buildString {
                append("Ошибка $action: HTTP $code")
                if (detail.isNotBlank()) append(" — ").append(detail)
            }
        }
    }
}
