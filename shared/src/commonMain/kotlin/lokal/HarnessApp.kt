package lokal

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.LocalTerminalState
import com.jakewharton.mosaic.layout.DrawScope
import com.jakewharton.mosaic.layout.drawBehind
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.layout.size
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.ui.Alignment
import com.jakewharton.mosaic.ui.Arrangement
import com.jakewharton.mosaic.ui.Color
import com.jakewharton.mosaic.ui.Column
import com.jakewharton.mosaic.ui.Text
import com.jakewharton.mosaic.ui.TextStyle
import com.jakewharton.mosaic.ui.Row
import lokal.terminal.StatusStripController
import lokal.terminal.StripEffect
import lokal.terminal.TerminalController
import lokal.terminal.runFullscreenMosaic
import lokal.ui.components.PromptEntryField
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import kotlinx.coroutines.launch
import org.koin.core.module.Module
import org.koin.dsl.module

import ai.koog.agents.core.agent.AIAgent
import lokal.ui.views.chat.ChatView
import lokal.ui.views.chat.ChatViewModel

fun platformModule(terminalController: TerminalController, agent: AIAgent<String, String>): Module = module {
    single<TerminalController> { terminalController }
    single<AIAgent<String, String>> { agent }
}

private val sharedModule = module {
    single { StatusStripController() }
    single { ChatViewModel(get()) }
    single { LokalApplication(get(), get(), get()) }
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
    private val stripController: StatusStripController,
    private val chatViewModel: ChatViewModel
) {
    suspend fun run() {
        runFullscreenMosaic(terminalController) {
            val terminalState = LocalTerminalState.current
            
            var topEffect by remember { mutableStateOf(stripController.topStripEffect.value) }
            
            LaunchedEffect(stripController.topStripEffect) {
                stripController.topStripEffect.collect { topEffect = it }
            }
            
            var bottomEffect by remember { mutableStateOf(stripController.bottomStripEffect.value) }

            LaunchedEffect(stripController.bottomStripEffect) {
                stripController.bottomStripEffect.collect { bottomEffect = it }
            }

            Column(
                modifier = Modifier.size(terminalState.size.columns, terminalState.size.rows)
            ) {
                StripView(topEffect, terminalState.size.columns, isTop = true)
                
                ChatView(
                    viewModel = chatViewModel,
                    modifier = Modifier.weight(1f).fillMaxWidth()
                )

                StripView(bottomEffect, terminalState.size.columns, isTop = false)
            }
        }
    }
}

@Composable
private fun StripView(effect: StripEffect, width: Int, isTop: Boolean) {
    val char = if (isTop) "▀" else "▄"

    Row(modifier = Modifier.size(width, 1).drawBehind {
        for (column in 0 until width) {
            val color = when (effect) {
                is StripEffect.Solid -> effect.color
                is StripEffect.Custom -> effect.render(column, width)
            }
            drawForegroundCell(
                column = column,
                row = 0,
                char = char,
                color = color
            )
        }
    }) {}
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
