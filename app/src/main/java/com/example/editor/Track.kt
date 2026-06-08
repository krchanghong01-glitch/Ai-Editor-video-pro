package com.example.editor

import java.io.Serializable

data class Track(
    val id: String = java.util.UUID.randomUUID().toString(),
    val trackLabel: String, // "V1", "V2", "A1", "A2"
    val clips: ArrayList<Clip> = ArrayList()
) : Serializable
