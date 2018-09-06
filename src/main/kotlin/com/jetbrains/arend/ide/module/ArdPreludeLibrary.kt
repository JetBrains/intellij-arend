package com.jetbrains.arend.ide.module

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import com.jetbrains.arend.ide.psi.ArdFile
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
import com.jetbrains.jetpad.vclang.typechecking.order.Ordering
import com.jetbrains.jetpad.vclang.typechecking.typecheckable.provider.ConcreteProvider
import com.jetbrains.jetpad.vclang.util.FileUtils
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths


class ArdPreludeLibrary(private val project: Project, typecheckerState: TypecheckerState?) : BaseLibrary(typecheckerState) {
    private var prelude: ArdFile? = null
    private var isTypechecked: Boolean = false
    private var scope: Scope? = null

    override fun getName() = "prelude"

    override fun getLoadedModules(): List<ModulePath> = if (prelude == null) emptyList() else listOf(Prelude.MODULE_PATH)

    override fun getModuleGroup(modulePath: ModulePath) = if (modulePath == Prelude.MODULE_PATH) prelude else null

    override fun getModuleScopeProvider() = ModuleScopeProvider { if (it == Prelude.MODULE_PATH) scope else null }

    override fun containsModule(modulePath: ModulePath) = modulePath == Prelude.MODULE_PATH

    override fun needsTypechecking() = !isTypechecked

    override fun getUpdatedModules(): List<ModulePath> = if (isTypechecked) emptyList() else listOf(Prelude.MODULE_PATH)

    override fun orderModules(ordering: Ordering): Boolean {
        if (isTypechecked) return true

        if (Prelude.INTERVAL == null) {
            synchronized(ArdPreludeLibrary::class.java) {
                if (Prelude.INTERVAL == null) {
                    return if (super.orderModules(ordering)) {
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
            synchronized(ArdPreludeLibrary::class.java) {
                if (prelude == null) {
                    val text = String(Files.readAllBytes(Paths.get(ArdPreludeLibrary::class.java.getResource("/lib").toURI()).resolve(Paths.get("Prelude" + FileUtils.EXTENSION))), StandardCharsets.UTF_8)
                    prelude = PsiFileFactory.getInstance(project).createFileFromText("Prelude" + FileUtils.EXTENSION, com.jetbrains.arend.ide.ArdLanguage.INSTANCE, text) as? ArdFile
                    prelude?.virtualFile?.isWritable = false
                }
            }
        }

        return prelude != null && super.load(libraryManager)
    }

    override fun unloadGroup(group: Group) {

    }

    override fun unloadDefinition(referable: LocatedReferable) {

    }

    fun resolveNames(referableConverter: ReferableConverter, concreteProvider: ConcreteProvider, errorReporter: ErrorReporter) {
        if (scope != null) throw IllegalStateException()
        val preludeFile = prelude ?: return
        scope = CachingScope.make(ConvertingScope(referableConverter, LexicalScope.opened(preludeFile)))
        DefinitionResolveNameVisitor(concreteProvider, errorReporter).resolveGroup(preludeFile, null, scope)
    }
}