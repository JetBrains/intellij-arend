package org.arend.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
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
        for (member in myMembersToMove)
            for (psiReference in ReferencesSearch.search(member))
                if (!isInMovedMember(psiReference.element))
                    usagesList.add(UsageInfo(psiReference))

        var usageInfos = usagesList.toTypedArray()
        usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos)
        return usageInfos
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
        val membersSet = myMembersToMove.toSet()
        val refFixData = HashMap<ArendGroup, MutableSet<UsageInfo>>()
        for (usage in usages) {
            val target = usage.reference?.resolve()
            if (target is ArendGroup && membersSet.contains(target)) {
                var refSet = refFixData[target]
                if (refSet == null) {
                    refSet = HashSet()
                    refFixData[target] = refSet
                }
                refSet.add(usage)
            }
        }

        for (m in myMembersToMove) {
            val mStatement = m.parent
            val mCopyStatement = mStatement.copy()
            val mCopy = (if (anchor == null) (targetPsiElement.add(mCopyStatement))
            else (anchor.parent.addAfter(mCopyStatement, anchor))).childOfType<ArendGroup>()!!

            val old = refFixData[m]
            if (old != null) refFixData[mCopy] = old
            refFixData.remove(m)
            mStatement.delete()
        }

        //Now fix references
        for (entry in refFixData) {
            val newTarget = entry.key
            for (usage in entry.value) {
                val referenceElement = usage.reference?.element
                if (referenceElement is ArendReferenceElement) {
                    val fixData = ResolveRefQuickFix.getDecision(newTarget, referenceElement)
                    fixData?.execute(null)
                }
            }
        }
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
}