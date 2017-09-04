package org.vclang.search

import com.intellij.lang.cacheBuilder.VersionedWordsScanner
import com.intellij.lang.cacheBuilder.WordOccurrence
import com.intellij.util.Processor
import org.vclang.lexer.VcLexerAdapter
import org.vclang.psi.VC_COMMENTS

open class VcWordScanner : VersionedWordsScanner() {
    private val lexer = VcLexerAdapter()

    override fun processWords(fileText: CharSequence, processor: Processor<WordOccurrence>) {
        lexer.start(fileText)
        val occurrence = WordOccurrence(fileText, 0, 0, null)
        while (lexer.tokenType != null) {
            val kind = if (VC_COMMENTS.contains(lexer.tokenType)) {
                WordOccurrence.Kind.COMMENTS
            } else {
                WordOccurrence.Kind.CODE
            }
            if (!stripWords(
                    processor,
                    fileText,
                    lexer.tokenStart,
                    lexer.tokenEnd,
                    kind,
                    occurrence
            )) return
            lexer.advance()
        }
    }

    companion object {

        fun isVclangIdentifierPart(c: Char): Boolean =
                c in '!'..'~' && c !in setOf('"', '(', ')', ',', '.', '`', '{', '}')

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
                    val ch = tokenText[index]
                    if (isVclangIdentifierPart(ch) || Character.isJavaIdentifierStart(ch)) {
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
