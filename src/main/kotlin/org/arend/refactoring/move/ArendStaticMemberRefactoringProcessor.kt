package org.arend.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiReference
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
import org.arend.naming.reference.Referable
import org.arend.psi.*
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.quickfix.LocationData
import org.arend.quickfix.ResolveRefQuickFix
import org.arend.quickfix.ResolveRefQuickFix.Companion.getDecision
import org.arend.refactoring.*
import org.arend.term.group.ChildGroup
import org.arend.util.LongName
import java.util.ArrayList
import java.util.Collections.singletonList
import kotlin.collections.HashMap
import kotlin.collections.HashSet
import kotlin.collections.List
import kotlin.collections.Map
import kotlin.collections.MutableMap
import kotlin.collections.MutableSet
import kotlin.collections.any
import kotlin.collections.emptyList
import kotlin.collections.filterIsInstance
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
                                            private val mySourceContainer: ChildGroup /* and also PsiElement...*/,
                                            private val myTargetContainer: PsiElement /* and also ChildGroup */) : BaseRefactoringProcessor(project, myMoveCallback) {
    private val myReferableDescriptors = ArrayList<LocationDescriptor>()

    override fun findUsages(): Array<UsageInfo> {
        val usagesList = ArrayList<UsageInfo>()
        val statCmdsToFix = HashMap<ArendStatCmd, PsiReference>()

        if (mySourceContainer is PsiElement) for (psiReference in ReferencesSearch.search(mySourceContainer)) {
            val statCmd = isStatCmdUsage(psiReference, true)
            if (statCmd is ArendStatCmd && psiReference.element.findNextSibling(ArendElementTypes.DOT) !is ArendReferenceElement &&
                    myMembers.any { getImportedNames(statCmd, it.name).isNotEmpty() })
                statCmdsToFix[statCmd] = psiReference
        }

        for ((i, member) in myMembers.withIndex())
            for (entry in collectRelevantReferables(member)) {
                val descriptor = LocationDescriptor(i, entry.value)
                myReferableDescriptors.add(descriptor)

                for (psiReference in ReferencesSearch.search(entry.key)) {
                    val referenceElement = psiReference.element
                    val referenceParent = referenceElement.parent
                    if (!isInMovedMember(psiReference.element)) {
                        val statCmd = isStatCmdUsage(psiReference, false)
                        val isUsageInHiding = referenceElement is ArendRefIdentifier && referenceParent is ArendStatCmd
                        if (statCmd == null || isUsageInHiding || !statCmdsToFix.contains(statCmd))
                            usagesList.add(ArendUsageInfo(psiReference, descriptor))
                    }
                }
            }
        //TODO: Somehow determine which of the statCmd usages are not relevant and filter them out

        for (statCmd in statCmdsToFix) usagesList.add(ArendStatCmdUsageInfo(statCmd.key, statCmd.value))

        var usageInfos = usagesList.toTypedArray()
        usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos)
        return usageInfos
    }

    private fun collectRelevantReferables(element: ArendGroup): Map<PsiLocatedReferable, List<Int>> {
        val relevantLocatedReferables = LinkedHashMap<PsiLocatedReferable, List<Int>>()
        relevantLocatedReferables[element] = emptyList()
        for (internalReferable in element.internalReferables)
            if (internalReferable.isVisible) {
                val path = ArrayList<Int>()
                var psi: PsiElement = internalReferable
                while (psi.parent != null && psi != element) {
                    val i = psi.parent.children.indexOf(psi)
                    path.add(0, i)
                    psi = psi.parent
                }
                relevantLocatedReferables[internalReferable] = path
            }

        return relevantLocatedReferables
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        var insertAnchor: PsiElement?
        val psiFactory = ArendPsiFactory(myProject)

        insertAnchor = when (myTargetContainer) {
            is ArendGroup -> {
                val oldWhereImpl = myTargetContainer.where
                val actualWhereImpl = if (oldWhereImpl != null) oldWhereImpl else {
                    val localAnchor = myTargetContainer.lastChild
                    val insertedWhere = myTargetContainer.addAfter(psiFactory.createWhere(), localAnchor) as ArendWhere
                    myTargetContainer.addAfter(psiFactory.createWhitespace(" "), localAnchor)
                    insertedWhere
                }

                if (actualWhereImpl.lbrace == null || actualWhereImpl.rbrace == null) {
                    val pOB = psiFactory.createPairOfBraces()
                    if (actualWhereImpl.lbrace == null) {
                        actualWhereImpl.addAfter(pOB.first, actualWhereImpl.whereKw)
                        actualWhereImpl.addAfter(psiFactory.createWhitespace(" "), actualWhereImpl.whereKw)
                    }
                    if (actualWhereImpl.rbrace == null) {
                        actualWhereImpl.addAfter(pOB.second, actualWhereImpl.lastChild)
                    }
                }

                actualWhereImpl.statementList.lastOrNull() ?: actualWhereImpl.lbrace
            }
            is ArendFile -> myTargetContainer.lastChild //null means file is empty
            else -> null
        }

        //Memorize references in myMembers being moved
        val descriptorsOfAllMovedMembers = HashMap<PsiLocatedReferable, LocationDescriptor>() //This set may be strictly larger than the set of myReferableDescriptors
        val usagesInMovedBodies = HashMap<PsiLocatedReferable, MutableSet<LocationDescriptor>>()
        val targetReferences = HashMap<PsiLocatedReferable, TargetReference>()
        val bodiesRefsFixData = HashMap<LocationDescriptor, TargetReference>()

        for ((mIndex, m) in myMembers.withIndex())
            collectUsagesAndMembers(emptyList(), m, mIndex, usagesInMovedBodies, descriptorsOfAllMovedMembers)

        for (referable in usagesInMovedBodies.keys)
            targetReferences[referable] = descriptorsOfAllMovedMembers[referable]?.let { DescriptorTargetReference(it) } ?: ReferableTargetReference(referable)

        for (usagePack in usagesInMovedBodies) for (usage in usagePack.value)
            targetReferences[usagePack.key]?.let{ bodiesRefsFixData[usage] = it }

        //Do move myMembers
        val holes = ArrayList<RelativePosition>()
        val newMemberList = ArrayList<ArendGroup>()
        for (m in myMembers) {
            val mStatement = m.parent
            val mCopyStatement = mStatement.copy()
            val mCopyStatementInserted = if (insertAnchor == null)
                (myTargetContainer.add(mCopyStatement)) else
                insertAnchor.parent.addAfter(mCopyStatement, insertAnchor)
            val mCopy = mCopyStatementInserted.childOfType<ArendGroup>()!!
            newMemberList.add(mCopy)

            mStatement.deleteAndGetPosition()?.let { holes.add(it) }
            insertAnchor = mCopyStatementInserted
        }
        myMembers = newMemberList

        //Create map from descriptors to actual psi elements of myMembers
        val movedReferablesMap = LinkedHashMap<LocationDescriptor, PsiLocatedReferable>()
        for (descriptor in myReferableDescriptors) locateChild(descriptor)?.let { movedReferablesMap[descriptor] = it }
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
                val next = remainderAnchor?.rightSiblings?.filterIsInstance<ArendCompositeElement>()?.firstOrNull()
                val prev = remainderAnchor?.leftSiblings?.filterIsInstance<ArendCompositeElement>()?.firstOrNull()
                if (next != null) remainderAnchor = next else
                    if (prev != null) remainderAnchor = prev
            }

            while (remainderAnchor !is ArendCompositeElement && remainderAnchor != null) remainderAnchor = remainderAnchor.parent

            if (remainderAnchor is ArendCompositeElement) {
                val sourceContainerFile = (mySourceContainer as PsiElement).containingFile as ArendFile
                val targetLocation = LocationData(myTargetContainer as PsiLocatedReferable)
                val importData = getDecision(targetLocation, sourceContainerFile, remainderAnchor)

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

            val importData = getDecision(LocationData(myTargetContainer as PsiLocatedReferable), usageFile, statCmd)
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
                if (targetReferable != null) ResolveRefQuickFix.getDecision(targetReferable, referenceElement)?.execute(null)
            }
        }

        //Fix references in the elements that have been moved
        for ((mIndex, m) in myMembers.withIndex()) restoreReferences(emptyList(), m, mIndex, bodiesRefsFixData)

        myMoveCallback.invoke()
    }

    private fun locateChild(element: PsiElement, childPath: List<Int>): PsiElement? {
        return if (childPath.isEmpty()) element else {
            val shorterPrefix = childPath.subList(1, childPath.size)
            val childElement = element.children[childPath[0]]
            if (childElement != null) locateChild(childElement, shorterPrefix) else null
        }
    }

    private fun locateChild(descriptor: LocationDescriptor): PsiLocatedReferable? {
        val num = descriptor.groupNumber
        val group = if (num < myMembers.size) myMembers[num] else null
        return if (group != null) locateChild(group, descriptor.childPath) as? PsiLocatedReferable else null
    }

    private fun collectUsagesAndMembers(prefix: List<Int>, element: PsiElement, groupNumber: Int,
                                        usagesData: MutableMap<PsiLocatedReferable, MutableSet<LocationDescriptor>>,
                                        memberData: MutableMap<PsiLocatedReferable, LocationDescriptor>) {
        when (element) {
            is ArendReferenceElement -> if (element !is ArendDefIdentifier) element.reference?.resolve().let {
                if (it is PsiLocatedReferable) {
                    var set = usagesData[it]
                    if (set == null) {
                        set = HashSet()
                        usagesData[it] = set
                    }
                    set.add(LocationDescriptor(groupNumber, prefix))
                }
            }
            is ArendLongName ->  element.children.withIndex().lastOrNull { (_, m) -> m is ArendReferenceElement }?.let{
                    collectUsagesAndMembers(prefix + singletonList(it.index), it.value, groupNumber, usagesData, memberData)
                }
            else -> {
                if (element is PsiLocatedReferable) memberData[element] = LocationDescriptor(groupNumber, prefix)
                element.children.mapIndexed { i, e -> collectUsagesAndMembers(prefix + singletonList(i), e, groupNumber, usagesData, memberData) }
            }
        }
    }

    private fun restoreReferences(prefix: List<Int>, element: PsiElement, groupIndex: Int, fixMap: HashMap<LocationDescriptor, TargetReference>) {
        if (element is ArendReferenceElement && element !is ArendDefIdentifier) {
            val correctTarget = fixMap[LocationDescriptor(groupIndex, prefix)]?.resolve()
            if (correctTarget != null && correctTarget !is ArendFile) {
                val currentTarget = element.reference?.resolve()
                if (currentTarget != correctTarget) {
                    val action = ResolveRefQuickFix.getDecision(correctTarget, element)
                    action?.execute(null)
                }

            }
        } else element.children.mapIndexed { i, e -> restoreReferences(prefix + singletonList(i), e, groupIndex, fixMap) }
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val conflicts = MultiMap<PsiElement, String>()
        val usages = refUsages.get()

        if (myTargetContainer is ChildGroup) {
            val localGroup = HashSet<PsiElement>()
            localGroup.addAll(myTargetContainer.subgroups.filterIsInstance<PsiElement>())
            localGroup.addAll(myTargetContainer.dynamicSubgroups.filterIsInstance<PsiElement>())

            val localNamesMap = HashMap<String, PsiElement>()
            for (psi in localGroup) if (psi is Referable) localNamesMap[psi.textRepresentation()] = psi

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
            myCachedResult = locateChild(myDescriptor)
            return myCachedResult
        }
    }
}