package org.vclang.search

import com.intellij.lang.cacheBuilder.VersionedWordsScanner
import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.util.Processor
import org.vclang.lexer.VcLexerAdapter
import org.vclang.psi.VC_COMMENTS
import org.vclang.psi.VC_NAMES

open class VcWordScanner : VersionedWordsScanner() {
    private val lexer = VcLexerAdapter()

    override fun processWords(fileText: CharSequence, processor: Processor<WordOccurrence>) {
        lexer.start(fileText)
        val occurrence = WordOccurrence(fileText, 0, 0, null)
        while (lexer.tokenType != null) {
            if (VC_COMMENTS.contains(lexer.tokenType)) {
                if (!stripWords(processor, fileText, lexer.tokenStart, lexer.tokenEnd, WordOccurrence.Kind.COMMENTS, occurrence)) return
            } else if (VC_NAMES.contains(lexer.tokenType)) {
                var start = lexer.tokenStart
                if (fileText[start] == '`') start++
                var end = lexer.tokenEnd
                if (fileText[end - 1] == '`') end--
                occurrence.init(fileText, start, end, WordOccurrence.Kind.CODE)
                if (!processor.process(occurrence)) return
            }
            lexer.advance()
        }
    }

    companion object {

        fun isVclangIdentifierPart(c: Char): Boolean =
            isVclangIdentifierStart(c) || c in '0'..'9' || c == '\''

        fun isVclangIdentifierStart(c: Char): Boolean =
            c in 'a'..'z' || c in 'A'..'Z' || c in "_~!@#$%^&*-+=<>?/|[];:"

        protected fun stripWords(
                processor: Processor<WordOccurrence>,
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

                    if (isVclangIdentifierStart(ch)) {
                        break
                    }
                    index++
                }

                val wordStart = index
                while (true) {
                    index++
                    if (index == to) break
                    val c = tokenText[index]
                    if (!isVclangIdentifierPart(c)) break
                }
                val wordEnd = index
                occurrence.init(tokenText, wordStart, wordEnd, kind)

                if (!processor.process(occurrence)) return false
            }
            return true
        }
    }
}
