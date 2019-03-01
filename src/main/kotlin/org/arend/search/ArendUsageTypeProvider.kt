package org.arend.search

import com.intellij.psi.PsiElement
import com.intellij.usages.UsageTarget
import com.intellij.usages.impl.rules.UsageType
import com.intellij.usages.impl.rules.UsageTypeProviderEx
import org.arend.psi.*

class ArendUsageTypeProvider: UsageTypeProviderEx {
    override fun getUsageType(element: PsiElement?, targets: Array<out UsageTarget>): UsageType? = getUsageType(element)

    override fun getUsageType(element: PsiElement?): UsageType? {
        val parent = element?.parent
        val grandparent = element?.parent?.parent

        if (parent is ArendLongName && grandparent is ArendStatCmd) return nsUsage
        if (parent is ArendStatCmd || (parent is ArendNsId && grandparent is ArendNsUsing)) return nsUsageInList
        if (parent is ArendLongName && element.rightSiblings.filterIsInstance<ArendRefIdentifier>().firstOrNull() != null) return leftUsage
        if (parent is ArendLongName && grandparent is ArendCoClause) return usagesInCoClauses
        if (parent is ArendPattern) return usagesInPatterns
        return defaultUsage
    }

    companion object {
        val nsUsage           = UsageType("Namespace commands")
        val nsUsageInList     = UsageType("Hiding of using lists of namespace commands")
        val leftUsage         = UsageType("Left sides of dot expressions")
        val usagesInCoClauses = UsageType("Coclauses")
        val usagesInPatterns  = UsageType("Patterns")
        val defaultUsage      = UsageType("Unclassified usages")
    }
}