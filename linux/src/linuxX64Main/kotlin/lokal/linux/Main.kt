package lokal.linux

import kotlinx.coroutines.runBlocking
import lokal.platformModule
import lokal.runLokal
import lokal.terminal.TerminalController

fun main() {
    runBlocking {
        runLokal(platformModule(LinuxTerminalController()))
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
