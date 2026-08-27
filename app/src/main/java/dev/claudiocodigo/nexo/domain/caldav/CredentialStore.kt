package dev.claudiocodigo.nexo.domain.caldav

import kotlinx.coroutines.flow.Flow

/**
 * Stores and retrieves the Nextcloud account material used by the CalDAV
 * clients. The application password is handled as a [CharArray] so it never
 * becomes an immutable [String] in memory or in logs.
 *
 * Implementations must protect the password with Android Keystore on device,
 * and must be able to report a corrupted/unavailable key without crashing.
 */
interface CredentialStore {
    /** Persists the non-secret account identity (server and user). */
    suspend fun saveAccount(server: String, user: String)

    /** Reads server and user, or `null` when not configured. */
    suspend fun readAccount(): AccountIdentity?

    /** Ciphers and persists the application password. */
    suspend fun saveAppPassword(password: CharArray)

    /** Deciphers and returns the application password, or `null` when absent. */
    suspend fun readAppPassword(): CharArray?

    /**
     * Deletes the secret, its ciphertext/IV and the non-secret identity.
     * Independent local drafts are never touched by this operation.
     */
    suspend fun clear()

    /** Whether an account is currently configured (non-secret, safe to expose). */
    fun hasAccount(): Flow<Boolean>

    /** Emits the currently configured non-secret account identity, or `null`. */
    fun observeAccount(): Flow<AccountIdentity?>
}

data class AccountIdentity(
    val server: String,
    val user: String
)
