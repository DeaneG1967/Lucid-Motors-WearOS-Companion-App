package com.droid.lucidgravitywatch.storage

import android.app.Application
import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.LucidSession
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class SharedPreferencesSessionStoreTest {

    private lateinit var context: Context
    private lateinit var sessionStore: SharedPreferencesSessionStore

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        sessionStore = SharedPreferencesSessionStore(context)
        sessionStore.clearSession()
    }

    @Test
    fun saveAndLoadSession_roundTripsAllFields() {
        assertNull(sessionStore.loadSession())

        val deviceId = sessionStore.getDeviceId()
        assertTrue(deviceId.isNotBlank())
        assertEquals(deviceId, sessionStore.getDeviceId())

        val session = LucidSession(
            uid = "uid-gravity-900",
            idToken = "id-token-abc",
            refreshToken = "refresh-token-xyz",
            gigyaJwt = "gigya-token-123",
            expiryTimeSec = 1990000000,
            userEmail = "gravity.owner@lucidmotors.com",
            firstName = "Morgan",
            lastName = "Blake",
            region = LucidRegion.SA
        )
        sessionStore.saveSession(session)
        sessionStore.setSelectedVehicleId("veh-gravity-sa-1")
        sessionStore.setFahrenheitPreferred(false)

        val loaded = sessionStore.loadSession()
        assertNotNull(loaded)
        assertEquals(session, loaded)
        assertEquals("veh-gravity-sa-1", sessionStore.getSelectedVehicleId())
        assertFalse(sessionStore.isFahrenheitPreferred())

        sessionStore.clearSession()
        assertNull(sessionStore.loadSession())
        // Device ID and temperature unit preference remain intact across sign-out
        assertEquals(deviceId, sessionStore.getDeviceId())
        assertFalse(sessionStore.isFahrenheitPreferred())
    }
}
