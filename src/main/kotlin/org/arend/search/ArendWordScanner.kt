package org.arend.search

import com.intellij.lang.cacheBuilder.VersionedWordsScanner
import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.util.Processor
import org.arend.lexer.ArendLexerAdapter
import org.arend.psi.AREND_COMMENTS
import org.arend.psi.AREND_NAMES
import org.arend.psi.ArendElementTypes.NEGATIVE_NUMBER
import org.arend.psi.ArendElementTypes.NUMBER

open class ArendWordScanner : VersionedWordsScanner() {
    private val lexer = ArendLexerAdapter()

    override fun processWords(fileText: CharSequence, processor: Processor<in WordOccurrence>) {
        lexer.start(fileText)
        val occurrence = WordOccurrence(fileText, 0, 0, null)
        while (lexer.tokenType != null) {
            if (AREND_COMMENTS.contains(lexer.tokenType)) {
                if (!stripWords(processor, fileText, lexer.tokenStart, lexer.tokenEnd, WordOccurrence.Kind.COMMENTS, occurrence)) return
            } else if (AREND_NAMES.contains(lexer.tokenType)) {
                var start = lexer.tokenStart
                if (fileText[start] == '`') start++
                var end = lexer.tokenEnd
                if (fileText[end - 1] == '`') end--
                occurrence.init(fileText, start, end, WordOccurrence.Kind.CODE)
                if (!processor.process(occurrence)) return
            } else if (lexer.tokenType == NUMBER || lexer.tokenType == NEGATIVE_NUMBER) {
                occurrence.init(fileText, lexer.tokenStart, lexer.tokenEnd, WordOccurrence.Kind.LITERALS)
                if (!processor.process(occurrence)) return
            }
            lexer.advance()
        }
    }

    companion object {
        fun isArendIdentifierPart(c: Char): Boolean =
            isArendIdentifierStart(c) || c in '0'..'9' || c == '\''

        fun isArendIdentifierStart(c: Char): Boolean =
            c in 'a'..'z' || c in 'A'..'Z' || c in "_~!@#$%^&*-+=<>?/|[]:" || c in '\u2200'..'\u22FF' || c in '\u2A00'..'\u2AFF'

        protected fun stripWords(
                processor: Processor<in WordOccurrence>,
                tokenText: CharSequence,
                from: Int,
                to: Int,
                kind: WordOccurrence.Kind,
                occurrence: WordOccurrence
        ): Boolean {
            var index = from
            ScanWordsLoop@ while (true) {
                while (true) {
                    if (index == to) break@ScanWordsLoop
                    var ch = tokenText[index]

                    while (ch == '\\') {
                        do {
                            index++
                            if (index == to) break@ScanWordsLoop
                            ch = tokenText[index]
                        } while (ch in 'a'..'z' || ch in 'A'..'Z' || ch == '-' || ch in '0'..'9')
                    }

                    if (isArendIdentifierStart(ch)) {
                        break
                    }
                    index++
                }

                val wordStart = index
                while (true) {
                    index++
                    if (index == to) break
                    val c = tokenText[index]
                    if (!isArendIdentifierPart(c)) break
                }
                val wordEnd = index
                occurrence.init(tokenText, wordStart, wordEnd, kind)

                if (!processor.process(occurrence)) return false
            }
            return true
        }
    }
}
