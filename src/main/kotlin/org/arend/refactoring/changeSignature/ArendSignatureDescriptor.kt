package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.MethodDescriptor

class ArendSignatureDescriptor(private val changeInfo: ArendChangeInfo) :
    MethodDescriptor<ArendParameterInfo, String> {
    override fun getName(): String = changeInfo.newName

    override fun getParameters(): List<ArendParameterInfo> {
        // FIXME: only one argument in tele for now
        return changeInfo.newParameters.map { it as ArendParameterInfo }
    }

    fun getReturnType(): String? = changeInfo.returnType

    fun setReturnType(type: String?) = changeInfo.updateReturnType(type)

    fun addNewParameter(parameter: ArendParameterInfo) = changeInfo.addNewParameter(parameter)

    fun removeParameter(index: Int) = changeInfo.removeParameter(index)

    override fun getParametersCount(): Int = changeInfo.newParameters.size

    override fun getVisibility() = ""

    override fun getMethod() = changeInfo.method

    override fun canChangeVisibility() = false

    override fun canChangeParameters() = true

    override fun canChangeName() = true

    override fun canChangeReturnType(): MethodDescriptor.ReadWriteOption = MethodDescriptor.ReadWriteOption.None
}