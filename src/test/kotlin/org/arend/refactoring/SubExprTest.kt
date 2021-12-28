package org.arend.refactoring

import com.intellij.openapi.components.service
import com.intellij.openapi.util.TextRange
import org.arend.ArendTestBase
import org.arend.core.expr.LetExpression
import org.arend.term.concrete.Concrete
import org.arend.typechecking.TypeCheckingService
import org.intellij.lang.annotations.Language

class SubExprTest : ArendTestBase() {
    override fun setUp() {
        super.setUp()
        project.service<TypeCheckingService>().initialize()
    }

    fun subexpr(@Language("Arend") code: String, selection: TextRange): SubExprResult {
        InlineFile(code)
        typecheck()
        return correspondedSubExpr(selection, myFixture.file, project)
    }

    fun subexpr(@Language("Arend") code: String): SubExprResult {
        InlineFile(code).withCaret()
        typecheck()
        val range = TextRange(myFixture.caretOffset, myFixture.caretOffset)
        return correspondedSubExpr(range, myFixture.file, project)
    }

    fun subexpr(@Language("Arend") code: String, selection: String): SubExprResult =
        subexpr(code, TextRange.from(code.indexOf(selection), selection.length))

    fun `test basic`() = subexpr("""
        \func yukio => 114514
        """, "114514").run {
        assertEquals("114514", subConcrete.toString())
        assertEquals("114514", subCore.toString())
    }

    fun `test basic 2`() = subexpr("""
        \func yukio => {-caret-}114514
        """).run {
        assertEquals("114514", subConcrete.toString())
        assertEquals("114514", subCore.toString())
    }

    fun `test infix select whole expr`() = subexpr("""
        \func yukio => 114 Nat.+ 514
        """, "114 Nat.+ 514").run {
        assertEquals("114 + 514", subConcrete.toString())
        assertEquals("628", subCore.toString())
    }

    fun `test infix only select op`() = subexpr("""
        \func yukio => 114 Nat.+ 514
        """, "+").run {
        assertEquals("114 + 514", subConcrete.toString())
        assertEquals("628", subCore.toString())
    }

    fun `test infix caret at op`() = subexpr("""
        \func yukio => 114 {-caret-}Nat.+ 514
        """).run {
        assertEquals("114 + 514", subConcrete.toString())
        assertEquals("628", subCore.toString())
    }

    fun `test infix caret at op 2`() = subexpr("""
        \func yukio => 114 Nat.{-caret-}+ 514
        """).run {
        assertEquals("114 + 514", subConcrete.toString())
        assertEquals("628", subCore.toString())
    }

    fun `test paren select left paren`() = subexpr("""
        \func madoka (a : Nat) => a
        \func homura => madoka (madoka 1)
        """, "(m").run {
        assertEquals("madoka 1", subConcrete.toString())
        assertEquals("madoka 1", subCore.toString())
    }

    fun `test paren caret at left paren`() = subexpr("""
        \func marisa (a : Nat) => a
        \func reimu => marisa {-caret-}(marisa 1)
        """).run {
        assertEquals("marisa 1", subConcrete.toString())
        assertEquals("marisa 1", subCore.toString())
    }

    fun `test #201`() = subexpr("""
        \func shinji (asuka : \Sigma Nat Nat) => asuka.1
        """, "asuka.1").run {
        assertEquals("asuka.1", subConcrete.toString())
        assertEquals("asuka.1", subCore.toString())
    }

    fun `test #201 workaround`() = subexpr("""
        \func ayanami (makinami : \Sigma Nat Nat) => makinami{-caret-}.1
        """).run {
        assertEquals("makinami.1", subConcrete.toString())
        assertEquals("makinami.1", subCore.toString())
    }

    fun `test #180`() = subexpr("""
        \func valis (a : Nat) => a
        \func sxyha => valis ({-caret-}valis 1)
        """).run {
        assertEquals("valis 1", subConcrete.toString())
        assertEquals("valis 1", subCore.toString())
    }

    fun `test #162`() = subexpr("""
        \func giogio => {-caret-}\let | mista => 4444 \in mista
        """).run {
        assertTrue(subConcrete is Concrete.LetExpression)
        assertTrue(subCore is LetExpression)
    }

    fun `test #150`() = subexpr("""
        \func yuki => {-caret-}\let | yuno (a : Nat) => idp {_} {{-caret-}a} \in yuno 2
        """).run {
        assertEquals("a", subConcrete.toString())
        assertEquals("a", subCore.toString())
    }

    fun `test #231`() {
        subexpr("""
            \record Tony
              | beta (lam : {-caret-}\Set0) (b : \Prop) (d : lam) (a : b) : b""").apply {
            assertEquals("\\Set0", subConcrete.toString())
            assertEquals("\\Set0", subCore.toString())
        }
        subexpr("""
            \record Tony
              | beta (lam : \Set0) (b : {-caret-}\Prop) (d : lam) (a : b) : b""").apply {
            assertEquals("\\Prop", subConcrete.toString())
            assertEquals("\\Prop", subCore.toString())
        }
        subexpr("""
            \record Tony
              | beta (lam : \Set0) (b : \Prop) (d : {-caret-}lam) (a : b) : b""").apply {
            assertEquals("lam", subConcrete.toString())
            assertEquals("lam", subCore.toString())
        }
        subexpr("""
            \record Tony
              | beta (lam : \Set0) (b : \Prop) (d : lam) (a : {-caret-}b) : b""").apply {
            assertEquals("b", subConcrete.toString())
            assertEquals("b", subCore.toString())
        }
    }
}