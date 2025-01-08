package org.arend.psi.ext

import com.intellij.openapi.util.TextRange
import org.arend.term.abs.AbstractReference

interface ArendReferenceElement : ArendReferenceContainer, AbstractReference {
    val rangeInElement: TextRange
}
