package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiTypeCodeFragmentImpl
import com.intellij.refactoring.changeSignature.ParameterTableModelBase
import com.intellij.refactoring.changeSignature.ParameterTableModelItemBase
import com.intellij.ui.BooleanTableCellEditor
import com.intellij.ui.BooleanTableCellRenderer
import org.arend.ArendFileType
import javax.swing.table.TableCellEditor
import javax.swing.table.TableCellRenderer

class ArendParameterTableModel(
    val descriptor: ArendSignatureDescriptor,
    defaultValueContext: PsiElement,
) : ParameterTableModelBase<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(
    descriptor.method,
    defaultValueContext,
    NameColumn<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(defaultValueContext.project),
    TypeColumn<ArendParameterInfo, ParameterTableModelItemBase<ArendParameterInfo>>(
        descriptor.method.project, ArendFileType
    ),
    ParamImplicitnessColumn()
) {
    override fun createRowItem(parameterInfo: ArendParameterInfo?): ParameterTableModelItemBase<ArendParameterInfo> {
        val resultParameterInfo = parameterInfo ?: ArendParameterInfo.createEmpty()

        val typeCodeFragment = PsiTypeCodeFragmentImpl(
            myTypeContext.project,
            true,
            "fragment.ard",
            resultParameterInfo.typeText,
            0,
            myTypeContext
        )

        return object : ParameterTableModelItemBase<ArendParameterInfo>(
            resultParameterInfo,
            typeCodeFragment,
            null
        ) {
            override fun isEllipsisType(): Boolean = false
        }
    }

    private class ParamImplicitnessColumn :
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