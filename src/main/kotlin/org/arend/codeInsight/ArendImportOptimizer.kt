package org.arend.codeInsight

import com.intellij.application.options.CodeStyle
import com.intellij.lang.ImportOptimizer
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.EmptyRunnable
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiRecursiveElementWalkingVisitor
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.elementType
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.util.concurrency.annotations.RequiresWriteLock
import org.arend.core.definition.Definition.TypeCheckingStatus.NO_ERRORS
import org.arend.core.expr.FunCallExpression
import org.arend.ext.concrete.definition.FunctionKind
import org.arend.ext.module.ModulePath
import org.arend.naming.reference.ClassReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.NamespaceCommandNamespace
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.getCompleteWhere
import org.arend.settings.ArendCustomCodeStyleSettings
import org.arend.settings.ArendCustomCodeStyleSettings.OptimizeImportsPolicy
import org.arend.term.NamespaceCommand
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.visitor.SearchVisitor
import org.arend.util.ArendBundle
import org.arend.util.mapToSet
import org.jetbrains.annotations.Contract
import java.util.Collections.singletonList
import kotlin.reflect.jvm.internal.impl.utils.SmartSet

/**
 * Import optimization is performed in 3 phases:
 * 1) Collecting all references in the file. Responsible code is in [ImportStructureCollector].
 * This stage determines _what_ should be referenced and with _what qualifier_.
 * Global instances are gathered from core expressions if they exist or from the scope in the other case.
 *
 * 2) Optimizing ns-commands structure. Responsible code is in [MutableFrame.contract].
 * This stage determines _where_ should go _what_ import.
 * It removes an \open in a child group if parent group should have this \open.
 *
 * 3) Writing changes to the file. Responsible code is in [psiModificationRunnable].
 * @see ArendCustomCodeStyleSettings.OptimizeImportsPolicy
 */
class ArendImportOptimizer : ImportOptimizer {

    override fun supports(file: PsiFile): Boolean = file is ArendFile && file.isWritable

    override fun processFile(file: PsiFile): Runnable {
        if (file !is ArendFile) return EmptyRunnable.getInstance()
        val optimizationResult = getOptimalImportStructure(file)
        return psiModificationRunnable(file, optimizationResult)
    }

    internal fun psiModificationRunnable(
        file: ArendFile,
        optimizationResult: OptimizationResult,
    ) = object : ImportOptimizer.CollectingInfoRunnable {
        val settings = CodeStyle.getCustomSettings(file, ArendCustomCodeStyleSettings::class.java)

        override fun run() {
            val (fileImports, optimalTree, _) = optimizationResult
            if (settings.OPTIMIZE_IMPORTS_POLICY == OptimizeImportsPolicy.SOFT) {
                optimizeImportsSoftly(fileImports, optimalTree)
            } else {
                optimizeImportsHard(fileImports, optimalTree)
            }
        }

        private fun optimizeImportsHard(
            fileImports: Map<FilePath, Set<ImportedName>>,
            optimalTree: OptimalModuleStructure
        ) {
            val definitelyToHide = HashMap<String, Referable>()
            val fileScopeProvider = getScopeProvider(true, file)
            fileImports.forEach { (path, names) ->
                val scope = fileScopeProvider(path) ?: return@forEach
                names.forEach { refName ->
                    scope.resolveName(refName.visibleName)?.let { definitelyToHide[refName.visibleName] = it }
                }
            }
            optimalTree.usages.forEach { (path, names) ->
                val scope = fileScopeProvider(path) ?: return@forEach
                names.forEach { refName ->
                    scope.resolveName(refName.visibleName)?.let { definitelyToHide[refName.visibleName] = it }
                }
            }
            addFileImports(file, fileImports, definitelyToHide)
            addModuleOpens(file, optimalTree, definitelyToHide)
        }

        private fun optimizeImportsSoftly(
            fileImports: Map<FilePath, Set<ImportedName>>,
            optimalTree: OptimalModuleStructure
        ) {
            processRedundantImportedDefinitions(file, fileImports, optimalTree, importRemover)
            val factory = ArendPsiFactory(file.project)
            val allImports = file.statements.filter { it.statCmd?.kind == NamespaceCommand.Kind.IMPORT }.map {
                val text = it.text
                it.delete()
                factory.createFromText(text)!!.statements[0]
            }.sortedByDescending { it.text }
            allImports.forEach { file.addBefore(it, file.firstChild) }
        }

        override fun getUserNotificationInfo(): String =
            if (optimizationResult.coreDefinitionsUsed) ArendBundle.message("arend.optimize.imports.message.core.used")
            else ArendBundle.message("arend.optimize.imports.message.scope.used")
    }
}

val importRemover : (ArendCompositeElement) -> Unit = { element ->
    if (element is ArendNsId) { // TODO: We could reuse existing code for this
        val nextComma = element.findNextSibling()?.takeIf { it.elementType == ArendElementTypes.COMMA }
        val prevComma = element.findPrevSibling()?.takeIf { it.elementType == ArendElementTypes.COMMA }
        if (nextComma != null) {
            nextComma.delete()
        } else if (prevComma != null) {
            prevComma.delete()
        } else {
            // if this function was called, then at least two nsId was imported, and one of them is used
        }
    }
    val singularWhere = element.parentOfType<ArendWhere>()?.takeIf { it.statList.singleOrNull() == element }
    element.delete()
    singularWhere?.delete()
}

fun processRedundantImportedDefinitions(group : ArendGroup, fileImports: Map<FilePath, Set<ImportedName>>, moduleStructure: OptimalModuleStructure, action: (ArendCompositeElement) -> Unit) {
    checkStatements(action, group.statements.filter { it is ArendStat && it.statCmd?.kind == NamespaceCommand.Kind.IMPORT }, fileImports, EMPTY_STRUCTURE, moduleStructure)
    visitModuleInconsistencies(action, group, moduleStructure, moduleStructure.usages, emptyMap())
}

/**
 * @return top-level import statements, that present in the file, but used somewhere in deep subgroups
 */
private fun checkStatements(action: (ArendCompositeElement) -> Unit,
                            statements: List<ArendStatement>,
                            pattern: Map<ModulePath, Set<ImportedName>>,
                            hierarchy: OptimalModuleStructure,
                            openStructure: OptimalModuleStructure = EMPTY_STRUCTURE) : Map<ModulePath, Set<ImportedName>> {
    val deepStatements = mutableMapOf<ModulePath, MutableSet<ImportedName>>()
    val openedImportedNames = openStructure.usages.flatMap { usage -> usage.value.map { import -> ImportedName(usage.key.toString(), import.renamed) } }
    for (stat in statements) {
        val statCmd = stat.namespaceCommand ?: continue
        val qualifiedReferences = statCmd.getQualifiedReferenceFromOpen()
        val importedPattern = qualifiedReferences.flatMapTo(HashSet()) { pattern[it] ?: emptySet() }
        val deepPatterns = qualifiedReferences.flatMapTo(HashSet()) { hierarchy.getDeeplyImportedNames(it) }
        val importedHere = statCmd.nsUsing?.nsIdList ?: emptyList()
        val importedNamesHere = importedHere.map { ImportedName(it.refIdentifier.text, it.defIdentifier?.text, it.refIdentifier.resolve) }
        val importedTopLevelButUsedDeeply = deepPatterns intersect importedNamesHere.toSet()
        if ((importedPattern.isEmpty() && importedTopLevelButUsedDeeply.isEmpty()) || statCmd.nsUsing?.nsIdList?.let { it.isNotEmpty() && it.all { ref ->
            ImportedName(ref.refIdentifier.text, ref.defIdentifier?.text, ref.refIdentifier.resolve) !in (importedPattern + deepPatterns + openedImportedNames)
        } } == true)  {
            action(stat)
            continue
        }
        for ((idx, nsId) in importedHere.withIndex()) {
            val importedName = importedNamesHere[idx]
            if (importedName !in (importedTopLevelButUsedDeeply + importedPattern + openedImportedNames)) {
                action(nsId)
            } else if (importedName !in importedPattern) {
                deepStatements.computeIfAbsent(qualifiedReferences[0]) { mutableSetOf() }.add(importedName)
            }
        }
    }
    return deepStatements
}

/**
 * Record fields may be referenced without actual qualifier for an enclosing record
 */
private fun ArendStatCmd.getQualifiedReferenceFromOpen() : List<ModulePath> {
    val group = openedReference?.resolve?.takeIf { it !is ArendFile } as? ArendGroup
    val groupQualifiedName = group?.qualifiedName()
    val groupReducedQualifiedName = if (group is ArendDefClass) groupQualifiedName?.dropLast(1) else null
    return (listOfNotNull(groupQualifiedName, groupReducedQualifiedName).takeIf { it.isNotEmpty() }
        ?: listOfNotNull(longName?.longName))
        .map { ModulePath(it) }
}

private fun ArendGroup.qualifiedName() : MutableList<String> {
    return if (this is ArendFile) mutableListOf()
    else this.parentGroup?.qualifiedName()?.also { it.add(this.refName) } ?: mutableListOf()
}

private fun visitModuleInconsistencies(action : (ArendCompositeElement) -> Unit, group: ArendGroup, moduleStructure: OptimalModuleStructure?, treeUsages : Map<ModulePath, Set<ImportedName>>, importedOnTopLevel: Map<ModulePath, Set<ImportedName>>) {
    val filteredPattern = HashMap(treeUsages)
    for ((modulePath, set) in importedOnTopLevel) {
        filteredPattern[modulePath] = filteredPattern[modulePath]?.let { it - set }
    }
    val deepStatements = checkStatements(action, group.statements.filter { it.namespaceCommand?.kind == NamespaceCommand.Kind.OPEN }, filteredPattern, moduleStructure ?: EMPTY_STRUCTURE)
    for (subgroup in group.statements.mapNotNull { it.group } + group.dynamicSubgroups) {
        val substructure = moduleStructure?.subgroups?.find { it.name == subgroup.name }
        visitModuleInconsistencies(action, subgroup, substructure, treeUsages + (substructure?.usages ?: emptyMap()), importedOnTopLevel + deepStatements)
    }
}

private val LOG = Logger.getInstance(ArendImportOptimizer::class.java)

@RequiresWriteLock
private fun addFileImports(
    file: ArendFile,
    imports: Map<ModulePath, Set<ImportedName>>,
    allIdentifiers: HashMap<String, Referable>,
) {
    eraseNamespaceCommands(file)
    doAddNamespaceCommands(file, imports, allIdentifiers,"\\import")
}

@RequiresWriteLock
private fun addModuleOpens(
        group: ArendGroup,
        rootStructure: OptimalModuleStructure?,
        alreadyImported: HashMap<String, Referable>
) {
    if (group !is PsiFile) {
        eraseNamespaceCommands(group)
    }
    if (rootStructure != null && rootStructure.usages.isNotEmpty()) {
        doAddNamespaceCommands(group, rootStructure.usages, alreadyImported)
    }
    for (subgroup in group.statements.flatMap { stat -> stat.group?.let { listOf(it) + it.dynamicSubgroups } ?: emptyList() }) {
        val substructure = rootStructure?.subgroups?.find { it.name == subgroup.name }
        val subset = if (substructure == null) alreadyImported else HashMap(alreadyImported)
        addModuleOpens(subgroup, substructure, subset) // remove nested opens
    }
}

private fun doAddNamespaceCommands(
        group: ArendGroup,
        importMap: Map<ModulePath, Set<ImportedName>>,
        alreadyImported: MutableMap<String, Referable>,
        prefix: String = "\\open"
) {
    val importStatements = mutableListOf<String>()
    val settings = CodeStyle.getCustomSettings(group.containingFile, ArendCustomCodeStyleSettings::class.java)
    val scopeProvider = getScopeProvider(prefix == "\\open", group)
    for ((path, identifiers) in importMap.toSortedMap { a, b -> a.toList().joinToString().compareTo(b.toList().joinToString()) }) {
        if (path.toList().isEmpty()) continue
        if (settings.OPTIMIZE_IMPORTS_POLICY == OptimizeImportsPolicy.ONLY_IMPLICIT || identifiers.size > settings.EXPLICIT_IMPORTS_LIMIT) {
            importStatements.add(createImplicitImport(prefix, path, scopeProvider, alreadyImported, identifiers.mapToSet(ImportedName::visibleName)))
            scopeProvider(path)?.globalSubscope?.getElements(null)?.forEach { alreadyImported[it.refName] = it }
        } else {
            importStatements.add(createExplicitImport("$prefix ${path.toList().joinToString(".")}", identifiers))
        }
    }
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

fun getScopeProvider(isForModule: Boolean, group: ArendGroup): ScopeProvider {
    return if (isForModule) {
        {
            group.scope.resolveNamespace(it.toList())
        }
    } else {
        val libraryManager = group.project.service<TypeCheckingService>().libraryManager
        { path ->
            val result = libraryManager.registeredLibraries.firstNotNullOfOrNull { libraryManager.getAvailableModuleScopeProvider(it).forModule(path) }
            if (result == null) println("FOO")
            result
        }
    }
}

private fun createExplicitImport(
    longPrefix: String,
    identifiers: Set<ImportedName>
) = longPrefix + identifiers.map { it.toString() }.sorted().joinToString(", ", " (", ")")

private typealias ScopeProvider = (ModulePath) -> Scope?

private fun createImplicitImport(
    prefix: String,
    modulePath: ModulePath,
    scopeProvider: ScopeProvider,
    alreadyImportedNames: Map<String, Referable>,
    toImportHere: Set<String>
) : String {
    // todo: modules with equal names from different libraries
    val currentScope = scopeProvider(modulePath)
    if (currentScope == null) {
        LOG.error("No library containing required module found while optimizing imports. Please report it to maintainers")
    }
    currentScope!!
    val namesToHide = currentScope.globalSubscope.getElements(null).mapNotNull { ref -> ref.refName.takeIf { it !in toImportHere && it in alreadyImportedNames && alreadyImportedNames[it] != ref } }
    val baseName = "$prefix ${modulePath.toList().joinToString(".")}"
    return if (namesToHide.isEmpty()) {
        baseName
    } else {
        "$baseName ${namesToHide.joinToString(", ", "\\hiding (", ")")}"
    }
}

@RequiresWriteLock
private fun eraseNamespaceCommands(group: ArendGroup) {
    val statCommands = group.statements.filter { it.namespaceCommand != null }
    val deleteWhere = statCommands.size == group.statements.size
    statCommands.forEach { it.delete() }
    if (deleteWhere) {
        group.where?.delete()
    }
}

data class ImportedName(val original: String, val renamed: String?) {
    constructor(original: String, renamed: String?, psi: PsiElement?): this ((psi as? Referable)?.refName ?: original, renamed)

    val visibleName = renamed ?: original
    override fun toString(): String = if (renamed == null) original else "$original \\as $renamed"
}

data class OptimizationResult(
    val fileImports: Map<FilePath, Set<ImportedName>>,
    val openStructure: OptimalModuleStructure,
    val coreDefinitionsUsed: Boolean
)

data class OptimalModuleStructure(
    val name: String,
    val subgroups: List<OptimalModuleStructure>,
    val usages: Map<ModulePath, Set<ImportedName>>,
)

private val EMPTY_STRUCTURE = OptimalModuleStructure("", emptyList(), emptyMap())

private fun OptimalModuleStructure.getDeeplyImportedNames(path: ModulePath) : Set<ImportedName> {
    return (usages[path] ?: emptySet()) + subgroups.flatMap { it.getDeeplyImportedNames(path) }
}

internal fun getOptimalImportStructure(group: ArendGroup, progressIndicator: ProgressIndicator? = null): OptimizationResult {
    val rootFrame = MutableFrame("")
    val file = (group.containingFile as ArendFile)
    val forbiddenFilesToImport = setOfNotNull(file.moduleLocation?.modulePath, Prelude.MODULE_PATH)
    val collector = ImportStructureCollector(rootFrame, progressIndicator)
    val settings = CodeStyle.getCustomSettings(file, ArendCustomCodeStyleSettings::class.java)

    group.accept(collector)
    rootFrame.contract(collector.allDefinitionsTypechecked, collector.fileImports.values.flatMapTo(HashSet()) { it }, settings)
        .forEach { (file, ids) -> collector.fileImports.computeIfAbsent(file) { HashSet() }.addAll(ids) }
    return OptimizationResult(collector.fileImports.filter { it.key !in forbiddenFilesToImport }, rootFrame.asOptimalTree(), collector.allDefinitionsTypechecked)
}

private class ImportStructureCollector(
    rootFrame: MutableFrame,
    private val progressIndicator: ProgressIndicator? = null
)
    : PsiRecursiveElementWalkingVisitor() {
    var allDefinitionsTypechecked = true
    val fileImports: MutableMap<FilePath, MutableSet<ImportedName>> = mutableMapOf()
    private val frameStack: MutableList<MutableFrame> = mutableListOf(rootFrame)
    private val groupStack: MutableList<String> = mutableListOf()
    private val currentFrame get() = frameStack.last()


    override fun visitElement(element: PsiElement) {
        progressIndicator?.checkCanceled()
        if (element is ArendDefinition<*>) {
            currentFrame.definitions.add((element as Referable).refName)
            registerCoClauses(element)
            addCoreGlobalInstances(element)
        }
        if (element is ArendGroup && element !is ArendFile) {
            frameStack.add(MutableFrame(element.refName))
            groupStack.add(element.refName)
            addSyntacticGlobalInstances(element)
        }
        if (element is ArendFile) {
            addSyntacticGlobalInstances(element)
        }

        if (element is ArendGroup) {
            for (statement in element.statements) {
                val namespaceCommand = statement.namespaceCommand
                if (namespaceCommand?.openKw != null) {
                    val resolved = namespaceCommand.openedReference?.resolve as? PsiStubbedReferableImpl<*> ?: continue
                    val (q1, q2) = collectQualifier(resolved) ?: continue
                    currentFrame.activeOpens[Pair(q1, ModulePath(q2.toList() + singletonList(resolved.getName())))] = namespaceCommand
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

    private fun registerCoClauses(element: PsiElement) {
        if (element !is ClassReferable) return
        element.fieldReferables.filterIsInstance<ArendClassField>().forEach { currentFrame.definitions.add(it.refName) }
    }

    private fun addCoreGlobalInstances(element: ArendDefinition<*>) {
        val tcReferable = element.tcReferable?.typechecked
        allDefinitionsTypechecked = allDefinitionsTypechecked && (tcReferable != null && tcReferable.status() == NO_ERRORS)
        if (!allDefinitionsTypechecked) return
        tcReferable!!.accept(object : SearchVisitor<Unit>() { // not-null assertion implied by '&&' above
            override fun visitFunCall(expr: FunCallExpression?, params: Unit?): Boolean {
                val pointerToDefinition = expr?.definition?.referable?.data as? SmartPsiElementPointer<*>
                val globalInstance =
                    (pointerToDefinition?.element as? ArendDefInstance)?.takeIf { it.isDefinitelyInstance() }
                if (globalInstance != null) {
                    currentFrame.instancesFromCore.add(globalInstance)
                }
                return super.visitFunCall(expr, params)
            }
        }, Unit)
    }

    private fun addSyntacticGlobalInstances(element: ArendGroup) {
        // only \open-ed instances can participate in instance candidate resolution
        val scope = element.scope
        val referables =
            element.statements.flatMap { stat -> stat.namespaceCommand?.let { NamespaceCommandNamespace.makeNamespace(scope, it).elements } ?: emptyList() }
        for (instanceCandidate in referables) {
            if (instanceCandidate is ArendDefInstance && instanceCandidate.isDefinitelyInstance()) {
                currentFrame.instancesFromScope.add(instanceCandidate)
            }
        }
    }

    private fun ArendDefInstance.isDefinitelyInstance(): Boolean = functionKind == FunctionKind.INSTANCE

    override fun elementFinished(element: PsiElement?) {
        if (element is ArendGroup && element !is ArendFile) {
            val last = frameStack.removeLast()
            groupStack.removeLast()
            currentFrame.subgroups.add(last)
        }
        super.elementFinished(element)
    }

    private fun visitReferenceElement(element: ArendReferenceElement) {
        // todo stubs
        val resolved = element.reference?.resolve() as? PsiStubbedReferableImpl<*> ?: return
        if (checkIfCanSkipImport(resolved, element)) return
        val (importedFilePath, groupPath) = collectQualifier(resolved) ?: return
        val importedAs = getImportedAs(element, resolved) // \import (a \as b)
        val (openingPath, preCharacteristics) = subtract(
            groupPath.toList(),
            importedFilePath.toList() ?: emptyList(),
            element.longName
        ) ?: return // if reference is 'A.B.c' from File1.X.A.B.c, then it's splitted to ([X], A)
        val characteristics = if (preCharacteristics == importedAs) resolved.refName else preCharacteristics
        val identifierImportedFromFile = groupPath.firstName ?: characteristics
        fileImports.computeIfAbsent(importedFilePath) { HashSet() }.add(ImportedName(identifierImportedFromFile, importedAs?.takeIf { identifierImportedFromFile == characteristics }, null))
        val shortenedOpeningPath = openingPath.shorten(groupStack)
        if (shortenedOpeningPath.isNotEmpty() || importedAs != null) {
            if (openingPath.size > 1) println(openingPath)
            currentFrame.usages[ImportedName(characteristics, importedAs, null)] = ModulePath(openingPath)
        }

        for (frame in frameStack) {
            val key : Pair<FilePath, ModulePath> = Pair(importedFilePath, ModulePath(openingPath))
            val relevantOpen = frame.activeOpens[key]
            if (relevantOpen != null) {
                val lN = relevantOpen.openedReference ?: continue
                for (ref in lN.refIdentifierList)
                    visitReferenceElement(ref)
            }
        }
    }

    private fun getImportedAs(
        element: ArendReferenceElement,
        resolved: PsiStubbedReferableImpl<*>
    ) : String? {
        return element.referenceName.takeIf {
            it != resolved.refName && it != (resolved as? ReferableBase<*>)?.aliasName
        }
    }

    private fun checkIfCanSkipImport(
        resolved: PsiStubbedReferableImpl<*>,
        element: ArendReferenceElement
    ): Boolean {
        val resolvedParentGroup by lazy(LazyThreadSafetyMode.NONE) { resolved.parentOfType<ArendGroup>() }
        val elementGroup by lazy(LazyThreadSafetyMode.NONE) { element.parentOfType<ArendGroup>() ?: element }
        if (/*resolved is ArendDefModule ||*/
            element.parent?.parent is CoClauseBase ||
            (resolved is ArendClassField &&
               resolved.name == element.referenceName && // check against renamed field
               PsiTreeUtil.isAncestor(resolvedParentGroup?.parentGroup, elementGroup, false)
               ) ||
            isSuperAffectsElement(resolvedParentGroup, resolved, elementGroup)
        ) {
            return true
        }
        return false
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
    val usages: MutableMap<ImportedName, ModulePath> = mutableMapOf(),
    val instancesFromScope: MutableList<ArendDefInstance> = mutableListOf(),
    val instancesFromCore: MutableSet<ArendDefInstance> = mutableSetOf(),
    val activeOpens: MutableMap<Pair<FilePath, ModulePath>, ArendStatCmd> = mutableMapOf()
) {
    fun asOptimalTree(): OptimalModuleStructure =
        OptimalModuleStructure(name, subgroups.map { it.asOptimalTree() }, run {
            val reverseMapping = mutableMapOf<ModulePath, MutableSet<ImportedName>>()
            this@MutableFrame.usages.forEach { (id, path) ->
                reverseMapping.computeIfAbsent(path) { HashSet() }.add(id)
            }
            reverseMapping
        })

    @Contract(mutates = "this")
    fun contract(useTypecheckedInstances: Boolean, fileImports: Set<ImportedName>, settings: ArendCustomCodeStyleSettings): Map<ModulePath, Set<ImportedName>> {
        val submaps = subgroups.map { it.contract(useTypecheckedInstances, fileImports, settings) }
        val additionalFiles = mutableMapOf<FilePath, MutableSet<ImportedName>>()
        submaps.forEach {
            it.forEach { (filePath, ids) ->
                additionalFiles.computeIfAbsent(filePath) { HashSet() }.addAll(ids)
            }
        }
        val allInnerIdentifiers = subgroups.flatMapTo(HashSet()) { it.usages.keys }

        for (identifier in allInnerIdentifiers) {
            if (usages.containsKey(identifier)) {
                // the subgroups that open something to bring this identifier will shadow it
                subgroups.forEach {
                    // the 'open' of parent group will be inherited
                    if (it.usages.containsKey(identifier)) {
                        if (settings.OPTIMIZE_IMPORTS_POLICY != OptimizeImportsPolicy.SOFT) it.usages.remove(identifier)
                    }
                }
            }
            if (definitions.contains(identifier.visibleName) || (name == "" && fileImports.contains(identifier))) {
                subgroups.forEach {
                    if (it.usages[identifier] == usages[identifier]) {
                        if (settings.OPTIMIZE_IMPORTS_POLICY != OptimizeImportsPolicy.SOFT) it.usages.remove(identifier)
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
                if (settings.OPTIMIZE_IMPORTS_POLICY != OptimizeImportsPolicy.SOFT) subgroups.forEach { it.usages.remove(identifier) }
            }
        }
        // the order is important. Implicitly used instances are not inherited, so they should be adder after erasing unnecessary usages
        instancesFromCore.addAll(subgroups.flatMap { it.instancesFromCore })
        val instanceSource =
            if (useTypecheckedInstances) instancesFromCore intersect instancesFromScope.toSet() else instancesFromScope
        for (instance in instanceSource) {
            val (importedFile, qualifier) = collectQualifier(instance) ?: continue
            additionalFiles.computeIfAbsent(importedFile) { HashSet() }
                .add(ImportedName(qualifier.firstName ?: instance.refName, null, instance))
            usages[ImportedName(instance.refName, null, instance)] =
                qualifier.takeIf { this@MutableFrame.name == "" || it.toList().isNotEmpty() } ?: importedFile
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
    moduleQualifier: List<String>,
    fileQualifier: List<String>,
    actualQualifier: List<String>
): Pair<List<String>, String>? {
    if (actualQualifier.size > fileQualifier.size && fileQualifier == actualQualifier.take(fileQualifier.size)) {
        return moduleQualifier to actualQualifier[fileQualifier.size]
    }
    if (actualQualifier.size == 1) return moduleQualifier to actualQualifier.last()
    for (index in actualQualifier.indices.reversed().drop(1)) { // start checking module name
        val indexInFullQualifier = moduleQualifier.lastIndex + (index - (actualQualifier.lastIndex - 1))
        if (indexInFullQualifier <= -1) {
            return emptyList<String>() to actualQualifier.last()
        }
        if (moduleQualifier[indexInFullQualifier] != actualQualifier[index]) {
            return null
        }
    }
    return moduleQualifier.take((moduleQualifier.size - (actualQualifier.size - 1)).coerceAtLeast(0)) to actualQualifier[0]
}

typealias FilePath = ModulePath

private fun collectQualifier(element: ArendCompositeElement): Pair<FilePath, ModulePath>? {
    var currentGroup = element.parentOfType<ArendGroup>()
    if (element is ArendConstructor || element is ArendClassField) {
        currentGroup = currentGroup?.parentOfType<ArendGroup>()
    }
    val container = mutableListOf<String>()
    while (currentGroup != null) {
        if (currentGroup is ArendFile) {
            val modulePath = currentGroup.moduleLocation?.modulePath?.toList() ?: return null
            container.reverse()
            return ModulePath(modulePath) to ModulePath(container)
        } else {
            container.add(currentGroup.refName)
        }
        currentGroup = currentGroup.parentGroup
    }
    return null
}