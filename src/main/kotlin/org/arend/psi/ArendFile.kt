package org.arend.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.components.service
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.resolve.FileContextUtil
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.arend.ArendFileType
import org.arend.ArendIcons
import org.arend.ArendLanguage
import org.arend.ext.module.LongName
import org.arend.ext.reference.Precedence
import org.arend.injection.PsiInjectionTextFile
import org.arend.module.ModuleLocation
import org.arend.module.config.LibraryConfig
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.TCReferable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.EmptyScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.ScopeFactory
import org.arend.prelude.Prelude
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.ext.impl.ArendInternalReferable
import org.arend.psi.listener.ArendDefinitionChangeService
import org.arend.psi.stubs.ArendFileStub
import org.arend.resolving.ArendReference
import org.arend.typechecking.TypeCheckingService
import org.arend.typechecking.provider.ConcreteProvider
import org.arend.typechecking.provider.EmptyConcreteProvider

class ArendFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ArendLanguage.INSTANCE), ArendSourceNode, PsiLocatedReferable, ArendGroup {
    var generatedModuleLocation: ModuleLocation? = null

    /**
     * You can enforce the scope of a file to be something else.
     */
    var enforcedScope: (() -> Scope)? = null

    val isReplFile get() = enforcedScope != null

    var enforcedLibraryConfig: LibraryConfig? = null

    val moduleLocation: ModuleLocation?
        get() = generatedModuleLocation ?: arendLibrary?.config?.getFileModulePath(this)

    val fullName: String
        get() = moduleLocation?.toString() ?: name

    val libraryName: String?
        get() = arendLibrary?.name ?: if (name == "Prelude.ard") Prelude.LIBRARY_NAME else null

    var concreteProvider: ConcreteProvider = EmptyConcreteProvider.INSTANCE

    var lastModifiedDefinition: TCDefinition? = null
        get() {
            if (field?.isValid == false) {
                field = null
            }
            return field
        }

    private var tcRefMapCache: HashMap<LongName, TCReferable>? = null

    val tcRefMap: HashMap<LongName, TCReferable>
        get() {
            tcRefMapCache?.let { return it }
            val location = moduleLocation ?: return HashMap()
            return synchronized(this) {
                tcRefMapCache ?: project.service<TypeCheckingService>().tcRefMaps.computeIfAbsent(location) { HashMap() }.also { tcRefMapCache = it }
            }
        }

    fun dropTCRefMapCache() {
        tcRefMapCache = null
    }

    override fun setName(name: String): PsiElement =
        super.setName(if (name.endsWith('.' + ArendFileType.defaultExtension)) name else name + '.' + ArendFileType.defaultExtension)

    override fun getStub(): ArendFileStub? = super.getStub() as ArendFileStub?

    override fun getKind() = GlobalReferable.Kind.OTHER

    val injectionContext: PsiElement?
        get() = FileContextUtil.getFileContext(this)

    val isInjected: Boolean
        get() = injectionContext != null

    override val scope: Scope
        get() = CachedValuesManager.getCachedValue(this) {
            val scopeSupplier = (originalFile as? ArendFile ?: this).enforcedScope
            if (scopeSupplier != null) return@getCachedValue cachedScope(scopeSupplier())
            val injectedIn = injectionContext
            cachedScope(if (injectedIn != null) {
                (injectedIn.containingFile as? PsiInjectionTextFile)?.scope
                    ?: EmptyScope.INSTANCE
            } else {
                CachingScope.make(ScopeFactory.forGroup(this, moduleScopeProvider))
            })
        }

    private fun cachedScope(scope: Scope?) =
        CachedValueProvider.Result(scope, PsiModificationTracker.MODIFICATION_COUNT, project.service<ArendDefinitionChangeService>())

    override val defIdentifier: ArendDefIdentifier?
        get() = null

    override val tcReferable: TCReferable?
        get() = null

    override fun dropTypechecked() {}

    override fun checkTCReferable() {}

    override fun getLocation() = moduleLocation

    override fun getTypecheckable(): PsiLocatedReferable = this

    override fun getLocatedReferableParent(): LocatedReferable? = null

    override fun getGroupScope() = scope

    override fun getNameIdentifier(): PsiElement? = null

    override fun getReference(): ArendReference? = null

    override fun getFileType(): FileType = ArendFileType

    override fun textRepresentation(): String = name.removeSuffix("." + ArendFileType.defaultExtension)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getParentGroup(): ArendGroup? = null

    override fun getReferable(): PsiLocatedReferable = this

    override fun getSubgroups(): List<ArendGroup> = children.mapNotNull { child -> (child as? ArendStatement)?.let { it.definition ?: it.defModule } }

    override fun getDynamicSubgroups(): List<ArendGroup> = emptyList()

    override fun getInternalReferables(): List<ArendInternalReferable> = emptyList()

    override fun getNamespaceCommands(): List<ArendStatCmd> = children.mapNotNull { (it as? ArendStatement)?.statCmd }

    override val statements
        get() = children.filterIsInstance<ArendStatement>()

    override val where: ArendWhere?
        get() = null

    override fun moduleTextRepresentation(): String = name

    override fun positionTextRepresentation(): String? = null

    override fun getTopmostEquivalentSourceNode() = this

    override fun getParentSourceNode(): ArendSourceNode? = null

    override fun getIcon(flags: Int) = ArendIcons.AREND_FILE
}
