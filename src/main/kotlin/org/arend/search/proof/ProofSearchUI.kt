package org.arend.search.proof

import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.PrefixMatcher
import com.intellij.codeInsight.completion.impl.CamelHumpMatcher
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.codeInsight.daemon.impl.UpdateHighlightersUtil
import com.intellij.codeInsight.hint.HintManager
import com.intellij.codeInsight.hint.actions.QuickPreviewAction
import com.intellij.codeInsight.lookup.LookupManager
import com.intellij.codeInsight.lookup.impl.actions.ChooseItemAction
import com.intellij.icons.AllIcons
import com.intellij.ide.actions.BigPopupUI
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.actionSystem.toolbarLayout.ToolbarLayoutStrategy
import com.intellij.openapi.application.invokeAndWaitIfNeeded
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.application.runInEdt
import com.intellij.openapi.application.runReadAction
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.components.service
import com.intellij.openapi.editor.Caret
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.actions.IncrementalFindAction
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.impl.DocumentMarkupModel
import com.intellij.openapi.editor.markup.HighlighterLayer
import com.intellij.openapi.editor.markup.HighlighterTargetArea
import com.intellij.openapi.editor.markup.MarkupModel
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.keymap.KeymapUtil
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.progress.runBackgroundableTask
import com.intellij.openapi.project.DumbAwareAction
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.SystemInfo
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.stubs.StubIndex
import com.intellij.psi.util.parentOfType
import com.intellij.psi.util.parentsOfType
import com.intellij.ui.*
import com.intellij.ui.AnimatedIcon
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.fields.ExtendableTextField
import com.intellij.ui.scale.JBUIScale
import com.intellij.util.Alarm
import com.intellij.util.concurrency.annotations.RequiresEdt
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.util.ui.*
import net.miginfocom.swing.MigLayout
import org.arend.ArendIcons
import org.arend.psi.ext.ArendDefinition
import org.arend.psi.ArendFile
import org.arend.psi.ArendPsiFactory
import org.arend.psi.ext.ArendCompositeElement
import org.arend.psi.ext.PsiReferable
import org.arend.psi.ext.ArendGroup
import org.arend.psi.ext.ReferableBase
import org.arend.psi.listener.ArendPsiChangeService
import org.arend.psi.navigate
import org.arend.psi.stubs.index.ArendDefinitionIndex
import org.arend.quickfix.referenceResolve.ResolveReferenceAction
import org.arend.refactoring.LocationData
import org.arend.refactoring.calculateReferenceName
import org.arend.term.abs.Abstract
import org.arend.util.ArendBundle
import org.arend.util.isDetailedViewEditor
import java.awt.BorderLayout
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.Font
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.*
import javax.swing.border.Border

class ProofSearchUI(private val project: Project, private val caret: Caret?) : BigPopupUI(project) {

    private val searchAlarm = Alarm(Alarm.ThreadToUse.SWING_THREAD, this)

    private val model: CollectionListModel<ProofSearchUIEntry> = CollectionListModel()

    private val loadingIcon: JBLabel = JBLabel(EmptyIcon.ICON_16)

    private val previewAction: QuickPreviewAction = QuickPreviewAction()

    private val myEditorTextField: MyEditorTextField = MyEditorTextField().apply {
        setFontInheritedFromLAF(false)
        setPlaceholder(ArendBundle.getMessage("arend.proof.search.placeholder"))
        setShowPlaceholderWhenFocused(true)
    }

    private val cellRenderer: ArendProofSearchRenderer = ArendProofSearchRenderer(project)

    @Volatile
    private var hasErrors: Boolean = false

    @Volatile
    private var progressIndicator: ProgressIndicator? = null
        get() = synchronized(this) {
            field
        }
        set(value) = synchronized(this) {
            field = value
        }

    init {
        init()
        initActions()
    }

    override fun dispose() {
        close()
        cellRenderer.dispose()
        model.removeAll()
    }

    override fun createList(): JBList<Any> {
        @Suppress("UNCHECKED_CAST")
        addListDataListener(model as AbstractListModel<Any>)

        return JBList(model)
    }

    override fun createCellRenderer(): ListCellRenderer<Any> {
        @Suppress("UNCHECKED_CAST")
        return cellRenderer as ListCellRenderer<Any>
    }

    private fun doCreateTopLeftPanel(): JPanel {
        val title = JBLabel(ArendBundle.message("arend.proof.search.title"))
        val topPanel = JPanel(MigLayout("flowx, ins 0, gap 0, fillx, filly"))
        val foregroundColor = when {
            JBColor.isBright() -> UIUtil.getLabelForeground()
            else -> JBColor(Gray._240, Gray._200)
        }
        title.foreground = foregroundColor
        title.border = BorderFactory.createEmptyBorder(3, 5, 5, 0)
        if (SystemInfo.isMac) {
            title.font = loadingIcon.font.deriveFont(Font.BOLD, title.font.size - 1f)
        } else {
            title.font = loadingIcon.font.deriveFont(Font.BOLD)
        }
        loadingIcon.font = loadingIcon.font.deriveFont(loadingIcon.font.size - 1f)
        topPanel.add(title, "gapright 4")
        topPanel.add(loadingIcon)
        topPanel.background = JBUI.CurrentTheme.BigPopup.headerBackground()
        title.background = JBUI.CurrentTheme.BigPopup.headerBackground()
        return topPanel
    }

    override fun createHeader(): JComponent {
        val header = JPanel(BorderLayout())
        header.add(doCreateTopLeftPanel(), BorderLayout.WEST)
        header.add(doCreateSettingsPanel(), BorderLayout.EAST)
        header.background = JBUI.CurrentTheme.BigPopup.headerBackground()
        return header
    }

    private fun doCreateSettingsPanel(): JPanel {
        val res = JPanel(FlowLayout(FlowLayout.RIGHT, 0, 0))
        res.isOpaque = false

        val actionGroup = DefaultActionGroup()
        actionGroup.addAction(GearActionGroup(this, project))
        actionGroup.addAction(ShowInFindWindowAction(this, project))
        actionGroup.addAction(ShowHelpAction(this))
        val toolbar = ActionManager.getInstance().createActionToolbar("proof.search.top.toolbar", actionGroup, true)
        toolbar.layoutStrategy = ToolbarLayoutStrategy.NOWRAP_STRATEGY
        toolbar.targetComponent = this
        val toolbarComponent = toolbar.component
        toolbarComponent.isOpaque = false
        res.add(toolbarComponent)
        return res
    }

    override fun getAccessibleName(): String = ArendBundle.message("arend.proof.search.title")

    // a hack, sorry IJ platform
    override fun init() {
        super.init()
        synchronized(this.treeLock) {
            for (it in components) {
                val field = (it as? JPanel)?.components?.find { it is ExtendableTextField }
                if (field != null) {
                    (it as? JPanel)?.remove(field)
                    it.add(myEditorTextField, BorderLayout.SOUTH)
                }
            }
        }
    }

    override fun installScrollingActions() {
        // handled in service
    }

    val editorSearchField: EditorTextField
        get() = myEditorTextField

    private inner class MyEditorTextField :
        TextFieldWithAutoCompletion<ReferableBase<*>>(project, ProofSearchTextCompletionProvider(project), false, "") {

        init {
            val empty: Border = JBUI.Borders.empty(-1)
            val topLine = JBUI.Borders.customLine(JBUI.CurrentTheme.BigPopup.searchFieldBorderColor(), 0, 0, 0, 0)
            border = JBUI.Borders.merge(empty, topLine, true)
            background = JBUI.CurrentTheme.BigPopup.searchFieldBackground()
            focusTraversalKeysEnabled = false
        }

        override fun createEditor(): EditorEx {
            val editor = super.createEditor()
            editor.putUserData(PROOF_SEARCH_EDITOR, Unit)
            editor.putUserData(IncrementalFindAction.SEARCH_DISABLED, true)
            return editor
        }

        override fun getPreferredSize(): Dimension? {
            val size = super.getPreferredSize()
            size.height = Integer.max(JBUIScale.scale(29), size.height)
            return size
        }

        override fun shouldHaveBorder(): Boolean {
            return false
        }
    }

    private class ProofSearchTextCompletionProvider(private val project: Project) :
        TextFieldWithAutoCompletionListProvider<ReferableBase<*>>(listOf()) {

        override fun createPrefixMatcher(prefix: String): PrefixMatcher {
            val qualifiers = prefix.split('.')
            return CamelHumpMatcher(qualifiers.lastOrNull() ?: "", true, true)
        }

        override fun getIcon(item: ReferableBase<*>): Icon? {
            return item.getIcon(0)
        }

        override fun getTailText(item: ReferableBase<*>): String {
            return getCompleteModuleLocation(item)?.let {" of $it" } ?: ""
        }

        override fun getItems(
            prefix: String?,
            cached: Boolean,
            parameters: CompletionParameters?
        ): Collection<ReferableBase<*>> {
            if (prefix == null || prefix.isEmpty() || prefix == "_" || prefix == "->") {
                return emptyList()
            }
            val qualifiers = prefix.split('.')
            val modules = if (qualifiers.size > 1) qualifiers.subList(0, qualifiers.size - 1) else emptyList()
            val matcher = CamelHumpMatcher(qualifiers.last(), true, true)

            return runReadAction {
                val container = ArrayList<ReferableBase<*>>()
                if (modules.isNotEmpty()) {
                    val groups = StubIndex.getElements(ArendDefinitionIndex.KEY, modules.last(), project, GlobalSearchScope.allScope(project), PsiReferable::class.java).filterIsInstance<ArendGroup>()
                        .filter { it.hasSuffixGroupStructure(modules.subList(0, modules.size - 1)) }
                    container.addAll(groups.flatMap { group -> group.statements.mapNotNull { (it.group as? ReferableBase<*>)?.takeIf { ref -> ref is ArendDefinition && matcher.prefixMatches(ref.refName) } } })
                } else {
                    StubIndex.getInstance().processAllKeys(ArendDefinitionIndex.KEY, project) { name ->
                        StubIndex.getInstance().processElements(ArendDefinitionIndex.KEY, name, project, null, PsiReferable::class.java) {
                            if (it is ReferableBase<*> && matcher.prefixMatches(name)) {
                                container.add(it)
                            }
                            true
                        }
                        true
                    }
                }
                container
            }
        }

        override fun getLookupString(item: ReferableBase<*>): String = item.refName

    }

    private fun initActions() {
        myResultsList.expandableItemsHandler.isEnabled = false

        registerSearchAction()
        registerEscapeAction()
        registerEnterAction()
        registerGoToDefinitionAction()
        registerInsertAction()
        registerMouseActions()
    }

    private val keywordAttributes = TextAttributes().apply {
        copyFrom(DefaultLanguageHighlighterColors.KEYWORD.defaultAttributes)
        fontType = Font.BOLD
    }

    private fun registerSearchAction() {
        myEditorTextField.document.addDocumentListener(object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                registerSearchAttempt()
            }
        })
    }


    fun registerSearchAttempt() {
        if (myEditorTextField.text.isEmpty()) {
            return
        }
        val shouldSearch = refreshHighlighting()
        if (shouldSearch) {
            scheduleSearch()
        }
    }

    /**
     * @return if search query is OK
     */
    private fun refreshHighlighting(): Boolean {
        val editor = myEditorTextField.editor ?: return false
        val text = myEditorTextField.text
        val markupModel = editor.markupModel
        DocumentMarkupModel.forDocument(editor.document, project, false)?.removeAllHighlighters()

        for (i in text.indices) {
            tryHighlightKeyword(i, text, markupModel, "\\and")
            tryHighlightKeyword(i, text, markupModel, "->")
        }
        val parsingResult = ProofSearchQuery.fromString(text) as? ParsingResult.Error<ProofSearchQuery>
        hasErrors = parsingResult != null
        if (parsingResult == null) return true
        val (msg, range) = parsingResult
        this.progressIndicator?.cancel()
        val info = HighlightInfo.newHighlightInfo(HighlightInfoType.ERROR)
            .descriptionAndTooltip(msg)
            .textAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)
            .range(range.first, range.last)
            .create()

        UpdateHighlightersUtil.setHighlightersToEditor(project, editor.document, range.first, range.last, listOf(info), null, -1)
        if (text.isNotEmpty()) {
            adjustLoadingIcon(LoadingIconState.SYNTAX_ERROR, msg)
        } else {
            adjustLoadingIcon(LoadingIconState.CLEAR, null)
        }
        return false
    }

    private fun tryHighlightKeyword(
        index: Int,
        text: String,
        markupModel: MarkupModel,
        keyword: String,
    ) {
        val size = keyword.length
        if (index + size <= text.length && text.subSequence(index, index + size) == keyword &&
            (index + size == text.length || text[index + size].isWhitespace()) &&
            (index == 0 || text[index - 1].isWhitespace())
        ) {
            markupModel.addRangeHighlighter(
                index, index + size, HighlighterLayer.SYNTAX,
                keywordAttributes, HighlighterTargetArea.EXACT_RANGE
            )
        }
    }

    override fun getSearchPattern(): String {
        return myEditorTextField.text
    }

    private fun registerMouseActions() {
        val mouseListener = object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                onMouseClicked(e)
            }
        }
        myResultsList.addMouseListener(mouseListener)
        myResultsList.addMouseMotionListener(mouseListener)
    }

    private fun registerEnterAction() {
        DumbAwareAction.create {
            val lookup = LookupManager.getActiveLookup(editorSearchField.editor)
            if (lookup?.component?.isVisible == true) {
                ChooseItemAction.FocusedOnly().actionPerformed(it)
            } else {
                val indices = myResultsList.selectedIndices
                if (indices.isNotEmpty()) {
                    onEntrySelected(model.getElementAt(indices[0]))
                }
            }
        }.registerCustomShortcutSet(CommonShortcuts.ENTER, this, this)
    }


    private fun registerGoToDefinitionAction() {
        DumbAwareAction.create {
            val indices = myResultsList.selectedIndices
            if (indices.isNotEmpty()) {
                goToDeclaration(model.getElementAt(indices[0]))
            }
        }.registerCustomShortcutSet(CommonShortcuts.getEditSource(), this, this)
    }


    private fun registerInsertAction() {
        DumbAwareAction.create {
            val indices = myResultsList.selectedIndices
            if (indices.isNotEmpty() && caret != null) {
                val element = model.getElementAt(indices[0])
                if (element is DefElement) {
                    close()
                    insertDefinition(project, element.entry.def, caret)
                }
            }
        }.registerCustomShortcutSet(CommonShortcuts.getCtrlEnter(), this, this)
    }

    private fun registerEscapeAction() {
        val escapeAction = ActionManager.getInstance().getAction("EditorEscape")
        DumbAwareAction
            .create {
                val lookup = LookupManager.getActiveLookup(editorSearchField.editor)
                if (lookup?.component?.isVisible == true) {
                    lookup.hideLookup(true)
                } else {
                    close()
                }
            }
            .registerCustomShortcutSet(escapeAction?.shortcutSet ?: CommonShortcuts.ESCAPE, this)
    }

    private fun scheduleSearch() {
        if (!searchAlarm.isDisposed && searchAlarm.activeRequestCount == 0) {
            searchAlarm.addRequest({ runProofSearch(0, null) }, 100)
        }
    }

    private fun onMouseClicked(e: MouseEvent) {
        if (e.button == MouseEvent.BUTTON1) {
            e.consume()
            val i = myResultsList.locationToIndex(e.point)
            if (i > -1) {
                myResultsList.selectedIndex = i
                onEntrySelected(model.getElementAt(i))
            }
        }
    }

    private fun onEntrySelected(element: ProofSearchUIEntry) = when (element) {
        is DefElement -> {
            previewAction.performForContext({
                when (it) {
                    CommonDataKeys.PROJECT.name -> project
                    CommonDataKeys.PSI_ELEMENT.name -> element.entry.def
                    else -> null
                }
            }, false)
        }
        is MoreElement -> {
            model.remove(element)
            runProofSearch(element.alreadyProcessed, element.sequence)
        }
    }

    private fun goToDeclaration(element: ProofSearchUIEntry) = when (element) {
        is DefElement -> {
            close()
            element.entry.def.navigationElement.navigate()
        }
        else -> Unit
    }

    @RequiresEdt
    fun runProofSearch(alreadyProcessed: Int, results: Sequence<ProofSearchEntry?>?) {
        EDT.assertIsEdt()
        cleanupCurrentResults(results == null)
        val settings = ProofSearchUISettings(project)

        runBackgroundableTask(ArendBundle.message("arend.proof.search.title"), myProject) { progressIndicator ->
            runWithLoadingIcon {
                this.progressIndicator = progressIndicator
                val elements = results ?: generateProofSearchResults(project, searchPattern)
                var counter = PROOF_SEARCH_RESULT_LIMIT
                for (element in elements) {
                    if (progressIndicator.isCanceled) {
                        return@runWithLoadingIcon "Search cancelled"
                    }
                    if (element == null) {
                        continue
                    }
                    invokeLater {
                        if (progressIndicator.isCanceled) {
                            return@invokeLater
                        }
                        model.add(DefElement(element))
                        if (results != null && counter == PROOF_SEARCH_RESULT_LIMIT && myResultsList.selectedIndex == -1) {
                            myResultsList.selectedIndex = myResultsList.itemsCount - 1
                        }
                    }
                    --counter
                    if (settings.shouldLimitSearch() && counter == 0) {
                        invokeLater {
                            if (progressIndicator.isCanceled) {
                                return@invokeLater
                            }
                            model.add(
                                MoreElement(
                                    alreadyProcessed + PROOF_SEARCH_RESULT_LIMIT,
                                    elements.drop(PROOF_SEARCH_RESULT_LIMIT)
                                )
                            )
                        }
                        return@runWithLoadingIcon "Showing first ${alreadyProcessed + PROOF_SEARCH_RESULT_LIMIT} results"
                    }
                }
                "Search completed with ${model.items.size} results"
            }
        }
    }

    private inline fun runWithLoadingIcon(action: () -> String) {
        var resultText: String? = null
        adjustLoadingIcon(LoadingIconState.LOADING, null)
        try {
            resultText = action()
        } finally {
            if (!hasErrors) {
                adjustLoadingIcon(LoadingIconState.DONE, resultText)
            }
        }
    }

    private enum class LoadingIconState {
        DONE, SYNTAX_ERROR, LOADING, CLEAR
    }

    private fun adjustLoadingIcon(state: LoadingIconState, message: String?) = runInEdt {
        if (hasErrors && state != LoadingIconState.SYNTAX_ERROR && state != LoadingIconState.CLEAR) {
            return@runInEdt
        }
        when (state) {
            LoadingIconState.DONE -> {
                loadingIcon.setIconWithAlignment(ArendIcons.CHECKMARK, SwingConstants.LEFT, SwingConstants.TOP)
                loadingIcon.toolTipText = ArendBundle.message("arend.proof.search.completed.tooltip")
                loadingIcon.text = message
            }
            LoadingIconState.SYNTAX_ERROR -> {
                loadingIcon.setIconWithAlignment(AllIcons.General.Error, SwingConstants.LEFT, SwingConstants.TOP)
                loadingIcon.toolTipText = ArendBundle.message("arend.proof.search.syntax.error.in.query")
                loadingIcon.text = message
            }
            LoadingIconState.LOADING -> {
                loadingIcon.icon = AnimatedIcon.Default.INSTANCE
                loadingIcon.toolTipText = ArendBundle.message("arend.proof.search.loading.tooltip")
                loadingIcon.text = null
            }
            LoadingIconState.CLEAR -> {
                loadingIcon.icon = null
                loadingIcon.toolTipText = ""
                loadingIcon.text = null
            }
        }
    }


    private fun cleanupCurrentResults(cleanModel: Boolean) {
        progressIndicator?.cancel()
        if (cleanModel) {
            invokeAndWaitIfNeeded {
                model.removeAll()
            }
        }
    }

    override fun getInitialHints(): Array<String> = arrayOf(
        ArendBundle.message("arend.proof.search.quick.preview.tip"),
        ArendBundle.message("arend.proof.search.go.to.definition.tip", KeymapUtil.getFirstKeyboardShortcutText(IdeActions.ACTION_EDIT_SOURCE)),
        ArendBundle.message("arend.proof.search.insert.definition.tip", KeymapUtil.getFirstKeyboardShortcutText(CommonShortcuts.getCtrlEnter())),
        ArendBundle.message("arend.proof.search.use.and.tip"),
        ArendBundle.message("arend.proof.search.use.arrow.tip"),
    )

    fun close() {
        stopSearch()
        searchFinishedHandler.run()
    }

    private fun stopSearch() {
        progressIndicator?.cancel()
    }

    fun moveListDown() {
        ScrollingUtil.moveDown(myResultsList, 0)
    }

    fun moveListUp() {
        ScrollingUtil.moveUp(myResultsList, 0)
    }

    companion object {
        fun insertDefinition(project: Project, definition: ReferableBase<*>, caret: Caret) {
            val mainEditor = caret.editor
            val mainDocument = caret.editor.document
            if (!mainDocument.isWritable || mainEditor.isDetailedViewEditor()) {
                HintManager.getInstance().showErrorHint(mainEditor, "File is read-only")
                return
            }
            val file = PsiDocumentManager.getInstance(definition.project).getPsiFile(mainDocument) as? ArendFile ?: return
            val offset = caret.offset
            val elementUnderCaret = file.findElementAt(offset)?.parentOfType<ArendCompositeElement>()
                ?: file.findElementAt(offset - 1)?.parentOfType<ArendCompositeElement>()
                ?: return
            val (action, representation) = runReadAction {
                val locationData = LocationData.createLocationData(definition)
                val (importAction, resultName) = locationData?.let{ calculateReferenceName(it, file, elementUnderCaret) } ?: return@runReadAction null
                val representation = getInsertableRepresentation(definition, resultName)
                val resolveReferenceAction = ResolveReferenceAction(definition, locationData.getLongName(), importAction, null)
                representation?.run(resolveReferenceAction::to)
            } ?: return
            WriteCommandAction.runWriteCommandAction(project, "Inserting Selected Definition...", "__Arend__Proof_search_insert_selected_definition", {
                mainDocument.insertString(offset, representation.text)
                PsiDocumentManager.getInstance(definition.project).commitDocument(mainDocument)
                action.execute(mainEditor)
                service<ArendPsiChangeService>().incModificationCount()
            })
        }

        @RequiresReadLock
        private fun getInsertableRepresentation(definition: ReferableBase<*>, resultName: List<String>) : PsiElement? {
            val factory = ArendPsiFactory(definition.project)
            val explicitArguments = when(definition) {
                is Abstract.ParametersHolder -> definition.parameters.count { it.isExplicit }
                else -> return null
            }
            return factory.createExpression("(${resultName.joinToString(".")}${" {?}".repeat(explicitArguments)})")
        }
    }
}

private fun ArendGroup.hasSuffixGroupStructure(subList: List<String>): Boolean {
    val parents = parentsOfType<ArendGroup>(false).take(subList.size).toList()
    val reversedSublist = subList.reversed()
    for (index in parents.indices) {
        val currentGroup = parents[index]
        if (subList.size >= index) {
            return false
        }
        if (currentGroup !is ArendFile) {
            if (parents[index].name != reversedSublist[index]) {
                return false
            }
        } else {
            val location = currentGroup.moduleLocation?.modulePath?.toList()?.toList()?.reversed() ?: return false
            for (endIndex in index until subList.size) {
                val locationIndex = endIndex - index
                if (locationIndex >= location.size || location[locationIndex] != reversedSublist[endIndex]) {
                    return false
                }
            }
        }
    }
    return true
}

const val PROOF_SEARCH_RESULT_LIMIT = 20