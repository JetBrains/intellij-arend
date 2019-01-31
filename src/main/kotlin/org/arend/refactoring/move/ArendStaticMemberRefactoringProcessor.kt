package org.arend.refactoring.move

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.psi.util.PsiUtilCore
import com.intellij.refactoring.BaseRefactoringProcessor
import com.intellij.refactoring.move.MoveMemberViewDescriptor
import com.intellij.refactoring.move.moveMembers.MoveMembersImpl
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor
import com.intellij.usageView.UsageViewUtil
import com.intellij.util.containers.MultiMap
import org.arend.naming.reference.Referable
import org.arend.psi.ArendDefinition
import org.arend.term.group.ChildGroup
import java.util.ArrayList
import java.util.Collections.singletonList

class ArendStaticMemberRefactoringProcessor(project: Project,
                                            successfulCallback: () -> Unit,
                                            private val myMembersToMove: List<SmartPsiElementPointer<ArendDefinition>>,
                                            private val targetPsiElement: PsiElement): BaseRefactoringProcessor(project, successfulCallback) {
    override fun findUsages(): Array<UsageInfo> {
        val usagesList = ArrayList<UsageInfo>()
        for (member in myMembersToMove) {
            val element = member.element
            if (element != null) for (psiReference in ReferencesSearch.search(element))
                usagesList.add(UsageInfo(psiReference))

        }
        var usageInfos = usagesList.toTypedArray()
        usageInfos = UsageViewUtil.removeDuplicatedUsages(usageInfos)
        return usageInfos
    }

    override fun performRefactoring(usages: Array<out UsageInfo>) {
        TODO("not implemented") //To change body of created functions use File | Settings | File Templates.
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

            for (memberToMove in myMembersToMove) {
                val member = memberToMove.element
                if (member != null) {
                    val text = member.textRepresentation()
                    val psi = localNamesMap[text]
                    if (psi != null) conflicts.put(psi, singletonList("Name clash with one of the members of the target module ($text)"))
                }
            }
        }

        return showConflicts(conflicts, usages)
    }

    override fun getCommandName(): String = MoveMembersImpl.REFACTORING_NAME

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
            MoveMemberViewDescriptor(PsiUtilCore.toPsiElementArray(myMembersToMove.mapNotNull { it.element }))
}