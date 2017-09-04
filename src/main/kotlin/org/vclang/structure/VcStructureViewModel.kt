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
import org.vclang.psi.VcClassField
import org.vclang.psi.VcClassImplement
import org.vclang.psi.VcClassStat
import org.vclang.psi.VcClassViewField
import org.vclang.psi.VcConstructor
import org.vclang.psi.VcDataBody
import org.vclang.psi.VcDataClauses
import org.vclang.psi.VcDefClass
import org.vclang.psi.VcDefClassView
import org.vclang.psi.VcDefData
import org.vclang.psi.VcDefFunction
import org.vclang.psi.VcDefInstance
import org.vclang.psi.VcDefinition
import org.vclang.psi.VcFile
import org.vclang.psi.VcStatement
import org.vclang.psi.VcWhere
import org.vclang.psi.ext.VcCompositeElement
import org.vclang.psi.ext.VcNamedElement

class VcStructureViewModel(editor: Editor?, file: VcFile)
    : StructureViewModelBase(file, editor, VcStructureViewElement(file)),
      StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(VcNamedElement::class.java)
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
            is VcFile -> psi.childDefinitions
            is VcDefClass -> psi.childDefinitions
            is VcDefClassView -> psi.childDefinitions
            is VcDefData -> psi.childDefinitions
            is VcDefFunction -> psi.childDefinitions
            else -> emptyList()
        }
}

private val VcFile.childDefinitions: List<VcDefinition>
    get() = children
            .filterIsInstance<VcStatement>()
            .mapNotNull { it.childDefinition }

private val VcDefClass.childDefinitions: List<VcDefinition>
    get() {
        val classStats = classStats?.classStatList
        val classDefinitions = classStats?.mapNotNull { it.childDefinition } ?: emptyList()
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
