package dev.claudiocodigo.nexo.data.security

import android.content.Context
import android.content.SharedPreferences
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import androidx.core.content.edit
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.claudiocodigo.nexo.domain.caldav.AccountIdentity
import dev.claudiocodigo.nexo.domain.caldav.CredentialStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Stores the Nextcloud account and protects the application password with a
 * non-exportable AES-GCM key held in the Android Keystore.
 *
 * Security rules enforced here:
 * - the plaintext password is never persisted; only IV + ciphertext (base64);
 * - the ciphertext lives in a dedicated prefs file excluded from backup;
 * - a missing/corrupted key or blob returns `null` instead of crashing;
 * - disconnecting deletes the key, the blob and the account identity, and never
 *   touches independent local drafts (which are Room, not this store).
 */
@Singleton
class AndroidKeystoreCredentialStore @Inject constructor(
    @ApplicationContext private val context: Context,
    private val dataStore: DataStore<Preferences>
) : CredentialStore {

    private val secretPrefs: SharedPreferences =
        context.getSharedPreferences(SECRET_PREFS_NAME, Context.MODE_PRIVATE)

    private object Keys {
        val SERVER = stringPreferencesKey("server")
        val USER = stringPreferencesKey("user")
    }

    override suspend fun saveAccount(server: String, user: String) {
        dataStore.edit { prefs ->
            prefs[Keys.SERVER] = server
            prefs[Keys.USER] = user
        }
    }

    override suspend fun readAccount(): AccountIdentity? {
        val prefs = dataStore.data.first()
        val server = prefs[Keys.SERVER] ?: return null
        val user = prefs[Keys.USER]
        return AccountIdentity(server = server, user = user ?: "")
    }

    override suspend fun saveAppPassword(password: CharArray) {
        val blob = encrypt(password)
        secretPrefs.edit { putString(BLOB_KEY, blob) }
    }

    override suspend fun readAppPassword(): CharArray? {
        val blob = secretPrefs.getString(BLOB_KEY, null) ?: return null
        return decrypt(blob)?.toCharArray()
    }

    override suspend fun clear() {
        dataStore.edit { it.remove(Keys.SERVER); it.remove(Keys.USER) }
        secretPrefs.edit { remove(BLOB_KEY) }
        deleteKey()
    }

    override fun hasAccount(): Flow<Boolean> =
        dataStore.data.map { it[Keys.SERVER] != null }

    override fun observeAccount(): Flow<AccountIdentity?> =
        dataStore.data.map { prefs ->
            val server = prefs[Keys.SERVER] ?: return@map null
            AccountIdentity(server = server, user = prefs[Keys.USER] ?: "")
        }

    private fun encrypt(password: CharArray): String {
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey())
        val iv = cipher.iv
        val ciphertext = cipher.doFinal(String(password).toByteArray(Charsets.UTF_8))
        val blob = ByteArray(iv.size + ciphertext.size)
        iv.copyInto(blob, 0)
        ciphertext.copyInto(blob, iv.size)
        return Base64.encodeToString(blob, Base64.NO_WRAP)
    }

    private fun decrypt(blobBase64: String): String? = try {
        val blob = Base64.decode(blobBase64, Base64.NO_WRAP)
        val iv = blob.copyOfRange(0, GCM_IV_SIZE)
        val ciphertext = blob.copyOfRange(GCM_IV_SIZE, blob.size)
        val cipher = Cipher.getInstance(TRANSFORMATION)
        cipher.init(Cipher.DECRYPT_MODE, secretKey(), GCMParameterSpec(128, iv))
        String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    } catch (_: Exception) {
        // Missing key, corrupted blob or tampered tag: treat as "no account",
        // never crash, and let the UI prompt to reconnect.
        null
    }

    private fun secretKey(): SecretKey {
        val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
        (keyStore.getKey(KEY_ALIAS, null) as? SecretKey)?.let { return it }

        val generator = KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, KEYSTORE)
        val spec = KeyGenParameterSpec.Builder(
            KEY_ALIAS,
            KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT
        )
            .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
            .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
            .setKeySize(256)
            .build()
        generator.init(spec)
        return generator.generateKey()
    }

    private fun deleteKey() {
        runCatching {
            val keyStore = KeyStore.getInstance(KEYSTORE).apply { load(null) }
            keyStore.deleteEntry(KEY_ALIAS)
        }
    }

    companion object {
        private const val KEYSTORE = "AndroidKeyStore"
        private const val KEY_ALIAS = "nexo_nextcloud_password"
        private const val TRANSFORMATION = "AES/GCM/NoPadding"
        private const val GCM_IV_SIZE = 12
        private const val SECRET_PREFS_NAME = "nexo_secure"
        private const val BLOB_KEY = "password_blob"
    }
}
