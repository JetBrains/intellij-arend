package org.arend.psi.ext

import com.intellij.openapi.util.TextRange

interface ArendReferenceElement : ArendReferenceContainer {
    val rangeInElement: TextRange
}
