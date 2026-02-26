package com.stelsuy.employee.hce

import android.content.Context
import android.nfc.cardemulation.HostApduService
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager

class NfcCardService : HostApduService() {

    private val aid = "F0010203040506"

    private val getEmpCmd = hex("00CA000000")
    private val getPubCmd = hex("00CC000000")
    private val signCmdPrefix = byteArrayOf(0x00, 0xCB.toByte())

    private val swOk = hex("9000")
    private val swUnknown = hex("6F00")
    private val swWrong = hex("6A82")

    override fun onCreate() {
        super.onCreate()
        // щоб ключ точно існував
        try { Crypto.ensureKey() } catch (_: Exception) {}
    }

    override fun processCommandApdu(commandApdu: ByteArray?, extras: Bundle?): ByteArray {
        if (commandApdu == null) return swUnknown

        // SELECT AID
        if (isSelectAid(commandApdu)) return swOk

        // GET EMP
        if (commandApdu.contentEquals(getEmpCmd)) {
            if (!Prefs.isEnabled(this)) return "DISABLED".toByteArray(Charsets.UTF_8) + swOk

            val empId = Prefs.getOrCreateEmployeeId(this)
            return "EMP:$empId".toByteArray(Charsets.UTF_8) + swOk
        }

        // GET PUBLIC KEY
        if (commandApdu.contentEquals(getPubCmd)) {
            if (!Prefs.isEnabled(this)) return "DISABLED".toByteArray(Charsets.UTF_8) + swOk

            val pub = Crypto.publicKeyB64()
            return "PUB:$pub".toByteArray(Charsets.UTF_8) + swOk
        }

        // SIGN CHALLENGE
        if (commandApdu.size > 2 &&
            commandApdu[0] == signCmdPrefix[0] &&
            commandApdu[1] == signCmdPrefix[1]
        ) {
            if (!Prefs.isEnabled(this)) return "DISABLED".toByteArray(Charsets.UTF_8) + swOk

            return try {
                val challenge = commandApdu.copyOfRange(2, commandApdu.size)
                val signatureB64 = Crypto.sign(challenge)
                vibrate() // зворотній зв'язок: підпис виконано
                signatureB64.toByteArray(Charsets.UTF_8) + swOk
            } catch (_: Exception) {
                swWrong
            }
        }

        return swWrong
    }

    override fun onDeactivated(reason: Int) {}

    /**
     * Коротка вібрація для зворотного зв'язку при скануванні.
     */
    private fun vibrate() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                val vm = getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
                vm?.defaultVibrator?.vibrate(
                    VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                )
            } else {
                @Suppress("DEPRECATION")
                val v = getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    v?.vibrate(
                        VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE)
                    )
                } else {
                    @Suppress("DEPRECATION")
                    v?.vibrate(150)
                }
            }
        } catch (_: Exception) {}
    }

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