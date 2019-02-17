package org.arend.quickfix

import com.intellij.psi.PsiElement
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.RedirectingReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.*
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.refactoring.*
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group
import org.arend.util.LongName
import java.lang.IllegalStateException
import java.util.Collections.singletonList

class ResolveRefQuickFix {
    companion object {
        fun getDecision(target: PsiLocatedReferable, element: ArendReferenceElement): ResolveReferenceAction? {
            val containingFile = element.containingFile as? ArendFile ?: return null
            val location = LocationData(target)
            val (importAction, resultName) = getDecision(location, containingFile, element) ?: return null
            val renameAction = if ((resultName.size > 1 || (resultName[0] != element.referenceName))) RenameReferenceAction(element, resultName) else null

            return ResolveReferenceAction(target, location.getFullName(), importAction, renameAction)
        }

        fun getDecision(defaultLocation: LocationData, currentFile: ArendFile, anchor: ArendCompositeElement): Pair<AbstractRefactoringAction?, List<String>>? {
            val targetFile = defaultLocation.myContainingFile
            val targetModulePath = defaultLocation.myContainingFile.modulePath ?: return null

            val alternativeLocation = when (defaultLocation.myTarget) {
                is ArendClassFieldSyn, is ArendClassField, is ArendConstructor -> LocationData(defaultLocation.myTarget, true)
                else -> null
            }
            val locations: MutableList<LocationData> = ArrayList()
            locations.add(defaultLocation)
            alternativeLocation?.let { locations.add(it) }

            var modifyingImportsNeeded = false
            var fallbackImportAction: AbstractRefactoringAction? = null

            val fileGroup = object : Group by currentFile {
                override fun getSubgroups(): Collection<Group> = emptyList()
            }
            val importedScope = ScopeFactory.forGroup(fileGroup, currentFile.moduleScopeProvider, false)
            val minimalImportMode = targetFile.subgroups.any { importedScope.resolveName(it.referable.textRepresentation()) != null } // True if imported scope of the current file has nonempty intersection with the scope of the target file
            var suitableImport: ArendStatCmd? = null
            var preludeImportedManually = false

            for (namespaceCommand in currentFile.namespaceCommands) if (namespaceCommand.importKw != null) {
                val isImportPrelude = namespaceCommand.longName?.referent?.textRepresentation() == Prelude.MODULE_PATH.toString()

                if (isImportPrelude) preludeImportedManually = true

                if (namespaceCommand.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() == targetFile) {
                    suitableImport = namespaceCommand // even if some of the members are unused or hidden we still can access them using "very long name"
                    for (location in locations) location.myAliases.addAll(location.calculateShorterNames(namespaceCommand))
                }
            }

            if (isPrelude(targetFile) && !preludeImportedManually) {
                defaultLocation.myAliases.add(defaultLocation.getFullName()) // items from prelude are visible in any context
                fallbackImportAction = ImportFileAction(targetFile, currentFile, null) // however if long name is to be used "\import Prelude" will be added to imports
            }


            if (locations.all { it.myAliases.isEmpty() }) { // target definition is inaccessible in current context
                modifyingImportsNeeded = true

                defaultLocation.myAliases.add(defaultLocation.getFullName())
                if (alternativeLocation != null) {
                    val alternativeFullName = alternativeLocation.getFullName()
                    if (importedScope.resolveName(alternativeFullName[0]) == null) alternativeLocation.myAliases.add(alternativeFullName)
                }

                if (suitableImport != null) { // target definition is hidden or not included into using list but targetFile already has been imported
                    val nsUsing = suitableImport.nsUsing
                    val hiddenList = suitableImport.refIdentifierList

                    for (location in locations) {
                        val locationFullName = location.getFullName()
                        val hiddenRef: ArendRefIdentifier? = hiddenList.lastOrNull { it.referenceName == locationFullName[0] }
                        location.myFileResolveAction = when {
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
                        val fName = location.getFullName()
                        location.myFileResolveAction =
                                if (minimalImportMode) ImportFileAction(targetFile, currentFile, singletonList(fName[0]))
                                else fallbackImportAction
                    }
                }
            }

            val ancestorGroups = ArrayList<Pair<ChildGroup?, List<ArendStatCmd>>>()

            var psi: PsiElement = anchor
            while (psi.parent != null) { //File also passes well (its parent is a directory)
                var statements: List<ArendStatCmd>? = null
                var containingGroup: ChildGroup? = null

                if (psi is ArendWhere) {
                    statements = psi.children.mapNotNull { (it as? ArendStatement)?.statCmd }
                    containingGroup = psi.parent as? ChildGroup /* in fact ArendGroup */
                } else if (psi is ArendFile) {
                    statements = psi.namespaceCommands
                    containingGroup = psi
                }

                if (statements != null)
                    ancestorGroups.add(0, Pair(containingGroup, statements.filter { it.openKw != null }))

                psi = psi.parent
            }

            for (openCommandBlock in ancestorGroups) {
                val currentGroup = openCommandBlock.first

                for (location in locations) {
                    if (currentGroup is PsiLocatedReferable) location.calculateRemainder(currentGroup)?.let { location.myAliases.add(it) }
                    for (openCommand in openCommandBlock.second) location.myAliases.addAll(location.calculateShorterNames(openCommand))
                }
            }

            val elementParent = anchor.parent
            var correctedScope = if (elementParent is ArendLongName) elementParent.scope else anchor.scope
            if (modifyingImportsNeeded && defaultLocation.isNotEmpty())
                correctedScope = MergeScope(correctedScope, defaultLocation.getComplementScope()) // calculate the scope imitating current scope after the imports have been fixed


            val resultingDecisions = ArrayList<Pair<List<String>, AbstractRefactoringAction?>>()

            for (location in locations) {
                location.myAliases.map { alias ->
                    if (alias.isEmpty()) return null // Trivial situation - we are trying to resolve the link to our direct parent :)

                    var referable = Scope.Utils.resolveName(correctedScope, alias)
                    if (referable is RedirectingReferable) referable = referable.originalReferable

                    if (referable is GlobalReferable && PsiLocatedReferable.fromReferable(referable) == defaultLocation.myTarget)
                        resultingDecisions.add(Pair(alias, location.myFileResolveAction))
                }
            }

            val veryLongName = ArrayList<String>()
            if (resultingDecisions.isEmpty()) {
                veryLongName.addAll(targetModulePath.toList()) // If we cannot resolve anything -- then perhaps there is some obstruction in scopes
                veryLongName.addAll(defaultLocation.getFullName()) // Let us use the "longest possible name" when referring to the anchor
                resultingDecisions.add(Pair(veryLongName, fallbackImportAction))
            }

            resultingDecisions.sortBy { LongName(it.first) }

            return if (resultingDecisions.size > 0) {
                val resultingName = resultingDecisions[0].first
                val importAction = if (targetFile != currentFile || resultingName == veryLongName)
                    resultingDecisions[0].second else null // If we use the long name of a file inside the file itself, we are required to import it first via a namespace command

                if (importAction is ImportFileAction && !importAction.isValid())
                    return null //Perhaps current or target directory is not marked as a content root

                return Pair(importAction, resultingName)
            } else null
        }
    }
}

class LocationData(target: PsiLocatedReferable, skipFirstParent: Boolean = false) {
    private val myLongName: List<Pair<String, Referable>>
    val myContainingFile: ArendFile
    val myAliases: MutableList<List<String>> = ArrayList()
    var myFileResolveAction: AbstractRefactoringAction? = null
    val myTarget = target

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

    fun getFullName(): List<String> = myLongName.map { it.first }.toList()

    fun isNotEmpty() = myLongName.isNotEmpty()

    fun getComplementScope(): Scope {
        val targetContainers = myLongName.reversed().map { it.second }
        return object : ListScope(targetContainers) {
            override fun resolveNamespace(name: String?, onlyInternal: Boolean): Scope? = targetContainers
                    .filterIsInstance<ArendGroup>()
                    .firstOrNull { name == it.textRepresentation() }
                    ?.let { LexicalScope.opened(it) }
        }
    }

    fun calculateShorterNames(openCommand: ArendStatCmd): List<List<String>> {
        val lastRef = openCommand.longName?.refIdentifierList?.lastOrNull()
        if (lastRef != null) {
            val openedGroup = lastRef.reference?.resolve()
            if (openedGroup is PsiLocatedReferable) {
                val remainder = calculateRemainder(openedGroup)
                if (remainder != null && remainder.isNotEmpty()) {
                    val currName = remainder[0]
                    val tail = remainder.drop(1).toList()
                    return getImportedNames(openCommand, currName).map { singletonList(it.first) + tail }
                }
            }
        }
        return emptyList()
    }

    fun calculateRemainder(referable: PsiLocatedReferable): List<String>? {
        var result: ArrayList<String>? = if (referable == myContainingFile) ArrayList() else null
        for (entry in myLongName) {
            result?.add(entry.first)
            if (entry.second == referable) {
                result = ArrayList()
            }
        }
        return result
    }
}