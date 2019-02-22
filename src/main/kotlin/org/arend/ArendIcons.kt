package org.arend

import com.intellij.icons.AllIcons
import javax.swing.Icon

object ArendIcons {
    val AREND: Icon = AllIcons.Nodes.AnonymousClass
    val AREND_MODULE: Icon = AllIcons.Nodes.AnonymousClass
    val AREND_FILE: Icon = AllIcons.FileTypes.Idl
    val DIRECTORY: Icon = AllIcons.Nodes.Package
    val LIBRARY_ICON: Icon? = AllIcons.Nodes.PpLib
    val YAML_KEY: Icon? = AllIcons.Nodes.FieldPK

    val RUN_CONFIGURATION: Icon = AllIcons.RunConfigurations.Application

    // Source code elements

    val CLASS_DEFINITION = AllIcons.Nodes.Class!!
    val CLASS_FIELD = AllIcons.Nodes.Property!!
    val IMPLEMENTATION = AllIcons.General.Show_to_implement!!
    val CLASS_INSTANCE = AllIcons.Nodes.Interface!!
    val CONSTRUCTOR = AllIcons.Nodes.AbstractClass!!
    val DATA_DEFINITION = AllIcons.Nodes.EjbCmpField!!
    val FUNCTION_DEFINITION = AllIcons.Nodes.Field!!
    val MODULE_DEFINITION = AllIcons.Nodes.Method!!
    val MODULE: Icon = AllIcons.Nodes.JavaModule
}
