package org.arend.documentation

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.notification.NotificationAction
import com.intellij.notification.NotificationType
import com.intellij.notification.SingletonNotificationManager
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.editor.colors.EditorColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.updateSettings.impl.pluginsAdvertisement.notificationGroup
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.openapi.vfs.VfsUtilCore.visitChildrenRecursively
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.vfs.VirtualFileVisitor
import com.intellij.openapi.vfs.readText
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.elementType
import com.intellij.testFramework.LightVirtualFile
import com.intellij.util.ui.ImageUtil
import com.intellij.util.ui.JBUI
import com.intellij.xml.util.XmlStringUtil
import org.apache.commons.lang.StringEscapeUtils
import org.arend.ArendLanguage
import org.arend.highlight.ArendHighlightingColors.Companion.AREND_COLORS
import org.arend.highlight.ArendHighlightingColors.Companion.colorsByAttributesKey
import org.arend.highlight.ArendHighlightingPass
import org.arend.highlight.ArendSyntaxHighlighter
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.util.ArendBundle
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.register
import org.jsoup.nodes.Element
import org.scilab.forge.jlatexmath.ParseException
import org.scilab.forge.jlatexmath.TeXConstants
import org.scilab.forge.jlatexmath.TeXFormula
import org.scilab.forge.jlatexmath.TeXIcon
import java.awt.image.BufferedImage
import java.io.File
import javax.imageio.ImageIO
import javax.swing.JLabel
import javax.swing.UIManager
import kotlin.system.exitProcess

private val LOG: Logger = Logger.getInstance("#org.arend.documentation")

const val FULL_PREFIX = "\\full:"
const val AREND_SECTION_START = 0
const val HIGHLIGHTER_CLASS = "highlighter-rouge"
const val AREND_DOCUMENTATION_BASE_PATH = "https://arend-lang.github.io/documentation/language-reference/"
const val DEFINITION_CHAPTER = "definitions/"
const val EXPRESSION_CHAPTER = "expressions/"

const val AREND_CSS = "Arend.css"
const val AREND_JS = "highlight-hover.js"
const val AREND_DIR_HTML = "arend-html-files/"
const val AREND_BASE_FILE = "Base.ard"

const val HTML_EXTENSION = ".html"
const val HTML_DIR_EXTENSION = "HTML"

internal val REGEX_SPAN = "<span class=\"(o|k|n|g|kt|u)\">([^\"<]+)</span>".toRegex()
internal val REGEX_SPAN_HIGHLIGHT = "<span class=\"inl-highlight\">([^\"]+)</span>".toRegex()
internal val REGEX_CODE = "<code class=\"language-plaintext highlighter-rouge\">([^\"]+)</code>".toRegex()
internal val REGEX_HREF = "<a href=\"([^\"]+)\">([^\"]+)</a>".toRegex()
internal val REGEX_AREND_LIB_VERSION = "\\* \\[(.+)]".toRegex()

const val DOC_TABS_SIZE = 2

internal fun String.htmlEscape(): String = XmlStringUtil.escapeString(this, true)

internal fun String.wrapTag(tag: String) = "<$tag>$this</$tag>"

internal fun StringBuilder.html(text: String) = append(text.htmlEscape())

internal inline fun StringBuilder.wrap(prefix: String, postfix: String, crossinline body: () -> Unit) {
    this.append(prefix)
    body()
    this.append(postfix)
}

internal inline fun StringBuilder.wrapTag(tag: String, crossinline body: () -> Unit) {
    wrap("<$tag>", "</$tag>", body)
}

fun StringBuilder.appendNewLineHtml(): StringBuilder = append("<br>")

internal fun StringBuilder.processElement(project: Project, element: Element, chapter: String?, folder: String?) {
    if (element.className() == HIGHLIGHTER_CLASS) {
        HtmlSyntaxInfoUtil.appendHighlightedByLexerAndEncodedAsHtmlCodeSnippet(
            this,
            project,
            ArendLanguage.INSTANCE,
            element.text(),
            DocumentationSettings.getHighlightingSaturation(false)
        )
        appendNewLineHtml()
    } else {
        appendLine(changeTags(element.toString(), chapter, folder, isAside(element.tagName())))
    }
    appendNewLineHtml()
}

internal var counterLatexImages = 0

internal fun getHtmlLatexCode(title: String, latexCode: String, isNewLine: Boolean, project: Project, offset: Int): String {
    try {
        val font = UIManager.getDefaults().getFont("Label.font").size.toFloat()

        val formula = TeXFormula(latexCode)
        val icon: TeXIcon = formula.TeXIconBuilder()
            .setStyle(TeXConstants.STYLE_DISPLAY)
            .setSize(font)
            .build()
        val image = ImageUtil.createImage(icon.iconWidth, icon.iconHeight, BufferedImage.TYPE_INT_ARGB)

        val graphics = image.createGraphics()
        graphics.color = EditorColorsManager.getInstance().globalScheme.getColor(EditorColors.DOCUMENTATION_COLOR)
        graphics.fillRect(0, 0, icon.iconWidth, icon.iconHeight)

        val label = JLabel()
        label.setForeground(JBUI.CurrentTheme.RunWidget.FOREGROUND)

        icon.paintIcon(label, graphics, 0, 0)

        val latexImagesDir = File("latex-images").apply {
            mkdir()
        }

        val file = File(latexImagesDir.path + File.separator + title + ".png")
        ImageIO.write(image, "png", file.getAbsoluteFile())

        return buildString {
            if (isNewLine) {
                append("<div class=\"center\">")
            }
            append("<img ${if (!isNewLine) "style=\"transform: translate(0px,${icon.iconDepth}px);\"" else ""} src=\"file:///${file.absolutePath}\" title=$title width=\"${icon.iconWidth}\" height=\"${icon.iconHeight}\">")
            if (isNewLine) {
                append("</div>")
            }
        }
    } catch (e: Exception) {
        if (e is ParseException) {
            val notificationManager = SingletonNotificationManager(notificationGroup.displayId, NotificationType.WARNING)

            val notificationAction = NotificationAction.createSimpleExpiring(ArendBundle.message("arend.click.to.set.cursor.latex")) {
                val fileEditorManager = FileEditorManager.getInstance(project)
                val editor = fileEditorManager.selectedTextEditor ?: return@createSimpleExpiring
                val caretModel = editor.caretModel
                caretModel.moveToOffset(offset)
            }

            e.message?.let {
                notificationManager.notify("LaTeX parsing warning", it, project) { notification ->
                    notification.setSuggestionType(true).addAction(notificationAction)
                }
            }
        } else {
            LOG.error(e)
        }
    }
    return ""
}

private fun isAside(tag: String) = tag == "aside"

internal fun changeTags(line: String, chapter: String?, folder: String?, isAside: Boolean): String {
    return line.replace(REGEX_SPAN) {
        it.groupValues.getOrNull(2)?.wrapTag("b") ?: ""
    }.replace(REGEX_SPAN_HIGHLIGHT) {
        it.groupValues.getOrNull(1)?.wrapTag("b") ?: ""
    }.replace(REGEX_CODE) {
        it.groupValues.getOrNull(1)?.wrapTag("b") ?: ""
    }.replace(REGEX_HREF) {
        "<a href=\"${
            AREND_DOCUMENTATION_BASE_PATH + chapter + (if (isAside) folder else "") + it.groupValues.getOrNull(
                1
            )
        }\">${it.groupValues.getOrNull(2)}</a>"
    }
}

fun generateHtmlForArendLib(
    pathToArendLib: String, pathToArendLibInArendSite: String, versionArendLib: String?, updateColorScheme: Boolean
) {
    val projectManager = ProjectManager.getInstance()
    val psiProject = projectManager.loadAndOpenProject(pathToArendLib) ?: run {
        LOG.warn("Can't open arend-lib on this path=$pathToArendLib")
        return
    }
    try {
        val moduleManager = ModuleManager.getInstance(psiProject)
        val module = moduleManager.modules.getOrNull(0) ?: run {
            LOG.warn("Can't find the arend-lib module")
            return
        }
        module.register()

        val configService = ArendModuleConfigService.getInstance(module) ?: run {
            LOG.warn("Can't load information from the YAML file about arend-lib. You need to initialize arend-lib as an Arend module before starting html file generation")
            return
        }

        val version = versionArendLib
            ?: ("v" + (configService.version?.longString ?: run {
                LOG.warn("The YAML file in arend-lib doesn't contain information about the library version. You need to pass the library version as an argument or write the library version to the yaml file")
                return
            }))
        val srcDir = configService.sourcesDir

        if (updateColorScheme) {
            createColorCssFile(EditorColorsManager.getInstance().globalScheme)
        }

        val arendSiteVersionDir = pathToArendLibInArendSite + File.separator + version + File.separator
        File(arendSiteVersionDir).deleteRecursively()

        File(arendSiteVersionDir + AREND_DIR_HTML).apply {
            mkdirs()
        }

        val basePath = psiProject.basePath ?: ""
        val arendBaseFile = File(basePath + File.separator + AREND_BASE_FILE).apply {
            writeText("")
        }

        val indexFile = File(pathToArendLibInArendSite+ File.separator + "index.md")
        indexFile.readLines().find { REGEX_AREND_LIB_VERSION.find(it)?.groupValues?.getOrNull(1) == version }
            ?: indexFile.appendText("\n * [$version]($version/$AREND_DIR_HTML/Base.html)")


        val psiManager = PsiManager.getInstance(psiProject)

        var counter = 0
        val psiElementIds = mutableMapOf<String, Int>()
        val extraFiles = mutableSetOf<VirtualFile>()
        val usedExtraFiles = mutableSetOf<VirtualFile>()
        val baseLines = mutableListOf<String>()

        val virtualFileVisitor = object : VirtualFileVisitor<Any>() {
            override fun visitFile(file: VirtualFile): Boolean {
                val psiFile: PsiFile? = psiManager.findFile(file)
                if (psiFile is ArendFile) {
                    if (File(psiFile.virtualFile.path).toRelativeString(File(basePath)).startsWith(srcDir)) {
                        counter = generateHtmlForArend(
                            psiFile, psiElementIds, counter, extraFiles, usedExtraFiles, arendSiteVersionDir, arendBaseFile, baseLines, srcDir, version
                        )
                    }
                }
                return true
            }
        }

        val basePathVirtualFile = LocalFileSystem.getInstance().findFileByPath(basePath)
        if (basePathVirtualFile != null) {
            visitChildrenRecursively(basePathVirtualFile, virtualFileVisitor)
        }

        val localFileSystem = LocalFileSystem.getInstance()
        while (extraFiles.isNotEmpty()) {
            val extraVirtualFile = extraFiles.first()
            usedExtraFiles.add(extraVirtualFile)

            val extraFilePath = basePath + extraVirtualFile.path
            val file = File(extraFilePath).apply {
                writeText(extraVirtualFile.readText())
            }

            localFileSystem.refreshAndFindFileByPath(extraFilePath)?.let { psiManager.findFile(it) }?.let {
                counter = generateHtmlForArend(it as ArendFile, psiElementIds, counter, extraFiles, usedExtraFiles, arendSiteVersionDir, arendBaseFile, baseLines, srcDir, version)
            }
            extraFiles.remove(extraVirtualFile)
            file.delete()
        }

        baseLines.sorted().forEach {
            arendBaseFile.appendText(it)
        }
        localFileSystem.refreshAndFindFileByIoFile(arendBaseFile)?.let { psiManager.findFile(it) }?.let {
            generateHtmlForArend(it as ArendFile, psiElementIds, counter, extraFiles, usedExtraFiles, arendSiteVersionDir, arendBaseFile, baseLines, srcDir, version)
        }
        arendBaseFile.delete()
    } catch (e : Exception) {
        LOG.warn(e)
    } finally {
        projectManager.closeAndDispose(psiProject)
    }
    exitProcess(0)
}

fun generateHtmlForArend(
    arendFile: ArendFile,
    psiElementIds: MutableMap<String, Int>,
    maxId: Int,
    extraFiles: MutableSet<VirtualFile>,
    usedExtraFiles: Set<VirtualFile>,
    arendSiteVersionDir: String,
    arendBaseFile: File,
    baseLines: MutableList<String>,
    arendLibSrcDir: String,
    version: String
): Int {
    var counter = maxId
    val arendLibProjectName = arendFile.project.name
    val arendLibProjectBasePath = arendFile.project.basePath ?: return maxId

    val containingDir = arendFile.containingDirectory ?: return maxId

    val project = arendFile.project
    val fileEditorManager = FileEditorManager.getInstance(project)
    val descriptor = OpenFileDescriptor(project, arendFile.virtualFile)
    val editor = fileEditorManager.openTextEditor(descriptor, false) ?: return maxId

    val highlightingPass = ArendHighlightingPass(arendFile, editor, TextRange(0, editor.document.textLength), DefaultHighlightInfoProcessor())

    val daemonIndicator = DaemonProgressIndicator()
    highlightingPass.collectInformationWithProgress(daemonIndicator)
    val highlights = highlightingPass.getHighlights().toList().sortedBy { it.startOffset }
    var indexHighlight = 0

    val relativePathToCurDir = File(containingDir.virtualFile.path).toRelativeString(File(arendLibProjectBasePath))
    var arendDir = relativePathToCurDir.removePrefix(arendLibSrcDir).removePrefix(File.separator).replace(File.separator, ".")
    if (arendDir.isNotEmpty()) {
        arendDir += "."
    }
    val title = arendFile.name.removeSuffix(EXTENSION)
    val arendPackage = "$arendDir$title"

    println("Generate an html file for $arendPackage")
    if (File(arendFile.virtualFile.path) != arendBaseFile) {
        baseLines.add("\\import $arendPackage\n")
    }

    val curPath = arendSiteVersionDir + File.separator + AREND_DIR_HTML + relativePathToCurDir + File.separator
    val curDir = File(curPath)
    curDir.mkdirs()

    val extraHtmlDir = title + HTML_DIR_EXTENSION

    val htmlDirPath = "$curPath$extraHtmlDir"
    File(htmlDirPath).apply {
        deleteRecursively()
        mkdir()
    }

    val projectDir = PathManager.getPluginsDir().parent.parent.parent.toString()
    addExtraFiles(projectDir, htmlDirPath)

    File("$curPath$title$HTML_EXTENSION").writeText(buildString {
        wrapTag("html") {
            wrapTag("head") {
                wrapTag("title") {
                    append(title)
                }
                append("<link rel=\"stylesheet\" href=\"${extraHtmlDir}/$AREND_CSS\">")
            }
            wrapTag("body") {
                append("<pre class=\"Arend\">")

                arendFile.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (element.firstChild != null && element !is ArendReferenceElement) {
                            super.visitElement(element)
                            return
                        }

                        while (indexHighlight < highlights.size && element.textRange.endOffset > highlights[indexHighlight].endOffset) {
                            indexHighlight++
                        }

                        val cssClass = if (indexHighlight in highlights.indices &&
                                            highlights[indexHighlight].startOffset <= element.textRange.startOffset &&
                                            highlights[indexHighlight].endOffset >= element.textRange.endOffset) {
                            colorsByAttributesKey[highlights[indexHighlight].forcedTextAttributesKey]
                        } else {
                            ArendSyntaxHighlighter.map(element.elementType)
                        }

                        val href = if (element is ArendReferenceElement) {
                            when (val resolve = element.resolve) {
                                element -> null
                                is PsiFile -> {
                                    val resolvePath = resolve.virtualFile.path
                                    val relativePathToRefFile =
                                        if (resolve.virtualFile is LightVirtualFile) {
                                            resolvePath.removePrefix(File.separator)
                                        } else {
                                            File(resolvePath).toRelativeString(File(arendLibProjectBasePath))
                                        }.removeSuffix(EXTENSION)

                                    "/$arendLibProjectName/$version/$AREND_DIR_HTML$relativePathToRefFile$HTML_EXTENSION"
                                }

                                is PsiLocatedReferable -> {
                                    val resolveVirtualFile = resolve.containingFile.virtualFile
                                    val result = if (resolveVirtualFile is LightVirtualFile) {
                                        if (!usedExtraFiles.contains(resolveVirtualFile)) {
                                            extraFiles.add(resolveVirtualFile)
                                        }
                                        getIdCounterAndRelativePath(
                                            resolve,
                                            psiElementIds,
                                            counter,
                                            resolveVirtualFile.path.removeSuffix(EXTENSION).removePrefix(File.separator)
                                        )
                                    } else {
                                        getIdCounterAndRelativePath(resolve, psiElementIds, counter)
                                    }
                                    val id = result.first
                                    counter = result.second
                                    val relativePathToRefFile = result.third

                                    "/$arendLibProjectName/$version/$AREND_DIR_HTML$relativePathToRefFile$HTML_EXTENSION#$id"
                                }

                                else -> null
                            }
                        } else {
                            null
                        }

                        val result = getIdCounterAndRelativePath(element, psiElementIds, counter)
                        val id = result.first
                        counter = result.second

                        append("<a${" id=\"$id\""}${href?.let { " href=\"$it\" style=\"text-decoration:none;\"" } ?: ""}${
                            cssClass?.let { " class=\"$it\"" } ?: ""}>${StringEscapeUtils.escapeHtml(element.text)}</a>")
                    }
                })
                append("<script src=\"$extraHtmlDir/$AREND_JS\"></script>")
                append("</pre>")
            }
        }
    })
    return counter
}

private fun addExtraFiles(projectDir: String, htmlDirPath: String) {
    File("$projectDir/src/main/html").also {
        File(it.path + File.separator + AREND_CSS).copyTo(File(htmlDirPath + File.separator + AREND_CSS))
        File(it.path + File.separator + AREND_JS).copyTo(File(htmlDirPath + File.separator + AREND_JS))
    }
}

private fun createColorCssFile(scheme: EditorColorsScheme) {
    val projectDir = PathManager.getPluginsDir().parent.parent.parent.toString()
    val cssFile = File("$projectDir/src/main/html/$AREND_CSS")
    cssFile.writeText("")

    for (arendColors in AREND_COLORS) {
        cssFile.appendText(
            ".Arend .${arendColors.name} { color: ${
                String.format(
                    "#%06x",
                    (scheme.getAttributes(arendColors.textAttributesKey)?.foregroundColor?.rgb ?: 0) and 0xFFFFFF
                )
            } }\n"
        )
    }
    cssFile.run {
        appendText("\n.Arend a { text-decoration: none }\n")
        appendText(".Arend a[href]:hover { background-color: #8fbc8f }\n")
        appendText(".Arend [href].hover-highlight { background-color: #8fbc8f; }")
    }
}

private fun getIdCounterAndRelativePath(
    element: PsiElement, psiIds: MutableMap<String, Int>, counter: Int, relativePath: String? = null
): Triple<Int, Int, String> {
    val relativePathToRefFile =
        relativePath ?: File(element.containingFile.virtualFile.path).toRelativeString(File(element.project.basePath!!))
            .removeSuffix(EXTENSION)
    val key = relativePathToRefFile + ':' + element.textOffset.toString()
    val id = psiIds[key] ?: run {
        psiIds[key] = counter
        psiIds[key]!!
    }
    return Triple(id, counter + 1, relativePathToRefFile)
}
