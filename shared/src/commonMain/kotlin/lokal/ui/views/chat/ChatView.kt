package lokal.ui.views.chat

import androidx.compose.runtime.Composable
import com.jakewharton.mosaic.modifier.Modifier
import com.jakewharton.mosaic.layout.fillMaxWidth
import com.jakewharton.mosaic.ui.Column
import lokal.ui.components.ChatScrollArea
import lokal.ui.components.PromptEntryField

@Composable
fun ChatView(
    viewModel: ChatViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ChatScrollArea(
            messages = viewModel.messages,
            modifier = Modifier.weight(1f).fillMaxWidth()
        )

        PromptEntryField(
            value = viewModel.promptText,
            onValueChange = {
                viewModel.onPromptChange(it)
            },
            onEnter = {
                viewModel.onEnter()
            }
        )
    }
}
