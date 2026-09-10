package com.mars.planner.sync

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.ConnectionSpec
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.io.InputStream
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.net.URLEncoder
import java.security.cert.CertificateException
import java.util.concurrent.TimeUnit
import javax.net.ssl.SSLException
import javax.net.ssl.SSLPeerUnverifiedException

/** Коды ошибок протокола v1 плюс локальные коды клиента. */
object SyncErrorCodes {
    const val UNAUTHORIZED = "unauthorized"
    const val FORBIDDEN = "forbidden"
    const val DEVICE_REVOKED = "device_revoked"
    const val BAD_PAIRING_TOKEN = "bad_pairing_token"
    const val PAIRING_TOKEN_EXPIRED = "pairing_token_expired"
    const val PAIRING_TOKEN_USED = "pairing_token_used"
    const val PAIRING_PENDING = "pairing_pending"
    const val PAIRING_DENIED = "pairing_denied"
    const val UNSUPPORTED_PROTOCOL = "unsupported_protocol"
    const val INVALID_PAYLOAD = "invalid_payload"
    const val UNRESOLVED_DEPENDENCY = "unresolved_dependency"
    const val PACKAGE_REJECTED = "package_rejected"
    const val SENDER_MISMATCH = "sender_mismatch"
    const val REQUEST_TOO_LARGE = "request_too_large"
    const val RATE_LIMITED = "rate_limited"
    const val DEMO_NOT_ALLOWED = "demo_not_allowed"
    const val SYNC_DISABLED = "sync_disabled"
    const val NOT_FOUND = "not_found"
    const val CONFLICT_STATE = "conflict_state"
    const val INTERNAL_ERROR = "internal_error"
    const val SNAPSHOT_UNSUPPORTED = "snapshot_unsupported"
    const val SNAPSHOT_CORRUPT = "snapshot_corrupt"
    /** Данные снимка применены локально, но ACK на ПК не завершён. */
    const val SNAPSHOT_ACK_PENDING = "snapshot_ack_pending"
    /** После восстановления копии нужен новый полный снимок с ПК. */
    const val SNAPSHOT_REQUIRED = "snapshot_required"
    /** Локальные маркеры снимка повреждены (несколько pending и т.п.). */
    const val SNAPSHOT_STATE_CORRUPT = "snapshot_state_corrupt"

    /** Локальные коды: на ПК не отправляются, нужны только для UI. */
    const val NOT_PAIRED = "local_not_paired"
    const val NOT_CONFIGURED = "local_not_configured"
    const val BAD_QR = "local_bad_qr"
    const val TLS_PIN_MISMATCH = "local_tls_pin_mismatch"
    const val TLS_ERROR = "local_tls_error"
    const val NETWORK_UNAVAILABLE = "local_network_unavailable"
    const val TIMEOUT = "local_timeout"
    const val RESPONSE_TOO_LARGE = "local_response_too_large"

    fun messageRu(code: String): String = when (code) {
        UNAUTHORIZED -> "ПК не принял токен устройства"
        FORBIDDEN -> "Доступ запрещён"
        DEVICE_REVOKED -> "Устройство отозвано на ПК, нужно новое сопряжение"
        BAD_PAIRING_TOKEN -> "Неверный код сопряжения"
        PAIRING_TOKEN_EXPIRED -> "Код сопряжения истёк, покажите QR заново"
        PAIRING_TOKEN_USED -> "Код сопряжения уже использован"
        PAIRING_PENDING -> "Ожидается подтверждение на ПК"
        PAIRING_DENIED -> "Сопряжение отклонено на ПК"
        UNSUPPORTED_PROTOCOL -> "Версии протокола не совпадают, обновите приложение или ПК"
        INVALID_PAYLOAD -> "ПК не смог разобрать данные"
        UNRESOLVED_DEPENDENCY -> "Ссылка на отсутствующий проект — снимок или пакет не принят"
        PACKAGE_REJECTED -> "Пакет отклонён, будет собран новый"
        SENDER_MISMATCH -> "Пакет отправлен от другого устройства"
        REQUEST_TOO_LARGE -> "Слишком большой пакет данных"
        RATE_LIMITED -> "Слишком много попыток, подождите минуту"
        DEMO_NOT_ALLOWED -> "На ПК открыта демо-база"
        SYNC_DISABLED -> "Синхронизация выключена на ПК"
        NOT_FOUND -> "Ресурс не найден"
        CONFLICT_STATE -> "Состояние на ПК изменилось"
        SNAPSHOT_UNSUPPORTED -> "ПК не поддерживает полный снимок — обновите «Рубеж»"
        SNAPSHOT_CORRUPT -> "Снимок ПК повреждён или неполный"
        SNAPSHOT_ACK_PENDING -> "Данные получены, но подтверждение на ПК не завершено — повторите подтверждение"
        SNAPSHOT_REQUIRED ->
            "Резервная копия телефона восстановлена. Для продолжения синхронизации снова получите полную копию с ПК"
        SNAPSHOT_STATE_CORRUPT -> "Состояние подтверждения снимка повреждено — получите полную копию с ПК заново"
        NOT_PAIRED -> "Телефон не сопряжён с ПК"
        NOT_CONFIGURED -> "Нет данных подключения, отсканируйте QR"
        BAD_QR -> "QR-код не подходит для синхронизации «Рубеж»"
        TLS_PIN_MISMATCH ->
            "Сертификат ПК изменился. Отсканируйте новый QR и выполните сопряжение заново"
        TLS_ERROR -> "Не удалось установить защищённое соединение с ПК"
        NETWORK_UNAVAILABLE -> "ПК недоступен, проверьте общую Wi-Fi-сеть и что «Рубеж» запущен"
        TIMEOUT -> "ПК не ответил вовремя"
        RESPONSE_TOO_LARGE -> "Ответ ПК превысил допустимый размер"
        INTERNAL_ERROR -> "Внутренняя ошибка клиента синхронизации"
        else -> "Ошибка синхронизации"
    }
}

/** Адрес и закреплённый сертификат ПК. */
data class SyncEndpoint(
    val host: String,
    val port: Int = DEFAULT_PORT,
    val certSha256: String,
    /** Только для тестов; рабочий транспорт всегда HTTPS. */
    internal val scheme: String = "https"
) {
    val isPinned: Boolean get() = scheme == "https"

    val baseUrl: String get() = "$scheme://$host:$port"

    fun isUsable(): Boolean =
        host.isNotBlank() && port in 1..65535 &&
            (!isPinned || TlsFingerprint.isValidFingerprint(certSha256))

    companion object {
        const val DEFAULT_PORT = 8765
    }
}

/** Полезная нагрузка QR сопряжения. */
data class PairingQr(
    val protocolVersion: Int,
    val host: String,
    val port: Int,
    val token: String,
    val certSha256: String
) {
    fun endpoint(): SyncEndpoint =
        SyncEndpoint(host = host, port = port, certSha256 = TlsFingerprint.normalizeHex(certSha256))

    companion object {
        fun parse(raw: String?): SyncCallResult<PairingQr> {
            val text = raw?.trim().orEmpty()
            if (text.isEmpty()) return SyncCallResult.Failure(SyncErrorCodes.BAD_QR)
            val obj = runCatching { JSONObject(text) }.getOrNull()
                ?: return SyncCallResult.Failure(SyncErrorCodes.BAD_QR)
            val version = obj.optInt("protocol_version", -1)
            if (version != VersionHash.PROTOCOL_VERSION) {
                return SyncCallResult.Failure(SyncErrorCodes.UNSUPPORTED_PROTOCOL)
            }
            val host = obj.optString("host").trim()
            val port = obj.optInt("port", SyncEndpoint.DEFAULT_PORT)
            val token = obj.optString("token").trim()
            val fingerprint = TlsFingerprint.normalizeHex(obj.optString("cert_sha256"))
            if (host.isEmpty() || token.isEmpty() || port !in 1..65535) {
                return SyncCallResult.Failure(SyncErrorCodes.BAD_QR)
            }
            if (!TlsFingerprint.isValidFingerprint(fingerprint)) {
                return SyncCallResult.Failure(SyncErrorCodes.BAD_QR)
            }
            return SyncCallResult.Ok(
                PairingQr(
                    protocolVersion = version,
                    host = host,
                    port = port,
                    token = token,
                    certSha256 = fingerprint
                )
            )
        }
    }
}

sealed class SyncCallResult<out T> {
    data class Ok<T>(val value: T) : SyncCallResult<T>()

    data class Failure(
        val code: String,
        val httpStatus: Int = 0,
        val detail: String? = null
    ) : SyncCallResult<Nothing>() {
        val messageRu: String get() = SyncErrorCodes.messageRu(code)
    }

    val isOk: Boolean get() = this is Ok

    fun valueOrNull(): T? = (this as? Ok)?.value

    fun failureOrNull(): Failure? = this as? Failure
}

data class HealthInfo(
    val protocolVersion: Int,
    val deviceId: String?,
    val features: Set<String> = emptySet()
) {
    val supportsFullSnapshot: Boolean get() = FEATURE_FULL_SNAPSHOT in features
}

data class PairRequestInfo(val requestId: String, val status: String)

data class PairStatusInfo(
    val status: String,
    val deviceId: String? = null,
    val deviceToken: String? = null
) {
    val isApproved: Boolean
        get() = status == "approved" && !deviceId.isNullOrBlank() && !deviceToken.isNullOrBlank()
}

data class PushAck(val packageId: String)

/**
 * HTTPS-клиент протокола «rubezh-sync» v1 с пиннингом сертификата ПК.
 * Токен устройства подставляется из [SecureTokenStore] и не логируется.
 */
class RubezhSyncClient(
    private val tokenStore: SecureTokenStore,
    private val endpointProvider: () -> SyncEndpoint?,
    private val clientFactory: (SyncEndpoint) -> OkHttpClient = ::defaultClientFor
) {

    private val lock = Any()
    private var cachedEndpoint: SyncEndpoint? = null
    private var cachedClient: OkHttpClient? = null

    suspend fun health(endpoint: SyncEndpoint? = null): SyncCallResult<HealthInfo> =
        authorizedCall(endpoint) { target, token ->
            val request = Request.Builder()
                .url(target.baseUrl + PATH_HEALTH)
                .header("Authorization", "Bearer $token")
                .get()
                .build()
            execute(target, request) { json ->
                val features = mutableSetOf<String>()
                val arr = json.optJSONArray("features")
                if (arr != null) {
                    for (i in 0 until arr.length()) features += arr.optString(i)
                }
                SyncCallResult.Ok(
                    HealthInfo(
                        protocolVersion = json.optInt("protocol_version", VersionHash.PROTOCOL_VERSION),
                        deviceId = json.optStringOrNull("device_id"),
                        features = features
                    )
                )
            }
        }

    /** Запрос сопряжения по одноразовому токену из QR (без Bearer). */
    suspend fun pairRequest(
        endpoint: SyncEndpoint,
        oneTimeToken: String,
        deviceName: String
    ): SyncCallResult<PairRequestInfo> = withContext(Dispatchers.IO) {
        if (!endpoint.isUsable()) {
            return@withContext SyncCallResult.Failure(SyncErrorCodes.NOT_CONFIGURED)
        }
        val body = JSONObject().apply {
            put("token", oneTimeToken)
            put("device_name", deviceName)
        }
        val request = requestWithJsonBody(endpoint.baseUrl + PATH_PAIR_REQUEST, body, token = null)
            ?: return@withContext SyncCallResult.Failure(SyncErrorCodes.REQUEST_TOO_LARGE)
        execute(endpoint, request) { json ->
            val requestId = json.optStringOrNull("request_id")
                ?: return@execute SyncCallResult.Failure(SyncErrorCodes.INVALID_PAYLOAD)
            SyncCallResult.Ok(
                PairRequestInfo(
                    requestId = requestId,
                    status = json.optString("status", "pending_confirm")
                )
            )
        }
    }

    /** Опрос подтверждения на ПК (без Bearer). */
    suspend fun pairStatus(
        endpoint: SyncEndpoint,
        requestId: String
    ): SyncCallResult<PairStatusInfo> = withContext(Dispatchers.IO) {
        if (!endpoint.isUsable()) {
            return@withContext SyncCallResult.Failure(SyncErrorCodes.NOT_CONFIGURED)
        }
        val query = URLEncoder.encode(requestId, "UTF-8")
        val request = Request.Builder()
            .url(endpoint.baseUrl + PATH_PAIR_STATUS + "?request_id=" + query)
            .get()
            .build()
        execute(endpoint, request) { json ->
            SyncCallResult.Ok(
                PairStatusInfo(
                    status = json.optString("status", "waiting"),
                    deviceId = json.optStringOrNull("device_id"),
                    deviceToken = json.optStringOrNull("device_token")
                )
            )
        }
    }

    /** Пакеты, опубликованные ПК для этого устройства. */
    suspend fun pullPackages(endpoint: SyncEndpoint? = null): SyncCallResult<List<SyncPackage>> =
        authorizedCall(endpoint) { target, token ->
            val body = JSONObject().apply {
                put("protocol_version", VersionHash.PROTOCOL_VERSION)
            }
            val request = requestWithJsonBody(target.baseUrl + PATH_PACKAGES_PULL, body, token)
                ?: return@authorizedCall SyncCallResult.Failure(SyncErrorCodes.REQUEST_TOO_LARGE)
            execute(target, request) { json ->
                val array = json.optJSONArray("packages")
                val packages = ArrayList<SyncPackage>(array?.length() ?: 0)
                try {
                    for (i in 0 until (array?.length() ?: 0)) {
                        val item = array?.optJSONObject(i)
                            ?: throw SyncEngineException(
                                SyncErrorCodes.INVALID_PAYLOAD,
                                "Пакет должен быть объектом"
                            )
                        packages += SyncPackage.fromJson(item)
                    }
                } catch (e: SyncEngineException) {
                    return@execute SyncCallResult.Failure(e.code, detail = e.message)
                }
                SyncCallResult.Ok(packages)
            }
        }

    /** Отправка собранного пакета на ПК. */
    suspend fun pushPackage(pkg: SyncPackage, endpoint: SyncEndpoint? = null): SyncCallResult<PushAck> =
        authorizedCall(endpoint) { target, token ->
            val body = JSONObject().apply {
                put("protocol_version", VersionHash.PROTOCOL_VERSION)
                put("package", pkg.toJson())
            }
            val request = requestWithJsonBody(target.baseUrl + PATH_PACKAGES_PUSH, body, token)
                ?: return@authorizedCall SyncCallResult.Failure(SyncErrorCodes.REQUEST_TOO_LARGE)
            execute(target, request) { json ->
                SyncCallResult.Ok(PushAck(json.optString("package_id", pkg.packageId)))
            }
        }

    /** Подтверждение применения пакета ПК. */
    suspend fun ackPackage(packageId: String, endpoint: SyncEndpoint? = null): SyncCallResult<Unit> =
        authorizedCall(endpoint) { target, token ->
            val body = JSONObject().apply { put("package_id", packageId) }
            val request = requestWithJsonBody(target.baseUrl + PATH_PACKAGES_ACK, body, token)
                ?: return@authorizedCall SyncCallResult.Failure(SyncErrorCodes.REQUEST_TOO_LARGE)
            execute(target, request) { SyncCallResult.Ok(Unit) }
        }

    suspend fun healthFeatures(endpoint: SyncEndpoint? = null): SyncCallResult<Set<String>> =
        authorizedCall(endpoint) { target, token ->
            val request = Request.Builder()
                .url(target.baseUrl + PATH_HEALTH)
                .get()
                .header("Authorization", "Bearer $token")
                .build()
            execute(target, request) { json ->
                val features = mutableSetOf<String>()
                val arr = json.optJSONArray("features")
                if (arr != null) {
                    for (i in 0 until arr.length()) features += arr.optString(i)
                }
                SyncCallResult.Ok(features)
            }
        }

    suspend fun snapshotInfo(endpoint: SyncEndpoint? = null): SyncCallResult<SnapshotInfo> =
        authorizedCall(endpoint) { target, token ->
            val request = requestWithJsonBody(target.baseUrl + PATH_SNAPSHOT_INFO, JSONObject(), token)
                ?: return@authorizedCall SyncCallResult.Failure(SyncErrorCodes.REQUEST_TOO_LARGE)
            execute(target, request) { json ->
                when {
                    json.optString("error") == SyncErrorCodes.NOT_FOUND ->
                        SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_UNSUPPORTED)
                    json.optString("error") == SyncErrorCodes.UNRESOLVED_DEPENDENCY ->
                        SyncCallResult.Failure(SyncErrorCodes.UNRESOLVED_DEPENDENCY)
                    !json.has("snapshot_id") ->
                        SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_UNSUPPORTED)
                    else -> try {
                        val info = FullSnapshotCodec.parseInfo(json)
                        FullSnapshotValidator.validateInfo(info)
                        SyncCallResult.Ok(info)
                    } catch (e: SyncEngineException) {
                        SyncCallResult.Failure(e.code, detail = e.message)
                    } catch (_: org.json.JSONException) {
                        SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_CORRUPT)
                    } catch (_: Exception) {
                        SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_CORRUPT)
                    }
                }
            }.mapInvalidPayloadToSnapshotCorrupt()
        }

    suspend fun snapshotChunk(
        snapshotId: String,
        chunkIndex: Int,
        endpoint: SyncEndpoint? = null
    ): SyncCallResult<SnapshotChunkResponse> =
        authorizedCall(endpoint) { target, token ->
            val body = JSONObject()
                .put("snapshot_id", snapshotId)
                .put("chunk_index", chunkIndex)
            val request = requestWithJsonBody(target.baseUrl + PATH_SNAPSHOT_CHUNK, body, token)
                ?: return@authorizedCall SyncCallResult.Failure(SyncErrorCodes.REQUEST_TOO_LARGE)
            execute(target, request) { json ->
                try {
                    SyncCallResult.Ok(FullSnapshotCodec.parseChunkResponse(json))
                } catch (e: SyncEngineException) {
                    SyncCallResult.Failure(e.code, detail = e.message)
                } catch (_: org.json.JSONException) {
                    SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_CORRUPT)
                } catch (_: Exception) {
                    SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_CORRUPT)
                }
            }.mapInvalidPayloadToSnapshotCorrupt()
        }

    suspend fun snapshotAck(snapshotId: String, endpoint: SyncEndpoint? = null): SyncCallResult<SnapshotAckResponse> =
        authorizedCall(endpoint) { target, token ->
            val body = JSONObject().put("snapshot_id", snapshotId)
            val request = requestWithJsonBody(target.baseUrl + PATH_SNAPSHOT_ACK, body, token)
                ?: return@authorizedCall SyncCallResult.Failure(SyncErrorCodes.REQUEST_TOO_LARGE)
            execute(target, request) { json ->
                try {
                    SyncCallResult.Ok(FullSnapshotCodec.parseAck(json, snapshotId))
                } catch (e: SyncEngineException) {
                    if (e.code == SyncErrorCodes.SNAPSHOT_CORRUPT ||
                        e.code == SyncErrorCodes.INVALID_PAYLOAD
                    ) {
                        SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_CORRUPT, detail = e.message)
                    } else {
                        SyncCallResult.Failure(e.code, detail = e.message)
                    }
                } catch (_: org.json.JSONException) {
                    SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_CORRUPT)
                } catch (_: Exception) {
                    SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_CORRUPT)
                }
            }.mapInvalidPayloadToSnapshotCorrupt()
        }

    private fun <T> SyncCallResult<T>.mapInvalidPayloadToSnapshotCorrupt(): SyncCallResult<T> =
        if (this is SyncCallResult.Failure && code == SyncErrorCodes.INVALID_PAYLOAD) {
            SyncCallResult.Failure(SyncErrorCodes.SNAPSHOT_CORRUPT, httpStatus, detail)
        } else {
            this
        }

    private suspend fun <T> authorizedCall(
        override: SyncEndpoint?,
        block: (SyncEndpoint, String) -> SyncCallResult<T>
    ): SyncCallResult<T> = withContext(Dispatchers.IO) {
        val target = override ?: endpointProvider()
        if (target == null || !target.isUsable()) {
            return@withContext SyncCallResult.Failure(SyncErrorCodes.NOT_CONFIGURED)
        }
        val token = tokenStore.deviceToken()
        if (token.isNullOrBlank()) {
            return@withContext SyncCallResult.Failure(SyncErrorCodes.NOT_PAIRED)
        }
        block(target, token)
    }

    private fun requestWithJsonBody(url: String, body: JSONObject, token: String?): Request? {
        val raw = body.toString().toByteArray(Charsets.UTF_8)
        if (raw.size > MAX_BODY_BYTES) return null
        val builder = Request.Builder()
            .url(url)
            .post(raw.toRequestBody(JSON_MEDIA_TYPE))
        if (!token.isNullOrBlank()) builder.header("Authorization", "Bearer $token")
        return builder.build()
    }

    private fun <T> execute(
        endpoint: SyncEndpoint,
        request: Request,
        onSuccess: (JSONObject) -> SyncCallResult<T>
    ): SyncCallResult<T> = try {
        clientFor(endpoint).newCall(request).execute().use { response ->
            val bodyText = readLimitedBody(response)
                ?: return SyncCallResult.Failure(
                    SyncErrorCodes.RESPONSE_TOO_LARGE,
                    response.code
                )
            val json = runCatching { JSONObject(bodyText.ifBlank { "{}" }) }.getOrNull()
                ?: return SyncCallResult.Failure(SyncErrorCodes.INVALID_PAYLOAD, response.code)
            if (!response.isSuccessful || !json.optBoolean("ok", response.isSuccessful)) {
                return SyncCallResult.Failure(
                    code = errorCodeOf(json, response.code),
                    httpStatus = response.code,
                    detail = json.optStringOrNull("status")
                )
            }
            onSuccess(json)
        }
    } catch (e: Throwable) {
        // HTTP-ответ уже обработан выше: Wi‑Fi-текст только при отсутствии TLS/HTTP.
        SyncCallResult.Failure(classifyTransportError(e))
    }

    private fun errorCodeOf(json: JSONObject, httpStatus: Int): String {
        json.optStringOrNull("error")?.let { return it }
        return when (httpStatus) {
            401 -> SyncErrorCodes.UNAUTHORIZED
            403 -> SyncErrorCodes.FORBIDDEN
            404 -> SyncErrorCodes.NOT_FOUND
            409 -> SyncErrorCodes.PACKAGE_REJECTED
            413 -> SyncErrorCodes.REQUEST_TOO_LARGE
            429 -> SyncErrorCodes.RATE_LIMITED
            503 -> SyncErrorCodes.SYNC_DISABLED
            else -> SyncErrorCodes.INTERNAL_ERROR
        }
    }

    /** null, если ответ превысил 1 MiB. */
    private fun readLimitedBody(response: Response): String? {
        val body = response.body ?: return ""
        body.contentLength().let { declared ->
            if (declared > MAX_BODY_BYTES) return null
        }
        val bytes = body.byteStream().use { readAtMost(it, MAX_BODY_BYTES + 1) }
        if (bytes.size > MAX_BODY_BYTES) return null
        return String(bytes, Charsets.UTF_8)
    }

    private fun clientFor(endpoint: SyncEndpoint): OkHttpClient = synchronized(lock) {
        val cached = cachedClient
        if (cached != null && cachedEndpoint == endpoint) return cached
        val created = clientFactory(endpoint)
        cachedEndpoint = endpoint
        cachedClient = created
        created
    }

    companion object {
        const val PROTOCOL_NAME = "rubezh-sync"
        const val MAX_BODY_BYTES = 1_048_576

        const val PATH_HEALTH = "/v1/health"
        const val PATH_PAIR_REQUEST = "/v1/pair/request"
        const val PATH_PAIR_STATUS = "/v1/pair/status"
        const val PATH_PACKAGES_PULL = "/v1/packages/pull"
        const val PATH_PACKAGES_PUSH = "/v1/packages/push"
        const val PATH_PACKAGES_ACK = "/v1/packages/ack"
        const val PATH_SNAPSHOT_INFO = "/v1/snapshot/info"
        const val PATH_SNAPSHOT_CHUNK = "/v1/snapshot/chunk"
        const val PATH_SNAPSHOT_ACK = "/v1/snapshot/ack"

        internal val JSON_MEDIA_TYPE = "application/json; charset=utf-8".toMediaType()
    }
}

/** HTTPS-клиент с закреплённым сертификатом ПК и TLS 1.2+. */
fun defaultClientFor(endpoint: SyncEndpoint): OkHttpClient {
    val builder = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .callTimeout(45, TimeUnit.SECONDS)
        // true: после перезапуска ПК повторить запрос на новом TCP, а не отдать
        // «ПК недоступен» из‑за мёртвого keep-alive в пуле (см. stage8-corr11).
        .retryOnConnectionFailure(true)
    if (endpoint.isPinned) {
        val trustManager = TlsFingerprint.trustManager(endpoint.certSha256)
        builder
            .connectionSpecs(listOf(ConnectionSpec.MODERN_TLS))
            .sslSocketFactory(TlsFingerprint.socketFactory(trustManager), trustManager)
            .hostnameVerifier(TlsFingerprint.hostnameVerifier(endpoint.certSha256))
    }
    return builder.build()
}

/**
 * Классификация транспорта без утечки токенов/ключей.
 * HTTP-ответы (401/403/…) сюда не попадают — только исключения сокета/TLS.
 */
internal fun classifyTransportError(error: Throwable): String {
    if (causeChainContains(error) { it is CertificateFingerprintException }) {
        return SyncErrorCodes.TLS_PIN_MISMATCH
    }
    if (causeChainContains(error) {
            it is SSLPeerUnverifiedException || it is SSLException || it is CertificateException
        }
    ) {
        return SyncErrorCodes.TLS_ERROR
    }
    if (causeChainContains(error) { it is SocketTimeoutException }) {
        return SyncErrorCodes.TIMEOUT
    }
    if (causeChainContains(error) {
            it is ConnectException || it is UnknownHostException || it is NoRouteToHostException
        }
    ) {
        return SyncErrorCodes.NETWORK_UNAVAILABLE
    }
    if (error is IOException) {
        val text = (error.message ?: "").lowercase()
        if (text.contains("ssl") || text.contains("cert") || text.contains("handshake") ||
            text.contains("trust") || text.contains("peer")
        ) {
            return SyncErrorCodes.TLS_ERROR
        }
        return SyncErrorCodes.NETWORK_UNAVAILABLE
    }
    return SyncErrorCodes.INTERNAL_ERROR
}

private fun causeChainContains(error: Throwable, pred: (Throwable) -> Boolean): Boolean {
    var cur: Throwable? = error
    var depth = 0
    while (cur != null && depth++ < 16) {
        if (pred(cur)) return true
        cur = cur.cause
    }
    return false
}

private fun readAtMost(stream: InputStream, limit: Int): ByteArray {
    val buffer = ByteArray(8 * 1024)
    val out = java.io.ByteArrayOutputStream()
    var total = 0
    while (total < limit) {
        val read = stream.read(buffer, 0, minOf(buffer.size, limit - total))
        if (read <= 0) break
        out.write(buffer, 0, read)
        total += read
    }
    return out.toByteArray()
}
