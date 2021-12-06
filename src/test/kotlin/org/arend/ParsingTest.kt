package org.arend

import com.intellij.testFramework.ParsingTestCase
import org.arend.parser.ArendParserDefinition
import org.junit.internal.runners.JUnit38ClassRunner
import org.junit.runner.RunWith

@RunWith(JUnit38ClassRunner::class) // TODO: A workaround for https://github.com/gradle/gradle/issues/18486
class ParsingTest : ParsingTestCase("org/arend/parser/fixtures", ArendFileType.defaultExtension, ArendParserDefinition()) {

    override fun getTestDataPath() = "src/test/resources"

    fun testSimpleDef() = doTest(true, true)

    fun testColonExprs() = doTest(true, true)

    fun testMixedReplCommandAndColon() = doTest(true, true)

    fun testMixedReplCommandAndColon2() = doTest(true, true)

    fun testReplTypeCommand() = doTest(true, true)

    fun testBareExprCommand() = doTest(true, true)
}
