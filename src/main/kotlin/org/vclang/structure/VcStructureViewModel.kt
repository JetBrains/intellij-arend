package org.vclang.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.NavigatablePsiElement
import com.jetbrains.jetpad.vclang.term.group.Group
import org.vclang.navigation.getPresentationForStructure
import org.vclang.psi.*
import org.vclang.psi.ext.PsiReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.impl.ClassDefinitionAdapter
import org.vclang.psi.ext.impl.DataDefinitionAdapter

class VcStructureViewModel(editor: Editor?, file: VcFile)
    : StructureViewModelBase(file, editor, VcStructureViewElement(file)),
        StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(PsiReferable::class.java)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean =
            element.value is VcFile

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = when (element.value) {
        is VcFile,
        is VcDefClass,
        is VcDefData,
        is VcDefInstance,
        is VcDefFunction -> false
        is VcClassField,
        is VcClassFieldSyn,
        is VcClassImplement,
        is VcConstructor -> true
        else -> error("Unexpected tree element")
    }
}

private class VcStructureViewElement(val psi: VcCompositeElement)
    : StructureViewTreeElement, Navigatable by (psi as NavigatablePsiElement) {

    override fun getValue(): Any = psi

    override fun getPresentation(): ItemPresentation = getPresentationForStructure(psi)

    override fun getChildren(): Array<TreeElement> =
            childElements.mapNotNull { e -> (e as? VcCompositeElement)?.let { VcStructureViewElement(it) } }.toTypedArray()

    private val childElements: List<Any>
        get() = when (psi) {
            is ClassDefinitionAdapter -> psi.fields
            is DataDefinitionAdapter -> psi.constructors
            else -> emptyList()
        } + ((psi as? Group)?.let { it.subgroups + it.dynamicSubgroups } ?: emptyList())
}
