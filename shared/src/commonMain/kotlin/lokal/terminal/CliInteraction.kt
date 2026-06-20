package lokal.terminal

import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.terminal.MouseEvent

data class CliBounds(
    val column: Int,
    val row: Int,
    val width: Int,
    val height: Int,
) {
    fun contains(column: Int, row: Int): Boolean {
        return column in this.column until this.column + width &&
            row in this.row until this.row + height
    }
}

private data class CliPointerPosition(
    val column: Int,
    val row: Int,
)

private class CliInteractionTarget(
    val key: Any,
    val bounds: CliBounds,
    val onClick: () -> Unit,
)

class CliInteractionManager {
    private var pointerPosition by mutableStateOf<CliPointerPosition?>(null)
    var lastAction by mutableStateOf("Click a button")

    private val clickTargets = mutableMapOf<Any, CliInteractionTarget>()
    private val scrollTargets = mutableMapOf<Any, CliScrollTarget>()

    fun registerClickTarget(key: Any, bounds: CliBounds, onClick: () -> Unit) {
        clickTargets[key] = CliInteractionTarget(key, bounds, onClick)
    }

    fun unregisterClickTarget(key: Any) {
        clickTargets.remove(key)
    }

    fun registerScrollTarget(key: Any, bounds: CliBounds, onScroll: (Int) -> Unit) {
        scrollTargets[key] = CliScrollTarget(key, bounds, onScroll)
    }

    fun unregisterScrollTarget(key: Any) {
        scrollTargets.remove(key)
    }

    fun isHovered(bounds: CliBounds): Boolean {
        val pointer = pointerPosition ?: return false
        return bounds.contains(pointer.column, pointer.row)
    }

    fun handleMouseEvent(event: MouseEvent) {
        pointerPosition = CliPointerPosition(event.x, event.y)

        if (event.type == MouseEvent.Type.Press && event.button == MouseEvent.Button.Left) {
            clickTargets.values
                .lastOrNull { target -> target.bounds.contains(event.x, event.y) }
                ?.onClick
                ?.invoke()
        }

        if (event.type == MouseEvent.Type.Press && event.button == MouseEvent.Button.WheelUp) {
            scrollTargets.values
                .lastOrNull { target -> target.bounds.contains(event.x, event.y) }
                ?.onScroll
                ?.invoke(-1)
        }

        if (event.type == MouseEvent.Type.Press && event.button == MouseEvent.Button.WheelDown) {
            scrollTargets.values
                .lastOrNull { target -> target.bounds.contains(event.x, event.y) }
                ?.onScroll
                ?.invoke(1)
        }
    }
}

private class CliScrollTarget(
    val key: Any,
    val bounds: CliBounds,
    val onScroll: (Int) -> Unit,
)

internal val LocalInteractionManager = staticCompositionLocalOf<CliInteractionManager> {
    error("No CLI interaction manager provided.")
}
