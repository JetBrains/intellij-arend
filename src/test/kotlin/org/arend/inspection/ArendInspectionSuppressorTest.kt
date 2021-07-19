package org.arend.inspection

import com.intellij.codeInspection.NonAsciiCharactersInspection
import org.arend.ArendTestBase

class ArendInspectionSuppressorTest : ArendTestBase() {
    override fun setUp() {
        super.setUp()
        myFixture.enableInspections(NonAsciiCharactersInspection::class.java)
    }

    fun `test non ascii chars are not reported`() {
        InlineFile("""
            \open Nat (+)
            \func \infixr 8 o \alias \infixr 8 ∘ {A B C : \Type} (g : B -> C) (f : A -> B) => \lam x => g (f x)
            \func ++ => (+ 1) ∘ (+ 1)
        """.trimIndent())
        myFixture.checkHighlighting()
    }
}