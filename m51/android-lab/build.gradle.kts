plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val sourceCommit = providers.environmentVariable("GITHUB_SHA").orElse(provider {
    ProcessBuilder("git", "rev-parse", "HEAD").directory(rootDir).start().inputStream.bufferedReader().use { it.readText().trim() }
}).get().also { require(it.matches(Regex("[0-9a-f]{40}"))) }
val generated = layout.buildDirectory.dir("generated/m51")
val prepareM51Sources by tasks.registering(Exec::class) {
    inputs.dir(rootProject.file("app/src/main/java"))
    inputs.file(rootProject.file("m51/prepare_sources.py"))
    outputs.dir(generated)
    commandLine("python3", rootProject.file("m51/prepare_sources.py"), "--output", generated.get().asFile)
}
android {
    namespace = "com.jarvis.interfaceapp"
    compileSdk = 35
    defaultConfig {
        applicationId = "fr.jarvis.v1.lab"
        minSdk = 26
        targetSdk = 35
        versionCode = 51
        versionName = "0.51.0-m51-lab"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testInstrumentationRunnerArguments["notClass"] = "com.jarvis.interfaceapp.ProcessRestartProbe"
        buildConfigField("String", "SOURCE_COMMIT", "\"$sourceCommit\"")
        buildConfigField("String", "UI_BASE_COMMIT", "\"0dfeef8ff09d60a68d18398ccb6b2530f3b87df4\"")
    }
    flavorDimensions += "composition"
    productFlavors {
        create("baseline") {
            dimension = "composition"
            applicationIdSuffix = ".baseline"
            manifestPlaceholders["labLabel"] = "JARVIS UI01C BASELINE"
        }
        create("candidate") {
            dimension = "composition"
            manifestPlaceholders["labLabel"] = "JARVIS V1 LAB"
        }
    }
    sourceSets {
        getByName("main") {
            java.srcDir(generated.map { it.dir("shared") })
            res.srcDir(rootProject.file("app/src/main/res"))
        }
        getByName("baseline").java.srcDir(generated.map { it.dir("baseline") })
        getByName("candidate").java.srcDir(generated.map { it.dir("candidate") })
        getByName("test").java.srcDir(rootProject.file("app/src/test/java"))
        getByName("androidTest").java.srcDir(rootProject.file("app/src/androidTest/java"))
        getByName("androidTestCandidate").java.srcDir("src/candidateAndroidTest/java")
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }
}
tasks.configureEach { if (name == "preBuild" || name.startsWith("compile")) dependsOn(prepareM51Sources) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.9.0")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.9.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
