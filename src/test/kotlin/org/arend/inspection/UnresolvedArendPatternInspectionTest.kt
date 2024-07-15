package org.arend.inspection

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class UnresolvedArendPatternInspectionTest : QuickFixTestBase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(UnresolvedArendPatternInspection::class.java)
    }

    fun testBool() = doWarningsCheck(myFixture, """
        -- ! Bool.ard
        \import Data.Bool(Bool, if)

        \data Bool | false | true 
        
        -- ! Main.ard
        \import Bool(Bool)

        \func bar (b : Bool) : Nat => \case b \with {
          | ${uapWarning("true")} => 1
          | ${uapWarning("false")} => 0
        } 
    """)

    companion object {
        fun uapWarning(text: String) = "<warning descr=\"${ArendBundle.message("arend.inspection.unresolved.pattern", text)}\" textAttributesKey=\"WARNING_ATTRIBUTES\">$text</warning>"
    }
}