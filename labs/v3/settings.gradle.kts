pluginManagement { repositories { google(); mavenCentral(); gradlePluginPortal() } }
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories { google(); mavenCentral() }
}
rootProject.name = "JarvisV3"
// V3 reuses the V1 STT (whisper.cpp) and Pocket modules IN PLACE (no copy): labs/v1 stays byte-identical.
include(":app", ":stt", ":pocket")
project(":stt").projectDir = file("../v1/stt")
project(":pocket").projectDir = file("../v1/pocket")
