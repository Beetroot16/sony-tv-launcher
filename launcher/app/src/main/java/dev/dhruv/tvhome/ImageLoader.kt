package dev.dhruv.tvhome

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.LruCache
import android.widget.ImageView
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors

/** Minimal poster loader: http(s)/content/file/android.resource URIs, downsampled, LRU cached. */
object ImageLoader {
    private const val MAX_BYTES = 8 * 1024 * 1024

    private val executor = Executors.newFixedThreadPool(2)
    private val main = Handler(Looper.getMainLooper())
    private val cache = object : LruCache<String, Bitmap>(16 * 1024 * 1024) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }

    fun load(context: Context, uri: String, target: ImageView, maxWidth: Int, onLoaded: () -> Unit) {
        target.tag = uri
        cache.get(uri)?.let {
            target.setImageBitmap(it)
            onLoaded()
            return
        }
        val app = context.applicationContext
        executor.execute {
            val bmp = runCatching { decode(app, uri, maxWidth) }.getOrNull() ?: return@execute
            cache.put(uri, bmp)
            main.post {
                if (target.tag == uri) {
                    target.setImageBitmap(bmp)
                    onLoaded()
                }
            }
        }
    }

    private fun decode(context: Context, uri: String, maxWidth: Int): Bitmap? {
        val bytes = open(context, uri)?.use { readCapped(it) } ?: return null
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
        var sample = 1
        while (bounds.outWidth / (sample * 2) >= maxWidth) sample *= 2
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }

    private fun open(context: Context, uri: String): InputStream? {
        val parsed = Uri.parse(uri)
        return when (parsed.scheme?.lowercase()) {
            "http", "https" -> (URL(uri).openConnection() as HttpURLConnection).run {
                connectTimeout = 5000
                readTimeout = 8000
                instanceFollowRedirects = true
                if (responseCode !in 200..299) null else inputStream
            }
            else -> context.contentResolver.openInputStream(parsed)
        }
    }

    private fun readCapped(input: InputStream): ByteArray? {
        val out = java.io.ByteArrayOutputStream()
        val buf = ByteArray(16 * 1024)
        while (true) {
            val n = input.read(buf)
            if (n < 0) break
            out.write(buf, 0, n)
            if (out.size() > MAX_BYTES) return null
        }
        return out.toByteArray()
    }
}
