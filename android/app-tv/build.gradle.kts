plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose); alias(libs.plugins.hilt); alias(libs.plugins.ksp) }
android {
    namespace = "app.sloplex.tv"
    compileSdk = 35
    defaultConfig { applicationId = "app.sloplex.tv"; minSdk = 26; targetSdk = 35; versionCode = 1; versionName = "0.1.0-alpha01"; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":ui:tv"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.mockwebserver)
}
