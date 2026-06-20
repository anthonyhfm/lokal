package lokal

import ai.koog.agents.core.agent.AIAgent
import ai.koog.prompt.executor.model.PromptExecutor
import lokal.llama.KoogLlamaExecutor
import lokal.llama.LlamaEngine
import ai.koog.prompt.llm.LLModel
import ai.koog.prompt.llm.LLMProvider

object LocalProvider : LLMProvider("local", "Local Llama Engine")

fun createKoogAgent(modelPath: String): AIAgent<String, String> {
    val engine = LlamaEngine.create(modelPath)
    val executor = KoogLlamaExecutor(engine)
    
    return AIAgent.builder()
        .promptExecutor(executor)
        .llmModel(LLModel(provider = LocalProvider, id = "gemma-4-12b"))
        .build()
}
