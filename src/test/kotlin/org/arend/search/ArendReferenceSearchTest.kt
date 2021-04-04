package org.arend.search

import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.searches.ReferencesSearch
import org.arend.ArendTestBase
import org.arend.module.ArendPreludeLibrary

class ArendReferenceSearchTest : ArendTestBase() {
    fun `test Nat is used in Prelude`() {
        InlineFile("""\func zero : Na{-caret-}t => 0""").withCaret()
        assertTrue(isUsedInPrelude(GlobalSearchScope.allScope(project)))
        assertFalse(isUsedInPrelude(GlobalSearchScope.projectScope(project)))
        assertTrue(isUsedInPrelude(GlobalSearchScope.moduleWithLibrariesScope(module)))
        assertFalse(isUsedInPrelude(GlobalSearchScope.moduleScope(module)))
    }

    fun `test path is used in Prelude`() {
        InlineFile("""\func zero-is-zero : 0 = 0 => pat{-caret-}h (\lam _ => 0)""").withCaret()
        assertTrue(isUsedInPrelude(GlobalSearchScope.allScope(project)))
        assertFalse(isUsedInPrelude(GlobalSearchScope.projectScope(project)))
        assertTrue(isUsedInPrelude(GlobalSearchScope.moduleWithLibrariesScope(module)))
        assertFalse(isUsedInPrelude(GlobalSearchScope.moduleScope(module)))
    }

    private fun isUsedInPrelude(scope: GlobalSearchScope): Boolean {
        val element = myFixture.elementAtCaret
        val search = ReferencesSearch.search(element, scope)
        return search.anyMatch { it.element.containingFile.name == ArendPreludeLibrary.PRELUDE_FILE_NAME }
    }
}