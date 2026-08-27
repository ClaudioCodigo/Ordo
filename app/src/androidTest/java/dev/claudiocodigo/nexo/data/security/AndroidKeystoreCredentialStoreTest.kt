package dev.claudiocodigo.nexo.data.security

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.preferencesDataStoreFile
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Verifies that the Keystore-backed credential store can cipher/de-cipher the
 * application password and that disconnecting deletes the secret and the
 * account identity without crashing (AUT-04, AUT-06).
 */
@RunWith(AndroidJUnit4::class)
class AndroidKeystoreCredentialStoreTest {

    private lateinit var context: Context
    private lateinit var dataStore: DataStore<Preferences>
    private lateinit var store: AndroidKeystoreCredentialStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        dataStore = PreferenceDataStoreFactory.create(scope = scope) {
            context.preferencesDataStoreFile("test-nexo-secure")
        }
        store = AndroidKeystoreCredentialStore(context, dataStore)
    }

    @Test
    fun savesAndReadsTheAppPasswordAndAccount() = runBlocking {
        store.saveAccount("https://cloud.example.com", "maria")
        store.saveAppPassword("secret".toCharArray())

        val account = store.readAccount()
        assertEquals("maria", account?.user)
        assertEquals("https://cloud.example.com", account?.server)
        assertEquals("secret", String(store.readAppPassword()!!))
    }

    @Test
    fun disconnectDeletesSecretAndAccount() = runBlocking {
        store.saveAccount("https://cloud.example.com", "maria")
        store.saveAppPassword("secret".toCharArray())

        store.clear()

        assertNull(store.readAccount())
        assertNull(store.readAppPassword())
    }

    @Test
    fun hasAccountReflectsConfiguredState() = runBlocking {
        repeat(2) {
            store.clear()
        }
        assertEquals(false, store.hasAccount().first())
        store.saveAccount("https://cloud.example.com", "maria")
        assertEquals(true, store.hasAccount().first())
    }
}