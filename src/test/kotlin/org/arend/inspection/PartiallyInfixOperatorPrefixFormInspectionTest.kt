package org.arend.inspection

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class PartiallyInfixOperatorPrefixFormInspectionTest : QuickFixTestBase() {

    fun testFunc() = doWarningsCheck(myFixture, """
       \data List (A : \Type)
          | nil
          | \infixr 5 :: A (List A)

       \func map {A B : \Type} (f : A -> B) (l : List A) : List B \elim l
          | nil => nil
          | :: a l => f a :: map f l

       \func f (xs : List Nat) => map (${infixWarning("Nat.+ 3")}) xs
    """)

    fun testData() = doWarningsCheck(myFixture, """
       \data \infix 5 And (A B : Nat)

       \func f => ${infixWarning("And 6")}
    """)

    companion object {
        fun infixWarning(text: String) = "<warning descr=\"${ArendBundle.message("arend.inspection.infix.partially.prefix.form")}\" textAttributesKey=\"WARNING_ATTRIBUTES\">$text</warning>"
    }
}