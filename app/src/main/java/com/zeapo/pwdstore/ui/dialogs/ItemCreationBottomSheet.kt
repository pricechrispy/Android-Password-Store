/*
 * Copyright © 2014-2020 The Android Password Store Authors. All Rights Reserved.
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.zeapo.pwdstore.ui.dialogs

import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.EditText
import android.widget.FrameLayout
import androidx.core.os.bundleOf
import androidx.fragment.app.setFragmentResult
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.zeapo.pwdstore.PasswordFragment.Companion.ACTION_FOLDER
import com.zeapo.pwdstore.PasswordFragment.Companion.ACTION_KEY
import com.zeapo.pwdstore.PasswordFragment.Companion.ACTION_PASSWORD
import com.zeapo.pwdstore.PasswordFragment.Companion.ACTION_OOPASS
import com.zeapo.pwdstore.PasswordFragment.Companion.ITEM_CREATION_REQUEST_KEY
import com.zeapo.pwdstore.R
import com.zeapo.pwdstore.utils.resolveAttribute

class ItemCreationBottomSheet : BottomSheetDialogFragment() {

    companion object {
        @JvmStatic
        fun newInstance(type: String) = ItemCreationBottomSheet().apply {
            arguments = Bundle().apply {
                putString("type", type)
            }
        }
    }

    private var type: String = ""
    private var behavior: BottomSheetBehavior<FrameLayout>? = null
    private val bottomSheetCallback = object : BottomSheetBehavior.BottomSheetCallback() {
        override fun onSlide(bottomSheet: View, slideOffset: Float) {
        }

        override fun onStateChanged(bottomSheet: View, newState: Int) {
            if (newState == BottomSheetBehavior.STATE_COLLAPSED) {
                dismiss()
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        if (savedInstanceState != null) dismiss()
        arguments?.getString("type")?.let {
            type = it
        }

        //val builder = android.app.AlertDialog.Builder(context)
        //builder.setMessage(type)
        //builder.create()
        //builder.show()

        if (type.equals("oopass")) {
            return inflater.inflate(R.layout.oopass_sheet, container, false)
        } else {
            return inflater.inflate(R.layout.item_create_sheet, container, false)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        view.viewTreeObserver.addOnGlobalLayoutListener(object : ViewTreeObserver.OnGlobalLayoutListener {
            override fun onGlobalLayout() {
                view.viewTreeObserver.removeOnGlobalLayoutListener(this)
                val dialog = dialog as BottomSheetDialog? ?: return
                behavior = dialog.behavior
                behavior?.apply {
                    state = BottomSheetBehavior.STATE_EXPANDED
                    peekHeight = 0
                    addBottomSheetCallback(bottomSheetCallback)
                }
                dialog.findViewById<View>(R.id.create_folder)?.setOnClickListener {
                    setFragmentResult(ITEM_CREATION_REQUEST_KEY, bundleOf(ACTION_KEY to ACTION_FOLDER))
                    dismiss()
                }
                dialog.findViewById<View>(R.id.create_password)?.setOnClickListener {
                    setFragmentResult(ITEM_CREATION_REQUEST_KEY, bundleOf(ACTION_KEY to ACTION_PASSWORD))
                    dismiss()
                }
                dialog.findViewById<View>(R.id.oopass_get_password)?.setOnClickListener {
                    val _master_password:String = (view.findViewById<View>(R.id.oopass_mp) as EditText).getText().toString()
                    val _auth_user = (view.findViewById<View>(R.id.oopass_user) as EditText).getText().toString()
                    val _auth_domain = (view.findViewById<View>(R.id.oopass_location) as EditText).getText().toString()

                    if ( _master_password.length < 6 ) {
                        context?.let { contextConditional ->
                            MaterialAlertDialogBuilder(contextConditional)
                                .setTitle("Error")
                                .setMessage("Password must be a minimum of 6 characters")
                                //.setCancelable(true)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                    else if ( _auth_user.length < 1 ) {
                        context?.let { contextConditional ->
                            MaterialAlertDialogBuilder(contextConditional)
                                .setTitle("Error")
                                .setMessage("Location must be a minimum of 1 character")
                                //.setCancelable(true)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                    else if ( _auth_domain.length < 1 ) {
                        context?.let { contextConditional ->
                            MaterialAlertDialogBuilder(contextConditional)
                                .setTitle("Error")
                                .setMessage("Service must be a minimum of 1 character")
                                //.setCancelable(true)
                                .setPositiveButton(android.R.string.ok, null)
                                .show()
                        }
                    }
                    else {
                        setFragmentResult(ITEM_CREATION_REQUEST_KEY, bundleOf(ACTION_KEY to ACTION_OOPASS, "_master_password" to _master_password, "_auth_user" to _auth_user, "_auth_domain" to _auth_domain))
                        dismiss()
                    }
                }
            }
        })
        val gradientDrawable = GradientDrawable().apply {
            setColor(requireContext().resolveAttribute(android.R.attr.windowBackground))
        }
        view.background = gradientDrawable
    }

    override fun dismiss() {
        super.dismiss()
        behavior?.removeBottomSheetCallback(bottomSheetCallback)
    }
}
