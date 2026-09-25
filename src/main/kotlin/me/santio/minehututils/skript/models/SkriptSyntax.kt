package me.santio.minehututils.skript.models

import com.google.gson.annotations.SerializedName

data class SkriptSyntax(
    val id: Long,
    var title: String,
    val description: String,
    @SerializedName("syntax_pattern")
    val syntaxPattern: String,
    val addon: String,
    val link: String
)
