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

    fun isExplicit(): Boolean = isExplicit

    fun switchExplicit() {
        isExplicit = !isExplicit
    }

    override fun isUseAnySingleVariable(): Boolean = false

    override fun setUseAnySingleVariable(b: Boolean) {}

    companion object {
        fun create(tele: ArendNameTele, index: Int): ArendParameterInfo =
            ArendParameterInfo(tele.identifierOrUnknownList[0].text, tele.expr?.text ?: "", index, tele.isExplicit)

        fun createEmpty(): ArendParameterInfo = ArendParameterInfo("", "", -1, true)
    }
}