package org.arend.refactoring

import org.arend.ArendTestBase
import org.arend.fileTreeFromText
import org.arend.psi.ancestor
import org.arend.psi.ext.PsiLocatedReferable
import org.arend.refactoring.changeSignature.*
import org.arend.term.abs.Abstract.ParametersHolder
import org.intellij.lang.annotations.Language
import kotlin.math.abs

abstract class ArendChangeSignatureTestBase: ArendTestBase() {
    fun changeSignature(@Language("Arend") contents: String,
                        @Language("Arend") resultingContent: String,
                        options: List<Any>,
                        typeQualifications: List<Pair<String, Pair<Boolean, String>>> = emptyList(),
                        newName: String? = null,
                        typecheck: Boolean = true,
                        fileName: String = "Main.ard") {
        val fileTree = fileTreeFromText(contents)
        fileTree.createAndOpenFileWithCaretMarker()
        val sourceElement = myFixture.elementAtCaret.ancestor<PsiLocatedReferable>() ?: throw AssertionError("Cannot find source anchor")
        val oldParams = ArendParametersInfo(sourceElement)
        val newParams = ArrayList<ArendTextualParameter>()
        val newNameActual = newName ?: sourceElement.refName
        val typeMap = HashMap<String, Pair<Boolean, String>>()
        for (tq in typeQualifications) typeMap[tq.first] = tq.second
        for (element in options) when (element) {
            is Int -> {
                val index = abs(element) - 1
                val toggle = element < 0
                val originalElement = oldParams.parameterInfo[index]
                ArendTextualParameter(originalElement.name, originalElement.typeText, index, originalElement.isExplicit().let { if (toggle) !it else it },
                    isProperty = originalElement.isProperty,
                    isClassifying = originalElement.isClassifying,
                    isCoerce = originalElement.isCoerce,
                    correspondingReferable = originalElement.correspondingReferable)
            }
            is String -> {
                val entry = typeMap[element]!!
                ArendTextualParameter(element, entry.second, -1, entry.first, correspondingReferable = null)
            }
            is Pair<*, *> -> {
                val (index, toggle) = (element.first as? Int ?: throw java.lang.IllegalArgumentException()).let { Pair(abs(it) - 1, it < 0) }
                val tq = typeMap[element.second]
                val originalElement = oldParams.parameterInfo[index]
                ArendTextualParameter(element.second as? String ?: throw java.lang.IllegalArgumentException(), tq?.second ?: originalElement.typeText, index, originalElement.isExplicit().let { tq?.first ?: if (toggle) !it else it }, correspondingReferable = originalElement.correspondingReferable)
            }
            else -> throw IllegalArgumentException()
        }.let { newParams.add(it) }

        if (typecheck) typecheck()

        if (!ArendChangeSignatureHandler.checkExternalParametersOk(sourceElement as ParametersHolder))
            throw AssertionError("External parameters have not been inferred properly")

        ArendChangeSignatureProcessor(project, ArendChangeInfo(ArendParametersInfo(sourceElement, newParams), null, newNameActual, sourceElement), false).run()
        myFixture.checkResult(fileName, resultingContent.trimIndent(), false)
    }
}