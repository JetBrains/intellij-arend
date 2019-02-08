package org.arend.quickfix

import com.intellij.psi.PsiElement
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.RedirectingReferable
import org.arend.naming.reference.Referable
import org.arend.naming.scope.*
import org.arend.prelude.Prelude
import org.arend.psi.*
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.refactoring.*
import org.arend.term.group.Group
import java.util.Collections.singletonList

class ResolveRefQuickFix {
    companion object {

        fun statCmdName(statCmd: ArendStatCmd) =
                (statCmd.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() as? ArendFile)?.modulePath?.toString()
                        ?: "???"

        fun isPrelude(file: ArendFile) = file.modulePath == Prelude.MODULE_PATH && file.containingDirectory == null

        fun getDecision(target: PsiLocatedReferable, element: ArendReferenceElement): ResolveRefFixData? {
            val targetFile = target.containingFile as? ArendFile ?: return null
            val currentFile = element.containingFile as? ArendFile ?: return null
            val targetModulePath = targetFile.modulePath ?: return null

            val fullName = ArrayList<String>()
            val alternativeFullName: ArrayList<String>? = if (target is ArendClassFieldSyn || target is ArendClassField || target is ArendConstructor)
                ArrayList() else
                null
            var ignoreFlag = true

            var psi: PsiElement = target
            var targetTop: MutableList<Referable> = ArrayList()

            var modifyingImportsNeeded = false
            var fallbackImportAction: ResolveRefFixAction? = null
            val importActionMap: HashMap<List<String>, ResolveRefFixAction?> = HashMap()

            while (psi.parent != null) {
                if (psi is PsiReferable && psi !is ArendFile) {
                    val name = psi.name ?: return null

                    fullName.add(0, name)
                    if (alternativeFullName != null) {
                        if (ignoreFlag && alternativeFullName.isNotEmpty()) {
                            ignoreFlag = false
                            targetTop.add(psi)
                        } else {
                            alternativeFullName.add(0, name)
                            targetTop = ArrayList()
                            targetTop.add(psi)
                        }
                    } else {
                        targetTop = ArrayList()
                        targetTop.add(psi)
                    }

                }
                psi = psi.parent
            }

            val fullNames = HashSet<List<String>>()
            fullNames.add(fullName)
            if (alternativeFullName != null) fullNames.add(alternativeFullName)

            val fileGroup = object : Group by currentFile {
                override fun getSubgroups(): Collection<Group> = emptyList()
            }
            val importedScope = ScopeFactory.forGroup(fileGroup, currentFile.moduleScopeProvider, false)

            val cautiousMode = targetFile.subgroups.any { importedScope.resolveName(it.referable.textRepresentation()) != null } // True if imported scope of the current file has nonempty intersection with the scope of the target file

            var suitableImport: ArendStatCmd? = null
            val aliases = HashMap<List<String>, HashSet<String>>()

            for (fName in fullNames) {
                aliases[fName] = HashSet()
            }

            var preludeImportedManually = false

            for (namespaceCommand in currentFile.namespaceCommands) if (namespaceCommand.importKw != null) {
                val isImportPrelude = namespaceCommand.longName?.referent?.textRepresentation() == Prelude.MODULE_PATH.toString()

                if (isImportPrelude) preludeImportedManually = true

                if (namespaceCommand.longName?.refIdentifierList?.lastOrNull()?.reference?.resolve() == targetFile) {
                    suitableImport = namespaceCommand // even if some of the members are unused or hidden we still can access them using "very long name"

                    val nsUsing = namespaceCommand.nsUsing
                    val hiddenList = namespaceCommand.refIdentifierList
                    val defaultNameHiddenFNames: HashSet<List<String>> = HashSet()

                    if (hiddenList.isNotEmpty()) for (ref in hiddenList) fullNames.filterTo(defaultNameHiddenFNames) { ref.referenceName == it[0] }

                    if (nsUsing != null) {
                        for (refIdentifier in nsUsing.nsIdList) {
                            for (fName in fullNames) {
                                val originalName = fName[0]
                                if (refIdentifier.refIdentifier.text == originalName) {
                                    val defIdentifier = refIdentifier.defIdentifier
                                    aliases[fName]?.add(defIdentifier?.textRepresentation() ?: originalName)
                                }
                            }
                        }

                        if (nsUsing.usingKw != null)
                            aliases.entries.filter { it.component2().isEmpty() && !defaultNameHiddenFNames.contains(it.component1()) }.forEach { it.component2().add(it.component1()[0]) }
                    } else
                        aliases.entries.filter { !defaultNameHiddenFNames.contains(it.component1()) }.forEach { it.component2().add(it.component1()[0]) }
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
                fullNames.add(fullName) // items from prelude are visible in any context
                fallbackImportAction = ImportFileAction(targetFile, currentFile, null) // however if long name is to be used "\import Prelude" will be added to imports
            }


            if (fullNames.isEmpty()) { // target definition is inaccessible in current context
                modifyingImportsNeeded = true

                if (importedScope.resolveName(fullName[0]) == null)
                    fullNames.add(fullName)
                if (alternativeFullName != null && importedScope.resolveName(alternativeFullName[0]) == null)
                    fullNames.add(alternativeFullName)

                if (suitableImport != null) { // target definition is hidden or not included into using list but targetFile already has been imported
                    val nsUsing = suitableImport.nsUsing
                    val hiddenList = suitableImport.refIdentifierList

                    for (fName in fullNames) {
                        val hiddenRef: ArendRefIdentifier? = hiddenList.lastOrNull { it.referenceName == fName[0] }
                        if (hiddenRef != null)
                            importActionMap[fName] = RemoveFromHidingAction(suitableImport, hiddenRef)
                        else if (nsUsing != null)
                            importActionMap[fName] = AddIdToUsingAction(suitableImport, singletonList(fName[0]))
                    }
                    fallbackImportAction = null
                } else { // targetFile has not been imported
                    if (cautiousMode) {
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

            val namespaceCommands = ArrayList<List<ArendStatCmd>>()
            psi = element
            while (psi.parent != null) {
                var statements: List<ArendStatCmd>? = null

                if (psi is ArendWhere)
                    statements = psi.children.mapNotNull { (it as? ArendStatement)?.statCmd }
                else if (psi is ArendFile)
                    statements = psi.namespaceCommands

                if (statements != null)
                    namespaceCommands.add(0, statements.filter { it.openKw != null })

                psi = psi.parent
            }

            for (commandBlock in namespaceCommands) {
                val newBlock = HashMap<List<String>, ResolveRefFixAction?>()
                newBlock.putAll(currentBlock)

                for (command in commandBlock) {
                    val refIdentifiers = command.longName?.refIdentifierList?.map { it.referenceName }
                    var renamings: HashMap<String, String>? = null
                    val using = command.nsUsing

                    if (using != null) {
                        renamings = HashMap()
                        for (nsId in using.nsIdList) {
                            val oldName = nsId.refIdentifier.referenceName
                            val defIdentifier = nsId.defIdentifier
                            if (defIdentifier != null)
                                renamings[oldName] = defIdentifier.textRepresentation()
                            else renamings[oldName] = oldName
                        }
                    }

                    if (refIdentifiers != null && refIdentifiers.isNotEmpty()) {
                        for (fName in currentBlock.keys) {
                            val i1 = fName.iterator()
                            val i2 = refIdentifiers.iterator()
                            var equals = true
                            while (i2.hasNext()) {
                                if (i1.next() != i2.next()) {
                                    equals = false
                                    break
                                }
                            }
                            if (equals && i1.hasNext()) {
                                val fName2 = ArrayList<String>()
                                while (i1.hasNext()) fName2.add(i1.next())
                                if (renamings != null) {
                                    val newName = renamings[fName2[0]]
                                    if (newName != null) {
                                        fName2.removeAt(0)
                                        fName2.add(0, newName)
                                    } else {
                                        equals = false
                                    }
                                }

                                if (equals)
                                    newBlock[fName2] = currentBlock[fName]

                            }
                        }
                    }
                }

                currentBlock = newBlock
            }

            val newBlock = HashMap<List<String>, ResolveRefFixAction?>()

            for (fName in currentBlock.keys) {
                val elementParent = element.parent
                var correctedScope = if (elementParent is ArendLongName) elementParent.scope else element.scope

                if (modifyingImportsNeeded && targetTop.isNotEmpty()) { // calculate the scope imitating current scope after the imports have been fixed
                    val complementScope = object : ListScope(targetTop) {
                        override fun resolveNamespace(name: String?, onlyInternal: Boolean): Scope? = targetTop
                                .filterIsInstance<ArendDefinition>()
                                .firstOrNull { name == it.textRepresentation() }
                                ?.let { LexicalScope.opened(it) }
                    }

                    correctedScope = MergeScope(correctedScope, complementScope)
                }

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
                veryLongName.addAll(fullName)
                // If we cannot resolve anything -- then perhaps there is some obstruction in scopes
                // Let us use the "longest possible name" when referring to the element
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

            val comparator = Comparator<Pair<List<String>, ResolveRefFixAction?>> { o1, o2 ->
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

                if (importAction != null && !importAction.isValid())
                    return null //Perhaps current or target directory is not marked as a content root

                val renameAction = if ((resultName.size > 1 || (resultName[0] != element.referenceName))) RenameReferenceAction(element, resultName) else null
                ResolveRefFixData(target, fullName, importAction, renameAction)
            } else
                null
        }
    }
}