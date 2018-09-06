package com.jetbrains.arend.ide

import com.intellij.icons.AllIcons
import javax.swing.Icon

object ArdIcons {
    val AREND: Icon = AllIcons.Nodes.AnonymousClass
    val AREND_FILE: Icon = AllIcons.FileTypes.Idl
    val AREND_LIB_FILE: Icon = AllIcons.FileTypes.WsdlFile
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
}
