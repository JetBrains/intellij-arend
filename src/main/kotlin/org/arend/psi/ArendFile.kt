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
import org.arend.module.ModulePath
import org.arend.module.config.ArendModuleConfigService
import org.arend.naming.reference.GlobalReferable
import org.arend.naming.reference.LocatedReferable
import org.arend.naming.reference.Reference
import org.arend.naming.scope.CachingScope
import org.arend.naming.scope.Scope
import org.arend.naming.scope.ScopeFactory
import org.arend.prelude.Prelude
import org.arend.psi.ext.ArendSourceNode
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.psi.stubs.ArendFileStub
import org.arend.resolving.ArendReference
import org.arend.term.Precedence
import org.arend.term.abs.Abstract
import org.arend.term.group.ChildGroup
import org.arend.term.group.Group

class ArendFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, ArendLanguage.INSTANCE), ArendSourceNode, PsiLocatedReferable, ChildGroup {
    val modulePath: ModulePath?
        get() {
            val fileName = originalFile.viewProvider.virtualFile.path
            if (fileName == "/Prelude.ard") {
                return ModulePath("Prelude")
            }
            val module = module ?: return null
            val root = ArendModuleConfigService.getConfig(module).sourcesPath?.let { FileUtil.toSystemIndependentName(it.toString()) }
            if (root == null || !fileName.startsWith(root)) {
                return null
            }
            val fullName = fileName.removePrefix(root).removePrefix("/").removeSuffix('.' + ArendFileType.defaultExtension).replace('/', '.')
            return ModulePath(fullName.split('.'))
        }

    val fullName
        get() = modulePath?.toString() ?: name

    val libraryName
        get() = module?.name ?: if (name == "Prelude.ard") Prelude.LIBRARY_NAME else null

    override fun setName(name: String): PsiElement =
        super.setName(if (name.endsWith('.' + ArendFileType.defaultExtension)) name else name + '.' + ArendFileType.defaultExtension)

    override fun getStub(): ArendFileStub? = super.getStub() as ArendFileStub?

    override fun getKind() = GlobalReferable.Kind.OTHER

    override val scope: Scope
        get() = CachedValuesManager.getCachedValue(this) {
            CachedValueProvider.Result(CachingScope.makeWithModules(ScopeFactory.forGroup(this, moduleScopeProvider)), PsiModificationTracker.MODIFICATION_COUNT)
        }

    override fun getLocation() = modulePath

    override fun getTypecheckable(): PsiLocatedReferable = this

    override fun getLocatedReferableParent(): LocatedReferable? = null

    override fun getGroupScope() = scope

    override fun getNameIdentifier(): PsiElement? = null

    override fun getReference(): ArendReference? = null

    override fun getFileType(): FileType = ArendFileType

    override fun textRepresentation(): String = name.removeSuffix("." + ArendFileType.defaultExtension)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getParentGroup(): ChildGroup? = null

    override fun getReferable(): PsiLocatedReferable = this

    override fun getSubgroups(): List<ChildGroup> = children.mapNotNull { child -> (child as? ArendStatement)?.let { it.definition ?: it.defModule as ChildGroup? } }

    override fun getNamespaceCommands(): List<ArendStatCmd> = children.mapNotNull { (it as? ArendStatement)?.statCmd }

    override fun getConstructors(): List<Group.InternalReferable> = emptyList()

    override fun getDynamicSubgroups(): List<Group> = emptyList()

    override fun getFields(): List<Group.InternalReferable> = emptyList()

    override fun moduleTextRepresentation(): String = name

    override fun positionTextRepresentation(): String? = null

    override fun getUnderlyingReference(): LocatedReferable? = null

    override fun getUnresolvedUnderlyingReference(): Reference? = null

    override fun getTopmostEquivalentSourceNode() = this

    override fun getParentSourceNode(): ArendSourceNode? = null

    override fun getErrorData(): Abstract.ErrorData? = null

    override fun getIcon(flags: Int) = ArendIcons.MODULE
}
