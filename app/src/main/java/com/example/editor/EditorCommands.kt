package com.example.editor

class AddClipCommand(
    private val track: Track,
    private val clip: Clip,
    private val onUpdate: () -> Unit
) : Command {
    override fun execute() {
        track.clips.add(clip)
        onUpdate()
    }

    override fun undo() {
        track.clips.remove(clip)
        onUpdate()
    }
}

class DeleteClipCommand(
    private val track: Track,
    private val clip: Clip,
    private val onUpdate: () -> Unit
) : Command {
    private var insertionIndex: Int = -1

    override fun execute() {
        insertionIndex = track.clips.indexOf(clip)
        if (insertionIndex != -1) {
            track.clips.removeAt(insertionIndex)
        }
        onUpdate()
    }

    override fun undo() {
        if (insertionIndex != -1 && insertionIndex <= track.clips.size) {
            track.clips.add(insertionIndex, clip)
        } else {
            track.clips.add(clip)
        }
        onUpdate()
    }
}

class UpdateClipCommand(
    private val clip: Clip,
    private val oldClipState: Clip,
    private val newClipState: Clip,
    private val onUpdate: () -> Unit
) : Command {
    override fun execute() {
        applyState(clip, newClipState)
        onUpdate()
    }

    override fun undo() {
        applyState(clip, oldClipState)
        onUpdate()
    }

    private fun applyState(target: Clip, source: Clip) {
        target.fileName = source.fileName
        target.durationMs = source.durationMs
        target.startOffsetMs = source.startOffsetMs
        target.speed = source.speed
        target.volume = source.volume
        target.cropRatio = source.cropRatio
        target.textAnnotation = source.textAnnotation
        target.fontName = source.fontName
        target.keyframePositionX = source.keyframePositionX
        target.keyframePositionY = source.keyframePositionY
        target.keyframeScale = source.keyframeScale
        target.keyframeRotation = source.keyframeRotation
        target.keyframeOpacity = source.keyframeOpacity
        target.hasChromaKey = source.hasChromaKey
        target.chromaColor = source.chromaColor
        target.activeFilter = source.activeFilter
        target.activeFx = source.activeFx
        target.hasAiCutout = source.hasAiCutout
        target.hasRadialMask = source.hasRadialMask
        target.maskInverted = source.maskInverted
    }
}
