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

    fun testWhitespace() = changeSignature(
        """
            \func foo{-caret-} -- test
               {{-1-}X {-2-} Y : {- 3 -} \Type {-4-}} -- foo
               (x : X) {- 5-} (y : Y) : 
               X => x
        """, """
            \func foo -- test
               ({-2-} Y{-1-}X Z : {- 3 -} \Type {-4-}) {- 5-} (y : Y) -- foo
               (x : X) : 
               X => x
        """, listOf(-2, -1, "Z", 4, 3), listOf(Pair("Z", Pair(true, "\\Type"))))

    fun testWhitespace2() = changeSignature("""
           \func foo{-caret-} ({-1-} a {-2-} b {-3-} c {-4-} d {-5-} : Nat) => 1
    """, """
           \func foo ({-1-} a {-5-} : Nat) {{-2-} b : Nat} ({-3-} c : Nat) => 1
    """, listOf(1, -2, 3))

    fun testRenameParameters() = changeSignature("""
       \func foo{-caret-} (a b : Nat) => a Nat.+ b 
    """, """
       \func foo (b a : Nat) => b Nat.+ a 
    """, listOf(Pair(1, "b"), Pair(2, "a")))

    fun testWith() = changeSignature("""
       \func foo{-caret-} {X : \Type} (a b : Nat) : Nat \with {
         | 0, 0 => 0
         | _, _ => 1
       }
       
       \func test => foo 1 2
    """, """
       \func foo {X : \Type} (b a : Nat) : Nat \elim a, b {
         | 0, 0 => 0
         | _, _ => 1
       }
       
       \func test => foo 2 1
    """, listOf(1, 3, 2))

}