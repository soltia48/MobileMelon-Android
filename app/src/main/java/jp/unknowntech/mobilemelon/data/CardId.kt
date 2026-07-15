package jp.unknowntech.mobilemelon.data

import jp.unknowntech.mobilemelon.data.CardId.parse


/**
 * Converts between the **card ID** — the human-readable string that transit-IC
 * wallet apps (Mobile Suica, PASMO, etc.) show — and the 8-byte **IDi** it
 * encodes.
 *
 * The IDi lays out as issuer(2) · remainder(2) · issue-date(2) · serial(2). The
 * string form (see suica-viewer's `idi_bytes_to_str`) replaces a known issuer's
 * two bytes with a two-letter identifier (`0103` → `JE`), renders the date as
 * `YYMMDD` and the serial as five digits:
 *
 *   known issuer:   `JE` + remainder(4 hex) + `YYMMDD`(6) + serial(5)   = 17 chars
 *   unknown issuer: issuer(4 hex) + remainder(4 hex) + `YYMMDD` + serial = 19 chars
 *
 * The mapping is reversible: only bit 15 of the date word is unused, and it is 0
 * on real cards, so [parse] reconstructs the exact IDi. A raw 16-hex-character IDi
 * is also accepted for convenience.
 */
object CardId {
    /** The Suica / transit-IC (Cybernetics) FeliCa system. */
    const val SYSTEM_CODE = 0x0003

    /** issuer hex → two-letter identifier. From suica-viewer's ISSUER_ID_MAP. */
    private val ISSUERS: Map<String, String> = mapOf(
        "0102" to "JH", // JR北海道
        "0103" to "JE", // JR東日本
        "0104" to "JC", // JR東海
        "0105" to "JW", // JR西日本
        "0107" to "JK", // JR九州
        "0252" to "PB", // PASMO
        "0387" to "TP", // 名古屋交通開発機構ほか
        "04AD" to "SU", // スルッとKANSAI
        "05D5" to "NR", // ニモカ
        "05D7" to "FC", // 福岡市交通局
    )
    private val IDENTIFIER_TO_HEX: Map<String, String> =
        ISSUERS.entries.associate { (hex, id) -> id to hex }

    data class Parsed(
        /** The 8-byte IDi. */
        val idi: ByteArray,
        /** IDi as 16 lowercase hex chars — the wire form for the server. */
        val idiHex: String,
        /** The canonical card ID string this IDi renders to. */
        val canonical: String,
    )

    sealed interface Result {
        data class Ok(val value: Parsed) : Result
        data class Invalid(val message: String) : Result
    }

    /** Parse a user-entered card ID (or raw IDi hex) into the IDi it denotes. */
    fun parse(input: String): Result {
        val s = input.trim().uppercase().replace("\\s".toRegex(), "")
        if (s.isEmpty()) return Result.Invalid("カード ID を入力してください。")

        val idi = when {
            s.length == 16 && s.isHex() -> hexToBytes(s)
            s.length == 17 -> parseKnownIssuer(s) ?: return badFormat()
            s.length == 19 -> parseUnknownIssuer(s) ?: return badFormat()
            else -> return badFormat()
        }
        return Result.Ok(
            Parsed(
                idi = idi,
                idiHex = bytesToHex(idi, 0, 8).lowercase(),
                canonical = format(idi),
            ),
        )
    }

    /** The canonical card ID string for an IDi (mirrors `idi_bytes_to_str`). */
    fun format(idi: ByteArray): String {
        require(idi.size == 8) { "IDi must be 8 bytes" }
        val issuerHex = bytesToHex(idi, 0, 2)
        val remainder = bytesToHex(idi, 2, 4)
        val head = (ISSUERS[issuerHex] ?: issuerHex) + remainder

        val v = ((idi[4].toInt() and 0xFF) shl 8) or (idi[5].toInt() and 0xFF)
        val year = (v ushr 9) and 0x3F
        val month = (v ushr 5) and 0x0F
        val day = v and 0x1F
        val date = "%02d%02d%02d".format(year % 100, month, day)

        val serial = ((idi[6].toInt() and 0xFF) shl 8) or (idi[7].toInt() and 0xFF)
        return "$head$date%05d".format(serial)
    }

    // ----- internals -----

    private fun parseKnownIssuer(s: String): ByteArray? {
        val issuerHex = IDENTIFIER_TO_HEX[s.substring(0, 2)] ?: return null
        return assemble(issuerHex, s.substring(2))
    }

    private fun parseUnknownIssuer(s: String): ByteArray? {
        val issuerHex = s.substring(0, 4)
        if (!issuerHex.isHex()) return null
        return assemble(issuerHex, s.substring(4))
    }

    /** `rest` = remainder(4 hex) + date(6 digits) + serial(5 digits). */
    private fun assemble(issuerHex: String, rest: String): ByteArray? {
        if (rest.length != 15) return null
        val remainder = rest.substring(0, 4)
        val date = rest.substring(4, 10)
        val serial = rest.substring(10, 15)
        if (!remainder.isHex() || !date.all { it.isDigit() } || !serial.all { it.isDigit() }) {
            return null
        }
        val yy = date.substring(0, 2).toInt()
        val month = date.substring(2, 4).toInt()
        val day = date.substring(4, 6).toInt()
        val serialVal = serial.toInt()
        if (yy > 0x3F || month > 0x0F || day > 0x1F || serialVal > 0xFFFF) return null

        val v = (yy shl 9) or (month shl 5) or day
        val out = ByteArray(8)
        hexToBytes(issuerHex).copyInto(out, 0)
        hexToBytes(remainder).copyInto(out, 2)
        out[4] = ((v ushr 8) and 0xFF).toByte()
        out[5] = (v and 0xFF).toByte()
        out[6] = ((serialVal ushr 8) and 0xFF).toByte()
        out[7] = (serialVal and 0xFF).toByte()
        return out
    }

    private fun badFormat() =
        Result.Invalid("カード ID の形式が正しくありません（例: JE で始まる 17 桁）。")

    private fun String.isHex() = length % 2 == 0 && all { it in '0'..'9' || it in 'A'..'F' }

    private fun hexToBytes(s: String): ByteArray = ByteArray(s.length / 2) { i ->
        ((Character.digit(s[i * 2], 16) shl 4) + Character.digit(s[i * 2 + 1], 16)).toByte()
    }

    private fun bytesToHex(bytes: ByteArray, from: Int, to: Int): String {
        val sb = StringBuilder((to - from) * 2)
        for (i in from until to) {
            val x = bytes[i].toInt() and 0xFF
            sb.append("0123456789ABCDEF"[x ushr 4])
            sb.append("0123456789ABCDEF"[x and 0x0F])
        }
        return sb.toString()
    }
}
