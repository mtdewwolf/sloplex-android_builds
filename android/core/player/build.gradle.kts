plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization) }
android {
    namespace = "app.sloplex.core.player"
    compileSdk = 35
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

}
kotlin { jvmToolchain(17) }
dependencies {
    api(libs.coroutines)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    api(project(":core:data"))
    api(libs.media3.exoplayer)
    implementation(libs.media3.hls)
    implementation(libs.media3.okhttp)
    api(libs.media3.ui)
    implementation(libs.media3.session)
}
