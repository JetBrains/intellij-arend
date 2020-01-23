package org.arend.refactoring.move

import com.intellij.ide.util.EditorHelper
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMemberViewDescriptor
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.containers.MultiMap
import org.arend.intention.SplitAtomPatternIntention.Companion.doSubstituteUsages
import org.arend.naming.renamer.StringRenamer
import org.arend.naming.scope.ClassFieldImplScope
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.psi.ext.impl.ArendGroup
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.quickfix.referenceResolve.ResolveReferenceAction.Companion.getTargetName
import org.arend.refactoring.LocationData
import org.arend.refactoring.*
import org.arend.util.LongName
import java.util.ArrayList
import java.util.Collections.singletonList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.List
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.any
import kotlin.collections.emptyList
import kotlin.collections.iterator
import kotlin.collections.lastOrNull
import kotlin.collections.map
import kotlin.collections.mapIndexed
import kotlin.collections.plus
import kotlin.collections.set
import kotlin.collections.toSet
import kotlin.collections.toTypedArray
import kotlin.collections.withIndex

class ArendStaticMemberRefactoringProcessor(project: Project,
                                            private val myMoveCallback: () -> Unit,
                                            private var myMembers: List<ArendGroup>,
                                            private val mySourceContainer: ArendGroup,
                                            private val myTargetContainer: ArendGroup,
                                            private val insertIntoDynamicPart: Boolean,
                                            private val myOpenInEditor: Boolean) : BaseRefactoringProcessor(project, myMoveCallback) {
    private val myReferableDescriptors = ArrayList<LocationDescriptor>()

    override fun findUsages(): Array<UsageInfo> {
        val usagesList = ArrayList<UsageInfo>()
        val statCmdsToFix = HashMap<ArendStatCmd, PsiReference>()

        for (psiReference in ReferencesSearch.search(mySourceContainer)) {
            val statCmd = isStatCmdUsage(psiReference, true)
            if (statCmd is ArendStatCmd && psiReference.element.findNextSibling(ArendElementTypes.DOT) !is ArendReferenceElement &&
                    myMembers.any { getImportedNames(statCmd, it.name).isNotEmpty() })
                statCmdsToFix[statCmd] = psiReference
        }

        for ((index, member) in myMembers.withIndex())
            for (entry in collectInternalReferablesWithSelf(member, index)) {
                myReferableDescriptors.add(entry.second)

                for (psiReference in ReferencesSearch.search(entry.first)) {
                    val referenceElement = psiReference.element
                    val referenceParent = referenceElement.parent
                    if (!isInMovedMember(psiReference.element)) {
                        val statCmd = isStatCmdUsage(psiReference, false)
                        val isUsageInHiding = referenceElement is ArendRefIdentifier && referenceParent is ArendStatCmd
                        if (statCmd == null || isUsageInHiding || !statCmdsToFix.contains(statCmd))
                            usagesList.add(ArendUsageInfo(psiReference, entry.second))
                    }
                }
            }
        //TODO: Somehow determine which of the statCmd usages are not relevant and filter them out

        for (statCmd in statCmdsToFix) usagesList.add(ArendStatCmdUsageInfo(statCmd.key, statCmd.value))

        var usageInfos = usagesList.toTypedArray()
        usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos)
        return usageInfos
    }

    private fun collectInternalReferablesWithSelf(element: ArendGroup, index: Int): ArrayList<Pair<PsiLocatedReferable, LocationDescriptor>> {
        val result = ArrayList<Pair<PsiLocatedReferable, LocationDescriptor>>()
        result.add(Pair(element, LocationDescriptor(index, emptyList())))
        for (internalReferable in element.internalReferables)
            if (internalReferable.isVisible) {
                val path = ArrayList<Int>()
                var psi: PsiElement = internalReferable
                while (psi.parent != null && psi != element) {
                    val i = psi.parent.children.indexOf(psi)
                    path.add(0, i)
                    psi = psi.parent
                }
                result.add(Pair(internalReferable, LocationDescriptor(index, path)))
            }

        return result
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        var insertAnchor: PsiElement?
        val psiFactory = ArendPsiFactory(myProject)
        val forceThisParameter = insertIntoDynamicPart && myTargetContainer is ArendDefClass && mySourceContainer is ArendDefClass &&
                !myTargetContainer.isSubClassOf(mySourceContainer)

        insertAnchor = if (myTargetContainer is ArendFile) {
            myTargetContainer.lastChild //null means file is empty
        } else if (myTargetContainer is ArendDefClass && insertIntoDynamicPart) {
            if (myTargetContainer.lbrace == null && myTargetContainer.rbrace == null) surroundWithBraces(psiFactory, myTargetContainer)
            myTargetContainer.classStatList.lastOrNull() ?: myTargetContainer.lbrace!!
        } else {
            getAnchorInAssociatedModule(psiFactory, myTargetContainer)
        }

        //Memorize references in myMembers being moved
        val descriptorsOfAllMovedMembers = HashMap<PsiLocatedReferable, LocationDescriptor>() //This set may be strictly larger than the set of myReferableDescriptors
        val bodiesRefsFixData = HashMap<LocationDescriptor, TargetReference>()
        val bodiesClassFieldUsages = HashSet<LocationDescriptor>() //We don't need to keep a link to class field as we replace its usages by "this.field" anyway

        run {
            val usagesInMovedBodies = HashMap<PsiLocatedReferable, MutableSet<LocationDescriptor>>()
            val targetReferences = HashMap<PsiLocatedReferable, TargetReference>()

            val recordFields =
                    if ((mySourceContainer as? ArendDefClass)?.recordKw != null)
                        ClassFieldImplScope(mySourceContainer, false).elements.filterIsInstanceTo(HashSet())
                    else emptySet<PsiLocatedReferable>()

            for ((mIndex, m) in myMembers.withIndex())
                collectUsagesAndMembers(emptyList(), m, mIndex, recordFields, usagesInMovedBodies, descriptorsOfAllMovedMembers)

            for (referable in usagesInMovedBodies.keys.minus(recordFields))
                targetReferences[referable] = descriptorsOfAllMovedMembers[referable]?.let { DescriptorTargetReference(it) }
                        ?: ReferableTargetReference(referable)

            for (usagePack in usagesInMovedBodies) for (usage in usagePack.value) {
                val referable = usagePack.key
                if (recordFields.contains(referable))
                    bodiesClassFieldUsages.add(usage) else
                    targetReferences[referable]?.let { bodiesRefsFixData[usage] = it }
            }

        }

        //Do move myMembers
        val holes = ArrayList<RelativePosition>()
        val newMemberList = ArrayList<ArendGroup>()
        val definitionsThatNeedThisParameter = ArrayList<TCDefinition>()
        val containingClass: ArendDefClass? = mySourceContainer.ancestors.filterIsInstance<ArendDefClass>().firstOrNull()

        for (member in myMembers) {
            val mStatementOrClassStat = member.parent
            val docs = (mStatementOrClassStat as? ArendStatement)?.let { getDocumentation(it) }

            var memberIsInDynamicPart = false
            run {
                var psi: PsiElement? = mStatementOrClassStat
                while (psi != null) {
                    if (psi is ArendClassStat) memberIsInDynamicPart = true
                    if (psi is ArendDefClass) break
                    psi = psi.parent
                }
            }

            val docsCopy = docs?.map { it.copy() }

            val copyOfMemberStatement: PsiElement =
                    if (myTargetContainer is ArendDefClass && insertIntoDynamicPart) {
                        val mCopyClassStat = if (mStatementOrClassStat is ArendClassStat) mStatementOrClassStat.copy() else {
                            val classStatContainer = psiFactory.createClassStat()
                            classStatContainer.definition!!.replace(member.copy())
                            classStatContainer
                        }

                        insertAnchor!!.parent!!.addAfterWithNotification(mCopyClassStat, insertAnchor)
                    } else {
                        val mCopyStatement = if (mStatementOrClassStat is ArendClassStat) {
                            val statementContainer = psiFactory.createFromText("\\func foo => {?}")?.childOfType<ArendStatement>()
                            statementContainer!!.definition!!.replace(member.copy())
                            statementContainer
                        } else mStatementOrClassStat.copy()

                        insertAnchor?.parent?.addAfterWithNotification(mCopyStatement, insertAnchor)
                                ?: myTargetContainer.addWithNotification(mCopyStatement)
                    }

            fun markGroupAsTheOneThatNeedsLeadingThisParameter(group: ArendGroup) {
                if (group is TCDefinition) definitionsThatNeedThisParameter.add(group)
                for (sg in group.subgroups) markGroupAsTheOneThatNeedsLeadingThisParameter(sg)
            }

            if (memberIsInDynamicPart && copyOfMemberStatement is ArendStatement) {
                copyOfMemberStatement.definition?.let { markGroupAsTheOneThatNeedsLeadingThisParameter(it) }
                copyOfMemberStatement.defModule?.let { markGroupAsTheOneThatNeedsLeadingThisParameter(it) }
            } else if (memberIsInDynamicPart && copyOfMemberStatement is ArendClassStat && forceThisParameter) {
                copyOfMemberStatement.definition?.let { markGroupAsTheOneThatNeedsLeadingThisParameter(it) }
                copyOfMemberStatement.defModule?.let { markGroupAsTheOneThatNeedsLeadingThisParameter(it) }
            }

            docsCopy?.forEach { copyOfMemberStatement.parent?.addBefore(it, copyOfMemberStatement) }

            val mCopy = copyOfMemberStatement.childOfType<ArendGroup>()!!
            newMemberList.add(mCopy)

            if (docs != null && docs.isNotEmpty()) (docs.first().prevSibling as? PsiWhiteSpace)?.delete()
            (mStatementOrClassStat.prevSibling as? PsiWhiteSpace)?.delete()
            mStatementOrClassStat.deleteAndGetPosition()?.let { if (!memberIsInDynamicPart) holes.add(it) }

            if (docs != null) for (doc in docs) doc.delete()
            insertAnchor = copyOfMemberStatement
        }

        var sourceContainerWhereBlockFreshlyCreated = false
        if (holes.isEmpty()) {
            sourceContainerWhereBlockFreshlyCreated = mySourceContainer.where == null
            val anchor = getAnchorInAssociatedModule(psiFactory, mySourceContainer)
            if (anchor != null) holes.add(RelativePosition(PositionKind.AFTER_ANCHOR, anchor))
        }

        myMembers = newMemberList

        //Create map from descriptors to actual psi elements of myMembers
        val movedReferablesMap = LinkedHashMap<LocationDescriptor, PsiLocatedReferable>()
        for (descriptor in myReferableDescriptors) (locateChild(descriptor) as? PsiLocatedReferable)?.let { movedReferablesMap[descriptor] = it }
        val movedReferablesNamesList = movedReferablesMap.values.mapNotNull { it.name }.toList()
        val movedReferablesNamesSet = movedReferablesNamesList.toSet()
        val movedReferablesUniqueNames = movedReferablesNamesSet.filter { name -> movedReferablesNamesList.filter { it == name }.size == 1 }
        val referablesWithUniqueNames = HashMap<String, PsiLocatedReferable>()
        for (entry in movedReferablesMap) {
            val name = entry.value.name
            if (name != null && movedReferablesUniqueNames.contains(name)) referablesWithUniqueNames[name] = entry.value
        }

        //Prepare the "remainder" namespace command (the one which is inserted in the place where one of the moved definitions was)
        if (holes.isNotEmpty()) {
            val uppermostHole = holes.asSequence().sorted().first()
            var remainderAnchor: PsiElement? = uppermostHole.anchor

            if (uppermostHole.kind != PositionKind.INSIDE_EMPTY_ANCHOR) {
                val next = remainderAnchor?.rightSibling<ArendCompositeElement>()
                val prev = remainderAnchor?.leftSibling<ArendCompositeElement>()
                if (next != null) remainderAnchor = next else
                    if (prev != null) remainderAnchor = prev
            }

            while (remainderAnchor !is ArendCompositeElement && remainderAnchor != null) remainderAnchor = remainderAnchor.parent

            if (remainderAnchor is ArendCompositeElement) {
                val sourceContainerFile = (mySourceContainer as PsiElement).containingFile as ArendFile
                val targetLocation = LocationData(myTargetContainer as PsiLocatedReferable)
                val importData = computeAliases(targetLocation, sourceContainerFile, remainderAnchor)

                if (importData != null) {
                    val importAction: AbstractRefactoringAction? = importData.first
                    val openedName: List<String>? = importData.second

                    importAction?.execute(null)
                    val renamings = movedReferablesUniqueNames.map { Pair(it, null) }
                    val groupMember = if (uppermostHole.kind == PositionKind.INSIDE_EMPTY_ANCHOR) {
                        if (remainderAnchor.children.isNotEmpty()) remainderAnchor.firstChild else null
                    } else remainderAnchor

                    val nsIds = addIdToUsing(groupMember, myTargetContainer, LongName(openedName).toString(), renamings, psiFactory, uppermostHole)
                    for (nsId in nsIds) {
                        val target = nsId.refIdentifier.reference?.resolve()
                        val name = nsId.refIdentifier.referenceName
                        if (target != referablesWithUniqueNames[name]) /* reference that we added to the namespace command is corrupt, so we need to remove it right after it was added */
                            RemoveRefFromStatCmdAction(null, nsId.refIdentifier).execute(null)
                    }
                }
            }
        }

        if (sourceContainerWhereBlockFreshlyCreated) {
            val where = mySourceContainer.where
            if (where != null && where.statementList.isEmpty())
                where.deleteWithNotification()
        }

        //Fix usages of namespace commands
        for (usage in usages) if (usage is ArendStatCmdUsageInfo) {
            val statCmd = usage.command
            val statCmdStatement = statCmd.parent
            val usageFile = statCmd.containingFile as ArendFile
            val renamings = ArrayList<Pair<String, String?>>()
            val nsIdToRemove = HashSet<ArendNsId>()

            for (memberName in movedReferablesNamesSet) {
                val importedName = getImportedNames(statCmd, memberName)

                for (name in importedName) {
                    val newName = if (name.first == memberName) null else name.first
                    renamings.add(Pair(memberName, newName))
                    val nsId = name.second
                    if (nsId != null) nsIdToRemove.add(nsId)
                }
            }

            val importData = computeAliases(LocationData(myTargetContainer as PsiLocatedReferable), usageFile, statCmd, myTargetContainer is ArendFile)
            val currentName: List<String>? = importData?.second

            if (renamings.isNotEmpty() && currentName != null) {
                importData.first?.execute(null)
                val name = when {
                    currentName.isNotEmpty() -> LongName(currentName).toString()
                    myTargetContainer is ArendFile -> myTargetContainer.modulePath?.lastName ?: ""
                    else -> ""
                }
                addIdToUsing(statCmdStatement, myTargetContainer, name, renamings, psiFactory, RelativePosition(PositionKind.AFTER_ANCHOR, statCmdStatement))
            }

            for (nsId in nsIdToRemove)
                RemoveRefFromStatCmdAction(statCmd, nsId.refIdentifier).execute(null)
        }

        //Now fix references of "normal" usages
        for (usage in usages) if (usage is ArendUsageInfo) {
            val referenceElement = usage.reference?.element
            val referenceParent = referenceElement?.parent

            if (referenceElement is ArendRefIdentifier && referenceParent is ArendStatCmd) { //Usage in "hiding" list which we simply delete
                RemoveRefFromStatCmdAction(referenceParent, referenceElement).execute(null)
            } else if (referenceElement is ArendReferenceElement) { //Normal usage which we try to fix
                val targetReferable = movedReferablesMap[usage.referableDescriptor]
                if (targetReferable != null) ResolveReferenceAction.getProposedFix(targetReferable, referenceElement)?.execute(null)
            }
        }

        //Fix references in the elements that have been moved
        for ((mIndex, m) in myMembers.withIndex()) restoreReferences(emptyList(), m, mIndex, bodiesRefsFixData)

        //Prepare a map which would allow us to fix class field usages on the next step (this step is needed only when we are moving definitions out of a record)
        val referenceElementsFixMap = HashMap<TCDefinition, HashSet<ArendReferenceElement>>()
        for (usage in bodiesClassFieldUsages) {
            val element = locateChild(usage)
            if (element is ArendReferenceElement) {
                val ancestor = element.ancestor<TCDefinition>()
                if (ancestor != null && definitionsThatNeedThisParameter.contains(ancestor)) {
                    val set = referenceElementsFixMap[ancestor] ?: HashSet()
                    referenceElementsFixMap[ancestor] = set
                    set.add(element)
                }
            }
        }

        //Add this modifier for items moved out of a class
        if (containingClass is ArendDefClass) for (definition in definitionsThatNeedThisParameter) if (definition is ArendFunctionalDefinition || definition is ArendDefData) {
            val anchor = definition.nameIdentifier
            val className = getTargetName(containingClass, definition).let { if (it.isNullOrEmpty()) containingClass.defIdentifier?.textRepresentation() else it }
            val thisVarName = StringRenamer().generateFreshName(VariableImpl("this"), getAllBindings(definition).map { VariableImpl(it) }.toList())

            if (className != null) {
                val thisTele: PsiElement = when (definition) {
                    is ArendFunctionalDefinition -> {
                        psiFactory.createNameTele(thisVarName, className, false)
                    }
                    is ArendDefData -> {
                        psiFactory.createTypeTele(thisVarName, className, false)
                    }
                    else -> throw IllegalStateException()
                }

                definition.addAfterWithNotification(thisTele, anchor)
                definition.addAfter(psiFactory.createWhitespace(" "), anchor)

                val classifyingField = getClassifyingField(containingClass)

                fun doSubstituteThisKwWithThisVar(psi: PsiElement) {
                    if (psi is ArendWhere) return
                    if (psi is ArendAtom && psi.thisKw != null) {
                        val literal = psiFactory.createExpression(thisVarName).childOfType<ArendAtom>()!!
                        psi.replaceWithNotification(literal)
                    } else for (c in psi.children) doSubstituteThisKwWithThisVar(c)
                }

                doSubstituteThisKwWithThisVar(definition)
                if (classifyingField != null) doSubstituteUsages(psiFactory, classifyingField, definition, thisVarName, false)

                val recordFieldsToFix = referenceElementsFixMap[definition]
                if (recordFieldsToFix != null) for (refElement in recordFieldsToFix)
                    RenameReferenceAction.create(refElement, listOf(thisVarName, refElement.referenceName))?.execute(null)
            }
        }

        myMoveCallback.invoke()

        if (myOpenInEditor && myMembers.isNotEmpty()) {
            val item = myMembers.first()
            if (item.isValid) EditorHelper.openInEditor(item)
        }
    }

    private fun locateChild(element: PsiElement, childPath: List<Int>): PsiElement? {
        return if (childPath.isEmpty()) element else {
            val shorterPrefix = childPath.subList(1, childPath.size)
            val childElement = element.children[childPath[0]]
            if (childElement != null) locateChild(childElement, shorterPrefix) else null
        }
    }

    private fun locateChild(descriptor: LocationDescriptor): PsiElement? {
        val num = descriptor.groupNumber
        val group = if (num < myMembers.size) myMembers[num] else null
        return if (group != null) locateChild(group, descriptor.childPath) else null
    }

    private fun collectUsagesAndMembers(prefix: List<Int>, element: PsiElement, groupNumber: Int,
                                        recordFields: Set<PsiLocatedReferable>,
                                        usagesData: MutableMap<PsiLocatedReferable, MutableSet<LocationDescriptor>>,
                                        memberData: MutableMap<PsiLocatedReferable, LocationDescriptor>) {
        when (element) {
            is ArendFieldDefIdentifier -> memberData[element] = LocationDescriptor(groupNumber, prefix)
            is ArendLongName -> {
                val reference = computeReferenceToBeFixed(element, recordFields)
                if (reference != null) collectUsagesAndMembers(prefix + singletonList(reference.index), reference.value, groupNumber, recordFields, usagesData, memberData)
            }
            is ArendReferenceElement ->
                if (element !is ArendDefIdentifier) element.reference?.resolve().let {
                    if (it is PsiLocatedReferable) {
                        var set = usagesData[it]
                        if (set == null) {
                            set = HashSet()
                            usagesData[it] = set
                        }
                        set.add(LocationDescriptor(groupNumber, prefix))
                    }
                }
            else -> {
                if (element is PsiLocatedReferable) memberData[element] = LocationDescriptor(groupNumber, prefix)
                element.children.mapIndexed { i, e -> collectUsagesAndMembers(prefix + singletonList(i), e, groupNumber, recordFields, usagesData, memberData) }
            }
        }
    }

    private fun computeReferenceToBeFixed(element: ArendLongName, recordFields: Set<PsiLocatedReferable>): IndexedValue<PsiElement>? {
        val references = element.children.withIndex().filter { (_, m) -> m is ArendReferenceElement }
        var classReference = true

        for (ref in references) { // This piece of code works nontrivially only when we are moving static definitions out of a record
            val refElement = ref.value
            val target = refElement.reference?.resolve()
            if (target is ArendDefClass) {
                classReference = true
                continue
            }
            if (recordFields.contains(target)) {
                return if (classReference)
                    ref else
                    null //Prevents the default mechanism of repairing links from being engaged on a longName which includes reference to a non-local classfield (it may break the reference by writing unnecessary "this" before it)
            }
            classReference = false
        }

        return references.lastOrNull() //this is the default behavior
    }

    private fun restoreReferences(prefix: List<Int>, element: PsiElement, groupIndex: Int, fixMap: HashMap<LocationDescriptor, TargetReference>) {
        if (element is ArendReferenceElement && element !is ArendDefIdentifier) {
            val correctTarget = fixMap[LocationDescriptor(groupIndex, prefix)]?.resolve()
            if (correctTarget != null && correctTarget !is ArendFile) {
                val currentTarget = element.reference?.resolve()
                if (currentTarget != correctTarget) ResolveReferenceAction.getProposedFix(correctTarget, element)?.execute(null)
            }
        } else element.children.mapIndexed { i, e -> restoreReferences(prefix + singletonList(i), e, groupIndex, fixMap) }
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val conflicts = MultiMap<PsiElement, String>()
        val usages = refUsages.get()

        if (mySourceContainer != myTargetContainer) {
            val localGroup = HashSet(myTargetContainer.subgroups)
            localGroup.addAll(myTargetContainer.dynamicSubgroups)

            val localNamesMap = HashMap<String, ArendGroup>()
            for (psi in localGroup) localNamesMap[psi.textRepresentation()] = psi

            for (member in myMembers) {
                val text = member.textRepresentation()
                val psi = localNamesMap[text]
                if (psi != null) conflicts.put(psi, singletonList("Name clash with one of the members of the target module ($text)"))
            }
        }

        return showConflicts(conflicts, usages)
    }

    override fun getCommandName(): String = MoveMembersImpl.REFACTORING_NAME

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
            MoveMemberViewDescriptor(PsiUtilCore.toPsiElementArray(myMembers.map { it }))

    private fun isInMovedMember(element: PsiElement): Boolean = myMembers.any { PsiTreeUtil.isAncestor(it, element, false) }

    private fun isStatCmdUsage(reference: PsiReference, insideLongNameOnly: Boolean): ArendStatCmd? {
        val parent = reference.element.parent
        if (parent is ArendStatCmd && !insideLongNameOnly) return parent
        if (parent is ArendLongName) {
            val grandparent = parent.parent
            if (grandparent is ArendStatCmd) return grandparent
        }
        return null
    }

    data class LocationDescriptor(val groupNumber: Int, val childPath: List<Int>)

    class ArendUsageInfo(reference: PsiReference, val referableDescriptor: LocationDescriptor) : UsageInfo(reference)

    class ArendStatCmdUsageInfo(val command: ArendStatCmd, reference: PsiReference) : UsageInfo(reference)

    private interface TargetReference {
        fun resolve(): PsiLocatedReferable?
    }

    private class ReferableTargetReference(private val myReferable: PsiLocatedReferable) : TargetReference {
        override fun resolve(): PsiLocatedReferable? = myReferable
    }

    private inner class DescriptorTargetReference(val myDescriptor: LocationDescriptor) : TargetReference {
        private var myCachedResult: PsiLocatedReferable? = null

        override fun resolve(): PsiLocatedReferable? {
            if (myCachedResult != null) return myCachedResult
            myCachedResult = locateChild(myDescriptor) as? PsiLocatedReferable
            return myCachedResult
        }
    }
}