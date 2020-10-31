package com.zeapo.pwdstore.ng.file

import java.io.File
import java.io.FileNotFoundException
import java.io.InputStream
import java.io.OutputStream

class FileImpl(private val file: File) : PasswordFile() {

    override val absolutePath: String
        get() = file.absolutePath

    override val canonicalPath: String
        get() = file.canonicalPath

    override val fileType: PasswordFileType
        get() = PasswordFileType.FILE

    override val lastModified: Long
        get() = file.lastModified()

    override val name: String
        get() = file.name

    override val nameWithoutExtension: String
        get() = file.nameWithoutExtension

    override fun openInputStream(): InputStream? {
        return try {
            file.inputStream()
        } catch (e: FileNotFoundException) {
            null
        }
    }

    override fun openOutputStream(): OutputStream? {
        return try {
            file.outputStream()
        } catch (e: FileNotFoundException) {
            null
        }
    }

    override fun listFiles(filter: (PasswordFile) -> Boolean): List<PasswordFile> {
        if (!file.isDirectory) return emptyList()
        val filesList = file.listFiles() ?: arrayOf()

        return filesList.map { getPasswordFile(it) }.filter(filter)
    }
}
