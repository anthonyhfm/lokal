package lokal.llama

import ai.koog.agents.core.tools.ToolDescriptor
import ai.koog.prompt.Prompt
import ai.koog.prompt.dsl.ModerationResult
import ai.koog.prompt.executor.model.PromptExecutorAPI
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.message.Message
import ai.koog.prompt.message.ResponseMetaInfo
import ai.koog.prompt.streaming.StreamFrame
import ai.koog.utils.time.KoogClock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

import ai.koog.prompt.executor.model.PromptExecutor

class KoogLlamaExecutor(
    private val engine: LlamaEngine,
    private val chatTemplate: ChatTemplate,
) : PromptExecutor() {

    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant {
        val promptText = formatPrompt(prompt)
        
        val builder = StringBuilder()
        engine.generate(promptText).collect { piece ->
            builder.append(piece)
        }
        
        return Message.Assistant(
            content = cleanResponse(builder.toString()),
            metaInfo = ResponseMetaInfo.create(KoogClock.System)
        )
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        val promptText = formatPrompt(prompt)
        
        engine.generate(promptText).collect { piece ->
            emit(StreamFrame.TextDelta(piece))
        }
        
        emit(StreamFrame.End(metaInfo = ResponseMetaInfo.create(KoogClock.System)))
    }

    override suspend fun moderate(prompt: Prompt, model: LLModel): ModerationResult {
        return ModerationResult(isHarmful = false, categories = emptyMap())
    }

    override suspend fun models(): List<LLModel> = emptyList()

    override fun close() {
        engine.close()
    }

    // ── Internal helpers ────────────────────────────────────────────────

    /**
     * Converts a Koog [Prompt] into the raw prompt string expected by the
     * model, using the appropriate [ChatTemplate].
     */
    private fun formatPrompt(prompt: Prompt): String {
        val chatMessages = prompt.messages.map { msg ->
            val role = when (msg.role) {
                Message.Role.System    -> "system"
                Message.Role.Assistant -> "assistant"
                else                   -> "user"
            }
            ChatMessage(role, msg.textContent())
        }
        return chatTemplate.formatPrompt(chatMessages)
    }

    /**
     * Strips chain-of-thought `<think>` tags and cleans up common
     * model output artifacts (trailing special tokens, whitespace, etc.).
     */
    private fun cleanResponse(raw: String): String {
        return raw
            // Strip complete <think>...</think> blocks (Qwen 3.5 CoT)
            .replace(Regex("<think>[\\s\\S]*?</think>"), "")
            // Strip orphaned/unclosed think tags
            .replace(Regex("^\\s*</?think>\\s*"), "")
            .replace(Regex("\\s*</?think>\\s*$"), "")
            // Strip leaked special tokens that shouldn't appear in output
            .replace("<|im_end|>", "")
            .replace("<|im_start|>", "")
            .replace("<end_of_turn>", "")
            .replace("<start_of_turn>", "")
            .replace("<|eot_id|>", "")
            .trim()
    }
}
