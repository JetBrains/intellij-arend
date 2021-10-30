package org.arend.search.structural

import com.intellij.codeInsight.daemon.quickFix.LightQuickFixTestCase
import com.intellij.structuralsearch.MatchOptions
import com.intellij.structuralsearch.MatchResult
import com.intellij.structuralsearch.Matcher
import com.intellij.structuralsearch.impl.matcher.compiler.PatternCompiler
import junit.framework.TestCase
import org.arend.ArendFileType
import org.arend.ArendLanguage
import org.intellij.lang.annotations.Language

const val PRE_TEXT =
    """
\func \infix 1 = {A : \Type} (a b : \Type) : \Type => {?}

\data Nat | zero | suc (n : Nat)

\func \infixl 3 + (a b : Nat) : Nat => {?}
"""

/**
 * Copy of StructuralSearchTestCase. It is somewhy not exported as part of openapi.
 */
class ArendProofSearchTest : LightQuickFixTestCase() {

    private var options: MatchOptions = MatchOptions()

    private fun findMatches(
        @Language("Arend") content: String,
        pattern: String
    ): List<MatchResult> {

        options.fillSearchCriteria(pattern)
        options.setFileType(ArendFileType)
        options.dialect = ArendLanguage.INSTANCE
        val compiledPattern = PatternCompiler.compilePattern(project, options, true, false)
        val matcher = Matcher(project, options, compiledPattern)
        return matcher.testFindMatches(PRE_TEXT + content, true, ArendFileType, false)
    }

    private fun assertCount(@Language("Arend") content: String, pattern: String, count: Int) =
        TestCase.assertEquals(count, findMatches(content, pattern).size)


    fun testBasic() = assertCount(
        """
        \func foo (a b : Nat) : a + b = b + a => {?}
    """, "= (+ _ _) (+ _ _)", 1
    )


}