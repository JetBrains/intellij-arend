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
import kotlin.collections.Set
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
                                            successfulCallback: () -> Unit,
                                            private val myMembersToMove: List<ArendGroup>,
                                            private val mySourceContainer: ChildGroup /* and also PsiElement...*/,
                                            private val myTargetContainer: PsiElement /* and also ChildGroup */) : BaseRefactoringProcessor(project, successfulCallback) {
    private val movedReferableNames = ArrayList<String>()

    override fun findUsages(): Array<UsageInfo> {
        val usagesList = ArrayList<UsageInfo>()
        val statCmdsToFix = HashMap<ArendStatCmd, PsiReference>()

        if (mySourceContainer is PsiElement) for (psiReference in ReferencesSearch.search(mySourceContainer)) {
            val statCmd = isStatCmdUsage(psiReference, true)
            if (statCmd is ArendStatCmd && psiReference.element.findNextSibling(ArendElementTypes.DOT) !is ArendReferenceElement &&
                    myMembersToMove.any { getImportedNames(statCmd, it.name).isNotEmpty() })
                statCmdsToFix[statCmd] = psiReference
        }

        for ((i, member) in myMembersToMove.withIndex())
            for (entry in collectRelevantReferables(member)) {
                entry.key.name?.let{ movedReferableNames.add(it) }

                for (psiReference in ReferencesSearch.search(entry.key)) {
                    val referenceElement = psiReference.element
                    val referenceParent = referenceElement.parent
                    if (!isInMovedMember(psiReference.element)) {
                        val statCmd = isStatCmdUsage(psiReference, false)
                        val isUsageInHiding = referenceElement is ArendRefIdentifier && referenceParent is ArendStatCmd
                        if (statCmd == null || isUsageInHiding || !statCmdsToFix.contains(statCmd))
                            usagesList.add(ArendUsageInfo(psiReference, i, entry.value))
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
        val insertAnchor: PsiElement?
        val psiFactory = ArendPsiFactory(myProject)

        when (myTargetContainer) {
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

                insertAnchor = actualWhereImpl.statementList.lastOrNull() ?: actualWhereImpl.lbrace
            }
            is ArendFile -> {
                insertAnchor = myTargetContainer.lastChild //null means file is empty
            }
            else -> {
                insertAnchor = null
            }
        }

        //Memorize references in members being moved
        val bodyRefFixData = HashMap<PsiLocatedReferable, Set<Pair<Int, List<Int>>>>()
        val membersMap = HashMap<ArendGroup, Map<PsiLocatedReferable, List<Int>>>()
        val newMemberList = ArrayList<ArendGroup>()

        for ((mIndex, m) in myMembersToMove.withIndex()) {
            val fixData = HashMap<PsiLocatedReferable, MutableSet<List<Int>>>()
            val memberData = HashMap<PsiLocatedReferable, List<Int>>()
            memorizeReferences(emptyList(), m, fixData, memberData)
            membersMap[m] = memberData
            for (r in fixData) bodyRefFixData[r.key] = r.value.map { Pair(mIndex, it) }.toSet()
        }

        //Do move members
        val holes = ArrayList<RelativePosition>()
        for (m in myMembersToMove) {
            val mStatement = m.parent
            val mCopyStatement = mStatement.copy()
            val mCopy = (if (insertAnchor == null) (myTargetContainer.add(mCopyStatement))
            else (insertAnchor.parent.addAfter(mCopyStatement, insertAnchor))).childOfType<ArendGroup>()!!

            newMemberList.add(mCopy)

            val members = membersMap[m]
            if (members != null) for (memberFixInfo in members) {
                val oldKey = memberFixInfo.key
                val refFixDataPack = bodyRefFixData[oldKey]
                if (refFixDataPack != null) {
                    val newTarget = locateMember(mCopy, memberFixInfo.value)
                    if (newTarget is PsiLocatedReferable) {
                        bodyRefFixData[newTarget] = refFixDataPack
                        bodyRefFixData.remove(oldKey)
                    }
                }
            }

            mStatement.deleteAndGetPosition()?.let { holes.add(it) }
        }

        //Prepare "remainder" namespace command (which is inserted in the place where one of the moved definitions was)
        if (holes.isNotEmpty()) {
            val uppermostHole = holes.sorted().first()
            var remainderAnchor: PsiElement? = uppermostHole.anchor

            val next = remainderAnchor?.rightSiblings?.filterIsInstance<ArendCompositeElement>()?.firstOrNull()
            val prev = remainderAnchor?.leftSiblings?.filterIsInstance<ArendCompositeElement>()?.firstOrNull()
            if (next != null) remainderAnchor = next else
                if (prev != null) remainderAnchor = prev else
                    while (remainderAnchor !is ArendCompositeElement && remainderAnchor != null) remainderAnchor = remainderAnchor.parent

            if (remainderAnchor is ArendCompositeElement) {
                val groupMember = if (uppermostHole.kind == PositionKind.INSIDE_EMPTY_ANCHOR) null else remainderAnchor
                val sourceContainerFile = (mySourceContainer as PsiElement).containingFile as ArendFile
                val importData = getDecision((myTargetContainer as PsiLocatedReferable), sourceContainerFile, remainderAnchor)
                if (importData != null) {
                    val importAction = importData.first
                    importAction?.execute(null)

                    val name = LongName(importData.second).toString()
                    val renamings = movedReferableNames.map { Pair(it, null) }
                    addIdToUsing(groupMember, myTargetContainer, name, renamings, psiFactory, uppermostHole)
                }
            }
        }


        //Fix usages of namespace commands
        for (usage in usages) if (usage is ArendStatCmdUsageInfo) {
            val statCmd = usage.command
            val statCmdStatement = statCmd.parent
            val usageFile = statCmd.containingFile as ArendFile

            val importData = getDecision(myTargetContainer as PsiLocatedReferable, usageFile, statCmd)
            if (importData != null) {
                val importAction = importData.first
                importAction?.execute(null)
            }

            val renamings = ArrayList<Pair<String, String?>>()
            for (memberName in movedReferableNames) {
                val importedName = getImportedNames(statCmd, memberName)

                for (name in importedName) {
                    val newName = if (name.first == memberName) null else name.first
                    renamings.add(Pair(memberName, newName))
                    val nsId = name.second

                    if (nsId != null) RemoveRefFromStatCmdAction(statCmd, nsId.refIdentifier).execute(null)
                }
            }

            val currentName: List<String>? = importData?.second
            if (statCmd.openKw != null && renamings.isNotEmpty() && currentName != null && currentName.isNotEmpty()) {
                val name = LongName(currentName).toString()
                addIdToUsing(statCmdStatement, myTargetContainer, name, renamings, psiFactory, RelativePosition(PositionKind.AFTER_ANCHOR, statCmdStatement))
            }

        }

        //Now fix references of "normal" usages
        for (usage in usages) if (usage is ArendUsageInfo) {
            val num = usage.memberNo
            val referenceElement = usage.reference?.element
            val referenceParent = referenceElement?.parent

            if (referenceElement is ArendRefIdentifier && referenceParent is ArendStatCmd) { //Usage in "hiding" list which we simply delete
                RemoveRefFromStatCmdAction(referenceParent, referenceElement).execute(null)
            } else {
                val enclosingGroup = if (num < newMemberList.size) newMemberList[num] else null
                if (enclosingGroup != null && referenceElement is ArendReferenceElement) {
                    val targetReferable = locateMember(enclosingGroup, usage.memberPath)
                    if (targetReferable is PsiLocatedReferable)
                        ResolveRefQuickFix.getDecision(targetReferable, referenceElement)?.execute(null)
                }
            }
        }


        //Fix references in the elements that have been moved
        for ((mIndex, m) in newMemberList.withIndex()) {
            val localFixData = HashMap<List<Int>, PsiLocatedReferable>()
            for (fD in bodyRefFixData) for (p in fD.value) if (p.first == mIndex) localFixData[p.second] = fD.key
            restoreReferences(emptyList(), m, localFixData)
        }

    }

    private fun locateMember(element: PsiElement, prefix: List<Int>): PsiElement? {
        return if (prefix.isEmpty()) element else {
            val shorterPrefix = prefix.subList(1, prefix.size)
            val childElement = element.children[prefix[0]]
            if (childElement != null) locateMember(childElement, shorterPrefix) else null
        }
    }

    private fun memorizeReferences(prefix: List<Int>,
                                   element: PsiElement,
                                   fixData: MutableMap<PsiLocatedReferable, MutableSet<List<Int>>>,
                                   memberData: MutableMap<PsiLocatedReferable, List<Int>>) {
        when (element) {
            is ArendReferenceElement -> if (element !is ArendDefIdentifier) element.reference?.resolve().let {
                if (it is PsiLocatedReferable) {
                    var set = fixData[it]
                    if (set == null) {
                        set = HashSet()
                        fixData[it] = set
                    }
                    set.add(prefix)
                }
            }
            is ArendLongName -> {
                var i1 = -1
                var e1: ArendReferenceElement? = null
                for (i in 0 until element.children.size) {
                    val e = element.children[i]
                    if (e is ArendReferenceElement) {
                        i1 = i
                        e1 = e
                    }
                }

                if (i1 != -1 && e1 != null) memorizeReferences(prefix + singletonList(i1), e1, fixData, memberData)
            }
            else -> {
                if (element is PsiLocatedReferable) memberData[element] = prefix
                element.children.mapIndexed { i, e -> memorizeReferences(prefix + singletonList(i), e, fixData, memberData) }
            }
        }
    }

    private fun restoreReferences(prefix: List<Int>, element: PsiElement, fixMap: Map<List<Int>, PsiLocatedReferable>) {
        if (element is ArendReferenceElement && element !is ArendDefIdentifier) {
            val correctTarget = fixMap[prefix]
            if (correctTarget != null && correctTarget !is ArendFile) {
                val currentTarget = element.reference?.resolve()
                if (currentTarget != correctTarget) {
                    val action = ResolveRefQuickFix.getDecision(correctTarget, element)
                    action?.execute(null)
                }

            }
        } else element.children.mapIndexed { i, e -> restoreReferences(prefix + singletonList(i), e, fixMap) }
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val conflicts = MultiMap<PsiElement, String>()
        val usages = refUsages.get()

        if (myTargetContainer is ChildGroup) {
            val localGroup = HashSet<PsiElement>()
            localGroup.addAll(myTargetContainer.subgroups.filterIsInstance<PsiElement>())
            localGroup.addAll(myTargetContainer.dynamicSubgroups.filterIsInstance<PsiElement>())
            //TODO: Add verification that constructors names' do not clash

            val localNamesMap = HashMap<String, PsiElement>()
            for (psi in localGroup) if (psi is Referable) localNamesMap[psi.textRepresentation()] = psi

            for (member in myMembersToMove) {
                val text = member.textRepresentation()
                val psi = localNamesMap[text]
                if (psi != null) conflicts.put(psi, singletonList("Name clash with one of the members of the target module ($text)"))
            }
        }

        return showConflicts(conflicts, usages)
    }

    override fun getCommandName(): String = MoveMembersImpl.REFACTORING_NAME

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
            MoveMemberViewDescriptor(PsiUtilCore.toPsiElementArray(myMembersToMove.map { it }))

    private fun isInMovedMember(element: PsiElement): Boolean =
            myMembersToMove.any { PsiTreeUtil.isAncestor(it, element, false) }

    private fun isStatCmdUsage(reference: PsiReference, insideLongNameOnly: Boolean): ArendStatCmd? {
        val parent = reference.element.parent
        if (parent is ArendStatCmd && !insideLongNameOnly) return parent
        if (parent is ArendLongName) {
            val grandparent = parent.parent
            if (grandparent is ArendStatCmd) return grandparent
        }
        return null
    }

    class ArendUsageInfo(reference: PsiReference, val memberNo: Int, val memberPath: List<Int>) : UsageInfo(reference)

    class ArendStatCmdUsageInfo(val command: ArendStatCmd, reference: PsiReference) : UsageInfo(reference)
}