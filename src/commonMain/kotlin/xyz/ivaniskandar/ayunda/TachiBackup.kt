package xyz.ivaniskandar.ayunda

import com.soywiz.korio.compression.compress
import com.soywiz.korio.compression.deflate.GZIP
import com.soywiz.korio.compression.uncompress
import com.soywiz.krypto.encoding.fromBase64
import eu.kanade.tachiyomi.data.backup.full.models.BackupSerializer
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToByteArray
import kotlinx.serialization.json.Json
import kotlinx.serialization.protobuf.ProtoBuf
import eu.kanade.tachiyomi.data.backup.full.models.Backup as FullBackup
import xyz.ivaniskandar.ayunda.model.Backup as LegacyBackup

object TachiBackup {
    private val protoBufParser = ProtoBuf
    private val jsonParser = Json { ignoreUnknownKeys = true }

    fun decodeProto(base64: String): FullBackup {
        val byteArray = base64.fromBase64().uncompress(GZIP)
        return protoBufParser.decodeFromByteArray(BackupSerializer, byteArray)
    }

    fun encodeProto(backup: FullBackup): ByteArray {
        return protoBufParser.encodeToByteArray(backup).compress(GZIP)
    }

    fun decodeJson(string: String): LegacyBackup {
        return jsonParser.decodeFromString(string)
    }

    fun encodeJson(backup: LegacyBackup): ByteArray {
        return jsonParser.encodeToString(LegacyBackup.serializer(), backup).encodeToByteArray()
    }
}
