package org.arend.highlight

import org.arend.inspection.doWarningsCheck
import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class RedundantParameterNameInspectionTest : QuickFixTestBase() {

    fun testFunc() = doWarningsCheck(myFixture, """
       \func f (${rpnWarning("x")} : Nat) => 0
    """)

    fun testRemoveRedundantParameterFunc() = doRemoveRedundantParameter(
        """
            \func f (x{-caret-} : Nat) => 0
        """, """
            \func f (_ : Nat) => 0
        """
    )

    private fun doRemoveRedundantParameter(before: String, after: String) =
        typedQuickFixTest(ArendBundle.message("arend.inspection.redundant.parameter.message"), before, after)

    companion object {
        fun rpnWarning(text: String) = "<warning descr=\"${ArendBundle.message("arend.inspection.parameter.redundant", text)}\" textAttributesKey=\"WARNING_ATTRIBUTES\">$text</warning>"
    }
}
