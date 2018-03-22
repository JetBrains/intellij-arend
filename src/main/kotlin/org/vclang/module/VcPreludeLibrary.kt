package org.vclang.module

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.jetpad.vclang.library.BaseLibrary
import com.jetbrains.jetpad.vclang.library.LibraryManager
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.scopeprovider.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.naming.scope.LexicalScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.prelude.Prelude
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.util.FileUtils
import org.vclang.VcLanguage
import org.vclang.psi.VcFile
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths


class VcPreludeLibrary(private val project: Project, typecheckerState: TypecheckerState?) : BaseLibrary(typecheckerState) {
    private var prelude: VcFile? = null
    private var isTypechecked: Boolean = false
    private var scope: Scope? = null

    override fun getName() = "prelude"

    override fun getLoadedModules(): List<ModulePath> = if (prelude == null) emptyList() else listOf(Prelude.MODULE_PATH)

    override fun getModuleGroup(modulePath: ModulePath) = if (modulePath == Prelude.MODULE_PATH) prelude else null

    override fun getModuleScopeProvider() = ModuleScopeProvider { if (it == Prelude.MODULE_PATH) scope else null }

    override fun containsModule(modulePath: ModulePath) = modulePath == Prelude.MODULE_PATH

    override fun needsTypechecking() = !isTypechecked

    override fun getUpdatedModules(): List<ModulePath> = if (isTypechecked) emptyList() else listOf(Prelude.MODULE_PATH)

    override fun typecheck(typechecking: Typechecking): Boolean {
        if (isTypechecked) return true
        if (!super.typecheck(typechecking)) return false

        if (Prelude.INTERVAL == null) {
            synchronized(VcPreludeLibrary::class.java) {
                if (Prelude.INTERVAL == null) {
                    return if (super.typecheck(typechecking)) {
                        isTypechecked = true
                        Prelude.initialize(moduleScopeProvider.forModule(Prelude.MODULE_PATH), typecheckerState)
                        true
                    } else {
                        false
                    }
                }
            }
        }

        isTypechecked = true
        Prelude.fillInTypecheckerState(typecheckerState)
        return true
    }

    override fun load(libraryManager: LibraryManager): Boolean {
        val text = String(Files.readAllBytes(Paths.get(VcPreludeLibrary::class.java.getResource("/lib").toURI()).resolve(Paths.get("Prelude" + FileUtils.EXTENSION))), StandardCharsets.UTF_8)
        prelude = PsiFileFactory.getInstance(project).createFileFromText("Prelude" + FileUtils.EXTENSION, VcLanguage, text) as? VcFile
        val preludeFile = prelude ?: return false
        preludeFile.virtualFile.isWritable = false
        scope = CachingScope.make(LexicalScope.opened(preludeFile))
        return super.load(libraryManager)
    }
}