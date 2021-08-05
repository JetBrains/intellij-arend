package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.psi.ArendNameTele

class ArendParameterInfo(val parameter: ArendNameTele) : ParameterInfo {
    override fun getName(): String {
        // only one argument in tele for now
        return parameter.identifierOrUnknownList[0].text
    }

    override fun getOldIndex(): Int = -1

    override fun getDefaultValue(): String = ""

    override fun setName(name: String?) {
//        TODO("Not yet implemented")
    }

    override fun getTypeText(): String? = parameter.expr?.text

    override fun isUseAnySingleVariable(): Boolean = false

    override fun setUseAnySingleVariable(b: Boolean) {}
}