package org.arend.util

import com.intellij.psi.PsiElement
import org.arend.ArendTestBase
import org.arend.psi.descendantOfType
import org.arend.psi.ext.ArendArgumentAppExpr
import org.arend.psi.ext.ArendFunctionDefinition
import org.arend.psi.parentOfType
import org.arend.term.concrete.Concrete

class ArendBinOpUtilsTest : ArendTestBase() {
    private fun testParseBinOp(file: String, result: String) {
        InlineFile(file)
        val element = myFixture.findElementByText("test", PsiElement::class.java)
        val appExprPsi = element.parentOfType<ArendFunctionDefinition<*>>()?.body?.expr?.descendantOfType<ArendArgumentAppExpr>()!!
        val appExpr = appExprToConcrete(appExprPsi) as Concrete.AppExpression
        val functionText = (appExpr.function.data as PsiElement).text
        val firstArgText = (appExpr.arguments[0].expression.data as PsiElement).text!!
        val secondArgText = (appExpr.arguments[1].expression.data as PsiElement).text!!
        assertEquals(result, "$firstArgText $functionText $secondArgText")
    }

    fun `test parse bin op seq of class field alias`() {
        testParseBinOp("""
            \class M {
              | o \alias \infixr 5 ∘ : Nat -> Nat -> Nat
            }
            \func test {m : M} => 1 ∘ 2
        """.trimIndent(), "1 ∘ 2")
    }

    fun `test parse bin op seq of long name alias`() {
        testParseBinOp("""
            \class M {
              | o \alias \infixr 5 ∘ : Nat -> Nat -> Nat
            }
            \func test {m : M} => 1 M.∘ 2
        """.trimIndent(), "1 M.∘ 2")
    }

    fun `test parse bin op seq of class field`() {
        testParseBinOp("""
            \class M {
              | o \alias \infixr 5 ∘ : Nat -> Nat -> Nat
            }
            \func test {m : M} => 1 o 2
        """.trimIndent(), "o 1 2")
    }

    fun `test parse bin op seq of long name`() {
        testParseBinOp("""
            \class M {
              | o \alias \infixr 5 ∘ : Nat -> Nat -> Nat
            }
            \func test {m : M} => 1 M.o 2
        """.trimIndent(), "M.o 1 2")
    }
}