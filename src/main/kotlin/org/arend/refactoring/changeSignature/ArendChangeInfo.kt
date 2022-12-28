package org.arend.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiComment
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiWhiteSpace
import com.intellij.psi.util.elementType
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.ArendLanguage
import org.arend.psi.*
import org.arend.psi.ext.*
import org.arend.refactoring.NsCmdRefactoringAction
import org.arend.term.abs.Abstract.ParametersHolder
import java.util.Collections.singletonList

class ArendChangeInfo (val parameterInfo : List<ArendParameterInfo>,
                       val returnType: String?,
                       val name: String,
                       val locatedReferable: PsiLocatedReferable,
                       val nsCmds: List<NsCmdRefactoringAction> = emptyList()) : ChangeInfo {
    private data class TeleWhitespaceData (val beforeTeleWhitespace: String, val beforeVarWhitespace: List<String>, val colonText: String?, val afterTypeText: String?)
    private val myTeleWhitespaces: List<TeleWhitespaceData>

    private fun leadingSpace(element: PsiElement?, onlySignificant: Boolean = true): String? {
        val parent = element?.parent ?: return null
        val children = when (onlySignificant) {
            false -> parent.childrenWithLeaves.filter { it !is PsiWhiteSpace && it !is PsiComment }.toList()
            true -> parent.children.toList()
        }
        var prev = element.prevSibling
        var buffer = ""
        while (prev != null && !children.contains(prev)) {
            buffer = prev.text + buffer
            prev = prev.prevSibling
        }
        return buffer
    }

    private fun trailingSpace(element: PsiElement?, onlySignificant: Boolean = true): String? {
        val parent = element?.parent ?: return null
        val children = when (onlySignificant) {
            false -> parent.childrenWithLeaves.filter { it !is PsiWhiteSpace && it !is PsiComment }.toList()
            true -> parent.children.toList()
        }
        var next = element.nextSibling
        var buffer = ""
        while (next != null && !children.contains(next)) {
            buffer += next.text
            next = next.nextSibling
        }
        return buffer
    }

    init {
        val teleWhitespaces = ArrayList<TeleWhitespaceData>()
        if (locatedReferable is ParametersHolder) {
            for (tele in locatedReferable.parameters) when (tele) {
                is ArendNameTele -> {
                    val beforeTeleWhitespace = leadingSpace(tele)!!
                    val beforeVarWhitespaces = ArrayList<String>()
                    for (id in tele.identifierOrUnknownList) {
                        val beforeVar = leadingSpace(id, false)
                        if (beforeVar != null) beforeVarWhitespaces.add(beforeVar)
                    }
                    val colonText = tele.type?.let { leadingSpace(it) }
                    val afterTypeText = tele.type?.let{ trailingSpace(it, false) }
                    teleWhitespaces.add(TeleWhitespaceData(beforeTeleWhitespace, beforeVarWhitespaces, colonText, afterTypeText))
                }
            }
        }
        myTeleWhitespaces = teleWhitespaces
        println(myTeleWhitespaces)
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

    fun parameterText(): String {
        val teleEntries = ArrayList<Pair<Pair<String?, Boolean>, MutableList<String?>>>()
        val whitespaceList = ArrayList<String>()
        var lastWhitespace = " "

        when (locatedReferable) {
            is ArendDefFunction -> {
                var buffer = ""
                var currNode = locatedReferable.parameters.firstOrNull()?.findPrevSibling()?.nextSibling
                while (currNode != null) {
                    if (currNode is ArendNameTele) {
                        lastWhitespace = buffer
                        whitespaceList.add(buffer)
                        buffer = ""
                    } else {
                        buffer += currNode.text
                    }
                    currNode = currNode.nextSibling
                }
            }
        }

        for (parameter in parameterInfo) {
            if (teleEntries.isEmpty() || (teleEntries.last().first.first != parameter.typeText || teleEntries.last().first.second != parameter.isExplicit())) {
                teleEntries.add(Pair(Pair(parameter.typeText, parameter.isExplicit()), singletonList(parameter.name).toMutableList()))
            } else {
                teleEntries.last().second.add(parameter.name)
            }
        }

        val newTeles = StringBuilder()
        for ((index, entry) in teleEntries.withIndex()) {
            val whitespace = whitespaceList.getOrNull(index) ?: lastWhitespace
            if (index > 0) newTeles.append(whitespace)
            newTeles.append (if (entry.first.second) "(" else "{")
            for ((i, p) in entry.second.withIndex()) {
                if (i > 0) newTeles.append(" ")
                newTeles.append(p ?: "_")
            }
            if (entry.first.first != null)
                newTeles.append(" : ${entry.first.first}")
            newTeles.append (if (entry.first.second) ")" else "}")
        }
        return newTeles.toString()
    }


    fun returnPart(): String {
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

    fun signaturePart() = "$name${(locatedReferable as? ReferableBase<*>)?.alias?.let{ " ${it.text}" } ?: ""}${parameterText().let { if (it.isNotEmpty()) " $it" else "" }}${returnPart()}"

    fun signaturePreview(): String = when (locatedReferable) {
        is ArendDefFunction -> "${locatedReferable.functionKw.text}${(locatedReferable as? ReferableBase<*>)?.prec?.let { " ${it.text}" } ?: ""} $name${(locatedReferable as? ReferableBase<*>)?.alias?.let{ " ${it.text}" } ?: ""}${parameterText().let { if (it.isNotEmpty()) " $it" else "" }}${returnPart()}"
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