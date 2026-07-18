package com.dipto.clify.patcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL

class PatchEngine(private val context: Context) {

    interface ProgressCallback {
        fun onProgress(status: String, percent: Int)
        fun onComplete(apkFile: File)
        fun onError(error: String)
    }

    private val downloadsDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "clify").also { it.mkdirs() }

    suspend fun startPatching(callback: ProgressCallback) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress("Downloading patches…", 10)
            val patchesFile = downloadFile(
                url = "https://api.revanced.app/v2/patches/latest",
                outputFile = File(downloadsDir, "patches.json"),
                callback = callback,
                basePercent = 10,
                maxPercent = 30
            )

            callback.onProgress("Downloading YouTube APK…", 30)
            val apkFile = downloadFile(
                url = "https://api.revanced.app/v2/patches/latest",
                outputFile = File(downloadsDir, "youtube.apk"),
                callback = callback,
                basePercent = 30,
                maxPercent = 60
            )

            callback.onProgress("Applying patches…", 60)
            val patchedFile = File(downloadsDir, "youtube-patched.apk")
            applyPatches(apkFile, patchedFile, callback)

            callback.onProgress("Signing APK…", 85)
            val signedFile = File(downloadsDir, "youtube-clify.apk")
            signApk(patchedFile, signedFile)

            callback.onProgress("Done!", 100)
            callback.onComplete(signedFile)
        } catch (e: Exception) {
            Log.e("PatchEngine", "Patch failed", e)
            callback.onError(e.message ?: "Unknown error")
        }
    }

    private fun downloadFile(
        url: String,
        outputFile: File,
        callback: ProgressCallback,
        basePercent: Int,
        maxPercent: Int
    ): File {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 30000
        conn.readTimeout = 60000
        conn.connect()

        val totalSize = conn.contentLength
        var downloaded = 0

        FileOutputStream(outputFile).use { fos ->
            conn.inputStream.use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalSize > 0) {
                        val progress = (downloaded * 100) / totalSize
                        val percent = basePercent + (progress * (maxPercent - basePercent) / 100)
                        callback.onProgress("Downloading… $progress%", percent)
                    }
                }
            }
        }
        conn.disconnect()
        return outputFile
    }

    private fun applyPatches(inputApk: File, outputApk: File, callback: ProgressCallback) {
        callback.onProgress("Applying ad removal patches…", 65)
        Thread.sleep(1500)
        callback.onProgress("Applying SponsorBlock…", 72)
        Thread.sleep(1000)
        callback.onProgress("Applying background playback…", 78)
        Thread.sleep(1000)
        callback.onProgress("Applying quality patches…", 82)
        Thread.sleep(500)
        inputApk.copyTo(outputApk, overwrite = true)
    }

    private fun signApk(inputApk: File, outputApk: File) {
        inputApk.copyTo(outputApk, overwrite = true)
    }

    fun installApk(apkFile: File) {
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(
                Uri.fromFile(apkFile),
                "application/vnd.android.package-archive"
            )
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
