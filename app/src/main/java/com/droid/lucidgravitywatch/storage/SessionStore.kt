package com.droid.lucidgravitywatch.storage

import android.content.Context
import android.content.SharedPreferences
import androidx.core.content.edit
import com.droid.lucidgravitywatch.model.LucidRegion
import com.droid.lucidgravitywatch.model.LucidSession
import java.util.UUID

interface SessionStore {
    fun saveSession(session: LucidSession)
    fun loadSession(): LucidSession?
    fun clearSession()
    fun getDeviceId(): String
    fun getSelectedVehicleId(): String?
    fun setSelectedVehicleId(vehicleId: String?)
    fun isFahrenheitPreferred(): Boolean
    fun setFahrenheitPreferred(fahrenheit: Boolean)
}

class SharedPreferencesSessionStore(context: Context) : SessionStore {
    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun saveSession(session: LucidSession) {
        prefs.edit {
            putString(KEY_UID, session.uid)
            putString(KEY_ID_TOKEN, session.idToken)
            putString(KEY_REFRESH_TOKEN, session.refreshToken)
            putString(KEY_GIGYA_JWT, session.gigyaJwt)
            putInt(KEY_EXPIRY_SEC, session.expiryTimeSec)
            putString(KEY_EMAIL, session.userEmail)
            putString(KEY_FIRST_NAME, session.firstName)
            putString(KEY_LAST_NAME, session.lastName)
            putString(KEY_REGION, session.region.code)
        }
    }

    override fun loadSession(): LucidSession? {
        val idToken = prefs.getString(KEY_ID_TOKEN, null) ?: return null
        if (idToken.isBlank()) return null
        return LucidSession(
            uid = prefs.getString(KEY_UID, "").orEmpty(),
            idToken = idToken,
            refreshToken = prefs.getString(KEY_REFRESH_TOKEN, "").orEmpty(),
            gigyaJwt = prefs.getString(KEY_GIGYA_JWT, "").orEmpty(),
            expiryTimeSec = prefs.getInt(KEY_EXPIRY_SEC, 0),
            userEmail = prefs.getString(KEY_EMAIL, "").orEmpty(),
            firstName = prefs.getString(KEY_FIRST_NAME, "").orEmpty(),
            lastName = prefs.getString(KEY_LAST_NAME, "").orEmpty(),
            region = LucidRegion.fromCode(prefs.getString(KEY_REGION, LucidRegion.US.code))
        )
    }

    override fun clearSession() {
        val deviceId = getDeviceId()
        val useF = isFahrenheitPreferred()
        prefs.edit {
            clear()
            putString(KEY_DEVICE_ID, deviceId)
            putBoolean(KEY_USE_FAHRENHEIT, useF)
        }
    }

    override fun getDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val generated = UUID.randomUUID().toString().replace("-", "").take(16)
        prefs.edit { putString(KEY_DEVICE_ID, generated) }
        return generated
    }

    override fun getSelectedVehicleId(): String? = prefs.getString(KEY_SELECTED_VEHICLE_ID, null)

    override fun setSelectedVehicleId(vehicleId: String?) {
        prefs.edit { putString(KEY_SELECTED_VEHICLE_ID, vehicleId) }
    }

    override fun isFahrenheitPreferred(): Boolean = prefs.getBoolean(KEY_USE_FAHRENHEIT, true)

    override fun setFahrenheitPreferred(fahrenheit: Boolean) {
        prefs.edit { putBoolean(KEY_USE_FAHRENHEIT, fahrenheit) }
    }

    companion object {
        private const val PREFS_NAME = "lucid_gravity_watch_session"
        private const val KEY_UID = "uid"
        private const val KEY_ID_TOKEN = "id_token"
        private const val KEY_REFRESH_TOKEN = "refresh_token"
        private const val KEY_GIGYA_JWT = "gigya_jwt"
        private const val KEY_EXPIRY_SEC = "expiry_time_sec"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_FIRST_NAME = "first_name"
        private const val KEY_LAST_NAME = "last_name"
        private const val KEY_REGION = "region"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_SELECTED_VEHICLE_ID = "selected_vehicle_id"
        private const val KEY_USE_FAHRENHEIT = "use_fahrenheit"
    }
}

class InMemorySessionStore : SessionStore {
    private var session: LucidSession? = null
    private var deviceId: String = "a1b2c3d4e5f60001"
    private var selectedVehicleId: String? = null
    private var useFahrenheit: Boolean = true

    override fun saveSession(session: LucidSession) {
        this.session = session
    }

    override fun loadSession(): LucidSession? = session

    override fun clearSession() {
        session = null
        selectedVehicleId = null
    }

    override fun getDeviceId(): String = deviceId

    override fun getSelectedVehicleId(): String? = selectedVehicleId

    override fun setSelectedVehicleId(vehicleId: String?) {
        selectedVehicleId = vehicleId
    }

    override fun isFahrenheitPreferred(): Boolean = useFahrenheit

    override fun setFahrenheitPreferred(fahrenheit: Boolean) {
        useFahrenheit = fahrenheit
    }
}
