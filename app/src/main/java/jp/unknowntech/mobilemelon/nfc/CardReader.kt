package jp.unknowntech.mobilemelon.nfc

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.NfcF

/** A FeliCa card as read off the NFC field. */
data class FelicaCard(val systemCode: Int, val idmHex: String)

/** Reader-mode flags: NFC-F (FeliCa) only, and skip the NDEF check we don't need. */
val READER_FLAGS: Int =
    NfcAdapter.FLAG_READER_NFC_F or NfcAdapter.FLAG_READER_SKIP_NDEF_CHECK

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
 * Parse a discovered NFC-F [Tag] into its IDm + System Code. For NFC-F, the tag's
 * id is the IDm (NFCID2); the System Code comes from the poll response.
 */
fun Tag.toFelicaCard(): FelicaCard? {
    val idm = id ?: return null
    if (idm.size != 8) return null
    val sc = NfcF.get(this)?.systemCode
    val systemCode = if (sc != null && sc.size >= 2) {
        ((sc[0].toInt() and 0xFF) shl 8) or (sc[1].toInt() and 0xFF)
    } else {
        0x0003
    }
    return FelicaCard(systemCode, idm.toHex())
}

private fun ByteArray.toHex(): String {
    val digits = "0123456789abcdef"
    val sb = StringBuilder(size * 2)
    for (b in this) {
        val v = b.toInt() and 0xFF
        sb.append(digits[v ushr 4])
        sb.append(digits[v and 0x0F])
    }
    return sb.toString()
}
