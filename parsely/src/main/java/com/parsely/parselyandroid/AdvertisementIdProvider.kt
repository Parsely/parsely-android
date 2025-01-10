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
                adKey = AdvertisingIdClient.getAdvertisingIdInfo(context).id
            } catch (e: Exception) {
                Log.e("No Google play services or error!", e)
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
        Log.d("Android ID: $uuid")
        return uuid
    }
}

internal fun interface IdProvider {
    fun provide(): String?
}
