package org.arend.codeInsight

import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.diagnostic.Logger
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
import org.jetbrains.annotations.Contract
import kotlin.reflect.jvm.internal.impl.utils.SmartSet

// each group has its own \open scope, so each of the scopes should be processed separately
// imports are inherited for nested subgroups
// solution -- merge imports in subgroups and move them up if there are no conflicts
// todo more than 3 imports -- try to fold in everything import
class ArendImportOptimizer : ImportOptimizer {

    override fun supports(file: PsiFile): Boolean = file is ArendFile && file.isWritable

    override fun processFile(file: PsiFile): Runnable {
        if (file !is ArendFile) return EmptyRunnable.getInstance()
        val (fileImports, optimalTree) = getOptimalImportStructure(file)

        return Runnable {
            addImportCommands(file, fileImports)
            runPsiChanges(listOf(), file, optimalTree)
        }
    }

}

private val LOG = Logger.getInstance(ArendImportOptimizer::class.java)

@RequiresWriteLock
private fun addImportCommands(file: ArendFile, importMap: Map<ModulePath, Set<String>>) {
    file.statements.mapNotNull { it.statCmd }.forEach { it.delete() }
    val correctedMap = importMap
        .filter { it.key != file.moduleLocation?.modulePath && it.key != Prelude.MODULE_PATH }
        .mapKeys { it.key.toList() }
    doAddNamespaceCommands(file, correctedMap, "\\import")
}

@RequiresWriteLock
private fun runPsiChanges(currentPath: List<String>, group: ArendGroup, rootStructure: OptimalModuleStructure) {
    if (group !is PsiFile) {
        val statCommands = group.statements.mapNotNull { it.statCmd }
        statCommands.forEach { it.delete() }
    }
    if (rootStructure.usages.isNotEmpty()) {
        val reverseMapping = mutableMapOf<List<String>, MutableSet<String>>()
        rootStructure.usages.forEach { (id, path) ->
            val shortPath = path.shorten(currentPath).takeIf { it.isNotEmpty() } ?: return@forEach
            reverseMapping.computeIfAbsent(shortPath) { HashSet() }.add(id)
        }
        if (reverseMapping.isNotEmpty()) {
            doAddNamespaceCommands(group, reverseMapping)
        }
    }
    for (subgroup in group.subgroups) {
        val substructure = rootStructure.subgroups.find { it.name == subgroup.name }
        if (substructure != null) {
            runPsiChanges(currentPath + listOf(substructure.name), subgroup, substructure)
        }
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
        if (identifiers.size > 3) {
            importStatements.add(longPrefix)
        } else {
            importStatements.add(longPrefix + identifiers.sorted().joinToString(", ", " (", ")"))
        }
    }
    importStatements.sort()
    val commands =
        ArendPsiFactory(group.project).createFromText(importStatements.joinToString("\n"))?.namespaceCommands ?: return
    val codeStyleManager = CodeStyleManager.getInstance(group.manager)
    val (holder, anchorElement) = if (group is ArendFile) {
        group to group.statements.lastOrNull { it.statCmd != null }
    } else {
        val where = group.where!! // if there is a nested definition, there is certainly a nested \where
        where to (where.lbrace ?: run {
            val (lbrace, rbrace) = ArendPsiFactory(group.project).createPairOfBraces()
            where.addAfter(rbrace, where.statementList.lastOrNull() ?: where.whereKw)
            val newLBrace = where.addAfter(lbrace, where.whereKw)
            where.addAfter(ArendPsiFactory(group.project).createWhitespace(" "), where.whereKw)
            newLBrace
        })
    }
    val newElements = commands.reversed().map {
        if (anchorElement == null)
            holder.addBefore(it, group.firstChild)
        else
            holder.addAfter(it, anchorElement)
    }
    if (anchorElement != null) {
        holder.addAfter(ArendPsiFactory(group.project).createWhitespace("\n"), anchorElement)
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

private fun subtract(fullQualifier: List<String>, shortQualifier: List<String>): List<String> {
    if (shortQualifier.size == 1) return fullQualifier
    return fullQualifier.take(fullQualifier.size - (shortQualifier.size - 1)) // drop suffix in case of partially qualified name
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
            // todo stubs
            val resolved = element.resolve.castSafelyTo<ArendCompositeElement>() ?: return
            if (resolved is ArendDefModule || resolved is ArendFile || resolved == element) {
                return
            }
            val characteristics = element.longName.first()
            val (importedFile, openingQualifier) = collectQualifier(resolved)
            val minimalImportedElement = openingQualifier.firstName ?: characteristics
            fileImports.computeIfAbsent(importedFile) { HashSet() }.add(minimalImportedElement)
            val openingPath = subtract(openingQualifier.toList(), element.longName)
            currentScopePath.last().usages[characteristics] = openingPath
        }
    })

    return fileImports to rootFrame.apply(MutableFrame::contract).asOptimalTree()
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
