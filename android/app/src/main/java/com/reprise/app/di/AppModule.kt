package com.reprise.app.di

import android.content.Context
import com.reprise.app.data.local.SettingsStore
import com.reprise.app.data.local.TokenStore
import com.reprise.app.data.remote.ApiClient
import com.reprise.app.data.repository.AuthRepository
import com.reprise.app.data.repository.FriendRepository
import com.reprise.app.data.repository.ProgressionRepository
import com.reprise.app.data.repository.WeightRepository
import com.reprise.app.data.repository.WorkoutRepository

/**
 * A tiny hand-written dependency container ("service locator").
 *
 * A large app would use Hilt/Dagger, but for a project this size that is a lot
 * of machinery for little benefit. Everything is built ONCE here, in
 * [RepRiseApplication], and the ViewModels read what they need from it.
 *
 * This keeps object creation in a single, obvious place instead of scattering
 * `Retrofit.Builder()` calls through the UI.
 */
object AppModule {

    private var initialized = false

    lateinit var tokenStore: TokenStore
        private set

    lateinit var settingsStore: SettingsStore
        private set

    lateinit var authRepository: AuthRepository
        private set

    lateinit var weightRepository: WeightRepository
        private set

    lateinit var workoutRepository: WorkoutRepository
        private set

    lateinit var progressionRepository: ProgressionRepository
        private set

    lateinit var friendRepository: FriendRepository
        private set

    /** Called from [com.reprise.app.RepRiseApplication]. Safe to call twice. */
    fun init(context: Context) {
        if (initialized) return

        val appContext = context.applicationContext
        tokenStore = TokenStore(appContext)
        settingsStore = SettingsStore(appContext)

        // One Retrofit client, shared by every repository.
        val api = ApiClient.create(tokenStore)
        authRepository = AuthRepository(api = api, tokenStore = tokenStore)
        weightRepository = WeightRepository(api = api)
        workoutRepository = WorkoutRepository(api = api)
        progressionRepository = ProgressionRepository(api = api)
        friendRepository = FriendRepository(api = api)

        initialized = true
    }
}
