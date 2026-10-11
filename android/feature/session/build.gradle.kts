plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.ksp); alias(libs.plugins.kotlin.compose) }
android {
    namespace = "app.sloplex.feature.session"
    compileSdk = 35
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    api(libs.coroutines)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    api(project(":core:player"))
    api(libs.lifecycle.viewmodel.compose)
    api(libs.lifecycle.runtime.compose)
    api(libs.hilt)
    ksp(libs.hilt.compiler)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.junit)
}
