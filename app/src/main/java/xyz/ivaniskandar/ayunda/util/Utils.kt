package xyz.ivaniskandar.ayunda.util

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.util.UUID

fun Uri.getDisplayName(context: Context): String? {
    context.contentResolver.query(this, null, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val i = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            return cursor.getString(i)
        }
    }
    return null
}

fun String.isUUID(): Boolean {
    return try {
        UUID.fromString(this)
        true
    } catch (e: IllegalArgumentException) {
        false
    }
}
