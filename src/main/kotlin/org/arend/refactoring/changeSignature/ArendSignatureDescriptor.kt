package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.MethodDescriptor
import org.arend.psi.ArendDefFunction

class ArendSignatureDescriptor(private val function: ArendDefFunction) : MethodDescriptor<ArendParameterInfo, String> {
    override fun getName(): String? = function.name

    override fun getParameters(): List<ArendParameterInfo> {
        return function.nameTeleList.map { ArendParameterInfo(it) }
    }

    override fun getParametersCount(): Int = function.nameTeleList.size

    override fun getVisibility() = ""

    override fun getMethod() = function

    override fun canChangeVisibility() = false

    override fun canChangeParameters() = true

    override fun canChangeName() = true

    override fun canChangeReturnType(): MethodDescriptor.ReadWriteOption = MethodDescriptor.ReadWriteOption.None
}