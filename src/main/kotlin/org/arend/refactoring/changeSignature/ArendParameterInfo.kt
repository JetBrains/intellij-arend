package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo

class ArendParameterInfo constructor(
    private var name: String?,
    private var type: String?,
    private val oldIndex: Int, // Should be -1 if it does not correspond to any old parameter
    private var isExplicit: Boolean
) : ParameterInfo {

    override fun getName(): String = name ?: "_"

    override fun getOldIndex(): Int = oldIndex

    override fun getDefaultValue(): String = ""

    override fun setName(name: String?) {
        if (name == null) return
        this.name = name
    }

    override fun getTypeText(): String? = type

    override fun isUseAnySingleVariable(): Boolean = false

    override fun setUseAnySingleVariable(b: Boolean) {}

    fun setType(type: String?) {
        if (type == null) return
        this.type = type
    }

    fun isExplicit(): Boolean = isExplicit

    fun switchExplicit() {
        isExplicit = !isExplicit
    }

    fun showTele(): String {
        val (lParen, rParen) = if (isExplicit) Pair("(", ")") else Pair("{", "}")
        return "$lParen$name : $type$rParen"
    }

    companion object {
        fun createEmpty(): ArendParameterInfo = ArendParameterInfo("", "", ParameterInfo.NEW_PARAMETER, true)
    }
}