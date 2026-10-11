plugins { alias(libs.plugins.android.library); alias(libs.plugins.kotlin.android); alias(libs.plugins.kotlin.serialization); alias(libs.plugins.ksp) }
android {
    namespace = "app.sloplex.core.data"
    compileSdk = 35
    defaultConfig { minSdk = 26; testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }

}
kotlin { jvmToolchain(17) }
dependencies {
    api(libs.coroutines)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    api(project(":core:network"))
    implementation(libs.room)
    implementation(libs.room.ktx)
    ksp(libs.room.compiler)
    implementation(libs.datastore)
    api(libs.hilt)
    ksp(libs.hilt.compiler)
}
ksp { arg("room.schemaLocation", "$projectDir/schemas") }
