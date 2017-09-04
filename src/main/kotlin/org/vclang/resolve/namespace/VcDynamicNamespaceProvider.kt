package org.vclang.resolve.namespace

import com.jetbrains.jetpad.vclang.naming.namespace.DynamicNamespaceProvider
import com.jetbrains.jetpad.vclang.term.Abstract

object VcDynamicNamespaceProvider : DynamicNamespaceProvider {

    override fun forClass(classDefinition: Abstract.ClassDefinition): VcNamespace {
        val namespace = forDefinitions(classDefinition.instanceDefinitions)
        classDefinition.fields.forEach { namespace.addDefinition(it) }
        classDefinition.superClasses
                .mapNotNull { Abstract.getUnderlyingClassDef(it.superClass) }
                .forEach { namespace.addAll(forClass(it)) }
        return namespace
    }

    private fun forData(definition: Abstract.DataDefinition): VcNamespace {
        val namespace = VcNamespace()
        definition.constructorClauses
                .flatMap { it.constructors }
                .forEach { namespace.addDefinition(it) }
        return namespace
    }

    private fun forDefinitions(definitions: Collection<Abstract.Definition>): VcNamespace {
        val namespace = VcNamespace()
        definitions
                .apply { forEach { namespace.addDefinition(it) } }
                .filterIsInstance<Abstract.DataDefinition>()
                .forEach { namespace.addAll(forData(it)) }
        return namespace
    }
}
