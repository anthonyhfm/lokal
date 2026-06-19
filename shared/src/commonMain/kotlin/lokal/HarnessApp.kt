package lokal

import androidx.compose.runtime.BroadcastFrameClock
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.Mosaic
import com.jakewharton.mosaic.TextCanvas
import com.jakewharton.mosaic.layout.DrawScope
import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.terminal.Terminal
import com.jakewharton.mosaic.tty.Tty
import com.jakewharton.mosaic.tty.terminal.asTerminalIn
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Box
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import kotlin.time.TimeSource
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.core.module.Module
import org.koin.dsl.module

interface TerminalController {
    fun enterAlternateScreen()

    fun exitAlternateScreen()
}

fun platformModule(terminalController: TerminalController): Module = module {
    single<TerminalController> { terminalController }
}

private val sharedModule = module {
    single { LokalApplication(get()) }
}

suspend fun runLokal(platformModule: Module) {
    val koin = startKoin {
        modules(sharedModule, platformModule)
    }.koin

    try {
        koin.get<LokalApplication>().run()
    } finally {
        stopKoin()
    }
}

class LokalApplication(
    private val terminalController: TerminalController,
) {
    suspend fun run() {
        runFullscreenMosaic(terminalController) {
            val terminalState = LocalTerminalState.current
            var rainbowPhase by remember { mutableIntStateOf(0) }

            LaunchedEffect(Unit) {
                while (true) {
                    delay(16)
                    rainbowPhase++
                }
            }

            Box(
                modifier = Modifier
                    .size(terminalState.size.columns, terminalState.size.rows)
                    .drawBehind {
                        drawRotatingRainbowBorder(rainbowPhase)
                    },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    value = "Welcome to Lokal!",
                )
            }
        }
    }
}

private suspend fun runFullscreenMosaic(
    terminalController: TerminalController,
    content: @Composable () -> Unit,
) = coroutineScope {
    val tty = Tty.tryBind() ?: error("Unable to run in non-interactive mode.")
    tty.asTerminalIn(this).use { terminal ->
        terminalController.enterAlternateScreen()
        try {
            val clock = BroadcastFrameClock()
            val rendering = FullscreenRendering(terminal)
            val mosaic = Mosaic(
                coroutineContext = coroutineContext + clock,
                onDraw = { rootNode ->
                    print(rendering.render(rootNode).toString())
                },
                terminal = terminal,
            )

            mosaic.setContent(content)

            val frameClockJob = launch {
                val timeSource = TimeSource.Monotonic
                val start = timeSource.markNow()
                while (true) {
                    clock.sendFrame(start.elapsedNow().inWholeNanoseconds)
                    delay(1)
                }
            }

            try {
                mosaic.awaitComplete()
            } finally {
                frameClockJob.cancelAndJoin()
            }
        } finally {
            terminalController.exitAlternateScreen()
        }
    }
}

private class FullscreenRendering(
    private val terminal: Terminal,
) {
    private val builder = StringBuilder(100)

    fun render(mosaic: Mosaic): CharSequence {
        return builder.apply {
            clear()
            append("\u001B[H")

            if (terminal.capabilities.synchronizedOutput) {
                append("\u001B[?2026h")
            }

            appendSurface(mosaic.draw())

            if (terminal.capabilities.synchronizedOutput) {
                append("\u001B[?2026l")
            }
        }
    }

    private fun StringBuilder.appendSurface(surface: TextCanvas) {
        for (row in 0 until surface.height) {
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

private fun DrawScope.drawRotatingRainbowBorder(phase: Int) {
    if (width < 2 || height < 2) return

    val perimeter = width * 2
    for (column in 0 until width) {
        drawForegroundCell(
            column = column,
            row = 0,
            char = "▀",
            color = rainbowColor(column, phase, perimeter),
        )

        val bottomColumn = width - 1 - column
        drawForegroundCell(
            column = bottomColumn,
            row = height - 1,
            char = "▄",
            color = rainbowColor(width + column, phase, perimeter),
        )
    }
}

private fun DrawScope.drawForegroundCell(column: Int, row: Int, char: String, color: Color) {
    if (column !in 0 until width || row !in 0 until height) return

    drawText(
        row = row,
        column = column,
        string = char,
        foreground = color,
        textStyle = TextStyle.Bold,
    )
}

private fun rainbowColor(position: Int, phase: Int, perimeter: Int): Color {
    val hue = (position - phase).floorMod(perimeter).toFloat() / perimeter.toFloat()
    return hsvToRgb(hue, saturation = 1.0f, value = 1.0f)
}

private fun hsvToRgb(hue: Float, saturation: Float, value: Float): Color {
    val scaledHue = hue * 6.0f
    val sector = scaledHue.toInt()
    val fraction = scaledHue - sector
    val p = value * (1.0f - saturation)
    val q = value * (1.0f - fraction * saturation)
    val t = value * (1.0f - (1.0f - fraction) * saturation)

    val (red, green, blue) = when (sector % 6) {
        0 -> Triple(value, t, p)
        1 -> Triple(q, value, p)
        2 -> Triple(p, value, t)
        3 -> Triple(p, q, value)
        4 -> Triple(t, p, value)
        else -> Triple(value, p, q)
    }

    return Color(
        red = (red * 255.0f + 0.5f).toInt().coerceIn(0, 255),
        green = (green * 255.0f + 0.5f).toInt().coerceIn(0, 255),
        blue = (blue * 255.0f + 0.5f).toInt().coerceIn(0, 255),
    )
}

private fun Int.floorMod(other: Int): Int {
    val remainder = this % other
    return if (remainder >= 0) remainder else remainder + other
}
