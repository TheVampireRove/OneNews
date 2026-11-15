package com.example.ui.reader

import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

internal data class MeasuredPaginatedPage(
    val pageIndex: Int,
    val isCoverPage: Boolean,
    val heroImageHeightDp: Dp,
    val paragraphs: List<String>,
    val isLastPage: Boolean,
)

/**
 * Adjusts a line-end offset from TextLayoutResult to a clean word boundary so
 * split paragraphs never slice a word in half across pages.
 */
internal fun findCleanWordSplitOffset(text: String, rawEndOffset: Int): Int {
    val clamped = rawEndOffset.coerceIn(0, text.length)
    if (clamped == 0 || clamped == text.length) return clamped
    if (text[clamped].isWhitespace() || text[clamped - 1].isWhitespace()) {
        return clamped
    }
    for (idx in clamped - 1 downTo 1) {
        if (text[idx].isWhitespace()) {
            return idx
        }
    }
    return clamped
}

/**
 * Dynamic layout-aware pagination engine powered by Compose TextMeasurer.
 * Measures exact paragraph line bounds against the active viewport width and height,
 * iteratively slicing the complete article across as many pages as required with zero
 * artificial page caps, character limits, or ellipsis truncation.
 */
internal fun paginateArticleWithTextMeasurer(
    paragraphs: List<String>,
    pageWidthPx: Int,
    pageHeightPx: Int,
    coverHeaderHeightPx: Int,
    paraGapPx: Int,
    lastPageFooterReservePx: Int,
    textMeasurer: TextMeasurer,
    bodyStyle: TextStyle,
    heroImageHeightDp: Dp,
    allowBodyOnCoverPage: Boolean = true,
    coverHeaderIncludesTrailingGap: Boolean = true,
): List<MeasuredPaginatedPage> {
    val textConstraints = Constraints(maxWidth = pageWidthPx.coerceAtLeast(120))
    val safePageHeightPx = pageHeightPx.coerceAtLeast(160)

    val pages = mutableListOf<MeasuredPaginatedPage>()
    val queue = ArrayDeque<String>(paragraphs.filter { it.isNotBlank() })

    var isFirstPage = true
    var usedHeightPx = coverHeaderHeightPx
    var currentParas = mutableListOf<String>()

    fun flushPage(isLast: Boolean = false) {
        pages.add(
            MeasuredPaginatedPage(
                pageIndex = pages.size,
                isCoverPage = isFirstPage,
                heroImageHeightDp = if (isFirstPage) heroImageHeightDp else 0.dp,
                paragraphs = currentParas.toList(),
                isLastPage = isLast,
            )
        )
        isFirstPage = false
        usedHeightPx = 0
        currentParas = mutableListOf()
    }

    if (!allowBodyOnCoverPage) {
        if (queue.isEmpty()) {
            flushPage(isLast = true)
            return pages
        }
        flushPage(isLast = false)
    }

    if (queue.isEmpty()) {
        flushPage(isLast = true)
        return pages
    }

    while (queue.isNotEmpty()) {
        val para = queue.removeFirst()
        val layout = textMeasurer.measure(para, bodyStyle, constraints = textConstraints)
        val paraHeightPx = layout.size.height

        val gapBefore = when {
            currentParas.isNotEmpty() -> paraGapPx
            isFirstPage && !coverHeaderIncludesTrailingGap && coverHeaderHeightPx > 0 -> paraGapPx
            else -> 0
        }
        val neededHeight = gapBefore + paraHeightPx
        val isFinalRemaining = queue.isEmpty()

        if (usedHeightPx + neededHeight <= safePageHeightPx) {
            val fitsWithFooter = !isFinalRemaining ||
                (usedHeightPx + neededHeight + lastPageFooterReservePx <= safePageHeightPx)

            if (fitsWithFooter) {
                currentParas.add(para)
                usedHeightPx += neededHeight
            } else if (currentParas.isNotEmpty() || isFirstPage) {
                // Move final paragraph onto the next page so it sits cleanly above the End of Article footer
                flushPage(isLast = false)
                queue.addFirst(para)
            } else {
                // Single tall paragraph on a body page leaves insufficient room for footer:
                // split off its closing lines onto a dedicated closing page with the End of Article footer
                val budgetWithFooter = (safePageHeightPx - lastPageFooterReservePx).coerceAtLeast(safePageHeightPx / 3)
                var splitLine = -1
                for (lineIdx in 0 until layout.lineCount) {
                    if (layout.getLineBottom(lineIdx) <= budgetWithFooter) {
                        splitLine = lineIdx
                    } else {
                        break
                    }
                }
                if (splitLine >= 0 && splitLine < layout.lineCount - 1) {
                    val rawEnd = layout.getLineEnd(splitLine, visibleEnd = true).coerceIn(1, para.length)
                    val cleanEnd = findCleanWordSplitOffset(para, rawEnd).let { if (it <= 0) rawEnd else it }
                    val headPart = para.substring(0, cleanEnd).trimEnd()
                    val tailPart = para.substring(cleanEnd).trimStart()
                    if (headPart.isNotEmpty() && tailPart.isNotEmpty()) {
                        currentParas.add(headPart)
                        flushPage(isLast = false)
                        queue.addFirst(tailPart)
                    } else {
                        currentParas.add(para)
                        flushPage(isLast = false)
                        flushPage(isLast = true)
                    }
                } else {
                    currentParas.add(para)
                    flushPage(isLast = false)
                    flushPage(isLast = true)
                }
            }
        } else {
            val availableForSplitPx = safePageHeightPx - usedHeightPx - gapBefore
            var splitLine = -1
            for (lineIdx in 0 until layout.lineCount) {
                if (layout.getLineBottom(lineIdx) <= availableForSplitPx) {
                    splitLine = lineIdx
                } else {
                    break
                }
            }

            val canSplitOnThisPage = splitLine >= 0 &&
                splitLine < layout.lineCount - 1 &&
                (splitLine >= 1 || (isFirstPage && currentParas.isEmpty()))

            if (canSplitOnThisPage) {
                val rawEnd = layout.getLineEnd(splitLine, visibleEnd = true).coerceIn(1, para.length)
                var cleanEnd = findCleanWordSplitOffset(para, rawEnd)
                if (cleanEnd <= 0) cleanEnd = rawEnd
                var headPart = para.substring(0, cleanEnd).trimEnd()
                var tailPart = para.substring(cleanEnd).trimStart()
                if (headPart.isEmpty() && rawEnd > 0) {
                    headPart = para.substring(0, rawEnd).trimEnd()
                    tailPart = para.substring(rawEnd).trimStart()
                }

                if (headPart.isNotEmpty() && tailPart.isNotEmpty()) {
                    currentParas.add(headPart)
                    queue.addFirst(tailPart)
                    flushPage(isLast = false)
                } else if (currentParas.isNotEmpty() || isFirstPage) {
                    flushPage(isLast = false)
                    queue.addFirst(para)
                } else {
                    currentParas.add(para)
                    flushPage(isLast = false)
                }
            } else if (currentParas.isNotEmpty() || isFirstPage) {
                flushPage(isLast = false)
                queue.addFirst(para)
            } else {
                // Paragraph itself is taller than an entire blank body page
                var lineCut = -1
                for (lineIdx in 0 until layout.lineCount) {
                    if (layout.getLineBottom(lineIdx) <= safePageHeightPx) {
                        lineCut = lineIdx
                    } else {
                        break
                    }
                }
                if (lineCut >= 0 && lineCut < layout.lineCount - 1) {
                    val rawCut = layout.getLineEnd(lineCut, visibleEnd = true).coerceIn(1, para.length)
                    var cleanCut = findCleanWordSplitOffset(para, rawCut)
                    if (cleanCut <= 0) cleanCut = rawCut
                    val headPart = para.substring(0, cleanCut).trimEnd()
                    val tailPart = para.substring(cleanCut).trimStart()
                    if (headPart.isNotEmpty() && tailPart.isNotEmpty()) {
                        currentParas.add(headPart)
                        queue.addFirst(tailPart)
                    } else {
                        currentParas.add(para)
                    }
                } else {
                    currentParas.add(para)
                }
                flushPage(isLast = false)
            }
        }
    }

    if (currentParas.isNotEmpty() || pages.isEmpty()) {
        flushPage(isLast = true)
    } else {
        pages[pages.lastIndex] = pages.last().copy(isLastPage = true)
    }

    return pages
}
