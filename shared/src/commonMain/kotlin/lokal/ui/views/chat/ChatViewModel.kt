package lokal.ui.views.chat

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import ai.koog.agents.core.agent.AIAgent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class ChatViewModel(private val agent: AIAgent<String, String>) {
    var messages by mutableStateOf(listOf<Pair<String, String>>())
        private set

    var isGenerating by mutableStateOf(false)
        private set

    var promptText by mutableStateOf("")

    private val scope = CoroutineScope(Dispatchers.Default + SupervisorJob())

    fun onPromptChange(newText: String) {
        if (!isGenerating) {
            promptText = newText
        }
    }

    fun onEnter() {
        if (promptText.isNotBlank() && !isGenerating) {
            val userMsg = promptText
            promptText = ""
            messages = messages + ("You" to userMsg)
            isGenerating = true

            scope.launch {
                try {
                    messages = messages + ("Agent" to "...") // placeholder
                    val response = agent.run(userMsg)
                    messages = messages.dropLast(1) + ("Agent" to response)
                } catch (e: Exception) {
                    messages = messages.dropLast(1) + ("System" to "Error: ${e.message}")
                } finally {
                    isGenerating = false
                }
            }
        }
    }
}
