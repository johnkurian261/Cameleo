package com.craftcam.app.processor

import android.content.ContentResolver
import android.content.ContentValues
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Log
import java.io.IOException
import java.io.OutputStream

/** Returns the new URI only after the writer closes and the item is published. */
internal fun ContentResolver.writeMediaItem(
    collection: Uri,
    values: ContentValues,
    write: (OutputStream) -> Unit
): Uri? {
    val uri = insert(collection, values) ?: return null
    try {
        val stream = openOutputStream(uri) ?: throw IOException("No output stream for $uri")
        stream.use(write)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val published = ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }
            if (update(uri, published, null, null) != 1) throw IOException("Failed to publish $uri")
        }
        return uri
    } catch (e: Exception) {
        try {
            delete(uri, null, null)
        } catch (cleanup: Exception) {
            Log.w("MediaStoreWriter", "Failed to discard incomplete item $uri", cleanup)
        }
        throw e
    }
}
