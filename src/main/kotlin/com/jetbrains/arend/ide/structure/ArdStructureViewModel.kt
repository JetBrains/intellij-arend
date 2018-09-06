package com.jetbrains.arend.ide.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.NavigatablePsiElement
import com.jetbrains.arend.ide.navigation.getPresentationForStructure
import com.jetbrains.arend.ide.psi.*
import com.jetbrains.arend.ide.psi.ext.ArdCompositeElement
import com.jetbrains.arend.ide.psi.ext.PsiReferable
import com.jetbrains.arend.ide.psi.ext.impl.ClassDefinitionAdapter
import com.jetbrains.arend.ide.psi.ext.impl.DataDefinitionAdapter
import com.jetbrains.jetpad.vclang.term.group.Group

class ArdStructureViewModel(editor: Editor?, file: ArdFile)
    : StructureViewModelBase(file, editor, ArdStructureViewElement(file)),
        StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(PsiReferable::class.java)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean =
            element.value is ArdFile

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = when (element.value) {
        is ArdFile,
        is ArdDefClass,
        is ArdDefData,
        is ArdDefInstance,
        is ArdDefFunction -> false
        is ArdClassField,
        is ArdClassFieldSyn,
        is ArdClassImplement,
        is ArdConstructor -> true
        else -> error("Unexpected tree element")
    }
}

private class ArdStructureViewElement(val psi: ArdCompositeElement)
    : StructureViewTreeElement, Navigatable by (psi as NavigatablePsiElement) {

    override fun getValue(): Any = psi

    override fun getPresentation(): ItemPresentation = getPresentationForStructure(psi)

    override fun getChildren(): Array<TreeElement> =
            childElements.mapNotNull { e -> (e as? ArdCompositeElement)?.let { ArdStructureViewElement(it) } }.toTypedArray()

    private val childElements: List<Any>
        get() = when (psi) {
            is ClassDefinitionAdapter -> psi.fields
            is DataDefinitionAdapter -> psi.constructors
            else -> emptyList()
        } + ((psi as? Group)?.let { it.subgroups + it.dynamicSubgroups } ?: emptyList())
}
