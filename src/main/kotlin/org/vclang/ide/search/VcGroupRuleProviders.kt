package org.vclang.ide.search

import com.intellij.openapi.project.Project
import com.intellij.usages.PsiNamedElementUsageGroupBase
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import org.vclang.lang.core.psi.*
import org.vclang.lang.core.psi.ext.VcNamedElement

class VcDefClassGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<VcDefClass>()
}

class VcDefClassViewGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<VcDefClassView>()
}

class VcDefDataGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<VcDefData>()
}

class VcDefFunctionGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<VcDefFunction>()
}

class VcClassFieldGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<VcClassField>()
}

class VcClassImplementGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<VcClassImplement>()
}

class VcClassViewFieldGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<VcClassViewField>()
}

class VcDefInstanceGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<VcDefInstance>()
}

class VcConstructorGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project): UsageGroupingRule? =
            createGroupingRule<VcConstructor>()
}

private inline fun <reified T : VcNamedElement> createGroupingRule(): UsageGroupingRule {
    return object : SingleParentUsageGroupingRule() {
        override fun getParentGroupFor(usage: Usage, targets: Array<out UsageTarget>): UsageGroup? {
            if (usage !is PsiElementUsage) return null
            val parent = usage.element.parentOfType<T>()
            return parent?.let { PsiNamedElementUsageGroupBase<VcNamedElement>(it) }
        }
    }
}
