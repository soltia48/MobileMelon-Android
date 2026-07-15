package jp.unknowntech.mobilemelon.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF
import java.io.IOException

/** Reader-mode flags: NFC-F (FeliCa) only, and skip the NDEF check we don't need. */
val READER_FLAGS: Int =
    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

/**
 * A longer presence-check delay stops the platform from constantly re-polling a
 * card that stays on the reader during the multi-frame auth relay (which churns
 * tag handles and logs "Tag is out of date"). We connect the moment a tag is
 * discovered, so slower presence checks don't hurt responsiveness.
 */
const val READER_PRESENCE_CHECK_DELAY_MS = 1000

/** Per-transceive timeout (ms) — generous so a slow crypto step doesn't abort. */
private const val TRANSCEIVE_TIMEOUT_MS = 2000

/** Walk the ContextWrapper chain to the hosting Activity, or null. */
fun Context.findActivity(): Activity? {
    var ctx: Context? = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

/**
 * Connect [NfcF] for [tag] **on the calling thread**. Must be called straight from
 * the NFC reader-mode callback thread: deferring `connect()` onto another thread
 * leaves a window in which the platform re-issues the tag handle and invalidates
 * this one ("Tag … is out of date"). The caller owns the connection and MUST
 * [NfcF.close] it when finished.
 */
fun connectFelica(tag: Tag): NfcF {
    val nfcF = NfcF.get(tag) ?: throw IOException("FeliCa（NFC-F）ではないカードです")
    nfcF.connect()
    runCatching { nfcF.timeout = TRANSCEIVE_TIMEOUT_MS }
    return nfcF
}
