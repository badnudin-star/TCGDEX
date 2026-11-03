package com.pokemontcg.sammlung

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the release build contract that cannot be verified from a debug APK.
 *
 * The versions below are the pair documented by the Android Gradle plugin 8.13
 * compatibility table: Gradle 8.13 is the minimum and default for AGP 8.13.2.
 * Kotlin, KSP and the Compose compiler plugin must stay on the same Kotlin line.
 */
class ReleaseConfigurationTest {

    private val projectRoot: File = generateSequence(File(System.getProperty("user.dir") ?: ".")) { it.parentFile }
        .first { File(it, "settings.gradle.kts").isFile }

    private fun projectFile(path: String): String = File(projectRoot, path).readText()

    @Test
    fun gradleWrapperAndAndroidGradlePluginUseCompatibleVersions() {
        val wrapper = projectFile("gradle/wrapper/gradle-wrapper.properties")
        val rootBuild = projectFile("build.gradle.kts")

        assertTrue(
            "AGP 8.13.2 requires Gradle 8.13",
            wrapper.contains("distributionUrl=https\\://services.gradle.org/distributions/gradle-8.13-bin.zip")
        )
        assertTrue(
            "Release configuration expects AGP 8.13.2",
            rootBuild.contains("id(\"com.android.application\") version \"8.13.2\"")
        )
        assertTrue(
            "Kotlin must stay on 2.0.21",
            rootBuild.contains("id(\"org.jetbrains.kotlin.android\") version \"2.0.21\"")
        )
        assertTrue(
            "Compose compiler plugin must match Kotlin",
            rootBuild.contains("id(\"org.jetbrains.kotlin.plugin.compose\") version \"2.0.21\"")
        )
        assertTrue(
            "KSP must match the Kotlin version line",
            rootBuild.contains("id(\"com.google.devtools.ksp\") version \"2.0.21-1.0.27\"")
        )
    }

    @Test
    fun releaseBuildEnablesR8AndResourceShrinking() {
        val appBuild = projectFile("app/build.gradle.kts")

        assertTrue("Release minification is required", appBuild.contains("isMinifyEnabled = true"))
        assertTrue("Release resource shrinking is required", appBuild.contains("isShrinkResources = true"))
        assertTrue(
            "The optimized Android ProGuard baseline must be used",
            appBuild.contains("proguard-android-optimize.txt")
        )
        assertTrue(
            "The app-specific R8 rules must be applied",
            appBuild.contains("proguard-rules.pro")
        )
    }

    @Test
    fun r8RulesKeepGsonRetrofitRoomAndEnumsAndKeepMlKitNarrow() {
        val rules = projectFile("app/proguard-rules.pro")

        assertTrue(
            "TCGdex/PokeAPI DTOs must keep their runtime names",
            rules.contains("-keep class com.pokemontcg.sammlung.data.api.** { *; }")
        )
        assertTrue(
            "Retrofit service interfaces are reflected at runtime",
            rules.contains("-keep interface com.pokemontcg.sammlung.data.api.** { *; }")
        )
        assertTrue(
            "Gson serialized fields must keep their annotations",
            rules.contains("@com.google.gson.annotations.SerializedName")
        )
        assertTrue(
            "Enums are reflected by Room and Gson",
            rules.contains("-keepclassmembers enum *")
        )
        assertTrue(
            "Room database implementations must survive shrinking",
            rules.contains("androidx.room.RoomDatabase")
        )
        assertTrue(
            "Room entities must keep their runtime names",
            rules.contains("-keep class com.pokemontcg.sammlung.data.local.entities.** { *; }")
        )
        assertTrue(
            "The user database and DAOs must keep their runtime names",
            rules.contains("-keep class com.pokemontcg.sammlung.data.local.user.** { *; }")
        )
        assertTrue(
            "JSON export models are reflected by Gson",
            rules.contains("-keep class com.pokemontcg.sammlung.data.export.** { *; }")
        )
        assertTrue(
            "Only the ML Kit text-recognition surface is kept",
            rules.contains("-keep class com.google.mlkit.vision.text.** { *; }")
        )
        assertFalse(
            "Do not keep the complete ML Kit SDK",
            rules.contains("-keep class com.google.mlkit.**")
        )
    }

    @Test
    fun debugOnlyDependenciesDoNotLeakIntoRelease() {
        val appBuild = projectFile("app/build.gradle.kts")

        assertTrue(
            "HTTP logging is debug-only",
            appBuild.contains("debugImplementation(\"com.squareup.okhttp3:logging-interceptor:")
        )
        assertTrue(
            "Only the used Material icon set is packaged",
            appBuild.contains("implementation(\"androidx.compose.material:material-icons-core\")")
        )
        assertFalse(
            "The extended Material icon set must not return",
            appBuild.contains("material-icons-extended")
        )
    }
}
