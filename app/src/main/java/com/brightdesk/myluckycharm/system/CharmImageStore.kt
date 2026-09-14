package com.brightdesk.myluckycharm.system

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File

/**
 * Copies a picked photo into app-private storage (spec §8). Copying rather than
 * holding a persistable URI permission keeps the charm working if the original
 * is moved or deleted, and across OS versions that treat those grants
 * differently.
 */
object CharmImageStore {

    private const val FILE_PREFIX = "custom_charm_"

    /** A charm is drawn a few dozen dp across; full camera resolution is pure waste. */
    private const val MAX_DIMENSION = 512

    fun save(context: Context, uri: Uri): String? {
        val bitmap = decodeScaled(context, uri) ?: return null
        val file = File(context.filesDir, "$FILE_PREFIX${System.currentTimeMillis()}.png")
        file.outputStream().use { output -> bitmap.compress(Bitmap.CompressFormat.PNG, 100, output) }
        bitmap.recycle()
        deleteOthers(context, keep = file.name)
        return file.absolutePath
    }

    fun load(path: String): Bitmap? = BitmapFactory.decodeFile(path)

    private fun decodeScaled(context: Context, uri: Uri): Bitmap? {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        // The elvis has to guard the *stream*, not the decode: a bounds-only
        // decode always returns null by design, so `openInputStream(uri)?.use {
        // decodeStream(...) } ?: return null` bails out on every photo ever
        // picked. That silently turned the whole save path into a no-op.
        val boundsStream = context.contentResolver.openInputStream(uri) ?: return null
        boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null

        var sampleSize = 1
        while (bounds.outWidth / sampleSize > MAX_DIMENSION ||
            bounds.outHeight / sampleSize > MAX_DIMENSION
        ) {
            sampleSize *= 2
        }

        val options = BitmapFactory.Options().apply { inSampleSize = sampleSize }
        return context.contentResolver.openInputStream(uri)?.use {
            BitmapFactory.decodeStream(it, null, options)
        }
    }

    /** Each pick writes a fresh timestamped file, so clear out the previous one. */
    private fun deleteOthers(context: Context, keep: String) {
        context.filesDir.listFiles()
            ?.filter { it.name.startsWith(FILE_PREFIX) && it.name != keep }
            ?.forEach { it.delete() }
    }
}
