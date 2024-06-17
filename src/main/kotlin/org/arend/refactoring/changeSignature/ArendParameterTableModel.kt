package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.refactoring.ui.CodeFragmentTableCellEditorBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import org.arend.ArendFileTypeInstance
import org.arend.ext.module.LongName
import org.arend.psi.ArendExpressionCodeFragment
import org.arend.util.FileUtils.isCorrectDefinitionName
import java.awt.Component
import java.util.Collections.singletonList
import javax.swing.JTable
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class ArendParameterTableModel(val descriptor: ArendChangeSignatureDescriptor,
                               private val dialog: ArendChangeSignatureDialog,
                               defaultValueContext: PsiElement):
    ParameterTableModelBase<ArendTextualParameter, ArendChangeSignatureDialogParameterTableModelItem>(descriptor.method, defaultValueContext, ArendNameColumn(descriptor, dialog), ArendTypeColumn(descriptor, dialog), ArendImplicitnessColumn()) {

    override fun createRowItem(parameterInfo: ArendTextualParameter?): ArendChangeSignatureDialogParameterTableModelItem {
        val resultParameterInfo = if (parameterInfo == null) {
            val newParameter = ArendTextualParameter.createEmpty()
            newParameter
        } else parameterInfo

        return ArendChangeSignatureDialogParameterTableModelItem(resultParameterInfo, ArendExpressionCodeFragment(myTypeContext.project, resultParameterInfo.typeText ?: "", myTypeContext, dialog))
    }

    private class ArendNameColumn(descriptor: ArendChangeSignatureDescriptor, val dialog: ArendChangeSignatureDialog): NameColumn<ArendTextualParameter, ArendChangeSignatureDialogParameterTableModelItem>(descriptor.method.project) {
        override fun setValue(item: ArendChangeSignatureDialogParameterTableModelItem, value: String?) {
            value ?: return
            val items = dialog.getParameterTableItems().toHashSet(); items.remove(item)
            if ((value == "_" || !items.any { it.parameter.name == value }) && isCorrectDefinitionName(LongName(singletonList(value)))) {
                val hasUsages = dialog.validateUsages(item, item.parameter.name)
                if (value == "_" && hasUsages) return //TODO: We may want to additionally prohibit changing name to "_" for those parameters, which have usages inside the definition
                dialog.refactorParameterNames(item, value)
                super.setValue(item, value)
            }
        }

    }

    private class ArendTypeColumn(descriptor: ArendChangeSignatureDescriptor, val dialog: ArendChangeSignatureDialog) :
        TypeColumn<ArendTextualParameter, ArendChangeSignatureDialogParameterTableModelItem>(descriptor.method.project, ArendFileTypeInstance) {
        override fun setValue(item: ArendChangeSignatureDialogParameterTableModelItem?, value: PsiCodeFragment) {
            val fragment = value as? ArendExpressionCodeFragment ?: return
            item?.parameter?.setType(fragment.text)
        }

        override fun doCreateEditor(o: ArendChangeSignatureDialogParameterTableModelItem?): TableCellEditor {
            return object: CodeFragmentTableCellEditorBase(myProject, ArendFileTypeInstance) {
                override fun getTableCellEditorComponent(table: JTable?, value: Any?, isSelected: Boolean, row: Int, column: Int): Component {
                    clearListeners()
                    val result = super.getTableCellEditorComponent(table, value, isSelected, row, column)
                    val myDocument = PsiDocumentManager.getInstance(myProject).getDocument(myCodeFragment)
                    if (myDocument != null) dialog.commonTypeFragmentListener.installDocumentListener(myDocument)
                    return result
                }
            }
        }
    }

    private class ArendImplicitnessColumn :
        ColumnInfoBase<ArendTextualParameter, ParameterTableModelItemBase<ArendTextualParameter>, Boolean>("Explicit") {
        override fun valueOf(item: ParameterTableModelItemBase<ArendTextualParameter>): Boolean =
            item.parameter.isExplicit()

        override fun setValue(item: ParameterTableModelItemBase<ArendTextualParameter>, value: Boolean?) {
            if (value == null) return
            item.parameter.switchExplicit()
        }

        override fun isCellEditable(item: ParameterTableModelItemBase<ArendTextualParameter>): Boolean = true

        override fun doCreateRenderer(item: ParameterTableModelItemBase<ArendTextualParameter>): TableCellRenderer =
            BooleanTableCellRenderer()

        override fun doCreateEditor(item: ParameterTableModelItemBase<ArendTextualParameter>): TableCellEditor =
            BooleanTableCellEditor()
    }
}