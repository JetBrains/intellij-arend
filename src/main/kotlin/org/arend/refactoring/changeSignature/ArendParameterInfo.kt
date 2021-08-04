package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.ParameterInfo
import org.arend.term.abs.Abstract

class ArendParameterInfo(val parameter: Abstract.Parameter) : ParameterInfo {
    override fun getName(): String = parameter.toString()

    override fun getOldIndex(): Int = -1

    override fun getDefaultValue(): String? = null

    override fun setName(name: String?) {
        TODO("Not yet implemented")
    }

    override fun getTypeText(): String {
        return parameter.type.toString()
    }


    override fun isUseAnySingleVariable(): Boolean {
        TODO("Not yet implemented")
    }

    override fun setUseAnySingleVariable(b: Boolean) {
        TODO("Not yet implemented")
    }


}