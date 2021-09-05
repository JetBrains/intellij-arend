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
        \func foo (a : Nat) {b{-caret-} : Nat} : D a b => con
        \func test : D 0 1 => foo _
        """
    )

    fun testFunctionIESaveUnderscore() = doTest(
        """
        \func kComb (A{-caret-} : \Type) {B : \Type} => \lam (a : A) (b : B) => a
        \func f => kComb _ {\Sigma Nat Nat} 1 (4, 2)
        """,
        """
        \func kComb {A{-caret-} : \Type} {B : \Type} => \lam (a : A) (b : B) => a
        \func f => kComb {_} {\Sigma Nat Nat} 1 (4, 2)
        """
    )

    fun testFunctionIESplitTele() = doTest(
        """
        \func k {A {-caret-}B : \Type} (a : A) (b : B) => a
        \func f => k {_} 1 (2, 3) Nat.+ k 4 5
        """,
        """
        \func k {A : \Type} (B{-caret-} : \Type) (a : A) (b : B) => a
        \func f => k {_} _ 1 (2, 3) Nat.+ k _ 4 5
        """
    )

    fun testFunctionIEAllTele1() = doTest(
        """
        \func k {A B : {-caret-}\Type} (a : A) (b : B) => a
        \func test => k 1 2 Nat.+ k 3 4
        """,
        """
        \func k (A B : \Type) {-caret-}(a : A) (b : B) => a
        \func test => k _ _ 1 2 Nat.+ k _ _ 3 4
        """
    )

    fun testFunctionEIAllTele() = doTest(
        """
        \func k (A B : {-caret-}\Type) (a : A) (b : B) => a
        \func test => k Nat _ 1 2 Nat.+ k _ Nat 3 4
        """,
        """
        \func k {A B : \Type} {-caret-}(a : A) (b : B) => a
        \func test => k {Nat} 1 2 Nat.+ k {_} {Nat} 3 4
        """
    )

    fun testFieldIESimple1() = doTest(
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test {\Sigma Nat Nat} (4, 2)
        """,
        """
        \class Test ({-caret-}X : \Type) (x : X)
        \func f => Test (\Sigma Nat Nat) (4, 2)
        """
    )

    fun testFieldEISimple2() = doTest(
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test (\Sigma Nat Nat) (4, 2)
        """,
        """
        \class Test {{-caret-}X : \Type} (x : X)
        \func f => Test {(\Sigma Nat Nat)} (4, 2)
        """
    )

    fun testFieldIESimple3() = doTest(
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test 42
        """,
        """
        \class Test ({-caret-}X : \Type) (x : X)
        \func f => Test _ 42
        """
    )

    fun testFieldEISimple4() = doTest(
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test _ 42
        """,
        """
        \class Test {{-caret-}X : \Type} (x : X)
        \func f => Test 42
        """
    )

    fun testFieldIEAllTele() = doTest(
        """
        \class Test (A B :{-caret-} \Type) (a : A) (b : B)
        \func test => Test Nat _ 4 2
        """,
        """
        \class Test {A B : \Type} {-caret-}(a : A) (b : B)
        \func test => Test {Nat} 4 2
        """
    )

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
          | k (A B : \Type) {-caret-}(a : A) (b : B) : A

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
          | k _ _ a b => a
        }
        """,
        """
        \record testRecord (T : \Type)
          | k {A B : \Type} {-caret-}(a : A) (b : B) : A

        \func h => \new testRecord (\Sigma Nat Nat) {
          | k a b => a
        }
        """
    )

    fun testInfixIESimple() = doTest(
        """
        \func mp {{-caret-}A B : \Type} (a : A) (b : B) => a
        \func g => 42 `mp` 41
        """,
        """
        \func mp ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => a
        \func g => mp _ 42 41
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
        \func f (a : Nat) {b{-caret-} : Nat} => a
        \func test => f 0 {1} + 2
        """
    )

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
        \func mp (A : \Type) ({-caret-}B : \Type) (a : A) (ab : A -> B) => ab a
        \func g => (mp Nat _ 42) (\lam n => n + 1)
        """
    )

    fun testFunctionIEInfixWithImplicit() = doTest(
        """
        \func f {A B : \Type} (a : A) {{-caret-}C : \Type} (b : B) (c : C) => (a, (b, c))
        \func g => (1 `f` {_} {_} 2) 3
        """,
        """
        \func f {A B : \Type} (a : A) ({-caret-}C : \Type) (b : B) (c : C) => (a, (b, c))
        \func g => (f {_} {_} 1 _ 2) 3
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
        \func \infix 6 !+! {A : \Type} (B{-caret-} : \Type) (a : A) (b : B) => (a, b)
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
        \func g => (1 !+! 2, 3 !+! {Nat} 4)
        """,
        """
        \func \infix 6 !+! ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => (a, b)
        \func g => (!+! _ 1 2, !+! Nat 3 4)
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
        \func id {A : \Type} (a : A) => a
        \func g => id (f {Nat})
        """,
        """
        \func f {A : \Type} (B{-caret-} : \Type) (a : A) (b : B) => (a, b)
        \func id {A : \Type} (a : A) => a
        \func g => id (\lam {B} a b => f {Nat} B a b)
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
        \func f (A : \Type) {B{-caret-} : \Type} (a : A) (b : B) => (a, b)
        \func g => (\lam B a b => f Nat {B} a b)
        """
    )

    fun testPartialAppEINoArguments() = doTest(
        """
        \func p (a {-caret-}b : Nat) => a
        \func test (f : (Nat -> Nat -> Nat) -> Nat) => f p
        """,
        """
        \func p (a : Nat) {b{-caret-} : Nat} => a
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
        \func g => (\lam b => !+! {Nat} {Nat} 1 {b})
        """
    )

    fun testPartialAppIEField() = doTest(
        """
        \class Test {A {-caret-}B : \Type} (a : A) (b : B)
        \func f => Test {Nat}
        """,
        """
        \class Test {A  : \Type} ({-caret-}B : \Type) (a : A) (b : B)
        \func f => (\lam {B} a b => Test {Nat} B a b)
        """
    )

    fun testPartialAppEIFreshNames() = doTest(
        """
        \func foo (a {-caret-}b b1 : Nat) => a
        \func test => \lam (a : Nat) (b : Nat) (b1 : Nat) => foo 0
        """,
        """
        \func foo (a : Nat) {b{-caret-} : Nat} (b1 : Nat) => a
        \func test => \lam (a : Nat) (b : Nat) (b1 : Nat) => (\lam b2 b3 => foo 0 {b2} b3)
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

    fun testFunctionEINested() = doTest(
        """
        \func \infixl 7 <> {A B : \Type} (a : A) (b : B) => a

        \func k {{-caret-}A B : \Type} (a : A) (b : B) => (a, b)

        \func test => k (k 0 1 <> k 2 3) 4
        """,
        """
        \func \infixl 7 <> {A B : \Type} (a : A) (b : B) => a

        \func k ({-caret-}A : \Type) {B : \Type} (a : A) (b : B) => (a, b)

        \func test => k _ ((k _ 0 1) <> (k _ 2 3)) 4
        """
    )
}
