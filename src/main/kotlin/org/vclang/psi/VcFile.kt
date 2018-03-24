package org.vclang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.naming.scope.ScopeFactory
import com.jetbrains.jetpad.vclang.term.Precedence
import com.jetbrains.jetpad.vclang.term.group.ChildGroup
import com.jetbrains.jetpad.vclang.term.group.Group
import org.vclang.VcFileType
import org.vclang.VcLanguage
import org.vclang.psi.ext.PsiLocatedReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.stubs.VcFileStub
import org.vclang.resolving.VcReference

class VcFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VcLanguage), VcCompositeElement, PsiLocatedReferable, ChildGroup {
    var modulePath: ModulePath

    init {
        val fileName = viewProvider.virtualFile.path
        val root = (sourceRoot ?: contentRoot)?.path
        val shortFileName = if (root == null || !fileName.startsWith(root)) fileName else fileName.removePrefix(root)
        val fullName = shortFileName.removePrefix("/").removeSuffix('.' + VcFileType.defaultExtension).replace('/', '.')
        modulePath = ModulePath(fullName.split('.'))
    }

    val fullName
        get() = modulePath.toString()

    override fun setName(name: String): PsiElement {
        val (nameWithExt, nameWithoutExt) = if (name.endsWith('.' + VcFileType.defaultExtension)) {
            Pair(name, name.removeSuffix('.' + VcFileType.defaultExtension))
        } else {
            Pair(name + '.' + VcFileType.defaultExtension, name)
        }

        val list = modulePath.toList()
        modulePath = ModulePath(list.subList(0, list.size - 1) + nameWithoutExt)
        return super.setName(nameWithExt)
    }

    override fun getStub(): VcFileStub? = super.getStub() as VcFileStub?

    override val scope: Scope
        get() = ScopeFactory.forGroup(this, moduleScopeProvider)

    override fun getLocation(fullName: MutableList<in String>) = modulePath

    override fun getGroupScope() = scope

    override fun getNameIdentifier(): PsiElement? = null

    override fun getReference(): VcReference? = null

    override fun getFileType(): FileType = VcFileType

    override fun textRepresentation(): String = name.removeSuffix("." + VcFileType.defaultExtension)

    override fun isModule(): Boolean = true

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getParentGroup(): ChildGroup? = null

    override fun getReferable(): PsiLocatedReferable = this

    override fun getSubgroups(): List<VcDefinition> = children.mapNotNull { (it as? VcStatement)?.definition }

    override fun getNamespaceCommands(): List<VcStatCmd> = children.mapNotNull { (it as? VcStatement)?.statCmd }

    override fun getConstructors(): List<Group.InternalReferable> = emptyList()

    override fun getDynamicSubgroups(): List<Group> = emptyList()

    override fun getFields(): List<Group.InternalReferable> = emptyList()

    override fun moduleTextRepresentation(): String = name

    override fun positionTextRepresentation(): String? = null
}
