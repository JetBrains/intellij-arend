package org.arend.refactoring

class ArendChangeSignatureTest: ArendChangeSignatureTestBase() {
    fun testFunctionChangeExplicitness() = changeSignature(
        """
           \func foo{-caret-} (a b c : Nat) => a Nat.+ b Nat.+ c
        """,
        """
           \func foo (a : Nat) {b : Nat} (c : Nat) => a Nat.+ b Nat.+ c
        """, listOf(1, -2, 3))

    fun testFunctionReorder() = changeSignature(
        """
            \func foo{-caret-} (a b c : Nat) => a
            \func bar => foo 1 2 3
        """, """
            \func foo (c : Nat) {b : Nat} (a : Nat) => a
            \func bar => foo 3 {2} 1
        """, listOf(3, -2, 1))

    fun testFunctionDeleteInsertArg() = changeSignature(
        """
           \func foo{-caret-} (a b c : Nat) => a
           \func bar => foo 1 2 3
        """, """
           \func foo (c a d : Nat) => a
           \func bar => foo 3 1 {?}
        """, listOf(3, 1, "d"), listOf(Pair("d", Pair(true, "Nat")))
    )

    fun testRemoveArg() = changeSignature(
        """
           \func bar{-caret-} (x : Nat) => 1
        """, """
           \func bar => 1
        """, listOf())

    fun testRemoveArg2() = changeSignature(
        """
           \func foo{-caret-} (a b c d : Nat) => bar a

           \func bar (x : Nat) => foo 1 2 
        """, """
           \func foo (c d : Nat) => bar a

           \func bar (x : Nat) => foo 
        """, listOf(3, 4))

    fun testRecordReorder() = changeSignature(
        """
           \record R {
             \func test => foo {_} {1} {2} {3}
               \where
                 \func foo{-caret-} {a : Nat} {b : Nat} {c : Nat} => c
           } 
        """, """
           \record R {
             \func test => foo 3 2 1
               \where
                 \func foo (c b a : Nat) => c
           } 
        """, listOf(-3, -2, -1))

    fun testSwapAndRenaming() = changeSignature(
        """
           \module F \where {
             \class Foo {
               \func bar{-caret-} (a b : Nat) => a Nat.+ b
             }
           }

           \module Bar \where {
             \open F.Foo (bar \as b)
  
             \func zoo {f : F.Foo} => b {f} 1 2
           }
        """, """
           \module F \where {
             \class Foo {
               \func bar123 (b a : Nat) => a Nat.+ b
             }
           }

           \module Bar \where {
             \open F.Foo (bar123 \as b)
  
             \func zoo {f : F.Foo} => b {f} 2 1
           }
        """, listOf(2, 1), emptyList(), "bar123")

}