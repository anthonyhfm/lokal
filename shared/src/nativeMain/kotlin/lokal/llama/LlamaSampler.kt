package lokal.llama

import cnames.structs.llama_context
import dev.lokal.native.llama.llama_sampler
import dev.lokal.native.llama.llama_sampler_chain_add
import dev.lokal.native.llama.llama_sampler_chain_default_params
import dev.lokal.native.llama.llama_sampler_chain_init
import dev.lokal.native.llama.llama_sampler_free
import dev.lokal.native.llama.llama_sampler_init_dist
import dev.lokal.native.llama.llama_sampler_init_greedy
import dev.lokal.native.llama.llama_sampler_init_min_p
import dev.lokal.native.llama.llama_sampler_init_temp
import dev.lokal.native.llama.llama_sampler_sample
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi

/** Sampling strategy for token selection. */
sealed class SamplingStrategy {
    /** Always picks the highest-probability token. Deterministic. */
    data object Greedy : SamplingStrategy()

    /**
     * Temperature sampling — higher [temp] = more creative/random.
     * Uses [seed] for reproducibility.
     */
    data class Temperature(
        val temp: Float = 0.2f,
        val minP: Float = 0.05f,
        val seed: UInt = 42u,
    ) : SamplingStrategy()
}

/**
 * Wraps a llama_sampler chain. Must be closed to free native memory.
 *
 * Note: individual samplers added to a chain are owned by the chain —
 * do NOT free them manually.
 */
@OptIn(ExperimentalForeignApi::class)
class LlamaSampler(strategy: SamplingStrategy = SamplingStrategy.Temperature()) : AutoCloseable {

    internal val ptr: CPointer<llama_sampler>

    init {
        val chainParams = llama_sampler_chain_default_params()
        val chain = llama_sampler_chain_init(chainParams)
            ?: error("Failed to initialize sampler chain")

        when (strategy) {
            is SamplingStrategy.Greedy -> {
                llama_sampler_chain_add(chain, llama_sampler_init_greedy())
            }
            is SamplingStrategy.Temperature -> {
                llama_sampler_chain_add(chain, llama_sampler_init_min_p(strategy.minP, 1uL))
                llama_sampler_chain_add(chain, llama_sampler_init_temp(strategy.temp))
                llama_sampler_chain_add(chain, llama_sampler_init_dist(strategy.seed))
            }
        }

        ptr = chain
    }

    /**
     * Samples the next token from the context's logits.
     *
     * @param ctx    The active inference context.
     * @param index  Index within the last decoded batch to sample from. -1 = last token.
     */
    fun sample(ctx: CPointer<llama_context>, index: Int = -1): Int =
        llama_sampler_sample(ptr, ctx, index)

    /** Frees the sampler chain and all samplers added to it. */
    override fun close() {
        llama_sampler_free(ptr)
    }
}
