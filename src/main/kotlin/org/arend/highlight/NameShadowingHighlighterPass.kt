package org.arend.highlight

import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.codeInsight.daemon.impl.HighlightInfoProcessor
import com.intellij.codeInsight.daemon.impl.HighlightInfoType
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.progress.ProgressIndicator
import com.intellij.openapi.util.TextRange
import com.intellij.psi.util.descendantsOfType
import org.arend.psi.ArendFile
import org.arend.psi.ext.*

class NameShadowingHighlighterPass(file: ArendFile, editor: Editor, highlightInfoProcessor: HighlightInfoProcessor) :
    BasePass(file, editor, "Arend name shadowing annotator", TextRange(0, editor.document.textLength), highlightInfoProcessor) {

    override fun collectInformationWithProgress(progress: ProgressIndicator) {
        val typeTeles = file.descendantsOfType<ArendTypeTele>()
        val nameTeles = file.descendantsOfType<ArendNameTele>()
        val nameUntypedTeles = file.descendantsOfType<ArendNameTeleUntyped>()

        exploreScope(typeTeles)
        exploreScope(nameTeles)
        exploreScope(nameUntypedTeles)
    }

    private fun exploreScope(teles: Sequence<ArendSourceNodeImpl>) {
        for (tele in teles) {
            val identifiers = when (tele) {
                is ArendTypeTele -> tele.typedExpr?.identifierOrUnknownList?.filter { it.defIdentifier != null }
                    ?.map { it.defIdentifier!! } ?: continue
                is ArendNameTele -> tele.identifierOrUnknownList.filter { it.defIdentifier != null }
                    .map { it.defIdentifier!! }
                is ArendNameTeleUntyped -> listOf(tele.defIdentifier)
                else -> emptyList()
            }
            searchForSameIdentifiers(identifiers, tele)
        }
    }

    private fun searchForSameIdentifiers(identifiers: List<ArendDefIdentifier>, element: ArendCompositeElement) {
        if (identifiers.isEmpty()) {
            return
        }
        val elements = element.scope.elements.map { it.refName }
        for (identifier in identifiers) {
            val name = identifier.name
            if (elements.contains(name)) {
                val builder = HighlightInfo
                    .newHighlightInfo(HighlightInfoType.WARNING)
                    .range(identifier.textRange)
                    .severity(HighlightSeverity.WARNING)
                    .descriptionAndTooltip("An identifier with the same name $name was announced earlier")
                addHighlightInfo(builder)
            }
        }
    }
}
