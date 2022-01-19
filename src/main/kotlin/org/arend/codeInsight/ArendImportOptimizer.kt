package org.arend.codeInsight

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.util.castSafelyTo
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.arend.core.expr.FunCallExpression
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.LexicalScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiStubbedReferableImpl
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.refactoring.getCompleteWhere
import org.arend.typechecking.visitor.SearchVisitor
import org.jetbrains.annotations.Contract
import kotlin.reflect.jvm.internal.impl.utils.SmartSet

// each group has its own \open scope, so each of the scopes should be processed separately
// opens are inherited for nested subgroups
// solution -- merge opens in subgroups and move them up if there are no conflicts
class ArendImportOptimizer : ImportOptimizer {

    override fun supports(file: PsiFile): Boolean = file is ArendFile && file.isWritable

    override fun processFile(file: PsiFile): Runnable {
        if (file !is ArendFile) return EmptyRunnable.getInstance()
        val (fileImports, optimalTree, coreUsed) = getOptimalImportStructure(file)
        return psiModificationRunnable(file, fileImports, optimalTree, coreUsed)
    }

    private fun psiModificationRunnable(
        file: ArendFile,
        fileImports: Map<ModulePath, Set<String>>,
        optimalTree: OptimalModuleStructure,
        coreUsed: Boolean
    ) = object : ImportOptimizer.CollectingInfoRunnable {
        override fun run() {
            addFileImports(file, fileImports)
            addModuleOpens(listOf(), file, optimalTree)
            file.project.service<ArendPsiChangeService>().processEvent(file, null, null, null, null, true)
        }

        override fun getUserNotificationInfo(): String =
            if (coreUsed) "Imports optimized. Instances were inferred from core"
            else "Imports optimized. Necessary instances were guessed"
    }
}

private val LOG = Logger.getInstance(ArendImportOptimizer::class.java)

@RequiresWriteLock
private fun addFileImports(file: ArendFile, imports: Map<ModulePath, Set<String>>) {
    eraseNamespaceCommands(file)
    doAddNamespaceCommands(file, imports.mapKeys { it.key.toList() }, "\\import")
}

@RequiresWriteLock
private fun addModuleOpens(currentPath: List<String>, group: ArendGroup, rootStructure: OptimalModuleStructure?) {
    if (group !is PsiFile) {
        eraseNamespaceCommands(group)
    }
    if (rootStructure != null && rootStructure.usages.isNotEmpty()) {
        doAddNamespaceCommands(group, rootStructure.usages)
    }
    for (subgroup in group.subgroups.flatMap { listOf(it) + it.dynamicSubgroups }) {
        val substructure = rootStructure?.subgroups?.find { it.name == subgroup.name }
        addModuleOpens(currentPath + listOf(substructure?.name ?: ""), subgroup, substructure) // remove nested opens
    }
}

private fun doAddNamespaceCommands(
    group: ArendGroup,
    importMap: Map<List<String>, Set<String>>,
    prefix: String = "\\open"
) {
    val importStatements = mutableListOf<String>()
    for ((path, identifiers) in importMap) {
        if (path.isEmpty()) continue
        val longPrefix = "$prefix ${path.joinToString(".")}"
        if (identifiers.size > 1000) {
            importStatements.add(longPrefix)
        } else {
            importStatements.add(longPrefix + identifiers.sorted().joinToString(", ", " (", ")"))
        }
    }
    importStatements.sort()
    if (importStatements.isEmpty()) {
        return
    }
    val factory = ArendPsiFactory(group.project)
    val (groupContainer, anchorElement) = if (group is ArendFile) {
        group to group.statements.lastOrNull { it.statCmd != null }
    } else {
        val where = getCompleteWhere(group, factory)
        where to where.lbrace
    }
    val commands = factory.createFromText(importStatements.joinToString(" "))?.statements ?: return
    for (command in commands.reversed()) {
        if (anchorElement == null)
            groupContainer.addBefore(command, group.firstChild)
        else
            groupContainer.addAfter(command, anchorElement)
    }
}

@RequiresWriteLock
private fun eraseNamespaceCommands(group: ArendGroup) {
    val statCommands = group.statements.filter { it.statCmd != null }
    val deleteWhere = statCommands.size == group.statements.size
    statCommands.forEach { it.delete() }
    if (deleteWhere) {
        group.where?.delete()
    }
}

internal data class OptimizationResult(
    val fileImports: Map<ModulePath, Set<String>>,
    val openStructure: OptimalModuleStructure,
    val coreDefinitionsUsed: Boolean
)

internal data class OptimalModuleStructure(
    val name: String,
    val subgroups: List<OptimalModuleStructure>,
    val usages: Map<List<String>, Set<String>>,
)

internal fun getOptimalImportStructure(file: ArendFile): OptimizationResult {
    val rootFrame = MutableFrame("")
    val forbiddenFilesToImport = setOfNotNull(file.moduleLocation?.modulePath, Prelude.MODULE_PATH)
    val collector = ImportStructureCollector(rootFrame)

    file.accept(collector)
    rootFrame.contract(collector.allDefinitionsTypechecked, collector.fileImports.values.flatMapTo(HashSet()) { it })
        .forEach { (file, ids) -> collector.fileImports.computeIfAbsent(file) { HashSet() }.addAll(ids) }
    return OptimizationResult(collector.fileImports.filter { it.key !in forbiddenFilesToImport }, rootFrame.asOptimalTree(), collector.allDefinitionsTypechecked)
}

private class ImportStructureCollector(rootFrame: MutableFrame) : PsiRecursiveElementWalkingVisitor() {
    var allDefinitionsTypechecked = true
    val fileImports: MutableMap<ModulePath, MutableSet<String>> = mutableMapOf()
    private val currentScopePath: MutableList<MutableFrame> = mutableListOf(rootFrame)
    private val currentModulePath: MutableList<String> = mutableListOf()
    private val currentFrame get() = currentScopePath.last()


    override fun visitElement(element: PsiElement) {
        if (element is ArendDefinition) {
            currentFrame.definitions.add((element as Referable).refName)
            registerCoClauses(element)
        }
        if (element is ArendGroup && element !is ArendFile) {
            currentScopePath.add(MutableFrame(element.refName))
            currentModulePath.add(element.refName)
            addSyntacticGlobalInstances(currentScopePath[currentScopePath.lastIndex - 1], element)
        }
        if (element is ArendFile) {
            addSyntacticGlobalInstances(MutableFrame(""), element)
        }
        if (element is ArendDefinition) {
            addCoreGlobalInstances(element)
        }

        if (element !is ArendStatCmd) {
            super.visitElement(element)
        }
        if (element is ArendReferenceElement) {
            visitReferenceElement(element)
        }
    }

    private fun registerCoClauses(element: PsiElement) {
        if (element !is ClassReferable) return
        element.fieldReferables.filterIsInstance<ArendClassField>().forEach { currentFrame.definitions.add(it.refName) }
    }

    private fun addCoreGlobalInstances(element: ArendDefinition) {
        val tcReferable = element.tcReferable?.castSafelyTo<TCDefReferable>()?.typechecked
        allDefinitionsTypechecked = allDefinitionsTypechecked && (tcReferable != null)
        if (!allDefinitionsTypechecked) return
        tcReferable!!.accept(object : SearchVisitor<Unit>() { // not-null assertion implied by '&&' above
            override fun visitFunCall(expr: FunCallExpression?, params: Unit?): Boolean {
                val pointerToDefinition = expr?.definition?.referable?.data?.castSafelyTo<SmartPsiElementPointer<*>>()
                val globalInstance = pointerToDefinition?.element?.castSafelyTo<ArendDefInstance>()
                if (globalInstance != null) {
                    currentFrame.instancesFromCore.add(globalInstance)
                }
                return super.visitFunCall(expr, params)
            }
        }, Unit)
    }

    private fun addSyntacticGlobalInstances(lastFrame : MutableFrame, element: ArendGroup) {
        val scope = element.scope
        val restrictedScope = if (scope is LexicalScope) {
            LexicalScope.insideOf(element, EmptyScope.INSTANCE)
        } else scope
        Scope.traverse(restrictedScope) {
            if (it is ArendDefInstance) {
                currentFrame.instancesFromScope.add(it)
            }
        }
        currentFrame.instancesFromScope.addAll(lastFrame.instancesFromScope)
    }

    override fun elementFinished(element: PsiElement?) {
        if (element is ArendGroup && element !is ArendFile) {
            val last = currentScopePath.removeLast()
            currentModulePath.removeLast()
            currentFrame.subgroups.add(last)
        }
        super.elementFinished(element)
    }

    private fun visitReferenceElement(element: ArendReferenceElement) {
        // todo stubs
        val resolved = element.reference?.resolve().castSafelyTo<PsiStubbedReferableImpl<*>>() ?: return
        val resolvedGroup by lazy(LazyThreadSafetyMode.NONE) { resolved.parentOfType<ArendGroup>() }
        val elementGroup by lazy(LazyThreadSafetyMode.NONE) { element.parentOfType<ArendGroup>() ?: element }
        if (resolved is ArendDefModule ||
            element.parent?.parent is CoClauseBase ||
            (resolved is ArendClassField && PsiTreeUtil.isAncestor(
                resolvedGroup?.parentGroup,
                elementGroup,
                false
            )) ||
            isSuperAffectsElement(resolvedGroup, resolved, elementGroup)
        ) {
            return
        }
        val (importedFile, openingQualifier) = collectQualifier(resolved)
        val (openingPath, characteristics) = subtract(
            openingQualifier.toList(),
            importedFile.toList(),
            element.longName
        )
        if (characteristics == null) {
            return
        }
        val minimalImportedElement = openingQualifier.firstName ?: characteristics
        fileImports.computeIfAbsent(importedFile) { HashSet() }.add(minimalImportedElement)
        val shortenedOpeningPath = openingPath.shorten(currentModulePath)
        if (shortenedOpeningPath.isNotEmpty()) {
            currentFrame.usages[characteristics] = openingPath
        }
    }
}

private fun isSuperAffectsElement(
    resolvedScope: ArendCompositeElement?,
    resolved: ArendCompositeElement,
    element: ArendCompositeElement
): Boolean {
    if (PsiTreeUtil.isAncestor(resolvedScope, element, false)) return true
    if (element is ArendGroup && resolvedScope is ArendGroup && element.where != null && element.where == resolvedScope.where) return true
    if (resolvedScope is ArendDefClass && isFieldOrDynamic(resolvedScope, resolved)) {
        return element.parentsOfType<ArendDefClass>().any { it.isSubClassOf(resolvedScope) }
    }
    return false
}

fun isFieldOrDynamic(resolvedScope: ArendDefClass, resolved: ArendCompositeElement): Boolean {
    return !PsiTreeUtil.isAncestor(resolvedScope.where, resolved, true)
}

private data class MutableFrame(
    val name: String,
    val subgroups: MutableList<MutableFrame> = mutableListOf(),
    val definitions: MutableSet<String> = mutableSetOf(),
    val usages: MutableMap<String, List<String>> = mutableMapOf(),
    val instancesFromScope: MutableList<ArendDefInstance> = mutableListOf(),
    val instancesFromCore: MutableSet<ArendDefInstance> = mutableSetOf(),
) {
    fun asOptimalTree(): OptimalModuleStructure =
        OptimalModuleStructure(name, subgroups.map { it.asOptimalTree() }, run {
            val reverseMapping = mutableMapOf<List<String>, MutableSet<String>>()
            this@MutableFrame.usages.forEach { (id, path) ->
                reverseMapping.computeIfAbsent(path) { HashSet() }.add(id)
            }
            reverseMapping
        })

    @Contract(mutates = "this")
    fun contract(useTypecheckedInstances: Boolean, fileImports: Set<String>): Map<ModulePath, Set<String>> {
        val submaps = subgroups.map { it.contract(useTypecheckedInstances, fileImports) }
        val additionalFiles = mutableMapOf<ModulePath, MutableSet<String>>()
        submaps.forEach {
            it.forEach { (filePath, ids) ->
                additionalFiles.computeIfAbsent(filePath) { HashSet() }.addAll(ids)
            }
        }
        val instanceSource = if (useTypecheckedInstances) instancesFromCore else instancesFromScope
        for (instance in instanceSource) {
            val (importedFile, qualifier) = collectQualifier(instance)
            additionalFiles.computeIfAbsent(importedFile) { HashSet() }.add(qualifier.firstName ?: instance.refName)
            usages[instance.refName] = qualifier.toList()
        }
        val allInnerIdentifiers = subgroups.flatMapTo(HashSet()) { it.usages.keys }

        for (identifier in allInnerIdentifiers) {
            if (definitions.contains(identifier) || (name == "" && fileImports.contains(identifier))) {
                subgroups.forEach {
                    if (it.usages[identifier] == usages[identifier]) {
                        it.usages.remove(identifier)
                    }
                }
                continue
            }
            val paths = subgroups.mapNotNullTo(SmartSet.create()) { it.usages[identifier] }
            usages[identifier]?.let(paths::add)
            if (paths.size > 1) {
                // identifiers with the same name occur with different qualifiers in submodules,
                // therefore they cannot be lifted
                continue
            } else {
                // identifier is unique for each of the submodules, import for it can be lifted
                usages[identifier] = paths.first()
                subgroups.forEach { it.usages.remove(identifier) }
            }
        }
        subgroups.removeAll { it.usages.isEmpty() && it.subgroups.isEmpty() }
        return additionalFiles
    }
}

private fun List<String>.shorten(currentPath: List<String>): List<String> {
    for (index in currentPath.indices) {
        if (index > this.lastIndex) {
            return emptyList()
        }
        if (this[index] != currentPath[index]) {
            return this.drop(index)
        }
    }
    return this.drop(currentPath.size)
}

private fun subtract(
    fullQualifier: List<String>,
    fileName: List<String>,
    shortQualifier: List<String>
): Pair<List<String>, String?> {
    if (shortQualifier.size > fileName.size && fileName == shortQualifier.take(fileName.size)) {
        return fullQualifier to shortQualifier[fileName.size]
    }
    if (shortQualifier.size == 1) return fullQualifier to shortQualifier.last()
    for (index in shortQualifier.indices.reversed().drop(1)) { // start checking module name
        val indexInFullQualifier = fullQualifier.lastIndex + (index - (shortQualifier.lastIndex - 1))
        if (indexInFullQualifier <= -1) {
            return emptyList<String>() to shortQualifier.first()
        }
        if (fullQualifier[indexInFullQualifier] != shortQualifier[index]) {
            return fullQualifier.subList(0, indexInFullQualifier + 1) to null
        }
    }
    return fullQualifier.take((fullQualifier.size - (shortQualifier.size - 1)).coerceAtLeast(0)) to shortQualifier[0]
}

private fun collectQualifier(element: ArendCompositeElement): Pair<ModulePath, ModulePath> {
    var currentGroup = element.parentOfType<ArendGroup>()
    if (element is ArendConstructor) {
        @Suppress("RemoveExplicitTypeArguments")
        currentGroup = currentGroup?.parentOfType<ArendGroup>()
    }
    val container = mutableListOf<String>()
    while (currentGroup != null) {
        if (currentGroup is ArendFile) {
            val modulePath = currentGroup.moduleLocation?.modulePath?.toList()
            container.reverse()
            return ModulePath(modulePath) to ModulePath(container)
        } else {
            container.add(currentGroup.refName)
        }
        currentGroup = currentGroup.parentGroup
    }
    LOG.error("Every stub hierarchy should end in PsiFile")
    error("unreachable")
}