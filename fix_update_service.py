filepath = 'app/src/main/java/com/theveloper/pixelplay/data/github/GitHubUpdateService.kt'
with open(filepath, 'r', encoding='utf-8') as f:
    content = f.read()

bad_block = """        check(PackageInfoCompat.getLongVersionCode(archive) > installedVersionCode(context)) {
            "Downloaded update is not newer than the installed VYBE version"
        }"""

good_block = """        val archiveCode = PackageInfoCompat.getLongVersionCode(archive)
        val installedCode = installedVersionCode(context)
        val archiveName = archive.versionName
        val installedName = installedVersionName(context)
        check(archiveCode > installedCode || (archiveCode == installedCode && archiveName != installedName)) {
            "Downloaded update is not newer than the installed VYBE version"
        }"""

content = content.replace(bad_block, good_block)

helper = """    private fun installedVersionCode(context: Context): Long {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }"""

helper_new = """    private fun installedVersionName(context: Context): String? {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return info.versionName
    }

    private fun installedVersionCode(context: Context): Long {
        @Suppress("DEPRECATION")
        val info = context.packageManager.getPackageInfo(context.packageName, 0)
        return PackageInfoCompat.getLongVersionCode(info)
    }"""

content = content.replace(helper, helper_new)
with open(filepath, 'w', encoding='utf-8') as f:
    f.write(content)
