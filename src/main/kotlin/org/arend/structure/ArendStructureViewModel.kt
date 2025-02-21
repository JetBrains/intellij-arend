package org.arend.structure

import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.pom.Navigatable
import com.intellij.psi.NavigatablePsiElement
import org.arend.navigation.getPresentationForStructure
import org.arend.psi.ArendFile
import org.arend.psi.ext.*

class ArendStructureViewModel(editor: Editor?, file: ArendFile)
    : StructureViewModelBase(file, editor, ArendStructureViewElement(file)),
        StructureViewModel.ElementInfoProvider {

    init {
        withSuitableClasses(PsiReferable::class.java)
    }

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean =
            element.value is ArendFile

    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean = when (element.value) {
        is ArendFile,
        is ArendDefClass,
        is ArendDefData,
        is ArendDefInstance,
        is ArendDefModule,
        is ArendDefFunction,
        is ArendDefMeta -> false
        is ArendClassFieldBase<*>,
        is ArendClassImplement,
        is ArendCoClauseDef,
        is ArendConstructor -> true
        else -> error("Unexpected tree anchor")
    }
}

private class ArendStructureViewElement(val psi: ArendCompositeElement)
    : StructureViewTreeElement, Navigatable by (psi as NavigatablePsiElement) {

    override fun getValue(): Any = psi

    override fun getPresentation(): ItemPresentation = getPresentationForStructure(psi)

    override fun getChildren(): Array<TreeElement> =
            childElements.mapNotNull { e -> (e as? ArendCompositeElement)?.let { ArendStructureViewElement(it) } }.toTypedArray()

    private val childElements: List<Any>
        get() = (psi as? ArendGroup)?.let { it.internalReferables + it.statements.mapNotNull { stat -> stat.group } + it.dynamicSubgroups } ?: emptyList()
}
