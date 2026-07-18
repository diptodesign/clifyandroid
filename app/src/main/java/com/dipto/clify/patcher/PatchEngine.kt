package com.dipto.clify.patcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
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
        .readTimeout(300, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadsDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "clify").also { it.mkdirs() }

    companion object {
        private const val TAG = "PatchEngine"
    }

    suspend fun startPatching(callback: ProgressCallback) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress("Finding ReVanced Manager…", 5)
            val releaseInfo = fetchLatestRelease(callback) ?: run {
                callback.onError("Could not find ReVanced Manager. Check your connection.")
                return@withContext
            }

            callback.onProgress("Downloading ReVanced Manager…", 15)
            val outputFile = File(downloadsDir, releaseInfo.first)
            downloadFile(releaseInfo.second, outputFile, callback, basePercent = 15, maxPercent = 95)

            if (outputFile.length() < 1_000_000) {
                callback.onError("Downloaded file is too small. May be corrupted.")
                outputFile.delete()
                return@withContext
            }

            callback.onProgress("Download complete! Opening installer…", 100)
            callback.onComplete(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed", e)
            callback.onError(e.message ?: "Unknown error occurred")
        }
    }

    private fun fetchLatestRelease(callback: ProgressCallback): Pair<String, String>? {
        val repos = listOf(
            "https://api.github.com/repos/ReVanced/revanced-manager/releases/latest",
            "https://api.github.com/repos/ReVanced/revanced-manager/releases"
        )

        for (url in repos) {
            try {
                callback.onProgress("Checking ReVanced releases…", 5)
                val request = Request.Builder()
                    .url(url)
                    .header("Accept", "application/vnd.github+json")
                    .build()
                val response = client.newCall(request).execute()
                if (!response.isSuccessful) continue

                val body = response.body?.string() ?: continue
                val jsonArray = if (body.trimStart().startsWith("[")) {
                    JSONObject(body.substringAfter("[").substringBefore("]").let { "[$it]" })
                    org.json.JSONArray(body)
                } else {
                    org.json.JSONArray("[$body]")
                }

                val json = if (jsonArray.length() > 0) jsonArray.getJSONObject(0) else continue
                val assets = json.getJSONArray("assets")

                for (i in 0 until assets.length()) {
                    val asset = assets.getJSONObject(i)
                    val name = asset.getString("name")
                    if (name.endsWith(".apk") && !name.contains(".asc")) {
                        val downloadUrl = asset.getString("browser_download_url")
                        val sizeMb = asset.getLong("size") / 1_048_576
                        callback.onProgress("Found $name ($sizeMb MB)", 8)
                        return Pair(name, downloadUrl)
                    }
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed: $url", e)
                continue
            }
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
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            apkFile
        )
        val intent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(intent)
    }
}
