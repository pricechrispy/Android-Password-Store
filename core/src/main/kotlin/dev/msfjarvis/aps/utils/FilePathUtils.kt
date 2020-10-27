package dev.msfjarvis.aps.utils

object FilePathUtils {

    /**
     * Gets the relative path to the repository
     */
    fun getRelativePath(fullPath: String, repositoryPath: String): String =
        fullPath.replace(repositoryPath, "").replace("/+".toRegex(), "/")

    /**
     * Gets the Parent path, relative to the repository
     */
    fun getParentPath(fullPath: String, repositoryPath: String): String {
        val relativePath = getRelativePath(fullPath, repositoryPath)
        val index = relativePath.lastIndexOf("/")
        return "/${relativePath.substring(startIndex = 0, endIndex = index + 1)}/".replace("/+".toRegex(), "/")
    }

    /**
     * /path/to/store/social/facebook.gpg -> social/facebook
     */
    @JvmStatic
    fun getLongName(fullPath: String, repositoryPath: String, basename: String): String {
        var relativePath = getRelativePath(fullPath, repositoryPath)
        return if (relativePath.isNotEmpty() && relativePath != "/") {
            // remove preceding '/'
            relativePath = relativePath.substring(1)
            if (relativePath.endsWith('/')) {
                relativePath + basename
            } else {
                "$relativePath/$basename"
            }
        } else {
            basename
        }
    }
}
