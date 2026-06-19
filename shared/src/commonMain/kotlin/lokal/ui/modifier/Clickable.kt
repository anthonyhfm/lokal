package lokal.ui.modifier

import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import com.jakewharton.mosaic.layout.LayoutModifier
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.modifier.composed
import com.jakewharton.mosaic.ui.unit.Constraints
import lokal.terminal.CliBounds
import lokal.terminal.CliInteractionManager
import lokal.terminal.LocalInteractionManager

fun Modifier.clickable(
    onClick: () -> Unit,
): Modifier = composed {
    val manager = LocalInteractionManager.current
    val key = remember { Any() }
    val currentOnClick = rememberUpdatedState(onClick)

    DisposableEffect(manager, key) {
        onDispose {
            manager.unregisterClickTarget(key)
        }
    }

    this.then(ClickableModifier(manager, key, currentOnClick))
}

private class ClickableModifier(
    private val manager: CliInteractionManager,
    private val key: Any,
    private val onClick: State<() -> Unit>,
) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)

        return layout(placeable.width, placeable.height) {
            manager.registerClickTarget(
                key = key,
                bounds = CliBounds(
                    column = x,
                    row = y,
                    width = placeable.width,
                    height = placeable.height,
                ),
                onClick = { onClick.value() },
            )
            placeable.place(0, 0)
        }
    }

    override fun toString(): String = "Clickable"
}
