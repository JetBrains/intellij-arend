package org.arend.tracer

import com.intellij.util.castSafelyTo
import org.arend.ArendTestBase
import org.arend.error.DummyErrorReporter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.concrete.Concrete

class ArendTraceActionTest : ArendTestBase() {
    companion object {
        const val pmap =
            """\func pmap {A B : \Type} (f : A -> B) {a a' : A} (p : a = a') : f a = f a' => path (\lam i => f (p @ i))"""
    }

    fun `test first trace entry 1`() {
        InlineFile(
            """
            $pmap
            \func test (a b : Nat) (p : a = b) => pmap suc (pmap suc {-caret-}p)
        """
        ).withCaret()
        val tracingData = doTrace()
        assertEquals("p", getFirstEntry(tracingData)?.psiElement?.text)
    }

    fun `test first trace entry 2`() {
        InlineFile(
            """
            $pmap
            \func test (a b : Nat) (p : a = b) => pmap suc (pmap s{-caret-}uc p)
        """
        ).withCaret()
        val tracingData = doTrace()
        assertEquals("suc", getFirstEntry(tracingData)?.psiElement?.text)
    }

    fun `test first trace entry 3`() {
        InlineFile(
            """
            $pmap
            \func test (a b : Nat) (p : a = b) => pmap suc (p{-caret-}map suc p)
        """
        ).withCaret()
        val tracingData = doTrace()
        assertEquals("pmap suc p", getFirstEntry(tracingData)?.psiElement?.text)
    }

    private fun doTrace(): ArendTracingData {
        val (expression, definitionRef) =
            ArendTraceAction.getElementAtCursor(myFixture.file, myFixture.editor)!!
        val definition = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true)
            .getConcrete(definitionRef).castSafelyTo<Concrete.Definition>()!!
        return ArendTraceAction.runTracingTypechecker(project, definition, expression)
    }

    private fun getFirstEntry(tracingData: ArendTracingData) =
        tracingData.trace.entries.getOrNull(tracingData.firstEntryIndex)
}