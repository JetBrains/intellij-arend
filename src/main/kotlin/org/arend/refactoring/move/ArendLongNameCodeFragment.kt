package org.arend.refactoring.move

import com.intellij.lang.PsiBuilder
import com.intellij.openapi.project.Project
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.PsiCodeFragmentImpl
import org.arend.IArendFile
import org.arend.parser.ArendParser
import org.arend.psi.ArendCodeFragmentElementType
import org.arend.psi.ArendElementTypes
import org.arend.resolving.ArendReference

class ArendLongNameCodeFragment(project: Project, text: String, context: PsiElement?) :
    PsiCodeFragmentImpl(project, ArendLongNameCodeFragmentElementType, true, "fragment.ard", text, context),
    IArendFile {

    override fun getReference(): ArendReference? = null // TODO:

    override fun moduleTextRepresentation(): String = name

    override fun positionTextRepresentation(): String? = null
}

object ArendLongNameCodeFragmentElementType : ArendCodeFragmentElementType("AREND_LONG_NAME_CODE_FRAGMENT", ArendElementTypes.LONG_NAME) {
    override fun doParse(builder: PsiBuilder): Boolean = ArendParser.longName(builder, 1)
}
