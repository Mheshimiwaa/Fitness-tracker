// Root build file.
//
// `apply false` means: make these plugin versions available to the modules,
// but do not apply them to the root project itself. The :app module applies
// the ones it actually needs.
//
// NOTE: there is deliberately no `kotlin-android` plugin here. AGP 9 compiles
// Kotlin itself ("built-in Kotlin"), so applying `org.jetbrains.kotlin.android`
// is now an error. We only declare the extra Kotlin *compiler* plugins we use.
plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.kotlin.serialization) apply false
}
