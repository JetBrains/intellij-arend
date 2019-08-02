package org.arend.quickfix


class PatternQuickFixTest : QuickFixTestBase() {
    fun `test too many patterns`() = simpleQuickFixTest("Remove",
        """
            \func test (x y : Nat) : Nat
              | _, _, _, _{-caret-} => 0
        """,
        """
            \func test (x y : Nat) : Nat
              | _, _, => 0
        """)

    fun `test too many patterns elim`() = simpleQuickFixTest("Remove",
        """
            \func test (x y : Nat) : Nat \elim x
              | _, {-caret-}_ => 0
        """,
        """
            \func test (x y : Nat) : Nat \elim x
              | _ => 0
        """)

    fun `test too many patterns implicit`() = simpleQuickFixTest("Remove",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | _, _, _{-caret-} => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | _, _ => 0
        """)


    fun `test elim implicit ok`() = checkNoQuickFixes("Remove",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
              | _, _, _{-caret-} => 0
        """)

    fun `test remove implicit`() = simpleQuickFixTest("Remove",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | {-caret-}{_}, _, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | _, _ => 0
        """)

    fun `test make explicit`() = simpleQuickFixTest("Make explicit",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | {-caret-}{_}, {_}, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat
              | _, {_}, _ => 0
        """)

    fun `test implicit with elim`() = simpleQuickFixTest("Make explicit",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
              | _, {-caret-}{_}, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
              | _, _, _ => 0
        """)

    fun `test many implicits with elim`() = simpleQuickFixTest("Make explicit",
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
              | {0}, 1, 2 => 1
              | 2, 3, {-caret-}{4} => 2
              | _, {_}, _ => 0
        """,
        """
            \func test (x : Nat) {y : Nat} (z : Nat) : Nat \elim x, y, z
              | 0, 1, 2 => 1
              | 2, 3, 4 => 2
              | _, _, _ => 0
        """)
}