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
import org.arend.psi.ext.impl.DefinitionAdapter
import org.arend.psi.ext.impl.ModuleAdapter
import org.arend.term.group.ChildGroup
import java.util.ArrayList
import java.util.Collections.singletonList

class ArendStaticMemberRefactoringProcessor(project: Project,
                                            successfulCallback: () -> Unit,
                                            private val myMembersToMove: List<ArendDefinition>,
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
            is DefinitionAdapter<*>, is ModuleAdapter -> {
                val oldWhereImpl = when (targetPsiElement) { //TODO: Rewrite later using new interface
                    is DefinitionAdapter<*> -> targetPsiElement.getWhere()
                    is ModuleAdapter -> targetPsiElement.where
                    else -> null
                }
                val actualWhereImpl = if (oldWhereImpl != null) oldWhereImpl else {
                        val localAnchor = targetPsiElement.lastChild
                        targetPsiElement.addAfter(psiFactory.createWhere(), localAnchor)
                        targetPsiElement.addAfter(psiFactory.createWhitespace(" "), localAnchor)
                        targetPsiElement.childOfType()!!
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

                val lastStatement = actualWhereImpl.statementList.lastOrNull()
                anchor = if (lastStatement == null) actualWhereImpl.lbrace else lastStatement
            }
            is ArendFile -> {
                anchor = targetPsiElement.lastChild //null means file is empty
            }
            else -> {
                anchor = null
            }
        }

        for (m in myMembersToMove) {
            val mStatement = m.parent
            val mCopy = mStatement.copy()
            mStatement.delete()

            if (anchor == null) {
                targetPsiElement.add(mCopy)
            } else {
                anchor.parent.addAfter(mCopy, anchor)
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