package lokal.llama

data class ChatMessage(val role: String, val content: String)

sealed class ChatTemplate {
    abstract fun formatPrompt(
        messages: List<ChatMessage>,
        addGenerationPrompt: Boolean = true,
    ): String

    data object ChatML : ChatTemplate() {
        override fun formatPrompt(messages: List<ChatMessage>, addGenerationPrompt: Boolean): String {
            val sb = StringBuilder()
            for (msg in messages) {
                sb.append("<|im_start|>").append(msg.role).append('\n')
                sb.append(msg.content)
                sb.append("<|im_end|>").append('\n')
            }
            if (addGenerationPrompt) {
                sb.append("<|im_start|>assistant\n")
            }
            return sb.toString()
        }
    }

    data object Gemma : ChatTemplate() {
        override fun formatPrompt(messages: List<ChatMessage>, addGenerationPrompt: Boolean): String {
            val systemParts = mutableListOf<String>()
            val processed = mutableListOf<ChatMessage>()
            var systemMerged = false

            for (msg in messages) {
                if (msg.role == "system") {
                    systemParts.add(msg.content)
                } else {
                    if (!systemMerged && msg.role == "user" && systemParts.isNotEmpty()) {
                        val prefix = systemParts.joinToString("\n") { "[System: $it]" }
                        processed.add(ChatMessage("user", "$prefix\n\n${msg.content}"))
                        systemMerged = true
                    } else {
                        processed.add(msg)
                    }
                }
            }

            if (!systemMerged && systemParts.isNotEmpty()) {
                val prefix = systemParts.joinToString("\n") { "[System: $it]" }
                processed.add(0, ChatMessage("user", prefix))
            }

            val sb = StringBuilder()
            for (msg in processed) {
                sb.append("<start_of_turn>").append(msg.role).append('\n')
                sb.append(msg.content)
                sb.append("<end_of_turn>").append('\n')
            }
            if (addGenerationPrompt) {
                sb.append("<start_of_turn>model\n")
            }
            return sb.toString()
        }
    }

    data object Llama3 : ChatTemplate() {
        override fun formatPrompt(messages: List<ChatMessage>, addGenerationPrompt: Boolean): String {
            val sb = StringBuilder()
            for (msg in messages) {
                sb.append("<|start_header_id|>").append(msg.role).append("<|end_header_id|>")
                sb.append("\n\n").append(msg.content)
                sb.append("<|eot_id|>")
            }
            if (addGenerationPrompt) {
                sb.append("<|start_header_id|>assistant<|end_header_id|>\n\n")
            }
            return sb.toString()
        }
    }

    data class Generic(val rawTemplate: String?) : ChatTemplate() {
        override fun formatPrompt(messages: List<ChatMessage>, addGenerationPrompt: Boolean): String {
            return ChatML.formatPrompt(messages, addGenerationPrompt)
        }
    }

    companion object {
        fun detect(templateStr: String?): ChatTemplate {
            if (templateStr == null) return ChatML

            return when {
                "<|im_start|>" in templateStr -> ChatML
                "<start_of_turn>" in templateStr -> Gemma
                "<|start_header_id|>" in templateStr -> Llama3
                else -> ChatML
            }
        }
    }
}
