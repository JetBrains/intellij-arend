package org.arend.refactoring

import java.util.Collections.singletonList

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
       \func foo (b a : Nat) : Nat \with {
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
       \func foo {l : Array Nat} : Nat \with
         | {nil} => 0 
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
               {-*-} (y : List Y) (z : List Z) : List (\Sigma Y X) \with
         | X, Y, Z, _, nil, z => nil
         | X, Y, Z, nil, _, z => nil
         | X, Y, Z, cons y ys, cons x xs, z => cons (x, y) (doubleZip2 _ _ {?} ys xs {?})

       \func doubleZip (A B C : \Set) (x : List A) (y : List B) (z : List C) => doubleZip2 _ _ {?} z (doubleZip2 _ _ {?} y x {?}) {?}
       """, listOf(Pair(-2, "X"), Pair(-1, "Y"), "Z", Pair(4, "x"), Pair(3, "y"), "z"),
            listOf(Pair("Z", Pair(true, "\\Set")),
                   Pair("x", Pair(true, "List X")),
                   Pair("y", Pair(true, "List Y")),
                   Pair("z", Pair(true, "List Z"))), "doubleZip2")

    fun testFunc() = changeSignature("""
       \func foo{-caret-} {n : Nat} (m : Nat) : Nat \with
         | {zero}, q => q
         | {suc n}, q => foo {n} 1
    """, """
       \func zoo {zz : Nat} (q : Nat) : Nat \with
         | {q}, zero => q
         | {q}, suc n => zoo {1} n
    """, listOf(Pair(-2, "zz"), Pair(-1, "q")), listOf(), "zoo")

    fun testCombined2() = changeSignature("""
       \module M \where {
         \func foo \alias fu{-caret-} (a : Nat) (b : Nat) => a Nat.+ b
  
         \func bar => foo 1 2
         
         \func bar2 => fu 1 2
       }

       \module M1 \where {
         \open M (foo \as fubar)
  
         \func lol => fubar 3 4
       } 
    """, """
       \module M \where {
         \func foobar \alias fu {a b : Nat} => b Nat.+ a
  
         \func bar => foobar {2} {1}
         
         \func bar2 => fu {2} {1}
       }

       \module M1 \where {
         \open M (foobar \as fubar)
  
         \func lol => fubar {4} {3}
       } 
    """, listOf(Pair(-2, "a"), Pair(-1, "b")), emptyList(), "foobar")

    fun testCombinedData() = changeSignature("""
       \module M \where \data MyData{-caret-} {X : \Type} {Y : \Type} (x : X) (y : Y) (n : Nat) \elim n
         | zero => \infixl 2 consZero (x = x)
         | suc n' => \infixl 1 consSuc (MyData {X} {Y} x y n') (MyData {X} {Y} x y n')

       \func usage (n : Nat) (d : M.MyData Nat Nat n) : M.MyData 1 1 n \with
         | zero, M.consZero p => M.MyData.consZero {_} {_} {1} {1} idp
         | suc n', M.consSuc d d2 => M.MyData.consSuc (usage n' d) (usage n' d)


       \module M2 \where {
         \open M (consZero, consSuc, MyData)
         \func bar : MyData {Nat} {Nat} 1 1 1 => consZero idp consSuc (consZero) {_} {_} {1} {1} idp
       }

       \func bar2 => M.consZero idp M.MyData.consSuc (M.consZero) {_} {_} {1} {1} idp
    """, """
       \module M \where \data MyData2 (X : \Type) (n : Nat) (y x : X) \elim n
         | zero => \infixl 2 consZero (x = x)
         | suc n' => \infixl 1 consSuc (MyData2 X n' y x) (MyData2 X n' y x)

       \func usage (n : Nat) (d : M.MyData2 _ n Nat Nat) : M.MyData2 _ n 1 1 \with
         | zero, M.consZero p => M.MyData2.consZero {_} {1} {1} idp
         | suc n', d M.consSuc d2 => (usage n' d) M.MyData2.consSuc (usage n' d)


       \module M2 \where {
         \open M (consZero, consSuc, MyData2)
         \func bar : MyData2 Nat 1 1 1 => consZero idp consSuc (consZero) {_} {1} {1} idp
       }

       \func bar2 => M.consZero idp M.MyData2.consSuc (M.consZero) {_} {1} {1} idp
    """, listOf(Pair(-1, "X"),
                Pair(5, "n"),
                Pair(4, "y"),
                Pair(3, "x")), singletonList(Pair("y", Pair(true, "X"))), "MyData2")

    fun testCombinedData2() = changeSignature(""" 
       \class C {Z : \Type} {
         \data Foo{-caret-} \alias Fu {X Y : \Type} (n : Nat) \elim n
           | zero => nil X Z
           | suc n => cons X (Foo {\this} {X} {Y} n)
       }

       \func lol (I : C {Nat}) : C.Fu 2 => C.cons 1 (C.cons 2 (C.Fu.nil {I} {_} {Nat} 3 4)) 
    """, """
       \class C {Z : \Type} {
         \data Bar \alias Fu (X : \Type) (n m : Nat) \elim n
           | zero => nil X Z
           | suc n => cons X (Bar {\this} X n {?})
       }

       \func lol (I : C {Nat}) : C.Fu _ 2 {?} => C.cons {_} {_} {_} {{?}} 1 (C.cons {_} {_} {_} {{?}} 2 (C.Fu.nil {I} {_} {{?}} 3 4)) 
    """, listOf(Pair(-1, "X"), Pair(3, "n"), "m"), listOf(Pair("m", Pair(true, "Nat"))), "Bar")

    fun testCombineData2b() = changeSignature("""
       \class C {Z : \Type} {
         \data Foo{-caret-} \alias Fu {X Y : \Type} (n : Nat) \elim n
           | zero => nil X Z
           | suc n => cons X (Foo {\this} {X} {Y} n)
       }

       \func lol (I : C {Nat}) : C.Fu 2 => C.cons 1 (C.cons 2 (C.Fu.nil {I} {_} {Nat} 3 4)) 
    """, """
       \class C {Z : \Type} {
         \data Bar \alias Fu {X : \Type} (n m : Nat) \elim n
           | zero => nil X Z
           | suc n => cons X (Bar {\this} {X} n {?})
       }

       \func lol (I : C {Nat}) : C.Fu 2 {?} => C.cons {_} {_} {_} {{?}} 1 (C.cons {_} {_} {_} {{?}} 2 (C.Fu.nil {I} {_} {{?}} 3 4)) 
    """, listOf(Pair(1, "X"), Pair(3, "n"), "m"), listOf(Pair("m", Pair(true, "Nat"))), "Bar")

    fun testData3() = changeSignature("""
       \data Vec{-caret-} {X : \Type} (n : Nat) \with
         | {X}, zero => nullV
         | {X}, suc n => consV {X} (Vec {X} n)
         
       \func lol => consV {Nat} {1} {101} (consV {_} {0} {101} nullV)
    """, """
       \data Vec {X : \Type} \with
         | {X}{-, zero-} => nullV
         | {X}{-, suc n-} => consV {X} (Vec {X})
         
       \func lol => consV {Nat} {101} (consV {_} {101} nullV)
    """, listOf(Pair(1, "X")))

    fun testData4() = changeSignature("""
       \data Vec2{-caret-} (n : Nat) \with
         | zero => nullV2
         | suc n => consV2 (Vec2 n)
    """, """
       \data Vec2 {-\with-}
         | {-zero =>-} nullV2
         | {-suc n =>-} consV2 (Vec2)
    """, listOf())

    fun testData5() = changeSignature("""
       \data Vec{-caret-} Nat Nat \with
         | zero, suc m => cons1 Nat (Vec 0 m)
         | suc n, zero => cons2 (Vec n 0) Nat 
    """, """
       \data Vec (_ _ : Nat) \with
         | suc m, zero => cons1 Nat (Vec m 0)
         | zero, suc n => cons2 (Vec 0 n) Nat 
    """, listOf(Pair(2, "_"), Pair(1, "_")))

    fun testData6() = changeSignature("""
       \data Vec{-caret-} (n : Nat) \with
         | zero => null
         | suc n => cons (Vec n)
    """, """
       \data Vec {n : Nat} \with
         | {zero} => null
         | {suc n} => cons (Vec {n})
    """, listOf(Pair(-1, "n")))

    fun testData7() = changeSignature("""
       \data Lol{-caret-} {n : Nat} (m : Nat) \with
         | 1 => cons1
         | {0}, p => cons2 Nat
         | {1}, suc p => cons3 (Lol {0} p)
         
         \func usage => cons1 {1}
    """, """
       \data Lol {m : Nat} (n : Nat) \with
         | {1}, n => cons1
         | {p}, 0 => cons2 Nat
         | {suc p}, 1 => cons3 (Lol {p} 0)
         
         \func usage => cons1 {1}
    """, listOf(Pair(-2, "m"), Pair(-1, "n")))

    fun testConstructor() = changeSignature("""
       \data K2
         | base
         | loop Nat : base = base
         | relation{-caret-} (n n' : Nat) (i j : I) \elim i, j {
           | left, j => base
           | right, j => loop n' j
           | i, left => loop n i
           | i, right => loop (n Nat.+ n') i
         }
    """, """
       \data K2
         | base
         | loop Nat : base = base
         | relation {n n' : Nat} {i j : I} \elim i, j {
           | left, j => base
           | right, j => loop n' j
           | i, left => loop n i
           | i, right => loop (n Nat.+ n') i
         }
    """, listOf(Pair(-1, "n"), Pair(-2, "n'"), Pair(-3, "i"), Pair(-4, "j")))

    fun testClass() = changeSignature("""
       \data Bool | true | false
       \data List (X : \Type) | nil | cons X (List X)

       \class C1{-caret-} (X : \Set0) {
         \field x : X
       }

       \class C2 (Y : \Type) \extends C1

       \class C3 (Z : \Type) (z : Z) \extends C1 | X => Nat

       \class C4 (y : Y) \extends C3, C2

       \func usage => \new C4 1 Bool true (List Nat) (cons 1 nil)
       \func usage2 => \new C3 1 Bool true
       \func usage3 => \new C2 Nat 1 Bool
    """, """
       \data Bool | true | false
       \data List (X : \Type) | nil | cons X (List X)

       \class C1 {XX : \Set0} {
         \field x : XX
       }

       \class C2 (Y : \Type) \extends C1

       \class C3 (Z : \Type) (z : Z) \extends C1 | XX => Nat

       \class C4 (y : Y) \extends C3, C2

       \func usage => \new C4 1 Bool true (List Nat) (cons 1 nil)
       \func usage2 => \new C3 1 Bool true
       \func usage3 => \new C2 Nat {1} Bool
    """, listOf(Pair(-1, "XX")))

    fun testClass2() = changeSignature("""
       \data Bool | true | false
       \data List (X : \Type) | nil | cons X (List X)

       \class C1 (X : \Set0) {
         \field x : X
       }

       \class C2 (Y : \Type) \extends C1

       \class C3{-caret-} (Z : \Type) (z : Z) \extends C1 | X => Nat

       \class C4 (y : Y) \extends C3, C2

       \func usage => \new C4 1 Bool true (List Nat) (cons 1 nil)
       \func usage2 => \new C3 1 Bool true
       \func usage3 => \new C2 Nat 1 Bool
    """, """
       \data Bool | true | false
       \data List (X : \Type) | nil | cons X (List X)

       \class C1 (X : \Set0) {
         \field x : X
       }

       \class C2 (Y : \Type) \extends C1

       \class C3 {Z : \Type} {z : Z} \extends C1 | X => Nat

       \class C4 (y : Y) \extends C3, C2

       \func usage => \new C4 1 {Bool} {true} (List Nat) (cons 1 nil)
       \func usage2 => \new C3 {1} {Bool} true
       \func usage3 => \new C2 Nat 1 Bool
    """, listOf(Pair(-1, "Z"), Pair(-2, "z")))

    fun testProperty() = changeSignature("""
       \func foo (\property {-caret-}A : \Type) => 101
    """, """
       \func foo {\property A : \Type} => 101
    """, listOf(-1))

    fun testPrivate() = changeSignature("""
       \private \func foo (A{-caret-} : \Type) => 101
       \func bar => foo Nat
    """, """
       \private \func foo {A : \Type} => 101
       \func bar => foo {Nat}
    """, listOf(-1))

    fun testExternalParameters() = changeSignature("""
       \func foo (x : \Type) => x \where
         \func bar{-caret-} (y : x) => y

       \func lol => foo.bar {Nat} 1
    """, """
       \func foo (x : \Type) => x \where
         \func bar (y : x) (z : Nat) => y

       \func lol => foo.bar {Nat} 1 {?}
    """, listOf(1, "z"), listOf(Pair("z", Pair(true, "Nat"))))

    fun testExternalParameters2() = changeSignature("""
    \class Foo {u : Nat} {
      | v : Nat

      \func foo (w : Nat) => u Nat.+ v Nat.+ w \where {
        \func \infixl 1 +{-caret-}++ (x y : Nat) => u Nat.+ v Nat.+ w Nat.+ x Nat.+ y

        \func usage1 (a b c : Nat) => a +++ b +++ c
      }

      \func usage2 (a b c : Nat) => foo.+++ {_} {0} (foo.+++ {_} {0} a b) c
    }

    \class Bar \extends Foo {
      \func lol1 => Foo.foo.+++ {_} {0} 101 102
    } \where {
      \func lol2 => Foo.foo.+++ {\new Foo {101} 102} {0} 101 102
    }
    """, """
    \class Foo {u : Nat} {
      | v : Nat

      \func foo (w : Nat) => u Nat.+ v Nat.+ w \where {
        \func \infixl 1 +++ {z : Nat} (x y : Nat) => u Nat.+ v Nat.+ w Nat.+ x Nat.+ y

        \func usage1 (a b c : Nat) => a +++ {_} {w} {{?}} b +++ {_} {w} {{?}} c
      }

      \func usage2 (a b c : Nat) => (a foo.+++ {_} {0} {{?}} b) foo.+++ {_} {0} {{?}} c
    }

    \class Bar \extends Foo {
      \func lol1 => 101 Foo.foo.+++ {_} {0} {{?}} 102
    } \where {
      \func lol2 => 101 Foo.foo.+++ {\new Foo {101} 102} {0} {{?}} 102
    }
    """, listOf("z", 1, 2), listOf(Pair("z", Pair(false, "Nat"))))

    fun testExternalParameters3() = changeSignature("""
    \func foo (X : \Type) => X \where {
      \data List {n{-caret-} : Nat} \elim n
        | 0 => nil
        | suc n => \infixl 1 :: X (List {X} {n})

      \func d (x : X) : List {_} {1} => x :: nil
    }

    \func bar : foo.List {Nat} {1} => 0 foo.:: {Nat} {0} foo.nil
 """, """
    \func foo (X : \Type) => X \where {
      \data List (n : Nat) \elim n
        | 0 => nil
        | suc n => \infixl 1 :: X (List {X} n)

      \func d (x : X) : List 1 => x :: nil
    }

    \func bar : foo.List {Nat} 1 => 0 foo.:: {Nat} {0} foo.nil
 """, listOf(-1))

   fun testExternalParameters4() = changeSignature("""
   \func foo (X{-caret-} : \Type) => X \where {
     \data List {n : Nat} \elim n
       | 0 => nil
       | suc n => \infixl 1 :: X (List {X} {n})

     \func d (x : X) : List {X} {1} => x :: nil
   }

   \func bar : foo.List {Nat} {1} => 0 foo.:: {Nat} {0} foo.nil
""", """
  \func foo {X : \Type} => X \where {
    \data List {n : Nat} \elim n
      | 0 => nil
      | suc n => \infixl 1 :: X (List {X} {n})

    \func d (x : X) : List {X} {1} => x :: nil
  }

  \func bar : foo.List {Nat} {1} => 0 foo.:: {Nat} {0} foo.nil
""", listOf(-1))

   fun testExternalParameters5() = changeSignature("""
   \func foo (X{-caret-} : \Type) => X \where {
     \data List {n : Nat} \elim n
       | 0 => nil
       | suc n => \infixl 1 :: X (List {X} {n})

     \func d (x : X) : List {X} {1} => x :: nil {X}
   }

   \func bar : foo.List {Nat} {1} => 0 foo.:: {Nat} {0} foo.nil {Nat}
""", """
   \func foo => X \where {
     \data List {X : \Type} {n : Nat} \elim n
       | 0 => nil
       | suc n => \infixl 1 :: X (List {X} {n})

     \func d {X : \Type} (x : X) : List {X} {1} => x :: nil {X}
   }

   \func bar : foo.List {Nat} {1} => 0 foo.:: {Nat} {0} foo.nil {Nat}
""", listOf())

   fun testExternalParameters6() = changeSignature("""
      \func Hom{-caret-} (R S : \Set) => R -> S \where {
        \func comp {T : \Set} (f : Hom R T) (g : Hom T S) : Hom R S => \lam r => g (f r)
        
        \func usage => comp {Nat} (\lam x => x Nat.+ 2) (\lam y => y Nat.* 2)
      }

      \func Hom_ {S R : \Set} (f : Hom S R) => Hom.comp {S} {R} (\lam x => x) f

      \func comp2 => Hom.comp (\lam (n : Nat) => n Nat.+ 2) (\lam (m : Nat) => m Nat.* 2) 
   """, """
      \func Hom (S_ R_ : \Set) => R_ -> S_ \where {
        \func comp {T : \Set} (f : Hom T R_) (g : Hom S_ T) : Hom S_ R_ => \lam r => g (f r)
        
        \func usage => comp {_} {Nat} (\lam x => x Nat.+ 2) (\lam y => y Nat.* 2)
      }

      \func Hom_ {S R : \Set} (f : Hom R S) => Hom.comp {R} {S} (\lam x => x) f

      \func comp2 => Hom.comp (\lam (n : Nat) => n Nat.+ 2) (\lam (m : Nat) => m Nat.* 2) 
   """, listOf(Pair(2, "S_"), Pair(1, "R_")), listOf(Pair("S_", Pair(true, "\\Set")), Pair("R_", Pair(true, "\\Set"))))

    fun testAlias() = changeSignature("""
       -- ! A.ard
       \func foo \alias bar (n{-caret-} : Nat) => n
              
       -- ! Main.ard
       \import A (foo, bar)
       
       \func test => foo 101 Nat.+ bar 101
    """, """       
       \import A (foo, bar)
       
       \func test => foo {101} Nat.+ bar {101}
    """, listOf(-1), fileName = "Main.ard")

    fun testClassImplement() = changeSignature("""
       \class C {
         | field (n{-caret-} : Nat) {m : Nat} : Nat
       }
       
       \class D \extends C {
         | field n {m} => n Nat.+ m
       }
    """, """
       \class C {
         | field {n m : Nat} : Nat
       }
       
       \class D \extends C {
         | field {n} {m} => n Nat.+ m
       }
    """, listOf(-1, 2))

    fun testExternalParametersInClasses1() = changeSignature("""
       \func foo{-caret-} (A : \Type) (a : A) => 101 \where {
         \class C (b : A) {
           | field : a = b
         }

         \class D (c : A) \extends foo.C {
           | field2 : a = c
         }
       }

       \func lol : foo.D => \new foo.D {Nat} {101} 101 {Nat} {42} 42 idp idp
    """, """
       \func foo (A : \Type) => 101 \where {
         \class C {a : A} (b : A) {
           | field : a = b
         }

         \class D {a : A} (c : A) \extends foo.C {
           | field2 : a = c
         }
       }

       \func lol : foo.D => \new foo.D {Nat} {101} 101 {Nat} {42} 42 idp idp
    """, listOf(1), typecheck = true)

    fun testExternalParametersInClasses2() = changeSignature("""
       \func foo (A : \Type) (a : A) => 101 \where {
         \class C{-caret-} (b1 b2 : A) {
           | field : a = b1
         }

         \class D (c : A) \extends foo.C {
           | field2 : a = c
         }
       }

       \class E \extends foo.C {
         | b1 => b2
       }

       \func lol : foo.D => \new foo.D {Nat} {101} 101 {Nat} {42} 42 42 idp idp

       \func lol2 : foo.D {Nat} {101} 101 {Nat} {42} 42 => {?}

       \func lol3 : foo.D {Nat} {101} 101 {Nat} {42} 42 42 idp => {?}

       \func lol4 : E => \new E {Nat} {101} 101 idp
    """, """
       \func foo (A : \Type) (a : A) => 101 \where {
         \class C (bb b2 : A) {x : A} {
           | field : a = bb
         }

         \class D (c : A) \extends foo.C {
           | field2 : a = c
         }
       }

       \class E \extends foo.C {
         | bb => b2
       }

       \func lol : foo.D => \new foo.D {Nat} {101} 101 {Nat} {42} 42 42 {{?}} idp idp

       \func lol2 : foo.D {Nat} {101} 101 {Nat} {42} 42 => {?}

       \func lol3 : foo.D {Nat} {101} 101 {Nat} {42} 42 42 {{?}} idp => {?}

       \func lol4 : E => \new E {Nat} {101} 101 {{?}} idp
    """, listOf(Pair(1, "bb"), 2, "x"), listOf(Pair("x", Pair(false, "A"))), typecheck = true)

    fun testLongNames() = changeSignature("""
       \class Foo {
         | n : Nat

         \func {-caret-}succ : Foo => \new Foo (Nat.suc n)
         
         \func pred : Foo => \new Foo (\case n \with {
           | zero => zero
           | suc n => n
         })
         
         \func lamReceiver (l : \Pi (F : Foo) -> Foo) => l
  
         \func usage1 => lamReceiver (succ {__})
         
         \func usage2 => succ.succ.succ

         \func usage3 => succ.pred.succ.pred
       }

       \func foo (x : Foo) => Foo.pred.succ.pred.succ
    """, """
       \class Foo {
         | n : Nat

         \func succ {x : Nat} : Foo => \new Foo (Nat.suc n)
         
         \func pred : Foo => \new Foo (\case n \with {
           | zero => zero
           | suc n => n
         })
         
         \func lamReceiver (l : \Pi (F : Foo) -> Foo) => l
  
         \func usage1 => lamReceiver (succ {__} {{?}})
         
         \func usage2 => succ {succ {succ {_} {{?}}} {{?}}} {{?}}

         \func usage3 => pred {succ {pred {succ {_} {{?}}}} {{?}}}
       }

       \func foo (x : Foo) => Foo.succ {Foo.pred {Foo.succ {Foo.pred} {{?}}}} {{?}}
    """, listOf("x"), listOf(Pair("x", Pair(false, "Nat"))))

    fun testLongNames2() = changeSignature("""
       \class Foo {
         | n : Nat

         \func {-caret-}succ : Foo => \new Foo (Nat.suc n)

         \func \infix 1 +++ (a b : Nat) => 101

         \func lol => 101 succ.succ.+++ 102
       }
    """, """
       \class Foo {
         | n : Nat

         \func succ {x : Nat} : Foo => \new Foo (Nat.suc n)

         \func \infix 1 +++ (a b : Nat) => 101

         \func lol => 101 +++ {succ {succ {_} {{?}}} {{?}}} 102
       }
    """, listOf("x"), listOf(Pair("x", Pair(false, "Nat"))))

}