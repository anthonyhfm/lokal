package lokal.llama

import cnames.structs.llama_context
import dev.lokal.native.llama.ggml_backend_load_all
import dev.lokal.native.llama.llama_batch_get_one
import dev.lokal.native.llama.llama_context_default_params
import dev.lokal.native.llama.llama_decode
import dev.lokal.native.llama.llama_free
import dev.lokal.native.llama.llama_init_from_model
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readValue
import kotlinx.cinterop.set
import kotlinx.cinterop.useContents
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow

/** Configuration for the llama inference context. */
data class LlamaContextConfig(
    /** KV-cache context size in tokens. */
    val nCtx: UInt = 4096u,
    /** Max tokens to process per decode call. */
    val nBatch: UInt = 512u,
    /** Number of CPU threads for inference. */
    val nThreads: Int = 4,
)

/**
 * Main entry point for llama.cpp inference.
 *
 * Owns a [LlamaModel], a native context, a [LlamaTokenizer], and a [LlamaSampler].
 * Close this engine when done — it frees all native resources in the correct order.
 *
 * Usage:
 * ```
 * val engine = LlamaEngine.create("models/qwen2.5-1.5b.Q4_K_M.gguf")
 * engine.generate("Hello, who are you?").collect { piece -> print(piece) }
 * engine.close()
 * ```
 */
@OptIn(ExperimentalForeignApi::class)
class LlamaEngine private constructor(
    private val model: LlamaModel,
    private val ctx: CPointer<llama_context>,
    val tokenizer: LlamaTokenizer,
    private val sampler: LlamaSampler,
) : AutoCloseable {

    val chatTemplateString: String? = model.chatTemplate()

    /**
     * Streams generated text from [prompt], emitting one decoded string piece per token.
     *
     * Collect this Flow to receive tokens as they are produced. Collection is
     * synchronous — suspend on a background dispatcher for non-blocking UI.
     *
     * @param prompt        Input text to continue from.
     * @param maxNewTokens  Max tokens to generate before stopping.
     */
    fun generate(prompt: String, maxNewTokens: Int = 512): Flow<String> = flow {
        val tokens = tokenizer.tokenize(prompt)

        // ── Decode prompt ──────────────────────────────────────────────────────
        memScoped {
            val arr = allocArray<IntVar>(tokens.size)
            tokens.forEachIndexed { i, t -> arr[i] = t }
            val batch = llama_batch_get_one(arr, tokens.size)
            check(llama_decode(ctx, batch) == 0) { "Failed to decode prompt" }
        }

        // ── Generation loop ────────────────────────────────────────────────────
        var generated = 0
        while (generated < maxNewTokens) {
            val newToken = sampler.sample(ctx)

            if (tokenizer.isEndOfGeneration(newToken)) break

            emit(tokenizer.tokenToString(newToken))

            memScoped {
                val arr = allocArray<IntVar>(1)
                arr[0] = newToken
                val batch = llama_batch_get_one(arr, 1)
                check(llama_decode(ctx, batch) == 0) { "Failed to decode token" }
            }
            generated++
        }
    }

    /** Frees the sampler, context, and model in the correct order. */
    override fun close() {
        sampler.close()
        llama_free(ctx)
        model.close()
    }

    companion object {
        /**
         * Loads a GGUF model from [modelPath] and returns a ready-to-use engine.
         *
         * This is the primary factory — prefer it over manual construction.
         *
         * @throws IllegalStateException if the model or context cannot be initialized.
         */
        fun create(
            modelPath: String,
            modelConfig: LlamaModelConfig = LlamaModelConfig(),
            contextConfig: LlamaContextConfig = LlamaContextConfig(),
            strategy: SamplingStrategy = SamplingStrategy.Temperature(),
        ): LlamaEngine {
            // Disable llama.cpp logs from printing to stderr and breaking Mosaic
            dev.lokal.native.llama.llama_log_set(kotlinx.cinterop.staticCFunction { _, _, _ -> }, null)

            // Load all GGML backends (CPU, Metal on macOS, CUDA on Linux, etc.)
            ggml_backend_load_all()

            val model = LlamaModel.load(modelPath, modelConfig)
                ?: error("Failed to load model: $modelPath")

            val ctxParams = llama_context_default_params().useContents {
                n_ctx = contextConfig.nCtx
                n_batch = contextConfig.nBatch
                n_threads = contextConfig.nThreads
                readValue()
            }

            val ctx = llama_init_from_model(model.ptr, ctxParams)
                ?: run { model.close(); error("Failed to create llama context") }

            val tokenizer = LlamaTokenizer(model.vocab)
            val sampler = LlamaSampler(strategy)

            return LlamaEngine(model, ctx, tokenizer, sampler)
        }
    }
}
