package org.arend.resolving

import org.arend.ext.reference.Precedence
import org.arend.ext.typechecking.MetaDefinition
import org.arend.naming.reference.MetaReferable
import org.arend.naming.reference.Referable

class ArendMetaReferable(precedence: Precedence, name: String, definition: MetaDefinition?) : MetaReferable(precedence, name, definition) {
    var underlyingRef: Referable? = null

    override fun getUnderlyingReferable() = underlyingRef ?: this
}