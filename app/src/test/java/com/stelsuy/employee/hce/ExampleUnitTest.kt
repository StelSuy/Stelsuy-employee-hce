package com.stelsuy.employee.hce

import android.util.Base64
import org.junit.Assert.*
import org.junit.Test

/**
 * Юніт-тести для проєкту Employee HCE.
 *
 * Перевіряють логіку без залежності від Android-пристрою:
 * - Формат Employee ID
 * - APDU-команди (hex-парсинг, SELECT AID, відповіді)
 * - Base64-кодування підпису
 */
class ExampleUnitTest {

    // ─── Допоміжні функції (дублюємо з NfcCardService, щоб не тягнути клас) ────

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val idx = i * 2
            out[i] = clean.substring(idx, idx + 2).toInt(16).toByte()
        }
        return out
    }

    private fun buildSelectAid(aidHex: String): ByteArray {
        val aidBytes = hex(aidHex)
        return byteArrayOf(0x00, 0xA4.toByte(), 0x04, 0x00, aidBytes.size.toByte()) + aidBytes
    }

    private fun isSelectAid(apdu: ByteArray, expectedAid: String = "F0010203040506"): Boolean {
        if (apdu.size < 6) return false
        if (apdu[0] != 0x00.toByte()) return false
        if (apdu[1] != 0xA4.toByte()) return false
        if (apdu[2] != 0x04.toByte()) return false
        if (apdu[3] != 0x00.toByte()) return false
        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return false
        val aidBytes = apdu.copyOfRange(5, 5 + lc)
        val aidHex = aidBytes.joinToString("") { "%02X".format(it) }
        return aidHex.equals(expectedAid, ignoreCase = true)
    }

    // ─── Employee ID ─────────────────────────────────────────────────────────────

    @Test
    fun employeeId_formatIsCorrect() {
        // Формат: EMP-XXXXXXXX (12 символів)
        val regex = Regex("^EMP-[A-Z0-9]{8}$")
        val sampleIds = listOf(
            "EMP-1A2B3C4D",
            "EMP-ABCDEF12",
            "EMP-00000000"
        )
        sampleIds.forEach { id ->
            assertTrue("ID '$id' не відповідає формату", regex.matches(id))
        }
    }

    @Test
    fun employeeId_invalidFormatsRejected() {
        val regex = Regex("^EMP-[A-Z0-9]{8}$")
        val bad = listOf("", "EMP-", "EMP-abc", "emp-ABCDEF12", "EMP-ABCDEF123")
        bad.forEach { id ->
            assertFalse("ID '$id' помилково пройшов перевірку", regex.matches(id))
        }
    }

    @Test
    fun employeeId_generationProducesUniqueValues() {
        // Симулюємо генерацію 100 ID — всі мають бути унікальні
        val ids = (1..100).map {
            "EMP-" + java.util.UUID.randomUUID()
                .toString()
                .replace("-", "")
                .uppercase()
                .substring(0, 8)
        }.toSet()
        assertEquals("Виявлено дублікати серед згенерованих ID", 100, ids.size)
    }

    // ─── HEX-утиліта ─────────────────────────────────────────────────────────────

    @Test
    fun hex_parsesCorrectly() {
        val result = hex("9000")
        assertEquals(2, result.size)
        assertEquals(0x90.toByte(), result[0])
        assertEquals(0x00.toByte(), result[1])
    }

    @Test
    fun hex_parsesWithSpaces() {
        val a = hex("90 00")
        val b = hex("9000")
        assertArrayEquals(a, b)
    }

    @Test
    fun hex_caseInsensitive() {
        val lower = hex("f0010203040506")
        val upper = hex("F0010203040506")
        assertArrayEquals(lower, upper)
    }

    // ─── SELECT AID ──────────────────────────────────────────────────────────────

    @Test
    fun selectAid_recognizedCorrectly() {
        val apdu = buildSelectAid("F0010203040506")
        assertTrue("SELECT AID не розпізнано", isSelectAid(apdu))
    }

    @Test
    fun selectAid_wrongAidRejected() {
        val apdu = buildSelectAid("A000000003000000")
        assertFalse("Чужий AID помилково прийнятий", isSelectAid(apdu))
    }

    @Test
    fun selectAid_tooShortApduRejected() {
        val apdu = byteArrayOf(0x00, 0xA4.toByte(), 0x04)
        assertFalse("Занадто короткий APDU помилково прийнятий", isSelectAid(apdu))
    }

    @Test
    fun selectAid_wrongClaRejected() {
        // CLA != 0x00
        val apdu = buildSelectAid("F0010203040506")
        val modified = apdu.copyOf()
        modified[0] = 0x01
        assertFalse("Невірний CLA помилково прийнятий", isSelectAid(modified))
    }

    @Test
    fun selectAid_wrongInsRejected() {
        val apdu = buildSelectAid("F0010203040506")
        val modified = apdu.copyOf()
        modified[1] = 0xB0.toByte() // READ BINARY, не SELECT
        assertFalse("Невірний INS помилково прийнятий", isSelectAid(modified))
    }

    // ─── APDU-відповіді ───────────────────────────────────────────────────────────

    @Test
    fun swOk_isCorrect() {
        val swOk = hex("9000")
        assertEquals(0x90.toByte(), swOk[0])
        assertEquals(0x00.toByte(), swOk[1])
    }

    @Test
    fun getEmpCmd_isCorrect() {
        val cmd = hex("00CA000000")
        assertEquals(5, cmd.size)
        assertEquals(0x00.toByte(), cmd[0])
        assertEquals(0xCA.toByte(), cmd[1])
    }

    @Test
    fun getPubCmd_isCorrect() {
        val cmd = hex("00CC000000")
        assertEquals(5, cmd.size)
        assertEquals(0xCC.toByte(), cmd[1])
    }

    @Test
    fun signCmd_prefixIsCorrect() {
        val prefix = byteArrayOf(0x00, 0xCB.toByte())
        assertEquals(0x00.toByte(), prefix[0])
        assertEquals(0xCB.toByte(), prefix[1])
    }

    // ─── EMP-відповідь ───────────────────────────────────────────────────────────

    @Test
    fun empResponse_containsPrefix() {
        val empId = "EMP-1A2B3C4D"
        val swOk = hex("9000")
        val response = "EMP:$empId".toByteArray(Charsets.UTF_8) + swOk
        val text = response.dropLast(2).toByteArray().toString(Charsets.UTF_8)
        assertTrue("Відповідь не починається з 'EMP:'", text.startsWith("EMP:"))
        assertEquals("EMP:EMP-1A2B3C4D", text)
    }

    @Test
    fun empResponse_disabledReturnsCorrectBytes() {
        val swOk = hex("9000")
        val response = "DISABLED".toByteArray(Charsets.UTF_8) + swOk
        val text = response.dropLast(2).toByteArray().toString(Charsets.UTF_8)
        assertEquals("DISABLED", text)
    }

    @Test
    fun pubResponse_containsPrefix() {
        val fakeB64 = "AAABBBCCC=="
        val swOk = hex("9000")
        val response = "PUB:$fakeB64".toByteArray(Charsets.UTF_8) + swOk
        val text = response.dropLast(2).toByteArray().toString(Charsets.UTF_8)
        assertTrue("Відповідь публічного ключа не починається з 'PUB:'", text.startsWith("PUB:"))
    }

    // ─── Base64-логіка підпису ────────────────────────────────────────────────────

    @Test
    fun base64_encodingAndDecodingRoundtrip() {
        val original = "Hello, HCE!".toByteArray(Charsets.UTF_8)
        // Використовуємо java.util.Base64 (доступний у JVM-тестах без Android)
        val encoded = java.util.Base64.getEncoder().encodeToString(original)
        val decoded = java.util.Base64.getDecoder().decode(encoded)
        assertArrayEquals("Base64 round-trip не збігається", original, decoded)
    }

    @Test
    fun base64_noWrapProducesNoNewlines() {
        val data = ByteArray(100) { it.toByte() }
        val encoded = java.util.Base64.getEncoder().encodeToString(data)
        assertFalse("Рядок Base64 містить переноси рядка", encoded.contains('\n'))
    }

    // ─── SIGN-команда ─────────────────────────────────────────────────────────────

    @Test
    fun signCmd_challengeExtractedCorrectly() {
        val prefix = byteArrayOf(0x00, 0xCB.toByte())
        val challenge = "RANDOM_CHALLENGE".toByteArray(Charsets.UTF_8)
        val apdu = prefix + challenge
        val extracted = apdu.copyOfRange(2, apdu.size)
        assertArrayEquals("Challenge витягнутий неправильно", challenge, extracted)
    }

    @Test
    fun signCmd_detectedByPrefix() {
        val prefix = byteArrayOf(0x00, 0xCB.toByte())
        val apdu = byteArrayOf(0x00, 0xCB.toByte()) + "data".toByteArray()
        val detected = apdu.size > 2 && apdu[0] == prefix[0] && apdu[1] == prefix[1]
        assertTrue("SIGN-команда не розпізнана", detected)
    }

    @Test
    fun signCmd_notDetectedWithWrongBytes() {
        val prefix = byteArrayOf(0x00, 0xCB.toByte())
        val apdu = byteArrayOf(0x00, 0xCA.toByte()) + "data".toByteArray()
        val detected = apdu.size > 2 && apdu[0] == prefix[0] && apdu[1] == prefix[1]
        assertFalse("Чужа команда помилково розпізнана як SIGN", detected)
    }

    // ─── Загальна перевірка ───────────────────────────────────────────────────────

    @Test
    fun addition_isCorrect() {
        assertEquals(4, 2 + 2)
    }
}
