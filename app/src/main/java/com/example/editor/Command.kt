package com.example.editor

interface Command {
    fun execute()
    fun undo()
}
