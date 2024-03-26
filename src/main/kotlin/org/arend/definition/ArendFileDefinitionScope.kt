package org.arend.definition

import org.arend.error.DummyErrorReporter
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.LexicalScope
import org.arend.naming.scope.Scope
import org.arend.psi.ArendFile
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.concrete.Concrete

class ArendFileDefinitionScope(private val arendFile: ArendFile) : Scope {
    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()
        arendFile.apply {
            val concreteProvider = PsiConcreteProvider(arendFile.project, DummyErrorReporter.INSTANCE, null)
            LexicalScope.opened(this).elements.forEach {
                val ref = concreteProvider.getConcrete(it as LocatedReferable)
                if (ref is Concrete.Definition) {
                    result.add(it)
                }
            }
        }
        return result
    }

    override fun getElements(kind: Referable.RefKind?): Collection<Referable> = if (kind == null || kind == Referable.RefKind.EXPR) elements else emptyList()
}
