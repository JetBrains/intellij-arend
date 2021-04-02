package org.arend.project

import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendTestBase

class ArendProjectViewTest : ArendTestBase() {
    lateinit var treeStructure: TestProjectTreeStructure

    override fun setUp() {
        super.setUp()
        treeStructure = TestProjectTreeStructure(project, testRootDisposable)
    }

    fun `test Prelude in External Libraries`() {
        val projectView = treeStructure.createPane().tree
        PlatformTestUtil.waitForPromise(TreeUtil.promiseExpand(projectView) { TreeVisitor.Action.CONTINUE })
        val actualTree = PlatformTestUtil.print(projectView, false)
        val expectedTree = """
            |-Project
            | PsiDirectory: src
            | -External Libraries
            |  -arend-prelude
            |   Prelude.ard
        """.trimMargin()
        assertEquals(expectedTree, actualTree)
    }
}