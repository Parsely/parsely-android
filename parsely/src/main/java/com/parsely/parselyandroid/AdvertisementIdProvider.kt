package com.parsely.parselyandroid

import android.content.Context
import android.provider.Settings
import com.google.android.gms.ads.identifier.AdvertisingIdClient
import com.parsely.parselyandroid.Logging.PLog
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
                adKey = AdvertisingIdClient.getAdvertisingIdInfo(context).id
            } catch (e: Exception) {
                PLog("No Google play services or error!")
            }
        }
    }

    /**
     * @return advertisement id if the coroutine in the constructor finished executing AdvertisingIdClient#getAdvertisingIdInfo
     * null otherwise
     */
    override fun provide(): String? = adKey
}

internal class AndroidIdProvider(private val context: Context) : IdProvider {
    override fun provide(): String? {
        val uuid = try {
            Settings.Secure.getString(
                context.applicationContext.contentResolver,
                Settings.Secure.ANDROID_ID
            )
        } catch (ex: Exception) {
            null
        }
        PLog(String.format("Android ID: %s", uuid))
        return uuid
    }
}

internal fun interface IdProvider {
    fun provide(): String?
}
