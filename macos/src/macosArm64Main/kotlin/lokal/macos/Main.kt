package lokal.macos

import kotlinx.coroutines.runBlocking
import lokal.platformModule
import lokal.runLokal
import lokal.terminal.TerminalController

import lokal.createKoogAgent

@OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
fun main() {
    // Disable any standard output (including koog kotlin-logging) so it doesn't break Mosaic TUI.
    // Mosaic is now configured to write directly to Tty.
    platform.posix.freopen("/dev/null", "w", platform.posix.stdout)

    // TODO: Update this path to your actual local GGUF model path
    val modelPath = "/Users/anthony/Desktop/Lokal/models/qwen3.5-9b-q4_k_m.gguf"
    val agent = createKoogAgent(modelPath)

    runBlocking {
        runLokal(platformModule(MacosTerminalController(), agent))
    }
}

private class MacosTerminalController : TerminalController {
    override fun enterAlternateScreen() {
        print("\u001B[?1049h\u001B[H\u001B[2J\u001B[?25l")
    }

    override fun exitAlternateScreen() {
        print("\u001B[?25h\u001B[?1049l")
    }
}
