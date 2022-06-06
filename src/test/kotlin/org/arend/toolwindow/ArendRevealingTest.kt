package org.arend.toolwindow

import com.intellij.openapi.components.service
import junit.framework.TestCase
import org.arend.ArendTestBase
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.ext.prettyprinting.PrettyPrinterFlag
import org.arend.injection.actions.NormalizationCache
import org.arend.injection.findRevealableCoreAtOffset
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.local.GoalError
import org.intellij.lang.annotations.Language
import java.util.*


class ArendRevealingTest : ArendTestBase() {

    private fun testRevealing(@Language("Arend") typecheckable: String, docString: String, expectedRepr: String?) {
        val file = myFixture.addFileToProject("Main.ard", typecheckable)
        typecheck()
        val error = project.service<ErrorService>().errors[file]!!.single().error as GoalError
        val doc = error.getBodyDoc(EMPTY_PP_CONFIG)!!
        val docOffset = docString.findAnyOf(listOf(CARET_MARKER))!!.first
        val actualDocString = docString.removeRange(docOffset, docOffset + CARET_MARKER.length)
        TestCase.assertEquals(doc.toString(), actualDocString)
        val fragment = findRevealableCoreAtOffset(docOffset, doc, error, EMPTY_PP_CONFIG, NormalizationCache())
        TestCase.assertEquals(expectedRepr == null, fragment == null)
        if (fragment == null) {
            return
        }
        TestCase.assertEquals(expectedRepr, fragment.result.getTextId())
    }

    fun `test basic reveal`() {
        testRevealing(
            """
            \data DD {T : \Type} (t : T) 
            \func foo : DD 1 => {?}""",
            """
                Expected type: D{-caret-}D 1
            """.trimIndent(), "DD"
        )
    }

    fun `test reveal at the end of identifier`() {
        testRevealing(
            """
            \data D {T : \Type} (t : T) 
            \func foo : D 1 => {?}""",
            """
                Expected type: D{-caret-} 1
            """.trimIndent(), "D"
        )
    }

    fun `test reveal for lambda parameters`() {
        testRevealing(
            """
            \func foo : (\lam (i : Nat) => i) = (\lam (i : Nat) => i) => {?}""",
            """
                Expected type: (\lam i{-caret-} => i) = (\lam i => i)
            """.trimIndent(), "i"
        )
    }

    fun `test reveal for idp`() {
        testRevealing(
                """
            \func foo : idp = {1 = 1} idp => {?}""",
                """
                Expected type: idp{-caret-} = idp
            """.trimIndent(), "idp"
        )
    }

    fun `test reveal for infix`() {
        testRevealing(
                """
            \func foo : idp = {1 = 1} idp => {?}""",
                """
                Expected type: idp ={-caret-} idp
            """.trimIndent(), "="
        )
    }

    fun `test reveal for coclause`() {
        testRevealing(
                """
            \record R { | r : 1 = 1 }
            \func foo : R { | r => idp } => {?}""",
                """
                Expected type:
                  R {
                    | r => {?hi{-caret-}dden}
                  }
            """.trimIndent(), "{?hidden}"
        )
    }

    fun `test reveal for coclause 2`() {
        testRevealing(
                """
            \record R { \field r : 1 = 1 }
            \func foo : R { | r => idp } => {?}""",
                """
                Expected type:
                  R {
                    | r => i{-caret-}dp
                  }
            """.trimIndent(), "idp"
        )
    }
}

private val EMPTY_PP_CONFIG: PrettyPrinterConfig = object : PrettyPrinterConfig {
    override fun getExpressionFlags(): EnumSet<PrettyPrinterFlag> {
        return EnumSet.noneOf(PrettyPrinterFlag::class.java)
    }
}
