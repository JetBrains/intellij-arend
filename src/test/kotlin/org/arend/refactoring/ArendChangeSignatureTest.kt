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

    fun testLevelsInSignature() = changeSignature("""
           \func \infix 1 foo{-caret-} \plevels p1 <= p2 \hlevels h1 >= h2 >= h3 \alias fubar : Nat => 101
    """, """
           \func \infix 1 foobar \plevels p1 <= p2 \hlevels h1 >= h2 >= h3 \alias fubar (A : \Type) : Nat => 101
    """, listOf("A"), listOf(Pair("A", Pair(true, "\\Type"))), "foobar")

    fun testRenameParameters() = changeSignature("""
       \func foo{-caret-} (a b : Nat) => a Nat.+ b 
    """, """
       \func foo (b a : Nat) => b Nat.+ a 
    """, listOf(Pair(1, "b"), Pair(2, "a")))

    fun testWith() = changeSignature("""
       \func foo{-caret-} (a b : Nat) : Nat \with {
         | 0, 0 => 0
         | 0, _ => 1
         | _, 0 => 2
         | suc a, suc b => foo a b
       }
       
       \func test => foo 1 2
    """, """
       \func foo (b a : Nat) : Nat \elim b, a {
         | 0, 0 => 0
         | _, 0 => 1
         | 0, _ => 2
         | suc b, suc a => foo b a
       }
       
       \func test => foo 2 1
    """, listOf(2, 1))

    fun testRemoveArgumentInElim() = changeSignature("""
       \func foo{-caret-} (x y z : Nat) : Nat \elim x, y {
         | 0, 0 => z
         | _, _ => 1
       } 
    """, """
       \func foo (x z : Nat) : Nat \elim x{-, y-} {
         | 0{-, 0-} => z
         | _{-, _-} => 1
       } 
    """, listOf(1, 3))

    fun testClausesWithoutElim() = changeSignature("""
       \func foo{-caret-} (l : Array Nat) : Nat
         | nil => 0 
    """, """
       \func foo {l : Array Nat} : Nat \elim l
         | nil => 0 
    """, listOf(-1))

    fun testCombined() = changeSignature("""
       \data List (A : \Set) | nil | cons A (List A)

       \func zip{-caret-} {A {-foo-} B : \Set} 
               {-*-} (x : List A) -- ... 
               (y : List {-bar-} B) : List (\Sigma A B) \with
         | nil, _ => nil
         | _, nil => nil
         | cons x xs, cons y ys => cons (x, y) (zip xs ys)

       \func doubleZip (A B C : \Set) (x : List A) (y : List B) (z : List C) => zip (zip x y) z
       """, """
       \data List (A : \Set) | nil | cons A (List A)

       \func doubleZip2 ({-foo-} X Y Z : \Set) -- ... 
               (x : List X) 
               {-*-} (y : List Y) (z : List Z) : List (\Sigma Y X) \elim x, y
         | _, nil => nil
         | nil, _ => nil
         | cons y ys, cons x xs => cons (x, y) (doubleZip2 _ _ {?} ys xs {?})

       \func doubleZip (A B C : \Set) (x : List A) (y : List B) (z : List C) => doubleZip2 _ _ {?} z (doubleZip2 _ _ {?} y x {?}) {?}
       """, listOf(Pair(-2, "X"), Pair(-1, "Y"), "Z", Pair(4, "x"), Pair(3, "y"), "z"),
            listOf(Pair("Z", Pair(true, "\\Set")),
                   Pair("x", Pair(true, "List X")),
                   Pair("y", Pair(true, "List Y")),
                   Pair("z", Pair(true, "List Z"))), "doubleZip2")

}