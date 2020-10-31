package com.zeapo.pwdstore.ng.file

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

class DocumentFileImpl(private val file: DocumentFile) : PasswordFile() {

    private lateinit var context: Context

    override val absolutePath: String
        get() = file.uri.path!!

    override val canonicalPath: String
        get() = TODO("Not yet implemented")

    override val fileType: PasswordFileType
        get() = PasswordFileType.DOCUMENT_FILE

    override val lastModified: Long
        get() = file.lastModified()

    override val name: String
        get() = TODO("Not yet implemented")

    override val nameWithoutExtension: String
        get() = TODO("Not yet implemented")


    override fun openInputStream(): InputStream? {
        return try {
            context.contentResolver.openInputStream(file.uri)
        } catch (e: FileNotFoundException) {
            null
        }
    }

    override fun openOutputStream(): OutputStream? {
        return try {
            context.contentResolver.openOutputStream(file.uri)
        } catch (e: FileNotFoundException) {
            null
        }
    }
}
