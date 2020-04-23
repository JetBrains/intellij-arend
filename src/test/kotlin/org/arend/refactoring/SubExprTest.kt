package org.arend.refactoring

import com.intellij.openapi.util.TextRange
import junit.framework.Assert
import junit.framework.Assert.assertTrue
import org.arend.ArendTestBase
import org.arend.core.expr.LetExpression
import org.arend.term.concrete.Concrete
import org.intellij.lang.annotations.Language

class SubExprTest : ArendTestBase() {
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
        assertEquals(subConcrete.toString(), "114514")
        assertEquals(subCore.toString(), "114514")
    }

    fun `test basic 2`() = subexpr("""
        \func yukio => {-caret-}114514
        """).run {
        assertEquals(subConcrete.toString(), "114514")
        assertEquals(subCore.toString(), "114514")
    }

    fun `test infix select whole expr`() = subexpr("""
        \func yukio => 114 Nat.+ 514
        """, "114 Nat.+ 514").run {
        assertEquals(subConcrete.toString(), "114 + 514")
        assertEquals(subCore.toString(), "114 + 514")
    }

    fun `test infix only select op`() = subexpr("""
        \func yukio => 114 Nat.+ 514
        """, "+").run {
        assertEquals(subConcrete.toString(), "114 + 514")
        assertEquals(subCore.toString(), "114 + 514")
    }

    fun `test infix caret at op`() = subexpr("""
        \func yukio => 114 {-caret-}Nat.+ 514
        """).run {
        assertEquals(subConcrete.toString(), "114 + 514")
        assertEquals(subCore.toString(), "114 + 514")
    }

    fun `test infix caret at op 2`() = subexpr("""
        \func yukio => 114 Nat.{-caret-}+ 514
        """).run {
        assertEquals(subConcrete.toString(), "114 + 514")
        assertEquals(subCore.toString(), "114 + 514")
    }

    fun `test paren select left paren`() = subexpr("""
        \func madoka (a : Nat) => a
        \func homura => madoka (madoka 1)
        """, "(m").run {
        assertEquals(subConcrete.toString(), "madoka 1")
        assertEquals(subCore.toString(), "madoka 1")
    }

    fun `test paren caret at left paren`() = subexpr("""
        \func marisa (a : Nat) => a
        \func reimu => marisa {-caret-}(marisa 1)
        """).run {
        assertEquals(subConcrete.toString(), "marisa 1")
        assertEquals(subCore.toString(), "marisa 1")
    }

    fun `test #180`() = subexpr("""
        \func valis (a : Nat) => a
        \func sxyha => valis ({-caret-}valis 1)
        """).run {
        assertEquals(subConcrete.toString(), "valis 1")
        assertEquals(subCore.toString(), "valis 1")
    }

    fun `test #162`() = subexpr("""
        \func giogio => {-caret-}\let | mista => 4444 \in mista
        """).run {
        Assert.assertTrue(subConcrete is Concrete.LetExpression)
        Assert.assertTrue(subCore is LetExpression)
    }

    fun `test #150`() = subexpr("""
        \func yuki => {-caret-}\let | yuno (a : Nat) => idp {_} {{-caret-}a} \in yuno 2
        """).run {
        assertEquals(subConcrete.toString(), "a")
        assertEquals(subCore.toString(), "a")
    }
}