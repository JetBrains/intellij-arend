package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class ChangeArgumentExplicitnessIntentionTest : QuickFixTestBase() {
    private val fixName = ArendBundle.message("arend.coClause.changeArgumentExplicitness")
    private fun doTest(contents: String, result: String) = simpleQuickFixTest(fixName, contents, result)

    // `IE` means changing from implicit to explicit
    // `EI` means changing from explicit to implicit

    fun testFunctionIESimple1() = doTest(
        """
        \func f (a b : Int) {p{-caret-} : a = b} => 0
        \func g => f 1 1 {idp}
        """,
        """
        \func f (a b : Int) (p{-caret-} : a = b) => 0
        \func g => f 1 1 idp
        """
    )

    fun testFunctionIESimple2() = doTest(
        """
        \func f (a: b: : Int) (p{-caret-} : a: = b:) => 0
        \func g => f 1 1 idp
        """,
        """
        \func f (a: b: : Int) {p{-caret-} : a: = b:} => 0
        \func g => f 1 1 {idp}
        """
    )

    fun testFunctionEIOmittedImplicit() = doTest(
        """
        \func f {A B : \Type} ({-caret-}a : A) (b : B) => a
        \func test (a b : Nat) => f a b
        """,
        """
        \func f {A B : \Type} {{-caret-}a : A} (b : B) => a
        \func test (a b : Nat) => f {_} {_} {a} b
        """
    )

    fun testFunctionEILastImplicit() = doTest(
        """
        \data D (a b : Nat) | con
        \func foo (a {-caret-}b : Nat) : D a b => con
        \func test : D 0 1 => foo _ _
        """,
        """
        \data D (a b : Nat) | con
        \func foo (a : Nat) {b : Nat} : D a b => con
        \func test : D 0 1 => foo _
        """
    )

    fun testFunctionIESaveUnderscore() = doTest(
        """
        \func kComb (A{-caret-} : \Type) {B : \Type} => \lam (a : A) (b : B) => a
        \func f => kComb _ {\Sigma Nat Nat} 1 (4, 2)
        """,
        """
        \func kComb {A B : \Type} => \lam (a : A) (b : B) => a
        \func f => kComb {_} {\Sigma Nat Nat} 1 (4, 2)
        """
    )

    fun testFunctionIESplitTele() = doTest(
        """
        \func k {A {-caret-}B : \Type} (a : A) (b : B) => a
        \func f => k {_} 1 (2, 3) Nat.+ k 4 5
        """,
        """
        \func k {A : \Type} (B : \Type) (a : A) (b : B) => a
        \func f => k _ 1 (2, 3) Nat.+ k _ 4 5
        """
    )

    fun testFunctionIEAllTele1() = doTest(
        """
        \func k {A B : {-caret-}\Type} (a : A) (b : B) => a
        \func test => k 1 2 Nat.+ k 3 4
        """,
        """
        \func k (A B : {-caret-}\Type) (a : A) (b : B) => a
        \func test => k _ _ 1 2 Nat.+ k _ _ 3 4
        """
    )

    fun testFunctionEIAllTele() = doTest(
        """
        \func k (A B : {-caret-}\Type) (a : A) (b : B) => a
        \func test => k Nat _ 1 2 Nat.+ k _ Nat 3 4
        """,
        """
        \func k {A B : {-caret-}\Type} (a : A) (b : B) => a
        \func test => k {Nat} 1 2 Nat.+ k {_} {Nat} 3 4
        """
    )

    fun testFieldIESimple1() = doTest(
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test {\Sigma Nat Nat} (4, 2)
        """,
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test (\Sigma Nat Nat) (4, 2)
        """
    )

    fun testFieldEISimple2() = doTest(
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test (\Sigma Nat Nat) (4, 2)
        """,
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test {\Sigma Nat Nat} (4, 2)
        """
    )

    fun testFieldIESimple3() = doTest(
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test 42
        """,
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test _ 42
        """
    )

    fun testFieldEISimple4() = doTest(
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test _ 42
        """,
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test 42
        """
    )

    fun testFieldIEAllTele() = doTest(
        """
        \class Test (A B :{-caret-} \Type) (a : A) (b : B)
        \func test => Test Nat _ 4 2
        """,
        """
        \class Test {A B :{-caret-} \Type} (a : A) (b : B)
        \func test => Test {Nat} 4 2
        """
    )

    fun testFieldDescendant() = doTest(
        """
        \class C {n : Nat}
        \class C2 {{-caret-}X : \Type}           
        \class D \extends C, C2
        \func foo (c2 : C2) => \new D {1} {Nat}
        """, """
        \class C {n : Nat}
        \class C2 (X : \Type)           
        \class D \extends C, C2
        \func foo (c2 : C2) => \new D {1} Nat 
        """)

    fun testFieldDescendant2() = doTest("""
       \class C {X : \Type}       
       \class C2 {-caret-}{n1 n2 : Nat}
       
       \class C3 {Y : \Type}
       \class D \extends C, C2
       \class D2 \extends D, C3

       \func foo (d : D2 {Nat}) => \new D2 {Nat} {1} {2} {Nat}
       \func foo1 (d : D {Nat}) => \new D {Nat} {1} {2}
       \func foo2 (d : D {Nat} {1}) => D {Nat} {1} 
    """, """
       \class C {X : \Type}       
       \class C2 (n1 n2 : Nat)
       
       \class C3 {Y : \Type}
       \class D \extends C, C2
       \class D2 \extends D, C3

       \func foo (d : D2 {Nat}) => \new D2 {Nat} 1 2 {Nat}
       \func foo1 (d : D {Nat}) => \new D {Nat} 1 2
       \func foo2 (d : D {Nat} 1) => D {Nat} 1 
    """)

//    TODO: this is related with implicit arguments in the end
//    fun testTypeImToExUnderscoreEnd() = doTest(
//        """
//        \record testRecord (T : \Type)
//          | k {A B : \Type} (a : A) (b :{-caret-} B) : A
//
//        \func h => \new testRecord (\Sigma Nat Nat) {
//          | k a _ => a
//        }
//        """,
//        """
//        \record testRecord (T : \Type)
//          | k {A B : \Type} (a : A) {b {-caret-}: B} : A
//
//        \func h => \new testRecord (\Sigma Nat Nat) {
//          | k a {_} => a
//        }
//        """
//    )

    fun testFieldIESplitTele() = doTest(
        """
        \class Test {{-caret-}A B : \Type} (a : A) (b : B)
        \func f => Test {Nat} 1 (4, 2)
        """,
        """
        \class Test ({-caret-}A : \Type) {B : \Type} (a : A) (b : B)
        \func f => Test Nat 1 (4, 2)
        """
    )

    fun testTypeEISimple1() = doTest(
        """
        \record testRecord (T : \Type)
          | k {{-caret-}A B : \Type} (a : A) (b : B) : A

        \func h => \new testRecord (\Sigma Nat Nat) {
          | k a b => a
        }
        """,
        """
        \record testRecord (T : \Type)
          | k ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) : A

        \func h => \new testRecord (\Sigma Nat Nat) {
          | k _ a b => a
        }
        """
    )

    fun testTypeEIOmittedImplicit() = doTest(
        """
        \record testRecord (T : \Type)
          | k {A B : \Type} ({-caret-}a : A) (b : B) : A

        \func h => \new testRecord (\Sigma Nat Nat) {
          | k {_} a b => a
        }
        """,
        """
        \record testRecord (T : \Type)
          | k {A B : \Type} {{-caret-}a : A} (b : B) : A

        \func h => \new testRecord (\Sigma Nat Nat) {
          | k {_} {_} {a} b => a
        }
        """
    )

    fun testTypeIEAllTele() = doTest(
        """
        \record testRecord (T : \Type)
          | k {A B : {-caret-}\Type} (a : A) (b : B) : A

        \func h => \new testRecord (\Sigma Nat Nat) {
          | k a b => a
        }
        """,
        """
        \record testRecord (T : \Type)
          | k (A B : {-caret-}\Type) (a : A) (b : B) : A

        \func h => \new testRecord (\Sigma Nat Nat) {
          | k _ _ a b => a
        }
        """
    )

    fun testTypeEIAllTele() = doTest(
        """
        \record testRecord (T : \Type)
          | k (A B : {-caret-}\Type) (a : A) (b : B) : A

        \func h => \new testRecord (\Sigma Nat Nat) {
          | k _ {- lol -} _ {- a -} a b => a
        }
        """,
        """
        \record testRecord (T : \Type)
          | k {A B : {-caret-}\Type} (a : A) (b : B) : A

        \func h => \new testRecord (\Sigma Nat Nat) {
          | k {- lol -} {- a -} a b => a
        }
        """
    )

    fun testInfixIESimple() = doTest(
        """
        \module M \where \func mp {{-caret-}A B : \Type} (a : A) (b : B) => a
        \func g => 42 M.`mp` 41
        """,
        """
        \module M \where \func mp ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => a
        \func g => M.mp _ 42 41
        """
    )


    fun testInfixIEChain() = doTest(
        """
        \func \infixl 6 !+! {{-caret-}A B : \Type} (a : A) (b : B) => (a, b)
        \func f => ((1, 2) !+! (3, 4)) !+! (5, 6)
        """,
        """
        \func \infixl 6 !+! ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => (a, b)
        \func f => !+! _ (!+! _ (1, 2) (3, 4)) (5, 6)
        """
    )

    fun testFunctionIESaveInfix() = doTest(
        """
        \open Nat (+)
        \func f (a {-caret-}b : Nat) => a
        \func test => f 0 1 + 2
        """,
        """
        \open Nat (+)
        \func f (a : Nat) {b : Nat} => a
        \func test => f 0 {1} + 2
        """
    )

    fun testInfixAsPostfix() = doTest("""
        \func \infix 4 ++ {-caret-}(n m : Nat) => n Nat.+ m
        
        \func zoo => `++ 1
    """, """
        \func \infix 4 ++ {n m : Nat} => n Nat.+ m
        
        \func zoo => \lam n => ++ {n} {1}
    """)

    fun testFunctionIESaveFullNames() = doTest(
        """
        \func f {A B : \Type} ({-caret-}a : A) (b : B) => a
        \func test (a b : Nat) => (f 1 2 Nat.+ 42) Nat.* 43
        """,
        """
        \func f {A B : \Type} {{-caret-}a : A} (b : B) => a
        \func test (a b : Nat) => (f {_} {_} {1} 2 Nat.+ 42) Nat.* 43
        """
    )

    fun testFunctionIEToPrefix() = doTest(
        """
        \func mp (A : \Type) {{-caret-}B : \Type} (a : A) (ab : A -> B) => ab a
        \func g => (Nat `mp` 42) (\lam n => n + 1)
        """,
        """
        \func mp (A B : \Type) (a : A) (ab : A -> B) => ab a
        \func g => (mp Nat _ 42) (\lam n => n + 1)
        """
    )

    fun testFunctionIEInfixWithImplicit() = doTest(
        """
        \func f {A B : \Type} (a : A) {{-caret-}C : \Type} (b : B) (c : C) => (a, (b, c))
        \func g => (1 {-foo-} `f` {-bar-} {_} {-bar2-} {_} {-baz-} 2) 3
        """,
        """
        \func f {A B : \Type} (a : A) ({-caret-}C : \Type) (b : B) (c : C) => (a, (b, c))
        \func g => (f {-bar-} {-bar2-} {-foo-} 1 _ {-baz-} 2) 3
        """
    )

    fun testInfixIEWithImplicit1() = doTest(
        """
        \func \infix 6 !+! {{-caret-}A B : \Type} (a : A) (b : B) => (a, b)
        \func g => 1 !+! {Nat} 2
        """,
        """
        \func \infix 6 !+! ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => (a, b)
        \func g => !+! Nat 1 2
        """
    )

    fun testInfixIEWithImplicit2() = doTest(
        """
        \func \infix 6 !+! {A {-caret-}B : \Type} (a : A) (b : B) => (a, b)
        \func g => 1 !+! {Nat} 2
        """,
        """
        \func \infix 6 !+! {A : \Type} (B : \Type) (a : A) (b : B) => (a, b)
        \func g => !+! {Nat} _ 1 2
        """
    )

    fun testFunctionIEPair() = doTest(
        """
        \func id {A{-caret-} : \Type} (a : A) => a
        \func g => (id 42, id {Nat} 43)
        """,
        """
        \func id (A{-caret-} : \Type) (a : A) => a
        \func g => (id _ 42, id Nat 43)
        """
    )

    fun testFunctionIENested() = doTest(
        """
        \func p {A{-caret-} : \Type} (a : A) => (a, a)
        \func g => (p (p 42), p 42)
        """,
        """
        \func p (A{-caret-} : \Type) (a : A) => (a, a)
        \func g => (p _ (p _ 42), p _ 42)
        """
    )

    fun testOperatorIEPair() = doTest(
        """
        \func \infix 6 !+! {{-caret-}A B : \Type} (a : A) (b : B) => (a, b)
        \func g => (1 !+! 2, 3 !+! {-foo-} {Nat} {-bar-} 4)
        """,
        """
        \func \infix 6 !+! ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => (a, b)
        \func g => (!+! _ 1 2, !+! {-foo-} Nat 3 {-bar-} 4)
        """
    )

    fun testInfixIEMixStyle() = doTest(
        """
        \func p {A{-caret-} : \Type} {B : \Type} (a : A) (b : B) => (a, b)
        \func g => p 1 (p 2 (3 `p` {Nat} (p {_} 4 5)))
        """,
        """
        \func p (A{-caret-} : \Type) {B : \Type} (a : A) (b : B) => (a, b)
        \func g => p _ 1 (p _ 2 (p Nat 3 (p _ 4 5)))
        """
    )

    fun testOperatorIEInfixr() = doTest(
        """
        \func \infixr 6 !+! {{-caret-}A B : \Type} (a : A) (b : B) => (a, b)
        \func g => 1 !+! (2 Nat.+ 3) !+! 4
        """,
        """
        \func \infixr 6 !+! ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => (a, b)
        \func g => !+! _ 1 (!+! _ (2 Nat.+ 3) 4)
        """
    )

    fun testOperatorIEInfixl() = doTest(
        """
        \func \infixl 6 !+! {{-caret-}A B : \Type} (a : A) (b : B) => (a, b)
        \func g => 1 !+! (2 Nat.+ 3) !+! 4
        """,
        """
        \func \infixl 6 !+! ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => (a, b)
        \func g => !+! _ (!+! _ 1 (2 Nat.+ 3)) 4
        """
    )

    fun testPartialAppIESimple() = doTest(
        """
        \func f {A {-caret-}B : \Type} (a : A) (b : B) => (a, b)
        \func id (a : \Pi {B : \Set} -> Nat -> B -> \Sigma Nat B ) => a
        \func g => id (f {Nat})
        """,
        """
        \func f {A : \Type} (B : \Type) (a : A) (b : B) => (a, b)
        \func id (a : \Pi {B : \Set} -> Nat -> B -> \Sigma Nat B ) => a
        \func g => id (\lam {B} => f {Nat} B)
        """
    )

    fun testPartialAppIENotToLambda() = doTest(
        """
        \func f {{-caret-}A B : \Type} (a : A) (b : B) => (a, b)
        \func g => f {Nat}
        """,
        """
        \func f ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => (a, b)
        \func g => f Nat
        """
    )

    fun testPartialAppEISimple() = doTest(
        """
        \func f (A {-caret-}B : \Type) (a : A) (b : B) => (a, b)
        \func g => f Nat
        """,
        """
        \func f (A : \Type) {B : \Type} (a : A) (b : B) => (a, b)
        \func g => \lam B => f Nat {B}
        """
    )

    fun testPartialAppEINoArguments() = doTest(
        """
        \func p (a {-caret-}b : Nat) => a
        \func test (f : (Nat -> Nat -> Nat) -> Nat) => f p
        """,
        """
        \func p (a : Nat) {b : Nat} => a
        \func test (f : (Nat -> Nat -> Nat) -> Nat) => f (\lam a b => p a {b})
        """
    )

    fun testPartialAppIEOperator() = doTest(
        """
        \func \infixl 6 !+! {A B : \Type} (a : A) ({-caret-}b : B) => (a, b)
        \func g => 1 !+! {Nat} {Nat}
        """,
        """
        \func \infixl 6 !+! {A B : \Type} (a : A) {{-caret-}b : B} => (a, b)
        \func g => \lam b => !+! {Nat} {Nat} 1 {b}
        """
    )

    fun testPartialAppIEField() = doTest(
        """
        \class Test {A {-caret-}B : \Type} (a : A) (b : B)
        \func f => Test {Nat}
        """,
        """
        \class Test {A : \Type} (B : \Type) (a : A) (b : B)
        \func f => Test {Nat}
        """
    )

    fun testPartialAppEIFreshNames() = doTest(
        """
        \func foo (a {-caret-}b b1 : Nat) => a
        \func test => \lam (a : Nat) (b : Nat) (b1 : Nat) => foo 0
        """,
        """
        \func foo (a : Nat) {b : Nat} (b1 : Nat) => a
        \func test => \lam (a : Nat) (b : Nat) (b1 : Nat) => \lam b2 => foo 0 {b2}
        """
    )

    fun testInfixIEArgumentsIsAppExpr() = doTest(
        """
        \func mp {A B : \Type} (a : A) (b : B) => (a, b)

        \func \infixl 6 <!> {{-caret-}A B : \Type} (p1 p2 : \Sigma A B) => (p1.1, p2.2)

        \func test => mp (1 Nat.+ 2) 3 <!> mp 4 (5 Nat.* 6)
        """,
        """
        \func mp {A B : \Type} (a : A) (b : B) => (a, b)

        \func \infixl 6 <!> ({-caret-}A : \Type) {B : \Type} (p1 p2 : \Sigma A B) => (p1.1, p2.2)

        \func test => <!> _ (mp (1 Nat.+ 2) 3) (mp 4 (5 Nat.* 6))
        """
    )

    fun testInfixEISaveInfix() = doTest(
        """
        \func f ({-caret-}a b : Nat) => a
        \func test => f 1 2 Nat.+ f 1 3 Nat.+ 1
        """,
        """
        \func f {{-caret-}a : Nat} (b : Nat) => a
        \func test => f {1} 2 Nat.+ f {1} 3 Nat.+ 1
        """
    )

    fun testInfixEIPreserveArguments() = doTest("""
       \func foo (n : Nat) : n = n => idp

       \func \infixr 9 *> {A : \Type} {a a' a'' : A} ({-caret-}p : a = a') (q : a' = a'') : a = a'' \elim q | idp => p

       \func lol => (foo 1 *> idp)
    """, """
       \func foo (n : Nat) : n = n => idp

       \func \infixr 9 *> {A : \Type} {a a' a'' : A} {p : a = a'} (q : a' = a'') : a = a'' \elim q | idp => p

       \func lol => (*> {_} {_} {_} {_} {foo 1} idp) 
    """)

    fun testFunctionEINested() = doTest(
        """
        \func \infixl 7 <> {A B : \Type} (a : A) (b : B) => a

        \func k {{-caret-}A B : \Type} (a : A) (b : B) => (a, b)

        \func test => k (k 0 1 <> k 2 3) 4
        """,
        """
        \func \infixl 7 <> {A B : \Type} (a : A) (b : B) => a

        \func k ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => (a, b)

        \func test => k _ (k _ 0 1 <> k _ 2 3) 4
        """
    )

    fun testImplicitLambdas() = doTest(
        """
           \func foo ({-caret-}f : \Sigma Nat Nat -> Nat) => f (1, 1)

           \func zoo (p : \Sigma Nat Nat) : Nat => foo __.1           
        """, """
           \func foo {f : \Sigma Nat Nat -> Nat} => f (1, 1)

           \func zoo (p : \Sigma Nat Nat) : Nat => foo {__.1}
        """)

    fun testImplicitLambdas2() = doTest(
        """
           \func foo ({-caret-}f : \Sigma Nat Nat -> Nat) => f (1, 1)

           \func zoo (p : \Sigma Nat Nat) (Z : \Sigma Nat Nat -> \Sigma Nat Nat) : Nat => foo (Z __).1
        """, """
           \func foo {f : \Sigma Nat Nat -> Nat} => f (1, 1)

           \func zoo (p : \Sigma Nat Nat) (Z : \Sigma Nat Nat -> \Sigma Nat Nat) : Nat => foo {(Z __).1} 
        """
    )

    fun testImplicitLambdas3() = doTest(
        """
           \func foo ({-caret-}f : \Sigma Nat Nat -> Nat) : Nat => f (1, 1)

           \func zoo (p : \Sigma Nat Nat) : \Pi  (\Sigma Nat Nat -> Nat) -> Nat => \case foo __ \with {
             | 0 => 1
             | suc n => 2
           }
        """, """
           \func foo {f : \Sigma Nat Nat -> Nat} : Nat => f (1, 1)

           \func zoo (p : \Sigma Nat Nat) : \Pi  (\Sigma Nat Nat -> Nat) -> Nat => \case foo {__} \with {
             | 0 => 1
             | suc n => 2
           } 
        """
    )

    fun testImplicitLambdas4() = doTest(
        """
           \func foo ({-caret-}a : Nat -> Nat) (b : Nat) : Nat => a b

           \func zoo : Nat -> Nat => foo (foo (\lam x => x) __) __ 
        """, """
           \func foo {a : Nat -> Nat} (b : Nat) : Nat => a b

           \func zoo : Nat -> Nat => foo {foo {\lam x => x} __} __ 
        """)

    fun testExplicitLambdas() = doTest(
        """
           \func foo ({-caret-}f : \Sigma Nat Nat -> Nat) => f (1, 1)

           \func zoo (p : \Sigma Nat Nat) : Nat => foo (\lam x => x.1)            
        """, """
           \func foo {f : \Sigma Nat Nat -> Nat} => f (1, 1)

           \func zoo (p : \Sigma Nat Nat) : Nat => foo {\lam x => x.1}
        """)

    fun testImplicitArguments() = doTest("""
       \func pmap {-caret-}{A B : \Type} {f : A -> B} {a a' : A} (p : a = a') : f a = f a' => path (\lam i => f (p @ i))

       \func \infixr 9 *> {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \elim q | idp => p

       \func foo {A : \Type} (a : A) (h : \Pi (a : A) -> a = a) (p : idp = h a *> h a) => pmap {_} {_} {h a *>} p 
    """, """
       \func pmap (A B : \Type) {f : A -> B} {a a' : A} (p : a = a') : f a = f a' => path (\lam i => f (p @ i))

       \func \infixr 9 *> {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \elim q | idp => p

       \func foo {A : \Type} (a : A) (h : \Pi (a : A) -> a = a) (p : idp = h a *> h a) => pmap _ _ {h a *>} p 
    """)

    fun testParenthesizedBinOp() = doTest("""
       \open Nat (+)

       \func pmap2 {A B C : \Type} (f : A -> B -> C) {a a' : A} (p : a = a') {b b' : B} ({-caret-}q : b = b') : f a b = f a' b'
         => path (\lam i => f (p @ i) (q @ i))

       \func inv {A : \Type} {a a' : A} (p : a = a') : a' = a \elim p
         | idp => idp

       \func +-assoc {x y z : Nat} : (x + y) + z = x + (y + z) => {?}

       \func foo (x y z : Nat) : x + (y + z) + (x + (y + z)) = x + y + z + (x + y + z)
         => inv (pmap2 (+) +-assoc +-assoc)
         
       \func foo2 (x y z : Nat) : x + y + z + (x + y + z) = x + (y + z) + (x + (y + z))
         => pmap2 (+) +-assoc +-assoc   
    """, """
       \open Nat (+)

       \func pmap2 {A B C : \Type} (f : A -> B -> C) {a a' : A} (p : a = a') {b b' : B} {q : b = b'} : f a b = f a' b'
         => path (\lam i => f (p @ i) (q @ i))

       \func inv {A : \Type} {a a' : A} (p : a = a') : a' = a \elim p
         | idp => idp

       \func +-assoc {x y z : Nat} : (x + y) + z = x + (y + z) => {?}

       \func foo (x y z : Nat) : x + (y + z) + (x + (y + z)) = x + y + z + (x + y + z)
         => inv (pmap2 (+) +-assoc {_} {_} {+-assoc})
       
       \func foo2 (x y z : Nat) : x + y + z + (x + y + z) = x + (y + z) + (x + (y + z))
         => pmap2 (+) +-assoc {_} {_} {+-assoc}   
    """)

    fun testData() = doTest("""
       \data D ({-caret-}a : Nat)
         | con (n : Nat)

       \func foo : D 0 => con 3 
    """, """
       \data D {a : Nat}
         | con (n : Nat)

       \func foo : D {0} => con 3 
    """)

    fun testDataConstructor() = doTest("""
       \data TruncP (A : \Type)
         | inP A
         | truncP (a a' : TruncP A) ({-caret-}i : I) \elim i {
           | left  => a
           | right => a'
       } \where {
         \use \level levelProp {A : \Type} (a a' : TruncP A) : a = a' => path (truncP a a')
       }
    """, """
       \data TruncP (A : \Type)
         | inP A
         | truncP (a a' : TruncP A) {i : I} \elim i {
           | left  => a
           | right => a'
       } \where {
         \use \level levelProp {A : \Type} (a a' : TruncP A) : a = a' => path (\lam i => truncP a a' {i})
       } 
    """)

    fun testDataConstructorClause() = doTest("""
       \data \infix 4 < (n m : Nat) \with
         | 0, suc _ => zero<suc
         | suc n, suc m => suc<suc ({-caret-}n < m)

       \func lol : 1 < 2 => suc<suc (zero<suc {0}) 
    """, """
       \data \infix 4 < (n m : Nat) \with
         | 0, suc _ => zero<suc
         | suc n, suc m => suc<suc {n < m}

       \func lol : 1 < 2 => suc<suc {_} {_} {zero<suc {0}} 
    """)

    fun testDataConstructorClause2() = doTest("""
       \data foo (n m : Nat) \with
         | 0, suc _ => cons1
         | suc n, suc m => cons2 (foo n m) ({-caret-}i : I)

       \func lol => path (cons2 (cons1 {1}))
    """, """
       \data foo (n m : Nat) \with
         | 0, suc _ => cons1
         | suc n, suc m => cons2 (foo n m) {i : I}

       \func lol => path (\lam i => cons2 (cons1 {1}) {i})         
    """)

    fun testPatterns() = doTest("""        
       \data \infix 4 < (n m : Nat) \with
         | 0, suc _ => zero<suc
         | suc n, suc m => suc<suc {k : Nat} ({-caret-}n < m)

       \func foo {n m : Nat} (p : n < suc m) : Nat \elim n, m, p
         | suc n, 0, suc<suc {-foo-} ()
         | suc (suc n), suc (suc m), suc<suc (suc<suc q) => 2
         | _, _, _ => 3
    """, """        
       \data \infix 4 < (n m : Nat) \with
         | 0, suc _ => zero<suc
         | suc n, suc m => suc<suc {k : Nat} {n < m}

       \func foo {n m : Nat} (p : n < suc m) : Nat \elim n, m, p
         | suc n, 0, suc<suc {_} {-foo-} {()}
         | suc (suc n), suc (suc m), suc<suc {_} {suc<suc {_} {q}} => 2
         | _, _, _ => 3
    """)

    fun testRangeForConcrete() = doTest("""
       \open Nat(+)

       \func \infix 2 ==< {A : \Type} (a : A) {a' : A} (p : a = a') => p

       \func \infixr 1 >== {A : \Type} {a a' a'' : A} (p : a = a') ({-caret-}q : a' = a'') : a = a'' \elim q
         | idp => p

       \func \fix 2 qed {A : \Type} (a : A) : a = a => idp  

       \func foo (x : Nat) =>
         0 + x  ==< idp >==
         0 + x  ==< idp >==
         0 + x  ==< idp >==
         0 + x `qed
    """, """
       \open Nat(+)

       \func \infix 2 ==< {A : \Type} (a : A) {a' : A} (p : a = a') => p

       \func \infixr 1 >== {A : \Type} {a a' a'' : A} (p : a = a') {q : a' = a''} : a = a'' \elim q
         | idp => p

       \func \fix 2 qed {A : \Type} (a : A) : a = a => idp  

       \func foo (x : Nat) => 
         >== (0 + x  ==< idp) 
         {>== (0 + x  ==< idp) 
         {>== (0 + x  ==< idp) 
         {0 + x `qed}}}
    """)

    fun testBrackets() = doTest("""
       \func \infixr 9 *> {A : \Type} {a a' a'' : A} {{-caret-}p : a = a'} (q : a' = a'') : a = a'' \elim q
         | idp => p

       \func foo {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') => *> {_} {_} {_} {_} {p} q 
    """, """
       \func \infixr 9 *> {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \elim q
         | idp => p

       \func foo {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') => p *> q 
    """)

    fun testBrackets2() = doTest("""
       \func \infixr 9 *> {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \elim q | idp => p

       \func \infixr 1 >== {A : \Type} {a a' a'' : A} ({-caret-}p : a = a') (q : a' = a'') => p *> q

       \func foo (p : 1 = 1) => (p *> p) *> (p *> p) >== p 
    """, """
       \func \infixr 9 *> {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \elim q | idp => p

       \func \infixr 1 >== {A : \Type} {a a' a'' : A} {p : a = a'} (q : a' = a'') => p *> q

       \func foo (p : 1 = 1) => >== {_} {_} {_} {_} {(p *> p) *> (p *> p)} p  
    """)

    fun testBrackets3() = doTest("""
       \func \infixr 9 *> {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \elim q | idp => p
       \func pmap {A B : \Type} (f : A -> B) {a {-caret-}a' : A} (p : a = a') : f a = f a' => path (\lam i => f (p @ i))
       \func foo {a b : Nat}: a Nat.+ a = b Nat.+ b => idp

       \lemma pred-right {x n : Nat} : x Nat.+ x = x Nat.+ x =>
         foo {x} {n} *> pmap (\lam x => x) foo
    """, """
       \func \infixr 9 *> {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') : a = a'' \elim q | idp => p
       \func pmap {A B : \Type} (f : A -> B) {a : A} (a' : A) (p : a = a') : f a = f a' => path (\lam i => f (p @ i))
       \func foo {a b : Nat}: a Nat.+ a = b Nat.+ b => idp

       \lemma pred-right {x n : Nat} : x Nat.+ x = x Nat.+ x =>
         foo {x} {n} *> pmap (\lam x => x) _ foo 
    """)

    fun testBrackets4() = doTest("""
       \class C | F {Nat} Nat {Nat} : Nat

       \func \infixl 9 ++ ({-caret-}a b : Nat) => a Nat.+ b

       \func foo {A B : \Type} (f : A -> B) (x : C) => x.F {1} 2 {3} ++ 2
    """, """
       \class C | F {Nat} Nat {Nat} : Nat
       
       \func \infixl 9 ++ {a : Nat} (b : Nat) => a Nat.+ b
       
       \func foo {A B : \Type} (f : A -> B) (x : C) => ++ {x.F {1} 2 {3}} 2 
    """)

    fun testBrackets5() = doTest("""
       \func \infixl 6 ++ (a b : Nat) => a Nat.+ b
       
       \func \infixl 7 ** ({-caret-}a b : Nat) => a Nat.+ b
       
       \func foo => 1 ** 2 ++ 3 ** 4
    """, """
       \func \infixl 6 ++ (a b : Nat) => a Nat.+ b
       
       \func \infixl 7 ** {a : Nat} (b : Nat) => a Nat.+ b
       
       \func foo => ** {1} 2 ++ (**) {3} 4 
    """)

    fun testBrackets6() = doTest("""
       \func \infixr 9 *> {A : \Type} {a a' a'' : A} ({-caret-}p : a = a') (q : a' = a'') : a = a'' \elim q | idp => p

       \func \infixr 1 >== {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') => p *> q

       \func foo (x : Nat) (p : x = x) => p *> p >== p *> p >== p 
    """, """
       \func \infixr 9 *> {A : \Type} {a a' a'' : A} {p : a = a'} (q : a' = a'') : a = a'' \elim q | idp => p

       \func \infixr 1 >== {A : \Type} {a a' a'' : A} (p : a = a') (q : a' = a'') => *> {_} {_} {_} {_} {p} q

       \func foo (x : Nat) (p : x = x) => *> {_} {_} {_} {_} {p} p >== (*>) {_} {_} {_} {_} {p} p >== p 
    """)

    fun testCommentsAndWhitespace() = doTest("""
       \func \infixl 1 ++ ({-caret-}a b : Nat) => a Nat.+ b
       
       \func foo => (1 {-1-}
       ++ {-2-} 2, ++ {-1-} 1 {-2-} 2)
    """, """
       \func \infixl 1 ++ {a : Nat} (b : Nat) => a Nat.+ b
       
       \func foo => (++ {-1-}
       {1} {-2-} 2, ++ {-1-} {1} {-2-} 2)
    """)

    fun testBrackets7() = doTest("""
       \func \infixl 5 ++ ({-caret-}a b : Nat) => Nat.+ a b 

       \func foo (a : \Sigma Nat Nat) => a.1 ++ a.2 
    """, """
       \func \infixl 5 ++ {a : Nat} (b : Nat) => Nat.+ a b 

       \func foo (a : \Sigma Nat Nat) => ++ {a.1} a.2  
    """)

    fun testBrackets8() = doTest("""
       \func \infixl 6 ++ (a b : Nat -> Nat) => a 1 Nat.+ b 1 
 
       \func \infixl 7 ** (a : Nat) ({-caret-}b : Nat) => a Nat.+ b 
 
       \func foo => ** 1 ++ (**) 3 
    """, """
       \func \infixl 6 ++ (a b : Nat -> Nat) => a 1 Nat.+ b 1

       \func \infixl 7 ** (a : Nat) {b : Nat} => a Nat.+ b

       \func foo => (\lam b => ** 1 {b}) ++ (\lam b => ** 3 {b}) 
    """)

    fun testBrackets9() = doTest("""
       \class Monoid ({-caret-}E : \Set) | \infixl 1 op : E -> E -> E
       
       \func foo (M : Monoid) => Monoid M.E (M.op) 
    """, """
       \class Monoid {E : \Set} | \infixl 1 op : E -> E -> E
       
       \func foo (M : Monoid) => Monoid {M.E} (M.op) 
    """)

    fun testRecord() = doTest("""
       \record R {
         \func foo {{-caret-}x : Nat} => x
       }

       \func bar (r : R) => r.foo {0} 
    """, """
       \record R {
         \func foo (x : Nat) => x
       }

       \func bar (r : R) => r.foo 0  
    """)

    /* fun testRecord2() = doTest("""
       \record R {
         \func foo ({-caret-}x y : Nat) => x
       }

       \func bar (r : R) => 0 r.`foo` 1
    """, """
       \record R {
         \func foo {x : Nat} (y : Nat) => x
       }

       \func bar (r : R) => r.foo {0} 1
    """) */ //Fixme

    fun testInfixNotation() = doTest("""
       \func \infixl 1 foo {{-caret-}A : \Type} (x : Nat) => x Nat.+ x

       \func bar => 0 foo {Nat} 
    """, """
       \func \infixl 1 foo (A : \Type) (x : Nat) => x Nat.+ x

       \func bar => Nat foo 0 
    """)

    fun testBug() = doTest("""
       \func modular-function {n : Nat} ({-caret-}C : Fin n -> \Type) (f g : \Pi (i : Fin n) -> C i) (delim : Fin (suc n)) (i : Fin n) : C i => {?} \where {
         \func pure-left-modular {n : Nat} (C : Fin n -> \Type) (f g : \Pi (i : Fin n) -> C i) : modular-function C f g 0 = f => {?}
       } 
    """, """
       \func modular-function {n : Nat} {C : Fin n -> \Type} (f g : \Pi (i : Fin n) -> C i) (delim : Fin (suc n)) (i : Fin n) : C i => {?} \where {
         \func pure-left-modular {n : Nat} (C : Fin n -> \Type) (f g : \Pi (i : Fin n) -> C i) : modular-function {_} {C} f g 0 = f => {?}
       } 
    """)

    fun testBug2() = doTest("""
       \record R {
         \func test => foo 1
           \where {
           \func foo ({-caret-}x : Nat) => x
         }
       } 
    """, """
       \record R {
         \func test => foo {_} {1}
           \where {
           \func foo {x : Nat} => x
         }
       } 
    """)

    fun testTypeTele() = doTest("""
       \data N | z | s N{-caret-}
  
       \func foo (n : N) : Nat \with {
         | z => 0
         | s n => suc (foo n)
        }
    """, """
       \data N | z | s {N}
  
       \func foo (n : N) : Nat \with {
         | z => 0
         | s {n} => suc (foo n)
        }
    """)

    fun testInfixPatterns() = doTest("""
       \data tree | nil | \infixl 1 :: (t{-caret-}1 t2 : tree)

       \func match (t : tree) : tree 
         | nil => nil
         | nil :: t => nil :: t
         | t1 :: t2 :: t3 => t1 :: t2 :: t3
    """, """
       \data tree | nil | \infixl 1 :: {t1 : tree} (t2 : tree)

       \func match (t : tree) : tree
         | nil => nil
         | :: {nil} t => :: {nil} t
         | :: {:: {t1} t2} t3 => :: {:: {t1} t2} t3
    """)

    fun testInfixPatterns2() = doTest("""
       \data tree | nil | \infixl 1 :: (x : tree) ({-caret-}y : tree)

       \func match (t : tree) : tree
         | nil => nil
         | nil :: t => t
         | (t1 :: t2 \as tt) :: t3 \as t => tt 
    """, """
       \data tree | nil | \infixl 1 :: (x : tree) {y : tree}

       \func match (t : tree) : tree
         | nil => nil
         | :: nil {t} => t
         | :: (:: t1 {t2} \as tt) {t3} \as t => tt 
    """)

    fun testInfixPatterns3() = doTest("""
       \data tree | nil | \infixl 1 :: {{-caret-}x : tree} (y : tree)

       \func match (t : tree) : tree         
         | :: {nil} _ => nil
    """, """
       \data tree | nil | \infixl 1 :: (x y : tree)

       \func match (t : tree) : tree         
         | nil :: _ => nil        
    """)

    fun testInfixPatterns4() = doTest("""
       \data tree | nil | \infixl 1 :: {x : tree} ({-caret-}y : tree)

       \func match (t : tree) : tree
         | :: {nil} _ => nil
         | nil => {?}
         | :: {:: t1} t2 => {?} 
    """, """
       \data tree | nil | \infixl 1 :: {x y : tree}

       \func match (t : tree) : tree
         | :: {nil} => nil
         | nil => {?}
         | :: {:: {_} {t1}} {t2} => {?} 
    """)

    fun testInfixPatterns5() = doTest("""
       \data tree | nil | \infixl 1 :: {{-caret-}x : tree} {y : tree}

       \func match (t : tree) : tree
         | :: {nil} => nil
         | nil => {?}
         | :: {:: {_} {t1}} {t2} => {?}
    """, """
       \data tree | nil | \infixl 1 :: (x : tree) {y : tree}

       \func match (t : tree) : tree
         | :: nil => nil
         | nil => {?}
         | :: (:: _ {t1}) {t2} => {?}
    """)

    fun testInfixPatterns6() = doTest("""
       \data tree | nil | :: {{-caret-}a : \Sigma Nat Nat} (b : tree)

       \func foo (t : tree) : Nat
         | nil => 0
         | :: {(n, m)} b => 1 
    """, """
       \data tree | nil | :: (a : \Sigma Nat Nat) (b : tree)

       \func foo (t : tree) : Nat
         | nil => 0
         | :: (n, m) b => 1 
    """)

    fun testInfixPatterns7() = doTest("""
       \data tree | nil | :: {a : tree} (b : tree) | ** ({-caret-}c d : tree)

       \func f (t : tree) : tree
         | :: {** c a1} t => :: {** c a1} t
       """, """
       \data tree | nil | :: {a : tree} (b : tree) | ** {c : tree} (d : tree)

       \func f (t : tree) : tree
         | :: {** {c} a1} t => :: {** {c} a1} t
       """)

    fun testInfixPatterns8() = doTest("""
       \data tree | nil | :: {{-caret-}a : tree} (b : tree) | ** (c d : tree)

       \func f {t : tree} : tree
         | {:: {** c a1} t} => :: {** c a1} t 
    """, """
       \data tree | nil | :: (a b : tree) | ** (c d : tree)
         
       \func f {t : tree} : tree
         | {:: (** c a1) t} => :: (** c a1) t 
    """)

    fun testInfixPatterns9() = doTest(
        """
           \data tree | nil | \infixl 1 :: {x : tree} ({-caret-}y : tree) | \infixl 2 ++ (x : tree) {y : tree}

           \func match (t : tree) : tree
             | :: {++ t {:: {t1} t3}} t2 => t3 
        """, """
           \data tree | nil | \infixl 1 :: {x y : tree} | \infixl 2 ++ (x : tree) {y : tree}

           \func match (t : tree) : tree
             | :: {++ t {:: {t1} {t3}}} {t2} => t3
        """
    )

    fun testInfixPatterns10() = doTest("""
       \data tree | nil | \infixl 2 ++ (x : tree) {{-caret-}y : tree}

       \func match (t : tree) : tree
         | ++ t => {?} 
    """, """
       \data tree | nil | \infixl 2 ++ (x y : tree)

       \func match (t : tree) : tree
         | ++ t _ => {?} 
    """)

    fun testInfixPatterns11() = doTest("""
       \data tree | nil | \infixl 1 :: ({-caret-}z : tree) (w : tree)

       \func match {t : tree} : tree
         | {t1 :: t2 :: t3} => t1
    """, """
       \data tree | nil | \infixl 1 :: {z : tree} (w : tree)

       \func match {t : tree} : tree
         | {:: {:: {t1} t2} t3} => t1 
    """)

    fun testElim() = doTest("""
       \func foo ({-caret-}a : Nat) (b : Nat) : Nat
         | 0, 0 => 0
         | 0, suc b => 1
         | suc a, 0 => 2
         | suc a, suc b => 3 
    """, """
       \func foo {a : Nat} (b : Nat) : Nat \with
         | {0}, 0 => 0
         | {0}, suc b => 1
         | {suc a}, 0 => 2
         | {suc a}, suc b => 3 
    """)

    fun testElim2() = doTest(""" 
        \func foo (a b : {-caret-}Nat) : Nat
         | 0, 0 => 0
         | 0, suc b => 1
         | suc a, 0 => 2
         | suc a, suc b => 3
    """, """
        \func foo {a b : Nat} : Nat \with
         | {0}, {0} => 0
         | {0}, {suc b} => 1
         | {suc a}, {0} => 2
         | {suc a}, {suc b} => 3 
    """)

    fun testTqInPattern() = doTest("""
       \class Lol {{-caret-}X Y : \Type} {foo : X -> Y}

       \func lol {X : \Type} (l : Lol {X}) : Nat \elim X, l
         | X, e : Lol => 1
    """, """
       \class Lol (X : \Type) {Y : \Type} {foo : X -> Y}

       \func lol {X : \Type} (l : Lol X) : Nat \elim X, l
         | X, e : Lol => 1
    """)

    fun testConstructor() = doTest("""
       \data Foo (X : \Type)
         | nil
         | cons X{-caret-} (Foo X)

       \func lol => cons 1 nil 
    """, """
       \data Foo (X : \Type)
         | nil
         | cons {X} (Foo X)

       \func lol => cons {_} {1} nil 
    """)

    fun testProperty() = doTest("""
       \func foo (\property {-caret-}A : \Type) => 101 
    """, """
       \func foo {\property A : \Type} => 101 
    """)
}
