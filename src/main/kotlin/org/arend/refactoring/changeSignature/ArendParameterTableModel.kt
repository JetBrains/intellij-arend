package org.arend.refactoring.changeSignature

import com.intellij.codeInsight.highlighting.BraceMatchingUtil
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import org.arend.ArendFileType
import org.arend.ext.module.LongName
import org.arend.naming.scope.Scope
import org.arend.util.FileUtils.isCorrectDefinitionName
import java.util.Collections.singletonList
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class ArendParameterTableModel(val descriptor: ArendChangeSignatureDescriptor,
                               dialog: ArendChangeSignatureDialog,
                               val scopeCalculator: (ArendChangeSignatureDialogParameterTableModelItem) -> () -> Scope,
                               defaultValueContext: PsiElement):
    ParameterTableModelBase<ArendParameterInfo, ArendChangeSignatureDialogParameterTableModelItem>(descriptor.method, defaultValueContext, ArendNameColumn(descriptor, dialog), ArendTypeColumn(descriptor, dialog), ArendImplicitnessColumn()) {

    override fun createRowItem(parameterInfo: ArendParameterInfo?): ArendChangeSignatureDialogParameterTableModelItem {
        val resultParameterInfo = if (parameterInfo == null) {
            val newParameter = ArendParameterInfo.createEmpty()
            newParameter
        } else parameterInfo
        
        var item: ArendChangeSignatureDialogParameterTableModelItem? = null
        val scope = { -> item!!.let { scopeCalculator.invoke(it).invoke() } }

        item = ArendChangeSignatureDialogParameterTableModelItem(resultParameterInfo,
            ArendChangeSignatureDialogCodeFragment(myTypeContext.project, resultParameterInfo.typeText ?: "",
                scope, myTypeContext))

        return item
    }



    private class ArendNameColumn(descriptor: ArendChangeSignatureDescriptor, val dialog: ArendChangeSignatureDialog): NameColumn<ArendParameterInfo, ArendChangeSignatureDialogParameterTableModelItem>(descriptor.method.project) {
        override fun setValue(item: ArendChangeSignatureDialogParameterTableModelItem, value: String?) {
            value ?: return
            if (isCorrectDefinitionName(LongName(singletonList(value)))) {
                dialog.refactorParameterNames(item, value)
                super.setValue(item, value)
            }
        }

    }

    private class ArendTypeColumn(descriptor: ArendChangeSignatureDescriptor, val dialog: ArendChangeSignatureDialog) :
        TypeColumn<ArendParameterInfo, ArendChangeSignatureDialogParameterTableModelItem>(descriptor.method.project, ArendFileType) {
        override fun setValue(item: ArendChangeSignatureDialogParameterTableModelItem?, value: PsiCodeFragment) {
            val fragment = value as? ArendChangeSignatureDialogCodeFragment ?: return
            item?.parameter?.setType(fragment.text)
            //if (item != null) dialog.highlightDependentItems(item)
        }
    }

    private class ArendImplicitnessColumn :
        ColumnInfoBase<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>, Boolean>("Explicit") {
        override fun valueOf(item: ParameterTableModelItemBase<ArendParameterInfo>): Boolean =
            item.parameter.isExplicit()

        override fun setValue(item: ParameterTableModelItemBase<ArendParameterInfo>, value: Boolean?) {
            if (value == null) return
            item.parameter.switchExplicit()
        }

        override fun isCellEditable(item: ParameterTableModelItemBase<ArendParameterInfo>): Boolean = true

        override fun doCreateRenderer(item: ParameterTableModelItemBase<ArendParameterInfo>): TableCellRenderer =
            BooleanTableCellRenderer()

        override fun doCreateEditor(item: ParameterTableModelItemBase<ArendParameterInfo>): TableCellEditor =
            BooleanTableCellEditor()
    }
}