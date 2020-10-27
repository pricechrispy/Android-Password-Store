/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

fun isSnapshot(): Boolean {
    return System.getenv("GITHUB_WORKFLOW") != null && System.getenv("SNAPSHOT") != null
}

plugins {
    id("com.android.library")
    kotlin("android")
    `aps-plugin`
}

android {
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }

    sourceSets {
        named("main").configure {
            java.setSrcDirs(setOf("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin"))
        }
    }
}

dependencies {
    compileOnly(Dependencies.AndroidX.annotation)
    implementation(project(":autofill-parser"))
    implementation(Dependencies.AndroidX.appcompat)
    implementation(Dependencies.AndroidX.core_ktx)
    implementation(Dependencies.AndroidX.fragment_ktx)
    implementation(Dependencies.AndroidX.lifecycle_common)
    implementation(Dependencies.AndroidX.lifecycle_livedata_ktx)
    implementation(Dependencies.AndroidX.lifecycle_viewmodel_ktx)
    implementation(Dependencies.AndroidX.material)
    implementation(Dependencies.AndroidX.preference)
    implementation(Dependencies.AndroidX.recycler_view)
    implementation(Dependencies.AndroidX.recycler_view_selection)
    implementation(Dependencies.AndroidX.security)
    implementation(Dependencies.FirstParty.openpgp_ktx)
    implementation(Dependencies.Kotlin.Coroutines.android)
    implementation(Dependencies.ThirdParty.jgit) {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation(Dependencies.ThirdParty.kotlin_result)
    implementation(Dependencies.ThirdParty.timber)
    implementation(Dependencies.ThirdParty.timberkt)
}
