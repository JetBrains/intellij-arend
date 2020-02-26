package org.arend.typechecking

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.ModulePath
import org.arend.ext.prettyprinting.PrettyPrinterConfig
import org.arend.library.LibraryManager
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ModuleSynchronizer
import org.arend.yaml.YAMLFileListener
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.SimpleReferableConverter
import org.arend.prelude.Prelude
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.TCDefinition
import org.arend.psi.listener.ArendDefinitionChangeListener
import org.arend.psi.listener.ArendDefinitionChangeService
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.dependency.DependencyCollector
import org.arend.typechecking.order.listener.TypecheckingOrderingListener
import org.arend.util.FullName
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TypeCheckingService(val project: Project) : ArendDefinitionChangeListener {
    val typecheckerState = SimpleTypecheckerState()
    val dependencyListener = DependencyCollector(typecheckerState)
    private val libraryErrorReporter = NotificationErrorReporter(project, PrettyPrinterConfig.DEFAULT)
    val libraryManager = LibraryManager(ArendLibraryResolver(project), null, libraryErrorReporter, libraryErrorReporter)

    private val simpleReferableConverter = SimpleReferableConverter()

    val updatedModules = HashSet<ModulePath>()

    fun newReferableConverter(withPsiReferences: Boolean) =
        ArendReferableConverter(if (withPsiReferences) project else null, simpleReferableConverter)

    var isInitialized = false
        private set

    fun initialize(): Boolean {
        if (isInitialized) {
            return false
        }

        // Initialize prelude
        val preludeLibrary = ArendPreludeLibrary(project, typecheckerState)
        libraryManager.loadLibrary(preludeLibrary, null)
        val referableConverter = newReferableConverter(false)
        val concreteProvider = PsiConcreteProvider(project, referableConverter, DummyErrorReporter.INSTANCE, null)
        preludeLibrary.resolveNames(referableConverter, concreteProvider, libraryManager.libraryErrorReporter)
        Prelude.PreludeTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), typecheckerState, concreteProvider, PsiElementComparator).typecheckLibrary(preludeLibrary)

        // Set the listener that updates typechecked definitions
        project.service<ArendDefinitionChangeService>().addListener(this)

        // Listen for YAML files changes
        YAMLFileListener(project).register()

        KeymapManager.getInstance().activeKeymap.addShortcut("Arend.ImplementedFields", KeyboardShortcut(KeyStroke.getKeyStroke(KeyEvent.VK_H, InputEvent.ALT_MASK or InputEvent.SHIFT_MASK), null))

        ModuleSynchronizer(project).install()

        isInitialized = true
        return true
    }

    val prelude: ArendFile?
        get() {
            for (library in libraryManager.registeredLibraries) {
                if (library is ArendPreludeLibrary) {
                    return library.prelude
                }
            }
            return null
        }

    fun getTypechecked(definition: TCDefinition) =
        simpleReferableConverter.toDataLocatedReferable(definition)?.let { typecheckerState.getTypechecked(it) }

    private fun removeDefinition(referable: LocatedReferable): TCReferable? {
        if (referable is PsiElement && !referable.isValid) {
            return null
        }

        val fullName = FullName(referable)
        val tcReferable = simpleReferableConverter.remove(fullName) ?: return null
        val curRef = referable.underlyingReferable
        val prevRef = tcReferable.underlyingReferable
        if (curRef is PsiLocatedReferable && prevRef is PsiLocatedReferable && prevRef != curRef) {
            if (FullName(prevRef) == fullName) {
                simpleReferableConverter.putIfAbsent(referable, tcReferable)
            } else {
                simpleReferableConverter.removeInternalReferables(referable, fullName)
            }
            return null
        }
        simpleReferableConverter.removeInternalReferables(referable, fullName)

        if (curRef is TCDefinition) {
            project.service<ErrorService>().clearTypecheckingErrors(curRef)
        }

        val tcTypecheckable = tcReferable.typecheckable ?: return null
        tcTypecheckable.location?.let { updatedModules.add(it) }
        return tcTypecheckable
    }

    enum class LastModifiedMode { SET, SET_NULL, DO_NOT_TOUCH }

    fun updateDefinition(referable: LocatedReferable, file: ArendFile?, mode: LastModifiedMode) {
        if (mode != LastModifiedMode.DO_NOT_TOUCH && referable is TCDefinition) {
            val isValid = referable.isValid
            (file ?: if (isValid) referable.containingFile as? ArendFile else null)?.let {
                if (mode == LastModifiedMode.SET) {
                    it.lastModifiedDefinition = if (isValid) referable else null
                } else {
                    if (it.lastModifiedDefinition != referable) {
                        it.lastModifiedDefinition = null
                    }
                }
            }
        }

        val tcReferable = removeDefinition(referable) ?: return
        val dependencies = synchronized(project) {
            dependencyListener.update(tcReferable)
        }
        for (ref in dependencies) {
            removeDefinition(ref)
        }

        if ((referable as? ArendDefFunction)?.functionKw?.useKw != null) {
            (referable.parentGroup as? TCDefinition)?.let { updateDefinition(it, file, LastModifiedMode.DO_NOT_TOUCH) }
        }
    }

    override fun updateDefinition(def: TCDefinition, file: ArendFile, isExternalUpdate: Boolean) {
        if (TypecheckingOrderingListener.getCancellationIndicator() is ArendCancellationIndicator) {
            synchronized(typecheckerState) {
                (TypecheckingOrderingListener.getCancellationIndicator() as? ArendCancellationIndicator)?.progress?.cancel()
                TypecheckingOrderingListener.resetCancellationIndicator()
            }
        }

        updateDefinition(def, file, if (isExternalUpdate) LastModifiedMode.SET_NULL else LastModifiedMode.SET)
    }
}
