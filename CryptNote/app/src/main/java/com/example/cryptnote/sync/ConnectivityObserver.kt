package com.example.cryptnote.sync

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Monitors default network availability using [ConnectivityManager.registerDefaultNetworkCallback].
 * Automatically schedules an immediate sync whenever an active Internet connection is restored.
 * Exposes a reactive [isOnline] StateFlow for UI status hints and animations.
 */
@Singleton
class ConnectivityObserver @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncScheduler: SyncScheduler
) {
    private val connectivityManager =
        context.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager

    private val _isOnline = MutableStateFlow(checkCurrentConnectivity())
    val isOnline: StateFlow<Boolean> = _isOnline.asStateFlow()

    private var isRegistered = false

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(network: Network) {
            super.onAvailable(network)
            _isOnline.value = true
            // Trigger immediate sync task via WorkManager
            syncScheduler.scheduleImmediate()
        }

        override fun onLost(network: Network) {
            super.onLost(network)
            _isOnline.value = false
        }

        override fun onCapabilitiesChanged(
            network: Network,
            networkCapabilities: NetworkCapabilities
        ) {
            super.onCapabilitiesChanged(network, networkCapabilities)
            val hasInternet = networkCapabilities.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            _isOnline.value = hasInternet
        }
    }

    init {
        startObserving()
    }

    private fun checkCurrentConnectivity(): Boolean {
        val cm = connectivityManager ?: return false
        val active = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(active) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    /**
     * Registers the default network callback to track device-wide connectivity.
     */
    fun startObserving() {
        if (isRegistered || connectivityManager == null) return

        try {
            // registerDefaultNetworkCallback is available on API 24+ (minSdk is 26)
            connectivityManager.registerDefaultNetworkCallback(networkCallback)
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
