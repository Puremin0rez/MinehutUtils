package me.santio.minehututils.skript.models

import com.google.gson.annotations.SerializedName

data class SkriptExample(
    @SerializedName("syntax_element")
    val syntaxElement: Long,
    @SerializedName("example_code")
    val exampleCode: String,
    val score: Long
)
