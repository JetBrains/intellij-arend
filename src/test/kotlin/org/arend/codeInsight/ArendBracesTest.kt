package org.arend.codeInsight

import org.arend.ArendTestBase

class ArendBracesTest : ArendTestBase() {
    private fun checkDoNothing(code: String, type: Char) {
        val file = InlineFile(code).withCaret()
        val text = file.text
        myFixture.type(type)
        myFixture.checkResult(text)
    }

    fun `test goal braces`() = checkDoNothing("""\func tony => {?{-caret-}}""", '}')

    fun `test simple braces`() = checkDoNothing("""\func beta => lamb {{-caret-}}""", '}')

    fun `test parenthesis`() = checkDoNothing("""\func tonyxu => (tql{-caret-})""", ')')

    fun `test goal expr braces`() = checkDoNothing("""\func tonyxty/ => {?(hStacks){-caret-}}""", '}')
}