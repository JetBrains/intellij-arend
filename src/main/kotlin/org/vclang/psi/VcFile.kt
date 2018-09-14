package org.vclang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.util.io.FileUtil
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.reference.LocatedReferable
import com.jetbrains.jetpad.vclang.naming.reference.Reference
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.abs.Abstract
import com.jetbrains.jetpad.vclang.term.group.ChildGroup
import com.jetbrains.jetpad.vclang.term.group.Group
import org.vclang.VcFileType
import org.vclang.VcIcons
import org.vclang.VcLanguage
import org.vclang.module.util.sourcesDir
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.VcSourceNode
import org.vclang.psi.stubs.VcFileStub
import org.vclang.resolving.VcReference

class VcFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VcLanguage.INSTANCE), VcSourceNode, PsiLocatedReferable, ChildGroup {
    val modulePath: ModulePath
        get() {
            val fileName = viewProvider.virtualFile.path
            val root = module?.sourcesDir?.let { FileUtil.toSystemIndependentName(it) }
            val shortFileName = if (root == null || !fileName.startsWith(root)) fileName else fileName.removePrefix(root)
            val fullName = shortFileName.removePrefix("/").removeSuffix('.' + VcFileType.defaultExtension).replace('/', '.')
            return ModulePath(fullName.split('.'))
        }

    val fullName
        get() = modulePath.toString()

    val libraryName
        get() = module?.name ?: if (name == "Prelude.vc") "prelude" else null

    override fun setName(name: String): PsiElement =
        super.setName(if (name.endsWith('.' + VcFileType.defaultExtension)) name else name + '.' + VcFileType.defaultExtension)

    override fun getStub(): VcFileStub? = super.getStub() as VcFileStub?

    override fun getKind() = GlobalReferable.Kind.OTHER

    override val scope: Scope
        get() = ScopeFactory.forGroup(this, moduleScopeProvider)

    override fun getLocation() = modulePath

    override fun getTypecheckable(): PsiLocatedReferable = this

    override fun getLocatedReferableParent(): LocatedReferable? = null

    override fun getGroupScope() = scope

    override fun getNameIdentifier(): PsiElement? = null

    override fun getReference(): VcReference? = null

    override fun getFileType(): FileType = VcFileType

    override fun textRepresentation(): String = name.removeSuffix("." + VcFileType.defaultExtension)

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getParentGroup(): ChildGroup? = null

    override fun getReferable(): PsiLocatedReferable = this

    override fun getSubgroups(): List<ChildGroup> = children.mapNotNull { (it as? VcStatement)?.let { it.definition ?: it.defModule as ChildGroup? } }

    override fun getNamespaceCommands(): List<VcStatCmd> = children.mapNotNull { (it as? VcStatement)?.statCmd }

    override fun getConstructors(): List<Group.InternalReferable> = emptyList()

    override fun getDynamicSubgroups(): List<Group> = emptyList()

    override fun getFields(): List<Group.InternalReferable> = emptyList()

    override fun moduleTextRepresentation(): String = name

    override fun positionTextRepresentation(): String? = null

    override fun getUnderlyingReference(): LocatedReferable? = null

    override fun getUnresolvedUnderlyingReference(): Reference? = null

    override fun getTopmostEquivalentSourceNode() = this

    override fun getParentSourceNode(): VcSourceNode? = null

    override fun getErrorData(): Abstract.ErrorData? = null

    override fun getIcon(flags: Int) = VcIcons.MODULE
}
