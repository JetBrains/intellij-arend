package org.arend.editor

import com.intellij.openapi.fileEditor.FileEditor
import com.intellij.openapi.fileEditor.FileEditorProvider
import com.intellij.openapi.fileEditor.ex.FileEditorProviderManager
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiFileFactory
import org.arend.ArendLanguage
import javax.swing.JComponent

class PidginArendEditor(text: CharSequence, project: Project) {
    private val fileEditorProvider: FileEditorProvider?
    private val fileEditor: FileEditor?

    init {
        val psi = PsiFileFactory.getInstance(project).createFileFromText("Dummy.ard", ArendLanguage.INSTANCE, text)
        val virtualFile = psi.virtualFile
        var myProvider: FileEditorProvider? = null
        for (provider in FileEditorProviderManager.getInstance().getProviders(project, virtualFile)) {
            if (provider.accept(project, virtualFile)) {
                myProvider = provider
                break
            }
        }
        fileEditorProvider = myProvider
        fileEditor = fileEditorProvider?.createEditor(project, virtualFile)
    }

    fun release() {
        if (fileEditor != null) {
            fileEditorProvider?.disposeEditor(fileEditor)
        }
    }

    val component: JComponent?
        get() = fileEditor?.component
}