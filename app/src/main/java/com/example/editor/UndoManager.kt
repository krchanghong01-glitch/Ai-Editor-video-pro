package com.example.editor

import java.util.Stack

class UndoManager {
    private val undoStack = Stack<Command>()
    private val redoStack = Stack<Command>()
    private val limit = 50

    fun executeCommand(command: Command) {
        command.execute()
        undoStack.push(command)
        if (undoStack.size > limit) {
            undoStack.removeAt(0)
        }
        redoStack.clear()
    }

    fun undo(): Boolean {
        if (undoStack.isNotEmpty()) {
            val command = undoStack.pop()
            command.undo()
            redoStack.push(command)
            return true
        }
        return false
    }

    fun redo(): Boolean {
        if (redoStack.isNotEmpty()) {
            val command = redoStack.pop()
            command.execute()
            undoStack.push(command)
            return true
        }
        return false
    }

    fun clear() {
        undoStack.clear()
        redoStack.clear()
    }

    fun canUndo(): Boolean = undoStack.isNotEmpty()
    fun canRedo(): Boolean = redoStack.isNotEmpty()
}
