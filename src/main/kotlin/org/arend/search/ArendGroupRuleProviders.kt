package org.arend.search

import com.intellij.openapi.project.Project
import com.intellij.usages.PsiNamedElementUsageGroupBase
import com.intellij.usages.Usage
import com.intellij.usages.UsageGroup
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.FileStructureGroupRuleProvider
import com.intellij.usages.rules.PsiElementUsage
import com.intellij.usages.rules.SingleParentUsageGroupingRule
import com.intellij.usages.rules.UsageGroupingRule
import org.arend.psi.ext.*
import org.arend.psi.parentOfType

class ArendDefClassGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project) = createGroupingRule<ArendDefClass>()
}

class ArendDefDataGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project) = createGroupingRule<ArendDefData>()
}

class ArendDefFunctionGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project) = createGroupingRule<ArendDefFunction>()
}

class ArendClassFieldGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project) = createGroupingRule<ArendClassField>()
}

class ArendDefInstanceGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project) = createGroupingRule<ArendDefInstance>()
}

class ArendConstructorGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project) = createGroupingRule<ArendConstructor>()
}

class ArendDefGroupingRuleProvider : FileStructureGroupRuleProvider {
    override fun getUsageGroupingRule(project: Project) = createGroupingRule<PsiLocatedReferable>()
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
