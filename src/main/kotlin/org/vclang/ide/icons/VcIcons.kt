package org.vclang.ide.icons

import com.intellij.icons.AllIcons
import javax.swing.Icon

object VcIcons {
    val VCLANG: Icon = AllIcons.Nodes.AnonymousClass
    val VCLANG_FILE: Icon = AllIcons.FileTypes.Idl

    // Source code elements

    val CLASS_DEFINITION = AllIcons.Nodes.Class!!
    val CLASS_FIELD = AllIcons.Nodes.Property!!
    val IMPLEMENTATION = AllIcons.General.Show_to_implement!!
    val CLASS_VIEW = AllIcons.Nodes.Variable!!
    val CLASS_VIEW_FIELD = AllIcons.Nodes.Parameter!!
    val CLASS_VIEW_INSTANCE = AllIcons.Nodes.Interface!!
    val CONSTRUCTOR = AllIcons.Nodes.AbstractClass!!
    val DATA_DEFINITION = AllIcons.Nodes.EjbCmpField!!
    val FUNCTION_DEFINITION = AllIcons.Nodes.Field!!
}
