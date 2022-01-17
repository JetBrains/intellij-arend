package org.arend.codeInsight

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.components.service
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.parentOfType
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.util.castSafelyTo
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.arend.ext.module.ModulePath
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.impl.ArendGroup
import org.arend.typechecking.TypeCheckingService
import org.jetbrains.annotations.Contract
import kotlin.reflect.jvm.internal.impl.utils.SmartSet

// each group has its own import scope, so each of the scopes should be processed separately
// imports are inherited for nested subgroups
// solution -- merge imports in subgroups and move them up if there are no conflicts
// todo more than 3 imports -- try to fold in everything import
class ArendImportOptimizer : ImportOptimizer {

    override fun supports(file: PsiFile): Boolean = file is ArendFile && file.isWritable

    override fun processFile(file: PsiFile): Runnable {
        if (file !is ArendFile) return EmptyRunnable.getInstance()
        val optimalTree = getOptimalImportStructure(file)

        return Runnable {
            runPsiChanges(listOf(), file, optimalTree)
        }
    }
}

@RequiresWriteLock
private fun runPsiChanges(currentPath: List<String>, group : ArendGroup, rootStructure: OptimalModuleStructure) {
    val statCommands = group.statements.mapNotNull { it.statCmd }
    statCommands.forEach { it.delete() }
    if (rootStructure.usages.isNotEmpty()) {
        insertNewImports(currentPath, group, rootStructure)
    }
    for (subgroup in group.subgroups) {
        val substructure = rootStructure.subgroups.find { it.name == subgroup.name }
        if (substructure != null) {
            runPsiChanges(currentPath + listOf(substructure.name), subgroup, substructure)
        }
    }
}

private fun insertNewImports(currentPath: List<String>, group: ArendGroup, rootStructure: OptimalModuleStructure) {
    val reverseMapping = mutableMapOf<List<String>, MutableSet<String>>()
    rootStructure.usages.forEach { (id, path) -> reverseMapping.computeIfAbsent(path) { HashSet() }.add(id) }
    // todo: \hiding
    // todo: \as
    val importStatements = mutableListOf<String>()
    val typecheckingService = group.project.service<TypeCheckingService>()
    val preludeDefinitions = mutableListOf<List<String>>()
    Prelude.forEach { def -> preludeDefinitions.add(Prelude.MODULE_PATH.toList() + def.name) }
    for ((path, identifiers) in reverseMapping) {
        val actualPath = if (path in preludeDefinitions) {
            path.drop(Prelude.MODULE_PATH.size())
        } else {
            path.shorten(currentPath)
        }
        if (actualPath.isEmpty()) {
            continue
        }
        if (actualPath == Prelude.MODULE_PATH.toList()) {
            continue
        }
        val isStandaloneModule = typecheckingService.libraryManager.registeredLibraries.any { it.containsModule(ModulePath(actualPath)) }
        val prefix = "\\${if (isStandaloneModule) "import" else "open"} ${actualPath.joinToString(".")}"
        if (identifiers.size > 3) {
            importStatements.add(prefix)
        } else {
            importStatements.add(prefix + identifiers.sorted().joinToString(", ", " (", ")"))
        }
    }
    importStatements.sort()
    val commands = ArendPsiFactory(group.project).createFromText(importStatements.joinToString("\n"))?.namespaceCommands ?: return
    val codeStyleManager = CodeStyleManager.getInstance(group.manager)
    val newElements = mutableListOf<PsiElement>()
    commands.reversed().forEach {
        if (group is ArendFile) {
            group.addBefore(it, group.firstChild)
        }  else {
            val where = group.where!! // if there is a nested definition, there is certainly a nested \where
            val lbrace = where.lbrace ?: run {
                val (lbrace, rbrace) = ArendPsiFactory(group.project).createPairOfBraces()
                where.addAfter(rbrace, where.statementList.lastOrNull() ?: where.whereKw)
                val newLBrace = where.addAfter(lbrace, where.whereKw)
                where.addAfter(ArendPsiFactory(group.project).createWhitespace(" "), where.whereKw)
                newLBrace
            }
            where.addAfter(it, lbrace)
        }.let(newElements::add)
    }
    if (newElements.isNotEmpty()) {
        val minOffset = newElements.minOf { it.startOffset }
        val maxOffset = newElements.maxOf { it.endOffset }
        codeStyleManager.reformatText(group.containingFile, minOffset, maxOffset + 1)
    }
}

private fun subtract(fullQualifier: List<String>, shortQualifier: List<String>): List<String> {
    if (shortQualifier.size == 1) return fullQualifier
    return fullQualifier.take(fullQualifier.size - (shortQualifier.size - 1)) // drop suffix in case of partially qualified name
}

private fun collectQualifier(element: ArendCompositeElement): List<String> {
    var currentGroup = element.parentOfType<ArendGroup>()
    if (element is ArendConstructor) {
        @Suppress("RemoveExplicitTypeArguments")
        currentGroup = currentGroup?.parentOfType<ArendGroup>()
    }
    val container = mutableListOf<String>()
    while (currentGroup != null) {
        if (currentGroup is ArendFile) {
            currentGroup.moduleLocation?.modulePath?.toList()?.reversed()?.forEach(container::add)
        } else {
            container.add(currentGroup.refName)
        }
        currentGroup = currentGroup.parentGroup
    }
    container.reverse()
    return container
}

data class OptimalModuleStructure(
    val name: String,
    val subgroups: List<OptimalModuleStructure>,
    val usages: Map<String, List<String>>,
)

fun getOptimalImportStructure(file: ArendFile): OptimalModuleStructure {
    val rootFrame = MutableFrame("")
    val filePath = file.moduleLocation?.modulePath?.toList()

    file.accept(object : PsiRecursiveElementWalkingVisitor() {
        private val currentScopePath: MutableList<MutableFrame> = mutableListOf(rootFrame)

        override fun visitElement(element: PsiElement) {
            if (element is ArendWhere) {
                val groupName = element.parentOfType<ArendGroup>()?.refName
                groupName ?: return // looks like a bare \where block, just skip it
                currentScopePath.add(MutableFrame(groupName))
            }
            if (element !is ArendStatCmd) {
                super.visitElement(element)
            }
            if (element is ArendReferenceElement) {
                visitReferenceElement(element)
            }
        }

        override fun elementFinished(element: PsiElement?) {
            if (element is ArendWhere) {
                val last = currentScopePath.removeLast()
                currentScopePath.last().subgroups.add(last)
            }
            super.elementFinished(element)
        }

        private fun visitReferenceElement(element: ArendReferenceElement) {
            val resolved = element.resolve.castSafelyTo<ArendCompositeElement>() ?: return
            if (resolved is ArendDefModule || resolved is ArendFile || resolved == element) {
                return
            }
            val qualifier = collectQualifier(resolved).shorten(filePath)
            val requiredPath = subtract(qualifier, element.longName)
            currentScopePath.last().usages[element.longName.first()] = requiredPath
        }
    })

    return rootFrame.apply(MutableFrame::contract).asOptimalTree()
}

private data class MutableFrame(
    val name: String,
    val subgroups: MutableList<MutableFrame> = mutableListOf(),
    val usages: MutableMap<String, List<String>> = mutableMapOf(),
) {
    fun asOptimalTree(): OptimalModuleStructure =
        OptimalModuleStructure(name, subgroups.map { it.asOptimalTree() }, usages)

    @Contract(mutates = "this")
    fun contract() {
        subgroups.forEach { it.contract() }
        val allInnerIdentifiers = subgroups.flatMapTo(HashSet()) { it.usages.keys }

        for (identifier in allInnerIdentifiers) {
            if (identifier !in usages) {
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
        subgroups.filter { it.usages.isNotEmpty() || it.subgroups.isNotEmpty() }
    }
}

fun List<String>.shorten(other: List<String>?) : List<String> {
    if (other == null || other.size > size) return this
    for (i in other.indices) {
        if (get(i) != other[i]) return this
    }
    return this.drop(other.size)
}
