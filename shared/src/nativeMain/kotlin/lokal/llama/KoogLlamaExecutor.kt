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

class KoogLlamaExecutor(private val engine: LlamaEngine) : PromptExecutor() {
    override suspend fun execute(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Message.Assistant {
        val promptText = buildString {
            prompt.messages.forEach { msg ->
                val roleStr = if (msg.role == Message.Role.Assistant) "model" else "user"
                append("<start_of_turn>${roleStr}\n${msg.textContent()}<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }
        
        val builder = StringBuilder()
        engine.generate(promptText).collect { piece ->
            builder.append(piece)
        }
        
        return Message.Assistant(
            content = builder.toString(),
            metaInfo = ResponseMetaInfo.create(KoogClock.System)
        )
    }

    override fun executeStreaming(
        prompt: Prompt,
        model: LLModel,
        tools: List<ToolDescriptor>
    ): Flow<StreamFrame> = flow {
        val promptText = buildString {
            prompt.messages.forEach { msg ->
                val roleStr = if (msg.role == Message.Role.Assistant) "model" else "user"
                append("<start_of_turn>${roleStr}\n${msg.textContent()}<end_of_turn>\n")
            }
            append("<start_of_turn>model\n")
        }
        
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
}
