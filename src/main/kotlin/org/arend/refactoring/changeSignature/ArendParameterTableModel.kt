package org.arend.refactoring.changeSignature

import com.intellij.codeInsight.daemon.DaemonCodeAnalyzer
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.util.Computable
import com.intellij.psi.PsiCodeFragment
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.refactoring.ui.CodeFragmentTableCellEditorBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import com.intellij.ui.EditorTextField
import org.arend.ArendFileType
import org.arend.naming.scope.Scope
import java.util.*
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer
import kotlin.collections.HashSet

class ArendParameterTableModel(val descriptor: ArendChangeSignatureDescriptor,
                               val scopeCalculator: (ArendChangeSignatureDialogParameterTableModelItem) -> () -> Scope,
                               defaultValueContext: PsiElement):
    ParameterTableModelBase<ArendParameterInfo, ArendChangeSignatureDialogParameterTableModelItem>(descriptor.method, defaultValueContext, ArendNameColumn(descriptor), ArendTypeColumn(descriptor), ArendImplicitnessColumn()) {
    override fun createRowItem(parameterInfo: ArendParameterInfo?): ArendChangeSignatureDialogParameterTableModelItem {
        val resultParameterInfo = if (parameterInfo == null) {
            val newParameter = ArendParameterInfo.createEmpty()
            newParameter
        } else parameterInfo
        
        var item: ArendChangeSignatureDialogParameterTableModelItem? = null
        val scope = { -> item?.let { scopeCalculator.invoke(it).invoke() } }

        item = ArendChangeSignatureDialogParameterTableModelItem(resultParameterInfo,
            ArendChangeSignatureDialogCodeFragment(myTypeContext.project, resultParameterInfo.typeText ?: "",
                scope, myTypeContext))

        return item
    }

    override fun removeRow(idx: Int) {
        super.removeRow(idx)
    }

    private class ArendNameColumn(val descriptor: ArendChangeSignatureDescriptor) :
        NameColumn<ArendParameterInfo, ArendChangeSignatureDialogParameterTableModelItem>(descriptor.method.project) {
        override fun setValue(item: ArendChangeSignatureDialogParameterTableModelItem, value: String?) {
            value ?: return

            super.setValue(item, value)
        }
    }

    private class ArendTypeColumn(descriptor: ArendChangeSignatureDescriptor) :
        TypeColumn<ArendParameterInfo, ArendChangeSignatureDialogParameterTableModelItem>(
            descriptor.method.project,
            ArendFileType
        ) {
        override fun setValue(item: ArendChangeSignatureDialogParameterTableModelItem?, value: PsiCodeFragment) {
            val fragment = value as? ArendChangeSignatureDialogCodeFragment ?: return
            item?.parameter?.setType(fragment.text)
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