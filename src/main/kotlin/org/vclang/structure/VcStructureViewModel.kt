package org.vclang.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.NavigatablePsiElement
import org.vclang.navigation.getPresentationForStructure
import org.vclang.psi.*
import org.vclang.psi.ext.PsiGlobalReferable
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.PsiReferable

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
        is VcDefClassView,
        is VcDefData,
        is VcDefFunction -> false
        is VcClassField,
        is VcClassImplement,
        is VcClassViewField,
        is VcDefInstance,
        is VcConstructor -> true
        else -> error("Unexpected tree element")
    }
}

private class VcStructureViewElement(val psi: VcCompositeElement)
    : StructureViewTreeElement, Navigatable by (psi as NavigatablePsiElement) {

    override fun getValue(): Any = psi

    override fun getPresentation(): ItemPresentation = getPresentationForStructure(psi)

    override fun getChildren(): Array<TreeElement> =
            childElements.sortedBy { it.textOffset }.map(::VcStructureViewElement).toTypedArray()

    private val childElements: List<VcCompositeElement>
        get() = when (psi) {
            is VcFile -> psi.children.filterIsInstance<VcStatement>().mapNotNull { it.definition }
            is VcDefClass -> psi.childDefinitions
            is VcDefClassView -> psi.classViewFieldList
            is VcDefData -> psi.dataBody?.constructorClauseList?.flatMap { it.constructorList } ?: psi.dataBody?.constructorList ?: emptyList()
            is VcDefFunction -> psi.where?.childDefinitions ?: emptyList()
            else -> emptyList()
        }
}

private val VcDefClass.childDefinitions: List<PsiGlobalReferable>
    get() {
        val classDefinitions = classStatList.mapNotNull { it.classField ?: it.definition as PsiGlobalReferable }
        val whereDefinitions = where?.childDefinitions ?: emptyList()
        return classDefinitions + whereDefinitions
    }

private val VcWhere.childDefinitions: List<VcDefinition>
    get() = statementList.mapNotNull { it.definition }
