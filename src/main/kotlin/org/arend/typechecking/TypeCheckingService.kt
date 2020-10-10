package org.arend.typechecking

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.ProgressManager
import com.intellij.openapi.progress.Task
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import org.arend.core.definition.Definition
import org.arend.error.DummyErrorReporter
import org.arend.ext.module.LongName
import org.arend.extImpl.DefinitionRequester
import org.arend.library.Library
import org.arend.library.LibraryManager
import org.arend.module.*
import org.arend.yaml.YAMLFileListener
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable
import org.arend.naming.reference.TCDefReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.LexicalScope
import org.arend.naming.scope.Scope
import org.arend.prelude.Prelude
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendFile
import org.arend.psi.ext.PsiConcreteReferable
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.impl.ReferableAdapter
import org.arend.psi.ext.impl.fillAdditionalNames
import org.arend.psi.listener.ArendDefinitionChangeListener
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.resolving.ArendReferableConverter
import org.arend.resolving.ArendResolveCache
import org.arend.resolving.PsiConcreteProvider
import org.arend.settings.ArendProjectSettings
import org.arend.typechecking.computation.ComputationRunner
import org.arend.typechecking.error.ErrorService
import org.arend.typechecking.error.NotificationErrorReporter
import org.arend.typechecking.execution.PsiElementComparator
import org.arend.typechecking.instance.provider.InstanceProviderSet
import org.arend.typechecking.order.dependency.DependencyCollector
import org.arend.util.FullName
import org.arend.util.Range
import org.arend.util.Version
import org.arend.util.refreshLibrariesDirectory
import java.util.concurrent.ConcurrentHashMap

class TypeCheckingService(val project: Project) : ArendDefinitionChangeListener, DefinitionRequester {
    val dependencyListener = DependencyCollector()
    private val libraryErrorReporter = NotificationErrorReporter(project)
    val libraryManager = object : LibraryManager(ArendLibraryResolver(project), null, libraryErrorReporter, libraryErrorReporter, this) {
        override fun showLibraryNotFoundError(libraryName: String) {
            if (libraryName == AREND_LIB) {
                showDownloadNotification(project, false)
            } else {
                super.showLibraryNotFoundError(libraryName)
            }
        }

        override fun showIncorrectLanguageVersionError(libraryName: String?, range: Range<Version>?) {
            if (libraryName == AREND_LIB) {
                showDownloadNotification(project, true)
            } else {
                super.showIncorrectLanguageVersionError(libraryName, range)
            }
        }
    }

    private val extensionDefinitions = HashMap<TCDefReferable, Boolean>()

    private val additionalNames = HashMap<String, ArrayList<PsiLocatedReferable>>()

    val tcRefMaps = ConcurrentHashMap<ModuleLocation, HashMap<LongName, TCReferable>>()

    val updatedModules = HashSet<ModuleLocation>()

    var isInitialized = false
        private set

    var isLoaded = false
        private set

    fun initialize(): Boolean {
        if (isInitialized) {
            return false
        }

        synchronized(ArendPreludeLibrary::class.java) {
            if (isInitialized) {
                return false
            }

            // Initialize prelude
            val preludeLibrary = ArendPreludeLibrary(project)
            this.preludeLibrary = preludeLibrary
            libraryManager.loadLibrary(preludeLibrary, null)
            preludeLibrary.prelude?.generatedModuleLocation = Prelude.MODULE_LOCATION

            if (Prelude.isInitialized()) {
                val tcRefMap = preludeLibrary.prelude?.tcRefMap
                if (tcRefMap != null) {
                    Prelude.forEach {
                        tcRefMap[it.referable.refLongName] = it.referable
                    }
                }
            }

            val concreteProvider = PsiConcreteProvider(project, DummyErrorReporter.INSTANCE, null)
            preludeLibrary.resolveNames(concreteProvider, libraryManager.libraryErrorReporter)
            Prelude.PreludeTypechecking(InstanceProviderSet(), concreteProvider, ArendReferableConverter, PsiElementComparator).typecheckLibrary(preludeLibrary)
            preludeLibrary.prelude?.let {
                fillAdditionalNames(it, additionalNames)
            }

            // Set the listener that updates typechecked definitions
            project.service<ArendPsiChangeService>().addListener(this)

            // Listen for YAML files changes
            YAMLFileListener(project).register()

            ModuleSynchronizer(project).install()

            isInitialized = true
            isLoaded = true
        }

        return true
    }

    private var preludeLibrary: ArendPreludeLibrary? = null

    val prelude: ArendFile?
        get() = preludeLibrary?.prelude

    val preludeScope: Scope
        get() = prelude?.let { LexicalScope.opened(it) } ?: EmptyScope.INSTANCE

    fun getPsiReferable(referable: LocatedReferable): PsiLocatedReferable? {
        (referable.underlyingReferable as? PsiLocatedReferable)?.let { return it }
        return Scope.Utils.resolveName(preludeScope, referable.refLongName.toList()) as? PsiLocatedReferable
    }

    fun getDefinitionPsiReferable(definition: Definition) = getPsiReferable(definition.referable)

    fun reload(onlyInternal: Boolean, refresh: Boolean = true) {
        ComputationRunner.getCancellationIndicator().cancel()
        ProgressManager.getInstance().run(object : Task.Backgroundable(project, "Reloading Arend libraries", false) {
            override fun run(indicator: ProgressIndicator) {
                project.service<BinaryFileSaver>().saveAll()
                if (refresh) {
                    refreshLibrariesDirectory(project.service<ArendProjectSettings>().librariesRoot)
                }

                runReadAction {
                    isLoaded = false
                    if (onlyInternal) {
                        libraryManager.reloadInternalLibraries {
                            val it = extensionDefinitions.iterator()
                            while (it.hasNext()) {
                                if (it.next().value) {
                                    it.remove()
                                }
                            }

                            prepareReload()
                        }
                    } else {
                        libraryManager.reload {
                            project.service<ArendResolveCache>().clear()
                            extensionDefinitions.clear()
                            tcRefMaps.clear()

                            prepareReload()
                        }
                    }
                }

                isLoaded = true
                DaemonCodeAnalyzer.getInstance(project).restart()
            }
        })
    }

    private fun prepareReload(): ArendTypechecking {
        project.service<ErrorService>().clearAllErrors()
        project.service<ArendPsiChangeService>().incModificationCount()
        project.service<TypecheckingTaskQueue>().clearQueue()
        return ArendTypechecking.create(project)
    }

    override fun request(definition: Definition, library: Library) {
        extensionDefinitions[definition.referable] = !library.isExternal
    }

    fun getAdditionalReferables(name: String) = additionalNames[name] ?: emptyList()

    val additionalReferables: Map<String, List<PsiLocatedReferable>>
        get() = additionalNames

    private fun resetErrors(def: Referable, removeTCRef: Boolean) {
        if (removeTCRef) {
            (def as? ReferableAdapter<*>)?.dropTCCache()
        }
        if (def is TCDefinition) {
            project.service<ErrorService>().clearTypecheckingErrors(def)
        }
    }

    private fun removeDefinition(referable: LocatedReferable, removeTCRef: Boolean): TCReferable? {
        if (referable is PsiElement && !referable.isValid) {
            return null
        }

        val curRef = referable.underlyingReferable
        val fullName = FullName(referable)
        val tcRefMap = fullName.modulePath?.let { tcRefMaps[it] }
        val tcReferable = tcRefMap?.get(fullName.longName)
        if (tcReferable !is TCDefReferable) {
            resetErrors(curRef, removeTCRef)
            return tcReferable
        }

        if (extensionDefinitions.containsKey(tcReferable)) {
            service<ArendExtensionChangeListener>().notifyIfNeeded(project)
        }

        val prevRef = tcReferable.underlyingReferable
        if (curRef is PsiLocatedReferable && prevRef is PsiLocatedReferable && prevRef != curRef) {
            return null
        }
        if (removeTCRef) {
            tcRefMap.remove(fullName.longName)
        }
        resetErrors(curRef, removeTCRef)

        val tcTypecheckable = tcReferable.typecheckable
        tcTypecheckable.location?.let { updatedModules.add(it) }
        return tcTypecheckable
    }

    enum class LastModifiedMode { SET, SET_NULL, DO_NOT_TOUCH }

    private fun updateDefinition(referable: LocatedReferable, file: ArendFile, mode: LastModifiedMode, removeTCRef: Boolean) {
        if (mode != LastModifiedMode.DO_NOT_TOUCH && referable is TCDefinition) {
            val isValid = referable.isValid
            if (mode == LastModifiedMode.SET) {
                file.lastModifiedDefinition = if (isValid) referable else null
            } else {
                if (file.lastModifiedDefinition != referable) {
                    file.lastModifiedDefinition = null
                }
            }
        }

        val tcReferable = removeDefinition(referable, removeTCRef) ?: return
        val dependencies = synchronized(project) {
            dependencyListener.update(tcReferable)
        }
        for (ref in dependencies) {
            removeDefinition(ref, removeTCRef)
        }

        if ((referable as? ArendDefFunction)?.functionKw?.useKw != null) {
            (referable.parentGroup as? TCDefinition)?.let { updateDefinition(it, file, LastModifiedMode.DO_NOT_TOUCH, removeTCRef) }
        }
    }

    override fun updateDefinition(def: PsiConcreteReferable, file: ArendFile, isExternalUpdate: Boolean) {
        if (!isExternalUpdate) {
            def.checkTCReferableName()
        }
        updateDefinition(def, file, if (isExternalUpdate) LastModifiedMode.SET_NULL else LastModifiedMode.SET, !isExternalUpdate)
    }
}
