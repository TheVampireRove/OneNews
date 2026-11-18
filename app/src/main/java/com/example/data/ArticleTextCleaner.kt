package com.example.data

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element

/**
 * Strips raw code containers, syntax-highlighted blocks, XML/HTML teardown snippets,
 * raw JSON payloads, and dense programming syntax lines from extracted article bodies
 * so only clean editorial prose is passed to readers and paginators.
 */
object ArticleTextCleaner {

    private const val CODE_CONTAINER_SELECTOR =
        "pre, code, script, style, noscript, kbd, samp, " +
            ".wp-block-code, .wp-block-syntaxhighlighter-code, .highlight, .highlighter-rouge, " +
            ".codehilite, .prism-code, .code-block, .code-toolbar, .gist, .gist-file, .gist-data, .gist-meta, " +
            "[class*=syntaxhighlighter], [class*=hljs], [class*=prism-], " +
            "pre[class*=language-], div[class*=language-], code[class*=language-], [data-language]"

    private val fencedCodeRegex = Regex("```[\\s\\S]*?```|~~~[\\s\\S]*?~~~")
    private val xmlCommentOrPrologRegex = Regex("<!--[\\s\\S]*?-->|<\\?xml[\\s\\S]*?\\?>|<!\\[CDATA\\[[\\s\\S]*?\\]\\]>")

    private val pairedAndroidXmlBlockRegex = Regex(
        "<(resources|manifest|application|activity|service|receiver|provider|intent-filter|" +
            "uses-permission|uses-feature|string|string-array|plurals|bool|integer|dimen|color|" +
            "style|attr|declare-styleable|item|meta-data|vector|selector|shape|gradient)\\b[^>]*>" +
            "[\\s\\S]*?</\\1>",
        RegexOption.IGNORE_CASE,
    )

    private val pairedAttributedXmlTagRegex = Regex(
        "<([a-zA-Z][\\w:-]*)\\b(?:\\s+[^<>]*|:[a-zA-Z][\\w-]*)>[\\s\\S]*?</\\1>",
    )

    private val selfClosingXmlTagRegex = Regex(
        "<[a-zA-Z][\\w:-]*(?:\\s+[^<>]*?)?/\\s*>",
    )

    private val strayXmlResourceTagRegex = Regex(
        "</?(?:resources|manifest|application|activity|service|receiver|provider|intent-filter|" +
            "uses-permission|uses-feature|string|string-array|plurals|bool|integer|dimen|color|" +
            "style|attr|declare-styleable|item|meta-data|div|span|script|style|pre|code|iframe|svg)\\b[^<>]*>",
        RegexOption.IGNORE_CASE,
    )

    private val jsonObjectPayloadRegex = Regex(
        "\\{\\s*\"[A-Za-z0-9_.\\-@$ ]+\"\\s*:\\s*(?:[^\\{\\}]|\\{[^{}]*\\})*\\}",
    )

    private val jsonArrayPayloadRegex = Regex(
        "\\[\\s*\\{\\s*\"[A-Za-z0-9_.\\-@$ ]+\"\\s*:[\\s\\S]*?\\}\\s*\\]",
    )

    private val codeDeclRegex = Regex(
        "^(?:val|var|const|let)\\s+[A-Za-z_]\\w*(?:\\s*:\\s*[^=]+)?\\s*=.*" +
            "|^(?:fun|function|def|async\\s+function|suspend\\s+fun)\\s+[A-Za-z_]\\w*\\s*\\([^)]*\\).*" +
            "|^(?:public|private|protected|internal|override|static|final)\\s+" +
            "(?:static|final|fun|val|var|class|interface|object|enum|struct|void|int|long|float|double|boolean|String)\\b.*[{(;=].*" +
            "|^(?:class|interface|struct|enum)\\s+[A-Z]\\w*(?:<[^>]+>)?(?:\\s*[:{(]|\\s+(?:extends|implements)\\s+\\w+).*" +
            "|^(?:if|else\\s+if|for|while|switch|catch)\\s*\\([^()]+\\)\\s*\\{?$" +
            "|^(?:import|package|#include|#define|#pragma)\\s+[A-Za-z_][\\w.*:/\"-]+;?$",
    )

    private val codeLineMarkersRegex = Regex(
        "^(?://|/\\*|\\*/|#|@Override\\b|@Composable\\b|@Suppress\\b|xmlns:|android:|app:)" +
            "|console\\.log\\(|System\\.out\\.|document\\.querySelector|window\\.addEventListener|findViewById\\(|setContentView\\(",
    )

    private val returnCodeLineRegex = Regex("^return\\s+[^.?!]*[;)}]$")
    private val camelOrSnakeCallRegex = Regex("\\b[a-z][a-zA-Z0-9_]*\\([^)]{0,60}\\)")
    private val codeTokenRegex = Regex("(?:=>|->|::|===|!==|==|!=|&&|\\|\\||\\+=|-=|\\{\\s*\\}|\\}[;)]?$|;$)")

    /**
     * Removes code containers (`<pre>`, `<code>`, `<script>`, `<style>`, syntax-highlighter blocks)
     * directly from [doc] before paragraph DOM extraction.
     */
    fun stripCodeContainers(doc: Document) {
        stripCodeContainers(doc as Element)
    }

    fun stripCodeContainers(root: Element) {
        root.select(CODE_CONTAINER_SELECTOR).remove()
    }

    /**
     * Extracts text from a DOM block element while preserving `<br>` line boundaries
     * so embedded XML/code lines can be detected and filtered line-by-line cleanly.
     */
    fun extractElementTextPreservingBreaks(el: Element): String {
        val rawHtml = el.html()
        if (!rawHtml.contains("<br", ignoreCase = true) && !rawHtml.contains('\n')) {
            return el.text()
        }
        val marker = " __ONENEWS_BR__ "
        val replaced = rawHtml.replace(Regex("(?i)<br\\s*/?>"), marker)
        return Jsoup.parseBodyFragment(replaced)
            .text()
            .replace("__ONENEWS_BR__", "\n")
    }

    /**
     * Removes inline XML/HTML resource tags, raw JSON payloads, and dense code syntax lines
     * while preserving normal editorial punctuation and quoted speech.
     */
    fun cleanParagraphText(rawText: String): String? {
        if (rawText.isBlank()) return null

        var text = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace('\u00A0', ' ')

        // Remove fenced code blocks, XML comments/prologs, paired XML/HTML teardown tags, and JSON payloads
        text = text.replace(fencedCodeRegex, "\n")
        text = text.replace(xmlCommentOrPrologRegex, " ")

        var previous: String
        do {
            previous = text
            text = text
                .replace(pairedAndroidXmlBlockRegex, "\n")
                .replace(pairedAttributedXmlTagRegex, " ")
        } while (text != previous)

        text = text
            .replace(selfClosingXmlTagRegex, " ")
            .replace(strayXmlResourceTagRegex, " ")
            .replace(jsonArrayPayloadRegex, "\n")
            .replace(jsonObjectPayloadRegex, "\n")

        val cleanLines = text.split('\n')
            .map { line -> line.replace(Regex("[ \\t]+"), " ").trim() }
            .filter { line ->
                line.isNotBlank() &&
                    !isOrphanedPunctuation(line) &&
                    !isCodeSyntaxLine(line)
            }

        if (cleanLines.isEmpty()) return null

        val joined = cleanLines
            .joinToString(" ")
            .replace(Regex("[ \\t\\n]+"), " ")
            .replace(Regex("\\s+([.,;!?])"), "$1")
            .trim()

        return joined.takeIf { it.isNotBlank() && !isOrphanedPunctuation(it) }
    }

    /**
     * Cleans a list of extracted paragraphs, removing blank entries, code remnants,
     * trailing feed ellipses, and collapsing any blank double-newlines.
     */
    fun sanitizeParagraphs(paragraphs: List<String>): List<String> {
        if (paragraphs.isEmpty()) return emptyList()
        val cleaned = paragraphs.mapNotNull { para ->
            cleanParagraphText(para)
                ?.replace(Regex("\\n{2,}"), "\n")
                ?.trim()
                ?.takeIf { it.isNotBlank() }
        }
        if (cleaned.isEmpty()) return emptyList()
        return RssFeedParser.stripTrailingFeedEllipsis(cleaned)
            .filter { it.isNotBlank() }
    }

    private fun isOrphanedPunctuation(line: String): Boolean {
        return line.all { ch -> !ch.isLetterOrDigit() }
    }

    internal fun isCodeSyntaxLine(rawLine: String): Boolean {
        val line = rawLine.trim()
        if (line.isBlank()) return true

        if (codeDeclRegex.containsMatchIn(line) ||
            codeLineMarkersRegex.containsMatchIn(line) ||
            returnCodeLineRegex.containsMatchIn(line)
        ) {
            return true
        }

        val endsWithCodeBlockBrace = (line.endsWith("{") || line.endsWith("};") || line.endsWith("});") || line.endsWith("]);")) &&
            (line.contains('=') || line.contains('(') || line.contains(':'))
        if (endsWithCodeBlockBrace) {
            return true
        }

        // Preserve normal prose sentences (capitalized start, multiple normal words, natural ending punctuation)
        val looksLikeProseSentence = line.firstOrNull()?.isUpperCase() == true &&
            (line.endsWith('.') || line.endsWith('!') || line.endsWith('?') || line.endsWith('”') || line.endsWith('"')) &&
            !line.contains('{') &&
            !line.contains('}') &&
            !line.endsWith(';') &&
            line.split(' ').count { token -> token.length >= 2 && token.all { c -> c.isLetter() || c == '\'' || c == '-' || c == ',' || c == '.' } } >= 4
        if (looksLikeProseSentence) {
            return false
        }

        val codeMarkerMatches = codeTokenRegex.findAll(line).count()
        val hasFunctionCall = camelOrSnakeCallRegex.containsMatchIn(line)
        val assignmentWithBrackets = line.contains('=') && (line.contains(';') || line.contains('{') || line.contains('(') || line.contains('['))

        if (assignmentWithBrackets && (codeMarkerMatches >= 1 || hasFunctionCall)) {
            return true
        }
        if (codeMarkerMatches >= 2 && (hasFunctionCall || line.contains('{') || line.contains('}') || line.contains('=') || line.endsWith(';'))) {
            return true
        }

        return false
    }
}
