package com.dipto.clify.model

data class PatchItem(
    val id: String,
    val titleRes: Int,
    val descRes: Int,
    val defaultEnabled: Boolean = true,
    var enabled: Boolean = defaultEnabled,
    val builtIn: Boolean = true
)
