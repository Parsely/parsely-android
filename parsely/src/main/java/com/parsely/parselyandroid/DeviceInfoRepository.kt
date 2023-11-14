package com.parsely.parselyandroid

import android.os.Build

internal interface DeviceInfoRepository{
    fun collectDeviceInfo(): Map<String, String>
}

internal open class AndroidDeviceInfoRepository(
    private val advertisementIdProvider: IdProvider,
    private val uuidProvider: IdProvider,
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
        dInfo["os_version"] = String.format("%d", Build.VERSION.SDK_INT)

        return dInfo
    }

    private val parselySiteUuid: String
        get() {
            val adKey = advertisementIdProvider.provide()
            val siteUuid = uuidProvider.provide()

            ParselyTracker.PLog("adkey is: %s, uuid is %s", adKey, siteUuid)

            return if (adKey != null) {
                adKey
            } else {
                ParselyTracker.PLog("falling back to device uuid")
                siteUuid .orEmpty()
            }
        }
}
