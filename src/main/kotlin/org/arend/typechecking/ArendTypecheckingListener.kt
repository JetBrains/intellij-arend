package org.arend.typechecking

import com.intellij.util.containers.ContainerUtil
import org.arend.core.context.binding.Binding
import org.arend.naming.reference.Referable


object ArendTypecheckingListener : TypecheckingListener {
    val referableTypeCache: MutableMap<Referable, Binding> = ContainerUtil.createWeakKeySoftValueMap<Referable, Binding>()

    override fun referableTypechecked(referable: Referable?, binding: Binding?) {
        if (referable != null && binding != null) referableTypeCache[referable.underlyingReferable] = binding
    }
}
