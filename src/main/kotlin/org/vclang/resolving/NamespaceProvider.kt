package org.vclang.resolving

import com.jetbrains.jetpad.vclang.error.DummyErrorReporter
import com.jetbrains.jetpad.vclang.naming.namespace.Namespace
import com.jetbrains.jetpad.vclang.naming.namespace.SimpleNamespace
import org.vclang.psi.*
import org.vclang.psi.ext.PsiReferable

// TODO[abstract]
object NamespaceProvider {
    /*
    fun forDefinition(
            definition: VcDefFunction,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        forTelescope(definition.teleList, namespace)
        definition.where?.let { forWhere(it, namespace) }
        return namespace
    }

    fun forDefinition(
            definition: VcDefData,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        definition
                .dataBody
                ?.dataConstructors
                ?.constructorList
                ?.forEach { namespace.addDefinition(it, DummyErrorReporter.INSTANCE) }
        forTelescope(definition.teleList, namespace)
        return namespace
    }

    fun forDefinition(
            definition: VcDefClass,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        val classStats = definition.classStats?.classStatList

        classStats
                ?.filterIsInstance<VcClassField>()
                ?.forEach { forDefinition(it, namespace) }

        val definitions = classStats?.mapNotNull { it.definition }
        if (definitions != null) {
            forDefinitions(definitions, namespace)
            definition.classTeles?.teleList?.let { forTelescope(it, namespace) }
            definition.where?.let { forWhere(it, namespace) }
        }

        return namespace
    }
    */

    fun forDefinition(
            definition: VcDefClassView,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        definition.classViewFieldList.forEach { namespace.addDefinition(it, DummyErrorReporter.INSTANCE) }
        return namespace
    }

    private fun forDefinition(
            definition: VcClassField,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        namespace.addDefinition(definition, DummyErrorReporter.INSTANCE)
        return namespace
    }

    fun forDefinitions(
            definitions: List<VcDefinition>,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        definitions.forEach {
            if (it is VcDefData) {
                it.dataBody?.dataConstructors?.constructorList?.forEach { namespace.addDefinition(it, DummyErrorReporter.INSTANCE) }
            }
            if (it is PsiReferable) {
                namespace.addDefinition(it, DummyErrorReporter.INSTANCE)
            }
        }
        return namespace
    }

    /*
    fun forExpression(
            expr: VcLamExpr,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace = forTelescope(expr.teleList, namespace)

    fun forExpression(
            expr: VcPiExpr,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace = forTelescope(expr.teleList, namespace)

    fun forExpression(
            expr: VcSigmaExpr,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace = forTelescope(expr.teleList, namespace)

    fun forExpression(
            expr: VcNewExpr,
            parentScope: Scope,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        expr.newKw ?: return namespace
        val className = expr
                .binOpArg
                ?.argumentBinOp
                ?.atomFieldsAcc
                ?.atom
                ?.literal
                ?.prefixName
                ?.referenceName
                ?: return namespace
        val classDef = parentScope.resolve(className)
        (classDef?.namespace as? SimpleNamespace)?.let { namespace.putAll(it) }
        return namespace
    }
    */

    fun forPattern(
            pattern: VcPattern,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
//        pattern.variableList.forEach { namespace.put(it) }
//        pattern.atomPatternList
//                .map { it.pattern }
//                .filterNotNull()
//                .forEach { forPattern(it, namespace) }
        return namespace
    }

    /*
    private fun forTelescope(
            teles: Collection<VcTele>,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        teles
                .mapNotNull { it.literal?.prefixName }
                .forEach { namespace.put(it.referenceName!!, it) }
        teles
                .mapNotNull { it.typedExpr }
                .flatMap { it.identifierOrUnknownList.mapNotNull { it.defIdentifier } }
                .forEach { namespace.put(it.referenceName!!, it) }
        return namespace
    }
    */

    private fun forWhere(where: VcWhere, namespace: SimpleNamespace) {
        val definitions = where.statementList.mapNotNull { it as? VcDefinition }
        forDefinitions(definitions, namespace)
    }
}
