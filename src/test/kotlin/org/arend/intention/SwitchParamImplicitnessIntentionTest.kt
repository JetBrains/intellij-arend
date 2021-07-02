package org.arend.intention

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle

class SwitchParamImplicitnessIntentionTest : QuickFixTestBase() {
    private val fixName = ArendBundle.message("arend.coClause.switchParamImplicitness")
    private fun doTest(contents: String, result: String) = simpleQuickFixTest(fixName, contents, result)

    fun testFunctionImToExRemoveBraces() = doTest(
        """
        \func f (a b : Int) {p : a = b} => 0
        \func g => f 1 1 {idp}
        """,
        """
        \func f (a b : Int) (p : a = b) => 0
        \func g => f 1 1 idp
        """
    )

    fun testFunctionExToImAddBraces() = doTest(
        """
        \func f (a b : Int) (p : a = b) => 0
        \func g => f 1 1 idp
        """,
        """
        \func f (a b : Int) {p : a = b} => 0
        \func g => f 1 1 {idp}
        """
    )

    fun testFunctionImToExAddParam() = doTest(
        """
        \func id {A : \Type} => \lam (x : A) => x
        \func g => id 1
        """,
        """
        \func id (A : \Type) => \lam (x : A) => x
        \func g => id _ 1
        """
    )

    // Param after underscore is implicit
    fun testFunctionExToImUnderscore() = doTest(
        """
        \func id (A : \Type) => \lam (x : A) => x
        \func g => id _ 1
        """,
        """
        \func id {A : \Type} => \lam (x : A) => x
        \func g => id 1
        """
    )

    // Param after underscore is explicit
    fun testFunctionImToExUnderscore() = doTest(
        """
        \func kComb (A : \Type) {B : \Type} => \lam (a : A) (b : B) => a
        \func f => kComb _ {\Sigma Nat Nat} 1 (4, 2)
        """,
        """
        \func kComb {A : \Type} {B : \Type} => \lam (a : A) (b : B) => a
        \func f => kComb {_} {\Sigma Nat Nat} 1 (4, 2) 
        """
    )
}
