package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class SwitchParamImplicitnessIntentionTest : QuickFixTestBase() {
    private val fixName = ArendBundle.message("arend.coClause.switchParamImplicitness")
    private fun doTest(contents: String, result: String) = simpleQuickFixTest(fixName, contents, result)

    fun testFunctionImToExRemoveBraces() = doTest(
        """
        \func f (a b : Int) {p{-caret-} : a = b} => 0
        \func g => f 1 1 {idp}
        """,
        """
        \func f (a b : Int) (p{-caret-} : a = b) => 0
        \func g => f 1 1 idp
        """
    )

    fun testFunctionExToImAddBraces() = doTest(
        """
        \func f (a b : Int) (p{-caret-} : a = b) => 0
        \func g => f 1 1 idp
        """,
        """
        \func f (a b : Int) {p{-caret-} : a = b} => 0
        \func g => f 1 1 {idp}
        """
    )

    fun testFunctionImToExAddParam() = doTest(
        """
        \func id {A{-caret-} : \Type} => \lam (x : A) => x
        \func g => id 1
        """,
        """
        \func id (A{-caret-} : \Type) => \lam (x : A) => x
        \func g => id _ 1
        """
    )

    // Param after underscore is implicit
    fun testFunctionExToImUnderscore() = doTest(
        """
        \func id (A{-caret-} : \Type) => \lam (x : A) => x
        \func g => id _ 1
        """,
        """
        \func id {A{-caret-} : \Type} => \lam (x : A) => x
        \func g => id 1
        """
    )

    // Param after underscore is explicit
    fun testFunctionImToExUnderscore() = doTest(
        """
        \func kComb (A{-caret-} : \Type) {B : \Type} => \lam (a : A) (b : B) => a
        \func f => kComb _ {\Sigma Nat Nat} 1 (4, 2)
        """,
        """
        \func kComb {A{-caret-} : \Type} {B : \Type} => \lam (a : A) (b : B) => a
        \func f => kComb {_} {\Sigma Nat Nat} 1 (4, 2)
        """
    )

    fun testFunctionExToImParens() = doTest(
        """
        \func f (P{-caret-} : \Type) (p : P) => p
        \func g => f (\Sigma Nat Nat) (4, 2)
        """,
        """
        \func f {P{-caret-} : \Type} (p : P) => p
        \func g => f {(\Sigma Nat Nat)} (4, 2)
        """
    )

    fun testFieldImToExRemoveBraces() = doTest(
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test {\Sigma Nat Nat} (4, 2)
        """,
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test (\Sigma Nat Nat) (4, 2)
        """
    )

    fun testFieldExToImAddBraces() = doTest(
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test (\Sigma Nat Nat) (4, 2)
        """,
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test {(\Sigma Nat Nat)} (4, 2)
        """
    )

    fun testFieldImToExAddParam() = doTest(
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test 42
        """,
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test _ 42
        """
    )

    fun testFieldExToImUnderscore() = doTest(
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test _ 42
        """,
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test 42
        """
    )

    fun testFieldImToExUnderscore() = doTest(
        """
        \class Test {X{-caret-} : \Type} (x : X)
        \func f => Test 42
        """,
        """
        \class Test (X{-caret-} : \Type) (x : X)
        \func f => Test _ 42
        """
    )

    fun testTypeExToImAddBraces() = doTest(
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd (X{-caret-} : \Type) (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd Nat n => 2
        }
        """,
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd {X{-caret-} : \Type} (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd {Nat} n => 2
        }
        """
    )

    fun testTypeImToExRemoveBraces() = doTest(
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd {X{-caret-} : \Type} (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd {Nat} n => 2
        }
        """,
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd (X{-caret-} : \Type) (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd Nat n => 2
        }
        """
    )

    fun testTypeImToExAddParam() = doTest(
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd {X{-caret-} : \Type} (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd n => 2
        }
        """,
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd (X{-caret-} : \Type) (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd _ n => 2
        }
        """
    )

    fun testTypeExToImUnderscore() = doTest(
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd (X{-caret-} : \Type) (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd _ n => 2
        }
        """,
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd {X{-caret-} : \Type} (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd n => 2
        }
        """
    )

    fun testTypeImToExUnderscore() = doTest(
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd (X{-caret-} : \Type) (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd _ n => 2
        }
        """,
        """
        \record testRecord (T : \Type)
          | fst (X : T) : Nat
          | snd {X{-caret-} : \Type} (n : X) : Nat

        \func h => \new testRecord (\Sigma Nat Nat) {
          | fst X => 1
          | snd n => 2
        }
        """
    )
}
