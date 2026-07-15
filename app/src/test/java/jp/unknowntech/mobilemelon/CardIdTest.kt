package jp.unknowntech.mobilemelon

import jp.unknowntech.mobilemelon.data.CardId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CardIdTest {

    private fun ok(input: String): CardId.Parsed {
        val r = CardId.parse(input)
        assertTrue("expected Ok for '$input', got $r", r is CardId.Result.Ok)
        return (r as CardId.Result.Ok).value
    }

    private fun invalid(input: String) {
        assertTrue(
            "expected Invalid for '$input'",
            CardId.parse(input) is CardId.Result.Invalid,
        )
    }

    @Test
    fun knownIssuerRoundTrips() {
        // issuer 0103 (JE), remainder 1234, date year=25 month=3 day=15
        // v = (25<<9)|(3<<5)|15 = 0x326F → bytes 32 6F, serial 54321 = 0xD431
        val idi = byteArrayOf(0x01, 0x03, 0x12, 0x34, 0x32, 0x6F, 0xD4.toByte(), 0x31)
        assertEquals("JE123425031554321", CardId.format(idi))

        val parsed = ok("JE123425031554321")
        assertEquals("01031234326fd431", parsed.idiHex)
        assertEquals("JE123425031554321", parsed.canonical)
    }

    @Test
    fun unknownIssuerUsesHexAndIsNineteenChars() {
        // issuer 0999 (not in the map) → 4-hex form, 19 chars total
        val idi = byteArrayOf(0x09, 0x99.toByte(), 0xAB.toByte(), 0xCD.toByte(), 0x32, 0x6F, 0x00, 0x01)
        val s = CardId.format(idi)
        assertEquals(19, s.length)
        assertEquals("0999ABCD25031500001", s)

        val parsed = ok(s)
        assertEquals("0999abcd326f0001", parsed.idiHex)
    }

    @Test
    fun rawHexIdiIsAccepted() {
        val parsed = ok("DEADBEEF00112233")
        assertEquals("deadbeef00112233", parsed.idiHex)
    }

    @Test
    fun lowercaseAndSpacesAreNormalized() {
        assertEquals(ok("JE123425031554321").idiHex, ok(" je1234 250315 54321 ").idiHex)
    }

    @Test
    fun allZeroDateStillRoundTrips() {
        // An empty date word is not a real date, but must survive the round trip.
        val idi = byteArrayOf(0x01, 0x03, 0x12, 0x34, 0x00, 0x00, 0x00, 0x01)
        val parsed = ok(CardId.format(idi))
        assertEquals("0103123400000001", parsed.idiHex)
    }

    @Test
    fun malformedInputsAreRejected() {
        invalid("")
        invalid("JE12")                 // too short
        invalid("ZZ123425031554321")    // unknown identifier at known-issuer length
        invalid("JE1234250315GGGGG")    // non-digit serial
        invalid("JEXX3425031554321")    // non-hex remainder
    }
}
