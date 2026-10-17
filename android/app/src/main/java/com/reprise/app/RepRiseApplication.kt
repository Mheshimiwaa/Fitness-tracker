package com.reprise.app

import android.app.Application
import coil3.ImageLoader
import coil3.SingletonImageLoader
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.util.DebugLogger
import com.reprise.app.di.AppModule

/**
 * The app's [Application] subclass — the first object Android creates.
 *
 * It builds the dependency container before any Activity starts, so
 * `AppModule.authRepository` is ready by the time the UI asks for it, and it
 * configures the image loader, which must be done once for the whole process.
 *
 * Registered in AndroidManifest.xml via `android:name=".RepRiseApplication"`.
 */
class RepRiseApplication : Application() {

    override fun onCreate() {
        super.onCreate()
        AppModule.init(this)
        configureImageLoading()
    }

    /**
     * Teach Coil how to fetch a profile photo over the network.
     *
     * Coil can discover a network layer by itself, but doing it explicitly means
     * the app does not depend on that discovery working — if the fetcher were
     * ever missing, every avatar would silently fall back to an initial, which
     * is a hard thing to notice. Stating it here makes a broken dependency a
     * build error instead.
     *
     * The client Coil uses is its own OkHttp instance rather than the app's
     * authenticated one: profile photos are served without a token, so there is
     * no reason to send the user's credentials along with an image request.
     */
    private fun configureImageLoading() {
        SingletonImageLoader.setSafe { context ->
            ImageLoader.Builder(context)
                .components { add(OkHttpNetworkFetcherFactory()) }
                // Debug builds log why an image did or did not load; release
                // builds stay quiet.
                .apply { if (BuildConfig.DEBUG) logger(DebugLogger()) }
                .build()
        }
    }
}
