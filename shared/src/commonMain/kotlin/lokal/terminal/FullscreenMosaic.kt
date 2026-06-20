package lokal.terminal

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import com.jakewharton.mosaic.Mosaic
import com.jakewharton.mosaic.TextCanvas
import com.jakewharton.mosaic.terminal.Event
import com.jakewharton.mosaic.terminal.KeyboardEvent
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

import androidx.compose.runtime.staticCompositionLocalOf
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.emptyFlow

val LocalRawKeyboardEvents = staticCompositionLocalOf<Flow<KeyboardEvent>> { emptyFlow() }

suspend fun runFullscreenMosaic(
    terminalController: TerminalController,
    content: @Composable () -> Unit,
) = coroutineScope {
    val tty = Tty.tryBind() ?: error("Unable to run in non-interactive mode.")

    tty.asTerminalIn(this).use { terminal ->
        tty.writeString("\u001B[?1049h\u001B[H\u001B[2J\u001B[?25l")
        tty.writeString(EnableMouseTracking)

        try {
            val clock = BroadcastFrameClock()
            val rendering = FullscreenRendering(terminal)
            val interactionManager = CliInteractionManager()
            val forwardedEvents = Channel<Event>(Channel.UNLIMITED)
            val rawKeyboardEvents = MutableSharedFlow<KeyboardEvent>(extraBufferCapacity = 64)
            val interactiveTerminal = InteractiveTerminal(terminal, forwardedEvents)
            val timeSource = TimeSource.Monotonic
            val start = timeSource.markNow()

            val eventPump = launch {
                for (event in terminal.events) {
                    if (event is MouseEvent) {
                        interactionManager.handleMouseEvent(event)
                    }
                    if (event is MouseEvent || event is ResizeEvent || event is KeyboardEvent) {
                        clock.sendFrame(start.elapsedNow().inWholeNanoseconds)
                    }
                    if (event is KeyboardEvent) {
                        rawKeyboardEvents.tryEmit(event)
                        val codepoint = event.codepoint
                        val isSafe = codepoint == 9 || codepoint == 13 || codepoint == 27 ||
                                     codepoint in 32..126 || codepoint == 127 ||
                                     codepoint in 57348..57357 || codepoint in 57364..57398
                        if (isSafe) {
                            forwardedEvents.send(event)
                        }
                    } else {
                        forwardedEvents.send(event)
                    }
                }
            }

            val mosaic = Mosaic(
                coroutineContext = coroutineContext + clock,
                onDraw = { rootNode ->
                    tty.writeString(rendering.render(rootNode).toString())
                },
                terminal = interactiveTerminal,
            )

            mosaic.setContent {
                CompositionLocalProvider(
                    LocalInteractionManager provides interactionManager,
                    LocalRawKeyboardEvents provides rawKeyboardEvents
                ) {
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

            tty.writeString("\u001B[?25h\u001B[?1049l")
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
