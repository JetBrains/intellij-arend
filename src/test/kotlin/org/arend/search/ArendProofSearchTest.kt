package org.arend.search

import junit.framework.TestCase
import org.arend.ArendTestBase
import org.arend.search.proof.generateProofSearchResults
import org.intellij.lang.annotations.Language

const val PRE_TEXT =
    """
\func \infix 1 = {A : \Type} (a b : \Type) : \Type => {?}

\data Nat | zero | suc (n : Nat)

\func \infixl 3 + (a b : Nat) : Nat => {?}

\func bar : Nat -> Nat -> Nat => {?}

\module M \where {
  \func \infixl 4 ** (a b : Nat) : Nat => {?}
}

\module N \where {
  \func \infixl 4 ** (a b : Nat) : Nat => {?}
}

\func \infix 3 ^^ \alias upup : Nat -> Nat -> Nat => {?}

\func baz (a b : Nat) : a ^^ b = upup b a => {?}

"""

class ArendProofSearchTest : ArendTestBase() {

    private fun findMatches(
        @Language("Arend") content: String,
        pattern: String
    ): Set<String> {
        myFixture.addFileToProject("_.ard", PRE_TEXT + content)
        val results = generateProofSearchResults(project, pattern)
        return results.toList().mapTo(HashSet()) { it.def.name!! }
    }

    private fun assertHasMatch(content: String, pattern: String) = TestCase.assertTrue(
        "Expected some matches of $pattern", findMatches(content, pattern).isNotEmpty()
    )

    private fun assertHasNoMatch(content: String, pattern: String) = TestCase.assertTrue(
        "Expected no matches of $pattern", findMatches(content, pattern).isEmpty()
    )

    fun testBasicFunction() = assertHasMatch("\\func foo (a b : Nat) : a + b = b + a => {?}", "= (+ _ _) (+ _ _)")

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

    fun testNoMatches() = assertHasNoMatch("\\func foo (a b : Nat) : bar a b = bar b a => {?}", "_ ** _ = _ ** _")

    fun testNoMatchesInBody() = assertHasNoMatch("\\func foo : \\Type => a + b = b + a", "_ + _ = _ + _")

    fun testQualifiedName() = assertHasMatch("\\func foo (a b : Nat) : a M.** b = b M.** a => {?}", "_ ** _ = _ ** _")

    fun testQualifiedNameInPattern() = assertHasMatch("\\func foo (a b : Nat) : a M.** b = b M.** a => {?}", "_ M.** _ = _ M.** _")

    fun testIgnoreNamesFromOtherModules() = assertHasNoMatch("\\func foo (a b : Nat) : a N.** b = b N.** a => {?}", "_ M.** _ = _ M.** _")

    fun testMatchAliasByOriginal() = assertHasMatch("\\func foo (a b : Nat) : a ^^ b = upup b a => {?}", "_ ^^ _ = _ ^^ _")

    fun testMatchPureAlias() = assertHasMatch("\\func foo (a b : Nat) : upup a b = upup b a => {?}", "upup _ _ = upup _ _")

    fun testMatchOriginalByAlias() = assertHasMatch("\\func foo (a b : Nat) : a ^^ b = upup b a => {?}", "upup _ _ = upup _ _")

}