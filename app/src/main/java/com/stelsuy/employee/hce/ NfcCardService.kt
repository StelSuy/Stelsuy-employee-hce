package com.stelsuy.employee.hce

import android.nfc.cardemulation.HostApduService
import android.os.Bundle
import java.nio.charset.Charset

class NfcCardService : HostApduService() {

    // AID из apduservice.xml: F0010203040506
    private val aid = "F0010203040506"

    // Команды:
    // GET EMP: 00 CA 00 00 00
    private val getEmpCmd = hex("00CA000000")

    // GET PUBKEY: 00 CC 00 00 00
    private val getPubCmd = hex("00CC000000")

    // SIGN CHALLENGE: 00 CB <challenge bytes...>
    private val signCmdPrefix = byteArrayOf(0x00, 0xCB.toByte())

    private val swOk = hex("9000")
    private val swUnknown = hex("6F00")
    private val swWrong = hex("6A82")

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return swUnknown

        // SELECT AID
        if (isSelectAid(commandApdu)) {
            return swOk
        }

        // GET EMP
        if (commandApdu.contentEquals(getEmpCmd)) {
            if (!Prefs.isEnabled(this)) {
                val payload = "DISABLED".toByteArray(Charset.forName("UTF-8"))
                return payload + swOk
            }

            val empId = Prefs.getOrCreateEmployeeId(this)
            val payload = "EMP:$empId".toByteArray(Charsets.UTF_8)
            return payload + swOk
        }

        // GET PUBLIC KEY
        if (commandApdu.contentEquals(getPubCmd)) {
            if (!Prefs.isEnabled(this)) {
                val payload = "DISABLED".toByteArray(Charset.forName("UTF-8"))
                return payload + swOk
            }

            Crypto.ensureKey()
            val pub = Crypto.publicKeyB64()
            val payload = "PUB:$pub".toByteArray(Charsets.UTF_8)
            return payload + swOk
        }

        // SIGN CHALLENGE
        if (commandApdu.size > 2 &&
            commandApdu[0] == signCmdPrefix[0] &&
            commandApdu[1] == signCmdPrefix[1]
        ) {
            if (!Prefs.isEnabled(this)) {
                val payload = "DISABLED".toByteArray(Charset.forName("UTF-8"))
                return payload + swOk
            }

            return try {
                val challenge = commandApdu.copyOfRange(2, commandApdu.size)
                Crypto.ensureKey()
                val signatureB64 = Crypto.sign(challenge)
                val payload = signatureB64.toByteArray(Charsets.UTF_8)
                payload + swOk
            } catch (_: Exception) {
                swWrong
            }
        }

        return swWrong
    }

    override fun onDeactivated(reason: Int) {}

    private fun isSelectAid(apdu: ByteArray): Boolean {
        if (apdu.size < 6) return false
        if (apdu[0] != 0x00.toByte()) return false
        if (apdu[1] != 0xA4.toByte()) return false
        if (apdu[2] != 0x04.toByte()) return false
        if (apdu[3] != 0x00.toByte()) return false

        val lc = apdu[4].toInt() and 0xFF
        if (apdu.size < 5 + lc) return false

        val aidBytes = apdu.copyOfRange(5, 5 + lc)
        val aidHex = aidBytes.joinToString("") { "%02X".format(it) }
        return aidHex.equals(aid, ignoreCase = true)
    }

    private fun hex(s: String): ByteArray {
        val clean = s.replace(" ", "")
        val out = ByteArray(clean.length / 2)
        for (i in out.indices) {
            val idx = i * 2
            out[i] = clean.substring(idx, idx + 2).toInt(16).toByte()
        }
        return out
    }
}
