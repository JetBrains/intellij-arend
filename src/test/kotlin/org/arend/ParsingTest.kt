package org.arend

import com.intellij.testFramework.ParsingTestCase
import org.arend.parser.ArendParserDefinition

class ParsingTest : ParsingTestCase("org/arend/parser/fixtures", ArendFileTypeInstance.defaultExtension, ArendParserDefinition()) {

    override fun getTestDataPath() = "src/test/resources"

    fun testSimpleDef() = doTest(true, true)

    fun testColonExprs() = doTest(true, true)

    fun testMixedReplCommandAndColon() = doTest(true, true)

    fun testMixedReplCommandAndColon2() = doTest(true, true)

    fun testReplTypeCommand() = doTest(true, true)

    fun testBareExprCommand() = doTest(true, true)
}
