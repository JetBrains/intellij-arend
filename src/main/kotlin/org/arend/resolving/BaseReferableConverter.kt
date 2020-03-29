package org.arend.resolving

import org.arend.naming.reference.Referable
import org.arend.naming.reference.converter.ReferableConverter
import org.arend.psi.ext.impl.ModuleAdapter

abstract class BaseReferableConverter : ReferableConverter {
    override fun convert(referable: Referable?): Referable? = (referable as? ModuleAdapter)?.metaReferable ?: super.convert(referable)
}