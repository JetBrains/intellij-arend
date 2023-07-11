package org.arend.quickfix

import org.arend.util.ArendBundle

class ElimSubstQuickFixTest : QuickFixTestBase() {

    fun testEliminationOrder1() = typedQuickFixTest(
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

    fun testEliminationOrder2() = typedQuickFixTest(
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

    fun testEliminationOrder3() = typedQuickFixTest(
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

    fun testEliminationOrder4() = typedQuickFixTest(
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

    fun testEliminationOrder5() = typedQuickFixTest(
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

    fun testEliminationOrder6() = typedQuickFixTest(ArendBundle.message("arend.elim.substitute"), """
       \func test (A : \Type) (x : A) (y : A) (p : x = y) : p = p =>
         \case \elim p, \elim y{-caret-}, \elim x \with {
           | p, y, x => {?}
         }
    """, """
       \func test (A : \Type) (x : A) (y : A) (p : x = y) : p = p =>
         \case \elim x, \elim y, \elim p \with {
           | x, y, p => {?}
         }
    """)

    fun testSubstitution1() = typedQuickFixTest(ArendBundle.message("arend.elim.substitute"), """
       \func test (A : \Type) (x : A) (p q : x = x) (y : p = q) : p = q =>
         \case \elim x, y{-caret-}, \elim p, \elim q \with {
           | x, y, _, _ => {?}
         }
    """, """
       \func test (A : \Type) (x : A) (p q : x = x) (y : p = q) : p = q =>
         \case \elim x, \elim p, \elim q, y \with {
           | x, _, _, y => {?}
         } 
    """)


    fun testSubstitution2() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) : p = p =>
        {-caret-}\case \elim x \with {
          | x => {?}
        }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) : p = p =>
        \case \elim x, \elim p \with {
          | x, _ => {?}
        }
    """
    )

    fun testSubstitution3() = typedQuickFixTest(
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

    fun testSubstitution4() = typedQuickFixTest(
        ArendBundle.message("arend.elim.substitute"), """
        \func test (A : \Type) (x : A) (p : x = x) (p1 : x = x) : p = p1 =>
          {-caret-}\case \elim x \with {
            | x => {?}
          }
    """, """
        \func test (A : \Type) (x : A) (p : x = x) (p1 : x = x) : p = p1 =>
          \case \elim x, \elim p, \elim p1 \with {
            | x, _, _ => {?}
          }
    """
    )

    fun testSubstitution5() = typedQuickFixTest(ArendBundle.message("arend.elim.substitute"), """
       \func test (A : \Type) (x : A) (p q : x = x) (y : p = q) : p = q =>
         \case \elim x, y{-caret-}, \elim p \with {
           | x, y, p => {?}
         } 
    """, """
       \func test (A : \Type) (x : A) (p q : x = x) (y : p = q) : p = q =>
         \case \elim x, \elim p, \elim q, y \with {
           | x, p, _, y => {?}
         } 
    """)
}
