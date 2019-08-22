package org.arend.typechecking

import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.libraries.Library
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPointerManager
import com.intellij.psi.SmartPsiElementPointer
import org.arend.core.definition.Definition
import org.arend.error.DummyErrorReporter
import org.arend.error.ErrorReporter
import org.arend.error.GeneralError
import org.arend.library.LibraryManager
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ModulePath
import org.arend.naming.reference.DataContainer
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.reference.converter.SimpleReferableConverter
import org.arend.prelude.Prelude
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ancestor
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.listener.ArendPsiListener
import org.arend.psi.listener.ArendPsiListenerService
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.PsiConcreteProvider
import org.arend.term.prettyprint.PrettyPrinterConfig
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.order.dependency.DependencyCollector
import org.arend.typechecking.order.dependency.DependencyListener
import org.arend.util.FullName
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashSet

interface TypeCheckingService : ErrorReporter {
    val libraryManager: LibraryManager

    val typecheckerState: TypecheckerState

    val dependencyListener: DependencyListener

    val project: Project

    val prelude: ArendFile?

    val updatedModules: HashSet<ModulePath>

    val isInitialized: Boolean

    fun initialize(): Boolean

    fun newReferableConverter(withPsiReferences: Boolean): ArendReferableConverter

    fun getTypechecked(definition: ArendDefinition): Definition?

    fun updateDefinition(referable: LocatedReferable)

    fun getErrors(file: ArendFile): List<Pair<GeneralError, ArendCompositeElement>>

    fun clearErrors(definition: ArendDefinition)

    fun addLibrary(library: Library)

    fun removeLibrary(library: Library): String?

    fun getLibraryName(library: Library): String?

    companion object {
        fun getInstance(project: Project): TypeCheckingService {
            val service = ServiceManager.getService(project, TypeCheckingService::class.java)
            return checkNotNull(service) { "Failed to get TypeCheckingService for $project" }
        }
    }
}

class TypeCheckingServiceImpl(override val project: Project) : ArendPsiListener(), TypeCheckingService {
    override val typecheckerState = SimpleTypecheckerState()
    override val dependencyListener = DependencyCollector(typecheckerState)
    private val libraryErrorReporter = NotificationErrorReporter(project, PrettyPrinterConfig.DEFAULT)
    override val libraryManager = LibraryManager(ArendLibraryResolver(project), null, libraryErrorReporter, libraryErrorReporter)

    private val simpleReferableConverter = SimpleReferableConverter()

    override val updatedModules = HashSet<ModulePath>()

    override fun newReferableConverter(withPsiReferences: Boolean) =
        ArendReferableConverter(if (withPsiReferences) project else null, simpleReferableConverter)

    override var isInitialized = false
        private set

    private val errorMap = WeakHashMap<ArendFile, MutableList<Pair<GeneralError, SmartPsiElementPointer<*>>>>()

    override fun initialize(): Boolean {
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
        ArendPsiListenerService.getInstance(project).addListener(this)

        isInitialized = true
        return true
    }

    override val prelude: ArendFile?
        get() {
            for (library in libraryManager.registeredLibraries) {
                if (library is ArendPreludeLibrary) {
                    return library.prelude
                }
            }
            return null
        }

    override fun getTypechecked(definition: ArendDefinition) =
        simpleReferableConverter.toDataLocatedReferable(definition)?.let { typecheckerState.getTypechecked(it) }

    override fun clearErrors(definition: ArendDefinition) {
        val errors = (definition.containingFile as? ArendFile)?.let { errorMap[it] }
        if (errors != null) {
            val it = errors.iterator()
            while (it.hasNext()) {
                val pair = it.next()
                if (definition == (pair.second.element as? ArendCompositeElement)?.ancestor<ArendDefinition>()) {
                    it.remove()
                }
            }
        }
    }

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
            clearErrors(curRef)
        }

        val tcTypecheckable = tcReferable.typecheckable ?: return null
        tcTypecheckable.location?.let { updatedModules.add(it) }
        return tcTypecheckable
    }

    override fun updateDefinition(referable: LocatedReferable) {
        if (referable is ArendDefinition && referable.isValid) {
            (referable.containingFile as? ArendFile)?.lastModifiedDefinition = referable
        }

        val tcReferable = removeDefinition(referable) ?: return
        for (ref in dependencyListener.update(tcReferable)) {
            removeDefinition(ref)
        }

        if (referable is ArendDefFunction && referable.useKw != null) {
            (referable.parentGroup as? ArendDefinition)?.let { updateDefinition(it as LocatedReferable) }
        }
    }

    override fun updateDefinition(def: ArendDefinition) {
        updateDefinition(def as LocatedReferable)
    }

    override fun report(error: GeneralError) {
        if (!error.isTypecheckingError) {
            return
        }

        val list = error.cause?.let { it as? Collection<*> ?: listOf(it) } ?: return
        runReadAction {
            loop@ for (data in list) {
                val element: ArendCompositeElement
                val pointer: SmartPsiElementPointer<*>
                when (val cause = (data as? DataContainer)?.data ?: data) {
                    is ArendCompositeElement -> {
                        element = cause
                        pointer = SmartPointerManager.createPointer(cause)
                    }
                    is SmartPsiElementPointer<*> -> {
                        element = cause.element as? ArendCompositeElement ?: continue@loop
                        pointer = cause
                    }
                    else -> continue@loop
                }
                val file = element.containingFile as? ArendFile ?: continue
                errorMap.computeIfAbsent(file) { LinkedList() }.add(Pair(error, pointer))
            }
        }
    }

    override fun getErrors(file: ArendFile): List<Pair<GeneralError, ArendCompositeElement>> {
        val errors = errorMap[file] ?: return emptyList()

        val list = ArrayList<Pair<GeneralError, ArendCompositeElement>>()
        for (pair in errors) {
            (pair.second.element as? ArendCompositeElement)?.let {
                list.add(Pair(pair.first, it))
            }
        }
        return list
    }

    private val libraryMap = WeakHashMap<Library, String>()

    override fun addLibrary(library: Library) {
        library.name?.let { libraryMap.putIfAbsent(library, it) }
    }

    override fun removeLibrary(library: Library) = libraryMap.remove(library)

    override fun getLibraryName(library: Library) = libraryMap[library]
}
