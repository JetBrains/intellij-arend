package org.arend.project

import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendTestBase
import org.arend.module.AREND_LIB

class ArendProjectViewTest : ArendTestBase() {
    lateinit var treeStructure: TestProjectTreeStructure

    override fun setUp() {
        super.setUp()
        treeStructure = TestProjectTreeStructure(project, testRootDisposable)
    }

    fun `test Prelude in External Libraries`() {
        doTest("""
            |-Project
            | PsiDirectory: src
            | -External Libraries
            |  -arend-prelude
            |   Prelude.ard
        """.trimMargin())
    }

    fun `test arend-lib metas`() {
        withStdLib {
            doTest("""
                |-Project
                | PsiDirectory: src
                | -External Libraries
                |  -Library: arend-lib
                |   -ext
                |    Meta.ard
                |    Function.Meta.ard
                |    Paths.Meta.ard
                |   -PsiDirectory: src
                |    Function.ard
                |   arend.yaml
                |  -arend-prelude
                |   Prelude.ard
            """.trimMargin())
        }
    }

    fun `test arend-lib metas when library is renamed`() {
        withStdLib(AREND_LIB + "_1") {
            doTest("""
                |-Project
                | PsiDirectory: src
                | -External Libraries
                |  -Library: arend-lib_1
                |   -ext
                |    Meta.ard
                |    Function.Meta.ard
                |    Paths.Meta.ard
                |   -PsiDirectory: src
                |    Function.ard
                |   arend.yaml
                |  -arend-prelude
                |   Prelude.ard
            """.trimMargin())
        }
    }

    private fun doTest(expectedTree: String) {
        val projectView = treeStructure.createPane().tree
        PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(projectView) { TreeVisitor.Action.CONTINUE })
        val actualTree = PlatformTestUtil.print(projectView, false)
        assertEquals(expectedTree, actualTree)
    }
}