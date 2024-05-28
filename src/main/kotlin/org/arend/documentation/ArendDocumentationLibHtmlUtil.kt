package org.arend.documentation

import com.intellij.codeInsight.daemon.impl.DaemonProgressIndicator
import com.intellij.codeInsight.daemon.impl.DefaultHighlightInfoProcessor
import com.intellij.openapi.application.PathManager
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.EditorColorsScheme
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.ProjectManager
import com.intellij.openapi.util.TextRange
import com.intellij.openapi.vfs.*
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.PsiRecursiveElementVisitor
import com.intellij.psi.util.elementType
import com.intellij.testFramework.LightVirtualFile
import org.apache.commons.lang.StringEscapeUtils
import org.arend.highlight.ArendHighlightingColors
import org.arend.highlight.ArendHighlightingPass
import org.arend.highlight.ArendSyntaxHighlighter
import org.arend.module.config.ArendModuleConfigService
import org.arend.psi.ArendFile
import org.arend.psi.ext.ArendReferenceElement
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.util.FileUtils
import org.arend.util.allModules
import org.arend.util.register
import java.io.File
import kotlin.system.exitProcess

const val HTML_EXTENSION = ".html"
const val HTML_DIR_EXTENSION = "HTML"

internal fun generateHtmlForArendLib(
    pathToArendLib: String,
    pathToArendLibInArendSite: String,
    versionArendLib: String?,
    updateColorScheme: Boolean
) {
    val projectManager = ProjectManager.getInstance()
    val psiProject = projectManager.loadAndOpenProject(pathToArendLib) ?: run {
        LOG.warn("Can't open arend-lib on this path=$pathToArendLib")
        return
    }
    try {
        val module = psiProject.allModules.getOrNull(0) ?: run {
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

        val localFileSystem = LocalFileSystem.getInstance()
        val basePathVirtualFile = localFileSystem.findFileByPath(basePath)
        if (basePathVirtualFile != null) {
            VfsUtilCore.visitChildrenRecursively(basePathVirtualFile, virtualFileVisitor)
        }

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

private fun generateHtmlForArend(
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
    val title = arendFile.name.removeSuffix(FileUtils.EXTENSION)
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
                            ArendHighlightingColors.colorsByAttributesKey[highlights[indexHighlight].forcedTextAttributesKey]
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
                                        }.removeSuffix(FileUtils.EXTENSION)

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
                                            resolveVirtualFile.path.removeSuffix(FileUtils.EXTENSION).removePrefix(File.separator)
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

    for (arendColors in ArendHighlightingColors.AREND_COLORS) {
        cssFile.appendText(
            ".Arend .${arendColors.name} { color: ${getHtmlRgbFormat(
                scheme.getAttributes(arendColors.textAttributesKey)?.foregroundColor?.rgb ?: 0
            )}\n"
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
            .removeSuffix(FileUtils.EXTENSION)
    val key = relativePathToRefFile + ':' + element.textOffset.toString()
    val id = psiIds[key] ?: run {
        psiIds[key] = counter
        psiIds[key]!!
    }
    return Triple(id, counter + 1, relativePathToRefFile)
}
