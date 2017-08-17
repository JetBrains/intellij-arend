package org.vclang.ide.structureView

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.NavigatablePsiElement
import org.vclang.ide.presentation.getPresentationForStructure
import org.vclang.lang.core.psi.*
import org.vclang.lang.core.psi.ext.VcCompositeElement
import org.vclang.lang.core.psi.ext.VcNamedElement

class VcStructureViewModel(editor: Editor?, file: VcFile)
    : StructureViewModelBase(file, editor, VcStructureViewElement(file)),
      StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(VcNamedElement::class.java)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement) = element.value is VcFile

    override fun isAlwaysLeaf(element: StructureViewTreeElement) = when (element.value) {
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
        else -> throw IllegalStateException()
    }

}

private class VcStructureViewElement(val psi: VcCompositeElement)
    : StructureViewTreeElement, Navigatable by (psi as NavigatablePsiElement) {

    override fun getValue() = psi

    override fun getPresentation(): ItemPresentation = getPresentationForStructure(psi)

    override fun getChildren(): Array<TreeElement> =
            childElements.sortedBy { it.textOffset }.map(::VcStructureViewElement).toTypedArray()

    private val childElements: List<VcCompositeElement>
        get() = when (psi) {
            is VcFile -> psi.childDefinitions
            is VcDefClass -> psi.childDefinitions
            is VcDefClassView -> psi.childDefinitions
            is VcDefData -> psi.childDefinitions
            is VcDefFunction -> psi.childDefinitions
            else -> emptyList()
        }
}

private val VcFile.childDefinitions: List<VcDefinition>
    get() = childOfType<VcStatements>()
            ?.statementList
            ?.mapNotNull { it.childDefinition }
            ?: emptyList()

private val VcDefClass.childDefinitions: List<VcDefinition>
    get() {
        val classDefinitions = classStatList.mapNotNull { it.childDefinition }
        val whereDefinitions = where?.childDefinitions ?: emptyList()
        return classDefinitions + whereDefinitions
    }

private val VcDefClassView.childDefinitions: List<VcDefinition>
    get() = classViewFieldList

private val VcDefData.childDefinitions: List<VcDefinition>
    get() = dataBody?.childDefinitions ?: emptyList()

private val VcDefFunction.childDefinitions: List<VcDefinition>
    get() = where?.childDefinitions ?: emptyList()

private val VcClassStat.childDefinition: VcDefinition?
    get() = definition ?: statement?.childDefinition

private val VcStatement.childDefinition: VcDefinition?
    get() = statDef?.definition

private val VcWhere.childDefinitions: List<VcDefinition>
    get() = statementList.mapNotNull { it.childDefinition }

private val VcDataBody.childDefinitions: List<VcDefinition>
    get() = dataClauses?.childDefinitions ?: dataConstructors?.constructorList ?: emptyList()

private val VcDataClauses.childDefinitions: List<VcDefinition>
    get() = constructorClauseList.flatMap { it.constructorList }
