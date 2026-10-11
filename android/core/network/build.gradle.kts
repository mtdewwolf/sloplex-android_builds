plugins { alias(libs.plugins.kotlin.jvm); alias(libs.plugins.kotlin.serialization) }
kotlin { jvmToolchain(17) }
dependencies {
    api(libs.serialization)
    testImplementation(libs.junit)
    testImplementation(libs.coroutines.test)
    api(project(":core:model")); api(libs.retrofit); api(libs.okhttp); implementation(libs.retrofit.serialization); testImplementation(libs.mockwebserver)
}
