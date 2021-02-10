/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.content.pm.ShortcutInfo.Builder
import android.content.pm.ShortcutManager
import android.graphics.drawable.Icon
import android.os.Build
import android.os.Bundle
import android.view.KeyEvent
import android.view.Menu
import android.view.MenuItem
import android.view.MenuItem.OnActionExpandListener
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.activity.viewModels
import androidx.appcompat.widget.SearchView
import androidx.appcompat.widget.SearchView.OnQueryTextListener
import androidx.core.content.edit
import androidx.core.content.getSystemService
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.commit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.github.ajalt.timberkt.d
import com.github.ajalt.timberkt.e
import com.github.ajalt.timberkt.i
import com.github.ajalt.timberkt.w
import com.github.michaelbull.result.fold
import com.github.michaelbull.result.getOr
import com.github.michaelbull.result.onFailure
import com.github.michaelbull.result.runCatching
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.textfield.TextInputEditText
import com.zeapo.pwdstore.autofill.oreo.AutofillMatcher
import com.zeapo.pwdstore.crypto.BasePgpActivity.Companion.getLongName
import com.zeapo.pwdstore.crypto.DecryptActivity
import com.zeapo.pwdstore.crypto.PasswordCreationActivity
import com.zeapo.pwdstore.git.BaseGitActivity
import com.zeapo.pwdstore.git.config.AuthMode
import com.zeapo.pwdstore.git.config.GitSettings
import com.zeapo.pwdstore.ui.dialogs.BasicBottomSheet
import com.zeapo.pwdstore.ui.dialogs.FolderCreationDialogFragment
import com.zeapo.pwdstore.ui.onboarding.activity.OnboardingActivity
import com.zeapo.pwdstore.utils.PasswordItem
import com.zeapo.pwdstore.utils.PasswordRepository
import com.zeapo.pwdstore.utils.PreferenceKeys
import com.zeapo.pwdstore.utils.base64
import com.zeapo.pwdstore.utils.commitChange
import com.zeapo.pwdstore.utils.contains
import com.zeapo.pwdstore.utils.getString
import com.zeapo.pwdstore.utils.isInsideRepository
import com.zeapo.pwdstore.utils.isPermissionGranted
import com.zeapo.pwdstore.utils.listFilesRecursively
import com.zeapo.pwdstore.utils.requestInputFocusOnView
import com.zeapo.pwdstore.utils.sharedPrefs
import java.io.File
import java.lang.Character.UnicodeBlock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.eclipse.jgit.api.Git
import com.koushikdutta.async.http.AsyncHttpClient
import com.koushikdutta.async.http.AsyncHttpClient.WebSocketConnectCallback
import com.koushikdutta.async.http.WebSocket
import com.koushikdutta.async.http.WebSocket.StringCallback
import com.koushikdutta.async.callback.DataCallback
import com.koushikdutta.async.callback.CompletedCallback
import com.koushikdutta.async.DataEmitter
import com.koushikdutta.async.ByteBufferList
import com.zeapo.pwdstore.crypto.ARC4
import com.zeapo.pwdstore.crypto.prng
import com.zeapo.pwdstore.crypto.seedrandom
import java.math.BigInteger
import java.security.SecureRandom
import java.security.MessageDigest
import java.util.Random
import javax.crypto.Mac
import javax.crypto.SecretKey
import javax.crypto.spec.SecretKeySpec
import kotlin.math.floor
import kotlin.math.roundToInt
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.asn1.x9.X9ECParameters
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.math.ec.ECPoint
import org.bouncycastle.util.encoders.Hex

const val PASSWORD_FRAGMENT_TAG = "PasswordsList"

class PasswordStore : BaseGitActivity() {

    private lateinit var searchItem: MenuItem
    private val settings by lazy { sharedPrefs }

    private val model: SearchableRepositoryViewModel by viewModels {
        ViewModelProvider.AndroidViewModelFactory(application)
    }

    private val storagePermissionRequest = registerForActivityResult(RequestPermission()) { granted ->
        if (granted) checkLocalRepository()
    }

    private val directorySelectAction = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            checkLocalRepository()
        }
    }

    private val listRefreshAction = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            refreshPasswordList()
        }
    }

    private val passwordMoveAction = registerForActivityResult(StartActivityForResult()) { result ->
        val intentData = result.data ?: return@registerForActivityResult
        val filesToMove = requireNotNull(intentData.getStringArrayExtra("Files"))
        val target = File(requireNotNull(intentData.getStringExtra("SELECTED_FOLDER_PATH")))
        val repositoryPath = PasswordRepository.getRepositoryDirectory().absolutePath
        if (!target.isDirectory) {
            e { "Tried moving passwords to a non-existing folder." }
            return@registerForActivityResult
        }

        d { "Moving passwords to ${intentData.getStringExtra("SELECTED_FOLDER_PATH")}" }
        d { filesToMove.joinToString(", ") }

        lifecycleScope.launch(Dispatchers.IO) {
            for (file in filesToMove) {
                val source = File(file)
                if (!source.exists()) {
                    e { "Tried moving something that appears non-existent." }
                    continue
                }
                val destinationFile = File(target.absolutePath + "/" + source.name)
                val basename = source.nameWithoutExtension
                val sourceLongName = getLongName(requireNotNull(source.parent), repositoryPath, basename)
                val destinationLongName = getLongName(target.absolutePath, repositoryPath, basename)
                if (destinationFile.exists()) {
                    e { "Trying to move a file that already exists." }
                    withContext(Dispatchers.Main) {
                        MaterialAlertDialogBuilder(this@PasswordStore)
                            .setTitle(resources.getString(R.string.password_exists_title))
                            .setMessage(resources.getString(
                                R.string.password_exists_message,
                                destinationLongName,
                                sourceLongName)
                            )
                            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                                launch(Dispatchers.IO) {
                                    moveFile(source, destinationFile)
                                }
                            }
                            .setNegativeButton(R.string.dialog_cancel, null)
                            .show()
                    }
                } else {
                    launch(Dispatchers.IO) {
                        moveFile(source, destinationFile)
                    }
                }
            }
            when (filesToMove.size) {
                1 -> {
                    val source = File(filesToMove[0])
                    val basename = source.nameWithoutExtension
                    val sourceLongName = getLongName(requireNotNull(source.parent), repositoryPath, basename)
                    val destinationLongName = getLongName(target.absolutePath, repositoryPath, basename)
                    withContext(Dispatchers.Main) {
                        commitChange(
                            resources.getString(R.string.git_commit_move_text, sourceLongName, destinationLongName),
                        )
                    }
                }
                else -> {
                    val repoDir = PasswordRepository.getRepositoryDirectory().absolutePath
                    val relativePath = getRelativePath("${target.absolutePath}/", repoDir)
                    withContext(Dispatchers.Main) {
                        commitChange(
                            resources.getString(R.string.git_commit_move_multiple_text, relativePath),
                        )
                    }
                }
            }
        }
        refreshPasswordList()
        getPasswordFragment()?.dismissActionMode()
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent): Boolean {
        // open search view on search key, or Ctr+F
        if ((keyCode == KeyEvent.KEYCODE_SEARCH || keyCode == KeyEvent.KEYCODE_F && event.isCtrlPressed) &&
            !searchItem.isActionViewExpanded) {
            searchItem.expandActionView()
            return true
        }

        // open search view on any printable character and query for it
        val c = event.unicodeChar.toChar()
        val printable = isPrintable(c)
        if (printable && !searchItem.isActionViewExpanded) {
            searchItem.expandActionView()
            (searchItem.actionView as SearchView).setQuery(c.toString(), true)
            return true
        }
        return super.onKeyDown(keyCode, event)
    }

    @SuppressLint("NewApi")
    override fun onCreate(savedInstanceState: Bundle?) {
        // If user opens app with permission granted then revokes and returns,
        // prevent attempt to create password list fragment
        var savedInstance = savedInstanceState
        if (savedInstanceState != null && (!settings.getBoolean(PreferenceKeys.GIT_EXTERNAL, false) ||
                !isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE))) {
            savedInstance = null
        }
        super.onCreate(savedInstance)
        setContentView(R.layout.activity_pwdstore)

        model.currentDir.observe(this) { dir ->
            val basePath = PasswordRepository.getRepositoryDirectory().absoluteFile
            supportActionBar!!.apply {
                if (dir != basePath)
                    title = dir.name
                else
                    setTitle(R.string.app_name)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        refreshPasswordList()
    }

    override fun onResume() {
        super.onResume()
        if (settings.getBoolean(PreferenceKeys.GIT_EXTERNAL, false)) {
            hasRequiredStoragePermissions()
        } else {
            checkLocalRepository()
        }
        if (settings.getBoolean(PreferenceKeys.SEARCH_ON_START, false) && ::searchItem.isInitialized) {
            if (!searchItem.isActionViewExpanded) {
                searchItem.expandActionView()
            }
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        val menuRes = when {
            GitSettings.authMode == AuthMode.None -> R.menu.main_menu_no_auth
            PasswordRepository.isGitRepo() -> R.menu.main_menu_git
            else -> R.menu.main_menu_non_git
        }
        menuInflater.inflate(menuRes, menu)
        return super.onCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        // Invalidation forces onCreateOptionsMenu to be called again. This is cheap and quick so
        // we can get by without any noticeable difference in performance.
        invalidateOptionsMenu()
        searchItem = menu.findItem(R.id.action_search)
        val searchView = searchItem.actionView as SearchView
        searchView.setOnQueryTextListener(
            object : OnQueryTextListener {
                override fun onQueryTextSubmit(s: String): Boolean {
                    searchView.clearFocus()
                    return true
                }

                override fun onQueryTextChange(s: String): Boolean {
                    val filter = s.trim()
                    // List the contents of the current directory if the user enters a blank
                    // search term.
                    if (filter.isEmpty())
                        model.navigateTo(
                            newDirectory = model.currentDir.value!!,
                            pushPreviousLocation = false
                        )
                    else
                        model.search(filter)
                    return true
                }
            })

        // When using the support library, the setOnActionExpandListener() method is
        // static and accepts the MenuItem object as an argument
        searchItem.setOnActionExpandListener(
            object : OnActionExpandListener {
                override fun onMenuItemActionCollapse(item: MenuItem): Boolean {
                    refreshPasswordList()
                    return true
                }

                override fun onMenuItemActionExpand(item: MenuItem): Boolean {
                    return true
                }
            })
        if (settings.getBoolean(PreferenceKeys.SEARCH_ON_START, false)) {
            searchItem.expandActionView()
        }
        return super.onPrepareOptionsMenu(menu)
    }

    // Handle action bar item clicks here. The action bar will
    // automatically handle clicks on the Home/Up button, so long
    // as you specify a parent activity in AndroidManifest.xml.
    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        val id = item.itemId
        val initBefore = MaterialAlertDialogBuilder(this)
            .setMessage(resources.getString(R.string.creation_dialog_text))
            .setPositiveButton(resources.getString(R.string.dialog_ok), null)
        when (id) {
            R.id.user_pref -> {
                runCatching {
                    startActivity(Intent(this, UserPreference::class.java))
                }.onFailure { e ->
                    e.printStackTrace()
                }
                return true
            }
            R.id.git_push -> {
                if (!PasswordRepository.isInitialized) {
                    initBefore.show()
                    return false
                }
                runGitOperation(GitOp.PUSH)
                return true
            }
            R.id.git_pull -> {
                if (!PasswordRepository.isInitialized) {
                    initBefore.show()
                    return false
                }
                runGitOperation(GitOp.PULL)
                return true
            }
            R.id.git_sync -> {
                if (!PasswordRepository.isInitialized) {
                    initBefore.show()
                    return false
                }
                runGitOperation(GitOp.SYNC)
                return true
            }
            R.id.refresh -> {
                refreshPasswordList()
                return true
            }
            android.R.id.home -> onBackPressed()
            else -> {
            }
        }
        return super.onOptionsItemSelected(item)
    }

    override fun onBackPressed() {
        if (getPasswordFragment()?.onBackPressedInActivity() != true)
            super.onBackPressed()
    }

    private fun getPasswordFragment(): PasswordFragment? {
        return supportFragmentManager.findFragmentByTag(PASSWORD_FRAGMENT_TAG) as? PasswordFragment
    }

    fun clearSearch() {
        if (searchItem.isActionViewExpanded)
            searchItem.collapseActionView()
    }

    private fun runGitOperation(operation: GitOp) = lifecycleScope.launch {
        launchGitOperation(operation).fold(
            success = { refreshPasswordList() },
            failure = { promptOnErrorHandler(it) },
        )
    }

    /**
     * Validates if storage permission is granted, and requests for it if not. The return value
     * is true if the permission has been granted.
     */
    private fun hasRequiredStoragePermissions(): Boolean {
        return if (!isPermissionGranted(Manifest.permission.WRITE_EXTERNAL_STORAGE)) {
            BasicBottomSheet.Builder(this)
                .setMessageRes(R.string.access_sdcard_text)
                .setPositiveButtonClickListener(getString(R.string.snackbar_action_grant)) {
                    storagePermissionRequest.launch(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                .build()
                .show(supportFragmentManager, "STORAGE_PERMISSION_MISSING")
            false
        } else {
            checkLocalRepository()
            true
        }
    }

    private fun checkLocalRepository() {
        val repo = PasswordRepository.initialize()
        if (repo == null) {
            directorySelectAction.launch(UserPreference.createDirectorySelectionIntent(this))
        } else {
            checkLocalRepository(PasswordRepository.getRepositoryDirectory())
        }
    }

    private fun checkLocalRepository(localDir: File?) {
        if (localDir != null && settings.getBoolean(PreferenceKeys.REPOSITORY_INITIALIZED, false)) {
            d { "Check, dir: ${localDir.absolutePath}" }
            // do not push the fragment if we already have it
            if (getPasswordFragment() == null ||
                settings.getBoolean(PreferenceKeys.REPO_CHANGED, false)) {
                settings.edit { putBoolean(PreferenceKeys.REPO_CHANGED, false) }
                val args = Bundle()
                args.putString(REQUEST_ARG_PATH, PasswordRepository.getRepositoryDirectory().absolutePath)

                // if the activity was started from the autofill settings, the
                // intent is to match a clicked pwd with app. pass this to fragment
                if (intent.getBooleanExtra("matchWith", false)) {
                    args.putBoolean("matchWith", true)
                }
                supportActionBar?.apply {
                    show()
                    setDisplayHomeAsUpEnabled(false)
                }
                supportFragmentManager.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
                supportFragmentManager.commit {
                    replace(R.id.main_layout, PasswordFragment.newInstance(args), PASSWORD_FRAGMENT_TAG)
                }
            }
        } else {
            startActivity(Intent(this, OnboardingActivity::class.java))
        }
    }

    private fun getRelativePath(fullPath: String, repositoryPath: String): String {
        return fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")
    }

    private fun getLastChangedTimestamp(fullPath: String): Long {
        val repoPath = PasswordRepository.getRepositoryDirectory()
        val repository = PasswordRepository.getRepository(repoPath)
        if (repository == null) {
            d { "getLastChangedTimestamp: No git repository" }
            return File(fullPath).lastModified()
        }
        val git = Git(repository)
        val relativePath = getRelativePath(fullPath, repoPath.absolutePath).substring(1) // Removes leading '/'
        return runCatching {
            val iterator = git.log().addPath(relativePath).call().iterator()
            if (!iterator.hasNext()) {
                w { "getLastChangedTimestamp: No commits for file: $relativePath" }
                return -1
            }
            iterator.next().commitTime.toLong() * 1000
        }.getOr(-1)
    }

    fun decryptPassword(item: PasswordItem) {
        val decryptIntent = Intent(this, DecryptActivity::class.java)
        val authDecryptIntent = Intent(this, LaunchActivity::class.java)
        for (intent in arrayOf(decryptIntent, authDecryptIntent)) {
            intent.putExtra("NAME", item.toString())
            intent.putExtra("FILE_PATH", item.file.absolutePath)
            intent.putExtra("REPO_PATH", PasswordRepository.getRepositoryDirectory().absolutePath)
            intent.putExtra("LAST_CHANGED_TIMESTAMP", getLastChangedTimestamp(item.file.absolutePath))
        }
        // Needs an action to be a shortcut intent
        authDecryptIntent.action = LaunchActivity.ACTION_DECRYPT_PASS

        startActivity(decryptIntent)

        // Adds shortcut
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N_MR1) {
            val shortcutManager: ShortcutManager = getSystemService() ?: return
            val shortcut = Builder(this, item.fullPathToParent)
                .setShortLabel(item.toString())
                .setLongLabel(item.fullPathToParent + item.toString())
                .setIcon(Icon.createWithResource(this, R.drawable.ic_lock_open_24px))
                .setIntent(authDecryptIntent)
                .build()
            val shortcuts = shortcutManager.dynamicShortcuts
            if (shortcuts.size >= shortcutManager.maxShortcutCountPerActivity && shortcuts.size > 0) {
                shortcuts.removeAt(shortcuts.size - 1)
                shortcuts.add(0, shortcut)
                shortcutManager.dynamicShortcuts = shortcuts
            } else {
                shortcutManager.addDynamicShortcuts(listOf(shortcut))
            }
        }
    }

    private fun validateState(): Boolean {
        if (!PasswordRepository.isInitialized) {
            MaterialAlertDialogBuilder(this)
                .setMessage(resources.getString(R.string.creation_dialog_text))
                .setPositiveButton(resources.getString(R.string.dialog_ok), null)
                .show()
            return false
        }
        return true
    }

    fun createPassword() {
        if (!validateState()) return
        val currentDir = currentDir
        i { "Adding file to : ${currentDir.absolutePath}" }
        val intent = Intent(this, PasswordCreationActivity::class.java)
        intent.putExtra("FILE_PATH", currentDir.absolutePath)
        intent.putExtra("REPO_PATH", PasswordRepository.getRepositoryDirectory().absolutePath)
        listRefreshAction.launch(intent)
    }

    fun createFolder() {
        if (!validateState()) return
        FolderCreationDialogFragment.newInstance(currentDir.path).show(supportFragmentManager, null)
    }

    fun getOopassMain(_master_password:String, _auth_user:String, _auth_domain:String) {
        if(!validateState()) return

        val _setting_api_key_email:String? = sharedPrefs.getString(PreferenceKeys.OOPASS_API_KEY_EMAIL)
        val _setting_server_host:String? = sharedPrefs.getString(PreferenceKeys.OOPASS_SERVER_HOST)
        val _setting_server_port:String? = sharedPrefs.getString(PreferenceKeys.OOPASS_SERVER_PORT)
        val _setting_requested_chars:String? = sharedPrefs.getString(PreferenceKeys.OOPASS_REQUESTED_CHARS)
        val _setting_requested_length:Int = sharedPrefs.getString(PreferenceKeys.OOPASS_REQUESTED_LENGTH)?.toIntOrNull() ?: 16
        val _setting_hmac_key:String? = sharedPrefs.getString(PreferenceKeys.OOPASS_HMAC_KEY)

        // initialize hmac key if not yet set for this device
        if ( _setting_hmac_key == null || _setting_hmac_key.trim().length == 0 )
        {
            val random_seed:SecureRandom = SecureRandom()
            val random_bytes:ByteArray = ByteArray(4)
            random_seed.nextBytes(random_bytes)

            val random_md5:MessageDigest = MessageDigest.getInstance("MD5")

            val random_32bytes:ByteArray = random_md5.digest( random_bytes )

            sharedPrefs.edit { putString(PreferenceKeys.OOPASS_HMAC_KEY, String(Hex.encode(random_32bytes))) }

            MaterialAlertDialogBuilder(this@PasswordStore)
                .setTitle("Notice: HMAC Key Generated")
                .setMessage("An HMAC key was not yet set for your device and has been generated.\n\nYou may go to the settings to set another value for the HMAC key.")
                //.setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .show()
        }

        if ( _setting_api_key_email == null || _setting_api_key_email.trim().length == 0 )
        {
            MaterialAlertDialogBuilder(this@PasswordStore)
                .setTitle("Error: Missing API Email")
                .setMessage("Go to the settings to set a value for the API email")
                //.setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .show()

            return
        }

        if ( _setting_server_host == null || _setting_server_host.trim().length == 0 )
        {
            MaterialAlertDialogBuilder(this@PasswordStore)
                .setTitle("Error: Missing Server Host")
                .setMessage("Go to the settings to set a value for the server host")
                //.setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .show()

            return
        }

        if ( _setting_server_port == null || _setting_server_port.trim().length == 0 )
        {
            MaterialAlertDialogBuilder(this@PasswordStore)
                .setTitle("Error: Missing Server Port")
                .setMessage("Go to the settings to set a value for the server port")
                //.setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .show()

            return
        }

        if ( _setting_requested_chars == null || _setting_requested_chars.trim().length == 0 )
        {
            MaterialAlertDialogBuilder(this@PasswordStore)
                .setTitle("Error: Missing Desired Characters")
                .setMessage("Go to the settings to set a value for the desired password characters")
                //.setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .show()

            return
        }

        if ( _setting_requested_length < 1 )
        {
            MaterialAlertDialogBuilder(this@PasswordStore)
                .setTitle("Error: Invalid Desired Length")
                .setMessage("Go to the settings to set a value for the desired password length")
                //.setCancelable(true)
                .setPositiveButton(android.R.string.ok, null)
                .show()

            return
        }

        val hmac_options_key:String? = sharedPrefs.getString(PreferenceKeys.OOPASS_HMAC_KEY)
        val hmac_options_algorithm:String = "HmacSHA256"
        val oprf_hmac_sha256_key:SecretKey = SecretKeySpec(hmac_options_key?.toByteArray(), hmac_options_algorithm)
        val ec_options:X9ECParameters = SECNamedCurves.getByName("secp256k1")

        var randomRho:BigInteger = BigInteger.ONE

        val client_version:String = "1.1.2.android"
        val protocol_version:String = "2.0.*"
        var _socket:WebSocket? = null

        val _auth_offset:Int = 0

        val handle_socket_connect = object:WebSocketConnectCallback {
            override fun onCompleted(ex:Exception?, webSocket:WebSocket) {
                ex?.printStackTrace()
                return
            }
        }

        val handle_socket_closed_ended = object:CompletedCallback {
            override fun onCompleted(ex:Exception?) {
                ex?.printStackTrace()
                return
            }
        }

        val handle_socket_data_read = object:DataCallback {
            override fun onDataAvailable(emitter:DataEmitter, byteBufferList:ByteBufferList) {
                //println("onDataAvailable()")

                // note that this data has been read
                byteBufferList.recycle()
            }
        }

        val handle_socket_received_beta = object:StringCallback {
            override fun onStringAvailable(s: String) {
                val data_array:List<String> = s.split(",")

                if ( data_array.size.equals(2) )
                {
                    val beta_x_str:String = data_array.get(0)
                    val beta_y_str:String = data_array.get(1)

                    println("RECEIVED CURVE POINTS X,Y:'" + beta_x_str + "','" + beta_y_str + "'")

                    val beta_x:BigInteger = BigInteger(1, Hex.decodeStrict(beta_x_str))
                    val beta_y:BigInteger = BigInteger(1, Hex.decodeStrict(beta_y_str))

                    val beta_point:ECPoint = ec_options.getCurve().createPoint(beta_x, beta_y)

                    println("beta_point.affineX:Y::'" + beta_point.normalize().getXCoord().toString() + "':'" + beta_point.normalize().getYCoord().toString() + "'")

                    val is_beta_point_member:Boolean = beta_point.isValid()

                    if ( is_beta_point_member )
                    {
                        println("Point is a member of curve")

                        // CANCEL OUT RANDOM LARGE VALUE IN BETA THAT WAS USED TO BLIND ALPHA

                        // aka: rwd = H'(x)^k = (beta)^(1/rho)
                        val rho_inv:BigInteger = randomRho.modInverse( ec_options.getN() )
                        val rwd_point:ECPoint = beta_point.multiply( rho_inv )

                        // GENERATE HMAC SHA256 OF RESULTING X,Y PAIR ON CURVE AND MASTER PASSWORD AS RESULT OF OPRF EXCHANGE

                        //val rwd_x:ByteArray = rwd_point.normalize().getXCoord().toBigInteger().toByteArray()
                        //val rwd_y:ByteArray = rwd_point.normalize().getYCoord().toBigInteger().toByteArray()
                        val rwd_x:String = rwd_point.normalize().getXCoord().toString()
                        val rwd_y:String = rwd_point.normalize().getYCoord().toString()

                        //println("rwd_point.affineX:Y::'" + String(Hex.encode(rwd_x)) + "':'" + String(Hex.encode(rwd_y)) + "'")
                        println("rwd_point.affineX:Y::'" + rwd_x + "':'" + rwd_y + "'")
                        println("rwd_point.isOnCurve():'" + rwd_point.isValid().toString())

                        //val rwd:String = String(Hex.encode(rwd_x)) + String(Hex.encode(rwd_y)) + _master_password
                        val rwd:String = rwd_x + rwd_y + _master_password

                        val rwd_hmac_sha256_key:SecretKey = SecretKeySpec(_auth_domain.toByteArray(), hmac_options_algorithm)
                        val rwd_hmac_sha256:Mac = Mac.getInstance(hmac_options_algorithm, BouncyCastleProvider())

                        rwd_hmac_sha256.init(rwd_hmac_sha256_key)
                        rwd_hmac_sha256.update(rwd.toByteArray())

                        val rwd_hmac_sha256_encrypted:ByteArray = rwd_hmac_sha256.doFinal()
                        val hashed:String = String(Hex.encode(rwd_hmac_sha256_encrypted))

                        println("Resolved Beta: '" + hashed + "'")

                        // MAP RESULT TO CONFIGURABLE PASSWORD ALPHABET
                        var seedrandom: ARC4 = seedrandom(hashed)

                        var pass:String = ""

                        for (i:Int in 0..(_setting_requested_length-1))
                        {
                            //println(prng(seedrandom))
                            pass += _setting_requested_chars.get(floor(prng(seedrandom) * _setting_requested_chars.length).roundToInt())
                        }

                        // SAVE TO CLIPBOARD WITH TIMER TO CLEAR
                        val decryptIntent = Intent(this@PasswordStore, DecryptActivity::class.java)
                        decryptIntent.putExtra("NAME", "OOPASS: Copied to Clipboard")
                        decryptIntent.putExtra("FILE_PATH", "OOPASS: Copied to Clipboard")
                        decryptIntent.putExtra("REPO_PATH", "OOPASS")
                        decryptIntent.putExtra("LAST_CHANGED_TIMESTAMP", "")
                        decryptIntent.putExtra("OOPASS_DATA", pass)
                        startActivity(decryptIntent)

                        //runOnUiThread(object:Runnable{
                        //    override fun run() {
                        //        //(BasePgpActivity::copyPasswordToClipboard)(BasePgpActivity(), pass)
                        //        (DecryptActivity::copyPasswordToClipboard)(DecryptActivity(), pass)
                        //        //MaterialAlertDialogBuilder(this@PasswordStore)
                        //        //    .setTitle("Your Password")
                        //        //    .setMessage(pass)
                        //        //    //.setCancelable(true)
                        //        //    .setPositiveButton(android.R.string.ok, null)
                        //        //    .show()
                        //    }
                        //})
                    }
                    else
                    {
                        println("Point is NOT a member of curve")
                    }
                }
                else if ( s.equals("invalid") )
                {
                    println("Point SENT is NOT a member of curve")
                }

                println("Closing socket connection")
                _socket?.close()

                return
            }
        }

        val handle_socket_data = object:StringCallback {
            override fun onStringAvailable(s:String) {
                //println("onStringAvailable(): '" + s + "'")

                if ( s.equals("__protocol_" + protocol_version + "_connected__") )
                {
                    // replace string callback handler
                    _socket?.setStringCallback(handle_socket_received_beta)

                    // GENERATE USER HASH TO BE BLINDED AND SENT TO SERVER

                    //generate hash( username+domain+ctr+password )
                    var hashForOPRF:String = _auth_user + _auth_domain + _auth_offset + _master_password

                    val oprf_hmac_sha256:Mac = Mac.getInstance(hmac_options_algorithm, BouncyCastleProvider())

                    oprf_hmac_sha256.init(oprf_hmac_sha256_key)
                    oprf_hmac_sha256.update(hashForOPRF.toByteArray())

                    val oprf_hmac_sha256_encrypted:ByteArray = oprf_hmac_sha256.doFinal()
                    hashForOPRF = String(Hex.encode(oprf_hmac_sha256_encrypted))

                    // GENERATE RANDOM LARGE VALUE FOR USE IN BLINDING OUR USER HASH

                    //ensure binary length >= (256+80=336 bits) 42bytes of entropy or more
                    val random_seed:SecureRandom = SecureRandom()
                    val random_bytes:ByteArray = ByteArray(43)
                    random_seed.nextBytes(random_bytes)

                    randomRho = BigInteger(random_bytes)

                    println("randomRho:bitlength::'" + randomRho + "':'" + randomRho.bitLength() + "'")

                    // GENERATE BLINDED ALPHA

                    // generate alpha = (hashForAlpha)^randomRho
                    // aka: alpha=H'(x)^rho
                    // power represented as point multiplication on the curve

                    val hash_bigi:BigInteger = BigInteger(1, Hex.decodeStrict(hashForOPRF))

                    println("hashForOPRF:bigi::'" + hashForOPRF + "':'" + hash_bigi + "'")

                    // get x,y pair on curve using user hmac as X
                    // Bitcoin keys use the secp256k1 (info on 2.7.1) parameters.
                    // Public keys are generated by: Q=dG where Q is the public key, d is the private key, and G is a curve parameter.
                    // A public key is a 65 byte long value consisting of a leading 0x04 and X and Y coordinates of 32 bytes each.
                    // http://www.secg.org/collateral/sec2_final.pdf
                    // aka: Q = alpha_point; d = hash_bigi; G = ec_options.G

                    val alpha_point:ECPoint = ec_options.getG().multiply( hash_bigi )
                    //println(ec_options.getG().toString())
                    //println(ec_options.getG().normalize().affineXCoord.toString())
                    //println(ec_options.getG().normalize().affineYCoord.toString())
                    //println(ec_options.getG().normalize().rawXCoord.toString())
                    //println(ec_options.getG().normalize().rawYCoord.toString())
                    //println(ec_options.getG().normalize().xCoord.toString())
                    //println(ec_options.getG().normalize().yCoord.toString())
                    println("alpha_point.affineX:Y::'" + alpha_point.normalize().getXCoord().toString() + "':'" + alpha_point.normalize().getYCoord().toString() + "'")
                    println("alpha_point.isOnCurve():'" + alpha_point.isValid().toString())

                    // point multiply with random large value, assumed reduction by ec_options.p
                    val alpha_mult:ECPoint = alpha_point.multiply( randomRho )
                    //val testr:BigInteger = BigInteger("7830433108338161311672534626810729051862579494468904564511192911136080794165799472284225839193527826330")
                    //println("testr:bitlength::'" + testr + "':'" + testr.bitLength() + "'")
                    //randomRho = testr
                    //val alpha_mult:ECPoint = alpha_point.multiply( testr )

                    // get x,y pair after point multiplication as 32byte==256bit length strings
                    //val alpha_x:ByteArray = alpha_mult.normalize().getXCoord().toBigInteger().toByteArray()
                    //val alpha_y:ByteArray = alpha_mult.normalize().getYCoord().toBigInteger().toByteArray()
                    val alpha_x:String = alpha_mult.normalize().getXCoord().toString()
                    val alpha_y:String = alpha_mult.normalize().getYCoord().toString()

                    //println("alpha_mult.affineX:Y::'" + String(Hex.encode(alpha_x)) + "':'" + String(Hex.encode(alpha_y)) + "'")
                    println("alpha_mult.affineX:Y::'" + alpha_mult.normalize().getXCoord().toString() + "':'" + alpha_mult.normalize().getYCoord().toString() + "'")
                    println("alpha_mult.isOnCurve():'" + alpha_mult.isValid().toString())

                    // will send x,y pair to server for decoding
                    //val alpha:String = String(Hex.encode(alpha_x)) + "," + String(Hex.encode(alpha_y))
                    val alpha:String = alpha_x + "," + alpha_y

                    // GENERATE UNIQUE USER ID TO ASSOCIATE EMAIL FOR GEOIP VIOLATION NOTIFICATIONS

                    var user_identifier:String = _auth_user + _auth_domain

                    // keyed hash of user_identifier into hex string
                    val user_identifier_hmac_sha256_key:SecretKey = SecretKeySpec(_auth_offset.toString().toByteArray(), hmac_options_algorithm)
                    val user_identifier_hmac_sha256:Mac = Mac.getInstance(hmac_options_algorithm, BouncyCastleProvider())

                    user_identifier_hmac_sha256.init(user_identifier_hmac_sha256_key)
                    user_identifier_hmac_sha256.update(user_identifier.toByteArray())

                    val user_identifier_hmac_sha256_encrypted:ByteArray = user_identifier_hmac_sha256.doFinal()
                    user_identifier = String(Hex.encode(user_identifier_hmac_sha256_encrypted))

                    // SEND VALUES TO SERVER

                    //val _setting_api_key_email:String? = sharedPrefs.getString(PreferenceKeys.OOPASS_API_KEY_EMAIL)

                    println("Sending Alpha + User Identifier + User Email")
                    println("Alpha:'" + alpha + "'::User Identifier:'" + user_identifier + "'::User Email:'" + _setting_api_key_email + "'")

                    val request_data:String = alpha + "," + user_identifier + "," + _setting_api_key_email

                    _socket?.send(request_data)
                }

                return
            }
        }

        //val _setting_server_host:String? = sharedPrefs.getString(PreferenceKeys.OOPASS_SERVER_HOST)
        //val _setting_server_port:String? = sharedPrefs.getString(PreferenceKeys.OOPASS_SERVER_PORT)
        val _server_protocol:String = "wss"

        val _socket_url:String? = _server_protocol + "://" + _setting_server_host + ":" + _setting_server_port

        val wsFuture = AsyncHttpClient.getDefaultInstance().websocket(_socket_url, _server_protocol, handle_socket_connect)
        _socket = wsFuture.get()

        _socket.setClosedCallback(handle_socket_closed_ended)
        _socket.setEndCallback(handle_socket_closed_ended)
        _socket.setDataCallback(handle_socket_data_read)
        _socket.setStringCallback(handle_socket_data)

        // handle_socket_opened
        _socket.send("__client_" + client_version + "_connected__")
    }

    fun deletePasswords(selectedItems: List<PasswordItem>) {
        var size = 0
        selectedItems.forEach {
            if (it.file.isFile)
                size++
            else
                size += it.file.listFilesRecursively().size
        }
        if (size == 0) {
            selectedItems.map { item -> item.file.deleteRecursively() }
            refreshPasswordList()
            return
        }
        MaterialAlertDialogBuilder(this)
            .setMessage(resources.getQuantityString(R.plurals.delete_dialog_text, size, size))
            .setPositiveButton(resources.getString(R.string.dialog_yes)) { _, _ ->
                val filesToDelete = arrayListOf<File>()
                selectedItems.forEach { item ->
                    if (item.file.isDirectory)
                        filesToDelete.addAll(item.file.listFilesRecursively())
                    else
                        filesToDelete.add(item.file)
                }
                selectedItems.map { item -> item.file.deleteRecursively() }
                refreshPasswordList()
                AutofillMatcher.updateMatches(applicationContext, delete = filesToDelete)
                val fmt = selectedItems.joinToString(separator = ", ") { item ->
                    item.file.toRelativeString(PasswordRepository.getRepositoryDirectory())
                }
                lifecycleScope.launch {
                    commitChange(
                        resources.getString(R.string.git_commit_remove_text, fmt),
                    )
                }
            }
            .setNegativeButton(resources.getString(R.string.dialog_no), null)
            .show()
    }

    fun movePasswords(values: List<PasswordItem>) {
        val intent = Intent(this, SelectFolderActivity::class.java)
        val fileLocations = values.map { it.file.absolutePath }.toTypedArray()
        intent.putExtra("Files", fileLocations)
        passwordMoveAction.launch(intent)
    }

    enum class CategoryRenameError(val resource: Int) {
        None(0),
        EmptyField(R.string.message_category_error_empty_field),
        CategoryExists(R.string.message_category_error_category_exists),
        DestinationOutsideRepo(R.string.message_error_destination_outside_repo),
    }

    /**
     * Prompt the user with a new category name to assign,
     * if the new category forms/leads a path (i.e. contains "/"), intermediate directories will be created
     * and new category will be placed inside.
     *
     * @param oldCategory The category to change its name
     * @param error Determines whether to show an error to the user in the alert dialog,
     * this error may be due to the new category the user entered already exists or the field was empty or the
     * destination path is outside the repository
     *
     * @see [CategoryRenameError]
     * @see [isInsideRepository]
     */
    private fun renameCategory(oldCategory: PasswordItem, error: CategoryRenameError = CategoryRenameError.None) {
        val view = layoutInflater.inflate(R.layout.folder_dialog_fragment, null)
        val newCategoryEditText = view.findViewById<TextInputEditText>(R.id.folder_name_text)

        if (error != CategoryRenameError.None) {
            newCategoryEditText.error = getString(error.resource)
        }

        val dialog = MaterialAlertDialogBuilder(this)
            .setTitle(R.string.title_rename_folder)
            .setView(view)
            .setMessage(getString(R.string.message_rename_folder, oldCategory.name))
            .setPositiveButton(R.string.dialog_ok) { _, _ ->
                val newCategory = File("${oldCategory.file.parent}/${newCategoryEditText.text}")
                when {
                    newCategoryEditText.text.isNullOrBlank() -> renameCategory(oldCategory, CategoryRenameError.EmptyField)
                    newCategory.exists() -> renameCategory(oldCategory, CategoryRenameError.CategoryExists)
                    !newCategory.isInsideRepository() -> renameCategory(oldCategory, CategoryRenameError.DestinationOutsideRepo)
                    else -> lifecycleScope.launch(Dispatchers.IO) {
                        moveFile(oldCategory.file, newCategory)

                        //associate the new category with the last category's timestamp in history
                        val preference = getSharedPreferences("recent_password_history", Context.MODE_PRIVATE)
                        val timestamp = preference.getString(oldCategory.file.absolutePath.base64())
                        if (timestamp != null) {
                            preference.edit {
                                remove(oldCategory.file.absolutePath.base64())
                                putString(newCategory.absolutePath.base64(), timestamp)
                            }
                        }

                        withContext(Dispatchers.Main) {
                            commitChange(
                                resources.getString(R.string.git_commit_move_text, oldCategory.name, newCategory.name),
                            )
                        }
                    }
                }
            }
            .setNegativeButton(R.string.dialog_skip, null)
            .create()

        dialog.requestInputFocusOnView<TextInputEditText>(R.id.folder_name_text)
        dialog.show()
    }

    fun renameCategory(categories: List<PasswordItem>) {
        for (oldCategory in categories) {
            renameCategory(oldCategory)
        }
    }

    /**
     * Refreshes the password list by re-executing the last navigation or search action, preserving
     * the navigation stack and scroll position. If the current directory no longer exists,
     * navigation is reset to the repository root. If the optional [target] argument is provided,
     * it will be entered if it is a directory or scrolled into view if it is a file (both inside
     * the current directory).
     */
    fun refreshPasswordList(target: File? = null) {
        val plist = getPasswordFragment()
        if (target?.isDirectory == true && model.currentDir.value?.contains(target) == true) {
            plist?.navigateTo(target)
        } else if (target?.isFile == true && model.currentDir.value?.contains(target) == true) {
            // Creating new passwords is handled by an activity, so we will refresh in onStart.
            plist?.scrollToOnNextRefresh(target)
        } else if (model.currentDir.value?.isDirectory == true) {
            model.forceRefresh()
        } else {
            model.reset()
            supportActionBar!!.setDisplayHomeAsUpEnabled(false)
        }
    }

    private val currentDir: File
        get() = getPasswordFragment()?.currentDir ?: PasswordRepository.getRepositoryDirectory()

    private suspend fun moveFile(source: File, destinationFile: File) {
        val sourceDestinationMap = if (source.isDirectory) {
            destinationFile.mkdirs()
            // Recursively list all files (not directories) below `source`, then
            // obtain the corresponding target file by resolving the relative path
            // starting at the destination folder.
            source.listFilesRecursively().associateWith { destinationFile.resolve(it.relativeTo(source)) }
        } else {
            mapOf(source to destinationFile)
        }
        if (!source.renameTo(destinationFile)) {
            e { "Something went wrong while moving $source to $destinationFile." }
            withContext(Dispatchers.Main) {
                MaterialAlertDialogBuilder(this@PasswordStore)
                    .setTitle(R.string.password_move_error_title)
                    .setMessage(getString(R.string.password_move_error_message, source, destinationFile))
                    .setCancelable(true)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
            }
        } else {
            AutofillMatcher.updateMatches(this, sourceDestinationMap)
        }
    }

    fun matchPasswordWithApp(item: PasswordItem) {
        val path = item.file
            .absolutePath
            .replace(PasswordRepository.getRepositoryDirectory().toString() + "/", "")
            .replace(".gpg", "")
        val data = Intent()
        data.putExtra("path", path)
        setResult(RESULT_OK, data)
        finish()
    }

    companion object {

        const val REQUEST_ARG_PATH = "PATH"
        private fun isPrintable(c: Char): Boolean {
            val block = UnicodeBlock.of(c)
            return (!Character.isISOControl(c) &&
                block != null && block !== UnicodeBlock.SPECIALS)
        }
    }
}
