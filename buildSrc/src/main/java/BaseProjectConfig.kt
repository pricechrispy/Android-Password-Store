/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

import com.android.build.gradle.TestedExtension
import com.android.build.gradle.internal.dsl.BaseAppModuleExtension
import org.gradle.api.JavaVersion
import org.gradle.api.Project
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.testing.Test
import org.gradle.api.tasks.testing.logging.TestLogEvent
import org.gradle.api.tasks.wrapper.Wrapper
import org.gradle.kotlin.dsl.repositories
import org.gradle.kotlin.dsl.withType
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile

/**
 * Configure root project.
 * Note that classpath dependencies still need to be defined in the `buildscript` block in the top-level build.gradle.kts file.
 */
internal fun Project.configureForRootProject() {
    // register task for cleaning the build directory in the root project
    tasks.register("clean", Delete::class.java) {
        delete(rootProject.buildDir)
    }
    tasks.withType<Wrapper> {
        gradleVersion = "6.8.2"
        distributionType = Wrapper.DistributionType.ALL
        distributionSha256Sum = "1433372d903ffba27496f8d5af24265310d2da0d78bf6b4e5138831d4fe066e9"
    }
    configureBinaryCompatibilityValidator()
}

/**
 * Configure all projects including the root project
 */
internal fun Project.configureForAllProjects() {
    repositories {
        google()
        mavenCentral()
        jcenter() {
            content {
                // Direct dependencies
                // https://github.com/zhanghai/AndroidFastScroll/issues/35
                includeModule("me.zhanghai.android.fastscroll", "library")
                // https://github.com/open-keychain/open-keychain/issues/2645
                includeModule("org.sufficientlysecure", "sshauthentication-api")

                // Indirect dependencies
                // https://youtrack.jetbrains.com/issue/IDEA-261387
                includeModule("org.jetbrains.trove4j", "trove4j")

                // https://github.com/Kotlin/dokka/issues/41
                includeGroup("org.jetbrains.dokka")
                includeGroup("org.jetbrains.kotlinx")
                includeModule("org.jetbrains", "markdown")
            }
        }
        maven { setUrl("https://jitpack.io") }
    }
    tasks.withType<KotlinCompile> {
        kotlinOptions {
            jvmTarget = JavaVersion.VERSION_1_8.toString()
            freeCompilerArgs = freeCompilerArgs + additionalCompilerArgs
            languageVersion = "1.4"
        }
    }
    tasks.withType<Test> {
        maxParallelForks = Runtime.getRuntime().availableProcessors() * 2
        testLogging {
            events(TestLogEvent.PASSED, TestLogEvent.SKIPPED, TestLogEvent.FAILED)
        }
    }
}

/**
 * Checks if we're building a snapshot
 */
@Suppress("UnstableApiUsage")
fun Project.isSnapshot(): Boolean {
    with(project.providers) {
        val workflow = environmentVariable("GITHUB_WORKFLOW").forUseAtConfigurationTime()
        val snapshot = environmentVariable("SNAPSHOT").forUseAtConfigurationTime()
        return workflow.isPresent && snapshot.isPresent
    }
}

/**
 * Apply configurations for app module
 */
@Suppress("UnstableApiUsage")
internal fun BaseAppModuleExtension.configureAndroidApplicationOptions(project: Project) {
    val minifySwitch = project.providers.environmentVariable("DISABLE_MINIFY").forUseAtConfigurationTime()

    adbOptions.installOptions("--user 0")

    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    buildTypes {
        named("release") {
            isMinifyEnabled = !minifySwitch.isPresent
            setProguardFiles(listOf("proguard-android-optimize.txt", "proguard-rules.pro"))
            buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "${project.isSnapshot()}")
        }
        named("debug") {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
            isMinifyEnabled = false
            buildConfigField("boolean", "ENABLE_DEBUG_FEATURES", "true")
        }
    }
}

/**
 * Apply baseline configurations for all Android projects (Application and Library).
 */
@Suppress("UnstableApiUsage")
internal fun TestedExtension.configureCommonAndroidOptions() {
    compileSdkVersion(30)

    defaultConfig {
        minSdkVersion(23)
        targetSdkVersion(29)
    }

    packagingOptions {
        exclude("**/*.version")
        exclude("**/*.txt")
        exclude("**/*.kotlin_module")
        exclude("**/plugin.properties")
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }

    testOptions.animationsDisabled = true
}
