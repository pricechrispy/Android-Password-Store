package com.zeapo.pwdstore.ng.file

import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.net.URI

class FileImpl() : PasswordFile() {

    private lateinit var file: File

    override val absolutePath: String
        get() = file.absolutePath

    override val canonicalPath: String
        get() = TODO("Not yet implemented")

    override val fileType: PasswordFileType
        get() = PasswordFileType.FILE

    override val lastModified: Long
        get() = file.lastModified()

    override val name: String
        get() = file.name

    override val nameWithoutExtension: String
        get() = file.nameWithoutExtension

    override fun openInputStream(): InputStream {
        TODO("Not yet implemented")
    }

    constructor(file: File) {
        this.file = file
    }

    constructor(pathname: String) {
        this.file = File(pathname)
    }

    constructor(uri: URI) {
        this.file = File(uri)
    }

    constructor(parent: String?, child: String) {
        this.file = File(parent, child)
    }

    constructor(parent: File?, child: String) {
        this.file = File(parent, child)
    }


    override fun openOutputStream(): OutputStream {
        TODO("Not yet implemented")
    }
}
