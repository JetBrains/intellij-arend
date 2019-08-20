package org.arend

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import org.arend.error.GeneralError
import javax.swing.Icon

object ArendIcons {
    val AREND: Icon = AllIcons.Nodes.AnonymousClass
    val AREND_MODULE: Icon = AllIcons.Nodes.AnonymousClass
    val AREND_FILE: Icon = AllIcons.Nodes.JavaModule!! // AllIcons.FileTypes.AS
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

    val MESSAGES = AllIcons.Toolwindows.ToolWindowMessages!!

    val SHOW_FIELDS_IMPL = IconLoader.getIcon("/icons/showFieldImpl.svg")
    val SHOW_NON_IMPLEMENTED = IconLoader.getIcon("/icons/showNonImpl.svg")

    val ERROR = AllIcons.RunConfigurations.ToolbarError!!
    val WARNING = AllIcons.RunConfigurations.ToolbarFailed!!
    val GOAL = IconLoader.getIcon("/icons/goal.svg")
    val INFO = AllIcons.General.NotificationInfo!!

    fun getErrorLevelIcon(level: GeneralError.Level) = when (level) {
        GeneralError.Level.INFO -> INFO
        GeneralError.Level.WEAK_WARNING -> WARNING
        GeneralError.Level.WARNING -> WARNING
        GeneralError.Level.GOAL -> GOAL
        GeneralError.Level.ERROR -> ERROR
    }
}
