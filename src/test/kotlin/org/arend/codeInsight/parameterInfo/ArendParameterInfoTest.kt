package org.arend.codeInsight.parameterInfo

import com.intellij.codeInsight.hint.ShowParameterInfoHandler
import com.intellij.psi.PsiElement
import com.intellij.testFramework.utils.parameterInfo.MockCreateParameterInfoContext
import com.intellij.testFramework.utils.parameterInfo.MockUpdateParameterInfoContext
import org.arend.ArendLanguage
import org.arend.ArendTestBase
import org.junit.Assert

class ArendParameterInfoTest : ArendTestBase() {

    private fun checkParameterInfo(code: String, expectedHint: String) {
        InlineFile(code).withCaret()

        val handler = ShowParameterInfoHandler.getHandlers(project, ArendLanguage.INSTANCE)?.firstOrNull() ?:
                            error("Could not find parameter info handler")
        val mockCreateParameterInfoContext = MockCreateParameterInfoContext(myFixture.editor, myFixture.file)
        val parameterOwner = handler.findElementForParameterInfo(mockCreateParameterInfoContext) as? PsiElement

        if (parameterOwner == null) {
            Assert.assertEquals(expectedHint, "")
            return
        }

        val updateContext = MockUpdateParameterInfoContext(myFixture.editor, myFixture.file)
        updateContext.parameterOwner = parameterOwner
        val elementForUpdating = handler.findElementForUpdatingParameterInfo(updateContext)
        if (elementForUpdating != null) {
            handler.updateParameterInfo(elementForUpdating, updateContext)
        }

        val parameterInfoUIContext = MockParameterInfoUIContext(parameterOwner)
        parameterInfoUIContext.currentParameterIndex = updateContext.currentParameter

        mockCreateParameterInfoContext.itemsToShow?.forEach {
            handler.updateUI(it, parameterInfoUIContext)
        }

        Assert.assertEquals(expectedHint, parameterInfoUIContext.resultText)
    }

    fun `test inner app parameter`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func g (y : Nat -> Nat) => 0\n" +
                "\\func h => f (g {-caret-})"
        val expectedHint = "<highlight>y : Nat -> Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test inner app function`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func g (y : Nat -> Nat) => 0\n" +
                "\\func h => f (g{-caret-})"
        val expectedHint = "y : Nat -> Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test outer app parameter`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func g (y : Nat -> Nat) => 0\n" +
                "\\func h => f g {-caret-}"
        val expectedHint = "x : Nat, <highlight>y : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test no hint in lam`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func h => f (\\lam x => {-caret-}x)"
        val expectedHint = ""
        checkParameterInfo(code, expectedHint)
    }

    fun `test hint in lam`() {
        val code = "\\func f (x y : Nat) => 0\n" +
                "\\func h => f (\\lam x =>{-caret-} x)"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test binop seq priority right`() {
        val code = "\\func f => 1 Nat.+ 2{-caret-} Nat.* 3"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test binop seq priority left`() {
        val code = "\\func f => 1 Nat.* 2{-caret-} Nat.+ 3"
        val expectedHint = "x : Nat, <highlight>y : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test binop seq no highlighting right`() {
        val code = "\\func f => 1 Nat.+ 2 Nat.*{-caret-} 3"
        val expectedHint = "x : Nat, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test binop seq no highlighting left`() {
        val code = "\\func f => 1 Nat.+ 2 {-caret-}Nat.* 3"
        val expectedHint = "x : Nat, y : Nat"
        checkParameterInfo(code, expectedHint)
    }


    fun `test long names`() {
        val code = "\\class C { x } \n" +
                "\\func f (x y : Nat) => 0\n" +
                "\\func h (c : C) => f (c.x{-caret-})"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test implicit params outer`() {
        val code = "\\func f (x y : Nat) {z : Nat} (w : Nat) => 0\n" +
                "\\func h (x : Nat) => f 0 0 {x{-caret-}}"
        val expectedHint = "x : Nat, y : Nat, <highlight>{z : Nat}</highlight>, w : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test implicit params skip implicit`() {
        val code = "\\func f (x y : Nat) {z : Nat} (w : Nat) => 0\n" +
                "\\func h (x : Nat) => f 0 0 x{-caret-}"
        val expectedHint = "x : Nat, y : Nat, {z : Nat}, <highlight>w : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test implicit params inner`() {
        val code = "\\func f (x y : Nat) {z : Nat} (w : Nat) => 0\n" +
                "\\func h (x : Nat) => f 0 0 {f x{-caret-}}"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat, {z : Nat}, w : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test class fields`() {
        val code = "\\class C { \\field f (x y : Nat) : Nat } \n" +
                "\\func h (c : C) (x : Nat) => c.f {-caret-}x"
        val expectedHint = "<highlight>x : Nat</highlight>, y : Nat"
        checkParameterInfo(code, expectedHint)
    }

    fun `test data constructors`() {
        val code = "\\data D (x : Nat) \n" +
                    "| cons (y : Nat)\n" +
                "\\func h (z : Nat) => cons {x} {-caret-}z"
        val expectedHint = "{x : Nat}, <highlight>y : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

    fun `test level`() {
        val code = "\\func f (x : Nat) : \\level (Nat -> Nat) 0\n" +
                "\\func g (x : Nat) => f x {-caret-}"
        val expectedHint = "x : Nat, <highlight>_ : Nat</highlight>"
        checkParameterInfo(code, expectedHint)
    }

}