package org.arend.inspection

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class RedundantParameterNameInspectionTest : QuickFixTestBase() {

    fun testFunc() = doWarningsCheck(myFixture, """
       \func f (${rpnWarning("x")} : Nat) => 0
    """)

    companion object {
        fun rpnWarning(text: String) = "<warning descr=\"${ArendBundle.message("arend.inspection.parameter.redundant", text)}\" textAttributesKey=\"WARNING_ATTRIBUTES\">$text</warning>"
    }
}
