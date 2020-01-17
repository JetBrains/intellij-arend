package org.arend.psi

import com.intellij.openapi.application.runReadAction
import com.intellij.psi.PsiErrorElement
import org.arend.ext.error.SourceInfo
import org.arend.ext.reference.DataContainer
import org.arend.psi.ext.moduleTextRepresentationImpl
import org.arend.psi.ext.positionTextRepresentationImpl

class SourceInfoErrorData(private val cause: PsiErrorElement) : SourceInfo, DataContainer {
    override fun getData(): Any = cause

    override fun moduleTextRepresentation(): String? = runReadAction { cause.moduleTextRepresentationImpl() }

    override fun positionTextRepresentation(): String? = runReadAction { cause.positionTextRepresentationImpl() }
}
