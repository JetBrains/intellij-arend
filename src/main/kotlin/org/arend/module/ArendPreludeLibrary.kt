package org.arend.module

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.arend.ArendLanguage
import org.arend.ext.error.ErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.library.BaseLibrary
import org.arend.library.LibraryDependency
import org.arend.library.LibraryManager
import org.arend.module.scopeprovider.ModuleScopeProvider
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.LexicalScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.resolving.ArendReferableConverter
import org.arend.term.group.Group
import org.arend.typechecking.TypecheckerState
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.typechecking.provider.ConcreteProvider
import org.arend.util.FileUtils
import java.nio.charset.StandardCharsets


class ArendPreludeLibrary(private val project: Project, typecheckerState: TypecheckerState?) : BaseLibrary(typecheckerState) {
    var prelude: ArendFile? = null
        private set
    private var isTypechecked: Boolean = false
    private var scope: Scope? = null

    override fun getName() = Prelude.LIBRARY_NAME

    override fun getLoadedModules(): List<ModulePath> = if (prelude == null) emptyList() else listOf(Prelude.MODULE_PATH)

    override fun getDependencies(): List<LibraryDependency> = emptyList()

    override fun getModuleGroup(modulePath: ModulePath) = if (modulePath == Prelude.MODULE_PATH) prelude else null

    override fun getModuleScopeProvider() = ModuleScopeProvider { if (it == Prelude.MODULE_PATH) scope else null }

    override fun containsModule(modulePath: ModulePath) = modulePath == Prelude.MODULE_PATH

    override fun isExternal() = true

    override fun getUpdatedModules(): List<ModulePath> = if (isTypechecked) emptyList() else listOf(Prelude.MODULE_PATH)

    override fun orderModules(ordering: Ordering): Boolean {
        if (isTypechecked) return true

        if (!Prelude.isInitialized()) {
            synchronized(ArendPreludeLibrary::class.java) {
                if (!Prelude.isInitialized()) {
                    isTypechecked = super.orderModules(ordering)
                    return isTypechecked
                }
            }
        }

        isTypechecked = true
        Prelude.fillInTypecheckerState(typecheckerState)
        return true
    }

    override fun load(libraryManager: LibraryManager, typechecking: TypecheckingOrderingListener?): Boolean {
        if (prelude == null) {
            synchronized(ArendPreludeLibrary::class.java) {
                if (prelude == null) {
                    val text = String(ArendPreludeLibrary::class.java.getResourceAsStream("/lib/Prelude" + FileUtils.EXTENSION).readBytes(), StandardCharsets.UTF_8)
                    prelude = PsiFileFactory.getInstance(project).createFileFromText("Prelude" + FileUtils.EXTENSION, ArendLanguage.INSTANCE, text) as? ArendFile
                    prelude?.virtualFile?.isWritable = false
                }
            }
        }

        return prelude != null && super.load(libraryManager, typechecking)
    }

    override fun resetGroup(group: Group) {}

    override fun resetDefinition(referable: LocatedReferable) {}

    override fun unload() = false

    override fun reset() {}

    fun resolveNames(referableConverter: ArendReferableConverter, concreteProvider: ConcreteProvider, errorReporter: ErrorReporter) {
        if (scope != null) throw IllegalStateException()
        val preludeFile = prelude ?: return
        scope = CachingScope.make(LexicalScope.opened(preludeFile))
        if (Prelude.isInitialized()) {
            Prelude.forEach {
                val fullName = ArrayList<String>(2)
                LocatedReferable.Helper.getLocation(it.referable, fullName)
                val psiRef = Scope.Utils.resolveName(scope, fullName)
                if (psiRef is PsiLocatedReferable) {
                    referableConverter.putIfAbsent(psiRef, it.referable)
                }
            }
        }
        runReadAction { DefinitionResolveNameVisitor(concreteProvider, errorReporter).resolveGroup(preludeFile, null, scope) }
    }
}