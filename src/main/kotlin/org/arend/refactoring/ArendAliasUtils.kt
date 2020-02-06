package org.arend.refactoring

import com.intellij.psi.PsiElement
import org.arend.ext.module.LongName
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
import java.util.Collections.singletonList

fun computeAliases(defaultLocation: LocationData, currentFile: ArendFile, anchor: ArendCompositeElement, allowSelfImport: Boolean = false): Pair<AbstractRefactoringAction?, List<String>>? {
    val targetFile = defaultLocation.myContainingFile
    val targetModulePath = defaultLocation.myContainingFile.modulePath ?: return null

    val alternativeLocation = when (defaultLocation.target) {
        is ArendClassField, is ArendConstructor -> LocationData(defaultLocation.target, true)
        else -> null
    }
    val locations: MutableList<LocationData> = ArrayList()
    alternativeLocation?.let { locations.add(it) }
    locations.add(defaultLocation)

    var modifyingImportsNeeded = false
    var fallbackImportAction: AbstractRefactoringAction? = null

    val fileGroup = object : Group by currentFile {
        override fun getSubgroups(): Collection<Group> = emptyList()
    }
    val importedScope = ScopeFactory.forGroup(fileGroup, currentFile.moduleScopeProvider, false)
    val minimalImportMode = targetFile.subgroups.any { importedScope.resolveName(it.referable.textRepresentation()) != null } // True if imported scope of the current file has nonempty intersection with the scope of the target file
    var suitableImport: ArendStatCmd? = null
    var preludeImportedManually = false
    val fileResolveActions = HashMap<LocationData, AbstractRefactoringAction?>()

    for (namespaceCommand in currentFile.namespaceCommands) if (namespaceCommand.importKw != null) {
        val isImportPrelude = namespaceCommand.longName?.referent?.textRepresentation() == Prelude.MODULE_PATH.toString()

        if (isImportPrelude) preludeImportedManually = true

        if (namespaceCommand.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() == targetFile) {
            suitableImport = namespaceCommand // even if some of the members are unused or hidden we still can access them using "very long name"
            for (location in locations) location.processStatCmd(namespaceCommand)
        }
    }

    if (isPrelude(targetFile) && !preludeImportedManually) {
        defaultLocation.addLongNameAsAlias() // items from prelude are visible in any context
        fallbackImportAction = ImportFileAction(targetFile, currentFile, null) // however if long name is to be used "\import Prelude" will be added to imports
    }


    if (locations.first().getAliases().isEmpty()) { // target definition is inaccessible in current context
        modifyingImportsNeeded = true

        defaultLocation.addLongNameAsAlias()
        if (alternativeLocation != null) {
            val alternativeFullName = alternativeLocation.getLongName()
            if (importedScope.resolveName(alternativeFullName[0]) == null) alternativeLocation.addLongNameAsAlias()
        }

        if (suitableImport != null) { // target definition is hidden or not included into using list but targetFile already has been imported
            val nsUsing = suitableImport.nsUsing
            val hiddenList = suitableImport.refIdentifierList

            for (location in locations) if (location.getLongName().isNotEmpty()) {
                val locationFullName = location.getLongName()
                val hiddenRef: ArendRefIdentifier? = hiddenList.lastOrNull { it.referenceName == locationFullName[0] }
                fileResolveActions[location] = when {
                    hiddenRef != null -> RemoveRefFromStatCmdAction(suitableImport, hiddenRef)
                    nsUsing != null -> AddIdToUsingAction(suitableImport, singletonList(Pair(locationFullName[0], null)))
                    else -> null
                }
            }

            fallbackImportAction = null
        } else { // targetFile has not been imported
            fallbackImportAction =
                    if (minimalImportMode) ImportFileAction(targetFile, currentFile, emptyList())
                    else ImportFileAction(targetFile, currentFile, null)

            for (location in locations) {
                val fName = location.getLongName()
                val importList = if (fName.isEmpty()) emptyList() else singletonList(fName[0])
                fileResolveActions[location] = if (minimalImportMode) ImportFileAction(targetFile, currentFile, importList) else fallbackImportAction
            }
        }
    }

    val ancestorGroups = ArrayList<Pair<ChildGroup?, List<ArendStatCmd>>>()

    var psi: PsiElement = anchor
    while (psi.parent != null) { //File also passes well (its parent is a directory)
        val containingGroup: ChildGroup? = when (psi) {
            is ArendWhere -> psi.parent as? ChildGroup /* in fact ArendGroup */
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


    val resultingDecisions = ArrayList<Pair<List<String>, AbstractRefactoringAction?>>()

    for (location in locations) {
        location.getAliases().map { alias ->
            if (alias.isEmpty() || Scope.Utils.resolveName(correctedScope, alias)?.underlyingReferable == defaultLocation.target) {
                resultingDecisions.add(Pair(alias, fileResolveActions[location]))
            }
        }
    }

    val veryLongName = ArrayList<String>()
    if (resultingDecisions.isEmpty()) {
        veryLongName.addAll(targetModulePath.toList()) // If we cannot resolve anything -- then perhaps there is some obstruction in scopes
        veryLongName.addAll(defaultLocation.getLongName()) // Let us use the "longest possible name" when referring to the anchor
        resultingDecisions.add(Pair(veryLongName, fallbackImportAction))
    }

    resultingDecisions.sortBy { LongName(it.first) }

    val resultingName = resultingDecisions[0].first
    val importAction = if (targetFile != currentFile || (resultingName.isNotEmpty() || allowSelfImport) && resultingName == veryLongName)
        resultingDecisions[0].second else null // If we use the long name of a file inside the file itself, we are required to import it first via a namespace command

    if (importAction is ImportFileAction && !importAction.isValid())
        return null //Perhaps current or target directory is not marked as a content root

    return Pair(importAction, resultingName)
}

class LocationData(val target: PsiLocatedReferable, skipFirstParent: Boolean = false) {
    private val myLongName: List<Pair<String, Referable>>
    private val myAliases: MutableSet<List<String>> = HashSet()
    val myContainingFile: ArendFile

    init {
        var psi: PsiElement? = target
        var skipFlag = skipFirstParent
        var containingFile: ArendFile? = null

        myLongName = ArrayList()
        while (psi != null) {
            if (psi is PsiReferable && psi !is ArendFile) {
                val name = psi.name ?: throw IllegalStateException() //Fix later :)

                if (skipFlag && myLongName.isNotEmpty()) {
                    skipFlag = false
                } else {
                    myLongName.add(0, Pair(name, psi))
                }
            }
            if (psi is ArendFile) containingFile = psi
            psi = psi.parent
        }

        myContainingFile = containingFile ?: throw IllegalStateException() //Fix later :)
    }

    fun getLongName(): List<String> = myLongName.map { it.first }.toList()

    fun getComplementScope(): Scope {
        val targetContainers = myLongName.reversed().map { it.second }
        return object : ListScope(targetContainers) {
            override fun resolveNamespace(name: String?, onlyInternal: Boolean): Scope? = targetContainers
                    .filterIsInstance<ArendGroup>()
                    .firstOrNull { name == it.textRepresentation() }
                    ?.let { LexicalScope.opened(it) }
        }
    }

    fun processStatCmd(statCmd: ArendStatCmd) {
        myAliases.addAll(calculateShorterNames(statCmd))
    }

    fun processParentGroup(group: PsiLocatedReferable) {
        calculateRemainder(group)?.let { myAliases.add(it) }
    }

    fun addLongNameAsAlias() {
        myAliases.add(getLongName())
    }

    fun getAliases(): Set<List<String>> = myAliases

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
        for (entry in myLongName) {
            result?.add(entry.first)
            if (entry.second == referable) result = ArrayList()
        }
        return result
    }
}