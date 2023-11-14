package com.parsely.parselyandroid

import android.content.Context
import android.content.SharedPreferences
import android.os.Build
import android.provider.Settings

internal interface DeviceInfoRepository{
    fun collectDeviceInfo(): Map<String, String>
}

internal open class AndroidDeviceInfoRepository(
    private val context: Context,
    private val advertisementIdProvider: IdProvider
): DeviceInfoRepository {
    private val settings: SharedPreferences = context.getSharedPreferences("parsely-prefs", 0)

    /**
     * Collect device-specific info.
     *
     *
     * Collects info about the device and user to use in Parsely events.
     */
    override fun collectDeviceInfo(): Map<String, String> {
        val dInfo: MutableMap<String, String> = HashMap()

        // TODO: screen dimensions (maybe?)
        dInfo["parsely_site_uuid"] = parselySiteUuid
        dInfo["manufacturer"] = Build.MANUFACTURER
        dInfo["os"] = "android"
        dInfo["os_version"] = String.format("%d", Build.VERSION.SDK_INT)

        return dInfo
    }

    private val parselySiteUuid: String
        get() {
            val adKey = advertisementIdProvider.provide()

            ParselyTracker.PLog("adkey is: %s, uuid is %s", adKey, siteUuid)

            return if (adKey != null) {
                adKey
            } else {
                ParselyTracker.PLog("falling back to device uuid")
                siteUuid .orEmpty()
            }
        }

    private val siteUuid: String?
        /**
         * Get the UUID for this user.
         */
        get() {
            var uuid: String? = ""
            try {
                uuid = settings.getString(UUID_KEY, "")
                if (uuid == "") {
                    uuid = generateSiteUuid()
                }
            } catch (ex: Exception) {
                ParselyTracker.PLog(
                    "Exception caught during site uuid generation: %s",
                    ex.toString()
                )
            }
            return uuid
        }

    /**
     * Read the Parsely UUID from application context or make a new one.
     *
     * @return The UUID to use for this user.
     */
    private fun generateSiteUuid(): String {
        val uuid = Settings.Secure.getString(
            context.applicationContext.contentResolver,
            Settings.Secure.ANDROID_ID
        )
        ParselyTracker.PLog(String.format("Generated UUID: %s", uuid))
        return uuid
    }

    companion object {
        private const val UUID_KEY = "parsely-uuid"
    }
}
