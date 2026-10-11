plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.kotlin.compose) }
android {
    namespace = "app.sloplex.ui.mobile"
    compileSdk = 35
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(libs.activity.compose)
    api(libs.coroutines)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    api(project(":feature:session"))
    implementation(project(":ui:design"))
    implementation(libs.compose.material3)
    implementation(libs.compose.icons)
    implementation(libs.coil)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.foundation)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.junit)
}
