package org.arend.tracer

import org.arend.ArendTestBase
import org.arend.error.DummyErrorReporter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.concrete.Concrete
import org.intellij.lang.annotations.Language

abstract class ArendTraceTestBase : ArendTestBase() {
    protected fun doTrace(@Language("Arend") code: String): ArendTracingData {
        InlineFile(code).withCaret()
        val (expression, definitionRef) =
            ArendTraceAction.getElementAtCursor(myFixture.file, myFixture.editor)!!
        val definition = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null, true)
            .getConcrete(definitionRef) as Concrete.Definition
        return ArendTraceAction.runTracingTypechecker(project, definition, expression)
    }

    protected fun getFirstEntry(tracingData: ArendTracingData) =
        tracingData.trace.entries.getOrNull(tracingData.firstEntryIndex)
}