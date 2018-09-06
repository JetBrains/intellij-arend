package com.jetbrains.arend.ide.search

import com.intellij.openapi.project.Project
import com.intellij.usages.PsiNamedElementUsageGroupBase
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.ext.PsiReferable

class ArdDefClassGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<ArdDefClass>()
}

class ArdDefDataGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<ArdDefData>()
}

class ArdDefFunctionGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<ArdDefFunction>()
}

class ArdClassFieldGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<ArdClassField>()
}

class ArdDefInstanceGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<ArdDefInstance>()
}

class ArdConstructorGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<ArdConstructor>()
}

class ArdClassFieldSynGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<ArdClassFieldSyn>()
}

private inline fun <reified T : PsiReferable> createGroupingRule(): UsageGroupingRule {
    return object : SingleParentUsageGroupingRule() {
        override fun getParentGroupFor(usage: Usage, targets: Array<out UsageTarget>): UsageGroup? {
            if (usage !is PsiElementUsage) return null
            val parent = usage.element.parentOfType<T>()
            return parent?.let { PsiNamedElementUsageGroupBase<PsiReferable>(it) }
        }
    }
}
