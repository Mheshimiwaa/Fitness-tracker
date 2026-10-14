# ---------------------------------------------------------------------------
# R8 / ProGuard rules for release builds.
#
# Minification is OFF for the MVP (see `isMinifyEnabled = false` in
# app/build.gradle.kts), so these rules are not active yet. The file must still
# exist because AGP 9 fails the build if a declared ProGuard file is missing.
#
# When minification is enabled later, add keep rules for reflection-based
# libraries (for example Retrofit / Kotlinx Serialization models).
# ---------------------------------------------------------------------------
