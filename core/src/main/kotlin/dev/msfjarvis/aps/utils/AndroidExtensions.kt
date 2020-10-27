/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */

package dev.msfjarvis.aps.utils

import dev.msfjarvis.aps.PasswordRepository
import dev.msfjarvis.aps.R
import android.app.KeyguardManager
import android.content.ClipboardManager
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build
import android.util.Base64
import android.util.TypedValue
import android.view.View
import android.view.autofill.AutofillManager
import android.view.inputmethod.InputMethodManager
import androidx.annotation.IdRes
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentActivity
import androidx.preference.PreferenceManager
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import com.google.android.material.snackbar.Snackbar

/**
 * Extension function for [AlertDialog] that requests focus for the
 * view whose id is [id]. Solution based on a StackOverflow
 * answer: https://stackoverflow.com/a/13056259/297261
 */
fun <T : View> AlertDialog.requestInputFocusOnView(@IdRes id: Int) {
    setOnShowListener {
        findViewById<T>(id)?.apply {
            setOnFocusChangeListener { v, _ ->
                v.post {
                    context.getSystemService<InputMethodManager>()
                        ?.showSoftInput(v, InputMethodManager.SHOW_IMPLICIT)
                }
            }
            requestFocus()
        }
    }
}

/**
 * Get an instance of [AutofillManager]. Only
 * available on Android Oreo and above
 */
val Context.autofillManager: AutofillManager?
    @RequiresApi(Build.VERSION_CODES.O)
    get() = getSystemService()

/**
 * Get an instance of [ClipboardManager]
 */
val Context.clipboard
    get() = getSystemService<ClipboardManager>()

/**
 * Wrapper for [getEncryptedPrefs] to avoid open-coding the file name at
 * each call site
 */
fun Context.getEncryptedGitPrefs() = getEncryptedPrefs("git_operation")

/**
 * Wrapper for [getEncryptedPrefs] to get the encrypted preference set for the HTTP
 * proxy.
 */
fun Context.getEncryptedProxyPrefs() = getEncryptedPrefs("http_proxy")

/**
 * Get an instance of [EncryptedSharedPreferences] with the given [fileName]
 */
private fun Context.getEncryptedPrefs(fileName: String): SharedPreferences {
    val masterKeyAlias = MasterKey.Builder(applicationContext)
        .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
        .build()
    return EncryptedSharedPreferences.create(
        applicationContext,
        fileName,
        masterKeyAlias,
        EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
        EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
    )
}

/**
 * Get an instance of [KeyguardManager]
 */
val Context.keyguardManager: KeyguardManager
    get() = getSystemService()!!

/**
 * Get the default [SharedPreferences] instance
 */
val Context.sharedPrefs: SharedPreferences
    get() = PreferenceManager.getDefaultSharedPreferences(applicationContext)


/**
 * Resolve [attr] from the [Context]'s theme
 */
fun Context.resolveAttribute(attr: Int): Int {
    val typedValue = TypedValue()
    this.theme.resolveAttribute(attr, typedValue, true)
    return typedValue.data
}

/**
 * Check if [permission] has been granted to the app.
 */
fun FragmentActivity.isPermissionGranted(permission: String): Boolean {
    return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
}

/**
 * Show a [Snackbar] in a [FragmentActivity] and correctly
 * anchor it to a [com.google.android.material.floatingactionbutton.FloatingActionButton]
 * if one exists in the [view]
 */
fun FragmentActivity.snackbar(
    view: View = findViewById(android.R.id.content),
    message: String,
    length: Int = Snackbar.LENGTH_SHORT,
): Snackbar {
    val snackbar = Snackbar.make(view, message, length)
    snackbar.anchorView = findViewById(R.id.fab)
    snackbar.show()
    return snackbar
}

/**
 * Simplifies the common `getString(key, null) ?: defaultValue` case slightly
 */
fun SharedPreferences.getString(key: String): String? = getString(key, null)

/**
 * Convert this [String] to its [Base64] representation
 */
fun String.base64(): String {
    return Base64.encodeToString(encodeToByteArray(), Base64.NO_WRAP)
}

fun Context.getDefaultUsername() = sharedPrefs.getString(PreferenceKeys.OREO_AUTOFILL_DEFAULT_USERNAME)

fun Context.getCustomSuffixes(): Sequence<String> {
    return sharedPrefs.getString(PreferenceKeys.OREO_AUTOFILL_CUSTOM_PUBLIC_SUFFIXES)
        ?.splitToSequence('\n')
        ?.filter { it.isNotBlank() && it.first() != '.' && it.last() != '.' }
        ?: emptySequence()
}
