package com.example.cryptnote

import android.app.Application
import dagger.hilt.android.HiltAndroidApp

/**
 * CryptNote Application class.
 * Annotated with @HiltAndroidApp to trigger Hilt's code generation
 * and serve as the application-level dependency container.
 */
@HiltAndroidApp
class CryptNoteApp : Application()
