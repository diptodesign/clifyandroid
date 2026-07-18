package com.dipto.clify.patcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.TimeUnit

class PatchEngine(private val context: Context) {

    interface ProgressCallback {
        fun onProgress(status: String, percent: Int)
        fun onComplete(apkFile: File)
        fun onError(error: String)
    }

    private val client = OkHttpClient.Builder()
        .connectTimeout(60, TimeUnit.SECONDS)
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadsDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "clify").also { it.mkdirs() }

    companion object {
        private const val TAG = "PatchEngine"

        private val APK_SOURCES = listOf(
            Triple(
                "https://api.github.com/repos/SinAble716/RVX_inotia00_patched/releases/latest",
                "youtube-rvx",
                "ReVanced Extended (Ad-free YouTube)"
            ),
            Triple(
                "https://api.github.com/repos/inotia00/revanced-patches/releases/latest",
                ".apk",
                "ReVanced Patches"
            )
        )
    }

    suspend fun startPatching(callback: ProgressCallback) = withContext(Dispatchers.IO) {
        try {
            var apkUrl: String? = null
            var sourceName = ""

            for ((apiUrl, nameFilter, displayName) in APK_SOURCES) {
                callback.onProgress("Checking $displayName…", 5)
                val result = findApkUrl(apiUrl, nameFilter, callback)
                if (result != null) {
                    apkUrl = result.first
                    sourceName = result.second
                    callback.onProgress("Found: $sourceName", 10)
                    break
                }
            }

            if (apkUrl == null) {
                callback.onError("Could not find a patched APK. Please try again later.")
                return@withContext
            }

            val outputFile = File(downloadsDir, "clify-youtube-$sourceName.apk")
            downloadFile(apkUrl, outputFile, callback, basePercent = 10, maxPercent = 90)

            if (outputFile.length() < 10_000_000) {
                callback.onError("Downloaded file is too small (${outputFile.length()} bytes). May be corrupted.")
                outputFile.delete()
                return@withContext
            }

            callback.onProgress("Download complete! Preparing to install…", 95)
            callback.onComplete(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Patching failed", e)
            callback.onError(e.message ?: "Unknown error occurred")
        }
    }

    private fun findApkUrl(apiUrl: String, nameFilter: String, callback: ProgressCallback): Pair<String, String>? {
        try {
            val request = Request.Builder()
                .url(apiUrl)
                .header("Accept", "application/vnd.github+json")
                .build()
            val response = client.newCall(request).execute()
            if (!response.isSuccessful) return null

            val body = response.body?.string() ?: return null
            val json = JSONObject(body)
            val assets = json.getJSONArray("assets")

            for (i in 0 until assets.length()) {
                val asset = assets.getJSONObject(i)
                val name = asset.getString("name")
                if (name.endsWith(".apk") && name.contains(nameFilter, ignoreCase = true)) {
                    val url = asset.getString("browser_download_url")
                    val sizeMb = asset.getLong("size") / 1_048_576
                    callback.onProgress("Found $name ($sizeMb MB)", 8)
                    return Pair(url, name.replace(".apk", ""))
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch from $apiUrl", e)
        }
        return null
    }

    private fun downloadFile(url: String, outputFile: File, callback: ProgressCallback, basePercent: Int, maxPercent: Int) {
        if (outputFile.exists()) outputFile.delete()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Download failed: HTTP ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val totalSize = body.contentLength()
        var downloaded = 0L
        val startTime = System.currentTimeMillis()

        FileOutputStream(outputFile).use { fos ->
            body.byteStream().use { input ->
                val buffer = ByteArray(16384)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalSize > 0) {
                        val progress = (downloaded * 100) / totalSize
                        val percent = basePercent + (progress * (maxPercent - basePercent) / 100).toInt()
                        val mb = downloaded / 1_048_576
                        val totalMb = totalSize / 1_048_576
                        val elapsed = (System.currentTimeMillis() - startTime) / 1000
                        val speed = if (elapsed > 0) (downloaded / elapsed / 1_048_576) else 0
                        callback.onProgress("Downloading… ${mb}MB / ${totalMb}MB (${speed}MB/s)", percent)
                    }
                }
            }
        }
    }

    fun installApk(apkFile: File) {
        val uri = Uri.fromFile(apkFile)
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(intent)
    }
}
