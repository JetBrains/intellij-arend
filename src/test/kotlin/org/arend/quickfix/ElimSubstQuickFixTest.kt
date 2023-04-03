package org.arend.quickfix

import org.arend.util.ArendBundle

class ElimSubstQuickFixTest : QuickFixTestBase() {

    fun testDefIdentifier() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) : p = p =>
        \case \elim p, \elim x{-caret-} \with {
            | _, _ => {?}
        }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) : p = p =>
        \case \elim x, \elim p \with {
            | _, _ => {?}
        }
    """
    )

    fun testCaseArg1() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) : p = p =>
        {-caret-}\case \elim x \with {
          | _ => {?}
        }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) : p = p =>
        \case \elim x, \elim p \with {
          | _ => {?}
        }
    """
    )

    fun testCaseArg2() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) : p = p =>
        {-caret-}\case \elim x, p \with {
          | _, _ => {?}
        }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) : p = p =>
        \case \elim x, \elim p \with {
          | _, _ => {?}
        }
    """
    )
}
