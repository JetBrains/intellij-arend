package org.arend.codeInsight

import com.intellij.lang.parameterInfo.*
import org.arend.codeInsight.ArendCodeInsightUtils.Companion.computeParameterInfo
import org.arend.psi.ext.*

class ArendParameterInfoHandler: ParameterInfoHandler<ArendReferenceContainer, List<ParameterDescriptor>> {

    override fun updateUI(p: List<ParameterDescriptor>?, context: ParameterInfoUIContext) {
        if (p == null) return
        var curOffset = 0
        val text = StringBuilder()
        var hlStart = -1; var hlEnd = -1

        val isDumbMode = context.currentParameterIndex < -1
        val unknownParameterSelected = context.currentParameterIndex == -2
        val currentParameterIndex = if (isDumbMode) -4-context.currentParameterIndex else context.currentParameterIndex
        var numberOfExplicitParameters = 0
        var numberOfUnreliableParameters = 0

        fun appendQuestionMark() {
            if (isDumbMode && numberOfExplicitParameters == 0 && numberOfUnreliableParameters == 0 && hlStart == -1 && hlEnd == -1) {
                if (unknownParameterSelected) hlStart = curOffset
                text.append("???")
                if (!p.isEmpty()) text.append(", ")
                if (unknownParameterSelected) hlEnd = text.length
                curOffset = text.length
            }
        }

        if (p.isEmpty()) appendQuestionMark()

        for ((index, pm) in p.withIndex()) {
            if (text.isNotEmpty()) {
                text.append(", ")
            }
            curOffset = text.length

            if (pm.isExplicit) appendQuestionMark() //??? is appended before first explicit parameter

            if (pm.isExplicit) numberOfExplicitParameters++

            val isReliableParameter = numberOfExplicitParameters > 0 || !isDumbMode || pm.isThis()
            var varText = pm.getNameOrUnderscore() + if (pm.getType() != null) " : " + pm.getType() else ""
            if (!pm.isExplicit) {
                varText = "{$varText}"
            }
            if (!isReliableParameter) {
                numberOfUnreliableParameters++
                varText = "?$varText?"
            }

            text.append(varText)
            if (!unknownParameterSelected && index == currentParameterIndex) {
                hlStart = curOffset
                hlEnd = text.length + 1
            }

            if (unknownParameterSelected) {
                if (hlStart == -1 && !pm.isThis() && !pm.isExplicit) hlStart = curOffset
                if (hlEnd == -1 && pm.isExplicit) hlEnd = curOffset
            }
        }

        context.setupUIComponentPresentation(text.toString(), hlStart, hlEnd, !context.isUIComponentEnabled, false, false, context.defaultParameterColor)
    }

    override fun findElementForParameterInfo(context: CreateParameterInfoContext): ArendReferenceContainer? {
        val info = computeParameterInfo(context.file, context.editor.caretModel.offset) ?: return null
        context.itemsToShow = arrayOf(info.parameters)
        return info.parameterOwner
    }

    override fun showParameterInfo(element: ArendReferenceContainer, context: CreateParameterInfoContext) {
        context.showHint(element, element.textRange.startOffset, this)
    }

    override fun findElementForUpdatingParameterInfo(context: UpdateParameterInfoContext): ArendReferenceContainer? {
        val info = computeParameterInfo(context.file, context.editor.caretModel.offset, context.parameterOwner) ?: return null
        if (info.externalParametersOk) {
            context.setCurrentParameter(info.parameterIndex)
        } else {
            val index = info.parameterIndex
            val correctedIndex = -4 - index
            context.setCurrentParameter(correctedIndex)
        }
        return info.parameterOwner
    }

    override fun updateParameterInfo(parameterOwner: ArendReferenceContainer, context: UpdateParameterInfoContext) {}
}
