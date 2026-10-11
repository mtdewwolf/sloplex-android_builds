plugins { alias(libs.plugins.android.application); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.compose); alias(libs.plugins.hilt); alias(libs.plugins.ksp) }
android {
    namespace = "app.sloplex.mobile"
    compileSdk = 35
    defaultConfig {
        applicationId = "app.sloplex.mobile"
        minSdk = 26
        targetSdk = 35
        versionCode = providers.gradleProperty("sloplexVersionCode").orElse("1").get().toInt()
        versionName = providers.gradleProperty("sloplexVersionName").orElse("0.1.0-alpha01").get()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    buildFeatures { compose = true }
    buildTypes { release { isMinifyEnabled = true; proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro") } }
}
kotlin { jvmToolchain(17) }
dependencies {
    implementation(project(":ui:mobile"))
    implementation(platform(libs.compose.bom))
    implementation(libs.activity.compose)
    implementation(libs.hilt)
    ksp(libs.hilt.compiler)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.test)
    androidTestImplementation(libs.android.test.junit)
    androidTestImplementation(libs.android.test.runner)
    androidTestImplementation(libs.mockwebserver)
    androidTestImplementation(project(":core:data"))
    androidTestImplementation(project(":core:player"))
    androidTestImplementation(libs.room)
}
