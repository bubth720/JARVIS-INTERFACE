plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
// whisperSource: pinned whisper.cpp checkout (tools/prepare_whisper.sh), default labs/v1/.deps/whisper.cpp
val whisperSource = providers.gradleProperty("whisperSource").getOrElse(rootProject.file(".deps/whisper.cpp").absolutePath)
android {
    namespace = "fr.jarvis.v1.stt"
    compileSdk = 35
    ndkVersion = "28.1.13356709"
    defaultConfig {
        minSdk = 29
        ndk { abiFilters += providers.gradleProperty("v1Abis").getOrElse("arm64-v8a").split(',') }
        externalNativeBuild { cmake { targets += "jarvis_stt"; arguments += listOf("-DWHISPER_SOURCE_DIR=$whisperSource", "-DANDROID_PLATFORM=android-29") } }
    }
    externalNativeBuild { cmake { path = file("src/main/cpp/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}
