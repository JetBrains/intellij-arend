package org.arend.search

import junit.framework.TestCase
import org.arend.ArendTestBase
import org.arend.search.proof.generateProofSearchResults
import org.intellij.lang.annotations.Language


class ArendProofSearchTest : ArendTestBase() {

    private fun findMatches(
        @Language("Arend") content: String,
        pattern: String
    ): Set<String> {
        myFixture.addFileToProject("Main.ard", PRE_TEXT + content)
        typecheck()
        val results = generateProofSearchResults(project, pattern)
        return results.mapNotNull { it }.toList().mapTo(HashSet()) { it.def.name!! }
    }

    private fun assertHasMatch(content: String, pattern: String) = TestCase.assertTrue(
        "Expected some matches of $pattern", findMatches(content, pattern).isNotEmpty()
    )

    private fun assertHasNoMatch(content: String, pattern: String) = TestCase.assertTrue(
        "Expected no matches of $pattern", findMatches(content, pattern).isEmpty()
    )

    fun testBasicFunction() =
        assertHasMatch("\\func foo (a b : Nat) : a + b = b + a => {?}", "= (+ _ _) (+ _ _)")

    fun testBasicClass() = assertHasMatch(
        """
        \class Comm (A : \Type) (f : A -> A -> A) {
          | comm (x y : A) : f x y = f y x
        }
        
        \instance AddComm : Comm Nat (+) \cowith
          | comm (x y : Nat) : x + y = y + x => {?}
    """, "= (+ _ _) (+ _ _)")

    fun testInfixPattern() = assertHasMatch("\\func foo (a b : Nat) : a + b = b + a => {?}", "_ + _ = _ + _")

    fun testInfixPatternWithParens() = assertHasMatch("\\func foo (a b : Nat) : a + b = b + a => {?}", "(_ + _) = (_ + _)")

    fun testNoMatches() = assertHasNoMatch("""
        \func bar : Nat -> Nat -> Nat => {?}
        
        \func foo (a b : Nat) : bar a b = bar b a => {?}
        """, "_ ** _ = _ ** _")

    fun testNoMatchesInBody() = assertHasNoMatch("\\func foo : \\Type => a + b = b + a", "_ + _ = _ + _")

    fun testQualifiedName() = assertHasMatch("$MODULES\\func foo (a b : Nat) : a M.** b = b M.** a => {?}", "_ ** _ = _ ** _")

    fun testQualifiedNameInPattern() =
        assertHasMatch("$MODULES\\func foo (a b : Nat) : a M.** b = b M.** a => {?}", "_ M.** _ = _ M.** _")

    fun testIgnoreNamesFromOtherModules() =
        assertHasNoMatch("$MODULES\\func foo (a b : Nat) : a N.** b = b N.** a => {?}", "_ M.** _ = _ M.** _")

    fun testMatchAliasByOriginal() =
        assertHasMatch("$ALIASES\\func foo (a b : Nat) : a ^^ b = upup b a => {?}", "_ ^^ _ = _ ^^ _")

    fun testMatchPureAlias() =
        assertHasMatch("$ALIASES\\func foo (a b : Nat) : upup a b = upup b a => {?}", "upup _ _ = upup _ _")

    fun testMatchOriginalByAlias() =
        assertHasMatch("$ALIASES\\func foo (a b : Nat) : a ^^ b = upup b a => {?}", "upup _ _ = upup _ _")

    fun testConstructor() = assertHasMatch(
        """
        \data Listt | nil Nat
        
    """, "Listt"
    )

    fun testConstructor2() = assertHasMatch(
        """
\data List (A : \Type)
  | nil
  | cons A (List A)""", "List"
    )

    fun testImplicitArgument() = assertHasMatch(
        """
\func p {n : Nat} : \Prop => 1 = 1

\func foo : p {1} => idp""", "p {_}"
    )

    fun testSigma() = assertHasMatch(
        """
        \func p : \Sigma (1 = 1) Nat => {?}
        """, "Nat"
    )

    fun testPi() = assertHasMatch("""
        \func f : Nat -> Nat => {?}
    """, "Nat")

    fun testLet() = assertHasMatch("""
        \func f : \let x => 1 \in x = x => {?}
    """, "_ = _")

    fun testLetArgument() = assertHasMatch("""
        \func f : \let x => Nat \in x = x => {?}
    """, "Nat")

    fun testLambda() = assertHasMatch("""
        \func f : (\lam x => Nat) = (\lam x => Nat) => {?}
    """, "Nat")

    fun testClass() = assertHasMatch("""
        \class Foo {
          | foo : Nat
        }
    """, "Nat")

    fun testCurried() = assertHasMatch("""
        \func f (a b : Nat) : a + b = a => {?}
    """, "+ _")

    fun testParameter() = assertHasMatch("""
        \data Bool

        \func foo : Nat -> Bool => {?}
    """, "Nat -> Bool")

    fun testParameter2() = assertHasMatch("""
        \data Bool

        \func foo : Nat -> Bool => {?}
    """, "Nat")

    fun testParameter3() = assertHasMatch("""
        \data Bool

        \func foo (b : Nat) : Bool => {?}
    """, "Nat -> Bool")

    fun testSparseQualifier() = assertHasMatch("""
        \module A \where \module B \where \module C \where \data D
        
        \open A.B.C
         
        \func f : D => {?}
    """, "A.C.D")

    fun testPiExprClassField() = assertHasMatch("""
        \class Bar {
          | \infix 4 <= : Nat -> Nat -> \Prop
          | <=-antisymmetric {x y : Nat} : x <= y -> y <= x -> x = y
        }
    """, "_ <= _ -> _ <= _ -> _ = _")
}


const val PRE_TEXT =
    """
\open Nat

"""

const val MODULES = """
    
\module M \where {
  \func \infixl 4 ** (a b : Nat) : Nat => {?}
}

\module N \where {
  \func \infixl 4 ** (a b : Nat) : Nat => {?}
}

"""

const val ALIASES = """
    
\func \infix 3 ^^ \alias upup : Nat -> Nat -> Nat => {?}

\func baz (a b : Nat) : a ^^ b = upup b a => {?}

"""