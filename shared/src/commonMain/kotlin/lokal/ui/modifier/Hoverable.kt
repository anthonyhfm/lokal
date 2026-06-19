package lokal.ui.modifier

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import com.jakewharton.mosaic.layout.LayoutModifier
import com.jakewharton.mosaic.layout.Measurable
import com.jakewharton.mosaic.layout.MeasureResult
import com.jakewharton.mosaic.layout.MeasureScope
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.modifier.composed
import com.jakewharton.mosaic.ui.unit.Constraints
import lokal.terminal.CliBounds
import lokal.terminal.LocalInteractionManager

fun Modifier.hoverable(
    onHoverChange: (Boolean) -> Unit,
): Modifier = composed {
    val manager = LocalInteractionManager.current
    val currentOnHoverChange = rememberUpdatedState(onHoverChange)
    var bounds by remember { mutableStateOf<CliBounds?>(null) }
    val hovered = bounds?.let(manager::isHovered) ?: false

    LaunchedEffect(hovered) {
        currentOnHoverChange.value(hovered)
    }

    this.then(
        HoverableModifier(
            bounds = { bounds },
            updateBounds = { bounds = it },
        ),
    )
}

private class HoverableModifier(
    private val bounds: () -> CliBounds?,
    private val updateBounds: (CliBounds) -> Unit,
) : LayoutModifier {
    override fun MeasureScope.measure(
        measurable: Measurable,
        constraints: Constraints,
    ): MeasureResult {
        val placeable = measurable.measure(constraints)

        return layout(placeable.width, placeable.height) {
            val currentBounds = CliBounds(
                column = x,
                row = y,
                width = placeable.width,
                height = placeable.height,
            )
            if (bounds() != currentBounds) {
                updateBounds(currentBounds)
            }
            placeable.place(0, 0)
        }
    }

    override fun toString(): String = "Hoverable"
}
