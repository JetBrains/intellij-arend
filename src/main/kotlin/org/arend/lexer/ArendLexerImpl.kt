package org.arend.lexer

class ArendLexerImpl : ArendLexer() {
    override fun reset(buffer: CharSequence?, start: Int, end: Int, initialState: Int) {
        super.reset(buffer, start, end, initialState)
        yybegin(ArendLexer.VERY_BEGINNING)
    }
}