package lokal.llama

import cnames.structs.llama_model
import cnames.structs.llama_vocab
import dev.lokal.native.llama.llama_model_chat_template
import dev.lokal.native.llama.llama_model_default_params
import dev.lokal.native.llama.llama_model_free
import dev.lokal.native.llama.llama_model_get_vocab
import dev.lokal.native.llama.llama_model_load_from_file
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.readValue
import kotlinx.cinterop.toKStringFromUtf8
import kotlinx.cinterop.useContents

/**
 * Configuration for loading a llama.cpp model from a GGUF file.
 */
data class LlamaModelConfig(
    /** Layers to offload to GPU. 99 = all layers. 0 = CPU only. */
    val nGpuLayers: Int = 99,
    /** Memory-map the model file instead of loading into RAM. */
    val useMmap: Boolean = true,
)

/**
 * A loaded llama.cpp model. Holds the native pointer and vocab.
 * Must be closed when no longer needed to free native memory.
 */
@OptIn(ExperimentalForeignApi::class)
class LlamaModel private constructor(
    internal val ptr: CPointer<llama_model>,
    internal val vocab: CPointer<llama_vocab>,
) : AutoCloseable {

    companion object {
        /**
         * Loads a GGUF model from the given file [path].
         * Returns `null` if the model could not be loaded.
         */
        @OptIn(ExperimentalForeignApi::class)
        fun load(path: String, config: LlamaModelConfig = LlamaModelConfig()): LlamaModel? {
            val params = llama_model_default_params().useContents {
                n_gpu_layers = config.nGpuLayers
                use_mmap = config.useMmap
                readValue()
            }

            val modelPtr = llama_model_load_from_file(path, params) ?: return null
            val vocabPtr = llama_model_get_vocab(modelPtr) ?: run {
                llama_model_free(modelPtr)
                return null
            }

            return LlamaModel(modelPtr, vocabPtr)
        }
    }

    fun chatTemplate(): String? = llama_model_chat_template(ptr, null)?.toKStringFromUtf8()

    override fun close() {
        llama_model_free(ptr)
    }
}
