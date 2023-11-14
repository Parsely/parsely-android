package com.parsely.parselyandroid

import android.content.Context
import android.content.SharedPreferences
import android.provider.Settings
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class AdvertisementIdProvider(
    private val context: Context,
    coroutineScope: CoroutineScope
) : IdProvider {

    private var adKey: String? = null

    init {
        coroutineScope.launch {
            try {
                val idInfo = AdvertisingIdClient.getAdvertisingIdInfo(context)
                idInfo.id
            } catch (e: Exception) {
                ParselyTracker.PLog("No Google play services or error!")
            }
        }
    }

    override fun provide(): String? = adKey
}

internal class UuidProvider(private val context: Context) : IdProvider {
    private val settings: SharedPreferences = context.getSharedPreferences("parsely-prefs", 0)

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

    override fun provide(): String? {
        return siteUuid
    }

    companion object {
        private const val UUID_KEY = "parsely-uuid"
    }

}

internal fun interface IdProvider {
    fun provide(): String?
}
