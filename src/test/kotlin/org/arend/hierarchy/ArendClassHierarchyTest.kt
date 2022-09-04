package org.arend.hierarchy

import com.intellij.openapi.components.service
import org.arend.ArendTestBase
import org.arend.psi.ext.ArendDefClass
import org.arend.search.ClassDescendantsSearch
import org.junit.Assert

class ArendClassHierarchyTest : ArendTestBase() {
    private fun codeSample() : String = "\\class {-caret-}A {X : \\Set} {\n" +
            "  | field : Nat }\n" +
            "\\class B \\extends A\n" +
            "\\class C (foo : \\Set -> \\Set) (X : \\Set) \\extends A {foo X}\n" +
            "\\instance I (foo : \\Set -> \\Set) (X : \\Set) : A {foo X} {\n" +
            "  | field => 0}"

    fun `test subclasses`() {
        InlineFile(codeSample())
        val classElement = myFixture.elementAtCaret as? ArendDefClass
        check(classElement != null) { "Caret should be at the name of the class in a class definition header" }
        val descendantsSearch = project.service<ClassDescendantsSearch>()
        descendantsSearch.FIND_SUBCLASSES = true
        descendantsSearch.FIND_INSTANCES = false
        Assert.assertEquals(descendantsSearch.search(classElement).map { it.name }.toSet(), setOf("B", "C"))
    }

    fun `test instances`() {
        InlineFile(codeSample())
        val classElement = myFixture.elementAtCaret as? ArendDefClass
        check(classElement != null) { "Caret should be at the name of the class in a class definition header" }
        val descendantsSearch = project.service<ClassDescendantsSearch>()
        descendantsSearch.FIND_SUBCLASSES = false
        descendantsSearch.FIND_INSTANCES = true
        Assert.assertEquals(descendantsSearch.search(classElement).map { it.name }.toSet(), setOf("I"))
    }
}