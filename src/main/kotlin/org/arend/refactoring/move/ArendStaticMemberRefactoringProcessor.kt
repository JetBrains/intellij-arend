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
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.impl.ArendGroup
import org.arend.quickfix.ResolveRefQuickFix
import org.arend.term.group.ChildGroup
import java.util.ArrayList
import java.util.Collections.singletonList

class ArendStaticMemberRefactoringProcessor(project: Project,
                                            successfulCallback: () -> Unit,
                                            private val myMembersToMove: List<ArendGroup>,
                                            private val targetPsiElement: PsiElement) : BaseRefactoringProcessor(project, successfulCallback) {
    override fun findUsages(): Array<UsageInfo> {
        val usagesList = ArrayList<UsageInfo>()
        for ((i, member) in myMembersToMove.withIndex())
            for (entry in doCollectRelevantLocatedReferables(member))
                for (psiReference in ReferencesSearch.search(entry.key))
                    if (!isInMovedMember(psiReference.element))
                        usagesList.add(ArendUsageInfo(psiReference, i, entry.value))

        var usageInfos = usagesList.toTypedArray()
        usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos)
        return usageInfos
    }

    fun doCollectRelevantLocatedReferables(element: PsiElement): Map<PsiLocatedReferable, List<Int>> {
        val relevantLocatedReferables = HashMap<PsiLocatedReferable, List<Int>>()
        collectRelevantLocatedReferables(element, emptyList(), relevantLocatedReferables)
        return relevantLocatedReferables
    }

    fun collectRelevantLocatedReferables(element: PsiElement,
                                         prefix: List<Int>,
                                         sink: MutableMap<PsiLocatedReferable, List<Int>>) {
        when (element) {
            is ArendWhere -> return
            is ArendGroup -> sink[element] = prefix
            is ArendConstructor -> sink[element] = prefix
        }

        for ((i, child) in element.children.withIndex())
            collectRelevantLocatedReferables(child, prefix + singletonList(i), sink)
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        val anchor: PsiElement?
        val psiFactory = ArendPsiFactory(myProject)

        when (targetPsiElement) {
            is ArendGroup -> {
                val oldWhereImpl = targetPsiElement.where
                val actualWhereImpl = if (oldWhereImpl != null) oldWhereImpl else {
                        val localAnchor = targetPsiElement.lastChild
                        val insertedWhere = targetPsiElement.addAfter(psiFactory.createWhere(), localAnchor) as ArendWhere
                        targetPsiElement.addAfter(psiFactory.createWhitespace(" "), localAnchor)
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

                anchor = actualWhereImpl.statementList.lastOrNull() ?: actualWhereImpl.lbrace
            }
            is ArendFile -> {
                anchor = targetPsiElement.lastChild //null means file is empty
            }
            else -> {
                anchor = null
            }
        }

        //Memorize references in members being moved
        val bodyRefFixData =  HashMap<PsiLocatedReferable, Set<Pair<Int, List<Int>>>>()
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
        for (m in myMembersToMove) {
            val mStatement = m.parent
            val mCopyStatement = mStatement.copy()
            val mCopy = (if (anchor == null) (targetPsiElement.add(mCopyStatement))
            else (anchor.parent.addAfter(mCopyStatement, anchor))).childOfType<ArendGroup>()!!

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

            mStatement.delete()
        }

        //Now fix references of usages
        for (usage in usages) if (usage is ArendUsageInfo) {
            val num = usage.memberNo
            val enclosingGroup = if (num < newMemberList.size) newMemberList[num] else null
            val referenceElement = usage.reference?.element
            if (enclosingGroup != null && referenceElement is ArendReferenceElement) {
                val targetReferable = locateMember(enclosingGroup, usage.memberPath)
                if (targetReferable is PsiLocatedReferable)
                    ResolveRefQuickFix.getDecision(targetReferable, referenceElement)?.execute(null)
            }
        }


        //Fix references in the elements that were moved
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
                element.children.mapIndexed{ i, e -> memorizeReferences(prefix + singletonList(i), e, fixData, memberData) }
            }
        }
    }

    private fun restoreReferences(prefix: List<Int>, element: PsiElement, fixMap: Map<List<Int>, PsiLocatedReferable>) {
        if (element is ArendReferenceElement && element !is ArendDefIdentifier) {
            val correctTarget = fixMap[prefix]
            if (correctTarget != null && correctTarget !is ArendFile) {
                val currentTarget = element.reference?.resolve()
                if (currentTarget != correctTarget)
                    ResolveRefQuickFix.getDecision(correctTarget, element)?.execute(null)
            }
        } else element.children.mapIndexed{ i, e -> restoreReferences(prefix + singletonList(i), e, fixMap) }
    }

    override fun preprocessUsages(refUsages: Ref<Array<UsageInfo>>): Boolean {
        val conflicts = MultiMap<PsiElement, String>()
        val usages = refUsages.get()

        if (targetPsiElement is ChildGroup) {
            val localGroup = HashSet<PsiElement>()
            localGroup.addAll(targetPsiElement.subgroups.filterIsInstance<PsiElement>())
            localGroup.addAll(targetPsiElement.dynamicSubgroups.filterIsInstance<PsiElement>())
            localGroup.addAll(targetPsiElement.fields.filterIsInstance<PsiElement>())
            localGroup.addAll(targetPsiElement.constructors.filterIsInstance<PsiElement>())

            val localNamesMap = HashMap<String, PsiElement>()
            for (psi in localGroup) if (psi is Referable) localNamesMap[psi.textRepresentation()] = psi

            for (member in myMembersToMove) for (locatedReferable in doCollectRelevantLocatedReferables(member).keys){
                val text = locatedReferable.textRepresentation()
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

    class ArendUsageInfo(reference: PsiReference,
                         val memberNo: Int,
                         val memberPath: List<Int>): UsageInfo(reference)
}