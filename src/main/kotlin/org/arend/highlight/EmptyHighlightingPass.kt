package org.arend.highlight

import com.intellij.codeHighlighting.TextEditorHighlightingPass
import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerEx
import com.intellij.openapi.editor.Document
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.project.Project

class EmptyHighlightingPass(project: Project, document: Document) : TextEditorHighlightingPass(project, document, false) {
    override fun doCollectInformation(progress: ProgressIndicator) {}

    override fun doApplyInformationToEditor() {
        DaemonCodeAnalyzerEx.getInstanceEx(myProject).fileStatusMap.markFileUpToDate(document, id)
    }
}