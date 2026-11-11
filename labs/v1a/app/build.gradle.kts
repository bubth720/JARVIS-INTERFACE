plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}
// a02Root: checkout of bubth720/JARVIS-LLM at the SHA pinned in sources.json (a02/native + .deps/llama.cpp).
val a02Root = providers.gradleProperty("a02Root").get()
android {
    namespace = "fr.jarvis.v1a"
    compileSdk = 35
    ndkVersion = "28.1.13356709"
    defaultConfig {
        applicationId = "fr.jarvis.v1a.lab"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "V1A-LAB-0.1"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += providers.gradleProperty("v1aAbis").getOrElse("arm64-v8a").split(',') }
        externalNativeBuild { cmake { targets += "jarvis_llm_a02"; arguments += listOf("-DLLAMA_SOURCE_DIR=$a02Root/.deps/llama.cpp", "-DANDROID_PLATFORM=android-29") } }
    }
    externalNativeBuild { cmake { path = file("$a02Root/a02/native/CMakeLists.txt"); version = "3.22.1" } }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").java.srcDir(providers.gradleProperty("labSources").get())
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { jniLibs { useLegacyPackaging = false } }
    lint { abortOnError = true }
}
dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
}
