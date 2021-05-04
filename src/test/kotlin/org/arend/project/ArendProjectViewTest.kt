package org.arend.project

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.application.runWriteAction
import com.intellij.openapi.components.service
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
import org.arend.module.config.ArendModuleConfigService
import org.arend.typechecking.TypeCheckingService
import org.jetbrains.yaml.psi.YAMLFile

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
                | -PsiDirectory: src
                |  arend.yaml
                | -External Libraries
                |  -Library: arend-lib
                |   -PsiDirectory: bin
                |    Function.arc
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
        val currentLibrariesRoot = ArendModuleConfigService.getInstance(module)?.librariesRoot
        val newLibrariesRoot = findTestDataFile("").path
        try {
            createIdeaArendLibrary(newLibrariesRoot)
            test()
        } finally {
            removeIdeaArendLibrary(currentLibrariesRoot)
        }
    }

    private fun createIdeaArendLibrary(librariesRoot: String) {
        myFixture.configureByText("arend.yaml", "dependencies: [$AREND_LIB]") as YAMLFile
        val moduleConfig = ArendModuleConfigService.getInstance(module)
                ?: throw IllegalStateException("Cannot find module config service")
        moduleConfig.librariesRoot = librariesRoot
        runReadAction { moduleConfig.copyFromYAML(true) }

        val arendLib = project.service<TypeCheckingService>().libraryManager.getRegisteredLibrary(AREND_LIB)
                as? ArendRawLibrary
                ?: throw IllegalStateException("Cannot find arend-lib")
        addGeneratedModules(arendLib) {
            declare(ModulePath("Meta"), LongName("using"), "", Precedence.DEFAULT, null)
            declare(ModulePath("Function", "Meta"), LongName("$"), "", Precedence.DEFAULT, null)
            declare(ModulePath("Paths", "Meta"), LongName("rewrite"), "", Precedence.DEFAULT, null)
        }
    }

    private fun removeIdeaArendLibrary(oldLibrariesRoot : String?) {
        runWriteAction {
            project.service<TypeCheckingService>().libraryManager.unloadLibrary(AREND_LIB)
            val projectTable = LibraryTablesRegistrar.getInstance().getLibraryTable(project)
            val arendLib = projectTable.getLibraryByName(AREND_LIB) ?: return@runWriteAction
            ModuleRootModificationUtil.updateModel(module) { model ->
                model.findLibraryOrderEntry(arendLib)?.let { model.removeOrderEntry(it) }
            }
            projectTable.removeLibrary(arendLib)
            oldLibrariesRoot?.let { ArendModuleConfigService.getInstance(module)?.librariesRoot = it }
        }
    }
}