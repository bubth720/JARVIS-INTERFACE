// VOICE pocketRuntime compiled UNCHANGED from the pinned JARVIS-VOICE checkout (voiceRoot, commit in sources.json).
plugins { id("org.jetbrains.kotlin.jvm") }
val voiceRoot = providers.gradleProperty("voiceRoot").get()
java { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
kotlin { compilerOptions { jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17) } }
sourceSets["main"].kotlin.srcDir("$voiceRoot/pocketRuntime/src/main/kotlin")
sourceSets["main"].resources.srcDir("$voiceRoot/pocketRuntime/src/main/resources")
dependencies {
    compileOnly("com.microsoft.onnxruntime:onnxruntime:1.27.0")
    compileOnly("org.json:json:20250517")
}
