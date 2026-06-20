package lokal.linux

import kotlinx.coroutines.runBlocking
import lokal.platformModule
import lokal.runLokal
import lokal.terminal.TerminalController

import lokal.createKoogAgent

fun main() {
    // TODO: Update this path to your actual local GGUF model path
    val modelPath = "/Users/anthony/Desktop/Lokal/models/gemma-4-12b-it-Q4_K_M.gguf"
    val agent = createKoogAgent(modelPath)

    runBlocking {
        runLokal(platformModule(LinuxTerminalController(), agent))
    }
}

private class LinuxTerminalController : TerminalController {
    override fun enterAlternateScreen() {
        print("\u001B[?1049h\u001B[H\u001B[2J\u001B[?25l")
    }

    override fun exitAlternateScreen() {
        print("\u001B[?25h\u001B[?1049l")
    }
}
