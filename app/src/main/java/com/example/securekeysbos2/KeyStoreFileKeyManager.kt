package com.example.securekeysbos2

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.security.KeyStore
import java.security.SecureRandom
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

class KeyStoreFileKeyManager(context: Context) {
    private val prefs = context.getSharedPreferences("keystore_file_keys", Context.MODE_PRIVATE)
    private val androidKeyStore = "AndroidKeyStore"
    private val alias = "secure_keys_bos2_master"

    fun getOrCreateFileKey(userName: String): ByteArray {
        ensureMasterKey()
        val ivKey = "${userName}_iv"
        val blobKey = "${userName}_blob"
        val blob = prefs.getString(blobKey, null)
        val iv = prefs.getString(ivKey, null)
        if (blob != null && iv != null) return decryptWrapped(blob.fromB64(), iv.fromB64())

        val fileKey = ByteArray(32).also { SecureRandom().nextBytes(it) }
        val (wrapped, newIv) = encryptWrapped(fileKey)
        prefs.edit().putString(blobKey, wrapped.toB64()).putString(ivKey, newIv.toB64()).apply()
        return fileKey
    }

    private fun ensureMasterKey() {
        val ks = KeyStore.getInstance(androidKeyStore).also { it.load(null) }
        if (ks.containsAlias(alias)) return
        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, androidKeyStore)
        generator.init(
            KeyGenParameterSpec.Builder(alias, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .setRandomizedEncryptionRequired(true)
                .build()
        )
        generator.generateKey()
    }

    private fun masterKey(): SecretKey {
        val ks = KeyStore.getInstance(androidKeyStore).also { it.load(null) }
        return ks.getKey(alias, null) as SecretKey
    }

    private fun encryptWrapped(clear: ByteArray): Pair<ByteArray, ByteArray> {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, masterKey())
        return cipher.doFinal(clear) to cipher.iv
    }

    private fun decryptWrapped(blob: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, masterKey(), GCMParameterSpec(128, iv))
        return cipher.doFinal(blob)
    }
}

