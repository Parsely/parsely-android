package com.parsely.parselyandroid

import android.content.Context
import android.net.ConnectivityManager

internal interface ConnectivityStatusProvider {
    /**
     * @return Whether the network is accessible.
     */
    fun isReachable(): Boolean
}

internal class AndroidConnectivityStatusProvider(private val context: Context):
    ConnectivityStatusProvider {

    override fun isReachable(): Boolean {
        val cm = context.getSystemService(
            Context.CONNECTIVITY_SERVICE
        ) as ConnectivityManager
        val netInfo = cm.activeNetworkInfo
        return netInfo != null && netInfo.isConnectedOrConnecting
    }
}
