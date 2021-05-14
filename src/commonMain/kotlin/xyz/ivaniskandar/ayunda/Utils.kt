package xyz.ivaniskandar.ayunda

import com.soywiz.korio.util.UUID

fun String.isUUID(): Boolean {
    return try {
        UUID.invoke(this)
        true
    } catch (e: Exception) {
        false
    }
}

fun Float.toPercentageString(): String {
    return "${(this * 100).toInt()}%"
}
