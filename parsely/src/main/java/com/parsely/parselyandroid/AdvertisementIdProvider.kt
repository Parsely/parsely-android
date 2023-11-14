package com.parsely.parselyandroid

import android.content.Context
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
        ParselyTracker.PLog(String.format("Android ID: %s", uuid))
        return uuid
    }
}

internal fun interface IdProvider {
    fun provide(): String?
}
