package com.justai.aimybox.speechkit.snips

import android.content.Context
import android.os.Environment
import androidx.annotation.RequiresPermission
import java.io.BufferedInputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.io.FileNotFoundException
import java.io.FileOutputStream


class SnipsAssets private constructor(val modelDirPath: File) {
    companion object {
        @RequiresPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
        fun fromApkAssets(
            context: Context,
            zipFileName: String = "assistant.zip",
            externalStorageDirectory: String = "Android/data/aimybox-snips-assets/"
        ): SnipsAssets {
            var directory = File("${Environment.getExternalStorageDirectory().absolutePath}/$externalStorageDirectory")

            if (directory.exists()) {
                directory.deleteRecursively()
            }

            unzip(context, zipFileName, directory)

            while (directory.isDirectory && directory.listFiles().size == 1) {
                directory = directory.listFiles()[0]
            }

            return SnipsAssets(directory)
        }

        private fun unzip(context: Context, zipFileName: String, directory: File) {
            context.assets.open(zipFileName).use { stream ->
                ZipInputStream(BufferedInputStream(stream)).use { zis ->
                    var ze: ZipEntry?

                    while (zis.nextEntry.also { ze = it } != null) {
                        val file = File(directory, ze?.name)

                        if (!ze?.isDirectory!!) {
                            try {
                                file.apply {
                                    parentFile.mkdirs()
                                    createNewFile()
                                }.outputStream()
                                    .use { out ->
                                        zis.copyTo(out)
                                        out.flush()
                                    }
                            } catch (e: Throwable) {
                                L.e("Failed to copy $file")
                                throw e
                            }
                        }
                    }
                }
            }
        }
    }
}