package org.arend.refactoring

import com.intellij.psi.PsiElement
import com.intellij.psi.util.isAncestor
import org.arend.ext.module.ModulePath
import org.arend.module.ModuleLocation
import org.arend.naming.reference.AliasReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.*
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.*
import org.arend.term.NamespaceCommand
import org.arend.term.group.AccessModifier
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group
import org.arend.toolWindow.repl.ArendReplService
import org.arend.util.mapFirstNotNull
import java.util.Collections.singletonList

fun doCalculateReferenceName(defaultLocation: LocationData,
                             currentFile: ArendFile,
                             anchor: ArendCompositeElement,
                             allowSelfImport: Boolean = false,
                             deferredImports: List<NsCmdRefactoringAction>? = null): Pair<NsCmdRefactoringAction?, List<String>> {
    val targetFile = defaultLocation.myContainingFile
    val targetModulePath = defaultLocation.myContainingFile.moduleLocation!! //safe to write
    val alternativeLocation = when (defaultLocation.target) {
        is ArendClassField, is ArendConstructor -> LocationData(defaultLocation.target, true)
        else -> null
    }
    val aliasLocation = if (defaultLocation.target.hasAlias()) LocationData(defaultLocation.target, alias = true) else null
    val locations: MutableList<LocationData> = ArrayList()
    alternativeLocation?.let { locations.add(it) }
    aliasLocation?.let { locations.add(it) }
    locations.add(defaultLocation)

    var modifyingImportsNeeded = false
    var fallbackImportAction: NsCmdRefactoringAction? = null

    val fileGroup = object : Group by currentFile {
        override fun getStatements() = currentFile.statements.filter { it.group == null }
    }
    val importedScope = CachingScope.make(ScopeFactory.forGroup(fileGroup, currentFile.moduleScopeProvider, false))
    val protectedAccessModifier = defaultLocation.target.accessModifier == AccessModifier.PROTECTED
    val minimalImportMode = protectedAccessModifier || targetFile.statements.any { stat -> stat.group?.let { importedScope.resolveName(it.referable.textRepresentation()) } != null } // True if imported scope of the current file has nonempty intersection with the scope of the target file
    var targetFileAlreadyImported = false
    var preludeImportedManually = false
    val fileResolveActions = HashMap<LocationData, NsCmdRefactoringAction?>()

    for (statement in currentFile.statements) {
        val command = statement.namespaceCommand ?: continue
        if (command.importKw != null) {
            val nsCmdLongName = command.longName?.referent?.textRepresentation()
            preludeImportedManually = preludeImportedManually || nsCmdLongName == Prelude.MODULE_PATH.toString()

            if (nsCmdLongName == targetFile.fullName) {
                targetFileAlreadyImported = true // even if some members are unused or hidden we still can access them using "very long name"
                for (location in locations) location.processStatCmd(command)
            }
        }
    }

    if (deferredImports != null) for (deferredImport in deferredImports) if (deferredImport.currentFile == currentFile) {
        if (deferredImport is ImportFileAction) preludeImportedManually = preludeImportedManually || deferredImport.getLongName().toString() == Prelude.MODULE_PATH.toString()
        if (deferredImport.getLongName().toString() == targetFile.fullName) {
            targetFileAlreadyImported = true
            for (location in locations) location.processDeferredImport(deferredImport)
        }
    }

    if (isPrelude(targetFile) && !preludeImportedManually) {
        defaultLocation.addLongNameAsReferenceName() // items from prelude are visible in any context
        alternativeLocation?.addLongNameAsReferenceName()
        fallbackImportAction = ImportFileAction(currentFile, targetFile, null) // however if long name is to be used "\import Prelude" will be added to imports
    }

    if (locations.first().getReferenceNames().isEmpty() || protectedAccessModifier) { // target definition is inaccessible in current context
        modifyingImportsNeeded = true

        defaultLocation.addLongNameAsReferenceName()
        aliasLocation?.addLongNameAsReferenceName()
        if (alternativeLocation != null) {
            val alternativeFullName = alternativeLocation.getLongName()
            if (importedScope.resolveName(alternativeFullName[0]) == null) alternativeLocation.addLongNameAsReferenceName()
        }

        if (targetFileAlreadyImported) { // target definition is hidden or not included into using list but targetFile already has been imported
            for (location in locations) if (location.getLongName().isNotEmpty())
                fileResolveActions[location] = AddIdToUsingAction(currentFile, targetFile, location)

            fallbackImportAction = null
        } else { // targetFile has not been imported
            fallbackImportAction = ImportFileAction(currentFile, targetFile, if (minimalImportMode) emptyList() else null)

            for (location in locations) {
                val fName = location.getLongName()
                val importList = if (fName.isEmpty()) emptyList() else singletonList(fName[0])
                fileResolveActions[location] = if (minimalImportMode) ImportFileAction(currentFile, targetFile, importList) else fallbackImportAction
            }
        }
    }

    val ancestorGroups = ArrayList<Pair<ChildGroup?, List<ArendStatCmd>>>()

    var psi: PsiElement = anchor
    while (psi.parent != null) {
        val containingGroup: ChildGroup? = when (psi) {
            is ArendWhere -> psi.parent as? ChildGroup
            is ArendFile -> psi
            is ArendDefClass -> psi
            else -> null
        }

        val statements: List<ArendStatCmd>? = containingGroup?.statements?.mapNotNull { it.namespaceCommand as? ArendStatCmd }

        if (!allowSelfImport) if (psi is PsiLocatedReferable && psi.isAncestor(defaultLocation.target))
            defaultLocation.processParentGroup(psi)

        if (statements != null)
            ancestorGroups.add(0, Pair(containingGroup, statements.filter { it.kind == NamespaceCommand.Kind.OPEN }))

        psi = psi.parent
    }

    for (openCommandBlock in ancestorGroups) {
        val currentGroup = openCommandBlock.first

        for (location in locations) {
            if (currentGroup is PsiLocatedReferable) location.processParentGroup(currentGroup)
            for (openCommand in openCommandBlock.second) location.processStatCmd(openCommand)
        }
    }

    val elementParent = anchor.parent
    var correctedScope = if (elementParent is ArendLongName) elementParent.scope else anchor.scope
    if (modifyingImportsNeeded && defaultLocation.getLongName().isNotEmpty())
        correctedScope = MergeScope(correctedScope, defaultLocation.getComplementScope()) // calculate the scope imitating current scope after the imports have been fixed


    data class ImportDecision(val refName: List<String>, val nsAction: NsCmdRefactoringAction?, val isAlias: Boolean = false): Comparable<ImportDecision> {
        override fun compareTo(other: ImportDecision): Int {
            val lD = this.refName.size - other.refName.size
            if (lD != 0) return lD // if this is more optimal => result < 0 => this < other
            if (this.isAlias && !other.isAlias) return -1
            if (!this.isAlias && other.isAlias) return 1 // other is more optimal
            return compareValues(this.refName.first(), this.refName.first())
        }
    }

    val resultingDecisions = ArrayList<ImportDecision>()

    for (location in locations) {
        location.getReferenceNames().map { referenceName ->
            if (referenceName.isEmpty() || Scope.resolveName(correctedScope, referenceName)?.underlyingReferable == defaultLocation.target) {
                resultingDecisions.add(ImportDecision(referenceName, fileResolveActions[location], location.alias))
            }
        }
    }

    val veryLongName = ArrayList<String>()
    if (resultingDecisions.isEmpty()) {
        veryLongName.addAll(targetModulePath.modulePath.toList()) // If we cannot resolve anything -- then perhaps there is some obstruction in scopes
        veryLongName.addAll(defaultLocation.getLongName()) // Let us use the "longest possible name" when referring to the anchor
        resultingDecisions.add(ImportDecision(veryLongName, fallbackImportAction, false))
    }
    resultingDecisions.sort()

    val resultingName = resultingDecisions[0].refName // most optimal name comes first
    val importAction = if (targetFile != currentFile || (resultingName.isNotEmpty() || allowSelfImport) && resultingName == veryLongName)
        resultingDecisions[0].nsAction else null // If we use the long name of a file inside the file itself, we are required to import it first via a namespace command

    return Pair(importAction, resultingName)
}

fun isVisible(importFile: ArendFile, currentFile: ArendFile): Boolean {
    val modulePath = importFile.moduleLocation?.modulePath ?: return false
    val locationsOk = importFile.moduleLocation != null && importFile.moduleLocation?.modulePath != null

    if (currentFile.isRepl) return locationsOk

    val conf = currentFile.arendLibrary?.config ?: return false
    val inTests = conf.getFileLocationKind(currentFile) == ModuleLocation.LocationKind.TEST

    return locationsOk && (importFile.generatedModuleLocation != null || conf.availableConfigs.mapFirstNotNull { it.findArendFile(modulePath, true, inTests) } == importFile) //Needed to prevent attempts of link repairing in a situation when the target directory is not marked as a content root
}


fun calculateReferenceName(defaultLocation: LocationData,
                           currentFile: ArendFile,
                           anchor: ArendCompositeElement,
                           allowSelfImport: Boolean = false,
                           deferredImports: List<NsCmdRefactoringAction>? = null): Pair<NsCmdRefactoringAction?, List<String>>? {
    if (defaultLocation.target.accessModifier == AccessModifier.PRIVATE) return null
    if (!isVisible(defaultLocation.myContainingFile, currentFile)) return null
    return doCalculateReferenceName(defaultLocation, currentFile, anchor, allowSelfImport, deferredImports)
}

class LocationData(val target: PsiLocatedReferable, skipFirstParent: Boolean = false, val alias: Boolean = false) {
    private val myLongNameWithRefs: List<Pair<String, Referable>>
    private val myReferenceNames: MutableSet<List<String>> = HashSet()
    val myContainingFile: ArendFile

    init {
        var psi: PsiElement? = target
        var skipFlag = skipFirstParent
        var containingFile: ArendFile? = null

        myLongNameWithRefs = ArrayList()
        while (psi != null) {
            if (psi is PsiReferable && psi !is ArendFile) {
                val name = if (psi is GlobalReferable) {
                    if (alias) psi.representableName else psi.textRepresentation()
                } else
                    psi.name ?: throw IllegalStateException() //Fix later :)

                if (skipFlag && myLongNameWithRefs.isNotEmpty()) {
                    skipFlag = false
                } else {
                    myLongNameWithRefs.add(0, Pair(name, psi))
                }
            }
            if (psi is ArendFile) containingFile = psi
            psi = psi.parent
        }

        myContainingFile = containingFile ?: throw IllegalStateException() //Fix later :)
    }

    fun getLongName(): List<String> = myLongNameWithRefs.map { it.first }.toList()

    fun getComplementScope(): Scope {
        val targetContainers = myLongNameWithRefs.reversed().map { it.second }
        return object : ListScope(targetContainers + targetContainers.mapNotNull { if (it is GlobalReferable) AliasReferable(it) else null }) {
            override fun resolveNamespace(name: String, onlyInternal: Boolean): Scope? = targetContainers
                    .filterIsInstance<ArendGroup>()
                    .firstOrNull { name == it.textRepresentation() || name == it.aliasName }
                    ?.let { LexicalScope.opened(it) }
        }
    }

    fun processStatCmd(statCmd: ArendStatCmd) {
        myReferenceNames.addAll(calculateShorterNames(statCmd))
    }

    fun processDeferredImport(deferredAction: NsCmdRefactoringAction) {
        if (deferredAction.getLongName() == myContainingFile.moduleLocation?.modulePath) {
            val elements = deferredAction.getImportedParts()
            val topLevelModule = myLongNameWithRefs.firstOrNull()
            if (elements == null || (topLevelModule != null && elements.contains(topLevelModule.first))) addLongNameAsReferenceName()
        }
    }

    fun processParentGroup(group: PsiLocatedReferable) {
        calculateRemainder(group)?.let { myReferenceNames.add(it) }
    }

    fun addLongNameAsReferenceName() {
        myReferenceNames.add(getLongName())
    }

    fun getReferenceNames(): Set<List<String>> = myReferenceNames

    private fun calculateShorterNames(statCmd: ArendStatCmd): List<List<String>> {
        val lastRef = statCmd.longName?.refIdentifierList?.lastOrNull()
        if (lastRef != null) {
            val openedGroup = lastRef.reference.resolve()
            if (openedGroup is PsiLocatedReferable) {
                val remainder = calculateRemainder(openedGroup)
                if (!remainder.isNullOrEmpty()) {
                    val currName = remainder[0]
                    val tail = remainder.drop(1).toList()
                    return getImportedNames(statCmd, currName).map { singletonList(it.first) + tail }
                }
            }
        }
        return emptyList()
    }

    private fun calculateRemainder(referable: PsiLocatedReferable): List<String>? {
        var result: ArrayList<String>? = if (referable == myContainingFile) ArrayList() else null
        for (entry in myLongNameWithRefs) {
            result?.add(entry.first)
            if (entry.second == referable) result = ArrayList()
        }
        return result
    }
}

abstract class NsCmdRefactoringAction(val currentFile: ArendFile) {
    abstract fun execute()

    abstract fun getImportedParts(): List<String>?

    abstract fun getLongName(): ModulePath

    abstract fun getAmendedScope(): Scope
}

class ImportFileAction(currentFile: ArendFile,
                       val targetFile: ArendFile,
                       private val usingList: List<String>?) : NsCmdRefactoringAction(currentFile) {
    override fun toString() = "Import file ${getLongName()}"

    override fun execute() {
        val factory = ArendPsiFactory(currentFile.project)
        val statCmdStatement = createStatCmdStatement(factory, getLongName().toString(), usingList?.map { Pair(it, null as String?) }?.toList(), ArendPsiFactory.StatCmdKind.IMPORT)

        if (currentFile.isRepl) {
            val replService = currentFile.project.getService(ArendReplService::class.java)
            replService.getRepl()?.repl(statCmdStatement.text) {""}
            return
        }

        addStatCmd(factory, statCmdStatement, findPlaceForNsCmd(currentFile, getLongName()))
    }

    override fun getImportedParts(): List<String>? = usingList

    override fun getLongName(): ModulePath = targetFile.moduleLocation?.modulePath!!
    override fun getAmendedScope(): Scope = targetFile.scope
}

class AddIdToUsingAction(currentFile: ArendFile,
                         val targetFile: ArendFile,
                         val locationData: LocationData) : NsCmdRefactoringAction(currentFile) {
    private val myId = locationData.getLongName()[0]
    override fun toString(): String = "Add $myId to the \"using\" list of the namespace command `${getLongName()}`"

    override fun execute() {
        /* locate statCmd using longName */
        var statCmd: ArendStatCmd? = null
        for (statement in currentFile.statements) {
            val namespaceCommand = statement.namespaceCommand ?: continue
            if (namespaceCommand.importKw != null) {
                val nsCmdLongName = namespaceCommand.longName?.referent?.textRepresentation()
                if (nsCmdLongName == getLongName().toString()) {
                    statCmd = namespaceCommand
                    break
                }
            }
        }

        if (statCmd == null) return
        /* if statCmd was found -- execute refactoring action */
        val hiddenList = statCmd.refIdentifierList
        val hiddenRef: ArendRefIdentifier? = hiddenList.lastOrNull { it.referenceName == myId }
        if (hiddenRef == null) doAddIdToUsing(statCmd, singletonList(Pair(myId, null)), locationData.target.accessModifier == AccessModifier.PROTECTED) else doRemoveRefFromStatCmd(hiddenRef)
    }

    override fun getLongName(): ModulePath = targetFile.moduleLocation?.modulePath!!

    override fun getImportedParts(): List<String>? = singletonList(myId)

    override fun getAmendedScope(): Scope = ListScope(singletonList(locationData.target))
}