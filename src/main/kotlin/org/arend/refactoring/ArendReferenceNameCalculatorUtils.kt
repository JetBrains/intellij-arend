package org.arend.refactoring

import com.intellij.openapi.components.ServiceManager
import com.intellij.psi.PsiElement
import org.arend.ext.module.LongName
import org.arend.ext.module.ModulePath
import org.arend.module.ModuleLocation
import org.arend.naming.reference.AliasReferable
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.*
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.term.NamespaceCommand
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
    val targetFilePath = targetFile.moduleLocation?.modulePath!! //safe to write (see canCalculateReferenceName)
    val targetModulePath = defaultLocation.myContainingFile.moduleLocation!! //safe to write
    val alternativeLocation = when (defaultLocation.target) {
        is ArendClassField, is ArendConstructor -> LocationData(defaultLocation.target, true)
        else -> null
    }
    val locations: MutableList<LocationData> = ArrayList()
    alternativeLocation?.let { locations.add(it) }
    locations.add(defaultLocation)

    var modifyingImportsNeeded = false
    var fallbackImportAction: NsCmdRefactoringAction? = null

    val fileGroup = object : Group by currentFile {
        override fun getSubgroups(): Collection<Group> = emptyList()
    }
    val importedScope = CachingScope.make(ScopeFactory.forGroup(fileGroup, currentFile.moduleScopeProvider, false))
    val minimalImportMode = targetFile.subgroups.any { importedScope.resolveName(it.referable.textRepresentation()) != null } // True if imported scope of the current file has nonempty intersection with the scope of the target file
    var targetFileAlreadyImported = false
    var preludeImportedManually = false
    val fileResolveActions = HashMap<LocationData, NsCmdRefactoringAction?>()

    for (namespaceCommand in currentFile.namespaceCommands) if (namespaceCommand.importKw != null) {
        val nsCmdLongName = namespaceCommand.longName?.referent?.textRepresentation()
        preludeImportedManually = preludeImportedManually || nsCmdLongName == Prelude.MODULE_PATH.toString()

        if (nsCmdLongName == targetFile.fullName) {
            targetFileAlreadyImported = true // even if some of the members are unused or hidden we still can access them using "very long name"
            for (location in locations) location.processStatCmd(namespaceCommand)
        }
    }

    if (deferredImports != null) for (deferredImport in deferredImports) if (deferredImport.currentFile == currentFile) {
        if (deferredImport is ImportFileAction) preludeImportedManually = preludeImportedManually || deferredImport.longName.toString() == Prelude.MODULE_PATH.toString()
        if (deferredImport.longName.toString() == targetFile.fullName) {
            targetFileAlreadyImported = true
            for (location in locations) location.processDeferredImport(deferredImport)
        }
    }

    if (isPrelude(targetFile) && !preludeImportedManually) {
        defaultLocation.addLongNameAsReferenceName() // items from prelude are visible in any context
        alternativeLocation?.addLongNameAsReferenceName()
        fallbackImportAction = ImportFileAction(currentFile, targetFilePath, null) // however if long name is to be used "\import Prelude" will be added to imports
    }

    if (locations.first().getReferenceNames().isEmpty()) { // target definition is inaccessible in current context
        modifyingImportsNeeded = true

        defaultLocation.addLongNameAsReferenceName()
        if (alternativeLocation != null) {
            val alternativeFullName = alternativeLocation.getLongName()
            if (importedScope.resolveName(alternativeFullName[0]) == null) alternativeLocation.addLongNameAsReferenceName()
        }

        if (targetFileAlreadyImported) { // target definition is hidden or not included into using list but targetFile already has been imported
            for (location in locations) if (location.getLongName().isNotEmpty())
                fileResolveActions[location] = AddIdToUsingAction(currentFile, targetFilePath, location.getLongName()[0])

            fallbackImportAction = null
        } else { // targetFile has not been imported
            fallbackImportAction = ImportFileAction(currentFile, targetFilePath, if (minimalImportMode) emptyList() else null)

            for (location in locations) {
                val fName = location.getLongName()
                val importList = if (fName.isEmpty()) emptyList() else singletonList(fName[0])
                fileResolveActions[location] = if (minimalImportMode) ImportFileAction(currentFile, targetFilePath, importList) else fallbackImportAction
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

        val statements: List<ArendStatCmd>? = containingGroup?.namespaceCommands?.filterIsInstance<ArendStatCmd>()?.toList()

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


    val resultingDecisions = ArrayList<Pair<List<String>, NsCmdRefactoringAction?>>()

    for (location in locations) {
        location.getReferenceNames().map { referenceName ->
            if (referenceName.isEmpty() || Scope.Utils.resolveName(correctedScope, referenceName)?.underlyingReferable == defaultLocation.target) {
                resultingDecisions.add(Pair(referenceName, fileResolveActions[location]))
            }
        }
    }

    val veryLongName = ArrayList<String>()
    if (resultingDecisions.isEmpty()) {
        veryLongName.addAll(targetModulePath.modulePath.toList()) // If we cannot resolve anything -- then perhaps there is some obstruction in scopes
        veryLongName.addAll(defaultLocation.getLongName()) // Let us use the "longest possible name" when referring to the anchor
        resultingDecisions.add(Pair(veryLongName, fallbackImportAction))
    }

    resultingDecisions.sortBy { LongName(it.first) }

    val resultingName = resultingDecisions[0].first
    val importAction = if (targetFile != currentFile || (resultingName.isNotEmpty() || allowSelfImport) && resultingName == veryLongName)
        resultingDecisions[0].second else null // If we use the long name of a file inside the file itself, we are required to import it first via a namespace command

    return Pair(importAction, resultingName)
}

fun canCalculateReferenceName(defaultLocation: LocationData, currentFile: ArendFile): Boolean {
    val importFile = defaultLocation.myContainingFile
    val modulePath = importFile.moduleLocation?.modulePath ?: return false
    val locationsOk = defaultLocation.myContainingFile.moduleLocation != null && importFile.moduleLocation?.modulePath != null

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
    if (!canCalculateReferenceName(defaultLocation, currentFile)) return null
    return doCalculateReferenceName(defaultLocation, currentFile, anchor, allowSelfImport, deferredImports)
}

class LocationData(val target: PsiLocatedReferable, skipFirstParent: Boolean = false) {
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
                val name = if (psi is GlobalReferable) psi.representableName else
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
            override fun resolveNamespace(name: String?, onlyInternal: Boolean): Scope? = targetContainers
                    .filterIsInstance<ArendGroup>()
                    .firstOrNull { name == it.textRepresentation() || name == it.aliasName }
                    ?.let { LexicalScope.opened(it) }
        }
    }

    fun processStatCmd(statCmd: ArendStatCmd) {
        myReferenceNames.addAll(calculateShorterNames(statCmd))
    }

    fun processDeferredImport(deferredAction: NsCmdRefactoringAction) {
        if (deferredAction.longName == myContainingFile.moduleLocation?.modulePath) {
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
            val openedGroup = lastRef.reference?.resolve()
            if (openedGroup is PsiLocatedReferable) {
                val remainder = calculateRemainder(openedGroup)
                if (remainder != null && remainder.isNotEmpty()) {
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

abstract class NsCmdRefactoringAction(val currentFile: ArendFile,
                                      val longName: ModulePath) {
    abstract fun execute()

    abstract fun getImportedParts(): List<String>?
}

class ImportFileAction(currentFile: ArendFile,
                       longName: ModulePath,
                       val usingList: List<String>?) : NsCmdRefactoringAction(currentFile, longName) {
    override fun toString() = "Import file $longName"

    override fun execute() {
        val factory = ArendPsiFactory(currentFile.project)
        val statCmdStatement = createStatCmdStatement(factory, longName.toString(), usingList?.map { Pair(it, null as String?) }?.toList(), ArendPsiFactory.StatCmdKind.IMPORT)

        if (currentFile.isRepl) {
            val replService = ServiceManager.getService(currentFile.project, ArendReplService::class.java)
            replService.getRepl()?.repl(statCmdStatement.text) {""}
            return
        }

        addStatCmd(factory, statCmdStatement, findPlaceForNsCmd(currentFile, longName))
    }

    override fun getImportedParts(): List<String>? = usingList
}

class AddIdToUsingAction(currentFile: ArendFile,
                         longName: ModulePath,
                         val id: String) : NsCmdRefactoringAction(currentFile, longName) {
    override fun toString(): String = "Add $id to the \"using\" list of the namespace command `$longName`"

    override fun execute() {
        /* locate statCmd using longName */
        var statCmd: ArendStatCmd? = null
        for (namespaceCommand in currentFile.namespaceCommands) if (namespaceCommand.importKw != null) {
            val nsCmdLongName = namespaceCommand.longName?.referent?.textRepresentation()
            if (nsCmdLongName == longName.toString()) {
                statCmd = namespaceCommand
                break
            }
        }

        if (statCmd == null) return
        /* if statCmd was found -- execute refactoring action */
        val hiddenList = statCmd.refIdentifierList
        val hiddenRef: ArendRefIdentifier? = hiddenList.lastOrNull { it.referenceName == id }
        if (hiddenRef == null) doAddIdToUsing(statCmd, singletonList(Pair(id, null))) else doRemoveRefFromStatCmd(hiddenRef)
    }

    override fun getImportedParts(): List<String>? = singletonList(id)
}