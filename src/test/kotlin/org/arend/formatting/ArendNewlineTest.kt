package org.arend.formatting

class ArendNewlineTest: ArendFormatterTestBase() {
    fun testAfterFunc1() = checkNewLine(
            """
            \func lol (a : Nat) =>{-caret-}
            """,
            """
            \func lol (a : Nat) =>
              {-caret-}
            """)

    fun testAfterFunc2() = checkNewLine(
    """
            \func lol (a : Nat) =>{-caret-}
            \func lol2 => 1
            """,
    """
            \func lol (a : Nat) =>
              {-caret-}
            \func lol2 => 1
            """)

    fun testAfterFunc3() = checkNewLine(
            """
            \func succ (n : Nat) \elim n{-caret-}
            """,
            """
            \func succ (n : Nat) \elim n
              {-caret-}
            """
    )

    fun testAfterFunc4() = checkNewLine(
            """
            \func lol (a : Nat){-caret-}
            \func lol2 => 1
            """,
            """
            \func lol (a : Nat)
              {-caret-}
            \func lol2 => 1
            """)

    fun testAfterFunc5() = checkNewLine(
            """
            \func lol (a : Nat){-caret-}
            """,
            """
            \func lol (a : Nat)
              {-caret-}
            """)
}