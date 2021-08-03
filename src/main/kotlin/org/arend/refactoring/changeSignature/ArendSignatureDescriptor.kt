package org.arend.refactoring.changeSignature

import com.intellij.psi.PsiElement
import com.intellij.refactoring.changeSignature.MethodDescriptor
import com.intellij.refactoring.changeSignature.ParameterInfoImpl
import org.arend.psi.ArendDefFunction

class ArendSignatureDescriptor(val function: ArendDefFunction) : MethodDescriptor<ParameterInfoImpl, String> {
    override fun getName(): String {
        return "getName"
    }

    override fun getParameters(): List<ParameterInfoImpl> {
        return emptyList()
    }

    override fun getParametersCount(): Int {
        return 0
    }

    override fun getVisibility(): String {
        return "visibility"
    }

    override fun getMethod(): PsiElement {
        return function
    }

    override fun canChangeVisibility(): Boolean {
//        TODO("Not yet implemented")
        return false
    }

    override fun canChangeParameters(): Boolean {
//        TODO("Not yet implemented")
        return false
    }

    override fun canChangeName(): Boolean {
//        TODO("Not yet implemented")
        return false
    }

    override fun canChangeReturnType(): MethodDescriptor.ReadWriteOption {
//        TODO("Not yet implemented")
        return MethodDescriptor.ReadWriteOption.ReadWrite
    }


}