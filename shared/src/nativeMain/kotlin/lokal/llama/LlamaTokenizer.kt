package lokal.llama

import cnames.structs.llama_vocab
import dev.lokal.native.llama.llama_token_to_piece
import dev.lokal.native.llama.llama_tokenize
import dev.lokal.native.llama.llama_vocab_is_eog
import kotlinx.cinterop.ByteVar
import kotlinx.cinterop.CPointer
import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.IntVar
import kotlinx.cinterop.allocArray
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.readBytes

/**
 * Handles tokenization and detokenization using the model's vocabulary.
 */
@OptIn(ExperimentalForeignApi::class)
class LlamaTokenizer(private val vocab: CPointer<llama_vocab>) {

    /**
     * Converts [text] into a list of token IDs.
     *
     * @param addSpecial    Prepend BOS / append EOS tokens where appropriate.
     * @param parseSpecial  Recognize special token strings like `<|im_start|>`.
     * @param maxTokens     Upper bound for the output buffer.
     */
    fun tokenize(
        text: String,
        addSpecial: Boolean = true,
        parseSpecial: Boolean = true,
        maxTokens: Int = 4096,
    ): List<Int> = memScoped {
        val buffer = allocArray<IntVar>(maxTokens)
        val count = llama_tokenize(
            vocab = vocab,
            text = text,
            text_len = text.length,
            tokens = buffer,
            n_tokens_max = maxTokens,
            add_special = addSpecial,
            parse_special = parseSpecial,
        )
        check(count >= 0) {
            "Token buffer too small: need ${-count} slots, max is $maxTokens"
        }
        (0 until count).map { buffer[it] }
    }

    /**
     * Converts a single [token] ID back into its string piece.
     * Note: llama does NOT null-terminate the output, so we use [readBytes].
     */
    fun tokenToString(token: Int, renderSpecial: Boolean = false): String = memScoped {
        val bufSize = 32
        val buf = allocArray<ByteVar>(bufSize)
        val written = llama_token_to_piece(
            vocab = vocab,
            token = token,
            buf = buf,
            length = bufSize,
            lstrip = 0,
            special = renderSpecial,
        )
        if (written <= 0) "" else buf.readBytes(written).decodeToString()
    }

    /** Returns `true` when [token] signals end-of-generation (EOS, EOT, etc.). */
    fun isEndOfGeneration(token: Int): Boolean =
        llama_vocab_is_eog(vocab, token)
}
