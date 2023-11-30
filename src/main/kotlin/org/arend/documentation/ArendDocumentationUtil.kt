package org.arend.documentation

import com.intellij.lang.documentation.DocumentationSettings
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.editor.richcopy.HtmlSyntaxInfoUtil
import com.intellij.openapi.module.ModuleManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.project.ProjectManager
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
import com.intellij.xml.util.XmlStringUtil
import org.apache.commons.lang.StringEscapeUtils
import org.arend.ArendLanguage
import org.arend.highlight.ArendHighlightingColors.Companion.AREND_COLORS
import org.arend.highlight.ArendSyntaxHighlighter
import org.arend.module.AREND_LIB
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.ArendFile
import org.arend.psi.ext.*
import org.arend.util.FileUtils.DEFAULT_SOURCES_DIR
import org.arend.util.FileUtils.EXTENSION
import org.arend.util.register
import org.jsoup.nodes.Element
import java.io.File
import kotlin.system.exitProcess

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

fun StringBuilder.appendNewLineHtml() = append("<br>")

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
    pathToArendLib: String?, scheme: EditorColorsScheme, host: String, port: Int?
) {
    val projectManager = ProjectManager.getInstance()
    val psiProject = pathToArendLib?.let { projectManager.loadAndOpenProject(it) } ?: return

    val moduleManager = ModuleManager.getInstance(psiProject)
    val module = moduleManager.modules.getOrNull(0)
    module?.register()

    val configService = ArendModuleConfigService.getInstance(module)
    val srcDir = configService?.sourcesDir ?: DEFAULT_SOURCES_DIR

    val psiManager = PsiManager.getInstance(psiProject)

    var counter = 0
    val psiElementIds = mutableMapOf<String, Int>()
    val extraFiles = mutableSetOf<VirtualFile>()
    val usedExtraFiles = mutableSetOf<VirtualFile>()

    createColorCssFile(scheme)

    val basePath = psiProject.basePath ?: ""
    val basePathWithSeparator = basePath + File.separator
    File(basePathWithSeparator + AREND_DIR_HTML).apply {
        deleteRecursively()
    }

    val arendBaseFile = File(basePathWithSeparator + AREND_BASE_FILE).apply {
        writeText("")
    }

    val psiProjectName = psiProject.name
    val indexFile = File(File(basePath).parent + File.separator + "index.md")
    indexFile.readLines().find { REGEX_AREND_LIB_VERSION.find(it)?.groupValues?.getOrNull(1) == psiProjectName }
        ?: indexFile.appendText("\n * [$psiProjectName]($psiProjectName/arend-html-files/Base.html)")

    val virtualFileVisitor = object : VirtualFileVisitor<Any>() {
        override fun visitFile(file: VirtualFile): Boolean {
            val psiFile: PsiFile? = psiManager.findFile(file)
            if (psiFile is ArendFile) {
                counter = generateHtmlForArend(
                    psiFile, psiElementIds, counter, extraFiles, usedExtraFiles, arendBaseFile, srcDir, host, port
                )
            }
            return true
        }
    }

    val basePathVirtualFile = LocalFileSystem.getInstance().findFileByPath(basePath)
    if (basePathVirtualFile != null) {
        visitChildrenRecursively(basePathVirtualFile, virtualFileVisitor)
    }

    while (extraFiles.isNotEmpty()) {
        val extraVirtualFile = extraFiles.first()
        usedExtraFiles.add(extraVirtualFile)

        val extraFilePath = basePath + extraVirtualFile.path
        File(extraFilePath).apply {
            writeText(extraVirtualFile.readText())
        }

        LocalFileSystem.getInstance().refreshAndFindFileByPath(extraFilePath)
            ?.let { visitChildrenRecursively(it, virtualFileVisitor) }
        extraFiles.remove(extraVirtualFile)
    }

    val arendBaseVirtualFile = LocalFileSystem.getInstance().refreshAndFindFileByIoFile(arendBaseFile)
    if (arendBaseVirtualFile != null) {
        visitChildrenRecursively(arendBaseVirtualFile, virtualFileVisitor)
    }

    projectManager.closeAndDispose(psiProject)
    exitProcess(0)
}

fun generateHtmlForArend(
    psiFile: PsiFile,
    psiElementIds: MutableMap<String, Int>,
    maxId: Int,
    extraFiles: MutableSet<VirtualFile>,
    usedExtraFiles: Set<VirtualFile>,
    arendBaseFile: File,
    arendLibSrcDir: String,
    host: String,
    port: Int?
): Int {
    var counter = maxId
    val libraryVersion = psiFile.project.name
    val projectBasePath = psiFile.project.basePath ?: return maxId

    println("Generate an html file for " + psiFile.virtualFile.path)

    val relativePathToCurDir = File(psiFile.containingDirectory.virtualFile.path).toRelativeString(File(projectBasePath))
    val curPath = psiFile.project.basePath + File.separator + AREND_DIR_HTML + relativePathToCurDir + File.separator
    val curDir = File(curPath)
    curDir.mkdirs()

    val title = psiFile.name.removeSuffix(EXTENSION)
    val extraHtmlDir = title + HTML_DIR_EXTENSION

    val htmlDirPath = "$curPath$extraHtmlDir"
    File(htmlDirPath).apply {
        deleteRecursively()
        mkdir()
    }

    val projectDir = PathManager.getPluginsDir().parent.parent.parent.toString()
    addExtraFiles(projectDir, htmlDirPath)

    var packageFile = relativePathToCurDir.removePrefix(arendLibSrcDir).removePrefix(File.separator).replace(File.separator, ".")
    if (packageFile.isNotEmpty()) {
        packageFile += "."
    }
    if (File(psiFile.virtualFile.path) != arendBaseFile) {
        arendBaseFile.appendText("\\import $packageFile$title\n")
    }

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

                psiFile.accept(object : PsiRecursiveElementVisitor() {
                    override fun visitElement(element: PsiElement) {
                        if (element.firstChild != null && element !is ArendReferenceElement) {
                            super.visitElement(element)
                            return
                        }

                        val cssClass = ArendSyntaxHighlighter.map(element.elementType)

                        val href = if (element is ArendReferenceElement) {
                            when (val resolve = element.resolve) {
                                element -> null
                                is PsiFile -> {
                                    val resolvePath = resolve.virtualFile.path
                                    val relativePathToRefFile =
                                        if (resolve.virtualFile is LightVirtualFile) {
                                            resolvePath.removePrefix(File.separator)
                                        } else {
                                            File(resolvePath).toRelativeString(File(projectBasePath))
                                        }.removeSuffix(EXTENSION)

                                    "$host:${port ?: ""}/$AREND_LIB/$libraryVersion/$AREND_DIR_HTML$relativePathToRefFile$HTML_EXTENSION"
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

                                    "$host:${port ?: ""}/$AREND_LIB/$libraryVersion/$AREND_DIR_HTML$relativePathToRefFile$HTML_EXTENSION#$id"
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
