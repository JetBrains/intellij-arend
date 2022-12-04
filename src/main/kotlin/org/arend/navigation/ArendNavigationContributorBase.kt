package org.arend.navigation

import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import org.arend.naming.reference.Referable
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.fullName

abstract class ArendNavigationContributorBase<T> protected constructor(
        private val indexKey: StubIndexKey<String, T>,
        private val clazz: Class<T>
) : GotoClassContributor where T : NavigationItem, T : PsiReferable {

    override fun getNames(project: Project?, includeNonProjectItems: Boolean): Array<String> {
        project ?: return emptyArray()
        return StubIndex.getInstance().getAllKeys(indexKey, project).toTypedArray() +
                getGeneratedItems(project).keys
    }

    override fun getItemsByName(
            name: String?,
            pattern: String?,
            project: Project?,
            includeNonProjectItems: Boolean
    ): Array<NavigationItem> {
        if (project == null || name == null) return emptyArray()
        val scope = if (includeNonProjectItems) {
            GlobalSearchScope.allScope(project)
        } else {
            GlobalSearchScope.projectScope(project)
        }
        val items = StubIndex.getElements(indexKey, name, project, scope, clazz).toTypedArray<NavigationItem>()
        return items + getGeneratedItems(project).getOrDefault(name, emptyList())
    }

    override fun getQualifiedName(item: NavigationItem): String? =
            when (item) {
                is PsiLocatedReferable -> item.fullName
                is Referable -> item.refLongName?.toString() ?: item.refName
                else -> null
            }

    override fun getQualifiedNameSeparator(): String = "."

    protected abstract fun getGeneratedItems(project: Project?): Map<String, List<PsiLocatedReferable>>
}
