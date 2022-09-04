package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.psi.search.searches.ReferencesSearch
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.refactoring.suggested.endOffset
import com.intellij.refactoring.suggested.startOffset
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap
import org.arend.psi.ext.ArendDefFunction

class ArendChangeSignatureUsageProcessor : ChangeSignatureUsageProcessor {
    override fun findUsages(info: ChangeInfo?): Array<UsageInfo> {
        info ?: return emptyArray()
        val usages: MutableList<UsageInfo> = mutableListOf()
        for (usage in ReferencesSearch.search(info.method)) {
            with(usage.element) {
                usages += UsageInfo(this, startOffset, endOffset)
            }
        }
        return usages.toTypedArray()
    }

    override fun findConflicts(info: ChangeInfo?, refUsages: Ref<Array<UsageInfo>>?): MultiMap<PsiElement, String> = MultiMap.empty()

    override fun processUsage(changeInfo: ChangeInfo?, usageInfo: UsageInfo?, beforeMethodChange: Boolean, usages: Array<out UsageInfo>?): Boolean = false

    override fun processPrimaryMethod(changeInfo: ChangeInfo?): Boolean {
        if (changeInfo !is ArendChangeInfo) return false
        processFunction(changeInfo.method.project, changeInfo, changeInfo.method as ArendDefFunction)
        return true
    }

    override fun shouldPreviewUsages(changeInfo: ChangeInfo?, usages: Array<out UsageInfo>?): Boolean = false

    override fun setupDefaultValues(changeInfo: ChangeInfo?, refUsages: Ref<Array<UsageInfo>>?, project: Project?): Boolean = false

    override fun registerConflictResolvers(snapshots: MutableList<in ResolveSnapshotProvider.ResolveSnapshot>?, resolveSnapshotProvider: ResolveSnapshotProvider, usages: Array<out UsageInfo>?, changeInfo: ChangeInfo?) {}

}