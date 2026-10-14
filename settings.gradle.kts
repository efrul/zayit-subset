rootProject.name = "ZayitSubset"

pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        google()
    }
}

plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

// The same repositories as Zayit (SeforimApp settings.gradle.kts, v0.6.4).
dependencyResolutionManagement {
    repositories {
        mavenCentral()
        google()
        maven("https://packages.jetbrains.team/maven/p/kpm/public/")
        maven("https://packages.jetbrains.team/maven/p/ij/intellij-dependencies/")
        maven("https://www.jetbrains.com/intellij-repository/releases")
    }
}
