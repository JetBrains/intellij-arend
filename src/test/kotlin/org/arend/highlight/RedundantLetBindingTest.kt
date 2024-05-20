package org.arend.highlight

import org.arend.inspection.doWarningsCheck
import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class RedundantLetBindingTest : QuickFixTestBase() {
    fun testOneLetClause() = doWarningsCheck(myFixture, """
       \func f : String => \let | ${bindingLetWarning("y => 1")} \in ""
    """)

    fun testRemoveOneLetClause() = doRemoveLetClause("""
       \func f : String => \let | {-caret-}y => 1 \in ""
    """, """
       \func f : String => ""
    """)

    fun testManyLetClauses() = doWarningsCheck(myFixture, """
       \func f : String => \let | ${bindingLetWarning("y => 1")} | x => "" \in x
    """)

    fun testRemoveOneFromManyLetClauses() = doRemoveLetClause("""
       \func f : String => \let | {-caret-}y => 1 | x => "" \in x
    """, """
       \func f : String => \let | x => "" \in x 
    """)

    fun testPattern() = doWarningsCheck(myFixture, """
        \func foo : \Sigma Nat Nat => (1, 1)

        \class Bar {
          | a : Nat
        }
        \func FooBar : Bar \cowith
          | a => \have (b, c) => foo \in b
    """)

    private fun doRemoveLetClause(before: String, after: String) =
        typedQuickFixTest(ArendBundle.message("arend.inspection.remove.letBinding"), before, after)

    companion object {
        fun bindingLetWarning(text: String) = "<warning descr=\"${ArendBundle.message("arend.inspection.remove.letBinding.message")}\" textAttributesKey=\"WARNING_ATTRIBUTES\">$text</warning>"
    }
}
