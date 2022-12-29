package org.arend.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.psi.util.elementType
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.ArendLanguage
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.term.abs.Abstract.ParametersHolder
import java.util.Collections.singletonList

class ArendChangeInfo (
    private val parameterInfo : List<ArendParameterInfo>,
    private val returnType: String?,
    private val name: String,
    private val locatedReferable: PsiLocatedReferable,
    val nsCmds: List<NsCmdRefactoringAction> = emptyList()) : ChangeInfo {
    private data class TeleWhitespaceData (val beforeTeleWhitespace: String, val beforeVarWhitespace: List<String>, val colonText: String?, val afterTypeText: String?)
    private val parameterToTeleWhitespaceData = HashMap<Int, TeleWhitespaceData>()

    init {
        var count = 0
        if (locatedReferable is ParametersHolder) {
            for (tele in locatedReferable.parameters) when (tele) {
                is ArendNameTele -> {
                    val beforeTeleWhitespace = tele.getWhitespace(SpaceDirection.LeadingSpace) ?: continue
                    val beforeVarWhitespaces = ArrayList<String>()
                    for (id in tele.identifierOrUnknownList) id.getWhitespace(SpaceDirection.LeadingSpace)?.let { beforeVarWhitespaces.add(it)  }
                    val colonText = tele.type?.let { it.getWhitespace(SpaceDirection.LeadingSpace, true) }
                    val afterTypeText = tele.type?.let{ it.getWhitespace(SpaceDirection.TrailingSpace) }
                    val data = TeleWhitespaceData(beforeTeleWhitespace, beforeVarWhitespaces, colonText, afterTypeText)
                    for (p in tele.identifierOrUnknownList) {
                        parameterToTeleWhitespaceData[count] = data
                        count++
                    }
                }
            }
        }

    }

    override fun getNewParameters(): Array<ParameterInfo> = parameterInfo.toTypedArray()

    override fun isParameterSetOrOrderChanged(): Boolean = true //TODO: Implement me

    override fun isParameterTypesChanged(): Boolean = true //TODO: Implement me

    override fun isParameterNamesChanged(): Boolean = true //TODO: Implement me

    override fun isGenerateDelegate(): Boolean = false

    override fun getMethod(): PsiElement = locatedReferable

    override fun isReturnTypeChanged(): Boolean = returnType != (locatedReferable as? ArendDefFunction)?.returnExpr?.text

    override fun isNameChanged(): Boolean = newName != locatedReferable.defIdentifier?.name

    override fun getNewName(): String = name

    override fun getLanguage(): Language = ArendLanguage.INSTANCE

    private data class TeleEntry(val typeText: String?, val isExplicit: Boolean, val parameterNames: MutableList<String?>)
    private fun parameterText(): String {
        val teleEntries = ArrayList<TeleEntry>()
        val associatedWhitespaceData = HashSet<Pair<TeleEntry, TeleWhitespaceData>>()
        val usedWhitespaceData = HashSet<TeleWhitespaceData>()

        for (parameter in parameterInfo) {
            if (teleEntries.isEmpty() || teleEntries.last().typeText != parameter.typeText || teleEntries.last().isExplicit != parameter.isExplicit())
                teleEntries.add(TeleEntry(parameter.typeText, parameter.isExplicit(), singletonList(parameter.name).toMutableList())) else
                    teleEntries.last().parameterNames.add(parameter.name)
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
            newTeles.append (if (entry.isExplicit) "(" else "{")
            for ((i, p) in entry.parameterNames.withIndex()) {
                newTeles.append(whitespaceData?.beforeVarWhitespace?.getOrNull(i) ?: if (i > 0) " " else "")
                newTeles.append(if (p.isNullOrEmpty()) "_" else p)
            }
            for (j in whitespaceData?.beforeVarWhitespace?.drop(entry.parameterNames.size) ?: emptyList()) newTeles.append(j)
            if (entry.typeText != null) {
                newTeles.append(whitespaceData?.colonText ?: " : ")
                newTeles.append(entry.typeText)
            }
            newTeles.append(whitespaceData?.afterTypeText ?: "")
            newTeles.append (if (entry.isExplicit) ")" else "}")
        }
        return newTeles.toString()
    }


    private fun returnPart(): String {
        val returnExpr = locatedReferable.children.firstOrNull { it.elementType == ArendElementTypes.RETURN_EXPR }
        var colonWhitespace = ""
        var pointer: PsiElement? = returnExpr?.prevSibling
        while (pointer != null && !locatedReferable.children.contains(pointer)) {
            colonWhitespace = pointer.text + colonWhitespace
            pointer.findPrevSibling()
            pointer = pointer.prevSibling
        }
        if (colonWhitespace == "") colonWhitespace = " "
        return if (returnType.isNullOrEmpty()) "" else "$colonWhitespace$returnType"
    }

    fun signaturePart() = "$name${(locatedReferable as? ReferableBase<*>)?.alias?.let{ " ${it.text}" } ?: ""}${parameterText()}}${returnPart()}"

    fun signaturePreview(): String = when (locatedReferable) {
        is ArendDefFunction -> "${locatedReferable.functionKw.text}${(locatedReferable as? ReferableBase<*>)?.prec?.let { " ${it.text}" } ?: ""} $name${(locatedReferable as? ReferableBase<*>)?.alias?.let{ " ${it.text}" } ?: ""}${parameterText()}${returnPart()}"
        else -> throw IllegalStateException()
    }

    companion object {
        fun getParameterInfo(locatedReferable: PsiLocatedReferable): MutableList<ArendParameterInfo> {
            var index = 0
            val result = ArrayList<ArendParameterInfo>()
            for (t in getTeles(locatedReferable)) when (t) {
                is ArendNameTele -> for (parameter in t.identifierOrUnknownList) {
                    result.add(ArendParameterInfo(parameter.defIdentifier?.name ?: "_", t.type?.text, index, t.isExplicit))
                    index++
                }
                is ArendTypeTele -> {
                    result.add(ArendParameterInfo(null, t.typedExpr?.text, index, t.isExplicit))
                    index++
                }
                is ArendNameTeleUntyped -> {
                    result.add(ArendParameterInfo(t.defIdentifier.name, null, index, t.isExplicit))
                    index++
                }
            }
            return result
        }
        private fun getTeles(locatedReferable: PsiLocatedReferable): List<PsiElement> = when (locatedReferable) {
            is ArendFunctionDefinition<*> -> locatedReferable.parameters
            is ArendDefData -> locatedReferable.parameters
            is ArendDefClass -> locatedReferable.fieldTeleList
            is ArendDefMeta -> locatedReferable.parameters
            is ArendConstructor -> locatedReferable.parameters
            else -> throw IllegalStateException()
        }
    }
}