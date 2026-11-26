plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
// a02Root: JARVIS-LLM at the SHA pinned in sources.json; labSources: the nine CORE/INTERFACE blobs.
val a02Root = providers.gradleProperty("a02Root").get()
android {
    namespace = "fr.jarvis.v1"
    compileSdk = 35
    ndkVersion = "28.1.13356709"
    defaultConfig {
        applicationId = "fr.jarvis.v1"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0.0"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk { abiFilters += providers.gradleProperty("v1Abis").getOrElse("arm64-v8a").split(',') }
        externalNativeBuild { cmake { targets += "jarvis_llm_a02"; arguments += listOf("-DLLAMA_SOURCE_DIR=$a02Root/.deps/llama.cpp", "-DANDROID_PLATFORM=android-29") } }
    }
    externalNativeBuild { cmake { path = file("$a02Root/a02/native/CMakeLists.txt"); version = "3.22.1" } }
    buildFeatures { compose = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    sourceSets.getByName("main").java.srcDir(providers.gradleProperty("labSources").get())
    testOptions { unitTests.isReturnDefaultValues = true }
    packaging { jniLibs { useLegacyPackaging = false }; resources { excludes += setOf("META-INF/*.md", "META-INF/LICENSE*", "META-INF/NOTICE*") } }
    lint { abortOnError = true; disable += setOf("MissingTranslation") }
}
dependencies {
    implementation(project(":stt"))
    implementation(project(":pocket"))
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.27.0")
    val bom = platform("androidx.compose:compose-bom:2025.04.01")
    implementation(bom); androidTestImplementation(bom)
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.foundation:foundation")
    implementation("androidx.compose.material3:material3")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.10.2")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.10.2")
    androidTestImplementation("androidx.test:runner:1.6.2")
    androidTestImplementation("androidx.test:core-ktx:1.6.1")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test.uiautomator:uiautomator:2.3.0")
}
