package org.arend

import com.intellij.testFramework.ParsingTestCase
import org.arend.parser.ArendParserDefinition

class ParsingTest : ParsingTestCase("org/arend/parser/fixtures", ArendFileType.defaultExtension, ArendParserDefinition()) {

    override fun getTestDataPath() = "src/test/resources"

    fun testSimpleDef() = doTest(true, true)

    fun testColonExprs() = doTest(true, true)

    fun testMixedReplCommandAndColon() = doTest(true, true)

    fun testReplTypeCommand() = doTest(true, true)

    fun testBareExprCommand() = doTest(true, true)
}
