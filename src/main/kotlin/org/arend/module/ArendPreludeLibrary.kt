package org.arend.module

import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.arend.error.ErrorReporter
import org.arend.library.BaseLibrary
import org.arend.library.LibraryManager
import org.arend.module.scopeprovider.ModuleScopeProvider
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.naming.resolving.visitor.DefinitionResolveNameVisitor
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.ConvertingScope
import org.arend.naming.scope.LexicalScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.term.group.Group
import org.arend.typechecking.TypecheckerState
import org.arend.typechecking.order.Ordering
import org.arend.typechecking.typecheckable.provider.ConcreteProvider
import org.arend.util.FileUtils
import org.arend.ArendLanguage
import org.arend.psi.ArendFile
import java.nio.charset.StandardCharsets


class ArendPreludeLibrary(private val project: Project, typecheckerState: TypecheckerState?) : BaseLibrary(typecheckerState) {
    var prelude: ArendFile? = null
        private set
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
            synchronized(ArendPreludeLibrary::class.java) {
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
            synchronized(ArendPreludeLibrary::class.java) {
                if (prelude == null) {
                    val text = String(ArendPreludeLibrary::class.java.getResourceAsStream("/lib/Prelude" + FileUtils.EXTENSION).readBytes(), StandardCharsets.UTF_8)
                    prelude = PsiFileFactory.getInstance(project).createFileFromText("Prelude" + FileUtils.EXTENSION, ArendLanguage.INSTANCE, text) as? ArendFile
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