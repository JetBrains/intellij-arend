package org.vclang.lang.core.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope
import org.vclang.lang.VcFileType
import org.vclang.lang.core.psi.*
import org.vclang.lang.core.psi.ext.VcNamedElement

object NamespaceProvider {

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
        definition.dataBody?.dataConstructors?.constructorList?.forEach { namespace.put(it) }
        forTelescope(definition.teleList, namespace)
        return namespace
    }

    fun forDefinition(
            definition: VcDefClass,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        val definitions = definition.classStatList.map { it.statement?.statDef }.filterNotNull()
        forDefinitions(definitions, namespace)
        forTelescope(definition.teleList, namespace)
        definition.where?.let { forWhere(it, namespace) }
        return namespace
    }

    fun forDefinition(
            definition: VcDefClassView,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        definition.classViewFieldList.forEach { namespace.put(it) }
        return namespace
    }

    fun forDefinitions(
            definitions: List<VcStatDef>,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        definitions
                .map { it.definition }
                .filterNotNull()
                .forEach { namespace.put(it as? VcNamedElement) }
        return namespace
    }

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
                ?.referenceName ?: return namespace
        val classDef = parentScope.resolve(className)
        (classDef?.namespace as? SimpleNamespace)?.let { namespace.putAll(it) }
        return namespace
    }

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

    fun forModulePath(
            pathParts: List<String>,
            project: Project,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        val virtualFiles = FilenameIndex.getVirtualFilesByName(
                project,
                "${pathParts.last()}.${VcFileType.defaultExtension}",
                GlobalSearchScope.allScope(project)
        )
        virtualFiles.first()?.let {
            val file = PsiManager.getInstance(project).findFile(it)
            val statements = file?.firstChild as? VcStatements
            statements?.namespace?.let { namespace.putAll(it as SimpleNamespace) }

        }
        return namespace
    }

    private fun forTelescope(
            teles: Collection<VcTele>,
            namespace: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        teles
                .map { it.literal?.prefixName }
                .filterNotNull()
                .forEach { namespace.put(it.referenceName!!, it) }
        teles
                .map { it.typedExpr }
                .filterNotNull()
                .flatMap { it.identifierOrUnknownList.map { it.identifier }.filterNotNull() }
                .forEach { namespace.put(it) }
        return namespace
    }

    private fun forWhere(where: VcWhere, namespace: SimpleNamespace) {
        val definitions = where.statementList.map { it.statDef }.filterNotNull()
        forDefinitions(definitions, namespace)
    }
}
