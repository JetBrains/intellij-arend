package org.arend.module.util

import com.intellij.openapi.module.ModuleConfigurationEditor
import com.intellij.openapi.roots.ui.configuration.ClasspathEditor
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationEditorProvider
import com.intellij.openapi.roots.ui.configuration.ModuleConfigurationState

class ArendModuleConfigEditorProvider: ModuleConfigurationEditorProvider {
    override fun createEditors(state: ModuleConfigurationState): Array<ModuleConfigurationEditor> {
        val editor = ClasspathEditor(state)

        //editor.addListener {
         //   System.out.println("ModuleEditor: configuration changed")
       // }
        /*
        (editor.createComponent() as? ClasspathPanelImpl)?.addListener {
            val module = state.rootModel.module
            val rootModel = state.rootModel
            val libEntriesNames = rootModel.moduleLibraryTable.libraries.map { it.name }.toSet()
            // if (module.libraryConfig?.dependencies?.map { it.name }?.toSet()?.equals(libEntriesNames) == true) return
            module.libraryConfig?.dependencies = libEntriesNames.map { LibraryDependency(it) }.toList()
        } */
        return arrayOf(editor)
    }
}