package lokal

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import lokal.llama.ChatTemplate
import lokal.llama.KoogLlamaExecutor
import lokal.llama.LlamaEngine
import lokal.llama.SamplingStrategy
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider

object LocalProvider : LLMProvider("local", "Local Llama Engine")

/**
 * System prompt that constrains local models to produce well-structured,
 * non-hallucinated coding responses.
 */
private val CODING_AGENT_SYSTEM_PROMPT = """
You are a precise, helpful coding assistant running locally. Follow these rules strictly:

1. RESPONSE FORMAT: Provide direct, clear answers. Do not include internal reasoning, thinking tags, or metacommentary.
2. CODE: When writing code, use proper markdown code blocks with language identifiers (e.g. ```kotlin).
3. ACCURACY: If you are unsure about something, say so explicitly. Never fabricate APIs, libraries, functions, or facts.
4. CONCISENESS: Keep responses focused and relevant. Avoid unnecessary preamble or filler.
5. COMPLETENESS: Always finish your responses properly. Never stop mid-sentence or mid-code-block.
6. LANGUAGE: Respond in the same language the user writes in.
""".trimIndent()

fun createKoogAgent(modelPath: String): AIAgent<String, String> {
    val engine = LlamaEngine.create(
        modelPath,
        strategy = SamplingStrategy.Temperature(temp = 0.2f, seed = 42u),
    )

    // Auto-detect the correct chat template from GGUF metadata
    val chatTemplate = ChatTemplate.detect(engine.chatTemplateString)
    val executor = KoogLlamaExecutor(engine, chatTemplate)

    return AIAgent.builder()
        .promptExecutor(executor)
        .llmModel(LLModel(provider = LocalProvider, id = "local"))
        .systemPrompt(CODING_AGENT_SYSTEM_PROMPT)
        .build()
}
