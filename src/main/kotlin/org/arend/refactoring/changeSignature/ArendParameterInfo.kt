package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.psi.ArendNameTele

class ArendParameterInfo(val parameter: ArendNameTele,
                         private var name: String,
                         private var isExplicit: Boolean) : ParameterInfo {

    override fun getName(): String {
        // only one argument can be in tele for now
        return parameter.identifierOrUnknownList[0].text
    }

    override fun getOldIndex(): Int = -1

    override fun getDefaultValue(): String = ""

    override fun setName(name: String?) {
        if (name == null) return
        this.name = name
    }

    override fun getTypeText(): String? = parameter.expr?.text

    fun isExplicit(): Boolean = isExplicit
//        parameter.isExplicit

    fun switchExplicit() {
        isExplicit.xor(true)
    }

    override fun isUseAnySingleVariable(): Boolean = false

    override fun setUseAnySingleVariable(b: Boolean) {}
}