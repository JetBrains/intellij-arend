package org.arend.quickfix

import com.intellij.psi.PsiElement
import com.intellij.psi.util.parents
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
import java.lang.IllegalStateException
import java.util.Collections.singletonList

class ResolveRefQuickFix {
    companion object {
        fun getDecision(target: PsiLocatedReferable, element: ArendReferenceElement): ResolveRefFixData? {
            val containingFile = element.containingFile as? ArendFile ?: return null
            val (importAction, resultName) = getDecision(target, containingFile, element) ?: return null

            val renameAction = if ((resultName.size > 1 || (resultName[0] != element.referenceName)))
                RenameReferenceAction(element, resultName) else null
            return ResolveRefFixData(target, importAction, renameAction)
        }

        class LocationData(target: PsiLocatedReferable, boundingGroup: ChildGroup?) {
            private val myLongName: List<Pair<String, Referable>>
            private val myAlternativeName: MutableList<Pair<String, Referable>>?

            init {
                var psi: PsiElement = target
                var ignoreFlag = true

                myLongName = ArrayList()
                myAlternativeName = when (target) {
                    is ArendClassFieldSyn, is ArendClassField, is ArendConstructor -> ArrayList()
                    else -> null
                }
                while (psi.parent != null) {
                    if (psi is PsiReferable && psi !is ArendFile && psi != boundingGroup) {
                        val name = psi.name ?: throw IllegalStateException() //Fix later :)

                        myLongName.add(0, Pair(name, psi))
                        if (myAlternativeName != null) {
                            if (ignoreFlag && myAlternativeName.isNotEmpty()) {
                                ignoreFlag = false
                            } else {
                                myAlternativeName.add(0, Pair(name, psi))
                            }
                        }
                    }
                    psi = psi.parent
                }
            }

            fun getFullName(altName: Boolean = false): MutableList<String>? {
                val source = if (altName) myAlternativeName else myLongName
                return source?.map { it.first }?.toMutableList()
            }

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

            fun calculateShorterName(openCommand: ArendStatCmd, altName: Boolean): List<String>? {
                val lastRef = openCommand.longName?.refIdentifierList?.lastOrNull()
                if (lastRef != null) {
                    val openedGroup = lastRef.reference?.resolve()
                    if (openedGroup is PsiLocatedReferable) {
                        val remainder = calculateRemainder(openedGroup, altName)
                        if (remainder != null && remainder.isNotEmpty()) {
                            val currName = remainder[0]
                            val tail = remainder.drop(1).toList()
                            val newName = getImportedName(openCommand, currName)
                            if (newName != null) {
                                return singletonList(newName.first) + tail
                            }
                        }
                    }
                }
                return null
            }

            private fun calculateRemainder(referable: PsiLocatedReferable, altName: Boolean): List<String>? {
                val source = (if (altName) myAlternativeName else myLongName) ?: return null
                var result: ArrayList<String>? = null
                for (entry in source) {
                    result?.add(entry.first)
                    if (entry.second == referable) {
                        result = ArrayList()
                    }
                }
                return result
            }
        }

        fun getDecision(target: PsiLocatedReferable,
                        currentFile: ArendFile,
                        anchor: ArendCompositeElement): Pair<ResolveRefFixAction?, List<String>>? {
            val targetFile = target.containingFile as? ArendFile ?: return null
            val targetModulePath = targetFile.modulePath ?: return null
            val boundingGroup = anchor.parents().filterIsInstance<ChildGroup>().firstOrNull()

            val location = LocationData(target, boundingGroup)

            var modifyingImportsNeeded = false
            var fallbackImportAction: ResolveRefFixAction? = null
            val importActionMap: HashMap<List<String>, ResolveRefFixAction?> = HashMap()

            val fullNames = HashSet<List<String>>()

            for (b in listOf(true, false)) location.getFullName(b)?.let { fullNames.add(it)  }

            val fileGroup = object : Group by currentFile {
                override fun getSubgroups(): Collection<Group> = emptyList()
            }
            val importedScope = ScopeFactory.forGroup(fileGroup, currentFile.moduleScopeProvider, false)

            val minimalImportMode = targetFile.subgroups.any { importedScope.resolveName(it.referable.textRepresentation()) != null } // True if imported scope of the current file has nonempty intersection with the scope of the target file

            var suitableImport: ArendStatCmd? = null
            val aliases = HashMap<List<String>, HashSet<String>>()

            for (fName in fullNames) aliases[fName] = HashSet()

            var preludeImportedManually = false

            for (namespaceCommand in currentFile.namespaceCommands) if (namespaceCommand.importKw != null) {
                val isImportPrelude = namespaceCommand.longName?.referent?.textRepresentation() == Prelude.MODULE_PATH.toString()

                if (isImportPrelude) preludeImportedManually = true

                if (namespaceCommand.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() == targetFile) {
                    suitableImport = namespaceCommand // even if some of the members are unused or hidden we still can access them using "very long name"

                    for (fName in fullNames) { //TODO: Could we reuse LocationData.calculateShorterName() here?
                        val importedName = getImportedName(namespaceCommand, fName[0])
                        if (importedName != null) aliases[fName]?.add(importedName.first)
                    }
                }
            }

            fullNames.clear()

            for ((fName, aliases2) in aliases.entries) {
                for (alias in aliases2) {
                    val fName2 = ArrayList<String>()
                    fName2.addAll(fName)
                    fName2.removeAt(0)
                    fName2.add(0, alias)
                    fullNames.add(fName2)
                }
            }

            if (isPrelude(targetFile) && !preludeImportedManually) {
                location.getFullName()?.let{fullNames.add(it)} // items from prelude are visible in any context
                fallbackImportAction = ImportFileAction(targetFile, currentFile, null) // however if long name is to be used "\import Prelude" will be added to imports
            }


            if (fullNames.isEmpty()) { // target definition is inaccessible in current context
                modifyingImportsNeeded = true

                location.getFullName()?.let {fullNames.add(it) }
                location.getFullName(true)?.let{
                    if (importedScope.resolveName(it[0]) == null) fullNames.add(it)
                }

                if (suitableImport != null) { // target definition is hidden or not included into using list but targetFile already has been imported
                    val nsUsing = suitableImport.nsUsing
                    val hiddenList = suitableImport.refIdentifierList

                    for (fName in fullNames) {
                        val hiddenRef: ArendRefIdentifier? = hiddenList.lastOrNull { it.referenceName == fName[0] }
                        if (hiddenRef != null)
                            importActionMap[fName] = RemoveRefFromStatCmdAction(suitableImport, hiddenRef)
                        else if (nsUsing != null)
                            importActionMap[fName] = AddIdToUsingAction(suitableImport, singletonList(Pair(fName[0], null)))
                    }
                    fallbackImportAction = null
                } else { // targetFile has not been imported
                    if (minimalImportMode) {
                        fallbackImportAction = ImportFileAction(targetFile, currentFile, emptyList())
                        for (fName in fullNames)
                            importActionMap[fName] = ImportFileAction(targetFile, currentFile, singletonList(fName[0]))
                    } else {
                        fallbackImportAction = ImportFileAction(targetFile, currentFile, null)
                        for (fName in fullNames)
                            importActionMap[fName] = fallbackImportAction
                    }
                }
            }


            var currentBlock: Map<List<String>, ResolveRefFixAction?>

            currentBlock = HashMap()
            for (fName in fullNames) currentBlock.put(fName, importActionMap[fName])

            val nestedOpenCommandBlocks = ArrayList<List<ArendStatCmd>>()

            var psi: PsiElement = anchor
            while (psi.parent != null) {
                var statements: List<ArendStatCmd>? = null

                if (psi is ArendWhere)
                    statements = psi.children.mapNotNull { (it as? ArendStatement)?.statCmd }
                else if (psi is ArendFile)
                    statements = psi.namespaceCommands

                if (statements != null)
                    nestedOpenCommandBlocks.add(0, statements.filter { it.openKw != null })

                psi = psi.parent
            }

            for (openCommandBlock in nestedOpenCommandBlocks) {
                val newBlock = HashMap<List<String>, ResolveRefFixAction?>()
                newBlock.putAll(currentBlock)

                for (openCommand in openCommandBlock) {
                    val lastRef = openCommand.longName?.refIdentifierList?.lastOrNull()
                    if (lastRef != null) {
                        for (b in listOf(true, false)) {
                            val oldFName = location.getFullName(b)
                            val correctedName = location.calculateShorterName(openCommand, b)
                            if (oldFName != null && correctedName != null) {
                                newBlock[correctedName] = currentBlock[oldFName]
                            }
                        }
                    }
                }
                currentBlock = newBlock
            }

            val newBlock = HashMap<List<String>, ResolveRefFixAction?>()

            for (fName in currentBlock.keys) {
                val elementParent = anchor.parent
                var correctedScope = if (elementParent is ArendLongName) elementParent.scope else anchor.scope

                if (modifyingImportsNeeded && location.isNotEmpty())
                    correctedScope = MergeScope(correctedScope, location.getComplementScope()) // calculate the scope imitating current scope after the imports have been fixed

                var referable = Scope.Utils.resolveName(correctedScope, fName)
                if (referable is RedirectingReferable) {
                    referable = referable.originalReferable
                }

                if (referable is GlobalReferable && PsiLocatedReferable.fromReferable(referable) == target)
                    newBlock[fName] = currentBlock[fName]
            }

            currentBlock = newBlock


            val veryLongName = ArrayList<String>()
            if (currentBlock.isEmpty()) {
                veryLongName.addAll(targetModulePath.toList())
                location.getFullName()?.let{ veryLongName.addAll(it) }

                // If we cannot resolve anything -- then perhaps there is some obstruction in scopes
                // Let us use the "longest possible name" when referring to the anchor
                currentBlock.put(veryLongName, fallbackImportAction)
            }

            // Determine shortest possible name in current scope
            val iterator = currentBlock.keys.iterator()
            var length = -1
            val resultNames: MutableList<Pair<List<String>, ResolveRefFixAction?>> = ArrayList()

            do {
                val lName = iterator.next()

                if (lName.size < length)
                    resultNames.clear()

                if (length == -1 || lName.size <= length) {
                    length = lName.size
                    resultNames.add(Pair(lName, currentBlock[lName]))
                }
            } while (iterator.hasNext())

            val comparator = Comparator<Pair<List<String>, ResolveRefFixAction?>> { o1, o2 -> //TODO: Isolate this piece of code
                if (o1 == null && o2 == null) return@Comparator 0
                if (o1 == null) return@Comparator -1
                if (o2 == null) return@Comparator 1

                val s1 = o1.first
                val s2 = o2.first
                (0 until minOf(s1.size, s2.size))
                        .filter { s1[it] != s2[it] }
                        .forEach { return@Comparator s1[it].compareTo(s2[it]) }
                if (s1.size != s2.size) return@Comparator s1.size.compareTo(s2.size)
                0
            }
            resultNames.sortedWith(comparator)

            return if (resultNames.size > 0) {
                val resultName = resultNames[0].first
                val importAction = if (targetFile != currentFile || resultName == veryLongName)
                    resultNames[0].second else null // If we use the long name of a file inside the file itself, we are required to import it first via a namespace command

                if (importAction is ImportFileAction && !importAction.isValid())
                    return null //Perhaps current or target directory is not marked as a content root

                return Pair(importAction, resultName)
            } else
                null
        }
    }
}