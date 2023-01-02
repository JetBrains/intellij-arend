package org.arend.refactoring

import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.psi.ancestor
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.refactoring.changeSignature.ArendChangeInfo
import org.arend.refactoring.changeSignature.ArendChangeSignatureProcessor
import org.arend.refactoring.changeSignature.ArendParameterInfo
import org.intellij.lang.annotations.Language
import kotlin.math.abs

abstract class ArendChangeSignatureTestBase: ArendTestBase() {
    fun changeSignature(@Language("Arend") contents: String,
                        @Language("Arend") resultingContent: String,
                        options: List<Any>,
                        typeQualifications: List<Pair<String, Pair<Boolean, String>>> = emptyList(),
                        newName: String? = null) {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        val sourceElement = myFixture.elementAtCaret.ancestor<PsiLocatedReferable>() ?: throw AssertionError("Cannot find source anchor")
        val baseParams = ArendChangeInfo.getParameterInfo(sourceElement)
        val newParams = ArrayList<ArendParameterInfo>()
        val newNameActual = newName ?: sourceElement.refName
        val typeMap = HashMap<String, Pair<Boolean, String>>()
        for (tq in typeQualifications) typeMap[tq.first] = tq.second
        for (element in options) when (element) {
            is Int -> {
                val index = abs(element) - 1
                val toggle = element < 0
                val originalElement = baseParams[index]
                ArendParameterInfo(originalElement.name, originalElement.typeText, index, originalElement.isExplicit().let { if (toggle) !it else it })
            }
            is String -> {
                val entry = typeMap[element]!!
                ArendParameterInfo(element, entry.second, -1, entry.first)
            }
            is Pair<*, *> -> {
                val (index, toggle) = (element.first as? Int ?: throw java.lang.IllegalArgumentException()).let { Pair(it - 1, it < 0) }
                val originalElement = baseParams[index]
                ArendParameterInfo(element.second as? String ?: throw java.lang.IllegalArgumentException(), originalElement.typeText, index, originalElement.isExplicit().let { if (toggle) !it else it })
            }
            else -> throw IllegalArgumentException()
        }.let { newParams.add(it) }

        ArendChangeSignatureProcessor(project, ArendChangeInfo(newParams, null, newNameActual, sourceElement)).run()
        myFixture.checkResult(resultingContent.trimIndent())
    }
}