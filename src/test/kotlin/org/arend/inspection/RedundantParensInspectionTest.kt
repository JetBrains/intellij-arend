package org.arend.inspection

import org.arend.quickfix.QuickFixTestBase
import org.arend.util.ArendBundle
import org.intellij.lang.annotations.Language

class RedundantParensInspectionTest : QuickFixTestBase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(RedundantParensInspection::class.java)
    }

    fun testNeverNeedsParens() = doTest()

    fun testNewExpression() = doTest()

    fun testReturnExpression() = doTest()

    fun testParameterType() = doTest()

    fun testBodyAndClauseAndCoClause() = doTest()

    fun testClausePattern() = doTest()

    fun testArrowExpression() = doTest()

    fun testSigmaExpression() = doTest()

    fun testPiExpression() = doTest()

    fun testLambdaExpression() = doTest()

    fun testLetExpression() = doTest()

    fun testTupleExpression() = doTest()

    fun testMetaDefCallWithClauses() = doTest()

    fun `test fix for atomic expression in function body`() = doTypedQuickFixTest("""
      \func test => (2){-caret-}
    """, """
      \func test => 2
    """)

    fun `test fix for composite expression in return type`() = doTypedQuickFixTest("""
      \func test : (0 = 0){-caret-} => idp
    """, """
      \func test : 0 = 0 => idp
    """)

    private fun doTest() {
        myFixture.configureByFile(fileName)
        myFixture.checkHighlighting()
    }

    private fun doTypedQuickFixTest(@Language("Arend") before: String, @Language("Arend") after: String) =
            typedQuickFixTest(ArendBundle.message("arend.unwrap.parentheses.fix"), before, after)

    override val dataPath = "org/arend/inspections/redundant_parens"
}