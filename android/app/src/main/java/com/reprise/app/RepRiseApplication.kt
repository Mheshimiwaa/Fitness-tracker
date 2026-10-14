package com.reprise.app

import android.app.Application
import com.reprise.app.di.AppModule

/**
 * The app's [Application] subclass — the first object Android creates.
 *
 * Its only job is to build the dependency container before any Activity starts,
 * so `AppModule.authRepository` is ready by the time the UI asks for it.
 *
 * Registered in AndroidManifest.xml via `android:name=".RepRiseApplication"`.
 */
class RepRiseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
    }
}
