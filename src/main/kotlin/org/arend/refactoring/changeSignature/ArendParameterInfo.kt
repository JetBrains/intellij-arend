package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo

class ArendParameterInfo(
    private var name: String,
    private var type: String?,
    private var isExplicit: Boolean
) : ParameterInfo {

    override fun getName(): String = name

    override fun getOldIndex(): Int = -1

    override fun getDefaultValue(): String = ""

    override fun setName(name: String?) {
        if (name == null) return
        this.name = name
    }

    override fun getTypeText(): String? = type

    fun isExplicit(): Boolean = isExplicit

    fun switchExplicit() {
        isExplicit = !isExplicit
    }

    override fun isUseAnySingleVariable(): Boolean = false

    override fun setUseAnySingleVariable(b: Boolean) {}
}