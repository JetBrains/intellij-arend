package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.term.abs.Abstract
import org.arend.term.group.AccessModifier
import java.util.*
import kotlin.collections.ArrayList
import kotlin.collections.HashMap
import kotlin.collections.HashSet

class ArendParametersInfo(val locatedReferable: PsiLocatedReferable, val parameterInfo: MutableList<ArendTextualParameter>) {
    private val parameterToTeleWhitespaceData = HashMap<Int, TeleWhitespaceData>()
    private val parameterWhitespaceData = HashMap<Int, String>()

    constructor(locatedReferable: PsiLocatedReferable): this(locatedReferable, getParameterInfo(locatedReferable))

    init {
        var count = 0
        if (locatedReferable is Abstract.ParametersHolder) {
            for (tele in locatedReferable.parameters) when (tele) {
                is ArendNameTele -> {
                    val beforeTeleWhitespace = tele.getWhitespace(SpaceDirection.LeadingSpace) ?: continue
                    val colonText = tele.type?.let { it.getWhitespace(SpaceDirection.LeadingSpace, true) }
                    val afterTypeText = tele.type?.let{ it.getWhitespace(SpaceDirection.TrailingSpace) }
                    val data = TeleWhitespaceData(beforeTeleWhitespace, colonText, afterTypeText)
                    for (id in tele.identifierOrUnknownList) {
                        parameterToTeleWhitespaceData[count] = data
                        id.getWhitespace(SpaceDirection.LeadingSpace)?.let{ parameterWhitespaceData[count] = it}
                        count++
                    }
                }
            }
        }
    }

    fun parameterText(): String {
        data class TeleEntry(val typeText: String?, val isExplicit: Boolean, val isCoerce: Boolean, val isClassifying: Boolean, val isProperty: Boolean, val accessModifier: org.arend.term.group.AccessModifier = org.arend.term.group.AccessModifier.PUBLIC, val parameterNames: MutableList<Pair<String?, Int>>)
        val teleEntries = ArrayList<TeleEntry>()
        val associatedWhitespaceData = HashSet<Pair<TeleEntry, TeleWhitespaceData>>()
        val usedWhitespaceData = HashSet<TeleWhitespaceData>()

        for (parameter in parameterInfo) {
            if (teleEntries.isEmpty() || teleEntries.last().typeText != parameter.typeText || teleEntries.last().isExplicit != parameter.isExplicit() || parameter.isCoerce || parameter.isClassifying)
                teleEntries.add(TeleEntry(parameter.typeText, parameter.isExplicit(), parameter.isCoerce, parameter.isClassifying, parameter.isProperty, parameter.accessModifier, Collections.singletonList(
                    Pair(parameter.name, parameter.oldIndex)
                ).toMutableList())) else
                teleEntries.last().parameterNames.add(Pair(parameter.name, parameter.oldIndex))
            val data = parameterToTeleWhitespaceData[parameter.oldIndex]
            if (data != null && !usedWhitespaceData.contains(data)) {
                usedWhitespaceData.add(data)
                associatedWhitespaceData.add(Pair(teleEntries.last(), data))
            }
        }

        val newTeles = StringBuilder()
        for (entry in teleEntries) {
            val whitespaceData = associatedWhitespaceData.firstOrNull { it.first == entry }?.second
            newTeles.append(whitespaceData?.beforeTeleWhitespace ?: " ")
            val parameterNames = StringBuilder()
            for ((i, p) in entry.parameterNames.withIndex()) {
                var space = parameterWhitespaceData[p.second] ?: " "
                if (i == 0 && space.startsWith(" ")) space = space.drop(1)
                if (i > 0 && space.isEmpty()) space = " "
                parameterNames.append(space)
                parameterNames.append(p.first.let{ if (it.isNullOrEmpty()) ArendElementTypes.UNDERSCORE else it })
            }

            val referableAllowsTypeTeles = locatedReferable is ArendConstructor || locatedReferable is ArendDefData
            val printParameterNames = parameterNames.toString() != "_" || !referableAllowsTypeTeles
            val printSurroundingParens = !entry.isExplicit || entry.typeText == null || printParameterNames
            if (printSurroundingParens) newTeles.append (if (entry.isExplicit) ArendElementTypes.LPAREN else ArendElementTypes.LBRACE)
            when (entry.accessModifier) {
                AccessModifier.PRIVATE -> newTeles.append("${ArendElementTypes.PRIVATE_KW} ")
                AccessModifier.PROTECTED -> newTeles.append("${ArendElementTypes.PROTECTED_KW} ")
                else -> {}
            }
            if (entry.isCoerce) newTeles.append("${ArendElementTypes.COERCE_KW} ") else
                if (entry.isClassifying) newTeles.append("${ArendElementTypes.CLASSIFYING_KW} ") else
                    if (entry.isProperty) newTeles.append("${ArendElementTypes.PROPERTY_KW} ")


            if (entry.typeText != null) {
                if (printParameterNames) {
                    newTeles.append(parameterNames.toString())
                    newTeles.append(whitespaceData?.colonText ?: " ${ArendElementTypes.COLON} ")
                }
                newTeles.append(if (entry.typeText.trim().contains(" ") && !printParameterNames && !printSurroundingParens) "(${entry.typeText})" else entry.typeText)
            } else {
                newTeles.append(parameterNames.toString())
            }

            newTeles.append(whitespaceData?.afterTypeText ?: "")
            if (printSurroundingParens) newTeles.append (if (entry.isExplicit) ArendElementTypes.RPAREN else ArendElementTypes.RBRACE)
        }
        return newTeles.toString()
    }

    fun getNewParameters(): Array<ParameterInfo> = parameterInfo.toTypedArray()
    fun isParameterSetOrOrderChanged(): Boolean { //Or explicitness of arguments
        val oldParameterInfo = getParameterInfo(locatedReferable)
        if (oldParameterInfo.size != parameterInfo.size) return true
        for ((i, p) in parameterInfo.withIndex()) if (p.oldIndex != i || p.isExplicit() != oldParameterInfo[i].isExplicit()) return true
        return false
    }

    fun isParameterTypesChanged(): Boolean {
        val oldParameterInfo = getParameterInfo(locatedReferable)
        for (p in parameterInfo) if (p.oldIndex != -1 && p.typeText != oldParameterInfo[p.oldIndex].typeText) return true
        return false
    }

    fun isParameterNamesChanged(): Boolean {
        val oldParameterInfo = getParameterInfo(locatedReferable)
        for (p in parameterInfo)
            if (p.oldIndex != -1 && p.name != oldParameterInfo[p.oldIndex].name)
                return true
        return false
    }

    companion object {
        private data class TeleWhitespaceData (val beforeTeleWhitespace: String, val colonText: String?, val afterTypeText: String?)

        fun getParameterInfo(locatedReferable: PsiLocatedReferable): MutableList<ArendTextualParameter> {
            var index = 0
            val result = ArrayList<ArendTextualParameter>()

            for (t in ArendChangeInfo.getTeles(locatedReferable)) when (t) {
                is ArendNameTele -> for (parameter in t.identifierOrUnknownList) {
                    result.add(
                        ArendTextualParameter(
                            parameter.defIdentifier?.name ?: "_",
                            t.type?.oneLineText,
                            index,
                            t.isExplicit,
                            isProperty = t.isProperty,
                            correspondingReferable = parameter.defIdentifier
                        )
                    )
                    index++
                }
                is ArendTypeTele -> {
                    val type = t.type?.text
                    var i = 0
                    t.typedExpr?.identifierOrUnknownList?.forEach { iOU ->
                        i++
                        iOU.defIdentifier?.let { dI -> result.add(
                            ArendTextualParameter(
                                dI.name,
                                type,
                                index,
                                t.isExplicit,
                                isProperty = t.isProperty,
                                correspondingReferable = iOU.defIdentifier
                            )
                        ) }
                        index++
                    }
                    if (i == 0) {
                        result.add(
                            ArendTextualParameter(
                                "_",
                                type,
                                index,
                                t.isExplicit,
                                isProperty = t.isProperty,
                                correspondingReferable = t
                            )
                        )
                        index++
                    }
                }
                is ArendNameTeleUntyped -> {
                    result.add(
                        ArendTextualParameter(
                            t.defIdentifier.name,
                            null,
                            index,
                            t.isExplicit,
                            isProperty = t.isProperty,
                            correspondingReferable = t
                        )
                    )
                    index++
                }

                is ArendFieldTele -> {
                    for (fdI in t.referableList) {
                        result.add(
                            ArendTextualParameter(
                                fdI.name,
                                t.type?.text ?: "",
                                index,
                                t.isExplicit,
                                t.isClassifying,
                                t.isCoerce,
                                t.isProperty,
                                t.descendantOfType<ArendAccessMod>()?.accessModifier ?: AccessModifier.PUBLIC,
                                correspondingReferable = fdI
                            )
                        )
                        index++
                    }
                }
            }
            return result
        }
    }
}