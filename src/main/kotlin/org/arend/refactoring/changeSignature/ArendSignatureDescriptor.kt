package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.MethodDescriptor
import org.arend.psi.ArendDefFunction

class ArendSignatureDescriptor(val function: ArendDefFunction) : MethodDescriptor<ArendParameterInfo, String> {
    override fun getName() = function.name

    override fun getParameters(): List<ArendParameterInfo> = function.parameters.map { ArendParameterInfo(it) }

    override fun getParametersCount(): Int = function.parameters.size

    override fun getVisibility() = ""

    override fun getMethod() = function

    override fun canChangeVisibility() = false

    override fun canChangeParameters() = true

    override fun canChangeName() = true

    override fun canChangeReturnType(): MethodDescriptor.ReadWriteOption {
        return MethodDescriptor.ReadWriteOption.ReadWrite
    }
}