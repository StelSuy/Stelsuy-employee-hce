package com.stelsuy.employee.hce

import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyPairGenerator
import java.security.KeyStore
import java.security.Signature

object Crypto {
    private const val KEY_ALIAS = "EMPLOYEE_KEY"

    fun ensureKey() {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        if (ks.containsAlias(KEY_ALIAS)) return

        val gen = KeyPairGenerator.getInstance(
            KeyProperties.KEY_ALGORITHM_RSA,
            "AndroidKeyStore"
        )

        gen.initialize(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_SIGN or KeyProperties.PURPOSE_VERIFY
            )
                .setDigests(KeyProperties.DIGEST_SHA256)
                .setSignaturePaddings(KeyProperties.SIGNATURE_PADDING_RSA_PKCS1)
                .build()
        )

        gen.generateKeyPair()
    }

    fun sign(data: ByteArray): String {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry

        val sig = Signature.getInstance("SHA256withRSA")
        sig.initSign(entry.privateKey)
        sig.update(data)
        return Base64.encodeToString(sig.sign(), Base64.NO_WRAP)
    }

    fun publicKeyB64(): String {
        val ks = KeyStore.getInstance("AndroidKeyStore")
        ks.load(null)
        val entry = ks.getEntry(KEY_ALIAS, null) as KeyStore.PrivateKeyEntry
        val pubBytes = entry.certificate.publicKey.encoded // DER
        return Base64.encodeToString(pubBytes, Base64.NO_WRAP)
    }
}
