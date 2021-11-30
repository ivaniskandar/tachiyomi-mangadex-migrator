package eu.kanade.tachiyomi.data.backup.full.models

import kotlinx.serialization.Serializable
import kotlinx.serialization.protobuf.ProtoNumber

@Serializable
data class BrokenBackupSource(
    @ProtoNumber(0) var name: String = "",
    @ProtoNumber(1) var sourceId: Long
)

@Serializable
data class BackupSource(
    @ProtoNumber(1) var name: String = "",
    @ProtoNumber(2) var sourceId: Long
)
