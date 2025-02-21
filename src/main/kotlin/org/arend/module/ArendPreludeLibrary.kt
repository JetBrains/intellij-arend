package org.arend.module

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.arend.ArendLanguage
import org.arend.ext.module.ModulePath
import org.arend.library.BaseLibrary
import org.arend.library.LibraryDependency
import org.arend.library.LibraryManager
import org.arend.module.scopeprovider.ModuleScopeProvider
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.term.group.ConcreteGroup
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.util.FileUtils
import org.arend.util.Version
import java.nio.charset.StandardCharsets


class ArendPreludeLibrary(private val project: Project) : BaseLibrary() {
    var prelude: ArendFile? = null
        private set
    private var isTypechecked: Boolean = false
    private var scope: Scope? = null

    override fun getName() = Prelude.LIBRARY_NAME

    override fun getVersion(): Version = Prelude.VERSION

    override fun getLoadedModules(): List<ModulePath> = if (prelude == null) emptyList() else listOf(Prelude.MODULE_PATH)

    override fun getDependencies(): List<LibraryDependency> = emptyList()

    override fun getModuleGroup(modulePath: ModulePath, inTests: Boolean) = null

    override fun getDeclaredModuleScopeProvider() = ModuleScopeProvider { if (it == Prelude.MODULE_PATH) scope else null }

    override fun containsModule(modulePath: ModulePath) = modulePath == Prelude.MODULE_PATH

    override fun isExternal() = true

    override fun getUpdatedModules(): List<ModulePath> = if (isTypechecked) emptyList() else listOf(Prelude.MODULE_PATH)

    override fun orderModules(ordering: Ordering): Boolean {
        if (isTypechecked) return true
        isTypechecked = Prelude.isInitialized() || super.orderModules(ordering)
        return isTypechecked
    }

    override fun load(libraryManager: LibraryManager, typechecking: TypecheckingOrderingListener?): Boolean {
        if (prelude == null) {
            val text = String(ArendPreludeLibrary::class.java.getResourceAsStream("/lib/Prelude" + FileUtils.EXTENSION)!!.readBytes(), StandardCharsets.UTF_8)
            prelude = PsiFileFactory.getInstance(project).createFileFromText(PRELUDE_FILE_NAME, ArendLanguage.INSTANCE, text) as? ArendFile
            prelude?.virtualFile?.isWritable = false
        }

        return prelude != null && super.load(libraryManager, typechecking)
    }

    override fun resetGroup(group: ConcreteGroup) {}

    override fun resetDefinition(referable: LocatedReferable) {}

    override fun unload() = false

    override fun reset() {}

    companion object {
        const val PRELUDE = "Prelude"
        const val PRELUDE_FILE_NAME = PRELUDE + FileUtils.EXTENSION
    }
}