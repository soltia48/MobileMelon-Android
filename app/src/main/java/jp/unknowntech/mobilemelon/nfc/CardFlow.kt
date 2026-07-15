package jp.unknowntech.mobilemelon.nfc

import android.nfc.tech.NfcF
import jp.unknowntech.mobilemelon.data.CardId
import jp.unknowntech.mobilemelon.data.MelonApi
import jp.unknowntech.mobilemelon.data.SelfBalance

/**
 * The card-present balance lookup: relay the server-driven mutual authentication
 * for a tapped card and return the balance the server computes for the verified
 * IDi. The server holds the keys and emits each command frame; this app only
 * sends it to the card and returns the response. The verified IDi never leaves the
 * server — the app only ever receives the balance.
 *
 * Every FeliCa call may throw [android.nfc.TagLostException] if the card leaves the
 * field mid-flow.
 */
object CardFlow {
    // Melon fixes the authenticated area/service at 0x0000 (matches MelonTerminal).
    private val AREAS = listOf(0x0000)
    private val SERVICES = listOf(0x0000)

    /** Fixed cap on relay round-trips — mutual auth is a small, bounded exchange. */
    private const val MAX_RELAY_STEPS = 16

    /** Authenticate a tapped card (transit-IC system 0x0003) and return its balance. */
    suspend fun authenticate(nfcF: NfcF): SelfBalance {
        // Poll the transit-IC system; that system's IDm/PMm seed the auth.
        val sys = polling(nfcF, CardId.SYSTEM_CODE)

        var step = MelonApi.selfAuthStart(
            systemCode = CardId.SYSTEM_CODE,
            idmHex = sys.idm.toHex(),
            pmmHex = sys.pmm.toHex(),
            areas = AREAS,
            services = SERVICES,
        )
        val sessionId = step.sessionId
            ?: throw FelicaProtocolException("サーバが session_id を返しませんでした")

        var steps = 0
        while (step.step != "complete") {
            if (steps++ >= MAX_RELAY_STEPS) {
                throw FelicaProtocolException("認証が完了しませんでした")
            }
            val frame = step.frame
                ?: throw FelicaProtocolException("サーバがコマンドフレームを返しませんでした")
            val cardResponse = relayFrame(nfcF, hexToBytes(frame))
            step = MelonApi.selfAuthContinue(sessionId, cardResponse.toHex())
        }

        return step.balance
            ?: throw FelicaProtocolException("サーバが残高を返しませんでした")
    }
}
