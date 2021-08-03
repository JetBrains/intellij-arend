package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Ref
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ChangeSignatureUsageProcessor
import com.intellij.refactoring.rename.ResolveSnapshotProvider
import com.intellij.usageView.UsageInfo
import com.intellij.util.containers.MultiMap

class ArendChangeSignatureUsageProcessor : ChangeSignatureUsageProcessor {
    override fun findUsages(info: ChangeInfo?): Array<UsageInfo> {
        TODO("Not yet implemented")
    }

    override fun findConflicts(info: ChangeInfo?, refUsages: Ref<Array<UsageInfo>>?): MultiMap<PsiElement, String> {
        TODO("Not yet implemented")
    }

    override fun processUsage(
        changeInfo: ChangeInfo?,
        usageInfo: UsageInfo?,
        beforeMethodChange: Boolean,
        usages: Array<out UsageInfo>?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun processPrimaryMethod(changeInfo: ChangeInfo?): Boolean {
        TODO("Not yet implemented")
    }

    override fun shouldPreviewUsages(changeInfo: ChangeInfo?, usages: Array<out UsageInfo>?): Boolean {
        TODO("Not yet implemented")
    }

    override fun setupDefaultValues(
        changeInfo: ChangeInfo?,
        refUsages: Ref<Array<UsageInfo>>?,
        project: Project?
    ): Boolean {
        TODO("Not yet implemented")
    }

    override fun registerConflictResolvers(
        snapshots: MutableList<ResolveSnapshotProvider.ResolveSnapshot>?,
        resolveSnapshotProvider: ResolveSnapshotProvider,
        usages: Array<out UsageInfo>?,
        changeInfo: ChangeInfo?
    ) {
        TODO("Not yet implemented")
    }
}