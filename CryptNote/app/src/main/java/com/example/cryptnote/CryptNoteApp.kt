package com.example.cryptnote

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.example.cryptnote.sync.ConnectivityObserver
import com.example.cryptnote.sync.SyncScheduler
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

/**
 * CryptNote Application class.
 * Annotated with @HiltAndroidApp to trigger Hilt's code generation
 * and serve as the application-level dependency container.
 *
 * Implements [Configuration.Provider] to wire HiltWorkerFactory into WorkManager.
 */
@HiltAndroidApp
class CryptNoteApp : Application(), Configuration.Provider {

    @Inject
    lateinit var workerFactory: HiltWorkerFactory

    @Inject
    lateinit var syncScheduler: SyncScheduler

    @Inject
    lateinit var connectivityObserver: ConnectivityObserver

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // Schedule periodic sync every 15 minutes
        syncScheduler.schedulePeriodic()

        // Start observing network connectivity to trigger immediate sync on reconnect
        connectivityObserver.startObserving()
    }
}
