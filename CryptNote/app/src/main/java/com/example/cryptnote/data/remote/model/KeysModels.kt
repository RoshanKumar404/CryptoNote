package com.example.cryptnote.data.remote.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject

@Serializable
data class GetKeysResponseDto(
    @SerialName("exists") val exists: Boolean,
    @SerialName("latest") val latest: WrappedKeyRecordDto? = null,
    @SerialName("older_key_versions") val olderKeyVersions: List<Int> = emptyList()
)

@Serializable
data class WrappedKeyRecordDto(
    @SerialName("key_version") val keyVersion: Int,
    @SerialName("pass_wrapped_mk") val passWrappedMk: String,
    @SerialName("pass_salt") val passSalt: String,
    @SerialName("kdf_params") val kdfParams: JsonObject? = null,
    @SerialName("recovery_wrapped_mk") val recoveryWrappedMk: String,
    @SerialName("kcv") val kcv: String? = null,
    @SerialName("updated_at") val updatedAt: String? = null
)

@Serializable
data class PutKeysRequestDto(
    @SerialName("pass_wrapped_mk") val passWrappedMk: String,
    @SerialName("pass_salt") val passSalt: String,
    @SerialName("kdf_params") val kdfParams: JsonObject,
    @SerialName("recovery_wrapped_mk") val recoveryWrappedMk: String,
    @SerialName("kcv") val kcv: String? = null,
    @SerialName("current_key_version") val currentKeyVersion: Int? = null
)

@Serializable
data class PutKeysResponseDto(
    @SerialName("key_version") val keyVersion: Int,
    @SerialName("status") val status: String
)
