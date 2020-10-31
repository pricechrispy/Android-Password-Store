package com.zeapo.pwdstore.ng.file

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

class DocumentFileImpl(private val context: Context, private val file: DocumentFile) : PasswordFile() {

    override val absolutePath: String
        get() = file.uri.path!!

    override val canonicalPath: String
        get() = TODO("Not yet implemented")

    override val fileType: PasswordFileType
        get() = PasswordFileType.DOCUMENT_FILE

    override val lastModified: Long
        get() = file.lastModified()

    // Returns empty string in case file name is null. Mocking java.io.File behaviour.
    override val name: String
        get() = file.name ?: ""

    override val nameWithoutExtension: String
        get() = name.substringBeforeLast(".") ?: ""


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

    override fun listFiles(filter: (PasswordFile) -> Boolean): List<PasswordFile> {
        if (!file.isDirectory) return emptyList()
        val filesList = file.listFiles()

        return filesList.map { getPasswordFile(context, it) }.filter(filter)
    }
}
