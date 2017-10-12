package org.vclang.psi

import com.intellij.extapi.psi.PsiFileBase
import com.intellij.openapi.fileTypes.FileType
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.jetbrains.jetpad.vclang.module.ModulePath
import com.jetbrains.jetpad.vclang.naming.reference.GlobalReferable
import com.jetbrains.jetpad.vclang.naming.scope.EmptyScope
import com.jetbrains.jetpad.vclang.naming.scope.LexicalScope
import com.jetbrains.jetpad.vclang.naming.scope.Scope
import com.jetbrains.jetpad.vclang.term.ChildGroup
import com.jetbrains.jetpad.vclang.term.Group
import com.jetbrains.jetpad.vclang.term.Precedence
import org.vclang.VcFileType
import org.vclang.VcLanguage
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.stubs.VcFileStub
import org.vclang.resolving.*
import java.nio.file.Paths

class VcFile(viewProvider: FileViewProvider) : PsiFileBase(viewProvider, VcLanguage), VcCompositeElement, PsiGlobalReferable, ChildGroup {
    override fun setName(name: String): PsiElement {
        val nameWithExtension = if (name.endsWith('.' + VcFileType.defaultExtension)) {
            name
        } else {
            "$name.${VcFileType.defaultExtension}"
        }
        return super.setName(nameWithExtension)
    }

    override fun getStub(): VcFileStub? = super.getStub() as VcFileStub?

    override val scope: Scope
        get() = LexicalScope(EmptyScope.INSTANCE, this)

    override fun getNameIdentifier(): PsiElement? = null

    override fun getReference(): VcReference? = null

    override fun getFileType(): FileType = VcFileType

    override fun textRepresentation(): String = name

    override fun getPrecedence(): Precedence = Precedence.DEFAULT

    override fun getParentGroup(): ChildGroup? = null

    override fun getReferable(): PsiGlobalReferable = this

    override fun getSubgroups(): List<VcDefinition> = children.mapNotNull { (it as? VcStatement)?.definition }

    override fun getNamespaceCommands(): List<VcStatCmd> = children.mapNotNull { (it as? VcStatement)?.statCmd }

    override fun getConstructors(): Collection<GlobalReferable> = emptyList()

    override fun getDynamicSubgroups(): Collection<Group> = emptyList()

    override fun getFields(): Collection<GlobalReferable> = emptyList()

    val relativeModulePath: ModulePath
        get() {
            val sourceRoot = sourceRoot ?: contentRoot ?: error("Failed to find source root")
            val sourcePath = Paths.get(sourceRoot.path)
            val modulePath = Paths.get(
                    virtualFile.path.removeSuffix('.' + VcFileType.defaultExtension)
            )
            val relativeModulePath = sourcePath.relativize(modulePath)
            return ModulePath(relativeModulePath.map { it.toString() })
        }

    override fun moduleTextRepresentation(): String = name

    override fun positionTextRepresentation(): String? = null
}
