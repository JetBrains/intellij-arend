package org.arend.inspection

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class NameShadowingInspectionTest : QuickFixTestBase() {

    fun testTypeTele() = doWarningsCheck(myFixture, """
       \data List (A : \Type)
          | nil
          | \infixr 5 :: A (List A)
       \data AllC {A : \Type} (P : A -> A -> \Prop) (l : List A) \elim l
          | nil => allC-nil
          | :: x nil => allC-single
          | :: x (:: y l) => allC-cons {${nsWarning("x")} : \Type} 
    """)

    fun testGlobalDefinitions() = doWarningsCheck(myFixture, """
       \data D | con {I : \Set} 
    """)

    companion object {
        fun nsWarning(text: String) = "<warning descr=\"${ArendBundle.message("arend.inspection.name.shadowed", text)}\" textAttributesKey=\"WARNING_ATTRIBUTES\">$text</warning>"
    }
}
