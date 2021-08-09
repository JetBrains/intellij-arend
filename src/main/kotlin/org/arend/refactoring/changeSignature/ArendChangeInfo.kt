package org.arend.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.ArendLanguage
import org.arend.psi.ArendDefFunction

class ArendChangeInfo private constructor(
    private val function: ArendDefFunction,
    private val name: String,
    private val teles: MutableList<ArendParameterInfo>,
    var returnType: String?
) : ChangeInfo {
    override fun getNewParameters(): Array<ParameterInfo> = teles.toTypedArray()

    override fun isParameterSetOrOrderChanged(): Boolean = false

    override fun isParameterTypesChanged(): Boolean = true

    override fun isParameterNamesChanged(): Boolean = true

    override fun isGenerateDelegate(): Boolean = false

    override fun isNameChanged(): Boolean = true

    override fun getMethod(): PsiElement = function

    override fun isReturnTypeChanged(): Boolean = false

    override fun getNewName(): String = name

    override fun getLanguage(): Language = ArendLanguage.INSTANCE

    fun addNewParameter(parameter: ArendParameterInfo) = teles.add(parameter)

    fun removeParameter(index: Int) = teles.removeAt(index)

    // replace this function?
    fun updateReturnType(type: String?) {
        returnType = type
    }

    // TODO: rename all occurrences in signature
    // \func id {{-caret-}A : \Type} (a : A) => a
    fun signature(): String = buildString {
        append("\\func $name ")
        for (tele in teles) {
            append("${tele.showTele()} ")
        }
        append(": $returnType")
    }

    companion object {
        fun create(function: ArendDefFunction): ArendChangeInfo {
            return ArendChangeInfo(
                function,
                function.name ?: "ERROR NAME",
                function.nameTeleList.mapIndexed { index, tele ->
                    ArendParameterInfo.create(
                        tele,
                        index
                    )
                } as MutableList<ArendParameterInfo>,
                function.returnExpr?.text
            )
        }
    }
}