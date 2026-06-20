package lokal.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Arrangement
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import lokal.terminal.CliBounds
import lokal.terminal.LocalInteractionManager

@Composable
fun ChatScrollArea(
    messages: List<Pair<String, String>>,
    modifier: Modifier = Modifier
) {
    val terminalState = LocalTerminalState.current
    val terminalWidth = terminalState.size.columns

    val allMessageLines = remember(messages, terminalWidth) {
        messages.flatMap { (sender, text) ->
            val fullText = "$sender: $text"
            val lines = mutableListOf<String>()
            fullText.split("\n").forEach { paragraph ->
                var current = paragraph
                while (current.length > terminalWidth && terminalWidth > 0) {
                    lines.add(current.take(terminalWidth))
                    current = current.drop(terminalWidth)
                }
                lines.add(current)
            }
            lines
        }
    }

    // Top (1) + Prompt (approx 3) + Bottom (1) = 5
    val maxLines = maxOf(1, terminalState.size.rows - 5)
    val maxScroll = maxOf(0, allMessageLines.size - maxLines)
    
    // Auto-scroll to bottom on new messages
    var scrollY by remember(allMessageLines.size, maxScroll) { mutableStateOf(maxScroll) }

    val interactionManager = LocalInteractionManager.current
    DisposableEffect(interactionManager, terminalState.size.columns, terminalState.size.rows, maxScroll) {
        interactionManager.registerScrollTarget(
            "chat_history", 
            CliBounds(0, 0, terminalState.size.columns, terminalState.size.rows)
        ) { direction ->
            scrollY = (scrollY + direction).coerceIn(0, maxScroll)
        }
        onDispose {
            interactionManager.unregisterScrollTarget("chat_history")
        }
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.Top,
    ) {
        val visibleLines = allMessageLines.drop(scrollY).take(maxLines)
        visibleLines.forEach { line ->
            Text(
                value = line,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}
