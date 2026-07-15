package jp.unknowntech.mobilemelon.data

import jp.unknowntech.mobilemelon.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/** One 6-month expiry lot of spendable balance. */
data class Bucket(val remaining: Long, val expiresAt: String)

/** The self-service balance for this device's chip. */
data class SelfBalance(val total: Long, val buckets: List<Bucket>)

sealed interface BalanceResult {
    data class Success(val balance: SelfBalance) : BalanceResult

    /** No melon account for this identifier (HTTP 404). */
    data object NotRegistered : BalanceResult

    data class Error(val message: String) : BalanceResult
}

/** A non-2xx response from the server, carrying the stable `code` the UI maps. */
class MelonApiException(val status: Int, val code: String?, message: String) : IOException(message)

/** One step of the mutual-authentication relay. */
data class AuthStep(
    val step: String,
    val sessionId: String?,
    /** The command frame to relay to the card (null on completion). */
    val frame: String?,
    /** The balance, present only when `step == "complete"`. */
    val balance: SelfBalance?,
)

/**
 * Talks to the melon server's unauthenticated self-service endpoints. Identifiers
 * travel in the request body (never the URL), so they stay out of access logs.
 */
object MelonApi {
    private const val TIMEOUT_MS = 15_000

    // ----- balance by asserted IDi (typed card ID) -----

    suspend fun selfBalanceByIdi(systemCode: Int, idiHex: String): BalanceResult =
        withContext(Dispatchers.IO) {
            val body = JSONObject().put("system_code", systemCode).put("idi", idiHex)
            val res = try {
                post("/v1/self/balance", body)
            } catch (e: IOException) {
                return@withContext BalanceResult.Error(e.message ?: "通信に失敗しました")
            }
            when (res.code) {
                HttpURLConnection.HTTP_OK -> parseBalance(res.body)
                    ?.let { BalanceResult.Success(it) }
                    ?: BalanceResult.Error("応答の解析に失敗しました")

                HttpURLConnection.HTTP_NOT_FOUND -> BalanceResult.NotRegistered
                else -> BalanceResult.Error("サーバエラー (${res.code})")
            }
        }

    // ----- card-present balance via mutual-authentication relay -----

    suspend fun selfAuthStart(
        systemCode: Int,
        idmHex: String,
        pmmHex: String,
        areas: List<Int>,
        services: List<Int>,
    ): AuthStep = withContext(Dispatchers.IO) {
        val body = JSONObject()
            .put("system_code", systemCode)
            .put("idm", idmHex)
            .put("pmm", pmmHex)
            .put("areas", JSONArray(areas))
            .put("services", JSONArray(services))
        authStep(post("/v1/self/authenticate", body))
    }

    suspend fun selfAuthContinue(sessionId: String, cardResponseHex: String): AuthStep =
        withContext(Dispatchers.IO) {
            val body = JSONObject()
                .put("session_id", sessionId)
                .put("card_response", cardResponseHex)
            authStep(post("/v1/self/authenticate", body))
        }

    // ----- plumbing -----

    private data class HttpResult(val code: Int, val body: String)

    /** POST [body] as JSON; return status + body text. Throws only on network failure. */
    private fun post(path: String, body: JSONObject): HttpResult {
        val base = BuildConfig.MELON_API_BASE_URL.trimEnd('/')
        val conn = URL("$base$path").openConnection() as HttpURLConnection
        return try {
            conn.apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(body.toString().toByteArray(Charsets.UTF_8)) }
            val code = conn.responseCode
            val stream = if (code in 200..299) conn.inputStream else conn.errorStream
            val text = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
            HttpResult(code, text)
        } finally {
            conn.disconnect()
        }
    }

    /** Parse an auth-relay response, or throw [MelonApiException] on a server error. */
    private fun authStep(res: HttpResult): AuthStep {
        if (res.code !in 200..299) {
            throw MelonApiException(res.code, errorCode(res.body), "サーバエラー (${res.code})")
        }
        val o = JSONObject(res.body)
        return AuthStep(
            step = o.getString("step"),
            sessionId = o.optString("session_id").ifEmpty { null },
            frame = o.optJSONObject("command")?.optString("frame")?.ifEmpty { null },
            balance = o.optJSONObject("result")?.let { parseBalanceObject(it) },
        )
    }

    private fun errorCode(body: String): String? =
        runCatching { JSONObject(body).optJSONObject("error")?.optString("code")?.ifEmpty { null } }
            .getOrNull()

    private fun parseBalance(text: String): SelfBalance? =
        runCatching { parseBalanceObject(JSONObject(text)) }.getOrNull()

    private fun parseBalanceObject(obj: JSONObject): SelfBalance {
        val bucketsJson = obj.optJSONArray("buckets")
        val buckets = buildList {
            if (bucketsJson != null) {
                for (i in 0 until bucketsJson.length()) {
                    val b = bucketsJson.getJSONObject(i)
                    add(Bucket(b.getLong("remaining"), b.optString("expires_at")))
                }
            }
        }
        return SelfBalance(total = obj.getLong("total"), buckets = buckets)
    }
}
