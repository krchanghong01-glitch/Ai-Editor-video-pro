package com.example.editor

import java.io.Serializable

data class Clip(
    val id: String = java.util.UUID.randomUUID().toString(),
    var type: String, // "video", "image", "audio", "voice", "subtitle", "sticker"
    var fileName: String,
    var durationMs: Long = 5000L,
    var startOffsetMs: Long = 0L,
    var speed: Float = 1.0f,
    var volume: Float = 1.0f,
    var cropRatio: String = "free",
    var textAnnotation: String = "",
    var fontName: String = "Default",
    var keyframePositionX: Float = 0f,
    var keyframePositionY: Float = 0f,
    var keyframeScale: Float = 1.0f,
    var keyframeRotation: Float = 0f,
    var keyframeOpacity: Float = 1.0f,
    var hasChromaKey: Boolean = false,
    var chromaColor: Int = 0xFF00FF00.toInt(),
    var activeFilter: String = "none",
    var activeFx: String = "none",
    var hasAiCutout: Boolean = false,
    var hasRadialMask: Boolean = false,
    var maskInverted: Boolean = false
) : Serializable
