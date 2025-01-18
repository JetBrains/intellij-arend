package org.arend.documentation

import com.intellij.codeInsight.documentation.DocumentationManagerProtocol.PSI_ELEMENT_PROTOCOL
import com.intellij.ide.IdeEventQueue
import com.intellij.lang.documentation.AbstractDocumentationProvider
import com.intellij.lang.documentation.DocumentationMarkup.*
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.ui.popup.JBPopupFactory
import com.intellij.openapi.util.Pair
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.ui.awt.RelativePoint
import com.intellij.ui.jcef.JBCefBrowser
import com.intellij.ui.jcef.JBCefBrowserBase
import com.intellij.ui.jcef.JBCefJSQuery
import org.arend.documentation.ArendKeyword.Companion.isArendKeyword
import org.arend.naming.reference.FieldReferable
import org.arend.psi.ArendFile
import org.arend.psi.doc.ArendDocComment
import org.arend.psi.ext.*
import org.arend.term.abs.Abstract
import org.cef.browser.CefBrowser
import org.cef.browser.CefFrame
import org.cef.handler.CefLoadHandlerAdapter
import java.awt.Dimension
import java.awt.MouseInfo
import java.awt.Toolkit
import java.awt.event.ActionListener
import java.awt.event.KeyEvent
import java.io.File
import javax.swing.KeyStroke
import javax.swing.UIManager
import kotlin.math.roundToInt


class ArendDocumentationProvider : AbstractDocumentationProvider() {

    private var popupCefBrowserHtml = ""
    private var lastElement: PsiElement? = null
    private var lastOriginalElement: PsiElement? = null

    override fun getQuickNavigateInfo(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, false)

    override fun generateDoc(element: PsiElement, originalElement: PsiElement?) = generateDoc(element, originalElement, true)

    override fun getDocumentationElementForLink(psiManager: PsiManager?, link: String, context: PsiElement?): PsiElement? {
        if (link.startsWith(ACTION_PREFIX)) {
            val elementText = link.removePrefix(ACTION_PREFIX)
            invokeLater {
                val color = getHtmlRgbFormat(UIManager.getColor("PopupMenu.foreground").rgb)
                showInCefBrowser(elementText, color)
            }
            return null
        }
        /* TODO[server2]
        val longName = link.removePrefix(FULL_PREFIX)
        val scope = ArendDocComment.getScope((context as? ArendDocComment)?.owner ?: context) ?: return null
        val ref = RedirectingReferable.getOriginalReferable(Scope.resolveName(scope, LongName.fromString(longName).toList()))
        return if (ref is PsiReferable && longName.length != link.length) ref.documentation else ref as? PsiElement
        */
        return null
    }

    override fun getCustomDocumentationElement(editor: Editor, file: PsiFile, contextElement: PsiElement?, targetOffset: Int): PsiElement? {
        if (contextElement?.isArendKeyword() == true) {
            return contextElement
        }
        return null
    }

    private fun generateDocForKeywords(element: PsiElement): String {
        return buildString { wrapTag("html") {
            wrapTag("head") {
                wrapTag("style") {
                    append(".normal_text { white_space: nowrap; }.code { white_space: pre; }")
                }
            }
            wrapTag("body") {
                wrap(DEFINITION_START, DEFINITION_END) {
                    wrapTag("b") {
                        html(element.text)
                    }
                }

                wrap(CONTENT_START, CONTENT_END) {
                    getDescriptionForKeyword(element)
                }
            }
        } }
    }

    private fun generateDoc(element: PsiElement, originalElement: PsiElement?, withDocComments: Boolean, suggestedFont: Int? = null): String? {
        val ref = element as? PsiReferable ?: (element as? ArendDocComment)?.owner
        ?: return if (element.isArendKeyword()) generateDocForKeywords(element) else null

        val font = suggestedFont ?:
            (UIManager.getDefaults().getFont("Label.font")
                ?.size?.times(COEFFICIENT_HTML_FONT))?.roundToInt()
        val docCommentInfo = ArendDocCommentInfo(hasLatexCode = false, wasPrevRow = false,
            suggestedFont = font?.times(COEFFICIENT_LATEX_FONT)?.toFloat() ?: DEFAULT_FONT)

        var offsetStartText = -1
        val htmlBuilder = StringBuilder().apply { wrapTag("html") {
            if (withDocComments) {
                wrapTag("head") {
                    wrapTag("style") {
                        append(".normal_text { white_space: nowrap; }.code { white_space: pre; }")
                        if (font != null) {
                            append("$ROW_FONT_HTML$font$END_FONT_HTML")
                            append("$DEFINITION_FONT_HTML$font$END_FONT_HTML")
                            append("$CONTENT_FONT_HTML$font$END_FONT_HTML")
                        }
                    }
                }
            }

            append("<body ")
            if (withDocComments) {
                val scheme = EditorColorsManager.getInstance().globalScheme
                append("style=\"color:${getHtmlRgbFormat(UIManager.getColor("PopupMenu.foreground").rgb)};" +
                        "background-color:${getHtmlRgbFormat(scheme.getColor(EditorColors.DOCUMENTATION_COLOR)?.rgb ?: 0)};\">")
            }
            offsetStartText = this.length
            wrap(DEFINITION_START, DEFINITION_END) {
                generateDefinition(ref)
            }

            wrap(CONTENT_START, CONTENT_END) {
                generateContent(ref, originalElement)
            }

            if (withDocComments) {
                val doc = element as? ArendDocComment ?: ref.documentation
                if (doc != null) {
                    File(LATEX_IMAGES_DIR).deleteRecursively()
                    docCommentInfo.hasLatexCode = hasLatexCode(doc)

                    wrap(CONTENT_START, CONTENT_END) {
                        generateDocComments(ref, doc, element is ArendDocComment, docCommentInfo)
                    }
                }
            }
            append("</body>")
        } }
        if (docCommentInfo.hasLatexCode) {
            val elementText = ref.textRepresentation()
            popupCefBrowserHtml = htmlBuilder.toString()
            lastElement = element
            lastOriginalElement = originalElement
            htmlBuilder.insert(offsetStartText, "<a href=\"${PSI_ELEMENT_PROTOCOL}${ACTION_PREFIX}$elementText\">Open in another browser</a>")
        }
        return htmlBuilder.toString()
    }

    private fun changeCefBrowserSize(browser: JBCefBrowser, shift: Double) {
        val startOffset = popupCefBrowserHtml.indexOf(ROW_FONT_HTML) + ROW_FONT_HTML.length
        val endOffset = popupCefBrowserHtml.indexOf(END_FONT_HTML, startOffset)
        val font = (popupCefBrowserHtml.substring(startOffset, endOffset).toDouble() + shift).roundToInt()
        if (font <= 0) {
            return
        }

        lastElement?.let { generateDoc(it, lastOriginalElement, true, font) }
        browser.loadHTML(popupCefBrowserHtml)
    }

    private fun showInCefBrowser(title: String, linkColor: String) {
        val browser = JBCefBrowser()
        browser.component.preferredSize = Dimension(1, 1)

        val actions = mutableListOf<Pair<ActionListener, KeyStroke>>()
        actions.add(Pair(ActionListener {
            changeCefBrowserSize(browser, 1.0)
        }, KeyStroke.getKeyStroke(KeyEvent.VK_EQUALS, KeyEvent.CTRL_DOWN_MASK)))
        actions.add(Pair(ActionListener {
            changeCefBrowserSize(browser, -1.0)
        }, KeyStroke.getKeyStroke(KeyEvent.VK_MINUS, KeyEvent.CTRL_DOWN_MASK)))

        val popup = JBPopupFactory.getInstance()
            .createComponentPopupBuilder(browser.component, browser.component)
            .setResizable(true)
            .setMovable(true)
            .setTitle(title)
            .setRequestFocus(true)
            .setCancelOnWindowDeactivation(true)
            .setKeyboardActions(actions)
            .createPopup()

        val queryWidth = JBCefJSQuery.create(browser as JBCefBrowserBase)
        val queryHeight = JBCefJSQuery.create(browser as JBCefBrowserBase)

        val cefLoadHandler = object : CefLoadHandlerAdapter() {
            override fun onLoadEnd(browser: CefBrowser?, frame: CefFrame?, httpStatusCode: Int) {
                val jsWidth = """
                    var width = document.documentElement.scrollWidth;
                    ${queryWidth.inject("width")}
                """
                val jsHeight = """
                    var height = document.documentElement.scrollHeight;
                    ${queryHeight.inject("height")}
                """
                val jsColorLink = """
                    (function() {
                        const style = document.createElement('style');
                        style.textContent = `
                            a, a:visited, a:hover, a:active {
                                color: $linkColor !important;
                            }
                        `;
                        document.head.appendChild(style);
                    })();
                """
                browser?.executeJavaScript(jsWidth, browser.url, 0)
                browser?.executeJavaScript(jsHeight, browser.url, 0)
                browser?.executeJavaScript(jsColorLink, browser.url, 0)
            }
        }

        browser.jbCefClient.addLoadHandler(cefLoadHandler, browser.cefBrowser)
        browser.loadHTML(popupCefBrowserHtml)

        val screenSize = Toolkit.getDefaultToolkit().screenSize
        val screenWidth = screenSize.getWidth().toInt()
        val screenHeight = screenSize.getHeight().toInt()

        val response = JBCefJSQuery.Response("")
        queryWidth.addHandler { result ->
            try {
                val widthResult = result.toIntOrNull() ?: return@addHandler response
                val diffWidth = screenWidth - popup.locationOnScreen.x
                popup.size = Dimension(if (widthResult > diffWidth) diffWidth else widthResult, popup.size.height)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            response
        }
        queryHeight.addHandler { result ->
            try {
                val heightResult = result.toIntOrNull() ?: return@addHandler response
                val diffHeight = screenHeight - popup.locationOnScreen.y
                popup.size = Dimension(popup.size.width, if (heightResult > diffHeight) diffHeight else heightResult)
            } catch (e: Exception) {
                e.printStackTrace()
            }
            response
        }

        invokeLater {
            IdeEventQueue.getInstance().popupManager.closeAllPopups()
            popup.show(RelativePoint(MouseInfo.getPointerInfo().location))
        }
    }

    private fun StringBuilder.generateDefinition(element: PsiReferable) {
        wrapTag("b") {
            html(element.textRepresentation())
        }
        (element as? ReferableBase<*>)?.alias?.aliasIdentifier?.id?.text?.let {
            html(" $it")
        }

        for (parameter in (element as? Abstract.ParametersHolder)?.parameters ?: emptyList()) {
            if (parameter is PsiElement) {
                html(" ${parameter.text}")
            }
        }

        element.psiElementType?.let { html(" : ${it.text}") }
    }

    private fun StringBuilder.generatePrecedence(prec: ArendPrec) {
        append(", ")
        append(prec.firstChild.text.drop(1))
        prec.number?.let { priority ->
            append(" ")
            append(priority.text)
        }
    }

    private fun StringBuilder.generateContent(element: PsiElement, originalElement: PsiElement?) {
        wrapTag("em") {
            getType(element)?.let { append(it) }
        }
        getSuperType(element)?.let { append(it) }

        (element as? ReferableBase<*>)?.prec?.let { generatePrecedence(it) }

        (element as? ReferableBase<*>)?.alias?.let { alias ->
            alias.prec?.let {
                generatePrecedence(it)
                html(" ${alias.aliasIdentifier?.id?.text ?: ""}")
            }
        }

        if (element !is PsiFile) {
            getSourceFileName(element, originalElement)?.let {
                val fileName = it.htmlEscape()
                append(", defined in ")
                wrapTag("b") {
                    append(fileName)
                }
            }
        }
    }

    private fun getType(element: PsiElement): String? = when (element) {
        is ArendDefClass -> if (element.isRecord) "record" else "class"
        is FieldReferable -> "field"
        is ArendDefInstance -> "instance"
        is ArendClassImplement -> "implementation"
        is ArendDefData -> "data"
        is ArendConstructor -> "constructor"
        is ArendDefFunction, is ArendCoClauseDef -> "function"
        is ArendDefModule -> "module"
        is ArendDefMeta -> "meta"
        is ArendLetClause -> "let"
        is ArendDefIdentifier -> if (element.parent is ArendLetClause) "let" else "variable"
        is ArendLevelIdentifier -> "level"
        is PsiFile -> "file"
        else -> null
    }

    private fun getSuperType(element: PsiElement): String? = when (element) {
        is FieldReferable ->
            (element.locatedReferableParent as? ArendDefClass)?.let {
                " of ${if (it.isRecord) "record" else "class"} <b>${it.name ?: return@let null}</b>"
            }
        is ArendConstructor -> (element.locatedReferableParent as? ArendDefData)?.name?.let { " of data <b> $it </b>" }
        else -> null
    }

    private fun getSourceFileName(element: PsiElement, originalElement: PsiElement?): String? {
        if (element is PsiLocatedReferable) {
            val file = element.containingFile.originalFile
            if (file != originalElement?.containingFile?.originalFile) {
                return (file as? ArendFile)?.fullName ?: file.name
            }
        }

        return null
    }

    companion object {
        const val COEFFICIENT_HTML_FONT = 1.2
        const val COEFFICIENT_LATEX_FONT = 1.25
        const val DEFAULT_FONT = 15.0f

        const val END_FONT_HTML = ";}"
        const val ROW_FONT_HTML = ".row { font-size: "
        const val DEFINITION_FONT_HTML = ".definition { font-size: "
        const val CONTENT_FONT_HTML = ".content { font-size: "
    }
}
