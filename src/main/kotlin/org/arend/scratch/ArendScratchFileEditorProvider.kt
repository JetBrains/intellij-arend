package org.arend.scratch

import com.intellij.diff.tools.util.BaseSyncScrollable
import com.intellij.diff.tools.util.SyncScrollSupport.TwosideSyncScrollSupport
import com.intellij.openapi.Disposable
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.serviceAsync
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.EditorKind
import com.intellij.openapi.editor.event.VisibleAreaListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.fileEditor.*
import com.intellij.openapi.fileEditor.impl.text.TextEditorProvider
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Computable
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.pom.Navigatable
import com.intellij.psi.PsiManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jetbrains.annotations.TestOnly
import org.jetbrains.kotlin.idea.KotlinJvmBundle
import org.jetbrains.kotlin.idea.base.psi.getLineNumber
import org.jetbrains.kotlin.idea.scratch.*
import org.jetbrains.kotlin.idea.scratch.output.*
import org.jetbrains.kotlin.idea.scratch.ui.ScratchEditorLinesTranslator
import org.jetbrains.kotlin.idea.scratch.ui.configureSyncHighlighting
import org.jetbrains.kotlin.idea.scratch.ui.createScratchFile
import org.jetbrains.kotlin.psi.UserDataProperty

private const val AREND_SCRATCH_EDITOR_PROVIDER: String = "ArendScratchFileEditorProvider"

private class ArendScratchFileEditorProvider : FileEditorProvider, AsyncFileEditorProvider {
    override fun getEditorTypeId(): String = AREND_SCRATCH_EDITOR_PROVIDER

    override fun accept(project: Project, file: VirtualFile): Boolean {
        if (!file.isValid) {
            return false
        }
        if (!file.isArendScratch) {
            return false
        }
        val psiFile = ApplicationManager.getApplication().runReadAction(Computable { PsiManager.getInstance(project).findFile(file) })
            ?: return false
        return ScratchFileLanguageProvider.get(psiFile.fileType) != null
    }

    override fun acceptRequiresReadAction(): Boolean = false

    override suspend fun createFileEditor(
        project: Project,
        file: VirtualFile,
        document: Document?,
        editorCoroutineScope: CoroutineScope
    ): FileEditor {
        val textEditorProvider = TextEditorProvider.getInstance()
        val scratchFile = readAction { createScratchFile(project, file) }
            ?: return textEditorProvider.createFileEditor(
                project = project,
                file = file,
                document = document,
                editorCoroutineScope = editorCoroutineScope,
            )

        val mainEditor = textEditorProvider.createFileEditor(
            project = project,
            file = scratchFile.file,
            document = readAction { FileDocumentManager.getInstance().getDocument(scratchFile.file) },
            editorCoroutineScope = editorCoroutineScope,
        )
        val editorFactory = serviceAsync<EditorFactory>()
        return withContext(Dispatchers.EDT) {
            val viewer = editorFactory.createViewer(editorFactory.createDocument(""), scratchFile.project, EditorKind.PREVIEW)
            Disposer.register(mainEditor) { editorFactory.releaseEditor(viewer) }
            val previewEditor = textEditorProvider.getTextEditor(viewer)
            ArendScratchFileEditorWithPreview(scratchFile, mainEditor, previewEditor)
        }
    }

    override fun createEditor(project: Project, file: VirtualFile): FileEditor {
        val scratchFile = createScratchFile(project, file) ?: return TextEditorProvider.getInstance().createEditor(project, file)
        return ArendScratchFileEditorWithPreview.createArendScratchFileEditor(scratchFile)
    }

    override fun getPolicy(): FileEditorPolicy = FileEditorPolicy.HIDE_DEFAULT_EDITOR
}

class ArendScratchFileEditorWithPreview internal constructor(
    val scratchFile: ScratchFile,
    sourceTextEditor: TextEditor,
    previewTextEditor: TextEditor
) : TextEditorWithPreview(sourceTextEditor, previewTextEditor), TextEditor, ScratchEditorLinesTranslator {

    private val sourceEditor = sourceTextEditor.editor as EditorEx
    private val _previewEditor = previewTextEditor.editor as EditorEx
    private val previewOutputManager: PreviewOutputBlocksManager = PreviewOutputBlocksManager(_previewEditor)

    private val toolWindowHandler: ScratchOutputHandler = requestToolWindowHandler()
    private val inlayScratchOutputHandler = InlayScratchOutputHandler(sourceTextEditor, toolWindowHandler)
    private val previewEditorScratchOutputHandler = PreviewEditorScratchOutputHandler(
        previewOutputManager,
        toolWindowHandler,
        previewTextEditor as Disposable
    )
    private val commonPreviewOutputHandler = ArendLayoutDependantOutputHandler(
        noPreviewOutputHandler = inlayScratchOutputHandler,
        previewOutputHandler = previewEditorScratchOutputHandler,
        layoutProvider = { getLayout()!! }
    )

    init {
        sourceTextEditor.parentScratchEditorWithPreview = this
        previewTextEditor.parentScratchEditorWithPreview = this

        scratchFile.compilingScratchExecutor?.addOutputHandler(commonPreviewOutputHandler)
        scratchFile.replScratchExecutor?.addOutputHandler(commonPreviewOutputHandler)

        configureSyncScrollForSourceAndPreview()
        configureSyncHighlighting(sourceEditor, _previewEditor, translator = this)

        ScratchFileAutoRunner.addListener(scratchFile.project, sourceTextEditor)
    }

    override fun getFile(): VirtualFile = scratchFile.file

    override fun previewLineToSourceLines(previewLine: Int): Pair<Int, Int>? {
        val expressionUnderCaret = scratchFile.getExpressionAtLine(previewLine) ?: return null
        val outputBlock = previewOutputManager.getBlock(expressionUnderCaret) ?: return null

        return outputBlock.lineStart to outputBlock.lineEnd
    }

    override fun sourceLineToPreviewLines(sourceLine: Int): Pair<Int, Int>? {
        val block = previewOutputManager.getBlockAtLine(sourceLine) ?: return null
        if (!block.sourceExpression.linesInformationIsCorrect()) return null

        return block.sourceExpression.lineStart to block.sourceExpression.lineEnd
    }

    private fun configureSyncScrollForSourceAndPreview() {
        val scrollable = object : BaseSyncScrollable() {
            override fun processHelper(helper: ScrollHelper) {
                if (!helper.process(0, 0)) return

                val alignments = previewOutputManager.computeSourceToPreviewAlignments()

                for ((fromSource, fromPreview) in alignments) {
                    if (!helper.process(fromSource, fromPreview)) return
                    if (!helper.process(fromSource, fromPreview)) return
                }

                helper.process(sourceEditor.document.lineCount, _previewEditor.document.lineCount)
            }

            override fun isSyncScrollEnabled(): Boolean = true
        }

        val scrollSupport = TwosideSyncScrollSupport(listOf(sourceEditor, _previewEditor), scrollable)
        val listener = VisibleAreaListener { e -> scrollSupport.visibleAreaChanged(e) }

        sourceEditor.scrollingModel.addVisibleAreaListener(listener)
        _previewEditor.scrollingModel.addVisibleAreaListener(listener)
    }

    override fun dispose() {
        scratchFile.replScratchExecutor?.stop()
        scratchFile.compilingScratchExecutor?.stop()
        releaseToolWindowHandler(toolWindowHandler)
        super.dispose()
    }

    override fun navigateTo(navigatable: Navigatable) {
        myEditor.navigateTo(navigatable)
    }

    override fun canNavigateTo(navigatable: Navigatable): Boolean {
        return myEditor.canNavigateTo(navigatable)
    }

    override fun getEditor(): Editor {
        return myEditor.editor
    }

    override fun createToolbar(): ActionToolbar {
        return ArendScratchTopPanel(scratchFile).actionsToolbar
    }

    fun clearOutputHandlers() {
        commonPreviewOutputHandler.clear(scratchFile)
    }

    override val isShowActionsInTabs: Boolean
        get() = false

    override fun createViewActionGroup(): ActionGroup {
        return DefaultActionGroup(showEditorAction, showEditorAndPreviewAction)
    }

    override val showEditorAction: ToggleAction
        get() {
            return super.showEditorAction.apply {
                templatePresentation.text = KotlinJvmBundle.message("scratch.inlay.output.mode.title")
                templatePresentation.description = KotlinJvmBundle.message("scratch.inlay.output.mode.description")
            }
        }

    override val showEditorAndPreviewAction: ToggleAction
        get() {
            return super.showEditorAndPreviewAction.apply {
                templatePresentation.text = KotlinJvmBundle.message("scratch.side.panel.output.mode.title")
                templatePresentation.description = KotlinJvmBundle.message("scratch.side.panel.output.mode.description")
            }
        }

    override fun onLayoutChange(oldValue: Layout?, newValue: Layout?) {
        when {
            oldValue != newValue -> clearOutputHandlers()
        }
    }

    @TestOnly
    fun setPreviewEnabled(isPreviewEnabled: Boolean) {
        setLayout(if (isPreviewEnabled) Layout.SHOW_EDITOR_AND_PREVIEW else Layout.SHOW_EDITOR)
    }

    companion object {
        internal fun createArendScratchFileEditor(scratchFile: ScratchFile): ArendScratchFileEditorWithPreview {
            val textEditorProvider = TextEditorProvider.getInstance()

            val mainEditor = textEditorProvider.createEditor(scratchFile.project, scratchFile.file) as TextEditor
            val editorFactory = EditorFactory.getInstance()

            val viewer = editorFactory.createViewer(editorFactory.createDocument(""), scratchFile.project, EditorKind.PREVIEW)
            Disposer.register(mainEditor) { editorFactory.releaseEditor(viewer) }

            val previewEditor = textEditorProvider.getTextEditor(viewer)

            return ArendScratchFileEditorWithPreview(scratchFile, mainEditor, previewEditor)
        }
    }
}

fun TextEditor.findArendScratchFileEditorWithPreview(): ArendScratchFileEditorWithPreview? =
    this as? ArendScratchFileEditorWithPreview ?: parentScratchEditorWithPreview

private var TextEditor.parentScratchEditorWithPreview: ArendScratchFileEditorWithPreview?
        by UserDataProperty(Key.create("parent.preview.editor"))

private class ArendLayoutDependantOutputHandler(
    private val noPreviewOutputHandler: ScratchOutputHandler,
    private val previewOutputHandler: ScratchOutputHandler,
    private val layoutProvider: () -> TextEditorWithPreview.Layout
) : ScratchOutputHandler {

    override fun onStart(file: ScratchFile) {
        targetHandler.onStart(file)
    }

    override fun handle(file: ScratchFile, expression: ScratchExpression, output: ScratchOutput) {
        targetHandler.handle(file, expression, output)
    }

    override fun error(file: ScratchFile, message: String) {
        targetHandler.error(file, message)
    }

    override fun onFinish(file: ScratchFile) {
        targetHandler.onFinish(file)
    }

    override fun clear(file: ScratchFile) {
        noPreviewOutputHandler.clear(file)
        previewOutputHandler.clear(file)
    }

    private val targetHandler
        get() = when (layoutProvider()) {
            TextEditorWithPreview.Layout.SHOW_EDITOR -> noPreviewOutputHandler
            else -> previewOutputHandler
        }
}

private fun ScratchExpression.linesInformationIsCorrect(): Boolean {
    if (!element.isValid) return false
    return element.getLineNumber(start = true) == lineStart && element.getLineNumber(start = false) == lineEnd
}
