package org.arend.inspection

import org.arend.fileTreeFromText
import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class NameShadowingInspectionTest : QuickFixTestBase() {

    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(RedundantParensInspection::class.java)
    }

    fun testTypeTele() = doWarningsCheck("""
       \data List (A : \Type)
          | nil
          | \infixr 5 :: A (List A)
       \data AllC {A : \Type} (P : A -> A -> \Prop) (l : List A) \elim l
          | nil => allC-nil
          | :: x nil => allC-single
          | :: x (:: y l) => allC-cons {${nsWarning("x")} : \Type} 
    """)

    private fun doWarningsCheck(contents: String, typecheck: Boolean = false) {
        val fileTree = fileTreeFromText(contents)
        fileTree.create(myFixture.project, myFixture.findFileInTempDir("."))
        myFixture.configureFromTempProjectFile("Main.ard")
        if (typecheck) {
            myFixture.doHighlighting()
        }
        myFixture.checkHighlighting(true, false, false)
    }

    companion object {
        fun nsWarning(text: String) = "<warning descr=\"${ArendBundle.message("arend.inspection.name.shadowed", text)}\" textAttributesKey=\"WARNING_ATTRIBUTES\">$text</warning>"
    }
}
