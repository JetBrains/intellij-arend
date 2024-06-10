package org.arend.inspection

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class RedundantParameterInspectionTest : QuickFixTestBase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(RedundantParameterInspection::class.java)
    }

    fun testFunc() = doWarningsCheck(myFixture, """
       \func f (${rpnWarning("x")} : Nat) => 0
    """)

    companion object {
        fun rpnWarning(text: String) = "<warning descr=\"${ArendBundle.message("arend.inspection.parameter.redundant", text)}\" textAttributesKey=\"WARNING_ATTRIBUTES\">$text</warning>"
    }
}
