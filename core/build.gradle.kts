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
    implementation(Dependencies.AndroidX.activity_ktx)
    implementation(Dependencies.AndroidX.appcompat)
    implementation(Dependencies.AndroidX.biometric)
    implementation(Dependencies.AndroidX.constraint_layout)
    implementation(Dependencies.AndroidX.core_ktx)
    implementation(Dependencies.AndroidX.documentfile)
    implementation(Dependencies.AndroidX.fragment_ktx)
    implementation(Dependencies.AndroidX.lifecycle_common)
    implementation(Dependencies.AndroidX.lifecycle_livedata_ktx)
    implementation(Dependencies.AndroidX.lifecycle_viewmodel_ktx)
    implementation(Dependencies.AndroidX.material)
    implementation(Dependencies.AndroidX.preference)
    implementation(Dependencies.AndroidX.recycler_view)
    implementation(Dependencies.AndroidX.recycler_view_selection)
    implementation(Dependencies.AndroidX.security)
    implementation(Dependencies.AndroidX.swiperefreshlayout)

    implementation(Dependencies.Kotlin.Coroutines.android)
    implementation(Dependencies.Kotlin.Coroutines.core)

    implementation(project(":autofill-parser"))
    implementation(Dependencies.FirstParty.openpgp_ktx)
    implementation(Dependencies.FirstParty.zxing_android_embedded)

    implementation(Dependencies.ThirdParty.commons_codec)
    implementation(Dependencies.ThirdParty.eddsa)
    implementation(Dependencies.ThirdParty.fastscroll)
    implementation(Dependencies.ThirdParty.jgit) {
        exclude(group = "org.apache.httpcomponents", module = "httpclient")
    }
    implementation(Dependencies.ThirdParty.kotlin_result)
    implementation(Dependencies.ThirdParty.sshj)
    implementation(Dependencies.ThirdParty.bouncycastle)
    implementation(Dependencies.ThirdParty.plumber)
    implementation(Dependencies.ThirdParty.ssh_auth)
    implementation(Dependencies.ThirdParty.timber)
    implementation(Dependencies.ThirdParty.timberkt)

    if (isSnapshot()) {
        implementation(Dependencies.ThirdParty.leakcanary)
        implementation(Dependencies.ThirdParty.whatthestack)
    } else {
        debugImplementation(Dependencies.ThirdParty.leakcanary)
        debugImplementation(Dependencies.ThirdParty.whatthestack)
    }

    "nonFreeImplementation"(Dependencies.NonFree.google_play_auth_api_phone)

    // Testing-only dependencies
    androidTestImplementation(Dependencies.Testing.junit)
    androidTestImplementation(Dependencies.Testing.kotlin_test_junit)
    androidTestImplementation(Dependencies.Testing.AndroidX.runner)
    androidTestImplementation(Dependencies.Testing.AndroidX.rules)

    testImplementation(Dependencies.Testing.junit)
    testImplementation(Dependencies.Testing.kotlin_test_junit)
}
