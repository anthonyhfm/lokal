package lokal.ui.components

import androidx.compose.runtime.*
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.DrawScope
import com.jakewharton.mosaic.layout.KeyEvent
import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.layout.onKeyEvent
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Row
import com.jakewharton.mosaic.ui.TextStyle
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.ui.Box

class PromptEntryFieldState(initialText: String = "") {
    var text by mutableStateOf(initialText)
        private set
    var cursorOffset by mutableIntStateOf(initialText.length)
        private set

    fun setText(newText: String) {
        text = newText
        if (cursorOffset > text.length) {
            cursorOffset = text.length
        }
    }

    fun insertChar(char: String) {
        text = text.substring(0, cursorOffset) + char + text.substring(cursorOffset)
        cursorOffset += char.length
    }

    fun insertNewline() {
        insertChar("\n")
    }

    fun deleteBackward() {
        if (cursorOffset > 0) {
            text = text.substring(0, cursorOffset - 1) + text.substring(cursorOffset)
            cursorOffset--
        }
    }

    fun deleteForward() {
        if (cursorOffset < text.length) {
            text = text.substring(0, cursorOffset) + text.substring(cursorOffset + 1)
        }
    }

    fun moveCursorLeft() {
        if (cursorOffset > 0) cursorOffset--
    }

    fun moveCursorRight() {
        if (cursorOffset < text.length) cursorOffset++
    }

    fun moveCursorToLineStart() {
        val lastNewline = text.lastIndexOf('\n', cursorOffset - 1)
        cursorOffset = if (lastNewline == -1) 0 else lastNewline + 1
    }

    fun moveCursorToLineEnd() {
        val nextNewline = text.indexOf('\n', cursorOffset)
        cursorOffset = if (nextNewline == -1) text.length else nextNewline
    }

    fun moveCursorUp() {
        val lastNewline = text.lastIndexOf('\n', cursorOffset - 1)
        if (lastNewline != -1) {
            val col = cursorOffset - (lastNewline + 1)
            val prevNewline = text.lastIndexOf('\n', lastNewline - 1)
            val prevLineStart = if (prevNewline == -1) 0 else prevNewline + 1
            val prevLineLen = lastNewline - prevLineStart
            cursorOffset = prevLineStart + minOf(col, prevLineLen)
        }
    }

    fun moveCursorDown() {
        val nextNewline = text.indexOf('\n', cursorOffset)
        if (nextNewline != -1) {
            val lastNewline = text.lastIndexOf('\n', cursorOffset - 1)
            val col = cursorOffset - (if (lastNewline == -1) 0 else lastNewline + 1)
            val nextNextNewline = text.indexOf('\n', nextNewline + 1)
            val nextLineEnd = if (nextNextNewline == -1) text.length else nextNextNewline
            val nextLineLen = nextLineEnd - (nextNewline + 1)
            cursorOffset = nextNewline + 1 + minOf(col, nextLineLen)
        }
    }

    fun moveCursorWordLeft() {
        cursorOffset = findWordBoundaryLeft(text, cursorOffset)
    }

    fun moveCursorWordRight() {
        cursorOffset = findWordBoundaryRight(text, cursorOffset)
    }

    fun deleteWordBackward() {
        val newOffset = findWordBoundaryLeft(text, cursorOffset)
        text = text.substring(0, newOffset) + text.substring(cursorOffset)
        cursorOffset = newOffset
    }

    fun deleteWordForward() {
        val newOffset = findWordBoundaryRight(text, cursorOffset)
        text = text.substring(0, cursorOffset) + text.substring(newOffset)
    }

    fun killToLineEnd() {
        val nextNewline = text.indexOf('\n', cursorOffset)
        val end = if (nextNewline == -1) text.length else nextNewline
        text = text.substring(0, cursorOffset) + text.substring(end)
    }

    fun killToLineStart() {
        val lastNewline = text.lastIndexOf('\n', cursorOffset - 1)
        val start = if (lastNewline == -1) 0 else lastNewline + 1
        text = text.substring(0, start) + text.substring(cursorOffset)
        cursorOffset = start
    }

    fun transposeChars() {
        if (cursorOffset > 0 && cursorOffset < text.length) {
            val chars = text.toCharArray()
            val temp = chars[cursorOffset - 1]
            chars[cursorOffset - 1] = chars[cursorOffset]
            chars[cursorOffset] = temp
            text = chars.concatToString()
            cursorOffset++
        }
    }

    fun clear() {
        text = ""
        cursorOffset = 0
    }

    private fun findWordBoundaryLeft(text: String, offset: Int): Int {
        var pos = offset - 1
        while (pos > 0 && text[pos].isWhitespace()) pos--
        while (pos > 0 && !text[pos - 1].isWhitespace()) pos--
        return maxOf(0, pos)
    }

    private fun findWordBoundaryRight(text: String, offset: Int): Int {
        var pos = offset
        while (pos < text.length && !text[pos].isWhitespace()) pos++
        while (pos < text.length && text[pos].isWhitespace()) pos++
        return pos
    }
}

@Composable
fun PromptEntryField(
    value: String,
    onValueChange: (String) -> Unit,
    onEnter: () -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "What can I help you with?",
    isFocused: Boolean = true,
) {
    val terminalState = LocalTerminalState.current
    val terminalWidth = terminalState.size.columns

    val contentWidth = terminalWidth - 6

    val state = remember { PromptEntryFieldState(value) }

    LaunchedEffect(value) {
        if (state.text != value) {
            state.setText(value)
        }
    }

    LaunchedEffect(state.text) {
        if (state.text != value) {
            onValueChange(state.text)
        }
    }

    val visualLines = wrapText(state.text, contentWidth)
    val cursorPos = cursorToVisualPosition(state.text, state.cursorOffset, contentWidth)

    val fieldHeight = visualLines.size + 2

    val rawKeyboardEvents = lokal.terminal.LocalRawKeyboardEvents.current

    LaunchedEffect(isFocused) {
        if (isFocused) {
            rawKeyboardEvents.collect { event ->
                if (event.eventType == com.jakewharton.mosaic.terminal.KeyboardEvent.EventTypePress) {
                    val key = when (val cp = event.codepoint) {
                        9 -> "Tab"
                        13 -> "Enter"
                        27 -> "Escape"
                        127 -> "Backspace"
                        57350 -> "ArrowLeft"
                        57351 -> "ArrowRight"
                        57352 -> "ArrowUp"
                        57353 -> "ArrowDown"
                        57348 -> "Insert"
                        57349 -> "Delete"
                        57354 -> "PageUp"
                        57355 -> "PageDown"
                        57356 -> "Home"
                        57357 -> "End"
                        in 57364..57398 -> "F" + (cp - 57363)
                        else -> if (cp in 32..0xFFFF) cp.toChar().toString() else "Unknown"
                    }
                    handleKeyEvent(key, event.ctrl, event.alt, event.shift, state, onEnter)
                }
            }
        }
    }

    Box(
        modifier = modifier
            .size(terminalWidth, fieldHeight)
            .drawBehind {
                val canvasWidth = this.width
                val canvasHeight = this.height
                if (canvasWidth <= 0 || canvasHeight <= 0) return@drawBehind

                val bgColor = if (isFocused) Color(80, 80, 92) else Color(28, 30, 36)
                
                val promptColor = if (isFocused) Color(240, 140, 255) else Color(120, 120, 130)
                val textColor = if (isFocused) Color(255, 255, 255) else Color(160, 160, 170)
                val cursorBg = if (isFocused) Color(120, 230, 255) else Color(160, 160, 170)
                val cursorFg = Color(20, 20, 30)
                val placeholderColor = Color(170, 170, 185)

                val emptyRow = " ".repeat(canvasWidth)
                for (r in 0 until canvasHeight) {
                    drawText(row = r, column = 0, string = emptyRow, background = bgColor)
                }

                if (canvasHeight > 1 && canvasWidth > 2) {
                    drawText(row = 1, column = 2, string = "✦", foreground = promptColor, background = bgColor, textStyle = TextStyle.Bold)
                }

                visualLines.forEachIndexed { lineIndex, line ->
                    val row = lineIndex + 1
                    if (row < canvasHeight - 1) {
                        drawText(row = row, column = 4, string = line, foreground = textColor, background = bgColor)
                    }
                }

                if (state.text.isEmpty() && canvasHeight > 1 && canvasWidth > 4) {
                    drawText(row = 1, column = 4, string = placeholder, foreground = placeholderColor, background = bgColor)
                }

                if (isFocused) {
                    val cursorRow = cursorPos.row + 1
                    val cursorCol = cursorPos.column + 4
                    if (cursorRow < canvasHeight - 1 && cursorCol < canvasWidth) {
                        val cursorChar = if (state.text.isEmpty() && placeholder.isNotEmpty()) {
                            placeholder[0].toString()
                        } else if (state.cursorOffset < state.text.length) {
                            val c = state.text[state.cursorOffset]
                            if (c == '\n') " " else c.toString()
                        } else " "
                        
                        drawText(
                            row = cursorRow, column = cursorCol,
                            string = cursorChar,
                            foreground = cursorFg,
                            background = cursorBg,
                        )
                    }
                }
            }
    )
}

private fun handleKeyEvent(
    key: String,
    ctrl: Boolean,
    alt: Boolean,
    shift: Boolean,
    state: PromptEntryFieldState,
    onEnter: () -> Unit,
): Boolean {
    return when {
        key == "Enter" && !ctrl && !shift && !alt -> { onEnter(); true }
        key == "Enter" && (shift || ctrl || alt) -> { state.insertNewline(); true }
        ctrl && key == "j" -> { state.insertNewline(); true }
        ctrl && key == "o" -> { state.insertNewline(); true }

        key == "ArrowLeft" && alt -> { state.moveCursorWordLeft(); true }
        key == "ArrowLeft" && ctrl -> { state.moveCursorWordLeft(); true }
        key == "ArrowRight" && alt -> { state.moveCursorWordRight(); true }
        key == "ArrowRight" && ctrl -> { state.moveCursorWordRight(); true }

        alt && key == "b" -> { state.moveCursorWordLeft(); true }
        alt && key == "f" -> { state.moveCursorWordRight(); true }

        key == "Home" -> { state.moveCursorToLineStart(); true }
        key == "End" -> { state.moveCursorToLineEnd(); true }
        ctrl && key == "a" -> { state.moveCursorToLineStart(); true }
        ctrl && key == "e" -> { state.moveCursorToLineEnd(); true }

        key == "ArrowLeft" -> { state.moveCursorLeft(); true }
        key == "ArrowRight" -> { state.moveCursorRight(); true }
        key == "ArrowUp" -> { state.moveCursorUp(); true }
        key == "ArrowDown" -> { state.moveCursorDown(); true }
        ctrl && key == "b" -> { state.moveCursorLeft(); true }
        ctrl && key == "f" -> { state.moveCursorRight(); true }

        key == "Backspace" && alt -> { state.deleteWordBackward(); true }
        key == "Backspace" && ctrl -> { state.deleteWordBackward(); true }
        key == "Delete" && alt -> { state.deleteWordForward(); true }
        key == "Delete" && ctrl -> { state.deleteWordForward(); true }
        ctrl && key == "w" -> { state.deleteWordBackward(); true }
        alt && key == "d" -> { state.deleteWordForward(); true }

        ctrl && key == "k" -> { state.killToLineEnd(); true }
        ctrl && key == "u" -> { state.killToLineStart(); true }

        key == "Backspace" -> { state.deleteBackward(); true }
        key == "Delete" -> { state.deleteForward(); true }
        ctrl && key == "d" -> { state.deleteForward(); true }

        ctrl && key == "t" -> { state.transposeChars(); true }

        key.length == 1 && !ctrl && !alt -> {
            state.insertChar(key)
            true
        }

        else -> false
    }
}
