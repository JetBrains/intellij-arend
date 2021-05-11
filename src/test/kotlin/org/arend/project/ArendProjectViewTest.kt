package org.arend.project

import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.roots.ModuleRootModificationUtil
import com.intellij.openapi.roots.libraries.LibraryTablesRegistrar
import com.intellij.projectView.TestProjectTreeStructure
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.ui.tree.TreeVisitor
import com.intellij.util.ui.tree.TreeUtil
import org.arend.ArendTestBase
import org.arend.ext.module.LongName
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.Precedence
import org.arend.module.AREND_LIB
import org.arend.module.ArendRawLibrary
import org.arend.module.ModuleSynchronizer
import org.arend.module.config.ExternalLibraryConfig
import org.arend.typechecking.TypeCheckingService
import org.arend.util.findExternalLibrary

class ArendProjectViewTest : ArendTestBase() {
    lateinit var treeStructure: TestProjectTreeStructure

    override val dataPath: String = "org/arend/projectView"

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
        withIdeaArendLib {
            doTest("""
                |-Project
                | PsiDirectory: src
                | -External Libraries
                |  -Library: arend-lib
                |   -ext.ard
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

    private fun withIdeaArendLib(test: () -> Unit) {
        try {
            createIdeaArendLibrary()
            test()
        } finally {
            removeIdeaArendLibrary()
        }
    }

    private fun createIdeaArendLibrary() {
        val arendLibConfig = project.findExternalLibrary(findTestDataFile(""), AREND_LIB)
                ?: throw IllegalStateException("Cannot find arend-lib")
        setupLibraryManager(arendLibConfig)
        setupProjectModel(arendLibConfig)
    }

    private fun setupLibraryManager(config: ExternalLibraryConfig) {
        val arendLib = ArendRawLibrary(config)
        addGeneratedModules(arendLib) {
            declare(ModulePath("Meta"), LongName("using"), "", Precedence.DEFAULT, null)
            declare(ModulePath("Function", "Meta"), LongName("$"), "", Precedence.DEFAULT, null)
            declare(ModulePath("Paths", "Meta"), LongName("rewrite"), "", Precedence.DEFAULT, null)
        }
        TypeCheckingService.LibraryManagerTestingOptions.setStdLibrary(arendLib, testRootDisposable)
    }

    private fun setupProjectModel(config: ExternalLibraryConfig) {
        runWriteAction {
            val projectModel = LibraryTablesRegistrar.getInstance().getLibraryTable(project).modifiableModel
            val ideaLib = projectModel.createLibrary(AREND_LIB)
            projectModel.commit()
            ideaLib.modifiableModel.apply {
                ModuleSynchronizer.setupFromConfig(this, config)
                commit()
            }
            ModuleRootModificationUtil.updateModel(module) { it.addLibraryEntry(ideaLib) }
        }
    }

    private fun removeIdeaArendLibrary() {
        runWriteAction {
            val projectTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            val arendLib = projectTable.getLibraryByName(AREND_LIB) ?: return@runWriteAction
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.findLibraryOrderEntry(arendLib)?.let { model.removeOrderEntry(it) }
            }
            projectTable.removeLibrary(arendLib)
        }
    }
}