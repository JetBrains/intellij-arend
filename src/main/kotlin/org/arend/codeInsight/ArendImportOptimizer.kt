package org.arend.codeInsight

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.castSafelyTo
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiStubbedReferableImpl
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.refactoring.getCompleteWhere
import org.jetbrains.annotations.Contract
import kotlin.reflect.jvm.internal.impl.utils.SmartSet

// each group has its own \open scope, so each of the scopes should be processed separately
// opens are inherited for nested subgroups
// solution -- merge opens in subgroups and move them up if there are no conflicts
class ArendImportOptimizer : ImportOptimizer {

    override fun supports(file: PsiFile): Boolean = file is ArendFile && file.isWritable

    override fun processFile(file: PsiFile): Runnable {
        if (file !is ArendFile) return EmptyRunnable.getInstance()
        val (fileImports, optimalTree) = getOptimalImportStructure(file)

        return object : ImportOptimizer.CollectingInfoRunnable {
            override fun run() {
                addImportCommands(file, fileImports)
                runPsiChanges(listOf(), file, optimalTree)
                file.project.service<ArendPsiChangeService>().processEvent(file, null, null, null, null, true)
            }

            override fun getUserNotificationInfo(): String = "Imports optimized. Necessary instances were guessed"
        }
    }

}

private val LOG = Logger.getInstance(ArendImportOptimizer::class.java)

@RequiresWriteLock
private fun addImportCommands(file: ArendFile, importMap: Map<ModulePath, Set<String>>) {
    file.statements.mapNotNull { it.statCmd }.forEach { it.deleteWithNotification() }
    val correctedMap = importMap
        .filter { it.key != file.moduleLocation?.modulePath && it.key != Prelude.MODULE_PATH }
        .mapKeys { it.key.toList() }
    doAddNamespaceCommands(file, correctedMap, "\\import")
}

@RequiresWriteLock
private fun runPsiChanges(currentPath: List<String>, group: ArendGroup, rootStructure: OptimalModuleStructure?) {
    if (group !is PsiFile) {
        val statCommands = group.statements.mapNotNull { it.statCmd }
        statCommands.forEach { it.deleteWithNotification() }
    }
    if (rootStructure != null && rootStructure.usages.isNotEmpty()) {
        val reverseMapping = mutableMapOf<List<String>, MutableSet<String>>()
        rootStructure.usages.forEach { (id, path) ->
            val shortPath = path.shorten(currentPath).takeIf { it.isNotEmpty() } ?: return@forEach
            reverseMapping.computeIfAbsent(shortPath) { HashSet() }.add(id)
        }
        if (reverseMapping.isNotEmpty()) {
            doAddNamespaceCommands(group, reverseMapping)
        }
    }
    for (subgroup in group.subgroups.flatMap { listOf(it) + it.dynamicSubgroups }) {
        val substructure = rootStructure?.subgroups?.find { it.name == subgroup.name }
        runPsiChanges(currentPath + listOf(substructure?.name ?: ""), subgroup, substructure) // remove nested opens
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

private fun doAddNamespaceCommands(
    group: ArendGroup,
    importMap: Map<List<String>, Set<String>>,
    prefix: String = "\\open"
) {
    val importStatements = mutableListOf<String>()
    val preludeDefinitions = mutableListOf<List<String>>()
    Prelude.forEach { def -> preludeDefinitions.add(Prelude.MODULE_PATH.toList() + def.name) }
    for ((path, identifiers) in importMap) {
        if (path.isEmpty()) {
            continue
        }
        val longPrefix = "$prefix ${path.joinToString(".")}"
        if (identifiers.size > 1000) {
            importStatements.add(longPrefix)
        } else {
            importStatements.add(longPrefix + identifiers.sorted().joinToString(", ", " (", ")"))
        }
    }
    importStatements.sort()
    val commands =
        ArendPsiFactory(group.project).createFromText(importStatements.joinToString("\n"))?.statements ?: return
    val codeStyleManager = CodeStyleManager.getInstance(group.manager)
    val (holder, anchorElement) = if (group is ArendFile) {
        group to group.statements.lastOrNull { it.statCmd != null }
    } else {
        val where = getCompleteWhere(group, ArendPsiFactory(group.project))
        where to (where.lbrace ?: run {
            val (lbrace, rbrace) = ArendPsiFactory(group.project).createPairOfBraces()
            where.addAfter(rbrace, where.statementList.lastOrNull() ?: where.whereKw)
            val newLBrace = where.addAfter(lbrace, where.whereKw)
            where.addAfter(ArendPsiFactory(group.project).createWhitespace(" "), where.whereKw)
            newLBrace
        })
    }
    val newline = ArendPsiFactory(group.project).createWhitespace("\n")
    val newElements = commands.reversed().mapIndexed { index, it ->
        val addedElement = if (anchorElement == null)
            holder.addBeforeWithNotification(it, group.firstChild)
        else
            holder.addAfterWithNotification(it, anchorElement)
        if (index != 0) {
            addedElement.add(newline)
        }
        addedElement
    }
    if (anchorElement != null) {
        holder.addAfterWithNotification(ArendPsiFactory(group.project).createWhitespace("\n"), anchorElement)
    }
    if (newElements.isNotEmpty()) {
        val minOffset = newElements.minOf { it.startOffset }
        val maxOffset = newElements.maxOf { it.endOffset }
        codeStyleManager.reformatText(
            group.containingFile,
            (minOffset - 1).coerceAtLeast(0),
            (maxOffset + 1).coerceAtMost(group.endOffset)
        )
    }
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

data class OptimalModuleStructure(
    val name: String,
    val subgroups: List<OptimalModuleStructure>,
    val usages: Map<String, List<String>>,
)

fun getOptimalImportStructure(file: ArendFile): Pair<Map<ModulePath, Set<String>>, OptimalModuleStructure> {
    val rootFrame = MutableFrame("")
    val fileImports: MutableMap<ModulePath, MutableSet<String>> = mutableMapOf()

    file.accept(object : PsiRecursiveElementWalkingVisitor() {
        private val currentScopePath: MutableList<MutableFrame> = mutableListOf(rootFrame)

        override fun visitElement(element: PsiElement) {
            if (element is ArendDefinition) {
                currentScopePath.last().definitions.add((element as Referable).refName)
                if (element is ClassReferable) {
                    element.fieldReferables.filterIsInstance<ArendClassField>().forEach { currentScopePath.last().definitions.add(it.refName) }
                }
            }
            if (element is ArendGroup && element !is ArendFile) {
                currentScopePath.add(MutableFrame(element.refName))
                Scope.traverse(element.scope) {
                    if (it is ArendDefInstance) {
                        currentScopePath.last().instances.add(it)
                    }
                }
            }
            if (element !is ArendStatCmd) {
                super.visitElement(element)
            }
            if (element is ArendReferenceElement) {
                visitReferenceElement(element)
            }
        }

        override fun elementFinished(element: PsiElement?) {
            if (element is ArendGroup && element !is ArendFile) {
                val last = currentScopePath.removeLast()
                currentScopePath.last().subgroups.add(last)
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
            // remove instance if it is resolved
            val minimalImportedElement = openingQualifier.firstName ?: characteristics
            fileImports.computeIfAbsent(importedFile) { HashSet() }.add(minimalImportedElement)
            currentScopePath.last().usages[characteristics] = openingPath
        }
    })

    rootFrame.contract().forEach { (file, ids) -> fileImports.computeIfAbsent(file) { HashSet() }.addAll(ids) }
    return fileImports to rootFrame.asOptimalTree()
}

private fun isSuperAffectsElement(
    resolvedScope: ArendCompositeElement?,
    resolved: ArendCompositeElement,
    element: ArendCompositeElement
): Boolean {
    if (PsiTreeUtil.isAncestor(resolvedScope, element, false)) return true
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
    val instances: MutableList<ArendDefInstance> = mutableListOf()
) {
    fun asOptimalTree(): OptimalModuleStructure =
        OptimalModuleStructure(name, subgroups.map { it.asOptimalTree() }, usages)

    @Contract(mutates = "this")
    fun contract(): Map<ModulePath, Set<String>> {
        val submaps = subgroups.map { it.contract() }
        val additionalFiles = mutableMapOf<ModulePath, MutableSet<String>>()
        submaps.forEach {
            it.forEach { (filePath, ids) ->
                additionalFiles.computeIfAbsent(filePath) { HashSet() }.addAll(ids)
            }
        }
        for (instance in instances) {
            val (importedFile, qualifier) = collectQualifier(instance)
            additionalFiles.computeIfAbsent(importedFile) { HashSet() }.add(qualifier.firstName ?: instance.refName)
            usages[instance.refName] = qualifier.toList()
        }
        val allInnerIdentifiers = subgroups.flatMapTo(HashSet()) { it.usages.keys }

        for (identifier in allInnerIdentifiers) {
            if (definitions.contains(identifier)) {
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
