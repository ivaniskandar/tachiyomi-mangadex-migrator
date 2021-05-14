package xyz.ivaniskandar.ayunda.model

import kotlinx.serialization.Serializable

@Serializable
data class MappingRequest(val type: String, val ids: List<Int>)

@Serializable
data class MappingResult(val data: Data)

@Serializable
data class Data(val attributes: Attributes)

@Serializable
data class Attributes(val legacyId: Int, val newId: String)
