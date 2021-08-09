package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.psi.ArendNameTele

class ArendParameterInfo private constructor(
    private var name: String,
    private var type: String?,
    private val oldIndex: Int,
    private var isExplicit: Boolean
) : ParameterInfo {

    override fun getName(): String = name

    override fun getOldIndex(): Int = oldIndex

    override fun getDefaultValue(): String = ""

    override fun setName(name: String?) {
        if (name == null) return
        this.name = name
    }

    override fun getTypeText(): String? = type

    fun setType(type: String?) {
        if (type == null) return
        this.type = type
    }

    fun isExplicit(): Boolean = isExplicit

    fun switchExplicit() {
        isExplicit = !isExplicit
    }

    fun showTele(): String {
        val (lBr, rBr) = if (isExplicit) Pair("(", ")") else Pair("{", "}")
        return "$lBr$name : $type$rBr"
    }

    override fun isUseAnySingleVariable(): Boolean = false

    override fun setUseAnySingleVariable(b: Boolean) {}

    companion object {
        fun create(tele: ArendNameTele, index: Int): ArendParameterInfo =
            ArendParameterInfo(tele.identifierOrUnknownList[0].text, tele.expr?.text ?: "", index, tele.isExplicit)

        fun createEmpty(): ArendParameterInfo = ArendParameterInfo("", "", ParameterInfo.NEW_PARAMETER, true)
    }
}