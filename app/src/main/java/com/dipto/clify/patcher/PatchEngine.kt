package com.dipto.clify.patcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import com.dipto.clify.model.PatchItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
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
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadsDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "clify").also { it.mkdirs() }

    private val GITHUB_RELEASES_URL = "https://api.github.com/repos/inotia00/ReVanced_Patches/releases/latest"
    private val GITHUB_REVANCED_URL = "https://api.github.com/repos/ReVanced/revanced-manager/releases/latest"

    suspend fun startPatching(enabledPatches: List<PatchItem>, callback: ProgressCallback) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress("Fetching latest release info…", 5)
            val apkUrl = fetchApkDownloadUrl(callback)
            if (apkUrl == null) {
                callback.onError("Could not find a download link. Please try again later.")
                return@withContext
            }

            callback.onProgress("Downloading patched APK…", 15)
            val apkFile = downloadApk(apkUrl, callback)

            callback.onProgress("Verifying download…", 90)
            if (apkFile.length() < 1_000_000) {
                callback.onError("Downloaded file is too small. May be corrupted.")
                return@withContext
            }

            callback.onProgress("Download complete!", 100)
            callback.onComplete(apkFile)
        } catch (e: Exception) {
            Log.e("PatchEngine", "Patching failed", e)
            callback.onError(e.message ?: "Unknown error occurred")
        }
    }

    private fun fetchApkDownloadUrl(callback: ProgressCallback): String? {
        val urls = listOf(GITHUB_RELEASES_URL, GITHUB_REVANCED_URL)

        for (url in urls) {
            try {
                callback.onProgress("Checking $url…", 5)
                val request = Request.Builder().url(url).header("Accept", "application/vnd.github+json").build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val body = response.body?.string() ?: continue
                val json = JSONObject(body)
                val assets = json.getJSONArray("assets")

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk") && (name.contains("revanced") || name.contains("patched") || name.contains("youtube"))) {
                        return asset.getString("browser_download_url")
                    }
                }
            } catch (e: Exception) {
                Log.w("PatchEngine", "Failed to fetch from $url", e)
                continue
            }
        }
        return null
    }

    private fun downloadApk(url: String, callback: ProgressCallback): File {
        val outputFile = File(downloadsDir, "clify-youtube.apk")
        if (outputFile.exists()) outputFile.delete()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()

        if (!response.isSuccessful) {
            throw Exception("Download failed: HTTP ${response.code}")
        }

        val body = response.body ?: throw Exception("Empty response body")
        val totalSize = body.contentLength()
        var downloaded = 0L

        FileOutputStream(outputFile).use { fos ->
            body.byteStream().use { input ->
                val buffer = ByteArray(8192)
                var bytesRead: Int
                while (input.read(buffer).also { bytesRead = it } != -1) {
                    fos.write(buffer, 0, bytesRead)
                    downloaded += bytesRead
                    if (totalSize > 0) {
                        val progress = (downloaded * 100) / totalSize
                        val percent = 15 + (progress * 75 / 100).toInt()
                        callback.onProgress("Downloading… ${(downloaded / 1048576)}MB / ${(totalSize / 1048576)}MB", percent)
                    }
                }
            }
        }
        return outputFile
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
