import org.jetbrains.compose.desktop.application.dsl.TargetFormat

plugins {
    alias(libs.plugins.kotlin.jvm)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.compose)
    alias(libs.plugins.kotlin.serialization)
}

group = "zayit.subset"
version = "2.0.0"

// Compiled with Java 21 (a JetBrains Runtime); run on JetBrains Runtime 25 like Zayit 0.6.4, since Jewel 0.41 is built
// for Java 25 and the decorated window needs JBR. Its folder: -Pjbr25=... (the CI passes it), else ~/.jdks/jbr-25.
val jbr25 = file(providers.gradleProperty("jbr25").orNull ?: (System.getProperty("user.home") + "/.jdks/jbr-25"))

kotlin {
    jvmToolchain {
        languageVersion.set(JavaLanguageVersion.of(21))
        vendor.set(JvmVendorSpec.JETBRAINS)
    }
}

dependencies {
    implementation(compose.desktop.currentOs) {
        exclude(group = "org.jetbrains.compose.material")
    }
    implementation(libs.jewel.int.ui.standalone)
    implementation(libs.jewel.int.ui.decorated.window)
    // IntelliJ's icon set, as Zayit's jewel module adds it (AllIconsKeys)
    implementation(libs.intellij.platform.icons)
    implementation(libs.intellij.platform.icons.api)
    implementation(libs.intellij.platform.icons.impl)
    implementation(libs.kotlinx.coroutines.swing)
    implementation(libs.filekit.dialogs.compose)
    implementation(libs.lucene.core)
    implementation(libs.kotlinx.serialization.protobuf)
    implementation(libs.sqlite.jdbc)
}

// As Zayit: Jewel's icons-api pulls IntelliJ's coroutines fork, which duplicates kotlinx-coroutines-core-jvm classes
configurations.configureEach {
    exclude(group = "org.jetbrains.intellij.deps.kotlinx", module = "kotlinx-coroutines-core-jvm")
}

compose.desktop {
    application {
        mainClass = "zayit.subset.MainKt"
        javaHome = jbr25.absolutePath
        jvmArgs += listOf("-Dfile.encoding=UTF-8", "--enable-native-access=ALL-UNNAMED", "-Xmx2g")
        nativeDistributions {
            targetFormats(TargetFormat.Msi)
            packageName = "ZayitSubset"
            packageVersion = "2.0.0"
            description = "יצירת ספרייה מוקטנת של זית לטלפון"
            vendor = "Zayit Subset"
            includeAllModules = true
            windows {
                iconFile.set(project.file("src/main/resources/drawable/app.ico"))
                perUserInstall = true
                menu = true
                menuGroup = ""
                upgradeUuid = "8f3f8d2e-5a3c-4b8e-9f6e-2b7c1d0a4e51"
            }
        }
        buildTypes.release.proguard { isEnabled.set(false) }
    }
}

// The app as it is installed: lib\ holds the app and its libraries (named with their group: two artifacts share a file
// name); the runtime is copied next to it by install.ps1.
val appLibs by tasks.registering {
    dependsOn("jar")
    doLast {
        val dir = layout.buildDirectory.dir("app/lib").get().asFile
        dir.deleteRecursively()
        dir.mkdirs()
        tasks.named<Jar>("jar").get().archiveFile.get().asFile.copyTo(File(dir, "zayit-subset.jar"), true)
        configurations.getByName("runtimeClasspath").resolvedConfiguration.resolvedArtifacts.forEach { a ->
            a.file.copyTo(File(dir, a.moduleVersion.id.group + "-" + a.file.name), true)
        }
    }
}
