package org.vclang

import com.intellij.icons.AllIcons
import org.vclang.psi.*
import org.vclang.psi.ext.PsiLocatedReferable
import javax.swing.Icon

object VcIcons {
    val VCLANG: Icon = AllIcons.Nodes.AnonymousClass
    val VCLANG_FILE: Icon = AllIcons.FileTypes.Idl
    val DIRECTORY: Icon = AllIcons.Nodes.Package

    // Source code elements

    val CLASS_DEFINITION = AllIcons.Nodes.Class!!
    val CLASS_FIELD = AllIcons.Nodes.Property!!
    val IMPLEMENTATION = AllIcons.General.Show_to_implement!!
    val CLASS_INSTANCE = AllIcons.Nodes.Interface!!
    val CONSTRUCTOR = AllIcons.Nodes.AbstractClass!!
    val DATA_DEFINITION = AllIcons.Nodes.EjbCmpField!!
    val FUNCTION_DEFINITION = AllIcons.Nodes.Field!!
    val MODULE: Icon = AllIcons.Nodes.JavaModule

    fun getIconFor(definition: PsiLocatedReferable) = when (definition) {
        is VcDefClass -> VcIcons.CLASS_DEFINITION
        is VcClassField, is VcFieldDefIdentifier, is VcClassFieldSyn -> VcIcons.CLASS_FIELD
        is VcConstructor -> VcIcons.CONSTRUCTOR
        is VcClassImplement -> VcIcons.IMPLEMENTATION
        is VcDefData -> VcIcons.DATA_DEFINITION
        is VcDefInstance -> VcIcons.CLASS_INSTANCE
        is VcDefFunction -> VcIcons.FUNCTION_DEFINITION
        is VcFile -> VcIcons.MODULE
        else -> null
    }
}
