package jp.unknowntech.mobilemelon.nfc

import android.nfc.tech.NfcF
import java.io.ByteArrayOutputStream
import java.io.IOException
import java.util.Locale

/**
 * The minimal FeliCa (NFC-F / Type-3 Tag) layer this app needs to *relay* the
 * server-driven mutual authentication: Polling and a raw frame relay. All
 * cryptography is driven by the melon server — this app only carries frames
 * between the server and the card.
 *
 * FeliCa frames are length-prefixed: `[LEN][command_code][idm…][params]`, where
 * LEN is the total length including itself. `NfcF.transceive()` expects and
 * returns such length-prefixed frames.
 *
 * Adapted from MelonTerminal's `felica` layer.
 */

/** A FeliCa protocol-level failure (unexpected response, truncation, …). */
class FelicaProtocolException(message: String) : IOException(message)

/** The card's IDm (manufacture ID) and PMm for a polled system. */
class PollingResult(val idm: ByteArray, val pmm: ByteArray)

private const val FS_POLLING_COMMAND_CODE = 0x00
private const val FS_POLLING_RESPONSE_CODE = 0x01

// ----- byte helpers -----

private fun uByte(value: Byte): Int = value.toInt() and 0xFF

private fun writeBe16(out: ByteArrayOutputStream, value: Int) {
    out.write((value ushr 8) and 0xFF)
    out.write(value and 0xFF)
}

private fun ensureMinLength(data: ByteArray, expected: Int, label: String) {
    if (data.size < expected) {
        throw FelicaProtocolException("$label (expected >= $expected bytes, got ${data.size})")
    }
}

/** Lowercase hex, no separators (matches the server's hex encoding). */
fun ByteArray.toHex(): String =
    joinToString("") { String.format(Locale.US, "%02x", it.toInt() and 0xFF) }

/** Parse hex into bytes; throws on odd length or bad digits. */
fun hexToBytes(hex: String): ByteArray {
    val clean = hex.trim()
    if (clean.length % 2 != 0) {
        throw FelicaProtocolException("hex string has odd length: ${clean.length}")
    }
    return ByteArray(clean.length / 2) { i ->
        val hi = Character.digit(clean[i * 2], 16)
        val lo = Character.digit(clean[i * 2 + 1], 16)
        if (hi < 0 || lo < 0) throw FelicaProtocolException("invalid hex in '$hex'")
        ((hi shl 4) or lo).toByte()
    }
}

// ----- framing / transceive -----

private fun frameWithLengthPrefix(payload: ByteArray): ByteArray {
    val frame = ByteArray(payload.size + 1)
    frame[0] = (payload.size + 1).toByte()
    payload.copyInto(frame, destinationOffset = 1)
    return frame
}

/** Trim a length-prefixed FeliCa response to its declared length. */
private fun trimToDeclaredLength(raw: ByteArray): ByteArray {
    ensureMinLength(raw, 2, "FeliCa response too short")
    val declared = uByte(raw[0])
    if (declared < 2) throw FelicaProtocolException("invalid FeliCa response length byte: $declared")
    if (declared > raw.size) {
        throw FelicaProtocolException("truncated FeliCa response: declared=$declared actual=${raw.size}")
    }
    return if (declared == raw.size) raw else raw.copyOf(declared)
}

private fun transceive(nfcF: NfcF, payload: ByteArray): ByteArray =
    trimToDeclaredLength(nfcF.transceive(frameWithLengthPrefix(payload)))

// ----- client-side commands -----

/** Poll [systemCode] and return its IDm/PMm. Each system has its own IDm. */
fun polling(nfcF: NfcF, systemCode: Int): PollingResult {
    val payload = ByteArrayOutputStream().apply {
        write(FS_POLLING_COMMAND_CODE)
        writeBe16(this, systemCode)
        write(0x00) // request code: none
        write(0x00) // time slots: 1
    }.toByteArray()
    val response = transceive(nfcF, payload)
    val responseCode = uByte(response[1])
    if (responseCode != FS_POLLING_RESPONSE_CODE) {
        throw FelicaProtocolException("Polling unexpected response code: 0x%02X".format(responseCode))
    }
    ensureMinLength(response, 18, "Polling response too short")
    return PollingResult(
        idm = response.copyOfRange(2, 10),
        pmm = response.copyOfRange(10, 18),
    )
}

/**
 * Relay one mutual-authentication command frame from the server to the card and
 * return the card's response. The server sends the **full length-prefixed** frame,
 * which is exactly what `NfcF.transceive()` wants — so it goes verbatim. The
 * response is trimmed to its declared length and posted straight back.
 */
fun relayFrame(nfcF: NfcF, frame: ByteArray): ByteArray =
    trimToDeclaredLength(nfcF.transceive(frame))
