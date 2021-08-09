package org.arend.refactoring.changeSignature

import com.intellij.openapi.project.Project
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ChangeSignatureProcessorBase
import com.intellij.usageView.BaseUsageViewDescriptor
import com.intellij.usageView.UsageInfo
import com.intellij.usageView.UsageViewDescriptor

class ArendChangeSignatureProcessor(project: Project, changeInfo: ChangeInfo?) :
    ChangeSignatureProcessorBase(project, changeInfo) {

    override fun createUsageViewDescriptor(usages: Array<out UsageInfo>): UsageViewDescriptor =
        BaseUsageViewDescriptor(changeInfo?.method)
}