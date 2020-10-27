/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

plugins {
    id("com.android.library")
    kotlin("android")
    `aps-plugin`
}

android {
    sourceSets {
        named("main").configure {
            java.setSrcDirs(setOf("src/main/kotlin", "src/test/kotlin", "src/androidTest/kotlin"))
        }
    }
}

dependencies {
    compileOnly(Dependencies.AndroidX.annotation)
    implementation(project(":autofill-parser"))
    implementation(Dependencies.AndroidX.core_ktx)
    implementation(Dependencies.ThirdParty.kotlin_result)
    implementation(Dependencies.ThirdParty.timber)
    implementation(Dependencies.ThirdParty.timberkt)
}
