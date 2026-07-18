package com.dipto.clify.patcher

import android.content.Context
import android.content.Intent
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
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(120, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    private val downloadsDir: File
        get() = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "clify").also { it.mkdirs() }

    suspend fun startPatching(callback: ProgressCallback) = withContext(Dispatchers.IO) {
        try {
            callback.onProgress("Checking latest NewPipe…", 5)
            val release = fetchLatestRelease(callback) ?: run {
                callback.onError("Could not find NewPipe release.")
                return@withContext
            }

            callback.onProgress("Downloading NewPipe ${release.first}…", 15)
            val outputFile = File(downloadsDir, release.first)
            downloadFile(release.second, outputFile, callback)

            if (outputFile.length() < 1_000_000) {
                callback.onError("Download failed — file too small.")
                outputFile.delete()
                return@withContext
            }

            callback.onProgress("Download complete!", 100)
            callback.onComplete(outputFile)
        } catch (e: Exception) {
            Log.e(TAG, "Failed", e)
            callback.onError(e.message ?: "Unknown error")
        }
    }

    private fun fetchLatestRelease(callback: ProgressCallback): Pair<String, String>? {
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/TeamNewPipe/NewPipe/releases/latest")
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
                if (name.endsWith(".apk")) {
                    val url = asset.getString("browser_download_url")
                    val sizeMb = asset.getLong("size") / 1_048_576
                    callback.onProgress("Found NewPipe ($sizeMb MB)", 10)
                    return Pair(name, url)
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Failed to fetch release", e)
        }
        return null
    }

    private fun downloadFile(url: String, outputFile: File, callback: ProgressCallback) {
        if (outputFile.exists()) outputFile.delete()

        val request = Request.Builder().url(url).build()
        val response = client.newCall(request).execute()
        if (!response.isSuccessful) throw Exception("Download failed: HTTP ${response.code}")

        val body = response.body ?: throw Exception("Empty response")
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
                        val percent = 15 + (progress * 85 / 100).toInt()
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

    companion object {
        private const val TAG = "PatchEngine"
    }
}
