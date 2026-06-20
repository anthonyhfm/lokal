package lokal.terminal

import com.jakewharton.mosaic.ui.Color
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class StatusStripController {
    private val _topStripEffect = MutableStateFlow<StripEffect>(StripEffect.Solid(Color(120, 120, 120)))
    val topStripEffect: StateFlow<StripEffect> = _topStripEffect.asStateFlow()

    private val _bottomStripEffect = MutableStateFlow<StripEffect>(StripEffect.Solid(Color(120, 120, 120)))
    val bottomStripEffect: StateFlow<StripEffect> = _bottomStripEffect.asStateFlow()

    fun setTopEffect(effect: StripEffect) {
        _topStripEffect.value = effect
    }

    fun setBottomEffect(effect: StripEffect) {
        _bottomStripEffect.value = effect
    }
}

sealed interface StripEffect {
    data class Solid(val color: Color) : StripEffect
    data class Custom(val render: (column: Int, width: Int) -> Color) : StripEffect
}
