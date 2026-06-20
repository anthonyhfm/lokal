package lokal.ui.components

data class VisualPosition(val row: Int, val column: Int)

fun wrapText(text: String, availableWidth: Int): List<String> {
    if (availableWidth <= 0) return listOf(text)
    val result = mutableListOf<String>()
    for (logicalLine in text.split('\n')) {
        if (logicalLine.isEmpty()) {
            result.add("")
        } else if (logicalLine.length <= availableWidth) {
            result.add(logicalLine)
        } else {
            var i = 0
            while (i < logicalLine.length) {
                val end = minOf(i + availableWidth, logicalLine.length)
                result.add(logicalLine.substring(i, end))
                i = end
            }
        }
    }
    if (result.isEmpty()) result.add("")
    return result
}

fun cursorToVisualPosition(
    text: String,
    cursorOffset: Int,
    availableWidth: Int,
): VisualPosition {
    var remaining = cursorOffset
    var row = 0
    for (logicalLine in text.split('\n')) {
        if (logicalLine.length <= availableWidth) {
            if (remaining <= logicalLine.length) {
                return VisualPosition(row, remaining)
            }
            remaining -= logicalLine.length + 1
            row++
        } else {
            var i = 0
            while (i < logicalLine.length) {
                val lineLen = minOf(availableWidth, logicalLine.length - i)
                if (remaining <= lineLen) {
                    return VisualPosition(row, remaining)
                }
                remaining -= lineLen
                i += lineLen
                row++
            }
            remaining--
        }
    }
    return VisualPosition(row, 0)
}
