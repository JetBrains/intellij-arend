package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo

data class ArendParameterInfo(
    private var name: String?,
    private var type: String?,
    private val oldIndex: Int, /* == -1, if does not correspond to an old parameter */
    private var isExplicit: Boolean) : ParameterInfo {
    override fun getName(): String = name ?: "_"

    override fun getOldIndex(): Int = oldIndex

    override fun getDefaultValue(): String = ""

    override fun setName(name: String?) {
        this.name = name ?: return
    }

    override fun getTypeText(): String? = type

    override fun isUseAnySingleVariable(): Boolean = false

    override fun setUseAnySingleVariable(b: Boolean) {}

    fun setType(type: String?) {
        this.type = type ?: return
    }

    fun isExplicit(): Boolean = isExplicit

    fun switchExplicit() {
        isExplicit = !isExplicit
    }

    companion object {
        fun createEmpty(): ArendParameterInfo = ArendParameterInfo("", "", ParameterInfo.NEW_PARAMETER, true)
    }
}