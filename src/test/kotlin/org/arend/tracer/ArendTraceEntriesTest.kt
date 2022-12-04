package org.arend.tracer

import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendDefFunction

class ArendTraceEntriesTest : ArendTraceTestBase() {
    companion object {
        const val pmap =
            """\func pmap {A B : \Type} (f : A -> B) {a a' : A} (p : a = a') : f a = f a' => path (\lam i => f (p @ i))"""
    }

    fun `test first trace entry 1`() {
        val tracingData = doTrace(
            pmap +
            """
            \func test (a b : Nat) (p : a = b) => pmap suc (pmap suc {-caret-}p)
            """
        )
        assertEquals("p", getFirstEntry(tracingData)?.psiElement?.text)
    }

    fun `test first trace entry 2`() {
        val tracingData = doTrace(
            pmap +
            """
            \func test (a b : Nat) (p : a = b) => pmap suc (pmap s{-caret-}uc p)
            """
        )
        assertEquals("suc", getFirstEntry(tracingData)?.psiElement?.text)
    }

    fun `test first trace entry 3`() {
        val tracingData = doTrace(
            pmap +
            """
            \func test (a b : Nat) (p : a = b) => pmap suc (p{-caret-}map suc p)
            """
        )
        assertEquals("pmap suc p", getFirstEntry(tracingData)?.psiElement?.text)
    }

    fun `test no trace entry outside function signature`() {
        val tracingData = doTrace(
            pmap +
            """
            \func test (a b : Nat) (p : a = b) => {-caret-}pmap suc (pmap suc p)
            """
        )
        assertEquals("pmap suc (pmap suc p)", getFirstEntry(tracingData)?.psiElement?.text)
        val funcKw = ((myFixture.file as ArendFile).statements[1].group as ArendDefFunction).functionKw
        assertEquals(-1, tracingData.trace.indexOfEntry(funcKw))
    }
}