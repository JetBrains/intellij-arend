package org.arend.quickfix

import org.arend.util.ArendBundle

class ElimSubstQuickFixTest : QuickFixTestBase() {

    fun testDefIdentifier1() = typedQuickFixTest(
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

    fun testDefIdentifier2() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) (p1 : x = x) : p = p1 =>
          \case \elim p, \elim p1, \elim x{-caret-} \with {
            | _, _, _ => {?}
          }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) (p1 : x = x) : p = p1 =>
          \case \elim x, \elim p, \elim p1 \with {
            | _, _, _ => {?}
          }
    """
    )

    fun testDefIdentifier3() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) (y : A): p = p =>
          \case \elim p, \elim y, \elim x{-caret-} \with {
            | p, y, x => {?}
          }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) (y : A): p = p =>
          \case \elim x, \elim p, \elim y \with {
            | x, p, y => {?}
          }
    """
    )

    fun testDefIdentifier4() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) : Nat =>
          \case \elim p, \elim x{-caret-} \with {
            | idp, x => {?}
          }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) : Nat =>
          \case \elim x, \elim p \with {
            | x, idp => {?}
          }
    """
    )

    fun testDefIdentifier5() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) (y : A): p = p =>
          \case \elim p, \elim p, \elim x{-caret-} \with {
            | _, _, _ => {?}
          }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) (y : A): p = p =>
          \case \elim x, \elim p, \elim p \with {
            | _, _, _ => {?}
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

    fun testCaseArg3() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) (p1 : x = x) : p = p1 =>
          {-caret-}\case \elim x \with {
            | _ => {?}
          }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) (p1 : x = x) : p = p1 =>
          \case \elim x, \elim p, \elim p1 \with {
            | _ => {?}
          }
    """
    )
}
