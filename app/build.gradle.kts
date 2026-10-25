plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
}
android {
    namespace = "com.jarvis.interfaceapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "com.jarvis.interfaceapp"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true }
    providers.gradleProperty("athenaPrivateAssets").orNull?.let { privatePath ->
        require(!providers.environmentVariable("CI").isPresent) { "Private Athena assets forbidden in CI" }
        val folder=file(privatePath).canonicalFile
        require(!folder.toPath().startsWith(rootProject.projectDir.canonicalFile.toPath())) { "Private assets must stay outside the checkout" }
        require(folder.resolve("athena-private/athena-review.zip").isFile)
        require(folder.walkTopDown().filter { it.isFile }.map { it.relativeTo(folder).invariantSeparatorsPath }.toSet()==setOf("athena-private/athena-review.zip")) { "Only the reviewed ZIP may enter the private asset source" }
        sourceSets.getByName("main").assets.srcDir(folder)
    }
    sourceSets.getByName("test").java.srcDir(rootProject.file("fixtures/core02-reference/src"))
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
dependencies {
    implementation(libs.datastore.core)
    implementation(libs.serialization.json)
    implementation(libs.coroutines.android)
    testImplementation(libs.coroutines.test)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation(libs.activity.compose)
    implementation(libs.lifecycle.compose)
    implementation(libs.lifecycle.viewmodel)
    testImplementation(libs.junit)
    androidTestImplementation(platform(libs.compose.bom))
    androidTestImplementation(libs.compose.ui.test)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.runner)
    debugImplementation(libs.compose.test.manifest)
}
