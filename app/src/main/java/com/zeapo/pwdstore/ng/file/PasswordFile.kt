package com.zeapo.pwdstore.ng.file

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

abstract class PasswordFile {

    abstract val absolutePath: String
    abstract val canonicalPath: String
    abstract val fileType: PasswordFileType
    abstract val lastModified: Long
    abstract val name: String
    abstract val nameWithoutExtension: String

    /**
     * Returns [InputStream] if file is found otherwise returns null
     */
    abstract fun openInputStream(): InputStream?

    /**
     * Returns [OutputStream] if file is found otherwise returns null
     */
    abstract fun openOutputStream(): OutputStream?

    abstract fun listFiles(filter: (PasswordFile) -> Boolean = { true }): List<PasswordFile>

    companion object {

        fun getPasswordFile(file: File): PasswordFile {
            return FileImpl(file)
        }

        fun getPasswordFile(uri: URI): PasswordFile {
            return FileImpl(File(uri))
        }

        fun getPasswordFile(context: Context, documentFile: DocumentFile): PasswordFile {
            return DocumentFileImpl(context, documentFile)
        }

        fun getPasswordFile(context: Context, uri: Uri, uriType: UriType): PasswordFile {
            return when (uriType) {
                UriType.TREE -> getPasswordFileFromTreeUri(context, uri)
                UriType.SINGLE -> getPasswordFileFromTreeUri(context, uri)
                UriType.FILE -> TODO("Decide if we need this or not")
            }
        }


        private fun getPasswordFileFromTreeUri(context: Context, treeUri: Uri): PasswordFile {
            // TODO: Not sure if we need this check, will take a look at it later.
            // check(DocumentFile.isDocumentUri(context, treeUri)) { "Passed treeUri: $treeUri is not backed by a DocumentsProvider" }

            val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            check(documentFile != null) { "DocumentFile is null" }

            return DocumentFileImpl(context, documentFile)
        }

        private fun getPasswordFileFromSingleUri(context: Context, singleUri: Uri): PasswordFile {
            // TODO: Not sure if we need this check, will take a look at it later.
            // check(DocumentFile.isDocumentUri(context, singleUri)) { "Passed singleUri: $singleUri is not backed by a DocumentsProvider" }

            val documentFile = DocumentFile.fromSingleUri(context, singleUri)
            check(documentFile != null) { "DocumentFile is null" }

            return DocumentFileImpl(context, documentFile)
        }
    }
}
