package org.vclang.ide.navigation.goto

import com.intellij.navigation.GotoClassContributor
import com.intellij.navigation.NavigationItem
import com.intellij.openapi.project.Project
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.stubs.StubIndexKey
import com.jetbrains.jetpad.vclang.term.Abstract
import org.vclang.lang.core.parser.fullyQualifiedName
import org.vclang.lang.core.psi.ext.VcNamedElement

abstract class VcNavigationContributorBase<T> protected constructor(
        private val indexKey: StubIndexKey<String, T>,
        private val clazz: Class<T>
) : GotoClassContributor where T : NavigationItem, T : VcNamedElement {

    override fun getNames(project: Project?, includeNonProjectItems: Boolean): Array<String> {
        project ?: return emptyArray()
        return StubIndex.getInstance().getAllKeys(indexKey, project).toTypedArray()
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
        return StubIndex.getElements(
                indexKey,
                name,
                project,
                scope,
                clazz
        ).toTypedArray()
    }

    override fun getQualifiedName(item: NavigationItem?): String? =
            (item as? Abstract.Definition)?.fullyQualifiedName

    override fun getQualifiedNameSeparator(): String = "."
}
