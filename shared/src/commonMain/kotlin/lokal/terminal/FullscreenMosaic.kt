package lokal.terminal

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.jakewharton.mosaic.Mosaic
import com.jakewharton.mosaic.TextCanvas
import com.jakewharton.mosaic.terminal.Event
import com.jakewharton.mosaic.terminal.MouseEvent
import com.jakewharton.mosaic.terminal.ResizeEvent
import com.jakewharton.mosaic.terminal.Terminal
import com.jakewharton.mosaic.tty.Tty
import com.jakewharton.mosaic.tty.terminal.asTerminalIn
import kotlin.time.TimeSource
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

suspend fun runFullscreenMosaic(
    terminalController: TerminalController,
    content: @Composable () -> Unit,
) = coroutineScope {
    val tty = Tty.tryBind() ?: error("Unable to run in non-interactive mode.")

    tty.asTerminalIn(this).use { terminal ->
        terminalController.enterAlternateScreen()
        tty.writeString(EnableMouseTracking)

        try {
            val clock = BroadcastFrameClock()
            val rendering = FullscreenRendering(terminal)
            val interactionManager = CliInteractionManager()
            val forwardedEvents = Channel<Event>(Channel.UNLIMITED)
            val interactiveTerminal = InteractiveTerminal(terminal, forwardedEvents)
            val timeSource = TimeSource.Monotonic
            val start = timeSource.markNow()

            val eventPump = launch {
                for (event in terminal.events) {
                    if (event is MouseEvent) {
                        interactionManager.handleMouseEvent(event)
                    }
                    if (event is MouseEvent || event is ResizeEvent) {
                        clock.sendFrame(start.elapsedNow().inWholeNanoseconds)
                    }
                    forwardedEvents.send(event)
                }
            }

            val mosaic = Mosaic(
                coroutineContext = coroutineContext + clock,
                onDraw = { rootNode ->
                    print(rendering.render(rootNode).toString())
                },
                terminal = interactiveTerminal,
            )

            mosaic.setContent {
                CompositionLocalProvider(LocalInteractionManager provides interactionManager) {
                    content()
                }
            }

            val frameClockJob = launch {
                while (true) {
                    clock.sendFrame(start.elapsedNow().inWholeNanoseconds)

                    delay(1)
                }
            }

            try {
                mosaic.awaitComplete()
            } finally {
                frameClockJob.cancelAndJoin()
                eventPump.cancel()
                forwardedEvents.close()
            }
        } finally {
            tty.writeString(DisableMouseTracking)

            terminalController.exitAlternateScreen()
        }
    }
}

private class InteractiveTerminal(
    private val delegate: Terminal,
    override val events: ReceiveChannel<Event>,
) : Terminal {
    override val name: String? get() = delegate.name
    override val interactive: Boolean get() = delegate.interactive
    override val state: Terminal.State get() = delegate.state
    override val capabilities: Terminal.Capabilities get() = delegate.capabilities

    override fun close() = Unit
}

private class FullscreenRendering(
    private val terminal: Terminal,
) {
    private val builder = StringBuilder(100)
    private var lastWidth = -1
    private var lastHeight = -1

    fun render(mosaic: Mosaic): CharSequence {
        val surface = mosaic.draw()

        return builder.apply {
            clear()
            append("\u001B[H")

            if (surface.width != lastWidth || surface.height != lastHeight) {
                append("\u001B[2J\u001B[3J")
                lastWidth = surface.width
                lastHeight = surface.height
            }

            if (terminal.capabilities.synchronizedOutput) {
                append("\u001B[?2026h")
            }

            appendSurface(surface)

            if (terminal.capabilities.synchronizedOutput) {
                append("\u001B[?2026l")
            }
        }
    }

    private fun StringBuilder.appendSurface(surface: TextCanvas) {
        for (row in 0 until surface.height) {
            append("\u001B[2K")
            surface.appendRowTo(
                appendable = this,
                row = row,
                ansiLevel = terminal.capabilities.ansiLevel,
                supportsKittyUnderlines = terminal.capabilities.kittyUnderline,
            )

            if (row != surface.height - 1) {
                append("\r\n")
            }
        }
    }
}

private fun Tty.writeString(value: String) {
    val bytes = value.encodeToByteArray()
    write(bytes, 0, bytes.size)
}

private const val EnableMouseTracking = "\u001B[?1003h\u001B[?1006h"
private const val DisableMouseTracking = "\u001B[?1003l\u001B[?1006l"
