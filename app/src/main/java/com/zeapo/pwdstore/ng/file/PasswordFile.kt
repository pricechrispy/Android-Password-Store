package com.zeapo.pwdstore.ng.file

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
     */    abstract fun openOutputStream(): OutputStream?

    companion object {

        fun getPasswordFile(file: File): PasswordFile {
            return FileImpl(file)
        }

        fun getPasswordFile(uri: URI, fileType: PasswordFileType): PasswordFile {
            return FileImpl(uri)
        }
    }
}
