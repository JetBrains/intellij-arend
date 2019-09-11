package org.arend.typechecking

import com.intellij.openapi.actionSystem.KeyboardShortcut
import com.intellij.openapi.components.service
import com.intellij.openapi.keymap.KeymapManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.error.DummyErrorReporter
import org.arend.library.LibraryManager
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ModulePath
import org.arend.module.ModuleSynchronizer
import org.arend.module.YAMLFileListener
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.SimpleReferableConverter
import org.arend.prelude.Prelude
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.listener.ArendPsiListener
import org.arend.psi.listener.ArendPsiListenerService
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.dependency.DependencyCollector
import org.arend.util.FullName
import java.awt.event.InputEvent
import java.awt.event.KeyEvent
import javax.swing.KeyStroke

class TypeCheckingService(val project: Project) : ArendPsiListener() {
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
        libraryManager.loadLibrary(preludeLibrary)
        val referableConverter = newReferableConverter(false)
        val concreteProvider = PsiConcreteProvider(project, referableConverter, DummyErrorReporter.INSTANCE, null)
        preludeLibrary.resolveNames(referableConverter, concreteProvider, libraryManager.libraryErrorReporter)
        Prelude.PreludeTypechecking(PsiInstanceProviderSet(concreteProvider, referableConverter), typecheckerState, concreteProvider, PsiElementComparator).typecheckLibrary(preludeLibrary)

        // Set the listener that updates typechecked definitions
        project.service<ArendPsiListenerService>().addListener(this)

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

    fun getTypechecked(definition: ArendDefinition) =
        simpleReferableConverter.toDataLocatedReferable(definition)?.let { typecheckerState.getTypechecked(it) }

    private fun removeDefinition(referable: LocatedReferable): TCReferable? {
        if (referable is PsiElement && !referable.isValid) {
            return null
        }

        val fullName = FullName(referable)
        val tcReferable = simpleReferableConverter.remove(referable, fullName) ?: return null
        val curRef = referable.underlyingReferable
        val prevRef = tcReferable.underlyingReferable
        if (curRef is PsiLocatedReferable && prevRef is PsiLocatedReferable && prevRef != curRef) {
            if (FullName(prevRef) == fullName) {
                simpleReferableConverter.putIfAbsent(referable, tcReferable)
            }
            return null
        }

        if (curRef is ArendDefinition) {
            project.service<ErrorService>().clearTypecheckingErrors(curRef)
        }

        val tcTypecheckable = tcReferable.typecheckable ?: return null
        tcTypecheckable.location?.let { updatedModules.add(it) }
        return tcTypecheckable
    }

    enum class LastModifiedMode { SET, SET_NULL, DO_NOT_TOUCH }

    fun updateDefinition(referable: LocatedReferable, file: ArendFile?, mode: LastModifiedMode) {
        if (mode != LastModifiedMode.DO_NOT_TOUCH && referable is ArendDefinition) {
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
        for (ref in dependencyListener.update(tcReferable)) {
            removeDefinition(ref)
        }

        if (referable is ArendDefFunction && referable.useKw != null) {
            (referable.parentGroup as? ArendDefinition)?.let { updateDefinition(it, file, LastModifiedMode.DO_NOT_TOUCH) }
        }
    }

    override fun updateDefinition(def: ArendDefinition, file: ArendFile, isExternalUpdate: Boolean) {
        updateDefinition(def, file, if (isExternalUpdate) LastModifiedMode.SET_NULL else LastModifiedMode.SET)
    }
}
