package com.parsely.parselyandroid

import android.content.Context
import android.content.SharedPreferences
import android.os.AsyncTask
import android.os.Build
import android.provider.Settings
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.google.android.gms.common.GooglePlayServicesNotAvailableException
import com.google.android.gms.common.GooglePlayServicesRepairableException
import java.io.IOException
import kotlinx.coroutines.launch

internal interface DeviceInfoRepository{
    fun collectDeviceInfo(): Map<String, String>
}

internal open class AndroidDeviceInfoRepository(private val context: Context): DeviceInfoRepository {
    private var adKey: String? = null
    private val settings: SharedPreferences = context.getSharedPreferences("parsely-prefs", 0)

    init {
        GetAdKey(context).execute()
    }

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

        // FIXME: Not passed in event or used anywhere else.
        val txt = context.packageManager.getApplicationLabel(context.applicationInfo)
        dInfo["appname"] = txt.toString()
        return dInfo
    }

    private val parselySiteUuid: String
        get() {
            ParselyTracker.PLog("adkey is: %s, uuid is %s", adKey, siteUuid)
            return if (adKey != null) adKey!! else siteUuid!!
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

    /**
     * Async task to get adKey for this device.
     */
    private inner class GetAdKey(private val mContext: Context) :
        AsyncTask<Void?, Void?, String?>() {
        protected override fun doInBackground(vararg params: Void?): String? {
            var idInfo: AdvertisingIdClient.Info? = null
            var advertId: String? = null
            try {
                idInfo = AdvertisingIdClient.getAdvertisingIdInfo(mContext)
            } catch (e: GooglePlayServicesRepairableException) {
                ParselyTracker.PLog("No Google play services or error! falling back to device uuid")
                // fall back to device uuid on google play errors
                advertId = siteUuid
            } catch (e: IOException) {
                ParselyTracker.PLog("No Google play services or error! falling back to device uuid")
                advertId = siteUuid
            } catch (e: GooglePlayServicesNotAvailableException) {
                ParselyTracker.PLog("No Google play services or error! falling back to device uuid")
                advertId = siteUuid
            } catch (e: IllegalArgumentException) {
                ParselyTracker.PLog("No Google play services or error! falling back to device uuid")
                advertId = siteUuid
            }
            try {
                advertId = idInfo!!.id
            } catch (e: NullPointerException) {
                advertId = siteUuid
            }
            return advertId
        }

        override fun onPostExecute(advertId: String?) {
            adKey = advertId
        }
    }

    companion object {
        private const val UUID_KEY = "parsely-uuid"
    }
}
