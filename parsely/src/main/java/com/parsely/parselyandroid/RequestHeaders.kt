package com.parsely.parselyandroid

import android.content.Context
import android.os.Build

/**
 * Headers sent with every upload, so edge servers can route by app or site without reading the body.
 */
internal object RequestHeaders {
    const val USER_AGENT = "User-Agent"
    const val APIKEY = "X-Parsely-Apikey"

    fun from(context: Context, siteId: String): Map<String, String> = build(
        appName = context.packageName,
        appVersion = appVersionName(context),
        osVersion = Build.VERSION.RELEASE,
        deviceModel = Build.MODEL,
        siteId = siteId,
    )

    /**
     * Builds `<app>/<version> Android/<os> (<model>) ParselySDK/android/<sdk version>` and, when
     * [siteId] is usable as a header value, an apikey header. [siteId] is the tracker's own site id
     * from init, not each event's `idsite`, so a batch never needs splitting by site.
     */
    fun build(
        appName: String?,
        appVersion: String?,
        osVersion: String?,
        deviceModel: String?,
        siteId: String,
        sdkVersion: String = SDK_VERSION,
    ): Map<String, String> {
        val userAgent = "${token(appName)}/${token(appVersion)} Android/${token(osVersion)} " +
            "(${comment(deviceModel)}) ParselySDK/android/${token(sdkVersion)}"
        val apikey = siteId.trim()
        return if (apikey.isNotEmpty() && apikey.all { it in '!'..'~' }) {
            mapOf(USER_AGENT to userAgent, APIKEY to apikey)
        } else {
            mapOf(USER_AGENT to userAgent)
        }
    }

    private fun appVersionName(context: Context): String? = try {
        @Suppress("DEPRECATION")
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    } catch (ex: Exception) {
        Log.e("Could not read the app version", ex)
        null
    }

    // Android's HttpURLConnection throws on non-ASCII header values, which would fail every upload.
    private fun token(value: String?): String =
        sanitize(value) { (it.isLetterOrDigit() && it.code < 128) || it in "!#$%&'*+-.^_`|~" }

    private fun comment(value: String?): String = sanitize(value) { it in ' '..'~' && it !in "()\\" }

    private fun sanitize(value: String?, allowed: (Char) -> Boolean): String =
        value?.trim()?.map { if (allowed(it)) it else '_' }?.joinToString("")?.ifEmpty { null } ?: "unknown"
}
