package org.arend.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.logger
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.project.Project
import com.intellij.openapi.roots.LibraryOrderEntry
import com.intellij.openapi.roots.ProjectFileIndex
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.SmartPsiElementPointer
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.arend.ArendFileTypeInstance
import org.arend.ArendIcons
import org.arend.ArendLanguage
import org.arend.ext.module.LongName
import org.arend.ext.reference.Precedence
import org.arend.injection.PsiInjectionTextFile
import org.arend.module.ArendPreludeLibrary
import org.arend.module.ArendRawLibrary
import org.arend.module.ModuleLocation
import org.arend.module.config.ArendModuleConfigService
import org.arend.module.config.LibraryConfig
import org.arend.module.orderRoot.ArendConfigOrderRootType
import org.arend.module.scopeprovider.ModuleScopeProvider
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Referable.RefKind
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.*
import org.arend.prelude.Prelude
import org.arend.psi.ext.*
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.psi.stubs.ArendFileStub
import org.arend.resolving.ArendReference
import org.arend.resolving.DataLocatedReferable
import org.arend.typechecking.BackgroundTypechecker
import org.arend.typechecking.TypeCheckingService
import org.arend.util.FileUtils
import org.arend.util.libraryName
import org.arend.util.mapFirstNotNull
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicLong

class ArendFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ArendLanguage.INSTANCE), ArendSourceNode, PsiLocatedReferable, ArendGroup {
    var generatedModuleLocation: ModuleLocation? = null

    /**
     * You can enforce the scope of a file to be something else.
     */
    var enforcedScope: () -> Scope? = { null }

    var enforcedLibraryConfig: LibraryConfig? = null

    val isRepl: Boolean
        get() = enforcedLibraryConfig != null

    var lastModification: AtomicLong = AtomicLong(-1)
    var lastDefinitionModification: AtomicLong = AtomicLong(-1)

    val isBackgroundTypecheckingFinished: Boolean
        get() = lastDefinitionModification.get() >= project.service<ArendPsiChangeService>().definitionModificationTracker.modificationCount

    val moduleLocation: ModuleLocation?
        get() = generatedModuleLocation ?: CachedValuesManager.getCachedValue(this) {
            cachedValue(arendLibrary?.config?.getFileModulePath(this))
        }

    val fullName: String
        get() = moduleLocation?.toString() ?: name

    val libraryName: String?
        get() = arendLibrary?.name ?: if (name == ArendPreludeLibrary.PRELUDE_FILE_NAME) Prelude.LIBRARY_NAME else null

    var lastModifiedDefinition: TCDefinition? = null
        get() {
            if (field?.isValid == false) {
                field = null
            }
            return field
        }

    class LifetimeAwareDefinitionRegistry : ConcurrentHashMap<LongName, TCReferable>() {
        override fun get(key: LongName): TCReferable? {
            val tc = super.get(key)
            if (tc is DataLocatedReferable && tc.data != null && tc.data!!.element == null) {
                logger<BackgroundTypechecker>().error("""
                    | Invalid definition ${tc.refLongName} in caches. 
                    | It does not do any harm generally, but there may be some incorrect red code in the editor.
                    | Please try to find a stable reproducer for this behavior and report it to maintainers.
                    | Typical reproduction actions involve deleting some parts of text or clearing and re-inserting the whole file.""".trimMargin())
            }
            return tc
        }

        fun cleanup(project: Project?) {
            val keysToRemove = this.filterValues {
                val pointer = it.data as? SmartPsiElementPointer<*>
                pointer != null && pointer.element == null
            }
            val typecheckingService = project?.service<TypeCheckingService>()
            for ((key, value) in keysToRemove) {
                remove(key)
                typecheckingService?.dependencyListener?.update(value)
            }
        }
    }

    fun getTCRefMap(refKind: RefKind): LifetimeAwareDefinitionRegistry {
        val location = moduleLocation ?: return LifetimeAwareDefinitionRegistry()
        return project.service<TypeCheckingService>().getTCRefMaps(refKind).computeIfAbsent(location) { LifetimeAwareDefinitionRegistry() }
    }

    fun cleanupTCRefMaps() {
        for (refKind in RefKind.entries) {
            getTCRefMap(refKind).cleanup(project)
        }
    }

    override fun setName(name: String): PsiElement =
        super.setName(if (name.endsWith(FileUtils.EXTENSION)) name else name + FileUtils.EXTENSION)

    override fun getStub(): ArendFileStub? = super.getStub() as ArendFileStub?

    override fun getKind() = GlobalReferable.Kind.OTHER

    val injectionContext: PsiElement?
        get() = FileContextUtil.getFileContext(this)

    val isInjected: Boolean
        get() = injectionContext != null

    override val scope: Scope
        get() = CachedValuesManager.getCachedValue(this) {
            val enforcedScope = (originalFile as? ArendFile ?: this).enforcedScope()
            if (enforcedScope != null) return@getCachedValue cachedValue(enforcedScope)
            val injectedIn = injectionContext
            cachedValue(if (injectedIn != null) {
                (injectedIn.containingFile as? PsiInjectionTextFile)?.scope
                    ?: EmptyScope.INSTANCE
            } else {
                CachingScope.make(ScopeFactory.forGroup(this, moduleScopeProvider))
            })
        }

    private fun <T> cachedValue(value: T) =
        CachedValueProvider.Result(value, PsiModificationTracker.MODIFICATION_COUNT)

    val arendLibrary: ArendRawLibrary?
        get() = CachedValuesManager.getCachedValue(this) {
            val virtualFile = originalFile.virtualFile ?: return@getCachedValue cachedValue(null)
            val project = project
            if (!project.isOpen) {
                return@getCachedValue cachedValue(null)
            }
            val fileIndex = ProjectFileIndex.getInstance(project)

            val module = runReadAction { fileIndex.getModuleForFile(virtualFile) }
            if (module != null) {
                return@getCachedValue cachedValue(ArendModuleConfigService.getInstance(module)?.library)
            }

            if (!fileIndex.isInLibrarySource(virtualFile)) {
                return@getCachedValue cachedValue(null)
            }

            for (orderEntry in fileIndex.getOrderEntriesForFile(virtualFile)) {
                if (orderEntry is LibraryOrderEntry) {
                    for (file in orderEntry.getRootFiles(ArendConfigOrderRootType)) {
                        val libName = file.libraryName ?: continue
                        return@getCachedValue cachedValue(ArendRawLibrary.getExternalLibrary(project.service<TypeCheckingService>().libraryManager, libName))
                    }
                }
            }

            cachedValue(null)
        }

    val moduleScopeProvider: ModuleScopeProvider
        get() = CachedValuesManager.getCachedValue(this) {
            val arendFile = originalFile as? ArendFile ?: this
            val config = arendFile.arendLibrary?.config
            val typecheckingService = arendFile.project.service<TypeCheckingService>()
            val inTests = config?.getFileLocationKind(arendFile) == ModuleLocation.LocationKind.TEST
            cachedValue(ModuleScopeProvider { modulePath ->
                val file = if (modulePath == Prelude.MODULE_PATH) {
                    typecheckingService.prelude
                } else {
                    if (config == null) {
                        return@ModuleScopeProvider typecheckingService.libraryManager.registeredLibraries.mapFirstNotNull {
                            it.moduleScopeProvider.forModule(modulePath)
                        }
                    } else {
                        config.forAvailableConfigs { it.findArendFile(modulePath, true, inTests) }
                    }
                }
                file?.let { LexicalScope.opened(it) }
            })
        }

    override val defIdentifier: ArendDefIdentifier?
        get() = null

    override val tcReferable: TCReferable?
        get() = null

    override fun dropTypechecked() {}

    override fun dropTCReferable() {}

    override fun checkTCReferable() = true

    override fun checkTCReferableName() {}

    override fun getLocation() = moduleLocation

    override fun getTypecheckable(): PsiLocatedReferable = this

    override fun getLocatedReferableParent(): LocatedReferable? = null

    override fun getGroupScope(extent: LexicalScope.Extent) = scope

    override fun getNameIdentifier(): PsiElement? = null

    override fun getReference(): ArendReference? = null

    override fun getFileType(): FileType = ArendFileTypeInstance

    override fun textRepresentation(): String = name.removeSuffix(FileUtils.EXTENSION)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getParentGroup(): ArendGroup? = null

    override fun getReferable(): PsiLocatedReferable = this

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()

    override fun getStatements() = children.filterIsInstance<ArendStat>()

    override val where: ArendWhere?
        get() = null

    override fun moduleTextRepresentation(): String = name

    override fun positionTextRepresentation(): String? = null

    override fun getTopmostEquivalentSourceNode() = this

    override fun getParentSourceNode(): ArendSourceNode? = null

    override fun getIcon(flags: Int) = ArendIcons.AREND_FILE
}
