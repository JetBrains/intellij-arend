package org.vclang.module

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.jetpad.vclang.error.ErrorReporter
import com.jetbrains.jetpad.vclang.library.BaseLibrary
import com.jetbrains.jetpad.vclang.library.LibraryManager
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.module.scopeprovider.ModuleScopeProvider
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.converter.ReferableConverter
import com.jetbrains.jetpad.vclang.naming.resolving.visitor.DefinitionResolveNameVisitor
import com.jetbrains.jetpad.vclang.naming.scope.CachingScope
import com.jetbrains.jetpad.vclang.naming.scope.ConvertingScope
import com.jetbrains.jetpad.vclang.naming.scope.LexicalScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.prelude.Prelude
import com.jetbrains.jetpad.vclang.term.group.Group
import com.jetbrains.jetpad.vclang.typechecking.TypecheckerState
import com.jetbrains.jetpad.vclang.typechecking.Typechecking
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
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

        if (Prelude.INTERVAL == null) {
            synchronized(VcPreludeLibrary::class.java) {
                if (Prelude.INTERVAL == null) {
                    return if (super.typecheck(typechecking)) {
                        isTypechecked = true
                        Prelude.initialize(scope ?: return false, typecheckerState)
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
        if (prelude == null) {
            synchronized(VcPreludeLibrary::class.java) {
                if (prelude == null) {
                    val text = String(Files.readAllBytes(Paths.get(VcPreludeLibrary::class.java.getResource("/lib").toURI()).resolve(Paths.get("Prelude" + FileUtils.EXTENSION))), StandardCharsets.UTF_8)
                    prelude = PsiFileFactory.getInstance(project).createFileFromText("Prelude" + FileUtils.EXTENSION, VcLanguage.INSTANCE, text) as? VcFile
                    prelude?.virtualFile?.isWritable = false
                }
            }
        }

        return prelude != null && super.load(libraryManager)
    }

    override fun unload() {
        throw IllegalStateException("prelude cannot be unloaded")
    }

    override fun unloadGroup(group: Group) {
        throw IllegalStateException("prelude cannot be unloaded")
    }

    override fun unloadDefinition(referable: LocatedReferable) {
        throw IllegalStateException("prelude cannot be unloaded")
    }

    fun resolveNames(referableConverter: ReferableConverter, concreteProvider: ConcreteProvider, errorReporter: ErrorReporter) {
        if (scope != null) throw IllegalStateException()
        val preludeFile = prelude ?: return
        scope = CachingScope.make(ConvertingScope(referableConverter, LexicalScope.opened(preludeFile)))
        DefinitionResolveNameVisitor(concreteProvider, errorReporter).resolveGroup(preludeFile, null, scope)
    }
}