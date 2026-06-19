package lokal.macos

import kotlinx.coroutines.runBlocking
import lokal.TerminalController
import lokal.platformModule
import lokal.runLokal

fun main() {
    runBlocking {
        runLokal(platformModule(MacosTerminalController()))
    }
}

private class MacosTerminalController : TerminalController {
    override fun enterAlternateScreen() {
        print("\u001B[?1049h\u001B[?25l")
    }

    override fun exitAlternateScreen() {
        print("\u001B[?25h\u001B[?1049l")
    }
}
