package org.vclang.resolving.namespace

import com.jetbrains.jetpad.vclang.naming.namespace.EmptyNamespace
import com.jetbrains.jetpad.vclang.naming.namespace.Namespace
import com.jetbrains.jetpad.vclang.naming.namespace.StaticNamespaceProvider
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable

// TODO[abstract]
object VcStaticNamespaceProvider : StaticNamespaceProvider {
    override fun forReferable(definition: GlobalReferable): Namespace {
        return EmptyNamespace.INSTANCE
        /*
        val namespace = VcNamespace()
        definition.accept(DefinitionGetNamespaceVisitor, namespace)
        return namespace
        */
    }

    /*
    private fun forFunction(definition: Abstract.FunctionDefinition, namespace: VcNamespace) =
            forDefinitions(definition.globalDefinitions, namespace)

    private fun forData(definition: Abstract.DataDefinition, namespace: VcNamespace) =
            definition.constructorClauses
                    .flatMap { it.constructors }
                    .forEach { namespace.addDefinition(it) }

    private fun forClass(definition: Abstract.ClassDefinition, namespace: VcNamespace) =
            forDefinitions(definition.globalDefinitions, namespace)

    private fun forClassView(definition: Abstract.ClassView, namespace: VcNamespace) =
            definition.fields.forEach { namespace.addDefinition(it) }

    private fun forDefinitions(
            definitions: Collection<Abstract.Definition>,
            namespace: VcNamespace
    ) {
        for (definition in definitions) {
            namespace.addDefinition(definition)
            if (definition is Abstract.ClassView) {
                forClassView(definition, namespace)
            } else if (definition is Abstract.DataDefinition) {
                forData(definition, namespace)
            }
        }
    }

    private object DefinitionGetNamespaceVisitor : BaseAbstractVisitor<VcNamespace, Void>() {

        override fun visitFunction(
                definition: Abstract.FunctionDefinition,
                namespace: VcNamespace
        ): Void? {
            forFunction(definition, namespace)
            return null
        }

        override fun visitData(definition: Abstract.DataDefinition, namespace: VcNamespace): Void? {
            forData(definition, namespace)
            return null
        }

        override fun visitClass(
                definition: Abstract.ClassDefinition,
                namespace: VcNamespace
        ): Void? {
            forClass(definition, namespace)
            return null
        }
    }
    */
}
