@file:Suppress("MagicNumber")

package com.ayagmar.pimobile.ui.chat

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.graphics.Color
import io.noties.prism4j.AbsVisitor
import io.noties.prism4j.Prism4j
import java.security.MessageDigest
import java.util.LinkedHashMap

// ── Syntax language mapping ─────────────────────────────────────────────────

/**
 * Languages supported by the bundled Prism4j grammar locator.
 * [prismGrammarName] is the grammar identifier that Prism4j expects, or null
 * when no highlighting is available.
 */
internal enum class SyntaxLanguage(
    val prismGrammarName: String?,
    val displayName: String,
) {
    KOTLIN("kotlin", "Kotlin"),
    JAVA("java", "Java"),
    JAVASCRIPT("javascript", "JavaScript"),
    JSON("json", "JSON"),
    MARKDOWN("markdown", "Markdown"),
    MARKUP("markup", "HTML"),
    MAKEFILE("makefile", "Makefile"),
    PYTHON("python", "Python"),
    GO("go", "Go"),
    SWIFT("swift", "Swift"),
    C("c", "C"),
    CPP("cpp", "C++"),
    CSHARP("csharp", "C#"),
    CSS("css", "CSS"),
    SQL("sql", "SQL"),
    YAML("yaml", "YAML"),
    PLAIN(null, "Text"),
}

// ── Highlight types ─────────────────────────────────────────────────────────

internal enum class HighlightKind {
    COMMENT,
    STRING,
    NUMBER,
    KEYWORD,
}

internal data class HighlightSpan(
    val start: Int,
    val end: Int,
    val kind: HighlightKind,
)

// ── Color mapping ───────────────────────────────────────────────────────────

internal data class SyntaxHighlightColors(
    val comment: Color,
    val string: Color,
    val number: Color,
    val keyword: Color,
)

internal fun highlightKindStyle(
    kind: HighlightKind,
    colors: SyntaxHighlightColors,
): SpanStyle {
    return when (kind) {
        HighlightKind.COMMENT -> SpanStyle(color = colors.comment)
        HighlightKind.STRING -> SpanStyle(color = colors.string)
        HighlightKind.NUMBER -> SpanStyle(color = colors.number)
        HighlightKind.KEYWORD -> SpanStyle(color = colors.keyword)
    }
}

// ── Prism4j highlighter singleton ───────────────────────────────────────────

internal object PrismHighlighter {
    private const val MAX_CACHE_ENTRIES = 256

    private val prism4j by lazy {
        Prism4j(DiffPrism4jGrammarLocator())
    }

    private val cache =
        object : LinkedHashMap<String, List<HighlightSpan>>(MAX_CACHE_ENTRIES, 0.75f, true) {
            override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, List<HighlightSpan>>?): Boolean {
                return size > MAX_CACHE_ENTRIES
            }
        }

    fun highlight(
        content: String,
        language: SyntaxLanguage,
    ): List<HighlightSpan> {
        val grammarName = language.prismGrammarName ?: return emptyList()
        val cacheKey = cacheKey(grammarName = grammarName, content = content)
        val cached = synchronized(cache) { cache[cacheKey] }

        return cached ?: computeUncached(content = content, grammarName = grammarName).also { computed ->
            synchronized(cache) {
                cache[cacheKey] = computed
            }
        }
    }

    private fun cacheKey(
        grammarName: String,
        content: String,
    ): String {
        val hash = sha256Hex(content)
        return "$grammarName:${content.length}:$hash"
    }

    private fun sha256Hex(content: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(content.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(separator = "") { byte -> "%02x".format(byte) }
    }

    private fun computeUncached(
        content: String,
        grammarName: String,
    ): List<HighlightSpan> {
        val grammar = prism4j.grammar(grammarName) ?: return emptyList()
        return runCatching {
            val visitor = PrismHighlightVisitor()
            visitor.visit(prism4j.tokenize(content, grammar))
            visitor.spans
        }.getOrDefault(emptyList())
    }
}

// ── Prism4j visitor ─────────────────────────────────────────────────────────

internal class PrismHighlightVisitor : AbsVisitor() {
    private val mutableSpans = mutableListOf<HighlightSpan>()
    private var cursor = 0

    val spans: List<HighlightSpan>
        get() = mutableSpans

    override fun visitText(text: Prism4j.Text) {
        cursor += text.literal().length
    }

    override fun visitSyntax(syntax: Prism4j.Syntax) {
        val start = cursor
        visit(syntax.children())
        val end = cursor

        if (end <= start) {
            return
        }

        tokenKind(
            tokenType = syntax.type(),
            alias = syntax.alias(),
        )?.let { kind ->
            mutableSpans +=
                HighlightSpan(
                    start = start,
                    end = end,
                    kind = kind,
                )
        }
    }
}

// ── Token classification ────────────────────────────────────────────────────

private val COMMENT_TOKEN_MARKERS = setOf("comment", "prolog", "doctype", "cdata")
private val STRING_TOKEN_MARKERS = setOf("string", "char", "attr-value", "url")
private val NUMBER_TOKEN_MARKERS = setOf("number", "boolean", "constant")
private val KEYWORD_TOKEN_MARKERS = setOf("keyword", "operator", "important", "atrule")

internal fun tokenKind(
    tokenType: String?,
    alias: String?,
): HighlightKind? {
    val tokenDescriptor = listOfNotNull(tokenType, alias).joinToString(separator = " ").lowercase()

    return when {
        tokenDescriptor.containsAny(COMMENT_TOKEN_MARKERS) -> HighlightKind.COMMENT
        tokenDescriptor.containsAny(STRING_TOKEN_MARKERS) -> HighlightKind.STRING
        tokenDescriptor.containsAny(NUMBER_TOKEN_MARKERS) -> HighlightKind.NUMBER
        tokenDescriptor.containsAny(KEYWORD_TOKEN_MARKERS) -> HighlightKind.KEYWORD
        else -> null
    }
}

private fun String.containsAny(markers: Set<String>): Boolean {
    return markers.any { marker -> contains(marker) }
}

// ── Language detection helpers ───────────────────────────────────────────────

internal val EXTENSION_LANGUAGE_MAP =
    mapOf(
        "kt" to SyntaxLanguage.KOTLIN,
        "kts" to SyntaxLanguage.KOTLIN,
        "java" to SyntaxLanguage.JAVA,
        "js" to SyntaxLanguage.JAVASCRIPT,
        "jsx" to SyntaxLanguage.JAVASCRIPT,
        "ts" to SyntaxLanguage.JAVASCRIPT,
        "tsx" to SyntaxLanguage.JAVASCRIPT,
        "json" to SyntaxLanguage.JSON,
        "jsonl" to SyntaxLanguage.JSON,
        "md" to SyntaxLanguage.MARKDOWN,
        "markdown" to SyntaxLanguage.MARKDOWN,
        "html" to SyntaxLanguage.MARKUP,
        "xml" to SyntaxLanguage.MARKUP,
        "svg" to SyntaxLanguage.MARKUP,
        "py" to SyntaxLanguage.PYTHON,
        "go" to SyntaxLanguage.GO,
        "swift" to SyntaxLanguage.SWIFT,
        "cs" to SyntaxLanguage.CSHARP,
        "cpp" to SyntaxLanguage.CPP,
        "cc" to SyntaxLanguage.CPP,
        "cxx" to SyntaxLanguage.CPP,
        "c" to SyntaxLanguage.C,
        "h" to SyntaxLanguage.C,
        "css" to SyntaxLanguage.CSS,
        "scss" to SyntaxLanguage.CSS,
        "sass" to SyntaxLanguage.CSS,
        "sql" to SyntaxLanguage.SQL,
        "yml" to SyntaxLanguage.YAML,
        "yaml" to SyntaxLanguage.YAML,
    )

/**
 * Maps the language tag from a code fence (e.g. ` ```kotlin `) to a [SyntaxLanguage].
 */
internal fun codeFenceLanguageToSyntax(tag: String?): SyntaxLanguage {
    if (tag.isNullOrBlank()) return SyntaxLanguage.PLAIN
    val lower = tag.lowercase().trim()
    return CODE_FENCE_TAG_MAP[lower] ?: SyntaxLanguage.PLAIN
}

/**
 * Detect syntax language from a file path (by extension).
 */
internal fun detectSyntaxLanguageFromPath(path: String): SyntaxLanguage {
    val lowerPath = path.lowercase()
    if (lowerPath.endsWith("makefile")) {
        return SyntaxLanguage.MAKEFILE
    }
    val extension = path.substringAfterLast('.', missingDelimiterValue = "").lowercase()
    return EXTENSION_LANGUAGE_MAP[extension] ?: SyntaxLanguage.PLAIN
}

private val CODE_FENCE_TAG_MAP =
    mapOf(
        "kotlin" to SyntaxLanguage.KOTLIN,
        "kt" to SyntaxLanguage.KOTLIN,
        "java" to SyntaxLanguage.JAVA,
        "javascript" to SyntaxLanguage.JAVASCRIPT,
        "js" to SyntaxLanguage.JAVASCRIPT,
        "typescript" to SyntaxLanguage.JAVASCRIPT,
        "ts" to SyntaxLanguage.JAVASCRIPT,
        "tsx" to SyntaxLanguage.JAVASCRIPT,
        "jsx" to SyntaxLanguage.JAVASCRIPT,
        "json" to SyntaxLanguage.JSON,
        "python" to SyntaxLanguage.PYTHON,
        "py" to SyntaxLanguage.PYTHON,
        "go" to SyntaxLanguage.GO,
        "golang" to SyntaxLanguage.GO,
        "swift" to SyntaxLanguage.SWIFT,
        "c" to SyntaxLanguage.C,
        "cpp" to SyntaxLanguage.CPP,
        "c++" to SyntaxLanguage.CPP,
        "csharp" to SyntaxLanguage.CSHARP,
        "cs" to SyntaxLanguage.CSHARP,
        "css" to SyntaxLanguage.CSS,
        "scss" to SyntaxLanguage.CSS,
        "html" to SyntaxLanguage.MARKUP,
        "xml" to SyntaxLanguage.MARKUP,
        "svg" to SyntaxLanguage.MARKUP,
        "sql" to SyntaxLanguage.SQL,
        "yaml" to SyntaxLanguage.YAML,
        "yml" to SyntaxLanguage.YAML,
        "markdown" to SyntaxLanguage.MARKDOWN,
        "md" to SyntaxLanguage.MARKDOWN,
        "makefile" to SyntaxLanguage.MAKEFILE,
        "make" to SyntaxLanguage.MAKEFILE,
        "bash" to SyntaxLanguage.PLAIN, // no bash grammar in Prism4j bundle
        "shell" to SyntaxLanguage.PLAIN,
        "sh" to SyntaxLanguage.PLAIN,
        "rust" to SyntaxLanguage.PLAIN,
        "rs" to SyntaxLanguage.PLAIN,
    )
