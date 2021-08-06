package org.arend.refactoring.changeSignature

import com.intellij.refactoring.changeSignature.MethodDescriptor
import org.arend.psi.ArendDefFunction

class ArendSignatureDescriptor(private val function: ArendDefFunction) :
    MethodDescriptor<ArendParameterInfo, String> {
    override fun getName(): String? = function.name

    override fun getParameters(): List<ArendParameterInfo> {
        // FIXME: only one argument in tele for now
        return function.nameTeleList.mapIndexed { index, it -> ArendParameterInfo.create(it, index,) }
    }

    override fun getParametersCount(): Int = function.nameTeleList.size

    override fun getVisibility() = ""

    override fun getMethod() = function

    override fun canChangeVisibility() = false

    override fun canChangeParameters() = true

    override fun canChangeName() = true

    override fun canChangeReturnType(): MethodDescriptor.ReadWriteOption = MethodDescriptor.ReadWriteOption.None
}