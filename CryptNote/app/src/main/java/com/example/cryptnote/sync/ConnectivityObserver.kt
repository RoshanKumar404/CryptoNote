package com.example.cryptnote.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors network availability using [ConnectivityManager.NetworkCallback].
 * Automatically schedules an immediate sync whenever an active Internet connection is restored.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncScheduler: SyncScheduler
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            // Trigger immediate sync task via WorkManager
            syncScheduler.scheduleImmediate()
        }
    }

    /**
     * Registers the network callback to start listening for connectivity changes.
     */
    fun startObserving() {
        if (isRegistered || connectivityManager == null) return

        try {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback)
            isRegistered = true
        } catch (_: Exception) {}
    }

    /**
     * Unregisters the network callback when the process is terminating.
     */
    fun stopObserving() {
        if (!isRegistered || connectivityManager == null) return

        try {
            connectivityManager.unregisterNetworkCallback(networkCallback)
            isRegistered = false
        } catch (_: Exception) {}
    }
}
