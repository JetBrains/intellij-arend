package org.arend.definition

import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.psi.ArendFile
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.concrete.Concrete

class ArendFileDefinitionScope(private val arendFile: ArendFile, private val extraPath: ModulePath = ModulePath()) : Scope {
    override fun getElements(): Collection<Referable> {
        val result = ArrayList<Referable>()
        val concreteProvider = PsiConcreteProvider(arendFile.project, DummyErrorReporter.INSTANCE, null)
        arendFile.getTCRefMap(Referable.RefKind.EXPR).forEach {
            if (ModulePath(it.key.toList().subList(0, extraPath.size())) == extraPath) {
                val ref = concreteProvider.getConcrete(it.value as GlobalReferable)
                if (ref is Concrete.Definition) {
                    val referable = it.value
                    referable.displayName = referable.refLongName.toString().removePrefix(extraPath.toString()).removePrefix(".")
                    if (referable.displayName!!.isNotEmpty()) {
                        result.add(referable)
                    }
                }
            }
        }
        return result
    }

    override fun getElements(kind: Referable.RefKind?): Collection<Referable> = if (kind == null || kind == Referable.RefKind.EXPR) elements else emptyList()

    override fun resolveNamespace(name: String, onlyInternal: Boolean) = ArendFileDefinitionScope(arendFile, ModulePath(extraPath.toList() + name))
}
