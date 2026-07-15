package jp.unknowntech.mobilemelon.data

import jp.unknowntech.mobilemelon.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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

    /** The card cannot be used, e.g. a randomized IDm (HTTP 422). */
    data object Unsupported : BalanceResult

    data class Error(val message: String) : BalanceResult
}

/**
 * Talks to the melon server's unauthenticated self-service balance endpoint,
 * `POST /v1/self/balance`. The identifier goes in the request body (never the
 * URL), so it does not land in server access logs.
 */
object MelonApi {
    private const val TIMEOUT_MS = 10_000

    /** Look up a balance by the card ID's underlying IDi. */
    suspend fun selfBalanceByIdi(systemCode: Int, idiHex: String): BalanceResult =
        withContext(Dispatchers.IO) { request(systemCode, "idi", idiHex) }

    /** Look up a balance by an IDm read off a card over NFC. */
    suspend fun selfBalanceByIdm(systemCode: Int, idmHex: String): BalanceResult =
        withContext(Dispatchers.IO) { request(systemCode, "idm", idmHex) }

    private fun request(systemCode: Int, field: String, value: String): BalanceResult {
        val base = BuildConfig.MELON_API_BASE_URL.trimEnd('/')
        val url = try {
            URL("$base/v1/self/balance")
        } catch (_: Exception) {
            return BalanceResult.Error("APIのURLが不正です")
        }
        val payload = JSONObject()
            .put("system_code", systemCode)
            .put(field, value)
            .toString()

        val conn = url.openConnection() as HttpURLConnection
        return try {
            conn.apply {
                requestMethod = "POST"
                connectTimeout = TIMEOUT_MS
                readTimeout = TIMEOUT_MS
                doOutput = true
                setRequestProperty("Content-Type", "application/json")
                setRequestProperty("Accept", "application/json")
            }
            conn.outputStream.use { it.write(payload.toByteArray(Charsets.UTF_8)) }

            when (val code = conn.responseCode) {
                HttpURLConnection.HTTP_OK -> parseBalance(conn.body())
                HttpURLConnection.HTTP_NOT_FOUND -> BalanceResult.NotRegistered
                422 -> BalanceResult.Unsupported
                else -> BalanceResult.Error("サーバエラー ($code)")
            }
        } catch (e: IOException) {
            BalanceResult.Error(e.message ?: "通信に失敗しました")
        } finally {
            conn.disconnect()
        }
    }

    private fun HttpURLConnection.body(): String {
        val stream = if (responseCode in 200..299) inputStream else errorStream
        return stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()
    }

    private fun parseBalance(text: String): BalanceResult = try {
        val obj = JSONObject(text)
        val bucketsJson = obj.optJSONArray("buckets")
        val buckets = buildList {
            if (bucketsJson != null) {
                for (i in 0 until bucketsJson.length()) {
                    val b = bucketsJson.getJSONObject(i)
                    add(Bucket(b.getLong("remaining"), b.optString("expires_at")))
                }
            }
        }
        BalanceResult.Success(SelfBalance(total = obj.getLong("total"), buckets = buckets))
    } catch (_: Exception) {
        BalanceResult.Error("応答の解析に失敗しました")
    }
}
