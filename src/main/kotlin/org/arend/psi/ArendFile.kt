package org.arend.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.util.CachedValueProvider
import com.intellij.psi.util.CachedValuesManager
import com.intellij.psi.util.PsiModificationTracker
import org.arend.ArendFileType
import org.arend.ArendIcons
import org.arend.ArendLanguage
import org.arend.ext.module.ModulePath
import org.arend.ext.reference.Precedence
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.ScopeFactory
import org.arend.prelude.Prelude
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.ext.TCDefinition
import org.arend.psi.ext.impl.ArendGroup
import org.arend.psi.ext.impl.ArendInternalReferable
import org.arend.psi.stubs.ArendFileStub
import org.arend.resolving.ArendReference
import org.arend.typechecking.provider.ConcreteProvider
import org.arend.typechecking.provider.EmptyConcreteProvider

class ArendFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ArendLanguage.INSTANCE), ArendSourceNode, PsiLocatedReferable, ArendGroup {
    val modulePath: ModulePath?
        get() {
            val fileName = originalFile.viewProvider.virtualFile.path
            if (fileName == "/Prelude.ard") {
                return Prelude.MODULE_PATH
            }
            val conf = libraryConfig ?: return null
            val root = conf.sourcesPath?.let { FileUtil.toSystemIndependentName(it.toString()) }
            if (root == null || !fileName.startsWith(root)) {
                return null
            }
            val fullName = fileName.removePrefix(root).removePrefix("/").removeSuffix('.' + ArendFileType.defaultExtension).replace('/', '.')
            return ModulePath(fullName.split('.'))
        }

    val fullName
        get() = modulePath?.toString() ?: name

    val libraryName
        get() = libraryConfig?.name ?: if (name == "Prelude.ard") Prelude.LIBRARY_NAME else null

    var concreteProvider: ConcreteProvider = EmptyConcreteProvider.INSTANCE

    var lastModifiedDefinition: TCDefinition? = null
        get() {
            if (field?.isValid == false) {
                field = null
            }
            return field
        }

    override fun setName(name: String): PsiElement =
        super.setName(if (name.endsWith('.' + ArendFileType.defaultExtension)) name else name + '.' + ArendFileType.defaultExtension)

    override fun getStub(): ArendFileStub? = super.getStub() as ArendFileStub?

    override fun getKind() = GlobalReferable.Kind.OTHER

    override val scope: Scope
        get() = CachedValuesManager.getCachedValue(this) {
            CachedValueProvider.Result(CachingScope.makeWithModules(ScopeFactory.forGroup(this, moduleScopeProvider)), PsiModificationTracker.MODIFICATION_COUNT)
        }

    override val defIdentifier: ArendDefIdentifier?
        get() = null

    override fun getLocation() = modulePath

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
