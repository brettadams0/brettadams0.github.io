package dev.cue.data.crypto

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/**
 * §10. The SQLCipher passphrase, wrapped by a key that never leaves the
 * Keystore.
 *
 * The passphrase itself is random bytes generated once. It is stored encrypted
 * under an AES key held in the Android Keystore, which on most devices means
 * hardware-backed and on all devices means non-exportable — so a copy of the
 * app's data directory is not enough to open the database.
 *
 * No user passphrase and no biometric prompt. Cue is a keyboard-adjacent tool
 * used mid-conversation; a lock screen in front of the drafts would be a lock
 * screen behind the phone's own, and the failure it protects against (someone
 * holding your unlocked phone) is not one this app can address.
 */
class DatabaseKey(private val context: Context) {

    fun passphrase(): ByteArray {
        val preferences = context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE)
        val stored = preferences.getString(KEY_WRAPPED, null)
        val storedIv = preferences.getString(KEY_IV, null)

        if (stored != null && storedIv != null) {
            return unwrap(
                wrapped = Base64.decode(stored, Base64.NO_WRAP),
                iv = Base64.decode(storedIv, Base64.NO_WRAP),
            )
        }

        val fresh = ByteArray(PASSPHRASE_BYTES).also { java.security.SecureRandom().nextBytes(it) }
        val cipher = Cipher.getInstance(TRANSFORMATION).apply { init(Cipher.ENCRYPT_MODE, wrappingKey()) }
        val wrapped = cipher.doFinal(fresh)

        preferences.edit()
            .putString(KEY_WRAPPED, Base64.encodeToString(wrapped, Base64.NO_WRAP))
            .putString(KEY_IV, Base64.encodeToString(cipher.iv, Base64.NO_WRAP))
            .commit()
        return fresh
    }

    private fun unwrap(wrapped: ByteArray, iv: ByteArray): ByteArray {
        val cipher = Cipher.getInstance(TRANSFORMATION).apply {
            init(Cipher.DECRYPT_MODE, wrappingKey(), GCMParameterSpec(GCM_TAG_BITS, iv))
        }
        return cipher.doFinal(wrapped)
    }

    private fun wrappingKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getEntry(KEY_ALIAS, null) as? KeyStore.SecretKeyEntry)?.let { return it.secretKey }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        generator.init(
            KeyGenParameterSpec.Builder(
                KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT,
            )
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                // Deliberately not setUserAuthenticationRequired: see the class
                // comment. Drafting happens while the phone is already unlocked.
                .build(),
        )
        return generator.generateKey()
    }

    private companion object {
        const val KEYSTORE = "AndroidKeyStore"
        const val KEY_ALIAS = "cue.db.wrapping"
        const val TRANSFORMATION = "AES/GCM/NoPadding"
        const val PREFERENCES = "cue.crypto"
        const val KEY_WRAPPED = "wrapped"
        const val KEY_IV = "iv"
        const val PASSPHRASE_BYTES = 32
        const val GCM_TAG_BITS = 128
    }
}
