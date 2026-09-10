package com.mars.planner.sync

import java.security.MessageDigest
import java.security.cert.CertificateException
import java.security.cert.X509Certificate
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLSession
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.X509TrustManager

/** Сертификат сервера не совпал с отпечатком из QR. */
class CertificateFingerprintException(message: String) : CertificateException(message)

/**
 * Пиннинг самоподписанного сертификата ПК: доверяем только сертификату,
 * SHA-256 DER-представления которого совпадает с `cert_sha256` из QR.
 * Системные корневые сертификаты для синхронизации не используются.
 */
object TlsFingerprint {

    /** SHA-256 DER-представления сертификата в нижнем регистре без разделителей. */
    fun sha256Hex(certificate: X509Certificate): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(certificate.encoded)
        return digest.joinToString("") { "%02x".format(it) }
    }

    /** Приводит отпечаток к нижнему регистру без `:`, пробелов и пустых символов. */
    fun normalizeHex(raw: String?): String =
        raw.orEmpty().filterNot { it == ':' || it == '-' || it.isWhitespace() }.lowercase()

    fun isValidFingerprint(raw: String?): Boolean {
        val hex = normalizeHex(raw)
        return hex.length == 64 && hex.all { it in '0'..'9' || it in 'a'..'f' }
    }

    /** Группы по 4 символа в верхнем регистре — для показа пользователю. */
    fun formatForDisplay(raw: String?): String =
        normalizeHex(raw).uppercase().chunked(4).joinToString(" ")

    fun matches(certificate: X509Certificate, expectedHex: String): Boolean =
        constantTimeEquals(sha256Hex(certificate), normalizeHex(expectedHex))

    fun trustManager(expectedHex: String): X509TrustManager = PinnedTrustManager(expectedHex)

    fun socketFactory(expectedHex: String): SSLSocketFactory =
        socketFactory(trustManager(expectedHex))

    fun socketFactory(trustManager: X509TrustManager): SSLSocketFactory {
        val context = SSLContext.getInstance("TLS")
        context.init(null, arrayOf(trustManager), java.security.SecureRandom())
        return context.socketFactory
    }

    /**
     * Сертификат ПК выписан на IP и самоподписан, поэтому проверка имени хоста
     * заменена проверкой того же отпечатка, что и у TrustManager.
     */
    fun hostnameVerifier(expectedHex: String): HostnameVerifier =
        FingerprintHostnameVerifier(expectedHex)

    internal fun constantTimeEquals(a: String, b: String): Boolean {
        if (a.length != b.length) return false
        var diff = 0
        for (i in a.indices) diff = diff or (a[i].code xor b[i].code)
        return diff == 0
    }
}

private class PinnedTrustManager(expectedHex: String) : X509TrustManager {

    private val expected: String = TlsFingerprint.normalizeHex(expectedHex)

    init {
        if (!TlsFingerprint.isValidFingerprint(expected)) {
            throw IllegalArgumentException("Отпечаток сертификата должен быть 64 hex-символа")
        }
    }

    override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        throw CertificateException("Клиентские сертификаты не поддерживаются")
    }

    override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {
        val leaf = chain?.firstOrNull()
            ?: throw CertificateFingerprintException("Сервер не предъявил сертификат")
        val actual = TlsFingerprint.sha256Hex(leaf)
        if (!TlsFingerprint.constantTimeEquals(actual, expected)) {
            throw CertificateFingerprintException("Отпечаток сертификата ПК не совпал")
        }
    }

    override fun getAcceptedIssuers(): Array<X509Certificate> = emptyArray()
}

private class FingerprintHostnameVerifier(expectedHex: String) : HostnameVerifier {

    private val expected: String = TlsFingerprint.normalizeHex(expectedHex)

    override fun verify(hostname: String?, session: SSLSession?): Boolean {
        val leaf = runCatching { session?.peerCertificates?.firstOrNull() }.getOrNull()
        val x509 = leaf as? X509Certificate ?: return false
        return TlsFingerprint.constantTimeEquals(TlsFingerprint.sha256Hex(x509), expected)
    }
}
