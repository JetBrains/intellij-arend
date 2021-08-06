package org.arend.refactoring.changeSignature

import com.intellij.lang.Language
import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.ChangeInfo
import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.ArendLanguage
import org.arend.psi.ArendDefFunction
import org.arend.psi.ArendNameTele

class ArendChangeInfo private constructor(
    private val function: ArendDefFunction,
    private val name: String,
    private val originalTeles: List<ArendParameterInfo>,
    private val returnType: String?
) : ChangeInfo {
    override fun getNewParameters(): Array<ParameterInfo> = originalTeles.toTypedArray()

    override fun isParameterSetOrOrderChanged(): Boolean = false

    override fun isParameterTypesChanged(): Boolean = true

    override fun isParameterNamesChanged(): Boolean = true

    override fun isGenerateDelegate(): Boolean = false

    override fun isNameChanged(): Boolean = true

    override fun getMethod(): PsiElement = function

    override fun isReturnTypeChanged(): Boolean = false

    override fun getNewName(): String = name

    override fun getLanguage(): Language = ArendLanguage.INSTANCE

    fun signature(): String = buildString {
        append("\\func $name ")
        for (tele in originalTeles) {
            val lBr = if (tele.isExplicit()) "(" else "{"
            val rBr = if (tele.isExplicit()) ")" else "}"
            append("$lBr${tele.name} : ${tele.typeText}$rBr ")
        }
        append(": $returnType")
    }

    companion object {
        fun create(function: ArendDefFunction): ArendChangeInfo {
            return ArendChangeInfo(
                function,
                function.name ?: "ERROR NAME",
                function.nameTeleList.mapIndexed { index, tele -> ArendParameterInfo.create(tele, index) },
                function.returnExpr?.text
            )
        }
    }
}