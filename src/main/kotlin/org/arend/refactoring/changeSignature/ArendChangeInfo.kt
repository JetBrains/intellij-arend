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

data class ArendChangeInfo (
    private val parameterInfo : List<ArendParameterInfo>,
    private val returnType: String?,
    private val name: String,
    private val locatedReferable: PsiLocatedReferable,
    private val nsCmds: List<NsCmdRefactoringAction> = emptyList()) : ChangeInfo {
    private data class TeleWhitespaceData (val beforeTeleWhitespace: String, val colonText: String?, val afterTypeText: String?)
    private val parameterToTeleWhitespaceData = HashMap<Int, TeleWhitespaceData>()
    private val parameterWhitespaceData = HashMap<Int, String>()

    private val pLevelsKw = locatedReferable.childrenWithLeaves.firstOrNull {it.elementType == ArendElementTypes.PLEVELS_KW}
    private val pLevelParam = (locatedReferable as? ArendDefinition<*>)?.pLevelParameters
    private val pLevelsText = if (pLevelsKw != null && pLevelParam is PsiElement) " ${pLevelsKw.text} ${pLevelParam.text}" else ""
    private val hLevelsKw = locatedReferable.childrenWithLeaves.firstOrNull {it.elementType == ArendElementTypes.HLEVELS_KW}
    private val hLevelParam = (locatedReferable as? ArendDefinition<*>)?.hLevelParameters
    private val hLevelsText = if (hLevelsKw != null && hLevelParam is PsiElement) " ${hLevelsKw.text} ${hLevelParam.text}" else ""
    private val precText = (locatedReferable as? ReferableBase<*>)?.prec?.let { " ${it.text}" } ?: ""
    private val aliasText = (locatedReferable as? ReferableBase<*>)?.alias?.let{ " ${it.text}" } ?: ""

    init {
        var count = 0
        if (locatedReferable is ParametersHolder) {
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

    override fun getNewParameters(): Array<ParameterInfo> = parameterInfo.toTypedArray()

    override fun isParameterSetOrOrderChanged(): Boolean { //Or explicitness of arguments
        val oldParameterInfo = getParameterInfo(locatedReferable)
        if (oldParameterInfo.size != parameterInfo.size) return true
        for ((i, p) in parameterInfo.withIndex()) if (p.oldIndex != i || p.isExplicit() != oldParameterInfo[i].isExplicit()) return true
        return false
    }

    override fun isParameterTypesChanged(): Boolean {
        val oldParameterInfo = getParameterInfo(locatedReferable)
        for (p in parameterInfo) if (p.oldIndex != -1 && p.typeText != oldParameterInfo[p.oldIndex].typeText) return true
        return false
    }

    override fun isParameterNamesChanged(): Boolean {
        val oldParameterInfo = getParameterInfo(locatedReferable)
        for (p in parameterInfo) if (p.oldIndex != -1 && p.name != oldParameterInfo[p.oldIndex].name) return true
        return false
    }

    override fun isGenerateDelegate(): Boolean = false

    override fun getMethod(): PsiElement = locatedReferable

    override fun isReturnTypeChanged(): Boolean = returnType != (locatedReferable as? ArendDefFunction)?.returnExpr?.text

    override fun isNameChanged(): Boolean = newName != locatedReferable.defIdentifier?.name

    override fun getNewName(): String = name

    override fun getLanguage(): Language = ArendLanguage.INSTANCE

    private fun parameterText(): String {
        data class TeleEntry(val typeText: String?, val isExplicit: Boolean, val parameterNames: MutableList<Pair<String?, Int>>)
        val teleEntries = ArrayList<TeleEntry>()
        val associatedWhitespaceData = HashSet<Pair<TeleEntry, TeleWhitespaceData>>()
        val usedWhitespaceData = HashSet<TeleWhitespaceData>()

        for (parameter in parameterInfo) {
            if (teleEntries.isEmpty() || teleEntries.last().typeText != parameter.typeText || teleEntries.last().isExplicit != parameter.isExplicit())
                teleEntries.add(TeleEntry(parameter.typeText, parameter.isExplicit(), singletonList(Pair(parameter.name, parameter.oldIndex)).toMutableList())) else
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
            newTeles.append (if (entry.isExplicit) "(" else "{")
            for ((i, p) in entry.parameterNames.withIndex()) {
                var space = parameterWhitespaceData[p.second] ?: " "
                if (i == 0 && space.startsWith(" ")) space = space.drop(1)
                if (i > 0 && space.isEmpty()) space = " "
                newTeles.append(space)
                newTeles.append(p.first.let{ if (it.isNullOrEmpty()) "_" else it })
            }
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
            pointer = pointer.prevSibling
        }
        return when {
            returnType == null && returnExpr == null -> ""
            returnType == null && returnExpr != null -> "$colonWhitespace${returnExpr.text}"
            returnType != null && returnType.isEmpty() -> ""
            returnType != null && returnExpr == null -> " : $returnType"
            else -> "$colonWhitespace$returnType"
        }
    }

    fun signaturePart(): String {
        return "$name$pLevelsText$hLevelsText${aliasText}${parameterText()}${returnPart()}"
    }

    fun signaturePreview(): String = when (locatedReferable) {
        is ArendDefFunction -> {
            "${locatedReferable.functionKw.text}$precText $name$pLevelsText$hLevelsText$aliasText${parameterText()}${returnPart()}"
        }
        else -> throw IllegalStateException()
    }

    fun addNamespaceCommands() {
        for (nsCmd in nsCmds) nsCmd.execute()
    }

    companion object {
        fun getParameterInfo(locatedReferable: PsiLocatedReferable): MutableList<ArendParameterInfo> {
            var index = 0
            val result = ArrayList<ArendParameterInfo>()

            for (t in getTeles(locatedReferable)) when (t) {
                is ArendNameTele -> for (parameter in t.identifierOrUnknownList) {
                    result.add(ArendParameterInfo(parameter.defIdentifier?.name ?: "_", t.type?.oneLineText, index, t.isExplicit))
                    index++
                }
                is ArendTypeTele -> {
                    result.add(ArendParameterInfo(null, t.typedExpr?.oneLineText, index, t.isExplicit))
                    index++
                }
                is ArendNameTeleUntyped -> {
                    result.add(ArendParameterInfo(t.defIdentifier.name, null, index, t.isExplicit))
                    index++
                }
            }
            return result
        }
        fun getTeles(locatedReferable: PsiLocatedReferable): List<PsiElement> = when (locatedReferable) {
            is ArendFunctionDefinition<*> -> locatedReferable.parameters
            is ArendDefData -> locatedReferable.parameters
            is ArendDefClass -> locatedReferable.fieldTeleList
            is ArendDefMeta -> locatedReferable.parameters
            is ArendConstructor -> locatedReferable.parameters
            else -> throw IllegalStateException()
        }
    }
}