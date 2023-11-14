package com.parsely.parselyandroid

import android.content.Context
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

internal fun interface IdProvider {
    fun provide(): String?
}
