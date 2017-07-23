package org.vclang.lang.core.resolve

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiManager
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.FilenameIndex
import org.vclang.lang.VcFileType
import org.vclang.lang.core.psi.*

object NamespaceProvider {

    fun forDefinition(
            def: VcDefFunction,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        forTelescope(def.teleList, ns)
        def.where?.let { forWhere(it, ns) }
        return ns
    }

    fun forDefinition(
            def: VcDefData,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        def.dataBody?.dataConstructors?.constructorList?.forEach { ns.put(it) }
        forTelescope(def.teleList, ns)
        return ns
    }

    fun forDefinition(
            def: VcDefClass,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        val definitions = def.statementList.map { it.statDef }.filterNotNull()
        forDefinitions(definitions, ns)
        forTelescope(def.teleList, ns)
        def.where?.let { forWhere(it, ns) }
        return ns
    }

    fun forDefinition(
            def: VcDefClassView,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        def.classViewFieldList.forEach { ns.put(it) }
        return ns
    }

    fun forDefinitions(
            definitions: List<VcStatDef>,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        definitions
                .map { it.definition }
                .filterNotNull()
                .forEach {
                    it.defFunction?.let {
                        ns.put(it)
//                        forDefinition(it, ns)
                    }
                    it.defData?.let {
                        ns.put(it)
                        forDefinition(it, ns)
                    }
                    it.defClass?.let {
                        ns.put(it)
//                        forDefinition(it, ns)
                    }
                    it.defClassView?.let {
                        ns.put(it)
//                        forDefinition(it, ns)
                    }
                    it.defAbstract?.let {
                        ns.put(it)
//                        forDefinition(it, ns)
                    }
                }
        return ns
    }

    fun forExpression(
            expr: VcLamExpr,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace = forTelescope(expr.teleList, ns)

    fun forExpression(
            expr: VcPiExpr,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace = forTelescope(expr.teleList, ns)

    fun forExpression(
            expr: VcSigmaExpr,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace = forTelescope(expr.teleList, ns)

    fun forExpression(
            expr: VcNewExpr,
            parentScope: Scope,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        expr.newKw ?: return ns
        val className = expr.binOpArg?.argumentBinOp?.atomFieldsAcc?.atom?.literal?.identifier?.name
        className ?: return ns
        val classDef = parentScope.resolve(className)
        (classDef?.namespace as? SimpleNamespace)?.let { ns.putAll(it) }
        return ns
    }

    fun forPattern(
        pattern : VcPattern,
        ns: SimpleNamespace = SimpleNamespace()
    ): Namespace {
//        pattern.variableList.forEach { ns.put(it) }
//        pattern.atomPatternList
//                .map { it.pattern }
//                .filterNotNull()
//                .forEach { forPattern(it, ns) }
        return ns
    }

    fun forModulePath(
            pathParts : List<String>,
            project: Project,
            ns: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        val virtualFiles = FilenameIndex.getVirtualFilesByName(
                project,
                "${pathParts.last()}.${VcFileType.defaultExtension}",
                GlobalSearchScope.allScope(project)
        )
        virtualFiles.first()?.let {
            val file = PsiManager.getInstance(project).findFile(it)
            val statements = file?.firstChild as? VcStatements
            statements?.namespace?.let { ns.putAll(it as SimpleNamespace) }

        }
        return ns
    }

    private fun forTelescope(
        teles : Collection<VcTele>,
        ns: SimpleNamespace = SimpleNamespace()
    ): Namespace {
        teles
                .map { it.literal?.identifier }
                .filterNotNull()
                .forEach { ns.put(it) }
        teles
                .map { it.typedExpr }
                .filterNotNull()
                .flatMap { it.unknownOrIDList.map { it.identifier }.filterNotNull() }
                .forEach { ns.put(it) }
        return ns
    }

    private fun forWhere(where: VcWhere, ns: SimpleNamespace) {
        val definitions = where.statementList.map { it.statDef }.filterNotNull()
        forDefinitions(definitions, ns)
    }
}
