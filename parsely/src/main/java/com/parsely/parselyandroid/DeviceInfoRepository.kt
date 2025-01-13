package com.parsely.parselyandroid

import android.os.Build

internal interface DeviceInfoRepository{
    fun collectDeviceInfo(): Map<String, String>
}

internal open class AndroidDeviceInfoRepository(
    private val advertisementIdProvider: IdProvider,
    private val androidIdProvider: IdProvider,
): DeviceInfoRepository {

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
        dInfo["os_version"] = Build.VERSION.SDK_INT.toString()

        return dInfo
    }

    private val parselySiteUuid: String
        get() {
            val adKey = advertisementIdProvider.provide()
            val androidId = androidIdProvider.provide()

            Log.d("adkey is: $adKey, uuid is $androidId")

            return if (adKey != null) {
                adKey
            } else {
                Log.d("falling back to device uuid")
                androidId .orEmpty()
            }
        }
}
